<!-- skill: S19 -->
<skill id="S19" name="api_client.gen">

# 技能：API 客户端生成

## Meta
- DependsOn: S4
- Category: frontend
- Status: stable

## 一句话描述
根据 API 契约，生成前端统一请求封装。

## 输入
- `docs/skill-chain/api_contract.md`：API 接口文档
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `api/`（请求封装代码目录）

## Prompt

你是一位资深前端工程师。
请根据 API 契约，生成统一的前端 API 客户端。

API 客户端必须追溯 OpenSpec：每个 client 方法必须标注对应 requirement / scenario。

前端 API 客户端选择规则：
- 必须以 S6 技术栈为准；S6 选择 Vue 3 时生成 Vue 3 + TypeScript + Axios API 客户端，S6 选择 React 时生成 React + TypeScript + Axios API 客户端。
- 认证优先依赖 HttpOnly Cookie；Axios 必须启用 `withCredentials`，不得从 LocalStorage 读取 Token 注入 Authorization。
- AI 流式请求必须提供 Vue composable 或 service 封装，页面和组件不得直接拼接 SSE 请求。

---

### Vue 3 + TypeScript 版（Axios，S6 选择 Vue 3 时使用）

```typescript
// src/api/client.ts
import axios, { type AxiosError } from 'axios';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.response.use(
  (res) => {
    const { code, message, data } = res.data;
    if (code !== 200) {
      if (code === 401) {
        window.location.href = '/login';
      }
      return Promise.reject(new Error(message || '请求失败'));
    }
    return data;
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      window.location.href = '/login';
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请稍后重试'));
    }
    return Promise.reject(error);
  }
);
```

```typescript
// src/api/reportApi.ts
import { apiClient } from './client';

export const reportApi = {
  createTask: (payload: { topic: string; focus?: string }) =>
    apiClient.post('/reports/generation-tasks', payload),

  confirmOutline: (taskId: string, outline: unknown) =>
    apiClient.post(`/reports/generation-tasks/${taskId}/outline:confirm`, { outline }),

  getTask: (taskId: string) =>
    apiClient.get(`/reports/generation-tasks/${taskId}`),
};
```

### React + TypeScript 版（Axios，仅 React 技术栈使用）

```typescript
// src/api/client.ts
import axios, {
    AxiosInstance,
    AxiosRequestConfig,
    AxiosResponse,
} from 'axios';
import { store } from '../store';
import { setError } from '../store/authSlice';

const apiClient: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 请求拦截器：Cookie 认证场景下不从前端读取 Token
apiClient.interceptors.request.use((config) => {
    config.withCredentials = true;
    return config;
});

// 响应拦截器：统一错误处理
apiClient.interceptors.response.use(
    (response: AxiosResponse) => {
        const { code, message, data } = response.data;
        if (code !== 200) {
            // Token 过期，跳转登录
            if (code === 401) {
                window.location.href = '/login';
                return Promise.reject(new Error('登录已过期'));
            }
            return Promise.reject(new Error(message || '请求失败'));
        }
        return data; // 直接返回 data 部分
    },
    (error) => {
        if (error.code === 'ECONNABORTED') {
            return Promise.reject(new Error('请求超时，请稍后重试'));
        }
        return Promise.reject(new Error('网络错误，请检查连接'));
    }
);

export default apiClient;
```

```typescript
// src/api/documentApi.ts
import apiClient from './client';
import type { Document, UploadResponse } from './types';

export const documentApi = {
    list: (params?: { page?: number; size?: number }) =>
        apiClient.get<Document[]>('/documents', { params }),

    upload: (formData: FormData) =>
        apiClient.post<UploadResponse>('/documents/upload', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: 120000, // 上传超时 2 分钟
        }),

    delete: (docId: number) =>
        apiClient.delete(`/documents/${docId}`),

    search: (query: string, topK: number = 5) =>
        apiClient.post<{
            chunks: Array<{ content: string; score: number; documentTitle: string }>;
            tokensUsed: number;
        }>('/documents/search', { query, topK }),
};
```

```typescript
// src/api/chatApi.ts
import apiClient from './client';
import type { Message } from '../store/chatSlice';

export const chatApi = {
    // 非流式对话
    send: (question: string, context?: string) =>
        apiClient.post<{ answer: string; references: any[] }>('/chat', {
            question,
            context,
        }),

    // 流式对话（SSE）
    stream: (question: string, onToken: (token: string) => void) => {
        const eventSource = new EventSource(
            `${import.meta.env.VITE_API_BASE_URL}/chat/stream?question=${encodeURIComponent(question)}`
        );

        return new Promise<{ references: any[] }>((resolve, reject) => {
            eventSource.onmessage = (event) => {
                const data = JSON.parse(event.data);
                if (data.type === 'token') {
                    onToken(data.content);
                } else if (data.type === 'done') {
                    eventSource.close();
                    resolve({ references: data.references });
                }
            };
            eventSource.onerror = () => {
                eventSource.close();
                reject(new Error('流式响应中断'));
            };
        });
    },
};
```

