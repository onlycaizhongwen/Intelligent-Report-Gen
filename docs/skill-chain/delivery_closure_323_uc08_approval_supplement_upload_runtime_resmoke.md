# Delivery Closure 323: UC-08 Approval Supplement Upload Runtime Re-Smoke

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Runtime paths:
  - Browser -> Java `http://127.0.0.1:18082/api/v1` -> MinIO
  - Browser -> Higress `http://127.0.0.1:18000/api/v1` -> Java -> MinIO

## Result

The approval supplement attachment upload runtime path was revalidated after local Docker services became available:

- Reused existing local Docker dependencies: `ir-java-smoke`, `ir-higress`, `ir-postgres`, `ir-minio`, `ir-milvus`, `ir-opensearch`, Redis, and RocketMQ.
- Repackaged `backend/java-report-core` from the latest source with Maven.
- Refreshed `intelligent-report-system-java-report-core:latest` by replacing `/app/app.jar` in the existing runtime image, avoiding new infrastructure downloads after the external Maven base image registry returned `401`.
- Restarted `ir-java-smoke` on the existing `intelligent-report-infra_default` network and port `18082`.
- Confirmed `ir-java-smoke` reached Docker health status `healthy`.
- Confirmed direct Java and Higress-routed browser uploads both returned a MinIO evidence URL through the existing Playwright acceptance.

## Evidence

- Docker dependency check: `docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"` showed the required local middleware and gateway containers running.
- Initial full image rebuild attempt: `docker build -f Dockerfile.java -t intelligent-report-system-java-report-core:latest .` failed before container replacement because `docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-17` returned `401 Unauthorized`.
- Jar build fallback: `.\mvnw.cmd -pl backend/java-report-core -DskipTests package` passed.
- Runtime image refresh: stdin Dockerfile based on `intelligent-report-system-java-report-core:latest` copied `target/java-report-core-0.1.0-SNAPSHOT.jar` to `/app/app.jar` and built successfully.
- Health: `ir-java-smoke` transitioned from `starting` to `healthy`.
- Direct Java browser smoke: `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/approval-inbox-real-backend.spec.ts -g "approval supplement attachment upload"` passed `1/1`.
- Higress browser smoke: `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18000 npm run e2e:real-backend -- tests/e2e/approval-inbox-real-backend.spec.ts -g "approval supplement attachment upload"` passed `1/1`.

## Files

- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`
- `docs/skill-chain/delivery_closure_322_uc08_approval_supplement_attachment_configurable_policy.md`
- `docs/skill-chain/delivery_closure_323_uc08_approval_supplement_upload_runtime_resmoke.md`

## Remaining Risk

- Basic antivirus-signature and content-signature inspection is added in Closure 324; external AV engine integration, archive unpacking, and sandbox/deep parser inspection remain future production hardening.
- The full P3 bundle was not rerun in this closure; this closure targeted the previously pending direct Java and Higress supplement upload runtime acceptance.
