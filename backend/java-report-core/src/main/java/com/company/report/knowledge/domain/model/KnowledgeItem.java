package com.company.report.knowledge.domain.model;

public record KnowledgeItem(
        Long id,
        Long knowledgeBaseId,
        String title,
        String content,
        String sourceType,
        String indexStatus,
        Long createdBy
) {
    public static KnowledgeItem manual(Long knowledgeBaseId, String title, String content, String sourceType, Long createdBy) {
        return new KnowledgeItem(null, knowledgeBaseId, title, content, sourceType, "pending", createdBy);
    }

    public KnowledgeItem withId(Long id) {
        return new KnowledgeItem(id, knowledgeBaseId, title, content, sourceType, indexStatus, createdBy);
    }
}
