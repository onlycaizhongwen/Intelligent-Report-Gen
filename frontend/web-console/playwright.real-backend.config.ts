import { defineConfig, devices } from '@playwright/test';

const port = Number(process.env.REAL_BACKEND_FRONTEND_PORT ?? 5174);
const backendOrigin = process.env.REAL_BACKEND_ORIGIN ?? 'http://127.0.0.1:18082';

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /.*real-backend\.spec\.ts/,
  timeout: 45_000,
  expect: { timeout: 8_000 },
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: 'on-first-retry'
  },
  webServer: {
    command: `npx vite --host 127.0.0.1 --port ${port}`,
    url: `http://127.0.0.1:${port}`,
    reuseExistingServer: false,
    timeout: 60_000,
    env: {
      VITE_API_BASE_URL: '/api/v1',
      VITE_API_PROXY_TARGET: backendOrigin
    }
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
