package com.company.report.report.application;

import com.company.report.audit.application.AuditApplicationService;
import com.company.report.audit.domain.model.ModelInvocationAudit;
import com.company.report.audit.domain.model.ModelResponseAudit;
import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.audit.domain.repository.ModelInvocationRepository;
import com.company.report.report.domain.model.ReportGenerationTask;
import com.company.report.report.domain.repository.ReportRepository;
import com.company.report.report.domain.repository.ReportContentRepository;
import com.company.report.report.domain.repository.ReportGenerationEventRepository;
import com.company.report.report.domain.repository.ReportExportFileRepository;
import com.company.report.report.domain.repository.ReportExportStorage;
import com.company.report.report.domain.repository.ReportGenerationTaskRepository;
import com.company.report.report.domain.repository.ReportTemplateRepository;
import com.company.report.report.infrastructure.persistence.InMemoryEnterpriseExportTemplateRepository;
import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.model.ReportStatus;
import com.company.report.report.domain.model.ReportTemplate;
import com.company.report.report.domain.service.ReportDomainService;
import com.company.report.shared.api.SseEvent;
import com.company.report.shared.event.DomainEventPublisher;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportApplicationServiceTest {
    @Test
    void listsActiveReportTemplatesWithFieldSchemaForTemplateFilling() {
        InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                new InMemoryAuditRepository(),
                new InMemoryModelInvocationRepository(),
                templateRepository
        );

        List<Map<String, Object>> templates = service.listReportTemplates();

        assertThat(templates)
                .singleElement()
                .satisfies(template -> {
                    assertThat(template)
                            .containsEntry("templateId", "enterprise-quarterly")
                            .containsEntry("name", "企业季度经营分析报告")
                            .containsEntry("version", "v1");
                    assertThat(template.get("fields"))
                            .asList()
                            .extracting(field -> String.valueOf(((Map<?, ?>) field).get("fieldKey")))
                            .containsExactlyElementsOf(List.of("period", "scope", "focus", "style"));
                });
    }

    @Test
    void managesEnterpriseExportTemplatesWithVersionedBrandSnapshots() {
        InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
        InMemoryEnterpriseExportTemplateRepository enterpriseTemplateRepository = new InMemoryEnterpriseExportTemplateRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                auditRepository,
                new InMemoryModelInvocationRepository(),
                templateRepository,
                enterpriseTemplateRepository
        );

        Map<String, Object> created = service.createEnterpriseExportTemplate(Map.of(
                "templateId", "enterprise-board",
                "name", "Board Pack Template",
                "brand", enterpriseBrand("Contoso Analytics", "#1f4e79")
        ));
        Map<String, Object> updated = service.updateEnterpriseExportTemplate("enterprise-board", Map.of(
                "name", "Board Pack Template v2",
                "brand", enterpriseBrand("Contoso Group", "#0f766e")
        ));
        Map<String, Object> disabled = service.disableEnterpriseExportTemplate("enterprise-board");
        Map<String, Object> enabled = service.enableEnterpriseExportTemplate("enterprise-board");

        assertThat(created)
                .containsEntry("templateId", "enterprise-board")
                .containsEntry("name", "Board Pack Template")
                .containsEntry("version", "v1")
                .containsEntry("status", "active");
        assertThat(updated)
                .containsEntry("templateId", "enterprise-board")
                .containsEntry("name", "Board Pack Template v2")
                .containsEntry("version", "v2")
                .containsEntry("status", "active");
        assertThat(disabled).containsEntry("status", "disabled");
        assertThat(enabled).containsEntry("status", "active");
        assertThat(service.listEnterpriseExportTemplates(1, 10, Map.of()).items())
                .extracting(item -> item.get("templateId"))
                .contains("enterprise-board");
        assertThat(service.listEnterpriseExportTemplateVersions("enterprise-board"))
                .extracting(version -> version.get("version"))
                .containsExactly("v2", "v1");
        assertThat(auditRepository.findPage(1, 20))
                .extracting(OperationLog::operationType)
                .contains(
                        "enterprise_export_template_created",
                        "enterprise_export_template_updated",
                        "enterprise_export_template_disabled",
                        "enterprise_export_template_enabled"
                );
    }

    @Test
    void usesManagedEnterpriseExportTemplateWhenBrandPayloadIsOmitted() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
        InMemoryEnterpriseExportTemplateRepository enterpriseTemplateRepository = new InMemoryEnterpriseExportTemplateRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                exportFileRepository,
                new InMemoryAuditRepository(),
                new InMemoryModelInvocationRepository(),
                templateRepository,
                enterpriseTemplateRepository
        );
        reportRepository.save(new Report(118L, "Managed template export report", 1L, ReportStatus.COMPLETED, 31L));
        contentRepository.saveCompletedVersion(118L, 31L, List.of(
                Map.of("heading", "Executive Summary", "content", "Managed template should supply brand fields.", "citations", List.of())
        ));
        service.createEnterpriseExportTemplate(Map.of(
                "templateId", "enterprise-board",
                "name", "Board Pack Template",
                "brand", enterpriseBrand("Contoso Analytics", "#1f4e79")
        ));

        Map<String, Object> exported = service.createExport(118L, Map.of(
                "format", "pdf",
                "templateId", "enterprise-board"
        ));

        assertThat(exported.get("brandSnapshot"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("templateId", "enterprise-board")
                .containsEntry("templateVersion", "v1")
                .containsEntry("companyName", "Contoso Analytics")
                .containsEntry("primaryColor", "#1F4E79");
        assertThat(exportStorage.lastContent).contains("Contoso Analytics");
    }

    @Test
    void rejectsTemplateTaskWhenRequiredSchemaFieldIsMissing() {
        InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                new InMemoryAuditRepository(),
                new InMemoryModelInvocationRepository(),
                templateRepository
        );

        assertThatThrownBy(() -> service.createTemplateTask("enterprise-quarterly", Map.of(
                "period", "2026Q1",
                "scope", "华东区",
                "style", "管理摘要"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template field required: focus");
    }

    @Test
    void rejectsTemplateTaskWhenSelectValueIsOutsideTemplateOptions() {
        InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                new InMemoryAuditRepository(),
                new InMemoryModelInvocationRepository(),
                templateRepository
        );

        assertThatThrownBy(() -> service.createTemplateTask("enterprise-quarterly", Map.of(
                "period", "2026Q1",
                "scope", "华东区",
                "focus", "收入与回款风险",
                "style", "随意发挥"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template field invalid option: style");
    }

    @Test
    void createsTemplateTaskWithTemplateMetadataAndValidatedParameterSnapshot() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        InMemoryTemplateRepository templateRepository = new InMemoryTemplateRepository();
        CapturingPublisher publisher = new CapturingPublisher();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                publisher,
                repository,
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                auditRepository,
                new InMemoryModelInvocationRepository(),
                templateRepository
        );

        Map<String, Object> created = service.createTemplateTask("enterprise-quarterly", Map.of(
                "period", "2026Q1",
                "scope", "华东区",
                "focus", "收入与回款风险",
                "style", "管理摘要"
        ));

        assertThat(created)
                .containsEntry("status", "outline_ready")
                .containsKey("templateSnapshot");
        assertThat(created.get("templateSnapshot"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("templateId", "enterprise-quarterly")
                .containsEntry("name", "企业季度经营分析报告")
                .containsEntry("version", "v1");
        assertThat(((Map<?, ?>) created.get("templateSnapshot")).get("parameters"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("period", "2026Q1")
                .containsEntry("style", "管理摘要");
        assertThat(repository.findById(Long.valueOf(created.get("taskId").toString())))
                .get()
                .extracting(ReportGenerationTask::templateSnapshot)
                .satisfies(snapshot -> assertThat(snapshot).containsEntry("templateId", "enterprise-quarterly"));
        assertThat(publisher.events).contains("report.generation.outline_ready");
        assertThat(auditRepository.findPage(1, 20))
                .filteredOn(log -> "report_generation_task_created".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("mode", "template")
                        .containsEntry("templateId", "enterprise-quarterly"));
    }

    @Test
    void createsDurableNaturalLanguageTasksWithUniqueIdsAndOutlineSnapshot() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        CapturingPublisher publisher = new CapturingPublisher();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        ReportApplicationService service = new ReportApplicationService(new ReportDomainService(), publisher, repository, new InMemoryReportRepository(), new InMemoryReportContentRepository(), new InMemoryGenerationEventRepository(), new InMemoryReportExportStorage(), new InMemoryReportExportFileRepository(), auditRepository);

        Map<String, Object> first = service.createGenerationTask(
                "quarter revenue",
                Map.of("period", "2026Q1", "focus", "revenue")
        );
        Map<String, Object> second = service.createGenerationTask(
                "risk review",
                Map.of("period", "2026Q2")
        );

        assertThat(first.get("taskId")).isNotEqualTo(second.get("taskId"));
        assertThat(first)
                .containsEntry("status", "outline_ready")
                .containsEntry("currentStage", "outline")
                .containsKey("outline");
        assertThat(repository.findById(Long.valueOf(first.get("taskId").toString())))
                .get()
                .extracting(ReportGenerationTask::status, ReportGenerationTask::currentStage, ReportGenerationTask::createdBy)
                .containsExactly("outline_ready", "outline", 1L);
        assertThat(publisher.events).contains("report.generation.outline_ready");
        assertThat(auditRepository.findPage(1, 10))
                .anySatisfy(log -> {
                    assertThat(log.actorUserId()).isEqualTo(1L);
                    assertThat(log.operationType()).isEqualTo("report_generation_task_created");
                    assertThat(log.resourceType()).isEqualTo("report_generation_task");
                    assertThat(log.resourceId()).isEqualTo(Long.valueOf(first.get("taskId").toString()));
                    assertThat(log.detail())
                            .containsEntry("topic", "quarter revenue")
                            .containsEntry("payload", Map.of("period", "2026Q1", "focus", "revenue"));
                });
    }

    @Test
    void confirmOutlineMovesTaskIntoRetrievalStage() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        CapturingPublisher publisher = new CapturingPublisher();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                publisher,
                repository,
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );
        Map<String, Object> created = service.createGenerationTask("cashflow report", Map.of("period", "2026Q1"));
        Long taskId = Long.valueOf(created.get("taskId").toString());

        Map<String, Object> confirmed = service.confirmOutline(taskId, Map.of(
                "confirmed", true,
                "outline", List.of("Executive summary", "Revenue analysis")
        ));

        assertThat(confirmed)
                .containsEntry("taskId", taskId)
                .containsEntry("status", "running")
                .containsEntry("nextStage", "retrieval");
        assertThat(repository.findById(taskId))
                .get()
                .extracting(ReportGenerationTask::status, ReportGenerationTask::currentStage, ReportGenerationTask::progress)
                .containsExactly("running", "retrieval", 10);
        assertThat(publisher.events).contains("report.generation.outline_confirmed");
        Map<String, Object> workerPayload = publisher.payloads.get(publisher.payloads.size() - 1);
        assertThat(workerPayload)
                .containsEntry("taskId", taskId)
                .containsEntry("reportId", confirmed.get("reportId"))
                .containsEntry("question", "cashflow report");
        assertThat(workerPayload.get("context"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("period", "2026Q1")
                .containsEntry("generationMode", "natural_language");
    }

    @Test
    void createExportAllowsStatusLookupAndDownloadUrl() {
        CapturingPublisher publisher = new CapturingPublisher();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        reportRepository.save(new Report(9L, "Exportable report", 1L, ReportStatus.COMPLETED, 1L));
        contentRepository.saveCompletedVersion(9L, 1L, List.of(Map.of("heading", "Summary", "content", "Export body", "citations", List.of())));
        ReportApplicationService service = new ReportApplicationService(new ReportDomainService(), publisher, new InMemoryTaskRepository(), reportRepository, contentRepository, new InMemoryGenerationEventRepository(), new InMemoryReportExportStorage(), new InMemoryReportExportFileRepository());

        Map<String, Object> created = service.createExport(9L, Map.of("format", "markdown", "templateId", "enterprise-default"));
        Map<String, Object> status = service.getExportStatus(9L, (Long) created.get("exportFileId"));

        assertThat(created)
                .containsEntry("reportId", 9L)
                .containsEntry("status", "completed")
                .containsEntry("downloadPolicy", "presigned_url");
        assertThat(status)
                .containsEntry("reportId", 9L)
                .containsEntry("exportFileId", created.get("exportFileId"))
                .containsEntry("status", "completed")
                .containsKey("downloadUrl")
                .containsKey("expiresAt");
        assertThat(status.get("downloadUrl").toString()).isEqualTo("/api/v1/files/report-exports/" + created.get("exportFileId") + "/download-url");
        assertThat(publisher.events).contains("report.export.requested");
    }

    @Test
    void getsReportFromRepositoryInsteadOfFixedDemoPayload() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        reportRepository.save(new Report(42L, "Real operations report", 1L, ReportStatus.DRAFT));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );

        Map<String, Object> report = service.getReport(42L);

        assertThat(report)
                .containsEntry("reportId", 42L)
                .containsEntry("title", "Real operations report")
                .containsEntry("status", "draft")
                .containsKey("sections");
    }

    @Test
    void listsReportsFromRepositoryPageInsteadOfFixedDemoPayload() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        reportRepository.save(new Report(201L, "First real report", 1L, ReportStatus.COMPLETED, 1L));
        reportRepository.save(new Report(202L, "Second real report", 1L, ReportStatus.DRAFT, null));
        reportRepository.save(new Report(203L, "Other owner report", 2L, ReportStatus.COMPLETED, 2L));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                new InMemoryReportContentRepository(),
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );

        var page = service.listReports(1, 10);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items())
                .extracting(item -> item.get("reportId"))
                .containsExactly(201L, 202L);
        assertThat(page.items())
                .extracting(item -> item.get("title"))
                .containsExactly("First real report", "Second real report");
    }

    @Test
    void completesTaskWithReportSectionsAndCurrentVersionSnapshot() {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        CapturingPublisher publisher = new CapturingPublisher();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                publisher,
                taskRepository,
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );
        ReportGenerationTask task = taskRepository.save(ReportGenerationTask.naturalLanguage(
                1L,
                "Operations analysis report",
                Map.of("period", "2026Q1"),
                "trace-complete-test"
        ).bindReport(88L));
        reportRepository.save(new Report(88L, "Operations analysis report", 1L, ReportStatus.DRAFT));

        Map<String, Object> completed = service.completeGenerationTask(task.id(), List.of(
                Map.of("heading", "Executive summary", "content", "Revenue growth stayed stable.", "citations", List.of()),
                Map.of("heading", "风险与建议", "content", "关注回款周期", "citations", List.of())
        ));
        Map<String, Object> report = service.getReport(88L);

        assertThat(completed)
                .containsEntry("taskId", task.id())
                .containsEntry("reportId", 88L)
                .containsEntry("status", "completed")
                .containsKey("versionId");
        assertThat(report)
                .containsEntry("status", "completed")
                .containsKey("currentVersionId");
        assertThat(report.get("sections"))
                .asList()
                .hasSize(2)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("heading", "Executive summary");
        assertThat(taskRepository.findById(task.id()))
                .get()
                .extracting(ReportGenerationTask::status, ReportGenerationTask::currentStage, ReportGenerationTask::progress)
                .containsExactly("completed", "export", 100);
        assertThat(publisher.events).contains("report.generation.completed");
    }

    @Test
    void completesTaskFromControlledAiCallbackWithReferencesAndModelAuditSummary() {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryGenerationEventRepository eventRepository = new InMemoryGenerationEventRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        InMemoryModelInvocationRepository modelInvocationRepository = new InMemoryModelInvocationRepository();
        CapturingPublisher publisher = new CapturingPublisher();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                publisher,
                taskRepository,
                reportRepository,
                contentRepository,
                eventRepository,
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                auditRepository,
                modelInvocationRepository
        );
        ReportGenerationTask task = taskRepository.save(ReportGenerationTask.naturalLanguage(
                1L,
                "AI callback report",
                Map.of("period", "2026Q1"),
                "trace-ai-callback"
        ).bindReport(89L).confirmOutline(Map.of("confirmed", true, "outline", List.of("Executive summary"))));
        reportRepository.save(new Report(89L, "AI callback report", 1L, ReportStatus.DRAFT));

        Map<String, Object> completed = service.completeGenerationTaskFromWorker(task.id(), Map.of(
                "sections", List.of(Map.of(
                        "heading", "Executive summary",
                        "content", "Receivables aging requires follow-up.",
                        "citations", List.of(Map.of(
                                "referenceId", 990004L,
                                "sourceTitle", "rag.txt",
                                "qualityScore", 0.92,
                                "snapshot", "Receivables aging requires follow-up and risk monitoring."
                        ))
                )),
                "references", List.of(Map.of(
                        "referenceId", 990004L,
                        "sourceTitle", "rag.txt",
                        "qualityScore", 0.92
                )),
                "modelInvocation", Map.ofEntries(
                        Map.entry("provider", "openai-compatible"),
                        Map.entry("model", "gpt"),
                        Map.entry("status", "succeeded"),
                        Map.entry("traceId", "trace-ai-callback"),
                        Map.entry("promptSnapshot", "Write an executive report"),
                        Map.entry("contextSnapshot", "Receivables aging requires follow-up."),
                        Map.entry("parameters", Map.of("temperature", 0.2)),
                        Map.entry("routingPolicy", Map.of(
                                "dimension", "tenant",
                                "key", "finance",
                                "env", "LLM_MODEL_ROUTE_TENANT_FINANCE",
                                "candidates", List.of("tenant-model", "gpt")
                        )),
                        Map.entry("durationMs", 1234L),
                        Map.entry("inputTokens", 321),
                        Map.entry("outputTokens", 654),
                        Map.entry("totalTokens", 975),
                        Map.entry("responseSummary", "Receivables aging requires follow-up."),
                        Map.entry("finishReason", "stop")
                )
        ));

        assertThat(completed)
                .containsEntry("taskId", task.id())
                .containsEntry("status", "completed")
                .containsKey("versionId");
        assertThat(service.getReport(89L).get("sections"))
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("content", "Receivables aging requires follow-up.");
        assertThat(eventRepository.findByTaskId(task.id()))
                .extracting(SseEvent::type)
                .containsSequence("stage", "delta", "references", "done");
        assertThat(eventRepository.findByTaskId(task.id()).stream()
                .filter(event -> "references".equals(event.type()))
                .findFirst())
                .get()
                .satisfies(event -> assertThat(event.references()).hasSize(1));
        assertThat(auditRepository.findPage(1, 20))
                .filteredOn(log -> "model_invocation".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.resourceType()).isEqualTo("report_generation_task");
                    assertThat(log.resourceId()).isEqualTo(task.id());
                    assertThat(log.detail()).containsEntry("model", "gpt");
                });
        assertThat(modelInvocationRepository.findByTaskId(task.id()))
                .get()
                .satisfies(invocation -> {
                    assertThat(invocation.reportId()).isEqualTo(89L);
                    assertThat(invocation.provider()).isEqualTo("openai-compatible");
                    assertThat(invocation.modelName()).isEqualTo("gpt");
                    assertThat(invocation.promptSnapshot()).isEqualTo("Write an executive report");
                    assertThat(invocation.contextSnapshot()).isEqualTo("Receivables aging requires follow-up.");
                    assertThat(invocation.parameters()).containsEntry("temperature", 0.2);
                    assertThat(invocation.parameters()).containsEntry("routingPolicy", Map.of(
                            "dimension", "tenant",
                            "key", "finance",
                            "env", "LLM_MODEL_ROUTE_TENANT_FINANCE",
                            "candidates", List.of("tenant-model", "gpt")
                    ));
                    assertThat(invocation.durationMs()).isEqualTo(1234L);
                    assertThat(invocation.inputTokens()).isEqualTo(321);
                    assertThat(invocation.outputTokens()).isEqualTo(654);
                    assertThat(invocation.totalTokens()).isEqualTo(975);
                    assertThat(invocation.traceId()).isEqualTo("trace-ai-callback");
                    assertThat(invocation.response().responseContent()).isEqualTo("Receivables aging requires follow-up.");
                    assertThat(invocation.response().finishReason()).isEqualTo("stop");
                });
        assertThat(publisher.events).contains("report.generation.completed");
    }

    @Test
    void streamsPersistedGenerationEventsInSequenceWhenAvailable() {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryGenerationEventRepository eventRepository = new InMemoryGenerationEventRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                taskRepository,
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                eventRepository,
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );
        ReportGenerationTask task = taskRepository.save(ReportGenerationTask.naturalLanguage(
                1L,
                "event stream report",
                Map.of("period", "2026Q1"),
                "trace-event-stream"
        ).confirmOutline(Map.of("confirmed", true, "outline", List.of("Executive summary"))));
        eventRepository.append(SseEvent.stage(task.id(), "retrieval", "retrieving evidence", 0.2));
        eventRepository.append(SseEvent.delta(task.id(), "section draft delta", "writing", 0.7));
        eventRepository.append(SseEvent.done(task.id()));

        List<SseEvent> events = service.streamEventsForTask(task.id());

        assertThat(events)
                .extracting(SseEvent::type)
                .containsExactly("stage", "delta", "done");
        assertThat(events.get(1).content()).isEqualTo("section draft delta");
        assertThat(events.get(1).stage()).isEqualTo("writing");
    }

    @Test
    void exportsCurrentReportVersionAsStoredMarkdownFile() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(99L, "Operations analysis report", 1L, ReportStatus.COMPLETED, 7L));
        contentRepository.saveCompletedVersion(99L, 1L, List.of(
                Map.of("heading", "Executive summary", "content", "Revenue growth stayed stable.", "citations", List.of("DOC-1")),
                Map.of("heading", "Risk recommendations", "content", "Monitor receivables cycle.", "citations", List.of())
        ));

        Map<String, Object> exported = service.createExport(99L, Map.of("format", "markdown", "templateId", "enterprise-default"));

        assertThat(exported)
                .containsEntry("reportId", 99L)
                .containsEntry("status", "completed")
                .containsEntry("format", "markdown")
                .containsEntry("contentType", "text/markdown; charset=UTF-8")
                .containsKey("objectKey")
                .containsKey("downloadUrl");
        assertThat((Long) exported.get("sizeBytes")).isGreaterThan(0L);
        assertThat(exportStorage.lastContent)
                .contains("# Operations analysis report")
                .contains("## Executive summary")
                .contains("Revenue growth stayed stable.")
                .contains("引用: DOC-1");
    }

    @Test
    void exportsCurrentReportVersionAsEnterpriseWordDocument() throws Exception {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(106L, "Enterprise Board Report", 1L, ReportStatus.COMPLETED, 20L));
        contentRepository.saveCompletedVersion(106L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Revenue grew by 12%.", "citations", List.of("citation-1")),
                Map.of("heading", "Risk Review", "content", "Monitor receivables aging.", "citations", List.of())
        ));

        Map<String, Object> exported = service.createExport(106L, Map.of(
                "format", "docx",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        assertThat(exported)
                .containsEntry("format", "docx")
                .containsEntry("contentType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(exported.get("fileName").toString()).endsWith(".docx");
        assertThat(exportStorage.lastBytes).startsWith(new byte[] { 'P', 'K' });
        String documentXml = unzipText(exportStorage.lastBytes, "word/document.xml");
        assertThat(documentXml).contains("Contoso Analytics");
        assertThat(documentXml).contains("Enterprise Board Report");
        assertThat(documentXml).contains("Table of Contents");
        assertThat(documentXml)
                .contains("w:fldSimple w:instr=\"TOC \\o &quot;1-3&quot; \\h \\z \\u\"")
                .contains("w:pStyle w:val=\"Heading1\"");
        assertThat(documentXml).contains("1. Executive Summary .... 2");
        assertThat(documentXml).contains("2. Risk Review .... 3");
        assertThat(documentXml)
                .contains("w:hyperlink w:anchor=\"section-1\"")
                .contains("w:hyperlink w:anchor=\"section-2\"")
                .contains("w:bookmarkStart w:id=\"1\" w:name=\"section-1\"")
                .contains("w:bookmarkStart w:id=\"2\" w:name=\"section-2\"");
        assertThat(documentXml).contains("1. Executive Summary");
        assertThat(documentXml).contains("2. Risk Review");
        assertThat(documentXml).contains("Executive Summary");
        assertThat(documentXml).contains("Revenue grew by 12%.");
        assertThat(documentXml).contains("References: citation-1");
        assertThat(documentXml)
                .contains("headerReference")
                .contains("footerReference");
        String headerXml = unzipText(exportStorage.lastBytes, "word/header1.xml");
        assertThat(headerXml)
                .contains("Confidential Board Report")
                .contains("Contoso Analytics");
        String footerXml = unzipText(exportStorage.lastBytes, "word/footer1.xml");
        assertThat(footerXml)
                .contains("Generated by Intelligent Report System")
                .contains("PAGE")
                .contains("NUMPAGES");
        assertThat(documentXml)
                .contains("<w:drawing>")
                .contains("r:embed=\"rIdLogo\"");
        assertThat(unzipText(exportStorage.lastBytes, "word/_rels/document.xml.rels"))
                .contains("rIdLogo")
                .contains("header1.xml")
                .contains("footer1.xml")
                .contains("relationships/image")
                .contains("media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "word/styles.xml"))
                .contains("w:styleId=\"Heading1\"")
                .contains("w:styleId=\"TOCHeading\"")
                .contains("w:rFonts w:ascii=\"Aptos\" w:hAnsi=\"Aptos\"")
                .contains("w:color w:val=\"1F4E79\"");
        assertThat(unzipText(exportStorage.lastBytes, "word/settings.xml"))
                .contains("w:updateFields w:val=\"true\"");
        assertThat(unzipText(exportStorage.lastBytes, "[Content_Types].xml"))
                .contains("image/svg+xml")
                .contains("/word/header1.xml")
                .contains("/word/footer1.xml")
                .contains("/word/styles.xml")
                .contains("/word/settings.xml");
        assertThat(unzipText(exportStorage.lastBytes, "word/media/logo.svg"))
                .contains("<svg")
                .contains("data-logo-object-key=\"branding/contoso-logo.png\"")
                .contains("companyName=Contoso Analytics")
                .contains("primaryColor=#1F4E79");
    }

    @Test
    void enterpriseWordExportEmbedsStoredLogoObjectWhenAvailable() throws Exception {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        byte[] logoBytes = """
                <svg xmlns="http://www.w3.org/2000/svg" width="120" height="40">
                  <text x="4" y="24">REAL-CONTOSO-LOGO</text>
                </svg>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exportStorage.objects.put("branding/contoso-logo.svg",
                new ReportExportStorage.StoredObject("test-bucket", "branding/contoso-logo.svg", "image/svg+xml", logoBytes));
        reportRepository.save(new Report(110L, "Enterprise Logo Report", 1L, ReportStatus.COMPLETED, 23L));
        contentRepository.saveCompletedVersion(110L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Logo must come from object storage.", "citations", List.of())
        ));

        service.createExport(110L, Map.of(
                "format", "docx",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.svg",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        assertThat(exportStorage.readKeys).containsExactly("branding/contoso-logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "word/media/logo.svg"))
                .contains("REAL-CONTOSO-LOGO")
                .doesNotContain("companyName=Contoso Analytics");
    }

    @Test
    void exportsCurrentReportVersionAsPdfDocument() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(108L, "Quarterly Operations Report", 1L, ReportStatus.COMPLETED, 21L));
        contentRepository.saveCompletedVersion(108L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Revenue grew by 12%.", "citations", List.of("citation-1")),
                Map.of("heading", "Risk Review", "content", "Monitor receivables aging.", "citations", List.of())
        ));

        Map<String, Object> exported = service.createExport(108L, Map.of(
                "format", "pdf",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        assertThat(exported)
                .containsEntry("format", "pdf")
                .containsEntry("contentType", "application/pdf");
        assertThat(exported.get("fileName").toString()).endsWith(".pdf");
        assertThat(exportStorage.lastBytes).startsWith(new byte[] { '%', 'P', 'D', 'F', '-' });
        assertThat(new String(exportStorage.lastBytes, java.nio.charset.StandardCharsets.ISO_8859_1))
                .contains("Confidential Board Report")
                .contains("Quarterly Operations Report")
                .contains("Table of Contents")
                .contains("1. Executive Summary .... 1")
                .contains("2. Risk Review .... 1")
                .contains("1. Executive Summary")
                .contains("2. Risk Review")
                .contains("Executive Summary")
                .contains("Revenue grew by 12%.")
                .contains("References: citation-1")
                .contains("PDF-LOGO")
                .contains("logoObjectKey=branding/contoso-logo.png")
                .contains("Contoso Analytics")
                .contains("0.122 0.306 0.475 rg")
                .contains("%% PDF-HEADER")
                .contains("%% PDF-BODY")
                .contains("%% PDF-FOOTER")
                .contains("%% PDF-PAGE 1")
                .contains("50 742 Td")
                .contains("50 690 Td")
                .contains("50 70 Td")
                .contains("Generated by Intelligent Report System")
                .contains("Page 1 of 1")
                .contains("%%EOF");
    }

    @Test
    void enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        byte[] pngLogoBytes = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0S8AAAAASUVORK5CYII="
        );
        exportStorage.objects.put("branding/contoso-logo.png",
                new ReportExportStorage.StoredObject("test-bucket", "branding/contoso-logo.png", "image/png", pngLogoBytes));
        reportRepository.save(new Report(111L, "Quarterly Operations Report", 1L, ReportStatus.COMPLETED, 24L));
        contentRepository.saveCompletedVersion(111L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Revenue grew by 12%.", "citations", List.of("citation-1"))
        ));

        service.createExport(111L, Map.of(
                "format", "pdf",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        String pdf = new String(exportStorage.lastBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(exportStorage.readKeys).containsExactly("branding/contoso-logo.png");
        assertThat(pdf)
                .contains("/Subtype /Image")
                .contains("/Filter /DCTDecode")
                .contains("/Im1")
                .contains("PDF-LOGO-BINARY logoObjectKey=branding/contoso-logo.png");
        assertThat(pdf)
                .doesNotContain("PDF-LOGO logoObjectKey=branding/contoso-logo.png companyName=Contoso Analytics");
    }

    @Test
    void enterprisePdfExportRendersStoredSvgLogoAsPdfVectorObjectWhenAvailable() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        byte[] svgLogoBytes = """
                <svg xmlns="http://www.w3.org/2000/svg" width="120" height="40">
                  <rect width="120" height="40" fill="#1F4E79"/>
                  <text x="8" y="24">REAL-CONTOSO-LOGO</text>
                </svg>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exportStorage.objects.put("branding/contoso-logo.svg",
                new ReportExportStorage.StoredObject("test-bucket", "branding/contoso-logo.svg", "image/svg+xml", svgLogoBytes));
        reportRepository.save(new Report(113L, "Quarterly Operations Report", 1L, ReportStatus.COMPLETED, 24L));
        contentRepository.saveCompletedVersion(113L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Revenue grew by 12%.", "citations", List.of("citation-1"))
        ));

        service.createExport(113L, Map.of(
                "format", "pdf",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.svg",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        String pdf = new String(exportStorage.lastBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(exportStorage.readKeys).containsExactly("branding/contoso-logo.svg");
        assertThat(pdf)
                .contains("/Subtype /Form")
                .contains("/Im1")
                .contains("REAL-CONTOSO-LOGO")
                .contains("PDF-LOGO-VECTOR logoObjectKey=branding/contoso-logo.svg");
        assertThat(pdf)
                .doesNotContain("PDF-LOGO logoObjectKey=branding/contoso-logo.svg companyName=Contoso Analytics");
    }

    @Test
    void exportsPdfAcrossMultiplePagesWhenBodyIsLong() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(112L, "Long Enterprise Report", 1L, ReportStatus.COMPLETED, 25L));
        List<Map<String, Object>> sections = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            sections.add(Map.of(
                    "heading", "Section " + index,
                    "content", "This is a long enterprise report paragraph for section " + index + ".",
                    "citations", List.of("citation-" + index)
            ));
        }
        contentRepository.saveCompletedVersion(112L, 1L, sections);

        service.createExport(112L, Map.of(
                "format", "pdf",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        String pdf = new String(exportStorage.lastBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(pdf)
                .contains("/Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3")
                .contains("%% PDF-PAGE 1")
                .contains("%% PDF-PAGE 2")
                .contains("%% PDF-PAGE 3")
                .contains("1. Section 1 .... 1")
                .contains("4. Section 4 .... 2")
                .contains("12. Section 12 .... 3")
                .doesNotContain("12. Section 12 .... 13")
                .contains("Page 1 of 3")
                .contains("Page 2 of 3")
                .contains("Page 3 of 3")
                .contains("1. Section 1")
                .contains("12. Section 12");
    }

    @Test
    void exportsCurrentReportVersionAsPowerPointDocument() throws Exception {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(109L, "Quarterly Board Presentation", 1L, ReportStatus.COMPLETED, 22L));
        contentRepository.saveCompletedVersion(109L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Revenue grew by 12%.", "citations", List.of("citation-1")),
                Map.of("heading", "Risk Review", "content", "Monitor receivables aging.", "citations", List.of())
        ));

        Map<String, Object> exported = service.createExport(109L, Map.of(
                "format", "pptx",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        assertThat(exported)
                .containsEntry("format", "pptx")
                .containsEntry("contentType", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        assertThat(exported.get("fileName").toString()).endsWith(".pptx");
        assertThat(exportStorage.lastBytes).startsWith(new byte[] { 'P', 'K' });
        String slide1Xml = unzipText(exportStorage.lastBytes, "ppt/slides/slide1.xml");
        assertThat(slide1Xml)
                .contains("Contoso Analytics")
                .contains("Confidential Board Report")
                .contains("Quarterly Board Presentation")
                .contains("typeface=\"Aptos\"")
                .contains("val=\"1F4E79\"")
                .contains("Cover")
                .doesNotContain("Table of Contents")
                .contains("Generated by Intelligent Report System")
                .contains("Page 1 of 5")
                .contains("name=\"Header\"")
                .contains("name=\"Footer\"")
                .contains("name=\"Body\"")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"")
                .contains("r:embed=\"rIdLogo\"");
        assertThat(slide1Xml)
                .contains("<p:cNvPr id=\"3\" name=\"Body\"")
                .contains("<p:cNvPr id=\"5\" name=\"Header\"")
                .contains("<p:cNvPr id=\"6\" name=\"Footer\"");
        String slide2Xml = unzipText(exportStorage.lastBytes, "ppt/slides/slide2.xml");
        assertThat(slide2Xml)
                .contains("Table of Contents")
                .contains("1. Executive Summary .... 3")
                .contains("2. Risk Review .... 4")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"")
                .contains("Page 2 of 5");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide3.xml"))
                .contains("1. Executive Summary")
                .contains("Executive Summary")
                .contains("Revenue grew by 12%.")
                .contains("References: citation-1")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"")
                .contains("Page 3 of 5");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide4.xml"))
                .contains("2. Risk Review")
                .contains("Risk Review")
                .contains("Monitor receivables aging.")
                .contains("Page 4 of 5");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide5.xml"))
                .contains("Closing")
                .contains("Contoso Analytics")
                .contains("Generated by Intelligent Report System")
                .contains("Page 5 of 5");
        assertThat(unzipText(exportStorage.lastBytes, "[Content_Types].xml"))
                .contains("presentationml.presentation.main+xml")
                .contains("presentationml.slide+xml")
                .contains("image/svg+xml")
                .contains("/ppt/slideMasters/slideMaster1.xml")
                .contains("/ppt/slideLayouts/slideLayout1.xml")
                .contains("/ppt/theme/theme1.xml")
                .contains("/ppt/slides/slide2.xml")
                .contains("/ppt/slides/slide3.xml")
                .contains("/ppt/slides/slide4.xml")
                .contains("/ppt/slides/slide5.xml");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/presentation.xml"))
                .contains("p:sldMasterIdLst")
                .contains("r:id=\"rIdMaster1\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/_rels/presentation.xml.rels"))
                .contains("Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\"")
                .contains("Target=\"slideMasters/slideMaster1.xml\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slideMasters/slideMaster1.xml"))
                .contains("p:sldMaster")
                .contains("r:id=\"rIdLayout1\"")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slideMasters/_rels/slideMaster1.xml.rels"))
                .contains("Target=\"../slideLayouts/slideLayout1.xml\"")
                .contains("Target=\"../theme/theme1.xml\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slideLayouts/slideLayout1.xml"))
                .contains("p:sldLayout")
                .contains("type=\"titleOnly\"")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/theme/theme1.xml"))
                .contains("a:theme")
                .contains("Office Theme");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/_rels/slide1.xml.rels"))
                .contains("slideLayout")
                .contains("rIdLogo")
                .contains("relationships/image")
                .contains("../media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/_rels/slide2.xml.rels"))
                .contains("slideLayout")
                .contains("rIdLogo")
                .contains("../media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/_rels/slide3.xml.rels"))
                .contains("slideLayout")
                .contains("rIdLogo")
                .contains("../media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/_rels/slide5.xml.rels"))
                .contains("slideLayout")
                .contains("rIdLogo")
                .contains("../media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/media/logo.svg"))
                .contains("<svg")
                .contains("data-logo-object-key=\"branding/contoso-logo.png\"")
                .contains("companyName=Contoso Analytics")
                .contains("primaryColor=#1F4E79");
    }

    @Test
    void exportsPowerPointAsOverviewAndSectionSlides() throws Exception {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(114L, "Quarterly Board Presentation", 1L, ReportStatus.COMPLETED, 27L));
        contentRepository.saveCompletedVersion(114L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Revenue grew by 12%.", "citations", List.of("citation-1")),
                Map.of("heading", "Risk Review", "content", "Monitor receivables aging.", "citations", List.of("citation-2"))
        ));

        service.createExport(114L, Map.of(
                "format", "pptx",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79"
                )
        ));

        assertThat(unzipText(exportStorage.lastBytes, "[Content_Types].xml"))
                .contains("/ppt/slides/slide1.xml")
                .contains("/ppt/slides/slide2.xml")
                .contains("/ppt/slides/slide3.xml")
                .contains("/ppt/slideMasters/slideMaster1.xml")
                .contains("/ppt/slideLayouts/slideLayout1.xml")
                .contains("/ppt/theme/theme1.xml")
                .contains("/ppt/slides/slide4.xml")
                .contains("/ppt/slides/slide5.xml");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/presentation.xml"))
                .contains("r:id=\"rIdMaster1\"")
                .contains("r:id=\"rId1\"")
                .contains("r:id=\"rId2\"")
                .contains("r:id=\"rId3\"")
                .contains("r:id=\"rId4\"")
                .contains("r:id=\"rId5\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/_rels/presentation.xml.rels"))
                .contains("Target=\"slideMasters/slideMaster1.xml\"")
                .contains("Target=\"slides/slide1.xml\"")
                .contains("Target=\"slides/slide2.xml\"")
                .contains("Target=\"slides/slide3.xml\"")
                .contains("Target=\"slides/slide4.xml\"")
                .contains("Target=\"slides/slide5.xml\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide1.xml"))
                .contains("Cover")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"")
                .doesNotContain("Table of Contents");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide2.xml"))
                .contains("Table of Contents")
                .contains("1. Executive Summary .... 3")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"")
                .contains("2. Risk Review .... 4");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide3.xml"))
                .contains("1. Executive Summary")
                .contains("Revenue grew by 12%.")
                .contains("References: citation-1")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"")
                .doesNotContain("2. Risk Review");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide4.xml"))
                .contains("2. Risk Review")
                .contains("Monitor receivables aging.")
                .contains("References: citation-2")
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"")
                .doesNotContain("1. Executive Summary");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/slide5.xml"))
                .contains("Closing")
                .contains("Contoso Analytics")
                .contains("Generated by Intelligent Report System")
                .contains("Page 5 of 5");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/_rels/slide3.xml.rels"))
                .contains("slideLayout")
                .contains("rIdLogo")
                .contains("../media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/_rels/slide4.xml.rels"))
                .contains("slideLayout")
                .contains("rIdLogo")
                .contains("../media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slides/_rels/slide5.xml.rels"))
                .contains("slideLayout")
                .contains("rIdLogo")
                .contains("../media/logo.svg");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slideMasters/slideMaster1.xml"))
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"");
        assertThat(unzipText(exportStorage.lastBytes, "ppt/slideLayouts/slideLayout1.xml"))
                .contains("type=\"title\"")
                .contains("type=\"body\"")
                .contains("type=\"ftr\"");
    }

    @Test
    void exportsPowerPointClosingSlideFromEnterpriseLayout() throws Exception {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(115L, "Customer Delivery Presentation", 1L, ReportStatus.COMPLETED, 28L));
        contentRepository.saveCompletedVersion(115L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Ready for customer handoff.", "citations", List.of())
        ));

        service.createExport(115L, Map.of(
                "format", "pptx",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1F4E79",
                        "layout", Map.of(
                                "closingTitle", "Thank You",
                                "closingMessage", "For board review and next-step alignment.",
                                "closingContact", "Contact: strategy-office@example.com"
                        )
                )
        ));

        String closingSlideXml = unzipText(exportStorage.lastBytes, "ppt/slides/slide4.xml");
        assertThat(closingSlideXml)
                .contains("Thank You")
                .contains("For board review and next-step alignment.")
                .contains("Contact: strategy-office@example.com")
                .contains("Contoso Analytics")
                .contains("Page 4 of 4")
                .doesNotContain(">Closing<");
    }

    @Test
    void normalizesBrandThemeConsistentlyAcrossWordPdfAndPowerPointExports() throws Exception {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(113L, "Normalized Theme Report", 1L, ReportStatus.COMPLETED, 26L));
        contentRepository.saveCompletedVersion(113L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Theme normalization should be shared.", "citations", List.of())
        ));
        Map<String, Object> brand = Map.of(
                "companyName", "Contoso Analytics",
                "logoObjectKey", "branding/contoso-logo.png",
                "header", "Confidential Board Report",
                "footer", "Generated by Intelligent Report System",
                "fontFamily", "Aptos",
                "primaryColor", "#1f4e79"
        );

        service.createExport(113L, Map.of(
                "format", "docx",
                "templateId", "enterprise-board",
                "brand", brand
        ));
        String wordStyles = unzipText(exportStorage.lastBytes, "word/styles.xml");
        String wordLogo = unzipText(exportStorage.lastBytes, "word/media/logo.svg");

        service.createExport(113L, Map.of(
                "format", "pdf",
                "templateId", "enterprise-board",
                "brand", brand
        ));
        String pdf = new String(exportStorage.lastBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        service.createExport(113L, Map.of(
                "format", "pptx",
                "templateId", "enterprise-board",
                "brand", brand
        ));
        String slideXml = unzipText(exportStorage.lastBytes, "ppt/slides/slide1.xml");
        String pptLogo = unzipText(exportStorage.lastBytes, "ppt/media/logo.svg");

        assertThat(wordStyles).contains("w:color w:val=\"1F4E79\"");
        assertThat(wordLogo).contains("primaryColor=#1F4E79");
        assertThat(pdf).contains("0.122 0.306 0.475 rg");
        assertThat(slideXml).contains("val=\"1F4E79\"");
        assertThat(pptLogo).contains("primaryColor=#1F4E79");
    }

    @Test
    void appliesSharedEnterpriseLayoutAcrossWordPdfAndPowerPointExports() throws Exception {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(114L, "Shared Layout Report", 1L, ReportStatus.COMPLETED, 27L));
        contentRepository.saveCompletedVersion(114L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Shared layout should shape all exports.", "citations", List.of("citation-layout"))
        ));
        Map<String, Object> brand = Map.of(
                "companyName", "Contoso Analytics",
                "logoObjectKey", "branding/contoso-logo.png",
                "header", "Confidential Board Report",
                "footer", "Generated by Intelligent Report System",
                "fontFamily", "Aptos",
                "primaryColor", "#1f4e79",
                "layout", Map.of(
                        "coverTitle", "Board Strategy Pack",
                        "tocTitle", "Report Outline",
                        "bodyTitlePrefix", "Section",
                        "bodyFontSize", 22,
                        "titleFontSize", 30,
                        "headerFontSize", 16,
                        "footerFontSize", 12,
                        "pageWidth", 520
                )
        );

        service.createExport(114L, Map.of(
                "format", "docx",
                "templateId", "enterprise-board",
                "brand", brand
        ));
        String wordStyles = unzipText(exportStorage.lastBytes, "word/styles.xml");
        String wordDocument = unzipText(exportStorage.lastBytes, "word/document.xml");

        service.createExport(114L, Map.of(
                "format", "pdf",
                "templateId", "enterprise-board",
                "brand", brand
        ));
        String pdf = new String(exportStorage.lastBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        service.createExport(114L, Map.of(
                "format", "pptx",
                "templateId", "enterprise-board",
                "brand", brand
        ));
        String coverSlide = unzipText(exportStorage.lastBytes, "ppt/slides/slide1.xml");
        String tocSlide = unzipText(exportStorage.lastBytes, "ppt/slides/slide2.xml");
        String sectionSlide = unzipText(exportStorage.lastBytes, "ppt/slides/slide3.xml");

        assertThat(wordStyles)
                .contains("w:styleId=\"Title\"")
                .contains("w:styleId=\"Header\"")
                .contains("w:styleId=\"Footer\"")
                .contains("w:sz w:val=\"60\"")
                .contains("w:sz w:val=\"44\"");
        assertThat(wordDocument)
                .contains("Board Strategy Pack")
                .contains("Report Outline")
                .contains("Section 1. Executive Summary");
        assertThat(pdf)
                .contains("Board Strategy Pack")
                .contains("Report Outline")
                .contains("Section 1. Executive Summary");
        assertThat(coverSlide)
                .contains("Board Strategy Pack")
                .contains("sz=\"3000\"");
        assertThat(tocSlide)
                .contains("Report Outline")
                .contains("sz=\"2200\"");
        assertThat(sectionSlide)
                .contains("Section 1. Executive Summary")
                .contains("sz=\"2200\"");
    }

    @Test
    void rejectsWordExportWhenEnterpriseBrandTemplateIsIncomplete() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(107L, "Brand required report", 1L, ReportStatus.COMPLETED, 21L));
        contentRepository.saveCompletedVersion(107L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Body", "citations", List.of())
        ));

        assertThatThrownBy(() -> service.createExport(107L, Map.of(
                "format", "word",
                "templateId", "enterprise-board",
                "brand", Map.of("companyName", "Contoso Analytics")
        )))
                .isInstanceOf(com.company.report.shared.error.BusinessException.class)
                .hasMessageContaining("企业导出模板缺失或配置异常");
    }

    @Test
    void rejectsPowerPointExportWhenEnterpriseBrandTemplateIsIncomplete() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );
        reportRepository.save(new Report(116L, "PPT brand required report", 1L, ReportStatus.COMPLETED, 29L));
        contentRepository.saveCompletedVersion(116L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Body", "citations", List.of())
        ));

        assertThatThrownBy(() -> service.createExport(116L, Map.of(
                "format", "pptx",
                "templateId", "enterprise-board",
                "brand", Map.of("companyName", "Contoso Analytics")
        )))
                .isInstanceOf(com.company.report.shared.error.BusinessException.class);
    }

    @Test
    void persistsEnterpriseExportBrandSnapshotForAuditAndStatusLookup() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                exportFileRepository
        );
        reportRepository.save(new Report(117L, "Auditable export template report", 1L, ReportStatus.COMPLETED, 30L));
        contentRepository.saveCompletedVersion(117L, 1L, List.of(
                Map.of("heading", "Executive Summary", "content", "Template snapshot must be auditable.", "citations", List.of())
        ));

        Map<String, Object> exported = service.createExport(117L, Map.of(
                "format", "pptx",
                "templateId", "enterprise-board",
                "brand", Map.of(
                        "companyName", "Contoso Analytics",
                        "logoObjectKey", "branding/contoso-logo.png",
                        "header", "Confidential Board Report",
                        "footer", "Generated by Intelligent Report System",
                        "fontFamily", "Aptos",
                        "primaryColor", "#1f4e79",
                        "layout", Map.of(
                                "coverTitle", "Board Strategy Pack",
                                "tocTitle", "Report Outline",
                                "bodyTitlePrefix", "Section",
                                "titleFontSize", 30,
                                "bodyFontSize", 22
                        )
                )
        ));

        Map<String, Object> status = service.getExportStatus(117L, ((Number) exported.get("exportFileId")).longValue());
        assertThat(status).containsKey("brandSnapshot");
        assertThat(status.get("brandSnapshot"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("templateId", "enterprise-board")
                .containsEntry("format", "pptx")
                .containsEntry("companyName", "Contoso Analytics")
                .containsEntry("logoObjectKey", "branding/contoso-logo.png")
                .containsEntry("fontFamily", "Aptos")
                .containsEntry("primaryColor", "#1F4E79");
        assertThat(((Map<?, ?>) status.get("brandSnapshot")).get("layout"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("coverTitle", "Board Strategy Pack")
                .containsEntry("tocTitle", "Report Outline")
                .containsEntry("bodyTitlePrefix", "Section")
                .containsEntry("titleFontSize", 30)
                .containsEntry("bodyFontSize", 22)
                .containsEntry("headerFontSize", 14)
                .containsEntry("footerFontSize", 12);
        assertThat(exportFileRepository.findByExportFileId(((Number) exported.get("exportFileId")).longValue()))
                .get()
                .extracting(file -> file.get("brandSnapshot"))
                .isEqualTo(status.get("brandSnapshot"));
    }

    @Test
    void readsExportStatusFromPersistentRepositoryAfterServiceRestart() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        reportRepository.save(new Report(100L, "Restart safe export", 1L, ReportStatus.COMPLETED, 8L));
        contentRepository.saveCompletedVersion(100L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Persistent export", "citations", List.of())
        ));
        ReportApplicationService firstService = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                exportFileRepository
        );

        Map<String, Object> created = firstService.createExport(100L, Map.of("format", "markdown", "templateId", "enterprise-default"));
        ReportApplicationService restartedService = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                exportFileRepository
        );

        Map<String, Object> status = restartedService.getExportStatus(100L, (Long) created.get("exportFileId"));

        assertThat(status)
                .containsEntry("reportId", 100L)
                .containsEntry("exportFileId", created.get("exportFileId"))
                .containsEntry("status", "completed")
                .containsEntry("objectKey", created.get("objectKey"))
                .containsEntry("downloadUrl", created.get("downloadUrl"));
    }

    @Test
    void listsVersionsAndRollsBackCurrentReportVersion() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        reportRepository.save(new Report(204L, "Versioned report", 1L, ReportStatus.COMPLETED, null));
        Long firstVersion = contentRepository.saveCompletedVersion(204L, 1L, List.of(
                Map.of("heading", "V1", "content", "first", "citations", List.of())
        ));
        Long secondVersion = contentRepository.saveCompletedVersion(204L, 1L, List.of(
                Map.of("heading", "V2", "content", "second", "citations", List.of())
        ));
        contentRepository.markCurrentVersion(204L, secondVersion);
        reportRepository.save(new Report(204L, "Versioned report", 1L, ReportStatus.COMPLETED, secondVersion));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );

        List<Map<String, Object>> versions = service.listVersions(204L);
        Map<String, Object> rollback = service.rollbackVersion(204L, firstVersion);

        assertThat(versions)
                .extracting(version -> version.get("versionId"))
                .containsExactly(secondVersion, firstVersion);
        assertThat(versions.get(0)).containsEntry("current", true);
        assertThat(versions.get(1)).containsEntry("current", false);
        Long newVersionId = ((Number) rollback.get("newVersionId")).longValue();
        assertThat(rollback)
                .containsEntry("reportId", 204L)
                .containsEntry("sourceVersionId", firstVersion)
                .containsEntry("currentVersionId", newVersionId)
                .containsEntry("changeReason", "rollback");
        assertThat(newVersionId).isNotEqualTo(firstVersion).isNotEqualTo(secondVersion);
        assertThat(reportRepository.findById(204L))
                .get()
                .extracting(Report::currentVersionId)
                .isEqualTo(newVersionId);
        assertThat(service.listVersions(204L))
                .extracting(version -> version.get("versionId"))
                .containsExactly(newVersionId, secondVersion, firstVersion);
        assertThat(service.listVersions(204L))
                .extracting(version -> version.get("current"))
                .containsExactly(true, false, false);
    }

    @Test
    void comparesReportVersionsBySectionSnapshot() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        reportRepository.save(new Report(304L, "Comparable report", 1L, ReportStatus.COMPLETED, null));
        Long baseVersion = contentRepository.saveCompletedVersion(304L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Revenue grew 8%.", "citations", List.of()),
                Map.of("heading", "Risk", "content", "No material risk.", "citations", List.of())
        ));
        Long targetVersion = contentRepository.saveCompletedVersion(304L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Revenue grew 12%.", "citations", List.of()),
                Map.of("heading", "Outlook", "content", "Expansion planned.", "citations", List.of())
        ));
        reportRepository.save(new Report(304L, "Comparable report", 1L, ReportStatus.COMPLETED, targetVersion));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );

        Map<String, Object> diff = service.compareVersions(304L, baseVersion, targetVersion);

        assertThat(diff)
                .containsEntry("reportId", 304L)
                .containsEntry("baseVersionId", baseVersion)
                .containsEntry("targetVersionId", targetVersion);
        assertThat(diff.get("summary"))
                .isEqualTo(Map.of("added", 1, "removed", 1, "modified", 1, "unchanged", 0));
        assertThat((List<Map<String, Object>>) diff.get("changes"))
                .extracting(change -> change.get("changeType"))
                .containsExactly("modified", "removed", "added");
    }

    @Test
    void getsReferenceFromCurrentReportCitationMarksInsteadOfFixedDemoScore() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("referenceId", 77L);
        citation.put("sourceTitle", "East region sales dataset");
        citation.put("sourceType", "knowledge_document");
        citation.put("snapshot", "Revenue increased 12% year over year.");
        citation.put("score", Map.of("credibility", 0.92, "citationQuality", 0.88));
        citation.put("anchor", Map.of("sectionNo", 1, "heading", "Summary", "text", "Revenue increased"));
        reportRepository.save(new Report(205L, "Referenced report", 1L, ReportStatus.COMPLETED, null));
        Long versionId = contentRepository.saveCompletedVersion(205L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Revenue increased 12% year over year.", "citations", List.of(citation))
        ));
        reportRepository.save(new Report(205L, "Referenced report", 1L, ReportStatus.COMPLETED, versionId));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );

        Map<String, Object> reference = service.getReference(205L, 77L);

        assertThat(reference)
                .containsEntry("reportId", 205L)
                .containsEntry("referenceId", 77L)
                .containsEntry("sourceTitle", "East region sales dataset")
                .containsEntry("sourceType", "knowledge_document")
                .containsEntry("snapshot", "Revenue increased 12% year over year.")
                .containsEntry("score", Map.of("credibility", 0.92, "citationQuality", 0.88))
                .containsEntry("anchor", Map.of("sectionNo", 1, "heading", "Summary", "text", "Revenue increased"));
        assertThat(reference).doesNotContainKey("qualityScore");
    }

    @Test
    void rejectsReferenceLookupWhenReportBelongsToAnotherUser() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        reportRepository.save(new Report(206L, "Private referenced report", 99L, ReportStatus.COMPLETED, null));
        Long versionId = contentRepository.saveCompletedVersion(206L, 99L, List.of(
                Map.of("heading", "Summary", "content", "Private evidence", "citations", List.of(
                        Map.of("referenceId", 88L, "sourceTitle", "Private source")
                ))
        ));
        reportRepository.save(new Report(206L, "Private referenced report", 99L, ReportStatus.COMPLETED, versionId));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );

        try {
            CurrentUserHolder.set(new CurrentUser(100L, Set.of("analyst"), Set.of("report:read")));

            assertThatThrownBy(() -> service.getReference(206L, 88L))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report reference access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void recordsReportExportOperationInAuditLog() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(101L, "Audited export", 1L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(101L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Audit export body", "citations", List.of())
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                auditRepository
        );

        Map<String, Object> exported = service.createExport(101L, Map.of("format", "markdown", "templateId", "enterprise-default"));
        List<Map<String, Object>> logs = new AuditApplicationService(auditRepository).auditLogs(1, 20).items();

        assertThat(logs)
                .anySatisfy(log -> assertThat(log)
                        .containsEntry("operationType", "report_export")
                        .containsEntry("resourceType", "report")
                        .containsEntry("resourceId", 101L)
                        .containsEntry("result", "succeeded")
                      .containsEntry("actorUserId", 1L)
                      .containsEntry("exportFileId", exported.get("exportFileId")));
    }

    @Test
    void rejectsExportWhenReportBelongsToAnotherUser() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(105L, "Private export report", 77L, ReportStatus.COMPLETED, 13L));
        contentRepository.saveCompletedVersion(105L, 77L, List.of(
                Map.of("heading", "Summary", "content", "Private export body", "citations", List.of())
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                exportFileRepository,
                auditRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(78L, Set.of("analyst"), Set.of("report:export")));

            assertThatThrownBy(() -> service.createExport(105L, Map.of("format", "markdown", "templateId", "enterprise-default")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report export access denied");
            assertThat(exportStorage.storeCount()).isZero();
            assertThat(exportFileRepository.count()).isZero();
            assertThat(auditRepository.count()).isZero();
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void marksGenerationTaskFailedWithPersistedErrorEventAndAllowsRetry() {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        InMemoryGenerationEventRepository eventRepository = new InMemoryGenerationEventRepository();
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                taskRepository,
                new InMemoryReportRepository(),
                new InMemoryReportContentRepository(),
                eventRepository,
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository()
        );
        ReportGenerationTask task = taskRepository.save(ReportGenerationTask.naturalLanguage(
                1L,
                "retryable report",
                Map.of("period", "2026Q1"),
                "trace-retryable"
        ).confirmOutline(Map.of("confirmed", true, "outline", List.of("Summary"))));

        Map<String, Object> failed = service.failGenerationTask(task.id(), Map.of(
                "errorCode", "AI_MODEL_UNAVAILABLE",
                "message", "AI service unavailable"
        ));
        Map<String, Object> retried = service.retryGenerationTask(task.id(), Map.of("reason", "manual_retry"));

        assertThat(failed)
                .containsEntry("taskId", task.id())
                .containsEntry("status", "retryable")
                .containsEntry("currentStage", "retrieval")
                .containsEntry("failureReason", "AI service unavailable");
        assertThat(retried)
                .containsEntry("taskId", task.id())
                .containsEntry("status", "running")
                .containsEntry("currentStage", "retrieval")
                .containsEntry("progress", 10);
        assertThat(taskRepository.findById(task.id()))
                .get()
                .extracting(ReportGenerationTask::status, ReportGenerationTask::failureReason)
                .containsExactly("running", null);
        assertThat(eventRepository.findByTaskId(task.id()))
                .extracting(SseEvent::type)
                .containsExactly("error", "stage");
        assertThat(eventRepository.findByTaskId(task.id()).get(0).errorCode()).isEqualTo("AI_MODEL_UNAVAILABLE");
        assertThat(eventRepository.findByTaskId(task.id()).get(1).content()).isEqualTo("retry requested");
    }

    @Test
    void createsControlledDownloadUrlForExportFile() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        reportRepository.save(new Report(102L, "Download API report", 1L, ReportStatus.COMPLETED, 10L));
        contentRepository.saveCompletedVersion(102L, 1L, List.of(
                Map.of("heading", "Summary", "content", "Download API body", "citations", List.of())
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        Map<String, Object> exported = service.createExport(102L, Map.of("format", "markdown", "templateId", "enterprise-default"));

        Map<String, Object> download = service.createExportDownloadUrl((Long) exported.get("exportFileId"));

        assertThat(exported.get("downloadUrl")).isEqualTo("/api/v1/files/report-exports/" + exported.get("exportFileId") + "/download-url");
        assertThat(download)
                .containsEntry("exportFileId", exported.get("exportFileId"))
                .containsEntry("fileName", exported.get("fileName"))
                .containsEntry("contentType", "text/markdown; charset=UTF-8");
        assertThat(download.get("downloadUrl").toString()).startsWith("https://minio.local/");
        assertThat(download.get("expiresAt")).isNotNull();
    }

    @Test
    void createsDownloadUrlOnlyForReportOwnerAndWritesDownloadAudit() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(103L, "Audited download report", 44L, ReportStatus.COMPLETED, 11L));
        contentRepository.saveCompletedVersion(103L, 44L, List.of(
                Map.of("heading", "Summary", "content", "Download audit body", "citations", List.of())
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository(),
                auditRepository
        );
        try {
            CurrentUserHolder.set(new CurrentUser(44L, Set.of("analyst"), Set.of("report:export")));
            Map<String, Object> exported = service.createExport(103L, Map.of("format", "markdown", "templateId", "enterprise-default"));

            Map<String, Object> download = service.createExportDownloadUrl((Long) exported.get("exportFileId"));

            assertThat(download)
                    .containsEntry("exportFileId", exported.get("exportFileId"))
                    .containsEntry("reportId", 103L);
            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "report_export_download".equals(log.operationType()))
                    .singleElement()
                    .satisfies(log -> {
                        assertThat(log.actorUserId()).isEqualTo(44L);
                        assertThat(log.resourceType()).isEqualTo("report_export_file");
                        assertThat(log.resourceId()).isEqualTo(exported.get("exportFileId"));
                        assertThat(log.detail()).containsEntry("reportId", 103L);
                    });
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void rollbackCreatesNewCurrentVersionFromHistoricalSnapshotAndWritesAudit() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(107L, "Rollback audited report", 77L, ReportStatus.COMPLETED, null));
        Long firstVersionId = contentRepository.saveCompletedVersion(107L, 77L, List.of(
                Map.of("heading", "Original", "content", "Original approved body", "citations", List.of())
        ));
        Long secondVersionId = contentRepository.saveCompletedVersion(107L, 77L, List.of(
                Map.of("heading", "Updated", "content", "Updated body", "citations", List.of())
        ));
        reportRepository.save(new Report(107L, "Rollback audited report", 77L, ReportStatus.COMPLETED, secondVersionId));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                new InMemoryReportExportStorage(),
                new InMemoryReportExportFileRepository(),
                auditRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(77L, Set.of("analyst"), Set.of("report:read")));

            Map<String, Object> rollback = service.rollbackVersion(107L, firstVersionId);

            Long newVersionId = ((Number) rollback.get("newVersionId")).longValue();
            assertThat(newVersionId).isNotEqualTo(firstVersionId).isNotEqualTo(secondVersionId);
            assertThat(rollback)
                    .containsEntry("reportId", 107L)
                    .containsEntry("sourceVersionId", firstVersionId)
                    .containsEntry("currentVersionId", newVersionId)
                    .containsEntry("changeReason", "rollback");
            assertThat(reportRepository.findById(107L)).get()
                    .extracting(Report::currentVersionId)
                    .isEqualTo(newVersionId);
            assertThat(contentRepository.findCurrentSections(107L))
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("heading", "Original")
                    .containsEntry("content", "Original approved body");
            assertThat(contentRepository.listVersions(107L))
                    .extracting(version -> version.get("versionId"))
                    .containsExactly(newVersionId, secondVersionId, firstVersionId);
            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "report_version_rollback".equals(log.operationType()))
                    .singleElement()
                    .satisfies(log -> {
                        assertThat(log.actorUserId()).isEqualTo(77L);
                        assertThat(log.resourceType()).isEqualTo("report");
                        assertThat(log.resourceId()).isEqualTo(107L);
                        assertThat(log.result()).isEqualTo("succeeded");
                        assertThat(log.detail())
                                .containsEntry("sourceVersionId", firstVersionId)
                                .containsEntry("newVersionId", newVersionId);
                    });
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void rejectsDownloadUrlWhenExportFileBelongsToAnotherUserReport() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportStorage exportStorage = new InMemoryReportExportStorage();
        reportRepository.save(new Report(104L, "Private download report", 55L, ReportStatus.COMPLETED, 12L));
        contentRepository.saveCompletedVersion(104L, 55L, List.of(
                Map.of("heading", "Summary", "content", "Private body", "citations", List.of())
        ));
        ReportApplicationService service = new ReportApplicationService(
                new ReportDomainService(),
                new CapturingPublisher(),
                new InMemoryTaskRepository(),
                reportRepository,
                contentRepository,
                new InMemoryGenerationEventRepository(),
                exportStorage,
                new InMemoryReportExportFileRepository()
        );
        try {
            CurrentUserHolder.set(new CurrentUser(55L, Set.of("analyst"), Set.of("report:export")));
            Map<String, Object> exported = service.createExport(104L, Map.of("format", "markdown", "templateId", "enterprise-default"));
            CurrentUserHolder.set(new CurrentUser(56L, Set.of("analyst"), Set.of("report:export")));

            assertThatThrownBy(() -> service.createExportDownloadUrl((Long) exported.get("exportFileId")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report export file access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    private static class CapturingPublisher implements DomainEventPublisher {
        private final List<String> events = new ArrayList<>();
        private final List<Map<String, Object>> payloads = new ArrayList<>();

        @Override
        public void publish(String eventType, String eventKey, Object payload) {
            events.add(eventType);
            if (payload instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                map.forEach((key, value) -> copied.put(String.valueOf(key), value));
                payloads.add(copied);
            }
        }
    }

    private static class InMemoryTaskRepository implements ReportGenerationTaskRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final Map<Long, ReportGenerationTask> tasks = new LinkedHashMap<>();

        @Override
        public ReportGenerationTask save(ReportGenerationTask task) {
            Long id = task.id() == null ? ids.getAndIncrement() : task.id();
            ReportGenerationTask saved = task.withId(id);
            tasks.put(id, saved);
            return saved;
        }

        @Override
        public Optional<ReportGenerationTask> findById(Long taskId) {
            return Optional.ofNullable(tasks.get(taskId));
        }
    }

    private static class InMemoryTemplateRepository implements ReportTemplateRepository {
        private final Map<String, ReportTemplate> templates = new LinkedHashMap<>();

        private InMemoryTemplateRepository() {
            ReportTemplate template = new ReportTemplate(
                    "enterprise-quarterly",
                    "企业季度经营分析报告",
                    "operations",
                    "v1",
                    "active",
                    List.of(
                            new ReportTemplate.TemplateField("period", "报告周期", "text", true, List.of(), null, null),
                            new ReportTemplate.TemplateField("scope", "对象范围", "text", true, List.of(), null, null),
                            new ReportTemplate.TemplateField("focus", "关注重点", "textarea", true, List.of(), null, null),
                            new ReportTemplate.TemplateField("style", "报告风格", "select", true, List.of("管理摘要", "经营分析"), "管理摘要", null)
                    ),
                    Map.of("sections", List.of("执行摘要", "经营表现", "风险与建议")),
                    java.time.OffsetDateTime.now()
            );
            templates.put(template.templateId(), template);
        }

        @Override
        public List<ReportTemplate> findActiveTemplates() {
            return List.copyOf(templates.values());
        }

        @Override
        public Optional<ReportTemplate> findActiveByTemplateId(String templateId) {
            return Optional.ofNullable(templates.get(templateId));
        }
    }

    private static class InMemoryReportRepository implements ReportRepository {
        private final Map<Long, Report> reports = new LinkedHashMap<>();

        @Override
        public Optional<Report> findById(Long id) {
            return Optional.ofNullable(reports.get(id));
        }

        @Override
        public Report save(Report report) {
            reports.put(report.id(), report);
            return report;
        }

        @Override
        public List<Report> findByOwner(Long ownerUserId, int page, int pageSize) {
            return reports.values().stream()
                    .filter(report -> report.ownerUserId().equals(ownerUserId))
                    .skip((long) (page - 1) * pageSize)
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public long countByOwner(Long ownerUserId) {
            return reports.values().stream()
                    .filter(report -> report.ownerUserId().equals(ownerUserId))
                    .count();
        }
    }

    private static class InMemoryReportContentRepository implements ReportContentRepository {
        private final AtomicLong versionIds = new AtomicLong(1);
        private final Map<Long, Long> currentVersions = new LinkedHashMap<>();
        private final Map<Long, List<Map<String, Object>>> reportSections = new LinkedHashMap<>();
        private final Map<Long, List<Map<String, Object>>> reportVersions = new LinkedHashMap<>();
        private final Map<Long, List<Map<String, Object>>> sectionsByVersion = new LinkedHashMap<>();

        @Override
        public Long saveCompletedVersion(Long reportId, Long createdBy, List<Map<String, Object>> sections) {
            Long versionId = versionIds.getAndIncrement();
            List<Map<String, Object>> copiedSections = sections == null ? List.of() : sections.stream()
                    .map(LinkedHashMap::new)
                    .map(section -> (Map<String, Object>) section)
                    .toList();
            currentVersions.put(reportId, versionId);
            reportSections.put(reportId, copiedSections);
            sectionsByVersion.put(versionId, copiedSections);
            Map<String, Object> newVersion = new LinkedHashMap<>();
            newVersion.put("versionId", versionId);
            newVersion.put("reportId", reportId);
            newVersion.put("versionNo", reportVersions.getOrDefault(reportId, List.of()).size() + 1);
            newVersion.put("createdBy", createdBy);
            newVersion.put("current", true);
            reportVersions.computeIfAbsent(reportId, ignored -> new ArrayList<>()).add(0, newVersion);
            for (Map<String, Object> version : reportVersions.get(reportId)) {
                version.put("current", matchesVersionId(versionId, version));
            }
            return versionId;
        }

        @Override
        public List<Map<String, Object>> findCurrentSections(Long reportId) {
            return reportSections.getOrDefault(reportId, List.of());
        }

        @Override
        public List<Map<String, Object>> findSectionsByVersion(Long reportId, Long versionId) {
            if (!reportVersions.getOrDefault(reportId, List.of()).stream().anyMatch(version -> matchesVersionId(versionId, version))) {
                throw new IllegalArgumentException("report version not found: " + versionId);
            }
            return sectionsByVersion.getOrDefault(versionId, List.of()).stream()
                    .map(LinkedHashMap::new)
                    .map(section -> (Map<String, Object>) section)
                    .toList();
        }

        @Override
        public Optional<Map<String, Object>> findReference(Long reportId, Long referenceId) {
            return reportSections.getOrDefault(reportId, List.of()).stream()
                    .map(section -> findReferenceInSection(reportId, referenceId, section))
                    .flatMap(Optional::stream)
                    .findFirst();
        }

        @Override
        public List<Map<String, Object>> listVersions(Long reportId) {
            return reportVersions.getOrDefault(reportId, List.of()).stream()
                    .map(LinkedHashMap::new)
                    .map(version -> (Map<String, Object>) version)
                    .toList();
        }

        @Override
        public void markCurrentVersion(Long reportId, Long versionId) {
            if (reportVersions.getOrDefault(reportId, List.of()).stream().noneMatch(version -> matchesVersionId(versionId, version))) {
                throw new IllegalArgumentException("report version not found: " + versionId);
            }
            currentVersions.put(reportId, versionId);
            for (Map<String, Object> version : reportVersions.get(reportId)) {
                version.put("current", matchesVersionId(versionId, version));
            }
        }

        @Override
        public Long createRollbackVersion(Long reportId, Long sourceVersionId, Long createdBy) {
            if (reportVersions.getOrDefault(reportId, List.of()).stream().noneMatch(version -> matchesVersionId(sourceVersionId, version))) {
                throw new IllegalArgumentException("report version not found: " + sourceVersionId);
            }
            Long newVersionId = versionIds.getAndIncrement();
            List<Map<String, Object>> sourceSections = sectionsByVersion.getOrDefault(sourceVersionId, List.of()).stream()
                    .map(LinkedHashMap::new)
                    .map(section -> (Map<String, Object>) section)
                    .toList();
            currentVersions.put(reportId, newVersionId);
            reportSections.put(reportId, sourceSections);
            sectionsByVersion.put(newVersionId, sourceSections);
            Map<String, Object> newVersion = new LinkedHashMap<>();
            newVersion.put("versionId", newVersionId);
            newVersion.put("reportId", reportId);
            newVersion.put("versionNo", reportVersions.getOrDefault(reportId, List.of()).size() + 1);
            newVersion.put("createdBy", createdBy);
            newVersion.put("changeReason", "rollback");
            newVersion.put("rollbackSourceVersionId", sourceVersionId);
            newVersion.put("current", true);
            reportVersions.computeIfAbsent(reportId, ignored -> new ArrayList<>()).add(0, newVersion);
            for (Map<String, Object> version : reportVersions.get(reportId)) {
                version.put("current", matchesVersionId(newVersionId, version));
            }
            return newVersionId;
        }

        private boolean matchesVersionId(Long expectedVersionId, Map<String, Object> version) {
            Object actualVersionId = version.get("versionId");
            return actualVersionId instanceof Number number
                    && number.longValue() == expectedVersionId;
        }

        private Optional<Map<String, Object>> findReferenceInSection(Long reportId, Long referenceId, Map<String, Object> section) {
            Object citations = section.get("citations");
            if (!(citations instanceof List<?> citationList)) {
                return Optional.empty();
            }
            for (Object citation : citationList) {
                if (citation instanceof Map<?, ?> citationMap && matchesReferenceId(referenceId, citationMap.get("referenceId"))) {
                    Map<String, Object> reference = new LinkedHashMap<>();
                    citationMap.forEach((key, value) -> reference.put(String.valueOf(key), value));
                    reference.put("reportId", reportId);
                    reference.put("referenceId", referenceId);
                    reference.putIfAbsent("anchor", Map.of(
                            "sectionNo", section.getOrDefault("sectionNo", 1),
                            "heading", section.get("heading"),
                            "text", section.get("content")
                    ));
                    return Optional.of(reference);
                }
            }
            return Optional.empty();
        }

        private boolean matchesReferenceId(Long expectedReferenceId, Object actualReferenceId) {
            if (actualReferenceId instanceof Number number) {
                return number.longValue() == expectedReferenceId;
            }
            return actualReferenceId != null && String.valueOf(expectedReferenceId).equals(String.valueOf(actualReferenceId));
        }
    }

    private static class InMemoryGenerationEventRepository implements ReportGenerationEventRepository {
        private final Map<Long, List<SseEvent>> events = new LinkedHashMap<>();

        @Override
        public void append(SseEvent event) {
            events.computeIfAbsent(Long.valueOf(event.taskId()), ignored -> new ArrayList<>()).add(event);
        }

        @Override
        public List<SseEvent> findByTaskId(Long taskId) {
            return events.getOrDefault(taskId, List.of());
        }
    }

    private static class InMemoryReportExportStorage implements ReportExportStorage {
        private String lastContent = "";
        private byte[] lastBytes = new byte[0];
        private int storeCount = 0;
        private final Map<String, StoredObject> objects = new LinkedHashMap<>();
        private final List<String> readKeys = new ArrayList<>();

        @Override
        public StoredExport store(String objectKey, String fileName, String contentType, byte[] content) {
            storeCount++;
            lastBytes = Arrays.copyOf(content, content.length);
            lastContent = new String(content, java.nio.charset.StandardCharsets.UTF_8);
            return new StoredExport("test-bucket", objectKey, fileName, contentType, content.length, "https://minio.local/" + objectKey);
        }

        @Override
        public String createDownloadUrl(String objectKey) {
            return "https://minio.local/" + objectKey + "?presigned=true";
        }

        @Override
        public Optional<StoredObject> readObject(String objectKey) {
            readKeys.add(objectKey);
            return Optional.ofNullable(objects.get(objectKey));
        }

        int storeCount() {
            return storeCount;
        }
    }

    private static String unzipText(byte[] zipBytes, String expectedEntry) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (expectedEntry.equals(entry.getName())) {
                    return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalArgumentException("zip entry not found: " + expectedEntry);
    }

    private static Map<String, Object> enterpriseBrand(String companyName, String primaryColor) {
        return Map.of(
                "companyName", companyName,
                "logoObjectKey", "branding/contoso-logo.png",
                "header", "Board reporting",
                "footer", "Confidential",
                "fontFamily", "Aptos",
                "primaryColor", primaryColor,
                "layout", Map.of(
                        "coverTitle", "Board Report",
                        "tocTitle", "Contents",
                        "bodyTitlePrefix", "Section",
                        "closingTitle", "Next Steps",
                        "closingMessage", "Prepared for executive review",
                        "closingContact", "reporting@example.com"
                )
        );
    }

    private static class InMemoryReportExportFileRepository implements ReportExportFileRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final Map<Long, Map<String, Object>> files = new LinkedHashMap<>();

        @Override
        public Map<String, Object> save(Map<String, Object> exportFile) {
            Long id = exportFile.get("exportFileId") == null ? ids.getAndIncrement() : ((Number) exportFile.get("exportFileId")).longValue();
            Map<String, Object> saved = new LinkedHashMap<>(exportFile);
            saved.put("exportFileId", id);
            files.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Map<String, Object>> findByReportIdAndExportFileId(Long reportId, Long exportFileId) {
            Map<String, Object> file = files.get(exportFileId);
            if (file == null || !reportId.equals(((Number) file.get("reportId")).longValue())) {
                return Optional.empty();
            }
            return Optional.of(file);
        }

        @Override
        public Optional<Map<String, Object>> findByExportFileId(Long exportFileId) {
            return Optional.ofNullable(files.get(exportFileId));
        }

        @Override
        public List<Map<String, Object>> findCompletedByReportId(Long reportId) {
            return files.values().stream()
                    .filter(file -> reportId.equals(((Number) file.get("reportId")).longValue()))
                    .filter(file -> "completed".equals(file.get("status")))
                    .map(LinkedHashMap::new)
                    .map(file -> (Map<String, Object>) file)
                    .toList();
        }

        int count() {
            return files.size();
        }
    }

    private static class InMemoryAuditRepository implements AuditRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final List<OperationLog> logs = new ArrayList<>();

        @Override
        public OperationLog save(OperationLog log) {
            OperationLog saved = log.id() == null ? log.withId(ids.getAndIncrement()) : log;
            logs.add(saved);
            return saved;
        }

        @Override
        public Optional<OperationLog> findById(Long id) {
            return logs.stream().filter(log -> log.id().equals(id)).findFirst();
        }

        @Override
        public List<OperationLog> findPage(int page, int pageSize) {
            return logs;
        }

        @Override
        public long count() {
            return logs.size();
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

        Optional<ModelInvocationAudit> findByTaskId(Long taskId) {
            return invocations.values().stream()
                    .filter(invocation -> taskId.equals(invocation.taskId()))
                    .findFirst();
        }
    }
}


