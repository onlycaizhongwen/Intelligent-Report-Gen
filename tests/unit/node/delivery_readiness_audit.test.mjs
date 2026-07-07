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
      'higress-waf-blocking-policy',
      'higress-trusted-tls-certificate',
      'higress-oidc-endpoint-security',
      'credentialed-delivery-smoke',
    ],
  );

  assert.equal(checks[0].scope, 'local');
  assert.equal(checks[2].scope, 'production');
  assert.equal(checks[4].kind, 'command');
  assert.equal(checks[4].env.HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE, 'true');
  assert.equal(checks[5].env.DELIVERY_SMOKE_DASHSCOPE_API_KEY, '<provided>');
  assert.equal(JSON.stringify(checks), JSON.stringify(checks).replace('secret-key', '<leaked>'));
  assert.equal(JSON.stringify(checks), JSON.stringify(checks).replace('secret-pem', '<leaked>'));
});

test('buildDeliveryReadinessChecks marks credential-gated production checks as blocked when inputs are missing', () => {
  const checks = buildDeliveryReadinessChecks({ env: {} });
  const oidc = checks.find((check) => check.name === 'higress-oidc-endpoint-security');
  const delivery = checks.find((check) => check.name === 'credentialed-delivery-smoke');

  assert.equal(oidc.kind, 'blocked');
  assert.equal(oidc.status, 'blocked');
  assert.deepEqual(oidc.missingEnv, [
    'HIGRESS_OIDC_PRIVATE_KEY_FILE or HIGRESS_OIDC_PRIVATE_KEY_PEM',
    'HIGRESS_OIDC_KEY_ID',
    'OIDC_ISSUER',
    'OIDC_AUDIENCE',
  ]);

  assert.equal(delivery.kind, 'blocked');
  assert.equal(delivery.status, 'blocked');
  assert.deepEqual(delivery.missingEnv, ['DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY']);
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
