import { readFile } from 'node:fs/promises';

import { validateProductionReadinessEnv } from './delivery-readiness-audit-lib.mjs';

function parseEnvFile(text) {
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

async function loadEnv() {
  if (!process.env.PRODUCTION_READINESS_ENV_FILE) {
    return process.env;
  }

  const envText = await readFile(process.env.PRODUCTION_READINESS_ENV_FILE, 'utf8');
  return {
    ...process.env,
    ...parseEnvFile(envText),
  };
}

try {
  const env = await loadEnv();
  const validation = validateProductionReadinessEnv({ env });
  console.log(JSON.stringify(validation, null, 2));
  process.exit(validation.ready ? 0 : 1);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
