package com.company.report.rule.infrastructure.persistence;

import com.company.report.rule.domain.model.Rule;
import com.company.report.rule.domain.model.RuleActionExecution;
import com.company.report.rule.domain.model.RuleApprovalDelegateRule;
import com.company.report.rule.domain.model.RuleApprovalRecord;
import com.company.report.rule.domain.model.RuleApprovalTemplate;
import com.company.report.rule.domain.model.RuleDebugRun;
import com.company.report.rule.domain.model.RuleRunMetrics;
import com.company.report.rule.domain.repository.RuleRepository;
import com.company.report.rule.domain.repository.RuleRepository.ApprovalRecordFilter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRuleRepository implements RuleRepository {
    private final AtomicLong ruleSequence = new AtomicLong(0);
    private final AtomicLong versionSequence = new AtomicLong(0);
    private final AtomicLong debugRunSequence = new AtomicLong(0);
    private final AtomicLong actionExecutionSequence = new AtomicLong(0);
    private final AtomicLong approvalRecordSequence = new AtomicLong(0);
    private final AtomicLong approvalDelegateRuleSequence = new AtomicLong(0);
    private final AtomicLong approvalTemplateSequence = new AtomicLong(0);
    private final Map<Long, Rule> rules = new LinkedHashMap<>();
    private final Map<Long, RuleDebugRun> debugRuns = new LinkedHashMap<>();
    private final Map<Long, RuleActionExecution> actionExecutions = new LinkedHashMap<>();
    private final Map<Long, RuleApprovalRecord> approvalRecords = new LinkedHashMap<>();
    private final Map<Long, RuleApprovalDelegateRule> approvalDelegateRules = new LinkedHashMap<>();
    private final Map<Long, RuleApprovalTemplate> approvalTemplates = new LinkedHashMap<>();
    private final Map<String, RuleApprovalTemplate> approvalTemplateVersions = new LinkedHashMap<>();
    private final Map<Long, OffsetDateTime> scheduleLocks = new LinkedHashMap<>();
    private final Map<Long, OffsetDateTime> actionExecutionLocks = new LinkedHashMap<>();

    @Override
    public synchronized Rule save(Rule rule) {
        Long id = rule.id();
        Rule saved = id == null || id <= 0 ? rule.withId(ruleSequence.incrementAndGet()) : rule;
        rules.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized Optional<Rule> findById(Long id) {
        return Optional.ofNullable(rules.get(id));
    }

    @Override
    public synchronized List<Rule> findPage(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return rules.values().stream()
                .sorted(Comparator.comparing(Rule::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long count() {
        return rules.size();
    }

    @Override
    public synchronized List<Rule> findRulesUsingApprovalTemplate(Long templateId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return rules.values().stream()
                .filter(rule -> usesApprovalTemplate(rule, templateId))
                .sorted(Comparator.comparing(Rule::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long countRulesUsingApprovalTemplate(Long templateId) {
        return rules.values().stream()
                .filter(rule -> usesApprovalTemplate(rule, templateId))
                .count();
    }

    @Override
    public synchronized Long saveVersion(Long ruleId, Map<String, Object> definition) {
        if (!rules.containsKey(ruleId)) {
            throw new IllegalArgumentException("rule not found: " + ruleId);
        }
        return versionSequence.incrementAndGet();
    }

    @Override
    public synchronized RuleDebugRun saveDebugRun(RuleDebugRun debugRun) {
        RuleDebugRun saved = debugRun.withId(debugRunSequence.incrementAndGet());
        debugRuns.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized RuleDebugRun updateDebugRun(RuleDebugRun debugRun) {
        if (debugRun == null || debugRun.id() == null || !debugRuns.containsKey(debugRun.id())) {
            throw new IllegalArgumentException("rule run not found: " + (debugRun == null ? null : debugRun.id()));
        }
        debugRuns.put(debugRun.id(), debugRun);
        return debugRun;
    }

    @Override
    public synchronized Optional<RuleDebugRun> findRunById(Long runId) {
        return Optional.ofNullable(debugRuns.get(runId));
    }

    @Override
    public synchronized List<RuleDebugRun> findRuns(Long ruleId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return debugRuns.values().stream()
                .filter(run -> ruleId.equals(run.ruleId()))
                .filter(run -> "production".equals(run.runType()))
                .sorted(Comparator.comparing(RuleDebugRun::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long countRuns(Long ruleId) {
        return debugRuns.values().stream()
                .filter(run -> ruleId.equals(run.ruleId()))
                .filter(run -> "production".equals(run.runType()))
                .count();
    }

    @Override
    public synchronized RuleRunMetrics metrics(Long ruleId) {
        List<RuleDebugRun> runs = debugRuns.values().stream()
                .filter(run -> ruleId.equals(run.ruleId()))
                .filter(run -> "production".equals(run.runType()))
                .sorted(Comparator.comparing(RuleDebugRun::id).reversed())
                .toList();
        if (runs.isEmpty()) {
            return RuleRunMetrics.empty();
        }
        long succeededRuns = runs.stream().filter(run -> "succeeded".equals(run.status())).count();
        long failedRuns = runs.stream().filter(run -> "failed".equals(run.status())).count();
        double averageDurationMs = runs.stream()
                .filter(run -> run.durationMs() != null)
                .mapToLong(RuleDebugRun::durationMs)
                .average()
                .orElse(0d);
        String lastErrorMessage = runs.stream()
                .filter(run -> run.errorMessage() != null && !run.errorMessage().isBlank())
                .findFirst()
                .map(RuleDebugRun::errorMessage)
                .orElse(null);
        return new RuleRunMetrics(
                runs.size(),
                succeededRuns,
                failedRuns,
                averageDurationMs,
                runs.get(0).status(),
                lastErrorMessage
        );
    }

    @Override
    public synchronized RuleActionExecution saveActionExecution(RuleActionExecution execution) {
        RuleActionExecution saved = execution.withId(actionExecutionSequence.incrementAndGet());
        actionExecutions.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized RuleApprovalRecord saveApprovalRecord(RuleApprovalRecord approvalRecord) {
        RuleApprovalRecord saved = approvalRecord.withId(approvalRecordSequence.incrementAndGet());
        approvalRecords.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized RuleApprovalDelegateRule saveApprovalDelegateRule(RuleApprovalDelegateRule delegateRule) {
        RuleApprovalDelegateRule saved = delegateRule.id() == null || delegateRule.id() <= 0
                ? delegateRule.withId(approvalDelegateRuleSequence.incrementAndGet())
                : delegateRule;
        approvalDelegateRules.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized Optional<RuleApprovalDelegateRule> findActiveApprovalDelegateRule(String assigneeRole, OffsetDateTime now) {
        String requestedRole = assigneeRole == null ? "" : assigneeRole.trim();
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now() : now;
        if (requestedRole.isBlank()) {
            return Optional.empty();
        }
        return approvalDelegateRules.values().stream()
                .filter(rule -> requestedRole.equals(rule.assigneeRole()))
                .filter(rule -> "enabled".equals(rule.status()))
                .filter(rule -> rule.activeFrom() == null || !effectiveNow.isBefore(rule.activeFrom()))
                .filter(rule -> rule.activeTo() == null || !effectiveNow.isAfter(rule.activeTo()))
                .filter(rule -> rule.activeWeekdays() == null
                        || rule.activeWeekdays().isEmpty()
                        || rule.activeWeekdays().contains(effectiveNow.getDayOfWeek().name()))
                .filter(rule -> rule.activeDates() == null
                        || rule.activeDates().isEmpty()
                        || rule.activeDates().contains(effectiveNow.toLocalDate().toString()))
                .sorted(Comparator.comparing(RuleApprovalDelegateRule::id).reversed())
                .findFirst();
    }

    @Override
    public synchronized Optional<RuleApprovalDelegateRule> findApprovalDelegateRuleById(Long delegateRuleId) {
        return Optional.ofNullable(approvalDelegateRules.get(delegateRuleId));
    }

    @Override
    public synchronized List<RuleApprovalDelegateRule> findApprovalDelegateRules(String status, String assigneeRole, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        String requestedStatus = status == null ? "" : status.trim();
        String requestedRole = assigneeRole == null ? "" : assigneeRole.trim();
        return approvalDelegateRules.values().stream()
                .filter(rule -> requestedStatus.isBlank() || requestedStatus.equals(rule.status()))
                .filter(rule -> requestedRole.isBlank() || requestedRole.equals(rule.assigneeRole()))
                .sorted(Comparator.comparing(RuleApprovalDelegateRule::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized List<RuleApprovalDelegateRule> findEnabledApprovalDelegateRulesByAssigneeRole(String assigneeRole) {
        String requestedRole = assigneeRole == null ? "" : assigneeRole.trim();
        if (requestedRole.isBlank()) {
            return List.of();
        }
        return approvalDelegateRules.values().stream()
                .filter(rule -> "enabled".equals(rule.status()))
                .filter(rule -> requestedRole.equals(rule.assigneeRole()))
                .sorted(Comparator.comparing(RuleApprovalDelegateRule::id).reversed())
                .toList();
    }

    @Override
    public synchronized long countApprovalDelegateRules(String status, String assigneeRole) {
        String requestedStatus = status == null ? "" : status.trim();
        String requestedRole = assigneeRole == null ? "" : assigneeRole.trim();
        return approvalDelegateRules.values().stream()
                .filter(rule -> requestedStatus.isBlank() || requestedStatus.equals(rule.status()))
                .filter(rule -> requestedRole.isBlank() || requestedRole.equals(rule.assigneeRole()))
                .count();
    }

    @Override
    public synchronized RuleApprovalDelegateRule updateApprovalDelegateRule(RuleApprovalDelegateRule delegateRule) {
        if (delegateRule.id() == null || !approvalDelegateRules.containsKey(delegateRule.id())) {
            throw new IllegalArgumentException("approval delegate rule not found: " + delegateRule.id());
        }
        approvalDelegateRules.put(delegateRule.id(), delegateRule);
        return delegateRule;
    }

    @Override
    public synchronized RuleApprovalTemplate saveApprovalTemplate(RuleApprovalTemplate template) {
        RuleApprovalTemplate saved = template.id() == null || template.id() <= 0
                ? template.withId(approvalTemplateSequence.incrementAndGet())
                : template;
        approvalTemplates.put(saved.id(), saved);
        approvalTemplateVersions.put(approvalTemplateVersionKey(saved.id(), saved.version()), saved);
        return saved;
    }

    @Override
    public synchronized Optional<RuleApprovalTemplate> findApprovalTemplateById(Long templateId) {
        return Optional.ofNullable(approvalTemplates.get(templateId));
    }

    @Override
    public synchronized Optional<RuleApprovalTemplate> findApprovalTemplateVersion(Long templateId, int version) {
        return Optional.ofNullable(approvalTemplateVersions.get(approvalTemplateVersionKey(templateId, version)));
    }

    @Override
    public synchronized List<RuleApprovalTemplate> findApprovalTemplateVersions(Long templateId) {
        return approvalTemplateVersions.values().stream()
                .filter(template -> templateId.equals(template.id()))
                .sorted(Comparator.comparing((RuleApprovalTemplate template) -> template.version() == null ? 1 : template.version()).reversed())
                .toList();
    }

    @Override
    public synchronized RuleApprovalTemplate updateApprovalTemplate(RuleApprovalTemplate template) {
        if (template.id() == null || !approvalTemplates.containsKey(template.id())) {
            throw new IllegalArgumentException("approval template not found: " + template.id());
        }
        approvalTemplates.put(template.id(), template);
        approvalTemplateVersions.put(approvalTemplateVersionKey(template.id(), template.version()), template);
        return template;
    }

    private String approvalTemplateVersionKey(Long templateId, Integer version) {
        return templateId + ":" + (version == null ? 1 : version);
    }

    @Override
    public synchronized List<RuleApprovalTemplate> findApprovalTemplates(String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        String requestedStatus = status == null ? "" : status.trim();
        return approvalTemplates.values().stream()
                .filter(template -> requestedStatus.isBlank() || requestedStatus.equals(template.status()))
                .sorted(Comparator.comparing(RuleApprovalTemplate::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long countApprovalTemplates(String status) {
        String requestedStatus = status == null ? "" : status.trim();
        return approvalTemplates.values().stream()
                .filter(template -> requestedStatus.isBlank() || requestedStatus.equals(template.status()))
                .count();
    }

    @Override
    public synchronized Optional<RuleApprovalRecord> findApprovalRecordById(Long approvalRecordId) {
        return Optional.ofNullable(approvalRecords.get(approvalRecordId));
    }

    @Override
    public synchronized RuleApprovalRecord updateApprovalRecord(RuleApprovalRecord approvalRecord) {
        if (approvalRecord.id() == null || !approvalRecords.containsKey(approvalRecord.id())) {
            throw new IllegalArgumentException("approval record not found: " + approvalRecord.id());
        }
        approvalRecords.put(approvalRecord.id(), approvalRecord);
        return approvalRecord;
    }

    @Override
    public synchronized List<RuleApprovalRecord> findApprovalRecords(Long ruleId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return approvalRecords.values().stream()
                .filter(record -> ruleId.equals(record.ruleId()))
                .sorted(Comparator.comparing(RuleApprovalRecord::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized List<RuleApprovalRecord> findApprovalRecordsByStatus(String status, ApprovalRecordFilter filter, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return approvalRecords.values().stream()
                .filter(record -> status.equals(record.status()))
                .filter(record -> matchesApprovalRecordFilter(record, filter))
                .sorted(Comparator.comparing(RuleApprovalRecord::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long countApprovalRecords(Long ruleId) {
        return approvalRecords.values().stream()
                .filter(record -> ruleId.equals(record.ruleId()))
                .count();
    }

    @Override
    public synchronized long countApprovalRecordsByStatus(String status, ApprovalRecordFilter filter) {
        return approvalRecords.values().stream()
                .filter(record -> status.equals(record.status()))
                .filter(record -> matchesApprovalRecordFilter(record, filter))
                .count();
    }

    private boolean matchesApprovalRecordFilter(RuleApprovalRecord record, ApprovalRecordFilter filter) {
        ApprovalRecordFilter safeFilter = filter == null ? ApprovalRecordFilter.empty() : filter;
        if (safeFilter.ruleId() != null && !safeFilter.ruleId().equals(record.ruleId())) {
            return false;
        }
        if (safeFilter.assigneeRole() != null && !safeFilter.assigneeRole().equals(record.assigneeRole())) {
            return false;
        }
        if (safeFilter.visibleRoles() != null && !safeFilter.visibleRoles().isEmpty()
                && !isVisibleToRole(record, safeFilter.visibleRoles(), safeFilter.visibilityAt())) {
            return false;
        }
        if (safeFilter.approvalTitle() != null
                && (record.approvalTitle() == null || !record.approvalTitle().toLowerCase().contains(safeFilter.approvalTitle().toLowerCase()))) {
            return false;
        }
        if (safeFilter.createdByUserId() != null && !safeFilter.createdByUserId().equals(record.createdByUserId())) {
            return false;
        }
        if (safeFilter.approvedByUserId() != null && !safeFilter.approvedByUserId().equals(record.approvedByUserId())) {
            return false;
        }
        if (safeFilter.createdAtFrom() != null
                && (record.createdAt() == null || record.createdAt().isBefore(safeFilter.createdAtFrom()))) {
            return false;
        }
        if (safeFilter.createdAtTo() != null
                && (record.createdAt() == null || record.createdAt().isAfter(safeFilter.createdAtTo()))) {
            return false;
        }
        return true;
    }

    private boolean isVisibleToRole(RuleApprovalRecord record, List<String> visibleRoles, OffsetDateTime visibilityAt) {
        if (visibleRoles.contains(record.assigneeRole())) {
            return true;
        }
        if (record.delegateRole() == null || !visibleRoles.contains(record.delegateRole())) {
            return false;
        }
        OffsetDateTime now = visibilityAt == null ? OffsetDateTime.now() : visibilityAt;
        if (record.delegateActiveFrom() != null && now.isBefore(record.delegateActiveFrom())) {
            return false;
        }
        if (record.delegateActiveTo() != null && now.isAfter(record.delegateActiveTo())) {
            return false;
        }
        return true;
    }

    private boolean usesApprovalTemplate(Rule rule, Long templateId) {
        if (rule == null || rule.definition() == null || templateId == null) {
            return false;
        }
        String nodePrefix = "tpl" + templateId + "_";
        Object nodesValue = rule.definition().get("nodes");
        if (!(nodesValue instanceof List<?> nodes)) {
            return false;
        }
        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> node)) {
                continue;
            }
            if (templateId.equals(nullableLong(node.get("approvalTemplateId")))) {
                return true;
            }
            Object nodeId = node.get("id");
            if (nodeId != null && String.valueOf(nodeId).startsWith(nodePrefix)) {
                return true;
            }
        }
        return false;
    }

    private Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    @Override
    public synchronized Optional<RuleActionExecution> findActionExecutionById(Long actionExecutionId) {
        return Optional.ofNullable(actionExecutions.get(actionExecutionId));
    }

    @Override
    public synchronized List<RuleActionExecution> findActionExecutions(Long ruleId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return actionExecutions.values().stream()
                .filter(execution -> ruleId.equals(execution.ruleId()))
                .sorted(Comparator.comparing(RuleActionExecution::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long countActionExecutions(Long ruleId) {
        return actionExecutions.values().stream()
                .filter(execution -> ruleId.equals(execution.ruleId()))
                .count();
    }

    @Override
    public synchronized List<RuleActionExecution> findDueWebhookActionExecutions(OffsetDateTime now, int limit) {
        return actionExecutions.values().stream()
                .filter(execution -> "webhook".equals(execution.actionType()))
                .filter(execution -> "pending_retry".equals(execution.status()) || "failed".equals(execution.status()))
                .filter(execution -> isLatestOpenActionExecution(execution))
                .filter(execution -> execution.nextRetryAt() == null || !execution.nextRetryAt().isAfter(now))
                .filter(execution -> {
                    OffsetDateTime lockedUntil = actionExecutionLocks.get(execution.id());
                    return lockedUntil == null || !lockedUntil.isAfter(now);
                })
                .sorted(Comparator.comparing((RuleActionExecution execution) ->
                        execution.nextRetryAt() == null ? OffsetDateTime.MIN : execution.nextRetryAt()).thenComparing(RuleActionExecution::id))
                .limit(Math.max(limit, 1))
                .toList();
    }

    private boolean isLatestOpenActionExecution(RuleActionExecution execution) {
        return actionExecutions.values().stream()
                .filter(item -> execution.idempotencyKey().equals(item.idempotencyKey()))
                .map(RuleActionExecution::id)
                .max(Long::compareTo)
                .filter(execution.id()::equals)
                .isPresent();
    }

    @Override
    public synchronized boolean tryAcquireActionExecutionLease(Long actionExecutionId, OffsetDateTime lockedUntil) {
        OffsetDateTime currentLock = actionExecutionLocks.get(actionExecutionId);
        if (currentLock != null && currentLock.isAfter(OffsetDateTime.now())) {
            return false;
        }
        actionExecutionLocks.put(actionExecutionId, lockedUntil);
        return true;
    }

    @Override
    public synchronized void releaseActionExecutionLease(Long actionExecutionId) {
        actionExecutionLocks.remove(actionExecutionId);
    }

    @Override
    public synchronized List<Rule> findDueScheduledRules(OffsetDateTime now, int limit) {
        return rules.values().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.scheduleEnabled()))
                .filter(rule -> "published".equals(rule.status()))
                .filter(rule -> rule.nextRunAt() != null && !rule.nextRunAt().isAfter(now))
                .filter(rule -> {
                    OffsetDateTime lockedUntil = scheduleLocks.get(rule.id());
                    return lockedUntil == null || !lockedUntil.isAfter(now);
                })
                .sorted(Comparator.comparing(Rule::nextRunAt).thenComparing(Rule::id))
                .limit(Math.max(limit, 1))
                .toList();
    }

    @Override
    public synchronized boolean tryAcquireRuleScheduleLease(Long ruleId, OffsetDateTime lockedUntil) {
        OffsetDateTime currentLock = scheduleLocks.get(ruleId);
        if (currentLock != null && currentLock.isAfter(OffsetDateTime.now())) {
            return false;
        }
        scheduleLocks.put(ruleId, lockedUntil);
        return true;
    }

    @Override
    public synchronized void releaseRuleScheduleLease(Long ruleId) {
        scheduleLocks.remove(ruleId);
    }

    @Override
    public synchronized Rule updateScheduleState(Long ruleId, OffsetDateTime nextRunAt, int failureCount) {
        Rule rule = rules.get(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("rule not found: " + ruleId);
        }
        Rule updated = rule.withScheduleState(nextRunAt, failureCount);
        rules.put(ruleId, updated);
        return updated;
    }
}
