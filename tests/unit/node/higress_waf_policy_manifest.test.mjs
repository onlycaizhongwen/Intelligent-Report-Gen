import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const activeManifestPath = 'config/higress/local-data/wasmplugins/intelligent-report-waf.yaml';
const candidateManifestPath = 'config/higress/waf/intelligent-report-waf.candidate.yaml';
const activeIngressPath = 'config/higress/local-data/ingresses/intelligent-report-routes.yaml';
const routeTemplatePath = 'config/higress/routes.yaml';

test('Higress WAF policy is kept as a non-active candidate until runtime plugin fetch is available', () => {
  assert.equal(existsSync(activeManifestPath), false);

  const manifest = readFileSync(candidateManifestPath, 'utf8');

  assert.match(manifest, /apiVersion:\s*extensions\.higress\.io\/v1alpha1/);
  assert.match(manifest, /kind:\s*WasmPlugin/);
  assert.match(manifest, /name:\s*intelligent-report-waf/);
  assert.match(
    manifest,
    /url:\s*oci:\/\/higress-registry\.cn-hangzhou\.cr\.aliyuncs\.com\/plugins\/waf:2\.0\.0/,
  );
  assert.match(manifest, /ingress:\s*\n\s*-\s*intelligent-report-routes/);
  assert.match(manifest, /useCRS:\s*true/);
});

test('Higress WAF candidate policy covers all readiness audit attack probes', () => {
  const manifest = readFileSync(candidateManifestPath, 'utf8');

  assert.match(manifest, /id:357001/);
  assert.match(manifest, /1'\\s\+or\\s\+'1'='1/);
  assert.match(manifest, /id:357002/);
  assert.match(manifest, /<script>/);
  assert.match(manifest, /id:357003/);
  assert.match(manifest, /%2e%2e%2f/);
  assert.match(manifest, /id:357004/);
  assert.match(manifest, /ignore previous instructions/);
  assert.match(manifest, /deny,status:403/);
});

test('local active Higress routes do not enable WAF without a locally fetchable plugin', () => {
  for (const path of [activeIngressPath, routeTemplatePath]) {
    const manifest = readFileSync(path, 'utf8');

    assert.doesNotMatch(manifest, /higress\.io\/enable-waf:\s*"true"/);
  }
});
