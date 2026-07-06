package com.company.report.citation.infrastructure.persistence;

import com.company.report.citation.domain.model.Annotation;
import com.company.report.citation.domain.model.CollaborationNotification;
import com.company.report.citation.domain.model.CollaborationTask;
import com.company.report.citation.domain.repository.CollaborationRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCollaborationRepository implements CollaborationRepository {
    private final AtomicLong annotationSequence = new AtomicLong(0);
    private final AtomicLong taskSequence = new AtomicLong(0);
    private final AtomicLong notificationSequence = new AtomicLong(0);
    private final Map<Long, Annotation> annotations = new LinkedHashMap<>();
    private final Map<Long, CollaborationTask> tasks = new LinkedHashMap<>();
    private final Map<Long, CollaborationNotification> notifications = new LinkedHashMap<>();

    @Override
    public synchronized Annotation saveAnnotation(Annotation annotation) {
        Annotation saved = annotation.id() == null || annotation.id() <= 0
                ? annotation.withId(annotationSequence.incrementAndGet())
                : annotation;
        annotations.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized CollaborationTask saveTask(CollaborationTask task) {
        CollaborationTask saved = task.id() == null || task.id() <= 0
                ? task.withId(taskSequence.incrementAndGet())
                : task;
        tasks.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized CollaborationNotification saveNotification(CollaborationNotification notification) {
        CollaborationNotification saved = notification.id() == null || notification.id() <= 0
                ? notification.withId(notificationSequence.incrementAndGet())
                : notification;
        notifications.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized Optional<CollaborationTask> findTaskById(Long taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }
}
