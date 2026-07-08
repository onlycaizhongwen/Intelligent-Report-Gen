import { existsSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const DEFAULT_CANDIDATE_MANIFEST = 'config/higress/waf/intelligent-report-waf.candidate.yaml';
const DEFAULT_ACTIVE_LOCAL_MANIFEST = 'config/higress/local-data/wasmplugins/intelligent-report-waf.yaml';
const OCI_MANIFEST_ACCEPT = 'application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json';

function hasText(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function readWafPluginUrl(manifestPath) {
  const manifest = readFileSync(manifestPath, 'utf8');
  const match = manifest.match(/^\s*url:\s*(\S+)\s*$/m);
  if (!match) {
    throw new Error(`WAF candidate manifest does not declare spec.url: ${manifestPath}`);
  }
  return match[1].trim();
}

function chooseWafPluginUrl({ pluginUrlOverride, candidateManifestPath }) {
  return hasText(pluginUrlOverride)
    ? pluginUrlOverride.trim()
    : readWafPluginUrl(candidateManifestPath);
}

function redactWafPluginUrl(url) {
  return String(url).replace(/^(oci:\/\/)[^/@\s]+@([^/\s]+)/, '$1<redacted>@$2');
}

export function parseWafPluginOciUrl(url) {
  if (!hasText(url) || !url.startsWith('oci://')) {
    throw new Error(`unsupported WAF plugin URL: ${redactWafPluginUrl(url)}`);
  }
  const withoutScheme = url.slice('oci://'.length);
  const slashIndex = withoutScheme.indexOf('/');
  const tagIndex = withoutScheme.lastIndexOf(':');
  if (slashIndex <= 0 || tagIndex <= slashIndex + 1 || tagIndex === withoutScheme.length - 1) {
    throw new Error(`invalid WAF plugin OCI URL: ${redactWafPluginUrl(url)}`);
  }
  const registry = withoutScheme.slice(0, slashIndex);
  if (registry.includes('@')) {
    throw new Error(`WAF plugin OCI URL must not include credentials: ${redactWafPluginUrl(url)}`);
  }
  return {
    registry,
    repository: withoutScheme.slice(slashIndex + 1, tagIndex),
    tag: withoutScheme.slice(tagIndex + 1),
  };
}

export function buildWafManifestRequest(plugin) {
  return {
    url: `https://${plugin.registry}/v2/${plugin.repository}/manifests/${plugin.tag}`,
    headers: {
      Accept: OCI_MANIFEST_ACCEPT,
    },
  };
}

export function buildContainerRegistryProbeCommand({
  containerName,
  registry,
  timeoutSeconds = 15,
}) {
  return [
    'docker',
    [
      'exec',
      containerName,
      'sh',
      '-lc',
      `curl -k -sS --max-time ${timeoutSeconds} https://${registry}/v2/ >/tmp/higress-waf-registry-preflight.out`,
    ],
  ];
}

function defaultCommandRunner(command, args) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    windowsHide: true,
  });
  return {
    status: result.status ?? 1,
    stdout: result.stdout ?? '',
    stderr: result.stderr ?? result.error?.message ?? '',
  };
}

export async function runHigressWafRuntimePreflight({
  candidateManifestPath = DEFAULT_CANDIDATE_MANIFEST,
  activeLocalManifestPath = DEFAULT_ACTIVE_LOCAL_MANIFEST,
  pluginUrlOverride,
  containerName = 'ir-higress',
  commandRunner = defaultCommandRunner,
  fetchImpl = fetch,
  timeoutMs = 15000,
} = {}) {
  const pluginUrl = chooseWafPluginUrl({ pluginUrlOverride, candidateManifestPath });
  const parsedPlugin = parseWafPluginOciUrl(pluginUrl);
  const request = buildWafManifestRequest(parsedPlugin);
  const activeLocalPlugin = existsSync(activeLocalManifestPath);
  const plugin = { url: pluginUrl, ...parsedPlugin };
  const [probeCommand, probeArgs] = buildContainerRegistryProbeCommand({
    containerName,
    registry: parsedPlugin.registry,
    timeoutSeconds: Math.max(1, Math.ceil(timeoutMs / 1000)),
  });
  const containerProbe = commandRunner(probeCommand, probeArgs);
  if (containerProbe.status !== 0) {
    return {
      passed: false,
      classification: 'waf-plugin-container-registry-unreachable',
      plugin,
      manifestUrl: request.url,
      activeLocalPlugin,
      containerRegistryReachable: false,
      containerProbe: {
        containerName,
        status: containerProbe.status,
        stderr: String(containerProbe.stderr ?? '').slice(0, 500),
        stdout: String(containerProbe.stdout ?? '').slice(0, 500),
      },
    };
  }

  try {
    const response = await fetchImpl(request.url, {
      method: 'GET',
      headers: request.headers,
      signal: AbortSignal.timeout(timeoutMs),
    });
    const body = await response.text();
    const passed = response.ok;
    return {
      passed,
      classification: passed ? 'waf-plugin-manifest-reachable' : 'waf-plugin-manifest-rejected',
      status: response.status,
      statusText: response.statusText,
      plugin,
      manifestUrl: request.url,
      activeLocalPlugin,
      containerRegistryReachable: true,
      bodyPreview: body.slice(0, 240),
    };
  } catch (error) {
    return {
      passed: false,
      classification: 'waf-plugin-manifest-unreachable',
      plugin,
      manifestUrl: request.url,
      activeLocalPlugin,
      containerRegistryReachable: true,
      errorMessage: error?.message ?? String(error),
    };
  }
}
