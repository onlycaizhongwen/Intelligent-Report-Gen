import { openSync } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import path from 'node:path';

import { buildHostRelayConfig } from './uc01-provider-host-relay-lib.mjs';

const config = buildHostRelayConfig({
  port: process.env.UC01_PROVIDER_RELAY_PORT,
  listenHost: process.env.UC01_PROVIDER_RELAY_LISTEN_HOST,
  targetOrigin: process.env.UC01_PROVIDER_RELAY_TARGET_ORIGIN,
});

async function isHealthy() {
  try {
    const response = await fetch(config.healthUrl, { signal: AbortSignal.timeout(1000) });
    return response.ok;
  } catch {
    return false;
  }
}

async function waitUntilHealthy() {
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) {
    if (await isHealthy()) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return false;
}

async function main() {
  if (await isHealthy()) {
    console.log(JSON.stringify({ alreadyRunning: true, ...config }, null, 2));
    return;
  }

  await mkdir(path.resolve('.omx', 'logs'), { recursive: true });
  const logPath = path.resolve('.omx', 'logs', 'uc01-provider-host-relay.log');
  const out = openSync(logPath, 'a');
  const child = spawn(process.execPath, ['scripts/uc01-provider-host-relay.mjs'], {
    detached: true,
    stdio: ['ignore', out, out],
    env: {
      ...process.env,
      UC01_PROVIDER_RELAY_PORT: config.port,
      UC01_PROVIDER_RELAY_LISTEN_HOST: config.listenHost,
      UC01_PROVIDER_RELAY_TARGET_ORIGIN: config.targetOrigin,
    },
    windowsHide: true,
  });
  child.unref();

  const healthy = await waitUntilHealthy();
  console.log(
    JSON.stringify(
      {
        alreadyRunning: false,
        started: healthy,
        pid: child.pid,
        logPath,
        ...config,
      },
      null,
      2,
    ),
  );
  if (!healthy) {
    process.exit(1);
  }
}

await main();
