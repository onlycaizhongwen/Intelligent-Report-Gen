# Delivery Closure 392: Enterprise Export Template Request Contracts

Date: 2026-07-08

## Scope

Closed the S4/API contract review gap for enterprise export template governance, which supports report layout, headers, footers, table-of-contents settings, and brand standards.

The following endpoints now declare field-level JSON request contracts instead of a generic `business request body` placeholder:

- `POST /api/v1/enterprise-export-templates`
- `PUT /api/v1/enterprise-export-templates/{templateId}`
- `POST /api/v1/enterprise-export-templates/{templateId}/disable`
- `POST /api/v1/enterprise-export-templates/{templateId}/enable`

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#enterpriseExportTemplateEndpointsDeclareFieldLevelRequestContracts" test` failed because `POST /api/v1/enterprise-export-templates requestBody.properties` was missing.
- GREEN: the same targeted test passed after the API contract declared `templateId/name/brand`, `name/brand`, and explicit empty-object semantics for enable/disable.
- JSON parse check confirmed all API contract JSON blocks remain parseable.

## Remaining Risk

This closes enterprise export template request-contract review evidence only. Remaining generic request-body placeholders are concentrated in rule governance/runtime endpoints, and production WAF/TLS/OIDC evidence remains external.
