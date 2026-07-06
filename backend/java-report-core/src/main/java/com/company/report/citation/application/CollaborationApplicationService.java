package com.company.report.citation.application;

import com.company.report.citation.domain.model.Annotation;
import com.company.report.citation.domain.model.CollaborationNotification;
import com.company.report.citation.domain.model.CollaborationTask;
import com.company.report.citation.domain.repository.CollaborationRepository;
import com.company.report.citation.infrastructure.persistence.InMemoryCollaborationRepository;
import com.company.report.permission.domain.model.UserAccount;
import com.company.report.permission.domain.repository.UserRepository;
import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.repository.ReportRepository;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CollaborationApplicationService {
    private final ReportRepository reportRepository;
    private final CollaborationRepository collaborationRepository;
    private final UserRepository userRepository;

    @Autowired
    public CollaborationApplicationService(ReportRepository reportRepository,
                                           CollaborationRepository collaborationRepository,
                                           UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.collaborationRepository = collaborationRepository;
        this.userRepository = userRepository;
    }

    public CollaborationApplicationService(ReportRepository reportRepository, UserRepository userRepository) {
        this(reportRepository, new InMemoryCollaborationRepository(), userRepository);
    }

    public CollaborationApplicationService(ReportRepository reportRepository, CollaborationRepository collaborationRepository) {
        this(reportRepository, collaborationRepository, null);
    }

    public CollaborationApplicationService(ReportRepository reportRepository) {
        this(reportRepository, new InMemoryCollaborationRepository(), null);
    }

    public Map<String, Object> addAnnotation(Long reportId, Map<String, Object> request) {
        CurrentUser user = requireUser();
        Report report = findReport(reportId);
        if (!report.ownerUserId().equals(user.userId())) {
            throw new SecurityException("report collaboration access denied");
        }

        Long assigneeUserId = longValue(request.get("assigneeUserId"), user.userId());
        requireEnabledAssignee(assigneeUserId);
        Map<String, Object> anchor = requireTextOffsetAnchor(map(request.get("anchor")));

        Annotation annotation = collaborationRepository.saveAnnotation(Annotation.open(
                reportId,
                user.userId(),
                assigneeUserId,
                string(request.get("content")),
                anchor
        ));
        CollaborationTask task = collaborationRepository.saveTask(CollaborationTask.open(
                reportId,
                annotation.id(),
                annotation.assigneeUserId()
        ));
        CollaborationNotification notification = collaborationRepository.saveNotification(CollaborationNotification.unread(
                annotation.assigneeUserId(),
                user.userId(),
                "collaboration_task_assigned",
                reportId,
                task.id(),
                Map.of("annotationId", annotation.id(), "selectedText", string(anchor.get("selectedText")))
        ));

        Map<String, Object> result = toAnnotationResponse(annotation);
        result.put("taskId", task.id());
        result.put("taskStatus", task.status());
        putNotification(result, notification);
        return result;
    }

    public Map<String, Object> updateTaskStatus(Long taskId, Map<String, Object> request) {
        CurrentUser user = requireUser();
        CollaborationTask task = collaborationRepository.findTaskById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("task not found: " + taskId));
        Report report = findReport(task.reportId());
        if (!report.ownerUserId().equals(user.userId()) && !task.assigneeUserId().equals(user.userId())) {
            throw new SecurityException("task collaboration access denied");
        }
        String nextStatus = requireSupportedTaskStatus(stringOrDefault(request.get("status"), "done"));
        CollaborationTask saved = collaborationRepository.saveTask(task.withStatus(nextStatus));
        Long recipientUserId = user.userId().equals(report.ownerUserId()) ? saved.assigneeUserId() : report.ownerUserId();
        CollaborationNotification notification = collaborationRepository.saveNotification(CollaborationNotification.unread(
                recipientUserId,
                user.userId(),
                "collaboration_task_status_changed",
                saved.reportId(),
                saved.id(),
                Map.of("annotationId", saved.annotationId(), "status", saved.status())
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", saved.id());
        result.put("reportId", saved.reportId());
        result.put("annotationId", saved.annotationId());
        result.put("assigneeUserId", saved.assigneeUserId());
        result.put("status", saved.status());
        putNotification(result, notification);
        return result;
    }

    private void requireEnabledAssignee(Long assigneeUserId) {
        if (userRepository == null) {
            return;
        }
        UserAccount assignee = userRepository.findById(assigneeUserId)
                .orElseThrow(() -> new SecurityException("assignee collaboration access denied: " + assigneeUserId));
        if (!"enabled".equals(assignee.status())) {
            throw new SecurityException("assignee collaboration access denied: " + assigneeUserId);
        }
    }

    private Map<String, Object> requireTextOffsetAnchor(Map<String, Object> anchor) {
        if (anchor == null
                || !anchor.containsKey("startOffset")
                || !anchor.containsKey("endOffset")
                || string(anchor.get("selectedText")).isBlank()) {
            throw new IllegalArgumentException("anchor startOffset/endOffset/selectedText required");
        }
        long startOffset = longValue(anchor.get("startOffset"), -1L);
        long endOffset = longValue(anchor.get("endOffset"), -1L);
        if (startOffset < 0 || endOffset <= startOffset) {
            throw new IllegalArgumentException("anchor startOffset/endOffset/selectedText required");
        }
        return anchor;
    }

    private String requireSupportedTaskStatus(String status) {
        if (!java.util.Set.of("open", "in_progress", "done", "rejected").contains(status)) {
            throw new IllegalArgumentException("unsupported task status: " + status);
        }
        return status;
    }

    private void putNotification(Map<String, Object> result, CollaborationNotification notification) {
        result.put("notificationId", notification.id());
        result.put("notificationRecipientUserId", notification.recipientUserId());
        result.put("notificationType", notification.type());
    }

    private Report findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
    }

    private CurrentUser requireUser() {
        CurrentUser user = CurrentUserHolder.get();
        if (user == null) {
            throw new SecurityException("current user required");
        }
        return user;
    }

    private Map<String, Object> toAnnotationResponse(Annotation annotation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("annotationId", annotation.id());
        result.put("reportId", annotation.reportId());
        result.put("createdBy", annotation.createdBy());
        result.put("assigneeUserId", annotation.assigneeUserId());
        result.put("content", annotation.content());
        result.put("anchor", annotation.anchor());
        result.put("status", annotation.status());
        return result;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        String text = string(value);
        return text.isBlank() ? defaultValue : text;
    }

    private static Long longValue(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return defaultValue;
        }
        return Long.valueOf(text);
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> input) {
            Map<String, Object> result = new LinkedHashMap<>();
            input.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }
}
