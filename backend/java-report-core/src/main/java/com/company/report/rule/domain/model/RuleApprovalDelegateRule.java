package com.company.report.rule.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public record RuleApprovalDelegateRule(
        Long id,
        String assigneeRole,
        String delegateRole,
        OffsetDateTime activeFrom,
        OffsetDateTime activeTo,
        List<String> activeWeekdays,
        List<String> activeDates,
        String status,
        String reason,
        Long createdByUserId,
        OffsetDateTime createdAt
) {
    public static RuleApprovalDelegateRule enabled(String assigneeRole,
                                                   String delegateRole,
                                                   OffsetDateTime activeFrom,
                                                   OffsetDateTime activeTo,
                                                   List<String> activeWeekdays,
                                                   List<String> activeDates,
                                                   String reason,
                                                   Long createdByUserId) {
        return new RuleApprovalDelegateRule(
                null,
                assigneeRole,
                delegateRole,
                activeFrom,
                activeTo,
                activeWeekdays == null ? List.of() : List.copyOf(activeWeekdays),
                activeDates == null ? List.of() : List.copyOf(activeDates),
                "enabled",
                reason,
                createdByUserId,
                OffsetDateTime.now()
        );
    }

    public RuleApprovalDelegateRule withId(Long id) {
        return new RuleApprovalDelegateRule(
                id,
                assigneeRole,
                delegateRole,
                activeFrom,
                activeTo,
                activeWeekdays,
                activeDates,
                status,
                reason,
                createdByUserId,
                createdAt
        );
    }

    public RuleApprovalDelegateRule disable(String reason) {
        return new RuleApprovalDelegateRule(
                id,
                assigneeRole,
                delegateRole,
                activeFrom,
                activeTo,
                activeWeekdays,
                activeDates,
                "disabled",
                reason,
                createdByUserId,
                createdAt
        );
    }

    public RuleApprovalDelegateRule enable(String reason) {
        return new RuleApprovalDelegateRule(
                id,
                assigneeRole,
                delegateRole,
                activeFrom,
                activeTo,
                activeWeekdays,
                activeDates,
                "enabled",
                reason,
                createdByUserId,
                createdAt
        );
    }

    public RuleApprovalDelegateRule update(String assigneeRole,
                                           String delegateRole,
                                           OffsetDateTime activeFrom,
                                           OffsetDateTime activeTo,
                                           List<String> activeWeekdays,
                                           List<String> activeDates,
                                           String reason) {
        return new RuleApprovalDelegateRule(
                id,
                assigneeRole,
                delegateRole,
                activeFrom,
                activeTo,
                activeWeekdays == null ? List.of() : List.copyOf(activeWeekdays),
                activeDates == null ? List.of() : List.copyOf(activeDates),
                status,
                reason,
                createdByUserId,
                createdAt
        );
    }
}
