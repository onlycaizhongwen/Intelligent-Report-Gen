package com.company.report.rule.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record RuleApprovalTemplate(
        Long id,
        String name,
        String description,
        String status,
        Integer version,
        List<Map<String, Object>> steps,
        Long createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RuleApprovalTemplate create(String name,
                                              String description,
                                              String status,
                                              List<Map<String, Object>> steps,
                                              Long createdByUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new RuleApprovalTemplate(
                null,
                name,
                description,
                status == null || status.isBlank() ? "enabled" : status,
                1,
                steps == null ? List.of() : List.copyOf(steps),
                createdByUserId,
                now,
                now
        );
    }

    public RuleApprovalTemplate withId(Long id) {
        return new RuleApprovalTemplate(id, name, description, status, version, steps, createdByUserId, createdAt, updatedAt);
    }

    public RuleApprovalTemplate withStatus(String status) {
        return new RuleApprovalTemplate(id, name, description, status, version, steps, createdByUserId, createdAt, OffsetDateTime.now());
    }

    public RuleApprovalTemplate withContent(String name,
                                            String description,
                                            String status,
                                            List<Map<String, Object>> steps) {
        return new RuleApprovalTemplate(
                id,
                name,
                description,
                status,
                (version == null ? 1 : version) + 1,
                steps == null ? List.of() : List.copyOf(steps),
                createdByUserId,
                createdAt,
                OffsetDateTime.now()
        );
    }
}
