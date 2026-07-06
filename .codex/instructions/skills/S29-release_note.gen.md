<!-- skill: S29 -->
<skill id="S29" name="release_note.gen">

# 技能：发版说明

## Meta
- DependsOn: S28
- Category: delivery
- Status: stable

## 一句话描述
根据变更日志，生成版本发版说明。

## 输入
- `docs/skill-chain/CHANGELOG.md`（变更日志）
- `openspec/changes/`：OpenSpec 变更目录

## 输出
- `docs/skill-chain/RELEASE.md`：发版说明文档（中文 Markdown）

## Prompt

你是一位资深技术写作工程师。
请根据变更日志，生成发版说明。

发版说明必须以 OpenSpec changes 为事实来源：
- 核心亮点、完整变更、兼容性、升级指南和已知问题必须能追溯到 OpenSpec change / capability。
- 每个已发布能力必须追溯到 OpenSpec requirement / scenario；OpenSpec baseline 冻结后，未纳入 change 的能力只能列为待规划，不得列为已发布。
- 未在 OpenSpec changes 中记录或未通过验证的能力，不得写入“已发布功能”。

### 输出格式

```markdown
# 🚀 FullstackApp v1.0.0 发版说明

**发布日期**：2024 年 1 月 15 日  
**版本类型**：🎉 重大更新  
**兼容性**：✅ 向后兼容

---

## 📋 本次更新概览

本次更新为 FullstackApp 首个正式版本，包含完整的用户管理、AI 知识库问答、
管理员后台等核心功能。

---

## 🚀 核心亮点

### 1. AI 知识库问答
- 支持上传 PDF / Word / TXT 文档，自动解析并向量化
- 基于 RAG（检索增强生成）架构，回答可追溯至原文
- 支持流式打字机效果，实时呈现 AI 回复
- 集成 BGE-M3 中文 Embedding 模型，检索精度高

### 2. 用户管理与权限控制
- JWT 鉴权 + RBAC 权限模型
- 支持管理员 / 普通用户 / 访客三种角色
- Spring Security 全局安全防护

### 3. 管理员后台
- 用户账号管理（启用 / 禁用）
- 文档库管理（查看 / 删除）
- 系统配置（Embedding 模型选择、切片参数）

---

## 📝 完整变更列表

### 🚀 新增功能
- 用户注册 / 登录（JWT 认证） #S13
- 文档上传与自动解析（PDF / Word / TXT） #S14
- AI 语义搜索与对话问答 #S14
- 流式打字机效果展示 #S17
- 引用来源展示（可追溯至原文） #S14
- 管理员用户管理 #S12
- 管理员文档管理 #S12
- BERT 文本分析（情感分析 / 关键词提取） #S14
- Token 用量统计与限流 #S15

### 🔧 技术优化
- 使用 S6 选定的向量检索方案优化检索性能 #S09
- 文档切片异步处理，上传响应 < 1s #S14
- Redis 缓存热点数据，QPS 提升 5x #S09
- 使用 S6 选定的网关方案承载入口、治理或 AI Gateway 能力 #S06

### 🐛 问题修复
- 修复文档解析超时问题 #S14
- 修复多用户并发上传冲突 #S12
- 修复 Token 计费不准确的 Bug #S15
- 修复前端消息列表滚动异常 #S17

### 🔒 安全加固
- 新增 Prompt 注入检测与拦截 #S13
- 敏感信息脱敏处理 #S13
- API 限流与防暴力破解 #S08
- HTTPS 全站加密（Certbot） #S27

---

## 🔧 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端 | 以 S6 技术栈为准 | 读取 docs/skill-chain/tech_stack.md |
| 后端（业务） | 以 S6 技术栈为准 | 读取 docs/skill-chain/tech_stack.md |
| 后端（AI） | 以 S6 技术栈为准 | 读取 docs/skill-chain/tech_stack.md |
| 网关 | 以 S6 技术栈为准 | 按部署环境锁定版本 |
| 数据库 | 以 S6 技术栈为准 | 按部署环境锁定版本 |
| 向量库 | 以 S6 技术栈为准 | 按部署环境锁定版本 |
| 缓存 | 以 S6 技术栈为准 | 按部署环境锁定版本 |
| 容器 | 以 S6/S27 部署方案为准 | 按部署环境锁定版本 |

---

## 📦 部署方式

### Docker Compose（快速体验）
```bash
git clone https://github.com/your-org/fullstack-app.git
cd fullstack-app
cp .env.example .env
# 编辑 .env 填入密钥
docker compose up -d
```

### Kubernetes（生产推荐）
```bash
kubectl apply -f k8s/base/ -n fullstack-app
kubectl apply -f k8s/app/ -n fullstack-app
```

---

## ⬆️ 升级指南

### 从 v0.x 升级到 v1.0.0

1. **数据库迁移**：
   ```bash
   # 执行新增字段迁移
   kubectl exec -it postgres-pod -n fullstack-app -- \
     psql -U app_user -d report_db -f migrations/V004__add_columns.sql
   ```

2. **向量数据库升级**：
   ```bash
   # Milvus 需重建 Collection（新维度）
   kubectl exec -it python-ai-pod -n fullstack-app -- \
     python scripts/rebuild_milvus.py
   ```

3. **配置更新**：
   ```bash
   # 新增环境变量
   echo "EMBEDDING_MODEL=bge-m3" >> .env
   echo "VECTOR_TOP_K=5" >> .env
   ```

---

## ⚠️ 已知问题

- 🟡 **Token 计费精度**：极小概率出现并发下 Token 计数偏差（不影响功能）
- 🟡 **大文件上传**：超过 100MB 的 PDF 解析较慢（建议拆分后上传）

---

## 🔗 相关链接

- [完整变更日志](docs/skill-chain/CHANGELOG.md)
- [部署文档](docs/skill-chain/DEPLOY.md)
- [API 接口文档](docs/skill-chain/api_contract.md)
- [技术栈说明](docs/skill-chain/DEPLOY.md#环境准备)

---

**感谢所有贡献者！** 🙏
```

## 行为规则

- ✅ 必须包含版本号、发布日期、版本类型
- ✅ 必须包含核心亮点（面向非技术读者）
- ✅ 必须包含完整变更列表（技术读者）
- ✅ AI 项目必须突出 AI 能力（RAG / Embedding / LLM）
- ✅ 如技术栈选择 Higress，发版说明必须体现 Higress 的 K8s 入口网关、微服务网关、AI 网关三合一能力
- ✅ 技术栈表必须读取 S6 产物；不得硬编码 React、Vue 或任何固定前端栈
- ✅ 功能亮点和变更列表必须关联 OpenSpec change / capability
- ✅ 已发布能力必须追溯 OpenSpec requirement / scenario
- ✅ 必须包含部署 / 升级指南
- ✅ 必须包含已知问题（坦诚透明）
- ✅ 输出必须是中文 Markdown
- ❌ 不得遗漏重大变更
- ❌ 不得发布 OpenSpec 未记录或未验证的能力
- ❌ 不得包含未验证的功能
- ❌ 不得跳过已知问题

## 使用示例

```
加载 <skill id="S29">，输入：docs/skill-chain/CHANGELOG.md、openspec/changes/
请生成 v1.0.0 的发版说明，包含 AI 功能亮点和升级指南。
```
</skill>
<!-- end -->
