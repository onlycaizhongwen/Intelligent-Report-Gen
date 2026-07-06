package com.company.report.rule.domain.model;

import java.time.OffsetDateTime;

public record RuleApprovalRecord(
        Long id,
        Long ruleId,
        Long runId,
        String nodeId,
        String assigneeRole,
        String delegateRole,
        OffsetDateTime delegateActiveFrom,
        OffsetDateTime delegateActiveTo,
        String approvalTitle,
        String status,
        Long createdByUserId,
        Long approvedByUserId,
        String approvalComment,
        OffsetDateTime approvedAt,
        OffsetDateTime createdAt,
        Integer slaHours,
        Integer remindCount,
        OffsetDateTime lastRemindedAt
) {
    public static RuleApprovalRecord pending(Long ruleId,
                                             Long runId,
                                             String nodeId,
                                             String assigneeRole,
                                             String delegateRole,
                                             OffsetDateTime delegateActiveFrom,
                                             OffsetDateTime delegateActiveTo,
                                             String approvalTitle,
                                             Long createdByUserId,
                                             Integer slaHours) {
        return new RuleApprovalRecord(
                null,
                ruleId,
                runId,
                nodeId,
                assigneeRole,
                delegateRole,
                delegateActiveFrom,
                delegateActiveTo,
                approvalTitle,
                "pending",
                createdByUserId,
                null,
                null,
                null,
                OffsetDateTime.now(),
                slaHours,
                0,
                null
        );
    }

    public RuleApprovalRecord withId(Long id) {
        return new RuleApprovalRecord(
                id,
                ruleId,
                runId,
                nodeId,
                assigneeRole,
                delegateRole,
                delegateActiveFrom,
                delegateActiveTo,
                approvalTitle,
                status,
                createdByUserId,
                approvedByUserId,
                approvalComment,
                approvedAt,
                createdAt,
                slaHours,
                remindCount,
                lastRemindedAt
        );
    }

    public RuleApprovalRecord handle(String status, Long approvedByUserId, String approvalComment, OffsetDateTime approvedAt) {
        return new RuleApprovalRecord(
                id,
                ruleId,
                runId,
                nodeId,
                assigneeRole,
                delegateRole,
                delegateActiveFrom,
                delegateActiveTo,
                approvalTitle,
                status,
                createdByUserId,
                approvedByUserId,
                approvalComment,
                approvedAt,
                createdAt,
                slaHours,
                remindCount,
                lastRemindedAt
        );
    }

    public RuleApprovalRecord remind(OffsetDateTime remindedAt) {
        return new RuleApprovalRecord(
                id,
                ruleId,
                runId,
                nodeId,
                assigneeRole,
                delegateRole,
                delegateActiveFrom,
                delegateActiveTo,
                approvalTitle,
                status,
                createdByUserId,
                approvedByUserId,
                approvalComment,
                approvedAt,
                createdAt,
                slaHours,
                remindCount == null ? 1 : remindCount + 1,
                remindedAt
        );
    }

    public RuleApprovalRecord supplement(String status, String comment, OffsetDateTime handledAt) {
        return new RuleApprovalRecord(
                id,
                ruleId,
                runId,
                nodeId,
                assigneeRole,
                delegateRole,
                delegateActiveFrom,
                delegateActiveTo,
                approvalTitle,
                status,
                createdByUserId,
                approvedByUserId,
                comment,
                handledAt,
                createdAt,
                slaHours,
                remindCount,
                lastRemindedAt
        );
    }
}
