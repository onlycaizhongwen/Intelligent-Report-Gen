# Closure 317: P3 approval supplement smoke repair

## Scope

- Requirement: `REQ-RULE-001`
- User journey: approval inbox rejection -> supplement-required evidence -> MinIO attachment upload -> supplement submission -> pending approval returns for review.
- Trigger: `node scripts/p3-local-smoke.mjs` failed in `uc08-real-backend-e2e` on `approval-inbox-real-backend.spec.ts`.

## Root Cause

- The browser clicked `Reject`, but the Java backend returned a generic `500` shown by the UI as `系统繁忙，请稍后重试`.
- Container logs showed PostgreSQL rejected an insert into `rule_approval_records`:
  - `null value in column "assignee_role" ... violates not-null constraint`
  - failing path: `RuleApplicationService.createSupplementRequest(...)`
- The supplement-required approval record was created without `assigneeRole`, `delegateRole`, and delegate active-window fields.

## Fix

- `RuleApplicationService.createSupplementRequest(...)` now copies the rejected approval record's:
  - `assigneeRole`
  - `delegateRole`
  - `delegateActiveFrom`
  - `delegateActiveTo`
- `RuleApplicationServiceTest` now asserts a rejected approval creates a `supplement_required` record with the original `assigneeRole`.
- The multi-assignee rejection regression now selects original approval records by `approvalRecordId`, because supplement-required history records can legitimately share the same assignee role.

## Local Runtime Refresh

- Normal Docker build first failed because configured mirror `docker.m.daocloud.io/library/eclipse-temurin:17-jre-alpine` returned `401`.
- Official Docker Hub retry also failed due local network timeout to `auth.docker.io`.
- To avoid adding new dependencies, the current Maven-built jar was copied into a temporary container based on the existing local `intelligent-report-system-java-report-core:latest` image, then committed as a refreshed local image.
- `ir-java-smoke` was replaced with the refreshed image, preserving:
  - network: `intelligent-report-infra_default`
  - port: `18082:8080`
  - profile: `SPRING_PROFILES_ACTIVE=dev`
  - existing PostgreSQL, MinIO, RocketMQ endpoints

## Verification

- RED: `./mvnw -pl backend/java-report-core -Dtest=RuleApplicationServiceTest#rejectsPendingApprovalRecordAndPreventsRepeatedHandling test`
  - failed because `assigneeRole` was `null` on the `supplement_required` record.
- GREEN targeted regression:
  - `./mvnw -pl backend/java-report-core -Dtest=RuleApplicationServiceTest#rejectsPendingApprovalRecordAndPreventsRepeatedHandling test`
  - result: `1/1` passed.
- Rule application regression:
  - `./mvnw -pl backend/java-report-core -Dtest=RuleApplicationServiceTest test`
  - result: `89/89` passed.
- Java module regression:
  - `./mvnw -pl backend/java-report-core test`
  - result: `300/300` passed.
- Java smoke container:
  - `GET http://127.0.0.1:18082/actuator/health`
  - result: `{"status":"UP"}`.
- Target real-backend browser E2E:
  - `RUN_REAL_BACKEND_E2E=true ... npm run e2e:real-backend -- tests/e2e/approval-inbox-real-backend.spec.ts -g "approval supplement attachment upload"`
  - result: `1/1` passed.
- Approval inbox real-backend browser E2E:
  - `RUN_REAL_BACKEND_E2E=true ... npm run e2e:real-backend -- tests/e2e/approval-inbox-real-backend.spec.ts`
  - result: `5/5` passed.
- Full P3 local smoke bundle:
  - `node scripts/p3-local-smoke.mjs`
  - result: all 10 planned steps completed.
  - Included `uc08-approval-supplement-upload-real-backend-e2e` and `uc08-approval-supplement-upload-higress-e2e`.

## Remaining Risk

- This closes the P3 smoke regression for approval supplement uploads.
- Docker base image pulls remain dependent on local registry/network availability; the local image refresh used an existing runtime image to keep the development loop unblocked.
