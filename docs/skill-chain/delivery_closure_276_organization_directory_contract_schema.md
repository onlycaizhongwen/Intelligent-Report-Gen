# Closure 276: Organization Directory Contract Schema

## Scope

- Requirement chain: `REQ-AUTH-001`
- Phase artifact: `S4 API Contract`
- Target endpoints:
  - `GET /api/v1/organization-directory`
  - `POST /api/v1/organization-units`
  - `PUT /api/v1/organization-units/{unitId}`
  - `POST /api/v1/organization-positions`
  - `POST /api/v1/organization-position-assignments`
  - `POST /api/v1/organization-position-assignments/batch-import`
  - `PUT /api/v1/organization-position-assignments/{assignmentId}`
  - `POST /api/v1/organization-position-assignments/{assignmentId}/disable`

## Result

Organization directory, unit, position, and assignment APIs no longer expose only generic `business response data` in the backend supplement. The API contract now declares backend-real field-level response schemas for organization hierarchy and assignment import flows.

- Directory fields: `departments`, `roles`, `organizationTree`
- Unit fields: `unitId`, `code`, `name`, `parentId`, `unitType`, `sortOrder`, `positions`, `children`
- Position fields: `positionId`, `organizationUnitId`, `code`, `name`, `roles`, `managerUserId`, `users`
- Assignment fields: `assignmentId`, `userId`, `positionId`, `primary`, `status`, `activeFrom`, `activeTo`
- Assignment import summary fields: `imported`, `failed`, `items`

## Evidence

- Contract guard: `ContractSurfaceTest#organizationDirectoryEndpointsDeclareFieldLevelResponseContracts`
- API contract: `docs/skill-chain/api_contract.md` section `13.2`
- Backend field source: `PermissionApplicationService#organizationDirectory`, `organizationUnitResponse`, `organizationPositionResponse`, and `organizationPositionAssignmentResponse`

## TDD Evidence

RED:

```text
ContractSurfaceTest.organizationDirectoryEndpointsDeclareFieldLevelResponseContracts
GET /api/v1/organization-directory responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#organizationDirectoryEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Gaps

- This closure did not rerun browser-level organization management E2E or live Higress route smoke.
- Share revoke, system alerts, report template, report diff, and rule-engine endpoints still have generic schemas.
