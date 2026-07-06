package com.company.report.knowledge.domain.repository;

import com.company.report.knowledge.domain.model.KnowledgeBase;
import com.company.report.knowledge.domain.model.KnowledgeDataSource;
import com.company.report.knowledge.domain.model.KnowledgeItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.OffsetDateTime;

public interface KnowledgeBaseRepository {
    KnowledgeBase save(KnowledgeBase knowledgeBase);

    Optional<KnowledgeBase> findById(Long id);

    List<KnowledgeBase> findByOwner(Long ownerUserId, int page, int pageSize);

    long countByOwner(Long ownerUserId);

    KnowledgeItem saveItem(KnowledgeItem item);

    List<KnowledgeItem> searchItems(Long ownerUserId, String keyword, int page, int pageSize);

    long countItems(Long ownerUserId, String keyword);

    Optional<KnowledgeItem> findItemById(Long id);

    boolean softDeleteItem(Long itemId);

    long countReportReferences(Long itemId);

    KnowledgeDataSource saveDataSource(KnowledgeDataSource dataSource);

    Optional<KnowledgeDataSource> findDataSourceById(Long id);

    KnowledgeDataSource updateDataSourceCursor(Long dataSourceId, String lastCursor);

    boolean tryAcquireDataSourceSyncLease(Long dataSourceId, OffsetDateTime lockedUntil);

    void releaseDataSourceSyncLease(Long dataSourceId);

    List<KnowledgeDataSource> findDueScheduledDataSources(OffsetDateTime now, int limit);

    KnowledgeDataSource updateDataSourceScheduleState(Long dataSourceId, OffsetDateTime nextRunAt, int failureCount);

    Map<String, Object> saveDataSourceSyncRun(Map<String, Object> syncRun);

    List<Map<String, Object>> findDataSourceSyncRuns(Long dataSourceId, int page, int pageSize);

    long countDataSourceSyncRuns(Long dataSourceId);
}
