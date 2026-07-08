# Delivery Closure 368: Readiness Report File Output

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/delivery-readiness-audit.mjs`
- Boundary: persisted readiness report artifact for customer/operator evidence collection

## Result

The delivery readiness audit can now persist its JSON or Markdown output to a file by setting:

```bash
DELIVERY_READINESS_REPORT_FILE=docs/skill-chain/generated/production-readiness-action-plan.md \
DELIVERY_READINESS_OUTPUT=markdown \
node scripts/delivery-readiness-audit.mjs
```

Behavior:

- default output remains JSON on stdout
- `DELIVERY_READINESS_OUTPUT=markdown` renders the customer-readable action plan
- `DELIVERY_READINESS_REPORT_FILE=<path>` writes the same output content to the requested file
- parent directories are created automatically

This gives customer delivery a stable report artifact for review and evidence tracking instead of relying on terminal scrollback.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because report formatting and file output helpers were not exported.
- GREEN: the same command passed `12/12`.

## Remaining Risk

Persisting the report does not close the production gates. Target-environment WAF plugin reachability, WAF blocking, trusted TLS, customer OIDC, and credentialed delivery smoke must still pass before `productionReady=true`.
