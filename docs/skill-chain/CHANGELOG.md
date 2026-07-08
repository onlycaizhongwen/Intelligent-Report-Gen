# 变更日志

本文档记录智能报告生成系统当前未发布版本的重要变更，遵循 Keep a Changelog 的结构。

## [未发布] - 2026-06-22

### 新增

- S08：新增安全设计，明确 Higress 承担 WAF、JWT/OIDC、AI Gateway、Prompt 安全过滤与可观测入口，关联 `REQ-AI-001`、`REQ-AUTH-001`。
- S09：新增 PostgreSQL 16 逻辑数据模型与 49 张表结构，覆盖报告、知识库、权限、审计、规则、导出与分享能力。
- S10：新增 Flyway 风格迁移脚本与回滚脚本，保持无物理外键约束。
- S11：新增覆盖 49 张表的种子数据。
- S12-S15：新增 Java 17 + Spring Boot 3 DDD 模块化单体骨架，使用 MyBatis Plus、RocketMQ、RBAC、审计与错误处理。
- S12-S15：新增 Python 3.11 + FastAPI AI 服务骨架，覆盖文档解析、RAG、PromptGuard、模型调用审计预留。
- S16-S19：新增 Vue 3 + TypeScript 前端控制台，覆盖工作台、报告生成、知识库、规则引擎、分享访问与审计页面。
- S20：新增 OpenSpec + API 契约校验报告。
- S21：新增 Java/Python 单元测试骨架，覆盖领域服务、权限切面、PromptGuard、RAG、文档解析和错误码。
- S22：新增 Playwright E2E 场景，覆盖报告生成、文档上传、SSE、分享过期、权限拒绝和审计查询。
- S23：新增代码审查报告。
- S24-S27：新增 Docker、Compose、CI、环境变量、Higress 路由和部署指南。
- S30：新增 Prometheus、Grafana、Loki、Promtail 监控配置。

### 安全

- 使用环境变量管理 `JWT_SECRET`、LLM API Key、数据库密码和 MinIO 密钥。
- 前端 Axios 使用 `withCredentials`，不把 Token 放入 LocalStorage。
- Python AI 服务保留 Prompt 注入检测和 JWT 上下文校验。
- Higress 路由配置保留 Prompt 安全、Token 限流和 AI Gateway 能力开关；WAF 改为非活动候选策略，避免本地开发网关在无法拉取 Wasm 插件时断连。
- Higress 外部路由不再直连 Python `/api/v1/chat` 或 `/api/v1/documents`，统一将 `/api/v1/**` 导入 Java 业务核心，避免绕过 RBAC、任务状态和审计闭环。

### 变更

