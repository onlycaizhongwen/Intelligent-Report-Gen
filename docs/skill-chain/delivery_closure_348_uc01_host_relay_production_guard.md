# Delivery Closure 348: UC-01 Host Relay Production Guard

## Scope

- Requirements: `REQ-REPORT-001`, `REQ-AI-001`
- User journey: UC-01 intelligent report generation local delivery smoke
- Runtime boundary: local smoke host relay -> Docker report-generation worker -> external OpenAI-compatible provider

## Result

The UC-01 provider host relay now has a programmatic production misuse guard:

- `validateHostRelayRuntime(...)` rejects `NODE_ENV=production`, `SPRING_PROFILES_ACTIVE=prod`, `SPRING_PROFILES_ACTIVE=production`, and equivalent `APP_PROFILE` values by default.
- Both relay entrypoints call the guard:
  - `scripts/uc01-provider-host-relay-start.mjs`
  - `scripts/uc01-provider-host-relay.mjs`
- Local smoke defaults remain unchanged, so P0 can still use the relay for the known local Docker-to-DashScope TLS boundary.

## Evidence

- RED: `node --test tests/unit/node/uc01_provider_host_relay.test.mjs` failed because `validateHostRelayRuntime` was not exported.
- GREEN: `node --test tests/unit/node/uc01_provider_host_relay.test.mjs` passed `5/5`.
- Regression: `node --test tests/unit/node/uc01_provider_host_relay.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs` passed `8/8`.
- Runtime guard: `SPRING_PROFILES_ACTIVE=prod node scripts/uc01-provider-host-relay-start.mjs` exited with code `1` and reported `UC-01 provider host relay is local smoke only and must not run with production profile: prod`.

## Files

- `scripts/uc01-provider-host-relay-lib.mjs`
- `scripts/uc01-provider-host-relay-start.mjs`
- `scripts/uc01-provider-host-relay.mjs`
- `tests/unit/node/uc01_provider_host_relay.test.mjs`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This guard prevents accidental production-profile startup of the local relay. It does not replace production egress design; production LLM traffic still requires direct provider reachability or a managed outbound proxy with proper network, TLS, audit, and operations controls.
