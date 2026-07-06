<!-- skill: S18 -->
<skill id="S18" name="state_design">

# 技能：状态管理设计

## Meta
- DependsOn: S16, S6
- Category: frontend
- Status: stable

## 一句话描述
根据页面结构，设计前端状态管理方案。

## 输入
- `pages/`（页面骨架代码）
- `docs/skill-chain/tech_stack.md`：技术栈选型文档
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `store/`（状态管理代码目录）

## Prompt

你是一位资深前端架构师。
请根据页面结构，设计状态管理方案。

**根据 docs/skill-chain/tech_stack.md 选择方案：Pinia（Vue 3）/ Redux Toolkit（React）**

前端状态选择规则：
- 必须以 S6 技术栈为准；S6 选择 Vue 3 时使用 **Pinia + TanStack Query for Vue**，S6 选择 React 时使用对应的 React 状态方案。
- Pinia 只保存客户端会话级 UI 状态、生成任务阶段状态、SSE 流式片段和必要的用户展示状态。
- 服务端列表、分页、筛选和详情缓存优先交给 TanStack Query for Vue，不要全部塞进 Pinia。
- Token 不得存放在 LocalStorage 或普通 Store；优先使用 HttpOnly Cookie，由后端和网关完成会话校验。

状态设计必须追溯 OpenSpec：
- 每个 Store / Slice / Action 必须标注覆盖的 requirement / scenario。
- 状态机必须覆盖 OpenSpec 中的 happy path、sad path、权限态和 AI 流式态。
- 不得为了前端便利新增 OpenSpec 未定义的业务状态。

---

### Vue 3 + Pinia 版（S6 选择 Vue 3 时使用）

```typescript
// src/stores/reportGeneration.ts
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { reportApi } from '../api/reportApi';

export const useReportGenerationStore = defineStore('reportGeneration', () => {
  const taskId = ref<string | null>(null);
  const stage = ref<'idle' | 'retrieving' | 'analyzing' | 'generating' | 'done' | 'failed'>('idle');
  const streamingText = ref('');
  const error = ref<string | null>(null);

  const start = async (payload: { topic: string; focus?: string }) => {
    error.value = null;
    const task = await reportApi.createTask(payload);
    taskId.value = task.id;
    stage.value = 'retrieving';
  };

  const appendToken = (content: string) => {
    streamingText.value += content;
  };

  const fail = (message: string) => {
    stage.value = 'failed';
    error.value = message;
  };

  return { taskId, stage, streamingText, error, start, appendToken, fail };
});
```

### React + Redux Toolkit 版（仅 React 技术栈使用）

```typescript
// src/store/chatSlice.ts
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { apiClient } from '../api/client';

interface Message {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    references?: Array<{ title: string; snippet: string }>;
    timestamp: number;
}

interface ChatState {
    messages: Message[];
    loading: boolean;
    error: string | null;
    tokenUsage: { prompt: number; completion: number } | null;
}

// 异步 Thunk：发送消息（SSE 流式）
export const sendMessage = createAsyncThunk(
    'chat/sendMessage',
    async (question: string, { dispatch }) => {
        dispatch(chatSlice.actions.setLoading(true));
        const eventSource = new EventSource(
            `/api/v1/chat/stream?question=${encodeURIComponent(question)}`
        );

        let fullContent = '';
        const refs: Message['references'] = [];

        return new Promise<Message>((resolve, reject) => {
            eventSource.onmessage = (event) => {
                const data = JSON.parse(event.data);
                if (data.type === 'token') {
                    fullContent += data.content;
                    dispatch(chatSlice.actions.appendToken(data.content));
                } else if (data.type === 'done') {
                    refs.push(...(data.references || []));
                    eventSource.close();
                    resolve({
                        id: Date.now().toString(),
                        role: 'assistant',
                        content: fullContent,
                        references: refs,
                        timestamp: Date.now(),
                    });
                }
            };
            eventSource.onerror = () => {
                eventSource.close();
                reject(new Error('流式响应中断'));
            };
        });
    }
);

const chatSlice = createSlice({
    name: 'chat',
    initialState: {
        messages: [],
        loading: false,
        error: null,
        tokenUsage: null,
    } as ChatState,
    reducers: {
        setLoading: (state, action) => { state.loading = action.payload; },
        appendToken: (state, action) => {
            const lastMsg = state.messages[state.messages.length - 1];
            if (lastMsg && lastMsg.role === 'assistant') {
                lastMsg.content += action.payload;
            }
        },
        addMessage: (state, action) => {
            state.messages.push(action.payload);
        },
        setError: (state, action) => {
            state.error = action.payload;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(sendMessage.pending, (state) => {
                state.loading = true;
                state.error = null;
            })
            .addCase(sendMessage.fulfilled, (state, action) => {
                state.loading = false;
                state.messages.push(action.payload);
            })
            .addCase(sendMessage.rejected, (state, action) => {
                state.loading = false;
                state.error = action.error.message || '发送失败';
            });
    },
});

export const { setLoading, appendToken, addMessage, setError } = chatSlice.actions;
export default chatSlice.reducer;
```

