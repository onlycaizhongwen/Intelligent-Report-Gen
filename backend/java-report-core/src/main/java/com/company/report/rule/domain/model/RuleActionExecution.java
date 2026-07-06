package com.company.report.rule.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record RuleActionExecution(
        Long id,
        Long ruleId,
        Long runId,
        String nodeId,
        String actionType,
        String status,
        Integer attempt,
        Integer maxRetryCount,
        String endpoint,
        String idempotencyKey,
        OffsetDateTime nextRetryAt,
        String errorMessage,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
    public static RuleActionExecution webhook(Long ruleId,
                                              Long runId,
                                              String nodeId,
                                              String status,
                                              int attempt,
                                              int maxRetryCount,
                                              String endpoint,
                                              String idempotencyKey,
                                              OffsetDateTime nextRetryAt,
                                              String errorMessage,
                                              Map<String, Object> metadata) {
        return new RuleActionExecution(
                null,
                ruleId,
                runId,
                nodeId,
                "webhook",
                status,
                attempt,
                maxRetryCount,
                endpoint,
                idempotencyKey,
                nextRetryAt,
                errorMessage,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                OffsetDateTime.now()
        );
    }

    public RuleActionExecution withId(Long id) {
        return new RuleActionExecution(
                id,
                ruleId,
                runId,
                nodeId,
                actionType,
                status,
                attempt,
                maxRetryCount,
                endpoint,
                idempotencyKey,
                nextRetryAt,
                errorMessage,
                metadata,
                createdAt
        );
    }

    public RuleActionExecution withStatus(String status,
                                          Integer attempt,
                                          OffsetDateTime nextRetryAt,
                                          String errorMessage,
                                          Map<String, Object> metadata) {
        return new RuleActionExecution(
                id,
                ruleId,
                runId,
                nodeId,
                actionType,
                status,
                attempt,
                maxRetryCount,
                endpoint,
                idempotencyKey,
                nextRetryAt,
                errorMessage,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                OffsetDateTime.now()
        );
    }

    public RuleActionExecution withTerminalStatus(String status,
                                                  String errorMessage,
                                                  Map<String, Object> metadata) {
        return new RuleActionExecution(
                null,
                ruleId,
                runId,
                nodeId,
                actionType,
                status,
                attempt,
                maxRetryCount,
                endpoint,
                idempotencyKey,
                null,
                errorMessage,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                OffsetDateTime.now()
        );
    }
}
