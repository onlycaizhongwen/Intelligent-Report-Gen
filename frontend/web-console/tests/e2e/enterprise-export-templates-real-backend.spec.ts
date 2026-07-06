import { expect, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('Real backend enterprise export templates E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-REPORT-004: browser manages enterprise export templates through the real backend', async ({ page }) => {
    const token = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['report:template:manage', 'report:export', 'report:read'],
      status: 'enabled'
    });
    const suffix = Date.now();
    const templateId = `browser-template-${suffix}`;

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/reports/enterprise-export-templates');
    await expect(page.getByRole('heading', { name: 'Enterprise Export Templates' })).toBeVisible();

    await page.getByLabel('Template ID').fill(templateId);
    await page.getByLabel('Template name').fill(`Browser export template ${suffix}`);
    await page.getByLabel('Company name').fill('Browser Finance');
    await page.getByLabel('Logo object key').fill(`logos/browser-${suffix}.svg`);
    await page.getByLabel('Header').fill('Browser Finance Board Pack');
    await page.getByLabel('Footer').fill('Browser confidential');
    await page.getByLabel('Font family').fill('Arial');
    await page.getByLabel('Primary color').fill('#155E75');
    await page.getByLabel('Cover title').fill('Browser Board Pack');
    await page.getByLabel('TOC title').fill('Browser Contents');
    await page.getByLabel('Section title prefix').fill('Browser Section');
    await page.getByRole('button', { name: 'Create enterprise export template' }).click();

    await expect(page.getByText(`Enterprise export template created: Browser export template ${suffix}`)).toBeVisible();
    await expect(page.getByText(`Template ${templateId}`)).toBeVisible();
    const createdCard = page.locator('.template-card').filter({ hasText: `Template ${templateId}` });
    await expect(createdCard.getByText('Version v1')).toBeVisible();
    await expect(createdCard.getByText('Browser Finance Board Pack')).toBeVisible();

    await page.getByRole('button', { name: `Edit Browser export template ${suffix}` }).click();
    await page.getByLabel('Template name').fill(`Browser export template ${suffix} v2`);
    await page.getByLabel('Footer').fill('Browser board confidential');
    await page.getByLabel('Cover title').fill('Browser Strategy Pack 2026');
    await page.getByRole('button', { name: 'Save enterprise export template changes' }).click();

    await expect(page.getByText(`Enterprise export template updated to version v2: Browser export template ${suffix} v2`)).toBeVisible();
    const updatedCard = page.locator('.template-card').filter({ hasText: `Template ${templateId}` });
    await expect(updatedCard.getByText('Version v2', { exact: true })).toBeVisible();
    await expect(updatedCard.getByText('Browser board confidential')).toBeVisible();

    await page.getByRole('button', { name: `Disable Browser export template ${suffix} v2` }).click();
    await expect(page.getByText(`Enterprise export template disabled: Browser export template ${suffix} v2`)).toBeVisible();
    await expect(updatedCard.getByText('Status disabled')).toBeVisible();

    await page.getByRole('button', { name: `Enable Browser export template ${suffix} v2` }).click();
    await expect(page.getByText(`Enterprise export template enabled: Browser export template ${suffix} v2`)).toBeVisible();
    await expect(updatedCard.getByText('Status active')).toBeVisible();

    await page.getByRole('button', { name: `View versions Browser export template ${suffix} v2` }).click();
    const versionPanel = page.getByLabel(`Version history for Browser export template ${suffix} v2`);
    await expect(versionPanel.getByText('Version snapshot v2')).toBeVisible();
    await expect(versionPanel.getByText('Version snapshot v1')).toBeVisible();
    await expect(versionPanel.getByText('Browser board confidential')).toBeVisible();
    await expect(versionPanel.getByText('Browser confidential')).toBeVisible();
  });
});

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
