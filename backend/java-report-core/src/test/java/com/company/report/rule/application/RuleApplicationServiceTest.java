package com.company.report.rule.application;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.citation.application.CollaborationApplicationService;
import com.company.report.citation.infrastructure.persistence.InMemoryCollaborationRepository;
import com.company.report.knowledge.infrastructure.storage.DocumentStorage;
import com.company.report.notification.domain.model.SystemAlert;
import com.company.report.notification.domain.repository.SystemAlertRepository;
import com.company.report.permission.domain.model.UserAccount;
import com.company.report.permission.infrastructure.persistence.InMemoryUserRepository;
import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.model.ReportStatus;
import com.company.report.report.domain.repository.ReportRepository;
import com.company.report.rule.domain.model.RuleActionExecution;
import com.company.report.rule.domain.model.RuleApprovalRecord;
import com.company.report.rule.domain.model.RuleDebugRun;
import com.company.report.rule.infrastructure.persistence.InMemoryRuleRepository;
import com.company.report.rule.domain.service.RuleDomainService;
import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleApplicationServiceTest {
    private final RuleApplicationService service = new RuleApplicationService(new RuleDomainService());

    @AfterEach
    void clearCurrentUser() {
        CurrentUserHolder.clear();
    }

    @Test
    void createsListsSavesAndDebugsRulesFromRepositoryState() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "score", "type", "condition", "field", "risk", "operator", ">=", "value", 90),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "score"),
                        Map.of("source", "score", "target", "end")
                )
        );
        Map<String, Object> created = service.create(Map.of(
                "name", "Risk scoring rule",
                "description", "Score generated report risk",
                "definition", definition
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();

        var listed = service.list(1, 10);
        Map<String, Object> saved = service.save(ruleId, Map.of(
                "name", "Risk scoring rule v2",
                "definition", definition,
                "status", "enabled"
        ));
        Map<String, Object> debugged = service.debug(ruleId, Map.of("nodeId", "score", "sample", Map.of("risk", 91)));

        assertThat(created)
                .containsEntry("name", "Risk scoring rule")
                .containsEntry("status", "draft");
        assertThat(listed.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", ruleId)
                .containsEntry("name", "Risk scoring rule");
        assertThat(saved)
                .containsEntry("ruleId", ruleId)
                .containsEntry("name", "Risk scoring rule v2")
                .containsEntry("valid", true)
                .containsEntry("versionId", 1L);
        assertThat(debugged)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "succeeded")
                .containsEntry("versionId", 1L);
        assertThat(debugged.get("output"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("matched", true)
                .containsKey("trace");
        assertThat(((Number) debugged.get("debugRunId")).longValue()).isGreaterThan(0L);
    }

    @Test
    void debugEvaluatesConditionNodesAgainstSampleData() {
        Map<String, Object> created = service.create(Map.of(
                "name", "Receivable risk rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        service.save(ruleId, Map.of("definition", created.get("definition"), "status", "enabled"));

        Map<String, Object> debugged = service.debug(ruleId, Map.of("sample", Map.of("daysOverdue", 12)));

        assertThat(debugged.get("output"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("matched", false)
                .containsEntry("evaluatedNodes", 3);
    }

    @Test
    void debugAndProductionRunSupportAggregateNodes() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "sumOverdue",
                                "type", "aggregate",
                                "sourceField", "invoices",
                                "operation", "sum",
                                "valueField", "overdueAmount",
                                "outputField", "totalOverdueAmount"
                        ),
                        Map.of("id", "risk", "type", "condition", "field", "totalOverdueAmount", "operator", ">=", "value", 10000),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "sumOverdue"),
                        Map.of("source", "sumOverdue", "target", "risk"),
                        Map.of("source", "risk", "target", "end")
                )
        );
        Map<String, Object> sample = Map.of(
                "invoices", java.util.List.of(
                        Map.of("invoiceNo", "A001", "overdueAmount", 4000),
                        Map.of("invoiceNo", "A002", "overdueAmount", 7000)
                )
        );
        Map<String, Object> created = service.create(Map.of(
                "name", "Aggregate receivable risk rule",
                "definition", definition
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        service.save(ruleId, Map.of("definition", definition, "status", "draft"));

        Map<String, Object> debugged = service.debug(ruleId, Map.of("sample", sample));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));
        Map<String, Object> executed = service.execute(ruleId, Map.of("sample", sample));

        assertThat(debugged.get("output"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("matched", true)
                .containsEntry("evaluatedNodes", 4)
                .extractingByKey("trace")
                .asList()
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("nodeId", "sumOverdue")
                        .containsEntry("type", "aggregate")
                        .containsEntry("outputField", "totalOverdueAmount")
                        .containsEntry("value", 11000d));
        assertThat(executed.get("output"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("matched", true)
                .containsEntry("evaluatedNodes", 4);
    }

    @Test
    void requiresReviewApprovalBeforeRuleCanRunInProduction() {
        Map<String, Object> created = service.create(Map.of(
                "name", "Receivable risk approval rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        service.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));

        assertThatThrownBy(() -> service.execute(ruleId, Map.of("sample", Map.of("daysOverdue", 45))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rule must be published");

        Map<String, Object> submitted = service.submitForReview(ruleId, Map.of("comment", "ready for approval"));
        Map<String, Object> approved = service.approve(ruleId, Map.of("comment", "approved for production"));
        Map<String, Object> executed = service.execute(ruleId, Map.of("sample", Map.of("daysOverdue", 45)));

        assertThat(submitted)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "pending_review")
                .containsEntry("reviewComment", "ready for approval");
        assertThat(approved)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "published")
                .containsEntry("approvalComment", "approved for production");
        assertThat(executed)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "succeeded")
                .containsEntry("runType", "production")
                .containsEntry("versionId", 1L);
        assertThat(executed.get("output"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("matched", true);
    }

    @Test
    void createsPendingApprovalRecordWhenProductionRunHitsApprovalNode() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "financeApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "approvalTitle", "Finance review",
                                "slaHours", 4
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Map<String, Object> created = approvalService.create(Map.of(
                "name", "Finance approval rule",
                "definition", definition
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        approvalService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        approvalService.submitForReview(ruleId, Map.of("comment", "ready"));
        approvalService.approve(ruleId, Map.of("comment", "approved"));

        Map<String, Object> executed = approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        var approvals = approvalService.listApprovalRecords(ruleId, 1, 10);

        assertThat(executed)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "succeeded")
                .containsEntry("runType", "production");
        assertThat(approvals.total()).isEqualTo(1);
        assertThat(approvals.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", ruleId)
                .containsEntry("runId", executed.get("debugRunId"))
                .containsEntry("nodeId", "financeApproval")
                .containsEntry("status", "pending")
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("approvalTitle", "Finance review")
                .containsEntry("slaHours", 4)
                .containsEntry("remindCount", 0)
                .containsEntry("approvedByUserId", null)
                .containsEntry("approvalComment", null)
                .containsEntry("lastRemindedAt", null)
                .containsEntry("isOverdue", false)
                .containsKey("slaDueAt");
        assertThat(auditRepository.logs)
                .extracting(OperationLog::operationType)
                .contains("rule_node_approval_pending");
    }

    @Test
    void remindsPendingApprovalRecordAndWritesAuditAndAlert() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        try {
            CurrentUserHolder.set(new CurrentUser(66L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            Long ruleId = createPublishedApprovalRule(approvalService, 2);
            approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
            Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();

            Map<String, Object> reminded = approvalService.remindApprovalRecord(ruleId, approvalRecordId);

            assertThat(reminded)
                    .containsEntry("approvalRecordId", approvalRecordId)
                    .containsEntry("ruleId", ruleId)
                    .containsEntry("status", "pending")
                    .containsEntry("remindCount", 1);
            assertThat(reminded.get("lastRemindedAt")).isNotNull();
            assertThat(alertRepository.alerts)
                    .singleElement()
                    .satisfies(alert -> assertThat(alert)
                            .extracting(SystemAlert::type, SystemAlert::resourceId)
                            .containsExactly("rule_approval_reminder_requested", ruleId));
            assertThat(alertRepository.alerts.get(0).payload())
                    .containsEntry("approvalRecordId", approvalRecordId)
                    .containsEntry("remindCount", 1);
            assertThat(auditRepository.logs)
                    .extracting(OperationLog::operationType)
                    .contains("rule_node_approval_pending", "rule_approval_reminder_requested");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        try {
            CurrentUserHolder.set(new CurrentUser(66L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            Long ruleId = createPublishedApprovalRule(approvalService, 2);
            approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
            Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                    .items()
                    .get(0)
                    .get("approvalRecordId")).longValue();

            approvalService.remindApprovalRecord(ruleId, approvalRecordId);
            Map<String, Object> secondReminder = approvalService.remindApprovalRecord(ruleId, approvalRecordId);

            assertThat(secondReminder)
                    .containsEntry("approvalRecordId", approvalRecordId)
                    .containsEntry("remindCount", 2);
            assertThat(alertRepository.alerts)
                    .singleElement()
                    .satisfies(alert -> {
                        assertThat(alert)
                                .extracting(SystemAlert::recipientUserId, SystemAlert::type, SystemAlert::severity, SystemAlert::resourceId)
                                .containsExactly(66L, "rule_approval_reminder_requested", "warning", ruleId);
                        assertThat(alert.payload())
                                .containsEntry("approvalRecordId", approvalRecordId)
                                .containsEntry("remindCount", 1)
                                .containsEntry("dedupeKey", "rule:" + ruleId + ":approval:" + approvalRecordId + ":reminder");
                    });
            assertThat(auditRepository.logs)
                    .extracting(OperationLog::operationType)
                    .filteredOn("rule_approval_reminder_requested"::equals)
                    .hasSize(2);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void routesApprovalReminderAlertToEnabledAssigneeRoleUser() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        userRepository.save(UserAccount.enabled("finance.manager", "Finance Manager", List.of("finance_manager")).withId(77L));
        userRepository.save(UserAccount.enabled("disabled.finance", "Disabled Finance", List.of("finance_manager")).withId(78L).withStatus("disabled"));
        userRepository.save(UserAccount.enabled("analyst.one", "Analyst One", List.of("analyst")).withId(79L));
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository,
                userRepository
        );
        try {
            CurrentUserHolder.set(new CurrentUser(66L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            Long ruleId = createPublishedApprovalRule(approvalService, 2);
            approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
            Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                    .items()
                    .get(0)
                    .get("approvalRecordId")).longValue();

            approvalService.remindApprovalRecord(ruleId, approvalRecordId);

            assertThat(alertRepository.alerts)
                    .singleElement()
                    .satisfies(alert -> assertThat(alert)
                            .extracting(SystemAlert::recipientUserId, SystemAlert::type, SystemAlert::resourceId)
                            .containsExactly(77L, "rule_approval_reminder_requested", ruleId));
            assertThat(alertRepository.alerts.get(0).payload())
                    .containsEntry("recipientSource", "assigneeRole")
                    .containsEntry("assigneeRole", "finance_manager");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void stopsDownstreamActionsWhenProductionRunHitsApprovalNode() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "notifyFinance", "type", "action", "actionType", "notify", "severity", "warning", "message", "Need finance confirmation"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "notifyFinance"),
                        Map.of("source", "notifyFinance", "target", "end")
                )
        );
        Map<String, Object> created = approvalService.create(Map.of(
                "name", "Finance approval notify rule",
                "definition", definition
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        approvalService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        approvalService.submitForReview(ruleId, Map.of("comment", "ready"));
        approvalService.approve(ruleId, Map.of("comment", "approved"));

        Map<String, Object> executed = approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));

        assertThat(executed)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "succeeded")
                .containsEntry("runType", "production");
        assertThat(approvalService.listApprovalRecords(ruleId, 1, 10).total()).isEqualTo(1);
        assertThat(alertRepository.alerts).isEmpty();
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10)).isEmpty();
        assertThat(auditRepository.logs)
                .extracting(OperationLog::operationType)
                .contains("rule_node_approval_pending")
                .doesNotContain("rule_action_notify");
    }

    @Test
    void approvesPendingApprovalRecordAndWritesAudit() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository
        );
        try {
            CurrentUserHolder.set(new CurrentUser(66L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            Long ruleId = createPublishedApprovalRule(approvalService);
            approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
            Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();

            Map<String, Object> approved = approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "finance approved"
            ));

            assertThat(approved)
                    .containsEntry("approvalRecordId", approvalRecordId)
                    .containsEntry("ruleId", ruleId)
                    .containsEntry("status", "approved")
                    .containsEntry("approvedByUserId", 66L)
                    .containsEntry("approvalComment", "finance approved");
            assertThat(approved.get("approvedAt")).isNotNull();
            assertThat(auditRepository.logs)
                    .extracting(OperationLog::operationType)
                    .contains("rule_node_approval_pending", "rule_node_approval_approved");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void approvingPendingApprovalRecordResumesDownstreamActions() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "notifyFinance", "type", "action", "actionType", "notify", "severity", "warning", "message", "Need finance confirmation"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "notifyFinance"),
                        Map.of("source", "notifyFinance", "target", "end")
                )
        );
        Long ruleId = ((Number) approvalService.create(Map.of(
                "name", "Finance approval resume rule",
                "definition", definition
        )).get("ruleId")).longValue();
        approvalService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        approvalService.submitForReview(ruleId, Map.of("comment", "ready"));
        approvalService.approve(ruleId, Map.of("comment", "approved"));
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();

        assertThat(alertRepository.alerts).isEmpty();

        Map<String, Object> approved = approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                "action", "approve",
                "comment", "finance approved"
        ));

        assertThat(approved)
                .containsEntry("approvalRecordId", approvalRecordId)
                .containsEntry("status", "approved");
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::type, SystemAlert::resourceId)
                        .containsExactly("rule_action_notify", ruleId));
    }

    @Test
    void sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "legalApproval", "type", "approval", "assigneeRole", "legal_manager", "approvalTitle", "Legal review"),
                        Map.of("id", "notifyApproved", "type", "action", "actionType", "notify", "severity", "info", "message", "Sequential approval completed"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "legalApproval"),
                        Map.of("source", "legalApproval", "target", "notifyApproved"),
                        Map.of("source", "notifyApproved", "target", "end")
                )
        );
        Long ruleId = publishRule(approvalService, "Sequential approval rule", definition);

        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));

        var firstLevelApprovals = approvalService.listApprovalRecords(ruleId, 1, 10);
        assertThat(firstLevelApprovals.total()).isEqualTo(1);
        Map<String, Object> financeApproval = approvalByRole(firstLevelApprovals.items(), "finance_manager");
        assertThat(financeApproval)
                .containsEntry("nodeId", "financeApproval")
                .containsEntry("status", "pending");
        assertThat(alertRepository.alerts).isEmpty();

        approvalService.handleApprovalRecord(ruleId, ((Number) financeApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "finance approved"
        ));

        var secondLevelApprovals = approvalService.listApprovalRecords(ruleId, 1, 10);
        assertThat(secondLevelApprovals.total()).isEqualTo(2);
        Map<String, Object> legalApproval = approvalByRole(secondLevelApprovals.items(), "legal_manager");
        assertThat(legalApproval)
                .containsEntry("nodeId", "legalApproval")
                .containsEntry("status", "pending");
        assertThat(alertRepository.alerts).isEmpty();

        approvalService.handleApprovalRecord(ruleId, ((Number) legalApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "legal approved"
        ));

        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::type, SystemAlert::resourceId)
                        .containsExactly("rule_action_notify", ruleId));
    }

    @Test
    void approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = multiAssigneeApprovalDefinition("all");
        Long ruleId = publishRule(approvalService, "Finance and legal approval rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));

        var approvals = approvalService.listApprovalRecords(ruleId, 1, 10);
        assertThat(approvals.total()).isEqualTo(2);
        Map<String, Object> financeApproval = approvalByRole(approvals.items(), "finance_manager");
        Map<String, Object> legalApproval = approvalByRole(approvals.items(), "legal_manager");
        assertThat(financeApproval)
                .containsEntry("approvalMode", "all")
                .containsEntry("approvalGroupTotalCount", 2)
                .containsEntry("approvalGroupApprovedCount", 0)
                .containsEntry("approvalGroupPendingCount", 2);
        assertThat(financeApproval.get("approvalGroupKey")).isEqualTo(legalApproval.get("approvalGroupKey"));
        assertThat(financeApproval.get("assigneeRoles")).isEqualTo(java.util.List.of("finance_manager", "legal_manager"));

        approvalService.handleApprovalRecord(ruleId, ((Number) financeApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "finance approved"
        ));

        Map<String, Object> financeAfterFirstApproval = approvalByRole(
                approvalService.listApprovalRecords(ruleId, 1, 10).items(),
                "finance_manager"
        );
        assertThat(financeAfterFirstApproval)
                .containsEntry("approvalGroupApprovedCount", 1)
                .containsEntry("approvalGroupPendingCount", 1);
        assertThat(alertRepository.alerts).isEmpty();
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10)).isEmpty();

        approvalService.handleApprovalRecord(ruleId, ((Number) legalApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "legal approved"
        ));

        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::type, SystemAlert::resourceId)
                        .containsExactly("rule_action_notify", ruleId));
    }

    @Test
    void approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = multiAssigneeApprovalDefinition("any");
        Long ruleId = publishRule(approvalService, "Finance or legal approval rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));

        var approvals = approvalService.listApprovalRecords(ruleId, 1, 10);
        assertThat(approvals.total()).isEqualTo(2);
        Map<String, Object> financeApproval = approvalByRole(approvals.items(), "finance_manager");
        Map<String, Object> legalApproval = approvalByRole(approvals.items(), "legal_manager");

        approvalService.handleApprovalRecord(ruleId, ((Number) financeApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "finance approved"
        ));

        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::type, SystemAlert::resourceId)
                        .containsExactly("rule_action_notify", ruleId));
        Map<String, Object> closedLegalApproval = approvalByRole(
                approvalService.listApprovalRecords(ruleId, 1, 10).items(),
                "legal_manager"
        );
        assertThat(closedLegalApproval)
                .containsEntry("approvalRecordId", legalApproval.get("approvalRecordId"))
                .containsEntry("status", "closed")
                .containsEntry("approvalComment", "closed because approval group was approved by any assignee")
                .containsEntry("approvalGroupApprovedCount", 1)
                .containsEntry("approvalGroupClosedCount", 1)
                .containsEntry("approvalGroupPendingCount", 0);
        assertThatThrownBy(() -> approvalService.handleApprovalRecord(ruleId, ((Number) legalApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "legal also approved"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already handled");
        assertThat(alertRepository.alerts).hasSize(1);
    }

    @Test
    void rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = multiAssigneeApprovalDefinition("all");
        Long ruleId = publishRule(approvalService, "Finance and legal rejection rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));

        var approvals = approvalService.listApprovalRecords(ruleId, 1, 10);
        Map<String, Object> financeApproval = approvalByRole(approvals.items(), "finance_manager");
        Map<String, Object> legalApproval = approvalByRole(approvals.items(), "legal_manager");

        approvalService.handleApprovalRecord(ruleId, ((Number) financeApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "reject",
                "comment", "missing evidence"
        ));

        var recordsAfterRejection = approvalService.listApprovalRecords(ruleId, 1, 10);
        Map<String, Object> rejectedFinance = approvalById(recordsAfterRejection.items(), financeApproval.get("approvalRecordId"));
        Map<String, Object> closedLegal = approvalById(recordsAfterRejection.items(), legalApproval.get("approvalRecordId"));
        assertThat(rejectedFinance)
                .containsEntry("status", "rejected")
                .containsEntry("approvalComment", "missing evidence")
                .containsEntry("approvalGroupRejectedCount", 1)
                .containsEntry("approvalGroupClosedCount", 1)
                .containsEntry("approvalGroupPendingCount", 0);
        assertThat(closedLegal)
                .containsEntry("approvalRecordId", legalApproval.get("approvalRecordId"))
                .containsEntry("status", "closed")
                .containsEntry("approvalComment", "closed because approval group was rejected")
                .containsEntry("approvalGroupRejectedCount", 1)
                .containsEntry("approvalGroupClosedCount", 1)
                .containsEntry("approvalGroupPendingCount", 0);
        assertThat(approvalService.listApprovalRecordsByStatus("pending", 1, 10).items())
                .noneSatisfy(record -> assertThat(record).containsEntry("approvalRecordId", legalApproval.get("approvalRecordId")));
        assertThat(approvalService.listApprovalRecordsByStatus("closed", 1, 10).items())
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("approvalRecordId", legalApproval.get("approvalRecordId"))
                        .containsEntry("status", "closed"));
        assertThat(approvalService.listApprovalRecordsByStatus("supplement_required", 1, 10).items())
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("status", "supplement_required")
                        .containsEntry("assigneeRole", "finance_manager")
                        .containsEntry("approvalComment", "missing evidence"));
    }

    @Test
    void rejectedApprovalExecutesConfiguredRejectedBranchOnly() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "notifyApproved", "type", "action", "actionType", "notify", "severity", "info", "message", "Approved path executed"),
                        Map.of("id", "notifyRejected", "type", "action", "actionType", "notify", "severity", "warning", "message", "Please supplement materials"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "notifyApproved"),
                        Map.of("source", "financeApproval", "target", "notifyRejected", "condition", "rejected"),
                        Map.of("source", "notifyApproved", "target", "end"),
                        Map.of("source", "notifyRejected", "target", "end")
                )
        );
        Long ruleId = publishRule(approvalService, "Rejected branch approval rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                "action", "reject",
                "comment", "missing invoice"
        ));

        assertThat(alertRepository.alerts)
                .filteredOn(alert -> "rule_action_notify".equals(alert.type()))
                .singleElement()
                .satisfies(alert -> assertThat(alert.payload())
                        .containsEntry("nodeId", "notifyRejected")
                        .containsEntry("message", "Please supplement materials"));
        assertThat(alertRepository.alerts)
                .noneSatisfy(alert -> assertThat(alert.payload()).containsEntry("nodeId", "notifyApproved"));
    }

    @Test
    void rejectedApprovalCreatesSupplementRequestAndResubmissionCreatesNewPendingApproval() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "notifyApproved", "type", "action", "actionType", "notify", "severity", "info", "message", "Approved path executed"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "notifyApproved"),
                        Map.of("source", "notifyApproved", "target", "end")
                )
        );
        Long ruleId = publishRule(approvalService, "Supplement resubmission approval rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long firstApprovalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        Map<String, Object> rejected = approvalService.handleApprovalRecord(ruleId, firstApprovalRecordId, Map.of(
                "action", "reject",
                "comment", "missing invoice"
        ));

        assertThat(rejected)
                .containsEntry("status", "rejected")
                .containsEntry("supplementStatus", "supplement_required");
        assertThat(approvalService.listApprovalRecordsByStatus("supplement_required", 1, 10).items())
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("ruleId", ruleId)
                        .containsEntry("nodeId", "financeApproval")
                        .containsEntry("status", "supplement_required")
                        .containsEntry("approvalComment", "missing invoice"));

        Map<String, Object> resubmitted = approvalService.submitApprovalSupplement(ruleId, firstApprovalRecordId, Map.of(
                "comment", "invoice uploaded",
                "evidenceUrl", "minio://reports/invoice-001.pdf"
        ));

        Long newApprovalRecordId = ((Number) resubmitted.get("newApprovalRecordId")).longValue();
        assertThat(resubmitted)
                .containsEntry("status", "pending")
                .containsEntry("sourceRejectedApprovalRecordId", firstApprovalRecordId)
                .containsEntry("supplementStatus", "resubmitted");
        assertThat(newApprovalRecordId).isNotEqualTo(firstApprovalRecordId);
        assertThat(approvalService.listApprovalRecordsByStatus("pending", 1, 10).items())
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("approvalRecordId", newApprovalRecordId)
                        .containsEntry("status", "pending")
                        .containsEntry("approvalGroupPendingCount", 1)
                        .containsEntry("approvalGroupRejectedCount", 0));

        approvalService.handleApprovalRecord(ruleId, newApprovalRecordId, Map.of(
                "action", "approve",
                "comment", "resubmission approved"
        ));

        assertThat(alertRepository.alerts)
                .anySatisfy(alert -> assertThat(alert.payload()).containsEntry("nodeId", "notifyApproved"));
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_approval_supplement_resubmitted".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("sourceRejectedApprovalRecordId", firstApprovalRecordId)
                        .containsEntry("newApprovalRecordId", newApprovalRecordId)
                        .containsEntry("evidenceUrl", "minio://reports/invoice-001.pdf"));
    }

    @Test
    void uploadsApprovalSupplementAttachmentToRuleScopedStorageAndWritesAudit() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeApprovalSupplementStorage storage = new FakeApprovalSupplementStorage();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                storage
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "financeApproval", "type", "approval", "assigneeRole", "finance_manager", "approvalTitle", "Finance review"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long ruleId = publishRule(approvalService, "Supplement attachment approval rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();
        approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                "action", "reject",
                "comment", "missing invoice"
        ));

        Map<String, Object> uploaded = approvalService.uploadApprovalSupplementAttachment(
                ruleId,
                approvalRecordId,
                new MockMultipartFile("file", "invoice package.pdf", "application/pdf", "invoice evidence".getBytes())
        );

        assertThat(uploaded)
                .containsEntry("ruleId", ruleId)
                .containsEntry("approvalRecordId", approvalRecordId)
                .containsEntry("fileName", "invoice package.pdf")
                .containsEntry("bucket", "approval-supplements")
                .containsEntry("contentType", "application/pdf")
                .containsEntry("sizeBytes", 16L);
        assertThat(uploaded.get("objectKey").toString())
                .startsWith("approval-supplements/rule-" + ruleId + "/approval-" + approvalRecordId + "/")
                .endsWith("/invoice-package.pdf");
        assertThat(uploaded)
                .containsEntry("evidenceUrl", "minio://approval-supplements/" + uploaded.get("objectKey"));
        assertThat(storage.objectKey)
                .isEqualTo(uploaded.get("objectKey"));
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_approval_supplement_attachment_uploaded".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("approvalRecordId", approvalRecordId)
                        .containsEntry("fileName", "invoice package.pdf")
                        .containsEntry("evidenceUrl", uploaded.get("evidenceUrl")));
    }

    @Test
    void productionRunExecutesSubprocessRuleAndWritesParentChildAudit() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> childDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "notifySubprocess", "type", "action", "actionType", "notify", "severity", "warning", "message", "Subprocess finished"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "notifySubprocess"),
                        Map.of("source", "notifySubprocess", "target", "end")
                )
        );
        Long childRuleId = publishRule(ruleService, "Child subprocess rule", childDefinition);
        Map<String, Object> parentDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskSubprocess", "type", "subprocess", "subprocessRuleId", childRuleId, "subprocessName", "Child subprocess rule"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskSubprocess"),
                        Map.of("source", "riskSubprocess", "target", "end")
                )
        );
        Long parentRuleId = publishRule(ruleService, "Parent subprocess rule", parentDefinition);

        Map<String, Object> executed = ruleService.execute(parentRuleId, Map.of(
                "sample", Map.of("riskScore", 91, "secretPrompt", "do-not-leak")
        ));

        Long parentRunId = ((Number) executed.get("debugRunId")).longValue();
        assertThat(ruleService.listRuns(childRuleId, 1, 10).items())
                .singleElement()
                .satisfies(run -> assertThat(run)
                        .containsEntry("ruleId", childRuleId)
                        .containsEntry("status", "succeeded")
                        .containsEntry("runType", "production"));
        Long childRunId = ((Number) ruleService.listRuns(childRuleId, 1, 10).items().get(0).get("runId")).longValue();
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.resourceId()).isEqualTo(childRuleId);
                    assertThat(alert.payload())
                            .containsEntry("ruleId", childRuleId)
                            .containsEntry("runId", childRunId)
                            .containsEntry("nodeId", "notifySubprocess")
                            .doesNotContainKeys("sample", "secretPrompt");
                    assertThat(alert.payload().toString()).doesNotContain("do-not-leak");
                });
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_subprocess_run".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("parentRuleId", parentRuleId)
                        .containsEntry("parentRunId", parentRunId)
                        .containsEntry("nodeId", "riskSubprocess")
                        .containsEntry("subprocessRuleId", childRuleId)
                        .containsEntry("subprocessRunId", childRunId));
    }

    @Test
    void parentProductionRunWaitsForSubprocessApprovalBeforeContinuingDownstreamActions() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> childDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "childApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "approvalMode", "any",
                                "approvalTitle", "Child approval"
                        ),
                        Map.of("id", "childNotify", "type", "action", "actionType", "notify", "severity", "info", "message", "child approved"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "childApproval"),
                        Map.of("source", "childApproval", "target", "childNotify"),
                        Map.of("source", "childNotify", "target", "end")
                )
        );
        Long childRuleId = publishRule(ruleService, "Child approval subprocess rule", childDefinition);
        Map<String, Object> parentDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskSubprocess", "type", "subprocess", "subprocessRuleId", childRuleId, "subprocessName", "Child approval subprocess rule"),
                        Map.of("id", "parentNotify", "type", "action", "actionType", "notify", "severity", "info", "message", "parent continued"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskSubprocess"),
                        Map.of("source", "riskSubprocess", "target", "parentNotify"),
                        Map.of("source", "parentNotify", "target", "end")
                )
        );
        Long parentRuleId = publishRule(ruleService, "Parent waits for child approval", parentDefinition);

        ruleService.execute(parentRuleId, Map.of("sample", Map.of("riskScore", 91)));

        Map<String, Object> childApproval = ruleService.listApprovalRecords(childRuleId, 1, 10).items().get(0);
        assertThat(childApproval)
                .containsEntry("nodeId", "childApproval")
                .containsEntry("status", "pending");
        assertThat(alertRepository.alerts)
                .noneSatisfy(alert -> assertThat(alert.payload()).containsEntry("nodeId", "parentNotify"));

        ruleService.handleApprovalRecord(childRuleId, ((Number) childApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "child approved"
        ));

        assertThat(alertRepository.alerts)
                .anySatisfy(alert -> assertThat(alert.payload()).containsEntry("nodeId", "childNotify"))
                .anySatisfy(alert -> assertThat(alert.payload()).containsEntry("nodeId", "parentNotify"));
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_subprocess_run".equals(log.operationType()))
                .anySatisfy(log -> assertThat(log.result()).isEqualTo("succeeded"));
    }

    @Test
    void subprocessRunWithPendingApprovalIsMarkedPendingApprovalUntilApprovalCompletes() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> childDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "childApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "approvalTitle", "Child approval"
                        ),
                        Map.of("id", "childNotify", "type", "action", "actionType", "notify", "severity", "info", "message", "child approved"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "childApproval"),
                        Map.of("source", "childApproval", "target", "childNotify"),
                        Map.of("source", "childNotify", "target", "end")
                )
        );
        Long childRuleId = publishRule(ruleService, "Pending status child subprocess rule", childDefinition);
        Map<String, Object> parentDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskSubprocess", "type", "subprocess", "subprocessRuleId", childRuleId),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskSubprocess"),
                        Map.of("source", "riskSubprocess", "target", "end")
                )
        );
        Long parentRuleId = publishRule(ruleService, "Parent tracks pending child subprocess status", parentDefinition);

        ruleService.execute(parentRuleId, Map.of("sample", Map.of("riskScore", 91)));

        Map<String, Object> childRun = ruleService.listRuns(childRuleId, 1, 10).items().get(0);
        Map<String, Object> childApproval = ruleService.listApprovalRecords(childRuleId, 1, 10).items().get(0);
        assertThat(childRun)
                .containsEntry("status", "pending_approval");

        ruleService.handleApprovalRecord(childRuleId, ((Number) childApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "child approved"
        ));

        assertThat(ruleService.listRuns(childRuleId, 1, 10).items().get(0))
                .containsEntry("runId", childRun.get("runId"))
                .containsEntry("status", "succeeded");
    }

    @Test
    void subprocessApprovalResumeQueriesPendingAuditBySubprocessRunIdWithoutFullAuditScan() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        QueryOnlyAuditRepository auditRepository = new QueryOnlyAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Map<String, Object> childDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "childApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "approvalTitle", "Child approval"
                        ),
                        Map.of("id", "childNotify", "type", "action", "actionType", "notify", "severity", "info", "message", "child approved"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "childApproval"),
                        Map.of("source", "childApproval", "target", "childNotify"),
                        Map.of("source", "childNotify", "target", "end")
                )
        );
        Long childRuleId = publishRule(ruleService, "Query optimized child subprocess rule", childDefinition);
        Map<String, Object> parentDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskSubprocess", "type", "subprocess", "subprocessRuleId", childRuleId),
                        Map.of("id", "parentNotify", "type", "action", "actionType", "notify", "severity", "info", "message", "parent continued"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskSubprocess"),
                        Map.of("source", "riskSubprocess", "target", "parentNotify"),
                        Map.of("source", "parentNotify", "target", "end")
                )
        );
        Long parentRuleId = publishRule(ruleService, "Parent resumes through query optimized audit lookup", parentDefinition);

        ruleService.execute(parentRuleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> childApproval = ruleService.listApprovalRecords(childRuleId, 1, 10).items().get(0);

        ruleService.handleApprovalRecord(childRuleId, ((Number) childApproval.get("approvalRecordId")).longValue(), Map.of(
                "action", "approve",
                "comment", "child approved"
        ));

        assertThat(auditRepository.detailLookupCount).isGreaterThan(0);
        assertThat(auditRepository.fullScanCount).isZero();
        assertThat(alertRepository.alerts)
                .anySatisfy(alert -> assertThat(alert.payload()).containsEntry("nodeId", "parentNotify"));
    }

    @Test
    void listsSubprocessRunTopologyForParentProductionRun() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository()
        );
        Map<String, Object> childDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "notifySubprocess", "type", "action", "actionType", "notify", "severity", "warning", "message", "Subprocess finished"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "notifySubprocess"),
                        Map.of("source", "notifySubprocess", "target", "end")
                )
        );
        Long childRuleId = publishRule(ruleService, "Child subprocess topology rule", childDefinition);
        Map<String, Object> parentDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskSubprocess", "type", "subprocess", "subprocessRuleId", childRuleId, "subprocessName", "Child subprocess topology rule"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskSubprocess"),
                        Map.of("source", "riskSubprocess", "target", "end")
                )
        );
        Long parentRuleId = publishRule(ruleService, "Parent subprocess topology rule", parentDefinition);
        Map<String, Object> executed = ruleService.execute(parentRuleId, Map.of("sample", Map.of("riskScore", 91)));
        Long parentRunId = ((Number) executed.get("debugRunId")).longValue();
        Long childRunId = ((Number) ruleService.listRuns(childRuleId, 1, 10).items().get(0).get("runId")).longValue();

        Map<String, Object> topology = ruleService.subprocessRunTopology(parentRuleId, parentRunId);

        assertThat(topology)
                .containsEntry("ruleId", parentRuleId)
                .containsEntry("runId", parentRunId);
        assertThat((List<Map<String, Object>>) topology.get("nodes"))
                .hasSize(2)
                .anySatisfy(node -> assertThat(node)
                        .containsEntry("runId", parentRunId)
                        .containsEntry("ruleId", parentRuleId)
                        .containsEntry("role", "parent")
                        .containsEntry("status", "succeeded"))
                .anySatisfy(node -> assertThat(node)
                        .containsEntry("runId", childRunId)
                        .containsEntry("ruleId", childRuleId)
                        .containsEntry("role", "subprocess")
                        .containsEntry("status", "succeeded"));
        assertThat((List<Map<String, Object>>) topology.get("edges"))
                .singleElement()
                .satisfies(edge -> assertThat(edge)
                        .containsEntry("parentRunId", parentRunId)
                        .containsEntry("subprocessRunId", childRunId)
                        .containsEntry("nodeId", "riskSubprocess")
                        .containsEntry("result", "succeeded"));
    }

    @Test
    void productionRunRejectsIndirectSubprocessCycleWithAuditEvidence() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository()
        );
        Long parentRuleId = publishRule(ruleService, "Parent cycle placeholder", Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(Map.of("source", "start", "target", "end"))
        ));
        Map<String, Object> childDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "backToParent", "type", "subprocess", "subprocessRuleId", parentRuleId),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "backToParent"),
                        Map.of("source", "backToParent", "target", "end")
                )
        );
        Long childRuleId = publishRule(ruleService, "Child cycle rule", childDefinition);
        Map<String, Object> parentDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "toChild", "type", "subprocess", "subprocessRuleId", childRuleId),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "toChild"),
                        Map.of("source", "toChild", "target", "end")
                )
        );
        ruleService.save(parentRuleId, Map.of("definition", parentDefinition, "status", "published"));

        assertThatThrownBy(() -> ruleService.execute(parentRuleId, Map.of("sample", Map.of("riskScore", 91))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subprocess cycle");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_subprocess_run".equals(log.operationType()))
                .anySatisfy(log -> assertThat(log)
                        .extracting(OperationLog::result)
                        .isEqualTo("failed"));
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_subprocess_run".equals(log.operationType()))
                .anySatisfy(log -> assertThat(log.detail().get("errorMessage").toString())
                        .contains("subprocess cycle"));
    }

    @Test
    void productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository()
        );
        Long terminalRuleId = publishRule(ruleService, "Depth terminal rule", Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(Map.of("source", "start", "target", "end"))
        ));
        Long nextRuleId = terminalRuleId;
        for (int depth = 0; depth < 8; depth++) {
            Long currentChildRuleId = nextRuleId;
            nextRuleId = publishRule(ruleService, "Depth subprocess rule " + depth, Map.of(
                    "nodes", java.util.List.of(
                            Map.of("id", "start", "type", "start"),
                            Map.of("id", "callChild", "type", "subprocess", "subprocessRuleId", currentChildRuleId),
                            Map.of("id", "end", "type", "end")
                    ),
                    "edges", java.util.List.of(
                            Map.of("source", "start", "target", "callChild"),
                            Map.of("source", "callChild", "target", "end")
                    )
            ));
        }
        Long parentRuleId = nextRuleId;

        assertThatThrownBy(() -> ruleService.execute(parentRuleId, Map.of("sample", Map.of("riskScore", 91))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subprocess depth");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_subprocess_run".equals(log.operationType()))
                .anySatisfy(log -> assertThat(log.detail().get("errorMessage").toString())
                        .contains("subprocess depth"));
    }

    @Test
    void productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository()
        );
        Long ruleId = publishRule(ruleService, "Oversized linear rule", linearDefinitionWithNodeCount(129));

        assertThatThrownBy(() -> ruleService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rule execution node budget exceeded");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_production_run".equals(log.operationType()))
                .anySatisfy(log -> {
                    assertThat(log.result()).isEqualTo("failed");
                    assertThat(log.detail().get("errorMessage").toString())
                            .contains("rule execution node budget exceeded");
                });
    }

    @Test
    void productionRunRejectsRuleExecutionBeyondDurationBudgetWithAuditEvidence() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        List<Long> nanoTimes = new ArrayList<>(List.of(0L, 31_000_000_000L));
        RuleApplicationService ruleService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                () -> nanoTimes.isEmpty() ? 31_000_000_000L : nanoTimes.remove(0)
        );
        Long ruleId = publishRule(ruleService, "Slow production rule", linearDefinitionWithNodeCount(3));

        assertThatThrownBy(() -> ruleService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rule execution duration budget exceeded");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_production_run".equals(log.operationType()))
                .anySatisfy(log -> {
                    assertThat(log.result()).isEqualTo("failed");
                    assertThat(log.detail().get("errorMessage").toString())
                            .contains("rule execution duration budget exceeded");
                });
    }

    @Test
    void listsOnlyPendingApprovalRecordsAcrossRules() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository()
        );
        Long firstRuleId = createPublishedApprovalRule(approvalService);
        Long secondRuleId = createPublishedApprovalRule(approvalService);

        approvalService.execute(firstRuleId, Map.of("sample", Map.of("riskScore", 91)));
        approvalService.execute(secondRuleId, Map.of("sample", Map.of("riskScore", 92)));
        Long firstApprovalRecordId = ((Number) approvalService.listApprovalRecords(firstRuleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        approvalService.handleApprovalRecord(firstRuleId, firstApprovalRecordId, Map.of(
                "action", "approve",
                "comment", "approved"
        ));

        var pendingApprovals = approvalService.listPendingApprovalRecords(1, 10);

        assertThat(pendingApprovals.total()).isEqualTo(1);
        assertThat(pendingApprovals.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", secondRuleId)
                .containsEntry("status", "pending")
                .containsEntry("nodeId", "financeApproval");
    }

    @Test
    void listsHandledApprovalRecordsByStatusAcrossRules() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository()
        );
        Long approvedRuleId = createPublishedApprovalRule(approvalService);
        Long rejectedRuleId = createPublishedApprovalRule(approvalService);

        approvalService.execute(approvedRuleId, Map.of("sample", Map.of("riskScore", 91)));
        approvalService.execute(rejectedRuleId, Map.of("sample", Map.of("riskScore", 92)));
        Long approvedRecordId = ((Number) approvalService.listApprovalRecords(approvedRuleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();
        Long rejectedRecordId = ((Number) approvalService.listApprovalRecords(rejectedRuleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        approvalService.handleApprovalRecord(approvedRuleId, approvedRecordId, Map.of(
                "action", "approve",
                "comment", "approved"
        ));
        approvalService.handleApprovalRecord(rejectedRuleId, rejectedRecordId, Map.of(
                "action", "reject",
                "comment", "rejected"
        ));

        var approvedApprovals = approvalService.listApprovalRecordsByStatus("approved", 1, 10);
        var rejectedApprovals = approvalService.listApprovalRecordsByStatus("rejected", 1, 10);

        assertThat(approvedApprovals.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", approvedRuleId)
                .containsEntry("status", "approved");
        assertThat(rejectedApprovals.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", rejectedRuleId)
                .containsEntry("status", "rejected")
                .containsEntry("approvalComment", "rejected");
    }

    @Test
    void nonManagersOnlyListApprovalRecordsForTheirRoles() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository()
        );
        Long financeRuleId = createPublishedApprovalRule(approvalService, "finance_manager");
        Long legalRuleId = createPublishedApprovalRule(approvalService, "legal_manager");

        approvalService.execute(financeRuleId, Map.of("sample", Map.of("riskScore", 91)));
        approvalService.execute(legalRuleId, Map.of("sample", Map.of("riskScore", 92)));

        try {
            CurrentUserHolder.set(new CurrentUser(77L, Set.of("finance_manager"), Set.of("rule:debug")));
            var financeApprovals = approvalService.listApprovalRecordsByStatus("pending", 1, 10);

            assertThat(financeApprovals.total()).isEqualTo(1);
            assertThat(financeApprovals.items())
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("ruleId", financeRuleId)
                    .containsEntry("assigneeRole", "finance_manager");

            CurrentUserHolder.set(new CurrentUser(1L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            var managerApprovals = approvalService.listApprovalRecordsByStatus("pending", 1, 10);

            assertThat(managerApprovals.total()).isEqualTo(2);
            assertThat(managerApprovals.items())
                    .extracting(item -> item.get("ruleId"))
                    .containsExactlyInAnyOrder(financeRuleId, legalRuleId);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void delegateRoleUserListsOnlyActiveDelegatedApprovalRecords() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Long activeDelegatedRuleId = createPublishedApprovalRule(
                approvalService,
                "finance_manager",
                null,
                "finance_delegate",
                OffsetDateTime.now().minusHours(1).toString(),
                OffsetDateTime.now().plusHours(1).toString()
        );
        Long expiredDelegatedRuleId = createPublishedApprovalRule(
                approvalService,
                "legal_manager",
                null,
                "finance_delegate",
                OffsetDateTime.now().minusHours(3).toString(),
                OffsetDateTime.now().minusHours(1).toString()
        );
        Long ownRoleRuleId = createPublishedApprovalRule(approvalService, "finance_delegate");

        approvalService.execute(activeDelegatedRuleId, Map.of("sample", Map.of("riskScore", 91)));
        approvalService.execute(expiredDelegatedRuleId, Map.of("sample", Map.of("riskScore", 92)));
        approvalService.execute(ownRoleRuleId, Map.of("sample", Map.of("riskScore", 93)));

        try {
            CurrentUserHolder.set(new CurrentUser(88L, Set.of("finance_delegate"), Set.of("rule:debug")));
            var delegateApprovals = approvalService.listApprovalRecordsByStatus("pending", 1, 10);

            assertThat(delegateApprovals.total()).isEqualTo(2);
            assertThat(delegateApprovals.items())
                    .extracting(item -> item.get("ruleId"))
                    .containsExactlyInAnyOrder(activeDelegatedRuleId, ownRoleRuleId)
                    .doesNotContain(expiredDelegatedRuleId);
            assertThat(delegateApprovals.items())
                    .anySatisfy(record -> assertThat(record)
                            .containsEntry("ruleId", activeDelegatedRuleId)
                            .containsEntry("assigneeRole", "finance_manager")
                            .containsEntry("delegateRole", "finance_delegate"));
            assertThat(delegateApprovals.items())
                    .anySatisfy(record -> assertThat(record)
                            .containsEntry("ruleId", ownRoleRuleId)
                            .containsEntry("assigneeRole", "finance_delegate"));
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void approvalRecordResponseIncludesEnabledAssigneeAndDelegateUsers() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        userRepository.save(UserAccount.enabled("fin.approver", "Finance Approver", List.of("finance_manager")));
        userRepository.save(new UserAccount(null, "fin.disabled", "Disabled Finance", "disabled", List.of("finance_manager")));
        userRepository.save(UserAccount.enabled("fin.delegate", "Finance Delegate", List.of("finance_delegate")));
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository(),
                userRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "financeApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "approvalTitle", "Finance approval",
                                "delegateRole", "finance_delegate"
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Map<String, Object> created = approvalService.create(Map.of("name", "Directory mapped approval", "definition", definition));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        approvalService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        approvalService.submitForReview(ruleId, Map.of("comment", "ready"));
        approvalService.approve(ruleId, Map.of("comment", "approved"));

        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95)));
        Map<String, Object> approvalRecord = approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(approvalRecord.get("assigneeUsers"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("username", "fin.approver")
                .containsEntry("displayName", "Finance Approver")
                .containsEntry("role", "finance_manager")
                .doesNotContainKey("status");
        assertThat(approvalRecord.get("delegateUsers"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("username", "fin.delegate")
                .containsEntry("displayName", "Finance Delegate")
                .containsEntry("role", "finance_delegate");
    }

    @Test
    void filtersApprovalRecordsByStatusRoleRuleCreatorApproverAndCreatedAt() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository()
        );
        Long financeRuleId = createPublishedApprovalRule(approvalService);
        Long secondFinanceRuleId = createPublishedApprovalRule(approvalService);
        Long approvedRuleId = createPublishedApprovalRule(approvalService);

        approvalService.execute(financeRuleId, Map.of("sample", Map.of("riskScore", 91)));
        approvalService.execute(secondFinanceRuleId, Map.of("sample", Map.of("riskScore", 92)));
        approvalService.execute(approvedRuleId, Map.of("sample", Map.of("riskScore", 93)));

        Long firstPendingId = ((Number) approvalService.listApprovalRecords(financeRuleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();
        Long secondPendingId = ((Number) approvalService.listApprovalRecords(secondFinanceRuleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();
        Long approvedRecordId = ((Number) approvalService.listApprovalRecords(approvedRuleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        RuleApprovalRecord firstPending = ruleRepository.findApprovalRecordById(firstPendingId).orElseThrow();
        RuleApprovalRecord secondPending = ruleRepository.findApprovalRecordById(secondPendingId).orElseThrow();
        ruleRepository.updateApprovalRecord(new RuleApprovalRecord(
                firstPending.id(),
                firstPending.ruleId(),
                firstPending.runId(),
                firstPending.nodeId(),
                firstPending.assigneeRole(),
                firstPending.delegateRole(),
                firstPending.delegateActiveFrom(),
                firstPending.delegateActiveTo(),
                firstPending.approvalTitle(),
                firstPending.status(),
                firstPending.createdByUserId(),
                firstPending.approvedByUserId(),
                firstPending.approvalComment(),
                firstPending.approvedAt(),
                OffsetDateTime.parse("2026-06-25T08:10:00Z"),
                firstPending.slaHours(),
                firstPending.remindCount(),
                firstPending.lastRemindedAt()
        ));
        ruleRepository.updateApprovalRecord(new RuleApprovalRecord(
                secondPending.id(),
                secondPending.ruleId(),
                secondPending.runId(),
                secondPending.nodeId(),
                secondPending.assigneeRole(),
                secondPending.delegateRole(),
                secondPending.delegateActiveFrom(),
                secondPending.delegateActiveTo(),
                secondPending.approvalTitle(),
                secondPending.status(),
                secondPending.createdByUserId(),
                secondPending.approvedByUserId(),
                secondPending.approvalComment(),
                secondPending.approvedAt(),
                OffsetDateTime.parse("2026-06-25T09:40:00Z"),
                secondPending.slaHours(),
                secondPending.remindCount(),
                secondPending.lastRemindedAt()
        ));

        try {
            CurrentUserHolder.set(new CurrentUser(66L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            approvalService.handleApprovalRecord(approvedRuleId, approvedRecordId, Map.of(
                    "action", "approve",
                    "comment", "approved by finance lead"
            ));
        } finally {
            CurrentUserHolder.clear();
        }

        var pendingFiltered = approvalService.listApprovalRecordsByStatus("pending", 1, 10, Map.of(
                "ruleId", financeRuleId,
                "assigneeRole", "finance_manager",
                "createdByUserId", 1,
                "createdAtFrom", "2026-06-25T08:00:00Z",
                "createdAtTo", "2026-06-25T08:30:00Z"
        ));
        var approvedFiltered = approvalService.listApprovalRecordsByStatus("approved", 1, 10, Map.of(
                "ruleId", approvedRuleId,
                "approvedByUserId", 66,
                "approvalTitle", "Finance review"
        ));

        assertThat(pendingFiltered.total()).isEqualTo(1);
        assertThat(pendingFiltered.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", financeRuleId)
                .containsEntry("status", "pending")
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("createdByUserId", 1L);
        assertThat(approvedFiltered.total()).isEqualTo(1);
        assertThat(approvedFiltered.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", approvedRuleId)
                .containsEntry("status", "approved")
                .containsEntry("approvedByUserId", 66L)
                .containsEntry("approvalTitle", "Finance review");
    }

    @Test
    void marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository()
        );
        Long ruleId = createPublishedApprovalRule(approvalService, 1);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        RuleApprovalRecord pendingRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();
        ruleRepository.updateApprovalRecord(new RuleApprovalRecord(
                pendingRecord.id(),
                pendingRecord.ruleId(),
                pendingRecord.runId(),
                pendingRecord.nodeId(),
                pendingRecord.assigneeRole(),
                pendingRecord.delegateRole(),
                pendingRecord.delegateActiveFrom(),
                pendingRecord.delegateActiveTo(),
                pendingRecord.approvalTitle(),
                pendingRecord.status(),
                pendingRecord.createdByUserId(),
                pendingRecord.approvedByUserId(),
                pendingRecord.approvalComment(),
                pendingRecord.approvedAt(),
                pendingRecord.createdAt().minusHours(5),
                pendingRecord.slaHours(),
                pendingRecord.remindCount(),
                pendingRecord.lastRemindedAt()
        ));

        var pendingApprovals = approvalService.listPendingApprovalRecords(1, 10);

        assertThat(pendingApprovals.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", ruleId)
                .containsEntry("status", "pending")
                .containsEntry("slaHours", 1)
                .containsEntry("isOverdue", true)
                .containsKey("slaDueAt");
    }

    @Test
    void scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Long ruleId = createPublishedApprovalRule(approvalService, 1);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        RuleApprovalRecord pendingRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();
        ruleRepository.updateApprovalRecord(new RuleApprovalRecord(
                pendingRecord.id(),
                pendingRecord.ruleId(),
                pendingRecord.runId(),
                pendingRecord.nodeId(),
                pendingRecord.assigneeRole(),
                pendingRecord.delegateRole(),
                pendingRecord.delegateActiveFrom(),
                pendingRecord.delegateActiveTo(),
                pendingRecord.approvalTitle(),
                pendingRecord.status(),
                pendingRecord.createdByUserId(),
                pendingRecord.approvedByUserId(),
                pendingRecord.approvalComment(),
                pendingRecord.approvedAt(),
                pendingRecord.createdAt().minusHours(3),
                pendingRecord.slaHours(),
                pendingRecord.remindCount(),
                pendingRecord.lastRemindedAt()
        ));
        RuleApprovalSlaScheduler scheduler = new RuleApprovalSlaScheduler(ruleRepository, approvalService, false);

        int firstScanCount = scheduler.runOverdueApprovalsOnce();
        int secondScanCount = scheduler.runOverdueApprovalsOnce();

        assertThat(firstScanCount).isEqualTo(1);
        assertThat(secondScanCount).isEqualTo(0);
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::recipientUserId, SystemAlert::type, SystemAlert::severity, SystemAlert::resourceId)
                        .containsExactly(1L, "rule_approval_sla_overdue", "warning", ruleId));
        assertThat(alertRepository.alerts.get(0).payload())
                .containsEntry("approvalRecordId", approvalRecordId)
                .containsEntry("slaHours", 1)
                .containsEntry("dedupeKey", "rule:" + ruleId + ":approval:" + approvalRecordId + ":sla-overdue");
        assertThat(auditRepository.logs)
                .extracting(OperationLog::operationType)
                .contains("rule_approval_sla_overdue");
    }

    @Test
    void routesOverdueSlaAlertToEnabledAssigneeRoleUser() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        userRepository.save(UserAccount.enabled("finance.manager", "Finance Manager", List.of("finance_manager")).withId(77L));
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository,
                userRepository
        );
        CurrentUserHolder.set(new CurrentUser(66L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
        Long ruleId = createPublishedApprovalRule(approvalService, 1);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();
        RuleApprovalRecord pendingRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();
        ruleRepository.updateApprovalRecord(new RuleApprovalRecord(
                pendingRecord.id(),
                pendingRecord.ruleId(),
                pendingRecord.runId(),
                pendingRecord.nodeId(),
                pendingRecord.assigneeRole(),
                pendingRecord.delegateRole(),
                pendingRecord.delegateActiveFrom(),
                pendingRecord.delegateActiveTo(),
                pendingRecord.approvalTitle(),
                pendingRecord.status(),
                pendingRecord.createdByUserId(),
                pendingRecord.approvedByUserId(),
                pendingRecord.approvalComment(),
                pendingRecord.approvedAt(),
                pendingRecord.createdAt().minusHours(2),
                pendingRecord.slaHours(),
                pendingRecord.remindCount(),
                pendingRecord.lastRemindedAt()
        ));
        RuleApprovalRecord overdueRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();

        approvalService.createApprovalSlaOverdueAlert(overdueRecord);

        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::recipientUserId, SystemAlert::type, SystemAlert::resourceId)
                        .containsExactly(77L, "rule_approval_sla_overdue", ruleId));
        assertThat(alertRepository.alerts.get(0).payload())
                .containsEntry("recipientSource", "assigneeRole")
                .containsEntry("assigneeRole", "finance_manager");
        CurrentUserHolder.clear();
    }

    @Test
    void routesOverdueSlaAlertToConfiguredEscalationRoleUser() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        userRepository.save(UserAccount.enabled("finance.manager", "Finance Manager", List.of("finance_manager")).withId(77L));
        userRepository.save(UserAccount.enabled("finance.director", "Finance Director", List.of("finance_director")).withId(88L));
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository,
                userRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "financeApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "approvalTitle", "Finance review",
                                "slaHours", 1,
                                "slaEscalationRole", "finance_director"
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long ruleId = publishRule(approvalService, "Finance escalation rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();
        RuleApprovalRecord pendingRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();
        ruleRepository.updateApprovalRecord(new RuleApprovalRecord(
                pendingRecord.id(),
                pendingRecord.ruleId(),
                pendingRecord.runId(),
                pendingRecord.nodeId(),
                pendingRecord.assigneeRole(),
                pendingRecord.delegateRole(),
                pendingRecord.delegateActiveFrom(),
                pendingRecord.delegateActiveTo(),
                pendingRecord.approvalTitle(),
                pendingRecord.status(),
                pendingRecord.createdByUserId(),
                pendingRecord.approvedByUserId(),
                pendingRecord.approvalComment(),
                pendingRecord.approvedAt(),
                pendingRecord.createdAt().minusHours(2),
                pendingRecord.slaHours(),
                pendingRecord.remindCount(),
                pendingRecord.lastRemindedAt()
        ));
        RuleApprovalRecord overdueRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();

        approvalService.createApprovalSlaOverdueAlert(overdueRecord);

        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::recipientUserId, SystemAlert::type, SystemAlert::resourceId)
                        .containsExactly(88L, "rule_approval_sla_overdue", ruleId));
        assertThat(alertRepository.alerts.get(0).payload())
                .containsEntry("recipientSource", "slaEscalationRole")
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("escalationRole", "finance_director");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_approval_sla_overdue".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("escalationRole", "finance_director")
                        .containsEntry("recipientSource", "slaEscalationRole"));
    }

    @Test
    void routesOverdueSlaAlertToHighestMatchedEscalationPolicy() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        userRepository.save(UserAccount.enabled("finance.manager", "Finance Manager", List.of("finance_manager")).withId(77L));
        userRepository.save(UserAccount.enabled("finance.director", "Finance Director", List.of("finance_director")).withId(88L));
        userRepository.save(UserAccount.enabled("risk.vp", "Risk VP", List.of("risk_vp")).withId(99L));
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository,
                userRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "financeApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "approvalTitle", "Finance review",
                                "slaHours", 1,
                                "slaEscalations", java.util.List.of(
                                        Map.of("afterHours", 4, "role", "finance_director"),
                                        Map.of("afterHours", 8, "role", "risk_vp")
                                )
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long ruleId = publishRule(approvalService, "Finance multi level escalation rule", definition);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();
        RuleApprovalRecord pendingRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();
        ruleRepository.updateApprovalRecord(new RuleApprovalRecord(
                pendingRecord.id(),
                pendingRecord.ruleId(),
                pendingRecord.runId(),
                pendingRecord.nodeId(),
                pendingRecord.assigneeRole(),
                pendingRecord.delegateRole(),
                pendingRecord.delegateActiveFrom(),
                pendingRecord.delegateActiveTo(),
                pendingRecord.approvalTitle(),
                pendingRecord.status(),
                pendingRecord.createdByUserId(),
                pendingRecord.approvedByUserId(),
                pendingRecord.approvalComment(),
                pendingRecord.approvedAt(),
                pendingRecord.createdAt().minusHours(9),
                pendingRecord.slaHours(),
                pendingRecord.remindCount(),
                pendingRecord.lastRemindedAt()
        ));
        RuleApprovalRecord overdueRecord = ruleRepository.findApprovalRecordById(approvalRecordId).orElseThrow();

        approvalService.createApprovalSlaOverdueAlert(overdueRecord);

        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert)
                        .extracting(SystemAlert::recipientUserId, SystemAlert::type, SystemAlert::resourceId)
                        .containsExactly(99L, "rule_approval_sla_overdue", ruleId));
        assertThat(alertRepository.alerts.get(0).payload())
                .containsEntry("recipientSource", "slaEscalationPolicy")
                .containsEntry("escalationRole", "risk_vp")
                .containsEntry("escalationAfterHours", 8);
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_approval_sla_overdue".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("recipientSource", "slaEscalationPolicy")
                        .containsEntry("escalationRole", "risk_vp")
                        .containsEntry("escalationAfterHours", 8));
    }

    @Test
    void rejectsPendingApprovalRecordAndPreventsRepeatedHandling() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Long ruleId = createPublishedApprovalRule(approvalService);
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();

        Map<String, Object> rejected = approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                "action", "reject",
                "comment", "missing attachment"
        ));

        assertThat(rejected)
                .containsEntry("approvalRecordId", approvalRecordId)
                .containsEntry("status", "rejected")
                .containsEntry("approvalComment", "missing attachment");
        assertThat(approvalService.listApprovalRecordsByStatus("supplement_required", 1, 10).items())
                .singleElement()
                .satisfies(record -> assertThat(record)
                        .containsEntry("ruleId", ruleId)
                        .containsEntry("status", "supplement_required")
                        .containsEntry("assigneeRole", "finance_manager")
                        .containsEntry("approvalTitle", "Finance review")
                        .containsEntry("approvalComment", "missing attachment"));
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> assertThat(alert.payload())
                        .containsEntry("ruleId", ruleId)
                        .containsEntry("approvalRecordId", approvalRecordId)
                        .containsEntry("status", "rejected")
                        .containsEntry("comment", "missing attachment"));
        assertThatThrownBy(() -> approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                "action", "approve",
                "comment", "retry"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already handled");
    }

    @Test
    void assigneeRoleUserCanApproveOwnPendingApprovalRecord() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager");
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        try {
            CurrentUserHolder.set(new CurrentUser(77L, Set.of("finance_manager"), Set.of("rule:debug")));
            Map<String, Object> approved = approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "approved by assigned finance manager"
            ));

            assertThat(approved)
                    .containsEntry("approvalRecordId", approvalRecordId)
                    .containsEntry("status", "approved")
                    .containsEntry("approvedByUserId", 77L)
                    .containsEntry("approvalComment", "approved by assigned finance manager");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void delegateRoleUserCanApproveDelegatedPendingApprovalRecord() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager", null, "finance_delegate");
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        try {
            CurrentUserHolder.set(new CurrentUser(88L, Set.of("finance_delegate"), Set.of("rule:debug")));
            Map<String, Object> approved = approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "approved by delegated finance approver"
            ));

            assertThat(approved)
                    .containsEntry("approvalRecordId", approvalRecordId)
                    .containsEntry("assigneeRole", "finance_manager")
                    .containsEntry("delegateRole", "finance_delegate")
                    .containsEntry("handledByDelegate", true)
                    .containsEntry("status", "approved")
                    .containsEntry("approvedByUserId", 88L)
                    .containsEntry("approvalComment", "approved by delegated finance approver");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void delegateRoleUserCanApproveOnlyInsideDelegateActiveWindow() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String activeFrom = OffsetDateTime.now().minusHours(1).toString();
        String activeTo = OffsetDateTime.now().plusHours(1).toString();
        Long ruleId = createPublishedApprovalRule(
                approvalService,
                "finance_manager",
                null,
                "finance_delegate",
                activeFrom,
                activeTo
        );
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        try {
            CurrentUserHolder.set(new CurrentUser(88L, Set.of("finance_delegate"), Set.of("rule:debug")));
            Map<String, Object> approved = approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "approved inside active delegate window"
            ));

            assertThat(approved)
                    .containsEntry("approvalRecordId", approvalRecordId)
                    .containsEntry("delegateRole", "finance_delegate")
                    .containsEntry("delegateActiveFrom", activeFrom)
                    .containsEntry("delegateActiveTo", activeTo)
                    .containsEntry("handledByDelegate", true)
                    .containsEntry("status", "approved")
                    .containsEntry("approvedByUserId", 88L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void activeDelegateRuleAppliesWhenApprovalNodeHasNoDelegateRole() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String activeFrom = OffsetDateTime.now().minusHours(1).toString();
        String activeTo = OffsetDateTime.now().plusHours(1).toString();
        Map<String, Object> delegateRule = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", activeFrom,
                "activeTo", activeTo,
                "reason", "finance manager vacation"
        ));
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager");

        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> approvalRecord = approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(delegateRule)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", "finance_delegate")
                .containsEntry("status", "enabled");
        assertThat(approvalRecord)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", "finance_delegate")
                .containsEntry("delegateActiveFrom", activeFrom)
                .containsEntry("delegateActiveTo", activeTo);

        try {
            CurrentUserHolder.set(new CurrentUser(88L, Set.of("finance_delegate"), Set.of("rule:debug")));
            Map<String, Object> approved = approvalService.handleApprovalRecord(
                    ruleId,
                    ((Number) approvalRecord.get("approvalRecordId")).longValue(),
                    Map.of("action", "approve", "comment", "approved through delegate rule")
            );

            assertThat(approved)
                    .containsEntry("status", "approved")
                    .containsEntry("handledByDelegate", true)
                    .containsEntry("approvedByUserId", 88L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void createsAndListsApprovalTemplatesWithValidatedSteps() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );

        Map<String, Object> created = approvalService.createApprovalTemplate(Map.of(
                "name", "Finance two-level approval",
                "description", "Finance manager then finance director",
                "status", "enabled",
                "steps", List.of(
                        Map.of(
                                "stepId", "financeManager",
                                "approvalTitle", "Finance manager approval",
                                "assigneeRoles", List.of("finance_manager"),
                                "approvalMode", "all",
                                "slaHours", 4,
                                "slaEscalations", List.of(Map.of(
                                        "afterHours", 8,
                                        "role", "finance_director"
                                ))
                        ),
                        Map.of(
                                "stepId", "financeDirector",
                                "approvalTitle", "Finance director approval",
                                "assigneeRoles", List.of("finance_director"),
                                "approvalMode", "all",
                                "slaHours", 8
                        )
                )
        ));

        var listed = approvalService.listApprovalTemplates(1, 10, Map.of("status", "enabled"));

        assertThat(created)
                .containsEntry("name", "Finance two-level approval")
                .containsEntry("description", "Finance manager then finance director")
                .containsEntry("status", "enabled")
                .containsEntry("version", 1)
                .containsKey("approvalTemplateId");
        assertThat(created.get("steps"))
                .asList()
                .hasSize(2)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("stepId", "financeManager")
                .containsEntry("approvalTitle", "Finance manager approval")
                .containsEntry("assigneeRoles", List.of("finance_manager"))
                .containsEntry("approvalMode", "all")
                .containsEntry("slaHours", 4)
                .containsEntry("slaEscalations", List.of(Map.of("afterHours", 8, "role", "finance_director")));
        assertThat(listed.total()).isEqualTo(1);
        assertThat(listed.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("approvalTemplateId", created.get("approvalTemplateId"))
                .containsEntry("name", "Finance two-level approval")
                .containsEntry("version", 1);

        assertThatThrownBy(() -> approvalService.createApprovalTemplate(Map.of(
                "name", "Invalid approval template",
                "steps", List.of(Map.of(
                        "stepId", "missingTitle",
                        "assigneeRoles", List.of("finance_manager")
                ))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approvalTitle is required");
        assertThatThrownBy(() -> approvalService.createApprovalTemplate(Map.of(
                "name", "Invalid approval template",
                "steps", List.of(Map.of(
                        "stepId", "missingAssignee",
                        "approvalTitle", "Missing assignee"
                ))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assigneeRoles is required");
    }

    @Test
    void disablesAndEnablesApprovalTemplatesForDesignerReuse() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> created = approvalService.createApprovalTemplate(Map.of(
                "name", "Finance lifecycle approval",
                "status", "enabled",
                "steps", List.of(Map.of(
                        "stepId", "financeManager",
                        "approvalTitle", "Finance manager approval",
                        "assigneeRoles", List.of("finance_manager")
                ))
        ));
        Long templateId = ((Number) created.get("approvalTemplateId")).longValue();

        Map<String, Object> disabled = approvalService.disableApprovalTemplate(templateId);
        var enabledAfterDisable = approvalService.listApprovalTemplates(1, 10, Map.of("status", "enabled"));
        var disabledList = approvalService.listApprovalTemplates(1, 10, Map.of("status", "disabled"));

        assertThat(disabled)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("status", "disabled");
        assertThat(enabledAfterDisable.items()).isEmpty();
        assertThat(disabledList.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("status", "disabled");

        Map<String, Object> enabled = approvalService.enableApprovalTemplate(templateId);

        assertThat(enabled)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("status", "enabled");
        assertThat(approvalService.listApprovalTemplates(1, 10, Map.of("status", "enabled")).items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("status", "enabled");
        assertThatThrownBy(() -> approvalService.disableApprovalTemplate(9999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approval template not found");
    }

    @Test
    void approvalTemplateListAndDisableExposeRuleUsageImpact() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> createdTemplate = approvalService.createApprovalTemplate(Map.of(
                "name", "Legal review approval",
                "steps", List.of(Map.of(
                        "stepId", "legalManager",
                        "approvalTitle", "Legal manager approval",
                        "assigneeRoles", List.of("legal_manager")
                ))
        ));
        Long templateId = ((Number) createdTemplate.get("approvalTemplateId")).longValue();
        approvalService.create(Map.of(
                "name", "Legal review rule",
                "definition", Map.of(
                        "nodes", List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of(
                                        "id", "tpl" + templateId + "_legalManager",
                                        "type", "approval",
                                        "approvalTitle", "Legal manager approval",
                                        "assigneeRoles", List.of("legal_manager")
                                ),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", List.of(
                                Map.of("source", "start", "target", "tpl" + templateId + "_legalManager"),
                                Map.of("source", "tpl" + templateId + "_legalManager", "target", "end")
                        )
                )
        ));

        var listed = approvalService.listApprovalTemplates(1, 10, Map.of("status", "enabled"));
        Map<String, Object> disabled = approvalService.disableApprovalTemplate(templateId);

        assertThat(listed.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("usageCount", 1)
                .extracting("usageRules")
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", 1L)
                .containsEntry("name", "Legal review rule")
                .containsEntry("status", "draft");
        assertThat(disabled)
                .containsEntry("usageCount", 1)
                .containsKey("impact");
        assertThat(disabled.get("impact"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("usageCount", 1);
    }

    @Test
    void approvalTemplateUsageRecognizesExplicitVersionReferencesOnRuleNodes() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> createdTemplate = approvalService.createApprovalTemplate(Map.of(
                "name", "Board approval",
                "steps", List.of(Map.of(
                        "stepId", "boardReview",
                        "approvalTitle", "Board review",
                        "assigneeRoles", List.of("board_secretary")
                ))
        ));
        Long templateId = ((Number) createdTemplate.get("approvalTemplateId")).longValue();
        approvalService.create(Map.of(
                "name", "Explicit template reference rule",
                "definition", Map.of(
                        "nodes", List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of(
                                        "id", "approvalFromTemplate",
                                        "type", "approval",
                                        "approvalTitle", "Board review",
                                        "assigneeRoles", List.of("board_secretary"),
                                        "approvalTemplateId", templateId,
                                        "approvalTemplateVersion", 1,
                                        "approvalTemplateStepId", "boardReview"
                                ),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", List.of(
                                Map.of("source", "start", "target", "approvalFromTemplate"),
                                Map.of("source", "approvalFromTemplate", "target", "end")
                        )
                )
        ));

        var listed = approvalService.listApprovalTemplates(1, 10, Map.of("status", "enabled"));

        assertThat(listed.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("usageCount", 1)
                .extracting("usageRules")
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", 1L)
                .containsEntry("name", "Explicit template reference rule");
    }

    @Test
    void approvalTemplateUsageCanBeQueriedWithPagination() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> createdTemplate = approvalService.createApprovalTemplate(Map.of(
                "name", "Paginated approval",
                "steps", List.of(Map.of(
                        "stepId", "riskReview",
                        "approvalTitle", "Risk review",
                        "assigneeRoles", List.of("risk_manager")
                ))
        ));
        Long templateId = ((Number) createdTemplate.get("approvalTemplateId")).longValue();
        approvalService.create(Map.of(
                "name", "First referenced rule",
                "definition", approvalTemplateReferenceDefinition(templateId, "firstApproval")
        ));
        approvalService.create(Map.of(
                "name", "Second referenced rule",
                "definition", approvalTemplateReferenceDefinition(templateId, "secondApproval")
        ));

        var usage = approvalService.listApprovalTemplateUsage(templateId, 2, 1);

        assertThat(usage.page()).isEqualTo(2);
        assertThat(usage.pageSize()).isEqualTo(1);
        assertThat(usage.total()).isEqualTo(2);
        assertThat(usage.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("ruleId", 1L)
                .containsEntry("name", "First referenced rule")
                .containsEntry("status", "draft");
    }

    @Test
    void approvalTemplateEditsCreateImmutableVersionSnapshots() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> createdTemplate = approvalService.createApprovalTemplate(Map.of(
                "name", "Finance approval",
                "description", "Initial finance approval path",
                "steps", List.of(Map.of(
                        "stepId", "financeManager",
                        "approvalTitle", "Finance manager approval",
                        "assigneeRoles", List.of("finance_manager")
                ))
        ));
        Long templateId = ((Number) createdTemplate.get("approvalTemplateId")).longValue();
        approvalService.create(Map.of(
                "name", "Rule using v1 template",
                "definition", approvalTemplateReferenceDefinition(templateId, "financeApproval")
        ));

        Map<String, Object> updatedTemplate = approvalService.updateApprovalTemplate(templateId, Map.of(
                "name", "Finance approval updated",
                "description", "Adds finance director",
                "status", "enabled",
                "steps", List.of(
                        Map.of(
                                "stepId", "financeManager",
                                "approvalTitle", "Finance manager approval",
                                "assigneeRoles", List.of("finance_manager")
                        ),
                        Map.of(
                                "stepId", "financeDirector",
                                "approvalTitle", "Finance director approval",
                                "assigneeRoles", List.of("finance_director"),
                                "approvalMode", "all",
                                "slaHours", 8
                        )
                )
        ));
        Map<String, Object> versionOne = approvalService.getApprovalTemplateVersion(templateId, 1);
        Map<String, Object> versionTwo = approvalService.getApprovalTemplateVersion(templateId, 2);
        List<Map<String, Object>> versions = approvalService.listApprovalTemplateVersions(templateId);

        assertThat(updatedTemplate)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("name", "Finance approval updated")
                .containsEntry("version", 2);
        assertThat(versionOne)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("version", 1)
                .containsEntry("name", "Finance approval");
        assertThat(versionOne.get("steps"))
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("stepId", "financeManager");
        assertThat(versionTwo)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("version", 2)
                .containsEntry("name", "Finance approval updated");
        assertThat(versionTwo.get("steps")).asList().hasSize(2);
        assertThat(versions)
                .extracting(version -> version.get("version"))
                .containsExactly(2, 1);
        assertThat(versions.get(0))
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("name", "Finance approval updated");
        assertThat(versions.get(1))
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("name", "Finance approval");
        assertThat(approvalService.listApprovalTemplateUsage(templateId, 1, 10).items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("name", "Rule using v1 template");
    }

    @Test
    void approvalTemplateVersionsCanBeComparedByStepId() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> createdTemplate = approvalService.createApprovalTemplate(Map.of(
                "name", "Legal approval",
                "steps", List.of(
                        Map.of(
                                "stepId", "legalManager",
                                "approvalTitle", "Legal manager approval",
                                "assigneeRoles", List.of("legal_manager"),
                                "slaHours", 6
                        ),
                        Map.of(
                                "stepId", "complianceReview",
                                "approvalTitle", "Compliance review",
                                "assigneeRoles", List.of("compliance_manager")
                        )
                )
        ));
        Long templateId = ((Number) createdTemplate.get("approvalTemplateId")).longValue();
        approvalService.updateApprovalTemplate(templateId, Map.of(
                "name", "Legal approval updated",
                "steps", List.of(
                        Map.of(
                                "stepId", "legalManager",
                                "approvalTitle", "Legal director approval",
                                "assigneeRoles", List.of("legal_director"),
                                "slaHours", 12
                        ),
                        Map.of(
                                "stepId", "cfoApproval",
                                "approvalTitle", "CFO approval",
                                "assigneeRoles", List.of("cfo")
                        )
                )
        ));

        Map<String, Object> diff = approvalService.compareApprovalTemplateVersions(templateId, 1, 2);

        assertThat(diff)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("baseVersion", 1)
                .containsEntry("targetVersion", 2);
        assertThat(diff.get("summary"))
                .isEqualTo(Map.of("added", 1, "removed", 1, "modified", 1, "unchanged", 0));
        assertThat((List<Map<String, Object>>) diff.get("changes"))
                .extracting(change -> change.get("changeType"))
                .containsExactly("modified", "removed", "added");
        assertThat((List<Map<String, Object>>) diff.get("changes"))
                .extracting(change -> change.get("stepId"))
                .containsExactly("legalManager", "complianceReview", "cfoApproval");
    }

    @Test
    void approvalTemplateRollbackCreatesNewCurrentVersionFromSnapshot() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> createdTemplate = approvalService.createApprovalTemplate(Map.of(
                "name", "Finance rollback approval",
                "description", "Original path",
                "steps", List.of(Map.of(
                        "stepId", "financeManager",
                        "approvalTitle", "Finance manager approval",
                        "assigneeRoles", List.of("finance_manager")
                ))
        ));
        Long templateId = ((Number) createdTemplate.get("approvalTemplateId")).longValue();
        approvalService.updateApprovalTemplate(templateId, Map.of(
                "name", "Finance rollback approval changed",
                "description", "Changed path",
                "steps", List.of(Map.of(
                        "stepId", "financeDirector",
                        "approvalTitle", "Finance director approval",
                        "assigneeRoles", List.of("finance_director")
                ))
        ));

        Map<String, Object> rollback = approvalService.rollbackApprovalTemplateVersion(templateId, 1);
        Map<String, Object> current = approvalService.getApprovalTemplateVersion(templateId, 3);

        assertThat(rollback)
                .containsEntry("approvalTemplateId", templateId)
                .containsEntry("sourceVersion", 1)
                .containsEntry("newVersion", 3)
                .containsEntry("currentVersion", 3)
                .containsEntry("changeReason", "rollback");
        assertThat(current)
                .containsEntry("version", 3)
                .containsEntry("name", "Finance rollback approval")
                .containsEntry("description", "Original path");
        assertThat(current.get("steps"))
                .asList()
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("stepId", "financeManager");
        assertThat(approvalService.listApprovalTemplateVersions(templateId))
                .extracting(version -> version.get("version"))
                .containsExactly(3, 2, 1);
    }

    @Test
    void expiredDelegateRuleDoesNotApplyToNewApprovalRecords() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", OffsetDateTime.now().minusHours(3).toString(),
                "activeTo", OffsetDateTime.now().minusHours(1).toString(),
                "reason", "expired vacation cover"
        ));
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager");

        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> approvalRecord = approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(approvalRecord)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", null)
                .containsEntry("delegateActiveFrom", null)
                .containsEntry("delegateActiveTo", null);
        try {
            CurrentUserHolder.set(new CurrentUser(88L, Set.of("finance_delegate"), Set.of("rule:debug")));

            assertThatThrownBy(() -> approvalService.handleApprovalRecord(
                    ruleId,
                    ((Number) approvalRecord.get("approvalRecordId")).longValue(),
                    Map.of("action", "approve", "comment", "expired delegate rule should not work")
            ))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("approval record access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void delegateRuleOnlyAppliesOnConfiguredWeekdays() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String tomorrow = OffsetDateTime.now().plusDays(1).getDayOfWeek().name();
        approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", OffsetDateTime.now().minusHours(1).toString(),
                "activeTo", OffsetDateTime.now().plusHours(1).toString(),
                "activeWeekdays", List.of(tomorrow),
                "reason", "weekday cover only"
        ));
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager");

        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 93)));
        Map<String, Object> approvalRecord = approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(approvalRecord)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", null)
                .containsEntry("delegateActiveFrom", null)
                .containsEntry("delegateActiveTo", null);
    }

    @Test
    void delegateRuleOnlyAppliesOnConfiguredActiveDates() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        Map<String, Object> tomorrowOnlyRule = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", OffsetDateTime.now().minusHours(1).toString(),
                "activeTo", OffsetDateTime.now().plusHours(1).toString(),
                "activeDates", List.of(tomorrow),
                "reason", "specific date cover only"
        ));
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager");

        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 93)));
        Map<String, Object> unmatchedApprovalRecord = approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0);
        Long delegateRuleId = ((Number) tomorrowOnlyRule.get("delegateRuleId")).longValue();
        Map<String, Object> todayRule = approvalService.updateApprovalDelegateRule(delegateRuleId, Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", OffsetDateTime.now().minusHours(1).toString(),
                "activeTo", OffsetDateTime.now().plusHours(1).toString(),
                "activeDates", List.of(today),
                "reason", "today cover"
        ));
        Long todayRuleId = createPublishedApprovalRule(approvalService, "finance_manager");
        approvalService.execute(todayRuleId, Map.of("sample", Map.of("riskScore", 94)));
        Map<String, Object> matchedApprovalRecord = approvalService.listApprovalRecords(todayRuleId, 1, 10).items().get(0);

        assertThat(tomorrowOnlyRule).containsEntry("activeDates", List.of(tomorrow));
        assertThat(unmatchedApprovalRecord)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", null)
                .containsEntry("delegateActiveFrom", null)
                .containsEntry("delegateActiveTo", null);
        assertThat(todayRule).containsEntry("activeDates", List.of(today));
        assertThat(matchedApprovalRecord)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", "finance_delegate");
    }

    @Test
    void rejectsApprovalDelegateRuleScheduleConflictsForSameAssigneeRole() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", "2026-06-29T08:00:00Z",
                "activeTo", "2026-06-29T18:00:00Z",
                "activeDates", List.of(today),
                "reason", "primary cover"
        ));

        assertThatThrownBy(() -> approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_backup",
                "activeFrom", "2026-06-29T12:00:00Z",
                "activeTo", "2026-06-29T20:00:00Z",
                "activeDates", List.of(today),
                "reason", "conflicting cover"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approval delegate rule schedule conflicts");

        Map<String, Object> nonConflictingDate = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_tomorrow_delegate",
                "activeFrom", "2026-06-29T12:00:00Z",
                "activeTo", "2026-06-29T20:00:00Z",
                "activeDates", List.of(tomorrow),
                "reason", "tomorrow cover"
        ));
        Map<String, Object> nonConflictingRole = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "legal_manager",
                "delegateRole", "legal_delegate",
                "activeFrom", "2026-06-29T12:00:00Z",
                "activeTo", "2026-06-29T20:00:00Z",
                "activeDates", List.of(today),
                "reason", "different role cover"
        ));

        assertThat(nonConflictingDate)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("activeDates", List.of(tomorrow));
        assertThat(nonConflictingRole)
                .containsEntry("assigneeRole", "legal_manager")
                .containsEntry("activeDates", List.of(today));
    }

    @Test
    void rejectsApprovalDelegateRuleUpdateScheduleConflictsForSameAssigneeRole() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", "2026-06-29T08:00:00Z",
                "activeTo", "2026-06-29T18:00:00Z",
                "activeDates", List.of(today),
                "reason", "primary cover"
        ));
        Map<String, Object> editable = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_tomorrow_delegate",
                "activeFrom", "2026-06-29T08:00:00Z",
                "activeTo", "2026-06-29T18:00:00Z",
                "activeDates", List.of(tomorrow),
                "reason", "tomorrow cover"
        ));
        Long editableId = ((Number) editable.get("delegateRuleId")).longValue();

        assertThatThrownBy(() -> approvalService.updateApprovalDelegateRule(editableId, Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_tomorrow_delegate",
                "activeFrom", "2026-06-29T12:00:00Z",
                "activeTo", "2026-06-29T20:00:00Z",
                "activeDates", List.of(today),
                "reason", "conflicting edit"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approval delegate rule schedule conflicts");
    }

    @Test
    void batchImportsApprovalDelegateRulesWithRowResults() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String today = LocalDate.now().toString();

        Map<String, Object> result = approvalService.batchImportApprovalDelegateRules(Map.of(
                "rules", List.of(
                        Map.of(
                                "assigneeRole", "finance_manager",
                                "delegateRole", "finance_delegate",
                                "activeFrom", "2026-06-29T08:00:00Z",
                                "activeTo", "2026-06-29T18:00:00Z",
                                "activeWeekdays", List.of("MONDAY", "WEDNESDAY"),
                                "activeDates", List.of(today),
                                "reason", "quarter close cover"
                        ),
                        Map.of(
                                "assigneeRole", "finance_manager",
                                "delegateRole", "finance_backup",
                                "activeFrom", "2026-06-29T12:00:00Z",
                                "activeTo", "2026-06-29T20:00:00Z",
                                "activeDates", List.of(today),
                                "reason", "conflicting cover"
                        ),
                        Map.of(
                                "assigneeRole", "legal_manager",
                                "delegateRole", "legal_delegate",
                                "activeFrom", "2026-06-30T08:00:00Z",
                                "activeTo", "2026-06-30T18:00:00Z",
                                "activeDates", List.of(LocalDate.now().plusDays(1).toString()),
                                "reason", "legal cover"
                        ),
                        Map.of(
                                "assigneeRole", "",
                                "delegateRole", "missing_assignee"
                        )
                )
        ));

        assertThat(result)
                .containsEntry("imported", 2)
                .containsEntry("failed", 2);
        assertThat(result.get("results"))
                .asList()
                .hasSize(4)
                .element(0)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rowNumber", 1)
                .containsEntry("status", "imported")
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", "finance_delegate");
        assertThat(result.get("results"))
                .asList()
                .element(1)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rowNumber", 2)
                .containsEntry("status", "failed")
                .extractingByKey("reason")
                .asString()
                .contains("approval delegate rule schedule conflicts");
        assertThat(result.get("results"))
                .asList()
                .element(3)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("rowNumber", 4)
                .containsEntry("status", "failed")
                .extractingByKey("reason")
                .asString()
                .contains("assigneeRole is required");
        assertThat(approvalService.listApprovalDelegateRules(1, 10, Map.of()).total()).isEqualTo(2);
    }

    @Test
    void listsAndDisablesApprovalDelegateRulesForOperations() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String activeFrom = OffsetDateTime.now().minusHours(1).toString();
        String activeTo = OffsetDateTime.now().plusHours(1).toString();
        Map<String, Object> created = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", activeFrom,
                "activeTo", activeTo,
                "reason", "quarter close cover"
        ));
        Long delegateRuleId = ((Number) created.get("delegateRuleId")).longValue();

        var listedBeforeDisable = approvalService.listApprovalDelegateRules(1, 10, Map.of("assigneeRole", "finance_manager"));
        Map<String, Object> disabled = approvalService.disableApprovalDelegateRule(delegateRuleId, Map.of("reason", "manager returned"));
        var listedAfterDisable = approvalService.listApprovalDelegateRules(1, 10, Map.of("status", "disabled"));
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager");
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> approvalRecord = approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(listedBeforeDisable.total()).isEqualTo(1);
        assertThat(listedBeforeDisable.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", "finance_delegate")
                .containsEntry("status", "enabled");
        assertThat(disabled)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("status", "disabled")
                .containsEntry("reason", "manager returned");
        assertThat(listedAfterDisable.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("status", "disabled");
        assertThat(approvalRecord)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", null);
    }

    @Test
    void reEnablesApprovalDelegateRulesForOperations() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String activeFrom = OffsetDateTime.now().minusHours(1).toString();
        String activeTo = OffsetDateTime.now().plusHours(1).toString();
        Map<String, Object> created = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", activeFrom,
                "activeTo", activeTo,
                "reason", "quarter close cover"
        ));
        Long delegateRuleId = ((Number) created.get("delegateRuleId")).longValue();

        approvalService.disableApprovalDelegateRule(delegateRuleId, Map.of("reason", "manager returned"));
        Map<String, Object> enabled = approvalService.enableApprovalDelegateRule(delegateRuleId, Map.of("reason", "manager away again"));
        var listedAfterEnable = approvalService.listApprovalDelegateRules(1, 10, Map.of("status", "enabled"));
        Long ruleId = createPublishedApprovalRule(approvalService, "finance_manager");
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> approvalRecord = approvalService.listApprovalRecords(ruleId, 1, 10).items().get(0);

        assertThat(enabled)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("status", "enabled")
                .containsEntry("reason", "manager away again");
        assertThat(listedAfterEnable.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("status", "enabled");
        assertThat(approvalRecord)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", "finance_delegate");
    }

    @Test
    void updatesApprovalDelegateRulesForOperations() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Map<String, Object> created = approvalService.createApprovalDelegateRule(Map.of(
                "assigneeRole", "finance_manager",
                "delegateRole", "finance_delegate",
                "activeFrom", OffsetDateTime.now().minusHours(1).toString(),
                "activeTo", OffsetDateTime.now().plusHours(1).toString(),
                "reason", "quarter close cover"
        ));
        Long delegateRuleId = ((Number) created.get("delegateRuleId")).longValue();
        String updatedFrom = OffsetDateTime.now().minusMinutes(10).toString();
        String updatedTo = OffsetDateTime.now().plusHours(2).toString();

        Map<String, Object> updated = approvalService.updateApprovalDelegateRule(delegateRuleId, Map.of(
                "assigneeRole", "finance_director",
                "delegateRole", "finance_director_delegate",
                "activeFrom", updatedFrom,
                "activeTo", updatedTo,
                "reason", "director travel cover"
        ));
        Long originalRoleRuleId = createPublishedApprovalRule(approvalService, "finance_manager");
        approvalService.execute(originalRoleRuleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> originalRoleApproval = approvalService.listApprovalRecords(originalRoleRuleId, 1, 10).items().get(0);
        Long updatedRoleRuleId = createPublishedApprovalRule(approvalService, "finance_director");
        approvalService.execute(updatedRoleRuleId, Map.of("sample", Map.of("riskScore", 91)));
        Map<String, Object> updatedRoleApproval = approvalService.listApprovalRecords(updatedRoleRuleId, 1, 10).items().get(0);

        assertThat(updated)
                .containsEntry("delegateRuleId", delegateRuleId)
                .containsEntry("assigneeRole", "finance_director")
                .containsEntry("delegateRole", "finance_director_delegate")
                .containsEntry("activeFrom", updatedFrom)
                .containsEntry("activeTo", updatedTo)
                .containsEntry("status", "enabled")
                .containsEntry("reason", "director travel cover");
        assertThat(originalRoleApproval)
                .containsEntry("assigneeRole", "finance_manager")
                .containsEntry("delegateRole", null);
        assertThat(updatedRoleApproval)
                .containsEntry("assigneeRole", "finance_director")
                .containsEntry("delegateRole", "finance_director_delegate");
    }

    @Test
    void delegateRoleUserCannotApproveOutsideDelegateActiveWindow() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        String activeFrom = OffsetDateTime.now().minusHours(3).toString();
        String activeTo = OffsetDateTime.now().minusHours(1).toString();
        Long ruleId = createPublishedApprovalRule(
                approvalService,
                "finance_manager",
                null,
                "finance_delegate",
                activeFrom,
                activeTo
        );
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        try {
            CurrentUserHolder.set(new CurrentUser(88L, Set.of("finance_delegate"), Set.of("rule:debug")));

            assertThatThrownBy(() -> approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "late delegated approval"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("approval record access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void assigneeRoleUserCannotHandleOtherRolePendingApprovalRecord() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository()
        );
        Long ruleId = createPublishedApprovalRule(approvalService, "legal_manager");
        approvalService.execute(ruleId, Map.of("sample", Map.of("riskScore", 91)));
        Long approvalRecordId = ((Number) approvalService.listApprovalRecords(ruleId, 1, 10)
                .items()
                .get(0)
                .get("approvalRecordId")).longValue();

        try {
            CurrentUserHolder.set(new CurrentUser(77L, Set.of("finance_manager"), Set.of("rule:debug")));

            assertThatThrownBy(() -> approvalService.handleApprovalRecord(ruleId, approvalRecordId, Map.of(
                    "action", "approve",
                    "comment", "not my queue"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("approval record access denied");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService approvalService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository
        );
        Long firstRuleId = createPublishedApprovalRule(approvalService);
        Long secondRuleId = createPublishedApprovalRule(approvalService);
        approvalService.execute(firstRuleId, Map.of("sample", Map.of("riskScore", 91)));
        approvalService.execute(secondRuleId, Map.of("sample", Map.of("riskScore", 92)));
        Long firstApprovalRecordId = ((Number) approvalService.listApprovalRecords(firstRuleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();
        Long secondApprovalRecordId = ((Number) approvalService.listApprovalRecords(secondRuleId, 1, 10).items().get(0).get("approvalRecordId")).longValue();

        try {
            CurrentUserHolder.set(new CurrentUser(66L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            Map<String, Object> result = approvalService.batchHandleApprovalRecords(Map.of(
                    "action", "approve",
                    "approvalRecordIds", java.util.List.of(firstApprovalRecordId, secondApprovalRecordId),
                    "comment", "approved in batch"
            ));

            assertThat(result)
                    .containsEntry("action", "approve")
                    .containsEntry("requestedCount", 2)
                    .containsEntry("succeededCount", 2L)
                    .containsEntry("failedCount", 0L);
            assertThat((java.util.List<Map<String, Object>>) result.get("items"))
                    .allSatisfy(item -> assertThat(item)
                            .containsEntry("result", "succeeded")
                            .containsEntry("status", "approved")
                            .containsEntry("approvedByUserId", 66L)
                            .containsEntry("approvalComment", "approved in batch"));
            assertThat(auditRepository.logs)
                    .extracting(OperationLog::operationType)
                    .contains("rule_node_approval_approved", "rule_approval_batch_approve");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void writesAuditLogsForReviewApprovalAndProductionRun() {
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService auditedService = new RuleApplicationService(
                new RuleDomainService(),
                new InMemoryRuleRepository(),
                auditRepository
        );
        try {
            CurrentUserHolder.set(new CurrentUser(88L, Set.of("rule-manager"), Set.of("rule:manage", "rule:debug")));
            Map<String, Object> created = auditedService.create(Map.of(
                    "name", "Audited production rule",
                    "definition", Map.of(
                            "nodes", java.util.List.of(
                                    Map.of("id", "start", "type", "start"),
                                    Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                    Map.of("id", "end", "type", "end")
                            ),
                            "edges", java.util.List.of(
                                    Map.of("source", "start", "target", "aging"),
                                    Map.of("source", "aging", "target", "end")
                            )
                    )
            ));
            Long ruleId = ((Number) created.get("ruleId")).longValue();
            auditedService.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));

            auditedService.submitForReview(ruleId, Map.of("comment", "ready for approval"));
            auditedService.approve(ruleId, Map.of("comment", "approved for production"));
            auditedService.execute(ruleId, Map.of("sample", Map.of("daysOverdue", 45)));

            assertThat(auditRepository.logs)
                    .extracting(OperationLog::operationType)
                    .containsExactly("rule_review_submit", "rule_publish_approve", "rule_production_run");
            assertThat(auditRepository.logs)
                    .allSatisfy(log -> {
                        assertThat(log.actorUserId()).isEqualTo(88L);
                        assertThat(log.resourceType()).isEqualTo("rule");
                        assertThat(log.resourceId()).isEqualTo(ruleId);
                        assertThat(log.result()).isEqualTo("succeeded");
                    });
            assertThat(auditRepository.logs.get(0).detail())
                    .containsEntry("status", "pending_review")
                    .containsEntry("comment", "ready for approval");
            assertThat(auditRepository.logs.get(1).detail())
                    .containsEntry("status", "published")
                    .containsEntry("comment", "approved for production");
            assertThat(auditRepository.logs.get(2).detail())
                    .containsEntry("runType", "production")
                    .containsEntry("matched", true);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void listsProductionRunHistoryForRuleMonitoring() {
        try {
            CurrentUserHolder.set(new CurrentUser(101L, Set.of("rule-manager"), Set.of("rule:run")));
            Map<String, Object> created = service.create(Map.of(
                    "name", "Monitored production rule",
                    "definition", Map.of(
                            "nodes", java.util.List.of(
                                    Map.of("id", "start", "type", "start"),
                                    Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                    Map.of("id", "end", "type", "end")
                            ),
                            "edges", java.util.List.of(
                                    Map.of("source", "start", "target", "aging"),
                                    Map.of("source", "aging", "target", "end")
                            )
                    )
            ));
            Long ruleId = ((Number) created.get("ruleId")).longValue();
            service.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
            service.submitForReview(ruleId, Map.of("comment", "ready"));
            service.approve(ruleId, Map.of("comment", "approved"));
            service.execute(ruleId, Map.of("sample", Map.of("daysOverdue", 45)));

            var runs = service.listRuns(ruleId, 1, 10);

            assertThat(runs.total()).isEqualTo(1);
            assertThat(runs.items())
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("ruleId", ruleId)
                    .containsEntry("versionId", 1L)
                    .containsEntry("status", "succeeded")
                    .containsEntry("runType", "production")
                    .containsEntry("matched", true)
                    .containsEntry("triggeredByUserId", 101L)
                    .containsEntry("errorMessage", null)
                    .containsKey("input")
                    .containsKey("output")
                    .extractingByKey("durationMs")
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LONG)
                    .isGreaterThanOrEqualTo(0L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void excludesDebugRunsFromProductionRunHistory() {
        Map<String, Object> created = service.create(Map.of(
                "name", "Separated run type rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        service.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
        service.debug(ruleId, Map.of("sample", Map.of("daysOverdue", 12)));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));
        service.execute(ruleId, Map.of("sample", Map.of("daysOverdue", 45)));

        var runs = service.listRuns(ruleId, 1, 10);

        assertThat(runs.total()).isEqualTo(1);
        assertThat(runs.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("runType", "production")
                .containsEntry("matched", true);
    }

    @Test
    void recordsFailedProductionRunsForRuleMonitoring() {
        try {
            CurrentUserHolder.set(new CurrentUser(102L, Set.of("rule-manager"), Set.of("rule:run")));
            Map<String, Object> created = service.create(Map.of(
                    "name", "Failed production rule",
                    "definition", Map.of(
                            "nodes", java.util.List.of(
                                    Map.of("id", "start", "type", "start"),
                                    Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                    Map.of("id", "end", "type", "end")
                            ),
                            "edges", java.util.List.of(
                                    Map.of("source", "start", "target", "aging"),
                                    Map.of("source", "aging", "target", "end")
                            )
                    )
            ));
            Long ruleId = ((Number) created.get("ruleId")).longValue();
            service.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
            service.submitForReview(ruleId, Map.of("comment", "ready"));
            service.approve(ruleId, Map.of("comment", "approved"));

            assertThatThrownBy(() -> service.execute(ruleId, Map.of()))
                    .isInstanceOf(BusinessException.class);

            var runs = service.listRuns(ruleId, 1, 10);

            assertThat(runs.total()).isEqualTo(1);
            assertThat(runs.items())
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("runType", "production")
                    .containsEntry("status", "failed")
                    .containsEntry("triggeredByUserId", 102L)
                    .containsEntry("matched", null)
                    .containsEntry("errorMessage", ErrorCode.RULE_VALIDATION_FAILED.message());
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void summarizesProductionRunMetricsForMonitoring() {
        Map<String, Object> created = service.create(Map.of(
                "name", "Metrics production rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        service.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));
        service.execute(ruleId, Map.of("sample", Map.of("daysOverdue", 45)));
        assertThatThrownBy(() -> service.execute(ruleId, Map.of()))
                .isInstanceOf(BusinessException.class);

        Map<String, Object> metrics = service.metrics(ruleId);

        assertThat(metrics)
                .containsEntry("ruleId", ruleId)
                .containsEntry("totalRuns", 2L)
                .containsEntry("succeededRuns", 1L)
                .containsEntry("failedRuns", 1L)
                .containsEntry("successRate", 0.5d)
                .containsEntry("lastStatus", "failed")
                .containsKey("lastErrorMessage");
        assertThat(String.valueOf(metrics.get("lastErrorMessage"))).isNotBlank();
        assertThat(metrics.get("averageDurationMs"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isGreaterThanOrEqualTo(0d);
    }

    @Test
    void metricsAggregatesAllProductionRunsInsteadOfOnlyLatestThousand() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService metricService = new RuleApplicationService(new RuleDomainService(), ruleRepository);
        Map<String, Object> created = metricService.create(Map.of(
                "name", "Large metrics rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(Map.of("source", "start", "target", "end"))
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        ruleRepository.saveDebugRun(RuleDebugRun.failedProduction(ruleId, null, 1L, 1010L, "old failure", Map.of("sample", Map.of())));
        for (int i = 0; i < 1000; i++) {
            ruleRepository.saveDebugRun(RuleDebugRun.succeededProduction(ruleId, null, 1L, 10L, Map.of("sample", Map.of()), Map.of("matched", true)));
        }

        Map<String, Object> metrics = metricService.metrics(ruleId);

        assertThat(metrics)
                .containsEntry("totalRuns", 1001L)
                .containsEntry("succeededRuns", 1000L)
                .containsEntry("failedRuns", 1L)
                .containsEntry("successRate", 1000d / 1001d)
                .containsEntry("lastStatus", "succeeded")
                .containsEntry("lastErrorMessage", "old failure");
        assertThat(metrics.get("averageDurationMs"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isCloseTo(11010d / 1001d, org.assertj.core.data.Offset.offset(0.000001d));
    }

    @Test
    void schedulerRunsDuePublishedRulesAndMovesNextRunForward() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService scheduledService = new RuleApplicationService(new RuleDomainService(), ruleRepository);
        RuleProductionScheduler scheduler = new RuleProductionScheduler(ruleRepository, scheduledService, false);
        Map<String, Object> created = scheduledService.create(Map.of(
                "name", "Scheduled production rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        scheduledService.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
        scheduledService.submitForReview(ruleId, Map.of("comment", "ready"));
        scheduledService.approve(ruleId, Map.of("comment", "approved"));
        Map<String, Object> configured = scheduledService.configureSchedule(ruleId, Map.of(
                "scheduleEnabled", true,
                "scheduleIntervalSeconds", 60,
                "nextRunAt", OffsetDateTime.now().minusMinutes(1).toString(),
                "maxRetryCount", 2,
                "scheduleInput", Map.of("sample", Map.of("daysOverdue", 45))
        ));

        int runs = scheduler.runDueRulesOnce();

        assertThat(configured)
                .containsEntry("scheduleEnabled", true)
                .containsEntry("scheduleIntervalSeconds", 60)
                .containsEntry("failureCount", 0)
                .containsEntry("maxRetryCount", 2);
        assertThat(runs).isEqualTo(1);
        assertThat(scheduledService.listRuns(ruleId, 1, 10).total()).isEqualTo(1);
        assertThat(scheduledService.listRuns(ruleId, 1, 10).items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "succeeded")
                .containsEntry("matched", true);
        assertThat(ruleRepository.findById(ruleId).orElseThrow().nextRunAt()).isAfter(OffsetDateTime.now());
        assertThat(ruleRepository.findById(ruleId).orElseThrow().failureCount()).isZero();
    }

    @Test
    void schedulerSkipsRuleWhenAnotherSchedulerHoldsTheLease() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService scheduledService = new RuleApplicationService(new RuleDomainService(), ruleRepository);
        RuleProductionScheduler scheduler = new RuleProductionScheduler(ruleRepository, scheduledService, false);
        Map<String, Object> created = scheduledService.create(Map.of(
                "name", "Lease guarded rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        scheduledService.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
        scheduledService.submitForReview(ruleId, Map.of("comment", "ready"));
        scheduledService.approve(ruleId, Map.of("comment", "approved"));
        scheduledService.configureSchedule(ruleId, Map.of(
                "scheduleEnabled", true,
                "scheduleIntervalSeconds", 60,
                "nextRunAt", OffsetDateTime.now().minusMinutes(1).toString(),
                "maxRetryCount", 2,
                "scheduleInput", Map.of("sample", Map.of("daysOverdue", 45))
        ));
        assertThat(ruleRepository.tryAcquireRuleScheduleLease(ruleId, OffsetDateTime.now().plusMinutes(5))).isTrue();

        int runs = scheduler.runDueRulesOnce();

        assertThat(runs).isZero();
        assertThat(scheduledService.listRuns(ruleId, 1, 10).total()).isZero();
        assertThat(ruleRepository.findById(ruleId).orElseThrow().failureCount()).isZero();
    }

    @Test
    void schedulerCreatesUnreadSystemAlertWhenScheduledRuleFailsWithoutLeakingInput() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService scheduledService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                alertRepository
        );
        RuleProductionScheduler scheduler = new RuleProductionScheduler(ruleRepository, scheduledService, false);
        Map<String, Object> created = scheduledService.create(Map.of(
                "name", "Failing scheduled rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        scheduledService.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
        scheduledService.submitForReview(ruleId, Map.of("comment", "ready"));
        scheduledService.approve(ruleId, Map.of("comment", "approved"));
        scheduledService.configureSchedule(ruleId, Map.of(
                "scheduleEnabled", true,
                "scheduleIntervalSeconds", 60,
                "nextRunAt", OffsetDateTime.now().minusMinutes(1).toString(),
                "maxRetryCount", 2,
                "scheduleInput", Map.of("sample", Map.of("daysOverdue", 45), "secretPrompt", "do-not-leak")
        ));
        var published = ruleRepository.findById(ruleId).orElseThrow();
        Map<String, Object> invalidDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", "contains", "value", 30),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "aging"),
                        Map.of("source", "aging", "target", "end")
                )
        );
        ruleRepository.save(published.update(
                published.name(),
                published.description(),
                published.status(),
                invalidDefinition,
                published.currentVersionId()
        ));

        int runs = scheduler.runDueRulesOnce();

        assertThat(runs).isEqualTo(1);
        assertThat(scheduledService.listRuns(ruleId, 1, 10).items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "failed")
                .containsEntry("runType", "production");
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.recipientUserId()).isEqualTo(1L);
                    assertThat(alert.type()).isEqualTo("rule_schedule_run_failed");
                    assertThat(alert.severity()).isEqualTo("warning");
                    assertThat(alert.status()).isEqualTo("unread");
                    assertThat(alert.resourceType()).isEqualTo("rule");
                    assertThat(alert.resourceId()).isEqualTo(ruleId);
                    assertThat(alert.payload())
                            .containsEntry("ruleId", ruleId)
                            .containsEntry("ruleName", "Failing scheduled rule")
                            .containsEntry("failureCount", 1)
                            .containsEntry("maxRetryCount", 2)
                            .containsKey("runId")
                            .containsKey("errorMessage");
                    assertThat(alert.payload()).doesNotContainKeys("scheduleInput", "input", "sample", "secretPrompt");
                    assertThat(alert.payload().toString()).doesNotContain("do-not-leak");
                });
    }

    @Test
    void productionRunExecutesNotifyActionAsUnreadSystemAlertWithoutLeakingSample() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                alertRepository
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of("id", "notifyHighRisk", "type", "action", "actionType", "notify", "severity", "warning", "message", "High risk report requires review"),
                        Map.of("id", "archiveLowRisk", "type", "action", "actionType", "archive", "message", "Low risk"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "notifyHighRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "archiveLowRisk", "condition", "false"),
                        Map.of("source", "notifyHighRisk", "target", "end"),
                        Map.of("source", "archiveLowRisk", "target", "end")
                )
        );
        Map<String, Object> created = actionService.create(Map.of(
                "name", "High risk action rule",
                "definition", definition
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        Map<String, Object> executed = actionService.execute(ruleId, Map.of(
                "sample", Map.of("riskScore", 91, "secretPrompt", "do-not-leak")
        ));

        assertThat(executed)
                .containsEntry("status", "succeeded")
                .containsEntry("runType", "production");
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.recipientUserId()).isEqualTo(1L);
                    assertThat(alert.type()).isEqualTo("rule_action_notify");
                    assertThat(alert.severity()).isEqualTo("warning");
                    assertThat(alert.status()).isEqualTo("unread");
                    assertThat(alert.resourceType()).isEqualTo("rule");
                    assertThat(alert.resourceId()).isEqualTo(ruleId);
                    assertThat(alert.payload())
                            .containsEntry("ruleId", ruleId)
                            .containsEntry("ruleName", "High risk action rule")
                            .containsEntry("nodeId", "notifyHighRisk")
                            .containsEntry("actionType", "notify")
                            .containsEntry("message", "High risk report requires review")
                            .containsKey("runId");
                    assertThat(alert.payload()).doesNotContainKeys("input", "sample", "secretPrompt");
                    assertThat(alert.payload().toString()).doesNotContain("do-not-leak");
                });
    }

    @Test
    void productionRunExecutesCreateTaskActionThroughCollaborationServiceWithoutLeakingSample() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryCollaborationRepository collaborationRepository = new InMemoryCollaborationRepository();
        userRepository.save(new UserAccount(1L, "owner", "Owner", "enabled", List.of("analyst")));
        userRepository.save(new UserAccount(2L, "reviewer", "Reviewer", "enabled", List.of("analyst")));
        reportRepository.save(new Report(88L, "Risk report", 1L, ReportStatus.DRAFT, null));
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository(),
                new CollaborationApplicationService(reportRepository, collaborationRepository, userRepository)
        );
        CurrentUserHolder.set(new CurrentUser(1L, Set.of("analyst"), Set.of("rule:write", "collaboration:write")));
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "createReviewTask",
                                "type", "action",
                                "actionType", "create_task",
                                "reportId", 88,
                                "assigneeUserId", 2,
                                "content", "Review high risk evidence",
                                "anchor", Map.of("sectionId", "summary", "startOffset", 0, "endOffset", 9, "selectedText", "High risk")
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "createReviewTask", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "createReviewTask", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "High risk task rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95, "secretPrompt", "do-not-leak")));

        assertThat(collaborationRepository.findTaskById(1L))
                .hasValueSatisfying(task -> assertThat(task)
                        .extracting("reportId", "annotationId", "assigneeUserId", "status")
                        .containsExactly(88L, 1L, 2L, "open"));
    }

    @Test
    void productionRunExecutesWebhookActionWithoutLeakingSample() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeWebhookClient webhookClient = new FakeWebhookClient();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "headers", Map.of("X-System", "report-rule"),
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "High risk webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95, "secretPrompt", "do-not-leak")));

        assertThat(webhookClient.calls)
                .singleElement()
                .satisfies(call -> {
                    assertThat(call)
                            .containsEntry("endpoint", "https://erp.example.com/risk-events")
                            .containsEntry("method", "POST");
                    assertThat(call.get("headers"))
                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                            .containsEntry("X-System", "report-rule")
                            .containsEntry("Idempotency-Key", "rule-" + ruleId + "-run-1-node-writebackRisk");
                    assertThat(call.get("body"))
                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                            .containsEntry("eventType", "high_risk_report")
                            .containsEntry("reportId", 88)
                            .containsEntry("ruleId", ruleId)
                            .containsEntry("ruleName", "High risk webhook rule")
                            .containsEntry("nodeId", "writebackRisk")
                            .containsKey("runId");
                    assertThat(call.toString()).doesNotContain("secretPrompt", "do-not-leak");
                });
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.result()).isEqualTo("succeeded");
                    assertThat(log.resourceType()).isEqualTo("rule");
                    assertThat(log.resourceId()).isEqualTo(ruleId);
                    assertThat(log.detail())
                            .containsEntry("runId", 1L)
                            .containsEntry("nodeId", "writebackRisk")
                            .containsEntry("endpoint", "https://erp.example.com/risk-events")
                            .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk");
                    assertThat(log.detail()).doesNotContainKeys("input", "sample", "secretPrompt");
                    assertThat(log.detail().toString()).doesNotContain("do-not-leak");
                });
    }

    @Test
    void productionRunSignsWebhookActionWithoutLeakingSignatureSecret() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeWebhookClient webhookClient = new FakeWebhookClient();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "signatureSecret", "super-secret-signing-key",
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Signed webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95, "secretPrompt", "do-not-leak")));

        assertThat(webhookClient.calls)
                .singleElement()
                .satisfies(call -> {
                    assertThat(call.get("headers"))
                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                            .containsEntry("X-Signature-Algorithm", "HMAC-SHA256")
                            .containsEntry("X-Signature-Payload", "rule-1-run-1-node-writebackRisk")
                            .containsEntry("X-Signature", "sha256=7d9e8cd0819f73d185d3f921304c221e45fcc59fd54c626b0832bc9af9633e05");
                    assertThat(call.toString()).doesNotContain("super-secret-signing-key", "secretPrompt", "do-not-leak");
                });
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.detail())
                            .containsEntry("signatureAlgorithm", "HMAC-SHA256")
                            .containsEntry("signaturePayload", "rule-1-run-1-node-writebackRisk")
                            .doesNotContainKeys("signatureSecret");
                    assertThat(log.detail().toString()).doesNotContain("super-secret-signing-key", "secretPrompt", "do-not-leak");
                });
    }

    @Test
    void productionRunRetriesWebhookActionWithSameIdempotencyKeyWithoutLeakingSample() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FlakyWebhookClient webhookClient = new FlakyWebhookClient(2);
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 2,
                                "retryBackoffSeconds", 0,
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Retrying webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        Map<String, Object> executed = actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95, "secretPrompt", "do-not-leak")));

        assertThat(executed).containsEntry("status", "succeeded");
        assertThat(webhookClient.calls).hasSize(3);
        assertThat(webhookClient.calls)
                .allSatisfy(call -> assertThat(call.get("headers"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("Idempotency-Key", "rule-" + ruleId + "-run-1-node-writebackRisk"));
        assertThat(webhookClient.calls.toString()).doesNotContain("secretPrompt", "do-not-leak");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook".equals(log.operationType()))
                .hasSize(3)
                .extracting(OperationLog::result)
                .containsExactly("failed", "failed", "succeeded");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook".equals(log.operationType()))
                .extracting(OperationLog::detail)
                .allSatisfy(detail -> {
                    assertThat(detail)
                            .containsEntry("maxRetryCount", 2)
                            .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk");
                    assertThat(detail).containsKey("attempt");
                    assertThat(detail).doesNotContainKeys("input", "sample", "secretPrompt");
                    assertThat(detail.toString()).doesNotContain("do-not-leak");
                });
    }

    @Test
    void productionRunCreatesFailedRunAndAlertWhenWebhookActionFailsWithoutLeakingSample() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        FailingWebhookClient webhookClient = new FailingWebhookClient();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository,
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Failing webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95, "secretPrompt", "do-not-leak"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("erp timeout");

        var runs = actionService.listRuns(ruleId, 1, 10);
        assertThat(runs.items())
                .filteredOn(run -> "failed".equals(run.get("status")))
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("runType", "production")
                .extractingByKey("errorMessage")
                .asString()
                .contains("webhook action failed", "writebackRisk", "erp timeout")
                .doesNotContain("secretPrompt", "do-not-leak");
        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.type()).isEqualTo("rule_action_webhook_failed");
                    assertThat(alert.severity()).isEqualTo("error");
                    assertThat(alert.status()).isEqualTo("unread");
                    assertThat(alert.resourceType()).isEqualTo("rule");
                    assertThat(alert.resourceId()).isEqualTo(ruleId);
                    assertThat(alert.payload())
                            .containsEntry("ruleId", ruleId)
                            .containsEntry("ruleName", "Failing webhook rule")
                            .containsEntry("nodeId", "writebackRisk")
                            .containsEntry("actionType", "webhook")
                            .containsEntry("endpoint", "https://erp.example.com/risk-events")
                            .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk")
                            .containsKey("runId")
                            .containsKey("errorMessage");
                    assertThat(alert.payload()).doesNotContainKeys("input", "sample", "secretPrompt");
                    assertThat(alert.payload().toString()).doesNotContain("do-not-leak");
                });
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.result()).isEqualTo("failed");
                    assertThat(log.resourceType()).isEqualTo("rule");
                    assertThat(log.resourceId()).isEqualTo(ruleId);
                    assertThat(log.detail())
                            .containsEntry("runId", 1L)
                            .containsEntry("nodeId", "writebackRisk")
                            .containsEntry("endpoint", "https://erp.example.com/risk-events")
                            .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk")
                            .containsKey("errorMessage");
                    assertThat(log.detail()).doesNotContainKeys("input", "sample", "secretPrompt");
                    assertThat(log.detail().toString()).doesNotContain("do-not-leak");
                });
    }

    @Test
    void productionRunSuppressesDuplicateUnreadWebhookFailureAlertsForSameRuleNodeAndEndpoint() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FakeSystemAlertRepository alertRepository = new FakeSystemAlertRepository();
        FailingWebhookClient webhookClient = new FailingWebhookClient();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                alertRepository,
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Noisy webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("erp timeout");
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 96))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("erp timeout");

        assertThat(alertRepository.alerts)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.type()).isEqualTo("rule_action_webhook_failed");
                    assertThat(alert.resourceId()).isEqualTo(ruleId);
                    assertThat(alert.payload())
                            .containsEntry("nodeId", "writebackRisk")
                            .containsEntry("endpoint", "https://erp.example.com/risk-events");
                });
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10))
                .filteredOn(action -> "pending_retry".equals(action.status()))
                .hasSize(2);
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook".equals(log.operationType()))
                .hasSize(2);
    }

    @Test
    void productionRunPersistsWebhookActionCompensationLedgerWithoutLeakingSensitiveInput() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository(),
                new FailingWebhookClient()
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 1,
                                "retryBackoffSeconds", 60,
                                "signatureSecret", "super-secret-signing-key",
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Compensated webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));

        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95, "secretPrompt", "do-not-leak"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("erp timeout");

        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10))
                .singleElement()
                .satisfies(execution -> {
                    assertThat(execution.ruleId()).isEqualTo(ruleId);
                    assertThat(execution.runId()).isEqualTo(1L);
                    assertThat(execution.nodeId()).isEqualTo("writebackRisk");
                    assertThat(execution.actionType()).isEqualTo("webhook");
                    assertThat(execution.status()).isEqualTo("pending_retry");
                    assertThat(execution.attempt()).isEqualTo(2);
                    assertThat(execution.maxRetryCount()).isEqualTo(1);
                    assertThat(execution.endpoint()).isEqualTo("https://erp.example.com/risk-events");
                    assertThat(execution.idempotencyKey()).isEqualTo("rule-" + ruleId + "-run-1-node-writebackRisk");
                    assertThat(execution.nextRetryAt()).isNotNull();
                    assertThat(execution.errorMessage()).contains("erp timeout");
                    assertThat(execution.metadata())
                            .containsEntry("method", "POST")
                            .containsEntry("retryBackoffSeconds", 60)
                            .containsEntry("signatureAlgorithm", "HMAC-SHA256")
                            .doesNotContainKeys("input", "sample", "secretPrompt", "signatureSecret");
                    assertThat(execution.toString()).doesNotContain("do-not-leak", "super-secret-signing-key");
                });
    }

    @Test
    void manualRetryReplaysPendingWebhookActionExecutionAndUpdatesLedger() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FlakyWebhookClient webhookClient = new FlakyWebhookClient(1);
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 0,
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Manual replay webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95))))
                .isInstanceOf(IllegalStateException.class);
        Long executionId = ruleRepository.findActionExecutions(ruleId, 1, 10).get(0).id();

        Map<String, Object> retried = actionService.retryWebhookActionExecution(ruleId, executionId);

        assertThat(retried)
                .containsEntry("ruleId", ruleId)
                .containsEntry("sourceActionExecutionId", executionId)
                .containsEntry("status", "succeeded")
                .containsEntry("attempt", 2)
                .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk");
        assertThat(((Number) retried.get("actionExecutionId")).longValue()).isGreaterThan(executionId);
        assertThat(webhookClient.calls).hasSize(2);
        assertThat(webhookClient.calls.get(1).get("headers"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("Idempotency-Key", "rule-" + ruleId + "-run-1-node-writebackRisk");
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10))
                .extracting("status")
                .containsExactly("succeeded", "pending_retry");
        assertThat(actionService.listActionExecutions(ruleId, 1, 10).items())
                .filteredOn(item -> "succeeded".equals(item.get("status")))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("sourceActionExecutionId", executionId)
                        .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk"));
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook_retry".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.result()).isEqualTo("succeeded");
                    assertThat(log.detail())
                            .containsEntry("sourceActionExecutionId", executionId)
                            .containsEntry("attempt", 2)
                            .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk");
                });
    }

    @Test
    void listsWebhookActionExecutionsForManualCompensationWithoutLeakingSensitiveInput() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                new InMemoryAuditRepository(),
                new FakeSystemAlertRepository(),
                new FailingWebhookClient()
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 0,
                                "signatureSecret", "super-secret-signing-key",
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Action ledger list rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95, "secretPrompt", "do-not-leak"))))
                .isInstanceOf(IllegalStateException.class);

        var page = actionService.listActionExecutions(ruleId, 1, 10);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item)
                            .containsEntry("ruleId", ruleId)
                            .containsEntry("runId", 1L)
                            .containsEntry("nodeId", "writebackRisk")
                            .containsEntry("actionType", "webhook")
                            .containsEntry("status", "pending_retry")
                            .containsEntry("attempt", 1)
                            .containsEntry("maxRetryCount", 0)
                            .containsEntry("endpoint", "https://erp.example.com/risk-events")
                            .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk")
                            .containsKey("actionExecutionId")
                            .containsKey("nextRetryAt")
                            .containsKey("errorMessage")
                            .containsKey("metadata");
                    assertThat(item).doesNotContainKeys("input", "sample", "secretPrompt", "signatureSecret");
                    assertThat(item.toString()).doesNotContain("do-not-leak", "super-secret-signing-key");
                });
    }

    @Test
    void webhookActionReplayWorkerProcessesDuePendingExecutionWithLease() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FlakyWebhookClient webhookClient = new FlakyWebhookClient(1);
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 0,
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Async replay webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95))))
                .isInstanceOf(IllegalStateException.class);
        Long pendingExecutionId = ruleRepository.findActionExecutions(ruleId, 1, 10).get(0).id();
        RuleWebhookActionReplayWorker worker = new RuleWebhookActionReplayWorker(ruleRepository, actionService, false);

        int processed = worker.runDueReplaysOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(webhookClient.calls).hasSize(2);
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10))
                .extracting("status")
                .containsExactly("succeeded", "pending_retry");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook_retry".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("sourceActionExecutionId", pendingExecutionId)
                        .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk"));
    }

    @Test
    void webhookActionReplayWorkerKeepsFailedReplayPendingForNextAttempt() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                new FailingWebhookClient()
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 0,
                                "retryBackoffSeconds", 0,
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Failed async replay webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95))))
                .isInstanceOf(IllegalStateException.class);
        RuleWebhookActionReplayWorker worker = new RuleWebhookActionReplayWorker(ruleRepository, actionService, false);

        int processed = worker.runDueReplaysOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10))
                .hasSize(2)
                .allSatisfy(execution -> {
                    assertThat(execution.status()).isEqualTo("pending_retry");
                    assertThat(execution.nextRetryAt()).isNotNull();
                    assertThat(execution.errorMessage()).contains("erp timeout");
                });
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook_retry".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.result()).isEqualTo("failed");
                    assertThat(log.detail()).containsEntry("attempt", 2);
                });
    }

    @Test
    void webhookActionReplayWorkerExhaustsActionAfterMaxAsyncReplayAttempts() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FailingWebhookClient webhookClient = new FailingWebhookClient();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 0,
                                "retryBackoffSeconds", 0,
                                "maxAsyncReplayAttempts", 1,
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Bounded async replay webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95))))
                .isInstanceOf(IllegalStateException.class);
        Long originalPendingExecutionId = ruleRepository.findActionExecutions(ruleId, 1, 10).get(0).id();
        RuleWebhookActionReplayWorker worker = new RuleWebhookActionReplayWorker(ruleRepository, actionService, false);
        assertThat(worker.runDueReplaysOnce()).isEqualTo(1);
        int callsAfterAllowedReplay = webhookClient.calls;

        int processed = worker.runDueReplaysOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(worker.runDueReplaysOnce()).isZero();
        assertThat(webhookClient.calls).isEqualTo(callsAfterAllowedReplay);
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10))
                .extracting("status")
                .containsExactly("compensation_exhausted", "pending_retry", "pending_retry");
        assertThat(actionService.listActionExecutions(ruleId, 1, 10).items())
                .filteredOn(item -> "compensation_exhausted".equals(item.get("status")))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("sourceActionExecutionId", originalPendingExecutionId)
                        .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk"));
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook_replay_exhausted".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.result()).isEqualTo("failed");
                    assertThat(log.detail())
                            .containsEntry("sourceActionExecutionId", originalPendingExecutionId)
                            .containsEntry("attempt", 2)
                            .containsEntry("maxAsyncReplayAttempts", 1)
                       .containsEntry("idempotencyKey", "rule-" + ruleId + "-run-1-node-writebackRisk");
           });
    }

    @Test
    void webhookActionReplayWorkerPublishesOperationalMetrics() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FailingWebhookClient webhookClient = new FailingWebhookClient();
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 0,
                                "retryBackoffSeconds", 0,
                                "maxAsyncReplayAttempts", 1,
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Measured async replay webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95))))
                .isInstanceOf(IllegalStateException.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RuleWebhookActionReplayWorker worker = new RuleWebhookActionReplayWorker(ruleRepository, actionService, false, meterRegistry);

        assertThat(worker.runDueReplaysOnce()).isEqualTo(1);
        assertThat(worker.runDueReplaysOnce()).isEqualTo(1);

        assertThat(meterRegistry.counter("rule.webhook.replay.worker.scans").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("rule.webhook.replay.worker.actions", "result", "failed").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("rule.webhook.replay.worker.actions", "result", "exhausted").count()).isEqualTo(1);
        assertThat(meterRegistry.timer("rule.webhook.replay.worker.duration").count()).isEqualTo(2);

        RuleActionExecution lockedExecution = ruleRepository.saveActionExecution(RuleActionExecution.webhook(
                ruleId,
                99L,
                "writebackRisk",
                "pending_retry",
                1,
                0,
                "https://erp.example.com/risk-events",
                "rule-" + ruleId + "-run-99-node-writebackRisk",
                OffsetDateTime.now().minusSeconds(1),
                "erp timeout",
                Map.of("maxAsyncReplayAttempts", 3)
        ));
        LeaseRejectingRuleRepository leaseRejectingRepository = new LeaseRejectingRuleRepository(ruleRepository, lockedExecution.id());
        RuleWebhookActionReplayWorker leaseSkippedWorker = new RuleWebhookActionReplayWorker(leaseRejectingRepository, actionService, false, meterRegistry);

        assertThat(leaseSkippedWorker.runDueReplaysOnce()).isZero();

        assertThat(leaseRejectingRepository.rejectedActionExecutionId).isEqualTo(lockedExecution.id());
        assertThat(meterRegistry.counter("rule.webhook.replay.worker.actions", "result", "lease_skipped").count()).isEqualTo(1);
    }

    @Test
    void batchRetriesAndIgnoresWebhookActionExecutionsWithPerItemResults() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        FlakyWebhookClient webhookClient = new FlakyWebhookClient(1);
        RuleApplicationService actionService = new RuleApplicationService(
                new RuleDomainService(),
                ruleRepository,
                auditRepository,
                new FakeSystemAlertRepository(),
                webhookClient
        );
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of(
                                "id", "writebackRisk",
                                "type", "action",
                                "actionType", "webhook",
                                "endpoint", "https://erp.example.com/risk-events",
                                "method", "POST",
                                "maxRetryCount", 0,
                                "retryBackoffSeconds", 0,
                                "body", Map.of("eventType", "high_risk_report", "reportId", 88)
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "writebackRisk", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "end", "condition", "false"),
                        Map.of("source", "writebackRisk", "target", "end")
                )
        );
        Long ruleId = ((Number) actionService.create(Map.of("name", "Batch compensation webhook rule", "definition", definition)).get("ruleId")).longValue();
        actionService.save(ruleId, Map.of("definition", definition, "status", "draft"));
        actionService.submitForReview(ruleId, Map.of("comment", "ready"));
        actionService.approve(ruleId, Map.of("comment", "approved"));
        assertThatThrownBy(() -> actionService.execute(ruleId, Map.of("sample", Map.of("riskScore", 95))))
                .isInstanceOf(IllegalStateException.class);
        Long firstPendingId = ruleRepository.findActionExecutions(ruleId, 1, 10).get(0).id();

        Map<String, Object> batchRetry = actionService.batchHandleWebhookActionExecutions(ruleId, Map.of(
                "operation", "retry",
                "actionExecutionIds", java.util.List.of(firstPendingId, 404L)
        ));

        assertThat(batchRetry)
                .containsEntry("operation", "retry")
                .containsEntry("requestedCount", 2)
                .containsEntry("succeededCount", 1L)
                .containsEntry("failedCount", 1L);
        assertThat(batchRetry.get("items"))
                .asList()
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("actionExecutionId", firstPendingId)
                        .containsEntry("result", "succeeded"))
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("actionExecutionId", 404L)
                        .containsEntry("result", "failed")
                        .extractingByKey("errorMessage")
                        .asString()
                        .contains("not found"));
        Long latestPendingId = ruleRepository.findActionExecutions(ruleId, 1, 10).stream()
                .filter(execution -> "pending_retry".equals(execution.status()))
                .findFirst()
                .orElseThrow()
                .id();

        Map<String, Object> batchIgnore = actionService.batchHandleWebhookActionExecutions(ruleId, Map.of(
                "operation", "ignore",
                "actionExecutionIds", java.util.List.of(latestPendingId),
                "reason", "external ERP incident closed manually"
        ));

        assertThat(batchIgnore)
                .containsEntry("operation", "ignore")
                .containsEntry("requestedCount", 1)
                .containsEntry("succeededCount", 1L)
                .containsEntry("failedCount", 0L);
        assertThat(ruleRepository.findActionExecutions(ruleId, 1, 10))
                .extracting("status")
                .contains("compensation_ignored", "pending_retry");
        assertThat(auditRepository.logs)
                .extracting(OperationLog::operationType)
                .contains("rule_action_webhook_retry", "rule_action_webhook_batch_retry", "rule_action_webhook_ignore", "rule_action_webhook_batch_ignore");
        assertThat(auditRepository.logs)
                .filteredOn(log -> "rule_action_webhook_ignore".equals(log.operationType()))
                .singleElement()
                .satisfies(log -> assertThat(log.detail())
                        .containsEntry("sourceActionExecutionId", latestPendingId)
                        .containsEntry("reason", "external ERP incident closed manually"));
    }

    @Test
    void manualRetryClearsFailedScheduleStateAfterMaxRetryCount() {
        InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
        RuleApplicationService scheduledService = new RuleApplicationService(new RuleDomainService(), ruleRepository);
        RuleProductionScheduler scheduler = new RuleProductionScheduler(ruleRepository, scheduledService, false);
        Map<String, Object> created = scheduledService.create(Map.of(
                "name", "Recoverable scheduled rule",
                "definition", Map.of(
                        "nodes", java.util.List.of(
                                Map.of("id", "start", "type", "start"),
                                Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", ">", "value", 30),
                                Map.of("id", "end", "type", "end")
                        ),
                        "edges", java.util.List.of(
                                Map.of("source", "start", "target", "aging"),
                                Map.of("source", "aging", "target", "end")
                        )
                )
        ));
        Long ruleId = ((Number) created.get("ruleId")).longValue();
        scheduledService.save(ruleId, Map.of("definition", created.get("definition"), "status", "draft"));
        scheduledService.submitForReview(ruleId, Map.of("comment", "ready"));
        scheduledService.approve(ruleId, Map.of("comment", "approved"));
        scheduledService.configureSchedule(ruleId, Map.of(
                "scheduleEnabled", true,
                "scheduleIntervalSeconds", 60,
                "nextRunAt", OffsetDateTime.now().minusMinutes(1).toString(),
                "maxRetryCount", 1,
                "scheduleInput", Map.of("sample", Map.of("daysOverdue", 45))
        ));
        var published = ruleRepository.findById(ruleId).orElseThrow();
        Map<String, Object> invalidDefinition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "aging", "type", "condition", "field", "daysOverdue", "operator", "contains", "value", 30),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "aging"),
                        Map.of("source", "aging", "target", "end")
                )
        );
        ruleRepository.save(published.update(published.name(), published.description(), published.status(), invalidDefinition, published.currentVersionId()));
        assertThat(scheduler.runDueRulesOnce()).isEqualTo(1);
        assertThat(scheduler.runDueRulesOnce()).isZero();
        var failedState = ruleRepository.findById(ruleId).orElseThrow();
        assertThat(failedState.failureCount()).isEqualTo(1);

        Map<String, Object> retryState = scheduledService.retrySchedule(ruleId, Map.of(
                "nextRunAt", OffsetDateTime.now().minusSeconds(1).toString(),
                "scheduleInput", Map.of("sample", Map.of("daysOverdue", 45))
        ));
        var restored = ruleRepository.findById(ruleId).orElseThrow();
        ruleRepository.save(restored.update(restored.name(), restored.description(), restored.status(), created.get("definition") instanceof Map<?, ?> map
                ? new java.util.LinkedHashMap<>(map.entrySet().stream().collect(java.util.stream.Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue)))
                : Map.of(), restored.currentVersionId()));

        assertThat(retryState)
                .containsEntry("failureCount", 0)
                .containsEntry("scheduleEnabled", true);
        assertThat(ruleRepository.findById(ruleId).orElseThrow().nextRunAt()).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(scheduler.runDueRulesOnce()).isEqualTo(1);
        assertThat(scheduledService.listRuns(ruleId, 1, 10).items())
                .filteredOn(run -> "succeeded".equals(run.get("status")))
                .hasSize(1);
    }

    @Test
    void savingOrDebuggingMissingRuleIsRejected() {
        assertThatThrownBy(() -> service.save(404L, Map.of("name", "missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rule not found");

        assertThatThrownBy(() -> service.debug(404L, Map.of("nodeId", "score")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rule not found");
    }

    private static Long createPublishedApprovalRule(RuleApplicationService service) {
        return createPublishedApprovalRule(service, "finance_manager", null);
    }

    private static Long createPublishedApprovalRule(RuleApplicationService service, String assigneeRole) {
        return createPublishedApprovalRule(service, assigneeRole, null);
    }

    private static Long createPublishedApprovalRule(RuleApplicationService service, Integer slaHours) {
        return createPublishedApprovalRule(service, "finance_manager", slaHours);
    }

    private static Long createPublishedApprovalRule(RuleApplicationService service, String assigneeRole, Integer slaHours) {
        return createPublishedApprovalRule(service, assigneeRole, slaHours, null);
    }

    private static Long createPublishedApprovalRule(RuleApplicationService service, String assigneeRole, Integer slaHours, String delegateRole) {
        return createPublishedApprovalRule(service, assigneeRole, slaHours, delegateRole, null, null);
    }

    private static Long createPublishedApprovalRule(RuleApplicationService service,
                                                    String assigneeRole,
                                                    Integer slaHours,
                                                    String delegateRole,
                                                    String delegateActiveFrom,
                                                    String delegateActiveTo) {
        Map<String, Object> approvalNode = new java.util.LinkedHashMap<>();
        approvalNode.put("id", "financeApproval");
        approvalNode.put("type", "approval");
        approvalNode.put("assigneeRole", assigneeRole);
        approvalNode.put("approvalTitle", "Finance review");
        if (slaHours != null) {
            approvalNode.put("slaHours", slaHours);
        }
        if (delegateRole != null) {
            approvalNode.put("delegateRole", delegateRole);
        }
        if (delegateActiveFrom != null) {
            approvalNode.put("delegateActiveFrom", delegateActiveFrom);
        }
        if (delegateActiveTo != null) {
            approvalNode.put("delegateActiveTo", delegateActiveTo);
        }
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        approvalNode,
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );
        Long ruleId = ((Number) service.create(Map.of(
                "name", "Finance approval rule",
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("definition", definition, "status", "draft"));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));
        return ruleId;
    }

    private static Map<String, Object> multiAssigneeApprovalDefinition(String approvalMode) {
        return Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "jointApproval",
                                "type", "approval",
                                "assigneeRoles", java.util.List.of("finance_manager", "legal_manager"),
                                "approvalMode", approvalMode,
                                "approvalTitle", "Joint review"
                        ),
                        Map.of("id", "notifyReview", "type", "action", "actionType", "notify", "severity", "warning", "message", "Joint review completed"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "jointApproval"),
                        Map.of("source", "jointApproval", "target", "notifyReview"),
                        Map.of("source", "notifyReview", "target", "end")
                )
        );
    }

    private static Long publishRule(RuleApplicationService service, String name, Map<String, Object> definition) {
        Long ruleId = ((Number) service.create(Map.of(
                "name", name,
                "definition", definition
        )).get("ruleId")).longValue();
        service.save(ruleId, Map.of("definition", definition, "status", "draft"));
        service.submitForReview(ruleId, Map.of("comment", "ready"));
        service.approve(ruleId, Map.of("comment", "approved"));
        return ruleId;
    }

    private static Map<String, Object> linearDefinitionWithNodeCount(int nodeCount) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(Map.of("id", "start", "type", "start"));
        String previous = "start";
        for (int index = 1; index <= nodeCount - 2; index++) {
            String nodeId = "notify" + index;
            nodes.add(Map.of(
                    "id", nodeId,
                    "type", "action",
                    "actionType", "notify",
                    "message", "node " + index
            ));
            edges.add(Map.of("source", previous, "target", nodeId));
            previous = nodeId;
        }
        nodes.add(Map.of("id", "end", "type", "end"));
        edges.add(Map.of("source", previous, "target", "end"));
        return Map.of("nodes", nodes, "edges", edges);
    }

    private static Map<String, Object> approvalTemplateReferenceDefinition(Long templateId, String nodeId) {
        return Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", nodeId,
                                "type", "approval",
                                "approvalTitle", "Risk review",
                                "assigneeRoles", List.of("risk_manager"),
                                "approvalTemplateId", templateId,
                                "approvalTemplateVersion", 1,
                                "approvalTemplateStepId", "riskReview"
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", List.of(
                        Map.of("source", "start", "target", nodeId),
                        Map.of("source", nodeId, "target", "end")
                )
        );
    }

    private static Map<String, Object> approvalByRole(List<Map<String, Object>> approvals, String assigneeRole) {
        return approvals.stream()
                .filter(item -> assigneeRole.equals(item.get("assigneeRole")))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> approvalById(List<Map<String, Object>> approvals, Object approvalRecordId) {
        return approvals.stream()
                .filter(item -> Objects.equals(approvalRecordId, item.get("approvalRecordId")))
                .findFirst()
                .orElseThrow();
    }

    private static class InMemoryAuditRepository implements AuditRepository {
        protected final List<OperationLog> logs = new ArrayList<>();

        @Override
        public OperationLog save(OperationLog log) {
            OperationLog saved = log.withId((long) logs.size() + 1);
            logs.add(saved);
            return saved;
        }

        @Override
        public Optional<OperationLog> findById(Long id) {
            return logs.stream().filter(log -> id.equals(log.id())).findFirst();
        }

        @Override
        public List<OperationLog> findPage(int page, int pageSize) {
            return logs.stream().skip((long) (page - 1) * pageSize).limit(pageSize).toList();
        }

        @Override
        public long count() {
            return logs.size();
        }
    }

    private static class QueryOnlyAuditRepository extends InMemoryAuditRepository {
        private int fullScanCount = 0;
        private int detailLookupCount = 0;

        @Override
        public List<OperationLog> findPage(int page, int pageSize) {
            if (page == 1 && pageSize == Integer.MAX_VALUE) {
                fullScanCount++;
                throw new AssertionError("full audit scan is not allowed for subprocess approval resume");
            }
            return super.findPage(page, pageSize);
        }

        public List<OperationLog> findByOperationAndDetail(String operationType, String detailKey, String detailValue) {
            detailLookupCount++;
            return logs.stream()
                    .filter(log -> operationType != null && operationType.equals(log.operationType()))
                    .filter(log -> detailValue != null && detailValue.equals(String.valueOf((log.detail() == null ? Map.of() : log.detail()).get(detailKey))))
                    .toList();
        }
    }

    private static class FakeApprovalSupplementStorage implements DocumentStorage {
        private String objectKey;

        @Override
        public StoredObject store(org.springframework.web.multipart.MultipartFile file, String objectKey) throws IOException {
            this.objectKey = objectKey;
            return new StoredObject("approval-supplements", objectKey, file.getOriginalFilename(), file.getContentType(), file.getSize());
        }
    }

    private static class FakeSystemAlertRepository implements SystemAlertRepository {
        private final List<SystemAlert> alerts = new ArrayList<>();

        @Override
        public SystemAlert save(SystemAlert alert) {
            SystemAlert saved = alert.withId((long) alerts.size() + 1);
            alerts.add(saved);
            return saved;
        }

        @Override
        public List<SystemAlert> findByRecipient(Long recipientUserId, String status, int page, int pageSize) {
            return alerts.stream()
                    .filter(alert -> recipientUserId.equals(alert.recipientUserId()))
                    .filter(alert -> status == null || status.equals(alert.status()))
                    .skip((long) (Math.max(page, 1) - 1) * Math.max(pageSize, 1))
                    .limit(Math.max(pageSize, 1))
                    .toList();
        }

        @Override
        public long countByRecipient(Long recipientUserId, String status) {
            return alerts.stream()
                    .filter(alert -> recipientUserId.equals(alert.recipientUserId()))
                    .filter(alert -> status == null || status.equals(alert.status()))
                    .count();
        }

        @Override
        public boolean existsUnreadByDedupeKey(Long recipientUserId,
                                               String type,
                                               String resourceType,
                                               Long resourceId,
                                               String dedupeKey) {
            return alerts.stream()
                    .filter(alert -> recipientUserId.equals(alert.recipientUserId()))
                    .filter(alert -> type.equals(alert.type()))
                    .filter(alert -> "unread".equals(alert.status()))
                    .filter(alert -> resourceType.equals(alert.resourceType()))
                    .filter(alert -> resourceId.equals(alert.resourceId()))
                    .anyMatch(alert -> dedupeKey.equals(alert.payload().get("dedupeKey")));
        }
    }

    private static class FakeWebhookClient implements RuleWebhookClient {
        private final List<Map<String, Object>> calls = new ArrayList<>();

        @Override
        public Map<String, Object> send(String endpoint, String method, Map<String, Object> headers, Map<String, Object> body) {
            calls.add(Map.of(
                    "endpoint", endpoint,
                    "method", method,
                    "headers", headers,
                    "body", body
            ));
            return Map.of("statusCode", 202, "status", "accepted");
        }
    }

    private static class FailingWebhookClient implements RuleWebhookClient {
        private int calls = 0;

        @Override
        public Map<String, Object> send(String endpoint, String method, Map<String, Object> headers, Map<String, Object> body) {
            calls++;
            throw new IllegalStateException("erp timeout");
        }
    }

    private static class FlakyWebhookClient implements RuleWebhookClient {
        private final int failuresBeforeSuccess;
        private final List<Map<String, Object>> calls = new ArrayList<>();

        private FlakyWebhookClient(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public Map<String, Object> send(String endpoint, String method, Map<String, Object> headers, Map<String, Object> body) {
            calls.add(Map.of(
                    "endpoint", endpoint,
                    "method", method,
                    "headers", headers,
                    "body", body
            ));
            if (calls.size() <= failuresBeforeSuccess) {
                throw new IllegalStateException("erp timeout " + calls.size());
            }
            return Map.of("statusCode", 202, "status", "accepted");
        }
    }

    private static class LeaseRejectingRuleRepository implements com.company.report.rule.domain.repository.RuleRepository {
        private final InMemoryRuleRepository delegate;
        private final Long dueActionExecutionId;
        private Long rejectedActionExecutionId;

        private LeaseRejectingRuleRepository(InMemoryRuleRepository delegate, Long dueActionExecutionId) {
            this.delegate = delegate;
            this.dueActionExecutionId = dueActionExecutionId;
        }

        @Override
        public com.company.report.rule.domain.model.Rule save(com.company.report.rule.domain.model.Rule rule) {
            return delegate.save(rule);
        }

        @Override
        public Optional<com.company.report.rule.domain.model.Rule> findById(Long id) {
            return delegate.findById(id);
        }

        @Override
        public List<com.company.report.rule.domain.model.Rule> findPage(int page, int pageSize) {
            return delegate.findPage(page, pageSize);
        }

        @Override
        public long count() {
            return delegate.count();
        }

        @Override
        public Long saveVersion(Long ruleId, Map<String, Object> definition) {
            return delegate.saveVersion(ruleId, definition);
        }

        @Override
        public RuleDebugRun saveDebugRun(RuleDebugRun debugRun) {
            return delegate.saveDebugRun(debugRun);
        }

        @Override
        public RuleDebugRun updateDebugRun(RuleDebugRun debugRun) {
            return delegate.updateDebugRun(debugRun);
        }

        @Override
        public Optional<RuleDebugRun> findRunById(Long runId) {
            return delegate.findRunById(runId);
        }

        @Override
        public List<RuleDebugRun> findRuns(Long ruleId, int page, int pageSize) {
            return delegate.findRuns(ruleId, page, pageSize);
        }

        @Override
        public long countRuns(Long ruleId) {
            return delegate.countRuns(ruleId);
        }

        @Override
        public com.company.report.rule.domain.model.RuleRunMetrics metrics(Long ruleId) {
            return delegate.metrics(ruleId);
        }

        @Override
        public RuleActionExecution saveActionExecution(RuleActionExecution execution) {
            return delegate.saveActionExecution(execution);
        }

        @Override
        public RuleApprovalRecord saveApprovalRecord(RuleApprovalRecord approvalRecord) {
            return delegate.saveApprovalRecord(approvalRecord);
        }

        @Override
        public Optional<RuleApprovalRecord> findApprovalRecordById(Long approvalRecordId) {
            return delegate.findApprovalRecordById(approvalRecordId);
        }

        @Override
        public RuleApprovalRecord updateApprovalRecord(RuleApprovalRecord approvalRecord) {
            return delegate.updateApprovalRecord(approvalRecord);
        }

        @Override
        public List<RuleApprovalRecord> findApprovalRecords(Long ruleId, int page, int pageSize) {
            return delegate.findApprovalRecords(ruleId, page, pageSize);
        }

        @Override
        public List<RuleApprovalRecord> findApprovalRecordsByStatus(String status, com.company.report.rule.domain.repository.RuleRepository.ApprovalRecordFilter filter, int page, int pageSize) {
            return delegate.findApprovalRecordsByStatus(status, filter, page, pageSize);
        }

        @Override
        public long countApprovalRecords(Long ruleId) {
            return delegate.countApprovalRecords(ruleId);
        }

        @Override
        public long countApprovalRecordsByStatus(String status, com.company.report.rule.domain.repository.RuleRepository.ApprovalRecordFilter filter) {
            return delegate.countApprovalRecordsByStatus(status, filter);
        }

        @Override
        public Optional<RuleActionExecution> findActionExecutionById(Long actionExecutionId) {
            return delegate.findActionExecutionById(actionExecutionId);
        }

        @Override
        public List<RuleActionExecution> findActionExecutions(Long ruleId, int page, int pageSize) {
            return delegate.findActionExecutions(ruleId, page, pageSize);
        }

        @Override
        public long countActionExecutions(Long ruleId) {
            return delegate.countActionExecutions(ruleId);
        }

        @Override
        public List<RuleActionExecution> findDueWebhookActionExecutions(OffsetDateTime now, int limit) {
            return delegate.findActionExecutionById(dueActionExecutionId).stream().toList();
        }

        @Override
        public boolean tryAcquireActionExecutionLease(Long actionExecutionId, OffsetDateTime lockedUntil) {
            rejectedActionExecutionId = actionExecutionId;
            return false;
        }

        @Override
        public void releaseActionExecutionLease(Long actionExecutionId) {
            delegate.releaseActionExecutionLease(actionExecutionId);
        }

        @Override
        public List<com.company.report.rule.domain.model.Rule> findDueScheduledRules(OffsetDateTime now, int limit) {
            return delegate.findDueScheduledRules(now, limit);
        }

        @Override
        public boolean tryAcquireRuleScheduleLease(Long ruleId, OffsetDateTime lockedUntil) {
            return delegate.tryAcquireRuleScheduleLease(ruleId, lockedUntil);
        }

        @Override
        public void releaseRuleScheduleLease(Long ruleId) {
            delegate.releaseRuleScheduleLease(ruleId);
        }

        @Override
        public com.company.report.rule.domain.model.Rule updateScheduleState(Long ruleId, OffsetDateTime nextRunAt, int failureCount) {
            return delegate.updateScheduleState(ruleId, nextRunAt, failureCount);
        }
    }

    private static class InMemoryReportRepository implements ReportRepository {
        private final List<Report> reports = new ArrayList<>();

        @Override
        public Optional<Report> findById(Long id) {
            return reports.stream().filter(report -> id.equals(report.id())).findFirst();
        }

        @Override
        public Report save(Report report) {
            reports.removeIf(existing -> existing.id().equals(report.id()));
            reports.add(report);
            return report;
        }

        @Override
        public List<Report> findByOwner(Long ownerUserId, int page, int pageSize) {
            return reports.stream().filter(report -> ownerUserId.equals(report.ownerUserId())).toList();
        }

        @Override
        public long countByOwner(Long ownerUserId) {
            return findByOwner(ownerUserId, 1, Integer.MAX_VALUE).size();
        }
    }
}

