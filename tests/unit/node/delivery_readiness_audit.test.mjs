import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildProductionReadinessActionPlan,
  buildDeliveryReadinessChecks,
  classifyDeliveryReadinessResult,
  formatDeliveryReadinessAuditOutput,
  loadDeliveryReadinessEnv,
  mergeEnvFileValues,
  parseEnvFileText,
  renderProductionReadinessActionPlanMarkdown,
  renderProductionReadinessEnvTemplate,
  summarizeDeliveryReadiness,
  validateProductionReadinessEnv,
  writeDeliveryReadinessReportFile,
} from '../../../scripts/delivery-readiness-audit-lib.mjs';

test('buildDeliveryReadinessChecks separates local evidence from production gates without secrets', () => {
  const checks = buildDeliveryReadinessChecks({
    env: {
      DELIVERY_SMOKE_DASHSCOPE_API_KEY: 'secret-key',
      HIGRESS_OIDC_PRIVATE_KEY_PEM: 'secret-pem',
      HIGRESS_OIDC_KEY_ID: 'kid-1',
      OIDC_ISSUER: 'https://idp.example.test',
      OIDC_AUDIENCE: 'intelligent-report',
    },
  });

  assert.deepEqual(
    checks.map((check) => check.name),
    [
      'frontend-browser-http',
      'higress-default-security-smoke',
      'higress-local-oidc-test-idp-smoke',
      'higress-waf-runtime-preflight',
      'higress-waf-blocking-policy',
      'higress-trusted-tls-certificate',
      'higress-oidc-endpoint-security',
      'credentialed-delivery-smoke',
    ],
  );

  assert.equal(checks[0].scope, 'local');
  assert.equal(checks[2].scope, 'local');
  assert.deepEqual(checks[2].args, ['scripts/higress-oidc-local-smoke.mjs']);
  assert.equal(checks[2].timeoutMs, 180_000);
  assert.equal(checks[3].scope, 'production');
  assert.deepEqual(checks[3].args, ['scripts/higress-waf-runtime-preflight.mjs']);
  assert.equal(checks[3].timeoutMs, 45_000);
  assert.equal(checks[6].kind, 'command');
  assert.equal(checks[6].env.HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE, 'true');
  assert.equal(checks[6].timeoutMs, 120_000);
  assert.equal(checks[7].env.DELIVERY_SMOKE_DASHSCOPE_API_KEY, '<provided>');
  assert.equal(checks[7].timeoutMs, 300_000);
  assert.equal(JSON.stringify(checks), JSON.stringify(checks).replace('secret-key', '<leaked>'));
  assert.equal(JSON.stringify(checks), JSON.stringify(checks).replace('secret-pem', '<leaked>'));
});

test('buildDeliveryReadinessChecks marks credential-gated production checks as blocked when inputs are missing', () => {
  const checks = buildDeliveryReadinessChecks({ env: {} });
  const oidc = checks.find((check) => check.name === 'higress-oidc-endpoint-security');
  const localOidc = checks.find((check) => check.name === 'higress-local-oidc-test-idp-smoke');
  const delivery = checks.find((check) => check.name === 'credentialed-delivery-smoke');

  assert.equal(localOidc.kind, 'command');
  assert.equal(localOidc.scope, 'local');
  assert.deepEqual(localOidc.args, ['scripts/higress-oidc-local-smoke.mjs']);

  assert.equal(oidc.kind, 'blocked');
  assert.equal(oidc.status, 'blocked');
  assert.deepEqual(oidc.missingEnv, [
    'HIGRESS_OIDC_ACCEPTED_TOKEN + HIGRESS_OIDC_WRONG_ISSUER_TOKEN + HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
    'or HIGRESS_OIDC_PRIVATE_KEY_FILE/HIGRESS_OIDC_PRIVATE_KEY_PEM + HIGRESS_OIDC_KEY_ID + OIDC_ISSUER + OIDC_AUDIENCE',
  ]);

  assert.equal(delivery.kind, 'blocked');
  assert.equal(delivery.status, 'blocked');
  assert.deepEqual(delivery.missingEnv, ['DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY']);
});

