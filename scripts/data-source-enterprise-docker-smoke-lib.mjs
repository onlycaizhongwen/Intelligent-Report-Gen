import { createServer } from 'node:http';
import { execFile } from 'node:child_process';
import crypto from 'node:crypto';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

const DEFAULT_API_BASE_URL = 'http://127.0.0.1:18082/api/v1';
const DEFAULT_JWT_SECRET = 'local-dev-secret-change-me-32-bytes-minimum';
const DEFAULT_MYSQL_CONTAINER = 'intelligent-report-system-mysql-1';
const DEFAULT_MYSQL_PORT = 13306;

export function buildEnterpriseDataSourceSmokePlan({
  apiBaseUrl = DEFAULT_API_BASE_URL,
  fixtureOrigin = 'http://host.docker.internal:0',
  mysqlJdbcHost = 'host.docker.internal',
  mysqlPort = DEFAULT_MYSQL_PORT,
} = {}) {
  return {
    apiBaseUrl,
    requiredContainers: ['ir-java-smoke', 'ir-postgres'],
    optionalContainers: [DEFAULT_MYSQL_CONTAINER],
    sources: buildEnterpriseDataSourceDefinitions({ fixtureOrigin, mysqlJdbcHost, mysqlPort }),
  };
}

export function buildEnterpriseDataSourceDefinitions({
  fixtureOrigin,
  mysqlJdbcHost = 'host.docker.internal',
  mysqlPort = DEFAULT_MYSQL_PORT,
} = {}) {
  if (!fixtureOrigin) {
    throw new Error('fixtureOrigin is required');
  }
  const normalizedFixtureOrigin = fixtureOrigin.replace(/\/$/, '');
  return [
    {
      key: 'erp-mysql',
      displayName: 'ERP MySQL orders',
      expectedProcessedRows: 2,
      expectedTitles: ['ERP overdue invoice', 'ERP revenue forecast'],
      dataSource: {
        name: 'ERP MySQL Docker Smoke',
        sourceType: 'mysql',
        endpoint: `jdbc:mysql://${mysqlJdbcHost}:${mysqlPort}/erp_source?allowPublicKeyRetrieval=true&useSSL=false`,
        username: 'erp',
        password: 'erp123',
        syncQuery: 'select id, title, content from smoke_erp_orders order by id asc',
        cursorColumn: 'id',
        maxRetryCount: 2,
      },
    },
    {
      key: 'oa-api',
      displayName: 'OA document API',
      expectedProcessedRows: 2,
      expectedTitles: ['OA-2026-001', 'OA-2026-002'],
      dataSource: {
        name: 'OA API Docker Smoke',
        sourceType: 'api',
        endpoint: `${normalizedFixtureOrigin}/oa/documents`,
        username: 'oa_reader',
        password: 'oa-token',
        cursorColumn: 'id',
        maxRetryCount: 2,
        fieldMapping: {
          profileId: 'oa-documents',
          rowsPath: 'data.documents',
          titleField: 'documentNo',
          contentField: 'content',
          cursorField: 'id',
          method: 'GET',
          authType: 'bearer',
          pageParam: 'page',
          pageSizeParam: 'pageSize',
          pageStart: 1,
          pageSize: 1,
          maxPages: 2,
          headers: {
            'X-System': 'oa',
          },
        },
      },
    },
    {
      key: 'finance-api',
      displayName: 'Finance voucher API',
      expectedProcessedRows: 2,
      expectedTitles: ['FIN-2026-001', 'FIN-2026-002'],
      dataSource: {
        name: 'Finance API Docker Smoke',
        sourceType: 'api',
        endpoint: `${normalizedFixtureOrigin}/finance/vouchers`,
        username: 'finance_reader',
        password: 'finance-secret',
        cursorColumn: 'voucherId',
        maxRetryCount: 2,
        fieldMapping: {
          profileId: 'finance-vouchers',
          rowsPath: 'data.vouchers',
          titleField: 'voucherNo',
          contentField: 'summary',
          cursorField: 'voucherId',
          method: 'POST',
          authType: 'api_key',
          apiKeyHeader: 'X-API-Key',
          bodyTemplate: '{"period":"2026Q1"}',
          pageParam: 'page',
          pageSizeParam: 'pageSize',
          pageStart: 1,
          pageSize: 1,
          maxPages: 2,
          headers: {
            'X-Tenant': 'finance',
          },
        },
      },
    },
  ];
}

