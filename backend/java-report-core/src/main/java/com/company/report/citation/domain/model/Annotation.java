package com.company.report.citation.domain.model;

import java.util.Map;

public record Annotation(
        Long id,
        Long reportId,
        Long createdBy,
        Long assigneeUserId,
        String content,
        Map<String, Object> anchor,
        String status
) {
    public static Annotation open(Long reportId, Long createdBy, Long assigneeUserId, String content, Map<String, Object> anchor) {
        return new Annotation(null, reportId, createdBy, assigneeUserId, content, anchor == null ? Map.of() : anchor, "open");
    }

    public Annotation withId(Long id) {
        return new Annotation(id, reportId, createdBy, assigneeUserId, content, anchor, status);
    }
}
