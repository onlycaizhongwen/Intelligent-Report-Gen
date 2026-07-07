import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildDockerRunArgs,
  buildProviderPreflightConfig,
  collectProxyEnv,
} from './uc01-real-provider-worker-lib.mjs';

const execFileAsync = promisify(execFile);

const apiKey =
  process.env.UC01_PROVIDER_PREFLIGHT_API_KEY ?? process.env.DASHSCOPE_API_KEY ?? '';
const containerName =
  process.env.UC01_PROVIDER_PREFLIGHT_CONTAINER ?? 'ir-uc01-provider-preflight';

async function removeExistingContainer(name) {
  try {
    await execFileAsync('docker', ['rm', '-f', name]);
    return true;
  } catch (error) {
    const output = `${error.stdout ?? ''}${error.stderr ?? ''}`;
    if (output.includes('No such container')) {
      return false;
    }
    throw error;
  }
}

async function main() {
  const config = buildProviderPreflightConfig({
    apiKey,
    containerName,
    network: process.env.UC01_PROVIDER_PREFLIGHT_NETWORK,
    image: process.env.UC01_PROVIDER_PREFLIGHT_IMAGE,
    llmBaseUrl: process.env.UC01_PROVIDER_PREFLIGHT_LLM_BASE_URL,
    timeoutSeconds: process.env.UC01_PROVIDER_PREFLIGHT_TIMEOUT_SECONDS,
    proxyEnv: collectProxyEnv(process.env),
  });

  const removed = await removeExistingContainer(config.containerName);
  const args = buildDockerRunArgs(config, {
    referenceEnvKeys: ['LLM_API_KEY'],
  });
  const execOptions = {
    env: {
      ...process.env,
      LLM_API_KEY: config.env.LLM_API_KEY,
    },
    windowsHide: true,
    maxBuffer: 1024 * 1024,
  };

  let stdout = '';
  let stderr = '';
  let exitCode = 0;
  try {
    const result = await execFileAsync('docker', args, execOptions);
    stdout = result.stdout;
    stderr = result.stderr;
  } catch (error) {
    stdout = error.stdout ?? '';
    stderr = error.stderr ?? '';
    exitCode = typeof error.code === 'number' ? error.code : 1;
  }

  console.log(
    JSON.stringify(
      {
        ok: exitCode === 0,
        removedExistingContainer: removed,
        containerName: config.containerName,
        network: config.network,
        image: config.image,
        llmBaseUrl: config.env.LLM_BASE_URL,
        proxy: {
          https: Boolean(config.env.HTTPS_PROXY),
          http: Boolean(config.env.HTTP_PROXY),
          noProxy: config.env.NO_PROXY ?? '',
        },
        stdout: stdout.trim(),
        stderr: stderr.trim(),
        exitCode,
      },
      null,
      2,
    ),
  );

  if (exitCode !== 0) {
    process.exit(exitCode);
  }
}

await main();
