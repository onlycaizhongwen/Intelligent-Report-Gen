package com.company.report.audit.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record OperationLog(
        Long id,
        Long actorUserId,
        String operationType,
        String resourceType,
        Long resourceId,
        String result,
        Map<String, Object> detail,
        OffsetDateTime createdAt
) {
    public OperationLog withId(Long id) {
        return new OperationLog(id, actorUserId, operationType, resourceType, resourceId, result, detail, createdAt);
    }

    public Map<String, Object> toResponse() {
        Map<String, Object> safeDetail = detail == null ? Map.of() : detail;
        java.util.LinkedHashMap<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("operationLogId", id);
        response.put("actorUserId", actorUserId);
        response.put("operationType", operationType);
        response.put("resourceType", resourceType);
        response.put("resourceId", resourceId);
        response.put("result", result);
        response.putAll(safeDetail);
        response.put("createdAt", createdAt == null ? null : createdAt.toString());
        return response;
    }
}
