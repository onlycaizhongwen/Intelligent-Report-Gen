# Delivery Closure 265: API Contract Controller Coverage

## Scope

- Requirement scope: S4 API contract, OpenSpec traceability, controller/API documentation drift prevention.
- Production risk closed: the Java backend had many implemented controller endpoints that were not present in `docs/skill-chain/api_contract.md`, so customer-facing API review could miss actual production routes.

## Result

API contract coverage now has a source-level regression test:

- Scans all Java `*Controller.java` files.
- Resolves class-level `@RequestMapping` plus method-level `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, and `@PatchMapping`.
- Fails if an implemented endpoint is not documented in `docs/skill-chain/api_contract.md`.
- Added an API contract supplement for currently implemented endpoints, including knowledge batch import, data source sync runs, system alerts, organization directory maintenance, enterprise export templates, report worker callbacks, report version diff, approval template governance, approval records, rule runtime metrics, and action retry endpoints.

## Code Evidence

- `backend/java-report-core/src/test/java/com/company/report/contract/ContractSurfaceTest.java`
  - `everyControllerEndpointIsDocumentedInApiContract`
  - `controllerBasePath`
  - `mappingPath`
  - `normalizePath`
- `docs/skill-chain/api_contract.md`
  - `13.1 后端实际端点覆盖补遗`

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointIsDocumentedInApiContract" test`
  - Failed with undocumented implemented endpoints including `POST /api/v1/knowledge-items/batch-import`, `GET /api/v1/auth/me`, enterprise export template endpoints, rule approval template endpoints, rule action retry endpoints, and others.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointIsDocumentedInApiContract" test`
  - `1/1` test passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test`
  - `22/22` tests passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest,SecurityRuntimeContractTest" test`
  - `34/34` tests passed.

## Residual Risk

The supplement proves endpoint presence in the contract, not full request/response JSON completeness for every endpoint. A later S4 hardening pass should generate structured JSON request/response examples for the newly covered endpoints.
