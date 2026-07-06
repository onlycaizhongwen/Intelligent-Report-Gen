import { beforeEach, describe, expect, it, vi } from 'vitest';

const interceptors = {
  request: vi.fn(),
  response: vi.fn()
};

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: interceptors.request },
        response: { use: interceptors.response }
      }
    }))
  }
}));

describe('api client runtime behavior', () => {
  const storage = new Map<string, string>();

  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    storage.clear();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
      clear: () => storage.clear()
    });
  });

  it('adds Bearer token from localStorage for real backend requests', async () => {
    localStorage.setItem('accessToken', 'test-token');

    await import('./client');
    const requestInterceptor = interceptors.request.mock.calls[0][0];
    const config = requestInterceptor({ headers: {} });

    expect(config.headers.Authorization).toBe('Bearer test-token');
  });

  it('keeps readable Chinese error messages instead of mojibake text', async () => {
    await import('./client');
    const errorInterceptor = interceptors.response.mock.calls[0][1];

    await expect(errorInterceptor({ code: 'ECONNABORTED' })).rejects.toThrow('请求超时，请稍后重试');
    await expect(errorInterceptor({ response: undefined })).rejects.toThrow('网络错误，请检查连接');
  });
});
