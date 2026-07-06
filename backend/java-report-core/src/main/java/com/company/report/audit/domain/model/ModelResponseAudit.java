package com.company.report.audit.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record ModelResponseAudit(
        Long id,
        Long invocationId,
        Integer responseOrder,
        String responseContent,
        Map<String, Object> responseMetadata,
        String finishReason,
        OffsetDateTime createdAt
) {
    public ModelResponseAudit withId(Long id) {
        return new ModelResponseAudit(id, invocationId, responseOrder, responseContent, responseMetadata, finishReason, createdAt);
    }

    public ModelResponseAudit withInvocationId(Long invocationId) {
        return new ModelResponseAudit(id, invocationId, responseOrder, responseContent, responseMetadata, finishReason, createdAt);
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("responseId", id);
        result.put("invocationId", invocationId);
        result.put("responseOrder", responseOrder);
        result.put("responseContent", responseContent);
        result.put("responseMetadata", responseMetadata == null ? Map.of() : responseMetadata);
        result.put("finishReason", finishReason);
        result.put("createdAt", createdAt);
        return result;
    }
}
