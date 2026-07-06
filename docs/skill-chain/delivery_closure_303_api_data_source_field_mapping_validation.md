# Delivery Closure 303: API Data Source Field Mapping Validation

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: validate source-specific API connector mappings before saving ingesting data sources.

## Result

- API data sources that target a knowledge base now require:
  - `fieldMapping.rowsPath`
  - `fieldMapping.titleField`
  - `fieldMapping.contentField`
- The rule applies only when `sourceType=api` and `knowledgeBaseId` is configured, so lightweight API connection tests can still be saved without ingestion mapping.
- S4 API contract documentation now states the conditional mapping requirement.

## Verification

- RED/GREEN validation test:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#rejectsApiKnowledgeDataSourceWithoutRequiredFieldMapping" test`
  - RED failed because the incomplete API mapping was saved successfully.
  - GREEN passed after adding save-time validation.
- API connector regression:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#rejectsApiKnowledgeDataSourceWithoutRequiredFieldMapping,KnowledgeApplicationServiceTest#syncsHttpApiRowsWithFieldMappingAndCursorIntoKnowledgeItems,KnowledgeApplicationServiceTest#syncsHttpApiRowsWithPostBodyCustomHeadersAndApiKey,KnowledgeApplicationServiceTest#syncsHttpApiRowsAcrossConfiguredPages,KnowledgeApplicationServiceTest#testsHttpApiDataSourceConnectionWithBearerToken" test`
  - Result: `5/5` tests passed.
- Broader Java regression before commit:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ContractSurfaceTest,ReportCoreApplicationContextTest,ReportCoreProdProfileContextTest" test`
  - Result: `66/66` tests passed.
- Static diff check to run before commit:
  - `git diff --check`
  - Result: no whitespace errors; Git reported expected LF-to-CRLF working-tree warnings.

## Remaining Gaps

- Add ERP/OA/finance source template presets for faster customer configuration.
- Run real Docker enterprise database/ERP/OA/finance sync smoke tests.
- Run live Higress route checks for data-source configuration and sync endpoints.
