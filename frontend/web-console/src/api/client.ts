import axios, { type AxiosError } from 'axios';

interface ApiErrorOptions {
  code?: number;
  status?: number;
}

export class ApiError extends Error {
  code?: number;
  status?: number;

  constructor(message: string, options: ApiErrorOptions = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = options.code;
    this.status = options.status;
  }
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30_000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
});

apiClient.interceptors.request.use((config) => {
  const token = globalThis.localStorage?.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const isLocalPreviewMode = () => globalThis.localStorage?.getItem('authMode') === 'local-preview';

const shouldRedirectToLogin = () => !isLocalPreviewMode();

export const isLocalPreviewUnauthorizedError = (error: unknown) => (
  error instanceof ApiError &&
  isLocalPreviewMode() &&
  (error.code === 401 || error.status === 401)
);

apiClient.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data;
    if (code !== 200) {
      if (code === 401 && shouldRedirectToLogin()) window.location.href = '/login';
      return Promise.reject(new ApiError(message || '请求失败', { code, status: response.status }));
    }
    return data;
  },
  (error: AxiosError) => {
    if (error.response?.status === 401 && shouldRedirectToLogin()) window.location.href = '/login';
    if (error.code === 'ECONNABORTED') return Promise.reject(new Error('请求超时，请稍后重试'));
    const responseData = error.response?.data as { code?: number; message?: string } | undefined;
    if (responseData?.message) {
      return Promise.reject(new ApiError(responseData.message, {
        code: responseData.code,
        status: error.response?.status
      }));
    }
    if (error.response?.status) {
      return Promise.reject(new ApiError('请求失败', { status: error.response.status }));
    }
    return Promise.reject(new Error('网络错误，请检查连接'));
  }
);
