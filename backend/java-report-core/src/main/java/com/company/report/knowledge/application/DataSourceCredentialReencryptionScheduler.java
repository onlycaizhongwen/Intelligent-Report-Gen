package com.company.report.knowledge.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DataSourceCredentialReencryptionScheduler {
    private final KnowledgeApplicationService service;
    private final boolean enabled;
    private final int limit;

    public DataSourceCredentialReencryptionScheduler(
            KnowledgeApplicationService service,
            @Value("${knowledge.data-source.credential-reencryption.scheduler.enabled:false}") boolean enabled,
            @Value("${knowledge.data-source.credential-reencryption.scheduler.limit:100}") int limit) {
        this.service = service;
        this.enabled = enabled;
        this.limit = Math.max(limit, 1);
    }

    @Scheduled(fixedDelayString = "${knowledge.data-source.credential-reencryption.scheduler.fixed-delay-ms:86400000}")
    public void runCredentialReencryption() {
        if (enabled) {
            runCredentialReencryptionOnce();
        }
    }

    Map<String, Object> runCredentialReencryptionOnce() {
        return service.reencryptStaleDataSourceCredentials(limit);
    }
}
