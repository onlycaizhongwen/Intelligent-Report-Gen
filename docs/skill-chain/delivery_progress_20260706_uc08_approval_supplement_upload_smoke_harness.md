# UC-08 Approval Supplement Upload Smoke Harness Progress

Date: 2026-07-06

## Scope

- Requirement: `REQ-RULE-001`
- User journey: `UC-08`
- Prior closure: `docs/skill-chain/delivery_closure_296_uc08_approval_supplement_attachment_upload.md`

## Result

The approval supplement attachment path now has repeatable real-backend and Higress-routed smoke entries.

- Added a real-backend browser acceptance in `frontend/web-console/tests/e2e/approval-inbox-real-backend.spec.ts`.
- The acceptance creates a runnable approval rule, rejects the pending approval, uploads `Supplement attachment`, verifies the upload response returns a `minio://` evidence URL, submits the supplement, and verifies the approval returns to `pending` with a `resubmitted` history record.
- Added direct Java smoke step `uc08-approval-supplement-upload-real-backend-e2e` to `scripts/p3-local-smoke-lib.mjs`.
- Added Higress-routed smoke step `uc08-approval-supplement-upload-higress-e2e` to `scripts/p3-local-smoke-lib.mjs`.
- Updated `docs/skill-chain/api_contract.md` so `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments` has a structured multipart contract.

## Verification

- RED: `node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` failed because P3 had `7` steps instead of the expected `9`.
- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test` failed with missing `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`.
- GREEN: `node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` passed `1/1`.
- GREEN: `npm run typecheck` passed.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test` passed `1/1`.
- Runtime blocker evidence: `docker ps` failed because the Docker daemon was not running; explicit `RUN_REAL_BACKEND_E2E=true` execution failed at `ECONNREFUSED 127.0.0.1:18082`.

## Remaining Gap

The smoke harness is ready, but direct Java/MinIO and Higress-routed runtime acceptance still need to be executed after local Docker services are running again.
