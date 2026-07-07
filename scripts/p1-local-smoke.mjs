import { buildP1SmokeSteps } from './p1-local-smoke-lib.mjs';
import { runSmokeStep } from './local-smoke-runner.mjs';

async function main() {
  const steps = buildP1SmokeSteps({
    realBackendApiBaseUrl:
      process.env.P1_SMOKE_REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1',
    realBackendOrigin:
      process.env.P1_SMOKE_REAL_BACKEND_ORIGIN ?? 'http://127.0.0.1:18082',
    gatewayApiBaseUrl:
      process.env.P1_SMOKE_HIGRESS_API_BASE_URL ?? 'http://127.0.0.1:18000/api/v1',
    gatewayOrigin:
      process.env.P1_SMOKE_HIGRESS_ORIGIN ?? 'http://127.0.0.1:18000',
  });

  const results = [];
  for (const step of steps) {
    const result = await runSmokeStep(step);
    results.push(result);
  }

  console.log(
    JSON.stringify(
      {
        completedSteps: results.map((result) => result.name),
        results,
        plannedSteps: steps.map((step) => ({
          name: step.name,
          command: step.command,
          args: step.args,
          workdir: step.workdir ?? '.',
        })),
      },
      null,
      2,
    ),
  );
}

await main();
