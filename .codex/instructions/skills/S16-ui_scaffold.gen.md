<!-- skill: S16 -->
<skill id="S16" name="ui_scaffold.gen">

# 技能：页面骨架生成

## Meta
- DependsOn: S4, S6
- Category: frontend
- Status: stable

## 一句话描述
根据 API 契约，生成前端页面骨架结构。

## 输入
- `docs/skill-chain/api_contract.md`：API 接口文档
- `docs/skill-chain/tech_stack.md`：技术栈选型文档
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `pages/`：页面文件目录

## Prompt

你是一位资深前端架构师。
请根据 API 契约，生成前端页面骨架。

页面骨架必须追溯 OpenSpec：每个页面、路由和关键用户操作必须标注对应 capability / scenario。

**根据 docs/skill-chain/tech_stack.md 选择框架：Vue 3 / React / Next.js**

前端生态选择规则：
- 必须以 `docs/skill-chain/tech_stack.md` 的前端主选型为准。
- 页面骨架必须跟随 S6 前端主选型生成；Vue、React、Next.js 等示例只在对应技术栈被选中时使用。
- 不得生成与 S6 前端主选型冲突的目录、路由或组件文件作为主方案。

---

### Vue 3 版（TypeScript + Vite，S6 选择 Vue 3 时使用）

项目结构：
```
src/
├── pages/
│   ├── Home.vue
│   ├── Login.vue
│   ├── Dashboard.vue
│   ├── reports/
│   │   ├── ReportCreate.vue
│   │   ├── ReportOutline.vue
│   │   └── ReportDetail.vue
│   ├── knowledge/
│   │   ├── KnowledgeList.vue
│   │   ├── KnowledgeUpload.vue
│   │   └── DataSourceConfig.vue
│   ├── rules/
│   │   └── RuleDesigner.vue
│   ├── audit/
│   │   └── AuditDashboard.vue
│   └── admin/
│       └── UserManage.vue
├── components/                # (由 S17 生成)
├── stores/                    # (由 S18 生成)
├── api/                       # (由 S19 生成)
├── router/
│   └── index.ts
├── App.vue
└── main.ts
```

页面骨架示例（报告生成页）：
```vue
<!-- src/pages/reports/ReportCreate.vue -->
<template>
  <section class="report-create">
    <header>
      <h1>智能报告生成</h1>
    </header>
    <el-form :model="form" label-position="top">
      <el-form-item label="报告主题">
        <el-input v-model="form.topic" />
      </el-form-item>
      <el-form-item label="重点关注">
        <el-input v-model="form.focus" type="textarea" />
      </el-form-item>
      <el-button type="primary" :loading="submitting" @click="submit">
        生成大纲
      </el-button>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';

const form = reactive({ topic: '', focus: '' });
const submitting = ref(false);

const submit = async () => {
  submitting.value = true;
  try {
    // 调用 Store / Service，页面不直接写业务逻辑
  } finally {
    submitting.value = false;
  }
};
</script>
```

### React 版（TypeScript + Vite，仅 React 技术栈使用）

项目结构：
```
src/
├── pages/
│   ├── Home/
│   │   ├── index.tsx
│   │   └── Home.module.css
│   ├── Login/
│   │   ├── index.tsx
│   │   └── Login.module.css
│   ├── Dashboard/
│   │   ├── index.tsx
│   │   └── Dashboard.module.css
│   ├── Documents/
│   │   ├── index.tsx           # 文档列表
│   │   ├── Upload.tsx         # 上传页面
│   │   └── Chat.tsx          # AI 对话页面
│   └── Admin/
│       ├── UserManage.tsx
│       └── SystemConfig.tsx
├── components/                # (由 S17 生成)
├── store/                    # (由 S18 生成)
├── api/                      # (由 S19 生成)
├── App.tsx
└── main.tsx
```

