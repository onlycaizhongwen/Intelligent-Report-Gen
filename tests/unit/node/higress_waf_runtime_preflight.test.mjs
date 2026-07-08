import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildContainerRegistryProbeCommand,
  buildWafManifestRequest,
  parseWafPluginOciUrl,
  runHigressWafRuntimePreflight,
} from '../../../scripts/higress-waf-runtime-preflight-lib.mjs';

test('parseWafPluginOciUrl extracts registry, repository, and tag', () => {
  assert.deepEqual(
    parseWafPluginOciUrl('oci://higress-registry.cn-hangzhou.cr.aliyuncs.com/plugins/waf:2.0.0'),
    {
      registry: 'higress-registry.cn-hangzhou.cr.aliyuncs.com',
      repository: 'plugins/waf',
      tag: '2.0.0',
    },
  );
});

test('parseWafPluginOciUrl rejects userinfo without leaking credentials', () => {
  assert.throws(
    () => parseWafPluginOciUrl('oci://user:secret@registry.customer.example/platform/higress-waf:2.0.0'),
    (error) => {
      assert.match(error.message, /must not include credentials/);
      assert.doesNotMatch(error.message, /secret/);
      assert.match(error.message, /<redacted>@registry\.customer\.example/);
      return true;
    },
  );
});

test('buildWafManifestRequest targets the OCI manifest endpoint', () => {
  assert.deepEqual(
    buildWafManifestRequest({
      registry: 'registry.example.test',
      repository: 'plugins/waf',
      tag: '2.0.0',
    }),
    {
      url: 'https://registry.example.test/v2/plugins/waf/manifests/2.0.0',
      headers: {
        Accept: 'application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json',
      },
    },
  );
});

test('buildContainerRegistryProbeCommand checks the registry from the Higress runtime container', () => {
  assert.deepEqual(
    buildContainerRegistryProbeCommand({
      containerName: 'ir-higress',
      registry: 'registry.example.test',
      timeoutSeconds: 12,
    }),
    [
      'docker',
      [
        'exec',
        'ir-higress',
        'sh',
        '-lc',
        'curl -k -sS --max-time 12 https://registry.example.test/v2/ >/tmp/higress-waf-registry-preflight.out',
      ],
    ],
  );
});

test('runHigressWafRuntimePreflight passes when the WAF image manifest is reachable', async () => {
  const result = await runHigressWafRuntimePreflight({
    commandRunner: () => ({ status: 0, stdout: '', stderr: '' }),
    fetchImpl: async (url, options) => ({
      ok: true,
      status: 200,
      statusText: 'OK',
      text: async () => JSON.stringify({ url, accept: options.headers.Accept }),
    }),
  });

  assert.equal(result.passed, true);
  assert.equal(result.classification, 'waf-plugin-manifest-reachable');
  assert.equal(result.status, 200);
  assert.equal(result.containerRegistryReachable, true);
  assert.equal(result.plugin.repository, 'plugins/waf');
  assert.equal(result.activeLocalPlugin, false);
});

test('runHigressWafRuntimePreflight can probe a mirrored WAF plugin URL override', async () => {
  let observedProbeArgs;
  const result = await runHigressWafRuntimePreflight({
    pluginUrlOverride: 'oci://registry.customer.example/platform/higress-waf:2.0.0',
    commandRunner: (_command, args) => {
      observedProbeArgs = args;
      return { status: 0, stdout: '', stderr: '' };
    },
    fetchImpl: async (url) => {
      assert.equal(url, 'https://registry.customer.example/v2/platform/higress-waf/manifests/2.0.0');
      return {
        ok: true,
        status: 200,
        statusText: 'OK',
        text: async () => '{}',
      };
    },
  });

  assert.equal(result.passed, true);
  assert.equal(result.plugin.url, 'oci://registry.customer.example/platform/higress-waf:2.0.0');
  assert.equal(result.plugin.registry, 'registry.customer.example');
  assert.deepEqual(
    observedProbeArgs,
    [
      'exec',
      'ir-higress',
      'sh',
      '-lc',
      'curl -k -sS --max-time 15 https://registry.customer.example/v2/ >/tmp/higress-waf-registry-preflight.out',
    ],
  );
});

test('runHigressWafRuntimePreflight fails closed when the Higress container cannot reach the registry', async () => {
  const result = await runHigressWafRuntimePreflight({
    commandRunner: () => ({ status: 35, stdout: '', stderr: 'unexpected eof while reading' }),
    fetchImpl: async () => ({ ok: true, status: 200, statusText: 'OK', text: async () => '{}' }),
  });

  assert.equal(result.passed, false);
  assert.equal(result.classification, 'waf-plugin-container-registry-unreachable');
  assert.equal(result.containerRegistryReachable, false);
  assert.match(result.containerProbe.stderr, /unexpected eof/);
  assert.equal(result.plugin.url, 'oci://higress-registry.cn-hangzhou.cr.aliyuncs.com/plugins/waf:2.0.0');
});

test('runHigressWafRuntimePreflight fails closed when the WAF image manifest cannot be fetched from the host', async () => {
  const result = await runHigressWafRuntimePreflight({
    commandRunner: () => ({ status: 0, stdout: '', stderr: '' }),
    fetchImpl: async () => {
      throw new Error('EOF');
    },
  });

  assert.equal(result.passed, false);
  assert.equal(result.classification, 'waf-plugin-manifest-unreachable');
  assert.match(result.errorMessage, /EOF/);
});
