import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';

import {
  buildDockerRunArgs,
  buildHigressOidcSmokeEnv,
  buildJavaOidcSmokeEnv,
  buildJwksResponse,
  createOidcKeyMaterial,
  redactOidcSmokeSummary,
  extractFailedSmokeResults,
  updateHigressEndpointPort,
} from '../../../scripts/higress-oidc-local-smoke-lib.mjs';

test('createOidcKeyMaterial returns RS256 signing material and public JWKS without leaking private key', () => {
  const material = createOidcKeyMaterial({ keyId: 'local-oidc-key' });

  assert.match(material.privateKeyPem, /BEGIN PRIVATE KEY/);
  assert.equal(material.publicJwk.kid, 'local-oidc-key');
  assert.equal(material.publicJwk.kty, 'RSA');
  assert.equal(material.publicJwk.alg, 'RS256');
  assert.equal(material.publicJwk.use, 'sig');
  assert.equal(material.publicJwk.d, undefined);

  const publicKey = crypto.createPublicKey({ key: material.publicJwk, format: 'jwk' });
  const signature = crypto.sign('RSA-SHA256', Buffer.from('payload'), material.privateKeyPem);

  assert.equal(crypto.verify('RSA-SHA256', Buffer.from('payload'), publicKey, signature), true);
});

test('buildJwksResponse exposes only public keys', () => {
  const material = createOidcKeyMaterial({ keyId: 'kid-1' });
  const jwks = buildJwksResponse(material);

  assert.deepEqual(Object.keys(jwks), ['keys']);
  assert.equal(jwks.keys.length, 1);
  assert.equal(jwks.keys[0].kid, 'kid-1');
  assert.equal(jwks.keys[0].d, undefined);
});

test('buildJavaOidcSmokeEnv configures a temporary RS256 Java runtime', () => {
  const env = buildJavaOidcSmokeEnv({
    jwksUrl: 'http://host.docker.internal:18087/.well-known/jwks.json',
    issuer: 'https://idp.local.test',
    audience: 'intelligent-report-api',
  });

  assert.equal(env.SPRING_PROFILES_ACTIVE, 'dev');
  assert.equal(env.JWT_ALGORITHM, 'RS256');
  assert.equal(env.OIDC_JWKS_URL, 'http://host.docker.internal:18087/.well-known/jwks.json');
  assert.equal(env.OIDC_ISSUER, 'https://idp.local.test');
  assert.equal(env.OIDC_AUDIENCE, 'intelligent-report-api');
  assert.equal(env.ROCKETMQ_ENABLED, 'false');
});

test('buildDockerRunArgs starts an isolated temporary Java OIDC runtime', () => {
  const args = buildDockerRunArgs({
    containerName: 'ir-java-oidc-smoke',
    image: 'intelligent-report-system-java-report-core',
    network: 'intelligent-report-infra_default',
    hostPort: 18086,
    env: {
      SPRING_PROFILES_ACTIVE: 'dev',
      JWT_ALGORITHM: 'RS256',
    },
  });

  assert.deepEqual(args.slice(0, 7), [
    'run',
    '-d',
    '--rm',
    '--name',
    'ir-java-oidc-smoke',
    '--network',
    'intelligent-report-infra_default',
  ]);
  assert.ok(args.includes('-p'));
  assert.ok(args.includes('18086:8080'));
  assert.ok(args.includes('SPRING_PROFILES_ACTIVE=dev'));
  assert.ok(args.includes('JWT_ALGORITHM=RS256'));
  assert.equal(args.at(-1), 'intelligent-report-system-java-report-core');
});

test('buildHigressOidcSmokeEnv enables only OIDC endpoint security probes', () => {
  const env = buildHigressOidcSmokeEnv({
    gatewayBaseUrl: 'http://127.0.0.1:18000',
    privateKeyPem: '-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----',
    keyId: 'kid-1',
    issuer: 'https://idp.local.test',
    audience: 'intelligent-report-api',
  });

  assert.equal(env.HIGRESS_GATEWAY_BASE_URL, 'http://127.0.0.1:18000');
  assert.equal(env.HIGRESS_GATEWAY_BASELINE_COVERAGE, 'false');
  assert.equal(env.HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE, 'true');
  assert.equal(env.HIGRESS_OIDC_KEY_ID, 'kid-1');
  assert.equal(env.OIDC_ISSUER, 'https://idp.local.test');
  assert.equal(env.OIDC_AUDIENCE, 'intelligent-report-api');
  assert.match(env.HIGRESS_OIDC_PRIVATE_KEY_PEM, /BEGIN PRIVATE KEY/);
});

test('updateHigressEndpointPort changes only the Java endpoint port', () => {
  const original = [
    'apiVersion: v1',
    'kind: Endpoints',
    'subsets:',
    '- addresses:',
    '  - ip: 172.30.0.1',
    '  ports:',
    '  - name: http',
    '    port: 18082',
    '    protocol: TCP',
    '',
  ].join('\n');

  const updated = updateHigressEndpointPort(original, 18086);

  assert.match(updated, /port: 18086/);
  assert.doesNotMatch(updated, /port: 18082/);
  assert.match(updated, /ip: 172\.30\.0\.1/);
});

test('redactOidcSmokeSummary never serializes private key material', () => {
  const summary = redactOidcSmokeSummary({
    privateKeyPem: '-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----',
    keyId: 'kid-1',
    issuer: 'https://idp.local.test',
    audience: 'intelligent-report-api',
    jwksUrl: 'http://host.docker.internal:18087/.well-known/jwks.json',
  });

  assert.equal(summary.privateKeyPem, undefined);
  assert.equal(summary.privateKey, '<redacted>');
  assert.equal(summary.keyId, 'kid-1');
  assert.equal(summary.jwksUrl, 'http://host.docker.internal:18087/.well-known/jwks.json');
});

test('extractFailedSmokeResults returns failed smoke items from JSON stdout', () => {
  const failed = extractFailedSmokeResults(JSON.stringify({
    passed: false,
    results: [
      { name: 'oidc-current-user-authorized-through-higress', passed: false, status: 401 },
      { name: 'oidc-current-user-wrong-issuer-through-higress', passed: true, status: 401 },
    ],
  }));

  assert.deepEqual(failed, [
    { name: 'oidc-current-user-authorized-through-higress', passed: false, status: 401 },
  ]);
});

test('extractFailedSmokeResults reports invalid smoke stdout', () => {
  assert.throws(
    () => extractFailedSmokeResults('not-json'),
    /invalid Higress smoke JSON stdout/,
  );
});
