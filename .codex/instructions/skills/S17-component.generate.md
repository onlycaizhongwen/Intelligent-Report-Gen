<!-- skill: S17 -->
<skill id="S17" name="component.generate">

# 技能：组件生成

## Meta
- DependsOn: S16
- Category: frontend
- Status: stable

## 一句话描述
根据页面骨架，生成可复用的 UI 组件。

## 输入
- `pages/`（页面骨架代码）
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `components/`（组件代码目录）

## Prompt

你是一位资深前端工程师。
请根据页面骨架，生成可复用的 UI 组件。

生成前必须读取 OpenSpec：
- 每个组件必须标注服务的页面、用户操作、requirement / scenario。
- 组件状态（加载、空态、错误、权限不可见、AI 流式中）必须来源于 OpenSpec 或 API 契约，不得凭空扩展流程。
- 必须读取 `docs/skill-chain/tech_stack.md` 或上游页面骨架判断前端生态；组件技术栈必须跟随 S6 产物，不得硬编码为 React 或 Vue。
- 当页面骨架为 Vue 3 时，必须生成 `.vue` 单文件组件和 `<script setup lang="ts">`，不得生成 React TSX 组件作为主方案。

---

### Vue 3 组件示例（S6 选择 Vue 3 时使用）

```vue
<!-- src/components/ChatInput.vue -->
<template>
  <div class="chat-input">
    <el-input
      v-model="input"
      type="textarea"
      :disabled="loading"
      placeholder="输入你的问题..."
      @keydown.enter.exact.prevent="handleSubmit"
    />
    <el-button type="primary" :loading="loading" :disabled="!input.trim()" @click="handleSubmit">
      发送
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{
  loading: boolean;
}>();

const emit = defineEmits<{
  submit: [text: string];
}>();

const input = ref('');

const handleSubmit = () => {
  if (!input.value.trim() || props.loading) return;
  emit('submit', input.value.trim());
  input.value = '';
};
</script>
```

```vue
<!-- src/components/StreamingMessage.vue -->
<template>
  <article class="streaming-message">
    <header>
      <span>AI 助手</span>
      <span v-if="isStreaming">|</span>
    </header>
    <section>{{ content }}</section>
    <el-collapse v-if="!isStreaming && references?.length">
      <el-collapse-item title="参考来源">
        <el-card v-for="ref in references" :key="ref.id || ref.title">
          <strong>{{ ref.title }}</strong>
          <p>{{ ref.snippet }}</p>
        </el-card>
      </el-collapse-item>
    </el-collapse>
  </article>
</template>

<script setup lang="ts">
defineProps<{
  content: string;
  isStreaming: boolean;
  references?: Array<{ id?: string; title: string; snippet: string }>;
}>();
</script>
```

### React 组件示例（仅 React 技术栈使用）

```tsx
// src/components/ChatInput/ChatInput.tsx
import React, { useState, useRef } from 'react';
import styles from './ChatInput.module.css';

interface ChatInputProps {
    loading: boolean;
    onSubmit: (text: string) => void;
}

export const ChatInput: React.FC<ChatInputProps> = ({ loading, onSubmit }) => {
    const [input, setInput] = useState('');
    const inputRef = useRef<HTMLInputElement>(null);

    const handleSubmit = () => {
        if (!input.trim() || loading) return;
        onSubmit(input.trim());
        setInput('');
        inputRef.current?.focus();
    };

    return (
        <div className={styles.container}>
            <input
                ref={inputRef}
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSubmit()}
                placeholder="输入你的问题..."
                disabled={loading}
                className={styles.input}
            />
            <button
                onClick={handleSubmit}
                disabled={loading || !input.trim()}
                className={styles.sendBtn}
            >
                {loading ? '发送中...' : '发送'}
            </button>
        </div>
    );
};
```

```tsx
// src/components/MessageList/MessageList.tsx
import React from 'react';
import styles from './MessageList.module.css';

interface Message {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    references?: Array<{ title: string; snippet: string }>;
    timestamp: Date;
}

interface MessageListProps {
    messages: Message[];
}

export const MessageList: React.FC<MessageListProps> = ({ messages }) => {
    return (
        <div className={styles.container}>
            {messages.map((msg) => (
                <div
                    key={msg.id}
                    className={`${styles.message} ${styles[msg.role]}`}
                >
                    <div className={styles.bubble}>
                        {msg.role === 'assistant' && (
                            <div className={styles.aiLabel}>AI 助手</div>
                        )}
                        <div className={styles.content}>{msg.content}</div>
                        {msg.references && (
                            <div className={styles.references}>
                                <h4>参考来源：</h4>
                                {msg.references.map((ref, i) => (
                                    <div key={i} className={styles.refItem}>
                                        <strong>{ref.title}</strong>
                                        <p>{ref.snippet}</p>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                    <span className={styles.time}>
                        {msg.timestamp.toLocaleTimeString()}
                    </span>
                </div>
            ))}
        </div>
    );
};
```

