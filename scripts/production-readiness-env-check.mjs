import { readFile } from 'node:fs/promises';

import {
  mergeEnvFileValues,
  parseEnvFileText,
  validateProductionReadinessEnv,
} from './delivery-readiness-audit-lib.mjs';

async function loadEnv() {
  if (!process.env.PRODUCTION_READINESS_ENV_FILE) {
    return process.env;
  }

  const envText = await readFile(process.env.PRODUCTION_READINESS_ENV_FILE, 'utf8');
  return mergeEnvFileValues({
    baseEnv: process.env,
    fileEnv: parseEnvFileText(envText),
  });
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