export async function runEnterpriseDataSourceDockerSmoke({
  apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? DEFAULT_API_BASE_URL,
  jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? DEFAULT_JWT_SECRET,
  mysqlContainer = process.env.MYSQL_CONTAINER ?? DEFAULT_MYSQL_CONTAINER,
  mysqlJdbcHost = process.env.MYSQL_JDBC_HOST ?? 'host.docker.internal',
  mysqlPort = Number(process.env.MYSQL_PORT ?? DEFAULT_MYSQL_PORT),
} = {}) {
  await ensureDockerContainerRunning('ir-java-smoke', { required: true });
  await ensureDockerContainerRunning('ir-postgres', { required: true });
  await ensureMysqlContainer(mysqlContainer);
  await seedMysqlEnterpriseRows(mysqlContainer);

  const fixtureServer = await startEnterpriseApiFixtureServer();
  try {
    const fixtureOrigin = `http://host.docker.internal:${fixtureServer.port}`;
    const plan = buildEnterpriseDataSourceSmokePlan({
      apiBaseUrl,
      fixtureOrigin,
      mysqlJdbcHost,
      mysqlPort,
    });
    const token = buildJwt({
      secret: jwtSecret,
      sub: '9307',
      roles: ['ADMIN'],
      permissions: ['datasource:manage', 'knowledge:manage'],
      status: 'enabled',
    });
    const results = [];
    for (const source of plan.sources) {
      results.push(await runSourceSmoke({ apiBaseUrl, token, source }));
    }
    return {
      apiBaseUrl,
      passed: results.every((result) => result.passed),
      fixtureRequests: fixtureServer.state.requests,
      results,
    };
  } finally {
    await fixtureServer.close();
  }
}