test('buildDeliveryReadinessChecks allows customer OIDC token-suite evidence without leaking tokens', () => {
  const checks = buildDeliveryReadinessChecks({
    env: {
      HIGRESS_OIDC_ACCEPTED_TOKEN: 'accepted.jwt.value',
      HIGRESS_OIDC_WRONG_ISSUER_TOKEN: 'wrong.issuer.jwt',
      HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN: 'wrong.audience.jwt',
    },
  });
  const oidc = checks.find((check) => check.name === 'higress-oidc-endpoint-security');

  assert.equal(oidc.kind, 'command');
  assert.equal(oidc.scope, 'production');
  assert.deepEqual(oidc.env, {
    HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE: 'true',
    HIGRESS_OIDC_ACCEPTED_TOKEN: '<provided>',
    HIGRESS_OIDC_WRONG_ISSUER_TOKEN: '<provided>',
    HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN: '<provided>',
  });
  assert.equal(JSON.stringify(checks), JSON.stringify(checks).replace('accepted.jwt.value', '<leaked>'));
  assert.equal(JSON.stringify(checks), JSON.stringify(checks).replace('wrong.issuer.jwt', '<leaked>'));
  assert.equal(JSON.stringify(checks), JSON.stringify(checks).replace('wrong.audience.jwt', '<leaked>'));
});

test('buildDeliveryReadinessChecks marks WAF plugin URL override as provided without leaking it', () => {
  const checks = buildDeliveryReadinessChecks({
    env: {
      HIGRESS_WAF_PLUGIN_URL: 'oci://user:secret@registry.customer.example/platform/higress-waf:2.0.0',
    },
  });
  const wafPreflight = checks.find((check) => check.name === 'higress-waf-runtime-preflight');

  assert.equal(wafPreflight.kind, 'command');
  assert.equal(wafPreflight.scope, 'production');
  assert.equal(wafPreflight.env.HIGRESS_WAF_PLUGIN_URL, '<provided>');
  assert.equal(
    JSON.stringify(checks),
    JSON.stringify(checks).replace('user:secret@registry.customer.example', '<leaked>'),
  );
});

test('classifyDeliveryReadinessResult turns command exits into readiness outcomes', () => {
  assert.deepEqual(
    classifyDeliveryReadinessResult(
      { name: 'higress-default-security-smoke', scope: 'local', required: true },
      { exitCode: 0, stdout: '{"passed":true}' },
    ),
    {
      name: 'higress-default-security-smoke',
      scope: 'local',
      status: 'passed',
      required: true,
      evidence: { passed: true },
    },
  );

  assert.deepEqual(
    classifyDeliveryReadinessResult(
      { name: 'higress-trusted-tls-certificate', scope: 'production', required: true },
      {
        exitCode: 1,
        stdout: '{"passed":false,"classification":"tls-untrusted","authorizationError":"DEPTH_ZERO_SELF_SIGNED_CERT"}',
      },
    ),
    {
      name: 'higress-trusted-tls-certificate',
      scope: 'production',
      status: 'failed',
      required: true,
      evidence: {
        passed: false,
        classification: 'tls-untrusted',
        authorizationError: 'DEPTH_ZERO_SELF_SIGNED_CERT',
      },
    },
  );
});

test('classifyDeliveryReadinessResult compacts nested smoke evidence', () => {
  const outcome = classifyDeliveryReadinessResult(
    { name: 'higress-default-security-smoke', scope: 'local', required: true },
    {
      exitCode: 0,
      stdout: JSON.stringify({
        passed: true,
        results: [
          { name: 'a', passed: true, bodyPreview: 'large body' },
          { name: 'b', passed: false, status: 500, bodyPreview: 'large failure body' },
        ],
      }),
    },
  );

  assert.deepEqual(outcome.evidence, {
    passed: true,
    resultCount: 2,
    failedResults: [
      {
        name: 'b',
        status: 500,
        classification: undefined,
        passed: false,
      },
    ],
  });
});

