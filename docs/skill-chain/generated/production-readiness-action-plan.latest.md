# Production Readiness Action Plan

Generated: 2026-07-08T08:10:24.736Z

Local ready: true
Production ready: false
Passed local gates: frontend-browser-http, higress-default-security-smoke, higress-local-oidc-test-idp-smoke, local-docker-dependency-health-smoke, report-generation-worker-health-smoke, document-parse-worker-health-smoke, knowledge-index-worker-health-smoke
Passed production gates: none
Production blockers: higress-waf-runtime-preflight, higress-waf-blocking-policy, higress-trusted-tls-certificate, higress-oidc-endpoint-security, credentialed-delivery-smoke

## Passed Local Evidence

### frontend-browser-http

Evidence:
- url: http://127.0.0.1:5173/
- statusCode: 200
- statusText: OK

### higress-default-security-smoke

Evidence:
- gatewayBaseUrl: http://127.0.0.1:18000
- passed: true
- resultCount: 80
- failedResults: none

### higress-local-oidc-test-idp-smoke

Evidence:
- passed: true
- keyId: local-oidc-key
- issuer: https://idp.local.test
- audience: intelligent-report-api
- jwksUrl: http://host.docker.internal:18087/.well-known/jwks.json
- gatewayBaseUrl: http://127.0.0.1:18000
- javaHostPort: 18086
- jwksHostPort: 18087
- containerName: ir-java-oidc-smoke
- privateKey: <redacted>
- resultCount: 3
- oidcResults: [{"name":"oidc-current-user-authorized-through-higress","status":200,"code":200,"classification":"endpoint-security-expected","passed":true},{"name":"oidc-current-user-wrong-issuer-through-higress","status":401,"code":401,"classification":"endpoint-security-expected","passed":true},{"name":"oidc-current-user-wrong-audience-through-higress","status":401,"code":401,"classification":"endpoint-security-expected","passed":true}]

### local-docker-dependency-health-smoke

Evidence:
- passed: true
- classification: local-docker-dependencies-healthy
- resultCount: 10
- failedResults: none

### report-generation-worker-health-smoke

Evidence:
- passed: true
- classification: report-generation-worker-healthy
- containerName: ir-report-generation-worker-smoke
- requiredHealthStatus: healthy
- state: {"status":"running","running":true,"exitCode":0,"error":"","healthStatus":"healthy","healthFailingStreak":0}

### document-parse-worker-health-smoke

Evidence:
- passed: true
- classification: document-parse-worker-healthy
- removedExistingContainer: true
- containerName: ir-document-parse-worker-smoke
- containerId: 93f4dffad43b7df9def800d9090d241df42a0ceda0d3458850bff299ef3dcd4b
- network: intelligent-report-infra_default
- topic: document_parse_requested
- consumerGroup: python-ai-document-parse-smoke
- minioEndpoint: http://ir-minio:9000
- opensearchUrl: http://ir-opensearch:9200
- milvusHost: ir-milvus
- state: {"status":"running","running":true,"exitCode":0,"error":"","healthStatus":"healthy","healthFailingStreak":0}

### knowledge-index-worker-health-smoke

Evidence:
- passed: true
- classification: knowledge-index-workers-running
- workerCount: 2
- workers: [{"role":"knowledge-index-cleanup","passed":true,"classification":"knowledge-index-cleanup-healthy","removedExistingContainer":true,"containerName":"ir-knowledge-index-cleanup-worker-smoke","containerId":"83962fc6e483acf44b3d3c4aeb1e36681badeefad255498f088d788145e05a11","network":"intelligent-report-infra_default","opensearchUrl":"http://ir-opensearch:9200","milvusHost":"ir-milvus","state":{"status":"running","running":true,"exitCode":0,"error":"","healthStatus":"healthy","healthFailingStreak":0}},{"role":"knowledge-item-index","passed":true,"classification":"knowledge-item-index-healthy","removedExistingContainer":true,"containerName":"ir-knowledge-item-index-worker-smoke","containerId":"f3c286bcf49a675208ad3c168d903eec5b0f9db85ad2f41bebe0b04fa677bfd5","network":"intelligent-report-infra_default","opensearchUrl":"http://ir-opensearch:9200","milvusHost":"ir-milvus","state":{"status":"running","running":true,"exitCode":0,"error":"","healthStatus":"healthy","healthFailingStreak":0}}]

## higress-waf-runtime-preflight

Status: failed
Description: Gateway WAF plugin OCI image must be reachable before enabling the blocking policy.
Required inputs: `HIGRESS_WAF_PLUGIN_URL`
Optional inputs: `none`

Commands:

```bash
HIGRESS_WAF_PLUGIN_URL=<plugin-oci-url> node scripts/higress-waf-runtime-preflight.mjs
```

Next action: mirror the approved Higress WAF OCI plugin into a registry reachable from the Higress runtime, set HIGRESS_WAF_PLUGIN_URL, then rerun the runtime preflight before enabling WAF.
Required evidence: Preflight returns passed=true and containerRegistryReachable=true for the configured plugin registry.

