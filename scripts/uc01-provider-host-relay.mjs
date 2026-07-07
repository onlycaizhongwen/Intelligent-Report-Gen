import http from 'node:http';

import {
  buildHostRelayConfig,
  isAllowedRelayPath,
  relayHeadersFromRequestHeaders,
  targetUrlForRelayRequest,
} from './uc01-provider-host-relay-lib.mjs';

const config = buildHostRelayConfig({
  port: process.env.UC01_PROVIDER_RELAY_PORT,
  listenHost: process.env.UC01_PROVIDER_RELAY_LISTEN_HOST,
  targetOrigin: process.env.UC01_PROVIDER_RELAY_TARGET_ORIGIN,
});

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => resolve(Buffer.concat(chunks)));
    request.on('error', reject);
  });
}

async function handleRelay(request, response) {
  const requestUrl = new URL(request.url ?? '/', 'http://relay.local');
  if (!isAllowedRelayPath(requestUrl.pathname)) {
    response.writeHead(404, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ error: 'relay_path_not_allowed' }));
    return;
  }

  if (requestUrl.pathname === '/health') {
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ status: 'ok', targetOrigin: config.targetOrigin }));
    return;
  }

  const body = ['GET', 'HEAD'].includes(request.method ?? '')
    ? undefined
    : await readBody(request);
  const targetUrl = targetUrlForRelayRequest(request.url ?? '/', config.targetOrigin);
  const upstream = await fetch(targetUrl, {
    method: request.method,
    headers: relayHeadersFromRequestHeaders(request.headers),
    body,
  });
  const upstreamBody = Buffer.from(await upstream.arrayBuffer());
  const headers = Object.fromEntries(upstream.headers.entries());
  delete headers['content-encoding'];
  delete headers['transfer-encoding'];
  headers['content-length'] = String(upstreamBody.length);
  response.writeHead(upstream.status, headers);
  response.end(upstreamBody);
}

const server = http.createServer((request, response) => {
  handleRelay(request, response).catch((error) => {
    response.writeHead(502, { 'content-type': 'application/json' });
    response.end(
      JSON.stringify({
        error: 'provider_relay_failed',
        errorType: error?.name ?? 'Error',
        message: error?.message ?? String(error),
      }),
    );
  });
});

server.listen(Number(config.port), config.listenHost, () => {
  console.log(
    JSON.stringify({
      status: 'started',
      listenHost: config.listenHost,
      port: config.port,
      targetOrigin: config.targetOrigin,
      containerBaseUrl: config.containerBaseUrl,
    }),
  );
});
