# 230. UC-08 Approval Template Lifecycle Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: reusable approval templates need an operational lifecycle so retired templates are not reused by rule designers.
- Delivery boundary: enable/disable existing approval templates, expose lifecycle endpoints through `rule:manage`, show lifecycle controls in the web console, and keep the rule designer loading only enabled templates.

## Completed

- Added approval template status mutation in the domain model.
- Added repository update support for in-memory and JDBC persistence.
- Added service APIs:
  - `disableApprovalTemplate(templateId)`
  - `enableApprovalTemplate(templateId)`
- Added REST endpoints:
  - `POST /api/v1/rules/approval-templates/{templateId}/disable`
  - `POST /api/v1/rules/approval-templates/{templateId}/enable`
- Added frontend API methods for enable/disable lifecycle calls.
- Added lifecycle buttons to the approval template management page.
- Kept the rule designer constrained to `status=enabled` templates.

## Verification

- RED:
  - Backend target test failed because `disableApprovalTemplate/enableApprovalTemplate` did not exist and controller routes were missing.
  - Frontend API contract failed with `ruleApi.disableApprovalTemplate is not a function`.
  - E2E failed waiting for `Disable Legal review approval`.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#disablesAndEnablesApprovalTemplatesForDesignerReuse,ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints" test` passed `2/2`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- Template versioning and immutable historical versions.
- Template usage count and impact analysis before disabling.
- Full visual independent approval flow designer.
- Explicit guided insertion into an existing start/end branch.
