package com.company.report.knowledge.application;

import com.company.report.knowledge.domain.model.KnowledgeDataSource;
import com.company.report.knowledge.domain.repository.KnowledgeBaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class DataSourceSyncScheduler {
    private final KnowledgeBaseRepository repository;
    private final KnowledgeApplicationService service;
    private final boolean enabled;

    public DataSourceSyncScheduler(KnowledgeBaseRepository repository,
                                   KnowledgeApplicationService service,
                                   @Value("${knowledge.data-source.scheduler.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${knowledge.data-source.scheduler.fixed-delay-ms:60000}")
    public void runDueSyncs() {
        if (enabled) {
            runDueSyncsOnce(Map.of());
        }
    }

    int runDueSyncsOnce(Map<String, Object> requestOverrides) {
        OffsetDateTime now = OffsetDateTime.now();
        int processed = 0;
        for (KnowledgeDataSource dataSource : repository.findDueScheduledDataSources(now, 20)) {
            if (retryLimitReached(dataSource)) {
                continue;
            }
            boolean success = false;
            try {
                Map<String, Object> result = service.runScheduledDataSourceSync(dataSource, requestOverrides);
                success = "succeeded".equals(result.get("status"));
            } catch (Exception ignored) {
                success = false;
            }
            int intervalSeconds = dataSource.scheduleIntervalSeconds() == null ? 300 : dataSource.scheduleIntervalSeconds();
            int nextFailureCount = success ? 0 : (dataSource.failureCount() == null ? 0 : dataSource.failureCount()) + 1;
            long backoffSeconds = success ? intervalSeconds : Math.min(3600L, intervalSeconds * (long) Math.max(1, nextFailureCount));
            repository.updateDataSourceScheduleState(dataSource.id(), OffsetDateTime.now().plusSeconds(backoffSeconds), nextFailureCount);
            processed++;
        }
        return processed;
    }

    private boolean retryLimitReached(KnowledgeDataSource dataSource) {
        int maxRetryCount = dataSource.maxRetryCount() == null ? 3 : dataSource.maxRetryCount();
        int failureCount = dataSource.failureCount() == null ? 0 : dataSource.failureCount();
        return maxRetryCount >= 0 && failureCount >= maxRetryCount;
    }
}
