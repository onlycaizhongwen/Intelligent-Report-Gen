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
      'DELIVERY_SMOKE_DASHSCOPE_API_KEY=provider-token-value',
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
    assert.equal(stdout.includes('provider-token-value'), false);
    assert.equal(stdout.includes('customer-kid'), false);
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
});