```typescript
// src/api/types.ts
export interface Document {
    id: number;
    title: string;
    status: 'pending' | 'processing' | 'ready' | 'failed';
    createdAt: string;
    userId: number;
}

export interface UploadResponse {
    documentId: number;
    message: string;
}

export interface ApiResponse<T> {
    code: number;
    message: string;
    data: T;
    timestamp: string;
}
```

### AI 项目额外封装

**SSE 流式请求 Composable（Vue 3，S6 选择 Vue 3 时使用）**：

```typescript
// src/composables/useSseStream.ts
import { ref } from 'vue';

export const useSseStream = () => {
  const streaming = ref(false);
  const error = ref<string | null>(null);
  let controller: AbortController | null = null;

  const connect = async (
    url: string,
    handlers: {
      onToken: (token: string) => void;
      onDone: (payload?: unknown) => void;
    }
  ) => {
    controller = new AbortController();
    streaming.value = true;
    error.value = null;
    try {
      const response = await fetch(url, {
        signal: controller.signal,
        credentials: 'include',
      });
      const reader = response.body?.getReader();
      if (!reader) throw new Error('流式响应不可读');
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value);
        for (const line of chunk.split('\n')) {
          if (!line.startsWith('data: ')) continue;
          const data = JSON.parse(line.slice(6));
          if (data.type === 'token') handlers.onToken(data.content);
          if (data.type === 'done') handlers.onDone(data);
        }
      }
    } catch (e: any) {
      if (e.name !== 'AbortError') error.value = e.message || '流式响应中断';
    } finally {
      streaming.value = false;
    }
  };

  const disconnect = () => controller?.abort();

  return { streaming, error, connect, disconnect };
};
```

**SSE 流式请求 Hook（React，仅 React 技术栈使用）**：

```typescript
// src/hooks/useSSE.ts
import { useState, useCallback, useRef } from 'react';

interface SSEOptions {
    onToken: (token: string) => void;
    onDone: (references?: any[]) => void;
    onError: (error: Error) => void;
}

export const useSSE = () => {
    const abortControllerRef = useRef<AbortController | null>(null);

    const connect = useCallback((url: string, options: SSEOptions) => {
        abortControllerRef.current = new AbortController();

        fetch(url, {
            signal: abortControllerRef.current.signal,
            credentials: 'include',
        })
            .then((response) => {
                const reader = response.body!.getReader();
                const decoder = new TextDecoder();

                const read = async () => {
                    while (true) {
                        const { done, value } = await reader.read();
                        if (done) break;

                        const chunk = decoder.decode(value);
                        const lines = chunk.split('\n');

                        for (const line of lines) {
                            if (line.startsWith('data: ')) {
                                const data = JSON.parse(line.slice(6));
                                if (data.type === 'token') {
                                    options.onToken(data.content);
                                } else if (data.type === 'done') {
                                    options.onDone(data.references);
                                }
                            }
                        }
                    }
                };

                read().catch((e) => options.onError(e));
            })
            .catch((e) => {
                if (e.name !== 'AbortError') {
                    options.onError(e);
                }
            });
    }, []);

    const disconnect = useCallback(() => {
        abortControllerRef.current?.abort();
    }, []);

    return { connect, disconnect };
};
```

## 行为规则

- ✅ 所有请求必须有统一的错误处理
- ✅ Cookie 认证场景必须启用 `withCredentials` / `credentials: 'include'`
- ✅ 401 自动跳转登录页
- ✅ AI 项目必须封装 SSE 流式请求
- ✅ 所有 API 必须有 TypeScript 类型定义
- ✅ 每个 API client 方法必须映射 OpenSpec requirement / scenario
- ✅ 超时时间可配置（普通请求 30s，上传 120s）
- ❌ 不得在每个页面单独写请求逻辑
- ❌ 不得从 LocalStorage 读取 Token 注入请求头
- ❌ 不得忽略响应拦截器中的错误
- ❌ 不得硬编码 API 地址（使用环境变量）

## 使用示例

```
加载 <skill id="S19">，输入：docs/skill-chain/api_contract.md
请根据 docs/skill-chain/tech_stack.md 中的前端技术栈生成 API 客户端代码，包含 SSE 流式请求封装。
```
</skill>
<!-- end -->