```typescript
// src/store/documentSlice.ts
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { documentApi } from '../api/documentApi';

const fetchDocuments = createAsyncThunk(
    'documents/fetchAll',
    async (userId: number) => {
        const res = await documentApi.list(userId);
        return res.data;
    }
);

const uploadDocument = createAsyncThunk(
    'documents/upload',
    async (file: File) => {
        const formData = new FormData();
        formData.append('file', file);
        const res = await documentApi.upload(formData);
        return res.data;
    }
);

const deleteDocument = createAsyncThunk(
    'documents/delete',
    async (docId: number) => {
        await documentApi.delete(docId);
        return docId;
    }
);

const documentSlice = createSlice({
    name: 'documents',
    initialState: {
        items: [],
        loading: false,
        uploading: false,
    },
    reducers: {},
    extraReducers: (builder) => {
        builder
            .addCase(fetchDocuments.fulfilled, (state, action) => {
                state.items = action.payload;
            })
            .addCase(uploadDocument.pending, (state) => {
                state.uploading = true;
            })
            .addCase(uploadDocument.fulfilled, (state, action) => {
                state.uploading = false;
                state.items.unshift(action.payload);
            })
            .addCase(deleteDocument.fulfilled, (state, action) => {
                state.items = state.items.filter(d => d.id !== action.payload);
            });
    },
});

export default documentSlice.reducer;
```

```typescript
// src/store/index.ts
import { configureStore } from '@reduxjs/toolkit';
import chatReducer from './chatSlice';
import documentReducer from './documentSlice';
import authReducer from './authSlice';

export const store = configureStore({
    reducer: {
        chat: chatReducer,
        documents: documentReducer,
        auth: authReducer,
    },
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware({
            serializableCheck: false, // SSE 流式数据不需要序列化检查
        }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

### 状态设计清单

| 状态模块 | 管理内容 | 持久化 |
|----------|----------|----------|
| `auth` | 当前用户摘要、角色、权限项；不保存 Token | ❌ Token 使用 HttpOnly Cookie |
| `reportGeneration` | 任务 ID、阶段、流式内容、错误态 | ❌（会话级） |
| `knowledgeUpload` | 上传队列、解析进度、失败原因 | ❌（任务级） |
| `ui` | 侧边栏折叠、主题、语言 | ✅ 非敏感偏好可本地持久化 |
| `tokenUsage` | Token 用量统计 | ❌（统计展示） |

## 行为规则

- ✅ 状态模块必须按功能拆分（不得全放一个 Store）
- ✅ AI 流式生成必须使用 Pinia Action / Service 处理 SSE 流
- ✅ 敏感数据（Token）不得存 Redux、Pinia 或 LocalStorage（用 HttpOnly Cookie）
- ✅ 状态必须有加载态 / 错误态 / 成功态
- ✅ Store / Action 必须追溯 OpenSpec requirement / scenario
- ❌ 禁止在组件中直接修改 Store（通过 Action）
- ❌ 禁止 Store 嵌套过深（不超过 2 层）
- ❌ 不得引入 OpenSpec 未定义的前端业务状态

## 使用示例

```
加载 <skill id="S18">，输入：pages/、docs/skill-chain/tech_stack.md、openspec/specs/
请设计状态管理方案，包含 AI 对话的流式状态处理。
```
</skill>
<!-- end -->
