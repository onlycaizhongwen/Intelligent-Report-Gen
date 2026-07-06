# Closure 278: Report Template and Version Diff Contract Schema

## Scope

- Requirement chain: `REQ-REPORT-001`, `REQ-REPORT-004`, `REQ-REPORT-005`
- Phase artifact: `S4 API Contract`
- Target endpoints:
  - `GET /api/v1/report-templates`
  - `GET /api/v1/reports/{reportId}/versions/diff`

## Result

Report template discovery and report version diff APIs no longer expose only generic `business response data` in the backend supplement. The API contract now declares backend-real field-level response schemas for template-driven report creation and version governance.

- Report template list shape: `data` is an array.
- Report template item fields: `templateId`, `name`, `category`, `version`, `status`, `fields`, `outlineSchema`, `updatedAt`
- Template field item fields: `fieldKey`, `label`, `type`, `required`, `options`, `defaultValue`, `helpText`
- Version diff fields: `reportId`, `baseVersionId`, `targetVersionId`, `summary`, `changes`
- Version diff summary fields: `added`, `removed`, `modified`, `unchanged`
- Version diff change fields: `changeType`, `heading`, `baseContent`, `targetContent`, `baseCitations`, `targetCitations`

## Evidence

- Contract guard: `ContractSurfaceTest#reportTemplateAndVersionDiffEndpointsDeclareFieldLevelResponseContracts`
- API contract: `docs/skill-chain/api_contract.md` section `13.2`
- Backend field source: `ReportTemplate#toResponse`, `ReportTemplate.TemplateField#toResponse`, and `ReportApplicationService#compareVersions`

## TDD Evidence

RED:

```text
ContractSurfaceTest.reportTemplateAndVersionDiffEndpointsDeclareFieldLevelResponseContracts
GET /api/v1/report-templates responseBody.data.type
expected: "array"
 but was: "object"
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#reportTemplateAndVersionDiffEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Gaps

- This closure did not rerun browser-level template filling or version diff E2E.
- Rule-engine endpoints still have generic schemas and should be deepened in subsequent closures.