- 技术栈以 S6 产物为准：Vue 3 + TypeScript、Java 17 + Spring Boot 3、MyBatis Plus、PostgreSQL 16、RocketMQ、MinIO、Milvus、OpenSearch、Higress。
- Docker 与 CI 严格读取 S6/S7 当前选型，不生成未冻结的旧模板技术。
- S20 上传链路由骨架升级为真实适配：Java 上传入口串联 MinIO 对象写入、PostgreSQL 元数据持久化和 RocketMQ 文档解析事件发布。
- RocketMQ 文档解析消息拆分物理 topic 与业务事件类型：物理 topic 使用 `document_parse_requested`，业务事件类型/tag 仍为 `document.parse.requested`。
- RocketMQ 报告生成、导出和 AI 完成事件同样使用下划线物理 topic，保留点分业务事件类型作为 tag。
- RocketMQ 生产者由每次发送临时创建调整为 Spring 托管生命周期，减少连接开销并提升可测试性。
- 本地 Docker 上传烟测已通过：复用现有 PostgreSQL、MinIO、RocketMQ。
- 2026-06-23 按用户确认补充最小资源 OpenSearch 与 Higress 本地容器，OpenSearch 已完成写读删烟测，Higress Console/Gateway 已可访问。
- 报告导出补充状态查询和下载 URL 返回，前端补充导出状态 API。
- Python AI 服务补充文档解析 RocketMQ envelope 处理器，支持 `document.parse.requested` 到解析结果事件的最小闭环。
- Playwright E2E 从场景文档升级为可执行配置，覆盖报告生成 SSE、文档上传、分享过期和权限拒绝。
- UC-08 rule runtime now has a real-backend browser smoke for published production execution, webhook action ledger rendering, and real callback idempotency evidence.
- UC-02 template filling now has a real-backend browser smoke and customer-visible template snapshot evidence on the report creation page.
- UC-02 template filling now continues from the created template task into `/reports/{taskId}/outline` and confirms the outline through the real Java backend before opening the generation stream.
- UC-02 template filling now has a local controlled-worker completion smoke that verifies completed report persistence, citation persistence, and model invocation audit without requiring an external LLM key.
- Higress local gateway route now has a repeatable smoke proving `/api/v1/**` reaches Java and `/api/v1/chat` is not directly exposed as Python chat.
- Higress local gateway smoke now verifies endpoint-level RBAC outcomes through `http://127.0.0.1:18000`: missing token `401`, insufficient permission `403`, and `permission:read` JWT `200`.
- UC-02 template browser acceptance now has a P0 smoke step that runs the same template filling and outline confirmation flow through Higress `http://127.0.0.1:18000`.
- UC-11 user/RBAC browser acceptance now has a P2 smoke step that runs batch import, account disable, and disabled-account management-page rejection through Higress.
- UC-04 export browser acceptance now has a P0 smoke step `uc04-higress-export-e2e` that runs PDF/PPTX export through Higress, verifies real MinIO artifact download, and keeps the report detail page on a controlled download link.
- UC-04 enterprise export template governance now has a web console management page and P0 smoke step `uc04-enterprise-export-template-higress-e2e`, covering create/update/enable/disable/version-history through Higress.
- UC-04 report detail export can now select a managed enterprise export template and send only `templateId`, allowing the Java backend to resolve the governed `brandSnapshot` instead of relying on inline brand fields.
- UC-04 managed-template export is now verified through Higress against the real Java backend and MinIO; the full real export spec passes PDF, PPTX, and managed-template PDF paths.
- UC-08 webhook failure compensation is now browser-visible and Higress-verified: failed production runs refresh the action ledger, show the concrete webhook error, and support batch ignore from `/rules`.
- UC-08 webhook retry compensation is now browser-visible and Higress-verified: a failed webhook can be retried from `/rules`, the retry reuses the original idempotency key, and the returned ledger preserves `sourceActionExecutionId`.
- UC-08 webhook automatic replay exhaustion is now browser-visible and Higress-verified: the scheduled replay worker can exhaust a repeatedly failing webhook, preserve the original source action trace, and stop duplicate terminal compensation records.
- Higress local WAF policy is now stored as a non-active candidate manifest. The active local route omits `higress.io/enable-waf` until the WAF plugin image can be mirrored/preloaded and verified with the opt-in blocking smoke.
- Higress OIDC local test-IdP smoke now proves `RS256`/JWKS authentication through the local gateway with accepted, wrong-issuer, and wrong-audience probes while keeping the default local `HS256` smoke unchanged.
- Delivery readiness audit now runs the local Higress OIDC test-IdP smoke as a required local gate while keeping customer production OIDC as a separate blocked gate when IdP settings are absent.
- Higress trusted TLS smoke now supports `HIGRESS_TLS_CA_FILE` for customer/private CA bundles while keeping certificate verification enabled.
- Higress production OIDC smoke now accepts a customer pre-signed token suite (`HIGRESS_OIDC_ACCEPTED_TOKEN`, `HIGRESS_OIDC_WRONG_ISSUER_TOKEN`, `HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN`) so customers do not need to provide private signing keys for readiness validation.
- 规则引擎 webhook 手动重试与自动重放边界补强：当 `maxAsyncReplayAttempts=0` 时，自动重放 worker 和 due-action 查询都会跳过该动作，避免后台自动重放与浏览器手动重试竞争生成重复成功记录。
- Delivery readiness audit adds `higress-waf-runtime-preflight`, which checks WAF plugin OCI reachability from the Higress runtime container before the blocking policy is enabled.
- Higress WAF runtime preflight now supports `HIGRESS_WAF_PLUGIN_URL`, allowing customer/private mirrored OCI plugin images to be probed without editing the candidate manifest; readiness reports the value as `<provided>` and preflight rejects URL userinfo with redaction.
- Delivery readiness audit now emits a structured `actionPlan` for non-passing production gates, including required inputs, rerun commands, next action, required evidence, and compact observed failure context.
- Delivery readiness audit can now render the production `actionPlan` as customer-readable Markdown through `DELIVERY_READINESS_OUTPUT=markdown`.
- Delivery readiness audit can now persist the JSON or Markdown report to `DELIVERY_READINESS_REPORT_FILE`, creating parent directories automatically.
- Delivery readiness action plans now distinguish optional inputs and alternative OIDC/TLS evidence paths, so customer handoff reports no longer imply private CA files or IdP signing keys are always mandatory.
- Delivery readiness command checks now have explicit timeouts, timeout evidence classification, and stderr progress markers so long smoke checks cannot leave customer handoff audits waiting silently.
- Delivery readiness Markdown now renders passed production gates, making credentialed P0-P3 delivery evidence visible even when WAF/TLS/OIDC gates remain open.
- Delivery readiness Markdown now renders compact evidence for passed production gates, so P0-P3 completion details are preserved in customer handoff reports.
- Delivery readiness audit can now write a customer-fillable production evidence `.env` template through `DELIVERY_READINESS_ENV_TEMPLATE_FILE`.
- Production readiness handoff now includes `scripts/production-readiness-env-check.mjs` so customers can precheck filled WAF/TLS/OIDC/model evidence inputs before running long readiness smokes.
- Delivery readiness audit now supports `DELIVERY_READINESS_ENV_FILE=<path>`, letting the full audit reuse the same customer-filled production evidence `.env` file after precheck.
- Production WAF and OIDC readiness inputs now require `HIGRESS_GATEWAY_BASE_URL`, preventing target-environment evidence from silently using the local Higress default.
- Production readiness action plans now render copyable commands with required env placeholders for WAF, TLS, OIDC, and credentialed smoke checks.
- Latest generated production readiness handoff artifacts are now covered by a regression test and regenerated with the current copyable command placeholders.
- Production readiness `.env` templates now include required inputs for all production evidence gates, including passed gates such as credentialed P0-P3 smoke, so customer target-environment reruns do not miss provider-key inputs.

### 已知问题

- OpenSearch 已完成本地最小资源部署，但知识全文索引业务链路仍需持续验收；Higress `/api/v1/** -> java-report-core` 本地路由和代表性 RBAC `401/403/200` 已有 smoke 证据；UC-08 webhook 失败补偿、手动重试补偿、自动重放耗尽均已有 Higress 浏览器验收证据；本地测试 IdP OIDC 已有 Higress 烟测证据；客户生产 OIDC 已支持预签 token 套件验收但仍需客户现场 token 证据；可信 TLS 仍待生产化验证；WAF 阻断需先通过 Higress runtime container 的 OCI 插件可达性预检。

[OK] Skill S28 completed
