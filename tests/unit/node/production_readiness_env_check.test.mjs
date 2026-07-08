import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

test('production readiness env check reports missing inputs and accepts a filled env file', async () => {
  const cleanEnv = { PATH: process.env.PATH };
  const missing = await execFileAsync('node', ['scripts/production-readiness-env-check.mjs'], {
    env: cleanEnv,
  }).catch((error) => error);

  assert.equal(missing.code, 1);
  const missingOutput = JSON.parse(missing.stdout);
  assert.equal(missingOutput.ready, false);
  assert.deepEqual(
    missingOutput.missingItems.map((item) => item.name),
    [
      'higress-waf-runtime-preflight',
      'higress-waf-blocking-policy',
      'higress-trusted-tls-certificate',
      'higress-oidc-endpoint-security',
      'credentialed-delivery-smoke',
    ],
  );

  const tempDir = await mkdtemp(join(tmpdir(), 'ir-readiness-env-'));
  try {
    const envFile = join(tempDir, 'production-readiness.env');
    await writeFile(envFile, [
      'HIGRESS_WAF_PLUGIN_URL=oci://registry.customer.example/platform/higress-waf:2.0.0',
      'HIGRESS_GATEWAY_BASE_URL=https://gateway.customer.example',
      'HIGRESS_TLS_GATEWAY_HOST=gateway.customer.example',
      'HIGRESS_TLS_SERVER_NAME=gateway.customer.example',
      'HIGRESS_OIDC_PRIVATE_KEY_FILE=/customer/keys/oidc.pem',
      'HIGRESS_OIDC_KEY_ID=customer-kid',
      'OIDC_ISSUER=https://idp.customer.example',
      'OIDC_AUDIENCE=intelligent-report-api',
      'DELIVERY_SMOKE_DASHSCOPE_API_KEY=provider-placeholder-token',
      '',
    ].join('\n'), 'utf8');

    const { stdout } = await execFileAsync('node', ['scripts/production-readiness-env-check.mjs'], {
      env: {
        ...cleanEnv,
        PRODUCTION_READINESS_ENV_FILE: envFile,
      },
    });
    const readyOutput = JSON.parse(stdout);

    assert.equal(readyOutput.ready, true);
    assert.deepEqual(readyOutput.missingItems, []);
    assert.equal(stdout.includes('provider-placeholder-token'), false);
    assert.equal(stdout.includes('customer-kid'), false);
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
});

test('production readiness env check treats blank env file evidence as authoritative over stale shell values', async () => {
  const cleanEnv = { PATH: process.env.PATH };
  const tempDir = await mkdtemp(join(tmpdir(), 'ir-readiness-env-blank-'));
  try {
    const envFile = join(tempDir, 'production-readiness.blank.env');
    await writeFile(envFile, [
      'HIGRESS_WAF_PLUGIN_URL=',
      'HIGRESS_GATEWAY_BASE_URL=',
      'HIGRESS_TLS_GATEWAY_HOST=',
      'HIGRESS_TLS_SERVER_NAME=',
      'HIGRESS_OIDC_ACCEPTED_TOKEN=',
      'HIGRESS_OIDC_WRONG_ISSUER_TOKEN=',
      'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN=',
      'HIGRESS_OIDC_PRIVATE_KEY_FILE=',
      'HIGRESS_OIDC_PRIVATE_KEY_PEM=',
      'HIGRESS_OIDC_KEY_ID=',
      'OIDC_ISSUER=',
      'OIDC_AUDIENCE=',
      'DELIVERY_SMOKE_DASHSCOPE_API_KEY=',
      'DASHSCOPE_API_KEY=',
      '',
    ].join('\n'), 'utf8');

    const staleShellEnv = {
      ...cleanEnv,
      PRODUCTION_READINESS_ENV_FILE: envFile,
      HIGRESS_WAF_PLUGIN_URL: 'oci://registry.stale.example/platform/higress-waf:2.0.0',
      HIGRESS_GATEWAY_BASE_URL: 'https://stale-gateway.example',
      HIGRESS_TLS_GATEWAY_HOST: 'stale-gateway.example',
      HIGRESS_TLS_SERVER_NAME: 'stale-gateway.example',
      HIGRESS_OIDC_ACCEPTED_TOKEN: 'stale-accepted-token',
      HIGRESS_OIDC_WRONG_ISSUER_TOKEN: 'stale-wrong-issuer-token',
      HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN: 'stale-wrong-audience-token',
      HIGRESS_OIDC_PRIVATE_KEY_FILE: '/stale/oidc.pem',
      HIGRESS_OIDC_PRIVATE_KEY_PEM: 'stale-private-key',
      HIGRESS_OIDC_KEY_ID: 'stale-kid',
      OIDC_ISSUER: 'https://stale-idp.example',
      OIDC_AUDIENCE: 'stale-audience',
      DELIVERY_SMOKE_DASHSCOPE_API_KEY: 'stale-provider-token',
      DASHSCOPE_API_KEY: 'stale-provider-token',
    };

    const failed = await execFileAsync('node', ['scripts/production-readiness-env-check.mjs'], {
      env: staleShellEnv,
    }).catch((error) => error);
    const output = JSON.parse(failed.stdout);

    assert.equal(failed.code, 1);
    assert.equal(output.ready, false);
    assert.deepEqual(
      output.missingItems.map((item) => item.name),
      [
        'higress-waf-runtime-preflight',
        'higress-waf-blocking-policy',
        'higress-trusted-tls-certificate',
        'higress-oidc-endpoint-security',
        'credentialed-delivery-smoke',
      ],
    );
    assert.equal(failed.stdout.includes('stale-provider-token'), false);
    assert.equal(failed.stdout.includes('stale-accepted-token'), false);
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
});

test('production readiness env check rejects local gateway URLs for production evidence', async () => {
  const cleanEnv = { PATH: process.env.PATH };
  const tempDir = await mkdtemp(join(tmpdir(), 'ir-readiness-env-local-gateway-'));
  try {
    const envFile = join(tempDir, 'production-readiness.local-gateway.env');
    await writeFile(envFile, [
      'HIGRESS_WAF_PLUGIN_URL=oci://registry.customer.example/platform/higress-waf:2.0.0',
      'HIGRESS_GATEWAY_BASE_URL=http://127.0.0.1:18000',
      'HIGRESS_TLS_GATEWAY_HOST=gateway.customer.example',
      'HIGRESS_TLS_SERVER_NAME=gateway.customer.example',
      'HIGRESS_OIDC_ACCEPTED_TOKEN=accepted-token-placeholder',
      'HIGRESS_OIDC_WRONG_ISSUER_TOKEN=wrong-issuer-token-placeholder',
      'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN=wrong-audience-token-placeholder',
      'DELIVERY_SMOKE_DASHSCOPE_API_KEY=provider-placeholder-token',
      '',
    ].join('\n'), 'utf8');

    const failed = await execFileAsync('node', ['scripts/production-readiness-env-check.mjs'], {
      env: {
        ...cleanEnv,
        PRODUCTION_READINESS_ENV_FILE: envFile,
      },
    }).catch((error) => error);
    const output = JSON.parse(failed.stdout);

    assert.equal(failed.code, 1);
    assert.equal(output.ready, false);
    assert.deepEqual(
      output.missingItems.map((item) => item.name),
      [
        'higress-waf-blocking-policy',
        'higress-oidc-endpoint-security',
      ],
    );
    assert.deepEqual(output.missingItems[0].missingInputs, [
      'HIGRESS_GATEWAY_BASE_URL (non-local target URL)',
    ]);
    assert.deepEqual(output.missingItems[1].missingInputs, [
      'HIGRESS_GATEWAY_BASE_URL (non-local target URL)',
    ]);
    assert.equal(failed.stdout.includes('provider-placeholder-token'), false);
    assert.equal(failed.stdout.includes('accepted-token-placeholder'), false);
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
});
