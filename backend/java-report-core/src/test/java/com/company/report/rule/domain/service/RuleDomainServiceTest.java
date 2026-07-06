package com.company.report.rule.domain.service;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rule domain service unit tests")
class RuleDomainServiceTest {
    private final RuleDomainService service = new RuleDomainService();

    @Test
    @DisplayName("REQ-RULE-001: rejects empty debug input")
    void ensureDebugInputValid_whenEmpty_thenThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.ensureDebugInputValid(Map.of()));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.RULE_VALIDATION_FAILED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-RULE-001: accepts valid debug input")
    void ensureDebugInputValid_whenValid_thenPasses() {
        assertDoesNotThrow(() -> service.ensureDebugInputValid(Map.of("nodeId", "start")));
    }

    @Test
    @DisplayName("REQ-RULE-001: rejects dangling edges")
    void validateDefinition_whenMissingNodesOrDanglingEdges_thenThrows() {
        Map<String, Object> invalid = Map.of(
                "nodes", java.util.List.of(Map.of("id", "start", "type", "start")),
                "edges", java.util.List.of(Map.of("source", "start", "target", "missing"))
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDefinition(invalid));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.RULE_VALIDATION_FAILED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-RULE-001: accepts valid nodes and edges")
    void validateDefinition_whenNodesAndEdgesValid_thenPasses() {
        Map<String, Object> valid = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "score", "type", "condition", "field", "risk", "operator", ">=", "value", 80),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "score"),
                        Map.of("source", "score", "target", "end")
                )
        );

        assertDoesNotThrow(() -> service.validateDefinition(valid));
    }

    @Test
    @DisplayName("REQ-RULE-001: rejects executable topology cycles")
    void validateDefinition_whenExecutablePathContainsCycle_thenThrows() {
        Map<String, Object> invalid = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "review", "type", "action", "actionType", "notify", "message", "review"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "review"),
                        Map.of("source", "review", "target", "start")
                )
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDefinition(invalid));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.RULE_VALIDATION_FAILED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-RULE-001: rejects topology paths that cannot reach end")
    void validateDefinition_whenStartPathCannotReachEnd_thenThrows() {
        Map<String, Object> invalid = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "notify", "type", "action", "actionType", "notify", "message", "notify"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "notify")
                )
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDefinition(invalid));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.RULE_VALIDATION_FAILED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-RULE-001: rejects create_task action without task fields")
    void validateDefinition_whenCreateTaskActionMissingTaskFields_thenThrows() {
        Map<String, Object> invalid = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "createReviewTask", "type", "action", "actionType", "create_task"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "createReviewTask"),
                        Map.of("source", "createReviewTask", "target", "end")
                )
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDefinition(invalid));

        assertEquals(422, ex.statusCode());
        assertEquals(ErrorCode.RULE_VALIDATION_FAILED.code(), ex.code());
    }

    @Test
    @DisplayName("REQ-RULE-001: aggregate node derives fields for later conditions")
    void executeDebug_whenAggregateNodeFeedsCondition_thenUsesDerivedValue() {
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

        Map<String, Object> output = service.executeDebug(definition, Map.of(
                "sample", Map.of(
                        "invoices", java.util.List.of(
                                Map.of("invoiceNo", "A001", "overdueAmount", 4000),
                                Map.of("invoiceNo", "A002", "overdueAmount", 7000)
                        )
                )
        ));

        assertEquals(true, output.get("matched"));
        assertEquals(4, output.get("evaluatedNodes"));
        assertTrue(output.get("trace") instanceof java.util.List<?>);
        java.util.List<?> trace = (java.util.List<?>) output.get("trace");
        assertTrue(trace.get(1) instanceof Map<?, ?>);
        Map<?, ?> aggregateTrace = (Map<?, ?>) trace.get(1);
        assertEquals("aggregate", aggregateTrace.get("type"));
        assertEquals(11000d, aggregateTrace.get("value"));
        assertEquals("totalOverdueAmount", aggregateTrace.get("outputField"));
    }

    @Test
    @DisplayName("REQ-RULE-001: branch node selects true or false path")
    void executeDebug_whenBranchNodeMatches_thenOnlyEvaluatesSelectedPath() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskBranch", "type", "branch", "field", "riskScore", "operator", ">=", "value", 80),
                        Map.of("id", "highRiskAction", "type", "action", "actionType", "notify", "message", "high risk"),
                        Map.of("id", "lowRiskAction", "type", "action", "actionType", "archive", "message", "low risk"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskBranch"),
                        Map.of("source", "riskBranch", "target", "highRiskAction", "condition", "true"),
                        Map.of("source", "riskBranch", "target", "lowRiskAction", "condition", "false"),
                        Map.of("source", "highRiskAction", "target", "end"),
                        Map.of("source", "lowRiskAction", "target", "end")
                )
        );

        Map<String, Object> output = service.executeDebug(definition, Map.of("sample", Map.of("riskScore", 91)));

        assertEquals(true, output.get("matched"));
        assertEquals(4, output.get("evaluatedNodes"));
        assertTrue(output.get("trace") instanceof java.util.List<?>);
        java.util.List<?> trace = (java.util.List<?>) output.get("trace");
        assertEquals(
                java.util.List.of("start", "riskBranch", "highRiskAction", "end"),
                trace.stream().map(item -> ((Map<?, ?>) item).get("nodeId")).toList()
        );
        Map<?, ?> branchTrace = (Map<?, ?>) trace.get(1);
        assertEquals("branch", branchTrace.get("type"));
        assertEquals("true", branchTrace.get("selectedPath"));
        assertEquals("highRiskAction", branchTrace.get("nextNodeId"));
    }

    @Test
    @DisplayName("REQ-RULE-001: single equals operator behaves as equality")
    void executeDebug_whenConditionUsesSingleEquals_thenTreatsItAsEquality() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "creditHold", "type", "condition", "field", "creditStatus", "operator", "=", "value", "hold"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "creditHold"),
                        Map.of("source", "creditHold", "target", "end")
                )
        );

        Map<String, Object> output = service.executeDebug(definition, Map.of("sample", Map.of("creditStatus", "hold")));

        assertEquals(true, output.get("matched"));
        assertEquals(3, output.get("evaluatedNodes"));
    }

    @Test
    @DisplayName("REQ-RULE-001: approval node marks pending approval in debug trace")
    void executeDebug_whenApprovalNodePresent_thenMarksPendingApprovalInTrace() {
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

        Map<String, Object> output = service.executeDebug(definition, Map.of("sample", Map.of("riskScore", 91)));

        assertEquals(true, output.get("matched"));
        assertEquals(3, output.get("evaluatedNodes"));
        assertTrue(output.get("trace") instanceof java.util.List<?>);
        java.util.List<?> trace = (java.util.List<?>) output.get("trace");
        Map<?, ?> approvalTrace = (Map<?, ?>) trace.get(1);
        assertEquals("financeApproval", approvalTrace.get("nodeId"));
        assertEquals("approval", approvalTrace.get("type"));
        assertEquals(true, approvalTrace.get("pendingApproval"));
        assertEquals("finance_manager", approvalTrace.get("assigneeRole"));
        assertEquals("Finance review", approvalTrace.get("approvalTitle"));
    }

    @Test
    @DisplayName("REQ-RULE-001: subprocess node marks referenced rule in debug trace")
    void executeDebug_whenSubprocessNodePresent_thenMarksReferencedRuleInTrace() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "riskSubprocess", "type", "subprocess", "subprocessRuleId", 42, "subprocessName", "Risk review flow"),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "riskSubprocess"),
                        Map.of("source", "riskSubprocess", "target", "end")
                )
        );

        Map<String, Object> output = service.executeDebug(definition, Map.of("sample", Map.of("riskScore", 91)));

        assertEquals(true, output.get("matched"));
        assertEquals(3, output.get("evaluatedNodes"));
        assertTrue(output.get("trace") instanceof java.util.List<?>);
        java.util.List<?> trace = (java.util.List<?>) output.get("trace");
        Map<?, ?> subprocessTrace = (Map<?, ?>) trace.get(1);
        assertEquals("riskSubprocess", subprocessTrace.get("nodeId"));
        assertEquals("subprocess", subprocessTrace.get("type"));
        assertEquals(true, subprocessTrace.get("subprocessPending"));
        assertEquals(42, subprocessTrace.get("subprocessRuleId"));
        assertEquals("Risk review flow", subprocessTrace.get("subprocessName"));
    }

    @Test
    @DisplayName("REQ-RULE-001: approval node marks multi assignee roles and mode in trace")
    void executeDebug_whenApprovalNodeHasAssigneeRoles_thenMarksRolesAndModeInTrace() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "jointApproval",
                                "type", "approval",
                                "assigneeRoles", java.util.List.of("finance_manager", "legal_manager"),
                                "approvalMode", "all",
                                "approvalTitle", "Joint review"
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "jointApproval"),
                        Map.of("source", "jointApproval", "target", "end")
                )
        );

        Map<String, Object> output = service.executeDebug(definition, Map.of("sample", Map.of("riskScore", 91)));

        java.util.List<?> trace = (java.util.List<?>) output.get("trace");
        Map<?, ?> approvalTrace = (Map<?, ?>) trace.get(1);
        assertEquals(java.util.List.of("finance_manager", "legal_manager"), approvalTrace.get("assigneeRoles"));
        assertEquals("all", approvalTrace.get("approvalMode"));
        assertEquals("finance_manager", approvalTrace.get("assigneeRole"));
    }

    @Test
    @DisplayName("REQ-RULE-001: approval node marks delegate role in trace")
    void executeDebug_whenApprovalNodeHasDelegateRole_thenMarksDelegateInTrace() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "financeApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "delegateRole", "finance_delegate",
                                "approvalTitle", "Finance review"
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );

        Map<String, Object> output = service.executeDebug(definition, Map.of("sample", Map.of("riskScore", 91)));

        java.util.List<?> trace = (java.util.List<?>) output.get("trace");
        Map<?, ?> approvalTrace = (Map<?, ?>) trace.get(1);
        assertEquals("finance_delegate", approvalTrace.get("delegateRole"));
    }

    @Test
    @DisplayName("REQ-RULE-001: approval node marks delegate active window in trace")
    void executeDebug_whenApprovalNodeHasDelegateActiveWindow_thenMarksWindowInTrace() {
        Map<String, Object> definition = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of(
                                "id", "financeApproval",
                                "type", "approval",
                                "assigneeRole", "finance_manager",
                                "delegateRole", "finance_delegate",
                                "delegateActiveFrom", "2026-06-26T08:00:00Z",
                                "delegateActiveTo", "2026-06-26T18:00:00Z",
                                "approvalTitle", "Finance review"
                        ),
                        Map.of("id", "end", "type", "end")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "financeApproval"),
                        Map.of("source", "financeApproval", "target", "end")
                )
        );

        Map<String, Object> output = service.executeDebug(definition, Map.of("sample", Map.of("riskScore", 91)));

        java.util.List<?> trace = (java.util.List<?>) output.get("trace");
        Map<?, ?> approvalTrace = (Map<?, ?>) trace.get(1);
        assertEquals("finance_delegate", approvalTrace.get("delegateRole"));
        assertEquals("2026-06-26T08:00:00Z", approvalTrace.get("delegateActiveFrom"));
        assertEquals("2026-06-26T18:00:00Z", approvalTrace.get("delegateActiveTo"));
    }
}
