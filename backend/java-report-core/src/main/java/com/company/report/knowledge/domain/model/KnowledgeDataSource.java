package com.company.report.knowledge.domain.model;

import java.time.OffsetDateTime;

public record KnowledgeDataSource(
        Long id,
        Long ownerUserId,
        String name,
        String sourceType,
        String endpoint,
        String username,
        String credentialSecret,
        Long knowledgeBaseId,
        String syncQuery,
        String fieldMappingJson,
        String cursorColumn,
        String lastCursor,
        Boolean scheduleEnabled,
        Integer scheduleIntervalSeconds,
        OffsetDateTime nextRunAt,
        Integer failureCount,
        Integer maxRetryCount,
        String status
) {
    public static KnowledgeDataSource enabled(Long ownerUserId, String name, String sourceType, String endpoint) {
        return enabled(ownerUserId, name, sourceType, endpoint, null, null, null, null, (String) null);
    }

    public static KnowledgeDataSource enabled(Long ownerUserId,
                                              String name,
                                              String sourceType,
                                              String endpoint,
                                              String username,
                                              String credentialSecret,
                                              Long knowledgeBaseId,
                                              String syncQuery,
                                              String lastCursor) {
        return enabled(ownerUserId, name, sourceType, endpoint, username, credentialSecret, knowledgeBaseId,
                syncQuery, null, lastCursor);
    }

    public static KnowledgeDataSource enabled(Long ownerUserId,
                                              String name,
                                              String sourceType,
                                              String endpoint,
                                              String username,
                                              String credentialSecret,
                                              Long knowledgeBaseId,
                                              String syncQuery,
                                              String cursorColumn,
                                              String lastCursor) {
        return enabled(ownerUserId, name, sourceType, endpoint, username, credentialSecret, knowledgeBaseId,
                syncQuery, null, cursorColumn, lastCursor, false, null, null, 0, 3);
    }

    public static KnowledgeDataSource enabled(Long ownerUserId,
                                              String name,
                                              String sourceType,
                                              String endpoint,
                                              String username,
                                              String credentialSecret,
                                              Long knowledgeBaseId,
                                              String syncQuery,
                                              String fieldMappingJson,
                                              String cursorColumn,
                                              String lastCursor) {
        return enabled(ownerUserId, name, sourceType, endpoint, username, credentialSecret, knowledgeBaseId,
                syncQuery, fieldMappingJson, cursorColumn, lastCursor, false, null, null, 0, 3);
    }

    public static KnowledgeDataSource enabled(Long ownerUserId,
                                              String name,
                                              String sourceType,
                                              String endpoint,
                                              String username,
                                              String credentialSecret,
                                              Long knowledgeBaseId,
                                              String syncQuery,
                                              String fieldMappingJson,
                                              String cursorColumn,
                                              String lastCursor,
                                              Boolean scheduleEnabled,
                                              Integer scheduleIntervalSeconds,
                                              OffsetDateTime nextRunAt,
                                              Integer failureCount) {
        return enabled(ownerUserId, name, sourceType, endpoint, username, credentialSecret, knowledgeBaseId,
                syncQuery, fieldMappingJson, cursorColumn, lastCursor, scheduleEnabled, scheduleIntervalSeconds,
                nextRunAt, failureCount, 3);
    }

    public static KnowledgeDataSource enabled(Long ownerUserId,
                                              String name,
                                              String sourceType,
                                              String endpoint,
                                              String username,
                                              String credentialSecret,
                                              Long knowledgeBaseId,
                                              String syncQuery,
                                              String fieldMappingJson,
                                              String cursorColumn,
                                              String lastCursor,
                                              Boolean scheduleEnabled,
                                              Integer scheduleIntervalSeconds,
                                              OffsetDateTime nextRunAt,
                                              Integer failureCount,
                                              Integer maxRetryCount) {
        return new KnowledgeDataSource(null, ownerUserId, name, sourceType, endpoint, username,
                credentialSecret, knowledgeBaseId, syncQuery, fieldMappingJson, cursorColumn, lastCursor,
                Boolean.TRUE.equals(scheduleEnabled), scheduleIntervalSeconds, nextRunAt,
                failureCount == null ? 0 : failureCount, maxRetryCount == null ? 3 : maxRetryCount, "enabled");
    }

    public KnowledgeDataSource withId(Long id) {
        return new KnowledgeDataSource(id, ownerUserId, name, sourceType, endpoint, username,
                credentialSecret, knowledgeBaseId, syncQuery, fieldMappingJson, cursorColumn, lastCursor,
                scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, status);
    }

    public KnowledgeDataSource withLastCursor(String lastCursor) {
        return new KnowledgeDataSource(id, ownerUserId, name, sourceType, endpoint, username,
                credentialSecret, knowledgeBaseId, syncQuery, fieldMappingJson, cursorColumn, lastCursor,
                scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, status);
    }

    public KnowledgeDataSource withScheduleState(OffsetDateTime nextRunAt, int failureCount) {
        return new KnowledgeDataSource(id, ownerUserId, name, sourceType, endpoint, username,
                credentialSecret, knowledgeBaseId, syncQuery, fieldMappingJson, cursorColumn, lastCursor,
                scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, status);
    }

    public KnowledgeDataSource withCredentialSecret(String newCredentialSecret) {
        return new KnowledgeDataSource(id, ownerUserId, name, sourceType, endpoint, username,
                newCredentialSecret, knowledgeBaseId, syncQuery, fieldMappingJson, cursorColumn, lastCursor,
                scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, status);
    }

    public KnowledgeDataSource withFieldMappingAndCursor(String newFieldMappingJson,
                                                         String newCursorColumn,
                                                         String newLastCursor) {
        return new KnowledgeDataSource(id, ownerUserId, name, sourceType, endpoint, username,
                credentialSecret, knowledgeBaseId, syncQuery, newFieldMappingJson, newCursorColumn, newLastCursor,
                scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, status);
    }
}
