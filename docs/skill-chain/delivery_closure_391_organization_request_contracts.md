# Delivery Closure 391: Organization Directory Request Contracts

Date: 2026-07-08

## Scope

Closed the next customer-review gap in S4/API contract evidence for RBAC and approval-assignee initialization.

The following endpoints now declare field-level JSON request contracts instead of a generic `business request body` placeholder:

- `POST /api/v1/organization-units`
- `PUT /api/v1/organization-units/{unitId}`
- `POST /api/v1/organization-positions`
- `POST /api/v1/organization-position-assignments`
- `POST /api/v1/organization-position-assignments/batch-import`
- `PUT /api/v1/organization-position-assignments/{assignmentId}`
- `POST /api/v1/organization-position-assignments/{assignmentId}/disable`

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#organizationMutationEndpointsDeclareFieldLevelRequestContracts" test` failed because `POST /api/v1/organization-units requestBody.properties` was missing.
- GREEN: the same targeted test passed after the API contract declared organization unit, position, and assignment request fields at field level.
- JSON parse check confirmed all API contract JSON blocks remain parseable.

## Remaining Risk

This closes the organization directory request-contract gap only. Production WAF/TLS/OIDC evidence and the remaining generic request-contract placeholders are separate delivery gates.
