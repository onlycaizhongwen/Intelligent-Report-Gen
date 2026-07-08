function hasText(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function parseJsonObject(text) {
  if (!hasText(text)) {
    return {};
  }

  try {
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === 'object' ? parsed : { value: parsed };
  } catch {
    return { outputPreview: text.trim().slice(0, 500) };
  }
}

function compactSmokeEvidence(evidence) {
  if (Array.isArray(evidence.oidcResults)) {
    return {
      ...evidence,
      oidcResults: evidence.oidcResults.map((result) => ({
        name: result.name,
        status: result.status,
        code: result.code,
        classification: result.classification,
        passed: result.passed,
      })),
    };
  }

  if (!Array.isArray(evidence.results)) {
    return evidence;
  }

  if (Array.isArray(evidence.completedSteps)) {
    return {
      completedSteps: evidence.completedSteps,
      plannedStepCount: Array.isArray(evidence.plannedSteps) ? evidence.plannedSteps.length : undefined,
      resultCount: evidence.results.length,
    };
  }

  return {
    ...Object.fromEntries(
      Object.entries(evidence).filter(([key]) => key !== 'results'),
    ),
    resultCount: evidence.results.length,
    failedResults: evidence.results
      .filter((result) => result?.passed === false)
      .map((result) => ({
        name: result.name,
        status: result.status,
        classification: result.classification,
        passed: result.passed,
      })),
  };
}

function blockedCheck({ name, scope, description, missingEnv }) {
  return {
    name,
    scope,
    required: true,
    kind: 'blocked',
    status: 'blocked',
    description,
    missingEnv,
  };
}

function commandCheck({ name, scope, description, command = 'node', args, env = {} }) {
  return {
    name,
    scope,
    required: true,
    kind: 'command',
    description,
    command,
    args,
    env: Object.fromEntries(Object.entries(env).filter(([, value]) => value !== undefined)),
  };
}

export function buildDeliveryReadinessChecks({ env = process.env } = {}) {
  const hasOidcPrivateKey = hasText(env.HIGRESS_OIDC_PRIVATE_KEY_FILE)
    || hasText(env.HIGRESS_OIDC_PRIVATE_KEY_PEM);
  const hasOidcConfig = hasOidcPrivateKey
    && hasText(env.HIGRESS_OIDC_KEY_ID)
    && hasText(env.OIDC_ISSUER)
    && hasText(env.OIDC_AUDIENCE);
  const hasOidcTokenSuite = hasText(env.HIGRESS_OIDC_ACCEPTED_TOKEN)
    && hasText(env.HIGRESS_OIDC_WRONG_ISSUER_TOKEN)
    && hasText(env.HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN);
  const hasDeliveryModelKey = hasText(env.DELIVERY_SMOKE_DASHSCOPE_API_KEY)
    || hasText(env.DASHSCOPE_API_KEY);

  return [
    {
      name: 'frontend-browser-http',
      scope: 'local',
      required: true,
      kind: 'http',
      description: 'Local browser entrypoint must respond successfully.',
      url: env.DELIVERY_READINESS_FRONTEND_URL ?? 'http://127.0.0.1:5173/',
    },
    commandCheck({
      name: 'higress-default-security-smoke',
      scope: 'local',
      description: 'Local Higress route must preserve Java auth and default security boundaries.',
      args: ['scripts/higress-gateway-smoke.mjs'],
    }),
    commandCheck({
      name: 'higress-local-oidc-test-idp-smoke',
      scope: 'local',
      description: 'Local Higress route must pass RS256/JWKS OIDC probes against a temporary test IdP and restore the default route.',
      args: ['scripts/higress-oidc-local-smoke.mjs'],
    }),
    commandCheck({
      name: 'higress-waf-runtime-preflight',
      scope: 'production',
      description: 'Gateway WAF plugin OCI image must be reachable before enabling the blocking policy.',
      args: ['scripts/higress-waf-runtime-preflight.mjs'],
    }),
    commandCheck({
      name: 'higress-waf-blocking-policy',
      scope: 'production',
      description: 'Gateway WAF policy must block representative SQLi, XSS, path traversal, and prompt-injection probes.',
      args: ['scripts/higress-gateway-smoke.mjs'],
      env: {
        HIGRESS_WAF_BLOCKING_COVERAGE: 'true',
      },
    }),
    commandCheck({
      name: 'higress-trusted-tls-certificate',
      scope: 'production',
      description: 'Gateway TLS certificate must be trusted and valid for the configured minimum window.',
      args: ['scripts/higress-tls-certificate-smoke.mjs'],
    }),
    hasOidcConfig || hasOidcTokenSuite
      ? commandCheck({
          name: 'higress-oidc-endpoint-security',
          scope: 'production',
          description: 'Gateway OIDC-compatible RS256 endpoint security probes must pass.',
          args: ['scripts/higress-gateway-smoke.mjs'],
          env: {
            HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE: 'true',
            HIGRESS_OIDC_PRIVATE_KEY_FILE: hasText(env.HIGRESS_OIDC_PRIVATE_KEY_FILE) ? '<provided>' : undefined,
            HIGRESS_OIDC_PRIVATE_KEY_PEM: hasText(env.HIGRESS_OIDC_PRIVATE_KEY_PEM) ? '<provided>' : undefined,
            HIGRESS_OIDC_KEY_ID: hasOidcConfig ? '<provided>' : undefined,
            OIDC_ISSUER: hasOidcConfig ? env.OIDC_ISSUER : undefined,
            OIDC_AUDIENCE: hasOidcConfig ? env.OIDC_AUDIENCE : undefined,
            HIGRESS_OIDC_ACCEPTED_TOKEN: hasText(env.HIGRESS_OIDC_ACCEPTED_TOKEN) ? '<provided>' : undefined,
            HIGRESS_OIDC_WRONG_ISSUER_TOKEN: hasText(env.HIGRESS_OIDC_WRONG_ISSUER_TOKEN) ? '<provided>' : undefined,
            HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN: hasText(env.HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN) ? '<provided>' : undefined,
          },
        })
      : blockedCheck({
          name: 'higress-oidc-endpoint-security',
          scope: 'production',
          description: 'Gateway OIDC smoke requires either customer token-suite evidence or a customer/test IdP signing configuration.',
          missingEnv: [
            'HIGRESS_OIDC_ACCEPTED_TOKEN + HIGRESS_OIDC_WRONG_ISSUER_TOKEN + HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
            'or HIGRESS_OIDC_PRIVATE_KEY_FILE/HIGRESS_OIDC_PRIVATE_KEY_PEM + HIGRESS_OIDC_KEY_ID + OIDC_ISSUER + OIDC_AUDIENCE',
          ],
        }),
    hasDeliveryModelKey
      ? commandCheck({
          name: 'credentialed-delivery-smoke',
          scope: 'production',
          description: 'Full P0-P3 delivery smoke must run with a real external model provider key.',
          args: ['scripts/delivery-local-smoke.mjs'],
          env: {
            DELIVERY_SMOKE_DASHSCOPE_API_KEY: '<provided>',
          },
        })
      : blockedCheck({
          name: 'credentialed-delivery-smoke',
          scope: 'production',
          description: 'Full P0-P3 delivery smoke requires a real external model provider key.',
          missingEnv: ['DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY'],
        }),
  ];
}

export function classifyDeliveryReadinessResult(check, result) {
  const evidence = compactSmokeEvidence(parseJsonObject(result.stdout));
  const passed = result.exitCode === 0 && evidence.passed !== false;

  return {
    name: check.name,
    scope: check.scope,
    status: passed ? 'passed' : 'failed',
    required: check.required === true,
    evidence,
  };
}

export function summarizeDeliveryReadiness(results) {
  const count = (status) => results.filter((result) => result.status === status).length;
  const localBlockingItems = results
    .filter((result) => result.scope === 'local' && result.required && result.status !== 'passed')
    .map((result) => result.name);
  const productionBlockingItems = results
    .filter((result) => result.scope === 'production' && result.required && result.status !== 'passed')
    .map((result) => result.name);

  return {
    localReady: localBlockingItems.length === 0,
    productionReady: productionBlockingItems.length === 0,
    total: results.length,
    passed: count('passed'),
    failed: count('failed'),
    blocked: count('blocked'),
    localBlockingItems,
    productionBlockingItems,
  };
}

export function sanitizeCommandEnv(env = {}) {
  return Object.fromEntries(
    Object.entries(env)
      .filter(([, value]) => value !== undefined && value !== '<provided>')
      .map(([key, value]) => [key, String(value)]),
  );
}
