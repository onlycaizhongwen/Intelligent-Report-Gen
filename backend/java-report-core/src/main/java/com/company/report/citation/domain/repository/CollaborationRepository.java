package com.company.report.citation.domain.repository;

import com.company.report.citation.domain.model.Annotation;
import com.company.report.citation.domain.model.CollaborationNotification;
import com.company.report.citation.domain.model.CollaborationTask;

import java.util.Optional;

public interface CollaborationRepository {
    Annotation saveAnnotation(Annotation annotation);

    CollaborationTask saveTask(CollaborationTask task);

    CollaborationNotification saveNotification(CollaborationNotification notification);

    Optional<CollaborationTask> findTaskById(Long taskId);
}
