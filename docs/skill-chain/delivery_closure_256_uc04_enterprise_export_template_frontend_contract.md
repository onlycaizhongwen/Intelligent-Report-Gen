# Delivery Closure 256: UC-04 Enterprise Export Template Frontend Contract

Date: 2026-07-01

## Target

Close the frontend API contract slice for centralized enterprise export template governance. The web console should expose the backend `/enterprise-export-templates` management surface with request and response types that match the Java contract.

## Changes

- Added frontend contract types:
  - `EnterpriseExportTemplateRequest`
  - `EnterpriseExportTemplate`
- Added optional `templateVersion` to `EnterpriseBrandSnapshot`.
- Added `brandSnapshot` to the `createExport` response type.
- Added `reportApi` methods:
  - `createEnterpriseExportTemplate`
  - `listEnterpriseExportTemplates`
  - `updateEnterpriseExportTemplate`
  - `listEnterpriseExportTemplateVersions`
  - `disableEnterpriseExportTemplate`
  - `enableEnterpriseExportTemplate`
- Corrected the frontend request contract to send `brand` for create/update requests. `brandSnapshot` remains a backend response and persisted audit/status field.

## Verification

RED:

- `npm run test -- src/api/apiContracts.test.ts` failed because `reportApi.createEnterpriseExportTemplate` did not exist.
- After checking the Java service contract, `npm run typecheck` failed because `EnterpriseExportTemplateRequest` rejected the backend-required `brand` request field.

GREEN:

- `npm run test -- src/api/apiContracts.test.ts`: 29 passed.
- `npm run typecheck`: passed.

## Remaining UC-04 Work

- The central enterprise export template management UI is still not implemented.
- This closure does not include a real browser flow or real backend smoke for template management.
- DOCX/PDF/PPTX rendering fidelity remains a separate production hardening stream.
