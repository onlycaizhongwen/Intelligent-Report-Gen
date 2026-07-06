package com.company.report.citation.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record CollaborationNotification(
        Long id,
        Long recipientUserId,
        Long actorUserId,
        String type,
        Long reportId,
        Long taskId,
        String status,
        Map<String, Object> payload,
        OffsetDateTime createdAt
) {
    public static CollaborationNotification unread(Long recipientUserId,
                                                   Long actorUserId,
                                                   String type,
                                                   Long reportId,
                                                   Long taskId,
                                                   Map<String, Object> payload) {
        return new CollaborationNotification(
                null,
                recipientUserId,
                actorUserId,
                type,
                reportId,
                taskId,
                "unread",
                payload == null ? Map.of() : payload,
                OffsetDateTime.now()
        );
    }

    public CollaborationNotification withId(Long id) {
        return new CollaborationNotification(id, recipientUserId, actorUserId, type, reportId, taskId, status, payload, createdAt);
    }
}
