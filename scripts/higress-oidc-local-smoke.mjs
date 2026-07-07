import fs from 'node:fs';
import http from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildDockerRunArgs,
  buildHigressOidcSmokeEnv,
  buildJavaOidcSmokeEnv,
  buildJwksResponse,
  createOidcKeyMaterial,
  extractFailedSmokeResults,
  redactOidcSmokeSummary,
  updateHigressEndpointPort,
} from './higress-oidc-local-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

function envNumber(name, fallback) {
  const parsed = Number.parseInt(process.env[name] ?? '', 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

async function docker(args, options = {}) {
  return execFileAsync('docker', args, {
    cwd: process.cwd(),
    windowsHide: true,
    maxBuffer: 10 * 1024 * 1024,
    ...options,
  });
}

async function discoverJavaImage() {
  if (process.env.HIGRESS_OIDC_SMOKE_JAVA_IMAGE) {
    return process.env.HIGRESS_OIDC_SMOKE_JAVA_IMAGE;
  }
  const sourceContainer = process.env.HIGRESS_OIDC_SMOKE_SOURCE_CONTAINER ?? 'ir-java-smoke';
  const { stdout } = await docker(['inspect', sourceContainer, '--format', '{{.Config.Image}}']);
  return stdout.trim();
}

function startJwksServer({ material, port }) {
  const jwks = buildJwksResponse(material);
  const server = http.createServer((request, response) => {
    if (request.url === '/.well-known/jwks.json' || request.url === '/jwks.json') {
      const body = JSON.stringify(jwks);
      response.writeHead(200, {
        'content-type': 'application/json',
        'content-length': Buffer.byteLength(body),
      });
      response.end(body);
      return;
    }
    response.writeHead(404, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ error: 'not_found' }));
  });

  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '0.0.0.0', () => resolve(server));
  });
}

async function waitForHttp(url, { timeoutMs = 120_000, acceptedStatuses = null } = {}) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(5_000) });
      if (!acceptedStatuses || acceptedStatuses.includes(response.status)) {
        return response;
      }
      lastError = new Error(`unexpected HTTP ${response.status}`);
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  throw new Error(`timed out waiting for ${url}: ${lastError?.message ?? 'no response'}`);
}

async function restartHigressAndWait(gatewayBaseUrl) {
  await docker(['restart', process.env.HIGRESS_OIDC_SMOKE_HIGRESS_CONTAINER ?? 'ir-higress']);
  await waitForHttp(`${gatewayBaseUrl.replace(/\/$/, '')}/api/v1/roles/permission-matrix`, {
    timeoutMs: 90_000,
    acceptedStatuses: [401, 403, 200],
  });
}

async function runSmokeCommand(env) {
  let stdout;
  let stderr;
  try {
    ({ stdout, stderr } = await execFileAsync('node', ['scripts/higress-gateway-smoke.mjs'], {
      cwd: process.cwd(),
      env: { ...process.env, ...env },
      windowsHide: true,
      maxBuffer: 25 * 1024 * 1024,
    }));
  } catch (error) {
    stdout = error.stdout;
    stderr = error.stderr;
  }
  const parsed = JSON.parse(stdout);
  if (!parsed.passed) {
    const failed = extractFailedSmokeResults(stdout);
    throw new Error(`OIDC Higress smoke failed: ${JSON.stringify(failed)}${stderr ? `\n${stderr}` : ''}`);
  }
  return parsed;
}

const keyId = process.env.HIGRESS_OIDC_KEY_ID || 'local-oidc-key';
const issuer = process.env.OIDC_ISSUER || 'https://idp.local.test';
const audience = process.env.OIDC_AUDIENCE || 'intelligent-report-api';
const gatewayBaseUrl = process.env.HIGRESS_GATEWAY_BASE_URL || 'http://127.0.0.1:18000';
const javaHostPort = envNumber('HIGRESS_OIDC_SMOKE_JAVA_HOST_PORT', 18086);
const jwksHostPort = envNumber('HIGRESS_OIDC_SMOKE_JWKS_HOST_PORT', 18087);
const network = process.env.HIGRESS_OIDC_SMOKE_DOCKER_NETWORK || 'intelligent-report-infra_default';
const containerName = process.env.HIGRESS_OIDC_SMOKE_CONTAINER || 'ir-java-oidc-smoke';
const endpointPath = process.env.HIGRESS_OIDC_SMOKE_ENDPOINT_PATH
  || 'config/higress/local-data/endpoints/java-report-core.yaml';

const material = createOidcKeyMaterial({ keyId });
const jwksUrl = `http://host.docker.internal:${jwksHostPort}/.well-known/jwks.json`;
const originalEndpoint = fs.readFileSync(endpointPath, 'utf8');
let endpointChanged = false;
let jwksServer = null;

try {
  const image = await discoverJavaImage();
  jwksServer = await startJwksServer({ material, port: jwksHostPort });

  await docker(['rm', '-f', containerName]).catch(() => null);
  const javaEnv = buildJavaOidcSmokeEnv({ jwksUrl, issuer, audience });
  await docker(buildDockerRunArgs({
    containerName,
    image,
    network,
    hostPort: javaHostPort,
    env: javaEnv,
  }));
  await waitForHttp(`http://127.0.0.1:${javaHostPort}/actuator/health`);

  fs.writeFileSync(endpointPath, updateHigressEndpointPort(originalEndpoint, javaHostPort));
  endpointChanged = true;
  await restartHigressAndWait(gatewayBaseUrl);

  const smoke = await runSmokeCommand(buildHigressOidcSmokeEnv({
    gatewayBaseUrl,
    privateKeyPem: material.privateKeyPem,
    keyId,
    issuer,
    audience,
  }));
  const oidcResults = smoke.results.filter((result) => result.name.startsWith('oidc-'));
  console.log(JSON.stringify({
    passed: true,
    ...redactOidcSmokeSummary({
      privateKeyPem: material.privateKeyPem,
      keyId,
      issuer,
      audience,
      jwksUrl,
      gatewayBaseUrl,
      javaHostPort,
      jwksHostPort,
      containerName,
    }),
    resultCount: smoke.results.length,
    oidcResults,
  }, null, 2));
} finally {
  if (endpointChanged) {
    fs.writeFileSync(endpointPath, originalEndpoint);
    await restartHigressAndWait(gatewayBaseUrl).catch((error) => {
      console.error(`failed to restore Higress route health: ${error.message}`);
    });
  }
  await docker(['rm', '-f', containerName]).catch(() => null);
  if (jwksServer) {
    await new Promise((resolve) => jwksServer.close(resolve));
  }
}
