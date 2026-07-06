# Closure 279: Rule Governance Contract Schema

## Scope

- Requirement chain: `REQ-RULE-001`
- Phase artifact: `S4 API Contract`
- Target endpoints:
  - `POST /api/v1/rules/approval-delegate-rules`
  - `GET /api/v1/rules/approval-delegate-rules`
  - `POST /api/v1/rules/approval-delegate-rules/batch-import`
  - `PUT /api/v1/rules/approval-delegate-rules/{delegateRuleId}`
  - `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/disable`
  - `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/enable`
  - `POST /api/v1/rules/approval-templates`
  - `GET /api/v1/rules/approval-templates`
  - `GET /api/v1/rules/approval-templates/{templateId}/usage`
  - `PUT /api/v1/rules/approval-templates/{templateId}`
  - `GET /api/v1/rules/approval-templates/{templateId}/versions`
  - `GET /api/v1/rules/approval-templates/{templateId}/versions/diff`
  - `POST /api/v1/rules/approval-templates/{templateId}/versions/{version}/rollback`
  - `GET /api/v1/rules/approval-templates/{templateId}/versions/{version}`
  - `POST /api/v1/rules/approval-templates/{templateId}/disable`
  - `POST /api/v1/rules/approval-templates/{templateId}/enable`

## Result

Rule governance APIs for approval delegate rules and approval templates no longer expose only generic `business response data` in the backend supplement. The API contract now declares backend-real field-level response schemas for delegate-rule scheduling, template versioning, template usage impact, rollback, and batch-import results.

- Delegate rule fields: `delegateRuleId`, `assigneeRole`, `delegateRole`, `activeFrom`, `activeTo`, `activeWeekdays`, `activeDates`, `status`, `reason`, `createdByUserId`, `createdAt`
- Delegate batch import fields: `imported`, `failed`, `results`
- Delegate batch row fields: `rowNumber`, `status`, `reason`, `delegateRuleId`, `assigneeRole`, `delegateRole`
- Approval template fields: `approvalTemplateId`, `name`, `description`, `status`, `version`, `steps`, `usageCount`, `usageRules`, `createdByUserId`, `createdAt`, `updatedAt`
- Template usage rule fields: `ruleId`, `name`, `status`
- Template version diff fields: `approvalTemplateId`, `baseVersion`, `targetVersion`, `summary`, `changes`
- Template rollback fields: `approvalTemplateId`, `sourceVersion`, `newVersion`, `currentVersion`, `changeReason`

## Evidence

- Contract guard: `ContractSurfaceTest#ruleGovernanceEndpointsDeclareFieldLevelResponseContracts`
- API contract: `docs/skill-chain/api_contract.md` section `13.2`
- Backend field source: `RuleApplicationService#toApprovalDelegateRuleResponse`, `toApprovalTemplateResponse`, `toApprovalTemplateUsageRuleResponse`, `compareApprovalTemplateVersions`, `rollbackApprovalTemplateVersion`, and `batchImportApprovalDelegateRules`

## TDD Evidence

RED:

```text
ContractSurfaceTest.ruleGovernanceEndpointsDeclareFieldLevelResponseContracts
POST /api/v1/rules/approval-delegate-rules responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleGovernanceEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Gaps

- This closure did not rerun browser-level rule governance E2E or live Higress route smoke.
- Rule review, execution, approval-record, action-execution, metrics, and schedule endpoints still have generic schemas and should be deepened in subsequent closures.
