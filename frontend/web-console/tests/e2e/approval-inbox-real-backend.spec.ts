import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('Real backend approval inbox E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-RULE-001: browser can view and approve real pending approval records', async ({ page }) => {
    const token = await generateJwt({
      sub: '9203',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    const approvalTitle = `Finance approval required ${Date.now()}`;
    const ruleId = await createApprovedRunnableRule(
      api,
      `Approval inbox smoke ${Date.now()}`,
      createApprovalDefinition(approvalTitle, 'finance_manager'),
    );

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/rules/approvals');
    await expect(page.getByRole('heading', { name: '审批待办' })).toBeVisible();
    const approvalCard = page.locator('.approval-card').filter({
      has: page.getByText(approvalTitle),
    });
    await expect(approvalCard).toBeVisible();
    await expect(approvalCard.getByText(`规则 #${ruleId}`)).toBeVisible();

    await approvalCard.getByRole('button', { name: '通过' }).click();
    await expect(approvalCard).toBeHidden();

    await page.getByRole('button', { name: '已通过' }).click();
    const approvedCard = page.locator('.approval-card').filter({
      has: page.getByText(approvalTitle),
    });
    await expect(approvedCard).toBeVisible();
    await expect(approvedCard.getByText(`规则 #${ruleId}`)).toBeVisible();
    await expect(approvedCard.getByText('状态 已通过')).toBeVisible();
    await expect(approvedCard.getByText('审批备注 在审批待办中通过')).toBeVisible();
  });

  test('REQ-RULE-001: browser filters approval records by rule and role across status tabs', async ({ page }) => {
    const token = await generateJwt({
      sub: '9204',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    const uniqueSeed = Date.now();
    const targetApprovalTitle = `Finance approval required ${uniqueSeed}`;
    const targetRuleId = await createApprovedRunnableRule(
      api,
      `Approval inbox filter target ${uniqueSeed}`,
      createApprovalDefinition(targetApprovalTitle, 'finance_manager'),
    );
    const distractorRuleId = await createApprovedRunnableRule(
      api,
      `Approval inbox filter distractor ${uniqueSeed}`,
      createApprovalDefinition(`Legal approval required ${uniqueSeed}`, 'legal_manager'),
    );

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/rules/approvals');
    await expect(page.getByRole('heading', { name: '审批待办' })).toBeVisible();
    await expect(page.getByText(targetApprovalTitle)).toBeVisible();
    await expect(page.getByText(`规则 #${targetRuleId}`)).toBeVisible();
    await expect(page.getByText(`规则 #${distractorRuleId}`)).toBeVisible();

    await page.getByLabel('规则编号筛选').fill(String(targetRuleId));
    await page.getByLabel('审批角色筛选').fill('finance_manager');
    await page.getByLabel('审批标题筛选').fill('Finance approval');
    await page.getByRole('button', { name: '应用筛选' }).click();

    const filteredPendingCard = page.locator('.approval-card').filter({
      has: page.getByText(targetApprovalTitle),
    });
    await expect(filteredPendingCard).toBeVisible();
    await expect(filteredPendingCard.getByText(`规则 #${targetRuleId}`)).toBeVisible();
    await expect(page.getByText(`规则 #${distractorRuleId}`)).toHaveCount(0);
    await expect(page.getByText('审批角色 legal_manager')).toHaveCount(0);

    await filteredPendingCard.getByRole('button', { name: '通过' }).click();
    await expect(filteredPendingCard).toBeHidden();
    await expect(page.getByText('暂无待审批记录')).toBeVisible();

    await page.getByRole('button', { name: '已通过' }).click();
    const filteredApprovedCard = page.locator('.approval-card').filter({
      has: page.getByText(targetApprovalTitle),
    });
    await expect(filteredApprovedCard).toBeVisible();
    await expect(filteredApprovedCard.getByText(`规则 #${targetRuleId}`)).toBeVisible();
    await expect(filteredApprovedCard.getByText('审批角色 finance_manager')).toBeVisible();
    await expect(filteredApprovedCard.getByText('状态 已通过')).toBeVisible();
    await expect(page.getByText(`规则 #${distractorRuleId}`)).toHaveCount(0);
    await expect(page.getByText('审批角色 legal_manager')).toHaveCount(0);
  });

  test('REQ-RULE-001: browser applies extended approval operator filters on real backend', async ({ page }) => {
    const token = await generateJwt({
      sub: '9205',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    const uniqueSeed = Date.now();
    const targetApprovalTitle = `Extended approval target ${uniqueSeed}`;
    const distractorApprovalTitle = `Extended approval distractor ${uniqueSeed}`;
    const targetRuleId = await createApprovedRunnableRule(
      api,
      `Approval inbox extended target ${uniqueSeed}`,
      createApprovalDefinition(targetApprovalTitle, 'finance_manager'),
    );
    const distractorRuleId = await createApprovedRunnableRule(
      api,
      `Approval inbox extended distractor ${uniqueSeed}`,
      createApprovalDefinition(distractorApprovalTitle, 'legal_manager'),
    );

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/rules/approvals');
    await expect(page.getByRole('heading', { name: '审批待办' })).toBeVisible();

    await page.getByLabel('发起人编号筛选').fill('9205');
    await page.getByLabel('创建开始时间筛选').fill('2026-06-25T00:00');
    await page.getByLabel('创建结束时间筛选').fill('2099-12-31T23:59');
    await page.getByRole('button', { name: '应用筛选' }).click();

    const filteredPendingCard = page.locator('.approval-card').filter({
      has: page.getByText(targetApprovalTitle),
    });
    await expect(filteredPendingCard).toBeVisible();
    await expect(filteredPendingCard.getByText(`规则 #${targetRuleId}`)).toBeVisible();
    await expect(page.getByText(`规则 #${distractorRuleId}`)).toBeVisible();

    await filteredPendingCard.getByRole('button', { name: '通过' }).click();
    await expect(filteredPendingCard).toBeHidden();

    await page.getByRole('button', { name: '已通过' }).click();
    await page.getByLabel('审批人编号筛选').fill('9205');
    await page.getByLabel('审批标题筛选').fill('Extended approval target');
    await page.getByRole('button', { name: '应用筛选' }).click();

    const filteredApprovedCard = page.locator('.approval-card').filter({
      has: page.getByText(targetApprovalTitle),
    });
    await expect(filteredApprovedCard).toBeVisible();
    await expect(filteredApprovedCard.getByText(`规则 #${targetRuleId}`)).toBeVisible();
    await expect(filteredApprovedCard.getByText('状态 已通过')).toBeVisible();
    await expect(page.getByText(distractorApprovalTitle)).toHaveCount(0);
  });

  test('REQ-RULE-001: browser batch approves selected pending approvals on real backend', async ({ page }) => {
    const token = await generateJwt({
      sub: '9206',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    const uniqueSeed = Date.now();
    const firstApprovalTitle = `Batch approval target A ${uniqueSeed}`;
    const secondApprovalTitle = `Batch approval target B ${uniqueSeed}`;
    const firstRuleId = await createApprovedRunnableRule(
      api,
      `Approval inbox batch A ${uniqueSeed}`,
      createApprovalDefinition(firstApprovalTitle, 'finance_manager'),
    );
    const secondRuleId = await createApprovedRunnableRule(
      api,
      `Approval inbox batch B ${uniqueSeed}`,
      createApprovalDefinition(secondApprovalTitle, 'finance_manager'),
    );

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/rules/approvals');
    await expect(page.getByRole('heading', { name: '审批待办' })).toBeVisible();

    const firstPendingCard = page.locator('.approval-card').filter({
      has: page.getByText(firstApprovalTitle),
    });
    const secondPendingCard = page.locator('.approval-card').filter({
      has: page.getByText(secondApprovalTitle),
    });
    await expect(firstPendingCard).toBeVisible();
    await expect(secondPendingCard).toBeVisible();
    await expect(firstPendingCard.getByText(`规则 #${firstRuleId}`)).toBeVisible();
    await expect(secondPendingCard.getByText(`规则 #${secondRuleId}`)).toBeVisible();

    const firstCheckbox = firstPendingCard.getByLabel(/选择审批记录 \d+/);
    const secondCheckbox = secondPendingCard.getByLabel(/选择审批记录 \d+/);
    await firstCheckbox.check();
    await secondCheckbox.check();
    await page.getByRole('button', { name: '批量通过' }).click();

    await expect(page.getByText('批量审批完成：成功 2 条，失败 0 条。')).toBeVisible();
    await expect(firstPendingCard).toBeHidden();
    await expect(secondPendingCard).toBeHidden();

    await page.getByRole('button', { name: '已通过' }).click();
    const firstApprovedCard = page.locator('.approval-card').filter({
      has: page.getByText(firstApprovalTitle),
    });
    const secondApprovedCard = page.locator('.approval-card').filter({
      has: page.getByText(secondApprovalTitle),
    });
    await expect(firstApprovedCard).toBeVisible();
    await expect(secondApprovedCard).toBeVisible();
    await expect(firstApprovedCard.getByText('状态 已通过')).toBeVisible();
    await expect(secondApprovedCard.getByText('状态 已通过')).toBeVisible();
    await expect(firstApprovedCard.getByText('审批备注 批量通过')).toBeVisible();
    await expect(secondApprovedCard.getByText('审批备注 批量通过')).toBeVisible();
  });

  test('REQ-RULE-001: approval supplement attachment upload returns MinIO evidence URL on real backend', async ({ page }) => {
    const token = await generateJwt({
      sub: '9207',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    const uniqueSeed = Date.now();
    const approvalTitle = `Supplement attachment upload ${uniqueSeed}`;
    const ruleId = await createApprovedRunnableRule(
      api,
      `Approval supplement upload ${uniqueSeed}`,
      createApprovalDefinition(approvalTitle, 'finance_manager'),
    );

    let uploadEvidenceUrl = '';
    const uploadResponsePromise = page.waitForResponse(async (response) => {
      if (!response.url().includes(`/api/v1/rules/${ruleId}/approval-records/`)
        || !response.url().includes('/supplement-attachments')
        || response.request().method() !== 'POST') {
        return false;
      }
      const body = await response.json() as { data?: { evidenceUrl?: string } };
      uploadEvidenceUrl = body.data?.evidenceUrl ?? '';
      return response.ok() && uploadEvidenceUrl.startsWith('minio://');
    });

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/rules/approvals');
    await expect(page.getByRole('heading', { name: '审批待办' })).toBeVisible();
    const pendingCard = page.locator('.approval-card').filter({
      has: page.getByText(approvalTitle),
    });
    await expect(pendingCard).toBeVisible();
    await expect(pendingCard.getByText(`规则 #${ruleId}`)).toBeVisible();

    await pendingCard.getByRole('button', { name: '驳回' }).click();
    await expect(page.getByText('审批已驳回，请补充材料后重新提交。')).toBeVisible();

    await page.getByRole('button', { name: '已驳回' }).click();
    const rejectedCard = page.locator('.approval-card').filter({
      has: page.getByText(approvalTitle),
    });
    await expect(rejectedCard).toBeVisible();
    await rejectedCard.getByLabel('补充说明').fill('uploaded real invoice evidence');
    await rejectedCard.getByLabel('补充附件').setInputFiles({
      name: 'invoice-evidence.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from(`invoice evidence ${uniqueSeed}`),
    });
    await uploadResponsePromise;
    expect(uploadEvidenceUrl).toContain(`/rule-${ruleId}/`);
    await expect(rejectedCard.getByText('invoice-evidence.txt')).toBeVisible();

    await rejectedCard.getByRole('button', { name: '提交补充' }).click();
    await expect(page.getByText('补充材料已提交，审批已回到待处理状态。')).toBeVisible();

    await page.getByRole('button', { name: '待审批' }).click();
    const resubmittedPendingCard = page.locator('.approval-card').filter({
      has: page.getByText(approvalTitle),
    });
    await expect(resubmittedPendingCard).toBeVisible();
    await expect(resubmittedPendingCard.getByText('状态 待审批')).toBeVisible();

    await page.getByRole('button', { name: '已重提' }).click();
    const resubmittedHistoryCard = page.locator('.approval-card').filter({
      has: page.getByText(approvalTitle),
    });
    await expect(resubmittedHistoryCard).toBeVisible();
    await expect(resubmittedHistoryCard.getByText('审批备注 uploaded real invoice evidence')).toBeVisible();
  });
});

function apiUrl(path: string) {
  return `${apiBaseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
}

function createApprovalDefinition(approvalTitle: string, assigneeRole: string) {
  return {
    nodes: [
      { id: 'start', type: 'start' },
      {
        id: 'approvalNode',
        type: 'approval',
        assigneeRole,
        approvalTitle,
      },
      { id: 'end', type: 'end' },
    ],
    edges: [
      { source: 'start', target: 'approvalNode' },
      { source: 'approvalNode', target: 'end' },
    ],
  };
}

async function createApprovedRunnableRule(
  api: Awaited<ReturnType<typeof request.newContext>>,
  ruleName: string,
  definition: Record<string, unknown>,
) {
  const createResponse = await api.post(apiUrl('rules'), {
    data: {
      name: ruleName,
      definition,
    },
  });
  expect(createResponse.ok(), `create rule failed: ${createResponse.status()} ${await createResponse.text()}`).toBeTruthy();
  const created = (await createResponse.json()).data as { ruleId: number };

  const saveResponse = await api.put(apiUrl(`rules/${created.ruleId}`), {
    data: { definition, status: 'draft' },
  });
  expect(saveResponse.ok(), `save rule failed: ${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();

  const reviewResponse = await api.post(apiUrl(`rules/${created.ruleId}/review-submissions`), {
    data: { comment: `ready for ${ruleName}` },
  });
  expect(
    reviewResponse.ok(),
    `submit rule for review failed: ${reviewResponse.status()} ${await reviewResponse.text()}`,
  ).toBeTruthy();

  const approveResponse = await api.post(apiUrl(`rules/${created.ruleId}/approvals`), {
    data: { comment: `approved for ${ruleName}` },
  });
  expect(
    approveResponse.ok(),
    `approve rule failed: ${approveResponse.status()} ${await approveResponse.text()}`,
  ).toBeTruthy();

  const executeResponse = await api.post(apiUrl(`rules/${created.ruleId}/runs`), {
    data: { sample: { riskScore: 95 } },
  });
  expect(
    executeResponse.ok(),
    `execute rule failed: ${executeResponse.status()} ${await executeResponse.text()}`,
  ).toBeTruthy();

  return created.ruleId;
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
    ['sign'],
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
