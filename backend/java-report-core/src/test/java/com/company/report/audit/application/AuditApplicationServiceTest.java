package com.company.report.audit.application;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.model.ModelInvocationAudit;
import com.company.report.audit.domain.model.ModelResponseAudit;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.audit.domain.repository.DashboardMetricsRepository;
import com.company.report.audit.domain.repository.ModelInvocationRepository;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditApplicationServiceTest {
    @Test
    void historyOnlyReturnsCurrentUsersOperationLogs() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        repository.save(new OperationLog(null, 501L, "report_export", "report", 101L, "succeeded", Map.of("format", "markdown"), OffsetDateTime.now()));
        repository.save(new OperationLog(null, 777L, "share_create", "share_link", 202L, "succeeded", Map.of("token", "other"), OffsetDateTime.now()));
        AuditApplicationService service = new AuditApplicationService(repository);

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("audit:history")));

            var history = service.history(1, 10);
            var auditLogs = service.auditLogs(1, 10);

            assertThat(history.items())
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("actorUserId", 501L)
                    .containsEntry("operationType", "report_export");
            assertThat(history.total()).isEqualTo(1L);
            assertThat(auditLogs.items()).hasSize(2);
            assertThat(auditLogs.total()).isEqualTo(2L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void readsModelInvocationFromDedicatedTableBeforeLegacyOperationLog() {
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        InMemoryModelInvocationRepository modelInvocationRepository = new InMemoryModelInvocationRepository();
        ModelInvocationAudit saved = modelInvocationRepository.save(new ModelInvocationAudit(
                null,
                77L,
                88L,
                501L,
                "openai-compatible",
                "gpt",
                "tpl-report",
                "Prompt snapshot",
                "Context snapshot",
                Map.of("temperature", 0.2),
                "sha256:req",
                "succeeded",
                1234L,
                321,
                654,
                975,
                null,
                null,
                "trace-model-table",
                "trace-model-table:77",
                OffsetDateTime.parse("2026-06-23T10:15:30+08:00"),
                new ModelResponseAudit(null, null, 1, "Response summary", Map.of("finish", "stop"), "stop", OffsetDateTime.parse("2026-06-23T10:15:31+08:00"))
        ));
        AuditApplicationService service = new AuditApplicationService(auditRepository, modelInvocationRepository);

        Map<String, Object> invocation = service.modelInvocation(saved.id());

        assertThat(invocation)
                .containsEntry("invocationId", saved.id())
                .containsEntry("taskId", 77L)
                .containsEntry("reportId", 88L)
                .containsEntry("model", "gpt")
                .containsEntry("provider", "openai-compatible")
                .containsEntry("promptSnapshot", "Prompt snapshot")
                .containsEntry("contextSnapshot", "Context snapshot")
                .containsEntry("durationMs", 1234L)
                .containsEntry("traceId", "trace-model-table")
                .containsEntry("responseSummary", "Response summary")
                .containsEntry("finishReason", "stop");
    }

    @Test
    void readsModelInvocationFromAuditLogInsteadOfFixedDemoPayload() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        OperationLog saved = repository.save(new OperationLog(
                null,
                501L,
                "model_invocation",
                "ai_model",
                9001L,
                "failed",
                Map.of(
                        "model", "gpt-4.1",
                        "provider", "openai",
                        "promptHash", "sha256:abc",
                        "latencyMs", 1234L,
                        "tokens", Map.of("prompt", 321, "completion", 654),
                        "traceId", "trace-model-9001"
                ),
                OffsetDateTime.parse("2026-06-23T10:15:30+08:00")
        ));
        AuditApplicationService service = new AuditApplicationService(repository);

        Map<String, Object> invocation = service.modelInvocation(saved.id());

        assertThat(invocation)
                .containsEntry("invocationId", saved.id())
                .containsEntry("actorUserId", 501L)
                .containsEntry("model", "gpt-4.1")
                .containsEntry("provider", "openai")
                .containsEntry("result", "failed")
                .containsEntry("promptHash", "sha256:abc")
                .containsEntry("latencyMs", 1234L)
                .containsEntry("traceId", "trace-model-9001");
        assertThat(invocation).doesNotContainEntry("model", "gpt-online-demo");
    }

    @Test
    void rejectsModelInvocationLookupForNonModelAuditLog() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        OperationLog saved = repository.save(new OperationLog(
                null,
                501L,
                "report_export",
                "report",
                9002L,
                "succeeded",
                Map.of("format", "markdown"),
                OffsetDateTime.now()
        ));
        AuditApplicationService service = new AuditApplicationService(repository);

        assertThatThrownBy(() -> service.modelInvocation(saved.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model invocation audit log not found");
    }

    @Test
    void buildsDashboardOverviewFromAuditLogsInsteadOfFixedDemoCards() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        repository.save(new OperationLog(null, 1L, "report_export", "report", 10L, "succeeded", Map.of("format", "markdown"), OffsetDateTime.now().minusDays(1)));
        repository.save(new OperationLog(null, 2L, "knowledge_item_create", "knowledge_item", 20L, "succeeded", Map.of("knowledgeBaseId", 3L), OffsetDateTime.now().minusDays(2)));
        repository.save(new OperationLog(null, 3L, "model_invocation", "ai_model", 30L, "failed", Map.of("model", "gpt-4.1"), OffsetDateTime.now().minusDays(3)));
        repository.save(new OperationLog(null, 4L, "report_export", "report", 40L, "succeeded", Map.of("format", "markdown"), OffsetDateTime.now().minusDays(40)));
        AuditApplicationService service = new AuditApplicationService(repository);

        Map<String, Object> overview = service.dashboard("last7days");

        assertThat(overview).containsEntry("range", "last7days");
        assertThat(overview.get("cards"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("reportExports", 1L)
                .containsEntry("knowledgeOperations", 1L)
                .containsEntry("modelInvocations", 1L)
                .containsEntry("failedOperations", 1L)
                .doesNotContainEntry("reportsThisMonth", 1);
        assertThat(overview.get("recentActivities"))
                .asList()
                .hasSize(3)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("operationType", "report_export");
    }

    @Test
    void buildsDashboardOverviewFromBusinessTablesAndKeepsRecentAuditActivities() {
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        auditRepository.save(new OperationLog(null, 11L, "report_generation_completed", "report", 101L, "succeeded", Map.of("title", "A"), OffsetDateTime.now().minusHours(2)));
        auditRepository.save(new OperationLog(null, 12L, "knowledge_item_create", "knowledge_item", 201L, "succeeded", Map.of("title", "B"), OffsetDateTime.now().minusHours(1)));
        InMemoryDashboardMetricsRepository dashboardMetricsRepository = new InMemoryDashboardMetricsRepository();
        AuditApplicationService service = new AuditApplicationService(
                auditRepository,
                new InMemoryModelInvocationRepository(),
                dashboardMetricsRepository
        );

        Map<String, Object> overview = service.dashboard("last7days");

        assertThat(dashboardMetricsRepository.since).isNotNull();
        assertThat(overview).containsEntry("range", "last7days");
        assertThat(overview.get("cards"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("reportOutputs", 7L)
                .containsEntry("knowledgeItems", 42L)
                .containsEntry("activeDataSources", 3L)
                .containsEntry("citationHitRate", 0.875)
                .containsEntry("activeUsers", 5L);
        assertThat(overview.get("reportTrend"))
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("date", "2026-06-23")
                .containsEntry("completedReports", 2L);
        assertThat(overview.get("knowledgeRank"))
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("knowledgeBaseId", 1L)
                .containsEntry("name", "Finance KB")
                .containsEntry("references", 9L);
        assertThat(overview.get("ruleScheduleHealth"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("scheduledRules", 4L)
                .containsEntry("failedScheduledRules", 2L)
                .containsEntry("blockedScheduledRules", 1L)
                .containsEntry("recentAlerts", 1L);
        assertThat(overview.get("recentActivities"))
                .asList()
                .hasSize(2);
    }

    @Test
    void dashboardUsesPersonalScopeForNonAdminUsersAndGlobalScopeForAdmins() {
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        auditRepository.save(new OperationLog(null, 501L, "report_generation_task_created", "report_generation_task", 101L, "succeeded", Map.of("topic", "mine"), OffsetDateTime.now().minusMinutes(2)));
        auditRepository.save(new OperationLog(null, 777L, "report_generation_task_created", "report_generation_task", 202L, "succeeded", Map.of("topic", "other"), OffsetDateTime.now().minusMinutes(1)));
        InMemoryDashboardMetricsRepository metricsRepository = new InMemoryDashboardMetricsRepository();
        AuditApplicationService service = new AuditApplicationService(
                auditRepository,
                new InMemoryModelInvocationRepository(),
                metricsRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("dashboard:read")));
            Map<String, Object> personalOverview = service.dashboard("last7days");

            assertThat(metricsRepository.actorUserId).isEqualTo(501L);
            assertThat(personalOverview.get("scope")).isEqualTo("personal");
            assertThat(personalOverview.get("recentActivities"))
                    .asList()
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("actorUserId", 501L)
                    .containsEntry("topic", "mine");

            CurrentUserHolder.set(new CurrentUser(1L, Set.of("ADMIN"), Set.of("dashboard:read", "audit:read")));
            Map<String, Object> globalOverview = service.dashboard("last7days");

            assertThat(metricsRepository.actorUserId).isNull();
            assertThat(globalOverview.get("scope")).isEqualTo("global");
            assertThat(globalOverview.get("recentActivities"))
                    .asList()
                    .hasSize(2);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    private static class InMemoryAuditRepository implements AuditRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final Map<Long, OperationLog> logs = new LinkedHashMap<>();

        @Override
        public OperationLog save(OperationLog log) {
            OperationLog saved = log.id() == null ? log.withId(ids.getAndIncrement()) : log;
            logs.put(saved.id(), saved);
            return saved;
        }

        @Override
        public Optional<OperationLog> findById(Long id) {
            return Optional.ofNullable(logs.get(id));
        }

        @Override
        public List<OperationLog> findPage(int page, int pageSize) {
            return new ArrayList<>(logs.values());
        }

        @Override
        public long count() {
            return logs.size();
        }

        @Override
        public List<OperationLog> findPageByActor(Long actorUserId, int page, int pageSize) {
            return logs.values().stream()
                    .filter(log -> actorUserId != null && actorUserId.equals(log.actorUserId()))
                    .toList();
        }

        @Override
        public long countByActor(Long actorUserId) {
            return findPageByActor(actorUserId, 1, Integer.MAX_VALUE).size();
        }
    }

    private static class InMemoryModelInvocationRepository implements ModelInvocationRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final Map<Long, ModelInvocationAudit> invocations = new LinkedHashMap<>();

        @Override
        public ModelInvocationAudit save(ModelInvocationAudit invocation) {
            Long id = invocation.id() == null ? ids.getAndIncrement() : invocation.id();
            ModelInvocationAudit saved = invocation.withId(id);
            ModelResponseAudit response = saved.response();
            if (response != null && response.invocationId() == null) {
                saved = saved.withResponse(response.withInvocationId(id));
            }
            invocations.put(id, saved);
            return saved;
        }

        @Override
        public Optional<ModelInvocationAudit> findById(Long id) {
            return Optional.ofNullable(invocations.get(id));
        }
    }

    private static class InMemoryDashboardMetricsRepository implements DashboardMetricsRepository {
        OffsetDateTime since;
        Long actorUserId;

        @Override
        public Map<String, Object> collect(OffsetDateTime since) {
            this.since = since;
            this.actorUserId = null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cards", Map.of(
                    "reportOutputs", 7L,
                    "knowledgeItems", 42L,
                    "activeDataSources", 3L,
                    "citationHitRate", 0.875,
                    "activeUsers", 5L
            ));
            result.put("reportTrend", List.of(Map.of("date", "2026-06-23", "completedReports", 2L)));
            result.put("knowledgeRank", List.of(Map.of("knowledgeBaseId", 1L, "name", "Finance KB", "references", 9L)));
            result.put("ruleScheduleHealth", Map.of(
                    "scheduledRules", 4L,
                    "failedScheduledRules", 2L,
                    "blockedScheduledRules", 1L,
                    "recentAlerts", 1L
            ));
            return result;
        }

        @Override
        public Map<String, Object> collect(OffsetDateTime since, Long actorUserId) {
            this.since = since;
            this.actorUserId = actorUserId;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cards", Map.of(
                    "reportOutputs", actorUserId == null ? 7L : 2L,
                    "knowledgeItems", actorUserId == null ? 42L : 6L,
                    "activeDataSources", actorUserId == null ? 3L : 1L,
                    "citationHitRate", actorUserId == null ? 0.875 : 0.5,
                    "activeUsers", actorUserId == null ? 5L : 1L
            ));
            result.put("reportTrend", List.of());
            result.put("knowledgeRank", List.of());
            result.put("ruleScheduleHealth", Map.of(
                    "scheduledRules", actorUserId == null ? 4L : 1L,
                    "failedScheduledRules", actorUserId == null ? 2L : 1L,
                    "blockedScheduledRules", actorUserId == null ? 1L : 0L,
                    "recentAlerts", actorUserId == null ? 1L : 0L
            ));
            return result;
        }
    }
}
