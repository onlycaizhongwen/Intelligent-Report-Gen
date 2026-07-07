export function buildWorkerConfig({
  apiKey,
  containerName = 'ir-report-generation-worker-smoke',
  network = 'intelligent-report-infra_default',
  image = 'intelligent-report-system-python-ai-service',
  entrypoint = 'python',
  command = ['-m', 'app.report_generation.worker_main'],
  detached = true,
  removeAfterExit = false,
  consumerGroup = 'python-ai-report-generation-smoke-real',
  javaServiceUrl = 'http://ir-java-smoke:8080',
  databaseUrl = 'postgresql://report:report123@postgres:5432/intelligent_report',
  opensearchUrl = 'http://ir-opensearch:9200',
  milvusHost = 'ir-milvus',
  milvusPort = '19530',
  rocketmqEndpoint = 'rocketmq-namesrv:9876',
  topic = 'report_generation_outline_confirmed',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
  llmProvider = 'openai-compatible',
  llmModel = 'qwen-plus',
  llmBaseUrl = 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  proxyEnv = {},
} = {}) {
  if (!apiKey) {
    throw new Error('UC01 real provider worker requires apiKey');
  }

  return {
    containerName,
    network,
    image,
    entrypoint,
    detached,
    removeAfterExit,
    command,
    env: {
      REPORT_GENERATION_WORKER_PROVIDER: 'rocketmq',
      ROCKETMQ_ENDPOINT: rocketmqEndpoint,
      ROCKETMQ_REPORT_GENERATION_TOPIC: topic,
      ROCKETMQ_REPORT_GENERATION_CONSUMER_GROUP: consumerGroup,
      JWT_SECRET: jwtSecret,
      JAVA_SERVICE_URL: javaServiceUrl,
      DATABASE_URL: databaseUrl,
      MILVUS_HOST: milvusHost,
      MILVUS_PORT: milvusPort,
      OPENSEARCH_URL: opensearchUrl,
      LLM_PROVIDER: llmProvider,
      LLM_MODEL: llmModel,
      LLM_BASE_URL: llmBaseUrl,
      LLM_API_KEY: apiKey,
      ...normalizeProxyEnv(proxyEnv),
    },
  };
}

export function buildDockerRunArgs(config, { referenceEnvKeys = [] } = {}) {
  const args = [
    'run',
  ];
  const referencedKeys = new Set(referenceEnvKeys);

  if (config.detached !== false) {
    args.push('-d');
  }

  if (config.removeAfterExit) {
    args.push('--rm');
  }

  args.push(
    '--name',
    config.containerName,
    '--network',
    config.network,
    '--entrypoint',
    config.entrypoint,
  );

  for (const [key, value] of Object.entries(config.env)) {
    args.push('-e', referencedKeys.has(key) ? key : `${key}=${value}`);
  }

  args.push(config.image, ...config.command);
  return args;
}

export function buildProviderPreflightConfig({
  apiKey,
  containerName = 'ir-uc01-provider-preflight',
  network = 'intelligent-report-infra_default',
  image = 'intelligent-report-system-python-ai-service',
  entrypoint = 'python',
  llmBaseUrl = 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  timeoutSeconds = '12',
  proxyEnv = {},
} = {}) {
  if (!apiKey) {
    throw new Error('UC01 provider preflight requires apiKey');
  }

  return {
    containerName,
    network,
    image,
    entrypoint,
    detached: false,
    removeAfterExit: true,
    command: ['-c', providerPreflightPython()],
    env: {
      UC01_PROVIDER_PREFLIGHT_LABEL: 'UC01 provider preflight',
      LLM_BASE_URL: llmBaseUrl,
      LLM_API_KEY: apiKey,
      UC01_PROVIDER_PREFLIGHT_TIMEOUT_SECONDS: timeoutSeconds,
      ...normalizeProxyEnv(proxyEnv),
    },
  };
}

export function collectProxyEnv(source = process.env) {
  return normalizeProxyEnv({
    HTTPS_PROXY: source.HTTPS_PROXY ?? source.https_proxy,
    HTTP_PROXY: source.HTTP_PROXY ?? source.http_proxy,
    NO_PROXY: source.NO_PROXY ?? source.no_proxy,
  });
}

function normalizeProxyEnv(proxyEnv = {}) {
  return Object.fromEntries(
    ['HTTPS_PROXY', 'HTTP_PROXY', 'NO_PROXY']
      .map((key) => [key, proxyEnv[key]])
      .filter(([, value]) => typeof value === 'string' && value.trim() !== ''),
  );
}

function providerPreflightPython() {
  return String.raw`
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request

base_url = os.environ["LLM_BASE_URL"].rstrip("/")
api_key = os.environ["LLM_API_KEY"]
timeout = float(os.environ.get("UC01_PROVIDER_PREFLIGHT_TIMEOUT_SECONDS", "12"))
url = base_url + "/models"
headers = {"Authorization": "Bearer " + api_key}
started = time.time()

try:
    request = urllib.request.Request(url, headers=headers, method="GET")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        status = response.getcode()
        body = response.read(256).decode("utf-8", "replace")
    print(json.dumps({
        "label": "UC01 provider preflight",
        "ok": True,
        "url": url,
        "status": status,
        "elapsedMs": int((time.time() - started) * 1000),
        "sample": body[:120],
    }, ensure_ascii=False))
except urllib.error.HTTPError as error:
    body = error.read(256).decode("utf-8", "replace")
    # Any HTTP response proves DNS, proxy and TLS reached the provider boundary.
    print(json.dumps({
        "label": "UC01 provider preflight",
        "ok": True,
        "url": url,
        "status": error.code,
        "elapsedMs": int((time.time() - started) * 1000),
        "sample": body[:120],
    }, ensure_ascii=False))
except Exception as error:
    print(json.dumps({
        "label": "UC01 provider preflight",
        "ok": False,
        "url": url,
        "elapsedMs": int((time.time() - started) * 1000),
        "errorType": type(error).__name__,
        "error": str(error),
        "httpsProxy": bool(os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")),
        "httpProxy": bool(os.environ.get("HTTP_PROXY") or os.environ.get("http_proxy")),
        "noProxy": os.environ.get("NO_PROXY") or os.environ.get("no_proxy") or "",
        "openssl": ssl.OPENSSL_VERSION,
    }, ensure_ascii=False))
    sys.exit(2)
`;
}