```tsx
// src/components/DocumentUpload/DocumentUpload.tsx
import React, { useCallback, useState } from 'react';
import styles from './DocumentUpload.module.css';

export const DocumentUpload: React.FC = () => {
    const [uploading, setUploading] = useState(false);
    const [dragOver, setDragOver] = useState(false);

    const handleUpload = useCallback(async (files: FileList) => {
        setUploading(true);
        try {
            const formData = new FormData();
            Array.from(files).forEach(f => formData.append('files', f));
            const res = await fetch('/api/v1/documents/upload', {
                method: 'POST',
                body: formData,
            });
            const data = await res.json();
            console.log('上传成功:', data);
        } catch (e) {
            console.error('上传失败:', e);
        } finally {
            setUploading(false);
        }
    }, []);

    return (
        <div
            className={`${styles.container} ${dragOver ? styles.dragOver : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                handleUpload(e.dataTransfer.files);
            }}
        >
            <div className={styles.icon}>📄</div>
            <p>拖拽文件到此处，或 <label className={styles.browse}>浏览文件</label></p>
            <p className={styles.hint}>支持 PDF、Word、TXT 格式</p>
            {uploading && <div className={styles.progress}>上传中...</div>}
            <input
                type="file"
                multiple
                accept=".pdf,.docx,.txt"
                onChange={(e) => e.target.files && handleUpload(e.target.files)}
                className={styles.hiddenInput}
            />
        </div>
    );
};
```

### AI 项目专属组件

| 组件名 | 功能 | 关键实现 |
|--------|------|----------|
| `ChatInput` | 输入框 + 发送按钮 | 支持 Enter 发送 |
| `MessageList` | 消息列表（含引用来源） | 打字机动画 |
| `StreamingMessage` | 流式消息气泡 | 逐字显示效果 |
| `ReferenceCard` | 引用来源卡片 | 可展开/折叠 |
| `DocumentUpload` | 拖拽上传区域 | 支持多文件 + 进度 |
| `DocumentList` | 文档列表（含状态） | 删除/重试操作 |
| `TokenUsage` | Token 用量展示 | 实时统计 |

### 打字机效果组件（AI 流式输出，React 示例仅 React 技术栈使用）

```tsx
// src/components/StreamingMessage/StreamingMessage.tsx
import React, { useEffect, useRef } from 'react';
import styles from './StreamingMessage.module.css';

interface StreamingMessageProps {
    content: string;       // 当前已接收的内容
    isStreaming: boolean;  // 是否还在流式传输中
    references?: Array<{ title: string; snippet: string }>;
}

export const StreamingMessage: React.FC<StreamingMessageProps> = ({
    content, isStreaming, references
}) => {
    const contentRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (contentRef.current && isStreaming) {
            contentRef.current.scrollTop = contentRef.current.scrollHeight;
        }
    }, [content, isStreaming]);

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <span className={styles.label}>AI 助手</span>
                {isStreaming && <span className={styles.cursor}>▌</span>}
            </div>
            <div ref={contentRef} className={styles.content}>
                {content}
            </div>
            {!isStreaming && references && (
                <div className={styles.references}>
                    <h4>📎 参考来源</h4>
                    {references.map((ref, i) => (
                        <details key={i} className={styles.refItem}>
                            <summary>{ref.title}</summary>
                            <p>{ref.snippet}</p>
                        </details>
                    ))}
                </div>
            )}
        </div>
    );
};
```

## 行为规则

- ✅ 组件必须单一职责（一个组件只做一件事）
- ✅ Props 必须有完整的 TypeScript 类型定义
- ✅ 必须遵循 S6 技术栈；S6 选择 Vue 3 时生成 Vue 单文件组件，S6 选择 React 时生成 React 组件
- ✅ AI 组件必须支持流式渲染
- ✅ 组件必须有加载态 / 空态 / 错误态
- ✅ 每个核心组件必须追溯 OpenSpec requirement / scenario
- ✅ 使用 CSS Modules / Scoped CSS（禁止全局样式污染）
- ❌ 组件不得直接调用 API（放到 Store / Service）
- ❌ 不得写死数据（通过 Props 传入）
- ❌ 不得新增 OpenSpec 未定义的用户操作或业务状态

## 使用示例

```
加载 <skill id="S17">，输入：pages/、openspec/specs/
请生成所有页面所需的 UI 组件，包含 AI 对话相关的流式组件。
```
</skill>
<!-- end -->
