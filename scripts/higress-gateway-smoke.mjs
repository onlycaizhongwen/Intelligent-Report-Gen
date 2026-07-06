import { runHigressGatewaySmoke } from './higress-gateway-smoke-lib.mjs';

const result = await runHigressGatewaySmoke({
  gatewayBaseUrl: process.env.HIGRESS_GATEWAY_BASE_URL ?? 'http://127.0.0.1:18000',
});

console.log(JSON.stringify(result, null, 2));

if (!result.passed) {
  process.exitCode = 1;
}
