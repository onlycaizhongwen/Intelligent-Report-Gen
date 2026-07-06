# 部署指南

## 一、环境准备

### 1.1 软件依赖

| 软件 | 版本 | 用途 |
| --- | --- | --- |
| Docker | 24+ | 本地容器运行 |
| Docker Compose | 2.20+ | 本地开发编排 |
| Java | 17 | Spring Boot 3 构建与运行 |
| Node.js | 20 | Vue 3 + TypeScript 构建 |
| Python | 3.11 | FastAPI AI 服务 |
| PostgreSQL | 16 | 主数据存储 |
| RocketMQ | 4.9.7（本地轻量）/ 5.x（生产可选） | 报告生成、文档解析、导出任务异步解耦 |
| MinIO | 2024.x | 文档、报告导出文件与快照存储 |
| Milvus | 2.4.x | 向量检索 |
| OpenSearch | 2.18.x | 全文检索 |
| Higress | 2.0.x | K8s 入口网关、微服务网关、AI 网关 |

### 1.2 当前范围约束

- 当前阶段不部署多租户、私有化部署、高可用、灾备或报告人工审核发布流程。
- 当前优先使用线上 GPT 模型，不启用本地 GPU/vLLM 推理。
- 所有外部入口统一经过 Higress；不额外引入自研轻量网关。

## 二、本地 Docker Compose 部署

### 2.1 配置环境变量

```bash
cp .env.example .env
```

必须填写：

