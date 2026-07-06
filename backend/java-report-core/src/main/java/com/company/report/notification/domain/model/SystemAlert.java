package com.company.report.notification.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record SystemAlert(
        Long id,
        Long recipientUserId,
        String type,
        String severity,
        String status,
        String resourceType,
        Long resourceId,
        Map<String, Object> payload,
        OffsetDateTime createdAt
) {
    public static SystemAlert unread(Long recipientUserId,
                                     String type,
                                     String severity,
                                     String resourceType,
                                     Long resourceId,
                                     Map<String, Object> payload) {
        return new SystemAlert(
                null,
                recipientUserId,
                type,
                severity == null || severity.isBlank() ? "info" : severity,
                "unread",
                resourceType,
                resourceId,
                payload == null ? Map.of() : payload,
                OffsetDateTime.now()
        );
    }

    public SystemAlert withId(Long id) {
        return new SystemAlert(id, recipientUserId, type, severity, status, resourceType, resourceId, payload, createdAt);
    }
}
