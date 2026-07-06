package com.company.report.report.domain.model;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReportGenerationTask(
        Long id,
        Long reportId,
        Long createdBy,
        String generationMode,
        Map<String, Object> userInput,
        Map<String, Object> templateSnapshot,
        String status,
        String currentStage,
        int progress,
        String failureReason,
        String traceId,
        Map<String, Object> outline,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReportGenerationTask naturalLanguage(Long createdBy, String topic, Map<String, Object> payload, String traceId) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("topic", topic);
        input.put("payload", payload == null ? Map.of() : payload);
        OffsetDateTime now = OffsetDateTime.now();
        return new ReportGenerationTask(null, null, createdBy, "natural_language", input, null,
                "outline_ready", "outline", 0, null, traceId, defaultOutline(topic, payload), now, now);
    }

    public static ReportGenerationTask template(Long createdBy, String templateId, Map<String, Object> payload, String traceId) {
        Map<String, Object> input = Map.of("templateId", templateId);
        Map<String, Object> snapshot = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        OffsetDateTime now = OffsetDateTime.now();
        return new ReportGenerationTask(null, null, createdBy, "template", input, snapshot,
                "outline_ready", "outline", 0, null, traceId, defaultOutline("模板报告 " + templateId, payload), now, now);
    }

    public ReportGenerationTask withId(Long id) {
        return new ReportGenerationTask(id, reportId, createdBy, generationMode, userInput, templateSnapshot,
                status, currentStage, progress, failureReason, traceId, outline, createdAt, OffsetDateTime.now());
    }

    public ReportGenerationTask bindReport(Long reportId) {
        return new ReportGenerationTask(id, reportId, createdBy, generationMode, userInput, templateSnapshot,
                status, currentStage, progress, failureReason, traceId, outline, createdAt, OffsetDateTime.now());
    }

    public ReportGenerationTask confirmOutline(Map<String, Object> request) {
        Map<String, Object> confirmedOutline = new LinkedHashMap<>();
        confirmedOutline.put("confirmed", true);
        confirmedOutline.put("content", request.getOrDefault("outline", outline.get("sections")));
        return new ReportGenerationTask(id, reportId, createdBy, generationMode, userInput, templateSnapshot,
                "running", "retrieval", 10, null, traceId, confirmedOutline, createdAt, OffsetDateTime.now());
    }

    public ReportGenerationTask complete() {
        return new ReportGenerationTask(id, reportId, createdBy, generationMode, userInput, templateSnapshot,
                "completed", "export", 100, null, traceId, outline, createdAt, OffsetDateTime.now());
    }

    public ReportGenerationTask fail(String reason, boolean retryable) {
        return new ReportGenerationTask(id, reportId, createdBy, generationMode, userInput, templateSnapshot,
                retryable ? "retryable" : "failed", currentStage, progress, reason, traceId, outline, createdAt, OffsetDateTime.now());
    }

    public ReportGenerationTask retry() {
        return new ReportGenerationTask(id, reportId, createdBy, generationMode, userInput, templateSnapshot,
                "running", currentStage == null || currentStage.isBlank() ? "retrieval" : currentStage, Math.max(progress, 10),
                null, traceId, outline, createdAt, OffsetDateTime.now());
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", id);
        response.put("reportId", reportId);
        response.put("status", status);
        response.put("currentStage", currentStage);
        response.put("progress", progress);
        response.put("traceId", traceId);
        response.put("outline", outline);
        response.put("failureReason", failureReason);
        response.put("createdAt", createdAt.toString());
        return response;
    }

    private static Map<String, Object> defaultOutline(String topic, Map<String, Object> payload) {
        Map<String, Object> outline = new LinkedHashMap<>();
        outline.put("title", topic);
        outline.put("sections", java.util.List.of("执行摘要", "关键发现", "数据与知识库依据", "风险与建议"));
        outline.put("inputSnapshot", payload == null ? Map.of() : payload);
        return outline;
    }
}