test('classifyDeliveryReadinessResult compacts OIDC local smoke evidence', () => {
  const outcome = classifyDeliveryReadinessResult(
    { name: 'higress-local-oidc-test-idp-smoke', scope: 'local', required: true },
    {
      exitCode: 0,
      stdout: JSON.stringify({
        passed: true,
        privateKey: '<redacted>',
        resultCount: 3,
        oidcResults: [
          {
            name: 'oidc-current-user-authorized-through-higress',
            status: 200,
            code: 200,
            classification: 'endpoint-security-expected',
            passed: true,
            bodyPreview: 'large accepted user payload',
          },
          {
            name: 'oidc-current-user-wrong-issuer-through-higress',
            status: 401,
            code: 401,
            classification: 'endpoint-security-expected',
            passed: true,
            bodyPreview: 'large rejected payload',
          },
        ],
      }),
    },
  );

  assert.deepEqual(outcome.evidence.oidcResults, [
    {
      name: 'oidc-current-user-authorized-through-higress',
      status: 200,
      code: 200,
      classification: 'endpoint-security-expected',
      passed: true,
    },
    {
      name: 'oidc-current-user-wrong-issuer-through-higress',
      status: 401,
      code: 401,
      classification: 'endpoint-security-expected',
      passed: true,
    },
  ]);
});

test('classifyDeliveryReadinessResult records command timeout evidence', () => {
  assert.deepEqual(
    classifyDeliveryReadinessResult(
      { name: 'higress-local-oidc-test-idp-smoke', scope: 'local', required: true },
      {
        exitCode: null,
        stdout: '',
        stderr: '',
        timedOut: true,
        signal: 'SIGTERM',
        timeoutMs: 180_000,
      },
    ),
    {
      name: 'higress-local-oidc-test-idp-smoke',
      scope: 'local',
      status: 'failed',
      required: true,
      evidence: {
        classification: 'command-timeout',
        timedOut: true,
        signal: 'SIGTERM',
        timeoutMs: 180_000,
      },
    },
  );
});

test('summarizeDeliveryReadiness keeps production readiness false for blocked production gates', () => {
  const summary = summarizeDeliveryReadiness([
    { name: 'frontend-browser-http', scope: 'local', status: 'passed', required: true },
    { name: 'higress-default-security-smoke', scope: 'local', status: 'passed', required: true },
    { name: 'credentialed-delivery-smoke', scope: 'production', status: 'passed', required: true },
    { name: 'higress-waf-blocking-policy', scope: 'production', status: 'blocked', required: true },
    { name: 'higress-trusted-tls-certificate', scope: 'production', status: 'failed', required: true },
  ]);

  assert.deepEqual(summary, {
    localReady: true,
    productionReady: false,
    total: 5,
    passed: 3,
    failed: 1,
    blocked: 1,
    localPassedItems: ['frontend-browser-http', 'higress-default-security-smoke'],
    productionPassedItems: ['credentialed-delivery-smoke'],
    localBlockingItems: [],
    productionBlockingItems: ['higress-waf-blocking-policy', 'higress-trusted-tls-certificate'],
  });
});

