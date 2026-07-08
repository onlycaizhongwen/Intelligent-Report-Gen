# Closure 386: Local Docker Dependency Health Gate

Date: 2026-07-08

## Scope

- Requirement: local customer handoff readiness for the prototype journeys and P0-P3 smoke chain.
- Delivery gate: required Docker dependencies must be explicitly healthy before local readiness is accepted.
- Risk closed: `localReady=true` could previously depend on downstream smoke success without showing first-class evidence for shared local services such as PostgreSQL, Redis, MinIO, RocketMQ, OpenSearch, Milvus, Java API, Higress, and etcd.

## Change

- Added `scripts/local-docker-dependency-health-smoke-lib.mjs` and `scripts/local-docker-dependency-health-smoke.mjs`.
- The smoke inspects each required container with `docker inspect --format '{{json .State}}'`.
- Containers with healthchecks must be `running` and `healthy`.
- Containers without healthchecks fail closed unless they are explicitly allowlisted as no-healthcheck runtime infrastructure. Current allowlist: `ir-higress`, `ir-etcd`.
- `buildDeliveryReadinessChecks(...)` now includes `local-docker-dependency-health-smoke` as a required local gate before the UC-06 document parse worker health gate.
- Latest generated production readiness Markdown now renders the local Docker dependency gate under `Passed Local Evidence`.

## Verification

- RED: `node --test tests/unit/node/local_docker_dependency_health_smoke.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` failed because the local Docker dependency smoke library and readiness gate were missing.
- GREEN: the same command passed `29/29` after implementation.
- Runtime local dependency smoke: `node scripts/local-docker-dependency-health-smoke.mjs` passed with `classification=local-docker-dependencies-healthy`, `resultCount=10`, and `failedResults=[]`.
- RED generated handoff guard: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `docs/skill-chain/generated/production-readiness-action-plan.latest.md` did not include `local-docker-dependency-health-smoke`.
- Runtime readiness regeneration: `DELIVERY_READINESS_OUTPUT=markdown DELIVERY_READINESS_REPORT_FILE=docs/skill-chain/generated/production-readiness-action-plan.latest.md DELIVERY_READINESS_ENV_TEMPLATE_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/delivery-readiness-audit.mjs` exited non-zero as expected for production blockers while reporting:
  - `Local ready: true`
  - `Passed local gates: frontend-browser-http, higress-default-security-smoke, higress-local-oidc-test-idp-smoke, local-docker-dependency-health-smoke, document-parse-worker-health-smoke`
  - `local-docker-dependency-health-smoke classification=local-docker-dependencies-healthy`
  - `credentialed-delivery-smoke` passed with P0-P3 completed.

## Remaining Risk

- This improves local customer handoff evidence and diagnosis for the Docker development stack.
- It does not provide customer target WAF plugin reachability, active WAF blocking, trusted TLS certificate chain, customer OIDC token-suite/signing evidence, or target-environment production smoke evidence.
