import { runEnterpriseDataSourceDockerSmoke } from './data-source-enterprise-docker-smoke-lib.mjs';

const result = await runEnterpriseDataSourceDockerSmoke();
console.log(JSON.stringify(result, null, 2));

if (!result.passed) {
  process.exitCode = 1;
}
