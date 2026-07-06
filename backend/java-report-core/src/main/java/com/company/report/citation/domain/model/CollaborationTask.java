package com.company.report.citation.domain.model;

public record CollaborationTask(
        Long id,
        Long reportId,
        Long annotationId,
        Long assigneeUserId,
        String status
) {
    public static CollaborationTask open(Long reportId, Long annotationId, Long assigneeUserId) {
        return new CollaborationTask(null, reportId, annotationId, assigneeUserId, "open");
    }

    public CollaborationTask withId(Long id) {
        return new CollaborationTask(id, reportId, annotationId, assigneeUserId, status);
    }

    public CollaborationTask withStatus(String status) {
        return new CollaborationTask(id, reportId, annotationId, assigneeUserId, status);
    }
}
