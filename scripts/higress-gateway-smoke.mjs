import fs from 'node:fs';
import { buildJavaControllerAuthorizationMatrix } from './controller-authorization-matrix-lib.mjs';
import { runHigressGatewaySmoke } from './higress-gateway-smoke-lib.mjs';

function isTruthy(value) {
  return ['1', 'true', 'all', 'yes'].includes(String(value ?? '').toLowerCase());
}

function isFalsey(value) {
  return ['0', 'false', 'none', 'no'].includes(String(value ?? '').toLowerCase());
}

function readOidcPrivateKey() {
  if (process.env.HIGRESS_OIDC_PRIVATE_KEY_PEM) {
    return process.env.HIGRESS_OIDC_PRIVATE_KEY_PEM.replace(/\\n/g, '\n');
  }
  if (process.env.HIGRESS_OIDC_PRIVATE_KEY_FILE) {
    return fs.readFileSync(process.env.HIGRESS_OIDC_PRIVATE_KEY_FILE, 'utf8');
  }
  return null;
}

function hasText(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function buildOidcEndpointSecurityConfig() {
  const requested = isTruthy(process.env.HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE);
  const acceptedToken = process.env.HIGRESS_OIDC_ACCEPTED_TOKEN;
  const wrongIssuerToken = process.env.HIGRESS_OIDC_WRONG_ISSUER_TOKEN;
  const wrongAudienceToken = process.env.HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN;
  const hasTokenSuite = hasText(acceptedToken) && hasText(wrongIssuerToken) && hasText(wrongAudienceToken);
  if (hasTokenSuite) {
    return {
      acceptedToken,
      wrongIssuerToken,
      wrongAudienceToken,
    };
  }
  const privateKey = readOidcPrivateKey();
  const issuer = process.env.OIDC_ISSUER;
  const audience = process.env.OIDC_AUDIENCE;
  if (!requested && !privateKey && !issuer && !audience
    && !acceptedToken && !wrongIssuerToken && !wrongAudienceToken) {
    return null;
  }
  const missing = [];
  if (acceptedToken || wrongIssuerToken || wrongAudienceToken) {
    if (!acceptedToken) {
      missing.push('HIGRESS_OIDC_ACCEPTED_TOKEN');
    }
    if (!wrongIssuerToken) {
      missing.push('HIGRESS_OIDC_WRONG_ISSUER_TOKEN');
    }
    if (!wrongAudienceToken) {
      missing.push('HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN');
    }
  }
  if (!privateKey) {
    missing.push('HIGRESS_OIDC_PRIVATE_KEY_PEM or HIGRESS_OIDC_PRIVATE_KEY_FILE');
  }
  if (!issuer) {
    missing.push('OIDC_ISSUER');
  }
  if (!audience) {
    missing.push('OIDC_AUDIENCE');
  }
  if (missing.length > 0) {
    throw new Error(`OIDC Higress smoke requires ${missing.join(', ')}`);
  }
  return {
    privateKey,
    keyId: process.env.HIGRESS_OIDC_KEY_ID ?? 'local-oidc-key',
    issuer,
    audience,
  };
}

const controllerEndpointAuthorizationSampleLimit = Number.parseInt(
  process.env.HIGRESS_CONTROLLER_ENDPOINT_AUTH_SAMPLE_LIMIT ?? '0',
  10,
);
const controllerEndpointAuthorizationNegativeCoverage = isTruthy(
  process.env.HIGRESS_CONTROLLER_ENDPOINT_AUTH_NEGATIVE_COVERAGE,
);
const controllerEndpointAuthorizationReadOnlyAuthorizedCoverage = isTruthy(
  process.env.HIGRESS_CONTROLLER_ENDPOINT_AUTH_READONLY_AUTHORIZED_COVERAGE,
);
const wafBlockingCoverage = isTruthy(process.env.HIGRESS_WAF_BLOCKING_COVERAGE);
const baselineCoverage = !isFalsey(process.env.HIGRESS_GATEWAY_BASELINE_COVERAGE);
const needsControllerMatrix = controllerEndpointAuthorizationSampleLimit > 0
  || controllerEndpointAuthorizationNegativeCoverage
  || controllerEndpointAuthorizationReadOnlyAuthorizedCoverage;
const oidcEndpointSecurityConfig = buildOidcEndpointSecurityConfig();

const result = await runHigressGatewaySmoke({
  gatewayBaseUrl: process.env.HIGRESS_GATEWAY_BASE_URL ?? 'http://127.0.0.1:18000',
  baselineCoverage,
  controllerEndpointAuthorizationSampleLimit,
  controllerEndpointAuthorizationNegativeCoverage,
  controllerEndpointAuthorizationReadOnlyAuthorizedCoverage,
  wafBlockingCoverage,
  oidcEndpointSecurityConfig,
  controllerMatrix: needsControllerMatrix
    ? buildJavaControllerAuthorizationMatrix({
      controllersRoot: 'backend/java-report-core/src/main/java/com/company/report',
    })
    : [],
});

console.log(JSON.stringify(result, null, 2));

if (!result.passed) {
  process.exitCode = 1;
}
