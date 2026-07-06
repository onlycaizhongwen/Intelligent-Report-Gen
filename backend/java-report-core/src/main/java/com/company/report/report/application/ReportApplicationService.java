package com.company.report.report.application;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.model.ModelInvocationAudit;
import com.company.report.audit.domain.model.ModelResponseAudit;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.audit.domain.repository.ModelInvocationRepository;
import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.model.EnterpriseExportTemplate;
import com.company.report.report.domain.model.ReportGenerationTask;
import com.company.report.report.domain.model.ReportStatus;
import com.company.report.report.domain.model.ReportTemplate;
import com.company.report.report.domain.repository.ReportContentRepository;
import com.company.report.report.domain.repository.EnterpriseExportTemplateRepository;
import com.company.report.report.domain.repository.ReportExportFileRepository;
import com.company.report.report.domain.repository.ReportExportStorage;
import com.company.report.report.domain.repository.ReportGenerationEventRepository;
import com.company.report.report.domain.repository.ReportRepository;
import com.company.report.report.domain.repository.ReportGenerationTaskRepository;
import com.company.report.report.domain.repository.ReportTemplateRepository;
import com.company.report.report.domain.service.ReportDomainService;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.api.SseEvent;
import com.company.report.shared.event.DomainEventPublisher;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ReportApplicationService {
    private final ReportDomainService domainService;
    private final DomainEventPublisher eventPublisher;
    private final ReportGenerationTaskRepository taskRepository;
    private final ReportRepository reportRepository;
    private final ReportContentRepository reportContentRepository;
    private final ReportGenerationEventRepository generationEventRepository;
    private final ReportExportStorage reportExportStorage;
    private final ReportExportFileRepository reportExportFileRepository;
    private final AuditRepository auditRepository;
    private final ModelInvocationRepository modelInvocationRepository;
    private final ReportTemplateRepository templateRepository;
    private final EnterpriseExportTemplateRepository enterpriseExportTemplateRepository;
    private final AtomicLong exportIdSequence = new AtomicLong(1L);

    @Autowired
    public ReportApplicationService(ReportDomainService domainService,
                                    DomainEventPublisher eventPublisher,
                                    ReportGenerationTaskRepository taskRepository,
                                    ReportRepository reportRepository,
                                    ReportContentRepository reportContentRepository,
                                    ReportGenerationEventRepository generationEventRepository,
                                    ReportExportStorage reportExportStorage,
                                    ReportExportFileRepository reportExportFileRepository,
                                    AuditRepository auditRepository,
                                    ModelInvocationRepository modelInvocationRepository,
                                    ReportTemplateRepository templateRepository,
                                    EnterpriseExportTemplateRepository enterpriseExportTemplateRepository) {
        this.domainService = domainService;
        this.eventPublisher = eventPublisher;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
        this.reportContentRepository = reportContentRepository;
        this.generationEventRepository = generationEventRepository;
        this.reportExportStorage = reportExportStorage;
        this.reportExportFileRepository = reportExportFileRepository;
        this.auditRepository = auditRepository;
        this.modelInvocationRepository = modelInvocationRepository;
        this.templateRepository = templateRepository;
        this.enterpriseExportTemplateRepository = enterpriseExportTemplateRepository;
    }

    public ReportApplicationService(ReportDomainService domainService,
                                    DomainEventPublisher eventPublisher,
                                    ReportGenerationTaskRepository taskRepository,
                                    ReportRepository reportRepository,
                                    ReportContentRepository reportContentRepository,
                                    ReportGenerationEventRepository generationEventRepository,
                                    ReportExportStorage reportExportStorage,
                                    ReportExportFileRepository reportExportFileRepository,
                                    AuditRepository auditRepository,
                                    ModelInvocationRepository modelInvocationRepository,
                                    ReportTemplateRepository templateRepository) {
        this(domainService, eventPublisher, taskRepository, reportRepository, reportContentRepository,
                generationEventRepository, reportExportStorage, reportExportFileRepository, auditRepository,
                modelInvocationRepository, templateRepository, null);
    }

    public ReportApplicationService(ReportDomainService domainService,
                                    DomainEventPublisher eventPublisher,
                                    ReportGenerationTaskRepository taskRepository,
                                    ReportRepository reportRepository,
                                    ReportContentRepository reportContentRepository,
                                    ReportGenerationEventRepository generationEventRepository,
                                    ReportExportStorage reportExportStorage,
                                    ReportExportFileRepository reportExportFileRepository,
                                    AuditRepository auditRepository,
                                    ModelInvocationRepository modelInvocationRepository,
                                    EnterpriseExportTemplateRepository enterpriseExportTemplateRepository) {
        this(domainService, eventPublisher, taskRepository, reportRepository, reportContentRepository,
                generationEventRepository, reportExportStorage, reportExportFileRepository, auditRepository,
                modelInvocationRepository, null, enterpriseExportTemplateRepository);
    }

    public ReportApplicationService(ReportDomainService domainService,
                                    DomainEventPublisher eventPublisher,
                                    ReportGenerationTaskRepository taskRepository,
                                    ReportRepository reportRepository,
                                    ReportContentRepository reportContentRepository,
                                    ReportGenerationEventRepository generationEventRepository,
                                    ReportExportStorage reportExportStorage,
                                    ReportExportFileRepository reportExportFileRepository,
                                    AuditRepository auditRepository,
                                    ModelInvocationRepository modelInvocationRepository) {
        this(domainService, eventPublisher, taskRepository, reportRepository, reportContentRepository,
                generationEventRepository, reportExportStorage, reportExportFileRepository, auditRepository,
                modelInvocationRepository, null, null);
    }

    public ReportApplicationService(ReportDomainService domainService,
                                    DomainEventPublisher eventPublisher,
                                    ReportGenerationTaskRepository taskRepository,
                                    ReportRepository reportRepository,
                                    ReportContentRepository reportContentRepository,
                                    ReportGenerationEventRepository generationEventRepository,
                                    ReportExportStorage reportExportStorage,
                                    ReportExportFileRepository reportExportFileRepository,
                                    AuditRepository auditRepository) {
        this(domainService, eventPublisher, taskRepository, reportRepository, reportContentRepository,
                generationEventRepository, reportExportStorage, reportExportFileRepository, auditRepository,
                noopModelInvocationRepository());
    }

    public ReportApplicationService(ReportDomainService domainService,
                                    DomainEventPublisher eventPublisher,
                                    ReportGenerationTaskRepository taskRepository,
                                    ReportRepository reportRepository,
                                    ReportContentRepository reportContentRepository,
                                    ReportGenerationEventRepository generationEventRepository,
                                    ReportExportStorage reportExportStorage,
                                    ReportExportFileRepository reportExportFileRepository) {
        this(domainService, eventPublisher, taskRepository, reportRepository, reportContentRepository,
                generationEventRepository, reportExportStorage, reportExportFileRepository, noopAuditRepository(),
                noopModelInvocationRepository());
    }

    public Map<String, Object> createGenerationTask(String topic, Map<String, Object> payload) {
        Long actorUserId = currentUserId();
        ReportGenerationTask task = taskRepository.save(ReportGenerationTask.naturalLanguage(
                actorUserId,
                topic,
                payload,
                "report-task-" + System.nanoTime()
        ));
        Map<String, Object> result = task.toResponse();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("mode", "natural_language");
        auditDetail.put("topic", topic);
        auditDetail.put("payload", payload == null ? Map.of() : payload);
        auditDetail.put("reportId", task.reportId());
        auditRepository.save(new OperationLog(
                null,
                actorUserId,
                "report_generation_task_created",
                "report_generation_task",
                task.id(),
                "succeeded",
                auditDetail,
                java.time.OffsetDateTime.now()
        ));
        eventPublisher.publish("report.generation.outline_ready", String.valueOf(task.id()), result);
        return result;
    }

    public Map<String, Object> createTemplateTask(String templateId, Map<String, Object> payload) {
        Long actorUserId = currentUserId();
        ReportTemplate template = activeTemplate(templateId);
        Map<String, Object> validatedParameters = validateTemplateParameters(template, payload);
        Map<String, Object> templateSnapshot = template.toSnapshot(validatedParameters);
        ReportGenerationTask task = taskRepository.save(ReportGenerationTask.template(
                actorUserId,
                templateId,
                templateSnapshot,
                "report-template-task-" + System.nanoTime()
        ));
        Map<String, Object> result = task.toResponse();
        result.put("templateSnapshot", task.templateSnapshot());
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("mode", "template");
        auditDetail.put("templateId", templateId);
        auditDetail.put("payload", validatedParameters);
        auditDetail.put("reportId", task.reportId());
        auditRepository.save(new OperationLog(
                null,
                actorUserId,
                "report_generation_task_created",
                "report_generation_task",
                task.id(),
                "succeeded",
                auditDetail,
                java.time.OffsetDateTime.now()
        ));
        eventPublisher.publish("report.generation.outline_ready", String.valueOf(task.id()), result);
        return result;
    }

    public List<Map<String, Object>> listReportTemplates() {
        return effectiveTemplateRepository().findActiveTemplates().stream()
                .map(ReportTemplate::toResponse)
                .toList();
    }

    public Map<String, Object> createEnterpriseExportTemplate(Map<String, Object> request) {
        EnterpriseExportTemplateRepository repository = enterpriseExportTemplateRepository();
        String templateId = requiredString(request, "templateId", "enterprise export template id is required");
        String name = requiredString(request, "name", "enterprise export template name is required");
        Object brand = request == null ? null : request.get("brand");
        domainService.ensureEnterpriseBrandTemplate(brand);
        OffsetDateTime now = OffsetDateTime.now();
        EnterpriseExportTemplate saved = repository.save(new EnterpriseExportTemplate(
                null,
                templateId,
                name,
                1,
                "active",
                enterpriseTemplateBrandSnapshot(templateId, 1, exportTheme(brand)),
                currentUserId(),
                now,
                now
        ));
        auditEnterpriseTemplate("enterprise_export_template_created", saved);
        return saved.toResponse();
    }

    public PageResponse<Map<String, Object>> listEnterpriseExportTemplates(int page, int pageSize, Map<String, Object> filters) {
        EnterpriseExportTemplateRepository repository = enterpriseExportTemplateRepository();
        String status = stringOrDefault(filters == null ? null : filters.get("status"), "");
        List<Map<String, Object>> items = repository.findPage(status, page, pageSize).stream()
                .map(EnterpriseExportTemplate::toResponse)
                .toList();
        return new PageResponse<>(items, Math.max(page, 1), Math.max(pageSize, 1), repository.count(status));
    }

    public List<Map<String, Object>> listEnterpriseExportTemplateVersions(String templateId) {
        return enterpriseExportTemplateRepository().findVersions(templateId).stream()
                .map(EnterpriseExportTemplate::toResponse)
                .toList();
    }

    public Map<String, Object> updateEnterpriseExportTemplate(String templateId, Map<String, Object> request) {
        EnterpriseExportTemplateRepository repository = enterpriseExportTemplateRepository();
        EnterpriseExportTemplate existing = repository.findLatestByTemplateId(templateId)
                .orElseThrow(() -> new IllegalArgumentException("enterprise export template not found: " + templateId));
        String name = stringOrDefault(request == null ? null : request.get("name"), existing.name());
        Object brand = request == null ? null : request.get("brand");
        domainService.ensureEnterpriseBrandTemplate(brand);
        int nextVersion = existing.version() + 1;
        OffsetDateTime now = OffsetDateTime.now();
        EnterpriseExportTemplate saved = repository.save(new EnterpriseExportTemplate(
                null,
                templateId,
                name,
                nextVersion,
                "active",
                enterpriseTemplateBrandSnapshot(templateId, nextVersion, exportTheme(brand)),
                currentUserId(),
                now,
                now
        ));
        auditEnterpriseTemplate("enterprise_export_template_updated", saved);
        return saved.toResponse();
    }

    public Map<String, Object> disableEnterpriseExportTemplate(String templateId) {
        return changeEnterpriseExportTemplateStatus(templateId, "disabled", "enterprise_export_template_disabled");
    }

    public Map<String, Object> enableEnterpriseExportTemplate(String templateId) {
        return changeEnterpriseExportTemplateStatus(templateId, "active", "enterprise_export_template_enabled");
    }

    public Map<String, Object> confirmOutline(Long taskId, Map<String, Object> request) {
        domainService.ensureOutlineConfirmed(request);
        ReportGenerationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("report generation task not found: " + taskId));
        ReportGenerationTask confirmed = taskRepository.save(task.confirmOutline(request));
        Map<String, Object> result = confirmed.toResponse();
        result.put("confirmed", true);
        result.put("nextStage", "retrieval");
        eventPublisher.publish("report.generation.outline_confirmed", String.valueOf(taskId), workerGenerationPayload(confirmed));
        return result;
    }

    public SseEmitter openTaskStream(Long taskId) {
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            if (generationEventRepository != null) {
                for (SseEvent event : streamEventsForTask(taskId)) {
                    emitter.send(SseEmitter.event().name(event.type()).data(event));
                }
                emitter.complete();
                return emitter;
            }
            ReportGenerationTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("report generation task not found: " + taskId));
            emitter.send(SseEmitter.event().name("stage").data(
                    SseEvent.stage(taskId, task.currentStage(), stageMessage(task), task.progress() / 100.0)
            ));
            if ("running".equals(task.status())) {
                emitter.send(SseEmitter.event().name("delta").data(
                        SseEvent.delta(taskId, "正在检索知识库并组织报告上下文", task.currentStage(), 0.2)
                ));
            }
            if ("completed".equals(task.status())) {
                emitter.send(SseEmitter.event().name("done").data(SseEvent.done(taskId)));
            }
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public PageResponse<Map<String, Object>> listReports(int page, int pageSize) {
        Long ownerUserId = currentUserId();
        List<Map<String, Object>> items = reportRepository.findByOwner(ownerUserId, page, pageSize).stream()
                .map(report -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("reportId", report.id());
                    item.put("title", report.title());
                    item.put("status", report.status().name().toLowerCase());
                    item.put("ownerUserId", report.ownerUserId());
                    item.put("currentVersionId", report.currentVersionId());
                    return item;
                })
                .toList();
        return new PageResponse<>(items, page, pageSize, reportRepository.countByOwner(ownerUserId));
    }

    public Map<String, Object> getReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", report.id());
        result.put("title", report.title());
        result.put("status", report.status().name().toLowerCase());
        result.put("ownerUserId", report.ownerUserId());
        result.put("currentVersionId", report.currentVersionId());
        result.put("sections", reportContentRepository.findCurrentSections(reportId));
        return result;
    }

    public Map<String, Object> completeGenerationTask(Long taskId, List<Map<String, Object>> sections) {
        return completeGenerationTask(taskId, sections, List.of(), null);
    }

    private Map<String, Object> completeGenerationTask(
            Long taskId,
            List<Map<String, Object>> sections,
            List<Object> references,
            String referenceTraceId
    ) {
        ReportGenerationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("report generation task not found: " + taskId));
        if (task.reportId() == null) {
            throw new IllegalStateException("report generation task is not bound to report: " + taskId);
        }
        Long versionId = reportContentRepository.saveCompletedVersion(task.reportId(), task.createdBy(), sections);
        generationEventRepository.append(SseEvent.stage(taskId, "writing", "正在写入报告正文版本", 0.9));
        for (Map<String, Object> section : sections == null ? List.<Map<String, Object>>of() : sections) {
            generationEventRepository.append(SseEvent.delta(
                    taskId,
                    String.valueOf(section.getOrDefault("content", "")),
                    "writing",
                    0.95
            ));
        }
        if (references != null && !references.isEmpty()) {
            generationEventRepository.append(SseEvent.references(taskId, references, referenceTraceId));
        }
        Report report = reportRepository.findById(task.reportId())
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + task.reportId()));
        reportRepository.save(new Report(report.id(), report.title(), report.ownerUserId(), ReportStatus.COMPLETED, versionId));
        ReportGenerationTask completed = taskRepository.save(task.complete());
        generationEventRepository.append(SseEvent.done(taskId));
        Map<String, Object> result = completed.toResponse();
        result.put("versionId", versionId);
        eventPublisher.publish("report.generation.completed", String.valueOf(taskId), result);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> completeGenerationTaskFromWorker(Long taskId, Map<String, Object> request) {
        Map<String, Object> payload = request == null ? Map.of() : request;
        List<Map<String, Object>> sections = mapList(payload.get("sections"));
        List<Object> references = objectList(payload.get("references"));
        Map<String, Object> modelInvocation = objectMap(payload.get("modelInvocation"));
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("sections are required for generation completion callback");
        }
        String traceId = String.valueOf(modelInvocation.getOrDefault("traceId", ""));
        Map<String, Object> result = completeGenerationTask(taskId, sections, references, traceId.isBlank() ? null : traceId);
        if (!modelInvocation.isEmpty()) {
            modelInvocationRepository.save(toModelInvocationAudit(taskId, result, modelInvocation));
            auditRepository.save(new OperationLog(
                    null,
                    currentUserId(),
                    "model_invocation",
                    "report_generation_task",
                    taskId,
                    String.valueOf(modelInvocation.getOrDefault("status", "succeeded")),
                    new LinkedHashMap<>(modelInvocation),
                    OffsetDateTime.now()
            ));
        }
        result.put("references", references);
        result.put("modelInvocation", modelInvocation);
        return result;
    }

    private ModelInvocationAudit toModelInvocationAudit(Long taskId, Map<String, Object> completionResult, Map<String, Object> modelInvocation) {
        Long reportId = nullableLong(completionResult.get("reportId"));
        String traceId = stringValue(modelInvocation.get("traceId"));
        String status = stringValue(modelInvocation.getOrDefault("status", "succeeded"));
        String responseSummary = stringValue(modelInvocation.getOrDefault("responseSummary", modelInvocation.get("response")));
        ModelResponseAudit response = responseSummary == null || responseSummary.isBlank()
                ? null
                : new ModelResponseAudit(
                null,
                null,
                1,
                responseSummary,
                objectMap(modelInvocation.get("responseMetadata")),
                stringValue(modelInvocation.get("finishReason")),
                OffsetDateTime.now()
        );
        return new ModelInvocationAudit(
                null,
                taskId,
                reportId,
                currentUserId(),
                stringValue(modelInvocation.get("provider")),
                stringValue(modelInvocation.getOrDefault("modelName", modelInvocation.get("model"))),
                stringValue(modelInvocation.get("promptTemplateId")),
                stringValue(modelInvocation.get("promptSnapshot")),
                stringValue(modelInvocation.get("contextSnapshot")),
                modelInvocationParameters(modelInvocation),
                stringValue(modelInvocation.getOrDefault("requestHash", modelInvocation.get("promptHash"))),
                status,
                nullableLong(modelInvocation.getOrDefault("durationMs", modelInvocation.get("latencyMs"))),
                nullableInteger(modelInvocation.get("inputTokens")),
                nullableInteger(modelInvocation.get("outputTokens")),
                nullableInteger(modelInvocation.get("totalTokens")),
                stringValue(modelInvocation.get("errorCode")),
                stringValue(modelInvocation.get("errorMessage")),
                traceId,
                stringValue(modelInvocation.getOrDefault("auditEventKey", traceId == null || traceId.isBlank() ? null : traceId + ":" + taskId)),
                OffsetDateTime.now(),
                response
        );
    }

    private Map<String, Object> modelInvocationParameters(Map<String, Object> modelInvocation) {
        Map<String, Object> parameters = new LinkedHashMap<>(objectMap(modelInvocation.get("parameters")));
        Map<String, Object> routingPolicy = objectMap(modelInvocation.get("routingPolicy"));
        if (!routingPolicy.isEmpty()) {
            parameters.put("routingPolicy", routingPolicy);
        }
        return parameters;
    }

    public Map<String, Object> failGenerationTask(Long taskId, Map<String, Object> request) {
        ReportGenerationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("report generation task not found: " + taskId));
        String errorCode = String.valueOf(request.getOrDefault("errorCode", "GENERATION_FAILED"));
        String message = String.valueOf(request.getOrDefault("message", "report generation failed"));
        boolean retryable = Boolean.parseBoolean(String.valueOf(request.getOrDefault("retryable", true)));
        ReportGenerationTask failed = taskRepository.save(task.fail(message, retryable));
        generationEventRepository.append(SseEvent.error(taskId, errorCode, message, task.traceId()));
        Map<String, Object> result = failed.toResponse();
        result.put("errorCode", errorCode);
        eventPublisher.publish("report.generation.failed", String.valueOf(taskId), result);
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                map.forEach((key, entryValue) -> copied.put(String.valueOf(key), entryValue));
                result.add(copied);
            }
        }
        return result;
    }

    private List<Object> objectList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> copied.put(String.valueOf(key), entryValue));
        return copied;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long nullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer nullableInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Map<String, Object> workerGenerationPayload(ReportGenerationTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.id());
        payload.put("reportId", task.reportId());
        payload.put("question", generationQuestion(task));
        payload.put("topic", generationQuestion(task));
        payload.put("context", generationContext(task));
        payload.put("outline", task.outline());
        payload.put("traceId", task.traceId());
        return payload;
    }

    private String generationQuestion(ReportGenerationTask task) {
        Object topic = task.userInput() == null ? null : task.userInput().get("topic");
        if (topic != null && !String.valueOf(topic).isBlank()) {
            return String.valueOf(topic);
        }
        Object templateId = task.userInput() == null ? null : task.userInput().get("templateId");
        return templateId == null ? "" : "模板报告 " + templateId;
    }

    private Map<String, Object> generationContext(ReportGenerationTask task) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (task.generationMode() != null && !task.generationMode().isBlank()) {
            context.put("generationMode", task.generationMode());
        }
        Object payload = task.userInput() == null ? null : task.userInput().get("payload");
        if (payload instanceof Map<?, ?> map) {
            map.forEach((key, value) -> context.put(String.valueOf(key), value));
        }
        if (task.templateSnapshot() != null && !task.templateSnapshot().isEmpty()) {
            context.put("templateSnapshot", task.templateSnapshot());
        }
        return context;
    }

    private ReportTemplate activeTemplate(String templateId) {
        return effectiveTemplateRepository().findActiveByTemplateId(templateId)
                .orElseThrow(() -> new IllegalArgumentException("report template not found or inactive: " + templateId));
    }

    private ReportTemplateRepository effectiveTemplateRepository() {
        return templateRepository == null ? new com.company.report.report.infrastructure.persistence.InMemoryReportTemplateRepository() : templateRepository;
    }

    private EnterpriseExportTemplateRepository enterpriseExportTemplateRepository() {
        if (enterpriseExportTemplateRepository == null) {
            throw new IllegalStateException("enterprise export template repository is not configured");
        }
        return enterpriseExportTemplateRepository;
    }

    private Map<String, Object> changeEnterpriseExportTemplateStatus(String templateId, String status, String operationType) {
        EnterpriseExportTemplateRepository repository = enterpriseExportTemplateRepository();
        EnterpriseExportTemplate existing = repository.findLatestByTemplateId(templateId)
                .orElseThrow(() -> new IllegalArgumentException("enterprise export template not found: " + templateId));
        EnterpriseExportTemplate saved = repository.save(existing.withStatus(status, OffsetDateTime.now()));
        auditEnterpriseTemplate(operationType, saved);
        return saved.toResponse();
    }

    private void auditEnterpriseTemplate(String operationType, EnterpriseExportTemplate template) {
        auditRepository.save(new OperationLog(
                null,
                currentUserId(),
                operationType,
                "enterprise_export_template",
                template.id(),
                "succeeded",
                Map.of(
                        "templateId", template.templateId(),
                        "version", template.versionLabel(),
                        "status", template.status()
                ),
                OffsetDateTime.now()
        ));
    }

    private Map<String, Object> validateTemplateParameters(ReportTemplate template, Map<String, Object> payload) {
        Map<String, Object> source = payload == null ? Map.of() : payload;
        Map<String, Object> validated = new LinkedHashMap<>();
        for (ReportTemplate.TemplateField field : template.fields() == null ? List.<ReportTemplate.TemplateField>of() : template.fields()) {
            Object value = source.get(field.fieldKey());
            if ((value == null || String.valueOf(value).isBlank()) && field.defaultValue() != null && !field.defaultValue().isBlank()) {
                value = field.defaultValue();
            }
            if (field.required() && (value == null || String.valueOf(value).isBlank())) {
                throw new IllegalArgumentException("template field required: " + field.fieldKey());
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                ensureTemplateFieldValue(field, value);
                validated.put(field.fieldKey(), value);
            }
        }
        return validated;
    }

    private void ensureTemplateFieldValue(ReportTemplate.TemplateField field, Object value) {
        String type = field.type() == null ? "text" : field.type();
        if ("select".equals(type) && field.options() != null && !field.options().isEmpty()
                && !field.options().contains(String.valueOf(value))) {
            throw new IllegalArgumentException("template field invalid option: " + field.fieldKey());
        }
        if ("number".equals(type)) {
            try {
                Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("template field invalid number: " + field.fieldKey(), ex);
            }
        }
    }

    public Map<String, Object> retryGenerationTask(Long taskId, Map<String, Object> request) {
        ReportGenerationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("report generation task not found: " + taskId));
        if (!"retryable".equals(task.status()) && !"failed".equals(task.status())) {
            throw new IllegalStateException("report generation task is not retryable: " + taskId);
        }
        ReportGenerationTask retried = taskRepository.save(task.retry());
        generationEventRepository.append(SseEvent.stage(taskId, retried.currentStage(), "retry requested", retried.progress() / 100.0));
        Map<String, Object> result = retried.toResponse();
        result.put("retryReason", request == null ? null : request.get("reason"));
        eventPublisher.publish("report.generation.retry_requested", String.valueOf(taskId), result);
        return result;
    }

    public Map<String, Object> getReference(Long reportId, Long referenceId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        ensureReportOwner(report, "report reference access denied: " + reportId);
        return reportContentRepository.findReference(reportId, referenceId)
                .orElseThrow(() -> new IllegalArgumentException("report reference not found: " + referenceId));
    }

    List<SseEvent> streamEventsForTask(Long taskId) {
        ReportGenerationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("report generation task not found: " + taskId));
        List<SseEvent> persistedEvents = generationEventRepository.findByTaskId(taskId);
        if (!persistedEvents.isEmpty()) {
            return persistedEvents;
        }
        if ("completed".equals(task.status())) {
            return List.of(
                    SseEvent.stage(taskId, task.currentStage(), stageMessage(task), task.progress() / 100.0),
                    SseEvent.done(taskId)
            );
        }
        if ("running".equals(task.status())) {
            return List.of(
                    SseEvent.stage(taskId, task.currentStage(), stageMessage(task), task.progress() / 100.0),
                    SseEvent.delta(taskId, "正在检索知识库并组织报告上下文", task.currentStage(), 0.2)
            );
        }
        return List.of(SseEvent.stage(taskId, task.currentStage(), stageMessage(task), task.progress() / 100.0));
    }

    public Map<String, Object> createExport(Long reportId, Map<String, Object> request) {
        domainService.ensureExportTemplateAvailable(request.get("templateId"));
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        ensureReportOwner(report, "report export access denied: " + reportId);
        List<Map<String, Object>> sections = reportContentRepository.findCurrentSections(reportId);
        Long exportFileId = exportIdSequence.getAndIncrement();
        String format = String.valueOf(request.getOrDefault("format", "markdown"));
        ExportFormat exportFormat = exportFormat(format);
        EnterpriseExportTemplate managedTemplate = managedEnterpriseExportTemplate(request);
        ExportTheme exportTheme = resolvedExportTheme(request, managedTemplate);
        if (enterpriseExportFormat(exportFormat)) {
            domainService.ensureEnterpriseBrandTemplate(exportTheme.brandMap());
        }
        if (!exportFormat.supported()) {
            throw new IllegalArgumentException("unsupported export format for current delivery slice: " + format);
        }
        String fileName = "report-" + reportId + "-v" + report.currentVersionId() + "." + exportFormat.extension();
        String objectKey = "reports/" + reportId + "/exports/" + exportFileId + "/" + fileName;
        String contentType = exportFormat.contentType();
        byte[] content = renderExportContent(report, sections, exportTheme, exportFormat);
        ReportExportStorage.StoredExport storedExport;
        try {
            storedExport = reportExportStorage.store(objectKey, fileName, contentType, content);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to store report export file", ex);
        }
        Map<String, Object> exportRecord = new LinkedHashMap<>();
        exportRecord.put("reportId", reportId);
        exportRecord.put("exportFileId", exportFileId);
        exportRecord.put("status", "completed");
        exportRecord.put("format", exportFormat.name());
        exportRecord.put("templateId", request.get("templateId"));
        exportRecord.put("downloadPolicy", "presigned_url");
        exportRecord.put("bucket", storedExport.bucket());
        exportRecord.put("objectKey", storedExport.objectKey());
        exportRecord.put("fileName", storedExport.fileName());
        exportRecord.put("contentType", storedExport.contentType());
        exportRecord.put("sizeBytes", storedExport.sizeBytes());
        exportRecord.put("downloadUrl", controlledDownloadUrl(exportFileId));
        exportRecord.put("expiresAt", OffsetDateTime.now().plusMinutes(30).toString());
        if (enterpriseExportFormat(exportFormat)) {
            exportRecord.put("brandSnapshot", enterpriseBrandSnapshot(request.get("templateId"), exportFormat.name(), exportTheme, managedTemplate));
        }
        exportRecord = new LinkedHashMap<>(reportExportFileRepository.save(exportRecord));
        exportRecord.put("downloadUrl", controlledDownloadUrl(((Number) exportRecord.get("exportFileId")).longValue()));
        exportRecord = new LinkedHashMap<>(reportExportFileRepository.save(exportRecord));
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("exportFileId", exportRecord.get("exportFileId"));
        auditDetail.put("format", exportFormat.name());
        auditDetail.put("templateId", request.get("templateId"));
        auditDetail.put("objectKey", storedExport.objectKey());
        if (exportRecord.containsKey("brandSnapshot")) {
            auditDetail.put("brandSnapshot", exportRecord.get("brandSnapshot"));
        }
        auditRepository.save(new OperationLog(
                null,
                currentUserId(),
                "report_export",
                "report",
                reportId,
                "succeeded",
                auditDetail,
                OffsetDateTime.now()
        ));
        eventPublisher.publish("report.export.requested", String.valueOf(exportFileId), exportRecord);
        return exportRecord;
    }

    public Map<String, Object> createExportDownloadUrl(Long exportFileId) {
        Map<String, Object> exportFile = reportExportFileRepository.findByExportFileId(exportFileId)
                .orElseThrow(() -> new IllegalArgumentException("report export file not found: " + exportFileId));
        Long reportId = ((Number) exportFile.get("reportId")).longValue();
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        ensureReportOwner(report, "report export file access denied: " + exportFileId);
        String objectKey = String.valueOf(exportFile.get("objectKey"));
        String downloadUrl;
        try {
            downloadUrl = reportExportStorage.createDownloadUrl(objectKey);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create report export download URL", ex);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("exportFileId", exportFileId);
        response.put("reportId", exportFile.get("reportId"));
        response.put("fileName", exportFile.get("fileName"));
        response.put("contentType", exportFile.get("contentType"));
        response.put("sizeBytes", exportFile.get("sizeBytes"));
        response.put("downloadUrl", downloadUrl);
        response.put("expiresAt", OffsetDateTime.now().plusMinutes(30).toString());
        auditRepository.save(new OperationLog(
                null,
                currentUserId(),
                "report_export_download",
                "report_export_file",
                exportFileId,
                "succeeded",
                Map.of(
                        "reportId", reportId,
                        "objectKey", objectKey,
                        "fileName", exportFile.get("fileName")
                ),
                OffsetDateTime.now()
        ));
        return response;
    }

    private String renderMarkdown(Report report, List<Map<String, Object>> sections) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(report.title()).append("\n\n");
        for (Map<String, Object> section : sections) {
            markdown.append("## ").append(section.getOrDefault("heading", "未命名章节")).append("\n\n");
            markdown.append(section.getOrDefault("content", "")).append("\n\n");
            Object citations = section.get("citations");
            if (citations instanceof List<?> citationList && !citationList.isEmpty()) {
                markdown.append("引用: ");
                markdown.append(String.join(", ", citationList.stream().map(String::valueOf).toList()));
                markdown.append("\n\n");
            }
        }
        return markdown.toString();
    }

    private byte[] renderExportContent(Report report, List<Map<String, Object>> sections, ExportTheme theme, ExportFormat exportFormat) {
        if (exportFormat.wordDocument()) {
            return renderDocx(report, sections, theme);
        }
        if (exportFormat.pdfDocument()) {
            return renderPdf(report, sections, theme);
        }
        if (exportFormat.powerPointDocument()) {
            return renderPptx(report, sections, theme);
        }
        return renderMarkdown(report, sections).getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> exportBrand(Object brand) {
        Map<String, Object> values = objectMap(brand);
        if (values.isEmpty()) {
            return Map.of(
                    "companyName", "Intelligent Report System",
                    "logoObjectKey", "branding/default-logo.svg",
                    "primaryColor", "#1F4E79"
            );
        }
        return values;
    }

    private ExportTheme exportTheme(Object brand) {
        Map<String, Object> values = exportBrand(brand);
        String primaryColor = normalizedPrimaryColor(String.valueOf(values.get("primaryColor")));
        return new ExportTheme(
                stringOrDefault(values.get("companyName"), "Intelligent Report System"),
                stringOrDefault(values.get("logoObjectKey"), "branding/default-logo.svg"),
                stringOrDefault(values.get("header"), ""),
                stringOrDefault(values.get("footer"), ""),
                stringOrDefault(values.get("fontFamily"), "Aptos"),
                primaryColor,
                exportLayout(values.get("layout"))
        );
    }

    private ExportLayout exportLayout(Object layout) {
        Map<String, Object> values = objectMap(layout);
        return new ExportLayout(
                stringOrDefault(values.get("coverTitle"), ""),
                stringOrDefault(values.get("tocTitle"), "Table of Contents"),
                stringOrDefault(values.get("bodyTitlePrefix"), ""),
                intOrDefault(values.get("titleFontSize"), 32),
                intOrDefault(values.get("bodyFontSize"), 18),
                intOrDefault(values.get("headerFontSize"), 14),
                intOrDefault(values.get("footerFontSize"), 12),
                intOrDefault(values.get("pageWidth"), 468),
                stringOrDefault(values.get("closingTitle"), "Closing"),
                stringOrDefault(values.get("closingMessage"), ""),
                stringOrDefault(values.get("closingContact"), "")
        );
    }

    private boolean enterpriseExportFormat(ExportFormat exportFormat) {
        return exportFormat.wordDocument() || exportFormat.pdfDocument() || exportFormat.powerPointDocument();
    }

    private EnterpriseExportTemplate managedEnterpriseExportTemplate(Map<String, Object> request) {
        if (request == null || request.containsKey("brand") || enterpriseExportTemplateRepository == null) {
            return null;
        }
        String templateId = String.valueOf(request.getOrDefault("templateId", ""));
        if (templateId.isBlank()) {
            return null;
        }
        return enterpriseExportTemplateRepository.findActiveByTemplateId(templateId)
                .orElseThrow(() -> new IllegalArgumentException("enterprise export template not found or inactive: " + templateId));
    }

    private ExportTheme resolvedExportTheme(Map<String, Object> request, EnterpriseExportTemplate managedTemplate) {
        if (managedTemplate != null) {
            return exportTheme(managedTemplate.brandSnapshot());
        }
        return exportTheme(request == null ? null : request.get("brand"));
    }

    private Map<String, Object> enterpriseTemplateBrandSnapshot(String templateId, int version, ExportTheme theme) {
        Map<String, Object> snapshot = enterpriseBrandSnapshot(templateId, null, theme, null);
        snapshot.put("templateVersion", "v" + version);
        return snapshot;
    }

    private Map<String, Object> enterpriseBrandSnapshot(Object templateId, String format, ExportTheme theme, EnterpriseExportTemplate managedTemplate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("templateId", String.valueOf(templateId));
        if (managedTemplate != null) {
            snapshot.put("templateVersion", managedTemplate.versionLabel());
        }
        if (format != null) {
            snapshot.put("format", format);
        }
        snapshot.put("companyName", theme.companyName());
        snapshot.put("logoObjectKey", theme.logoObjectKey());
        snapshot.put("header", theme.header());
        snapshot.put("footer", theme.footer());
        snapshot.put("fontFamily", theme.fontFamily());
        snapshot.put("primaryColor", theme.primaryColor());
        snapshot.put("layout", exportLayoutSnapshot(theme.layout()));
        return snapshot;
    }

    private Map<String, Object> exportLayoutSnapshot(ExportLayout layout) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("coverTitle", layout.coverTitle());
        snapshot.put("tocTitle", layout.tocTitle());
        snapshot.put("bodyTitlePrefix", layout.bodyTitlePrefix());
        snapshot.put("titleFontSize", layout.titleFontSize());
        snapshot.put("bodyFontSize", layout.bodyFontSize());
        snapshot.put("headerFontSize", layout.headerFontSize());
        snapshot.put("footerFontSize", layout.footerFontSize());
        snapshot.put("pageWidth", layout.pageWidth());
        snapshot.put("closingTitle", layout.closingTitle());
        snapshot.put("closingMessage", layout.closingMessage());
        snapshot.put("closingContact", layout.closingContact());
        return snapshot;
    }

    private String requiredString(Map<String, Object> request, String key, String message) {
        String value = stringOrDefault(request == null ? null : request.get(key), "");
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String stringOrDefault(Object value, String defaultValue) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private int intOrDefault(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String normalizedPrimaryColor(String color) {
        return "#" + wordColor(color);
    }

    private byte[] renderPdf(Report report, List<Map<String, Object>> sections, ExportTheme brand) {
        List<String> bodyLines = new java.util.ArrayList<>();
        bodyLines.add(brand.layout().tocTitle());
        List<Integer> tocLineIndexes = new ArrayList<>();
        List<String> tocHeadings = new ArrayList<>();
        int sectionNo = 1;
        for (Map<String, Object> section : sections) {
            tocLineIndexes.add(bodyLines.size());
            tocHeadings.add(String.valueOf(section.getOrDefault("heading", "Untitled Section")));
            bodyLines.add("");
            sectionNo++;
        }
        bodyLines.add("");
        sectionNo = 1;
        List<Integer> sectionStartLineIndexes = new ArrayList<>();
        for (Map<String, Object> section : sections) {
            sectionStartLineIndexes.add(bodyLines.size());
            bodyLines.add(layoutSectionTitle(sectionNo, String.valueOf(section.getOrDefault("heading", "Untitled Section")), brand.layout()));
            bodyLines.add(String.valueOf(section.getOrDefault("content", "")));
            Object citations = section.get("citations");
            if (citations instanceof List<?> citationList && !citationList.isEmpty()) {
                bodyLines.add("References: " + String.join(", ", citationList.stream().map(String::valueOf).toList()));
            }
            bodyLines.add("");
            sectionNo++;
        }
        for (int index = 0; index < tocLineIndexes.size(); index++) {
            int pageNo = pdfLinePage(sectionStartLineIndexes.get(index), 24);
            bodyLines.set(tocLineIndexes.get(index), tocLine(index + 1, tocHeadings.get(index), pageNo));
        }
        LogoAsset logo = resolvePdfLogoAsset(brand);
        boolean hasPdfImage = logo.pdfImageObject() != null;
        List<List<String>> pages = paginatePdfBodyLines(bodyLines, 24);
        int totalPages = pages.size();
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        int fontObjectId = 3 + totalPages * 2;
        Integer imageObjectId = hasPdfImage ? fontObjectId + 1 : null;
        StringBuilder kids = new StringBuilder();
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int pageObjectId = 3 + pageIndex * 2;
            kids.append(pageObjectId).append(" 0 R ");
        }
        objects.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + totalPages + " >>");
        String pageResources = imageObjectId == null
                ? "<< /Font << /F1 " + fontObjectId + " 0 R >> >>"
                : "<< /Font << /F1 " + fontObjectId + " 0 R >> /XObject << /Im1 " + imageObjectId + " 0 R >> >>";
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int contentObjectId = 4 + pageIndex * 2;
            String content = pdfPageContent(report, brand, logo, pages.get(pageIndex), pageIndex + 1, totalPages, hasPdfImage);
            byte[] stream = content.getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources " + pageResources + " /Contents " + contentObjectId + " 0 R >>");
            objects.add("<< /Length " + stream.length + " >>\nstream\n" + content + "endstream");
        }
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        if (hasPdfImage) {
            objects.add(logo.pdfImageObject());
        }
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new java.util.ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(index + 1).append(" 0 obj\n")
                    .append(objects.get(index))
                    .append("\nendobj\n");
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(xrefOffset).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private List<List<String>> paginatePdfBodyLines(List<String> bodyLines, int maxLinesPerPage) {
        List<List<String>> pages = new ArrayList<>();
        List<String> currentPage = new ArrayList<>();
        for (String line : bodyLines) {
            if (currentPage.size() >= maxLinesPerPage) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
            }
            currentPage.add(line);
        }
        if (currentPage.isEmpty()) {
            currentPage.add("");
        }
        pages.add(currentPage);
        return pages;
    }

    private String pdfPageContent(Report report, ExportTheme brand, LogoAsset logo, List<String> pageLines, int currentPage, int totalPages, boolean hasPdfImage) {
        StringBuilder content = new StringBuilder();
        content.append("%% PDF-PAGE ").append(currentPage).append("\n");
        content.append(pdfLogoBlock(brand, logo));
        if (hasPdfImage) {
            content.append("q\n");
            content.append("72 0 0 24 50 792 cm\n");
            content.append("/Im1 Do\n");
            content.append("Q\n");
        }
        content.append("%% PDF-HEADER\n");
        content.append("BT\n/F1 12 Tf\n50 742 Td\n14 TL\n");
        content.append("(").append(pdfText(brand.header())).append(") Tj\nT*\n");
        String pageTitle = currentPage == 1 ? resolvedCoverTitle(report.title(), brand.layout()) : report.title();
        content.append("(").append(pdfText(pageTitle)).append(") Tj\nT*\n");
        content.append("ET\n");
        content.append("%% PDF-BODY\n");
        content.append("BT\n/F1 11 Tf\n50 690 Td\n14 TL\n");
        for (String line : pageLines) {
            content.append("(").append(pdfText(line)).append(") Tj\nT*\n");
        }
        content.append("ET\n");
        content.append("%% PDF-FOOTER\n");
        content.append("BT\n/F1 10 Tf\n50 70 Td\n12 TL\n");
        content.append("(").append(pdfText(brand.footer())).append(") Tj\nT*\n");
        content.append("(").append(pdfText(pageFooter(currentPage, totalPages))).append(") Tj\nT*\n");
        content.append("ET\n");
        return content.toString();
    }

    private String pdfLogoBlock(ExportTheme brand, LogoAsset logo) {
        double[] rgb = pdfRgb(brand.primaryColor());
        String logoComment;
        if (logo.pdfImageObjectId() == null) {
            logoComment = "%% PDF-LOGO logoObjectKey=%s companyName=%s";
        } else if ("svg".equals(logo.extension())) {
            logoComment = "%% PDF-LOGO-VECTOR logoObjectKey=%s companyName=%s";
        } else {
            logoComment = "%% PDF-LOGO-BINARY logoObjectKey=%s companyName=%s";
        }
        return """
                %s
                q
                %.3f %.3f %.3f rg
                50 792 72 24 re
                f
                Q
                BT
                /F1 10 Tf
                130 800 Td
                (%s) Tj
                ET
                """.formatted(
                logoComment.formatted(
                        pdfText(brand.logoObjectKey()),
                        pdfText(brand.companyName())
                ),
                rgb[0],
                rgb[1],
                rgb[2],
                pdfText(brand.companyName())
        );
    }

    private double[] pdfRgb(String color) {
        String value = color == null ? "" : color.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return new double[]{0.122d, 0.306d, 0.475d};
        }
        try {
            int red = Integer.parseInt(value.substring(0, 2), 16);
            int green = Integer.parseInt(value.substring(2, 4), 16);
            int blue = Integer.parseInt(value.substring(4, 6), 16);
            return new double[]{red / 255d, green / 255d, blue / 255d};
        } catch (NumberFormatException ex) {
            return new double[]{0.122d, 0.306d, 0.475d};
        }
    }

    private byte[] renderDocx(Report report, List<Map<String, Object>> sections, ExportTheme brand) {
        try {
            LogoAsset logo = resolveLogoAsset(brand);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                writeZipEntry(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Default Extension="%s" ContentType="%s"/>
                          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                          <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
                          <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
                          <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                          <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
                          <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                        </Types>
                        """.formatted(logo.extension(), logo.contentType()));
                writeZipEntry(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                        </Relationships>
                        """);
                writeZipEntry(zip, "docProps/core.xml", corePropertiesXml(report, brand));
                writeZipEntry(zip, "word/_rels/document.xml.rels", wordDocumentRelationshipsXml(logo.mediaFileName()));
                writeZipEntry(zip, "word/media/" + logo.mediaFileName(), logo.content());
                writeZipEntry(zip, "word/header1.xml", headerXml(brand));
                writeZipEntry(zip, "word/footer1.xml", footerXml(sections, brand));
                writeZipEntry(zip, "word/styles.xml", stylesXml(brand));
                writeZipEntry(zip, "word/settings.xml", settingsXml());
                writeZipEntry(zip, "word/document.xml", documentXml(report, sections, brand));
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to render report docx", ex);
        }
    }

    private byte[] renderPptx(Report report, List<Map<String, Object>> sections, ExportTheme brand) {
        try {
            LogoAsset logo = resolveLogoAsset(brand);
            List<String> slideXmlEntries = pptSlides(report, sections, brand);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                writeZipEntry(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Default Extension="%s" ContentType="%s"/>
                          <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                          <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
                          <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
                          <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
                          <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
                          %s
                        </Types>
                        """.formatted(logo.extension(), logo.contentType(), pptSlideContentTypes(slideXmlEntries.size())));
                writeZipEntry(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
                          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                        </Relationships>
                        """);
                writeZipEntry(zip, "docProps/core.xml", corePropertiesXml(report, brand));
                writeZipEntry(zip, "ppt/presentation.xml", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                          <p:sldMasterIdLst>
                            <p:sldMasterId id="2147483648" r:id="rIdMaster1"/>
                          </p:sldMasterIdLst>
                          <p:sldIdLst>
                            %s
                          </p:sldIdLst>
                          <p:sldSz cx="12192000" cy="6858000" type="screen16x9"/>
                          <p:notesSz cx="6858000" cy="9144000"/>
                        </p:presentation>
                        """.formatted(pptSlideIds(slideXmlEntries.size())));
                writeZipEntry(zip, "ppt/_rels/presentation.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rIdMaster1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
                          %s
                        </Relationships>
                        """.formatted(pptSlideRelationships(slideXmlEntries.size())));
                writeZipEntry(zip, "ppt/slideMasters/slideMaster1.xml", pptSlideMasterXml());
                writeZipEntry(zip, "ppt/slideMasters/_rels/slideMaster1.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rIdLayout1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                          <Relationship Id="rIdTheme1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
                        </Relationships>
                        """);
                writeZipEntry(zip, "ppt/slideLayouts/slideLayout1.xml", pptSlideLayoutXml());
                writeZipEntry(zip, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rIdMaster1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
                        </Relationships>
                        """);
                writeZipEntry(zip, "ppt/theme/theme1.xml", pptThemeXml());
                for (int slideIndex = 0; slideIndex < slideXmlEntries.size(); slideIndex++) {
                    int slideNo = slideIndex + 1;
                    writeZipEntry(zip, "ppt/slides/slide" + slideNo + ".xml", slideXmlEntries.get(slideIndex));
                    writeZipEntry(zip, "ppt/slides/_rels/slide" + slideNo + ".xml.rels", """
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rIdLayout1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
                          <Relationship Id="rIdLogo" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/%s"/>
                        </Relationships>
                        """.formatted(logo.mediaFileName()));
                }
                writeZipEntry(zip, "ppt/media/" + logo.mediaFileName(), logo.content());
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to render report pptx", ex);
        }
    }

    private String pptSlideContentTypes(int slideCount) {
        StringBuilder builder = new StringBuilder();
        for (int slideIndex = 1; slideIndex <= slideCount; slideIndex++) {
            builder.append("<Override PartName=\"/ppt/slides/slide")
                    .append(slideIndex)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n");
        }
        return builder.toString().stripTrailing();
    }

    private String pptSlideIds(int slideCount) {
        StringBuilder builder = new StringBuilder();
        for (int slideIndex = 1; slideIndex <= slideCount; slideIndex++) {
            builder.append("<p:sldId id=\"")
                    .append(255 + slideIndex)
                    .append("\" r:id=\"rId")
                    .append(slideIndex)
                    .append("\"/>\n");
        }
        return builder.toString().stripTrailing();
    }

    private String pptSlideRelationships(int slideCount) {
        StringBuilder builder = new StringBuilder();
        for (int slideIndex = 1; slideIndex <= slideCount; slideIndex++) {
            builder.append("<Relationship Id=\"rId")
                    .append(slideIndex)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide")
                    .append(slideIndex)
                    .append(".xml\"/>\n");
        }
        return builder.toString().stripTrailing();
    }

    private String pptSlideMasterXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sldMaster xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <p:cSld>
                    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F7F9FC"/></a:solidFill></p:bgPr></p:bg>
                    <p:spTree>
                      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
                      <p:sp>
                        <p:nvSpPr>
                          <p:cNvPr id="2" name="Master Title Placeholder"/>
                          <p:cNvSpPr/>
                          <p:nvPr><p:ph type="title"/></p:nvPr>
                        </p:nvSpPr>
                        <p:spPr/>
                        <p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody>
                      </p:sp>
                      <p:sp>
                        <p:nvSpPr>
                          <p:cNvPr id="3" name="Master Body Placeholder"/>
                          <p:cNvSpPr/>
                          <p:nvPr><p:ph type="body" idx="1"/></p:nvPr>
                        </p:nvSpPr>
                        <p:spPr/>
                        <p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody>
                      </p:sp>
                      <p:sp>
                        <p:nvSpPr>
                          <p:cNvPr id="4" name="Master Footer Placeholder"/>
                          <p:cNvSpPr/>
                          <p:nvPr><p:ph type="ftr" sz="quarter" idx="10"/></p:nvPr>
                        </p:nvSpPr>
                        <p:spPr/>
                        <p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody>
                      </p:sp>
                    </p:spTree>
                  </p:cSld>
                  <p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/>
                  <p:sldLayoutIdLst>
                    <p:sldLayoutId id="1" r:id="rIdLayout1"/>
                  </p:sldLayoutIdLst>
                  <p:txStyles/>
                </p:sldMaster>
                """;
    }

    private String pptSlideLayoutXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                    type="titleOnly" preserve="1">
                  <p:cSld name="Title Only">
                    <p:spTree>
                      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
                      <p:sp>
                        <p:nvSpPr>
                          <p:cNvPr id="2" name="Title Placeholder 1"/>
                          <p:cNvSpPr/>
                          <p:nvPr><p:ph type="title"/></p:nvPr>
                        </p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="685800" y="457200"/><a:ext cx="10972800" cy="914400"/></a:xfrm></p:spPr>
                        <p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody>
                      </p:sp>
                      <p:sp>
                        <p:nvSpPr>
                          <p:cNvPr id="3" name="Body Placeholder 2"/>
                          <p:cNvSpPr txBox="1"/>
                          <p:nvPr><p:ph type="body" idx="1"/></p:nvPr>
                        </p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="685800" y="1600200"/><a:ext cx="10972800" cy="4572000"/></a:xfrm></p:spPr>
                        <p:txBody><a:bodyPr wrap="square"/><a:lstStyle/><a:p/></p:txBody>
                      </p:sp>
                      <p:sp>
                        <p:nvSpPr>
                          <p:cNvPr id="4" name="Footer Placeholder 3"/>
                          <p:cNvSpPr txBox="1"/>
                          <p:nvPr><p:ph type="ftr" sz="quarter" idx="10"/></p:nvPr>
                        </p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="685800" y="6248400"/><a:ext cx="10972800" cy="304800"/></a:xfrm></p:spPr>
                        <p:txBody><a:bodyPr wrap="square"/><a:lstStyle/><a:p/></p:txBody>
                      </p:sp>
                    </p:spTree>
                  </p:cSld>
                  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
                </p:sldLayout>
                """;
    }

    private String pptThemeXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Office Theme">
                  <a:themeElements>
                    <a:clrScheme name="Office">
                      <a:dk1><a:srgbClr val="1F1F1F"/></a:dk1>
                      <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
                      <a:dk2><a:srgbClr val="1F4E79"/></a:dk2>
                      <a:lt2><a:srgbClr val="F7F9FC"/></a:lt2>
                      <a:accent1><a:srgbClr val="1F4E79"/></a:accent1>
                      <a:accent2><a:srgbClr val="5B9BD5"/></a:accent2>
                      <a:accent3><a:srgbClr val="70AD47"/></a:accent3>
                      <a:accent4><a:srgbClr val="FFC000"/></a:accent4>
                      <a:accent5><a:srgbClr val="C00000"/></a:accent5>
                      <a:accent6><a:srgbClr val="7030A0"/></a:accent6>
                      <a:hlink><a:srgbClr val="0563C1"/></a:hlink>
                      <a:folHlink><a:srgbClr val="954F72"/></a:folHlink>
                    </a:clrScheme>
                    <a:fontScheme name="Office">
                      <a:majorFont><a:latin typeface="Aptos"/></a:majorFont>
                      <a:minorFont><a:latin typeface="Aptos"/></a:minorFont>
                    </a:fontScheme>
                    <a:fmtScheme name="Office"/>
                  </a:themeElements>
                </a:theme>
                """;
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        writeZipEntry(zip, name, content.stripLeading().getBytes(StandardCharsets.UTF_8));
    }

    private void writeZipEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private String corePropertiesXml(Report report, ExportTheme brand) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>%s</dc:title>
                  <dc:creator>%s</dc:creator>
                </cp:coreProperties>
                """.formatted(xml(report.title()), xml(brand.companyName()));
    }

    private String wordDocumentRelationshipsXml(String mediaFileName) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rIdLogo" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/%s"/>
                  <Relationship Id="rIdHeader" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>
                  <Relationship Id="rIdFooter" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
                  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                  <Relationship Id="rIdSettings" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>
                </Relationships>
                """.formatted(xml(mediaFileName));
    }

    private LogoAsset resolveLogoAsset(ExportTheme brand) {
        String objectKey = brand.logoObjectKey();
        try {
            Optional<ReportExportStorage.StoredObject> storedObject = reportExportStorage.readObject(objectKey);
            if (storedObject.isPresent()) {
                ReportExportStorage.StoredObject object = storedObject.get();
                Optional<String> extension = logoExtension(object.contentType(), object.objectKey());
                if (extension.isPresent() && object.content() != null && object.content().length > 0) {
                    return new LogoAsset("logo." + extension.get(), extension.get(), object.contentType(), Arrays.copyOf(object.content(), object.content().length));
                }
            }
        } catch (IOException ignored) {
            // Export should remain available even when a brand asset is temporarily missing from object storage.
        }
        return new LogoAsset("logo.svg", "svg", "image/svg+xml", logoSvg(brand).stripLeading().getBytes(StandardCharsets.UTF_8));
    }

    private LogoAsset resolvePdfLogoAsset(ExportTheme brand) {
        LogoAsset asset = resolveLogoAsset(brand);
        if ("svg".equals(asset.extension())) {
            return asset.withPdfImageObject(pdfSvgFormObject(asset));
        }
        if (!"png".equals(asset.extension()) && !"jpg".equals(asset.extension())) {
            return asset;
        }
        return asset.withPdfImageObject(pdfImageObject(asset));
    }

    private String pdfImageObject(LogoAsset asset) {
        String filter = "png".equals(asset.extension()) ? "DCTDecode" : "DCTDecode";
        String encoded = new String(asset.content(), StandardCharsets.ISO_8859_1);
        return "<< /Type /XObject /Subtype /Image /Width 1 /Height 1 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /%s /Length %d >>\nstream\n%s\nendstream"
                .formatted(filter, asset.content().length, encoded);
    }

    private String pdfSvgFormObject(LogoAsset asset) {
        String svg = new String(asset.content(), StandardCharsets.UTF_8);
        double width = svgNumberAttribute(svg, "width").orElse(120d);
        double height = svgNumberAttribute(svg, "height").orElse(40d);
        String fill = svgAttribute(svg, "fill").orElse("#1F4E79");
        double[] rgb = pdfRgb(fill);
        String text = svgText(svg).orElse("");
        String stream = """
                q
                %.3f %.3f %.3f rg
                0 0 %.1f %.1f re
                f
                Q
                BT
                /F1 10 Tf
                8 %.1f Td
                (%s) Tj
                ET
                """.formatted(rgb[0], rgb[1], rgb[2], width, height, Math.max(10d, height - 16d), pdfText(text));
        return "<< /Type /XObject /Subtype /Form /BBox [0 0 %.1f %.1f] /Resources << /Font << /F1 5 0 R >> >> /Length %d >>\nstream\n%sendstream"
                .formatted(width, height, stream.getBytes(StandardCharsets.ISO_8859_1).length, stream);
    }

    private Optional<Double> svgNumberAttribute(String svg, String attribute) {
        return svgAttribute(svg, attribute)
                .map(value -> value.replaceAll("[^0-9.\\-]", ""))
                .filter(value -> !value.isBlank())
                .flatMap(value -> {
                    try {
                        return Optional.of(Double.parseDouble(value));
                    } catch (NumberFormatException ignored) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<String> svgAttribute(String svg, String attribute) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(attribute + "\\s*=\\s*[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(svg == null ? "" : svg);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private Optional<String> svgText(String svg) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<text\\b[^>]*>(.*?)</text>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                .matcher(svg == null ? "" : svg);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).replaceAll("<[^>]+>", "").trim());
    }

    private Optional<String> logoExtension(String contentType, String objectKey) {
        String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        if ("image/svg+xml".equals(type)) {
            return Optional.of("svg");
        }
        if ("image/png".equals(type)) {
            return Optional.of("png");
        }
        if ("image/jpeg".equals(type) || "image/jpg".equals(type)) {
            return Optional.of("jpg");
        }
        String key = objectKey == null ? "" : objectKey.toLowerCase(java.util.Locale.ROOT);
        if (key.endsWith(".svg")) {
            return Optional.of("svg");
        }
        if (key.endsWith(".png")) {
            return Optional.of("png");
        }
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) {
            return Optional.of("jpg");
        }
        return Optional.empty();
    }

    private record LogoAsset(String mediaFileName, String extension, String contentType, byte[] content, Integer pdfImageObjectId, String pdfImageObject) {
        private LogoAsset(String mediaFileName, String extension, String contentType, byte[] content) {
            this(mediaFileName, extension, contentType, content, null, null);
        }

        private LogoAsset withPdfImageObject(String pdfImageObject) {
            return new LogoAsset(mediaFileName, extension, contentType, content, 6, pdfImageObject);
        }
    }

    private String logoSvg(ExportTheme brand) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="320" height="96" viewBox="0 0 320 96" data-logo-object-key="%s">
                  <rect width="320" height="96" rx="8" fill="%s"/>
                  <text x="24" y="52" font-family="Arial, sans-serif" font-size="22" fill="#FFFFFF">companyName=%s</text>
                  <text x="24" y="78" font-family="Arial, sans-serif" font-size="12" fill="#FFFFFF">primaryColor=%s</text>
                </svg>
                """.formatted(
                xml(brand.logoObjectKey()),
                xml(brand.primaryColor()),
                xml(brand.companyName()),
                xml(brand.primaryColor())
        );
    }

    private String documentXml(Report report, List<Map<String, Object>> sections, ExportTheme brand) {
        StringBuilder body = new StringBuilder();
        body.append(paragraph(brand.companyName()));
        body.append(logoDrawingParagraph());
        body.append(styledParagraph(resolvedCoverTitle(report.title(), brand.layout()), "Title"));
        body.append(styledParagraph(report.title(), "Heading1"));
        body.append(styledParagraph(brand.layout().tocTitle(), "TOCHeading"));
        body.append(tocFieldParagraph());
        int sectionNo = 1;
        for (Map<String, Object> section : sections) {
            String heading = String.valueOf(section.getOrDefault("heading", "Untitled Section"));
            body.append(tocParagraph(sectionNo, heading));
            sectionNo++;
        }
        sectionNo = 1;
        for (Map<String, Object> section : sections) {
            String heading = String.valueOf(section.getOrDefault("heading", "Untitled Section"));
            body.append(bookmarkedHeadingParagraph(sectionNo, layoutSectionTitle(sectionNo, heading, brand.layout())));
            body.append(paragraph(String.valueOf(section.getOrDefault("content", ""))));
            Object citations = section.get("citations");
            if (citations instanceof List<?> citationList && !citationList.isEmpty()) {
                body.append(paragraph("References: " + String.join(", ", citationList.stream().map(String::valueOf).toList())));
            }
            sectionNo++;
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <w:body>
                %s
                    <w:sectPr>
                      <w:headerReference w:type="default" r:id="rIdHeader"/>
                      <w:footerReference w:type="default" r:id="rIdFooter"/>
                    </w:sectPr>
                  </w:body>
                </w:document>
                """.formatted(body);
    }

    private String tocLine(int sectionNo, String heading) {
        return sectionNo + ". " + heading + " .... " + (sectionNo + 1);
    }

    private String tocLine(int sectionNo, String heading, int pageNo) {
        return sectionNo + ". " + heading + " .... " + pageNo;
    }

    private int pdfLinePage(int lineIndex, int maxLinesPerPage) {
        return (lineIndex / maxLinesPerPage) + 1;
    }

    private String pageFooter(int currentPage, int totalPages) {
        return "Page " + currentPage + " of " + totalPages;
    }

    private String headerXml(ExportTheme brand) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:p><w:pPr><w:pStyle w:val="Header"/></w:pPr><w:r><w:t>%s</w:t></w:r></w:p>
                  <w:p><w:pPr><w:pStyle w:val="Header"/></w:pPr><w:r><w:t>%s</w:t></w:r></w:p>
                </w:hdr>
                """.formatted(
                xml(brand.companyName()),
                xml(brand.header())
        );
    }

    private String footerXml(List<Map<String, Object>> sections, ExportTheme brand) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:p><w:pPr><w:pStyle w:val="Footer"/></w:pPr><w:r><w:t>%s</w:t></w:r></w:p>
                  <w:p>
                    <w:pPr><w:pStyle w:val="Footer"/></w:pPr>
                    <w:r><w:t xml:space="preserve">Page </w:t></w:r>
                    <w:fldSimple w:instr=" PAGE "/>
                    <w:r><w:t xml:space="preserve"> of </w:t></w:r>
                    <w:fldSimple w:instr=" NUMPAGES "/>
                  </w:p>
                </w:ftr>
                """.formatted(
                xml(brand.footer())
        );
    }

    private String logoDrawingParagraph() {
        return """
                    <w:p>
                      <w:r>
                        <w:drawing>
                          <wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                              xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                              xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                            <wp:extent cx="2438400" cy="731520"/>
                            <wp:docPr id="1" name="Enterprise Logo"/>
                            <a:graphic>
                              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                <pic:pic>
                                  <pic:nvPicPr><pic:cNvPr id="1" name="Enterprise Logo"/><pic:cNvPicPr/></pic:nvPicPr>
                                  <pic:blipFill><a:blip r:embed="rIdLogo"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                                  <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2438400" cy="731520"/></a:xfrm><a:prstGeom prst="rect"/></pic:spPr>
                                </pic:pic>
                              </a:graphicData>
                            </a:graphic>
                          </wp:inline>
                        </w:drawing>
                      </w:r>
                    </w:p>
                """;
    }

    private String paragraph(String text) {
        return "    <w:p><w:r><w:t>" + xml(text) + "</w:t></w:r></w:p>\n";
    }

    private String styledParagraph(String text, String styleId) {
        return """
                <w:p>
                  <w:pPr><w:pStyle w:val="%s"/></w:pPr>
                  <w:r><w:t>%s</w:t></w:r>
                </w:p>
                """.formatted(xml(styleId), xml(text));
    }

    private String tocParagraph(int sectionNo, String heading) {
        return """
                <w:p>
                  <w:hyperlink w:anchor="%s">
                    <w:r><w:t>%s</w:t></w:r>
                  </w:hyperlink>
                </w:p>
                """.formatted(xml(sectionBookmarkName(sectionNo)), xml(tocLine(sectionNo, heading)));
    }

    private String bookmarkedHeadingParagraph(int sectionNo, String heading) {
        return """
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:bookmarkStart w:id="%d" w:name="%s"/>
                  <w:r><w:t>%s</w:t></w:r>
                  <w:bookmarkEnd w:id="%d"/>
                </w:p>
                """.formatted(sectionNo, xml(sectionBookmarkName(sectionNo)), xml(sectionNo + ". " + heading), sectionNo);
    }

    private String sectionBookmarkName(int sectionNo) {
        return "section-" + sectionNo;
    }

    private String tocFieldParagraph() {
        return """
                <w:p>
                  <w:r><w:fldSimple w:instr="TOC \\o &quot;1-3&quot; \\h \\z \\u"/></w:r>
                </w:p>
                """;
    }

    private String stylesXml(ExportTheme brand) {
        String fontFamily = xml(brand.fontFamily());
        String primaryColor = wordColor(brand.primaryColor());
        int titleSize = brand.layout().titleFontSize() * 2;
        int headingSize = Math.max(brand.layout().bodyFontSize() * 2, 24);
        int headerSize = brand.layout().headerFontSize() * 2;
        int footerSize = brand.layout().footerFontSize() * 2;
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                    <w:name w:val="Normal"/>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Title">
                    <w:name w:val="Title"/>
                    <w:basedOn w:val="Normal"/>
                    <w:rPr>
                      <w:rFonts w:ascii="%s" w:hAnsi="%s"/>
                      <w:color w:val="%s"/>
                      <w:b/>
                      <w:sz w:val="%d"/>
                    </w:rPr>
                    <w:qFormat/>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Heading1">
                    <w:name w:val="heading 1"/>
                    <w:basedOn w:val="Normal"/>
                    <w:rPr>
                      <w:rFonts w:ascii="%s" w:hAnsi="%s"/>
                      <w:color w:val="%s"/>
                      <w:b/>
                      <w:sz w:val="%d"/>
                    </w:rPr>
                    <w:qFormat/>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="TOCHeading">
                    <w:name w:val="TOC Heading"/>
                    <w:basedOn w:val="Normal"/>
                    <w:rPr>
                      <w:rFonts w:ascii="%s" w:hAnsi="%s"/>
                      <w:color w:val="%s"/>
                      <w:b/>
                      <w:sz w:val="%d"/>
                    </w:rPr>
                    <w:qFormat/>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Header">
                    <w:name w:val="Header"/>
                    <w:basedOn w:val="Normal"/>
                    <w:rPr>
                      <w:rFonts w:ascii="%s" w:hAnsi="%s"/>
                      <w:color w:val="%s"/>
                      <w:b/>
                      <w:sz w:val="%d"/>
                    </w:rPr>
                  </w:style>
                  <w:style w:type="paragraph" w:styleId="Footer">
                    <w:name w:val="Footer"/>
                    <w:basedOn w:val="Normal"/>
                    <w:rPr>
                      <w:rFonts w:ascii="%s" w:hAnsi="%s"/>
                      <w:color w:val="%s"/>
                      <w:sz w:val="%d"/>
                    </w:rPr>
                  </w:style>
                </w:styles>
                """.formatted(
                fontFamily, fontFamily, primaryColor, titleSize,
                fontFamily, fontFamily, primaryColor, headingSize,
                fontFamily, fontFamily, primaryColor, headingSize,
                fontFamily, fontFamily, primaryColor, headerSize,
                fontFamily, fontFamily, primaryColor, footerSize
        );
    }

    private String wordColor(String color) {
        String value = color == null ? "" : color.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.matches("[0-9A-Fa-f]{6}")) {
            return value.toUpperCase(java.util.Locale.ROOT);
        }
        return "1F4E79";
    }

    private String settingsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:updateFields w:val="true"/>
                </w:settings>
                """;
    }

    private List<String> pptSlides(Report report, List<Map<String, Object>> sections, ExportTheme brand) {
        List<String> slides = new ArrayList<>();
        int totalSlides = Math.max(3, sections.size() + 3);
        slides.add(slideXml(resolvedCoverTitle(report.title(), brand.layout()), coverSlideBody(brand), brand, 1, totalSlides));
        slides.add(slideXml(brand.layout().tocTitle(), tocSlideBody(sections, 3, brand.layout()), brand, 2, totalSlides));
        int sectionNo = 1;
        for (Map<String, Object> section : sections) {
            slides.add(slideXml(
                    layoutSectionTitle(sectionNo, String.valueOf(section.getOrDefault("heading", "Untitled Section")), brand.layout()),
                    sectionSlideBody(section),
                    brand,
                    sectionNo + 2,
                    totalSlides
            ));
            sectionNo++;
        }
        slides.add(slideXml(brand.layout().closingTitle(), closingSlideBody(brand), brand, totalSlides, totalSlides));
        return slides;
    }

    private String coverSlideBody(ExportTheme brand) {
        String coverHeading = brand.layout().coverTitle().isBlank() ? "Cover" : brand.layout().coverTitle();
        return """
                %s

                %s
                %s
                """.formatted(coverHeading, brand.companyName(), brand.header()).trim();
    }

    private String tocSlideBody(List<Map<String, Object>> sections, int firstSectionSlideNo, ExportLayout layout) {
        StringBuilder body = new StringBuilder();
        body.append(layout.tocTitle()).append("\n");
        int tocSectionNo = 1;
        for (Map<String, Object> section : sections) {
            body.append(tocLine(tocSectionNo, String.valueOf(section.getOrDefault("heading", "Untitled Section")), firstSectionSlideNo + tocSectionNo - 1)).append("\n");
            tocSectionNo++;
        }
        return body.toString().trim();
    }

    private String sectionSlideBody(Map<String, Object> section) {
        StringBuilder body = new StringBuilder();
        body.append(String.valueOf(section.getOrDefault("content", "")));
        Object citations = section.get("citations");
        if (citations instanceof List<?> citationList && !citationList.isEmpty()) {
            body.append("\n\nReferences: ")
                    .append(String.join(", ", citationList.stream().map(String::valueOf).toList()));
        }
        return body.toString().trim();
    }

    private String closingSlideBody(ExportTheme brand) {
        List<String> lines = new ArrayList<>();
        if (!brand.layout().closingMessage().isBlank()) {
            lines.add(brand.layout().closingMessage());
        }
        lines.add(brand.companyName());
        if (!brand.header().isBlank()) {
            lines.add(brand.header());
        }
        if (!brand.layout().closingContact().isBlank()) {
            lines.add(brand.layout().closingContact());
        } else if (!brand.footer().isBlank()) {
            lines.add(brand.footer());
        }
        return String.join("\n", lines).trim();
    }

    private String slideXml(String title, String bodyText, ExportTheme brand, int currentSlide, int totalSlides) {
        String headerText = brand.companyName() + "\n" + brand.header();
        String footerText = brand.footer() + "\n" + pageFooter(currentSlide, totalSlides);
        String fontFamily = xml(brand.fontFamily());
        String primaryColor = wordColor(brand.primaryColor());
        int titleSize = brand.layout().titleFontSize() * 100;
        int bodySize = brand.layout().bodyFontSize() * 100;
        int headerSize = brand.layout().headerFontSize() * 100;
        int footerSize = brand.layout().footerFontSize() * 100;
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                    xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <p:cSld>
                    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F7F9FC"/></a:solidFill></p:bgPr></p:bg>
                    <p:spTree>
                      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
                      <p:pic>
                        <p:nvPicPr><p:cNvPr id="4" name="Enterprise Logo"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
                        <p:blipFill><a:blip r:embed="rIdLogo"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
                        <p:spPr><a:xfrm><a:off x="685800" y="152400"/><a:ext cx="2438400" cy="731520"/></a:xfrm><a:prstGeom prst="rect"/></p:spPr>
                      </p:pic>
                      <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="685800" y="457200"/><a:ext cx="10972800" cy="914400"/></a:xfrm><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></p:spPr>
                        <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" sz="%d" b="1"><a:solidFill><a:srgbClr val="%s"/></a:solidFill><a:latin typeface="%s"/></a:rPr><a:t>%s</a:t></a:r></a:p></p:txBody>
                      </p:sp>
                      <p:sp>
                        <p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="685800" y="1600200"/><a:ext cx="10972800" cy="4572000"/></a:xfrm><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></p:spPr>
                        <p:txBody><a:bodyPr wrap="square"/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" sz="%d"><a:latin typeface="%s"/></a:rPr><a:t>%s</a:t></a:r></a:p></p:txBody>
                      </p:sp>
                      <p:sp>
                        <p:nvSpPr><p:cNvPr id="5" name="Header"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="3474720" y="152400"/><a:ext cx="7955280" cy="457200"/></a:xfrm><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></p:spPr>
                        <p:txBody><a:bodyPr wrap="square"/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" sz="%d" b="1"><a:solidFill><a:srgbClr val="%s"/></a:solidFill><a:latin typeface="%s"/></a:rPr><a:t>%s</a:t></a:r></a:p></p:txBody>
                      </p:sp>
                      <p:sp>
                        <p:nvSpPr><p:cNvPr id="6" name="Footer"/><p:cNvSpPr/><p:nvPr><p:ph type="ftr" sz="quarter" idx="10"/></p:nvPr></p:nvSpPr>
                        <p:spPr><a:xfrm><a:off x="685800" y="6248400"/><a:ext cx="10972800" cy="304800"/></a:xfrm><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></p:spPr>
                        <p:txBody><a:bodyPr wrap="square"/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" sz="%d"><a:solidFill><a:srgbClr val="%s"/></a:solidFill><a:latin typeface="%s"/></a:rPr><a:t>%s</a:t></a:r></a:p></p:txBody>
                      </p:sp>
                    </p:spTree>
                  </p:cSld>
                  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
                </p:sld>
                """.formatted(
                titleSize, primaryColor, fontFamily, xml(title),
                bodySize, fontFamily, xml(bodyText),
                headerSize, primaryColor, fontFamily, xml(headerText),
                footerSize, primaryColor, fontFamily, xml(footerText)
        );
    }

    private record ExportTheme(
            String companyName,
            String logoObjectKey,
            String header,
            String footer,
            String fontFamily,
            String primaryColor,
            ExportLayout layout
    ) {
        private Map<String, Object> brandMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("companyName", companyName);
            values.put("logoObjectKey", logoObjectKey);
            values.put("header", header);
            values.put("footer", footer);
            values.put("fontFamily", fontFamily);
            values.put("primaryColor", primaryColor);
            Map<String, Object> layoutValues = new LinkedHashMap<>();
            layoutValues.put("coverTitle", layout.coverTitle());
            layoutValues.put("tocTitle", layout.tocTitle());
            layoutValues.put("bodyTitlePrefix", layout.bodyTitlePrefix());
            layoutValues.put("titleFontSize", layout.titleFontSize());
            layoutValues.put("bodyFontSize", layout.bodyFontSize());
            layoutValues.put("headerFontSize", layout.headerFontSize());
            layoutValues.put("footerFontSize", layout.footerFontSize());
            layoutValues.put("pageWidth", layout.pageWidth());
            layoutValues.put("closingTitle", layout.closingTitle());
            layoutValues.put("closingMessage", layout.closingMessage());
            layoutValues.put("closingContact", layout.closingContact());
            values.put("layout", layoutValues);
            return values;
        }
    }

    private String layoutSectionTitle(int sectionNo, String heading, ExportLayout layout) {
        String prefix = layout.bodyTitlePrefix().trim();
        if (prefix.isEmpty()) {
            return sectionNo + ". " + heading;
        }
        return prefix + " " + sectionNo + ". " + heading;
    }

    private String resolvedCoverTitle(String reportTitle, ExportLayout layout) {
        String coverTitle = layout.coverTitle().trim();
        return coverTitle.isEmpty() ? reportTitle : coverTitle;
    }

    private record ExportLayout(
            String coverTitle,
            String tocTitle,
            String bodyTitlePrefix,
            int titleFontSize,
            int bodyFontSize,
            int headerFontSize,
            int footerFontSize,
            int pageWidth,
            String closingTitle,
            String closingMessage,
            String closingContact
    ) {
    }

    private String xml(Object value) {
        return String.valueOf(value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String pdfText(Object value) {
        return String.valueOf(value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x09\\x0A\\x0D\\x20-\\x7E]", "?");
    }

    private ExportFormat exportFormat(String format) {
        String normalized = format == null ? "markdown" : format.toLowerCase();
        return switch (normalized) {
            case "md", "markdown" -> new ExportFormat("markdown", "md", "text/markdown; charset=UTF-8", true, false);
            case "docx", "word" -> new ExportFormat("docx", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true, true);
            case "pdf" -> new ExportFormat("pdf", "pdf", "application/pdf", true, false, true);
            case "ppt", "pptx", "powerpoint" -> new ExportFormat("pptx", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", true, false, false, true);
            default -> new ExportFormat(normalized, normalized, "application/octet-stream", false, false);
        };
    }

    private record ExportFormat(String name, String extension, String contentType, boolean supported, boolean wordDocument, boolean pdfDocument, boolean powerPointDocument) {
        private ExportFormat(String name, String extension, String contentType, boolean supported, boolean wordDocument) {
            this(name, extension, contentType, supported, wordDocument, false, false);
        }

        private ExportFormat(String name, String extension, String contentType, boolean supported, boolean wordDocument, boolean pdfDocument) {
            this(name, extension, contentType, supported, wordDocument, pdfDocument, false);
        }
    }

    public Map<String, Object> getExportStatus(Long reportId, Long exportFileId) {
        return reportExportFileRepository.findByReportIdAndExportFileId(reportId, exportFileId)
                .orElseGet(() -> Map.of(
                        "reportId", reportId,
                        "exportFileId", exportFileId,
                        "status", "expired",
                        "downloadPolicy", "presigned_url"
                ));
    }

    private String controlledDownloadUrl(Long exportFileId) {
        return "/api/v1/files/report-exports/" + exportFileId + "/download-url";
    }

    private void ensureReportOwner(Report report, String message) {
        if (!report.ownerUserId().equals(currentUserId())) {
            throw new SecurityException(message);
        }
    }

    public Map<String, Object> rollbackVersion(Long reportId, Long versionId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        ensureReportOwner(report, "report rollback access denied");
        Long actorUserId = currentUserId();
        Long newVersionId = reportContentRepository.createRollbackVersion(reportId, versionId, actorUserId);
        reportRepository.save(new Report(report.id(), report.title(), report.ownerUserId(), report.status(), newVersionId));
        auditRepository.save(new OperationLog(
                null,
                actorUserId,
                "report_version_rollback",
                "report",
                reportId,
                "succeeded",
                Map.of("sourceVersionId", versionId, "newVersionId", newVersionId),
                java.time.OffsetDateTime.now()
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("sourceVersionId", versionId);
        result.put("newVersionId", newVersionId);
        result.put("currentVersionId", newVersionId);
        result.put("changeReason", "rollback");
        return result;
    }

    public List<Map<String, Object>> listVersions(Long reportId) {
        return reportContentRepository.listVersions(reportId);
    }

    public Map<String, Object> compareVersions(Long reportId, Long baseVersionId, Long targetVersionId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        ensureReportOwner(report, "report version diff access denied");
        List<Map<String, Object>> baseSections = reportContentRepository.findSectionsByVersion(reportId, baseVersionId);
        List<Map<String, Object>> targetSections = reportContentRepository.findSectionsByVersion(reportId, targetVersionId);
        Map<String, Map<String, Object>> baseByHeading = indexByHeading(baseSections);
        Map<String, Map<String, Object>> targetByHeading = indexByHeading(targetSections);
        List<Map<String, Object>> changes = new java.util.ArrayList<>();
        int added = 0;
        int removed = 0;
        int modified = 0;
        int unchanged = 0;
        for (Map.Entry<String, Map<String, Object>> entry : baseByHeading.entrySet()) {
            Map<String, Object> target = targetByHeading.get(entry.getKey());
            if (target == null) {
                removed++;
                changes.add(versionChange("removed", entry.getKey(), entry.getValue(), null));
            } else if (sameSection(entry.getValue(), target)) {
                unchanged++;
            } else {
                modified++;
                changes.add(versionChange("modified", entry.getKey(), entry.getValue(), target));
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : targetByHeading.entrySet()) {
            if (!baseByHeading.containsKey(entry.getKey())) {
                added++;
                changes.add(versionChange("added", entry.getKey(), null, entry.getValue()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("baseVersionId", baseVersionId);
        result.put("targetVersionId", targetVersionId);
        result.put("summary", Map.of(
                "added", added,
                "removed", removed,
                "modified", modified,
                "unchanged", unchanged
        ));
        result.put("changes", changes);
        return result;
    }

    private Map<String, Map<String, Object>> indexByHeading(List<Map<String, Object>> sections) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        int index = 1;
        for (Map<String, Object> section : sections == null ? List.<Map<String, Object>>of() : sections) {
            String heading = String.valueOf(section.getOrDefault("heading", "section-" + index));
            result.put(heading, section);
            index++;
        }
        return result;
    }

    private boolean sameSection(Map<String, Object> base, Map<String, Object> target) {
        return java.util.Objects.equals(base.get("content"), target.get("content"))
                && java.util.Objects.equals(base.get("citations"), target.get("citations"));
    }

    private Map<String, Object> versionChange(String changeType, String heading, Map<String, Object> base, Map<String, Object> target) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("changeType", changeType);
        change.put("heading", heading);
        change.put("baseContent", base == null ? null : base.get("content"));
        change.put("targetContent", target == null ? null : target.get("content"));
        change.put("baseCitations", base == null ? List.of() : base.getOrDefault("citations", List.of()));
        change.put("targetCitations", target == null ? List.of() : target.getOrDefault("citations", List.of()));
        return change;
    }

    private Long currentUserId() {
        CurrentUser user = CurrentUserHolder.get();
        return user == null ? 1L : user.userId();
    }

    private String stageMessage(ReportGenerationTask task) {
        return switch (task.currentStage()) {
            case "outline" -> "报告大纲已生成，等待确认";
            case "retrieval" -> "正在检索知识库";
            case "analysis" -> "正在分析数据";
            case "writing" -> "正在生成报告正文";
            default -> "任务状态：" + task.status();
        };
    }

    private static AuditRepository noopAuditRepository() {
        return new AuditRepository() {
            @Override
            public OperationLog save(OperationLog log) {
                return log;
            }

            @Override
            public Optional<OperationLog> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<OperationLog> findPage(int page, int pageSize) {
                return List.of();
            }

            @Override
            public long count() {
                return 0L;
            }
        };
    }

    private static ModelInvocationRepository noopModelInvocationRepository() {
        return new ModelInvocationRepository() {
            @Override
            public ModelInvocationAudit save(ModelInvocationAudit invocation) {
                return invocation;
            }

            @Override
            public Optional<ModelInvocationAudit> findById(Long id) {
                return Optional.empty();
            }
        };
    }
}
