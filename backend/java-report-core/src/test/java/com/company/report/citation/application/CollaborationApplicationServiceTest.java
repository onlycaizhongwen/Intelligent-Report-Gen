package com.company.report.citation.application;

import com.company.report.permission.domain.model.UserAccount;
import com.company.report.permission.infrastructure.persistence.InMemoryUserRepository;
import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.model.ReportStatus;
import com.company.report.report.domain.repository.ReportRepository;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollaborationApplicationServiceTest {
    private final InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    private final InMemoryUserRepository userRepository = new InMemoryUserRepository();
    private final CollaborationApplicationService service = new CollaborationApplicationService(reportRepository, userRepository);

    @AfterEach
    void clearUser() {
        CurrentUserHolder.clear();
    }

    CollaborationApplicationServiceTest() {
        userRepository.save(new UserAccount(1001L, "owner", "Owner", "enabled", List.of("analyst")));
        userRepository.save(new UserAccount(1002L, "reviewer", "Reviewer", "enabled", List.of("analyst")));
        userRepository.save(new UserAccount(9002L, "disabled", "Disabled", "disabled", List.of("analyst")));
    }

    @Test
    void addAnnotationCreatesReviewTaskForReportOwner() {
        reportRepository.save(new Report(10L, "Quarterly report", 1001L, ReportStatus.DRAFT, null));
        CurrentUserHolder.set(new CurrentUser(1001L, Set.of("analyst"), Set.of("collaboration:write")));

        Map<String, Object> created = service.addAnnotation(10L, Map.of(
                "anchor", Map.of("sectionId", "summary", "startOffset", 0, "endOffset", 12, "selectedText", "Revenue risk"),
                "content", "Please verify revenue evidence",
                "assigneeUserId", 1002L
        ));

        assertThat(created)
                .containsEntry("reportId", 10L)
                .containsEntry("status", "open")
                .containsEntry("taskStatus", "open")
                .containsEntry("assigneeUserId", 1002L);
        assertThat(((Number) created.get("annotationId")).longValue()).isGreaterThan(0L);
        assertThat(((Number) created.get("taskId")).longValue()).isGreaterThan(0L);
    }

    @Test
    void addAnnotationAcceptsNumericStringAssigneeFromJsonFormInput() {
        reportRepository.save(new Report(15L, "Quarterly report", 1001L, ReportStatus.DRAFT, null));
        CurrentUserHolder.set(new CurrentUser(1001L, Set.of("analyst"), Set.of("collaboration:write")));

        Map<String, Object> created = service.addAnnotation(15L, Map.of(
                "anchor", Map.of("sectionId", "summary", "startOffset", 0, "endOffset", 12, "selectedText", "Revenue risk"),
                "content", "Please verify revenue evidence",
                "assigneeUserId", "1002"
        ));

        assertThat(created).containsEntry("assigneeUserId", 1002L);
    }

    @Test
    void addAnnotationRejectsNonOwnerBeforeCreatingTask() {
        reportRepository.save(new Report(11L, "Private report", 1001L, ReportStatus.DRAFT, null));
        CurrentUserHolder.set(new CurrentUser(2001L, Set.of("analyst"), Set.of("collaboration:write")));

        assertThatThrownBy(() -> service.addAnnotation(11L, Map.of("content", "try")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("report collaboration access denied");
    }

    @Test
    void taskStatusCanBeUpdatedByAssigneeOrReportOwnerOnly() {
        reportRepository.save(new Report(12L, "Review report", 1001L, ReportStatus.DRAFT, null));
        CurrentUserHolder.set(new CurrentUser(1001L, Set.of("analyst"), Set.of("collaboration:write")));
        Long taskId = ((Number) service.addAnnotation(12L, Map.of(
                "content", "Review this",
                "assigneeUserId", 1002L,
                "anchor", Map.of("sectionId", "summary", "startOffset", 0, "endOffset", 6, "selectedText", "Review")
        )).get("taskId")).longValue();

        CurrentUserHolder.set(new CurrentUser(3001L, Set.of("analyst"), Set.of("collaboration:write")));
        assertThatThrownBy(() -> service.updateTaskStatus(taskId, Map.of("status", "done")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("task collaboration access denied");

        CurrentUserHolder.set(new CurrentUser(1002L, Set.of("analyst"), Set.of("collaboration:write")));
        Map<String, Object> updated = service.updateTaskStatus(taskId, Map.of("status", "done"));

        assertThat(updated)
                .containsEntry("taskId", taskId)
                .containsEntry("status", "done");
    }

    @Test
    void addAnnotationRequiresTextOffsetAnchorAndCreatesAssigneeNotification() {
        reportRepository.save(new Report(13L, "Anchored report", 1001L, ReportStatus.DRAFT, null));
        CurrentUserHolder.set(new CurrentUser(1001L, Set.of("analyst"), Set.of("collaboration:write")));

        assertThatThrownBy(() -> service.addAnnotation(13L, Map.of(
                "content", "Please check",
                "assigneeUserId", 1002L,
                "anchor", Map.of("sectionId", "summary")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anchor startOffset/endOffset/selectedText required");

        Map<String, Object> created = service.addAnnotation(13L, Map.of(
                "content", "Please check source",
                "assigneeUserId", 1002L,
                "anchor", Map.of(
                        "sectionId", "summary",
                        "startOffset", 18,
                        "endOffset", 31,
                        "selectedText", "revenue growth"
                )
        ));

        assertThat(created)
                .containsEntry("notificationRecipientUserId", 1002L)
                .containsEntry("notificationType", "collaboration_task_assigned");
    }

    @Test
    void addAnnotationRejectsInactiveAssigneeAndTaskStatusFlowIsRestricted() {
        reportRepository.save(new Report(14L, "Status report", 1001L, ReportStatus.DRAFT, null));
        CurrentUserHolder.set(new CurrentUser(1001L, Set.of("analyst"), Set.of("collaboration:write")));

        assertThatThrownBy(() -> service.addAnnotation(14L, Map.of(
                "content", "Review this",
                "assigneeUserId", 9002L,
                "anchor", Map.of(
                        "sectionId", "risk",
                        "startOffset", 0,
                        "endOffset", 4,
                        "selectedText", "risk"
                )
        )))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("assignee collaboration access denied");

        Long taskId = ((Number) service.addAnnotation(14L, Map.of(
                "content", "Review valid",
                "assigneeUserId", 1002L,
                "anchor", Map.of(
                        "sectionId", "risk",
                        "startOffset", 0,
                        "endOffset", 4,
                        "selectedText", "risk"
                )
        )).get("taskId")).longValue();

        CurrentUserHolder.set(new CurrentUser(1002L, Set.of("analyst"), Set.of("collaboration:write")));
        assertThatThrownBy(() -> service.updateTaskStatus(taskId, Map.of("status", "archived")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported task status");

        Map<String, Object> inProgress = service.updateTaskStatus(taskId, Map.of("status", "in_progress"));
        Map<String, Object> done = service.updateTaskStatus(taskId, Map.of("status", "done"));

        assertThat(inProgress).containsEntry("status", "in_progress");
        assertThat(done)
                .containsEntry("status", "done")
                .containsEntry("notificationRecipientUserId", 1001L)
                .containsEntry("notificationType", "collaboration_task_status_changed");
    }

    private static class InMemoryReportRepository implements ReportRepository {
        private final List<Report> reports = new ArrayList<>();

        @Override
        public Optional<Report> findById(Long id) {
            return reports.stream().filter(report -> report.id().equals(id)).findFirst();
        }

        @Override
        public Report save(Report report) {
            reports.removeIf(existing -> existing.id().equals(report.id()));
            reports.add(report);
            return report;
        }

        @Override
        public List<Report> findByOwner(Long ownerUserId, int page, int pageSize) {
            return reports.stream().filter(report -> report.ownerUserId().equals(ownerUserId)).toList();
        }

        @Override
        public long countByOwner(Long ownerUserId) {
            return findByOwner(ownerUserId, 1, Integer.MAX_VALUE).size();
        }
    }
}
