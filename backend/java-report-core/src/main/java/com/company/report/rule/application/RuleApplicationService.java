package com.company.report.rule.application;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.citation.application.CollaborationApplicationService;
import com.company.report.knowledge.infrastructure.storage.DocumentStorage;
import com.company.report.notification.domain.model.SystemAlert;
import com.company.report.notification.domain.repository.SystemAlertRepository;
import com.company.report.permission.domain.model.UserAccount;
import com.company.report.permission.domain.repository.UserRepository;
import com.company.report.rule.domain.model.Rule;
import com.company.report.rule.domain.model.RuleActionExecution;
import com.company.report.rule.domain.model.RuleApprovalDelegateRule;
import com.company.report.rule.domain.model.RuleApprovalRecord;
import com.company.report.rule.domain.model.RuleApprovalTemplate;
import com.company.report.rule.domain.model.RuleDebugRun;
import com.company.report.rule.domain.model.RuleRunMetrics;
import com.company.report.rule.domain.repository.RuleRepository;
import com.company.report.rule.domain.repository.RuleRepository.ApprovalRecordFilter;
import com.company.report.rule.domain.service.RuleDomainService;
import com.company.report.rule.infrastructure.persistence.InMemoryRuleRepository;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.CurrentUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

@Service
public class RuleApplicationService {
    private static final int MAX_SUBPROCESS_STACK_DEPTH = 8;
    private static final int MAX_PRODUCTION_TRACE_NODES = 128;
    private static final long MAX_PRODUCTION_EXECUTION_MILLIS = 30_000L;

    private final RuleDomainService domainService;
    private final RuleRepository ruleRepository;
    private final AuditRepository auditRepository;
    private final SystemAlertRepository systemAlertRepository;
    private final Optional<CollaborationApplicationService> collaborationApplicationService;
    private final Optional<RuleWebhookClient> ruleWebhookClient;
    private final Optional<UserRepository> userRepository;
    private final Optional<DocumentStorage> documentStorage;
    private final RuleApprovalSupplementAttachmentPolicy approvalSupplementAttachmentPolicy;
    private final RuleApprovalSupplementAttachmentInspector approvalSupplementAttachmentInspector;
    private final LongSupplier nanoTimeSource;

