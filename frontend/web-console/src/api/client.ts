import axios, { type AxiosError } from 'axios';

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

apiClient.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data;
    if (code !== 200) {
      if (code === 401) window.location.href = '/login';
      return Promise.reject(new Error(message || '请求失败'));
    }
    return data;
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) window.location.href = '/login';
    if (error.code === 'ECONNABORTED') return Promise.reject(new Error('请求超时，请稍后重试'));
    const responseData = error.response?.data as { message?: string } | undefined;
    if (responseData?.message) return Promise.reject(new Error(responseData.message));
    return Promise.reject(new Error('网络错误，请检查连接'));
  }
);
