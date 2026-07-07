import { buildJavaControllerAuthorizationMatrix } from './controller-authorization-matrix-lib.mjs';
import { runHigressGatewaySmoke } from './higress-gateway-smoke-lib.mjs';

const controllerEndpointAuthorizationSampleLimit = Number.parseInt(
  process.env.HIGRESS_CONTROLLER_ENDPOINT_AUTH_SAMPLE_LIMIT ?? '0',
  10,
);
const controllerEndpointAuthorizationNegativeCoverage = ['1', 'true', 'all', 'yes'].includes(
  (process.env.HIGRESS_CONTROLLER_ENDPOINT_AUTH_NEGATIVE_COVERAGE ?? '').toLowerCase(),
);
const needsControllerMatrix = controllerEndpointAuthorizationSampleLimit > 0
  || controllerEndpointAuthorizationNegativeCoverage;

const result = await runHigressGatewaySmoke({
  gatewayBaseUrl: process.env.HIGRESS_GATEWAY_BASE_URL ?? 'http://127.0.0.1:18000',
  controllerEndpointAuthorizationSampleLimit,
  controllerEndpointAuthorizationNegativeCoverage,
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
