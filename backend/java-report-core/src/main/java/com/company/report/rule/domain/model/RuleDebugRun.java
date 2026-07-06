package com.company.report.rule.domain.model;

import java.util.Map;

public record RuleDebugRun(
        Long id,
        Long ruleId,
        Long versionId,
        String runType,
        String status,
        Long triggeredByUserId,
        Long durationMs,
        String errorMessage,
        Map<String, Object> input,
        Map<String, Object> output
) {
    public static RuleDebugRun succeededDebug(Long ruleId, Long versionId, Map<String, Object> input, Map<String, Object> output) {
        return succeeded("debug", ruleId, versionId, null, null, input, output);
    }

    public static RuleDebugRun succeededProduction(Long ruleId, Long versionId, Long triggeredByUserId, Long durationMs, Map<String, Object> input, Map<String, Object> output) {
        return succeeded("production", ruleId, versionId, triggeredByUserId, durationMs, input, output);
    }

    public static RuleDebugRun failedProduction(Long ruleId, Long versionId, Long triggeredByUserId, Long durationMs, String errorMessage, Map<String, Object> input) {
        return new RuleDebugRun(
                null,
                ruleId,
                versionId,
                "production",
                "failed",
                triggeredByUserId,
                durationMs,
                errorMessage,
                input == null ? Map.of() : input,
                Map.of()
        );
    }

    private static RuleDebugRun succeeded(String runType, Long ruleId, Long versionId, Long triggeredByUserId, Long durationMs, Map<String, Object> input, Map<String, Object> output) {
        return new RuleDebugRun(
                null,
                ruleId,
                versionId,
                runType,
                "succeeded",
                triggeredByUserId,
                durationMs,
                null,
                input == null ? Map.of() : input,
                output == null ? Map.of() : output
        );
    }

    public RuleDebugRun withId(Long id) {
        return new RuleDebugRun(id, ruleId, versionId, runType, status, triggeredByUserId, durationMs, errorMessage, input, output);
    }

    public RuleDebugRun withStatus(String status) {
        return new RuleDebugRun(id, ruleId, versionId, runType, status, triggeredByUserId, durationMs, errorMessage, input, output);
    }
}