test('buildProductionReadinessActionPlan converts production blockers into customer evidence steps', () => {
  const checks = buildDeliveryReadinessChecks({ env: {} });
  const results = [
    { name: 'frontend-browser-http', scope: 'local', status: 'passed', required: true, evidence: { statusCode: 200 } },
    {
      name: 'higress-waf-runtime-preflight',
      scope: 'production',
      status: 'failed',
      required: true,
      evidence: { classification: 'waf-plugin-container-registry-unreachable' },
    },
    {
      name: 'higress-waf-blocking-policy',
      scope: 'production',
      status: 'failed',
      required: true,
      evidence: { failedResults: [{ name: 'report-list-sqli-waf-block-through-higress' }] },
    },
    {
      name: 'higress-trusted-tls-certificate',
      scope: 'production',
      status: 'failed',
      required: true,
      evidence: { authorizationError: 'DEPTH_ZERO_SELF_SIGNED_CERT' },
    },
    {
      name: 'higress-oidc-endpoint-security',
      scope: 'production',
      status: 'blocked',
      required: true,
      evidence: {
        missingEnv: [
          'HIGRESS_OIDC_ACCEPTED_TOKEN + HIGRESS_OIDC_WRONG_ISSUER_TOKEN + HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
        ],
      },
    },
  ];

  const plan = buildProductionReadinessActionPlan({ checks, results });

  assert.equal(plan.ready, false);
  assert.deepEqual(
    plan.blockingItems.map((item) => item.name),
    [
      'higress-waf-runtime-preflight',
      'higress-waf-blocking-policy',
      'higress-trusted-tls-certificate',
      'higress-oidc-endpoint-security',
    ],
  );
  assert.deepEqual(plan.blockingItems[0].requiredInputs, ['HIGRESS_WAF_PLUGIN_URL']);
  assert.deepEqual(plan.blockingItems[0].commands, ['HIGRESS_WAF_PLUGIN_URL=<plugin-oci-url> node scripts/higress-waf-runtime-preflight.mjs']);
  assert.match(plan.blockingItems[0].nextAction, /mirror/);
  assert.deepEqual(plan.blockingItems[1].requiredInputs, [
    'HIGRESS_GATEWAY_BASE_URL',
    'HIGRESS_WAF_BLOCKING_COVERAGE',
  ]);
  assert.deepEqual(plan.blockingItems[1].commands, ['HIGRESS_GATEWAY_BASE_URL=<target-gateway-url> HIGRESS_WAF_BLOCKING_COVERAGE=true node scripts/higress-gateway-smoke.mjs']);
  assert.deepEqual(plan.blockingItems[2].requiredInputs, [
    'HIGRESS_TLS_GATEWAY_HOST',
    'HIGRESS_TLS_SERVER_NAME',
  ]);
  assert.deepEqual(plan.blockingItems[2].optionalInputs, ['HIGRESS_TLS_CA_FILE']);
  assert.deepEqual(plan.blockingItems[2].commands, ['HIGRESS_TLS_GATEWAY_HOST=<gateway-host> HIGRESS_TLS_SERVER_NAME=<server-name> node scripts/higress-tls-certificate-smoke.mjs']);
  assert.deepEqual(plan.blockingItems[3].requiredInputs, [
    'HIGRESS_GATEWAY_BASE_URL',
    'HIGRESS_OIDC_ACCEPTED_TOKEN',
    'HIGRESS_OIDC_WRONG_ISSUER_TOKEN',
    'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
  ]);
  assert.deepEqual(plan.blockingItems[3].inputOptions, [
    {
      name: 'customer-token-suite',
      requiredInputs: [
        'HIGRESS_GATEWAY_BASE_URL',
        'HIGRESS_OIDC_ACCEPTED_TOKEN',
        'HIGRESS_OIDC_WRONG_ISSUER_TOKEN',
        'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
      ],
      requiredEvidence: 'Accepted token succeeds while wrong issuer and wrong audience tokens are rejected through Higress.',
    },
    {
      name: 'signing-jwks-test-configuration',
      requiredInputs: [
        'HIGRESS_GATEWAY_BASE_URL',
        'HIGRESS_OIDC_PRIVATE_KEY_FILE or HIGRESS_OIDC_PRIVATE_KEY_PEM',
        'HIGRESS_OIDC_KEY_ID',
        'OIDC_ISSUER',
        'OIDC_AUDIENCE',
      ],
      requiredEvidence: 'Generated RS256/JWKS probes prove accepted issuer/audience succeeds and wrong issuer/audience are rejected through Higress.',
    },
  ]);
  assert.equal(JSON.stringify(plan).includes('secret'), false);
});

