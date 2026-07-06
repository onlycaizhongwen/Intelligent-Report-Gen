import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('Real backend approval delegate rules E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-RULE-001: browser manages approval delegate rules on the real backend', async ({ page }) => {
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

    const seed = Date.now();
    const createdAssigneeRole = `delegate_smoke_manager_${seed}`;
    const createdDelegateRole = `delegate_smoke_delegate_${seed}`;
    const updatedAssigneeRole = `delegate_smoke_director_${seed}`;
    const updatedDelegateRole = `delegate_smoke_director_delegate_${seed}`;
    const updatedWindowText = 'Window 2026-06-27T08:00Z..2099-12-31T19:00Z';

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/rules/delegate-rules');
    await expect(page.getByRole('heading', { name: 'Approval Delegate Rules' })).toBeVisible();

    await page.getByLabel('Delegate assignee role').fill(createdAssigneeRole);
    await page.getByLabel('Delegate role').fill(createdDelegateRole);
    await page.getByLabel('Delegate active from').fill('2026-06-26T08:00:00Z');
    await page.getByLabel('Delegate active to').fill('2099-12-31T18:00:00Z');
    await page.getByLabel('Delegate active weekdays').fill('MONDAY,WEDNESDAY');
    await page.getByLabel('Delegate reason').fill('real backend delegate smoke create');
    await page.getByRole('button', { name: 'Create delegate rule' }).click();

    await expect(page.getByText(`Delegate rule created: ${createdAssigneeRole} -> ${createdDelegateRole}`)).toBeVisible();
    const createdRule = page.locator('.delegate-rule-card').filter({
      hasText: `${createdAssigneeRole} -> ${createdDelegateRole}`,
    });
    await expect(createdRule).toBeVisible();
    await expect(createdRule.getByText('Status enabled')).toBeVisible();

    await createdRule.getByRole('button', { name: 'Edit' }).click();
    await createdRule.getByLabel('Edit assignee role').fill(updatedAssigneeRole);
    await createdRule.getByLabel('Edit delegate role').fill(updatedDelegateRole);
    await createdRule.getByLabel('Edit active from').fill('2026-06-27T08:00:00Z');
    await createdRule.getByLabel('Edit active to').fill('2099-12-31T19:00:00Z');
    await createdRule.getByLabel('Edit active weekdays').fill('TUESDAY,THURSDAY');
    await createdRule.getByLabel('Edit reason').fill('real backend delegate smoke edit');
    await createdRule.getByRole('button', { name: 'Save edit' }).click();

    await expect(page.getByText(`Delegate rule updated: ${updatedAssigneeRole} -> ${updatedDelegateRole}`)).toBeVisible();
    const updatedRule = page.locator('.delegate-rule-card').filter({
      hasText: `${updatedAssigneeRole} -> ${updatedDelegateRole}`,
    });
    await expect(updatedRule).toBeVisible();
    await expect(updatedRule.getByText(updatedWindowText)).toBeVisible();
    await expect(updatedRule.getByText('Weekdays TUESDAY, THURSDAY')).toBeVisible();
    await expect(updatedRule.getByText('Reason real backend delegate smoke edit')).toBeVisible();

    await updatedRule.getByLabel('Disable reason').fill('real backend delegate smoke disable');
    await updatedRule.getByRole('button', { name: 'Disable' }).click();
    await expect(page.getByText(`Delegate rule disabled: ${updatedAssigneeRole} -> ${updatedDelegateRole}`)).toBeVisible();
    await expect(updatedRule.getByText('Status disabled')).toBeVisible();

    await updatedRule.getByLabel('Enable reason').fill('real backend delegate smoke enable');
    await updatedRule.getByRole('button', { name: 'Enable' }).click();
    await expect(page.getByText(`Delegate rule enabled: ${updatedAssigneeRole} -> ${updatedDelegateRole}`)).toBeVisible();
    await expect(updatedRule.getByText('Status enabled')).toBeVisible();

    const listResponse = await api.get(apiUrl('rules/approval-delegate-rules'), {
      params: {
        page: 1,
        pageSize: 20,
        assigneeRole: updatedAssigneeRole,
      },
    });
    expect(
      listResponse.ok(),
      `list delegate rules failed: ${listResponse.status()} ${await listResponse.text()}`,
    ).toBeTruthy();
    const listed = (await listResponse.json()).data as {
      items: Array<{
        assigneeRole: string;
        delegateRole: string;
        activeFrom?: string;
        activeTo?: string;
        status: string;
        reason?: string;
        activeWeekdays?: string[];
      }>;
    };
    expect(listed.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          assigneeRole: updatedAssigneeRole,
          delegateRole: updatedDelegateRole,
          activeFrom: '2026-06-27T08:00Z',
          activeTo: '2099-12-31T19:00Z',
          activeWeekdays: ['TUESDAY', 'THURSDAY'],
          status: 'enabled',
          reason: 'real backend delegate smoke enable',
        }),
      ]),
    );
  });
});

function apiUrl(path: string) {
  return `${apiBaseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
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
