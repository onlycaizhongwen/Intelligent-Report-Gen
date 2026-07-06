package com.company.report.rule.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record Rule(
        Long id,
        String name,
        String description,
        String status,
        Map<String, Object> definition,
        Long currentVersionId,
        Boolean scheduleEnabled,
        Integer scheduleIntervalSeconds,
        OffsetDateTime nextRunAt,
        Integer failureCount,
        Integer maxRetryCount,
        Map<String, Object> scheduleInput
) {
    public Rule(Long id, String name, String description, String status, Map<String, Object> definition, Long currentVersionId) {
        this(id, name, description, status, definition, currentVersionId, false, null, null, 0, 3, Map.of());
    }

    public static Rule draft(String name, String description, Map<String, Object> definition) {
        return new Rule(null, name, description, "draft", definition == null ? Map.of() : definition, null);
    }

    public Rule withId(Long id) {
        return new Rule(id, name, description, status, definition, currentVersionId,
                scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, scheduleInput);
    }

    public Rule update(String name, String description, String status, Map<String, Object> definition, Long versionId) {
        return new Rule(
                id,
                name == null || name.isBlank() ? this.name : name,
                description == null ? this.description : description,
                status == null || status.isBlank() ? this.status : status,
                definition == null ? this.definition : definition,
                versionId,
                scheduleEnabled,
                scheduleIntervalSeconds,
                nextRunAt,
                failureCount,
                maxRetryCount,
                scheduleInput
        );
    }

    public Rule withStatus(String status) {
        return new Rule(id, name, description, status, definition, currentVersionId,
                scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, scheduleInput);
    }

    public Rule withSchedule(Boolean scheduleEnabled,
                             Integer scheduleIntervalSeconds,
                             OffsetDateTime nextRunAt,
                             Integer failureCount,
                             Integer maxRetryCount,
                             Map<String, Object> scheduleInput) {
        return new Rule(id, name, description, status, definition, currentVersionId,
                Boolean.TRUE.equals(scheduleEnabled),
                scheduleIntervalSeconds,
                nextRunAt,
                failureCount == null ? 0 : failureCount,
                maxRetryCount == null ? 3 : maxRetryCount,
                scheduleInput == null ? Map.of() : scheduleInput);
    }

    public Rule withScheduleState(OffsetDateTime nextRunAt, int failureCount) {
        return withSchedule(scheduleEnabled, scheduleIntervalSeconds, nextRunAt, failureCount, maxRetryCount, scheduleInput);
    }
}
