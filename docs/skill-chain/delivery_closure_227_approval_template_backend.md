# 227. UC-08 Approval Template Backend Foundation Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: reusable approval templates and the foundation for an independent manual approval flow designer.
- Delivery boundary: backend contract only. Frontend template management and rule-designer template application remain follow-up work.

## Completed

- Added `RuleApprovalTemplate` as the domain model for reusable multi-step approval definitions.
- Added repository contracts and both in-memory and JDBC persistence for approval templates.
- Added PostgreSQL migration `V049__rule_approval_templates.sql`.
- Added service APIs:
  - `RuleApplicationService.createApprovalTemplate(...)`
  - `RuleApplicationService.listApprovalTemplates(...)`
- Added REST endpoints protected by `rule:manage`:
  - `POST /api/v1/rules/approval-templates`
  - `GET /api/v1/rules/approval-templates`
- Added validation for required template name, non-empty steps, step `approvalTitle`, non-empty `assigneeRoles`, `approvalMode` in `all/any`, and valid SLA escalation entries.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsAndListsApprovalTemplatesWithValidatedSteps" test` failed because `createApprovalTemplate` and `listApprovalTemplates` did not exist.
- GREEN: the same test passed `1/1`.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints,ContractSurfaceTest#ruleApprovalDelegateRuleOperationsUseManagePermission,RuleApplicationServiceTest#createsAndListsApprovalTemplatesWithValidatedSteps,RuleApplicationServiceTest#rejectedApprovalCreatesSupplementRequestAndResubmissionCreatesNewPendingApproval,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` passed `6/6`.

## Remaining Gaps

- Frontend approval template management page.
- Rule designer action to apply a template and generate approval nodes/edges.
- Template versioning and disable/update lifecycle.
- Full visual independent approval flow designer.
