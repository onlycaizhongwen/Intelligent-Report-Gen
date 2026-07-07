import crypto from 'node:crypto';

export function createOidcKeyMaterial({ keyId = 'local-oidc-key' } = {}) {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
  });
  const privateKeyPem = privateKey.export({ type: 'pkcs8', format: 'pem' });
  const publicJwk = publicKey.export({ format: 'jwk' });

  return {
    privateKeyPem,
    publicJwk: {
      ...publicJwk,
      kid: keyId,
      alg: 'RS256',
      use: 'sig',
    },
  };
}

export function buildJwksResponse(material) {
  const { d, p, q, dp, dq, qi, oth, ...publicJwk } = material.publicJwk;
  return { keys: [publicJwk] };
}

export function buildJavaOidcSmokeEnv({
  jwksUrl,
  issuer,
  audience,
  databaseUrl = 'jdbc:postgresql://postgres:5432/intelligent_report',
  databaseUsername = 'report',
  databasePassword = 'report123',
} = {}) {
  return {
    SPRING_PROFILES_ACTIVE: 'dev',
    SERVER_PORT: '8080',
    DB_JDBC_URL: databaseUrl,
    DB_USERNAME: databaseUsername,
    DB_PASSWORD: databasePassword,
    MINIO_ENDPOINT: 'http://minio:9000',
    MINIO_PUBLIC_ENDPOINT: 'http://host.docker.internal:9000',
    MINIO_ROOT_USER: 'minioadmin',
    MINIO_ROOT_PASSWORD: 'minioadmin123',
    ROCKETMQ_ENDPOINT: 'rocketmq-namesrv:9876',
    ROCKETMQ_ENABLED: 'false',
    JWT_SECRET: 'local-dev-secret-change-me-32-bytes-minimum',
    JWT_ALGORITHM: 'RS256',
    OIDC_JWKS_URL: jwksUrl,
    OIDC_ISSUER: issuer,
    OIDC_AUDIENCE: audience,
    OIDC_JWKS_CACHE_TTL_SECONDS: '30',
    RULE_WEBHOOK_REPLAY_WORKER_ENABLED: 'false',
  };
}

export function buildDockerRunArgs({
  containerName,
  image,
  network,
  hostPort,
  env = {},
}) {
  return [
    'run',
    '-d',
    '--rm',
    '--name',
    containerName,
    '--network',
    network,
    '-p',
    `${hostPort}:8080`,
    ...Object.entries(env).flatMap(([key, value]) => ['-e', `${key}=${value}`]),
    image,
  ];
}

export function buildHigressOidcSmokeEnv({
  gatewayBaseUrl,
  privateKeyPem,
  keyId,
  issuer,
  audience,
}) {
  return {
    HIGRESS_GATEWAY_BASE_URL: gatewayBaseUrl,
    HIGRESS_GATEWAY_BASELINE_COVERAGE: 'false',
    HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE: 'true',
    HIGRESS_OIDC_PRIVATE_KEY_PEM: privateKeyPem,
    HIGRESS_OIDC_KEY_ID: keyId,
    OIDC_ISSUER: issuer,
    OIDC_AUDIENCE: audience,
  };
}

export function updateHigressEndpointPort(manifest, port) {
  const updated = String(manifest).replace(
    /(\n\s*-\s*name:\s*http\s*\n\s*)port:\s*\d+/,
    `$1port: ${port}`,
  );
  if (updated === manifest) {
    throw new Error('java-report-core endpoint http port not found');
  }
  return updated;
}

export function redactOidcSmokeSummary(summary) {
  const { privateKeyPem, ...rest } = summary;
  return {
    ...rest,
    privateKey: privateKeyPem ? '<redacted>' : undefined,
  };
}

export function extractFailedSmokeResults(stdout) {
  let parsed;
  try {
    parsed = JSON.parse(String(stdout ?? ''));
  } catch (error) {
    throw new Error(`invalid Higress smoke JSON stdout: ${error.message}`);
  }
  return (parsed.results ?? []).filter((result) => result.passed === false);
}
