import { runHigressWafRuntimePreflight } from './higress-waf-runtime-preflight-lib.mjs';

const result = await runHigressWafRuntimePreflight({
  candidateManifestPath: process.env.HIGRESS_WAF_CANDIDATE_MANIFEST
    ?? 'config/higress/waf/intelligent-report-waf.candidate.yaml',
  activeLocalManifestPath: process.env.HIGRESS_WAF_ACTIVE_LOCAL_MANIFEST
    ?? 'config/higress/local-data/wasmplugins/intelligent-report-waf.yaml',
  containerName: process.env.HIGRESS_WAF_PREFLIGHT_CONTAINER ?? 'ir-higress',
  timeoutMs: Number(process.env.HIGRESS_WAF_PREFLIGHT_TIMEOUT_MS ?? 15000),
});

console.log(JSON.stringify(result, null, 2));

if (!result.passed) {
  process.exitCode = 1;
}
