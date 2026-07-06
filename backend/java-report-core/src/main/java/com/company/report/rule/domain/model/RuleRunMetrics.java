package com.company.report.rule.domain.model;

public record RuleRunMetrics(
        long totalRuns,
        long succeededRuns,
        long failedRuns,
        double averageDurationMs,
        String lastStatus,
        String lastErrorMessage
) {
    public static RuleRunMetrics empty() {
        return new RuleRunMetrics(0L, 0L, 0L, 0d, null, null);
    }
}
