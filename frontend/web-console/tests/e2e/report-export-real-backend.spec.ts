import { expect, request, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const backendOrigin = process.env.REAL_BACKEND_ORIGIN ?? 'http://127.0.0.1:18082';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端报告导出 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-REPORT-004：浏览器可连接真实后端发起企业 PDF 导出并收到成功反馈', async ({ page }) => {
    const token = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read', 'report:export', 'report:share'],
      status: 'enabled'
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });
    const apiRoot = await request.newContext({
      baseURL: backendOrigin,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });
    const anonymousApi = await request.newContext();
    const suffix = Date.now();

    const taskResponse = await api.post(apiUrl('reports/generation-tasks'), {
      data: { topic: `真实后端导出验收 ${suffix}`, payload: { acceptance: 'REQ-REPORT-004' } }
    });
    expect(taskResponse.ok(), `create task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number; reportId: number };

    await completeReportWithSections(api, task.taskId, [
      {
        sectionId: 'summary',
        heading: '经营概览',
        content: '收入增长 12%，但应收账款风险上升。',
        citations: ['doc-real-export']
      }
    ]);

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto(`/reports/${task.reportId}`);
    await expect(page.getByRole('heading', { name: '报告详情' })).toBeVisible();
    await expect(page.getByText(`真实后端导出验收 ${suffix}`)).toBeVisible();

    await page.getByRole('button', { name: 'PDF' }).click();
    await page.getByRole('button', { name: '导出文件' }).click();

    await expect(page.getByText(/导出已完成：.*\.pdf/)).toBeVisible();
  });

  test('REQ-REPORT-004：PPT 导出会生成总览页和章节页多幻灯片产物', async ({ page }) => {
    const token = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read', 'report:export', 'report:share'],
      status: 'enabled'
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });
    const apiRoot = await request.newContext({
      baseURL: backendOrigin,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });
    const anonymousApi = await request.newContext();
    const suffix = Date.now();

    const taskResponse = await api.post(apiUrl('reports/generation-tasks'), {
      data: { topic: `真实后端 PPT 导出验收 ${suffix}`, payload: { acceptance: 'REQ-REPORT-004-PPT' } }
    });
    expect(taskResponse.ok(), `create task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number; reportId: number };

    await completeReportWithSections(api, task.taskId, [
      {
        sectionId: 'summary',
        heading: 'Executive Summary',
        content: 'Revenue grew by 12%, and margin stayed stable.',
        citations: ['doc-real-export-1']
      },
      {
        sectionId: 'risk',
        heading: 'Risk Review',
        content: 'Receivables risk increased and requires weekly tracking.',
        citations: ['doc-real-export-2']
      }
    ]);

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto(`/reports/${task.reportId}`);
    await expect(page.getByRole('heading', { name: '报告详情' })).toBeVisible();
    await expect(page.getByText(`真实后端 PPT 导出验收 ${suffix}`)).toBeVisible();

    const exportResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/reports/${task.reportId}/exports`) &&
      response.status() === 200
    );

    await page.getByRole('button', { name: 'PPT' }).click();
    await page.getByLabel('封面标题').fill('Board Strategy Pack');
    await page.getByLabel('目录标题').fill('Report Outline');
    await page.getByLabel('章节标题前缀').fill('Section');
    await page.getByLabel('标题字号').fill('30');
    await page.getByLabel('正文字号').fill('22');
    await page.getByLabel('页眉字号').fill('16');
    await page.getByLabel('页脚字号').fill('12');
    await page.getByRole('button', { name: '导出文件' }).click();

    await expect(page.getByText(/导出已完成：.*\.pptx/)).toBeVisible();

    const exportEnvelope = await exportResponsePromise.then((response) => response.json()) as {
      data: { exportFileId: number; fileName: string };
    };
    expect(exportEnvelope.data.fileName).toMatch(/\.pptx$/);

    const statusResponse = await api.get(apiUrl(`reports/${task.reportId}/exports/${exportEnvelope.data.exportFileId}`));
    expect(statusResponse.ok(), `export status failed: ${statusResponse.status()} ${await statusResponse.text()}`).toBeTruthy();
    const statusEnvelope = (await statusResponse.json()).data as { downloadUrl: string };

    const presignedResponse = await apiRoot.get(statusEnvelope.downloadUrl);
    expect(presignedResponse.ok(), `download-url failed: ${presignedResponse.status()} ${await presignedResponse.text()}`).toBeTruthy();
    const presignedEnvelope = (await presignedResponse.json()).data as { downloadUrl: string };

    const artifactResponse = await anonymousApi.get(presignedEnvelope.downloadUrl);
    expect(artifactResponse.ok(), `artifact download failed: ${artifactResponse.status()} ${await artifactResponse.text()}`).toBeTruthy();
    const artifactBytes = Buffer.from(await artifactResponse.body());
    const artifactText = artifactBytes.toString('latin1');
    const pptEntries = unzipOfficeEntries(artifactBytes, '.pptx');

    expect(artifactText).toContain('ppt/slides/slide1.xml');
    expect(artifactText).toContain('ppt/slides/slide2.xml');
    expect(artifactText).toContain('ppt/slides/slide3.xml');
    expect(artifactText).toContain('ppt/slides/slide4.xml');
    expect(artifactText).toContain('ppt/slides/_rels/slide2.xml.rels');
    expect(artifactText).toContain('ppt/slides/_rels/slide3.xml.rels');
    expect(pptEntries['ppt/slides/slide1.xml']).toContain('Board Strategy Pack');
    expect(pptEntries['ppt/slides/slide2.xml']).toContain('Report Outline');
    expect(pptEntries['ppt/slides/slide2.xml']).toContain('1. Executive Summary .... 3');
    expect(pptEntries['ppt/slides/slide2.xml']).toContain('2. Risk Review .... 4');
    expect(pptEntries['ppt/slides/slide3.xml']).toContain('Section 1. Executive Summary');
    expect(pptEntries['ppt/slides/slide3.xml']).toContain('Revenue grew by 12%, and margin stayed stable.');
    expect(pptEntries['ppt/slides/slide4.xml']).toContain('Receivables risk increased and requires weekly tracking.');
    expect(pptEntries['ppt/slides/slide1.xml']).toContain('type="title"');
    expect(pptEntries['ppt/slides/slide1.xml']).toContain('type="body"');
    expect(pptEntries['ppt/slides/slide1.xml']).toContain('type="ftr"');
    expect(pptEntries['ppt/slides/slide1.xml']).toContain('sz="3000"');
    expect(pptEntries['ppt/slides/slide2.xml']).toContain('sz="2200"');
    expect(pptEntries['ppt/slideMasters/slideMaster1.xml']).toContain('type="title"');
    expect(pptEntries['ppt/slideMasters/slideMaster1.xml']).toContain('type="body"');
    expect(pptEntries['ppt/slideMasters/slideMaster1.xml']).toContain('type="ftr"');
    expect(pptEntries['ppt/slideLayouts/slideLayout1.xml']).toContain('type="title"');
    expect(pptEntries['ppt/slideLayouts/slideLayout1.xml']).toContain('type="body"');
    expect(pptEntries['ppt/slideLayouts/slideLayout1.xml']).toContain('type="ftr"');
    expect(pptEntries['ppt/theme/theme1.xml']).toContain('Office Theme');
  });
  test('REQ-REPORT-004: managed enterprise template selection exports through the real backend', async ({ page }) => {
    const token = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read', 'report:export', 'report:share', 'report:template:manage'],
      status: 'enabled'
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });
    const apiRoot = await request.newContext({
      baseURL: backendOrigin,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });
    const anonymousApi = await request.newContext();
    const suffix = Date.now();
    const templateId = `managed-export-${suffix}`;

    const templateResponse = await api.post(apiUrl('enterprise-export-templates'), {
      data: {
        templateId,
        name: `Managed export template ${suffix}`,
        brand: {
          companyName: 'Managed Finance',
          logoObjectKey: `logos/managed-${suffix}.svg`,
          header: 'Managed Real Board Pack',
          footer: 'Managed confidential',
          fontFamily: 'Arial',
          primaryColor: '#155E75',
          layout: {
            coverTitle: 'Managed Board Pack',
            tocTitle: 'Managed Contents',
            bodyTitlePrefix: 'Managed Section',
            titleFontSize: 28,
            bodyFontSize: 18,
            headerFontSize: 12,
            footerFontSize: 10
          }
        }
      }
    });
    expect(templateResponse.ok(), `create managed template failed: ${templateResponse.status()} ${await templateResponse.text()}`).toBeTruthy();

    const taskResponse = await api.post(apiUrl('reports/generation-tasks'), {
      data: { topic: `Managed template export acceptance ${suffix}`, payload: { acceptance: 'REQ-REPORT-004-MANAGED' } }
    });
    expect(taskResponse.ok(), `create task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number; reportId: number };

    await completeReportWithSections(api, task.taskId, [
      {
        sectionId: 'summary',
        heading: 'Managed Executive Summary',
        content: 'Managed template export uses the governed brand snapshot.',
        citations: ['doc-managed-export']
      }
    ]);

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto(`/reports/${task.reportId}`);
    await page.locator('select[aria-label="已治理企业模板"]').selectOption(templateId);
    await expect(page.getByText('Managed Real Board Pack')).toBeVisible();
    await expect(page.getByText('Version v1')).toBeVisible();

    const exportResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/reports/${task.reportId}/exports`) &&
      response.status() === 200
    );

    await page.getByRole('button', { name: 'PDF' }).click();
    await page.getByRole('button', { name: '导出文件' }).click();

    await expect(page.getByText(/导出已完成：.*\.pdf/)).toBeVisible();

    const exportResponse = await exportResponsePromise;
    const exportRequest = exportResponse.request().postDataJSON() as {
      format: string;
      templateId: string;
      brand?: unknown;
    };
    expect(exportRequest).toMatchObject({ format: 'pdf', templateId });
    expect(exportRequest).not.toHaveProperty('brand');

    const exportEnvelope = await exportResponse.json() as {
      data: { exportFileId: number; fileName: string; brandSnapshot: { templateId: string; templateVersion: string } };
    };
    expect(exportEnvelope.data.fileName).toMatch(/\.pdf$/);
    expect(exportEnvelope.data.brandSnapshot.templateId).toBe(templateId);
    expect(exportEnvelope.data.brandSnapshot.templateVersion).toBe('v1');

    const statusResponse = await api.get(apiUrl(`reports/${task.reportId}/exports/${exportEnvelope.data.exportFileId}`));
    expect(statusResponse.ok(), `export status failed: ${statusResponse.status()} ${await statusResponse.text()}`).toBeTruthy();
    const statusEnvelope = (await statusResponse.json()).data as { downloadUrl: string };

    const presignedResponse = await apiRoot.get(statusEnvelope.downloadUrl);
    expect(presignedResponse.ok(), `download-url failed: ${presignedResponse.status()} ${await presignedResponse.text()}`).toBeTruthy();
    const presignedEnvelope = (await presignedResponse.json()).data as { downloadUrl: string };

    const artifactResponse = await anonymousApi.get(presignedEnvelope.downloadUrl);
    expect(artifactResponse.ok(), `artifact download failed: ${artifactResponse.status()} ${await artifactResponse.text()}`).toBeTruthy();
    expect((await artifactResponse.body()).byteLength).toBeGreaterThan(100);
  });
});

function unzipOfficeEntries(bytes: Buffer, extension: '.pptx' | '.docx') {
  const tempDir = mkdtempSync(join(tmpdir(), `ir-export-${extension.slice(1)}-`));
  const archivePath = join(tempDir, 'artifact.zip');
  const extractDir = join(tempDir, 'unzipped');
  try {
    writeFileSync(archivePath, bytes);
    execFileSync(
      'powershell',
      [
        '-NoProfile',
        '-Command',
        `Expand-Archive -LiteralPath '${archivePath.replace(/'/g, "''")}' -DestinationPath '${extractDir.replace(/'/g, "''")}' -Force`
      ],
      { stdio: 'pipe' }
    );
    return Object.fromEntries(
      [
        'ppt/slides/slide1.xml',
        'ppt/slides/slide2.xml',
        'ppt/slides/slide3.xml',
        'ppt/slides/slide4.xml',
        'ppt/slideMasters/slideMaster1.xml',
        'ppt/slideLayouts/slideLayout1.xml',
        'ppt/theme/theme1.xml'
      ].map((relativePath) => [relativePath, readFileSync(join(extractDir, ...relativePath.split('/')), 'utf8')])
    ) as Record<string, string>;
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
}

async function completeReportWithSections(
  api: Awaited<ReturnType<typeof request.newContext>>,
  taskId: number,
  sections: Array<{
    sectionId: string;
    heading: string;
    content: string;
    citations: string[];
  }>
) {
  const response = await api.post(apiUrl(`reports/generation-tasks/${taskId}/completion`), {
    data: {
      sections,
      references: sections.map((section, index) => ({
        sourceId: section.citations[0],
        score: 0.94 - index * 0.01,
        title: `${section.heading} reference`
      })),
      modelInvocation: {
        provider: 'local-fallback',
        modelName: 'real-backend-e2e',
        status: 'succeeded',
        traceId: `trace-real-export-${Date.now()}`,
        inputTokens: 10,
        outputTokens: 20,
        totalTokens: 30,
        latencyMs: 5,
        responseSummary: sections.map((section) => section.content).join(' ')
      }
    }
  });
  expect(response.ok(), `complete report failed: ${response.status()} ${await response.text()}`).toBeTruthy();
}

function apiUrl(path: string) {
  return `${apiBaseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
}

function toAbsoluteBackendUrl(path: string) {
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  return `${backendOrigin.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
}

async function generateJwt(payload: Record<string, unknown>) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const body = { iat: now, exp: now + 7200, ...payload };
  const unsigned = `${base64UrlJson(header)}.${base64UrlJson(body)}`;
  const signature = await hmacSha256(unsigned, jwtSecret);
  return `${unsigned}.${signature}`;
}

function base64UrlJson(value: unknown) {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

async function hmacSha256(value: string, secret: string) {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(value));
  return base64Url(new Uint8Array(signature));
}

function base64Url(bytes: Uint8Array) {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