页面骨架示例（Document Chat）：
```tsx
// src/pages/Documents/Chat.tsx
import React, { useState, useRef, useEffect } from 'react';
import { ChatInput } from '../../components/ChatInput';
import { MessageList } from '../../components/MessageList';
import { useChat } from '../../store/chatStore';
import styles from './Chat.module.css';

const DocumentChat: React.FC = () => {
    const { messages, loading, sendMessage } = useChat();
    const bottomRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    return (
        <div className={styles.container}>
            <header className={styles.header}>
                <h1>AI 知识库问答</h1>
            </header>
            <main className={styles.chatArea}>
                <MessageList messages={messages} />
                <div ref={bottomRef} />
            </main>
            <footer className={styles.inputArea}>
                <ChatInput
                    loading={loading}
                    onSubmit={(text) => sendMessage(text)}
                />
            </footer>
        </div>
    );
};

export default DocumentChat;
```

### AI 项目额外页面

| 页面 | 核心功能 | 关键交互 |
|------|----------|----------|
| 文档上传页 | 拖拽上传 + 进度条 | 支持 PDF/Word/TXT |
| AI 对话页 | 流式打字机效果 + 引用展示 | SSE 实时接收 |
| 文档管理页 | 列表 + 删除 + 状态查看 | 实时刷新状态 |
| 知识库配置页 | Embedding 模型选择 + 切片参数 | 管理员专属 |

### 路由配置（Vue Router 4，S6 选择 Vue 3 时使用）

```typescript
// src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../pages/Login.vue') },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: () => import('../pages/Dashboard.vue') },
    { path: '/reports/create', component: () => import('../pages/reports/ReportCreate.vue') },
    { path: '/reports/:id', component: () => import('../pages/reports/ReportDetail.vue') },
    { path: '/knowledge', component: () => import('../pages/knowledge/KnowledgeList.vue') },
    { path: '/knowledge/upload', component: () => import('../pages/knowledge/KnowledgeUpload.vue') },
    { path: '/rules', component: () => import('../pages/rules/RuleDesigner.vue') },
    { path: '/audit', component: () => import('../pages/audit/AuditDashboard.vue') },
    { path: '/admin/users', component: () => import('../pages/admin/UserManage.vue') },
  ],
});
```

### 路由配置（React Router v6，仅 React 技术栈使用）

```tsx
// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ProtectedRoute } from './components/ProtectedRoute';

const App: React.FC = () => (
    <BrowserRouter>
        <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<ProtectedRoute />}>
                <Route index element={<Navigate to="/dashboard" />} />
                <Route path="dashboard" element={<Dashboard />} />
                <Route path="documents" element={<DocumentList />} />
                <Route path="documents/upload" element={<DocumentUpload />} />
                <Route path="documents/chat" element={<DocumentChat />} />
                <Route path="admin/users" element={<UserManage />} />
            </Route>
        </Routes>
    </BrowserRouter>
);
```

## 行为规则

- ✅ 页面结构必须覆盖 API 契约中的所有资源
- ✅ 页面和路由必须覆盖 OpenSpec user journey / scenario
- ✅ 必须遵循 S6 技术栈；S6 选择 Vue 3 时生成 Vue 页面骨架，S6 选择 React 时生成 React 页面骨架
- ✅ AI 项目必须包含对话页（流式响应 UI）
- ✅ 必须使用 TypeScript（类型安全）
- ✅ 页面必须有加载态 / 空态 / 错误态
- ✅ 必须使用 CSS Modules / Tailwind（禁止全局 CSS 污染）
- ❌ 不得在页面中写业务逻辑（放到 Store / Service）
- ❌ 不得硬编码 API 地址

## 使用示例

```
加载 <skill id="S16">，输入：docs/skill-chain/api_contract.md 和 docs/skill-chain/tech_stack.md
请根据 docs/skill-chain/tech_stack.md 中的前端技术栈生成页面骨架，包含报告生成、知识库、规则画布和审计工作台页面。
```
</skill>
<!-- end -->
