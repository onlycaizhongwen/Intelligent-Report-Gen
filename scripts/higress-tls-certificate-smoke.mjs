import { runHigressTlsCertificateSmoke } from './higress-tls-certificate-smoke-lib.mjs';

const result = await runHigressTlsCertificateSmoke({
  host: process.env.HIGRESS_TLS_GATEWAY_HOST ?? '127.0.0.1',
  port: Number.parseInt(process.env.HIGRESS_TLS_GATEWAY_PORT ?? '18443', 10),
  servername: process.env.HIGRESS_TLS_SERVER_NAME,
  minValidDays: Number.parseInt(process.env.HIGRESS_TLS_MIN_VALID_DAYS ?? '14', 10),
});

console.log(JSON.stringify(result, null, 2));

if (!result.passed) {
  process.exitCode = 1;
}
