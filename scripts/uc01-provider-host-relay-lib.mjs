export function buildHostRelayConfig({
  port = '18091',
  listenHost = '0.0.0.0',
  targetOrigin = 'https://dashscope.aliyuncs.com',
  containerHost = 'host.docker.internal',
} = {}) {
  const normalizedPort = String(port || '18091');
  const normalizedTargetOrigin = String(targetOrigin || 'https://dashscope.aliyuncs.com').replace(/\/+$/, '');

  return {
    port: normalizedPort,
    listenHost,
    targetOrigin: normalizedTargetOrigin,
    healthUrl: `http://127.0.0.1:${normalizedPort}/health`,
    containerBaseUrl: `http://${containerHost}:${normalizedPort}/compatible-mode/v1`,
  };
}

export function buildHostRelayStartStep(options = {}) {
  const config = buildHostRelayConfig(options);
  return {
    name: 'uc01-provider-host-relay',
    command: 'node',
    args: ['scripts/uc01-provider-host-relay-start.mjs'],
    env: {
      UC01_PROVIDER_RELAY_PORT: config.port,
      UC01_PROVIDER_RELAY_LISTEN_HOST: config.listenHost,
      UC01_PROVIDER_RELAY_TARGET_ORIGIN: config.targetOrigin,
    },
  };
}

export function isAllowedRelayPath(pathname) {
  return pathname === '/health' || pathname.startsWith('/compatible-mode/v1/');
}

export function targetUrlForRelayRequest(requestUrl, targetOrigin) {
  const url = new URL(requestUrl, 'http://relay.local');
  const target = new URL(url.pathname + url.search, targetOrigin);
  return target.toString();
}

export function relayHeadersFromRequestHeaders(headers) {
  const blocked = new Set([
    'connection',
    'content-length',
    'host',
    'keep-alive',
    'proxy-authenticate',
    'proxy-authorization',
    'te',
    'trailer',
    'transfer-encoding',
    'upgrade',
  ]);
  const result = {};
  for (const [key, value] of Object.entries(headers)) {
    if (!blocked.has(key.toLowerCase()) && value !== undefined) {
      result[key] = Array.isArray(value) ? value.join(', ') : value;
    }
  }
  return result;
}
