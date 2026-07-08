import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';

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

const ENV_FILE_CONTROL_KEYS = new Set([
  'DELIVERY_READINESS_ENV_FILE',
  'DELIVERY_READINESS_OUTPUT',
  'DELIVERY_READINESS_REPORT_FILE',
  'DELIVERY_READINESS_ENV_TEMPLATE_FILE',
  'PRODUCTION_READINESS_ENV_FILE',
]);

export function parseEnvFileText(text) {
  const entries = {};
  for (const line of String(text).split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }

    const assignment = trimmed.startsWith('export ') ? trimmed.slice('export '.length).trim() : trimmed;
    const separator = assignment.indexOf('=');
    if (separator <= 0) {
      continue;
    }

    const name = assignment.slice(0, separator).trim();
    const rawValue = assignment.slice(separator + 1).trim();
    entries[name] = rawValue.replace(/^(['"])(.*)\1$/, '$2');
  }
  return entries;
}

export function mergeEnvFileValues({ baseEnv = process.env, fileEnv = {} } = {}) {
  const evidenceEnv = Object.fromEntries(
    Object.entries(fileEnv).filter(([key]) => !ENV_FILE_CONTROL_KEYS.has(key)),
  );
  return {
    ...evidenceEnv,
    ...baseEnv,
  };
}

export async function loadDeliveryReadinessEnv({
  baseEnv = process.env,
  readFileImpl = readFile,
} = {}) {
  if (!hasText(baseEnv.DELIVERY_READINESS_ENV_FILE)) {
    return baseEnv;
  }

  const envText = await readFileImpl(baseEnv.DELIVERY_READINESS_ENV_FILE, 'utf8');
  return mergeEnvFileValues({
    baseEnv,
    fileEnv: parseEnvFileText(envText),
  });
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

function commandCheck({
  name,
  scope,
  description,
  command = 'node',
  args,
  env = {},
  timeoutMs = 120_000,
}) {
  return {
    name,
    scope,
    required: true,
    kind: 'command',
    description,
    command,
    args,
    timeoutMs,
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
      timeoutMs: 60_000,
    }),
    commandCheck({
      name: 'higress-local-oidc-test-idp-smoke',
      scope: 'local',
      description: 'Local Higress route must pass RS256/JWKS OIDC probes against a temporary test IdP and restore the default route.',
      args: ['scripts/higress-oidc-local-smoke.mjs'],
      timeoutMs: 180_000,
    }),
    commandCheck({
      name: 'higress-waf-runtime-preflight',
      scope: 'production',
      description: 'Gateway WAF plugin OCI image must be reachable before enabling the blocking policy.',
      args: ['scripts/higress-waf-runtime-preflight.mjs'],
      timeoutMs: 45_000,
      env: {
        HIGRESS_WAF_PLUGIN_URL: hasText(env.HIGRESS_WAF_PLUGIN_URL) ? '<provided>' : undefined,
      },
    }),
    commandCheck({
      name: 'higress-waf-blocking-policy',
      scope: 'production',
      description: 'Gateway WAF policy must block representative SQLi, XSS, path traversal, and prompt-injection probes.',
      args: ['scripts/higress-gateway-smoke.mjs'],
      timeoutMs: 60_000,
      env: {
        HIGRESS_GATEWAY_BASE_URL: hasText(env.HIGRESS_GATEWAY_BASE_URL) ? '<provided>' : undefined,
        HIGRESS_WAF_BLOCKING_COVERAGE: 'true',
      },
    }),
    commandCheck({
      name: 'higress-trusted-tls-certificate',
      scope: 'production',
      description: 'Gateway TLS certificate must be trusted and valid for the configured minimum window.',
      args: ['scripts/higress-tls-certificate-smoke.mjs'],
      timeoutMs: 45_000,
    }),
    hasOidcConfig || hasOidcTokenSuite
      ? commandCheck({
          name: 'higress-oidc-endpoint-security',
          scope: 'production',
          description: 'Gateway OIDC-compatible RS256 endpoint security probes must pass.',
          args: ['scripts/higress-gateway-smoke.mjs'],
          timeoutMs: 120_000,
          env: {
            HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE: 'true',
            HIGRESS_GATEWAY_BASE_URL: hasText(env.HIGRESS_GATEWAY_BASE_URL) ? '<provided>' : undefined,
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
          timeoutMs: 300_000,
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
  const timeoutEvidence = result.timedOut
    ? {
        classification: 'command-timeout',
        timedOut: true,
        signal: result.signal,
        timeoutMs: result.timeoutMs,
      }
    : {};
  const passed = result.exitCode === 0 && evidence.passed !== false;

  return {
    name: check.name,
    scope: check.scope,
    status: passed ? 'passed' : 'failed',
    required: check.required === true,
    evidence: {
      ...evidence,
      ...timeoutEvidence,
    },
  };
}

export function summarizeDeliveryReadiness(results) {
  const count = (status) => results.filter((result) => result.status === status).length;
  const localPassedItems = results
    .filter((result) => result.scope === 'local' && result.required && result.status === 'passed')
    .map((result) => result.name);
  const productionPassedItems = results
    .filter((result) => result.scope === 'production' && result.required && result.status === 'passed')
    .map((result) => result.name);
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
    localPassedItems,
    productionPassedItems,
    localBlockingItems,
    productionBlockingItems,
  };
}

const PRODUCTION_ACTIONS = {
  'higress-waf-runtime-preflight': {
    requiredInputs: ['HIGRESS_WAF_PLUGIN_URL'],
    commands: ['HIGRESS_WAF_PLUGIN_URL=<plugin-oci-url> node scripts/higress-waf-runtime-preflight.mjs'],
    nextAction: 'mirror the approved Higress WAF OCI plugin into a registry reachable from the Higress runtime, set HIGRESS_WAF_PLUGIN_URL, then rerun the runtime preflight before enabling WAF.',
    requiredEvidence: 'Preflight returns passed=true and containerRegistryReachable=true for the configured plugin registry.',
  },
  'higress-waf-blocking-policy': {
    requiredInputs: ['HIGRESS_GATEWAY_BASE_URL', 'HIGRESS_WAF_BLOCKING_COVERAGE'],
    commands: ['HIGRESS_GATEWAY_BASE_URL=<target-gateway-url> HIGRESS_WAF_BLOCKING_COVERAGE=true node scripts/higress-gateway-smoke.mjs'],
    nextAction: 'enable the approved Higress WAF policy only after the runtime plugin preflight passes, set the target HIGRESS_GATEWAY_BASE_URL, then prove SQLi, XSS, path traversal, and prompt-injection probes are blocked at that gateway.',
    requiredEvidence: 'Gateway WAF blocking smoke returns passed=true with no waf-not-blocked failedResults.',
  },
  'higress-trusted-tls-certificate': {
    requiredInputs: ['HIGRESS_TLS_GATEWAY_HOST', 'HIGRESS_TLS_SERVER_NAME'],
    optionalInputs: ['HIGRESS_TLS_CA_FILE'],
    commands: ['HIGRESS_TLS_GATEWAY_HOST=<gateway-host> HIGRESS_TLS_SERVER_NAME=<server-name> node scripts/higress-tls-certificate-smoke.mjs'],
    nextAction: 'install a trusted gateway certificate for the customer hostname, configure hostname/servername and optional private CA bundle, then rerun the TLS smoke with verification enabled.',
    requiredEvidence: 'TLS smoke returns passed=true/classification=tls-trusted with daysRemaining above the configured minimum.',
  },
  'higress-oidc-endpoint-security': {
    requiredInputs: [
      'HIGRESS_GATEWAY_BASE_URL',
      'HIGRESS_OIDC_ACCEPTED_TOKEN',
      'HIGRESS_OIDC_WRONG_ISSUER_TOKEN',
      'HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN',
    ],
    inputOptions: [
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
    ],
    commands: ['HIGRESS_GATEWAY_BASE_URL=<target-gateway-url> HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE=true node scripts/higress-gateway-smoke.mjs'],
    nextAction: 'set the target HIGRESS_GATEWAY_BASE_URL, provide a customer token suite or signing/JWKS test configuration, then prove accepted issuer/audience succeeds and wrong issuer/audience are rejected through Higress.',
    requiredEvidence: 'OIDC endpoint security smoke returns passed=true for accepted-token 200 and wrong issuer/audience 401 probes.',
  },
  'credentialed-delivery-smoke': {
    requiredInputs: ['DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY'],
    commands: ['DELIVERY_SMOKE_DASHSCOPE_API_KEY=<provider-api-key> node scripts/delivery-local-smoke.mjs'],
    nextAction: 'provide a real external model provider key and run the full P0-P3 delivery smoke against the target environment.',
    requiredEvidence: 'Credentialed delivery smoke completes P0, P1, P2, and P3 with passed status.',
  },
};

function buildDefaultProductionAction(result, check) {
  return {
    requiredInputs: [],
    commands: check?.kind === 'command' && Array.isArray(check.args)
      ? [`${check.command ?? 'node'} ${check.args.join(' ')}`]
      : [],
    nextAction: check?.description ?? 'collect target-environment evidence and rerun this production readiness check.',
    requiredEvidence: 'The production readiness check returns status=passed.',
    observed: {
      status: result.status,
      classification: result.evidence?.classification,
    },
  };
}

export function buildProductionReadinessActionPlan({ checks = [], results = [] } = {}) {
  const checksByName = new Map(checks.map((check) => [check.name, check]));
  const blockingItems = results
    .filter((result) => result.scope === 'production' && result.required && result.status !== 'passed')
    .map((result) => {
      const check = checksByName.get(result.name);
      const action = PRODUCTION_ACTIONS[result.name] ?? buildDefaultProductionAction(result, check);
      return {
        name: result.name,
        status: result.status,
        description: check?.description,
        requiredInputs: action.requiredInputs,
        optionalInputs: action.optionalInputs ?? [],
        inputOptions: action.inputOptions ?? [],
        commands: action.commands,
        nextAction: action.nextAction,
        requiredEvidence: action.requiredEvidence,
        observed: {
          status: result.status,
          classification: result.evidence?.classification,
          authorizationError: result.evidence?.authorizationError,
          failedResultCount: Array.isArray(result.evidence?.failedResults)
            ? result.evidence.failedResults.length
            : undefined,
          missingEnv: result.evidence?.missingEnv,
        },
      };
    });

  return {
    ready: blockingItems.length === 0,
    blockingItems,
  };
}

function renderInlineCodeList(values = []) {
  if (!Array.isArray(values) || values.length === 0) {
    return '`none`';
  }
  return values.map((value) => `\`${value}\``).join(', ');
}

function renderInputOptions(inputOptions = []) {
  if (!Array.isArray(inputOptions) || inputOptions.length === 0) {
    return [];
  }

  return [
    'Input options:',
    ...inputOptions.map((option) => {
      const inputs = renderInlineCodeList(option.requiredInputs);
      return `- ${option.name}: ${inputs}; evidence: ${option.requiredEvidence}`;
    }),
  ];
}

function renderObserved(observed = {}) {
  const entries = Object.entries(observed)
    .filter(([, value]) => value !== undefined)
    .map(([key, value]) => {
      const rendered = Array.isArray(value) ? value.join('; ') : String(value);
      return `- ${key}: ${rendered}`;
    });
  return entries.length > 0 ? entries.join('\n') : '- none';
}

function renderPassedProductionEvidence(results = []) {
  const passedItems = Array.isArray(results)
    ? results.filter((result) => result.scope === 'production'
      && result.required
      && result.status === 'passed')
    : [];
  if (passedItems.length === 0) {
    return [];
  }

  const lines = ['## Passed Production Evidence', ''];
  for (const item of passedItems) {
    lines.push(
      `### ${item.name}`,
      '',
      'Evidence:',
      renderObserved(item.evidence),
      '',
    );
  }
  return lines;
}

function splitEnvAlternatives(input) {
  return String(input)
    .split(/\s+or\s+/i)
    .map((value) => value.trim())
    .filter(Boolean);
}

function renderEnvAssignment(name, { commented = false } = {}) {
  const defaultValues = {
    HIGRESS_WAF_BLOCKING_COVERAGE: 'true',
  };
  const prefix = commented ? '# ' : '';
  return `${prefix}${name}=${defaultValues[name] ?? ''}`;
}

function renderEnvInputLines(inputs = [], renderedInputs = new Set()) {
  const lines = [];
  for (const input of inputs) {
    const alternatives = splitEnvAlternatives(input);
    alternatives.forEach((name, index) => {
      if (renderedInputs.has(name)) {
        return;
      }
      renderedInputs.add(name);
      lines.push(renderEnvAssignment(name, { commented: index > 0 }));
    });
  }
  return lines;
}

function envInputSatisfied(input, env, defaults = {}) {
  return splitEnvAlternatives(input).some((name) => hasText(env[name]) || hasText(defaults[name]));
}

function missingRequiredInputs(inputs = [], env = {}, defaults = {}) {
  return inputs.filter((input) => !envInputSatisfied(input, env, defaults));
}

export function validateProductionReadinessEnv({ env = process.env } = {}) {
  const defaults = {
    HIGRESS_WAF_BLOCKING_COVERAGE: 'true',
  };
  const missingItems = Object.entries(PRODUCTION_ACTIONS)
    .map(([name, action]) => {
      if (Array.isArray(action.inputOptions) && action.inputOptions.length > 0) {
        const optionResults = action.inputOptions.map((option) => {
          const missingInputs = missingRequiredInputs(option.requiredInputs, env, defaults);
          return {
            name: option.name,
            ready: missingInputs.length === 0,
            missingInputs,
          };
        });

        return optionResults.some((option) => option.ready)
          ? null
          : {
              name,
              missingInputs: action.requiredInputs ?? [],
              optionResults,
            };
      }

      const missingInputs = missingRequiredInputs(action.requiredInputs, env, defaults);
      return missingInputs.length === 0
        ? null
        : {
            name,
            missingInputs,
          };
    })
    .filter(Boolean);

  return {
    ready: missingItems.length === 0,
    missingItems,
  };
}

export function renderProductionReadinessEnvTemplate({
  generatedAt = new Date().toISOString(),
  actionPlan = { ready: true, blockingItems: [] },
} = {}) {
  const lines = [
    '# Production Readiness Evidence Environment Template',
    `# Generated: ${generatedAt}`,
    '# Fill these values in the target/customer environment, then rerun the listed readiness commands.',
    '# Precheck after filling: PRODUCTION_READINESS_ENV_FILE=<this-file> node scripts/production-readiness-env-check.mjs',
    '# Full audit after precheck: DELIVERY_READINESS_ENV_FILE=<this-file> node scripts/delivery-readiness-audit.mjs',
    '',
  ];

  const blockingItems = Array.isArray(actionPlan.blockingItems) ? actionPlan.blockingItems : [];
  const renderedItems = new Map(blockingItems.map((item) => [item.name, item]));
  for (const [name, action] of Object.entries(PRODUCTION_ACTIONS)) {
    if (!renderedItems.has(name)) {
      renderedItems.set(name, {
        name,
        requiredInputs: action.requiredInputs ?? [],
        optionalInputs: action.optionalInputs ?? [],
        inputOptions: action.inputOptions ?? [],
      });
    }
  }

  const renderedInputs = new Set();
  for (const item of renderedItems.values()) {
    lines.push(`# ${item.name}`);
    if (Array.isArray(item.inputOptions) && item.inputOptions.length > 0) {
      for (const option of item.inputOptions) {
        lines.push(`# Option: ${option.name}`);
        lines.push(...renderEnvInputLines(option.requiredInputs, renderedInputs));
      }
    } else {
      lines.push(...renderEnvInputLines(item.requiredInputs, renderedInputs));
    }
    if (Array.isArray(item.optionalInputs) && item.optionalInputs.length > 0) {
      lines.push('# Optional');
      for (const input of item.optionalInputs) {
        for (const name of splitEnvAlternatives(input)) {
          if (renderedInputs.has(name)) {
            continue;
          }
          renderedInputs.add(name);
          lines.push(renderEnvAssignment(name, { commented: true }));
        }
      }
    }
    lines.push('');
  }

  return lines.join('\n');
}

export function renderProductionReadinessActionPlanMarkdown({
  generatedAt = new Date().toISOString(),
  summary = {},
  actionPlan = { ready: true, blockingItems: [] },
  results = [],
} = {}) {
  const lines = [
    '# Production Readiness Action Plan',
    '',
    `Generated: ${generatedAt}`,
    '',
    `Local ready: ${summary.localReady === true}`,
    `Production ready: ${summary.productionReady === true}`,
    `Passed production gates: ${(summary.productionPassedItems ?? []).join(', ') || 'none'}`,
    `Production blockers: ${(summary.productionBlockingItems ?? []).join(', ') || 'none'}`,
    '',
  ];

  lines.push(...renderPassedProductionEvidence(results));

  if (actionPlan.ready || !Array.isArray(actionPlan.blockingItems) || actionPlan.blockingItems.length === 0) {
    lines.push('No production readiness blockers are currently reported.', '');
    return lines.join('\n');
  }

  for (const item of actionPlan.blockingItems) {
    lines.push(
      `## ${item.name}`,
      '',
      `Status: ${item.status}`,
      `Description: ${item.description ?? ''}`,
      `Required inputs: ${renderInlineCodeList(item.requiredInputs)}`,
      `Optional inputs: ${renderInlineCodeList(item.optionalInputs)}`,
      ...renderInputOptions(item.inputOptions),
      '',
      'Commands:',
      '',
    );
    for (const command of item.commands ?? []) {
      lines.push('```bash', command, '```', '');
    }
    lines.push(
      `Next action: ${item.nextAction}`,
      `Required evidence: ${item.requiredEvidence}`,
      '',
      'Observed:',
      renderObserved(item.observed),
      '',
    );
  }

  return lines.join('\n');
}

export function formatDeliveryReadinessAuditOutput({
  payload,
  outputFormat = 'json',
  generatedAt = new Date().toISOString(),
} = {}) {
  if (outputFormat === 'markdown') {
    return renderProductionReadinessActionPlanMarkdown({
      generatedAt,
      summary: payload?.summary,
      actionPlan: payload?.actionPlan,
      results: payload?.results,
    });
  }
  return JSON.stringify(payload, null, 2);
}

export async function writeDeliveryReadinessReportFile({
  reportFile,
  content,
  mkdirImpl = mkdir,
  writeFileImpl = writeFile,
} = {}) {
  if (!hasText(reportFile)) {
    return false;
  }
  await mkdirImpl(dirname(reportFile), { recursive: true });
  await writeFileImpl(reportFile, content, 'utf8');
  return true;
}

export function sanitizeCommandEnv(env = {}) {
  return Object.fromEntries(
    Object.entries(env)
      .filter(([, value]) => value !== undefined && value !== '<provided>')
      .map(([key, value]) => [key, String(value)]),
  );
}
