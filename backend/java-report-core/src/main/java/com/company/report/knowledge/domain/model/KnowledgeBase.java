package com.company.report.knowledge.domain.model;

public record KnowledgeBase(Long id, String name, Long ownerUserId, String status) {
    public static KnowledgeBase newBase(String name, Long ownerUserId) {
        return new KnowledgeBase(null, name, ownerUserId, "enabled");
    }

    public KnowledgeBase withId(Long id) {
        return new KnowledgeBase(id, name, ownerUserId, status);
    }
}