test('buildProductionReadinessActionPlan renders credentialed smoke command with key placeholder', () => {
  const checks = buildDeliveryReadinessChecks({ env: {} });
  const plan = buildProductionReadinessActionPlan({
    checks,
    results: [
      {
        name: 'credentialed-delivery-smoke',
        scope: 'production',
        status: 'blocked',
        required: true,
        evidence: { missingEnv: ['DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY'] },
      },
    ],
  });

  assert.deepEqual(plan.blockingItems[0].commands, [
    'DELIVERY_SMOKE_DASHSCOPE_API_KEY=<provider-api-key> node scripts/delivery-local-smoke.mjs',
  ]);
  assert.equal(JSON.stringify(plan).includes('secret'), false);
});

test('renderProductionReadinessActionPlanMarkdown creates a customer handoff checklist', () => {
  const markdown = renderProductionReadinessActionPlanMarkdown({
    generatedAt: '2026-07-08T12:00:00.000Z',
    summary: {
      localReady: true,
      productionReady: false,
      productionPassedItems: ['credentialed-delivery-smoke'],
      productionBlockingItems: ['higress-waf-runtime-preflight'],
    },
    results: [
      {
        name: 'credentialed-delivery-smoke',
        scope: 'production',
        status: 'passed',
        required: true,
        evidence: {
          completedSteps: ['P0', 'P1', 'P2', 'P3'],
          resultCount: 4,
        },
      },
    ],
    actionPlan: {
      ready: false,
      blockingItems: [
        {
          name: 'higress-waf-runtime-preflight',
          status: 'failed',
          description: 'Gateway WAF plugin OCI image must be reachable before enabling the blocking policy.',
          requiredInputs: ['HIGRESS_WAF_PLUGIN_URL'],
          optionalInputs: ['HIGRESS_WAF_PLUGIN_DIGEST'],
          inputOptions: [
            {
              name: 'customer-registry-mirror',
              requiredInputs: ['HIGRESS_WAF_PLUGIN_URL'],
              requiredEvidence: 'Preflight reaches the mirrored OCI plugin.',
            },
          ],
          commands: ['HIGRESS_WAF_PLUGIN_URL=<plugin-oci-url> node scripts/higress-waf-runtime-preflight.mjs'],
          nextAction: 'mirror the approved Higress WAF OCI plugin.',
          requiredEvidence: 'Preflight returns passed=true.',
          observed: {
            status: 'failed',
            classification: 'waf-plugin-container-registry-unreachable',
          },
        },
      ],
    },
  });

  assert.match(markdown, /^# Production Readiness Action Plan/);
  assert.match(markdown, /Generated: 2026-07-08T12:00:00.000Z/);
  assert.match(markdown, /Local ready: true/);
  assert.match(markdown, /Production ready: false/);
  assert.match(markdown, /Passed production gates: credentialed-delivery-smoke/);
  assert.match(markdown, /## Passed Production Evidence/);
  assert.match(markdown, /### credentialed-delivery-smoke/);
  assert.match(markdown, /- completedSteps: P0; P1; P2; P3/);
  assert.match(markdown, /- resultCount: 4/);
  assert.match(markdown, /## higress-waf-runtime-preflight/);
  assert.match(markdown, /Required inputs: `HIGRESS_WAF_PLUGIN_URL`/);
  assert.match(markdown, /Optional inputs: `HIGRESS_WAF_PLUGIN_DIGEST`/);
  assert.match(markdown, /Input options:/);
  assert.match(markdown, /customer-registry-mirror: `HIGRESS_WAF_PLUGIN_URL`/);
  assert.match(markdown, /```bash\nHIGRESS_WAF_PLUGIN_URL=<plugin-oci-url> node scripts\/higress-waf-runtime-preflight\.mjs\n```/);
  assert.match(markdown, /waf-plugin-container-registry-unreachable/);
  assert.equal(markdown.includes('secret'), false);
});

test('formatDeliveryReadinessAuditOutput preserves JSON default and markdown handoff mode', () => {
  const payload = {
    summary: { localReady: true, productionReady: false, productionBlockingItems: ['higress-waf-runtime-preflight'] },
    actionPlan: { ready: false, blockingItems: [] },
    checks: [],
    results: [],
  };

  assert.deepEqual(
    JSON.parse(formatDeliveryReadinessAuditOutput({ payload, outputFormat: 'json' })),
    payload,
  );
  assert.match(
    formatDeliveryReadinessAuditOutput({ payload, outputFormat: 'markdown', generatedAt: '2026-07-08T12:00:00.000Z' }),
    /^# Production Readiness Action Plan/,
  );
});

test('renderProductionReadinessEnvTemplate creates a customer-fillable blocker env file', () => {
  const actionPlan = {
    ready: false,
    blockingItems: [
      {
        name: 'higress-waf-runtime-preflight',
        requiredInputs: ['HIGRESS_WAF_PLUGIN_URL'],
        optionalInputs: [],
        inputOptions: [],
      },
      {
        name: 'higress-waf-blocking-policy',
        requiredInputs: ['HIGRESS_GATEWAY_BASE_URL', 'HIGRESS_WAF_BLOCKING_COVERAGE'],
        optionalInputs: [],
        inputOptions: [],
      },
      {
        name: 'higress-trusted-tls-certificate',
        requiredInputs: ['HIGRESS_TLS_GATEWAY_HOST', 'HIGRESS_TLS_SERVER_NAME'],
        optionalInputs: ['HIGRESS_TLS_CA_FILE'],
        inputOptions: [],
      },
      {
        name: 'higress-oidc-endpoint-security',
        requiredInputs: [
          'HIGRESS_GATEWAY_BASE_URL',
          'HIGRESS_OIDC_ACCEPTED_TOKEN',
          'HIGRESS_OIDC_WRONG_ISSUER_TOKEN',
          'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
        ],
        optionalInputs: [],
        inputOptions: [
          {
            name: 'customer-token-suite',
            requiredInputs: [
              'HIGRESS_GATEWAY_BASE_URL',
              'HIGRESS_OIDC_ACCEPTED_TOKEN',
              'HIGRESS_OIDC_WRONG_ISSUER_TOKEN',
              'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
            ],
          },
          {
            name: 'signing-jwks-test-configuration',
            requiredInputs: [
              'HIGRESS_GATEWAY_BASE_URL',
              'HIGRESS_OIDC_PRIVATE_KEY_FILE or HIGRESS_OIDC_PRIVATE_KEY_PEM',
              'HIGRESS_OIDC_KEY_ID',
              'OIDC_ISSUER',
              'OIDC_AUDIENCE',
            ],
          },
        ],
      },
    ],
  };

  const template = renderProductionReadinessEnvTemplate({ actionPlan });

  assert.match(template, /^# Production Readiness Evidence Environment Template/);
  assert.match(template, /PRODUCTION_READINESS_ENV_FILE=<this-file> node scripts\/production-readiness-env-check\.mjs/);
  assert.match(template, /DELIVERY_READINESS_ENV_FILE=<this-file> node scripts\/delivery-readiness-audit\.mjs/);
  assert.match(template, /HIGRESS_WAF_PLUGIN_URL=/);
  assert.match(template, /HIGRESS_GATEWAY_BASE_URL=/);
  assert.equal(template.match(/^HIGRESS_GATEWAY_BASE_URL=/gm).length, 1);
  assert.match(template, /HIGRESS_WAF_BLOCKING_COVERAGE=true/);
  assert.match(template, /HIGRESS_TLS_GATEWAY_HOST=/);
  assert.match(template, /HIGRESS_TLS_SERVER_NAME=/);
  assert.match(template, /# Optional/);
  assert.match(template, /# HIGRESS_TLS_CA_FILE=/);
  assert.match(template, /# Option: customer-token-suite/);
  assert.match(template, /HIGRESS_OIDC_ACCEPTED_TOKEN=/);
  assert.match(template, /HIGRESS_OIDC_WRONG_ISSUER_TOKEN=/);
  assert.match(template, /HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN=/);
  assert.match(template, /# Option: signing-jwks-test-configuration/);
  assert.match(template, /HIGRESS_OIDC_PRIVATE_KEY_FILE=/);
  assert.match(template, /# HIGRESS_OIDC_PRIVATE_KEY_PEM=/);
  assert.match(template, /HIGRESS_OIDC_KEY_ID=/);
  assert.match(template, /OIDC_ISSUER=/);
  assert.match(template, /OIDC_AUDIENCE=/);
  assert.equal(template.includes('secret'), false);
});

test('validateProductionReadinessEnv reports missing production evidence inputs without leaking values', () => {
  const emptyValidation = validateProductionReadinessEnv({ env: {} });

  assert.equal(emptyValidation.ready, false);
  assert.deepEqual(
    emptyValidation.missingItems.map((item) => item.name),
    [
      'higress-waf-runtime-preflight',
      'higress-waf-blocking-policy',
      'higress-trusted-tls-certificate',
      'higress-oidc-endpoint-security',
      'credentialed-delivery-smoke',
    ],
  );
  assert.deepEqual(emptyValidation.missingItems[0].missingInputs, ['HIGRESS_WAF_PLUGIN_URL']);
  assert.deepEqual(emptyValidation.missingItems[1].missingInputs, ['HIGRESS_GATEWAY_BASE_URL']);
  assert.deepEqual(emptyValidation.missingItems[2].missingInputs, [
    'HIGRESS_TLS_GATEWAY_HOST',
    'HIGRESS_TLS_SERVER_NAME',
  ]);
  assert.deepEqual(emptyValidation.missingItems[3].optionResults, [
    {
      name: 'customer-token-suite',
      ready: false,
      missingInputs: [
        'HIGRESS_GATEWAY_BASE_URL',
        'HIGRESS_OIDC_ACCEPTED_TOKEN',
        'HIGRESS_OIDC_WRONG_ISSUER_TOKEN',
        'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
      ],
    },
    {
      name: 'signing-jwks-test-configuration',
      ready: false,
      missingInputs: [
        'HIGRESS_GATEWAY_BASE_URL',
        'HIGRESS_OIDC_PRIVATE_KEY_FILE or HIGRESS_OIDC_PRIVATE_KEY_PEM',
        'HIGRESS_OIDC_KEY_ID',
        'OIDC_ISSUER',
        'OIDC_AUDIENCE',
      ],
    },
  ]);

  const tokenSuiteValidation = validateProductionReadinessEnv({
    env: {
      HIGRESS_WAF_PLUGIN_URL: 'oci://user:secret@registry.customer.example/platform/higress-waf:2.0.0',
      HIGRESS_GATEWAY_BASE_URL: 'https://gateway.customer.example',
      HIGRESS_TLS_GATEWAY_HOST: 'gateway.customer.example',
      HIGRESS_TLS_SERVER_NAME: 'gateway.customer.example',
      HIGRESS_OIDC_ACCEPTED_TOKEN: 'accepted.jwt.value',
      HIGRESS_OIDC_WRONG_ISSUER_TOKEN: 'wrong.issuer.jwt',
      HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN: 'wrong.audience.jwt',
      DELIVERY_SMOKE_DASHSCOPE_API_KEY: 'provider-secret',
    },
  });

  assert.equal(tokenSuiteValidation.ready, true);
  assert.deepEqual(tokenSuiteValidation.missingItems, []);
  assert.equal(JSON.stringify(tokenSuiteValidation).includes('secret'), false);
  assert.equal(JSON.stringify(tokenSuiteValidation).includes('accepted.jwt.value'), false);
});

test('parseEnvFileText and mergeEnvFileValues load customer evidence without overwriting shell controls', () => {
  const parsed = parseEnvFileText([
    '# customer evidence',
    'export HIGRESS_WAF_PLUGIN_URL="oci://registry.customer.example/platform/higress-waf:2.0.0"',
    'HIGRESS_TLS_GATEWAY_HOST=gateway.customer.example',
    'HIGRESS_TLS_SERVER_NAME=gateway.customer.example',
    'DELIVERY_READINESS_OUTPUT=markdown',
    'MALFORMED_LINE',
    '',
  ].join('\n'));

  assert.deepEqual(parsed, {
    HIGRESS_WAF_PLUGIN_URL: 'oci://registry.customer.example/platform/higress-waf:2.0.0',
    HIGRESS_TLS_GATEWAY_HOST: 'gateway.customer.example',
    HIGRESS_TLS_SERVER_NAME: 'gateway.customer.example',
    DELIVERY_READINESS_OUTPUT: 'markdown',
  });

  assert.deepEqual(
    mergeEnvFileValues({
      baseEnv: {
        DELIVERY_READINESS_OUTPUT: 'json',
        DELIVERY_READINESS_REPORT_FILE: 'docs/report.json',
      },
      fileEnv: parsed,
    }),
    {
      DELIVERY_READINESS_OUTPUT: 'json',
      DELIVERY_READINESS_REPORT_FILE: 'docs/report.json',
      HIGRESS_WAF_PLUGIN_URL: 'oci://registry.customer.example/platform/higress-waf:2.0.0',
      HIGRESS_TLS_GATEWAY_HOST: 'gateway.customer.example',
      HIGRESS_TLS_SERVER_NAME: 'gateway.customer.example',
    },
  );
});

test('loadDeliveryReadinessEnv reads the customer env file for full readiness audits', async () => {
  const loaded = await loadDeliveryReadinessEnv({
    baseEnv: {
      DELIVERY_READINESS_ENV_FILE: 'docs/skill-chain/generated/production-readiness.env',
      DELIVERY_READINESS_OUTPUT: 'json',
    },
    readFileImpl: async (path, encoding) => {
      assert.equal(path, 'docs/skill-chain/generated/production-readiness.env');
      assert.equal(encoding, 'utf8');
      return [
        'DELIVERY_READINESS_OUTPUT=markdown',
        'HIGRESS_WAF_PLUGIN_URL=oci://registry.customer.example/platform/higress-waf:2.0.0',
        'HIGRESS_OIDC_ACCEPTED_TOKEN=accepted.jwt.value',
        'HIGRESS_OIDC_WRONG_ISSUER_TOKEN=wrong.issuer.jwt',
        'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN=wrong.audience.jwt',
        '',
      ].join('\n');
    },
  });

  assert.equal(loaded.DELIVERY_READINESS_OUTPUT, 'json');
  assert.equal(loaded.HIGRESS_WAF_PLUGIN_URL, 'oci://registry.customer.example/platform/higress-waf:2.0.0');
  assert.equal(loaded.HIGRESS_OIDC_ACCEPTED_TOKEN, 'accepted.jwt.value');
});

test('writeDeliveryReadinessReportFile creates the parent directory and writes content', async () => {
  const calls = [];

  await writeDeliveryReadinessReportFile({
    reportFile: 'docs/skill-chain/generated/readiness-action-plan.md',
    content: '# report\n',
    mkdirImpl: async (path, options) => calls.push(['mkdir', path, options]),
    writeFileImpl: async (path, content, encoding) => calls.push(['writeFile', path, content, encoding]),
  });

  assert.deepEqual(calls, [
    ['mkdir', 'docs/skill-chain/generated', { recursive: true }],
    ['writeFile', 'docs/skill-chain/generated/readiness-action-plan.md', '# report\n', 'utf8'],
  ]);
});