Observed:
- status: failed
- classification: waf-plugin-container-registry-unreachable

## higress-waf-blocking-policy

Status: blocked
Description: Gateway WAF blocking smoke requires an explicit non-local target gateway URL.
Required inputs: `HIGRESS_GATEWAY_BASE_URL`, `HIGRESS_WAF_BLOCKING_COVERAGE`
Optional inputs: `none`

Commands:

```bash
HIGRESS_GATEWAY_BASE_URL=<target-gateway-url> HIGRESS_WAF_BLOCKING_COVERAGE=true node scripts/higress-gateway-smoke.mjs
```

Next action: enable the approved Higress WAF policy only after the runtime plugin preflight passes, set the target HIGRESS_GATEWAY_BASE_URL, then prove SQLi, XSS, path traversal, and prompt-injection probes are blocked at that gateway.
Required evidence: Gateway WAF blocking smoke returns passed=true with no waf-not-blocked failedResults.

Observed:
- status: blocked
- missingEnv: HIGRESS_GATEWAY_BASE_URL

## higress-trusted-tls-certificate

Status: failed
Description: Gateway TLS certificate must be trusted and valid for the configured minimum window.
Required inputs: `HIGRESS_TLS_GATEWAY_HOST`, `HIGRESS_TLS_SERVER_NAME`
Optional inputs: `HIGRESS_TLS_CA_FILE`

Commands:

```bash
HIGRESS_TLS_GATEWAY_HOST=<gateway-host> HIGRESS_TLS_SERVER_NAME=<server-name> node scripts/higress-tls-certificate-smoke.mjs
```

Next action: install a trusted gateway certificate for the customer hostname, configure hostname/servername and optional private CA bundle, then rerun the TLS smoke with verification enabled.
Required evidence: TLS smoke returns passed=true/classification=tls-trusted with daysRemaining above the configured minimum.

Observed:
- status: failed
- classification: tls-untrusted
- authorizationError: DEPTH_ZERO_SELF_SIGNED_CERT

## higress-oidc-endpoint-security

Status: blocked
Description: Gateway OIDC smoke requires either customer token-suite evidence or a customer/test IdP signing configuration.
Required inputs: `HIGRESS_GATEWAY_BASE_URL`, `HIGRESS_OIDC_ACCEPTED_TOKEN`, `HIGRESS_OIDC_WRONG_ISSUER_TOKEN`, `HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN`
Optional inputs: `none`
Input options:
- customer-token-suite: `HIGRESS_GATEWAY_BASE_URL`, `HIGRESS_OIDC_ACCEPTED_TOKEN`, `HIGRESS_OIDC_WRONG_ISSUER_TOKEN`, `HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN`; evidence: Accepted token succeeds while wrong issuer and wrong audience tokens are rejected through Higress.
- signing-jwks-test-configuration: `HIGRESS_GATEWAY_BASE_URL`, `HIGRESS_OIDC_PRIVATE_KEY_FILE or HIGRESS_OIDC_PRIVATE_KEY_PEM`, `HIGRESS_OIDC_KEY_ID`, `OIDC_ISSUER`, `OIDC_AUDIENCE`; evidence: Generated RS256/JWKS probes prove accepted issuer/audience succeeds and wrong issuer/audience are rejected through Higress.

Commands:

```bash
HIGRESS_GATEWAY_BASE_URL=<target-gateway-url> HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE=true node scripts/higress-gateway-smoke.mjs
```

Next action: set the target HIGRESS_GATEWAY_BASE_URL, provide a customer token suite or signing/JWKS test configuration, then prove accepted issuer/audience succeeds and wrong issuer/audience are rejected through Higress.
Required evidence: OIDC endpoint security smoke returns passed=true for accepted-token 200 and wrong issuer/audience 401 probes.

Observed:
- status: blocked
- missingEnv: HIGRESS_GATEWAY_BASE_URL; HIGRESS_OIDC_ACCEPTED_TOKEN + HIGRESS_OIDC_WRONG_ISSUER_TOKEN + HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN; or HIGRESS_OIDC_PRIVATE_KEY_FILE/HIGRESS_OIDC_PRIVATE_KEY_PEM + HIGRESS_OIDC_KEY_ID + OIDC_ISSUER + OIDC_AUDIENCE

## credentialed-delivery-smoke

Status: blocked
Description: Full P0-P3 delivery smoke requires a real external model provider key.
Required inputs: `DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY`
Optional inputs: `none`

Commands:

```bash
DELIVERY_SMOKE_DASHSCOPE_API_KEY=<provider-api-key> node scripts/delivery-local-smoke.mjs
```

Next action: provide a real external model provider key and run the full P0-P3 delivery smoke against the target environment.
Required evidence: Credentialed delivery smoke completes P0, P1, P2, and P3 with passed status.

Observed:
- status: blocked
- missingEnv: DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY
