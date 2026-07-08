import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildDeliveryReadinessChecks,
  classifyDeliveryReadinessResult,
  summarizeDeliveryReadiness,
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
  assert.equal(checks[3].scope, 'production');
  assert.deepEqual(checks[3].args, ['scripts/higress-waf-runtime-preflight.mjs']);
  assert.equal(checks[6].kind, 'command');
  assert.equal(checks[6].env.HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE, 'true');
  assert.equal(checks[7].env.DELIVERY_SMOKE_DASHSCOPE_API_KEY, '<provided>');
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

test('summarizeDeliveryReadiness keeps production readiness false for blocked production gates', () => {
  const summary = summarizeDeliveryReadiness([
    { name: 'frontend-browser-http', scope: 'local', status: 'passed', required: true },
    { name: 'higress-default-security-smoke', scope: 'local', status: 'passed', required: true },
    { name: 'higress-waf-blocking-policy', scope: 'production', status: 'blocked', required: true },
    { name: 'higress-trusted-tls-certificate', scope: 'production', status: 'failed', required: true },
  ]);

  assert.deepEqual(summary, {
    localReady: true,
    productionReady: false,
    total: 4,
    passed: 2,
    failed: 1,
    blocked: 1,
    localBlockingItems: [],
    productionBlockingItems: ['higress-waf-blocking-policy', 'higress-trusted-tls-certificate'],
  });
});
