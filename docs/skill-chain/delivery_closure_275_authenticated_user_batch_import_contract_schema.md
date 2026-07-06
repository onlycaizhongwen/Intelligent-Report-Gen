# Closure 275: Authenticated User and Batch Import Contract Schema

## Scope

- Requirement chain: `REQ-AUTH-001`
- Phase artifact: `S4 API Contract`
- Target endpoints:
  - `GET /api/v1/auth/me`
  - `POST /api/v1/users/batch-import`

## Result

Authenticated user profile and user batch-import APIs no longer expose only generic `business response data` in the backend supplement. The API contract now declares backend-real field-level response schemas.

- Current profile fields: `userId`, `displayName`, `roles`, `permissions`, `status`, `authProvider`
- Batch import summary fields: `imported`, `failed`, `items`
- Batch import row fields: `username`, `status`, `reason`, `userId`, `displayName`, `department`, `position`, `roles`

## Evidence

- Contract guard: `ContractSurfaceTest#authenticatedUserAndBatchImportEndpointsDeclareFieldLevelResponseContracts`
- API contract: `docs/skill-chain/api_contract.md` section `13.2`
- Backend field source: `PermissionController#currentUser` and `PermissionApplicationService#batchImportUsers`

## TDD Evidence

RED:

```text
ContractSurfaceTest.authenticatedUserAndBatchImportEndpointsDeclareFieldLevelResponseContracts
GET /api/v1/auth/me responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#authenticatedUserAndBatchImportEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Gaps

- This closure did not rerun live Higress/OIDC login smoke.
- Organization directory and organization mutation endpoints still have generic schemas and should be deepened in later closures.
