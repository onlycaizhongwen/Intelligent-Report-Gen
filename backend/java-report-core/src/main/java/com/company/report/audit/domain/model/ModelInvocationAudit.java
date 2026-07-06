package com.company.report.audit.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record ModelInvocationAudit(
        Long id,
        Long taskId,
        Long reportId,
        Long actorUserId,
        String provider,
        String modelName,
        String promptTemplateId,
        String promptSnapshot,
        String contextSnapshot,
        Map<String, Object> parameters,
        String requestHash,
        String status,
        Long durationMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        String errorCode,
        String errorMessage,
        String traceId,
        String auditEventKey,
        OffsetDateTime createdAt,
        ModelResponseAudit response
) {
    public ModelInvocationAudit withId(Long id) {
        return new ModelInvocationAudit(id, taskId, reportId, actorUserId, provider, modelName, promptTemplateId,
                promptSnapshot, contextSnapshot, parameters, requestHash, status, durationMs, inputTokens,
                outputTokens, totalTokens, errorCode, errorMessage, traceId, auditEventKey, createdAt, response);
    }

    public ModelInvocationAudit withResponse(ModelResponseAudit response) {
        return new ModelInvocationAudit(id, taskId, reportId, actorUserId, provider, modelName, promptTemplateId,
                promptSnapshot, contextSnapshot, parameters, requestHash, status, durationMs, inputTokens,
                outputTokens, totalTokens, errorCode, errorMessage, traceId, auditEventKey, createdAt, response);
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("invocationId", id);
        result.put("taskId", taskId);
        result.put("reportId", reportId);
        result.put("actorUserId", actorUserId);
        result.put("provider", provider);
        result.put("model", modelName);
        result.put("modelName", modelName);
        result.put("promptTemplateId", promptTemplateId);
        result.put("promptSnapshot", promptSnapshot);
        result.put("contextSnapshot", contextSnapshot);
        result.put("parameters", parameters == null ? Map.of() : parameters);
        result.put("requestHash", requestHash);
        result.put("status", status);
        result.put("result", status);
        result.put("durationMs", durationMs);
        result.put("latencyMs", durationMs);
        result.put("inputTokens", inputTokens);
        result.put("outputTokens", outputTokens);
        result.put("totalTokens", totalTokens);
        result.put("errorCode", errorCode);
        result.put("errorMessage", errorMessage);
        result.put("traceId", traceId);
        result.put("auditEventKey", auditEventKey);
        result.put("createdAt", createdAt);
        if (response != null) {
            result.put("response", response.toResponse());
            result.put("responseSummary", response.responseContent());
            result.put("finishReason", response.finishReason());
        }
        return result;
    }
}
