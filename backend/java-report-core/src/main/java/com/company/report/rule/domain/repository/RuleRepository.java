package com.company.report.rule.domain.repository;

import com.company.report.rule.domain.model.Rule;
import com.company.report.rule.domain.model.RuleActionExecution;
import com.company.report.rule.domain.model.RuleApprovalDelegateRule;
import com.company.report.rule.domain.model.RuleApprovalRecord;
import com.company.report.rule.domain.model.RuleApprovalTemplate;
import com.company.report.rule.domain.model.RuleDebugRun;
import com.company.report.rule.domain.model.RuleRunMetrics;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RuleRepository {
    record ApprovalRecordFilter(
            Long ruleId,
            String assigneeRole,
            String approvalTitle,
            Long createdByUserId,
            Long approvedByUserId,
            OffsetDateTime createdAtFrom,
            OffsetDateTime createdAtTo,
            List<String> visibleRoles,
            OffsetDateTime visibilityAt
    ) {
        public static ApprovalRecordFilter empty() {
            return new ApprovalRecordFilter(null, null, null, null, null, null, null, List.of(), null);
        }
    }

    Rule save(Rule rule);

    Optional<Rule> findById(Long id);

    List<Rule> findPage(int page, int pageSize);

    long count();

    default List<Rule> findRulesUsingApprovalTemplate(Long templateId, int page, int pageSize) {
        return List.of();
    }

    default long countRulesUsingApprovalTemplate(Long templateId) {
        return 0L;
    }

    Long saveVersion(Long ruleId, Map<String, Object> definition);

    RuleDebugRun saveDebugRun(RuleDebugRun debugRun);

    RuleDebugRun updateDebugRun(RuleDebugRun debugRun);

    Optional<RuleDebugRun> findRunById(Long runId);

    List<RuleDebugRun> findRuns(Long ruleId, int page, int pageSize);

    long countRuns(Long ruleId);

    RuleRunMetrics metrics(Long ruleId);

    RuleActionExecution saveActionExecution(RuleActionExecution execution);

    RuleApprovalRecord saveApprovalRecord(RuleApprovalRecord approvalRecord);

    default RuleApprovalDelegateRule saveApprovalDelegateRule(RuleApprovalDelegateRule delegateRule) {
        throw new UnsupportedOperationException("approval delegate rule persistence is not supported");
    }

    default Optional<RuleApprovalDelegateRule> findActiveApprovalDelegateRule(String assigneeRole, OffsetDateTime now) {
        return Optional.empty();
    }

    default Optional<RuleApprovalDelegateRule> findApprovalDelegateRuleById(Long delegateRuleId) {
        return Optional.empty();
    }

    default List<RuleApprovalDelegateRule> findApprovalDelegateRules(String status, String assigneeRole, int page, int pageSize) {
        return List.of();
    }

    default List<RuleApprovalDelegateRule> findEnabledApprovalDelegateRulesByAssigneeRole(String assigneeRole) {
        return List.of();
    }

    default long countApprovalDelegateRules(String status, String assigneeRole) {
        return 0L;
    }

    default RuleApprovalDelegateRule updateApprovalDelegateRule(RuleApprovalDelegateRule delegateRule) {
        throw new UnsupportedOperationException("approval delegate rule update is not supported");
    }

    default RuleApprovalTemplate saveApprovalTemplate(RuleApprovalTemplate template) {
        throw new UnsupportedOperationException("approval template persistence is not supported");
    }

    default Optional<RuleApprovalTemplate> findApprovalTemplateById(Long templateId) {
        return Optional.empty();
    }

    default Optional<RuleApprovalTemplate> findApprovalTemplateVersion(Long templateId, int version) {
        return Optional.empty();
    }

    default List<RuleApprovalTemplate> findApprovalTemplateVersions(Long templateId) {
        return List.of();
    }

    default RuleApprovalTemplate updateApprovalTemplate(RuleApprovalTemplate template) {
        throw new UnsupportedOperationException("approval template update is not supported");
    }

    default List<RuleApprovalTemplate> findApprovalTemplates(String status, int page, int pageSize) {
        return List.of();
    }

    default long countApprovalTemplates(String status) {
        return 0L;
    }

    Optional<RuleApprovalRecord> findApprovalRecordById(Long approvalRecordId);

    RuleApprovalRecord updateApprovalRecord(RuleApprovalRecord approvalRecord);

    List<RuleApprovalRecord> findApprovalRecords(Long ruleId, int page, int pageSize);

    List<RuleApprovalRecord> findApprovalRecordsByStatus(String status, ApprovalRecordFilter filter, int page, int pageSize);

    long countApprovalRecords(Long ruleId);

    long countApprovalRecordsByStatus(String status, ApprovalRecordFilter filter);

    Optional<RuleActionExecution> findActionExecutionById(Long actionExecutionId);

    List<RuleActionExecution> findActionExecutions(Long ruleId, int page, int pageSize);

    long countActionExecutions(Long ruleId);

    List<RuleActionExecution> findDueWebhookActionExecutions(OffsetDateTime now, int limit);

    boolean tryAcquireActionExecutionLease(Long actionExecutionId, OffsetDateTime lockedUntil);

    void releaseActionExecutionLease(Long actionExecutionId);

    List<Rule> findDueScheduledRules(OffsetDateTime now, int limit);

    boolean tryAcquireRuleScheduleLease(Long ruleId, OffsetDateTime lockedUntil);

    void releaseRuleScheduleLease(Long ruleId);

    Rule updateScheduleState(Long ruleId, OffsetDateTime nextRunAt, int failureCount);
}
