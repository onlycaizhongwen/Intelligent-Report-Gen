# Delivery Closure 304: Enterprise Data Source Presets

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: ERP/OA/finance source template presets for faster customer data-source configuration.

## Result

- Added `GET /api/v1/data-sources/presets`, protected by `datasource:manage`.
- The backend now returns editable presets for:
  - `erp-postgresql`
  - `oa-api`
  - `finance-api`
- API presets include complete `fieldMapping` examples for `rowsPath`, `titleField`, `contentField`, auth, pagination, cursor field, retry count, and sync interval.
- The Vue data-source configuration page reads presets from Java and applies the selected template into the existing save/test/sync form without exposing or pre-filling secrets.
- S4 API contract and backend contract tests now cover the new endpoint and field-level response shape.

## Verification

- RED/GREEN backend test:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#listsEnterpriseDataSourcePresetsForErpOaAndFinanceConfiguration" test`
  - RED failed because `KnowledgeApplicationService.listDataSourcePresets()` did not exist.
  - GREEN passed after adding backend presets and the controller endpoint.
- Frontend contract RED/GREEN:
  - `npm test -- --run src/api/apiContracts.test.ts`
  - RED failed with `knowledgeApi.listDataSourcePresets is not a function`.
  - GREEN passed after adding the API client method.
- Browser acceptance:
  - `npm run e2e -- tests/e2e/data-source-sync.spec.ts`
  - Result: `1/1` passed; selecting the finance preset filled endpoint and API mapping fields before the existing save/test/sync workflow.
- Backend API contract:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointIsDocumentedInApiContract,ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract,ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts" test`
  - Result: `3/3` passed.

## Remaining Gaps

- Run real Docker enterprise database/ERP/OA/finance sync smoke tests.
