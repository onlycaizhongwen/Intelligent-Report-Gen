<!-- skill: S28 -->
<skill id="S28" name="changelog.gen">

# 技能：变更日志生成

## Meta
- DependsOn: S27
- Category: delivery
- Status: stable

## 一句话描述
根据 Git 提交记录，自动生成变更日志。

## 输入
- `docs/skill-chain/DEPLOY.md`（部署文档）
- `openspec/changes/`：OpenSpec 变更目录

## 输出
- `docs/skill-chain/CHANGELOG.md`：变更日志（中文 Markdown）

## Prompt

你是一位资深技术写作工程师。
请根据 Git 提交记录，生成符合 [Keep a Changelog](https://keepachangelog.com/) 规范的变更日志。

变更来源必须包含 OpenSpec：
- 每条功能、安全、接口、数据、测试、部署变更必须关联 OpenSpec change 或 capability。
- 每条功能性变更必须追溯到 OpenSpec requirement / scenario；OpenSpec baseline 冻结后不得把未纳入 change 的能力写入变更日志。
- 如果 Git 提交与 OpenSpec changes 不一致，必须输出差异项，不得把未记录的功能写成已确认变更。

### 输出格式

```markdown
# 变更日志

本文档记录所有项目版本的重要变更。

---

## [未发布] - YYYY-MM-DD

### 🚀 新增
- 新增用户管理模块（S13 鉴权实现）
- 新增文档上传与解析功能（S14 业务逻辑）
- 新增 AI 对话流式响应（S14 业务逻辑）

### 🔧 优化
- 优化向量检索性能（S14 RAG 检索）
- 优化前端聊天 UI 打字机效果（S17 组件生成）

### 🐛 修复
- 修复文档删除后 Milvus 向量未清理的问题（S14）
- 修复 Token 超时未正确处理的问题（S15 错误处理）

### 🔒 安全
- 新增 Prompt 注入检测（S13 鉴权实现）
- 敏感信息脱敏处理（S13）

---

## [1.0.0] - 2024-01-15

### 🚀 新增
- 初始版本发布
- 用户注册/登录（JWT 鉴权）
- 文档上传与 AI 语义搜索
- AI 对话（SSE 流式响应）
- 管理员用户管理

### 🔧 技术栈
- 后端：以 `docs/skill-chain/tech_stack.md` 的 S6 技术栈为准
- AI 服务：以 `docs/skill-chain/tech_stack.md` 的 S6 技术栈为准
- 网关：以 `docs/skill-chain/tech_stack.md` 的 S6 技术栈为准
- 向量数据库：以 `docs/skill-chain/tech_stack.md` 的 S6 技术栈为准
- 前端：以 `docs/skill-chain/tech_stack.md` 的 S6 技术栈为准
```

### 提交类型映射

| Git Commit 前缀 | Changelog 分类 | Emoji |
|----------------|---------------|-------|
| `feat:` | 🚀 新增 | 🚀 |
| `fix:` | 🐛 修复 | 🐛 |
| `refactor:` | 🔧 优化 | 🔧 |
| `perf:` | ⚡ 性能 | ⚡ |
| `security:` / `sec:` | 🔒 安全 | 🔒 |
| `docs:` | 📝 文档 | 📝 |
| `test:` | ✅ 测试 | ✅ |
| `chore:` | 🔧 杂项 | 🔧 |

### AI 项目额外分类

| Git Commit 前缀 | Changelog 分类 | Emoji |
|----------------|---------------|-------|
| `model:` | 🤖 模型 | 🤖 |
| `rag:` | 📚 检索增强 | 📚 |
| `prompt:` | 💬 Prompt | 💬 |
| `vector:` | 🔍 向量 | 🔍 |
| `token:` | 💰 Token | 💰 |

## 行为规则

- ✅ 必须按版本号组织（语义化版本 v2.0.0）
- ✅ 必须包含日期
- ✅ 分类使用中文 + Emoji
- ✅ AI 项目必须包含 🤖 模型 / 📚 检索增强 / 💬 Prompt 分类
- ✅ 如技术栈选择 Higress，技术栈区必须写 Higress，不得继续写 Go Gin 网关
- ✅ 技术栈区必须读取 S6 产物；不得硬编码 React、Vue 或任何固定前端栈
- ✅ 每条变更必须有对应的 Skill 编号（如 S14）
- ✅ 每条功能性变更必须关联 OpenSpec change / capability
- ✅ 每条功能性变更必须追溯 OpenSpec requirement / scenario
- ✅ 必须包含 [未发布] 区段
- ❌ 不得包含无意义的提交（如 "fix typo" 不重要的）
- ❌ 不得发布 OpenSpec 未记录的功能变更
- ❌ 不得遗漏重大变更

## 使用示例

```
加载 <skill id="S28">，输入：docs/skill-chain/DEPLOY.md、openspec/changes/
请根据 Git 提交记录生成 docs/skill-chain/CHANGELOG.md，包含 AI 相关变更分类。
```
</skill>
<!-- end -->