| 变量 | 说明 |
| --- | --- |
| `DB_PASSWORD` | PostgreSQL 用户密码 |
| `JWT_SECRET` | Java 与 Python 服务共享的 JWT 校验密钥 |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | MinIO 管理账号 |
| `OPENSEARCH_INITIAL_ADMIN_PASSWORD` | OpenSearch 初始化密码 |
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` / `LLM_TIMEOUT_SECONDS` | 线上 GPT 兼容模型配置；未填写 `LLM_API_KEY` 时自动使用 `local-fallback` |

### 2.2 启动服务

```bash
docker compose up -d --build
```

如本地已存在可复用的 PostgreSQL、Redis、MinIO、RocketMQ、Milvus、etcd，仅需补充网关与全文检索，可使用最小化编排：

```bash
docker compose -f docker-compose.local-gateway-search.yml up -d
```

当前最小化编排会加入已有 `intelligent-report-infra_default` 网络，仅新增：

| 服务 | 本地端口 | 说明 |
| --- | --- | --- |
| Higress | `18000` HTTP、`18443` HTTPS、`18001` Console | 避开本地已占用的 `80` |
| OpenSearch | `9200` API、`9600` Performance Analyzer | 单节点，Security plugin 关闭，JVM 堆 `512m` |
| Java 服务 | `18080` | 映射容器内 `8080`，避免占用宿主机默认应用端口 |
| Python AI 服务 | `18081` | 映射容器内 `8000`，Python `/api/v1/chat` 仅作为内部或受控路由 |
| 前端控制台 | `13000` | 映射容器内 `80` |
| PostgreSQL | `15432` | 映射容器内 `5432` |
| Redis | `16379` | 映射容器内 `6379` |
| MinIO | `19000` API、`19001` Console | 映射容器内 `9000/9001` |
| OpenSearch（主编排） | `19200` | 映射容器内 `9200` |

### 2.3 查看状态

```bash
docker compose ps
docker compose logs -f java-report-core
docker compose logs -f python-ai-service
```

### 2.4 初始化数据

业务库结构由 Java 服务启动时通过 Flyway 自动迁移，迁移脚本位于 `backend/java-report-core/src/main/resources/db/migration`。不要在 PostgreSQL 容器启动阶段重复挂载旧版 SQL 初始化脚本，避免与 Flyway 版本表冲突。

如需导入演示种子数据：

```bash
docker compose exec -T postgres psql -U report_app -d report_db < seed.sql
```

## 三、Higress 路由

Higress 负责三类能力：

| 能力 | 覆盖范围 | OpenSpec |
| --- | --- | --- |
| K8s 入口网关 | `/api/v1/**` 南北向入口 | scope-guardrails |
| 微服务网关 | Java 业务核心与 Python AI 服务路由 | report-generation、knowledge-base-ingestion |
| AI 网关 | LLM 代理、Fallback、Token 限流、Prompt 安全、语义缓存、MCP 代理预留 | REQ-AI-001 |

完整 Compose 默认通过 `higress` 服务暴露 `18000/18443/15020`，可在 `.env` 中覆盖为生产或专用环境端口。本地最小化编排通过 `ir-higress` 暴露 `18000/18443/18001`，避免与本机 `80` 冲突。Kubernetes 可复用 `config/higress/routes.yaml`：

```bash
kubectl apply -f config/higress/routes.yaml -n report-system
```

外部业务 API 路由必须统一进入 `java-report-core`。Python AI 服务的 `/api/v1/chat`、文档解析、Embedding 等接口只作为内部服务或 RocketMQ 消费执行面，不在公开 Higress Ingress 中直连暴露，避免绕过 Java RBAC、报告任务状态和审计闭环。

本地 Higress all-in-one 使用 `config/higress/local-data` 下的文件型 Kubernetes 资源。当前已提供：

- `config/higress/local-data/ingresses/intelligent-report-routes.yaml`
- `config/higress/local-data/services/java-report-core.yaml`
- `config/higress/local-data/endpoints/java-report-core.yaml`

如果 Java 容器 IP 变化，需要同步更新 `endpoints/java-report-core.yaml`，或改为完整 Compose 同网络服务名方案。

## 四、健康检查

| 检查项 | 命令 | 预期 |
| --- | --- | --- |
| 前端 | `curl -I http://localhost:13000` | HTTP 200 |
| Java 服务 | `curl http://localhost:18080/actuator/health` | `UP` |
| Python AI 服务 | `curl http://localhost:18081/health` | `ok` |
| Higress 指标 | `curl http://localhost:15020/metrics` | Prometheus 指标 |
| 本地 Higress Console | `curl http://localhost:18001` | HTTP 200 |
| 本地 Higress Gateway | `curl http://localhost:18000` | HTTP 200 |
| Higress 业务路由 | `curl -i http://localhost:18000/api/v1/reports` | 未带 JWT 时返回 Java `401`，不能返回 Higress Console HTML |
| PostgreSQL | `docker compose exec postgres pg_isready -U report_app -d report_db` | accepting connections |
| OpenSearch | `curl http://localhost:19200` | cluster info |
| Milvus | `docker compose ps milvus` | running |

## 五、验证业务链路

| 场景 | 入口 | OpenSpec |
| --- | --- | --- |
| 创建报告任务 | `POST /api/v1/reports/generation-tasks` | REQ-REPORT-001 |
| 确认大纲 | `PUT /api/v1/reports/generation-tasks/{taskId}/outline` | REQ-REPORT-002 |
| 流式生成 | `GET /api/v1/reports/generation-tasks/{taskId}/stream` | REQ-AI-001 |
| 上传文档 | `POST /api/v1/documents/upload` | REQ-KB-002 |
| 导出报告 | `POST /api/v1/reports/{reportId}/exports` | REQ-REPORT-004 |
| 分享访问 | `POST /api/v1/share-links/{shareToken}/access` | REQ-COLLAB-001 |
| 审计查询 | `GET /api/v1/audit-logs` | REQ-AUDIT-001 |

### 5.1 线上 GPT 兼容模型配置

Python AI 服务和 `report-generation-worker` 共用以下环境变量：

| 变量 | 示例 | 说明 |
| --- | --- | --- |
| `LLM_PROVIDER` | `openai-compatible` | 当前支持 OpenAI-compatible `/chat/completions` 协议 |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | 兼容服务根地址，不包含 `/chat/completions` |
| `LLM_API_KEY` | `sk-...` | 线上模型密钥；为空时不会外呼 |
| `LLM_MODEL` | `gpt-4.1-mini` | 模型名称 |
| `LLM_TIMEOUT_SECONDS` | `30` | 单次模型调用超时时间 |

未配置 `LLM_API_KEY`、provider 非兼容类型或外部调用失败时，报告生成会明确写入 `provider=local-fallback`、`model=local-rag-fallback`、`fallbackUsed=true`，并继续保留引用和模型调用审计字段。配置真实 Key 后，应重新执行一次报告生成链路，并在 Java `model_invocations/model_responses` 中验证真实 provider/model/token 记录。

### 5.2 UC-01 真实 provider 本地 smoke

如需先把本地 `ir-report-generation-worker-smoke` 切换到真实 DashScope OpenAI-compatible provider，可直接执行：

```bash
UC01_REAL_PROVIDER_API_KEY=$DASHSCOPE_API_KEY node scripts/uc01-real-provider-worker.mjs
```

该脚本会：

- 删除已有 `ir-report-generation-worker-smoke` 容器；
- 以 `openai-compatible + qwen-plus + DashScope compatible-mode` 重新启动 worker；
- 继续复用 `intelligent-report-infra_default` 网络、`ir-java-smoke`、`ir-postgres`、`ir-opensearch`、`ir-milvus` 和现有 RocketMQ namesrv；
- 输出新容器 ID、consumer group 和 provider 关键配置，便于复核。

当本地 `ir-java-smoke`、`ir-report-generation-worker-smoke`、PostgreSQL、RocketMQ、OpenSearch、Milvus 已运行，且宿主机已注入真实兼容模型密钥时，可直接复用仓库内 smoke 脚本触发一条 UC-01 报告生成任务：

```bash
node scripts/uc01-real-provider-smoke.mjs
```

默认约定：

- `UC01_SMOKE_BASE_URL=http://127.0.0.1:18082/api/v1`
- `UC01_SMOKE_JWT_SECRET=local-dev-secret-change-me-32-bytes-minimum`
- `UC01_SMOKE_SUB=1`

可按需覆盖：

```bash
UC01_SMOKE_BASE_URL=http://127.0.0.1:18082/api/v1 \
UC01_SMOKE_JWT_SECRET=local-dev-secret-change-me-32-bytes-minimum \
UC01_SMOKE_SUB=1 \
node scripts/uc01-real-provider-smoke.mjs
```

脚本会：

- 使用共享 `JWT_SECRET` 生成本地 smoke JWT；
- 调用 `POST /api/v1/reports/generation-tasks` 创建报告任务；
- 调用 `PUT /api/v1/reports/generation-tasks/{taskId}/outline` 确认大纲；
- 轮询 `GET /api/v1/reports/{reportId}` 直到任务进入 `completed/failed`；
- 输出 `taskId/reportId/reportSummary`，供后续查询容器日志与数据库证据。

如需同时读取 PostgreSQL 模型调用审计专表，可额外指定本地容器名：

```bash
UC01_SMOKE_POSTGRES_CONTAINER=ir-postgres node scripts/uc01-real-provider-smoke.mjs
```

如需把这条 smoke 当作“必须命中真实外部 provider”的严格门禁，可再打开 strict 模式：

```bash
UC01_SMOKE_POSTGRES_CONTAINER=ir-postgres \
UC01_SMOKE_STRICT_AUDIT=true \
node scripts/uc01-real-provider-smoke.mjs
```

可选变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `UC01_SMOKE_POLL_ATTEMPTS` | `20` | 报告终态轮询次数 |
| `UC01_SMOKE_POLL_INTERVAL_MS` | `1000` | 每次轮询间隔毫秒数 |
| `UC01_SMOKE_POSTGRES_CONTAINER` | 空 | 指定后会执行 `docker exec <container> psql ...` 查询 `model_invocations` |
| `UC01_SMOKE_POSTGRES_USER` | `report` | PostgreSQL 用户 |
| `UC01_SMOKE_POSTGRES_DB` | `intelligent_report` | PostgreSQL 数据库名 |
| `UC01_SMOKE_STRICT_AUDIT` | `false` | 开启后要求 `model_invocations` 存在，且 `provider != local-fallback`、`status = succeeded` |

若要走真实外部 GPT provider，而不是 `local-fallback`，需确保 `ir-report-generation-worker-smoke` 或当前消费 worker 以如下环境运行：

- `LLM_PROVIDER=openai-compatible`
- `LLM_BASE_URL=<真实兼容基址>`
- `LLM_API_KEY=<真实模型密钥>`
- `LLM_MODEL=<真实模型名>`

当 `UC01_SMOKE_POSTGRES_CONTAINER=ir-postgres` 且 worker 已配置真实 provider 时，脚本输出中会额外包含 `modelInvocation.provider/modelName/status/totalTokens/traceId`。若再启用 `UC01_SMOKE_STRICT_AUDIT=true`，脚本会把“查不到审计证据”“仍走 `local-fallback`”或“审计状态不是 `succeeded`”直接判为失败，可直接作为 UC-01 P0 回归门禁。

推荐本地一键闭环顺序：

```bash
UC01_REAL_PROVIDER_API_KEY=$DASHSCOPE_API_KEY node scripts/uc01-real-provider-worker.mjs
UC01_SMOKE_POSTGRES_CONTAINER=ir-postgres UC01_SMOKE_STRICT_AUDIT=true node scripts/uc01-real-provider-smoke.mjs
```

如果希望把当前最关键的 P0 链路一次跑完，可直接执行仓库内本地 smoke 套餐：

```bash
P0_SMOKE_DASHSCOPE_API_KEY=$DASHSCOPE_API_KEY node scripts/p0-local-smoke.mjs
```

当前套餐顺序固定为：

1. 重建 `ir-report-generation-worker-smoke` 为真实 DashScope provider
2. 执行 UC-01 strict smoke，要求 `model_invocations` 命中真实 provider
3. 执行 UC-06 `knowledge-upload-real-backend.spec.ts`，验证浏览器上传扫描 PDF 后回写 `processed`
4. 执行 UC-04 `report-export-real-backend.spec.ts`，验证浏览器基于真实后端导出 PDF 并收到成功反馈

若脚本成功，输出会包含每一步的 `stdout/stderr` 与完成步骤列表，可直接作为当前本地 P0 回归结果归档。

如果希望把“客户验收视角”的引用、分享、审计三条真实后端链路一次跑完，可直接执行仓库内 P1 smoke 套餐：

```bash
node scripts/p1-local-smoke.mjs
```

如果希望把当前仓库已沉淀的 `P0 + P1 + P2 + P3` 一次顺序跑完，作为“本地客户交付前总回归”入口，可直接执行：

```bash
DELIVERY_SMOKE_DASHSCOPE_API_KEY=$DASHSCOPE_API_KEY node scripts/delivery-local-smoke.mjs
```

当前总套餐顺序固定为：

1. `P0`: 生成 / 上传 / 导出
2. `P1`: 引用 / 分享 / 审计
3. `P2`: 协作 / RBAC / 版本回滚
4. `P3`: 工作台 / 数据源同步 / 审批待办

可按需覆盖：

- `DELIVERY_SMOKE_REAL_BACKEND_API_BASE_URL`
- `DELIVERY_SMOKE_REAL_BACKEND_ORIGIN`
- `DELIVERY_SMOKE_POSTGRES_CONTAINER`

脚本成功后会输出统一 JSON：

- `completedSteps`
- `results`
- `plannedSteps`

可直接作为当前一轮本地交付回归归档证据。

当前套餐顺序固定为：

1. 执行 UC-03 `report-citation-real-backend.spec.ts`，验证浏览器可从真实报告详情点击引用并查看来源快照评分
2. 执行 UC-09 `share-real-backend.spec.ts`，验证外部用户通过分享密码只读访问报告并获取受控下载 URL
3. 执行 UC-12 `audit-real-backend.spec.ts`，验证个人历史按当前用户隔离、全局审计要求管理员权限

若脚本成功，输出会包含每一步的 `stdout/stderr` 与完成步骤列表，可直接作为当前本地 P1 回归结果归档。

如果希望把“协作与治理”相关的批注、RBAC、版本回滚三条真实后端链路一次跑完，可直接执行仓库内 P2 smoke 套餐：

```bash
node scripts/p2-local-smoke.mjs
```

当前套餐顺序固定为：

1. 执行 UC-10 `report-collaboration-real-backend.spec.ts`，验证浏览器提交正文选区批注后真实写入协作表
2. 执行 UC-11 `user-rbac-real-backend.spec.ts`，验证批量导入用户并阻止禁用账号访问管理页
3. 执行 UC-14 `report-version-real-backend.spec.ts`，验证浏览器连接真实后端查看差异并回滚版本

若脚本成功，输出会包含每一步的 `stdout/stderr` 与完成步骤列表，可直接作为当前本地 P2 回归结果归档。

如果希望把“运营与治理”相关的工作台、数据源同步、审批待办三条真实后端浏览器链路一次跑完，可直接执行仓库内 P3 smoke 套餐：

```bash
node scripts/p3-local-smoke.mjs
```

当前套餐顺序固定为：

1. 执行 UC-13 `dashboard-real-backend.spec.ts`，验证浏览器连接真实 Java API、PostgreSQL 与 JWT 后，工作台指标、趋势和最近活动会随真实业务数据变化
2. 执行 UC-07 `data-source-sync-real-backend.spec.ts`，验证浏览器通过真实后端配置 API 数据源、触发同步，并在同步日志中看到 `manual/succeeded/2/sync completed`
3. 执行 UC-08 `approval-inbox-real-backend.spec.ts`，验证浏览器通过真实后端查看审批待办、批准目标记录，并在 `Approved` 视图看到审批意见

若脚本成功，输出会包含每一步的 `stdout/stderr` 与完成步骤列表，可直接作为当前本地 P3 回归结果归档。

## 六、回滚方案

### 6.1 应用回滚

```bash
docker compose pull
docker compose up -d --no-build java-report-core python-ai-service web-console
```

Kubernetes 环境：

```bash
kubectl rollout undo deployment/java-report-core -n report-system
kubectl rollout undo deployment/python-ai-service -n report-system
kubectl rollout undo deployment/web-console -n report-system
```

### 6.2 数据库回滚

执行前必须先备份当前数据库：

```bash
docker compose exec postgres pg_dump -U report_app report_db > backup-before-rollback.sql
```

数据库结构由 Flyway 管理。生产回滚必须提供与当前版本匹配的显式回滚脚本或执行前一版本镜像的兼容迁移方案，禁止直接在容器启动目录挂载旧版初始化 SQL 覆盖现有库。

## 七、已知部署限制

- 当前 Compose 仅用于开发和小规模验证，不声称高可用。
- 当前未包含灾备、跨地域容灾、私有化交付和 GPU 推理。
- 本地上传集成烟测会写入 MinIO、PostgreSQL 并向 RocketMQ 发送消息；文档解析业务事件类型为 `document.parse.requested`，RocketMQ 物理 topic 使用 `document_parse_requested`。
- RocketMQ 本地开发使用 4.9.7 + 小 JVM 参数。Broker 必须通过 `brokerIP1=rocketmq-broker` 注册容器网络地址，禁止注册 `127.0.0.1:10911`，否则 Java 容器无法发送消息。
- 当前本地 Docker 已发现 PostgreSQL、Redis、MinIO、Milvus、etcd 可复用；RocketMQ 已切换为当前 Compose 的轻量配置；OpenSearch 与 Higress 已通过最小资源策略补部署。OpenSearch 已完成写读删烟测；Higress `/api/v1/** -> java-report-core` 已完成业务路由烟测，Python `/api/v1/chat` 未公开直连。

[OK] Skill S27 completed