async function runSourceSmoke({ apiBaseUrl, token, source }) {
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
  const knowledgeBase = await apiJson(`${apiBaseUrl}/knowledge-bases`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ name: `Enterprise ${source.key} ${Date.now()}` }),
  });
  const knowledgeBaseId = knowledgeBase.data.knowledgeBaseId;
  const savePayload = {
    ...source.dataSource,
    knowledgeBaseId,
  };
  const saved = await apiJson(`${apiBaseUrl}/data-sources`, {
    method: 'POST',
    headers,
    body: JSON.stringify(savePayload),
  });
  const dataSourceId = saved.data.dataSourceId;
  const connection = await apiJson(`${apiBaseUrl}/data-sources/test-connection`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ dataSourceId }),
  });
  const syncRun = await apiJson(`${apiBaseUrl}/data-sources/${dataSourceId}/sync-runs`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ mode: 'manual', timeoutMs: 30000 }),
  });
  const titleChecks = [];
  for (const title of source.expectedTitles) {
    const search = await apiJson(`${apiBaseUrl}/knowledge-items?page=1&pageSize=10&keyword=${encodeURIComponent(title)}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    titleChecks.push({
      title,
      found: search.data.items.some((item) => item.title === title),
    });
  }
  const passed = connection.data.success === true
    && syncRun.data.status === 'succeeded'
    && Number(syncRun.data.processedRows) === source.expectedProcessedRows
    && titleChecks.every((check) => check.found);
  return {
    key: source.key,
    dataSourceId,
    knowledgeBaseId,
    connectionSuccess: connection.data.success,
    syncStatus: syncRun.data.status,
    processedRows: syncRun.data.processedRows,
    lastCursor: syncRun.data.lastCursor,
    titleChecks,
    passed,
  };
}

async function apiJson(url, options = {}) {
  const response = await fetch(url, options);
  const body = await response.text();
  let payload;
  try {
    payload = JSON.parse(body);
  } catch {
    payload = { rawBody: body };
  }
  if (!response.ok || payload.code < 200 || payload.code >= 300) {
    throw new Error(`API request failed ${response.status} ${url}: ${body}`);
  }
  return payload;
}

async function ensureMysqlContainer(containerName) {
  const exists = await dockerContainerExists(containerName);
  if (!exists) {
    await runCommand('docker', ['compose', '--env-file', '.env.example', 'up', '-d', 'mysql']);
  } else {
    await ensureDockerContainerRunning(containerName, { required: false });
  }
  await waitForHealthyContainer(containerName, 120_000);
}

async function ensureDockerContainerRunning(containerName, { required }) {
  const exists = await dockerContainerExists(containerName);
  if (!exists && required) {
    throw new Error(`required Docker container not found: ${containerName}`);
  }
  if (!exists) {
    return;
  }
  const running = (await dockerInspect(containerName, '{{.State.Running}}')).trim() === 'true';
  if (!running) {
    await runCommand('docker', ['start', containerName]);
  }
}

async function dockerContainerExists(containerName) {
  const result = await runCommand('docker', ['inspect', containerName], { reject: false });
  return result.exitCode === 0;
}

async function waitForHealthyContainer(containerName, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const status = (await dockerInspect(containerName, '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{if .State.Running}}running{{else}}stopped{{end}}{{end}}')).trim();
    if (status === 'healthy' || status === 'running') {
      return;
    }
    await sleep(2000);
  }
  throw new Error(`Docker container did not become healthy: ${containerName}`);
}

async function dockerInspect(containerName, format) {
  const result = await runCommand('docker', ['inspect', '--format', format, containerName]);
  return result.stdout;
}

async function seedMysqlEnterpriseRows(containerName) {
  const sql = `
DROP TABLE IF EXISTS smoke_erp_orders;
CREATE TABLE smoke_erp_orders (
  id INT PRIMARY KEY,
  title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL
);
INSERT INTO smoke_erp_orders (id, title, content) VALUES
  (1, 'ERP overdue invoice', 'Invoice A-100 is overdue and requires finance follow-up.'),
  (2, 'ERP revenue forecast', 'Revenue forecast improved after order backlog cleanup.');
`;
  await runCommand('docker', [
    'exec',
    containerName,
    'mysql',
    '-uerp',
    '-perp123',
    'erp_source',
    '-e',
    sql,
  ]);
}

async function startEnterpriseApiFixtureServer() {
  const state = { requests: [] };
  const server = createServer(async (req, res) => {
    const url = new URL(req.url ?? '/', 'http://127.0.0.1');
    const chunks = [];
    for await (const chunk of req) {
      chunks.push(typeof chunk === 'string' ? Buffer.from(chunk) : chunk);
    }
    const body = Buffer.concat(chunks).toString('utf8');
    state.requests.push({
      method: req.method,
      path: `${url.pathname}${url.search}`,
      authScheme: req.headers.authorization?.toString().split(/\s+/)[0] ?? '',
      apiKeyReceived: Boolean(req.headers['x-api-key']),
      tenant: req.headers['x-tenant'] ? 'present' : '',
      body,
    });
    const page = url.searchParams.get('page') ?? '1';
    const payload = fixturePayload(url.pathname, page);
    res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(payload));
  });
  await new Promise((resolve) => server.listen(0, '0.0.0.0', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('fixture server address not available');
  }
  return {
    port: address.port,
    state,
    close: () => new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve()))),
  };
}

function fixturePayload(pathname, page) {
  if (pathname === '/oa/documents') {
    return page === '2'
      ? { data: { documents: [{ id: 2, documentNo: 'OA-2026-002', content: 'OA approval memo requires legal review.' }] } }
      : { data: { documents: [{ id: 1, documentNo: 'OA-2026-001', content: 'OA policy update for regional operations.' }] } };
  }
  if (pathname === '/finance/vouchers') {
    return page === '2'
      ? { data: { vouchers: [{ voucherId: 2, voucherNo: 'FIN-2026-002', summary: 'Travel reimbursement exception needs review.' }] } }
      : { data: { vouchers: [{ voucherId: 1, voucherNo: 'FIN-2026-001', summary: 'Quarterly accrual voucher is ready.' }] } };
  }
  return { data: { items: [] } };
}

function buildJwt({ secret, ...payload }) {
  const header = base64UrlJson({ alg: 'HS256', typ: 'JWT' });
  const now = Math.floor(Date.now() / 1000);
  const body = base64UrlJson({ iat: now, exp: now + 7200, ...payload });
  const unsigned = `${header}.${body}`;
  const signature = crypto.createHmac('sha256', secret).update(unsigned).digest('base64url');
  return `${unsigned}.${signature}`;
}

function base64UrlJson(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

async function runCommand(command, args, { reject = true } = {}) {
  try {
    const result = await execFileAsync(command, args, {
      cwd: process.cwd(),
      windowsHide: true,
      maxBuffer: 1024 * 1024 * 10,
    });
    return { exitCode: 0, stdout: result.stdout, stderr: result.stderr };
  } catch (error) {
    const result = {
      exitCode: error.code ?? 1,
      stdout: error.stdout ?? '',
      stderr: error.stderr ?? error.message,
    };
    if (reject) {
      throw new Error(`${command} ${args.join(' ')} failed: ${result.stderr || result.stdout}`);
    }
    return result;
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