    @Autowired
    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  Optional<CollaborationApplicationService> collaborationApplicationService,
                                  Optional<RuleWebhookClient> ruleWebhookClient,
                                  Optional<UserRepository> userRepository,
                                  Optional<DocumentStorage> documentStorage,
                                  RuleApprovalSupplementAttachmentPolicy approvalSupplementAttachmentPolicy,
                                  RuleApprovalSupplementAttachmentInspector approvalSupplementAttachmentInspector) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, collaborationApplicationService, ruleWebhookClient, userRepository, documentStorage, approvalSupplementAttachmentPolicy, approvalSupplementAttachmentInspector, System::nanoTime);
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  LongSupplier nanoTimeSource) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), RuleApprovalSupplementAttachmentPolicy.defaults(), new BasicRuleApprovalSupplementAttachmentInspector(), nanoTimeSource);
    }

    private RuleApplicationService(RuleDomainService domainService,
                                   RuleRepository ruleRepository,
                                   AuditRepository auditRepository,
                                   SystemAlertRepository systemAlertRepository,
                                   Optional<CollaborationApplicationService> collaborationApplicationService,
                                   Optional<RuleWebhookClient> ruleWebhookClient,
                                   Optional<UserRepository> userRepository,
                                   Optional<DocumentStorage> documentStorage,
                                   RuleApprovalSupplementAttachmentPolicy approvalSupplementAttachmentPolicy,
                                   RuleApprovalSupplementAttachmentInspector approvalSupplementAttachmentInspector,
                                   LongSupplier nanoTimeSource) {
        this.domainService = domainService;
        this.ruleRepository = ruleRepository;
        this.auditRepository = auditRepository;
        this.systemAlertRepository = systemAlertRepository;
        this.collaborationApplicationService = collaborationApplicationService == null ? Optional.empty() : collaborationApplicationService;
        this.ruleWebhookClient = ruleWebhookClient == null ? Optional.empty() : ruleWebhookClient;
        this.userRepository = userRepository == null ? Optional.empty() : userRepository;
        this.documentStorage = documentStorage == null ? Optional.empty() : documentStorage;
        this.approvalSupplementAttachmentPolicy = approvalSupplementAttachmentPolicy == null
                ? RuleApprovalSupplementAttachmentPolicy.defaults()
                : approvalSupplementAttachmentPolicy;
        this.approvalSupplementAttachmentInspector = approvalSupplementAttachmentInspector == null
                ? new BasicRuleApprovalSupplementAttachmentInspector()
                : approvalSupplementAttachmentInspector;
        this.nanoTimeSource = nanoTimeSource == null ? System::nanoTime : nanoTimeSource;
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  RuleWebhookClient ruleWebhookClient) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.empty(), Optional.ofNullable(ruleWebhookClient), Optional.empty(), Optional.empty(), RuleApprovalSupplementAttachmentPolicy.defaults(), new BasicRuleApprovalSupplementAttachmentInspector());
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  CollaborationApplicationService collaborationApplicationService) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.ofNullable(collaborationApplicationService), Optional.empty(), Optional.empty(), Optional.empty(), RuleApprovalSupplementAttachmentPolicy.defaults(), new BasicRuleApprovalSupplementAttachmentInspector());
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  UserRepository userRepository) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.empty(), Optional.empty(), Optional.ofNullable(userRepository), Optional.empty(), RuleApprovalSupplementAttachmentPolicy.defaults(), new BasicRuleApprovalSupplementAttachmentInspector());
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  DocumentStorage documentStorage) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(documentStorage), RuleApprovalSupplementAttachmentPolicy.defaults(), new BasicRuleApprovalSupplementAttachmentInspector());
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  DocumentStorage documentStorage,
                                  RuleApprovalSupplementAttachmentPolicy approvalSupplementAttachmentPolicy) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(documentStorage), approvalSupplementAttachmentPolicy, new BasicRuleApprovalSupplementAttachmentInspector());
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository,
                                  DocumentStorage documentStorage,
                                  RuleApprovalSupplementAttachmentPolicy approvalSupplementAttachmentPolicy,
                                  RuleApprovalSupplementAttachmentInspector approvalSupplementAttachmentInspector) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(documentStorage), approvalSupplementAttachmentPolicy, approvalSupplementAttachmentInspector);
    }

    public RuleApplicationService(RuleDomainService domainService,
                                  RuleRepository ruleRepository,
                                  AuditRepository auditRepository,
                                  SystemAlertRepository systemAlertRepository) {
        this(domainService, ruleRepository, auditRepository, systemAlertRepository, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), RuleApprovalSupplementAttachmentPolicy.defaults(), new BasicRuleApprovalSupplementAttachmentInspector());
    }

    public RuleApplicationService(RuleDomainService domainService, RuleRepository ruleRepository, AuditRepository auditRepository) {
        this(domainService, ruleRepository, auditRepository, noopSystemAlertRepository());
    }

    public RuleApplicationService(RuleDomainService domainService, RuleRepository ruleRepository) {
        this(domainService, ruleRepository, noopAuditRepository());
    }

    public RuleApplicationService(RuleDomainService domainService) {
        this(domainService, new InMemoryRuleRepository());
    }

    public PageResponse<Map<String, Object>> list(int page, int pageSize) {
        List<Map<String, Object>> items = ruleRepository.findPage(page, pageSize).stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.count());
    }

    public Map<String, Object> create(Map<String, Object> request) {
        Map<String, Object> definition = map(request.get("definition"));
        domainService.validateDefinition(definition);
        Rule saved = ruleRepository.save(Rule.draft(
                string(request.get("name")),
                string(request.get("description")),
                definition
        ));
        return toResponse(saved);
    }

    public Map<String, Object> save(Long ruleId, Map<String, Object> request) {
        Rule existing = findRule(ruleId);
        Map<String, Object> definition = map(request.get("definition"));
        domainService.validateDefinition(definition);
        Long versionId = ruleRepository.saveVersion(ruleId, definition);
        Rule saved = ruleRepository.save(existing.update(
                string(request.get("name")),
                string(request.get("description")),
                string(request.get("status")),
                definition,
                versionId
        ));
        Map<String, Object> result = new LinkedHashMap<>(toResponse(saved));
        result.put("valid", true);
        return result;
    }

    public Map<String, Object> debug(Long ruleId, Map<String, Object> request) {
        domainService.ensureDebugInputValid(request);
        Rule rule = findRule(ruleId);
        Map<String, Object> output = domainService.executeDebug(rule.definition(), request);
        RuleDebugRun saved = ruleRepository.saveDebugRun(RuleDebugRun.succeededDebug(ruleId, rule.currentVersionId(), request, output));
        return Map.of(
                "debugRunId", saved.id(),
                "ruleId", ruleId,
                "versionId", rule.currentVersionId(),
                "status", saved.status(),
                "output", saved.output()
        );
    }

    public Map<String, Object> submitForReview(Long ruleId, Map<String, Object> request) {
        Rule rule = findRule(ruleId);
        if (!"draft".equals(rule.status())) {
            throw new IllegalStateException("only draft rules can be submitted for review");
        }
        Rule saved = ruleRepository.save(rule.withStatus("pending_review"));
        Map<String, Object> result = new LinkedHashMap<>(toResponse(saved));
        result.put("reviewComment", string(request.get("comment")));
        writeAudit("rule_review_submit", saved, "succeeded", auditDetail(
                "status", saved.status(),
                "comment", string(request.get("comment"))
        ));
        return result;
    }

    public Map<String, Object> approve(Long ruleId, Map<String, Object> request) {
        Rule rule = findRule(ruleId);
        if (!"pending_review".equals(rule.status())) {
            throw new IllegalStateException("rule must be pending_review before approval");
        }
        Rule saved = ruleRepository.save(rule.withStatus("published"));
        Map<String, Object> result = new LinkedHashMap<>(toResponse(saved));
        result.put("approvalComment", string(request.get("comment")));
        writeAudit("rule_publish_approve", saved, "succeeded", auditDetail(
                "status", saved.status(),
                "comment", string(request.get("comment"))
        ));
        return result;
    }

    public Map<String, Object> execute(Long ruleId, Map<String, Object> request) {
        long startedAt = nanoTime();
        Rule rule = findRule(ruleId);
        if (!"published".equals(rule.status())) {
            throw new IllegalStateException("rule must be published before production execution");
        }
        Map<String, Object> output;
        try {
            domainService.ensureDebugInputValid(request);
            output = domainService.executeDebug(rule.definition(), request);
            ensureProductionExecutionWithinDurationBudget(startedAt);
            ensureProductionTraceWithinBudget(output);
        } catch (RuntimeException error) {
            RuleDebugRun failed = ruleRepository.saveDebugRun(RuleDebugRun.failedProduction(
                    ruleId,
                    rule.currentVersionId(),
                    currentUserId(),
                    elapsedMillis(startedAt),
                    errorMessage(error),
                    request
            ));
            writeAudit("rule_production_run", rule, "failed", auditDetail(
                    "runType", "production",
                    "versionId", rule.currentVersionId(),
                    "runId", failed.id(),
                    "errorMessage", failed.errorMessage()
            ));
            throw error;
        }
        RuleDebugRun saved = ruleRepository.saveDebugRun(RuleDebugRun.succeededProduction(
                ruleId,
                rule.currentVersionId(),
                currentUserId(),
                elapsedMillis(startedAt),
                request,
                output
        ));
        try {
            executeRuleActions(rule, saved, output);
        } catch (RuntimeException error) {
            RuleDebugRun failed = ruleRepository.saveDebugRun(RuleDebugRun.failedProduction(
                    ruleId,
                    rule.currentVersionId(),
                    currentUserId(),
                    elapsedMillis(startedAt),
                    errorMessage(error),
                    Map.of()
            ));
            writeAudit("rule_production_run", rule, "failed", auditDetail(
                    "runType", "production",
                    "versionId", rule.currentVersionId(),
                    "runId", failed.id(),
                    "sourceRunId", saved.id(),
                    "errorMessage", failed.errorMessage()
            ));
            throw error;
        }
        writeAudit("rule_production_run", rule, "succeeded", auditDetail(
                "runType", "production",
                "versionId", rule.currentVersionId(),
                "matched", output.get("matched")
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("debugRunId", saved.id());
        result.put("ruleId", ruleId);
        result.put("versionId", rule.currentVersionId());
        result.put("status", saved.status());
        result.put("runType", "production");
        result.put("triggeredByUserId", saved.triggeredByUserId());
        result.put("durationMs", saved.durationMs());
        result.put("errorMessage", saved.errorMessage());
        result.put("output", saved.output());
        return result;
    }

    public PageResponse<Map<String, Object>> listRuns(Long ruleId, int page, int pageSize) {
        findRule(ruleId);
        List<Map<String, Object>> items = ruleRepository.findRuns(ruleId, page, pageSize).stream()
                .map(this::toRunResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.countRuns(ruleId));
    }

    public Map<String, Object> subprocessRunTopology(Long ruleId, Long runId) {
        findRule(ruleId);
        RuleDebugRun parentRun = ruleRepository.findRunById(runId)
                .orElseThrow(() -> new IllegalArgumentException("rule run not found: " + runId));
        if (!ruleId.equals(parentRun.ruleId())) {
            throw new IllegalArgumentException("rule run does not belong to rule: " + runId);
        }
        List<OperationLog> subprocessLogs = auditRepository.findByResourceOperationAndDetail(
                "rule",
                ruleId,
                "rule_subprocess_run",
                "parentRunId",
                String.valueOf(runId)
        );
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(topologyNode(parentRun, "parent"));
        List<Map<String, Object>> edges = new ArrayList<>();
        for (OperationLog log : subprocessLogs) {
            Map<String, Object> detail = log.detail() == null ? Map.of() : log.detail();
            Long subprocessRunId = nullableLong(detail.get("subprocessRunId"));
            if (subprocessRunId != null) {
                ruleRepository.findRunById(subprocessRunId)
                        .map(run -> topologyNode(run, "subprocess"))
                        .ifPresent(nodes::add);
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("operationLogId", log.id());
            edge.put("parentRuleId", nullableLong(detail.get("parentRuleId")));
            edge.put("parentRunId", nullableLong(detail.get("parentRunId")));
            edge.put("nodeId", string(detail.get("nodeId")));
            edge.put("subprocessRuleId", nullableLong(detail.get("subprocessRuleId")));
            edge.put("subprocessRunId", subprocessRunId);
            edge.put("subprocessVersionId", nullableLong(detail.get("subprocessVersionId")));
            edge.put("result", log.result());
            edge.put("errorMessage", string(detail.get("errorMessage")));
            edge.put("createdAt", log.createdAt() == null ? "" : log.createdAt().toString());
            edges.add(edge);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", ruleId);
        result.put("runId", runId);
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    public PageResponse<Map<String, Object>> listApprovalRecords(Long ruleId, int page, int pageSize) {
        findRule(ruleId);
        List<Map<String, Object>> items = ruleRepository.findApprovalRecords(ruleId, page, pageSize).stream()
                .map(this::toApprovalRecordResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.countApprovalRecords(ruleId));
    }

    public PageResponse<Map<String, Object>> listPendingApprovalRecords(int page, int pageSize) {
        return listApprovalRecordsByStatus("pending", page, pageSize, Map.of());
    }

    public Map<String, Object> createApprovalDelegateRule(Map<String, Object> request) {
        ApprovalDelegateRuleFields fields = approvalDelegateRuleFields(request);
        ensureNoApprovalDelegateScheduleConflict(null, fields);
        RuleApprovalDelegateRule saved = ruleRepository.saveApprovalDelegateRule(RuleApprovalDelegateRule.enabled(
                fields.assigneeRole(),
                fields.delegateRole(),
                fields.activeFrom(),
                fields.activeTo(),
                fields.activeWeekdays(),
                fields.activeDates(),
                fields.reason(),
                currentUserId()
        ));
        return toApprovalDelegateRuleResponse(saved);
    }

    public PageResponse<Map<String, Object>> listApprovalDelegateRules(int page, int pageSize, Map<String, Object> filters) {
        Map<String, Object> safeFilters = filters == null ? Map.of() : filters;
        String status = blankToNull(string(safeFilters.get("status")));
        String assigneeRole = blankToNull(string(safeFilters.get("assigneeRole")));
        List<Map<String, Object>> items = ruleRepository.findApprovalDelegateRules(status, assigneeRole, page, pageSize).stream()
                .map(this::toApprovalDelegateRuleResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.countApprovalDelegateRules(status, assigneeRole));
    }

    public Map<String, Object> createApprovalTemplate(Map<String, Object> request) {
        String name = blankToNull(string(request == null ? null : request.get("name")));
        if (name == null) {
            throw new IllegalArgumentException("approval template name is required");
        }
        String status = blankToNull(string(request.get("status")));
        if (status == null) {
            status = "enabled";
        }
        if (!"enabled".equals(status) && !"disabled".equals(status)) {
            throw new IllegalArgumentException("approval template status must be enabled or disabled");
        }
        RuleApprovalTemplate saved = ruleRepository.saveApprovalTemplate(RuleApprovalTemplate.create(
                name,
                string(request.get("description")),
                status,
                approvalTemplateSteps(request.get("steps")),
                currentUserId()
        ));
        return toApprovalTemplateResponse(saved);
    }

    public PageResponse<Map<String, Object>> listApprovalTemplates(int page, int pageSize, Map<String, Object> filters) {
        Map<String, Object> safeFilters = filters == null ? Map.of() : filters;
        String status = blankToNull(string(safeFilters.get("status")));
        List<Map<String, Object>> items = ruleRepository.findApprovalTemplates(status, page, pageSize).stream()
                .map(this::toApprovalTemplateResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.countApprovalTemplates(status));
    }

    public PageResponse<Map<String, Object>> listApprovalTemplateUsage(Long templateId, int page, int pageSize) {
        ruleRepository.findApprovalTemplateById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("approval template not found: " + templateId));
        List<Map<String, Object>> items = ruleRepository.findRulesUsingApprovalTemplate(templateId, page, pageSize).stream()
                .map(this::toApprovalTemplateUsageRuleResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.countRulesUsingApprovalTemplate(templateId));
    }

    public Map<String, Object> updateApprovalTemplate(Long templateId, Map<String, Object> request) {
        RuleApprovalTemplate existing = ruleRepository.findApprovalTemplateById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("approval template not found: " + templateId));
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String name = blankToNull(string(safeRequest.get("name")));
        if (name == null) {
            throw new IllegalArgumentException("approval template name is required");
        }
        String status = blankToNull(string(safeRequest.get("status")));
        if (status == null) {
            status = existing.status();
        }
        if (!"enabled".equals(status) && !"disabled".equals(status)) {
            throw new IllegalArgumentException("approval template status must be enabled or disabled");
        }
        RuleApprovalTemplate updated = ruleRepository.updateApprovalTemplate(existing.withContent(
                name,
                string(safeRequest.get("description")),
                status,
                approvalTemplateSteps(safeRequest.get("steps"))
        ));
        return toApprovalTemplateResponse(updated);
    }

    public Map<String, Object> getApprovalTemplateVersion(Long templateId, int version) {
        if (version < 1) {
            throw new IllegalArgumentException("approval template version must be greater than 0");
        }
        RuleApprovalTemplate snapshot = ruleRepository.findApprovalTemplateVersion(templateId, version)
                .orElseThrow(() -> new IllegalArgumentException("approval template version not found: " + templateId + "@" + version));
        return toApprovalTemplateResponse(snapshot);
    }

    public List<Map<String, Object>> listApprovalTemplateVersions(Long templateId) {
        ruleRepository.findApprovalTemplateById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("approval template not found: " + templateId));
        return ruleRepository.findApprovalTemplateVersions(templateId).stream()
                .map(this::toApprovalTemplateResponse)
                .toList();
    }

    public Map<String, Object> compareApprovalTemplateVersions(Long templateId, int baseVersion, int targetVersion) {
        RuleApprovalTemplate base = ruleRepository.findApprovalTemplateVersion(templateId, baseVersion)
                .orElseThrow(() -> new IllegalArgumentException("approval template version not found: " + templateId + "@" + baseVersion));
        RuleApprovalTemplate target = ruleRepository.findApprovalTemplateVersion(templateId, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("approval template version not found: " + templateId + "@" + targetVersion));
        Map<String, Map<String, Object>> baseSteps = approvalTemplateStepsById(base.steps());
        Map<String, Map<String, Object>> targetSteps = approvalTemplateStepsById(target.steps());
        List<Map<String, Object>> changes = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int modified = 0;
        int unchanged = 0;
        for (Map.Entry<String, Map<String, Object>> entry : baseSteps.entrySet()) {
            Map<String, Object> targetStep = targetSteps.get(entry.getKey());
            if (targetStep == null) {
                removed++;
                changes.add(approvalTemplateVersionChange("removed", entry.getKey(), entry.getValue(), null));
            } else if (java.util.Objects.equals(entry.getValue(), targetStep)) {
                unchanged++;
            } else {
                modified++;
                changes.add(approvalTemplateVersionChange("modified", entry.getKey(), entry.getValue(), targetStep));
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : targetSteps.entrySet()) {
            if (!baseSteps.containsKey(entry.getKey())) {
                added++;
                changes.add(approvalTemplateVersionChange("added", entry.getKey(), null, entry.getValue()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalTemplateId", templateId);
        result.put("baseVersion", baseVersion);
        result.put("targetVersion", targetVersion);
        result.put("summary", Map.of(
                "added", added,
                "removed", removed,
                "modified", modified,
                "unchanged", unchanged
        ));
        result.put("changes", changes);
        return result;
    }

    public Map<String, Object> rollbackApprovalTemplateVersion(Long templateId, int version) {
        RuleApprovalTemplate existing = ruleRepository.findApprovalTemplateById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("approval template not found: " + templateId));
        RuleApprovalTemplate snapshot = ruleRepository.findApprovalTemplateVersion(templateId, version)
                .orElseThrow(() -> new IllegalArgumentException("approval template version not found: " + templateId + "@" + version));
        RuleApprovalTemplate rolledBack = ruleRepository.updateApprovalTemplate(existing.withContent(
                snapshot.name(),
                snapshot.description(),
                existing.status(),
                snapshot.steps()
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalTemplateId", templateId);
        result.put("sourceVersion", version);
        result.put("newVersion", rolledBack.version());
        result.put("currentVersion", rolledBack.version());
        result.put("changeReason", "rollback");
        return result;
    }

    public Map<String, Object> disableApprovalTemplate(Long templateId) {
        return changeApprovalTemplateStatus(templateId, "disabled");
    }

    public Map<String, Object> enableApprovalTemplate(Long templateId) {
        return changeApprovalTemplateStatus(templateId, "enabled");
    }

    public Map<String, Object> batchImportApprovalDelegateRules(Map<String, Object> request) {
        List<Map<String, Object>> results = new ArrayList<>();
        int imported = 0;
        int failed = 0;
        List<?> rows = list(request == null ? null : request.get("rules"));
        for (int index = 0; index < rows.size(); index++) {
            int rowNumber = index + 1;
            Map<String, Object> rowResult = new LinkedHashMap<>();
            rowResult.put("rowNumber", rowNumber);
            try {
                Map<String, Object> created = createApprovalDelegateRule(map(rows.get(index)));
                imported++;
                rowResult.put("status", "imported");
                rowResult.put("delegateRuleId", created.get("delegateRuleId"));
                rowResult.put("assigneeRole", created.get("assigneeRole"));
                rowResult.put("delegateRole", created.get("delegateRole"));
            } catch (RuntimeException error) {
                failed++;
                rowResult.put("status", "failed");
                rowResult.put("reason", errorMessage(error));
            }
            results.add(rowResult);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imported", imported);
        response.put("failed", failed);
        response.put("results", results);
        return response;
    }

    private Map<String, Object> changeApprovalTemplateStatus(Long templateId, String status) {
        RuleApprovalTemplate existing = ruleRepository.findApprovalTemplateById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("approval template not found: " + templateId));
        RuleApprovalTemplate updated = ruleRepository.updateApprovalTemplate(existing.withStatus(status));
        Map<String, Object> response = new LinkedHashMap<>(toApprovalTemplateResponse(updated));
        response.put("impact", approvalTemplateUsageImpact(templateId));
        return response;
    }

    public Map<String, Object> disableApprovalDelegateRule(Long delegateRuleId, Map<String, Object> request) {
        RuleApprovalDelegateRule existing = ruleRepository.findApprovalDelegateRuleById(delegateRuleId)
                .orElseThrow(() -> new IllegalArgumentException("approval delegate rule not found: " + delegateRuleId));
        RuleApprovalDelegateRule disabled = ruleRepository.updateApprovalDelegateRule(existing.disable(
                string(request == null ? null : request.get("reason"))
        ));
        return toApprovalDelegateRuleResponse(disabled);
    }

    public Map<String, Object> updateApprovalDelegateRule(Long delegateRuleId, Map<String, Object> request) {
        RuleApprovalDelegateRule existing = ruleRepository.findApprovalDelegateRuleById(delegateRuleId)
                .orElseThrow(() -> new IllegalArgumentException("approval delegate rule not found: " + delegateRuleId));
        ApprovalDelegateRuleFields fields = approvalDelegateRuleFields(request);
        ensureNoApprovalDelegateScheduleConflict(existing.id(), fields);
        RuleApprovalDelegateRule updated = ruleRepository.updateApprovalDelegateRule(existing.update(
                fields.assigneeRole(),
                fields.delegateRole(),
                fields.activeFrom(),
                fields.activeTo(),
                fields.activeWeekdays(),
                fields.activeDates(),
                fields.reason()
        ));
        return toApprovalDelegateRuleResponse(updated);
    }

    public Map<String, Object> enableApprovalDelegateRule(Long delegateRuleId, Map<String, Object> request) {
        RuleApprovalDelegateRule existing = ruleRepository.findApprovalDelegateRuleById(delegateRuleId)
                .orElseThrow(() -> new IllegalArgumentException("approval delegate rule not found: " + delegateRuleId));
        RuleApprovalDelegateRule enabled = ruleRepository.updateApprovalDelegateRule(existing.enable(
                string(request == null ? null : request.get("reason"))
        ));
        return toApprovalDelegateRuleResponse(enabled);
    }

    private ApprovalDelegateRuleFields approvalDelegateRuleFields(Map<String, Object> request) {
        String assigneeRole = blankToNull(string(request == null ? null : request.get("assigneeRole")));
        String delegateRole = blankToNull(string(request == null ? null : request.get("delegateRole")));
        if (assigneeRole == null) {
            throw new IllegalArgumentException("assigneeRole is required");
        }
        if (delegateRole == null) {
            throw new IllegalArgumentException("delegateRole is required");
        }
        if (assigneeRole.equals(delegateRole)) {
            throw new IllegalArgumentException("delegateRole must be different from assigneeRole");
        }
        OffsetDateTime activeFrom = nullableOffsetDateTime(request == null ? null : request.get("activeFrom"));
        OffsetDateTime activeTo = nullableOffsetDateTime(request == null ? null : request.get("activeTo"));
        if (activeFrom != null && activeTo != null && activeFrom.isAfter(activeTo)) {
            throw new IllegalArgumentException("delegate active window is invalid");
        }
        List<String> activeWeekdays = activeWeekdays(request == null ? null : request.get("activeWeekdays"));
        List<String> activeDates = activeDates(request == null ? null : request.get("activeDates"));
        return new ApprovalDelegateRuleFields(
                assigneeRole,
                delegateRole,
                activeFrom,
                activeTo,
                activeWeekdays,
                activeDates,
                string(request == null ? null : request.get("reason"))
        );
    }

    private record ApprovalDelegateRuleFields(
            String assigneeRole,
            String delegateRole,
            OffsetDateTime activeFrom,
            OffsetDateTime activeTo,
            List<String> activeWeekdays,
            List<String> activeDates,
            String reason
    ) {
    }

    private List<Map<String, Object>> approvalTemplateSteps(Object value) {
        List<?> rawSteps = list(value);
        if (rawSteps.isEmpty()) {
            throw new IllegalArgumentException("approval template steps are required");
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : rawSteps) {
            Map<String, Object> step = map(item);
            String stepId = blankToNull(string(step.get("stepId")));
            String approvalTitle = blankToNull(string(step.get("approvalTitle")));
            List<String> assigneeRoles = stringList(step.get("assigneeRoles"));
            if (approvalTitle == null) {
                throw new IllegalArgumentException("approvalTitle is required");
            }
            if (assigneeRoles.isEmpty()) {
                throw new IllegalArgumentException("assigneeRoles is required");
            }
            String approvalMode = blankToNull(string(step.get("approvalMode")));
            if (approvalMode == null) {
                approvalMode = "all";
            }
            if (!"all".equals(approvalMode) && !"any".equals(approvalMode)) {
                throw new IllegalArgumentException("approvalMode must be all or any");
            }
            Map<String, Object> normalizedStep = new LinkedHashMap<>();
            normalizedStep.put("stepId", stepId == null ? "step" + (normalized.size() + 1) : stepId);
            normalizedStep.put("approvalTitle", approvalTitle);
            normalizedStep.put("assigneeRoles", assigneeRoles);
            normalizedStep.put("approvalMode", approvalMode);
            normalizedStep.put("slaHours", nullableInteger(step.get("slaHours")));
            normalizedStep.put("slaEscalations", normalizeApprovalTemplateEscalations(step.get("slaEscalations")));
            normalized.add(normalizedStep);
        }
        return List.copyOf(normalized);
    }

    private List<Map<String, Object>> normalizeApprovalTemplateEscalations(Object value) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : list(value)) {
            Map<String, Object> escalation = map(item);
            Integer afterHours = nullableInteger(escalation.get("afterHours"));
            String role = blankToNull(string(escalation.get("role")));
            if (afterHours == null || afterHours <= 0) {
                throw new IllegalArgumentException("slaEscalations.afterHours must be greater than 0");
            }
            if (role == null) {
                throw new IllegalArgumentException("slaEscalations.role is required");
            }
            normalized.add(Map.of("afterHours", afterHours, "role", role));
        }
        return List.copyOf(normalized);
    }

    private void ensureNoApprovalDelegateScheduleConflict(Long currentRuleId, ApprovalDelegateRuleFields fields) {
        Optional<RuleApprovalDelegateRule> conflictingRule = ruleRepository.findEnabledApprovalDelegateRulesByAssigneeRole(fields.assigneeRole()).stream()
                .filter(rule -> currentRuleId == null || !currentRuleId.equals(rule.id()))
                .filter(rule -> windowsOverlap(rule.activeFrom(), rule.activeTo(), fields.activeFrom(), fields.activeTo()))
                .filter(rule -> stringListsOverlap(rule.activeWeekdays(), fields.activeWeekdays()))
                .filter(rule -> stringListsOverlap(rule.activeDates(), fields.activeDates()))
                .findFirst();
        if (conflictingRule.isPresent()) {
            throw new IllegalArgumentException("approval delegate rule schedule conflicts with rule: " + conflictingRule.get().id());
        }
    }

    private static boolean windowsOverlap(OffsetDateTime leftFrom,
                                          OffsetDateTime leftTo,
                                          OffsetDateTime rightFrom,
                                          OffsetDateTime rightTo) {
        return (leftTo == null || rightFrom == null || !leftTo.isBefore(rightFrom))
                && (rightTo == null || leftFrom == null || !rightTo.isBefore(leftFrom));
    }

    private static boolean stringListsOverlap(List<String> left, List<String> right) {
        List<String> safeLeft = left == null ? List.of() : left;
        List<String> safeRight = right == null ? List.of() : right;
        if (safeLeft.isEmpty() || safeRight.isEmpty()) {
            return true;
        }
        return safeLeft.stream().anyMatch(safeRight::contains);
    }

    public PageResponse<Map<String, Object>> listApprovalRecordsByStatus(String status, int page, int pageSize) {
        return listApprovalRecordsByStatus(status, page, pageSize, Map.of());
    }

    public PageResponse<Map<String, Object>> listApprovalRecordsByStatus(String status, int page, int pageSize, Map<String, Object> filters) {
        ApprovalRecordFilter filter = toApprovalRecordFilter(filters);
        filter = applyApprovalRecordVisibility(filter);
        List<Map<String, Object>> items = ruleRepository.findApprovalRecordsByStatus(status, filter, page, pageSize).stream()
                .map(this::toApprovalRecordResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.countApprovalRecordsByStatus(status, filter));
    }

    public Map<String, Object> handleApprovalRecord(Long ruleId, Long approvalRecordId, Map<String, Object> request) {
        Rule rule = findRule(ruleId);
        RuleApprovalRecord record = ruleRepository.findApprovalRecordById(approvalRecordId)
                .orElseThrow(() -> new IllegalArgumentException("approval record not found: " + approvalRecordId));
        if (!ruleId.equals(record.ruleId())) {
            throw new IllegalArgumentException("approval record does not belong to rule: " + approvalRecordId);
        }
        if (!"pending".equals(record.status())) {
            throw new IllegalStateException("approval record already handled: " + approvalRecordId);
        }
        ensureApprovalRecordAccess(record);
        String action = string(request.get("action"));
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new IllegalArgumentException("approval action must be approve or reject");
        }
        String status = "approve".equals(action) ? "approved" : "rejected";
        RuleApprovalRecord updated = ruleRepository.updateApprovalRecord(record.handle(
                status,
                currentUserId(),
                string(request.get("comment")),
                OffsetDateTime.now()
        ));
        if ("approve".equals(action)) {
            if (shouldResumeAfterApproval(rule, record, updated)) {
                resumeRuleActionsAfterApproval(rule, updated);
                closeSiblingPendingApprovalsAfterAnyApproval(rule, updated);
            }
        } else {
            closeSiblingPendingApprovalsAfterRejection(rule, updated);
            createSupplementRequest(rule, updated);
            executeRejectedApprovalBranch(rule, updated);
            createApprovalRejectedAlert(rule, updated);
        }
        writeAudit(
                "approve".equals(action) ? "rule_node_approval_approved" : "rule_node_approval_rejected",
                rule,
                "succeeded",
                auditDetail(
                        "approvalRecordId", updated.id(),
                        "runId", updated.runId(),
                        "nodeId", updated.nodeId(),
                        "status", updated.status(),
                        "comment", updated.approvalComment()
                )
        );
        Map<String, Object> result = new LinkedHashMap<>(toApprovalRecordResponse(updated));
        if ("reject".equals(action)) {
            result.put("supplementStatus", "supplement_required");
        }
        return result;
    }

    public Map<String, Object> submitApprovalSupplement(Long ruleId, Long rejectedApprovalRecordId, Map<String, Object> request) {
        Rule rule = findRule(ruleId);
        RuleApprovalRecord rejectedRecord = ruleRepository.findApprovalRecordById(rejectedApprovalRecordId)
                .orElseThrow(() -> new IllegalArgumentException("approval record not found: " + rejectedApprovalRecordId));
        if (!ruleId.equals(rejectedRecord.ruleId())) {
            throw new IllegalArgumentException("approval record does not belong to rule: " + rejectedApprovalRecordId);
        }
        if (!"rejected".equals(rejectedRecord.status())) {
            throw new IllegalStateException("only rejected approval records can be supplemented: " + rejectedApprovalRecordId);
        }
        Optional<RuleApprovalRecord> supplementRequest = approvalSiblingRecordsIncludingHistory(rejectedRecord).stream()
                .filter(record -> "supplement_required".equals(record.status()))
                .filter(record -> rejectedRecord.approvalComment() == null || rejectedRecord.approvalComment().equals(record.approvalComment()))
                .findFirst();
        supplementRequest.ifPresent(record -> ruleRepository.updateApprovalRecord(record.supplement(
                "resubmitted",
                string(request == null ? null : request.get("comment")),
                OffsetDateTime.now()
        )));
        Map<String, Object> approvalNode = approvalNode(rule, rejectedRecord.nodeId());
        ApprovalDelegateSnapshot delegate = approvalDelegateSnapshot(approvalNode, rejectedRecord.assigneeRole());
        RuleApprovalRecord newPending = ruleRepository.saveApprovalRecord(RuleApprovalRecord.pending(
                rule.id(),
                rejectedRecord.runId(),
                rejectedRecord.nodeId(),
                rejectedRecord.assigneeRole(),
                delegate.delegateRole(),
                delegate.activeFrom(),
                delegate.activeTo(),
                rejectedRecord.approvalTitle(),
                currentUserId(),
                rejectedRecord.slaHours()
        ));
        Map<String, Object> detail = auditDetail(
                "sourceRejectedApprovalRecordId", rejectedApprovalRecordId,
                "supplementRecordId", supplementRequest.map(RuleApprovalRecord::id).orElse(null),
                "newApprovalRecordId", newPending.id(),
                "runId", rejectedRecord.runId(),
                "nodeId", rejectedRecord.nodeId(),
                "assigneeRole", newPending.assigneeRole(),
                "comment", string(request == null ? null : request.get("comment"))
        );
        String evidenceUrl = string(request == null ? null : request.get("evidenceUrl"));
        if (evidenceUrl != null && !evidenceUrl.isBlank()) {
            detail.put("evidenceUrl", evidenceUrl);
        }
        writeAudit("rule_approval_supplement_resubmitted", rule, "succeeded", detail);
        Map<String, Object> result = new LinkedHashMap<>(toApprovalRecordResponse(newPending));
        result.put("sourceRejectedApprovalRecordId", rejectedApprovalRecordId);
        result.put("newApprovalRecordId", newPending.id());
        result.put("supplementStatus", "resubmitted");
        if (evidenceUrl != null && !evidenceUrl.isBlank()) {
            result.put("evidenceUrl", evidenceUrl);
        }
        return result;
    }

    public Map<String, Object> uploadApprovalSupplementAttachment(Long ruleId, Long approvalRecordId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("supplement attachment file is required");
        }
        Rule rule = findRule(ruleId);
        RuleApprovalRecord rejectedRecord = ruleRepository.findApprovalRecordById(approvalRecordId)
                .orElseThrow(() -> new IllegalArgumentException("approval record not found: " + approvalRecordId));
        if (!ruleId.equals(rejectedRecord.ruleId())) {
            throw new IllegalArgumentException("approval record does not belong to rule: " + approvalRecordId);
        }
        if (!"rejected".equals(rejectedRecord.status())) {
            throw new IllegalStateException("only rejected approval records can receive supplement attachments: " + approvalRecordId);
        }
        ensureApprovalSupplementAttachmentPolicy(rule, rejectedRecord, file);
        DocumentStorage storage = documentStorage
                .orElseThrow(() -> new IllegalStateException("approval supplement storage is not configured"));
        String safeFileName = safeApprovalSupplementFileName(file);
        String objectKey = "approval-supplements/rule-" + ruleId
                + "/approval-" + approvalRecordId
                + "/" + UUID.randomUUID()
                + "/" + safeFileName;
        DocumentStorage.StoredObject storedObject;
        try {
            storedObject = storage.store(file, objectKey);
        } catch (IOException error) {
            throw new IllegalStateException("failed to store approval supplement attachment", error);
        }
        String evidenceUrl = "minio://" + storedObject.bucket() + "/" + storedObject.objectKey();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", ruleId);
        result.put("approvalRecordId", approvalRecordId);
        result.put("fileName", storedObject.fileName());
        result.put("bucket", storedObject.bucket());
        result.put("objectKey", storedObject.objectKey());
        result.put("contentType", storedObject.contentType());
        result.put("sizeBytes", storedObject.sizeBytes());
        result.put("evidenceUrl", evidenceUrl);
        writeAudit("rule_approval_supplement_attachment_uploaded", rule, "succeeded", auditDetail(
                "approvalRecordId", approvalRecordId,
                "runId", rejectedRecord.runId(),
                "nodeId", rejectedRecord.nodeId(),
                "fileName", storedObject.fileName(),
                "bucket", storedObject.bucket(),
                "objectKey", storedObject.objectKey(),
                "contentType", storedObject.contentType(),
                "sizeBytes", storedObject.sizeBytes(),
                "evidenceUrl", evidenceUrl
        ));
        return result;
    }

    private void ensureApprovalSupplementAttachmentPolicy(Rule rule, RuleApprovalRecord approvalRecord, MultipartFile file) {
        String contentType = approvalSupplementContentType(file);
        String fileName = safeApprovalSupplementFileName(file);
        long sizeBytes = file.getSize();
        long maxSizeBytes = approvalSupplementAttachmentPolicy.maxSizeBytes();
        Set<String> allowedContentTypes = approvalSupplementAttachmentPolicy.allowedContentTypes();
        if (!allowedContentTypes.contains(contentType)) {
            rejectApprovalSupplementAttachment(rule, approvalRecord, fileName, contentType, sizeBytes, maxSizeBytes, allowedContentTypes, "unsupported_content_type",
                    "unsupported approval supplement attachment content type: " + contentType);
        }
        if (sizeBytes > maxSizeBytes) {
            rejectApprovalSupplementAttachment(rule, approvalRecord, fileName, contentType, sizeBytes, maxSizeBytes, allowedContentTypes, "file_too_large",
                    "approval supplement attachment exceeds max size: " + sizeBytes + " > " + maxSizeBytes);
        }
        RuleApprovalSupplementAttachmentInspector.InspectionResult inspectionResult;
        try {
            inspectionResult = approvalSupplementAttachmentInspector.inspect(file, contentType, fileName);
        } catch (IOException error) {
            rejectApprovalSupplementAttachment(
                    rule,
                    approvalRecord,
                    fileName,
                    contentType,
                    sizeBytes,
                    maxSizeBytes,
                    allowedContentTypes,
                    "content_inspection_unavailable",
                    "approval supplement attachment content inspection failed",
                    Map.of(
                            "inspectionEngine", approvalSupplementAttachmentInspector.engineName(),
                            "inspectionMessage", error.getMessage()
                    )
            );
            return;
        }
        if (!inspectionResult.accepted()) {
            rejectApprovalSupplementAttachment(
                    rule,
                    approvalRecord,
                    fileName,
                    contentType,
                    sizeBytes,
                    maxSizeBytes,
                    allowedContentTypes,
                    inspectionResult.rejectionReason(),
                    "approval supplement attachment failed content inspection: " + inspectionResult.rejectionReason(),
                    Map.of(
                            "inspectionEngine", inspectionResult.engineName() == null
                                    ? approvalSupplementAttachmentInspector.engineName()
                                    : inspectionResult.engineName(),
                            "inspectionMessage", inspectionResult.message()
                    )
            );
        }
    }

    private void rejectApprovalSupplementAttachment(Rule rule,
                                                    RuleApprovalRecord approvalRecord,
                                                    String fileName,
                                                    String contentType,
                                                    long sizeBytes,
                                                    long maxSizeBytes,
                                                    Set<String> allowedContentTypes,
                                                    String rejectionReason,
                                                    String message) {
        rejectApprovalSupplementAttachment(rule, approvalRecord, fileName, contentType, sizeBytes, maxSizeBytes, allowedContentTypes, rejectionReason, message, Map.of());
    }

    private void rejectApprovalSupplementAttachment(Rule rule,
                                                    RuleApprovalRecord approvalRecord,
                                                    String fileName,
                                                    String contentType,
                                                    long sizeBytes,
                                                    long maxSizeBytes,
                                                    Set<String> allowedContentTypes,
                                                    String rejectionReason,
                                                    String message,
                                                    Map<String, Object> additionalDetails) {
        Map<String, Object> detail = auditDetail(
                "approvalRecordId", approvalRecord.id(),
                "runId", approvalRecord.runId(),
                "nodeId", approvalRecord.nodeId(),
                "fileName", fileName,
                "contentType", contentType,
                "sizeBytes", sizeBytes,
                "maxSizeBytes", maxSizeBytes,
                "rejectionReason", rejectionReason,
                "allowedContentTypes", allowedContentTypes
        );
        if (additionalDetails != null && !additionalDetails.isEmpty()) {
            detail.putAll(additionalDetails);
        }
        writeAudit("rule_approval_supplement_attachment_rejected", rule, "failed", detail);
        throw new IllegalArgumentException(message);
    }

    private void closeSiblingPendingApprovalsAfterAnyApproval(Rule rule, RuleApprovalRecord approvedRecord) {
        if (!"any".equals(approvalMode(approvalNode(rule, approvedRecord.nodeId())))) {
            return;
        }
        for (RuleApprovalRecord sibling : approvalSiblingRecords(approvedRecord)) {
            if (sibling.id().equals(approvedRecord.id()) || !"pending".equals(sibling.status())) {
                continue;
            }
            RuleApprovalRecord closed = ruleRepository.updateApprovalRecord(sibling.handle(
                    "closed",
                    currentUserId(),
                    "closed because approval group was approved by any assignee",
                    OffsetDateTime.now()
            ));
            writeAudit(
                    "rule_node_approval_closed",
                    rule,
                    "succeeded",
                    auditDetail(
                            "approvalRecordId", closed.id(),
                            "runId", closed.runId(),
                            "nodeId", closed.nodeId(),
                            "status", closed.status(),
                            "reason", closed.approvalComment()
                    )
            );
        }
    }

    private void closeSiblingPendingApprovalsAfterRejection(Rule rule, RuleApprovalRecord rejectedRecord) {
        for (RuleApprovalRecord sibling : approvalSiblingRecords(rejectedRecord)) {
            if (sibling.id().equals(rejectedRecord.id()) || !"pending".equals(sibling.status())) {
                continue;
            }
            RuleApprovalRecord closed = ruleRepository.updateApprovalRecord(sibling.handle(
                    "closed",
                    currentUserId(),
                    "closed because approval group was rejected",
                    OffsetDateTime.now()
            ));
            writeAudit(
                    "rule_node_approval_closed",
                    rule,
                    "succeeded",
                    auditDetail(
                            "approvalRecordId", closed.id(),
                            "runId", closed.runId(),
                            "nodeId", closed.nodeId(),
                            "status", closed.status(),
                            "reason", closed.approvalComment()
                    )
            );
        }
    }

    private void createSupplementRequest(Rule rule, RuleApprovalRecord rejectedRecord) {
        RuleApprovalRecord supplement = ruleRepository.saveApprovalRecord(new RuleApprovalRecord(
                null,
                rejectedRecord.ruleId(),
                rejectedRecord.runId(),
                rejectedRecord.nodeId(),
                rejectedRecord.assigneeRole(),
                rejectedRecord.delegateRole(),
                rejectedRecord.delegateActiveFrom(),
                rejectedRecord.delegateActiveTo(),
                rejectedRecord.approvalTitle(),
                "supplement_required",
                rejectedRecord.createdByUserId(),
                rejectedRecord.approvedByUserId(),
                rejectedRecord.approvalComment(),
                rejectedRecord.approvedAt(),
                OffsetDateTime.now(),
                rejectedRecord.slaHours(),
                0,
                null
        ));
        writeAudit("rule_approval_supplement_required", rule, "succeeded", auditDetail(
                "sourceRejectedApprovalRecordId", rejectedRecord.id(),
                "supplementRecordId", supplement.id(),
                "runId", supplement.runId(),
                "nodeId", supplement.nodeId(),
                "assigneeRole", supplement.assigneeRole(),
                "comment", supplement.approvalComment()
        ));
    }

    public Map<String, Object> remindApprovalRecord(Long ruleId, Long approvalRecordId) {
        Rule rule = findRule(ruleId);
        RuleApprovalRecord record = ruleRepository.findApprovalRecordById(approvalRecordId)
                .orElseThrow(() -> new IllegalArgumentException("approval record not found: " + approvalRecordId));
        if (!ruleId.equals(record.ruleId())) {
            throw new IllegalArgumentException("approval record does not belong to rule: " + approvalRecordId);
        }
        if (!"pending".equals(record.status())) {
            throw new IllegalStateException("approval record already handled: " + approvalRecordId);
        }
        ensureApprovalRecordAccess(record);
        RuleApprovalRecord reminded = ruleRepository.updateApprovalRecord(record.remind(OffsetDateTime.now()));
        createApprovalReminderAlert(rule, reminded);
        writeAudit(
                "rule_approval_reminder_requested",
                rule,
                "succeeded",
                auditDetail(
                        "approvalRecordId", reminded.id(),
                        "runId", reminded.runId(),
                        "nodeId", reminded.nodeId(),
                        "assigneeRole", reminded.assigneeRole(),
                        "approvalTitle", reminded.approvalTitle(),
                        "remindCount", reminded.remindCount()
                )
        );
        return toApprovalRecordResponse(reminded);
    }

    public boolean createApprovalSlaOverdueAlert(RuleApprovalRecord approvalRecord) {
        if (!isApprovalOverdue(approvalRecord)) {
            return false;
        }
        Rule rule = findRule(approvalRecord.ruleId());
        String dedupeKey = approvalSlaOverdueDedupeKey(rule.id(), approvalRecord.id());
        ApprovalSlaEscalation escalation = approvalSlaEscalation(rule, approvalRecord);
        ApprovalAlertRecipient recipient = resolveSlaOverdueAlertRecipient(approvalRecord, escalation);
        Long recipientUserId = recipient.userId();
        if (systemAlertRepository.existsUnreadByDedupeKey(
                recipientUserId,
                "rule_approval_sla_overdue",
                "rule",
                rule.id(),
                dedupeKey
        )) {
            return false;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", rule.id());
        payload.put("ruleName", rule.name());
        payload.put("approvalRecordId", approvalRecord.id());
        payload.put("runId", approvalRecord.runId());
        payload.put("nodeId", approvalRecord.nodeId());
        payload.put("assigneeRole", approvalRecord.assigneeRole());
        payload.put("approvalTitle", approvalRecord.approvalTitle());
        payload.put("slaHours", approvalRecord.slaHours());
        payload.put("slaDueAt", calculateSlaDueAt(approvalRecord));
        payload.put("dedupeKey", dedupeKey);
        payload.put("recipientSource", recipient.source());
        if (escalation != null) {
            payload.put("escalationRole", escalation.role());
            if (escalation.afterHours() != null) {
                payload.put("escalationAfterHours", escalation.afterHours());
            }
        }
        systemAlertRepository.save(SystemAlert.unread(
                recipientUserId,
                "rule_approval_sla_overdue",
                "warning",
                "rule",
                rule.id(),
                payload
        ));
        writeAudit(
                "rule_approval_sla_overdue",
                rule,
                "succeeded",
                auditDetail(
                        "approvalRecordId", approvalRecord.id(),
                        "runId", approvalRecord.runId(),
                        "nodeId", approvalRecord.nodeId(),
                        "assigneeRole", approvalRecord.assigneeRole(),
                        "approvalTitle", approvalRecord.approvalTitle(),
                        "slaHours", approvalRecord.slaHours(),
                        "slaDueAt", calculateSlaDueAt(approvalRecord),
                        "recipientSource", recipient.source(),
                        "escalationRole", escalation == null ? null : escalation.role(),
                        "escalationAfterHours", escalation == null ? null : escalation.afterHours()
                )
        );
        return true;
    }

    public Map<String, Object> batchHandleApprovalRecords(Map<String, Object> request) {
        String action = string(request == null ? null : request.get("action"));
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new IllegalArgumentException("approval action must be approve or reject");
        }
        List<Long> approvalRecordIds = list(request == null ? null : request.get("approvalRecordIds")).stream()
                .map(this::nullableLong)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (approvalRecordIds.isEmpty()) {
            throw new IllegalArgumentException("approvalRecordIds is required");
        }
        String comment = string(request == null ? null : request.get("comment"));
        List<Map<String, Object>> items = approvalRecordIds.stream()
                .map(approvalRecordId -> batchHandleApprovalRecord(approvalRecordId, action, comment))
                .toList();
        long succeededCount = items.stream().filter(item -> "succeeded".equals(item.get("result"))).count();
        Map<String, Object> detail = auditDetail(
                "action", action,
                "requestedCount", approvalRecordIds.size(),
                "succeededCount", succeededCount,
                "failedCount", approvalRecordIds.size() - succeededCount
        );
        if (comment != null && !comment.isBlank()) {
            detail.put("comment", comment);
        }
        writeAudit("rule_approval_batch_" + action, null, succeededCount == approvalRecordIds.size() ? "succeeded" : "partial_failed", detail);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("requestedCount", approvalRecordIds.size());
        result.put("succeededCount", succeededCount);
        result.put("failedCount", approvalRecordIds.size() - succeededCount);
        result.put("items", items);
        return result;
    }

    private Map<String, Object> batchHandleApprovalRecord(Long approvalRecordId, String action, String comment) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("approvalRecordId", approvalRecordId);
        try {
            RuleApprovalRecord record = ruleRepository.findApprovalRecordById(approvalRecordId)
                    .orElseThrow(() -> new IllegalArgumentException("approval record not found: " + approvalRecordId));
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("action", action);
            if (comment != null && !comment.isBlank()) {
                request.put("comment", comment);
            }
            Map<String, Object> handled = handleApprovalRecord(record.ruleId(), approvalRecordId, request);
            item.put("result", "succeeded");
            item.putAll(handled);
        } catch (RuntimeException error) {
            item.put("result", "failed");
            item.put("errorMessage", errorMessage(error));
        }
        return item;
    }

    public Map<String, Object> metrics(Long ruleId) {
        findRule(ruleId);
        RuleRunMetrics metrics = ruleRepository.metrics(ruleId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", ruleId);
        result.put("totalRuns", metrics.totalRuns());
        result.put("succeededRuns", metrics.succeededRuns());
        result.put("failedRuns", metrics.failedRuns());
        result.put("successRate", metrics.totalRuns() == 0 ? 0d : (double) metrics.succeededRuns() / metrics.totalRuns());
        result.put("averageDurationMs", metrics.averageDurationMs());
        result.put("lastStatus", metrics.lastStatus());
        result.put("lastErrorMessage", metrics.lastErrorMessage());
        return result;
    }

    public Map<String, Object> configureSchedule(Long ruleId, Map<String, Object> request) {
        Rule rule = findRule(ruleId);
        boolean scheduleEnabled = booleanValue(request == null ? null : request.get("scheduleEnabled"));
        Integer intervalSeconds = nullableInteger(request == null ? null : request.get("scheduleIntervalSeconds"));
        Integer maxRetryCount = nullableInteger(request == null ? null : request.get("maxRetryCount"));
        OffsetDateTime nextRunAt = nullableOffsetDateTime(request == null ? null : request.get("nextRunAt"));
        Map<String, Object> scheduleInput = map(request == null ? null : request.get("scheduleInput"));
        if (scheduleEnabled && !"published".equals(rule.status())) {
            throw new IllegalStateException("rule must be published before enabling schedule");
        }
        if (scheduleEnabled && (intervalSeconds == null || intervalSeconds < 30)) {
            throw new IllegalArgumentException("scheduleIntervalSeconds must be at least 30 when schedule is enabled");
        }
        if (maxRetryCount != null && (maxRetryCount < 0 || maxRetryCount > 20)) {
            throw new IllegalArgumentException("maxRetryCount must be between 0 and 20");
        }
        Rule saved = ruleRepository.save(rule.withSchedule(
                scheduleEnabled,
                intervalSeconds,
                scheduleEnabled ? (nextRunAt == null ? OffsetDateTime.now() : nextRunAt) : null,
                0,
                maxRetryCount == null ? 3 : maxRetryCount,
                scheduleInput
        ));
        return toResponse(saved);
    }

    public Map<String, Object> retrySchedule(Long ruleId, Map<String, Object> request) {
        Rule rule = findRule(ruleId);
        if (!Boolean.TRUE.equals(rule.scheduleEnabled())) {
            throw new IllegalStateException("rule schedule is not enabled");
        }
        if (!"published".equals(rule.status())) {
            throw new IllegalStateException("rule must be published before retrying schedule");
        }
        OffsetDateTime nextRunAt = nullableOffsetDateTime(request == null ? null : request.get("nextRunAt"));
        Map<String, Object> scheduleInput = map(request == null ? null : request.get("scheduleInput"));
        Rule saved = ruleRepository.updateScheduleState(
                rule.id(),
                nextRunAt == null ? OffsetDateTime.now() : nextRunAt,
                0
        );
        if (!scheduleInput.isEmpty()) {
            saved = ruleRepository.save(saved.withSchedule(
                    saved.scheduleEnabled(),
                    saved.scheduleIntervalSeconds(),
                    saved.nextRunAt(),
                    saved.failureCount(),
                    saved.maxRetryCount(),
                    scheduleInput
            ));
        }
        writeAudit("rule_schedule_retry", saved, "succeeded", auditDetail(
                "failureCount", saved.failureCount() == null ? 0 : saved.failureCount(),
                "nextRunAt", saved.nextRunAt() == null ? "" : saved.nextRunAt().toString()
        ));
        return toResponse(saved);
    }

    public Map<String, Object> retryWebhookActionExecution(Long ruleId, Long actionExecutionId) {
        Rule rule = findRule(ruleId);
        RuleActionExecution execution = ruleRepository.findActionExecutionById(actionExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("rule action execution not found: " + actionExecutionId));
        if (!ruleId.equals(execution.ruleId())) {
            throw new IllegalArgumentException("rule action execution does not belong to rule: " + actionExecutionId);
        }
        if (!"webhook".equals(execution.actionType())) {
            throw new IllegalStateException("only webhook action executions can be retried");
        }
        if (!"pending_retry".equals(execution.status()) && !"failed".equals(execution.status())) {
            throw new IllegalStateException("only pending or failed webhook action executions can be retried");
        }
        RuleWebhookClient webhookClient = ruleWebhookClient
                .orElseThrow(() -> new IllegalStateException("webhook client required for webhook action"));
        String method = string(execution.metadata().getOrDefault("method", "POST"));
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Idempotency-Key", execution.idempotencyKey());
        Object signatureAlgorithm = execution.metadata().get("signatureAlgorithm");
        Object signaturePayload = execution.metadata().get("signaturePayload");
        if (signatureAlgorithm != null && signaturePayload != null) {
            headers.put("X-Signature-Algorithm", signatureAlgorithm);
            headers.put("X-Signature-Payload", signaturePayload);
        }
        Map<String, Object> body = auditDetail(
                "ruleId", rule.id(),
                "ruleName", rule.name(),
                "runId", execution.runId(),
                "nodeId", execution.nodeId(),
                "retryOfActionExecutionId", actionExecutionId
        );
        int attempt = execution.attempt() == null ? 1 : execution.attempt() + 1;
        Map<String, Object> retryMetadata = actionCompensationMetadata(execution, actionExecutionId);
        try {
            webhookClient.send(execution.endpoint(), method, headers, body);
            RuleActionExecution saved = ruleRepository.saveActionExecution(execution.withStatus(
                    "succeeded",
                    attempt,
                    null,
                    null,
                    retryMetadata
            ));
            writeAudit("rule_action_webhook_retry", rule, "succeeded", auditDetail(
                    "sourceActionExecutionId", actionExecutionId,
                    "actionExecutionId", saved.id(),
                    "runId", execution.runId(),
                    "nodeId", execution.nodeId(),
                    "endpoint", execution.endpoint(),
                    "attempt", attempt,
                    "idempotencyKey", execution.idempotencyKey()
            ));
            return toActionExecutionResponse(saved, actionExecutionId);
        } catch (RuntimeException error) {
            RuleActionExecution saved = ruleRepository.saveActionExecution(execution.withStatus(
                    "pending_retry",
                    attempt,
                    OffsetDateTime.now().plusSeconds(Math.max(0, nullableInteger(execution.metadata().get("retryBackoffSeconds")) == null
                            ? 0
                            : nullableInteger(execution.metadata().get("retryBackoffSeconds")))),
                    errorMessage(error),
                    retryMetadata
            ));
                writeAudit("rule_action_webhook_retry", rule, "failed", auditDetail(
                    "sourceActionExecutionId", actionExecutionId,
                    "actionExecutionId", saved.id(),
                    "runId", execution.runId(),
                    "nodeId", execution.nodeId(),
                    "endpoint", execution.endpoint(),
                    "attempt", attempt,
                    "idempotencyKey", execution.idempotencyKey(),
                    "errorMessage", errorMessage(error)
            ));
            throw new IllegalStateException("webhook action retry failed: actionExecutionId=" + actionExecutionId + ", " + errorMessage(error), error);
        }
    }

    public Map<String, Object> exhaustWebhookActionReplay(Long ruleId, Long actionExecutionId, int maxAsyncReplayAttempts) {
        Rule rule = findRule(ruleId);
        RuleActionExecution execution = ruleRepository.findActionExecutionById(actionExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("rule action execution not found: " + actionExecutionId));
        if (!ruleId.equals(execution.ruleId())) {
            throw new IllegalArgumentException("rule action execution does not belong to rule: " + actionExecutionId);
        }
        if (!"webhook".equals(execution.actionType())) {
            throw new IllegalStateException("only webhook action executions can be exhausted");
        }
        if (!"pending_retry".equals(execution.status()) && !"failed".equals(execution.status())) {
            throw new IllegalStateException("only pending or failed webhook action executions can be exhausted");
        }
        Long sourceActionExecutionId = originalSourceActionExecutionId(execution, actionExecutionId);
        Map<String, Object> metadata = new LinkedHashMap<>(execution.metadata());
        metadata.put("maxAsyncReplayAttempts", maxAsyncReplayAttempts);
        metadata.put("sourceActionExecutionId", sourceActionExecutionId);
        metadata.put("exhaustedSourceActionExecutionId", actionExecutionId);
        RuleActionExecution saved = ruleRepository.saveActionExecution(execution.withTerminalStatus(
                "compensation_exhausted",
                "webhook async replay attempts exhausted",
                metadata
        ));
        writeAudit("rule_action_webhook_replay_exhausted", rule, "failed", auditDetail(
                "sourceActionExecutionId", sourceActionExecutionId,
                "exhaustedSourceActionExecutionId", actionExecutionId,
                "actionExecutionId", saved.id(),
                "runId", execution.runId(),
                "nodeId", execution.nodeId(),
                "endpoint", execution.endpoint(),
                "attempt", execution.attempt(),
                "maxAsyncReplayAttempts", maxAsyncReplayAttempts,
                "idempotencyKey", execution.idempotencyKey()
        ));
        return toActionExecutionResponse(saved, sourceActionExecutionId);
    }

    public Map<String, Object> batchHandleWebhookActionExecutions(Long ruleId, Map<String, Object> request) {
        Rule rule = findRule(ruleId);
        String operation = string(request == null ? null : request.get("operation"));
        if (!"retry".equals(operation) && !"ignore".equals(operation)) {
            throw new IllegalArgumentException("operation must be retry or ignore");
        }
        List<Long> actionExecutionIds = list(request == null ? null : request.get("actionExecutionIds")).stream()
                .map(this::nullableLong)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (actionExecutionIds.isEmpty()) {
            throw new IllegalArgumentException("actionExecutionIds is required");
        }
        String reason = string(request == null ? null : request.get("reason"));
        List<Map<String, Object>> items = actionExecutionIds.stream()
                .map(actionExecutionId -> batchHandleWebhookActionExecution(ruleId, actionExecutionId, operation, reason))
                .toList();
        long succeededCount = items.stream().filter(item -> "succeeded".equals(item.get("result"))).count();
        Map<String, Object> detail = auditDetail(
                "operation", operation,
                "requestedCount", actionExecutionIds.size(),
                "succeededCount", succeededCount,
                "failedCount", actionExecutionIds.size() - succeededCount
        );
        if (reason != null && !reason.isBlank()) {
            detail.put("reason", reason);
        }
        writeAudit("rule_action_webhook_batch_" + operation, rule, succeededCount == actionExecutionIds.size() ? "succeeded" : "partial_failed", detail);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", operation);
        result.put("ruleId", ruleId);
        result.put("requestedCount", actionExecutionIds.size());
        result.put("succeededCount", succeededCount);
        result.put("failedCount", actionExecutionIds.size() - succeededCount);
        result.put("items", items);
        return result;
    }

    private Map<String, Object> batchHandleWebhookActionExecution(Long ruleId, Long actionExecutionId, String operation, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actionExecutionId", actionExecutionId);
        try {
            Map<String, Object> handled = "retry".equals(operation)
                    ? retryWebhookActionExecution(ruleId, actionExecutionId)
                    : ignoreWebhookActionExecution(ruleId, actionExecutionId, reason);
            item.put("result", "succeeded");
            item.put("handledActionExecutionId", handled.get("actionExecutionId"));
            item.put("status", handled.get("status"));
        } catch (RuntimeException error) {
            item.put("result", "failed");
            item.put("errorMessage", errorMessage(error));
        }
        return item;
    }

    public Map<String, Object> ignoreWebhookActionExecution(Long ruleId, Long actionExecutionId, String reason) {
        Rule rule = findRule(ruleId);
        RuleActionExecution execution = ruleRepository.findActionExecutionById(actionExecutionId)
                .orElseThrow(() -> new IllegalArgumentException("rule action execution not found: " + actionExecutionId));
        if (!ruleId.equals(execution.ruleId())) {
            throw new IllegalArgumentException("rule action execution does not belong to rule: " + actionExecutionId);
        }
        if (!"webhook".equals(execution.actionType())) {
            throw new IllegalStateException("only webhook action executions can be ignored");
        }
        if (!"pending_retry".equals(execution.status()) && !"failed".equals(execution.status())) {
            throw new IllegalStateException("only pending or failed webhook action executions can be ignored");
        }
        Map<String, Object> metadata = actionCompensationMetadata(execution, actionExecutionId);
        metadata.put("ignoredSourceActionExecutionId", actionExecutionId);
        if (reason != null && !reason.isBlank()) {
            metadata.put("ignoreReason", reason);
        }
        RuleActionExecution saved = ruleRepository.saveActionExecution(execution.withTerminalStatus(
                "compensation_ignored",
                reason == null || reason.isBlank() ? "webhook compensation ignored" : reason,
                metadata
        ));
        Map<String, Object> detail = auditDetail(
                "sourceActionExecutionId", actionExecutionId,
                "actionExecutionId", saved.id(),
                "runId", execution.runId(),
                "nodeId", execution.nodeId(),
                "endpoint", execution.endpoint(),
                "attempt", execution.attempt(),
                "idempotencyKey", execution.idempotencyKey()
        );
        if (reason != null && !reason.isBlank()) {
            detail.put("reason", reason);
        }
        writeAudit("rule_action_webhook_ignore", rule, "succeeded", detail);
        return toActionExecutionResponse(saved, actionExecutionId);
    }

    public PageResponse<Map<String, Object>> listActionExecutions(Long ruleId, int page, int pageSize) {
        findRule(ruleId);
        List<Map<String, Object>> items = ruleRepository.findActionExecutions(ruleId, page, pageSize).stream()
                .map(execution -> toActionExecutionResponse(execution, sourceActionExecutionId(execution)))
                .toList();
        return new PageResponse<>(items, page, pageSize, ruleRepository.countActionExecutions(ruleId));
    }

    private Map<String, Object> actionCompensationMetadata(RuleActionExecution execution, Long sourceActionExecutionId) {
        Map<String, Object> metadata = new LinkedHashMap<>(execution.metadata());
        metadata.put("sourceActionExecutionId", originalSourceActionExecutionId(execution, sourceActionExecutionId));
        return metadata;
    }

    private Long sourceActionExecutionId(RuleActionExecution execution) {
        return nullableLong(execution.metadata().get("sourceActionExecutionId"));
    }

    private Long originalSourceActionExecutionId(RuleActionExecution execution, Long fallbackActionExecutionId) {
        Long sourceActionExecutionId = sourceActionExecutionId(execution);
        return sourceActionExecutionId == null ? fallbackActionExecutionId : sourceActionExecutionId;
    }

    public Map<String, Object> runScheduledRule(Rule rule) {
        Map<String, Object> scheduleInput = rule.scheduleInput() == null || rule.scheduleInput().isEmpty()
                ? Map.of("sample", Map.of())
                : rule.scheduleInput();
        return execute(rule.id(), scheduleInput);
    }

    private void ensureProductionTraceWithinBudget(Map<String, Object> output) {
        Integer evaluatedNodeCount = nullableInteger(output.get("evaluatedNodes"));
        int evaluatedNodes = evaluatedNodeCount == null ? 0 : evaluatedNodeCount;
        if (evaluatedNodes > MAX_PRODUCTION_TRACE_NODES) {
            throw new IllegalStateException("rule execution node budget exceeded: "
                    + evaluatedNodes + " > " + MAX_PRODUCTION_TRACE_NODES);
        }
    }

    private void ensureProductionExecutionWithinDurationBudget(long startedAt) {
        long elapsedMillis = elapsedMillis(startedAt);
        if (elapsedMillis > MAX_PRODUCTION_EXECUTION_MILLIS) {
            throw new IllegalStateException("rule execution duration budget exceeded: "
                    + elapsedMillis + "ms > " + MAX_PRODUCTION_EXECUTION_MILLIS + "ms");
        }
    }

    private void executeRuleActions(Rule rule, RuleDebugRun run, Map<String, Object> output) {
        executeRuleActions(rule, run, output, null, true, new java.util.LinkedHashSet<>(java.util.List.of(rule.id())));
    }

    private void executeRuleActionsAfterApproval(Rule rule, RuleDebugRun run, Map<String, Object> output, String approvalNodeId) {
        executeRuleActions(rule, run, output, approvalNodeId, false, new java.util.LinkedHashSet<>(java.util.List.of(rule.id())));
    }

    private void executeRuleActions(Rule rule,
                                    RuleDebugRun run,
                                    Map<String, Object> output,
                                    String startAfterNodeId,
                                    boolean stopAtApproval,
                                    Set<Long> subprocessStack) {
        Map<String, Map<String, Object>> nodesById = nodesById(rule.definition());
        boolean started = startAfterNodeId == null;
        for (Object traceItem : list(output.get("trace"))) {
            if (!(traceItem instanceof Map<?, ?> rawTrace)) {
                continue;
            }
            String nodeId = string(rawTrace.get("nodeId"));
            if (!started) {
                if (startAfterNodeId.equals(nodeId)) {
                    started = true;
                }
                continue;
            }
            Map<String, Object> node = nodesById.get(nodeId);
            if (node == null) {
                continue;
            }
            if ("approval".equals(string(node.get("type")))) {
                if (stopAtApproval || !hasApprovalRecordsForRunNode(rule.id(), run.id(), nodeId)) {
                    createApprovalRecord(rule, run, nodeId, node);
                }
                break;
            }
            if ("subprocess".equals(string(node.get("type")))) {
                if (!executeSubprocessNode(rule, run, nodeId, node, subprocessStack)) {
                    break;
                }
                continue;
            }
            if (!"action".equals(string(node.get("type")))) {
                continue;
            }
            executeActionNode(rule, run, nodeId, node);
        }
    }

    private void executeRejectedApprovalBranch(Rule rule, RuleApprovalRecord approvalRecord) {
        RuleDebugRun run = ruleRepository.findRunById(approvalRecord.runId())
                .orElseThrow(() -> new IllegalStateException("rule run not found for approval record: " + approvalRecord.runId()));
        Map<String, Map<String, Object>> nodesById = nodesById(rule.definition());
        String nodeId = nextNodeId(rule.definition(), approvalRecord.nodeId(), "rejected");
        Set<String> visited = new java.util.HashSet<>();
        while (nodeId != null) {
            if (!visited.add(nodeId)) {
                throw new IllegalStateException("rule rejected branch contains a cycle: " + nodeId);
            }
            Map<String, Object> node = nodesById.get(nodeId);
            if (node == null) {
                return;
            }
            String type = string(node.get("type"));
            if ("approval".equals(type)) {
                if (!hasApprovalRecordsForRunNode(rule.id(), run.id(), nodeId)) {
                    createApprovalRecord(rule, run, nodeId, node);
                }
                return;
            }
            if ("action".equals(type)) {
                executeActionNode(rule, run, nodeId, node);
            }
            if ("subprocess".equals(type)) {
                if (!executeSubprocessNode(rule, run, nodeId, node, new java.util.LinkedHashSet<>(java.util.List.of(rule.id())))) {
                    return;
                }
            }
            if ("end".equals(type)) {
                return;
            }
            nodeId = nextNodeId(rule.definition(), nodeId, null);
        }
    }

    private void executeActionNode(Rule rule, RuleDebugRun run, String nodeId, Map<String, Object> node) {
        String actionType = string(node.get("actionType"));
        if ("notify".equals(actionType)) {
            executeNotifyAction(rule, run, nodeId, node);
        } else if ("create_task".equals(actionType)) {
            executeCreateTaskAction(node);
        } else if ("webhook".equals(actionType)) {
            executeWebhookAction(rule, run, nodeId, node);
        }
    }

    private boolean executeSubprocessNode(Rule parentRule, RuleDebugRun parentRun, String nodeId, Map<String, Object> node, Set<Long> subprocessStack) {
        Long subprocessRuleId = nullableLong(node.get("subprocessRuleId"));
        if (subprocessRuleId == null) {
            throw new IllegalArgumentException("subprocessRuleId is required");
        }
        if (subprocessStack.size() >= MAX_SUBPROCESS_STACK_DEPTH) {
            throw new IllegalStateException("subprocess depth limit exceeded: "
                    + subprocessStack.size() + " >= " + MAX_SUBPROCESS_STACK_DEPTH);
        }
        if (subprocessStack.contains(subprocessRuleId)) {
            throw new IllegalStateException("subprocess cycle detected: " + subprocessStack + " -> " + subprocessRuleId);
        }
        Rule subprocessRule = findRule(subprocessRuleId);
        if (!"published".equals(subprocessRule.status())) {
            throw new IllegalStateException("subprocess rule must be published before production execution: " + subprocessRuleId);
        }
        long startedAt = nanoTime();
        Map<String, Object> input = parentRun.input() == null ? Map.of() : parentRun.input();
        RuleDebugRun subprocessRun = null;
        try {
            domainService.ensureDebugInputValid(input);
            Map<String, Object> output = domainService.executeDebug(subprocessRule.definition(), input);
            subprocessRun = ruleRepository.saveDebugRun(RuleDebugRun.succeededProduction(
                    subprocessRule.id(),
                    subprocessRule.currentVersionId(),
                    currentUserId(),
                    elapsedMillis(startedAt),
                    input,
                    output
            ));
            Set<Long> childStack = new java.util.LinkedHashSet<>(subprocessStack);
            childStack.add(subprocessRule.id());
            executeRuleActions(subprocessRule, subprocessRun, output, null, true, childStack);
            if (hasPendingApprovalForRun(subprocessRule.id(), subprocessRun.id())) {
                subprocessRun = ruleRepository.updateDebugRun(subprocessRun.withStatus("pending_approval"));
                writeAudit("rule_subprocess_run", parentRule, "pending", auditDetail(
                        "parentRuleId", parentRule.id(),
                        "parentRunId", parentRun.id(),
                        "nodeId", nodeId,
                        "subprocessRuleId", subprocessRule.id(),
                        "subprocessRunId", subprocessRun.id(),
                        "subprocessVersionId", subprocessRule.currentVersionId(),
                        "pendingReason", "approval"
                ));
                return false;
            }
            writeAudit("rule_subprocess_run", parentRule, "succeeded", auditDetail(
                    "parentRuleId", parentRule.id(),
                    "parentRunId", parentRun.id(),
                    "nodeId", nodeId,
                    "subprocessRuleId", subprocessRule.id(),
                    "subprocessRunId", subprocessRun.id(),
                    "subprocessVersionId", subprocessRule.currentVersionId()
            ));
            return true;
        } catch (RuntimeException error) {
            RuleDebugRun failed = ruleRepository.saveDebugRun(RuleDebugRun.failedProduction(
                    subprocessRule.id(),
                    subprocessRule.currentVersionId(),
                    currentUserId(),
                    elapsedMillis(startedAt),
                    errorMessage(error),
                    Map.of()
            ));
            writeAudit("rule_subprocess_run", parentRule, "failed", auditDetail(
                    "parentRuleId", parentRule.id(),
                    "parentRunId", parentRun.id(),
                    "nodeId", nodeId,
                    "subprocessRuleId", subprocessRule.id(),
                    "subprocessRunId", subprocessRun == null ? failed.id() : subprocessRun.id(),
                    "subprocessVersionId", subprocessRule.currentVersionId(),
                    "errorMessage", errorMessage(error)
            ));
            throw error;
        }
    }

    private void resumeRuleActionsAfterApproval(Rule rule, RuleApprovalRecord approvalRecord) {
        RuleDebugRun run = ruleRepository.findRunById(approvalRecord.runId())
                .orElseThrow(() -> new IllegalStateException("rule run not found for approval record: " + approvalRecord.runId()));
        executeRuleActionsAfterApproval(rule, run, run.output(), approvalRecord.nodeId());
        resumeParentRuleActionsAfterSubprocessApproval(rule, run);
    }

    private void resumeParentRuleActionsAfterSubprocessApproval(Rule subprocessRule, RuleDebugRun subprocessRun) {
        List<OperationLog> pendingLinks = auditRepository.findByOperationAndDetail(
                        "rule_subprocess_run",
                        "subprocessRunId",
                        String.valueOf(subprocessRun.id()))
                .stream()
                .filter(log -> "rule".equals(log.resourceType()))
                .filter(log -> "pending".equals(log.result()))
                .toList();
        for (OperationLog link : pendingLinks) {
            Map<String, Object> detail = link.detail() == null ? Map.of() : link.detail();
            Long parentRuleId = nullableLong(detail.get("parentRuleId"));
            Long parentRunId = nullableLong(detail.get("parentRunId"));
            String parentNodeId = string(detail.get("nodeId"));
            if (parentRuleId == null || parentRunId == null || parentNodeId == null) {
                continue;
            }
            Rule parentRule = findRule(parentRuleId);
            RuleDebugRun parentRun = ruleRepository.findRunById(parentRunId)
                    .orElseThrow(() -> new IllegalStateException("parent rule run not found for subprocess resume: " + parentRunId));
            ruleRepository.updateDebugRun(subprocessRun.withStatus("succeeded"));
            executeRuleActions(parentRule, parentRun, parentRun.output(), parentNodeId, false, new java.util.LinkedHashSet<>(java.util.List.of(parentRule.id())));
            writeAudit("rule_subprocess_run", parentRule, "succeeded", auditDetail(
                    "parentRuleId", parentRule.id(),
                    "parentRunId", parentRun.id(),
                    "nodeId", parentNodeId,
                    "subprocessRuleId", subprocessRule.id(),
                    "subprocessRunId", subprocessRun.id(),
                    "subprocessVersionId", subprocessRule.currentVersionId(),
                    "resumedFromPendingLogId", link.id()
            ));
        }
    }

    private boolean shouldResumeAfterApproval(Rule rule, RuleApprovalRecord originalRecord, RuleApprovalRecord updatedRecord) {
        Map<String, Object> approvalNode = approvalNode(rule, updatedRecord.nodeId());
        String mode = approvalMode(approvalNode);
        List<RuleApprovalRecord> siblingRecords = approvalSiblingRecords(updatedRecord);
        if ("any".equals(mode)) {
            return siblingRecords.stream()
                    .filter(record -> !record.id().equals(originalRecord.id()))
                    .noneMatch(record -> "approved".equals(record.status()));
        }
        return siblingRecords.stream().allMatch(record -> "approved".equals(record.status()));
    }

    private Map<String, Object> approvalNode(Rule rule, String nodeId) {
        Map<String, Object> node = nodesById(rule.definition()).get(nodeId);
        if (node == null || !"approval".equals(string(node.get("type")))) {
            return Map.of();
        }
        return node;
    }

    private List<RuleApprovalRecord> approvalSiblingRecords(RuleApprovalRecord approvalRecord) {
        if (!isCurrentApprovalStatus(approvalRecord.status())) {
            return approvalSiblingRecordsIncludingHistory(approvalRecord).stream()
                    .filter(record -> !"supplement_required".equals(record.status()) && !"resubmitted".equals(record.status()))
                    .toList();
        }
        return currentApprovalSiblingRecords(approvalSiblingRecordsIncludingHistory(approvalRecord));
    }

    private List<RuleApprovalRecord> approvalSiblingRecordsIncludingHistory(RuleApprovalRecord approvalRecord) {
        return ruleRepository.findApprovalRecords(approvalRecord.ruleId(), 1, Math.max((int) ruleRepository.countApprovalRecords(approvalRecord.ruleId()), 1))
                .stream()
                .filter(record -> approvalRecord.runId().equals(record.runId()))
                .filter(record -> approvalRecord.nodeId().equals(record.nodeId()))
                .toList();
    }

    private List<RuleApprovalRecord> currentApprovalSiblingRecords(List<RuleApprovalRecord> records) {
        List<RuleApprovalRecord> current = records.stream()
                .filter(record -> "pending".equals(record.status()) || "approved".equals(record.status()))
                .toList();
        if (current.isEmpty()) {
            current = records.stream()
                    .filter(record -> "closed".equals(record.status()) || "rejected".equals(record.status()))
                    .toList();
        }
        return current;
    }

    private boolean isCurrentApprovalStatus(String status) {
        return "pending".equals(status) || "approved".equals(status);
    }

    private boolean hasApprovalRecordsForRunNode(Long ruleId, Long runId, String nodeId) {
        return ruleRepository.findApprovalRecords(ruleId, 1, Math.max((int) ruleRepository.countApprovalRecords(ruleId), 1))
                .stream()
                .filter(record -> runId.equals(record.runId()))
                .filter(record -> nodeId.equals(record.nodeId()))
                .anyMatch(record -> "pending".equals(record.status()) || "approved".equals(record.status()));
    }

    private boolean hasPendingApprovalForRun(Long ruleId, Long runId) {
        return ruleRepository.findApprovalRecords(ruleId, 1, Math.max((int) ruleRepository.countApprovalRecords(ruleId), 1))
                .stream()
                .filter(record -> runId.equals(record.runId()))
                .anyMatch(record -> "pending".equals(record.status()));
    }

    private void createApprovalRecord(Rule rule, RuleDebugRun run, String nodeId, Map<String, Object> node) {
        for (String assigneeRole : approvalAssigneeRoles(node)) {
            ApprovalDelegateSnapshot delegate = approvalDelegateSnapshot(node, assigneeRole);
            RuleApprovalRecord saved = ruleRepository.saveApprovalRecord(RuleApprovalRecord.pending(
                    rule.id(),
                    run.id(),
                    nodeId,
                    assigneeRole,
                    delegate.delegateRole(),
                    delegate.activeFrom(),
                    delegate.activeTo(),
                    string(node.get("approvalTitle")),
                    currentUserId(),
                    nullableInteger(node.get("slaHours"))
            ));
            writeAudit("rule_node_approval_pending", rule, "succeeded", auditDetail(
                    "runId", run.id(),
                    "approvalRecordId", saved.id(),
                    "nodeId", nodeId,
                    "assigneeRole", saved.assigneeRole(),
                    "delegateRole", saved.delegateRole(),
                    "delegateActiveFrom", saved.delegateActiveFrom() == null ? null : saved.delegateActiveFrom().toString(),
                    "delegateActiveTo", saved.delegateActiveTo() == null ? null : saved.delegateActiveTo().toString(),
                    "assigneeRoles", approvalAssigneeRoles(node),
                    "approvalMode", approvalMode(node),
                    "approvalTitle", saved.approvalTitle(),
                    "status", saved.status()
            ));
        }
    }

    private ApprovalDelegateSnapshot approvalDelegateSnapshot(Map<String, Object> node, String assigneeRole) {
        String nodeDelegateRole = approvalDelegateRole(node);
        if (nodeDelegateRole != null) {
            return new ApprovalDelegateSnapshot(
                    nodeDelegateRole,
                    nullableOffsetDateTime(node.get("delegateActiveFrom")),
                    nullableOffsetDateTime(node.get("delegateActiveTo"))
            );
        }
        return ruleRepository.findActiveApprovalDelegateRule(assigneeRole, OffsetDateTime.now())
                .map(rule -> new ApprovalDelegateSnapshot(rule.delegateRole(), rule.activeFrom(), rule.activeTo()))
                .orElse(ApprovalDelegateSnapshot.empty());
    }

    private record ApprovalDelegateSnapshot(String delegateRole, OffsetDateTime activeFrom, OffsetDateTime activeTo) {
        static ApprovalDelegateSnapshot empty() {
            return new ApprovalDelegateSnapshot(null, null, null);
        }
    }

    private List<String> approvalAssigneeRoles(Map<String, Object> node) {
        List<String> roles = new ArrayList<>();
        String legacyRole = string(node.get("assigneeRole"));
        if (legacyRole != null && !legacyRole.isBlank()) {
            roles.add(legacyRole);
        }
        for (Object value : list(node.get("assigneeRoles"))) {
            String role = string(value);
            if (role != null && !role.isBlank() && !roles.contains(role)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private String approvalMode(Map<String, Object> node) {
        String mode = string(node.get("approvalMode"));
        return "any".equals(mode) ? "any" : "all";
    }

    private String approvalDelegateRole(Map<String, Object> node) {
        String role = string(node.get("delegateRole"));
        return role == null || role.isBlank() ? null : role;
    }

    private void executeNotifyAction(Rule rule, RuleDebugRun run, String nodeId, Map<String, Object> node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", rule.id());
        payload.put("ruleName", rule.name());
        payload.put("runId", run.id());
        payload.put("nodeId", nodeId);
        payload.put("actionType", "notify");
        payload.put("message", string(node.get("message")));
        systemAlertRepository.save(SystemAlert.unread(
                currentUserId(),
                "rule_action_notify",
                string(node.get("severity")),
                "rule",
                rule.id(),
                payload
        ));
    }

    private void executeCreateTaskAction(Map<String, Object> node) {
        CollaborationApplicationService collaborationService = collaborationApplicationService
                .orElseThrow(() -> new IllegalStateException("collaboration service required for create_task action"));
        Long reportId = nullableLong(node.get("reportId"));
        if (reportId == null) {
            throw new IllegalArgumentException("create_task action requires reportId");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("content", string(node.get("content")));
        request.put("assigneeUserId", node.get("assigneeUserId"));
        request.put("anchor", map(node.get("anchor")));
        collaborationService.addAnnotation(reportId, request);
    }

    private void executeWebhookAction(Rule rule, RuleDebugRun run, String nodeId, Map<String, Object> node) {
        RuleWebhookClient webhookClient = ruleWebhookClient
                .orElseThrow(() -> new IllegalStateException("webhook client required for webhook action"));
        String endpoint = string(node.get("endpoint"));
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("webhook action requires endpoint");
        }
        String method = string(node.getOrDefault("method", "POST"));
        Map<String, Object> body = new LinkedHashMap<>(map(node.get("body")));
        body.put("ruleId", rule.id());
        body.put("ruleName", rule.name());
        body.put("runId", run.id());
        body.put("nodeId", nodeId);
        Map<String, Object> headers = new LinkedHashMap<>(map(node.get("headers")));
        String idempotencyKey = webhookIdempotencyKey(rule.id(), run.id(), nodeId);
        headers.put("Idempotency-Key", idempotencyKey);
        String signaturePayload = null;
        if (string(node.get("signatureSecret")) != null && !string(node.get("signatureSecret")).isBlank()) {
            signaturePayload = idempotencyKey;
            headers.put("X-Signature-Algorithm", "HMAC-SHA256");
            headers.put("X-Signature-Payload", signaturePayload);
            headers.put("X-Signature", "sha256=" + hmacSha256Hex(string(node.get("signatureSecret")), signaturePayload));
        }
        Integer configuredMaxRetryCount = nullableInteger(node.get("maxRetryCount"));
        Integer configuredBackoffSeconds = nullableInteger(node.get("retryBackoffSeconds"));
        Integer configuredMaxAsyncReplayAttempts = nullableInteger(node.get("maxAsyncReplayAttempts"));
        int maxRetryCount = Math.max(0, configuredMaxRetryCount == null ? 0 : configuredMaxRetryCount);
        int maxAsyncReplayAttempts = Math.max(0, configuredMaxAsyncReplayAttempts == null ? 3 : configuredMaxAsyncReplayAttempts);
        long backoffMillis = Math.max(0L, (configuredBackoffSeconds == null ? 0L : configuredBackoffSeconds.longValue()) * 1000L);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxRetryCount + 1; attempt++) {
            try {
                webhookClient.send(endpoint, method, headers, body);
                writeWebhookActionAudit(rule, run, nodeId, endpoint, idempotencyKey, signaturePayload, attempt, maxRetryCount, "succeeded", null);
                saveWebhookActionExecution(rule, run, nodeId, endpoint, method, idempotencyKey, signaturePayload, attempt, maxRetryCount, configuredBackoffSeconds, maxAsyncReplayAttempts, "succeeded", null);
                return;
            } catch (RuntimeException error) {
                lastError = error;
                writeWebhookActionAudit(rule, run, nodeId, endpoint, idempotencyKey, signaturePayload, attempt, maxRetryCount, "failed", error);
                if (attempt <= maxRetryCount) {
                    sleepBeforeWebhookRetry(backoffMillis);
                }
            }
        }
        saveWebhookActionExecution(rule, run, nodeId, endpoint, method, idempotencyKey, signaturePayload, maxRetryCount + 1, maxRetryCount, configuredBackoffSeconds, maxAsyncReplayAttempts, "pending_retry", lastError);
        createWebhookFailureAlert(rule, run, nodeId, endpoint, idempotencyKey, lastError);
        throw new IllegalStateException(
                "webhook action failed: nodeId=" + nodeId + ", endpoint=" + endpoint + ", " + errorMessage(lastError),
                lastError
        );
    }

    private String webhookIdempotencyKey(Long ruleId, Long runId, String nodeId) {
        return "rule-" + ruleId + "-run-" + runId + "-node-" + nodeId;
    }

    private void writeWebhookActionAudit(Rule rule,
                                         RuleDebugRun run,
                                         String nodeId,
                                         String endpoint,
                                         String idempotencyKey,
                                         String signaturePayload,
                                         int attempt,
                                         int maxRetryCount,
                                         String result,
                                         RuntimeException error) {
        Map<String, Object> detail = auditDetail(
                "runId", run.id(),
                "nodeId", nodeId,
                "actionType", "webhook",
                "endpoint", endpoint,
                "idempotencyKey", idempotencyKey,
                "attempt", attempt,
                "maxRetryCount", maxRetryCount
        );
        if (signaturePayload != null) {
            detail.put("signatureAlgorithm", "HMAC-SHA256");
            detail.put("signaturePayload", signaturePayload);
        }
        if (error != null) {
            detail.put("errorMessage", errorMessage(error));
        }
        writeAudit("rule_action_webhook", rule, result, detail);
    }

    private void saveWebhookActionExecution(Rule rule,
                                            RuleDebugRun run,
                                            String nodeId,
                                            String endpoint,
                                            String method,
                                            String idempotencyKey,
                                            String signaturePayload,
                                            int attempt,
                                            int maxRetryCount,
                                            Integer retryBackoffSeconds,
                                            int maxAsyncReplayAttempts,
                                            String status,
                                            RuntimeException error) {
        Map<String, Object> metadata = auditDetail(
                "method", method,
                "retryBackoffSeconds", retryBackoffSeconds == null ? 0 : retryBackoffSeconds,
                "maxAsyncReplayAttempts", maxAsyncReplayAttempts
        );
        if (signaturePayload != null) {
            metadata.put("signatureAlgorithm", "HMAC-SHA256");
            metadata.put("signaturePayload", signaturePayload);
        }
        OffsetDateTime nextRetryAt = "pending_retry".equals(status)
                ? OffsetDateTime.now().plusSeconds(Math.max(0, retryBackoffSeconds == null ? 0 : retryBackoffSeconds))
                : null;
        ruleRepository.saveActionExecution(RuleActionExecution.webhook(
                rule.id(),
                run.id(),
                nodeId,
                status,
                attempt,
                maxRetryCount,
                endpoint,
                idempotencyKey,
                nextRetryAt,
                error == null ? null : errorMessage(error),
                metadata
        ));
    }

    private void sleepBeforeWebhookRetry(long backoffMillis) {
        if (backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("webhook retry interrupted", error);
        }
    }

    private void createWebhookFailureAlert(Rule rule,
                                           RuleDebugRun run,
                                           String nodeId,
                                           String endpoint,
                                           String idempotencyKey,
                                           RuntimeException error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", rule.id());
        payload.put("ruleName", rule.name());
        payload.put("runId", run.id());
        payload.put("nodeId", nodeId);
        payload.put("actionType", "webhook");
        payload.put("endpoint", endpoint);
        payload.put("idempotencyKey", idempotencyKey);
        payload.put("errorMessage", errorMessage(error));
        String dedupeKey = "rule:" + rule.id() + ":webhook:" + nodeId + ":" + endpoint;
        payload.put("dedupeKey", dedupeKey);
        if (systemAlertRepository.existsUnreadByDedupeKey(
                currentUserId(),
                "rule_action_webhook_failed",
                "rule",
                rule.id(),
                dedupeKey
        )) {
            return;
        }
        systemAlertRepository.save(SystemAlert.unread(
                currentUserId(),
                "rule_action_webhook_failed",
                "error",
                "rule",
                rule.id(),
                payload
        ));
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        return Long.valueOf(text);
    }

    public void createScheduledFailureAlert(Rule rule, Map<String, Object> failedRun, int nextFailureCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", rule.id());
        payload.put("ruleName", rule.name());
        payload.put("runId", failedRun.get("runId"));
        payload.put("errorMessage", failedRun.get("errorMessage"));
        payload.put("failureCount", nextFailureCount);
        payload.put("maxRetryCount", rule.maxRetryCount() == null ? 3 : rule.maxRetryCount());
        payload.put("nextRunAt", rule.nextRunAt() == null ? "" : rule.nextRunAt().toString());
        systemAlertRepository.save(SystemAlert.unread(
                1L,
                "rule_schedule_run_failed",
                "warning",
                "rule",
                rule.id(),
                payload
        ));
    }

    private void createApprovalRejectedAlert(Rule rule, RuleApprovalRecord approvalRecord) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", rule.id());
        payload.put("ruleName", rule.name());
        payload.put("approvalRecordId", approvalRecord.id());
        payload.put("runId", approvalRecord.runId());
        payload.put("nodeId", approvalRecord.nodeId());
        payload.put("status", approvalRecord.status());
        payload.put("comment", approvalRecord.approvalComment());
        payload.put("assigneeRole", approvalRecord.assigneeRole());
        payload.put("approvalTitle", approvalRecord.approvalTitle());
        systemAlertRepository.save(SystemAlert.unread(
                currentUserId(),
                "rule_approval_rejected",
                "warning",
                "rule",
                rule.id(),
                payload
        ));
    }

    private void createApprovalReminderAlert(Rule rule, RuleApprovalRecord approvalRecord) {
        ApprovalAlertRecipient recipient = resolveApprovalAlertRecipient(approvalRecord);
        Long recipientUserId = recipient.userId();
        String dedupeKey = approvalReminderDedupeKey(rule.id(), approvalRecord.id());
        if (systemAlertRepository.existsUnreadByDedupeKey(
                recipientUserId,
                "rule_approval_reminder_requested",
                "rule",
                rule.id(),
                dedupeKey
        )) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", rule.id());
        payload.put("ruleName", rule.name());
        payload.put("approvalRecordId", approvalRecord.id());
        payload.put("runId", approvalRecord.runId());
        payload.put("nodeId", approvalRecord.nodeId());
        payload.put("assigneeRole", approvalRecord.assigneeRole());
        payload.put("approvalTitle", approvalRecord.approvalTitle());
        payload.put("remindCount", approvalRecord.remindCount());
        payload.put("createdByUserId", approvalRecord.createdByUserId());
        payload.put("dedupeKey", dedupeKey);
        payload.put("recipientSource", recipient.source());
        systemAlertRepository.save(SystemAlert.unread(
                recipientUserId,
                "rule_approval_reminder_requested",
                "warning",
                "rule",
                rule.id(),
                payload
        ));
    }

    private String approvalReminderDedupeKey(Long ruleId, Long approvalRecordId) {
        return "rule:%d:approval:%d:reminder".formatted(ruleId, approvalRecordId);
    }

    private ApprovalAlertRecipient resolveApprovalAlertRecipient(RuleApprovalRecord approvalRecord) {
        Optional<UserAccount> assignee = userRepository
                .flatMap(repository -> repository.findEnabledByRole(approvalRecord.assigneeRole()).stream().findFirst());
        if (assignee.isPresent()) {
            return new ApprovalAlertRecipient(assignee.get().id(), "assigneeRole");
        }
        return new ApprovalAlertRecipient(
                approvalRecord.createdByUserId() == null ? currentUserId() : approvalRecord.createdByUserId(),
                "createdByUserId"
        );
    }

    private ApprovalAlertRecipient resolveSlaOverdueAlertRecipient(RuleApprovalRecord approvalRecord, ApprovalSlaEscalation escalation) {
        if (escalation != null) {
            Optional<UserAccount> escalationUser = userRepository
                    .flatMap(repository -> repository.findEnabledByRole(escalation.role()).stream().findFirst());
            if (escalationUser.isPresent()) {
                return new ApprovalAlertRecipient(escalationUser.get().id(), escalation.source());
            }
        }
        return resolveApprovalAlertRecipient(approvalRecord);
    }

    private ApprovalSlaEscalation approvalSlaEscalation(Rule rule, RuleApprovalRecord approvalRecord) {
        Map<String, Object> node = approvalNode(rule, approvalRecord.nodeId());
        ApprovalSlaEscalation configured = highestMatchedApprovalSlaEscalation(node, approvalRecord);
        if (configured != null) {
            return configured;
        }
        String legacyRole = blankToNull(string(node.get("slaEscalationRole")));
        return legacyRole == null ? null : new ApprovalSlaEscalation(legacyRole, null, "slaEscalationRole");
    }

    private ApprovalSlaEscalation highestMatchedApprovalSlaEscalation(Map<String, Object> node, RuleApprovalRecord approvalRecord) {
        if (approvalRecord.createdAt() == null) {
            return null;
        }
        long overdueHours = java.time.Duration.between(approvalRecord.createdAt(), OffsetDateTime.now()).toHours();
        ApprovalSlaEscalation result = null;
        for (Object item : list(node.get("slaEscalations"))) {
            if (!(item instanceof Map<?, ?> rawPolicy)) {
                continue;
            }
            Map<String, Object> policy = new LinkedHashMap<>();
            rawPolicy.forEach((key, value) -> policy.put(String.valueOf(key), value));
            Integer afterHours = nullableInteger(policy.get("afterHours"));
            String role = blankToNull(string(policy.get("role")));
            if (afterHours == null || afterHours <= 0 || role == null || overdueHours < afterHours) {
                continue;
            }
            if (result == null || result.afterHours() == null || afterHours > result.afterHours()) {
                result = new ApprovalSlaEscalation(role, afterHours, "slaEscalationPolicy");
            }
        }
        return result;
    }

    private record ApprovalAlertRecipient(Long userId, String source) {
    }

    private record ApprovalSlaEscalation(String role, Integer afterHours, String source) {
    }

    private Map<String, Map<String, Object>> nodesById(Map<String, Object> definition) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Object item : list(definition.get("nodes"))) {
            if (!(item instanceof Map<?, ?> rawNode)) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            rawNode.forEach((key, value) -> node.put(String.valueOf(key), value));
            result.put(string(node.get("id")), node);
        }
        return result;
    }

    private String nextNodeId(Map<String, Object> definition, String source, String condition) {
        for (Object item : list(definition.get("edges"))) {
            if (!(item instanceof Map<?, ?> rawEdge)) {
                continue;
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            rawEdge.forEach((key, value) -> edge.put(String.valueOf(key), value));
            if (!source.equals(string(edge.get("source")))) {
                continue;
            }
            if (condition == null || condition.equals(string(edge.get("condition")))) {
                return string(edge.get("target"));
            }
        }
        return null;
    }

    private void writeAudit(String operationType, Rule rule, String result, Map<String, Object> detail) {
        auditRepository.save(new OperationLog(
                null,
                currentUserId(),
                operationType,
                "rule",
                rule == null ? null : rule.id(),
                result,
                detail,
                OffsetDateTime.now()
        ));
    }

    private Long currentUserId() {
        var currentUser = CurrentUserHolder.get();
        return currentUser == null ? 1L : currentUser.userId();
    }

    private Rule findRule(Long ruleId) {
        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("rule not found: " + ruleId));
    }

    private Map<String, Object> toResponse(Rule rule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", rule.id());
        result.put("name", rule.name());
        result.put("description", rule.description() == null ? "" : rule.description());
        result.put("status", rule.status());
        result.put("definition", rule.definition());
        result.put("versionId", rule.currentVersionId() == null ? 0L : rule.currentVersionId());
        result.put("scheduleEnabled", Boolean.TRUE.equals(rule.scheduleEnabled()));
        result.put("scheduleIntervalSeconds", rule.scheduleIntervalSeconds());
        result.put("nextRunAt", rule.nextRunAt() == null ? "" : rule.nextRunAt().toString());
        result.put("failureCount", rule.failureCount() == null ? 0 : rule.failureCount());
        result.put("maxRetryCount", rule.maxRetryCount() == null ? 3 : rule.maxRetryCount());
        result.put("scheduleInput", rule.scheduleInput() == null ? Map.of() : rule.scheduleInput());
        return result;
    }

    private Map<String, Object> toRunResponse(RuleDebugRun run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.id());
        result.put("ruleId", run.ruleId());
        result.put("versionId", run.versionId() == null ? 0L : run.versionId());
        result.put("status", run.status());
        result.put("runType", run.runType());
        result.put("triggeredByUserId", run.triggeredByUserId());
        result.put("durationMs", run.durationMs());
        result.put("errorMessage", run.errorMessage());
        result.put("matched", run.output().get("matched"));
        result.put("input", run.input());
        result.put("output", run.output());
        return result;
    }

    private Map<String, Object> topologyNode(RuleDebugRun run, String role) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.id());
        result.put("ruleId", run.ruleId());
        result.put("versionId", run.versionId() == null ? 0L : run.versionId());
        result.put("role", role);
        result.put("status", run.status());
        result.put("runType", run.runType());
        result.put("durationMs", run.durationMs());
        result.put("errorMessage", run.errorMessage());
        return result;
    }

    private Map<String, Object> toActionExecutionResponse(RuleActionExecution execution, Long sourceActionExecutionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionExecutionId", execution.id());
        result.put("sourceActionExecutionId", sourceActionExecutionId);
        result.put("ruleId", execution.ruleId());
        result.put("runId", execution.runId());
        result.put("nodeId", execution.nodeId());
        result.put("actionType", execution.actionType());
        result.put("status", execution.status());
        result.put("attempt", execution.attempt());
        result.put("maxRetryCount", execution.maxRetryCount());
        result.put("endpoint", execution.endpoint());
        result.put("idempotencyKey", execution.idempotencyKey());
        result.put("nextRetryAt", execution.nextRetryAt() == null ? "" : execution.nextRetryAt().toString());
        result.put("errorMessage", execution.errorMessage());
        result.put("metadata", execution.metadata());
        return result;
    }

    private Map<String, Object> toApprovalRecordResponse(RuleApprovalRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalRecordId", record.id());
        result.put("ruleId", record.ruleId());
        result.put("runId", record.runId());
        result.put("nodeId", record.nodeId());
        result.put("assigneeRole", record.assigneeRole());
        result.put("delegateRole", record.delegateRole());
        result.put("assigneeUsers", approvalRoleUsers(record.assigneeRole()));
        result.put("delegateUsers", approvalRoleUsers(record.delegateRole()));
        result.put("delegateActiveFrom", record.delegateActiveFrom() == null ? null : record.delegateActiveFrom().toString());
        result.put("delegateActiveTo", record.delegateActiveTo() == null ? null : record.delegateActiveTo().toString());
        result.put("handledByDelegate", isHandledByDelegate(record));
        result.put("approvalTitle", record.approvalTitle());
        result.put("status", record.status());
        result.put("createdByUserId", record.createdByUserId());
        result.put("approvedByUserId", record.approvedByUserId());
        result.put("approvalComment", record.approvalComment());
        result.put("approvedAt", record.approvedAt() == null ? null : record.approvedAt().toString());
        result.put("createdAt", record.createdAt() == null ? null : record.createdAt().toString());
        result.put("slaHours", record.slaHours());
        result.put("remindCount", record.remindCount() == null ? 0 : record.remindCount());
        result.put("lastRemindedAt", record.lastRemindedAt() == null ? null : record.lastRemindedAt().toString());
        result.put("slaDueAt", calculateSlaDueAt(record));
        result.put("isOverdue", isApprovalOverdue(record));
        ApprovalGroupSummary groupSummary = approvalGroupSummary(record);
        result.put("approvalGroupKey", groupSummary.key());
        result.put("approvalMode", groupSummary.mode());
        result.put("assigneeRoles", groupSummary.assigneeRoles());
        result.put("approvalGroupTotalCount", groupSummary.totalCount());
        result.put("approvalGroupApprovedCount", groupSummary.approvedCount());
        result.put("approvalGroupPendingCount", groupSummary.pendingCount());
        result.put("approvalGroupRejectedCount", groupSummary.rejectedCount());
        result.put("approvalGroupClosedCount", groupSummary.closedCount());
        return result;
    }

    private Map<String, Object> toApprovalDelegateRuleResponse(RuleApprovalDelegateRule delegateRule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("delegateRuleId", delegateRule.id());
        result.put("assigneeRole", delegateRule.assigneeRole());
        result.put("delegateRole", delegateRule.delegateRole());
        result.put("activeFrom", delegateRule.activeFrom() == null ? null : delegateRule.activeFrom().toString());
        result.put("activeTo", delegateRule.activeTo() == null ? null : delegateRule.activeTo().toString());
        result.put("activeWeekdays", delegateRule.activeWeekdays() == null ? List.of() : delegateRule.activeWeekdays());
        result.put("activeDates", delegateRule.activeDates() == null ? List.of() : delegateRule.activeDates());
        result.put("status", delegateRule.status());
        result.put("reason", delegateRule.reason());
        result.put("createdByUserId", delegateRule.createdByUserId());
        result.put("createdAt", delegateRule.createdAt() == null ? null : delegateRule.createdAt().toString());
        return result;
    }

    private Map<String, Object> toApprovalTemplateResponse(RuleApprovalTemplate template) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalTemplateId", template.id());
        result.put("name", template.name());
        result.put("description", template.description() == null ? "" : template.description());
        result.put("status", template.status());
        result.put("version", template.version() == null ? 1 : template.version());
        result.put("steps", template.steps() == null ? List.of() : template.steps());
        Map<String, Object> usageImpact = approvalTemplateUsageImpact(template.id());
        result.put("usageCount", usageImpact.get("usageCount"));
        result.put("usageRules", usageImpact.get("usageRules"));
        result.put("createdByUserId", template.createdByUserId());
        result.put("createdAt", template.createdAt() == null ? null : template.createdAt().toString());
        result.put("updatedAt", template.updatedAt() == null ? null : template.updatedAt().toString());
        return result;
    }

    private Map<String, Object> approvalTemplateUsageImpact(Long templateId) {
        List<Map<String, Object>> usageRules = ruleRepository.findRulesUsingApprovalTemplate(templateId, 1, 20).stream()
                .map(this::toApprovalTemplateUsageRuleResponse)
                .toList();
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("usageCount", Math.toIntExact(ruleRepository.countRulesUsingApprovalTemplate(templateId)));
        impact.put("usageRules", usageRules);
        return impact;
    }

    private Map<String, Map<String, Object>> approvalTemplateStepsById(List<Map<String, Object>> steps) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        int index = 1;
        for (Map<String, Object> step : steps == null ? List.<Map<String, Object>>of() : steps) {
            String stepId = blankToNull(string(step.get("stepId")));
            result.put(stepId == null ? "step" + index : stepId, step);
            index++;
        }
        return result;
    }

    private Map<String, Object> approvalTemplateVersionChange(String changeType,
                                                              String stepId,
                                                              Map<String, Object> baseStep,
                                                              Map<String, Object> targetStep) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("changeType", changeType);
        change.put("stepId", stepId);
        change.put("baseStep", baseStep);
        change.put("targetStep", targetStep);
        return change;
    }

    private Map<String, Object> toApprovalTemplateUsageRuleResponse(Rule rule) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("ruleId", rule.id());
        usage.put("name", rule.name());
        usage.put("status", rule.status());
        return usage;
    }

    private List<Map<String, Object>> approvalRoleUsers(String role) {
        String normalizedRole = role == null ? "" : role.trim();
        if (normalizedRole.isBlank() || userRepository.isEmpty()) {
            return List.of();
        }
        return userRepository.get().findEnabledByRole(normalizedRole).stream()
                .map(user -> approvalRoleUserResponse(user, normalizedRole))
                .toList();
    }

    private Map<String, Object> approvalRoleUserResponse(UserAccount user, String role) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.id());
        result.put("username", user.username());
        result.put("displayName", user.displayName());
        result.put("role", role);
        return result;
    }

    private record ApprovalGroupSummary(
            String key,
            String mode,
            List<String> assigneeRoles,
            int totalCount,
            int approvedCount,
            int pendingCount,
            int rejectedCount,
            int closedCount
    ) {
    }

    private ApprovalGroupSummary approvalGroupSummary(RuleApprovalRecord record) {
        Rule rule = ruleRepository.findById(record.ruleId()).orElse(null);
        Map<String, Object> approvalNode = rule == null ? Map.of() : approvalNode(rule, record.nodeId());
        List<RuleApprovalRecord> siblings = approvalSiblingRecords(record);
        List<String> roles = siblings.stream()
                .map(RuleApprovalRecord::assigneeRole)
                .filter(role -> role != null && !role.isBlank())
                .distinct()
                .toList();
        List<String> configuredRoles = approvalAssigneeRoles(approvalNode);
        if (!configuredRoles.isEmpty()) {
            roles = configuredRoles;
        }
        int approvedCount = (int) siblings.stream().filter(item -> "approved".equals(item.status())).count();
        int pendingCount = (int) siblings.stream().filter(item -> "pending".equals(item.status())).count();
        int rejectedCount = (int) siblings.stream().filter(item -> "rejected".equals(item.status())).count();
        int closedCount = (int) siblings.stream().filter(item -> "closed".equals(item.status())).count();
        return new ApprovalGroupSummary(
                "rule:" + record.ruleId() + ":run:" + record.runId() + ":node:" + record.nodeId(),
                approvalMode(approvalNode),
                roles,
                siblings.size(),
                approvedCount,
                pendingCount,
                rejectedCount,
                closedCount
        );
    }

    private String calculateSlaDueAt(RuleApprovalRecord record) {
        if (record.createdAt() == null || record.slaHours() == null || record.slaHours() <= 0) {
            return null;
        }
        return record.createdAt().plusHours(record.slaHours()).toString();
    }

    private boolean isApprovalOverdue(RuleApprovalRecord record) {
        if (!"pending".equals(record.status()) || record.createdAt() == null || record.slaHours() == null || record.slaHours() <= 0) {
            return false;
        }
        return !record.createdAt().plusHours(record.slaHours()).isAfter(OffsetDateTime.now());
    }

    private String approvalSlaOverdueDedupeKey(Long ruleId, Long approvalRecordId) {
        return "rule:" + ruleId + ":approval:" + approvalRecordId + ":sla-overdue";
    }

    private ApprovalRecordFilter toApprovalRecordFilter(Map<String, Object> filters) {
        Map<String, Object> safeFilters = filters == null ? Map.of() : filters;
        return new ApprovalRecordFilter(
                nullableLong(safeFilters.get("ruleId")),
                blankToNull(string(safeFilters.get("assigneeRole"))),
                blankToNull(string(safeFilters.get("approvalTitle"))),
                nullableLong(safeFilters.get("createdByUserId")),
                nullableLong(safeFilters.get("approvedByUserId")),
                nullableOffsetDateTime(safeFilters.get("createdAtFrom")),
                nullableOffsetDateTime(safeFilters.get("createdAtTo")),
                List.of(),
                null
        );
    }

    private ApprovalRecordFilter applyApprovalRecordVisibility(ApprovalRecordFilter filter) {
        var currentUser = CurrentUserHolder.get();
        if (currentUser == null || currentUser.hasPermission("rule:manage") || filter.assigneeRole() != null) {
            return filter;
        }
        List<String> visibleRoles = currentUser.roles().stream()
                .filter(role -> role != null && !role.isBlank())
                .sorted()
                .toList();
        if (visibleRoles.isEmpty()) {
            return filter;
        }
        return new ApprovalRecordFilter(
                filter.ruleId(),
                filter.assigneeRole(),
                filter.approvalTitle(),
                filter.createdByUserId(),
                filter.approvedByUserId(),
                filter.createdAtFrom(),
                filter.createdAtTo(),
                visibleRoles,
                OffsetDateTime.now()
        );
    }

    private void ensureApprovalRecordAccess(RuleApprovalRecord record) {
        var currentUser = CurrentUserHolder.get();
        if (currentUser == null || currentUser.hasPermission("rule:manage")) {
            return;
        }
        if (currentUser.roles().contains(record.assigneeRole())) {
            return;
        }
        if (record.delegateRole() != null && currentUser.roles().contains(record.delegateRole()) && isDelegateActive(record, OffsetDateTime.now())) {
            return;
        }
        throw new SecurityException("approval record access denied: " + record.id());
    }

    private boolean isDelegateActive(RuleApprovalRecord record, OffsetDateTime now) {
        if (record.delegateActiveFrom() != null && now.isBefore(record.delegateActiveFrom())) {
            return false;
        }
        if (record.delegateActiveTo() != null && now.isAfter(record.delegateActiveTo())) {
            return false;
        }
        return true;
    }

    private boolean isHandledByDelegate(RuleApprovalRecord record) {
        if (record.delegateRole() == null || record.approvedByUserId() == null) {
            return false;
        }
        var currentUser = CurrentUserHolder.get();
        return currentUser != null
                && record.approvedByUserId().equals(currentUser.userId())
                && currentUser.roles().contains(record.delegateRole())
                && !currentUser.roles().contains(record.assigneeRole());
    }

    private long nanoTime() {
        return nanoTimeSource.getAsLong();
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (nanoTime() - startedAt) / 1_000_000L);
    }

    private static String errorMessage(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String safeApprovalSupplementFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unknown";
        }
        String normalized = originalFilename.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }
        normalized = normalized.trim().replaceAll("\\s+", "-").replaceAll("[^A-Za-z0-9._-]", "-");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static String approvalSupplementContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int parameterIndex = contentType.indexOf(';');
        if (parameterIndex >= 0) {
            contentType = contentType.substring(0, parameterIndex);
        }
        return contentType.trim().toLowerCase();
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException error) {
            throw new IllegalStateException("webhook signature calculation failed", error);
        }
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> input) {
            Map<String, Object> result = new LinkedHashMap<>();
            input.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        for (Object item : list(value)) {
            String text = string(item);
            if (text == null || text.isBlank()) {
                continue;
            }
            result.add(text.trim());
        }
        return List.copyOf(result);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer nullableInteger(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private static OffsetDateTime nullableOffsetDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private static List<String> activeWeekdays(Object value) {
        List<String> weekdays = new ArrayList<>();
        for (Object item : list(value)) {
            String weekday = string(item);
            if (weekday == null || weekday.isBlank()) {
                continue;
            }
            String normalized = weekday.trim().toUpperCase();
            try {
                DayOfWeek.valueOf(normalized);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("activeWeekdays must contain valid weekdays");
            }
            if (!weekdays.contains(normalized)) {
                weekdays.add(normalized);
            }
        }
        return List.copyOf(weekdays);
    }

    private static List<String> activeDates(Object value) {
        List<String> dates = new ArrayList<>();
        for (Object item : list(value)) {
            String date = string(item);
            if (date == null || date.isBlank()) {
                continue;
            }
            String normalized = LocalDate.parse(date.trim()).toString();
            if (!dates.contains(normalized)) {
                dates.add(normalized);
            }
        }
        return List.copyOf(dates);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, Object> auditDetail(Object... pairs) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            detail.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return detail;
    }

    private static AuditRepository noopAuditRepository() {
        return new AuditRepository() {
            @Override
            public OperationLog save(OperationLog log) {
                return log.withId(0L);
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
                return 0;
            }
        };
    }

    private static SystemAlertRepository noopSystemAlertRepository() {
        return new SystemAlertRepository() {
            @Override
            public SystemAlert save(SystemAlert alert) {
                return alert.withId(0L);
            }

            @Override
            public List<SystemAlert> findByRecipient(Long recipientUserId, String status, int page, int pageSize) {
                return List.of();
            }

            @Override
            public long countByRecipient(Long recipientUserId, String status) {
                return 0;
            }
        };
    }
}
