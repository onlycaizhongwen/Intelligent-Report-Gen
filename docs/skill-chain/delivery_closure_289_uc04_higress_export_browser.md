# Closure 289: UC-04 Higress Export Browser Acceptance

## Scope

- Requirement chain: `REQ-REPORT-004`, `REQ-AUTH-001`
- Use case: `UC-04`
- Target slice: prove the report export browser flow works through local Higress, creates real MinIO artifacts, and gives the customer a controlled download link for PDF and PPTX.
- Runtime route: browser -> Vite `/api/v1` proxy -> Higress `http://127.0.0.1:18000` -> Java report core -> MinIO.

## Result

UC-04 export browser acceptance now has repeatable Higress evidence:

- The browser opens a report detail page and triggers PDF/PPTX export through Higress.
- Java creates the export record, writes the object to MinIO, and returns the controlled download URL.
- The UI keeps the report detail page state and renders a customer-visible `下载导出文件` link instead of navigating the page away.
- The final file download uses a MinIO presigned URL generated with `MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000`, so the signed host matches the browser-downloadable host.
- P0 local smoke now includes `uc04-higress-export-e2e` before the direct-Java `uc04-real-backend-e2e` step.

## Code Evidence

- `scripts/p0-local-smoke-lib.mjs`
- `tests/unit/node/p0_local_smoke_bundle.test.mjs`
- `frontend/web-console/tests/e2e/report-export-real-backend.spec.ts`
- `frontend/web-console/tests/e2e/report-generation.spec.ts`
- `frontend/web-console/src/pages/reports/ReportDetail.vue`
- `backend/java-report-core/src/main/java/com/company/report/report/infrastructure/storage/MinioReportExportStorage.java`
- `backend/java-report-core/src/test/java/com/company/report/report/infrastructure/storage/MinioReportExportStorageTest.java`

## TDD And Runtime Evidence

RED, smoke registration:

```text
node --test tests/unit/node/p0_local_smoke_bundle.test.mjs

failed with:
7 !== 8
```

RED, frontend export UX:

```text
npm run e2e -- tests/e2e/report-generation.spec.ts -g "REQ-REPORT-004"

failed because the page navigated to:
chrome-error://chromewebdata/
```

RED, Java runtime context:

```text
.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test

failed with:
No default constructor found
```

GREEN, Java storage/context regression:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreApplicationContextTest,MinioReportExportStorageTest" test

3 tests passed
```

Docker runtime evidence:

```text
Java image rebuilt:
sha256:d9392a1166529bbb59bd092e9b1d5d2b1ec97e20d791e8603bc6efa27f5af927

ir-java-smoke recreated on intelligent-report-infra_default
port mapping: 18082:8080
alias: java-report-core
MINIO_ENDPOINT=http://minio:9000
MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000
SPRING_PROFILES_ACTIVE=dev

container status: healthy
```

Gateway security evidence:

```text
node scripts/higress-gateway-smoke.mjs

passed=true
/api/v1/chat returned Java auth boundary 401
permission matrix through Higress returned expected 401, 403, and 200 outcomes
```

Runtime browser evidence:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18000
REAL_BACKEND_FRONTEND_PORT=5179
npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts

2 passed
```

Regression evidence:

```text
npm run e2e -- tests/e2e/report-generation.spec.ts -g "REQ-REPORT-004"

1 passed
```

```text
node --test tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/p2_local_smoke_bundle.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs

8 passed
```

## Remaining Gaps

- This closure proves browser -> Higress -> Java -> MinIO export and controlled download for PDF/PPTX. It does not close enterprise template management UI or high-fidelity template rendering governance.
- OIDC login, TLS certificates, WAF behavior, production K8s Gateway API resources, and a full endpoint-by-endpoint gateway authorization matrix remain production hardening work.
