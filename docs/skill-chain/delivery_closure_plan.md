# 智能报告生成系统可交付闭环实施计划

> 输入基线：`docs/skill-chain/requirements.md`、`docs/skill-chain/user_journey.md`、`docs/skill-chain/prototype_requirement_acceptance_matrix.md`、当前 Java/Python/Vue 工程取证。  
> 目标：从原型与需求出发，把当前“全栈骨架”推进到可演示、可验证、可交付的业务闭环版本。  
> 停止条件：UC-01 到 UC-14 均有真实持久化、权限、审计或明确降级提示；关键链路可通过 API、E2E、Docker 烟测复现。

## 1. 当前判断

| 维度 | 当前状态 | 判断 |
| --- | --- | --- |
| 工程骨架 | Java DDD 目录、Python AI 服务目录、Vue 3 + TypeScript 页面和 Playwright 测试均已存在 | 可以进入闭环开发，不需要重建工程 |
| 基础设施 | PostgreSQL、Redis、RocketMQ、MinIO、Milvus、OpenSearch、Higress 已在本地 Docker 运行 | 具备真实联调条件 |
| 文档基线 | S1/S2/验收矩阵已重建，OpenSpec baseline 已修复为可读中文 | 可以作为需求与验收来源 |
| 业务能力 | 上传解析、RAG 检索、报告生成 worker、模板填报字段 schema、引用来源快照评分、Markdown/DOCX/PDF/PPTX 导出、DOCX/PPTX 真实 Logo 对象读取与嵌入、版本差异/回滚、模型审计专表、分享访问安全、外部只读分享详情、分享下载授权、规则发布审批、规则生产运行审计、规则运行历史、规则 aggregate/branch 节点、规则 notify action 未读告警、规则 create_task action 协作任务与前端配置、规则 webhook action 外部 HTTP 回写、幂等键、HMAC 签名、同步重试退避、失败告警补偿、重复未读告警降噪、持久动作台账、动作台账分页查询、规则页面动作台账管理 UI、补偿运营摘要、Webhook 节点完整配置表单、规则节点新增/删除保存、规则节点字段级配置、规则连线新增/删除保存、最小人工重放 API、批量补偿操作、Webhook 异步重放 worker、异步补偿次数上限和 worker Micrometer 指标、规则最小可拖拽画布与节点位置持久化、顺序多级审批闸门已形成多条真实闭环；高级企业版式、OCR/表格识别、独立人工审批流编排、子流程与更高级的规则可视化能力仍未完全闭合 | 尚不能称为完整客户生产版本 |

## 2. 交付主线

### 主线 A：报告生成闭环

覆盖：UC-01、UC-02、UC-03、UC-14；REQ-REPORT-001、REQ-REPORT-002、REQ-REPORT-003、REQ-REPORT-005、REQ-AI-001。

必须实现：

- `report_generation_tasks` 真实状态机：`pending`、`outline_ready`、`running`、`completed`、`failed`、`retryable`。
- 自然语言与模板填报都写入任务、参数快照、创建人、traceId。
- 模板填报必须先读取 `report_templates` active 模板，按字段 schema 校验必填、枚举和数字类型，任务快照必须包含模板 ID、名称、版本、字段定义和参数。
- 大纲生成与确认持久化，确认后才能进入正文生成。
- Java SSE 从任务状态和生成片段读取，不再发送固定示例文本。
- 生成完成后保存报告正文、段落、结构化引用标记、引用来源快照、版本记录、模型调用审计。
- 失败时保存失败原因并支持重试。

验收证据：

- API：创建任务 -> 查看大纲 -> 确认大纲 -> SSE 流式生成 -> 查询报告详情。
- DB：任务、报告、版本、引用、模型调用审计均有记录。
- E2E：按原型输入自然语言或模板字段后能看到真实状态变化、正文、引用和版本。

### 主线 B：知识入库与检索闭环

覆盖：UC-05、UC-06；REQ-KB-001、REQ-KB-002。

必须实现：

- 知识库列表、知识条目搜索、新增、删除改为 PostgreSQL 真实数据。
- 删除被引用条目时基于报告章节结构化引用检查 `knowledgeItemId` 并要求确认。
- Python 消费 `document.parse.requested` 后生成解析结果、片段、索引状态和失败原因。
- 文档分块写 PostgreSQL，向量写 Milvus，全文索引写 OpenSearch。
- OCR、表格识别、扫描件处理先实现可插拔策略；本地无重型 OCR 时必须给出明确降级状态，不得假装成功。

验收证据：

- 上传 TXT/CSV/PDF 样例后，状态从 `pending` 变为 `processed` 或带原因的 `failed`。
- 上传内容可在知识条目搜索中检索到。
- OpenSearch 查询命中全文片段，Milvus 写入有可检查记录或 mock 明确隔离在测试 profile。

### 主线 C：RAG + GPT 生成链

覆盖：UC-01、UC-03；REQ-AI-001、REQ-REPORT-003。

必须实现：

- Java 报告任务通过 RocketMQ 或内部受控调用触发 Python RAG/生成。
- Python 从 PostgreSQL/OpenSearch/Milvus 检索真实知识片段。
- 线上 GPT 兼容模型调用通过配置注入 API Key；无 Key 或外部调用失败时走明确的本地降级，并在响应中标记 `provider=local-fallback`、`fallbackUsed=true`。
- 生成结果返回引用来源、来源快照、证据评分、文本锚点、traceId、模型调用记录。
- Prompt、上下文摘要、参数、响应摘要、耗时、状态写审计。

验收证据：

- 报告正文中的引用能点击查看来源快照、可信度、引用质量和文本锚点。
- 关闭 API Key 时显示开发降级来源；配置 API Key 后调用真实 provider。
- 模型调用审计可按 traceId 查到。

### 主线 D：导出与下载闭环

覆盖：UC-04；REQ-REPORT-004。

必须实现：

- 导出基于当前报告版本生成真实文件。
- 报告回滚必须复制历史版本快照生成新的当前版本，并记录 `report_version_rollback` 审计，禁止只切换历史版本 current 标记。
- 报告版本差异必须基于历史版本 snapshot 计算新增、删除、修改和未变段落，前端回滚前必须可查看差异。
- Markdown 必须优先闭环；PDF/Word/PPT 至少实现一种真实格式，其余格式给出明确支持矩阵和失败提示。
- 企业模板配置必须校验 Logo、页眉页脚、目录、字体、颜色等品牌规范字段。
- 导出文件写 MinIO，返回短期下载 URL。
- 导出请求、完成、失败和下载访问写审计。

验收证据：

- 点击导出后可下载真实文件。
- 文件包含报告标题、目录或结构、正文、引用、企业模板字段。
- 缺模板时返回错误，不生成伪文件。

### 主线 E：权限、分享、协作闭环

覆盖：UC-09、UC-10、UC-11；REQ-AUTH-001、REQ-COLLAB-001、REQ-COLLAB-002。

必须实现：

- RBAC 权限矩阵在后端生效，前端仅作为展示控制。
- Access Token 和 `/auth/me` 必须携带账号状态，后端权限切面必须拒绝 disabled 账号，避免旧 token 绕过账号停用。
- 用户、角色、账号状态、部门持久化。
- 分享链接保存密码哈希、有效期、撤销状态、授权范围。
- 外部访问必须校验密码、有效期、撤销状态和报告授权。
- 外部只读报告详情必须通过 share token 反查授权报告，不得暴露 owner、密码、密码哈希或内部编辑接口。
- 外部下载默认关闭，只有 `allowDownload=true` 的分享链接才能通过受控分享下载接口签发短期 URL。
- 批注绑定报告段落或文本 offset，任务指派校验被指派人访问权限。
- 当前增量已在后端强制 `sectionId/startOffset/endOffset/selectedText` 文本锚点、启用用户指派校验、任务状态白名单和站内通知落库；下一步补报告页面选中文本批注 UI 与 Playwright E2E。
- 站内通知写入通知表并支持未读状态。

验收证据：

- 禁用用户无法继续访问受保护 API。
- 错误密码、过期分享、撤销分享均拒绝访问并记录日志。
- 无权限用户不能被指派到无法访问的报告任务。

### 主线 F：审计、历史与工作台闭环

覆盖：UC-12、UC-13；REQ-AUDIT-001、REQ-DASH-001。

必须实现：

- 创建报告、确认大纲、生成完成、上传、删除、导出、分享、访问分享、批注、任务状态变更、规则提交审核、规则审批发布、规则生产运行、数据源配置均写审计。
- 普通用户只能查看个人历史，管理员可以查看全局审计。
- 工作台指标从真实表聚合，不再返回固定样例。
- 指标口径要写入文档：报告产出、知识条目、活跃数据源、引用命中率、活跃用户、趋势、排行、最近活动。

验收证据：

- 执行主链操作后审计页面立即可查。
- 不同角色访问历史和工作台返回不同授权范围。
- Dashboard 指标随真实操作变化。

### 主线 G：规则引擎与数据源闭环

覆盖：UC-07、UC-08；REQ-KB-003、REQ-RULE-001。

必须实现：

- 数据源配置、测试连接、同步策略、同步日志持久化。
- PostgreSQL/MySQL/API 连接器先闭环；ERP/OA/财务系统以适配器接口加示例配置承接，不伪造真实连接。
- 规则节点 schema、画布、连线、版本、执行记录持久化。
- 连线合法性校验与调试日志写入。

验收证据：

- 错误数据源连接返回真实失败原因。
- 规则画布保存后刷新仍可恢复。
- 单步调试产生节点日志。

## 3. 推荐实施批次

| 批次 | 目标 | 包含 UC | 原因 |
| --- | --- | --- | --- |
| Batch 1 | 报告任务状态机、持久化、大纲确认、SSE | UC-01, UC-02, UC-14 | 所有核心旅程的主干 |
| Batch 2 | 知识条目、上传解析状态、OpenSearch/Milvus 索引 | UC-05, UC-06 | RAG 和引用依赖真实知识 |
| Batch 3 | RAG + GPT 生成、引用锚点、模型审计 | UC-01, UC-03, UC-12 | AI 价值闭环 |
| Batch 4 | 真实导出、MinIO 下载、导出审计 | UC-04 | 用户最终交付物 |
| Batch 5 | RBAC、分享、批注、任务、通知 | UC-09, UC-10, UC-11 | 安全协作边界 |
| Batch 6 | Dashboard、审计聚合、数据源、规则调试 | UC-07, UC-08, UC-13 | 管理与扩展能力 |

## 4. 测试策略

| 层级 | 必测内容 |
| --- | --- |
| Java 单元/集成测试 | 报告状态机、权限切面、上传编排、导出、分享校验、审计写入 |
| Python 单元/集成测试 | 文档解析、RAG 检索、GPT provider、RocketMQ envelope、引用评分 |
| 前端 E2E | UC-01 报告生成、UC-06 上传解析、UC-09 分享访问、UC-12 审计查询 |
| Docker 烟测 | PostgreSQL、RocketMQ、MinIO、Milvus、OpenSearch、Higress 全链路可达 |
| 契约校验 | API JSON 请求/响应、SSE schema、错误码、OpenSpec traceability |

## 5. 当前优先修复清单

| 优先级 | 问题 | 建议动作 |
| --- | --- | --- |
| P0 | Java/Python 源码中仍有中文乱码字符串 | 关键 Java 报告链路已按 UTF-8 复核；后续逐模块修复，不做批量编码转换 |
| P0 | `ReportApplicationService` 固定 taskId、固定 SSE、固定报告内容 | 已完成任务表读写、报告 draft 绑定、大纲确认状态机、报告正文、版本、结构化引用和真实 PostgreSQL 仓储验证 |
| P0 | `RagService.retrieve` 返回固定 chunk | 已接入真实 OpenSearch/Milvus 检索与本地 fallback；OpenAI-compatible provider 已完成代码闭环，仍需配置真实 API Key 后做外部烟测 |
| P1 | `PermissionApplicationService.accessShare` 固定放行 | 已实现密码哈希、有效期、撤销、访问审计、外部只读报告详情和显式下载授权；分享页已补 Playwright 覆盖过期、密码错误、只读详情、允许下载和拒绝下载路径；仍需真实后端环境 E2E |
| P1 | 导出版式不完整 | Markdown、DOCX、PDF 与 PPTX 已生成真实文件并写 MinIO；DOCX/PPTX 已优先从 MinIO 读取真实 SVG/PNG/JPEG Logo 二进制并嵌入 Office 媒体部件，读取不到时回退基础 SVG；PDF 已包含基础矢量 Logo 标记和品牌色绘制；仍需高级 Word/PDF/PPT 版式、PDF 真实 Logo 二进制嵌入和统一企业模板治理 |
| P2 | Dashboard、规则、数据源仍需生产化 | Dashboard 已接入 PostgreSQL 真实聚合；规则已补节点 schema、连线校验、condition/aggregate/branch 调试执行、notify action 未读系统告警、create_task action 协作任务与前端配置、webhook action 外部 HTTP 回写、`Idempotency-Key`、HMAC 签名、同步重试退避、失败告警补偿、重复未读告警 `dedupeKey` 降噪、持久动作台账、动作台账分页查询 API、规则页面动作台账列表/过滤/人工重放 UI、补偿运营摘要、Webhook 节点 endpoint/method/retry/backoff/async replay/signature/headers/body 配置表单、规则节点新增/删除保存、condition/branch/aggregate 字段级节点配置、规则连线新增/删除保存、最小人工重放 API、批量补偿 retry/ignore API 与 UI、Webhook 异步重放 worker、动作级租约、异步补偿次数上限、worker Micrometer 扫描/结果/耗时指标、规则 metrics 仓储/SQL 全量聚合、debug trace 落库和前端 E2E；数据源已补保存、AES-GCM 凭据加密、真实 PostgreSQL JDBC 连接探测与抽取、真实 MySQL 容器连接探测与增量抽取、HTTP API 连接器、高级请求配置、字段映射、按 `cursorColumn` 推进的增量游标、数据库同步租约、请求级超时分类、最大失败重试次数、人工重跑恢复、手动/定时同步入库、同步日志、失败告警、失败原因、配置/测试/同步审计和前端 API/调度/重试配置入口；后续继续补独立人工审批节点、子流程节点、拖拽式完整画布编辑器和超大规模指标预聚合 |

## 28. 2026-06-24 UC-05 知识条目删除引用保护增量

本轮目标：补齐知识条目删除时的真实引用关系校验，避免把“已索引”误判为“已被报告引用”，也避免删除仍被报告正文引用的知识条目。

已完成：

- `KnowledgeBaseRepository` 新增 `countReportReferences(itemId)`。
- `JdbcKnowledgeBaseRepository` 从 `report_sections.citation_marks` JSONB 数组中统计 `knowledgeItemId`，只统计未删除报告章节。
- `KnowledgeApplicationService.deleteItem()` 改为基于真实引用计数判断是否需要二次确认，并在响应中返回 `referenceCount`。
- `knowledgeApi.deleteItem()` 和 `KnowledgeList.vue` 接入删除接口，首次删除被引用条目时展示业务冲突提示，用户点击“确认删除”后带 `confirmed=true` 再次请求。
- 应用层测试覆盖被引用条目未确认时抛出业务冲突、确认后软删并返回引用数量。
- PostgreSQL 集成测试通过真实 `JdbcReportContentRepository.saveCompletedVersion()` 写入结构化 citation，再验证删除保护与软删路径。
- 确认删除成功后发布 `knowledge.item.deleted` 事件，payload 包含 `knowledgeItemId/knowledgeBaseId/title/referenceCount/deletedBy`，作为 Python/OpenSearch/Milvus 索引清理 worker 的稳定消费契约。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#requiresConfirmationWhenKnowledgeItemIsReferencedByReportCitation" test` 先因 JDBC 仓储未实现 `countReportReferences` 编译失败。
- GREEN：同用例通过 1 个测试。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest" test` 通过 8 个测试。
- 事件契约回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,RocketMqDomainEventPublisherTest" test` 通过 12 个测试。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true POSTGRES_IT_JDBC_URL=jdbc:postgresql://localhost:5432/intelligent_report POSTGRES_IT_USERNAME=report POSTGRES_IT_PASSWORD=report123 .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#blocksKnowledgeItemDeleteWhenReferencedByReportCitationInPostgres" test` 通过 1 个真实 PostgreSQL 测试。
- 前端：`npm run test -- apiContracts` 通过 13 个测试；`npm run typecheck` 通过；`npm run e2e -- knowledge-management.spec.ts` 通过 1 个 Chromium E2E。

后续闭合：

- 当时遗留的批量导入、真实索引清理和自动索引缺口已在第 29、30、31 节继续闭合，并补充真实 Docker 三端烟测证据。

## 29. 2026-06-24 UC-05 知识索引清理 worker 增量

本轮目标：承接 Java `knowledge.item.deleted` 事件，把知识条目删除后的索引生命周期从“事件契约”推进到 Python worker 代码闭环。

已完成：

- 新增 `KnowledgeIndexCleanupService`，消费 `knowledge.item.deleted` 后按 `knowledgeItemId` 查询 `document_chunks/embeddings`。
- 清理流程会删除 `embeddings` 记录、软删 `document_chunks.deleted_at`，并按 chunk 主键删除 OpenSearch `knowledge_entries_text` 文档和 Milvus `knowledge_chunks` 向量。
- `OpenSearchIndex` 新增 `delete_document()`，使用 `DELETE /{index}/_doc/{id}`，404 视为幂等成功。
- `MilvusVectorIndex` 新增 `delete_chunks()`，按 `chunk_id in [...]` 主键过滤删除。
- 新增 `RocketMqKnowledgeIndexCleanupSource` 和 `cleanup_worker_main`，本地 Compose 新增无端口暴露的 `knowledge-index-cleanup-worker`。
- Java `RocketMqProperties` 新增 `knowledgeItemDeletedTopic`，`RocketMqDomainEventPublisher` 将 `knowledge.item.deleted` 显式路由到 `ROCKETMQ_KNOWLEDGE_ITEM_DELETED_TOPIC`。

验证证据：

- RED：`python -m pytest tests/unit/python/test_knowledge_index_cleanup.py -q` 先因缺少 `KnowledgeIndexCleanupService` 失败。
- GREEN：`python -m pytest tests/unit/python/test_knowledge_index_cleanup.py -q` 通过 2 个测试。
- RED：`python -m pytest tests/unit/python/test_vector_index.py -q` 先因缺少 `delete_chunks()` 失败。
- GREEN：`python -m pytest tests/unit/python/test_vector_index.py -q` 通过 2 个测试。
- RED：`python -m pytest tests/unit/python/test_rocketmq_worker_adapter.py -q` 先因缺少 `RocketMqKnowledgeIndexCleanupSource` 失败。
- GREEN：`python -m pytest tests/unit/python/test_rocketmq_worker_adapter.py -q` 通过 4 个测试。
- RED：`python -m pytest tests/unit/python/test_docker_compose_contract.py -q` 先因 Compose 缺少 `knowledge-index-cleanup-worker` 失败。
- GREEN：`python -m pytest tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_knowledge_index_cleanup.py tests/unit/python/test_vector_index.py tests/unit/python/test_rocketmq_worker_adapter.py tests/unit/python/test_docker_compose_contract.py -q` 通过 15 个测试。
- 回归：`python -m pytest backend/python-ai-service/tests/test_ai_service_runtime.py tests/unit/python/test_report_generation_worker.py tests/unit/python/test_indexed_rag_retriever.py -q` 通过 10 个测试。
- Java：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RocketMqDomainEventPublisherTest,KnowledgeApplicationServiceTest" test` 通过 13 个测试。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。
- 真实 Docker 三端清理烟测：使用当前源码挂载进 `intelligent-report-system-python-ai-service:latest`，连接 `intelligent-report-infra_default` 网络里的 `postgres/opensearch/milvus`，准备 `knowledge_item_id` 对应的 `document_chunks/embeddings`、OpenSearch `_doc` 和 Milvus `knowledge_chunks` 向量后调用 `KnowledgeIndexCleanupService.handle()`；输出 `chunks=1/searchIndexStatus=deleted/vectorIndexStatus=deleted/persistenceStatus=soft_deleted`，并验证 PostgreSQL `deleted_at` 非空、`embeddings=0`、OpenSearch `found=false`、Milvus 查询为空。

仍需继续：

- 当前文档解析链生成的是文档 chunk，未天然绑定手工知识条目；批量导入或手工知识索引需要写入 `knowledge_item_id`，才能完全复用这条清理链。
- 当前运行中的 `intelligent-report-system-python-ai-service:latest` 镜像未包含最新 cleanup service，烟测通过源码挂载验证；后续交付镜像前需要重建 Python 镜像并补镜像级启动验收。

## 30. 2026-06-24 UC-05 知识条目批量导入增量

本轮目标：补齐原型“知识条目管理”的批量导入能力，并修复知识库页面和 E2E 的可见中文乱码，避免客户验收时看到不可读文案。

已完成：

- `KnowledgeApplicationService.batchImportItems()` 支持按 `knowledgeBaseId` 批量导入知识条目。
- 同一知识库内按标题去重，同时识别本批重复标题；逐条返回 `imported/failed`，失败原因包括 `title_required/content_required/duplicate_title`。
- `KnowledgeController` 新增 `POST /api/v1/knowledge-items/batch-import`，继续受 `knowledge:manage` 权限保护。
- `knowledgeApi.batchImportItems()` 补齐前端类型和 S4 API 契约。
- `KnowledgeList.vue` 新增“批量导入知识条目”面板，支持逐行输入 `标题,内容,来源类型`，导入后刷新列表并展示成功/失败统计。
- `KnowledgeList.vue` 与 `knowledge-management.spec.ts` 可见中文文案恢复为 UTF-8 可读文本。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#batchImportsKnowledgeItemsAndReportsInvalidOrDuplicateRows" test` 先因缺少 `batchImportItems()` 编译失败。
- GREEN：同用例通过 1 个测试。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,RocketMqDomainEventPublisherTest" test` 通过 14 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 14 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- knowledge-management.spec.ts` 通过 2 个 Chromium E2E，覆盖被引用删除二次确认和批量导入刷新列表。

仍需继续：

- 批量导入目前是文本/CSV 简版入口，后续可补文件上传、导入预览、字段映射和错误明细下载。
- 批量导入后的自动索引缺口已在第 31 节闭合，手工/批量知识条目现在可生成带 `knowledge_item_id` 的 PostgreSQL chunk、OpenSearch 文档和 Milvus 向量。

## 31. 2026-06-24 UC-05 批量导入后自动索引增量

本轮目标：把批量导入成功的知识条目接入真实索引生命周期，形成“导入 -> PostgreSQL chunk -> OpenSearch -> Milvus -> 删除清理”的闭环。

已完成：

- `KnowledgeApplicationService.batchImportItems()` 对每条成功导入的知识条目发布 `knowledge.item.index_requested`，失败项不发布事件。
- `RocketMqProperties` 新增 `knowledgeItemIndexRequestedTopic`，`RocketMqDomainEventPublisher` 显式路由 `knowledge.item.index_requested` 到 `ROCKETMQ_KNOWLEDGE_ITEM_INDEX_REQUESTED_TOPIC`。
- Python 新增 `KnowledgeItemIndexingService`，将手工/批量知识条目内容分块，并写入带 `knowledge_item_id` 的 `document_chunks`、`embeddings`、OpenSearch `knowledge_entries_text` 和 Milvus `knowledge_chunks`。
- Python 新增 `RocketMqKnowledgeItemIndexSource`、`item_index_worker_main`，`docker-compose.yml` 新增无端口暴露的 `knowledge-item-index-worker`。
- `MilvusVectorIndex.upsert_chunk()` 在插入后执行 `flush/load_collection`，修复真实 Milvus 写入后立即查询不可见的问题。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#batchImportsKnowledgeItemsAndReportsInvalidOrDuplicateRows" test` 先因未发布 `knowledge.item.index_requested` 失败。
- GREEN：同用例通过 1 个测试。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RocketMqDomainEventPublisherTest" test` 先因 `RocketMqProperties` 缺少索引 topic 编译失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RocketMqDomainEventPublisherTest,KnowledgeApplicationServiceTest" test` 通过 15 个测试。
- RED：`python -m pytest tests/unit/python/test_knowledge_item_indexing.py -q` 先因缺少 `KnowledgeItemIndexingService` 失败。
- GREEN：`python -m pytest tests/unit/python/test_knowledge_item_indexing.py -q` 通过 2 个测试。
- RED：`python -m pytest tests/unit/python/test_rocketmq_worker_adapter.py -q` 先因缺少 `RocketMqKnowledgeItemIndexSource` 失败。
- GREEN：`python -m pytest tests/unit/python/test_rocketmq_worker_adapter.py -q` 通过 5 个测试。
- RED：`python -m pytest tests/unit/python/test_docker_compose_contract.py -q` 先因 Compose 缺少 `knowledge-item-index-worker` 失败。
- GREEN：`python -m pytest tests/unit/python/test_knowledge_item_indexing.py tests/unit/python/test_rocketmq_worker_adapter.py tests/unit/python/test_docker_compose_contract.py tests/unit/python/test_knowledge_index_cleanup.py -q` 通过 13 个测试；`docker compose --env-file .env.example config --quiet` 通过。
- 真实 Docker 三端索引烟测：使用当前源码挂载进 `intelligent-report-system-python-ai-service:latest`，连接 `intelligent-report-infra_default` 网络里的 `postgres/opensearch/milvus`，调用 `KnowledgeItemIndexingService.handle(knowledge.item.index_requested)`；输出 `chunks=1/persistenceStatus=persisted/searchIndexStatus=indexed`，PostgreSQL 查到 `knowledge_item_id` 和 `embeddings=1`，OpenSearch `_doc.found=true` 且 `_source.knowledgeItemId` 正确，Milvus `chunk_id=item_{knowledgeItemId}_chunk_0` 查询命中。

仍需继续：

- 当前运行镜像仍需重建后才能原生包含 `KnowledgeItemIndexingService` 和两个新 worker；本轮真实烟测通过源码挂载验证当前代码。
- 批量导入 UI 仍是简版文本输入，面向客户可继续补上传文件、字段映射、导入预览和错误明细下载。

## 7. 执行进度记录

### 2026-06-23 Batch 1 增量

已完成：

- Java 报告生成任务从固定 `taskId` 改为 `report_generation_tasks` 持久化，支持自然语言任务、模板任务、大纲确认。
- 保存报告任务时创建并绑定 `reports` draft 主记录，为后续报告详情、版本和导出提供主实体。
- `GET /api/v1/reports/{reportId}` 对应应用服务已从 `ReportRepository` 读取真实 `reports` 主记录，不再返回固定演示标题。
- 报告正文段落已写入 `report_sections`，完成快照已写入 `report_versions`，并回填 `reports.current_version_id`。
- `completeGenerationTask` 最小闭环已完成：任务进入 `completed/export/100`，报告状态进入 `completed`，详情接口返回当前版本和正文段落。
- `report_outlines` 与任务状态同步更新，确认大纲后进入 `running/retrieval`，进度为 `10`。
- SSE 阶段消息改为从任务状态读取，不再只发送固定任务示例。
- 新增 `application-dev.yml`，本地 dev profile 默认复用当前 Docker 依赖：PostgreSQL `intelligent_report/report/report123`、MinIO、RocketMQ。
- `.env.example` 的 PostgreSQL 默认值已同步到当前本地 Docker 可用配置。

验证证据：

- `.\mvnw.cmd -pl backend/java-report-core test`：21 个测试，0 失败。
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`：4 个 PostgreSQL 集成测试，0 失败。
- 真库 Flyway 输出：成功校验 3 个迁移，当前 schema 版本为 `003`。
- 验证注意：同一 Maven 模块不要并行运行多个测试命令，否则会竞争 `target` 目录并产生“包不存在”的假编译失败；后续验证按串行执行。

仍未完成：

- 引用锚点仍只是段落 JSON 字段承接，尚未与知识片段/引用详情表形成双向溯源。
- SSE 还只是任务状态快照和阶段提示，尚未从生成片段事件表持续推送正文增量。
- 失败、重试、模型调用审计、生成事件表仍需进入下一轮 Batch 1 子任务。
- `completeGenerationTask` 目前是应用层内部方法，还没有绑定异步 RocketMQ/Python 生成回调或受控 REST 回调。

## 6. 执行约束

- 所有架构图和流程图必须使用 Mermaid。
- 后端继续遵循 DDD 分层：`interfaces`、`application`、`domain`、`infrastructure`。
- 文档集中在 `docs/skill-chain/`，不得散落到代码目录。
- 本地依赖优先复用现有 Docker；新增依赖必须最小资源配置。
- 不得公开暴露 Python `/api/v1/chat` 绕过 Java 业务核心。
- 不得把“接口存在、页面存在、测试骨架存在”视为业务交付完成。

## 8. 2026-06-23 Batch 1 事件流增量记录

本轮目标：补齐报告生成任务的持久化事件流，让 SSE 可以从真实事件记录恢复，而不是只根据任务状态临时拼装固定文本。

已完成：

- 新增领域端口 `ReportGenerationEventRepository`，将生成事件作为报告上下文的一等持久化能力。
- 新增 JDBC 实现 `JdbcReportGenerationEventRepository`，支持按 `task_id + sequence_no` 写入和顺序读取 `stage/delta/references/error/done` 事件。
- 新增 Flyway `V004__report_generation_events.sql`，表字段覆盖事件类型、阶段、正文增量、引用 JSON、进度、错误码、traceId 和 sequenceNo。
- `ReportApplicationService.openTaskStream()` 已改为通过 `streamEventsForTask()` 输出事件列表；有持久化事件时优先使用事件表，空事件时保留状态快照兜底。
- `completeGenerationTask()` 会追加写入 `writing` 阶段、正文 `delta` 和 `done` 事件，为报告完成后的 SSE 回放提供数据。
- PostgreSQL 集成测试已覆盖 V004 迁移、事件顺序读取和 `references_payload` JSON 读写。

验证证据：

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`：6 个测试，0 失败。
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`：5 个 PostgreSQL 集成测试，0 失败；Flyway 从 V003 成功迁移到 V004。
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`：1 个 Spring 上下文测试，0 失败。
- `.\mvnw.cmd -pl backend/java-report-core test`：22 个模块测试，0 失败。

仍需继续：

- 生成失败、重试和错误事件尚未形成完整状态机。
- Python RAG/GPT 生成结果已经具备 RocketMQ 自动 worker 入口和 Java completion 回调代码契约；仍需补真实 Docker 自动消费烟测和线上 GPT provider。
- 引用锚点仍未与知识片段形成双向可追溯关系。
- 导出仍需从当前版本生成真实文件并写入 MinIO。

## 9. 2026-06-23 Batch 4 Markdown 导出增量记录

本轮目标：先把“用户可拿到真实报告文件”的最小闭环打通，避免继续返回内存态假下载 URL。

已完成：

- 新增领域端口 `ReportExportStorage`，报告导出不再依赖应用层内存记录伪造下载地址。
- 新增 `MinioReportExportStorage`，复用当前 MinIO 配置，将报告导出字节写入对象存储，并返回 30 分钟预签名下载 URL。
- `ReportApplicationService.createExport()` 现在会读取真实 `reports` 主记录和当前版本 `report_sections`，渲染 Markdown 文件，写入存储后返回 `bucket/objectKey/fileName/contentType/sizeBytes/downloadUrl`。
- Markdown 导出内容包含报告标题、章节标题、正文和引用标记；当前交付切片明确只支持 `markdown`/`md`，其它格式返回不支持，避免伪造 PDF/Word/PPT 成功。
- 旧导出测试已升级：导出前必须存在真实报告和正文版本，不能再对不存在的报告生成假文件。

验证证据：

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`：7 个测试，0 失败。
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`：1 个 Spring 上下文测试，0 失败。
- `.\mvnw.cmd -pl backend/java-report-core test`：23 个模块测试，0 失败。

仍需继续：

- 导出记录仍是应用内存 Map，服务重启后状态会丢失；需要新增 `report_export_files` 持久化表。
- 预签名下载 URL 已由 MinIO 返回，但 `/api/v1/files/{id}/download-url` 形式的统一文件下载 API 尚未闭环。
- PDF/Word/PPT 仍未实现真实格式生成，应在模板字段校验和品牌规范稳定后逐步补齐。
- 导出审计尚未写入真实审计表。

## 10. 2026-06-23 Batch 4 导出记录持久化增量记录

本轮目标：让导出状态查询不依赖应用内存 Map，服务重启后仍能查询到已生成文件的对象存储元数据。

已完成：

- 新增领域端口 `ReportExportFileRepository`。
- 新增 JDBC 实现 `JdbcReportExportFileRepository`。
- 新增 Flyway `V005__report_export_files.sql`，保存 `report_id/status/format/template_id/bucket/object_key/file_name/content_type/size_bytes/download_url/expires_at` 等字段。
- `ReportApplicationService.createExport()` 已在 MinIO 写入后保存导出文件记录。
- `ReportApplicationService.getExportStatus()` 已改为从导出文件仓储读取状态，不再依赖应用内存 Map。
- 单元测试新增“模拟服务重启后仍可查询导出状态”的用例。
- PostgreSQL 集成测试新增导出文件元数据保存与读取用例。

验证证据：

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`：8 个测试，0 失败。
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`：6 个 PostgreSQL 集成测试，0 失败；Flyway 从 V004 成功迁移到 V005。
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`：1 个 Spring 上下文测试，0 失败。
- `.\mvnw.cmd -pl backend/java-report-core test`：24 个模块测试，0 失败。

仍需继续：

- 统一文件下载 API 尚未完成，当前返回的是 MinIO 预签名 URL。
- 导出审计未落到真实审计表。
- 导出失败状态、重试和过期刷新还需要补齐。
- PDF/Word/PPT 仍需真实格式生成能力。

## 11. 2026-06-23 Batch 3 报告生成 RocketMQ Worker 增量记录

本轮目标：把上一轮手工调用 Python 内部 REST 的报告生成烟测，推进为 Java 确认大纲后可通过 RocketMQ 自动触发 Python worker 的代码与本地编排契约。

已完成：

- `ReportApplicationService.confirmOutline()` 不再只发布前端状态快照，而是发布 worker 可直接消费的 payload，包含 `taskId`、`reportId`、`question`、`context`、`outline`、`traceId`。
- `RocketMqProperties` 新增 `reportGenerationTopic`，`RocketMqDomainEventPublisher` 将 `report.generation.outline_confirmed` 路由到默认物理 topic `report_generation_outline_confirmed`。
- Python 新增 `RocketMqReportGenerationSource`，订阅 `report.generation.outline_confirmed` tag 后调用 `ReportGenerationWorker.handle()`。
- Python 新增 `app.report_generation.worker_main`，用于独立启动报告生成 worker。
- `docker-compose.yml` 新增 `report-generation-worker` 服务，不暴露端口，复用 Python 镜像、RocketMQ、PostgreSQL、OpenSearch、Milvus 和 Java completion callback 配置。

验证证据：

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#confirmOutlineMovesTaskIntoRetrievalStage,ContractSurfaceTest#rocketMqPropertiesExposeReportGenerationTopic,RocketMqDomainEventPublisherTest" test`：5 个 Java 测试，0 失败。
- `python -m pytest tests/unit/python/test_rocketmq_worker_adapter.py tests/unit/python/test_docker_compose_contract.py -q`：5 个 Python/Compose 契约测试，0 失败。
- `python -m pytest tests/unit/python/test_report_generation_worker.py backend/python-ai-service/tests/test_ai_service_runtime.py -q`：8 个 Python 运行时测试，0 失败。
- `docker compose --env-file .env.example config --quiet`：Compose 配置通过。
- Docker 自动消费烟测：2026-06-25 针对 `UC-01` 重新做 fresh smoke。先发现旧 `ir-report-generation-worker-smoke` 虽已 healthy 且能消费 RocketMQ，但回调 Java `/api/v1/reports/generation-tasks/{taskId}/completion` 被 `401` 拒绝；根因是容器里历史注入的静态 `JAVA_SERVICE_TOKEN` 已过期。为本地开发环境补上 Python worker completion auth 回归测试后，调整为优先使用显式 `JAVA_SERVICE_TOKEN`，缺省时基于共享 `JWT_SECRET` 自动生成 2 小时有效的 dev service token。重建最新 Python 镜像并以新镜像启动 `ir-report-generation-worker-smoke` 后，通过 Java API 创建并确认 `taskId=205/reportId=333`，worker 自动消费并回调 Java；报告详情在 2 秒内返回 `completed/currentVersionId=121`，正文包含 `Based on Document 990004: Receivables aging requires follow-up and risk monitoring.`，引用包含 `doc_990004_chunk_1` 与 `doc_990004_chunk_0`。
- Docker DB 验证：`report_generation_tasks(205)=completed/export/100`；`reports(333)=completed,current_version_id=121`；`model_invocations(task_id=205)` 存在 `provider=local-fallback`、`model_name=local-rag-fallback`、`status=succeeded`、`trace_id=trace_205_131ec1d6c0d3`。本轮 `report_generation_events` 未重新追加检查，因为完成闭环已由 API 返回、任务表、报告表和 `model_invocations` 专表交叉证实。

仍需继续：

- 当前模型调用已支持 OpenAI-compatible provider；2026-06-25 已通过真实 `DASHSCOPE_API_KEY` 完成 DashScope OpenAI-compatible 外部烟测，并确认 Java `model_invocations/model_responses` 留痕；后续重点转为把这条真实 provider 闭环沉淀为稳定自动回归，而不是继续停留在手工 smoke。
- 模型调用审计已拆成 `model_invocations/model_responses` 专表，并保留 `operation_logs` 摘要兼容。

## 12. 2026-06-23 Batch 3 模型调用审计专表增量记录

本轮目标：把模型调用审计从 `operation_logs` 摘要补成可迁移、可查询、可追溯的专表能力，满足 Prompt、上下文、参数、响应、耗时、Token、结果和 traceId 留痕要求。

已完成：

- 新增 `ModelInvocationAudit`、`ModelResponseAudit` 领域模型与 `ModelInvocationRepository` 端口。
- 新增 `JdbcModelInvocationRepository`，写入 `model_invocations` 与 `model_responses`，并通过 `audit_event_key` 支持 worker 重试幂等。
- 新增 Flyway `V015__model_invocation_audit.sql`，包含任务、报告、用户、provider/model、Prompt、上下文、参数、Token、耗时、错误、traceId、响应摘要等字段和索引。
- `ReportApplicationService.completeGenerationTaskFromWorker()` 在保留 `operation_logs` 兼容摘要的同时写入模型审计专表。
- `AuditApplicationService.modelInvocation()` 优先读取模型审计专表，旧 `operation_logs` 数据保留回退查询能力。

验证证据：

- RED：`ReportApplicationServiceTest#completesTaskFromControlledAiCallbackWithReferencesAndModelAuditSummary` 先因缺少 `ModelInvocationAudit/ModelResponseAudit/ModelInvocationRepository` 与服务注入点编译失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#completesTaskFromControlledAiCallbackWithReferencesAndModelAuditSummary test`：1 个测试，0 失败。
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=AuditApplicationServiceTest test`：4 个测试，0 失败。
- `.\mvnw.cmd -pl backend/java-report-core test`：59 个 Java 测试，0 失败。
- `python -m pytest backend/python-ai-service tests/unit/python -q`：36 个 Python 测试，0 失败。
- `docker compose --env-file .env.example config --quiet`：Compose 配置通过。
- `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndReadsModelInvocationAuditTablesInPostgres test`：Flyway 成功从 V014 迁移到 V015，真实 PostgreSQL 写读 `model_invocations/model_responses` 通过。

仍需继续：

- OpenAI-compatible provider 已完成真实外部烟测；后续需把真实 provider 路径沉淀为可重复执行的 Docker/脚本化回归，并继续补多模型 fallback 列表的实际轮转策略。
- 无 Key 或外部调用失败时继续明确标记本地 fallback provider，不得伪装为线上模型成功。


## 13. 2026-06-23 Batch 5 分享访问安全增量记录

本轮目标：补强外部分享访问边界，避免分享链接只有 token 和有效期，缺少密码哈希、撤销和访问审计。

已完成：

- `ShareLink` 新增 `passwordHash`，创建带密码分享时只保存 SHA-256 哈希，不返回明文或哈希给前端。
- `PermissionApplicationService.accessShare()` 校验 active、有效期和密码；密码错误写入 `share_access_failed` 审计并拒绝访问。
- 新增 `revokeShare()`，仅创建人可撤销分享，撤销后访问被拒绝并写入 `share_revoke` 审计。
- `JdbcShareLinkRepository` 支持更新撤销状态和持久化 `password_hash`。
- 新增 Flyway `V016__share_link_password_hash.sql`，为 `share_links` 增加 `password_hash` 与状态索引。
- 修复前端 `ShareAccess.vue` 与 `ReportDetail.vue` 的明显乱码文案，`shareApi` 响应类型补充 `reportId/passwordRequired`。
- 新增 `PermissionApplicationService.sharedReport()` 和 `POST /api/v1/share-links/{shareToken}/report`，外部用户通过 share token 和密码校验后只能读取报告只读字段与 sections。
- `SecurityConfig` 放行 share access/report 两个公开路径；JWT filter 保持 `/api/v1/share-links/` 前缀受控豁免，避免 Python `/chat` 绕过 Java 闭环。
- `ShareAccess.vue` 改为访问成功后展示报告标题、状态、章节正文和引用；`shareApi.getSharedReport()` 补充前端 API 契约。
- `client.ts` 修复明显乱码错误提示，外部访问失败时返回可读中文错误。
- 新增 Flyway `V017__share_link_download_policy.sql`，为 `share_links` 增加 `allow_download`，历史和默认分享均不可下载。
- 新增 `PermissionApplicationService.sharedExportDownloadUrl()` 和 `POST /api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url`，每次下载前重新校验 share token、密码、有效期、撤销状态、`allowDownload` 和导出文件归属。
- 分享下载成功写 `share_export_download` 审计，下载未授权写 `share_download_denied`，密码错误继续写 `share_access_failed`。
- `PermissionApplicationService.sharedReport()` 在 `allowDownload=true` 时返回已完成导出文件摘要，字段只包含 `exportFileId/fileName/format/contentType/sizeBytes/createdAt`，不暴露 `bucket/objectKey/downloadUrl`。
- `ShareAccess.vue` 已接入下载按钮：外部用户访问分享报告后，若存在可下载导出文件，点击按钮会调用受控分享下载接口获取短期 URL。
- `docs/skill-chain/api_contract.md` 补齐 `/share-links/{shareToken}/report` 与 `/share-links/{shareToken}/exports/{exportFileId}/download-url` 的 JSON 请求/响应约定。

验证证据：

- RED：`PermissionApplicationServiceTest#protectsPasswordShareWithHashAndWritesAccessAudit` 先因缺少四参构造器、`passwordHash()` 和 `revokeShare()` 编译失败。
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest test`：6 个测试，0 失败。
- `PermissionApplicationServiceTest#returnsReadOnlyReportDetailAfterSharePasswordValidation` 先因缺少 `sharedReport()` 与五参构造器失败，随后验证只读详情、密码拒绝、不泄露 owner/password/passwordHash 和 `share_report_view` 审计。
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest test`：7 个测试，0 失败。
- `npm run test -- apiContracts`：3 个前端 API 契约测试，0 失败。
- `PermissionApplicationServiceTest#shareDownloadRequiresExplicitGrantAndValidPassword` 先因缺少 `allowDownload()`、扩展构造器和 `sharedExportDownloadUrl()` 失败，随后验证默认拒绝、显式允许、错密码拒绝、短期 URL 和下载审计。
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest test`：9 个测试，0 失败。
- `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsShareDownloadPolicyAndCreatesSharedDownloadUrlInPostgres test`：Flyway 成功从 V016 迁移到 V017，真实 PostgreSQL 验证 `allow_download` 落库和分享下载短链通过。
- `npm run test -- apiContracts`：4 个前端 API 契约测试，0 失败。
- `PermissionApplicationServiceTest#returnsReadOnlyReportDetailAfterSharePasswordValidation` 增加 exports 摘要断言；`PermissionApplicationServiceTest#omitsExportListWhenShareDownloadIsNotAllowed` 验证未授权下载时列表为空。
- `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsShareDownloadPolicyAndCreatesSharedDownloadUrlInPostgres test`：真实 PostgreSQL 额外验证 `sharedReport` 可通过 `JdbcReportExportFileRepository.findCompletedByReportId()` 读取导出文件摘要。
- `npm run test -- apiContracts`：5 个前端 API 契约测试，0 失败。
- `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#protectsPasswordShareAndWritesAccessAuditInPostgres test`：Flyway 成功从 V015 迁移到 V016，真实 PostgreSQL 验证密码哈希、撤销状态和访问审计通过。
- `.\mvnw.cmd -pl backend/java-report-core test`：60 个 Java 测试，0 失败。
- `python -m pytest backend/python-ai-service tests/unit/python -q`：36 个 Python 测试，0 失败。
- `docker compose --env-file .env.example config --quiet`：Compose 配置通过。
- `npm run typecheck`：Vue TypeScript 类型检查通过。
- `npm run build`：前端生产构建通过。

仍需继续：

- 分享访问已补前端 Playwright E2E；下一步需要接入真实 Java 后端和数据库种子数据跑端到端验收，避免只依赖前端 route mock。
- 密码哈希当前为本地 SHA-256 基线，后续可升级为 BCrypt/Argon2，并增加密码复杂度策略。

## 14. 2026-06-23 Batch 3 OpenAI-compatible Provider 增量记录

本轮目标：补齐线上 GPT 兼容模型调用边界，让报告生成 worker 在配置 API Key 时可调用真实 `/chat/completions`，无 Key 时保留可审计的本地降级。

已完成：

- 新增 `OpenAiCompatibleProvider`，支持 `LLM_PROVIDER=openai-compatible`、`LLM_MODEL`、`LLM_BASE_URL`、`LLM_API_KEY`、`LLM_TIMEOUT_SECONDS`。
- 有 API Key 时向 `{LLM_BASE_URL}/chat/completions` 发送标准 messages 请求，并读取正文与 `prompt_tokens/completion_tokens/total_tokens`。
- 未配置 API Key、provider 非兼容类型或外部调用失败时，明确回退到 `provider=local-fallback`、`model=local-rag-fallback`、`fallbackUsed=true`。
- `ReportGenerationWorker` 已改为使用 LLM provider 生成报告正文，并在 Java completion payload 中写入 `inputTokens/outputTokens/totalTokens/latencyMs/fallbackUsed/errorMessage`，匹配 Java 模型审计入库字段。
- `.env.example` 与 `docker-compose.yml` 已补充 `LLM_TIMEOUT_SECONDS`，Python API 服务和 report generation worker 均可读取同一组 LLM 配置。

验证证据：

- RED：`python -m pytest tests/unit/python/test_llm_provider.py -q` 先因缺少 `app.llm_orchestration.application.openai_compatible_provider` 失败。
- RED：`python -m pytest tests/unit/python/test_report_generation_worker.py -q` 先因 worker completion payload 缺少 `inputTokens` 失败。
- GREEN：`python -m pytest tests/unit/python/test_llm_provider.py tests/unit/python/test_report_generation_worker.py backend/python-ai-service/tests/test_ai_service_runtime.py -q`：11 个测试通过。
- Compose 契约：`python -m pytest tests/unit/python/test_docker_compose_contract.py -q`：2 个测试通过。

仍需继续：

- 真实 `LLM_API_KEY` 烟测已完成；下一步应补可重复执行的 Docker E2E 或脚本化验收，持续确认 Java `model_invocations/model_responses` 记录真实 provider/model/token。
- 需要补多模型 fallback 列表的实际轮转策略，目前只保留配置字段和本地降级。

## 15. 2026-06-23 Batch 6 Dashboard 真实聚合增量记录

本轮目标：把工作台从静态卡片/审计日志粗聚合推进到原型要求的真实业务指标、趋势、排行和最近活动。

已完成：

- 新增 `DashboardMetricsRepository` 领域端口和 `JdbcDashboardMetricsRepository` PostgreSQL 实现。
- `GET /api/v1/dashboard/overview` 现在优先从真实业务表聚合：
  - `cards.reportOutputs`：指定时间范围内已完成报告数。
  - `cards.knowledgeItems`：指定时间范围内知识条目数。
  - `cards.activeDataSources`：启用且近期更新的数据源数。
  - `cards.citationHitRate`：带引用章节数 / 总章节数。
  - `cards.activeUsers`：审计日志中的活跃用户数。
  - `reportTrend`：按日完成报告趋势。
  - `knowledgeRank`：基于报告章节引用标记的知识库引用排行。
  - `recentActivities`：最近审计活动。
- `AuditApplicationService` 保留无 Dashboard 仓储时的审计日志 fallback，方便单元测试和轻量运行。
- 前端 `Dashboard.vue` 已移除静态乱码卡片，改为调用 `/dashboard/overview`，展示指标、趋势、排行和最近活动。
- `auditApi.dashboardOverview()` 和前端 API 契约测试已补齐。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=AuditApplicationServiceTest#buildsDashboardOverviewFromBusinessTablesAndKeepsRecentAuditActivities" test` 先因缺少 `DashboardMetricsRepository` 与三参构造器编译失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=AuditApplicationServiceTest" test`：5 个测试通过。
- PostgreSQL：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#buildsDashboardMetricsFromBusinessTablesInPostgres" test`：Flyway 校验 17 个迁移，1 个真实 PostgreSQL 聚合测试通过。
- 前端契约：`npm run test -- apiContracts`：6 个测试通过。
- 前端类型：`npm run typecheck`：通过。

仍需继续：

- Dashboard 指标仍需按角色和资源授权范围收敛，当前后端聚合是全局业务表口径。
- Dashboard 已补 Playwright E2E，覆盖页面切换时间范围后指标、趋势、排行和最近活动刷新；下一步需要接入真实后端数据种子验收。

## 16. 2026-06-23 Batch 5 分享访问 Playwright 增量记录

本轮目标：补齐 UC-09 分享访问的前端端到端验收，覆盖外部分享高风险边界。

已完成：

- `share-and-permission.spec.ts` 覆盖分享链接过期、密码错误、外部只读详情、允许下载时调用受控分享下载接口、未授权下载时保留只读详情并展示错误。
- 增加分享页首屏中文标题断言，防止可见文案回退为不可读乱码。
- 复核 `ShareAccess.vue`：文件为 UTF-8 正常中文，早前 PowerShell 默认读取显示的乱码不是源码内容。

验证证据：

- `npm run e2e -- share-and-permission.spec.ts`：6 个 Chromium Playwright E2E 通过。

仍需继续：

- 需要将该 E2E 从 route mock 升级为连接本地 Java/PostgreSQL/MinIO 的真实后端验收数据流。

## 17. 2026-06-23 Batch 6 Dashboard Playwright 增量记录

本轮目标：补齐 UC-13 工作台页面级验收，证明前端不是静态卡片，而是按时间范围请求并刷新真实聚合字段。

已完成：

- `audit-dashboard.spec.ts` 新增 `REQ-DASH-001` 场景，覆盖默认 `last7days` 请求和点击“近 30 天”后的 `last30days` 请求。
- 断言范围包括指标卡、引用命中率百分比、报告趋势、知识库引用排行和最近活动。
- 保留已有 `REQ-AUDIT-001` 审计日志 E2E。

验证证据：

- RED：首次运行因日期文本同时出现在趋势和最近活动中触发 Playwright strict mode 多匹配，证明测试实际命中页面 DOM。
- GREEN：`npm run e2e -- audit-dashboard.spec.ts`：2 个 Chromium Playwright E2E 通过。

仍需继续：

- 需要把 Dashboard E2E 从 route mock 升级为本地 Java/PostgreSQL 真实后端验收，并补角色化指标范围。

## 18. 2026-06-23 Batch 6 规则编排调试增量记录

本轮目标：把 UC-08 从占位画布推进到可验证的最小规则执行闭环。

已完成：

- 后端 `RuleDomainService` 新增 `validateDefinition()`，校验 `nodes/edges` schema、必需 start/end 节点、condition 节点参数和悬空连线。
- 后端 `executeDebug()` 支持按 sample 执行 condition 节点，输出 `matched/evaluatedNodes/trace`，不再固定返回 `matched=true`。
- `RuleApplicationService` 在创建/保存规则前执行定义校验，并将调试输出持久化到 `rule_debug_runs.output_json`。
- 前端 `RuleDesigner.vue` 从占位画布升级为规则列表、节点卡片、连线列表、调试样本 JSON 输入和 trace 展示。
- `rule-engine.spec.ts` 覆盖规则页面展示和调试运行。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest,RuleApplicationServiceTest" test` 先因缺少 `validateDefinition()` 编译失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest,RuleApplicationServiceTest" test`：7 个 Java 测试通过。
- PostgreSQL：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test`：Flyway 17 个迁移校验通过，1 个真实 PostgreSQL 测试通过，并断言 `output_json` 包含 `matched=true` 与 `evaluatedNodes=3`。
- 前端 RED：`npm run e2e -- rule-engine.spec.ts` 先因占位页缺少规则名称失败。
- 前端 GREEN：`npm run e2e -- rule-engine.spec.ts`：1 个 Chromium Playwright E2E 通过。
- 契约/类型：`npm run test -- apiContracts`：6 个测试通过；`npm run typecheck`：通过。

仍需继续：

- 规则画布仍是只读展示，不支持拖拽建模、节点编辑、分支条件、发布审批和生产调度。
- 需要补真实后端 E2E 或 API smoke，从浏览器经 Java/PostgreSQL 创建、保存、调试规则。

## 19. 2026-06-23 Batch 6 数据源同步日志增量记录

本轮目标：把 UC-07 从“保存配置和测试连接”推进到可追踪的最小同步任务闭环，前端能看到同步状态、处理行数和失败原因。

已完成：

- 新增 Flyway `V018__knowledge_data_source_sync_runs.sql`，持久化数据源同步运行记录。
- `KnowledgeBaseRepository` 与 `JdbcKnowledgeBaseRepository` 新增同步运行记录保存、分页查询和计数能力。
- `KnowledgeApplicationService.startDataSourceSync()` 基于已保存数据源执行 PostgreSQL JDBC endpoint 可连通性判定，并写入 `succeeded/failed` 同步记录、处理行数、消息和失败原因。
- `KnowledgeController` 新增 `POST /api/v1/data-sources/{dataSourceId}/sync-runs` 与 `GET /api/v1/data-sources/{dataSourceId}/sync-runs`。
- 前端 `knowledgeApi`、`DataSourceConfig.vue` 和 Playwright 覆盖保存数据源、测试连接、启动同步、展示同步日志。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest" test` 先因缺少 `startDataSourceSync/listDataSourceSyncRuns` 与仓储方法编译失败。
- RED：`npm run test -- apiContracts` 先因缺少 `knowledgeApi.saveDataSource` 失败；`npm run e2e -- data-source-sync.spec.ts` 先因页面缺少“数据源名称”失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest" test`：7 个 Java 测试通过。
- PostgreSQL：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test`：Flyway 校验 18 个迁移并验证 `knowledge_data_source_sync_runs` 真实写读，1 个集成测试通过。
- 前端契约：`npm run test -- apiContracts`：7 个测试通过。
- 前端 E2E：`npm run e2e -- data-source-sync.spec.ts`：1 个 Chromium Playwright E2E 通过。
- 前端类型：`npm run typecheck`：通过。

仍需继续：

- 当前同步只是最小手动任务记录，不执行 PostgreSQL/MySQL/API 的真实数据抽取、转换、入库和索引联动。
- 数据源凭据尚未加密保存，后续必须引入凭据密文、密钥轮换和脱敏展示。
- 暂未实现周期调度、增量游标、失败重试、同步审计和真实后端浏览器 E2E。

## 20. 2026-06-23 Batch 4 Word 导出增量记录

本轮目标：把 UC-04 从仅有 Markdown 交付推进到至少一种 Office 文件真实可下载，并强制企业品牌模板字段完整。

已完成：

- `ReportApplicationService.createExport()` 支持 `docx` 与 `word` 格式别名，生成最小 OOXML ZIP 文件。
- DOCX 导出文件名使用 `.docx`，content type 使用 `application/vnd.openxmlformats-officedocument.wordprocessingml.document`，对象仍写入 `ReportExportStorage` 并走原有导出记录持久化与受控下载 URL。
- `ReportDomainService.ensureEnterpriseBrandTemplate()` 强制校验 `companyName/logoObjectKey/header/footer/fontFamily/primaryColor`，字段缺失时返回企业模板异常，不生成伪文件。
- DOCX 正文包含企业名称、页眉、报告标题、目录占位、章节标题、正文、引用和页脚。
- `ReportDomainService` 注释恢复为可读 UTF-8 中文，避免继续扩散编码噪音。
- 前端 `ReportDetail.vue` 新增导出格式选择、企业品牌模板字段和导出结果提示；Word/Markdown 可选，PDF/PPT 显式禁用为暂未支持。
- `reportApi.createExport()` 类型支持 `brand` 模板字段，前端契约测试覆盖企业 Word 导出 payload。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test` 先因 `unsupported export format for current delivery slice: docx` 失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#rejectsWordExportWhenEnterpriseBrandTemplateIsIncomplete" test`：2 个测试通过，验证 DOCX ZIP、`word/document.xml` 内容、Word MIME 类型和缺品牌字段拒绝路径。
- 前端 RED：`npm run typecheck` 先因 `brand` 不存在于 `reportApi.createExport()` payload 类型失败。
- 前端 GREEN：`npm run test -- apiContracts`：8 个测试通过；`npm run typecheck`：通过；`npm run e2e -- report-generation.spec.ts`：3 个 Chromium E2E 通过，覆盖 Word 导出、PDF/PPT 禁用和成功反馈。

仍需继续：

- 当前 DOCX 是最小 OOXML 文档，未嵌入真实 Logo 图片，也未实现页眉页脚部件、字体/主题色样式和自动目录域。
- PDF 与 PPT 仍未实现真实格式生成。
- 前端导出界面仍需暴露 Word/PDF/PPT 支持矩阵和缺模板错误提示。

## 21. 2026-06-23 UC-14 版本差异与页面回滚增量记录

本轮目标：把报告版本从后端回滚语义推进到原型页面可用闭环，用户能先查看版本差异，再回滚历史版本。

已完成：

- `ReportContentRepository` 新增 `findSectionsByVersion()`，`JdbcReportContentRepository` 从 `report_versions.snapshot` 读取历史版本 sections。
- `ReportApplicationService.compareVersions()` 基于章节标题对比两个版本，输出 `added/removed/modified/unchanged` 汇总和逐段 `changes`。
- `ReportController` 新增 `GET /api/v1/reports/{reportId}/versions/diff?baseVersionId=&targetVersionId=`。
- `reportApi` 新增报告详情、版本列表、版本差异和回滚接口类型。
- `ReportDetail.vue` 修复可见中文乱码，接入真实报告详情、版本列表、版本差异、历史版本回滚和可访问的版本回滚按钮标签。
- `report-generation.spec.ts` 新增 UC-14 页面级 E2E，覆盖版本差异请求参数、差异展示和回滚生成新版本提示。
- `report-version-real-backend.spec.ts` 新增真实后端浏览器 E2E，先通过当前 Java API 和 PostgreSQL 创建报告、写入两个版本，再从浏览器访问报告详情，完成查看差异和回滚。
- `playwright.real-backend.config.ts` 固定使用独立前端端口和 Vite `/api/v1` 代理，避免复用旧 dev server 或旧镜像造成验收污染。
- `RocketMqDomainEventPublisher` 对 RocketMQ 发送失败改为记录警告并允许同步业务继续，避免 MQ broker 路由不可达时阻断已落库的报告任务创建。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#comparesReportVersionsBySectionSnapshot" test` 先因缺少 `compareVersions()` 编译失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#comparesReportVersionsBySectionSnapshot" test`：1 个应用层测试通过。
- PostgreSQL：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#comparesVersionSnapshotsFromPostgres" test`：Flyway 19 个迁移校验通过，真实 PostgreSQL snapshot 差异测试通过。
- 前端契约：`npm run test -- apiContracts`：9 个测试通过。
- 前端 E2E：`npm run e2e -- report-generation.spec.ts`：4 个 Chromium E2E 通过。
- 前端类型/构建：`npm run typecheck`、`npm run build` 均通过。
- 真实后端 E2E：当前源码 Java 服务以 `SERVER_PORT=18084`、`SPRING_PROFILES_ACTIVE=dev` 连接本地 Docker PostgreSQL 后，执行 `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18084/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18084 npm run e2e:real-backend`：1 个 Chromium E2E 通过。
- 可用性回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RocketMqDomainEventPublisherTest#doesNotFailSynchronousBusinessFlowWhenRocketMqSendFails" test`：1 个测试通过。

仍需继续：

- 差异算法当前按章节标题匹配，后续可引入稳定 section key 或文本相似度以支持标题重命名场景。

## 22. 2026-06-23 可见编码与真实后端验收加固

本轮目标：清理 UC-14 验收入口和鉴权链路的用户可见乱码，并把真实后端浏览器验收命令固化为可复跑脚本。

已完成：

- `ApiResponse.success()` 成功文案修复为 `操作成功`。
- `JwtAuthenticationFilter` 未登录文案修复为 `请登录后继续操作`，并显式设置 UTF-8 响应编码。
- `PermissionController.currentUser()` 展示名修复为 `当前用户`。
- `ReportDetail.vue` 恢复报告详情、导出、正文、引用、版本管理、差异和回滚相关中文文案。
- `client.ts` 恢复 `请求失败`、`请求超时，请稍后重试`、`网络错误，请检查连接`，并保留 `localStorage.accessToken` 自动注入 Bearer Token。

验证证据：

- RED：`JwtAuthenticationFilterTest#returnsReadableChineseMessageWhenBearerTokenIsMissing` 先因响应体为问号失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#exposesExternalAuthProfileEndpoint+apiResponseUsesReadableChineseSuccessMessage,JwtAuthenticationFilterTest#returnsReadableChineseMessageWhenBearerTokenIsMissing" test`：3 个测试通过。
- `npm run test -- client`：2 个前端测试通过。
- `npm run typecheck`：通过。
- `npm run e2e -- report-generation.spec.ts`：4 个 Chromium E2E 通过。

## 23. 2026-06-23 UC-10 页面选中文本批注增量记录

本轮目标：把批注协作从后端接口闭环推进到报告详情页可操作闭环，用户能在正文中选中文本并创建带文本锚点的协作任务。

已完成：

- `report-generation.spec.ts` 新增 `REQ-COLLAB-002` 页面级 Playwright 场景，覆盖正文选区、批注意见、指派用户和提交 payload。
- `ReportDetail.vue` 正文段落新增稳定 `data-section-id` 与 `data-section-content` 锚点，选中文本后反推 `sectionId/startOffset/endOffset/selectedText`。
- 报告详情页新增批注协作面板，支持填写批注意见和指派用户 ID，并调用 `collaborationApi.createComment()`。
- `collaborationApi.createComment()` 补充明确返回类型，页面可展示后端返回的 `taskId`，并允许 `assigneeUserId` 使用数值型 ID。
- `CollaborationApplicationService` 兼容 JSON 表单传入的数字字符串 assignee，避免浏览器输入框提交 `"1002"` 时触发 500。
- `report-collaboration-real-backend.spec.ts` 连接当前 Java 服务和 PostgreSQL，验证页面提交批注后 `report_annotations/collaboration_tasks/collaboration_notifications` 真实落库。
- 复核 `ReportDetail.vue` 与 `client.ts` UTF-8 文案，关键文件 mojibake 扫描未命中。

验证证据：

- RED：`npm run e2e -- report-generation.spec.ts -g "REQ-COLLAB-002"` 先因页面缺少 `[data-section-id="summary"]` 失败。
- GREEN：`npm run e2e -- report-generation.spec.ts -g "REQ-COLLAB-002"`：1 个 Chromium E2E 通过，验证 `anchor.sectionId=summary`、`selectedText=回款风险` 和 offset payload。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=CollaborationApplicationServiceTest#addAnnotationAcceptsNumericStringAssigneeFromJsonFormInput" test` 先因 `String` 不能强转 `Number` 失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=CollaborationApplicationServiceTest#addAnnotationAcceptsNumericStringAssigneeFromJsonFormInput" test`：1 个测试通过；`.\mvnw.cmd -pl backend/java-report-core "-Dtest=CollaborationApplicationServiceTest" test`：6 个测试通过。
- 真实后端 RED：`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18084/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18084 npm run e2e:real-backend -- report-collaboration-real-backend.spec.ts` 先因后端 `assigneeUserId` 字符串转换异常返回 500。
- 真实后端 GREEN：同命令通过 1 个 Chromium E2E，页面连接当前 Java 服务和 PostgreSQL，断言三张协作表均写入记录。
- 回归：`npm run e2e -- report-generation.spec.ts`：5 个 Chromium E2E 通过。
- UC-14 真实后端回归：`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18084/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18084 npm run e2e:real-backend -- report-version-real-backend.spec.ts`：1 个 Chromium E2E 通过。
- 类型/契约：`npm run typecheck` 通过；`npm run test -- apiContracts`：9 个测试通过。
- 构建：`npm run build` 通过。
- 编码扫描：`rg -n "鎶|瀵|璇|澶|缃|锛|<replacement-char>" frontend/web-console/src/pages/reports/ReportDetail.vue frontend/web-console/src/api/client.ts frontend/web-console/src/api/collaborationApi.ts` 无命中。

仍需继续：

- 当前 offset 计算按选中文本首次出现位置匹配；若同一段落重复出现相同文本，后续应改为基于 DOM Range 的精确字符偏移。

## 24. 2026-06-24 UC-11 用户管理与 RBAC 真实后端闭环

本轮目标：把用户与 RBAC 从后端能力推进到原型页面可操作闭环，管理员能批量导入用户、启用/禁用账号，并证明 disabled 账号不能绕过后端权限切面。

已完成：

- `PermissionApplicationService.batchImportUsers()` 支持批量导入用户，逐条返回 `imported/failed` 状态和 `duplicate_username/username_required` 失败原因。
- `PermissionController` 新增 `POST /api/v1/users/batch-import`，受 `user:manage` 权限保护。
- `adminApi` 补齐 `batchImportUsers()` 与 `updateUserStatus()` 类型化接口，契约指向 S4 API。
- `UserManage.vue` 从真实 `/users` 加载数据，支持 CSV 文本批量导入、刷新列表、启用/禁用账号和结果提示。
- `share-and-permission.spec.ts` 新增页面级管理员批量导入并禁用用户验收。
- `user-rbac-real-backend.spec.ts` 连接当前 Java 服务和 PostgreSQL，验证批量导入用户、禁用账号、disabled token 访问管理页被后端返回 `当前账号无权执行该操作`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#batchImportsUsersAndReportsDuplicatesWithoutOverwritingExistingAccounts" test` 先因缺少 `batchImportUsers` 编译失败。
- GREEN：同命令通过 1 个测试；扩展命令 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest,PermissionAspectTest,JwtTokenProviderTest" test` 通过 15 个测试。
- RED：`npm run test -- apiContracts` 先因 `adminApi.batchImportUsers is not a function` 失败。
- GREEN：`npm run test -- apiContracts` 通过 10 个测试。
- RED：`npm run e2e -- share-and-permission.spec.ts -g "管理员批量导入并禁用用户"` 先因页面缺少“批量导入用户”输入失败。
- GREEN：`npm run e2e -- share-and-permission.spec.ts` 通过 7 个 Chromium E2E。
- 真实后端 RED：`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18084/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18084 npm run e2e:real-backend -- user-rbac-real-backend.spec.ts` 先因 18084 旧进程未加载新路由，`/api/v1/users/batch-import` 被映射为静态资源并返回 500。
- 真实后端 GREEN：启动当前源码服务到 18085 后，`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18085/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18085 npm run e2e:real-backend -- user-rbac-real-backend.spec.ts` 通过 1 个 Chromium E2E。
- 类型检查：`npm run typecheck` 通过。

仍需继续：

- 当前批量导入采用简洁 CSV 文本入口，后续面向客户交付可继续补文件上传、导入预览、字段映射、部门字段和错误明细下载。
- 角色矩阵当前为固定角色到权限映射，后续若客户需要在线维护角色，需要单独扩展角色/权限配置表和审计。

## 25. 2026-06-24 UC-09 分享访问真实后端闭环

本轮目标：把分享访问从页面级 mock 验收推进到当前 Java 服务、PostgreSQL、MinIO 的真实浏览器闭环，并修复错误密码被包装成 500 的生产可用性问题。

已完成：

- 新增 `share-real-backend.spec.ts`，通过当前 Java API 创建报告任务、写入正文和引用、生成 Markdown 导出文件、创建带密码且允许下载的分享链接。
- 外部浏览器访问 `/share/{shareToken}` 后，输入正确密码可以只读查看报告标题、正文、引用和可下载文件入口。
- 分享下载接口 `POST /share-links/{shareToken}/exports/{exportFileId}/download-url` 会重新校验密码，并返回 `downloadPolicy=share_presigned_url` 的短期下载 URL。
- 错误密码访问分享报告从兜底 500 修复为明确 403 `访问被拒绝`，避免把业务拒绝伪装成系统繁忙。
- `GlobalExceptionHandler` 新增 `SecurityException` 映射，复用后端安全边界的可读错误响应。

验证证据：

- RED：`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18085/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18085 npm run e2e:real-backend -- share-real-backend.spec.ts` 先因错误密码返回 500 而失败，期望为 403。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=GlobalExceptionHandlerTest" test` 先因缺少 `handleSecurity(SecurityException)` 编译失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=GlobalExceptionHandlerTest" test` 通过 1 个测试。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest" test` 通过 11 个测试。
- 真实后端 GREEN：重启当前源码服务到 18085 后，`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18085/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18085 npm run e2e:real-backend -- share-real-backend.spec.ts` 通过 1 个 Chromium E2E。

仍需继续：

- 分享链接管理页撤销、复制链接、下载授权开关等基础 owner 操作体验已在第 182 节闭合。

## 26. 2026-06-24 UC-12 审计历史权限边界真实后端闭环

本轮目标：把“个人历史/全局审计”的权限边界从文档要求推进到后端、前端和真实 PostgreSQL 数据的可验证闭环，并补上报告任务创建审计，保证用户创建报告后能在历史页看到自己的业务操作。

已完成：

- `AuditApplicationService.history()` 不再复用全局 `auditLogs()`，改为读取 `CurrentUserHolder` 并按 `actor_user_id` 过滤。
- `AuditRepository` 增加按 actor 分页与计数能力，`JdbcAuditRepository` 使用 SQL `WHERE actor_user_id = ?` 实现真实库过滤。
- `ReportApplicationService.createGenerationTask()` 和 `createTemplateTask()` 写入 `report_generation_task_created` 审计，审计详情包含模式、topic/templateId、payload 和 reportId。
- 前端 `auditApi` 增加 `/history` 契约，审计页拆分“个人历史”和“全局审计”两个 tab，并统一展示后端审计字段。
- 新增真实后端 E2E：两个普通用户分别创建报告任务后，用户 A 的 `/history` 只包含自己的日志；用户 A 访问 `/audit-logs` 返回 403；管理员访问 `/audit-logs` 可看到两个用户的全局日志。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=AuditApplicationServiceTest" test` 先因 `history()` 返回 2 条含他人日志失败。
- RED：`npm run test -- apiContracts` 先因 `auditApi.history is not a function` 失败。
- RED：`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18086/api/v1 npm run e2e:real-backend -- audit-real-backend.spec.ts` 先因报告任务创建没有审计导致 `/history` 为空失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#createsDurableNaturalLanguageTasksWithUniqueIdsAndOutlineSnapshot,AuditApplicationServiceTest" test` 通过 7 个测试。
- GREEN：`npm run test -- apiContracts` 通过 11 个测试。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- audit-dashboard.spec.ts` 通过 2 个 Chromium E2E。
- 真实后端 GREEN：当前源码 Java 服务连接本地 Docker PostgreSQL 后，`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18086/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18086 npm run e2e:real-backend -- audit-real-backend.spec.ts` 通过 1 个 Chromium E2E。

仍需继续：

- UC-12 的主权限边界已闭环，但规则运行、数据源配置、部分知识库管理等长尾业务操作仍需逐项补自动审计。
- 工作台 UC-13 仍标记为部分闭环，下一轮应补角色化指标范围和真实后端验收。

## 27. 2026-06-24 UC-13 工作台角色化指标真实后端闭环

本轮目标：补齐工作台“角色化指标范围”，让管理员查看全局指标，普通用户只查看个人范围指标和最近活动，同时清理工作台页面中文乱码。

已完成：

- `AuditApplicationService.dashboard()` 根据当前用户角色/权限决定指标范围：`ADMIN` 或 `audit:read` 为全局，普通用户按当前 `userId` 限定个人范围。
- `JdbcDashboardMetricsRepository` 增加按 `actorUserId` 收敛的指标查询：报告产出按 `reports.owner_user_id`，知识条目/排行按 `knowledge_bases.owner_user_id`，数据源按 `knowledge_data_sources.owner_user_id`，活跃用户按 `operation_logs.actor_user_id`。
- 最近活动与工作台指标同口径：普通用户只返回自己的 `operation_logs`，管理员返回全局最近活动。
- Dashboard 前端页面修复中文乱码，补充“个人指标/全局指标”口径提示。
- 新增 PostgreSQL 集成测试，插入两个用户的报告、知识库、数据源和审计日志后，验证个人指标只统计当前用户数据。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=AuditApplicationServiceTest#dashboardUsesPersonalScopeForNonAdminUsersAndGlobalScopeForAdmins" test` 先因 Dashboard 未向指标仓储传递 actor 范围失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=AuditApplicationServiceTest#dashboardUsesPersonalScopeForNonAdminUsersAndGlobalScopeForAdmins,AuditApplicationServiceTest#buildsDashboardOverviewFromBusinessTablesAndKeepsRecentAuditActivities" test` 通过 2 个测试。
- GREEN：`RUN_POSTGRES_INTEGRATION=true POSTGRES_IT_JDBC_URL=jdbc:postgresql://localhost:5432/intelligent_report POSTGRES_IT_USERNAME=report POSTGRES_IT_PASSWORD=report123 .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#buildsDashboardMetricsWithPersonalScopeInPostgres" test` 连接本地 Docker PostgreSQL，通过 1 个集成测试。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- audit-dashboard.spec.ts` 通过 2 个 Chromium E2E。

仍需继续：

- 工作台已按角色范围闭环；后续可补真实后端浏览器 E2E 验证不同 token 打开 `/dashboard` 的 UI 口径显示。
- UC-01 至 UC-08 仍有“部分闭环”项，下一轮应优先推进 UC-01/UC-03 的引用锚点落库与来源快照，或 UC-04 的高级导出版式。

## 32. 2026-06-24 UC-02 模板填报字段 schema 闭环

本轮目标：把模板填报从“提交一个 templateId + 任意 payload”推进到模板库、字段 schema、后端校验和前端动态填报的可验证闭环。

已完成：

- 新增 `ReportTemplate` 领域模型和 `ReportTemplateRepository`，模板字段包含 `fieldKey/label/type/required/options/defaultValue/helpText`。
- 新增 `report_templates` 表与 `V020__report_templates.sql`，内置 `enterprise-quarterly` 企业季度经营分析模板。
- 新增 `JdbcReportTemplateRepository`，生产环境从 PostgreSQL 读取 active 模板；保留内存仓储作为缺省开发兜底。
- `ReportApplicationService.createTemplateTask()` 在创建任务前读取模板，校验必填字段、select 枚举和 number 类型，并把模板 ID、名称、版本、字段定义和参数写入 `templateSnapshot`。
- 新增 `GET /api/v1/report-templates`，前端可获取模板库字段 schema。
- `ReportCreate.vue` 增加“模板填报”入口，从模板库动态渲染输入框、文本域和下拉选项，提交到 `/reports/template-generation-tasks`。

验证证据：

- RED：`npm run test -- apiContracts` 先因 `reportApi.listTemplates is not a function` 失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#listsActiveReportTemplatesWithFieldSchemaForTemplateFilling+rejectsTemplateTaskWhenRequiredSchemaFieldIsMissing+rejectsTemplateTaskWhenSelectValueIsOutsideTemplateOptions+createsTemplateTaskWithTemplateMetadataAndValidatedParameterSnapshot" test` 通过 4 个测试。
- GREEN：`npm run test -- apiContracts` 通过 15 个测试。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- report-generation.spec.ts` 通过 7 个 Chromium E2E，新增 UC-02 场景断言模板字段 payload。

仍需继续：

- 模板后台编辑、模板版本发布审批、字段 schema 可视化维护仍未做，当前交付切片先满足“选择模板 -> 填字段 -> 生成报告任务”的原型旅程。
- 企业导出版式模板和模板填报模板目前是两类能力，后续可在运营后台统一管理，但不阻塞 UC-02 生成闭环。


## 33. 2026-06-24 UC-07 企业数据源凭据与 PostgreSQL 抽取增量

本轮目标：把企业数据源从“保存配置和同步日志”推进到可验证的 PostgreSQL 手动抽取、知识条目导入和生产级凭据边界。

已完成：

- `KnowledgeDataSource` 增加 `username/credentialSecret/knowledgeBaseId/syncQuery/lastCursor`，Flyway `V021__knowledge_data_source_credentials_and_extraction.sql` 持久化对应字段。
- 新增 `DataSourceCredentialCodec`，使用 JDK 标准 AES-GCM 生成 `enc:v1:` 密文；`security.data-source-credential-key` 通过 dev/prod/env 配置注入，生产 profile 不再依赖默认 key。
- `KnowledgeApplicationService.saveDataSource()` 保存密码密文，不在 API 响应返回 `password/credentialSecret`，并校验目标知识库归属。
- `testConnection()` 执行真实 PostgreSQL JDBC 连接探测，避免仅凭 URL 前缀误报连接成功。
- `startDataSourceSync()` 支持从请求样例行或真实 PostgreSQL JDBC `syncQuery` 抽取数据，绑定知识库时导入为 `knowledge_items`，并发布 `knowledge.item.index_requested` 事件。
- 同步结果写入 `knowledge_data_source_sync_runs`，记录 `processedRows/failureReason/message/lastCursor`，并回写数据源 `lastCursor`。
- 保存数据源、测试连接和启动同步均写入 `operation_logs`，审计详情不包含密码、密文或明文凭据。
- 兼容旧 `enc:` Base64 包装数据源，允许历史数据源继续解码读取；新数据不再把 `sampleRows` 混入凭据字段。
- `DataSourceConfig.vue` 已恢复可读中文，支持用户名、密码、目标知识库 ID、同步 SQL 和凭据状态展示。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#savesDataSourceCredentialsWithoutReturningPlainSecret+syncsConfiguredRowsIntoKnowledgeItemsAndPublishesIndexEvents+decryptsLegacyBase64WrappedCredentialsForExistingDataSources" test` 先因新凭据仍是旧 `enc:` Base64 失败。
- GREEN：同命令通过 3 个测试，验证 `enc:v1:`、同步请求样例行导入、旧 `enc:` 兼容读取。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 14 个测试。
- PostgreSQL 集成：`RUN_POSTGRES_INTEGRATION=true POSTGRES_IT_JDBC_URL=jdbc:postgresql://localhost:5432/intelligent_report POSTGRES_IT_USERNAME=report POSTGRES_IT_PASSWORD=report123 .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway 迁移到 V021，验证 JDBC 连接探测、JDBC 抽取、知识条目导入、密文落库、同步日志和审计落库。
- 前端契约：`npm run test -- apiContracts` 通过 15 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- data-source-sync.spec.ts` 通过 1 个 Chromium E2E。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 已在第 34 节补齐按 `cursorColumn` 推进的 PostgreSQL 增量 cursor，在第 35 节补齐轻量定时调度器，在第 36 节补齐 HTTP API 连接器和字段映射，在第 37 节补齐数据库同步租约和请求级超时分类，在第 38 节补齐最大失败重试次数与人工重跑恢复，在第 39 节补齐前端 API/调度/重试配置入口，在第 40 节补齐 MySQL JDBC 代码路径，在第 59 节补齐真实 MySQL 容器烟测；失败告警和 HTTP API 高级配置也已在后续章节闭合。

## 34. 2026-06-24 UC-07 企业数据源增量游标闭环

本轮目标：把 UC-07 的 `lastCursor` 从“处理行数”升级为可验证的主键/时间戳游标推进，避免周期同步或人工二次同步重复导入旧数据。

已完成：

- `KnowledgeDataSource` 增加 `cursorColumn`，API 响应和前端配置页可保存、展示增量游标列。
- 新增 Flyway `V022__knowledge_data_source_incremental_cursor.sql`，持久化 `knowledge_data_sources.cursor_column`。
- `KnowledgeApplicationService.saveDataSource()` 对 `cursorColumn` 做安全 SQL 标识符校验，仅允许字母、数字和下划线，避免游标列拼接引入 SQL 注入风险。
- `startDataSourceSync()` 在配置游标列时复用统一游标过滤逻辑：样例行会过滤掉不大于旧 `lastCursor` 的行；真实 PostgreSQL JDBC 抽取会把 `syncQuery` 包装为子查询并下推 `WHERE incremental_source.<cursorColumn> > ? ORDER BY <cursorColumn>`。
- 同步成功后 `lastCursor` 更新为本轮同步行里的最大游标值；若本轮无新增行，则保留旧游标。
- PostgreSQL 集成测试覆盖第一次同步两行、插入第三行、第二次只同步第三行的真实数据库场景。

验证证据：

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#syncsOnlyRowsAfterConfiguredCursorColumnAndKeepsLatestCursor" test`：新增红绿用例通过，证明第二次同步只处理游标之后的新增行。
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test`：15 个 Java 目标测试通过。
- `$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test`：1 个真实 PostgreSQL 集成测试通过，Flyway 验证到 v022。
- `npm run test -- apiContracts`：15 个前端契约测试通过。
- `npm run typecheck`：Vue 3 + TypeScript 类型检查通过。

仍需继续：

- HTTP API 连接器和 JSON 字段映射已在第 36 节闭合，同步并发互斥和请求级超时分类已在第 37 节闭合，最大失败重试次数和人工重跑恢复已在第 38 节闭合，MySQL JDBC 代码路径已在第 40 节闭合，真实 MySQL 容器烟测已在第 59 节闭合；ERP/OA/财务系统专用适配器配置模板仍作为后续增强。
- 已在第 35 节补齐轻量定时调度器，并在第 39 节补齐基础前端配置；当前仍需增强失败告警、调度观测指标和 HTTP API 高级配置。
- 当前游标下推要求用户提供的 `syncQuery` 暴露 `cursorColumn`，后续可补字段映射预览和 SQL 校验提示。

## 35. 2026-06-24 UC-07 企业数据源轻量调度闭环

本轮目标：把企业数据源同步从纯手动推进到可本地开发验证的轻量定时调度，满足“可持续同步企业内部数据源”的生产雏形，同时保持本地默认不自动跑后台任务。

已完成：

- `KnowledgeDataSource` 增加 `scheduleEnabled/scheduleIntervalSeconds/nextRunAt/failureCount`，数据源保存响应会返回调度状态。
- 新增 Flyway `V023__knowledge_data_source_schedule.sql`，持久化调度字段，并为到期扫描建立部分索引。
- `KnowledgeBaseRepository` 增加 `findDueScheduledDataSources()` 与 `updateDataSourceScheduleState()`，JDBC 实现可按 `next_run_at` 查找到期任务并推进下次运行时间。
- 新增 `DataSourceSyncScheduler`，默认由 `knowledge.data-source.scheduler.enabled=false` 关闭；开启后通过 Spring `@Scheduled` 周期扫描到期数据源，调用 `runScheduledDataSourceSync()` 以数据源 owner 身份执行同步，避免后台线程缺少用户上下文。
- 调度成功后清零 `failureCount` 并按 `scheduleIntervalSeconds` 推进 `nextRunAt`；失败后增加 `failureCount` 并做基础退避，最高 1 小时。
- `.env.example`、`application-dev.yml`、`application-prod.yml` 增加 `DATA_SOURCE_SYNC_SCHEDULER_ENABLED` 和 `DATA_SOURCE_SYNC_SCHEDULER_DELAY_MS`，本地默认关闭，符合资源最小化原则。
- 前端 API 契约覆盖 `scheduleEnabled/scheduleIntervalSeconds` payload。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#savesScheduledDataSourceAndSchedulerRunsDueSyncThenMovesNextRunForward" test` 首次因缺少 `DataSourceSyncScheduler/nextRunAt/failureCount` 编译失败，证明测试覆盖新能力缺口。
- GREEN：同一目标测试通过，验证到期数据源被调度一次、写入 `scheduled` 同步日志、推进 `nextRunAt` 并清零 `failureCount`。
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test`：16 个 Java 目标测试通过。
- `$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test`：1 个真实 PostgreSQL 集成测试通过，Flyway 验证到 v023。
- `npm run test -- apiContracts`：15 个前端契约测试通过。
- `npm run typecheck`：Vue 3 + TypeScript 类型检查通过。
- `docker compose --env-file .env.example config --quiet`：通过。

仍需继续：

- HTTP API 连接器和 JSON 字段映射已在第 36 节闭合；MySQL JDBC 代码路径已在第 40 节闭合，真实 MySQL 容器烟测已在第 59 节闭合，ERP/OA/财务系统专用适配器配置模板仍作为后续增强。
- 当前失败处理已在第 38 节补齐可配置最大失败重试次数和人工重跑恢复；仍需补失败告警、更多失败原因分类和调度可观测指标。
- 同步并发互斥和 API 请求级超时分类已在第 37 节闭合，前端重试配置入口已在第 39 节闭合；仍缺告警和调度可观测指标。
- 前端页面可继续补完整调度配置控件和最近/下次运行状态展示。

## 36. 2026-06-24 UC-07 企业数据源 HTTP API 连接器与字段映射闭环

本轮目标：把企业数据源从 PostgreSQL-only 推进到可对接 ERP/OA/财务系统常见 HTTP API 的最小生产闭环，支持 token、JSON 字段映射和增量 cursor。

已完成：

- `KnowledgeDataSource` 增加 `fieldMappingJson`，Flyway `V024__knowledge_data_source_api_mapping.sql` 持久化 `knowledge_data_sources.field_mapping_json`。
- `sourceType=api` 的 `testConnection()` 使用 Java 17 `HttpClient` 发起 HTTP GET；保存了密码/token 时通过 `Authorization: Bearer <token>` 发送，不在响应或审计中泄露。
- `startDataSourceSync()` 对 API 响应 JSON 支持 `rowsPath/titleField/contentField` 字段映射，例如 `data.items/headline/body`，映射后导入 `knowledge_items` 并发布 `knowledge.item.index_requested`。
- API 数据源复用现有 `cursorColumn/lastCursor` 逻辑，第二次同步只导入游标之后的新行。
- 前端 `knowledgeApi.saveDataSource()` 增加 `SaveDataSourceRequest` 与 `DataSourceFieldMapping` 类型，API 契约测试覆盖 `fieldMapping` payload。
- PostgreSQL 集成测试验证 V024 迁移、`field_mapping_json` 落库和响应字段回显。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#testsHttpApiDataSourceConnectionWithBearerToken+syncsHttpApiRowsWithFieldMappingAndCursorIntoKnowledgeItems" test` 先因 `sourceType=api` 连接返回 `success=false`、同步返回 `unsupported or unreachable endpoint` 失败。
- GREEN：同一目标测试通过 2 个测试，验证 Bearer token、JSON 字段映射、API 同步入库、增量 cursor 和索引事件。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 18 个测试。
- PostgreSQL 集成：`$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway 从 v023 迁移到 v024。
- 前端契约：`npm run test -- apiContracts` 通过 15 个测试。
- 前端类型：`npm run typecheck` 通过。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- MySQL JDBC 代码路径已在第 40 节闭合，真实 MySQL 容器烟测已在第 59 节闭合。
- HTTP API 连接器当前是 GET + Bearer token 的最小闭环；后续需补 Basic/API Key/Header 配置、分页、POST body、响应预览和更细失败原因。
- 同步并发互斥和请求级超时分类已在第 37 节闭合，最大失败重试次数和人工重跑恢复已在第 38 节闭合；仍缺告警。
- `DataSourceConfig.vue` 已在第 39 节补齐 API 字段映射、调度和重试配置入口；HTTP API Header/API Key/Basic Auth、分页和响应预览仍需增强。

## 37. 2026-06-24 UC-07 企业数据源同步租约与超时分类闭环

本轮目标：把企业数据源同步从“可执行”推进到“可在多实例/定时场景下安全执行”，避免同一个数据源被手动同步和调度任务重复并发导入，并让 API 慢响应有明确失败分类。

已完成：

- 新增 Flyway `V025__knowledge_data_source_sync_lease.sql`，在 `knowledge_data_sources` 增加 `sync_locked_until`，并建立租约索引。
- `KnowledgeBaseRepository` 增加 `tryAcquireDataSourceSyncLease()` 与 `releaseDataSourceSyncLease()`；JDBC 实现使用条件 `UPDATE`，仅当锁为空或已过期时抢占成功，适配多实例部署。
- `KnowledgeApplicationService.startDataSourceSync()` 在抽取前抢占租约，抢不到时写入同步运行记录：`status=skipped`、`failureReason=sync already running`、`processedRows=0`。
- 同步成功或失败后都会释放租约，避免失败路径永久卡住数据源。
- API 数据源同步支持请求级 `timeoutMs`，默认 5 秒；超时异常归类为 `failureReason=timeout`，区别于普通不可达/不支持端点。
- PostgreSQL 集成测试验证服务同步后锁已释放、首次抢锁成功、重复抢锁失败、释放后可重新抢锁。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#rejectsDataSourceSyncWhenAnotherRunHoldsTheLease+recordsTimeoutFailureReasonWhenApiSyncExceedsConfiguredTimeout" test` 首次因仓储接口没有租约方法编译失败。
- GREEN：同一目标测试通过 2 个测试，验证锁占用写 `skipped`，API 超时写 `timeout` 且释放租约。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 20 个测试。
- PostgreSQL 集成：`$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway 从 v024 迁移到 v025。
- 前端契约：`npm run test -- apiContracts` 通过 15 个测试。
- 前端类型：`npm run typecheck` 通过。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- MySQL JDBC 代码路径已在第 40 节闭合，真实 MySQL 容器烟测已在第 59 节闭合。
- 当前 `timeoutMs` 是请求级参数，前端尚未提供配置控件；后续可持久化到数据源配置。
- 最大重试次数和人工重跑恢复已在第 38 节闭合；仍需补失败告警、人工重跑前端按钮和调度可观测指标。
- HTTP API 连接器仍可继续增强分页、POST body、Header/API Key/Basic Auth 配置和响应预览。

## 38. 2026-06-24 UC-07 企业数据源失败重试策略与人工重跑闭环

本轮目标：把企业数据源从“失败后基础退避”推进到可配置的最大失败重试策略，避免长期失败的数据源被调度器无限重试，同时保留人工修复配置后的受控重跑恢复路径。

已完成：

- 新增 Flyway `V026__knowledge_data_source_retry_policy.sql`，在 `knowledge_data_sources` 增加 `max_retry_count`，默认 3。
- `KnowledgeDataSource`、`KnowledgeApplicationService.saveDataSource()` 和 JDBC 仓储已持久化并返回 `maxRetryCount`，并限制范围为 `0..20`。
- `DataSourceSyncScheduler` 在扫描到期数据源时跳过 `failureCount >= maxRetryCount` 的记录，避免无限失败重试。
- `startDataSourceSync()` 支持 `mode=manual_retry`，人工重跑成功后清零 `failureCount` 并按调度间隔推进 `nextRunAt`。
- 前端 API 契约补充 `SaveDataSourceRequest.maxRetryCount`，确保 UI 后续接入不会丢字段。
- PostgreSQL 集成测试验证 V026、`max_retry_count=1` 落库、成功同步后 `nextRunAt` 被推进且 `failureCount=0`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#schedulerSkipsDataSourceAfterMaxRetryCountAndManualRetryClearsFailureState" test` 首次失败为第二次调度仍返回 1，证明调度器未按最大重试次数跳过。
- GREEN：同一目标测试通过，验证失败达到上限后调度跳过，`mode=manual_retry` 成功后清零失败计数并发布索引事件。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core clean test "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest"` 通过 21 个测试。
- PostgreSQL 集成：`$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway 当前为 V026。
- 前端契约：`npm run test -- apiContracts` 通过 15 个测试。
- 前端类型：`npm run typecheck` 通过。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- MySQL JDBC 代码路径已在第 40 节闭合，真实 MySQL 容器烟测已在第 59 节闭合。
- 失败告警和调度可观测指标仍需补齐；人工重跑前端按钮和最大重试次数 UI 控件已在第 39 节补齐。
- HTTP API 连接器仍可继续增强分页、POST body、Header/API Key/Basic Auth 配置和响应预览。

## 39. 2026-06-24 UC-07 企业数据源前端配置入口闭环

本轮目标：把后端已经具备的 API 字段映射、调度、最大失败重试次数和人工重跑能力暴露到数据源配置页，避免客户只能通过接口调用完成生产配置。

已完成：

- `DataSourceConfig.vue` 修复可见中文乱码，页面文案恢复为可交付中文。
- 数据源配置页新增 API 字段映射控件：`rowsPath/titleField/contentField`。
- 数据源配置页新增调度与重试控件：`scheduleEnabled/scheduleIntervalSeconds/maxRetryCount`。
- 保存数据源时按数据源类型构造 payload：API 数据源发送 `fieldMapping`，JDBC 数据源发送 `syncQuery`。
- 状态区展示凭据配置、游标列、同步间隔和最大重试次数。
- 新增“人工重跑”按钮，调用既有同步接口并发送 `mode=manual_retry`，和“启动同步”的 `mode=manual` 路径分开。

验证证据：

- RED：`npm run e2e -- data-source-sync.spec.ts` 首次失败在找不到“用户名或 Token 标识”等新增可读字段，证明页面未暴露本轮能力。
- GREEN：`npm run e2e -- data-source-sync.spec.ts` 通过 1 个 Chromium E2E，验证保存 payload 包含 `fieldMapping/scheduleEnabled/scheduleIntervalSeconds/maxRetryCount`，并验证“启动同步/人工重跑”分别发送 `mode=manual/manual_retry`。
- 前端契约：`npm run test -- apiContracts` 通过 15 个测试。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- MySQL JDBC 代码路径已在第 40 节闭合，真实 MySQL 容器烟测已在第 59 节闭合。
- 失败告警和调度可观测指标仍需补齐。
- HTTP API 连接器仍可继续增强分页、POST body、Header/API Key/Basic Auth 配置和响应预览。

## 40. 2026-06-24 UC-07 企业数据源 MySQL JDBC 连接器代码闭环

本轮目标：把企业数据源连接器从 PostgreSQL/API 扩展到 MySQL，满足 S1/S6 中“企业内部数据库、ERP、OA、财务系统等数据源”对 MySQL 数据库的基础同步要求。

已完成：

- `KnowledgeApplicationService` 的 JDBC 连接识别从 PostgreSQL-only 扩展为按 `sourceType` 支持 `postgresql/mysql`。
- `sourceType=mysql` 复用 JDBC 连接探测、SQL 抽取、`cursorColumn` 增量游标、知识条目导入和索引事件发布。
- 新增 `syncsMysqlDataSourceRowsWithJdbcConnectorAndCursor`，用 H2 MySQL 模式验证 MySQL SQL 方言路径：首次同步导入 2 行，第二次同步只导入游标之后的 1 行。
- 当时检查本地 Docker 和 compose 未发现 MySQL/MariaDB 容器；该历史缺口已在第 59 节通过最小 MySQL 容器和真实集成测试闭合。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#syncsMysqlDataSourceRowsWithJdbcConnectorAndCursor" test` 首次失败为 `sourceType=mysql` 连接探测 `success=false`，证明原实现仍被 PostgreSQL 前缀限制。
- GREEN：同一目标测试通过，验证 MySQL JDBC 代码路径、游标推进、导入来源类型 `data_source:mysql` 和 3 条索引事件。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 22 个测试。

仍需继续：

- 真实 MySQL/MariaDB 容器烟测已在第 59 节闭合。
- 失败告警和调度可观测指标仍需补齐。
- HTTP API 连接器仍可继续增强分页、POST body、Header/API Key/Basic Auth 配置和响应预览。

## 41. 2026-06-24 UC-07 企业数据源失败系统告警闭环

本轮目标：把企业数据源同步失败从“同步日志可查”推进到“用户可收到未读系统告警”，避免生产环境里数据源长期失败但无人感知。

已完成：

- 新增独立 `system_alerts` 表和 Flyway `V027__system_alerts.sql`，不复用强绑定报告协作外键的 `collaboration_notifications`。
- 新增 `SystemAlert` 领域模型、`SystemAlertRepository` 和 `JdbcSystemAlertRepository`，payload 使用 PostgreSQL JSONB 保存。
- 新增 `SystemAlertApplicationService` 与 `GET /api/v1/system-alerts`，按当前用户分页查询系统告警，可按 `status` 过滤。
- `KnowledgeApplicationService.startDataSourceSync()` 在失败同步运行落库后写入 `knowledge_data_source_sync_failed` 未读告警。
- 告警 payload 包含 `dataSourceId/dataSourceName/sourceType/failureReason/syncRunId/mode`，明确不写入 `password/credentialSecret`。
- 前端新增 `notificationApi.listSystemAlerts()` 契约，供后续消息中心或工作台未读提醒接入。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#createsUnreadSystemAlertWhenDataSourceSyncFailsWithoutLeakingSecrets" test` 首次因缺少 `SystemAlertRepository/SystemAlert` 和服务构造器失败。
- GREEN：同一目标测试通过，验证失败同步创建未读告警且 payload 不泄露明文密钥。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 23 个测试。
- PostgreSQL 集成：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsAndSearchesKnowledgeBasesAndItemsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway 从 V026 迁移到 V027，并验证 `system_alerts` 告警 JSONB 落库。
- 前端契约：`npm run test -- apiContracts` 通过 16 个测试。
- 前端类型：`npm run typecheck` 通过。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 真实 MySQL/MariaDB 容器烟测已在第 59 节闭合。
- HTTP API 连接器仍需增强 Header/API Key/Basic Auth、分页、POST body 和响应预览。
- 后续可在 Dashboard 或消息中心展示未读系统告警数量，并增加已读/批量已读接口。

## 42. 2026-06-24 UC-07 企业数据源 HTTP API 高级配置闭环

本轮目标：把 HTTP API 数据源从“GET + Bearer + 字段映射”的最小连接器升级为可接入常见 ERP/OA/财务系统接口的高级配置能力。

已完成：

- `fieldMapping` 扩展支持 `method/authType/apiKeyHeader/headers/bodyTemplate/pageParam/pageStart/pageSizeParam/pageSize/maxPages`。
- 后端 API 数据源支持 GET/POST，POST 时发送 JSON Body。
- 后端支持 Bearer Token、API Key Header、Basic Auth 和无认证模式。
- 后端支持自定义业务 Header，并阻止通过配置覆盖 `Authorization/Content-Length/Host` 等敏感或协议级 Header。
- 后端支持按页码参数和每页数量参数连续拉取多页，再统一执行字段映射、游标过滤和知识条目导入。
- `DataSourceConfig.vue` 重写为可读中文，补齐 API 请求方法、认证方式、API Key Header、自定义 Header、POST Body、分页配置、字段映射、调度和重试配置。
- `data-source-sync.spec.ts` 改为可读中文 E2E，并断言高级 API 配置 payload。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#syncsHttpApiRowsWithPostBodyCustomHeadersAndApiKey+syncsHttpApiRowsAcrossConfiguredPages" test` 首次失败为 API 仍发送 GET 且只处理 1 页。
- GREEN：同一目标测试通过 2 个测试，验证 POST Body、自定义 Header、API Key Header 和两页同步。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 25 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 16 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- data-source-sync.spec.ts` 通过 1 个 Chromium E2E。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 真实 MySQL/MariaDB 容器烟测已在第 59 节闭合。
- 后续可补响应预览、分页终止条件和消息中心未读告警展示。

## 43. 2026-06-24 UC-04 PDF 导出增量

本轮目标：把 UC-04 从 Markdown/DOCX 继续推进到 PDF 可下载闭环，避免前端展示 PDF 但后端无法生成真实文件。

已完成：

- `ReportApplicationService.createExport()` 支持 `pdf` 格式，文件名使用 `.pdf`，content type 使用 `application/pdf`。
- PDF 导出生成最小 PDF 1.4 文件，包含报告标题、章节标题、正文和引用摘要，文件字节以 `%PDF-` 开头并以 `%%EOF` 结束。
- PDF 导出继续复用当前 MinIO 写入、导出记录持久化、受控下载 URL 和导出审计链路。
- `ReportDetail.vue` 放开 PDF 导出选项，保留 PPT 暂未支持提示，避免把未闭合格式伪装成可交付。
- 前端契约和 E2E 已覆盖 PDF 导出 payload、`.pdf` 文件名反馈和报告详情导出流程。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument" test` 先因 `unsupported export format for current delivery slice: pdf` 失败。
- GREEN：同一目标测试通过，验证 PDF MIME、文件名、PDF 头和 EOF。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 29 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 17 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- report-generation.spec.ts` 通过 7 个 Chromium E2E。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 当前 PDF 是最小可打开文档，未实现中文字体嵌入、真实 Logo、页眉页脚、目录页码和企业视觉规范。
- PPT 仍未实现真实格式生成。
- 高级 Word/PDF 企业版式应统一从企业导出版式模板读取，避免 Word 与 PDF 两套配置漂移。

## 44. 2026-06-24 UC-06 文档解析失败可观测增量

本轮目标：在 OCR、表格识别和扫描件解析尚未接入生产组件前，先保证不可解析文档不会长期停留在 `pending`，也不会被伪装成解析成功。

已完成：

- `DocumentParseIndexingService.handle()` 捕获对象加载和解析异常，转为 `document.parse.failed` 事件。
- 失败 payload 包含 `documentId/status=failed/failureReason/knowledgeChunks=[]`。
- 失败结果仍写入 `document_parse_results`，并回写 `knowledge_documents.parse_status=failed` 与 `parse_failure_reason`。
- 失败结果明确 `searchIndexStatus=skipped`，不会写入 `document_chunks/embeddings/OpenSearch/Milvus`。
- 保留正常文本解析链路：TXT/CSV/MD/JSON 等文本对象仍可生成 chunk、embedding、全文索引和向量索引。

验证证据：

- RED：`python -m pytest tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_persists_failed_status_when_ocr_is_required -q` 首次因 `ValueError: document knowledge/scanned.pdf requires OCR or table extraction before text parsing` 冒泡失败。
- GREEN：同一目标测试通过，验证失败事件、失败原因、parse result、document status 和无 chunk 写入。
- Python 回归：`python -m pytest tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_consumer.py tests/unit/python/test_document_parse_worker.py tests/unit/python/test_minio_object_loader.py -q` 通过 14 个测试。

仍需继续：

- 生产级 OCR、图片表格识别和扫描件解析尚未实现。
- 前端上传页目前只展示上传返回的 `pending`，后续应补文档状态轮询/列表，让用户看到异步解析后的 `processed/failed` 和失败原因。
- 如引入 OCR 服务，必须按本地开发环境约束选择低资源 Docker 方案，并把未配置时的降级状态保留下来。

## 45. 2026-06-24 UC-06 文档解析状态查询与前端反馈增量

本轮目标：把“上传成功后等待异步解析”的用户体验补齐，用户不应只看到上传接口返回的 `pending`，而应能看到 worker 后续写回的 `processed/failed` 和失败原因。

已完成：

- `KnowledgeDocumentRepository` 新增 `findById(documentId)`。
- `JdbcKnowledgeDocumentRepository.findById()` 联查 `knowledge_documents` 与 `file_objects`，返回文件名、对象存储位置、解析状态和 `parse_failure_reason`。
- `StoredDocument` 增加 `parseFailureReason`，并保留旧构造器兼容现有测试和调用点。
- `KnowledgeApplicationService.getDocumentStatus()` 读取文档状态，并通过 `knowledgeBaseId` 校验当前用户只能查看自己知识库下的上传文档。
- `KnowledgeController` 新增 `GET /api/v1/documents/{documentId}`。
- `knowledgeApi.getDocumentStatus()` 接入前端契约。
- `KnowledgeUpload.vue` 修复页面可见中文乱码，上传后轮询状态接口，终态为 `failed` 时展示 `parseFailureReason`。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#getsUploadedDocumentStatusForOwningKnowledgeBase" test` 首次因缺少 `getDocumentStatus()`、`findById()` 和 `parseFailureReason` 字段编译失败。
- 后端 GREEN：同一目标测试通过，验证 owner 范围下可读取失败状态和失败原因。
- 前端契约 RED：`npm run test -- apiContracts` 首次因 `knowledgeApi.getDocumentStatus is not a function` 失败。
- 前端契约 GREEN：`npm run test -- apiContracts` 通过 18 个测试。
- 前端 E2E RED：`npm run e2e -- knowledge-upload.spec.ts` 首次找不到 `scan.pdf failed`。
- 前端 E2E GREEN：`npm run e2e -- knowledge-upload.spec.ts` 通过 1 个 Chromium E2E，验证上传后轮询并展示 OCR 缺失失败原因。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 26 个测试。
- Python 回归：`python -m pytest tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_consumer.py tests/unit/python/test_document_parse_worker.py tests/unit/python/test_minio_object_loader.py -q` 通过 14 个测试。
- 前端类型：`npm run typecheck` 通过。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 文档列表页或上传历史页尚未实现，当前只在上传完成后的页面内轮询单个文档。
- 生产级 OCR、图片表格识别和扫描件解析仍是 UC-06 最大缺口。

## 46. 2026-06-24 UC-04 PPTX 导出增量

本轮目标：把报告详情页中仍禁用的 PPT 导出补齐为真实 PPTX 文件生成，避免 UC-04 只覆盖 Word/PDF/Markdown 而缺少演示汇报格式。

已完成：

- `ReportApplicationService.createExport()` 支持 `ppt/pptx/powerpoint`，统一归一化为 `pptx`。
- PPTX 导出生成 OOXML ZIP，包含 `[Content_Types].xml`、`_rels/.rels`、`docProps/core.xml`、`ppt/presentation.xml`、`ppt/_rels/presentation.xml.rels`、`ppt/slides/slide1.xml` 和 slide relationships。
- PPTX 内容包含报告标题、章节标题、正文和引用摘要，并继续复用当前 MinIO 写入、导出文件持久化、受控下载 URL 与导出审计链路。
- 报告详情页放开 PPT 导出选项，提交 payload 为 `format=pptx`、`templateId=enterprise-board`，不携带 Word 专属 `brand` 字段。
- 前端 API 契约新增 PPTX 导出 payload 断言。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 首次因 `unsupported export format for current delivery slice: pptx` 失败。
- 后端 GREEN：同一目标测试通过，验证 PPTX MIME、`.pptx` 文件名、ZIP 头、slide XML 内容和 content type 部件。
- 前端 E2E RED：`npm run e2e -- report-generation.spec.ts` 首次因 PPT 按钮 disabled 失败。
- 前端 E2E GREEN：`npm run e2e -- report-generation.spec.ts` 通过 7 个 Chromium E2E，覆盖 PPT 按钮启用、导出 payload 和 `.pptx` 成功反馈。
- 前端契约：`npm run test -- apiContracts` 通过 19 个测试。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- 当前 PPTX 是最小可打开演示文稿，未实现企业主题、母版、Logo、页眉页脚、目录页、分页和图表排版。
- 高级 Word/PDF/PPT 企业版式应统一从企业导出版式模板读取，避免多格式配置漂移。

## 47. 2026-06-24 UC-08 规则发布审批与生产运行增量

本轮目标：把规则从“可保存、可调试”推进到具备最小生产发布边界，避免草稿规则直接进入生产执行。

已完成：

- `RuleApplicationService` 新增 `submitForReview()`、`approve()` 和 `execute()`。
- 规则状态流收敛为 `draft -> pending_review -> published`：只有草稿可提交审核，只有待审核规则可审批发布。
- 生产运行 `execute()` 只允许 `published` 规则执行；非发布规则返回 `rule must be published before production execution`。
- 生产运行复用现有规则定义校验和 condition 节点执行逻辑，运行结果包含 `runType=production/versionId/output`，并保存运行记录。
- 规则提交审核、审批发布和生产运行分别写入 `rule_review_submit`、`rule_publish_approve`、`rule_production_run` 审计，记录操作者、规则资源、状态、意见、运行类型和命中结果。
- REST API 新增 `/rules/{ruleId}/review-submissions`、`/rules/{ruleId}/approvals` 和 `/rules/{ruleId}/runs`。
- 前端规则编排页新增提交审核、审批发布、生产运行按钮，并按状态禁用不可用动作。
- 前端 API 契约和 Playwright E2E 覆盖发布审批 payload、状态流和生产运行结果展示。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#requiresReviewApprovalBeforeRuleCanRunInProduction" test` 首次因缺少 `execute/submitForReview/approve` 方法编译失败。
- 后端 GREEN：同一目标测试通过，验证草稿拒绝生产运行、提交审核、审批发布和发布后生产命中。
- 前端契约 RED：`npm run test -- apiContracts` 首次因 `ruleApi.submitForReview is not a function` 失败。
- 前端契约 GREEN：`npm run test -- apiContracts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，覆盖规则画布、审批发布、生产运行和调试样本。

仍需继续：

- 当前审批意见已进入审计日志，但未单独持久化到规则审批表；若客户要求审批流查询、撤回、驳回和多级审批，需要扩展 `rule_approval_records`。
- 生产调度、复杂节点、完整画布编辑器和规则监控指标仍未闭合。

## 48. 2026-06-24 UC-08/UC-12 规则审批与生产运行审计增量

本轮目标：把规则提交审核、审批发布和生产运行纳入统一审计链路，避免生产规则操作只改变状态而缺少合规留痕。

已完成：

- `RuleApplicationService` 接入 `AuditRepository`，Spring 运行时使用真实审计仓储，轻量测试仍保留 noop 审计仓储兼容。
- `submitForReview()` 写入 `rule_review_submit` 审计，detail 包含 `status=pending_review` 和提交意见。
- `approve()` 写入 `rule_publish_approve` 审计，detail 包含 `status=published` 和审批意见。
- `execute()` 写入 `rule_production_run` 审计，detail 包含 `runType=production`、`versionId` 和 `matched` 命中结果。
- 审计操作者来自 `CurrentUserHolder`，无登录上下文时使用系统默认用户 `1L`，避免后台任务或测试上下文空指针。
- 审计 detail 使用可容忍空值的 `LinkedHashMap`，避免可选意见为空时 `Map.of` 抛出 NPE。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#writesAuditLogsForReviewApprovalAndProductionRun" test` 首次因缺少带 `AuditRepository` 的构造器编译失败。
- GREEN：同一目标测试通过 1 个测试，验证三条审计日志的操作类型、操作者、资源、结果和 detail。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 10 个测试，验证规则服务和 Spring 应用上下文未被审计注入破坏。

仍需继续：

- 若客户要求审批流台账，需要新增规则审批记录表，而不是仅依赖通用 `operation_logs`。
- 规则生产调度、复杂节点、完整画布编辑器和运行监控指标仍是 UC-08 的主要交付缺口。

## 49. 2026-06-24 UC-08 规则运行历史监控增量

本轮目标：让规则生产运行不只返回一次性结果，还能在规则页面和 API 中查询最近运行记录，为生产监控和问题追溯打底。

已完成：

- `RuleRepository` 新增 `findRuns(ruleId, page, pageSize)` 和 `countRuns(ruleId)`。
- `InMemoryRuleRepository` 保存并分页返回运行记录，支持应用层测试。
- `JdbcRuleRepository` 基于既有 `rule_debug_runs` 表分页查询运行记录，按 `id DESC` 返回最新运行。
- `RuleApplicationService.listRuns()` 返回 `PageResponse`，每条包含 `runId/ruleId/versionId/status/runType/matched/input/output`，且只返回 `run_type=production` 的生产记录。
- `RuleController` 新增 `GET /api/v1/rules/{ruleId}/runs`，继续受 `rule:debug` 权限保护。
- 前端 `ruleApi.listRuns()` 接入运行历史接口，规则页面选择规则和生产运行后会刷新最近运行记录。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsProductionRunHistoryForRuleMonitoring" test` 首次因缺少 `listRuns()` 编译失败。
- 后端 GREEN：同一目标测试通过 1 个测试，验证生产运行后可分页查询运行记录和命中结果。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 12 个测试，Spring 注册控制器映射数为 56。
- 前端契约 RED：`npm run test -- apiContracts` 首次因 `ruleApi.listRuns is not a function` 失败。
- 前端契约 GREEN：`npm run test -- apiContracts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，覆盖规则页面审批发布、生产运行和调试链路，并 mock 运行历史查询接口。

仍需继续：

- 当前运行历史已通过 V028 `rule_debug_runs.run_type` 区分 `debug/production`，并新增 `(rule_id, run_type, id desc)` 查询索引；若客户需要更严谨运行台账，后续可继续补 `trigger_type/triggered_by/duration_ms/error_message` 字段或独立 `rule_runs` 表。
- 运行监控指标、失败重试、生产调度和复杂节点仍需后续闭合。

## 50. 2026-06-24 UC-08 规则运行类型隔离增量

本轮目标：把规则调试运行和生产运行从同一历史列表中拆开，避免调试样本污染生产运行台账。

已完成：

- `RuleDebugRun` 增加 `runType`，并提供 `succeededDebug()` 与 `succeededProduction()` 工厂方法。
- `debug()` 写入 `run_type=debug`，`execute()` 写入 `run_type=production`。
- `InMemoryRuleRepository.findRuns/countRuns` 只返回生产运行。
- `JdbcRuleRepository` 写入、读取并按 `run_type='production'` 过滤运行历史。
- 新增 Flyway `V028__rule_run_type.sql`，为 `rule_debug_runs` 增加 `run_type` 默认值和 `(rule_id, run_type, id DESC)` 索引。
- 前端 E2E mock 覆盖运行历史查询，并断言页面显示生产运行记录。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#excludesDebugRunsFromProductionRunHistory" test` 首次失败为 `expected: 1L but was: 2L`，证明 debug run 混入了生产历史。
- GREEN：同一目标测试通过 1 个测试。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 12 个测试。
- PostgreSQL 集成：`RUN_POSTGRES_INTEGRATION=true POSTGRES_IT_JDBC_URL=jdbc:postgresql://localhost:5432/intelligent_report POSTGRES_IT_USERNAME=report POSTGRES_IT_PASSWORD=report123 .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个测试，Flyway 从 V027 迁移到 V028，并验证调试运行落库 `run_type=debug`。

仍需继续：

- 生产运行已补 `triggeredByUserId/durationMs/errorMessage` 基础可观测字段；失败运行落库和聚合指标仍需继续。
- 规则调度、复杂节点和完整画布编辑器仍是 UC-08 的主要缺口。

## 51. 2026-06-24 UC-08 规则生产运行可观测字段增量

本轮目标：把规则生产运行历史从“有记录”提升为可审计、可追溯的生产台账，补齐操作者、耗时和错误信息字段。

已完成：

- `RuleDebugRun` 增加 `triggeredByUserId/durationMs/errorMessage` 字段，生产运行由 `CurrentUserHolder` 写入操作者。
- `RuleApplicationService.execute()` 记录生产执行耗时，并在执行结果和 `listRuns()` 历史响应中返回可观测字段。
- `JdbcRuleRepository` 写入、读取新字段，运行历史仍只查询 `run_type=production`。
- 新增 Flyway `V029__rule_run_observability.sql`，为 `rule_debug_runs` 增加 `triggered_by_user_id/duration_ms/error_message` 和 `(rule_id, run_type, status, id DESC)` 索引。
- 前端 `RuleDesigner.vue` 类型模型保留生产运行可观测字段，乐观回填运行历史时同步保存这些字段。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsProductionRunHistoryForRuleMonitoring" test` 首次失败为缺少 `triggeredByUserId`。
- GREEN：同一目标测试通过 1 个测试，验证运行历史返回 `triggeredByUserId=101`、`durationMs>=0` 和 `errorMessage=null`。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` 通过 7 个测试。
- PostgreSQL 集成：`RUN_POSTGRES_INTEGRATION=true POSTGRES_IT_JDBC_URL=jdbc:postgresql://localhost:5432/intelligent_report POSTGRES_IT_USERNAME=report POSTGRES_IT_PASSWORD=report123 .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个测试，Flyway 从 V028 迁移到 V029，并验证生产运行新列持久化。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- 当前仅覆盖成功生产运行台账；失败生产运行是否落库为 `status=failed/errorMessage` 还需下一轮设计和 TDD。
- 规则生产调度、复杂节点、失败重试和聚合监控指标仍未闭合。

## 52. 2026-06-24 UC-08 规则失败生产运行台账增量

本轮目标：避免生产规则执行失败时只返回异常而不留运行记录，保证客户现场排障、审计追溯和监控统计不断链。

已完成：

- `RuleDebugRun` 增加 `failedProduction()` 工厂方法，失败生产运行记录 `status=failed`、`errorMessage`、操作者和耗时。
- `RuleApplicationService.execute()` 在 published 规则执行失败时保存 failed production run，并保持原业务异常继续向上抛出。
- 失败生产运行同步写入 `rule_production_run` 审计，`result=failed`，detail 包含 `runId/versionId/errorMessage`。
- 运行历史查询继续只返回 `run_type=production`，成功和失败记录都会纳入分页总数。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#recordsFailedProductionRunsForRuleMonitoring" test` 首次有效失败为 `expected: 1L but was: 0L`，证明失败执行没有落台账。
- GREEN：同一目标测试通过 1 个测试，验证 failed run 返回 `triggeredByUserId=102`、`matched=null` 和可读 `errorMessage`。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` 通过 8 个测试。
- PostgreSQL 集成回归：`RUN_POSTGRES_INTEGRATION=true POSTGRES_IT_JDBC_URL=jdbc:postgresql://localhost:5432/intelligent_report POSTGRES_IT_USERNAME=report POSTGRES_IT_PASSWORD=report123 .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个测试，确认 V029 schema 当前可用。

仍需继续：

- 规则生产调度、复杂节点、失败重试策略、聚合监控指标和完整画布编辑器仍未闭合。

## 53. 2026-06-24 UC-08 规则生产运行聚合监控指标增量

本轮目标：在已有生产运行台账基础上提供规则级聚合指标，让客户可以快速判断某条规则的运行健康度，而不是只能逐条翻运行历史。

已完成：

- `RuleApplicationService.metrics(ruleId)` 基于生产运行历史聚合 `totalRuns/succeededRuns/failedRuns/successRate/averageDurationMs/lastStatus/lastErrorMessage`。
- `RuleController` 新增 `GET /api/v1/rules/{ruleId}/metrics`，继续使用 `rule:debug` 权限保护。
- 前端 `ruleApi.metrics(ruleId)` 接入新接口，API 契约覆盖 `/rules/{ruleId}/metrics`。
- 真实 PostgreSQL 集成测试在 JDBC 仓储路径上验证 metrics 可基于 `rule_debug_runs` 聚合。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#summarizesProductionRunMetricsForMonitoring" test` 首次因缺少 `metrics(Long)` 编译失败。
- 前端 RED：`npm run test -- apiContracts` 首次因 `ruleApi.metrics is not a function` 失败。
- 后端 GREEN：同一目标测试通过 1 个测试，验证成功/失败各 1 次时 `successRate=0.5` 且最近状态为 `failed`。
- 前端 GREEN：`npm run test -- apiContracts` 通过 20 个测试。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 14 个测试，Spring 映射数增至 57。
- PostgreSQL 集成：`RUN_POSTGRES_INTEGRATION=true POSTGRES_IT_JDBC_URL=jdbc:postgresql://localhost:5432/intelligent_report POSTGRES_IT_USERNAME=report POSTGRES_IT_PASSWORD=report123 .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个测试，当前 schema v029。
- 前端类型：`npm run typecheck` 通过。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 当前 metrics 在服务层读取最近最多 1000 条生产运行后聚合；大规模生产数据需要后续下推 SQL 聚合或预聚合表。
- 规则生产调度已在第 54 节闭合本地轻量版本；复杂节点、调度失败告警、分布式锁和完整画布编辑器仍未闭合。

## 54. 2026-06-24 UC-08 规则生产轻量调度闭环

本轮目标：把已发布规则从“只能人工生产运行”推进到可本地开发验证的轻量生产调度，同时保持默认关闭，避免开发环境后台任务误跑。

已完成：

- `Rule` 增加 `scheduleEnabled/scheduleIntervalSeconds/nextRunAt/failureCount/maxRetryCount/scheduleInput`，保存与列表响应返回调度状态。
- 新增 Flyway `V030__rule_schedule.sql`，持久化规则调度字段，并为 due scan 建立 `idx_rules_schedule_due` 索引。
- `RuleApplicationService.configureSchedule()` 提供 `PUT /api/v1/rules/{ruleId}/schedule`，仅允许 published 规则开启调度，校验最小间隔和最大失败重试次数。
- 新增 `RuleProductionScheduler`，默认由 `rule.production.scheduler.enabled=false` 关闭；开启后通过 `@Scheduled` 扫描到期 published 规则，调用已有生产执行链路，成功后清零失败计数并推进 `nextRunAt`，失败后按基础退避增加 `failureCount`。
- `RuleProductionScheduler.runDueRulesOnce()` 公开为一次性触发入口，便于本地开发、测试和受控运维烟测复用，不改变默认关闭策略。
- 前端 `ruleApi.configureSchedule()` 已补契约测试，API payload 使用 JSON 结构描述调度参数。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#schedulerRunsDuePublishedRulesAndMovesNextRunForward" test` 首次因缺少调度器、调度字段和配置 API 编译失败。
- GREEN：同一目标测试通过，验证 due published rule 被调度一次，写入 production run，命中结果为 `matched=true`，并推进下一次运行时间。
- PostgreSQL RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 首次因 `runDueRulesOnce()` 非公共方法无法跨包调用而编译失败。
- PostgreSQL GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway 校验 schema v030，并验证调度字段、`schedule_input_json`、due scan、调度生产运行台账和 `next_run_at` 推进。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 15 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 多实例重复执行风险已在第 55 节用数据库租约闭合；调度失败仍仅记录运行台账和失败计数，仍需补系统告警、失败重试上限后的人工恢复入口和 Dashboard 监控聚合。
- 规则复杂节点类型与完整画布编辑器仍是 UC-08 主要交付缺口。

## 55. 2026-06-24 UC-08 规则调度数据库租约闭环

本轮目标：把规则生产调度从“单实例轻量调度”推进到可在多实例部署中避免重复执行同一到期规则，补齐客户生产环境最基础的调度互斥能力。

已完成：

- 新增 Flyway `V031__rule_schedule_lease.sql`，为 `rules` 增加 `schedule_locked_until` 并建立锁字段索引。
- `RuleRepository` 增加 `tryAcquireRuleScheduleLease()` 和 `releaseRuleScheduleLease()`，内存仓库与 JDBC 仓库均实现相同语义。
- `JdbcRuleRepository.findDueScheduledRules()` 和内存仓库 due scan 均排除未过期锁，避免调度器枚举正在被其他实例处理的规则。
- `RuleProductionScheduler` 执行前抢占数据库租约，抢不到时跳过且不计入 processed；抢到后无论成功或失败都会在 finally 中释放租约。
- PostgreSQL 集成测试对共享测试库做调度隔离，避免历史 due rule 污染当前调度断言。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#schedulerSkipsRuleWhenAnotherSchedulerHoldsTheLease" test` 首次因缺少 `tryAcquireRuleScheduleLease()` 编译失败。
- GREEN：同一目标测试通过 1 个测试，验证锁被占用时调度器返回 0，且不会写入生产运行台账或增加失败计数。
- PostgreSQL RED：`$env:RUN_POSTGRES_INTEGRATION='true'; ... "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 首次失败为锁住后仍调度 2 条，暴露 JDBC due scan 未排除 `schedule_locked_until`；修复后又因共享测试库残留 due rules 导致 3 条，被测试隔离修正。
- PostgreSQL GREEN：同一真实 PostgreSQL 测试通过 1 个测试，Flyway schema v031，并验证锁住时跳过、释放后执行一次、`schedule_locked_until` 最终为空、生产运行台账总数正确。

仍需继续：

- 调度失败告警已在第 56 节闭合；失败上限后的人工恢复入口已在第 57 节闭合；Dashboard 规则调度健康指标已在第 58 节闭合。
- 复杂规则节点类型与完整画布编辑器仍需继续按原型补齐。

## 56. 2026-06-24 UC-08 规则调度失败系统告警闭环

本轮目标：让规则调度失败不只停留在运行台账和失败计数中，还能进入统一系统告警，便于客户现场发现、排查和处理失败规则。

已完成：

- `RuleApplicationService` 增加可注入 `SystemAlertRepository`，默认构造保持 noop，Spring/测试可注入真实 JDBC 告警仓储。
- `RuleProductionScheduler` 在调度执行失败后读取最新 failed production run，调用 `createScheduledFailureAlert()` 写入系统告警。
- 告警类型为 `rule_schedule_run_failed`，`severity=warning/status=unread/resourceType=rule`，payload 包含 `ruleId/ruleName/runId/errorMessage/failureCount/maxRetryCount/nextRunAt`。
- 告警 payload 明确不包含 `scheduleInput/input/sample/secretPrompt` 等调度输入或敏感字段。
- PostgreSQL 集成测试使用 `JdbcSystemAlertRepository` 验证告警真实落入 `system_alerts`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#schedulerCreatesUnreadSystemAlertWhenScheduledRuleFailsWithoutLeakingInput" test` 首次因缺少四参构造器编译失败，证明规则服务无法注入告警仓储。
- 修正测试场景 RED：同一测试随后因调度输入未触发失败而红，调整为已发布规则定义被存储层污染成非法 operator 后，稳定覆盖 failed production run 和告警路径。
- GREEN：同一目标测试通过 1 个测试，验证调度失败写入 failed production run，并生成不泄漏输入的 unread warning 告警。
- PostgreSQL RED：真实库测试首次因 `payload_json ? 'runId'` 被 JDBC 当作占位符失败，修正为文本键名断言；随后因测试服务仍使用 noop 告警仓储查不到告警，改为注入 `JdbcSystemAlertRepository`。
- PostgreSQL GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，验证 `system_alerts` 中存在 `rule_schedule_run_failed`，且 payload 不包含 `do-not-leak/secretPrompt/scheduleInput`。

仍需继续：

- Dashboard 规则调度健康指标已在第 58 节补齐基础 SQL 聚合；大规模生产数据仍需预聚合或物化视图。
- 复杂规则节点类型与完整画布编辑器仍需继续按原型补齐。

## 57. 2026-06-24 UC-08 规则调度失败上限人工恢复闭环

本轮目标：当规则调度连续失败达到 `maxRetryCount` 后，提供受权限保护的人工恢复入口，让用户在修正规则或输入后可清零失败计数并重新安排下一次调度，而不是只能改数据库。

已完成：

- `RuleApplicationService.retrySchedule(ruleId, request)` 校验规则已发布且调度已开启，清零 `failureCount`，按请求或当前时间设置 `nextRunAt`，可选更新 `scheduleInput`。
- `RuleController` 新增 `POST /api/v1/rules/{ruleId}/schedule/retry`，继续使用 `rule:manage` 权限保护。
- 人工恢复会写入 `rule_schedule_retry` 审计，detail 包含规则状态和恢复后的调度时间。
- 前端 `ruleApi.retrySchedule()` 接入恢复接口，规则页面在 published、调度开启且 `failureCount >= maxRetryCount` 时显示“人工重试调度”入口，并用当前调试样本作为新的 `scheduleInput`。
- PostgreSQL 集成测试验证恢复后 `failure_count=0`、`next_run_at` 可立即到期、`schedule_input_json` 更新且 `operation_logs.operation_type=rule_schedule_retry`。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#manualRetryClearsFailedScheduleStateAfterMaxRetryCount" test` 首次因缺少 `retrySchedule()` 编译失败。
- 后端 GREEN：同一目标测试通过 1 个测试，验证达到失败上限后的调度可通过人工恢复清零失败计数并重新安排。
- 前端 RED：`npm run test -- apiContracts` 首次因 `ruleApi.retrySchedule is not a function` 失败。
- 前端 GREEN：`npm run test -- apiContracts` 通过 20 个测试，覆盖 `/rules/{ruleId}/schedule/retry` JSON payload。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 18 个测试，Spring 映射数增至 59。
- PostgreSQL 集成：`$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway schema v031。
- 前端类型：`npm run typecheck` 通过。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- Dashboard 规则调度健康指标已在第 58 节补齐基础 SQL 聚合。
- 规则级 metrics 基础 SQL 下推已在第 65 节闭合；超大规模生产数据后续可继续补预聚合表或物化视图。
- 复杂规则节点类型与完整画布编辑器仍需继续按原型补齐。

## 58. 2026-06-24 UC-08 Dashboard 规则调度健康指标闭环

本轮目标：把规则调度健康度从规则详情页和告警列表中提升到工作台，便于客户管理员在 Dashboard 上直接看到计划调度、失败、达到重试上限和未读调度告警。

已完成：

- `JdbcDashboardMetricsRepository.collect()` 新增 `ruleScheduleHealth` 聚合字段。
- `ruleScheduleHealth` 包含 `scheduledRules/failedScheduledRules/blockedScheduledRules/recentAlerts`。
- `scheduledRules` 统计已发布且开启调度的规则；`failedScheduledRules` 统计失败计数大于 0 的调度规则；`blockedScheduledRules` 统计 `failure_count >= max_retry_count` 的规则；`recentAlerts` 统计时间范围内 unread `rule_schedule_run_failed` 告警。
- `AuditApplicationService.dashboard()` 在无指标仓储时提供零值兜底，保持 API 结构稳定。
- `DashboardOverview` 前端类型和契约样例新增 `ruleScheduleHealth`。
- `Dashboard.vue` 新增“规则调度健康”面板，并修复工作台页面可见中文乱码。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=AuditApplicationServiceTest#buildsDashboardOverviewFromBusinessTablesAndKeepsRecentAuditActivities" test` 首次失败为 `ruleScheduleHealth` 为 null。
- 后端 GREEN：同一目标测试通过 1 个测试，验证 Dashboard overview 返回调度健康结构。
- PostgreSQL 集成：`$env:RUN_POSTGRES_INTEGRATION='true'; $env:POSTGRES_IT_JDBC_URL='jdbc:postgresql://localhost:5432/intelligent_report'; $env:POSTGRES_IT_USERNAME='report'; $env:POSTGRES_IT_PASSWORD='report123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#buildsDashboardMetricsFromBusinessTablesInPostgres" test` 通过 1 个真实 PostgreSQL 测试，验证 `rules/system_alerts` 聚合。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=AuditApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 8 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。

仍需继续：

- 规则级 `/rules/{ruleId}/metrics` 已在第 65 节改为仓储/SQL 全量聚合；超大规模生产数据后续可继续补预聚合表或物化视图。
- 复杂规则节点类型与完整画布编辑器仍需继续按原型补齐。

## 59. 2026-06-24 UC-07 真实 MySQL 容器烟测闭环

本轮目标：把 UC-07 的 MySQL 能力从 H2 MySQL 模式代码路径推进到本地真实 MySQL 容器烟测，证明企业内部 MySQL 数据源可以被 Java 服务连接、增量抽取并写入 PostgreSQL 知识库。

已完成：

- `docker-compose.yml` 已提供最小 MySQL 8.4 本地依赖，默认宿主机端口偏移到 `13306`，并在 `.env.example` 中补充 MySQL 本地开发变量。
- `backend/java-report-core/pom.xml` 增加 `com.mysql:mysql-connector-j` runtime 依赖，让生产和集成测试环境都具备真实 MySQL JDBC 驱动。
- 新增 `KnowledgeDataSourceMysqlIT`，使用 PostgreSQL 作为系统主库、MySQL 容器作为外部 ERP 数据源。
- 测试创建真实 MySQL 源表，首次同步 2 行，追加第 3 行后再次同步只处理新增行，并断言 `lastCursor=3`。
- 测试验证 `sourceType=mysql` 连接探测成功、`knowledge_items.source_type=data_source:mysql` 和 `knowledge_data_source_sync_runs` 真实落库。

验证证据：

- Docker 依赖：`docker inspect -f '{{.State.Health.Status}}' intelligent-report-system-mysql-1` 返回 `healthy`。
- RED：`$env:RUN_MYSQL_INTEGRATION='true'; $env:MYSQL_IT_JDBC_URL='jdbc:mysql://127.0.0.1:13306/erp_source'; $env:MYSQL_IT_USERNAME='erp'; $env:MYSQL_IT_PASSWORD='erp123'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeDataSourceMysqlIT#syncsRealMysqlContainerRowsIntoPostgresKnowledgeItemsWithCursor" test` 首次失败为 `No suitable driver found for jdbc:mysql://127.0.0.1:13306/erp_source`。
- GREEN：补充 `mysql-connector-j` 后，同一真实 MySQL 容器集成测试通过 1 个测试。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 26 个测试。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- UC-07 主链已达到 PostgreSQL/MySQL/API 数据源真实后端闭环；后续增强聚焦 ERP/OA/财务系统专用适配器配置模板和更多真实厂商连接样例。
- 当前整体可交付缺口继续收敛到 UC-01 真实外部 GPT provider 烟测、UC-04 高级企业版式与 PDF 真实 Logo 二进制嵌入、UC-06 生产 OCR/表格/扫描件解析、UC-08 复杂规则节点与更高级的规则画布/子流程编排。

## 61. 2026-06-24 UC-04 DOCX/PPTX 基础 Logo 嵌入闭环

本轮目标：把 Word 和 PPT 企业导出从“品牌字段校验 + 文本占位”推进到 Office 包内存在可验证的 Logo 媒体部件和 OOXML 图片关系，减少客户验收时对企业品牌规范的落差。

已完成：

- DOCX `[Content_Types].xml` 增加 `svg` 的 `image/svg+xml` 类型声明。
- DOCX 包新增 `word/_rels/document.xml.rels`，使用 `rIdLogo` 指向 `word/media/logo.svg`。
- DOCX 包新增 `word/media/logo.svg`，根据企业品牌模板生成基础 SVG Logo，保留 `logoObjectKey/companyName/primaryColor` 可追踪信息。
- `word/document.xml` 新增 `w:drawing` 图片引用，正文通过 `r:embed="rIdLogo"` 引用 Logo 关系。
- `ReportApplicationServiceTest.exportsCurrentReportVersionAsEnterpriseWordDocument` 已升级断言 DOCX 关系文件、SVG 媒体部件、content type 和正文 drawing 引用。
- PPTX `[Content_Types].xml` 增加 `svg` 的 `image/svg+xml` 类型声明。
- PPTX 包新增 `ppt/slides/_rels/slide1.xml.rels`，使用 `rIdLogo` 指向 `../media/logo.svg`。
- PPTX 包新增 `ppt/media/logo.svg`，并在 `ppt/slides/slide1.xml` 中新增 `p:pic` 图片节点引用 `r:embed="rIdLogo"`。
- PPTX 支持可选 `brand` payload；未传时使用安全默认品牌，保持当前前端 PPT 导出兼容。

验证证据：

- RED 1：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test` 首次失败为 `word/document.xml` 不包含 `rIdLogo`。
- RED 2：补关系占位后再次收紧测试，失败为 `word/document.xml` 不包含 `<w:drawing>`，证明文本占位不能满足 Logo 嵌入验收。
- GREEN：同一目标测试通过 1 个测试，验证 `word/_rels/document.xml.rels`、`word/media/logo.svg`、`image/svg+xml` 和 `r:embed="rIdLogo"`。
- PPTX RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 首次失败为 `ppt/slides/slide1.xml` 不包含 `r:embed="rIdLogo"`。
- PPTX GREEN：同一目标测试通过 1 个测试，验证 `ppt/slides/_rels/slide1.xml.rels`、`ppt/media/logo.svg`、`image/svg+xml` 和幻灯片图片引用。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 30 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试，验证当前前端 PPT 导出 payload 兼容。
- 前端类型：`npm run typecheck` 通过。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 基础 Logo 生成缺口已在第 67 节进一步收敛：DOCX/PPTX 会优先读取 `logoObjectKey` 对应的真实 SVG/PNG/JPEG 对象，读取不到时才回退基础 SVG。
- PDF 基础 Logo 已在第 66 节闭合；Word/PPT/PDF 仍未实现统一主题、页眉页脚部件、目录页码、母版或品牌模板治理，PDF 仍未嵌入真实 Logo 二进制。

## 66. 2026-06-24 UC-04 PDF 基础 Logo 嵌入闭环

本轮目标：把 PDF 企业导出从“标题、正文、引用摘要”推进到至少具备可验证的企业 Logo 和品牌色嵌入，补齐 DOCX/PPTX 已有基础 Logo 后 PDF 的品牌落差。

已完成：

- PDF 导出现在和 Word 一样会校验企业品牌模板字段，缺少 `companyName/logoObjectKey/header/footer/fontFamily/primaryColor` 时拒绝导出，不静默降级。
- `renderPdf()` 接收品牌模板，在 PDF 内容流顶部写入基础 Logo 区域：品牌色矩形、公司名文本和 `PDF-LOGO` 注释。
- PDF 内容流包含 `logoObjectKey=<...>` 与 `companyName=<...>` 可追踪注释，便于自动化验收确认使用了企业品牌字段。
- 品牌主色 `#RRGGBB` 会转换为 PDF `rg` RGB 绘制命令；例如 `#1F4E79` 会写入 `0.122 0.306 0.475 rg`。
- PDF 正文继续保留报告标题、章节正文、引用摘要、页眉和页脚，仍写入 MinIO 并走导出记录、受控下载和审计链路。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument" test` 首次失败，PDF 字节中不包含 `PDF-LOGO`。
- GREEN：同一目标测试通过 1 个测试，验证 PDF 包含 `PDF-LOGO`、`logoObjectKey=branding/contoso-logo.png`、`Contoso Analytics` 和 `0.122 0.306 0.475 rg`。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 30 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- report-generation.spec.ts` 通过 7 个 Chromium E2E，覆盖企业 Word、PDF 与 PPT 导出入口。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 当前 PDF Logo 是基于品牌字段绘制的基础矢量标记，不是从 MinIO 读取 `logoObjectKey` 对应的用户上传原始 Logo 二进制；后续需接入对象读取、图片格式校验、尺寸处理和失败提示。
- Word/PDF/PPT 的高级企业版式仍需统一治理：主题样式、页眉页脚部件、目录页码、PPT 母版和模板版本发布审批。

## 67. 2026-06-24 UC-04 DOCX/PPTX 真实 Logo 对象读取闭环

本轮目标：把 Office 企业导出从“基于品牌字段生成基础 SVG Logo”推进到“优先使用 `logoObjectKey` 指向的对象存储真实 Logo 二进制”，减少客户验收时上传企业 Logo 后导出文件仍显示占位图的落差。

已完成：

- `ReportExportStorage` 新增 `readObject(objectKey)` 默认方法和 `StoredObject` 载体，旧测试桩可不实现而保持兼容。
- `MinioReportExportStorage.readObject()` 会从当前 MinIO bucket 读取对象内容，按对象后缀识别 SVG/PNG/JPEG content type；对象不存在时返回空，其他读取异常抛出 `IOException`。
- DOCX 导出通过 `resolveLogoAsset()` 优先读取 `brand.logoObjectKey`，若为 SVG/PNG/JPEG 且内容非空，则写入 `word/media/logo.svg|png|jpg` 并动态生成 `[Content_Types].xml` 与 `word/_rels/document.xml.rels`；读取不到或格式不支持时回退原基础 SVG。
- PPTX 导出复用同一 Logo 解析逻辑，写入 `ppt/media/logo.svg|png|jpg`，并动态生成 `ppt/slides/_rels/slide1.xml.rels` 图片关系；读取不到时仍保持基础 SVG 兼容。
- PDF 仍保持第 66 节的基础矢量 Logo 标记，不在本轮强行嵌入真实图片二进制。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#enterpriseWordExportEmbedsStoredLogoObjectWhenAvailable" test` 首次编译失败，缺少 `ReportExportStorage.StoredObject/readObject`，证明接口还不能读取真实 Logo 对象。
- GREEN：同一目标测试通过 1 个测试，验证 `word/media/logo.svg` 包含对象存储中的 `REAL-CONTOSO-LOGO`，且不再是基础 SVG 的 `companyName=Contoso Analytics` 占位内容。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest,ReportCoreApplicationContextTest,ContractSurfaceTest" test` 通过 38 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- UC-04 高级企业版式仍未完成：主题样式、页眉页脚部件、目录页码、PPT 母版、模板版本发布审批和统一品牌模板治理。
- PDF 仍是基础矢量 Logo 标记，尚未读取并嵌入真实 Logo 图片二进制。

## 100. 2026-06-25 UC-04 企业导出前后端品牌契约对齐

本轮目标：修复报告详情页企业导出时的真实前后端契约偏差，避免 PDF/PPT 在前端未携带品牌模板字段而被后端 `ensureEnterpriseBrandTemplate()` 拒绝，影响客户直接导出交付物。

已完成：

- 前端 `ReportDetail.vue` 的 `createEnterpriseExport()` 已统一对 `docx/pdf/pptx` 三种企业导出格式发送同一份 `brand` 模板字段，不再只对 Word 透传品牌信息。
- 前端 API 契约测试已更新，明确 `pdf` 与 `pptx` 也属于企业导出链路，需要携带 `companyName/logoObjectKey/header/footer/fontFamily/primaryColor`。
- 报告详情 Playwright E2E 已调整为实际验证 PDF 导出 payload，确保页面点击 `PDF` 后提交的请求与后端 `REQ-REPORT-004` 约定一致。
- 当前导出渲染链在代码层已具备基础目录标题、章节序号和页眉/页脚文案输出，这轮同步把前端触发面和后端能力对齐，避免“后端支持、前端漏参”这类客户可感知故障。

验证证据：

- 前端契约：`npm run test -- src/api/apiContracts.test.ts` 通过 20 个测试。
- 前端 E2E：`npm run e2e -- tests/e2e/report-generation.spec.ts` 通过 7 个 Chromium 测试，其中 `REQ-REPORT-004` 断言 PDF 导出 payload 包含完整 `brand` 字段。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- UC-04 仍缺更正式的目录页码、页眉页脚部件布局、主题样式、PPT 母版和统一品牌模板治理。
- PDF 仍未读取并嵌入 `logoObjectKey` 指向的真实 Logo 二进制，当前仍是基础矢量品牌标记。

## 101. 2026-06-25 UC-04 PDF 真实 Logo 二进制嵌入闭环

本轮目标：把 PDF 企业导出从“只有基础矢量品牌标记”推进到“能优先读取对象存储里的真实 Logo 二进制”，减少客户上传企业 Logo 后 PDF 仍只看到占位色块的落差。

已完成：

- `renderPdf()` 已接入 `resolvePdfLogoAsset()`，复用现有 `logoObjectKey` 对象读取能力。
- 当 `logoObjectKey` 指向 PNG/JPEG 对象时，PDF 导出会额外生成 `/Subtype /Image` 的 XObject，并在页面资源中挂接 `/Im1`，正文流中通过 `Do` 指令绘制真实 Logo 图片。
- 当对象不存在、读取失败，或当前对象为 SVG 等暂未转成 PDF 图片流的格式时，仍保持原有基础矢量品牌标记回退，不影响导出可用性。
- 这轮没有强行引入复杂的 SVG 栅格化依赖，先把真实 PNG/JPEG Logo 这条最常见客户路径打通。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable" test` 首次失败，`readKeys` 为空，证明 PDF 渲染此前根本没有读取 `logoObjectKey`。
- GREEN：同一目标测试通过 1 个测试，验证 PDF 中出现 `/Subtype /Image`、`/Im1` 和 `PDF-LOGO-BINARY logoObjectKey=branding/contoso-logo.png`。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument+ReportApplicationServiceTest#enterpriseWordExportEmbedsStoredLogoObjectWhenAvailable+ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument+ReportApplicationServiceTest#enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable+ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 通过。
- 前端 E2E：`npm run e2e -- tests/e2e/report-generation.spec.ts` 通过 7 个 Chromium 测试。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- 当前 PDF 真实 Logo 仅优先支持 PNG/JPEG，SVG 仍走基础矢量品牌标记回退。
- UC-04 仍缺目录页码、页眉页脚部件布局、主题样式、PPT 母版和统一品牌模板治理。

## 68. 2026-06-24 UC-08 规则 Webhook 外部回写闭环

本轮目标：把规则生产动作从站内通知和协作任务继续推进到外部系统回写，让规则命中后能调用 ERP/OA/财务系统的 HTTP 接口，形成可验证的最小集成闭环。

已完成：

- 新增 `RuleWebhookClient` 应用层端口，`RestClientRuleWebhookClient` 使用 Spring `RestClient` 发起 HTTP 请求，避免规则应用服务直接绑定具体 HTTP 实现。
- `RuleApplicationService` 支持可选注入 `RuleWebhookClient`，默认构造器保持无 webhook 依赖，Spring 环境通过 `Optional<RuleWebhookClient>` 自动装配。
- 生产运行时只对实际进入 `trace` 的 `actionType=webhook` 节点触发外部回写，未命中的分支不会误调用。
- Webhook 请求使用节点配置的 `endpoint/method/headers/body`，并在 body 中补充 `ruleId/ruleName/runId/nodeId`；不会把运行输入 `sample/secretPrompt` 原样泄露给外部系统。
- 当前版本是同步最小回写闭环；幂等键已在第 69 节闭合，失败告警补偿已在第 70 节闭合，重试策略和补偿队列留作后续增强。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunExecutesWebhookActionWithoutLeakingSample" test` 首次编译失败，缺少 `RuleWebhookClient` 和支持 webhook client 的构造注入。
- GREEN：同一目标测试通过 1 个测试，验证 webhook 仅收到 endpoint/method/headers/body + rule/run/node 元数据，且不包含 `secretPrompt/do-not-leak`。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 25 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E。

仍需继续：

- Webhook action 的幂等键已在第 69 节闭合；失败告警补偿、HMAC 签名、同步重试退避和基础回写审计明细已在第 70/71/72 节闭合，持久动作台账、分页查询、人工重放、管理 UI 和异步重放 worker 已在第 73-77 节闭合。
- 规则完整画布编辑器尚未提供 webhook 节点配置 UI；当前能力优先保证后端生产执行闭环。

## 69. 2026-06-24 UC-08 规则 Webhook 幂等键闭环

本轮目标：在已具备 Webhook 外部 HTTP 回写的基础上，为每次规则动作回写提供可追踪的幂等键，降低调度重试或网络重复提交导致外部 ERP/OA/财务系统重复处理的风险。

已完成：

- `RuleApplicationService.executeWebhookAction()` 会基于 `ruleId/runId/nodeId` 生成 `Idempotency-Key`，格式为 `rule-{ruleId}-run-{runId}-node-{nodeId}`。
- 幂等键写入 Webhook 请求 headers，并在合并节点自定义 headers 后由系统强制覆盖，避免规则配置误覆盖生产去重标识。
- Webhook body 仍只包含节点配置 body 与 `ruleId/ruleName/runId/nodeId`，不会把 `sample/secretPrompt` 泄露给外部系统。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunExecutesWebhookActionWithoutLeakingSample" test` 首次失败为 headers 缺少 `Idempotency-Key=rule-1-run-1-node-writebackRisk`。
- GREEN：同一目标测试通过 1 个测试，验证 Webhook headers 同时包含节点自定义 `X-System` 和系统生成 `Idempotency-Key`。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 25 个测试。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E。

仍需继续：

- Webhook 失败告警补偿、HMAC 签名、同步重试退避和基础回写审计明细已在第 70/71/72 节闭合；持久动作台账、分页查询、人工重放、管理 UI 和异步重放 worker 已在第 73-77 节闭合。
- 规则完整画布编辑器尚未提供 webhook 节点配置 UI。

## 70. 2026-06-24 UC-08 规则 Webhook 失败告警补偿闭环

本轮目标：让 Webhook 外部回写失败不再只表现为调用异常，而是进入生产运行可观测台账、审计和系统告警，便于客户现场定位并人工补偿。

已完成：

- `RuleApplicationService.execute()` 在规则主执行成功后执行 action；若 Webhook action 抛错，会追加一条 `status=failed/run_type=production` 的失败生产运行，错误信息包含 `webhook action failed`、`nodeId`、`endpoint` 和底层失败原因。
- action 失败会写入 `rule_production_run` 失败审计，审计 detail 包含失败运行 `runId`、原始成功执行上下文 `sourceRunId`、版本和错误信息。
- Webhook action 成功或失败都会写入 `rule_action_webhook` 审计，detail 包含 `runId/nodeId/actionType/endpoint/idempotencyKey`，失败时额外包含 `errorMessage`。
- `executeWebhookAction()` 捕获 Webhook client 异常后创建 `type=rule_action_webhook_failed`、`severity=error`、`status=unread` 的 `SystemAlert`。
- 告警 payload 和 action 审计 detail 只包含补偿所需元数据，不包含 `input/sample/secretPrompt`，避免执行样本或敏感 Prompt 泄漏。
- 保留同步抛错语义，让调用方或调度器能继续走现有失败路径；当前未引入异步补偿队列，避免在没有重试策略表和签名策略前假装自动恢复。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunCreatesFailedRunAndAlertWhenWebhookActionFailsWithoutLeakingSample" test` 首次失败为失败生产运行集合为空，证明 Webhook action 失败不可见。
- GREEN：同一目标测试通过 1 个测试，验证失败生产运行、未读告警、失败 action 审计、幂等键和敏感样本脱敏。
- 审计 GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunExecutesWebhookActionWithoutLeakingSample+productionRunCreatesFailedRunAndAlertWhenWebhookActionFailsWithoutLeakingSample" test` 通过 2 个测试，验证 Webhook 成功/失败均写 `rule_action_webhook` 基础审计明细且不泄露执行样本。

仍需继续：

- Webhook 持久动作台账、分页查询、人工重放、管理 UI 和异步重放 worker 已在第 73-77 节闭合。
- 规则完整画布编辑器尚未提供 webhook 节点配置 UI。

## 71. 2026-06-24 UC-08 规则 Webhook HMAC 签名闭环

本轮目标：在外部系统回写已支持幂等键和失败可见补偿的基础上，补齐可选签名鉴权能力，避免 ERP/OA/财务系统只能依赖来源 IP 或明文 Header 判断请求可信度。

已完成：

- Webhook action 节点支持可选 `signatureSecret` 配置。
- 配置 `signatureSecret` 后，系统以 `Idempotency-Key` 作为签名 payload，使用 HMAC-SHA256 生成 `X-Signature-Algorithm=HMAC-SHA256`、`X-Signature-Payload` 和 `X-Signature=sha256=<hex>` 请求头。
- `signatureSecret` 只参与签名计算，不进入 Webhook body、系统告警或审计 detail。
- `rule_action_webhook` 审计只记录 `signatureAlgorithm/signaturePayload` 等可排查元数据，不记录签名密钥。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunSignsWebhookActionWithoutLeakingSignatureSecret" test` 首次失败为 headers 中缺少 `X-Signature-Algorithm=HMAC-SHA256`。
- GREEN：同一目标测试通过 1 个测试，验证 HMAC-SHA256 签名头、签名 payload、签名密钥脱敏和 action 审计元数据。

仍需继续：

- Webhook 持久动作台账、分页查询、人工重放、管理 UI 和异步重放 worker 已在第 73-77 节闭合。
- 规则完整画布编辑器尚未提供 webhook 签名配置 UI。

## 72. 2026-06-24 UC-08 规则 Webhook 同步重试退避闭环

本轮目标：让短暂网络抖动或外部系统瞬时不可用时，Webhook action 能按节点配置同步重试，避免一次失败就进入人工补偿，同时保持幂等和审计可追踪。

已完成：

- Webhook action 节点支持 `maxRetryCount` 和 `retryBackoffSeconds`。
- `maxRetryCount` 表示首次失败后的最大重试次数；总尝试次数为 `maxRetryCount + 1`。
- 每次尝试复用同一个 `Idempotency-Key` 和签名 payload，避免外部系统把重试误判为不同业务事件。
- 每次尝试都会写 `rule_action_webhook` 审计，detail 包含 `attempt/maxRetryCount/idempotencyKey`；失败尝试记录 `errorMessage`。
- 仅当所有尝试耗尽后才创建 `rule_action_webhook_failed` 告警和失败生产运行，避免瞬时失败造成告警噪声。
- 审计、告警和请求体仍不包含 `input/sample/secretPrompt`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunRetriesWebhookActionWithSameIdempotencyKeyWithoutLeakingSample" test` 首次在第一次 Webhook 失败后直接抛错，证明尚未重试。
- GREEN：同一目标测试通过 1 个测试，验证失败两次后第三次成功、三次请求复用同一幂等键、三条 action 审计记录 attempt/maxRetryCount 且不泄露执行样本。

仍需继续：

- 当前是同步重试闭环；Webhook 持久动作台账、分页查询、人工重放、管理 UI 和异步重放 worker 已在第 73-77 节闭合。
- 规则完整画布编辑器尚未提供 webhook 重试/退避配置 UI。

## 60. 2026-06-24 UC-08 规则 aggregate 聚合节点闭环

本轮目标：把规则引擎从单一 `condition` 判断推进到可处理明细数据的复杂节点能力，先闭合财务/应收场景常用的“明细汇总后判断”。

已完成：

- `RuleDomainService.validateDefinition()` 支持 `aggregate` 节点 schema，要求 `sourceField/outputField/operation`，`sum` 操作额外要求 `valueField`。
- `RuleDomainService.executeDebug()` 执行 `aggregate` 节点时从 `sample` 中读取明细列表，支持 `sum/count`，把结果写入运行上下文，供后续 `condition` 节点使用。
- debug trace 对 `aggregate` 节点返回 `operation/outputField/value`，便于前端和审计排查派生值。
- `RuleApplicationService` 的调试和生产运行复用同一领域执行链，aggregate 节点可在发布后生产运行中生效。
- `RuleDesigner.vue` 类型和节点卡片支持 `aggregate`，调试 trace 展示聚合输出值。
- `rule-engine.spec.ts` 增加聚合节点展示与 trace 断言。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenAggregateNodeFeedsCondition_thenUsesDerivedValue" test` 首次失败为 `RuleDomainService.validateDefinition()` 拒绝 `aggregate` 节点。
- GREEN：同一领域测试通过 1 个测试，验证 `invoices[].overdueAmount` 汇总为 `totalOverdueAmount=11000` 并命中后续条件。
- 应用层：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#debugAndProductionRunSupportAggregateNodes" test` 通过 1 个测试，验证调试和发布后的生产运行都支持 aggregate。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，验证画布展示 `sum invoices.overdueAmount -> totalOverdueAmount` 和 trace 中 `sumOverdue · aggregate · true · totalOverdueAmount: 11000`。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- UC-08 已补齐 condition + aggregate 的基础复杂节点组合；后续继续补分支、通知/动作、子流程或人工审批等更多节点类型。
- 当前前端当时仍是静态节点展示和样本调试；后续已补节点新增/删除、连线新增/删除和配置表单，仍需拖拽布局和更完整的可视化校验。
- 大规模规则运行指标仍需要 SQL 下推、预聚合或物化视图。

## 62. 2026-06-24 UC-08 规则 branch 分支节点闭环

本轮目标：把规则执行从“按节点数组顺序逐个执行”推进到“沿连线执行”，并支持按条件选择 `true/false` 后续路径，避免画布连线只是展示而没有业务语义。

已完成：

- `RuleDomainService.validateDefinition()` 支持 `branch` 节点 schema，要求 `field/operator/value`。
- `branch` 节点必须具备 `condition=true` 与 `condition=false` 两条后继边，否则保存或调试时拒绝。
- `RuleDomainService.executeDebug()` 改为从 `start` 节点出发沿 `edges` 执行，防止未连通节点被数组顺序误执行。
- `branch` 节点执行时复用条件判断，命中后选择对应后继边，trace 返回 `selectedPath` 和 `nextNodeId`。
- 前端规则画布支持展示 branch 节点条件，debug trace 支持展示分支选择和下一节点。
- `rule-engine.spec.ts` 覆盖 aggregate -> branch -> action -> condition 的调试链路，并断言 branch trace 可见。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenBranchNodeMatches_thenOnlyEvaluatesSelectedPath" test` 首次失败为 `RuleDomainService.validateDefinition()` 拒绝 `branch` 节点。
- GREEN：同一目标测试通过 1 个测试，验证高风险样本只执行 `highRiskAction` 路径，跳过 `lowRiskAction`。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest,RuleApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 21 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，验证 branch 节点展示和 trace 中 `riskBranch -> notifyHighRisk` 分支路径。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 当前 `actionType=notify` 已在第 63 节补齐真实未读系统告警，`actionType=create_task` 已在第 64 节补齐真实协作任务；其他 action 类型仍需继续补调用外部系统、Webhook、子流程、人工审批和异常处理。
- 当前前端当时仍是静态节点展示；后续已补节点新增/删除、连线新增/删除和配置表单，仍需拖拽布局和更完整的可视化校验。

## 63. 2026-06-24 UC-08 规则 notify action 真实副作用闭环

本轮目标：把规则 `action` 节点从“trace 级通过”推进到至少一种可验证的真实业务副作用，先闭合生产运行中 `actionType=notify` 创建站内未读系统告警。

已完成：

- `RuleApplicationService.execute()` 在 published 规则生产运行成功落库后解析执行 trace，只对本次真实走到的 action 节点执行副作用，避免未命中分支误触发。
- `actionType=notify` 会创建 `type=rule_action_notify`、`status=unread` 的 `SystemAlert`，资源指向当前规则。
- 告警 payload 只包含 `ruleId/ruleName/runId/nodeId/actionType/message` 等必要元数据，不包含 `input/sample/secretPrompt`，防止调试样本或敏感 Prompt 泄漏到告警表。
- 新增应用层测试覆盖 branch 命中 notify action、告警字段、未读状态和样本脱敏。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunExecutesNotifyActionAsUnreadSystemAlertWithoutLeakingSample" test` 首次失败为 `alertRepository.alerts` 数量为 0，证明生产运行未产生通知副作用。
- GREEN：同一目标测试通过 1 个测试。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest" test` 通过 22 个测试。

仍需继续：

- `actionType=create_task` 已在第 64 节补齐真实协作任务；Webhook/HTTP 调用与外部系统回写已在第 68 节闭合，幂等键已在第 69 节闭合，失败告警补偿已在第 70 节闭合，HMAC 签名已在第 71 节闭合，同步重试退避已在第 72 节闭合，持久动作台账、分页查询、人工重放、管理 UI 和异步重放 worker 已在第 73-77 节闭合。
- 子流程、人工审批、异常分支和完整拖拽画布编辑器仍未闭合。
- 规则级 `/rules/{ruleId}/metrics` 已在第 65 节改为仓储/SQL 全量聚合；超大规模生产数据后续可继续补预聚合表或物化视图。

## 64. 2026-06-24 UC-08 规则 create_task action 协作任务闭环

本轮目标：把规则动作从“只提醒”推进到“可处理工作项”，让生产规则命中后能复用 UC-10 协作模型创建批注任务并通知负责人。

已完成：

- `RuleApplicationService` 支持注入 `CollaborationApplicationService`，Spring 上下文通过可选依赖完成装配。
- 规则生产运行成功后统一解析实际执行 trace，只对本次命中的 `action` 节点执行副作用。
- `actionType=create_task` 读取节点上的 `reportId/assigneeUserId/content/anchor`，调用 `CollaborationApplicationService.addAnnotation()` 创建批注、协作任务和指派通知。
- 该路径复用协作服务已有的报告 owner 校验、启用用户校验、文本 offset anchor 校验和通知写入，避免规则引擎绕过 UC-10 的权限边界。
- 新增应用层测试覆盖 branch 命中 create_task action 后真实创建 `CollaborationTask`，并使用包含 `secretPrompt` 的样本验证任务创建不依赖也不持久化执行样本。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunExecutesCreateTaskActionThroughCollaborationServiceWithoutLeakingSample" test` 首次编译失败，报 `RuleApplicationService(..., CollaborationApplicationService)` 构造器不存在，证明规则服务尚不能承接协作副作用。
- GREEN：同一目标测试通过 1 个测试，验证创建 `reportId=88/annotationId=1/assigneeUserId=2/status=open` 的协作任务。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,CollaborationApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 29 个测试。

仍需继续：

- `create_task` 节点的前端 action 配置表单已在第 88 节补齐，报告选择器和指派人选择器已在第 90 节补齐，正文 anchor 选区生成已在第 91 节补齐。
- Webhook/HTTP 调用和外部系统回写已在第 68 节闭合，幂等键已在第 69 节闭合，失败告警补偿已在第 70 节闭合，HMAC 签名已在第 71 节闭合，同步重试退避已在第 72 节闭合，持久动作台账、分页查询、人工重放、管理 UI 和异步重放 worker 已在第 73-77 节闭合；人工审批/子流程仍未闭合。

## 65. 2026-06-24 UC-08 规则 metrics SQL 下推闭环

本轮目标：把 `/rules/{ruleId}/metrics` 从服务层读取最近 1000 条运行记录后聚合，推进到仓储/数据库级全量聚合，避免生产运行量超过 1000 后失败数、平均耗时和最近错误失真。

已完成：

- 新增 `RuleRunMetrics` 领域值对象，承载总运行数、成功数、失败数、平均耗时、最近状态和最近错误。
- `RuleRepository` 新增 `metrics(ruleId)`，由应用服务直接读取聚合结果，不再在 `RuleApplicationService.metrics()` 中拉取最近 1000 条运行记录计算。
- `InMemoryRuleRepository.metrics()` 基于全量 production runs 聚合，保证单元测试能覆盖超过 1000 条的边界。
- `JdbcRuleRepository.metrics()` 使用 SQL `COUNT/SUM/AVG` 对 `rule_debug_runs` 全量 production 运行记录做数据库侧聚合，并分别查询最近状态与最近非空错误。
- 保留 `findRuns()` 分页接口用于运行历史列表，避免列表分页和指标聚合互相耦合。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#metricsAggregatesAllProductionRunsInsteadOfOnlyLatestThousand" test` 首次失败，1001 条运行中旧逻辑返回 `failedRuns=0/averageDurationMs=10/lastErrorMessage=null`，证明最近 1000 条截断导致指标失真。
- GREEN：同一目标测试通过 1 个测试，验证 `totalRuns=1001/succeededRuns=1000/failedRuns=1/lastErrorMessage=old failure` 且平均耗时按全量记录计算。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,CollaborationApplicationServiceTest,ReportCoreApplicationContextTest" test` 通过 30 个测试。
- PostgreSQL 集成：`RUN_POSTGRES_INTEGRATION=true ... "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsRulesVersionsAndDebugRunsInPostgres" test` 通过 1 个真实 PostgreSQL 测试，验证规则版本、运行记录和 metrics 查询仍可在 V031 schema 上工作。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E。
- 前端类型：`npm run typecheck` 通过。
- Compose 配置：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 超大规模生产数据可继续引入预聚合表、物化视图或时间窗口指标，以降低高并发 Dashboard 和规则详情页查询压力。
- Webhook/HTTP 调用、外部系统回写、人工审批/子流程和完整拖拽画布编辑器仍未闭合。

## 73. 2026-06-24 UC-08 规则 Webhook 持久动作台账基础

本轮目标：把 Webhook action 从“审计日志可见”推进到“有独立持久动作执行台账”，为后续异步重放 worker、人工补偿页面和运营排障打基础。

已完成：

- 新增 `RuleActionExecution` 领域模型，记录 `ruleId/runId/nodeId/actionType/status/attempt/maxRetryCount/endpoint/idempotencyKey/nextRetryAt/errorMessage/metadata`。
- `RuleRepository` 增加 `saveActionExecution()` 和 `findActionExecutions()`，内存仓储和 JDBC 仓储均已实现。
- 新增 Flyway `V032__rule_action_executions.sql`，创建 `rule_action_executions` 表，并增加规则维度查询索引和 `pending_retry/failed` 补偿扫描索引。
- Webhook action 成功后写入 `status=succeeded` 动作台账；同步重试耗尽后写入 `status=pending_retry`，并按 `retryBackoffSeconds` 计算 `nextRetryAt`。
- 台账 metadata 只保留 `method/retryBackoffSeconds/signatureAlgorithm/signaturePayload` 等排障字段，不写入 `sample/secretPrompt/signatureSecret`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunPersistsWebhookActionCompensationLedgerWithoutLeakingSensitiveInput" test` 首次编译失败为缺少 `findActionExecutions(Long,int,int)`，证明系统没有动作台账查询能力。
- GREEN：同一目标测试通过 1 个测试，验证失败 Webhook 生成 `pending_retry` 台账、attempt/maxRetryCount/endpoint/idempotencyKey/nextRetryAt/errorMessage 完整，并验证不泄露样本和签名密钥。

仍需继续：

- 当前已具备持久动作台账基础；人工补偿 API/UI 已在第 74-76 节闭合，异步重放 worker 和动作级租约已在第 77 节闭合，异步补偿次数上限已在第 78 节闭合，批量操作已在第 79 节闭合，worker 指标已在第 80 节闭合，重复未读告警降噪已在第 81 节闭合。
- 规则完整画布编辑器尚未提供 webhook endpoint/header/body/signature/retry 配置 UI。

## 74. 2026-06-24 UC-08 规则 Webhook 人工重放最小闭环

本轮目标：在持久动作台账基础上补齐最小人工补偿入口，让客户现场可以对 `pending_retry/failed` Webhook 动作进行受控重放，而不是只能看见失败记录。

已完成：

- `RuleApplicationService.retryWebhookActionExecution(ruleId, actionExecutionId)` 支持重放 `pending_retry/failed` 的 Webhook 动作。
- 重放只使用动作台账中的 `endpoint/method/idempotencyKey` 和规则/运行/节点元数据，不重新执行整条规则，避免重复触发其他 action。
- 重放成功会追加一条 `status=succeeded` 的动作台账记录，并保留 `sourceActionExecutionId` 指向原失败记录，形成不可变补偿链路。
- 重放失败会追加新的 `pending_retry` 台账记录，保留错误原因和下一次重试时间。
- 重放审计写入 `rule_action_webhook_retry`，包含原 actionExecutionId、新 actionExecutionId、attempt、endpoint 和幂等键。
- 后端 REST 暴露 `POST /api/v1/rules/{ruleId}/action-executions/{actionExecutionId}/retry`，前端 `ruleApi.retryWebhookActionExecution()` 已补契约。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#manualRetryReplaysPendingWebhookActionExecutionAndUpdatesLedger" test` 首次编译失败为缺少 `retryWebhookActionExecution(Long,Long)`。
- GREEN：同一目标测试通过 1 个测试，验证人工重放复用原 `Idempotency-Key`、追加 succeeded 台账、保留原 pending 记录，并写入 `rule_action_webhook_retry` 审计。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试，覆盖 `/rules/{ruleId}/action-executions/{actionExecutionId}/retry`。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- 动作台账分页查询 REST 与规则页面管理 UI 已在第 75-76 节闭合；异步重放 worker 和动作级租约已在第 77 节闭合，异步补偿次数上限已在第 78 节闭合，批量操作已在第 79 节闭合，worker 指标已在第 80 节闭合，重复未读告警降噪已在第 81 节闭合。

## 75. 2026-06-24 UC-08 规则 Webhook 动作台账分页查询闭环

本轮目标：让人工补偿不再依赖事先知道 `actionExecutionId`，先补齐可分页查询的脱敏动作台账 API，为后续规则页面管理 UI 提供数据面。

已完成：

- `RuleApplicationService.listActionExecutions(ruleId, page, pageSize)` 返回 `PageResponse<Map<String,Object>>`。
- `RuleRepository.countActionExecutions(ruleId)` 在内存仓储和 JDBC 仓储中实现，分页响应 total 不再用当前页数量代替。
- 后端 REST 暴露 `GET /api/v1/rules/{ruleId}/action-executions?page=&pageSize=`，使用 `rule:debug` 权限保护。
- 前端 `ruleApi.listActionExecutions()` 已补契约，路径为 `/rules/{ruleId}/action-executions`。
- 列表响应只包含动作执行排障字段，不返回 `input/sample/secretPrompt/signatureSecret`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsWebhookActionExecutionsForManualCompensationWithoutLeakingSensitiveInput" test` 首次编译失败为缺少 `listActionExecutions(Long,int,int)`。
- GREEN：同一目标测试通过 1 个测试，验证分页 total、动作台账字段和敏感字段脱敏。

仍需继续：

- 规则页面动作台账列表、过滤和人工重放 UI 已在第 76 节闭合。
- 异步重放 worker 和动作级租约已在第 77 节闭合，异步补偿次数上限已在第 78 节闭合，批量补偿操作已在第 79 节闭合，worker 指标已在第 80 节闭合，重复未读告警降噪已在第 81 节闭合。

## 76. 2026-06-24 UC-08 规则 Webhook 动作台账管理 UI 闭环

本轮目标：把 Webhook 动作台账从“只有 API/契约”推进到规则页面可操作，客户现场可以直接查看失败动作、过滤待补偿记录并触发人工重放。

已完成：

- `RuleDesigner.vue` 新增 `Webhook action ledger` 区域，加载 `GET /api/v1/rules/{ruleId}/action-executions?page=1&pageSize=10`。
- 台账行展示 `nodeId/status/attempt/maxRetryCount/endpoint/idempotencyKey`，用于排查外部 ERP/OA/财务系统回写失败。
- 提供 `Only pending/failed` 过滤，只保留 `pending_retry/failed` 且 `actionType=webhook` 的可补偿动作。
- 对 `pending_retry/failed` Webhook 动作提供 `Retry {actionExecutionId}` 按钮，调用 `POST /api/v1/rules/{ruleId}/action-executions/{actionExecutionId}/retry` 后刷新台账。
- 规则详情辅助数据加载改为 `Promise.allSettled([loadRunHistory(), loadActionExecutions()])`，避免运行历史接口失败时阻断动作台账刷新，或动作台账失败时影响规则主画布。
- `rule-engine.spec.ts` 的 API mock 改为按 URL pathname 精确匹配，避免 `runs` 路由误拦截 `action-executions` 请求。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败为 `actionExecutionListCalls=0`，定位到 Playwright mock 中 `**/api/v1/rules/12/runs` 过宽，吞掉了 `/rules/12/action-executions` 请求。
- GREEN：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，覆盖动作台账展示、`pending_retry` 状态、endpoint 展示、过滤隐藏 `auditHook`、点击 `Retry 501` 后刷新为 `succeeded`。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest,ContractSurfaceTest" test` 通过 38 个测试。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 当前人工重放按钮已闭合；生产级补偿队列的异步 worker 和动作级租约已在第 77 节闭合，异步补偿次数上限已在第 78 节闭合，批量补偿操作已在第 79 节闭合，worker 指标已在第 80 节闭合，重复未读告警降噪已在第 81 节闭合。
- 规则完整画布编辑器仍未提供 webhook endpoint/header/body/signature/retry 的可视化配置表单。

## 77. 2026-06-24 UC-08 规则 Webhook 异步重放 worker 闭环

本轮目标：把 Webhook 补偿从“必须人工点击重放”推进到“后台 worker 可自动扫描到期补偿动作并重放”，降低客户现场因外部 ERP/OA/财务系统短暂不可用导致长期 pending 的风险。

已完成：

- 新增 `RuleWebhookActionReplayWorker`，默认关闭，可通过 `rule.webhook-replay.worker.enabled=true` 启用定时扫描。
- Worker 扫描 due 的 `pending_retry/failed` Webhook 动作，复用 `RuleApplicationService.retryWebhookActionExecution()`，因此继续沿用原幂等键、审计和不可变动作台账链路。
- `RuleRepository` 新增 `findDueWebhookActionExecutions()`、`tryAcquireActionExecutionLease()` 和 `releaseActionExecutionLease()`。
- `InMemoryRuleRepository` 增加 action execution 级内存租约，单元测试可验证并发领取语义。
- `JdbcRuleRepository` 增加 PostgreSQL 版本的 due 查询和 `replay_locked_until` 租约更新。
- 新增 Flyway `V033__rule_action_execution_replay_lease.sql`，为 `rule_action_executions` 增加 `replay_locked_until` 并建立补偿扫描索引。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#webhookActionReplayWorkerProcessesDuePendingExecutionWithLease" test` 首次编译失败，缺少 `RuleWebhookActionReplayWorker`。
- GREEN：同一目标测试通过 1 个测试，验证 worker 领取 pending Webhook 动作、调用重放、追加 succeeded 台账，并写入 `rule_action_webhook_retry` 审计。
- 失败路径：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#webhookActionReplayWorkerKeepsFailedReplayPendingForNextAttempt" test` 通过 1 个测试，验证重放失败后追加新的 `pending_retry` 台账并保留错误原因和下次重试时间。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest,ContractSurfaceTest" test` 通过 40 个测试。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 第 78 节已补每条动作的最大异步补偿次数；第 79 节已补批量重放/批量忽略操作；第 80 节已补 worker 指标；第 81 节已补重复未读告警降噪。
- 规则完整画布编辑器仍未提供 webhook endpoint/header/body/signature/retry 的可视化配置表单。

## 78. 2026-06-24 UC-08 规则 Webhook 异步补偿次数上限闭环

本轮目标：避免 Webhook 异步补偿 worker 对同一外部系统失败动作无限重放，给客户生产环境提供最小补偿治理护栏。

已完成：

- Webhook action 节点支持 `maxAsyncReplayAttempts` 元数据，未配置时默认允许 3 次异步补偿；该配置独立于同步发送阶段的 `maxRetryCount`。
- `RuleWebhookActionReplayWorker` 会按 `attempt - maxRetryCount - 1` 计算已发生的异步补偿次数。
- 当最新待补偿动作达到 `maxAsyncReplayAttempts` 时，worker 不再调用外部 Webhook，而是追加 `status=compensation_exhausted` 的终态动作台账。
- 达到上限时写入 `rule_action_webhook_replay_exhausted` 审计，包含 source action、终态 action、attempt、maxAsyncReplayAttempts、endpoint 和 idempotencyKey。
- `findDueWebhookActionExecutions()` 改为同一 `idempotencyKey` 只返回最新一条 `pending_retry/failed` 台账，避免旧 pending 记录在后续扫描中重复领取。
- 人工重放 API 保持可用，不被自动补偿次数上限拦截，便于客户现场修复外部系统后人工干预。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#webhookActionReplayWorkerExhaustsActionAfterMaxAsyncReplayAttempts" test` 首次失败为第二次 worker 扫描继续处理 2 条 pending，并再次调用 Webhook，证明当前缺少同幂等键最新台账过滤和异步补偿上限。
- GREEN：同一目标测试通过 1 个测试，验证达到上限后不再调用 Webhook，追加 `compensation_exhausted` 台账并写 `rule_action_webhook_replay_exhausted` 审计。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest,ContractSurfaceTest" test` 通过 41 个测试，0 失败。

仍需继续：

- Webhook 补偿治理的 worker 指标已在第 80 节闭合，重复未读告警降噪已在第 81 节闭合。
- 规则完整画布编辑器仍未提供 webhook endpoint/header/body/signature/retry/maxAsyncReplayAttempts 的可视化配置表单。

## 79. 2026-06-25 UC-08 规则 Webhook 批量补偿操作闭环

本轮目标：把 Webhook 动作补偿从“逐条点击”推进到“运营人员可批量处理”，覆盖批量重放和批量忽略，减少客户现场外部 ERP/OA/财务系统短时故障后的人工处理成本。

已完成：

- `RuleApplicationService.batchHandleWebhookActionExecutions(ruleId, request)` 支持 `operation=retry/ignore`、`actionExecutionIds` 和可选 `reason`。
- 批量重放逐项复用单条 `retryWebhookActionExecution()`，继续沿用原幂等键、动作台账和 `rule_action_webhook_retry` 审计链路。
- 批量忽略新增 `ignoreWebhookActionExecution()`，仅允许处理当前规则下 `actionType=webhook` 且 `pending_retry/failed` 的动作，追加 `status=compensation_ignored` 终态动作台账。
- 批量接口返回 `requestedCount/succeededCount/failedCount/items`，逐项标明 `succeeded/failed`、处理后的 actionExecutionId、终态 status 或错误原因，避免部分失败被整体吞掉。
- 批量操作额外写入 `rule_action_webhook_batch_retry` 或 `rule_action_webhook_batch_ignore` 审计，记录批量请求、成功/失败数量和每项结果。
- 后端 REST 暴露 `POST /api/v1/rules/{ruleId}/action-executions/batch`，请求体使用 JSON 对象描述操作和动作 ID 列表。
- 前端 `ruleApi.batchHandleWebhookActionExecutions()` 已补契约；`RuleDesigner.vue` 的 Webhook action ledger 支持勾选可补偿动作并触发 `Batch retry` / `Batch ignore`，执行后清空选择并刷新台账。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#batchRetriesAndIgnoresWebhookActionExecutionsWithPerItemResults" test` 首次编译失败为缺少 `batchHandleWebhookActionExecutions(Long, Map)`，证明系统没有批量补偿入口。
- GREEN：同一目标测试通过 1 个测试，验证批量 retry 成功追加 succeeded 台账、批量 ignore 追加 `compensation_ignored` 台账，并写入 `rule_action_webhook_batch_retry/rule_action_webhook_batch_ignore` 审计。
- 回归修复：旧用例 `productionRunRetriesWebhookActionWithSameIdempotencyKeyWithoutLeakingSample` 恢复 `FlakyWebhookClient(2)` 测试夹具，目标测试通过 1 个测试，重新证明同步重试两次后第三次成功。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest,ContractSurfaceTest" test` 通过 42 个测试，0 失败。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试，覆盖批量补偿 API。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，覆盖规则页动作台账和批量忽略交互。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- Webhook 补偿治理已补 worker 扫描/结果/耗时指标、重复未读告警降噪和规则页补偿运营摘要。
- 规则完整画布编辑器仍未提供 webhook endpoint/header/body/signature/retry/maxAsyncReplayAttempts 的可视化配置表单，也未闭合子流程/人工审批节点。

## 80. 2026-06-25 UC-08 规则 Webhook worker 指标闭环

本轮目标：把 Webhook 异步补偿 worker 从“有后台重放能力”推进到“可被运维观测”，让客户生产环境能看见扫描次数、处理结果和耗时分布。

已完成：

- `RuleWebhookActionReplayWorker` 接入 Micrometer `MeterRegistry`，Spring 构造器支持依赖注入，同时保留测试可直接构造的兼容入口。
- 每次 `runDueReplaysOnce()` 记录 `rule.webhook.replay.worker.scans` 扫描计数。
- 每个动作处理结果记录 `rule.webhook.replay.worker.actions{result=...}`，当前覆盖 `succeeded`、`failed`、`exhausted` 和 `lease_skipped`。
- 每轮扫描记录 `rule.webhook.replay.worker.duration` 计时器，用于后续 Prometheus/Grafana 观测 worker 耗时。
- 指标不写入 Webhook body、输入样本、`secretPrompt` 或 `signatureSecret`，只暴露运营级结果标签。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#webhookActionReplayWorkerPublishesOperationalMetrics" test` 首次失败为 worker 构造器不支持 `MeterRegistry`，证明此前没有可验证的 worker 指标出口。
- GREEN：同一目标测试通过 1 个测试，验证 2 次扫描、1 次 failed、1 次 exhausted、1 次 lease_skipped 和 2 次 duration 记录。
- 回归修复：新增构造器后 `ReportCoreApplicationContextTest#contextLoadsWithReportGenerationTaskRepository` 曾暴露 Spring 构造器选择歧义；已通过在 4 参数 Spring 构造器标记 `@Autowired` 修复，目标上下文测试通过 1 个测试。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest,ContractSurfaceTest" test` 通过 43 个测试，0 失败。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 规则页已在第 82 节补最小补偿运营摘要；Dashboard 级补偿趋势可作为后续增强。
- 规则完整画布编辑器仍未提供 webhook endpoint/header/body/signature/retry/maxAsyncReplayAttempts 的可视化配置表单，也未闭合子流程/人工审批节点。

## 81. 2026-06-25 UC-08 规则 Webhook 重复未读告警降噪闭环

本轮目标：避免同一规则、同一 Webhook 节点、同一 endpoint 连续失败时不断创建重复未读告警，同时保留动作台账和审计，便于运营处理真实积压。

已完成：

- `SystemAlertRepository` 增加 `existsUnreadByDedupeKey()` 查询能力，默认实现保持兼容。
- `JdbcSystemAlertRepository` 使用 PostgreSQL JSONB 条件 `payload_json ->> 'dedupeKey'` 查询同类未读告警。
- Webhook 失败告警 payload 写入稳定 `dedupeKey=rule:{ruleId}:webhook:{nodeId}:{endpoint}`。
- `RuleApplicationService.createWebhookFailureAlert()` 保存告警前先检查同一 recipient、type、resource 和 `dedupeKey` 是否已有未读告警；存在时不再重复创建。
- 降噪只影响未读系统告警数量，不影响 `rule_action_executions` 动作台账、`rule_action_webhook` 审计和失败生产运行记录。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunSuppressesDuplicateUnreadWebhookFailureAlertsForSameRuleNodeAndEndpoint" test` 首次失败，显示同一规则节点连续失败创建了 2 条 `rule_action_webhook_failed` 未读告警。
- GREEN：同一目标测试通过 1 个测试，验证连续两次失败只保留 1 条未读告警，同时保留 2 条 `pending_retry` 动作台账和 2 条 Webhook 审计。
- 原有失败告警回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunCreatesFailedRunAndAlertWhenWebhookActionFailsWithoutLeakingSample+productionRunSuppressesDuplicateUnreadWebhookFailureAlertsForSameRuleNodeAndEndpoint" test` 通过 2 个测试。
- PostgreSQL JSONB 查询验证：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#findsUnreadSystemAlertByDedupeKeyInPostgres" test` 通过 1 个真实 PostgreSQL 测试，并验证 Flyway 迁移到 v033 后 `payload_json ->> 'dedupeKey'` 可查询。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest,ReportCoreApplicationContextTest,ContractSurfaceTest" test` 通过 44 个测试，0 失败。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 规则页已在第 82 节补最小补偿运营摘要；Dashboard 级补偿趋势可作为后续增强。
- 规则完整画布编辑器仍未提供 webhook endpoint/header/body/signature/retry/maxAsyncReplayAttempts 的可视化配置表单，也未闭合子流程/人工审批节点。

## 82. 2026-06-25 UC-08 规则 Webhook 补偿运营摘要闭环

本轮目标：在规则页面把 Webhook 补偿状态从“只能逐条看台账”推进到“可快速看见积压、成功、耗尽和忽略概况”，便于客户现场运营判断是否需要批量处理或外部系统排障。

已完成：

- `RuleDesigner.vue` 在 Webhook action ledger 上方新增 `Compensation operations` 摘要区。
- 摘要基于当前已加载的动作台账本地计算，不新增后端接口，展示 `Pending`、`Succeeded`、`Exhausted`、`Ignored` 和 `Success rate`。
- `pending_retry/failed` 统一计入待补偿积压，`compensation_exhausted` 和 `compensation_ignored` 单独展示，避免终态被混入成功。
- 摘要随刷新、单条重放、批量 retry/ignore 后的台账重新加载自动更新。
- 页面仍保留逐条 endpoint、幂等键、attempt、过滤、单条重放和批量处理能力。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败为找不到 `Compensation operations`，证明规则页没有补偿运营摘要。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖 pending/succeeded/exhausted/ignored 计数、25% 成功率、批量 ignore 后行级状态变化和单条 retry。
- 前端类型：`npm run typecheck` 通过。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。
- Compose：`docker compose --env-file .env.example config --quiet` 通过。

仍需继续：

- 当前运营摘要是规则页当前分页台账的轻量视图；超大规模场景仍需要后端预聚合、物化视图或专门统计接口。
- Webhook headers/body 可视化配置已在第 84 节补齐；规则完整画布编辑器仍未闭合拖拽建模、节点新增/删除、子流程/人工审批节点。

## 83. 2026-06-25 UC-08 规则 Webhook 节点配置表单闭环

本轮目标：把 Webhook action 从“只能依赖静态规则定义”推进到“规则页面可编辑并保存核心 Webhook 配置”，减少客户现场修改 ERP/OA/财务系统回写参数时对代码或种子数据的依赖。

已完成：

- `RuleDesigner.vue` 在规则画布下方新增 `Webhook node configuration` 配置区。
- 页面会从当前规则定义中识别 `type=action/actionType=webhook` 节点，并展示节点选择、endpoint、method、同步重试次数、同步退避秒数、异步补偿上限和签名密钥字段。
- `Save webhook config` 调用既有 `PUT /rules/{ruleId}` 保存接口，将 `endpoint/method/maxRetryCount/retryBackoffSeconds/maxAsyncReplayAttempts/signatureSecret` 写回规则定义；headers/body 已在第 84 节扩展为同一表单内保存。
- 保存后复用 `replaceSelectedRule()` 刷新当前规则、运行历史和动作台账，避免配置保存后页面状态与补偿账本脱节。
- 节点卡片会展示 Webhook 节点的 method 和 endpoint；执行历史账本仍展示历史动作的 endpoint，避免把新配置误认为已发生动作的历史参数。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败为找不到 `Webhook node configuration`，证明此前规则页没有 Webhook 配置表单。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖表单初始值、编辑保存、`PUT /rules/12` payload 写回规则定义，以及后续审批、生产运行、动作台账、批量 ignore、单条 retry 链路未被破坏。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- Webhook headers/body JSON 编辑器已在第 84 节补齐。
- 规则节点新增/删除保存已在第 85 节补齐，连线新增/删除已在第 86 节补齐；完整画布编辑器仍未闭合拖拽建模、子流程/人工审批节点。
- 超大规模动作台账与规则 metrics 仍需后端预聚合、物化视图或专门统计接口。

## 85. 2026-06-25 UC-08 规则节点新增删除保存闭环

本轮目标：把规则页面从“只能查看与配置已有节点”推进到“可修改画布节点集合”，让客户可以在页面上增删规则节点并保存定义。

已完成：

- `RuleDesigner.vue` 新增 `Rule canvas editor` 区域。
- 支持输入 `New node id`、选择 `New node type`，对 action 节点可填写 `New action type` 与 `New action message`。
- `Add node` 会把新节点加入当前规则定义并立即展示在节点卡片中。
- `Delete node` 支持删除非 `start` 节点，删除时同步清理 source/target 指向该节点的边，避免保存悬空连线。
- `Save canvas changes` 复用 `PUT /rules/{ruleId}` 保存当前规则定义，并保留现有审批、生产运行、Webhook 配置和动作台账链路。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败为找不到 `Rule canvas editor`，证明此前没有节点新增/删除编辑入口。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖新增 `notify` action 节点、删除 `archiveLowRisk` 节点、保存 payload 中新增节点存在且删除节点及关联边不存在，并继续覆盖审批、生产运行、Webhook 配置、补偿账本、批量忽略和单条重放链路。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- condition/branch/aggregate 字段级配置已在第 87 节补齐。
- 连线新增/删除已在第 86 节补齐；拖拽定位、自动布局、子流程/人工审批节点仍未闭合。
- 超大规模动作台账与规则 metrics 仍需后端预聚合、物化视图或专门统计接口。

## 86. 2026-06-25 UC-08 规则连线新增删除保存闭环

本轮目标：把规则页面从“可修改节点集合”推进到“可修改节点之间的执行路径”，让客户可以在页面上增删规则连线并保存定义。

已完成：

- `RuleDesigner.vue` 的 `Rule canvas editor` 新增连线编辑区。
- 支持选择 `New edge source`、`New edge target`，填写可选 `New edge condition` 并通过 `Add edge` 加入当前规则定义。
- 支持通过 `Delete edge` 删除已有连线。
- 新增连线会立即展示在 edge 列表中；删除连线后 edge 列表同步隐藏。
- `Save canvas changes` 会把节点和连线变更一起通过 `PUT /rules/{ruleId}` 保存，继续复用后端规则定义校验。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次在 `getByLabel('New edge source')` 超时，证明此前没有连线新增/删除入口。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖新增 `escalateFinance -> aging` 连线、删除 `riskBranch -> notifyHighRisk` 连线、保存 payload 中新增连线存在且删除连线不存在，并继续覆盖节点新增/删除、Webhook 配置、审批、生产运行、补偿账本、批量忽略和单条重放链路。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- 当前连线编辑为表单式，不支持拖拽连线、自动布局和可视化冲突提示。
- condition/branch/aggregate 的字段级配置已在第 87 节补齐；子流程/人工审批节点仍未闭合。
- 超大规模动作台账与规则 metrics 仍需后端预聚合、物化视图或专门统计接口。

## 87. 2026-06-25 UC-08 规则节点字段级配置闭环

本轮目标：把规则页面的新增节点从“只创建空壳节点”推进到“可按节点类型填写核心业务字段”，让客户在页面上新增 condition、branch 和 aggregate 节点后即可保存可执行定义。

已完成：

- `RuleDesigner.vue` 的 `Rule canvas editor` 对 condition/branch 节点新增 `field/operator/value` 表单字段。
- aggregate 节点新增 `sourceField/operation/valueField/outputField` 表单字段，并限制 operation 在 `sum/count` 中选择。
- 前端会阻止空字段 condition/branch 节点进入画布；aggregate 会校验 source/output，且 sum 聚合要求 valueField。
- `Add node` 会按节点类型把字段写入当前规则 definition，节点卡片立即展示条件表达式或聚合表达式。
- `Save canvas changes` 继续复用既有 `PUT /rules/{ruleId}` 保存接口，保存 payload 会包含新增节点的字段级配置。
- 该改动不改变已有 action、Webhook 配置、连线编辑、审批、生产运行和补偿账本链路。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次在 `getByLabel('New condition field')` 超时，证明规则画布新增节点缺少字段级配置入口。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖新增 `creditHold` condition 节点、填写 `creditStatus = hold`、节点卡片展示表达式，并断言保存 payload 包含 `field/operator/value`。
- RED：同一 E2E 增补空 condition 节点用例后，首次失败为找不到 `New condition field is required`，证明前端未阻止空字段节点。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖空 condition 节点被前端拦截，且无效节点不进入画布。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenConditionUsesSingleEquals_thenTreatsItAsEquality" test` 首次失败为 `RULE_VALIDATION_FAILED`，证明前端可输入的 `operator="="` 在真实后端调试/生产执行链路会断开。
- GREEN：同一后端目标测试通过 1 个测试，`RuleDomainService` 将 `=` 与 `==` 一样按等值判断执行，避免规则画布保存后只在 mock E2E 中成立。

仍需继续：

- 当前字段级配置仍是表单式，不支持拖拽节点、自动布局、可视化连线冲突提示或 schema 驱动的高级校验提示。
- 子流程、人工审批等复杂节点仍未闭合。
- 超大规模动作台账与规则 metrics 仍需后端预聚合、物化视图或专门统计接口。

## 88. 2026-06-25 UC-08 create_task 节点前端配置闭环

本轮目标：把后端已支持的 `actionType=create_task` 从“只能依赖静态规则定义”推进到“规则页面可新增并配置协作任务动作”，让页面保存的定义可以被生产运行链路直接执行。

已完成：

- `RuleDesigner.vue` 在新增 action 节点且 `actionType=create_task` 时展示 `reportId/assigneeUserId/content/anchor` 配置字段。
- 前端新增校验：`reportId`、`assigneeUserId` 必须是正整数，`content` 和 `selectedText` 必填，`startOffset` 必须是非负整数，`endOffset` 必须大于 `startOffset`。
- 新增节点会把 `reportId/assigneeUserId/content/anchor.sectionId/startOffset/endOffset/selectedText` 写入规则 definition，保存 payload 与后端 `RuleApplicationService` 的 create_task 执行契约一致。
- 节点卡片会展示 `task report {reportId} -> user {assigneeUserId}`，便于规则编辑者在画布上快速核对动作目标。
- 该改动复用既有规则保存接口，不改变 Webhook 配置、补偿账本、审批、生产运行和调度链路。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次在 `getByLabel('New task report id')` 超时，证明前端规则画布缺少 create_task 配置入口。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖新增 `createReviewTask` action 节点、填写任务目标与 anchor、节点卡片展示，并断言 `PUT /rules/12` payload 包含后端可执行的 `create_task` 定义。
- 前端类型：`npm run typecheck` 通过。
- 前端契约：`npm run test -- apiContracts` 通过 20 个测试。

仍需继续：

- 当前 create_task 已支持报告和指派人的下拉选择，也可从报告正文选中文本生成 anchor，降低客户配置错误率。
- 独立人工审批节点、子流程节点、拖拽式完整画布和高级可视化校验仍未闭合。

## 89. 2026-06-25 UC-08 create_task 后端 schema 校验闭环

本轮目标：把 `create_task` 从“前端可配置、生产执行时再暴露缺字段”推进到“保存/发布前由规则 definition 校验拒绝无效任务节点”，避免 API 绕过前端或旧定义导致生产运行失败。

已完成：

- `RuleDomainService.validateDefinition()` 对 `actionType=create_task` 增加专属 schema 校验。
- 后端会要求 `reportId/assigneeUserId` 为正整数，`content` 必填，`anchor.sectionId/selectedText` 必填。
- 后端会要求 `anchor.startOffset` 为非负整数，`anchor.endOffset` 为非负整数且必须大于 `startOffset`。
- 其他 action 类型继续保持原有兼容行为，避免误伤 notify、archive、webhook 等既有规则定义。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#validateDefinition_whenCreateTaskActionMissingTaskFields_thenThrows" test` 首次失败为 `Expected BusinessException to be thrown, but nothing was thrown`，证明后端此前会放行缺字段 create_task。
- GREEN：同一目标测试通过 1 个测试，缺少任务字段的 create_task 现在返回 `RULE_VALIDATION_FAILED`。
- 规则域回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest" test` 通过 8 个测试，覆盖 debug 输入、节点/连线校验、condition/aggregate/branch 执行和 `=` 等值兼容。

仍需继续：

- create_task 配置已支持报告和指派人的下拉选择，也可从报告正文选中文本生成 anchor，降低客户配置错误率。
- 独立人工审批节点、子流程节点、拖拽式完整画布和高级可视化校验仍未闭合。

## 90. 2026-06-25 UC-08 create_task 报告与指派人选择器闭环

本轮目标：把 `create_task` 节点从“手工输入 reportId/assigneeUserId”推进到“优先从现有报告和启用用户列表选择”，减少客户配置任务动作时填错 ID 的概率，同时保留手工输入作为接口或数据权限不足时的 fallback。

已完成：

- `RuleDesigner.vue` 进入规则页时复用 `reportApi.list({ page: 1, pageSize: 20 })` 加载报告候选项。
- 复用 `adminApi.listUsers({ page: 1, pageSize: 20 })` 加载用户候选项，并只展示 `status=enabled` 的可指派用户。
- 新增 `New task report selector` 和 `New task assignee selector` 两个下拉，选择后同步写入原 `reportId/assigneeUserId` 字段。
- 原 `New task report id` 和 `New task assignee user id` 输入框保留，保证接口受限、列表为空或需要临时 ID 时仍可手工填写。
- 保存 payload 不变，继续输出后端 `create_task` 可执行定义。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败为 `Element is not a <select> element`，证明页面还没有 report/user 选择器。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖 mock 报告列表、mock 用户列表、选择 `reportId=88` 和 `assigneeUserId=2`、同步到原 ID 字段，并断言保存 payload 仍包含后端可执行的 create_task 定义。

仍需继续：

- create_task 的正文 anchor 选区生成已在第 91 节补齐；手工字段继续保留作为 fallback。
- 独立人工审批节点、子流程节点、拖拽式完整画布和高级可视化校验仍未闭合。

## 91. 2026-06-25 UC-08 create_task 正文 anchor 选区闭环

本轮目标：把 `create_task` 的 anchor 从“手工填写 section/start/end/selectedText”推进到“从目标报告正文选中文本自动生成 anchor”，减少任务配置时的文本锚点错误，并保持手工字段作为兜底。

已完成：

- `RuleDesigner.vue` 在选择 `New task report selector` 后调用 `reportApi.getDetail(reportId)` 加载报告详情。
- 页面展示目标报告 sections 的只读正文片段，作为 `Task anchor source`。
- 用户在正文片段中选中文本后点击 `Use selected text as task anchor`，自动写入 `sectionId/startOffset/endOffset/selectedText`。
- 原 `New task section id/start offset/end offset/selected text` 输入框继续保留，支持列表或选区不可用时手工修正。
- 保存 payload 不变，仍输出后端 `create_task` 可执行定义。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败为找不到 `Risk summary`，证明规则页选择报告后还没有报告正文 anchor 来源。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖 mock `/reports/88` 详情、展示 `Risk summary`、选中 `High risk`、点击 `Use selected text as task anchor` 后自动填充 `summary/0/9/High risk`，并断言保存 payload 包含相同 anchor。

仍需继续：

- 规则画布仍是表单式，不支持拖拽布局、画布缩放、连线拖拽或高级可视化校验。
- 独立人工审批节点和子流程节点仍未闭合。

## 92. 2026-06-25 UC-08 approval 节点最小闭环

本轮目标：补齐规则编排中的独立 `approval` 节点最小建模能力，让前端可配置审批角色与审批标题，后端可校验 schema，并在 debug trace 中明确输出待审批状态，作为后续真实人工审批工作流的承接点。

已完成：

- `RuleDomainService.validateDefinition()` 新增 `approval` 节点校验，要求 `assigneeRole` 与 `approvalTitle` 必填。
- `RuleDomainService.executeDebug()` 新增 `approval` trace 节点输出，包含 `pendingApproval=true`、`assigneeRole` 与 `approvalTitle`。
- `RuleDesigner.vue` 已支持新增 `approval` 节点，页面可填写 `New approval assignee role` 与 `New approval title`，保存后进入规则定义。
- `rule-engine.spec.ts` 已覆盖 approval 节点新增、节点卡片展示与保存 payload 断言。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenApprovalNodePresent_thenMarksPendingApprovalInTrace" test` 首次失败，原因为 `approval` 节点类型尚未被后端校验与执行链支持。
- GREEN：同用例通过；`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest" test` 通过 9 个测试。
- 前端 RED：`npm run e2e -- rule-engine.spec.ts` 首次失败，因为 `New node type` 中缺少 `approval` 选项。
- 前端 GREEN：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E；`npm run typecheck` 通过。

当前边界：

- 该闭环仅覆盖规则定义、前端配置和 debug trace 建模，不代表已具备真实人工审批流、审批待办中心、驳回/撤回、多级审批或审批 SLA。
- UC-08 仍缺拖拽式完整画布、独立人工审批流编排、子流程节点和更完整的生产级运行治理。

## 93. 2026-06-25 UC-08 approval 待审批记录最小闭环

本轮目标：把 `approval` 节点从“只有规则定义与 debug trace 建模”推进到“生产运行命中后会真实落待审批记录，并可从规则页查询可见”，为后续独立人工审批流编排打基础。

已完成：

- 新增后端领域模型 `RuleApprovalRecord`，并通过 Flyway `V034__rule_approval_records.sql` 建表。
- `RuleApplicationService.execute()` 在生产运行 trace 命中 `approval` 节点时，会写入 `rule_approval_records`，状态为 `pending`，记录 `ruleId/runId/nodeId/assigneeRole/approvalTitle/createdByUserId`。
- 新增 `GET /api/v1/rules/{ruleId}/approval-records` 查询接口，`RuleDesigner.vue` 已接入并展示 `Approval records` 只读列表。
- 追加审计 `rule_node_approval_pending`，用于追踪哪次规则运行生成了待审批节点记录。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode" test` 首次失败，因为 `RuleApplicationService` 还没有 `listApprovalRecords()` 查询面与审批记录持久化结构。
- GREEN：同用例通过 1 个测试。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest,RuleDomainServiceTest" test` 通过 40 个测试。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsApprovalRecordsWhenProductionRunHitsApprovalNodeInPostgres" test` 通过 1 个真实 PostgreSQL 测试，并验证 Flyway 迁移到 `V034`。
- 前端契约/类型：`npm run test -- apiContracts` 通过 20 个测试；`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，并断言规则页在生产运行后展示 `Approval records` 与 `financeApproval / finance_manager / pending`。

当前边界：

- 当前仅实现“待审批记录生成与查询”，尚未实现审批人处理动作、审批通过/驳回状态流、审批待办中心、多级审批与 SLA。
- 这条能力是独立人工审批流编排的入口，不等同于完整审批工作流交付完成。

## 94. 2026-06-25 UC-08 approval 处理动作最小闭环

本轮目标：把 `approval` 节点从“只能生成待审批记录并查询”推进到“审批人可以对待审批记录执行批准/驳回，状态、审计和前端展示同步更新”，补齐最小可操作闭环。

已完成：

- `RuleRepository` 新增 `findApprovalRecordById()` 与 `updateApprovalRecord()`，`InMemoryRuleRepository` 和 `JdbcRuleRepository` 均已支持按记录 ID 查询与更新审批状态。
- `RuleApprovalRecord` 新增 `handle()`，用于在保留原始建单信息的前提下更新 `status/approvedByUserId/approvalComment/approvedAt`。
- `RuleApplicationService` 新增 `handleApprovalRecord(ruleId, approvalRecordId, request)`，支持 `action=approve/reject`，仅允许处理 `pending` 记录，并写入 `rule_node_approval_approved` 或 `rule_node_approval_rejected` 审计。
- `RuleController` 新增 `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/actions`。
- `ruleApi.ts` 新增 `handleApprovalRecord()`，`RuleDesigner.vue` 的 `Approval records` 区域已从只读列表升级为可操作列表：`pending` 记录展示 `Approve/Reject` 按钮，处理成功后刷新审批记录列表并展示更新后的状态与审批意见。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvesPendingApprovalRecordAndWritesAudit+RuleApplicationServiceTest#rejectsPendingApprovalRecordAndPreventsRepeatedHandling" test` 首次失败，提示 `RuleApplicationService.handleApprovalRecord(...)` 未定义。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core clean test "-Dtest=RuleApplicationServiceTest#approvesPendingApprovalRecordAndWritesAudit+RuleApplicationServiceTest#rejectsPendingApprovalRecordAndPreventsRepeatedHandling"` 通过 2 条审批动作应用层测试。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#updatesApprovalRecordStatusWhenHandledInPostgres" test` 通过 1 个真实 PostgreSQL 测试，验证 `rule_approval_records` 状态更新与 `rule_node_approval_approved` 审计落库。
- 前端契约/类型：`npm run test -- apiContracts` 通过 20 个测试；`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- rule-engine.spec.ts` 通过 1 个 Chromium E2E，验证规则页在生产运行后显示 `Approve` 按钮，点击后状态从 `pending` 变为 `approved`，并展示审批意见。

当前边界：

- 当前仍是“审批记录级最小动作闭环”，尚未实现完整审批待办中心、审批通过后驱动规则继续流转、驳回回退路径、多级审批、SLA/催办或审批权限隔离。
- 独立人工审批流编排已从“建模 + 只读记录”推进到“记录可处理”，但距离完整客户级审批工作流仍有明显差距。

## 95. 2026-06-25 UC-08 approval 执行闸门闭环

本轮目标：把 `approval` 节点从“会生成待审批记录，但仍继续执行后续 action”推进到“生产运行命中审批后立即停在审批节点”，让审批在业务执行链中成为真实闸门，而不是只写审计的装饰节点。

已完成：

- `RuleApplicationService.executeRuleActions()` 在命中 `approval` 节点后，仍会先写入 `rule_approval_records` 与 `rule_node_approval_pending` 审计，但不再继续执行 trace 中后续的 `action` 节点。
- 这意味着 `approval -> notify/create_task/webhook -> ...` 的生产链路现在会在审批节点处停住，不会再提前发通知、建协作任务或调用外部 Webhook。
- 该调整保持了现有 debug trace、待审批记录查询、审批批准/驳回动作和审计能力不变，只收紧了生产执行边界。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#stopsDownstreamActionsWhenProductionRunHitsApprovalNode" test` 首次失败，断言显示命中 `approval` 后仍创建了 `rule_action_notify` 告警，证明审批节点此前没有阻断后续动作。
- GREEN：同一目标测试通过，确认命中 `approval` 后仅生成待审批记录，不再产生通知和动作台账。
- 应用层回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` 通过 34 个测试。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsApprovalRecordsWhenProductionRunHitsApprovalNodeInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#stopsDownstreamNotifyActionWhenApprovalNodeIsHitInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#updatesApprovalRecordStatusWhenHandledInPostgres" test` 通过 3 个真实 PostgreSQL 测试，验证 `rule_approval_records` 正常写入，且 `system_alerts(rule_action_notify)` 与 `rule_action_executions` 不再落下游执行痕迹。

当前边界：

- 当前审批已经是“生产执行闸门”，但审批通过后不会自动恢复原规则链继续往下执行，也没有待办中心驱动恢复、驳回回退分支、多级审批、SLA/催办或子流程编排。
- 因此 UC-08 的审批能力已从“记录级最小闭环”推进到“阻断型最小闭环”，但仍未达到完整客户级审批工作流交付标准。

## 96. 2026-06-25 UC-08 approval 批准后续跑闭环

本轮目标：把 `approval` 节点从“批准后只更新记录状态”推进到“批准后会恢复执行该审批节点后续的规则动作”，让审批链从单纯阻断进一步变成最小可恢复流转。

已完成：

- `RuleRepository` 新增 `findRunById(runId)`，`InMemoryRuleRepository` 与 `JdbcRuleRepository` 都可按 `approvalRecord.runId` 取回原生产运行。
- `RuleApplicationService.handleApprovalRecord()` 在 `action=approve` 时，会读取原始 `RuleDebugRun.output.trace`，并从被批准的 `approval` 节点之后继续执行剩余 `action` 节点。
- 续跑逻辑复用既有 `notify/create_task/webhook` 执行分支，不重新发起整条生产运行，也不会再次创建新的待审批记录。
- `reject` 路径仍只更新审批状态，不触发后续动作；重复处理仍会被 `pending` 状态保护拒绝。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvingPendingApprovalRecordResumesDownstreamActions" test` 首次失败，断言显示 `approve` 后 `rule_action_notify` 仍未触发，证明批准后续跑能力此前不存在。
- GREEN：同一目标测试通过，验证首次生产运行不会提前发通知，而在 `approve` 后会补发 `notify`。
- 应用层回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` 通过 35 个测试。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsApprovalRecordsWhenProductionRunHitsApprovalNodeInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#stopsDownstreamNotifyActionWhenApprovalNodeIsHitInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#updatesApprovalRecordStatusWhenHandledInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#approvingApprovalRecordResumesDownstreamNotifyActionInPostgres" test` 通过 4 个真实 PostgreSQL 测试，验证先阻断、后批准恢复通知、审批记录状态更新与审计落库。

当前边界：

- 当前已具备“阻断 + 批准后最小续跑”闭环，但续跑仍是基于原 trace 的线性后续动作执行，不包含审批驳回回退分支、多级审批、待办中心驱动恢复、人工补单重入控制、SLA/催办或子流程编排。
- 因此 UC-08 的审批能力已从“阻断型最小闭环”推进到“可恢复流转最小闭环”，但仍未达到完整客户级审批工作流交付标准。

## 97. 2026-06-25 UC-08 全局审批待办中心最小闭环

本轮目标：把审批能力从“只能进入单条规则页面查看和处理审批记录”推进到“可跨规则集中查看待审批项并直接处理”，补齐客户可见的最小审批待办中心。

已完成：

- `RuleRepository` 新增 `findApprovalRecordsByStatus(status, page, pageSize)` 与 `countApprovalRecordsByStatus(status)`，`InMemoryRuleRepository` 和 `JdbcRuleRepository` 均已支持按状态分页查询审批记录。
- `RuleApplicationService` 新增 `listPendingApprovalRecords(page, pageSize)`，当前最小交付只开放 `pending` 状态全局查询。
- `RuleController` 新增 `GET /api/v1/rules/approval-records?page=1&pageSize=20&status=pending`，统一返回跨规则待审批分页结果。
- 前端新增 `ApprovalInbox.vue` 页面与 `/rules/approvals` 路由，侧边导航已补 `审批待办` 入口。
- 审批待办页可展示 `approvalTitle/ruleId/nodeId/assigneeRole/createdAt`，并支持直接 `Approve/Reject`；处理成功后自动刷新全局待办列表。
- `ruleApi.ts`、前端 API 契约测试和 Playwright E2E 已补齐全局待办接口与页面交互覆盖。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsOnlyPendingApprovalRecordsAcrossRules" test` 首次失败，错误为 `RuleApplicationService.listPendingApprovalRecords(...)` 未定义，证明全局审批待办查询此前不存在。
- GREEN：同一目标测试通过 1 个应用层测试，验证两条规则各自产生待审批后，处理其中一条，待办中心只剩另一条 `pending` 记录。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#listsOnlyPendingApprovalRecordsAcrossRulesInPostgres" test` 通过 1 个真实 PostgreSQL 测试，验证按状态跨规则查询真实生效。
- 前端契约：`npm run test -- src/api/apiContracts.test.ts` 通过 20 个测试，包含 `GET /rules/approval-records` 契约断言。
- 前端 E2E：`npm run e2e -- tests/e2e/approval-inbox.spec.ts` 通过 1 个 Chromium 测试，验证审批待办页展示两条待办、点击 `Approve` 后列表刷新为剩余 1 条。
- 前端类型：`npm run typecheck` 通过。

当前边界：

- 当前待办中心只开放 `pending` 状态最小查询，不包含已处理历史筛选、审批人维度过滤、搜索、催办、批量审批或 SLA。
- 驳回后仍只更新审批记录状态，不包含回退到规则编辑、补充材料或重新提交的完整业务分支。
- 因此这条能力补齐了“完整审批待办中心”的最小客户可见入口，但还没有扩展到完整企业级审批运营台。

## 98. 2026-06-25 UC-08 approval 驳回回退反馈最小闭环

本轮目标：把审批驳回从“只更新审批记录状态，没有后续反馈”推进到“驳回后系统会明确通知发起侧需要补充材料并重新发起运行”，补齐最小可见回退路径。

已完成：

- `RuleApplicationService.handleApprovalRecord()` 在 `action=reject` 时新增 `rule_approval_rejected` 系统告警写入。
- 告警 payload 包含 `ruleId/ruleName/approvalRecordId/runId/nodeId/status/comment/assigneeRole/approvalTitle`，便于后续对接更完整的补件、重跑或待办协作流程。
- 当前 reject 路径仍不会恢复下游动作，也不会回写规则发布状态，保持已发布规则与审批实例解耦。
- 前端审批待办页在 `Reject` 成功后展示明确反馈文案：`Approval rejected. Submit updated materials before running again.`，避免用户只看到待办消失却不知道下一步该做什么。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsPendingApprovalRecordAndPreventsRepeatedHandling" test` 首次失败，断言显示 `alertRepository.alerts` 为空，证明驳回路径此前没有任何回退反馈。
- GREEN：同一应用层测试通过，验证 `reject` 后审批记录状态为 `rejected`，并新增 1 条带 `comment=missing attachment` 的 `rule_approval_rejected` 告警。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#createsRejectedApprovalAlertWhenApprovalIsRejectedInPostgres" test` 通过 1 个真实 PostgreSQL 测试，验证 `system_alerts.type=rule_approval_rejected` 与 `payload_json.comment` 真实落库。
- 前端 E2E：`npm run e2e -- tests/e2e/approval-inbox.spec.ts` 通过 2 个 Chromium 测试，其中新增 reject 路径验证驳回成功提示和待办列表刷新。
- 后端回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvingPendingApprovalRecordResumesDownstreamActions,RuleApplicationServiceTest#rejectsPendingApprovalRecordAndPreventsRepeatedHandling" test` 通过 2 个测试，确认 `approve` 续跑链路未被破坏。

当前边界：

- 当前“回退”仍是运行实例级反馈，不是完整的驳回回编、补件任务、重新提交审批或规则状态回滚。
- 尚未补齐独立审批运营台中的驳回历史筛选、责任人分派、批注协同或自动重跑入口。

## 99. 2026-06-25 UC-08 approval 已处理历史与状态筛选闭环

本轮目标：把全局审批待办中心从“只能看 pending”推进到“可查看 pending / approved / rejected 三类记录”，补齐客户在同一入口查看审批历史的最小运营能力。

已完成：

- `RuleApplicationService` 新增 `listApprovalRecordsByStatus(status, page, pageSize)`，`listPendingApprovalRecords()` 复用该能力。
- `RuleController` 放开 `GET /api/v1/rules/approval-records` 的 `status` 参数校验，支持 `pending`、`approved`、`rejected` 三种状态。
- 前端 `ApprovalInbox.vue` 新增 `Pending / Approved / Rejected` 状态切换按钮，并根据当前状态展示不同的 summary、空态文案和记录列表。
- 已处理审批记录在审批待办页展示 `status`、`approvalComment`、`approvedAt`，且仅 `pending` 记录显示 `Approve/Reject` 操作按钮，避免误操作已处理记录。
- 前端 API 契约与 Playwright E2E 已覆盖：
  - `approved/rejected` 状态查询参数正确透传；
  - 审批通过后可切换到 `Approved` 查看新产生的历史记录；
  - 审批驳回后可切换到 `Rejected` 查看驳回记录与驳回意见。

验证证据：

- 后端应用层：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsHandledApprovalRecordsByStatusAcrossRules" test` 通过 1 个测试。
- PostgreSQL：`RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#listsHandledApprovalRecordsByStatusAcrossRulesInPostgres" test` 通过 1 个真实 PostgreSQL 测试。
- 前端契约：`npm run test -- src/api/apiContracts.test.ts` 通过 20 个测试。
- 前端类型：`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- tests/e2e/approval-inbox.spec.ts` 通过 2 个 Chromium 测试，覆盖审批待办、批准后历史切换和驳回后历史切换。

仍需继续：

- 当前审批中心已补最小过滤能力：可按 `ruleId/assigneeRole/approvalTitle` 过滤跨规则审批记录；后续继续补更完整的时间范围、发起人、审批人等运营维度过滤。
- 仍未补齐批量审批、催办、SLA 超时预警、审批撤回、重新提交和更完整的审批运营台能力。

## 102. 2026-06-25 UC-04 企业导出目录页码与页脚页码闭环

本轮目标：把企业导出从“只有目录标题和页脚文案”推进到“目录显式带页码、文件显式带页脚页码”，让 Word/PDF/PPT 更接近客户可交付文档。

已完成：

- `ReportApplicationService.documentXml()` 现在会在 `Table of Contents` 下输出带页码的目录条目，例如 `1. Executive Summary .... 2`。
- `ReportApplicationService.renderPdf()` 现在会在 PDF 正文中输出同样的目录页码文本，并补充显式页脚页码 `Page 1 of N`。
- DOCX 导出正文末尾新增稳定页脚页码文本 `Page 1 of N`，与 PDF 语义保持一致。
- `slideXml()` 重新承接企业 `brand` 参数，恢复 PPTX 中的公司名、页眉、目录与页脚文案，并同步带上目录页码文本，避免 PPT 与 Word/PDF 契约再次漂移。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument" test` 首次失败，断言显示 Word/PDF 均缺少 `1. Executive Summary .... 2`。
- GREEN：同一命令通过 2 个测试，证明 DOCX/PDF 已输出目录页码与页脚页码文本。
- 后端导出回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#enterpriseWordExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 通过 5 个测试，确认 Word/PDF/PPT 企业导出、真实 Logo 引用与新增目录页码能力同时成立。
- 前端类型：`npm run typecheck` 通过，确认本轮后端导出契约增强未引入前端类型回归。

当前边界：

- 当前页码仍是稳定文本式页码，不是基于真实分页计算的 Word 域、PDF 多页分页引擎或 PPT 母版页码。
- 高级企业模板、主题样式、复杂目录自动刷新、页眉页脚部件化和 SVG Logo 到 PDF 图片流的完整转换仍需后续继续补齐。

## 103. 2026-06-25 UC-04 DOCX 真实页眉页脚部件闭环

本轮目标：把 Word 导出从“正文中伪装页眉页脚文案”推进到“真实 OOXML header/footer 部件”，让客户下载的 DOCX 更接近正式企业文档结构。

已完成：

- `renderDocx()` 现在会额外写出 `word/header1.xml` 与 `word/footer1.xml`，不再只把页眉页脚内容塞在 `word/document.xml` 正文中。
- `[Content_Types].xml` 已增加 `/word/header1.xml` 与 `/word/footer1.xml` 的 override，确保生成文件具备完整的 Word 部件声明。
- `word/_rels/document.xml.rels` 已新增 `rIdHeader` 与 `rIdFooter`，`document.xml` 中的 `w:sectPr` 也已新增 `w:headerReference` / `w:footerReference` 引用。
- 企业公司名与页眉文案已进入 `header1.xml`；页脚文案与 `Page 1 of N` 进入 `footer1.xml`；正文保留报告标题、目录、章节、引用和 Logo，不再混入页眉页脚文本。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test` 首次失败，断言显示 `document.xml` 中缺少 `headerReference`，证明此前 DOCX 仍没有真实页眉页脚部件。
- GREEN：同一命令通过 1 个测试，验证 `word/header1.xml`、`word/footer1.xml`、`document.xml` 的 `headerReference/footerReference` 以及 `document.xml.rels` / `[Content_Types].xml` 中的相关部件声明都已存在。
- 后端导出回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#enterpriseWordExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 通过 5 个测试，确认 Word 页眉页脚部件升级没有破坏 Word/PDF/PPT 的导出与 Logo 能力。
- 前端类型：`npm run typecheck` 通过，确认本轮后端导出结构升级未引入前端回归。

当前边界：

- 当前真实部件只先补在 DOCX；PDF 仍是最小 PDF 文本流页脚，PPT 仍是 slide 正文内的页眉页脚文本，不是母版或专用部件。
- Word 页码仍是稳定文本 `Page 1 of N`，尚未升级为基于真实分页域的自动页码与目录刷新。

## 104. 2026-06-25 UC-04 PPTX 独立页眉页脚形状闭环

本轮目标：把 PPT 导出从“页眉页脚文案混在正文文本框里”推进到“独立 Header/Footer shape”，让导出的汇报文件更接近正式企业演示文稿结构。

已完成：

- `slideXml()` 不再把公司名、页眉、目录、正文和页脚全部拼成一个 Body 文本框，而是拆成 `Title`、`Body`、`Header`、`Footer` 四个独立 shape。
- `Header` shape 现承载企业公司名与页眉文案；`Footer` shape 现承载页脚文案；`Body` shape 专注目录页码和章节正文，避免页眉页脚与正文混排。
- 企业 Logo 仍通过原有 `rIdLogo` 关系嵌入 `ppt/media/logo.*`，此次结构升级不改变现有真实 Logo 引用能力。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 首次失败，断言显示 `slide1.xml` 中不存在 `name="Header"`，证明此前 PPT 仍是单一正文文本框承载全部内容。
- GREEN：同一命令通过 1 个测试，验证 `slide1.xml` 已存在 `name="Header"`、`name="Footer"` 和保留的 `name="Body"`，且 `Header/Footer` 已具备独立 `cNvPr id`。
- 后端导出回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#enterpriseWordExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 通过 5 个测试，确认 PPT 结构升级没有破坏 Word/PDF/PPT 导出链路。
- 前端类型：`npm run typecheck` 通过，确认本轮后端导出结构升级未引入前端回归。

当前边界：

- 当前 PPT 仅升级为独立文本 shape，不是 Slide Master、Notes、主题占位符或真正的母版页眉页脚系统。
- PDF 仍未升级为更正式的页眉页脚布局；DOCX/PDF/PPT 仍未实现真实分页驱动的目录刷新与自动页码。

## 105. 2026-06-25 UC-04 PDF 页眉/正文/页脚布局闭环

本轮目标：把 PDF 导出从“所有文本顺序堆叠在一个文本流里”推进到“显式页眉区、正文区、页脚区”的最小正式版式，让 PDF 更接近可交付企业报告。

已完成：

- `renderPdf()` 不再使用单一 `BT ... ET` 文本块从上到下输出全部内容，而是拆成 `PDF-HEADER`、`PDF-BODY`、`PDF-FOOTER` 三段独立文本块。
- 页眉区现在承载企业页眉文案与报告标题；正文区承载目录页码和章节正文；页脚区承载页脚文案与 `Page 1 of N`。
- 三段文本分别使用独立坐标起点，当前已明确区分为 `50 742 Td`、`50 690 Td`、`50 70 Td`，避免页眉、正文、页脚全部从同一正文流连续写出。
- 企业品牌色块与 Logo 引用逻辑保持不变；本轮布局升级不影响现有 PNG/JPEG Logo image object 或基础矢量回退能力。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument" test` 首次失败，断言显示 PDF 中不存在 `%% PDF-HEADER`，证明此前仍没有显式布局分区。
- GREEN：同一命令通过 1 个测试，验证 PDF 已包含 `%% PDF-HEADER`、`%% PDF-BODY`、`%% PDF-FOOTER` 注释以及 `50 742 Td`、`50 690 Td`、`50 70 Td` 布局锚点。
- 后端导出回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#enterpriseWordExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable,ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 通过 5 个测试，确认 PDF 布局升级没有破坏 Word/PDF/PPT 导出链路。
- 前端类型：`npm run typecheck` 通过，确认本轮后端导出结构升级未引入前端回归。

当前边界：

- 当前 PDF 仍是单页最小 PDF 1.4 文本流，不具备真实多页分页、自动换页、动态页码总数计算或目录自动刷新。
- DOCX/PDF/PPT 虽然都已补结构层增强，但仍未形成统一主题、模板版本治理和完整高级企业版式系统。

## 84. 2026-06-25 UC-08 规则 Webhook headers/body 配置闭环

本轮目标：把 Webhook 节点配置从核心连接参数推进到完整请求参数，让客户可以在规则页面配置外部 ERP/OA/财务系统需要的自定义 Header 和业务 Body。

已完成：

- `RuleDesigner.vue` 的 `Webhook node configuration` 新增 `Webhook headers JSON` 与 `Webhook body JSON` 两个 JSON 编辑区。
- 页面从规则定义中的 `headers/body` 读取并格式化展示 JSON 对象，未配置时使用 `{}`。
- 保存时会解析两个 JSON 字段，并将对象写回当前 Webhook 节点的 `headers/body`。
- 保存 payload 继续包含 endpoint、method、同步重试、同步退避、异步补偿上限和签名密钥，避免补 headers/body 时丢失已有配置。
- JSON 字段只进入规则定义；动作台账仍展示历史执行参数，避免把新配置误读为旧动作历史。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败为找不到 `Webhook headers JSON`，证明此前规则页没有 headers/body 配置入口。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖 headers/body 初始值展示、编辑保存、`PUT /rules/12` payload 写回规则定义，以及审批、生产运行、补偿账本、批量忽略、单条重放链路未被破坏。
- 前端类型：`npm run typecheck` 通过。

仍需继续：

- JSON 编辑区当前不提供字段级表单、校验提示位置和密钥脱敏策略；后续可升级为 key/value 表格或 schema 驱动表单。
- 规则完整画布编辑器仍未闭合拖拽建模、节点新增/删除、子流程/人工审批节点。
- 超大规模动作台账与规则 metrics 仍需后端预聚合、物化视图或专门统计接口。

## 106. 2026-06-25 UC-04 真实导出 500 根因校正

本轮目标：校正 `REQ-REPORT-004 / UC-04` 真实后端导出 500 的归因，确认问题来自当前代码还是本地烟测环境版本漂移。

已完成：

- 对比当前仓库 `ReportApplicationService.createExport(...)`，确认源码已支持 `pdf/docx/pptx`，与线上 500 现象不一致。
- 复查旧 `ir-java-smoke` 容器日志，明确定位到 `java.lang.IllegalArgumentException: unsupported export format for current delivery slice: pdf`，证明真实烟测容器仍在运行旧镜像。
- 使用当前仓库源码重建 Java 镜像：`docker build -f Dockerfile.java -t intelligent-report-system-java-report-core .`。
- 删除旧 `ir-java-smoke` 并以新镜像重建，恢复 `intelligent-report-infra_default` 与 `intelligent-report-system_report-net` 双网络连接。
- 新容器启动日志已切换为 `ReportCoreApplication v0.1.0-SNAPSHOT`、`profile=dev`，并完成 Flyway 校验，说明 `18082` 已对应当前源码版后端。
- 重跑真实浏览器导出验收 `tests/e2e/report-export-real-backend.spec.ts` 后通过，证明此前 500 已由环境层修复消除。

验证证据：

- 旧容器根因日志：`docker logs --tail 200 ir-java-smoke` 出现 `unsupported export format for current delivery slice: pdf`。
- 新容器启动验证：`docker logs --tail 80 ir-java-smoke` 显示 `ReportCoreApplication v0.1.0-SNAPSHOT`、`The following 1 profile is active: "dev"`、`Schema "public" is up to date. No migration necessary.`。
- 真实后端浏览器 GREEN：在 `frontend/web-console` 目录执行  
  `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 VITE_API_PROXY_TARGET=http://127.0.0.1:18082 npm run e2e -- tests/e2e/report-export-real-backend.spec.ts`，结果 `1 passed`。

结论：

- 这次 `UC-04` 真实导出 500 的根因不是当前导出实现回退，而是本地真实烟测容器使用了旧镜像。
- 当前源码版 Java 服务替换旧容器后，`REQ-REPORT-004` 的真实浏览器导出链路已恢复。

## 107. 2026-06-25 UC-04 真实导出下载链最终闭环

本轮目标：在“旧烟测容器导致 PDF 500”之外，继续把 `REQ-REPORT-004` 剩余的真实下载链问题收敛掉，直到浏览器能对当前 Java 服务完成 PDF 与 PPTX 两条真实导出下载闭环。

已完成：

- `ReportApplicationService.createExport(...)` 不再依赖内存 `AtomicLong` 预分配导出 ID；导出文件先落库，再以真实持久化的 `report_export_files.id` 回写 `/api/v1/files/report-exports/{id}/download-url`，消除导出记录主键与下载 URL 中 ID 偶发错位的问题。
- PostgreSQL 已验证最新导出记录与下载地址对齐，最近真实行如：`69 -> /api/v1/files/report-exports/69/download-url`、`70 -> /api/v1/files/report-exports/70/download-url`。
- MinIO 本地开发配置新增 `storage.minio.public-endpoint`；不再在预签名后强改 Host 为 `127.0.0.1`，而是统一使用 `host.docker.internal` 作为宿主机与容器都可解析的签名终点，避免 `SignatureDoesNotMatch`。
- 当前 `ir-java-smoke` 以 `MINIO_ENDPOINT=http://host.docker.internal:9000` 与 `MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000` 运行，`http://127.0.0.1:18082/actuator/health` 返回 `{"status":"UP"}`。
- `report-export-real-backend.spec.ts` 在真实对象下载阶段改为使用匿名 `request.newContext()` 拉取 MinIO 预签名 URL，避免把业务 Bearer Token 带到对象存储请求里触发 `InvalidRequest (multiple authentication types)`。
- 同一条真实浏览器验收现在同时覆盖 PDF 与 PPTX，不再只证明“某一种格式能过”。

验证证据：

- Java 存储层回归：`.\mvnw.cmd -Dtest=MinioReportExportStorageTest -pl backend/java-report-core test` 通过。
- Java 导出应用层回归：`.\mvnw.cmd -Dtest=ReportApplicationServiceTest#createsControlledDownloadUrlForExportFile+exportsCurrentReportVersionAsPowerPointDocument+exportsPowerPointAsOverviewAndSectionSlides -pl backend/java-report-core test` 通过。
- PostgreSQL 对账：`docker exec ir-postgres psql -U report -d intelligent_report -c "select id, report_id, format, object_key, download_url, created_at from report_export_files order by id desc limit 8;"` 可查到：
  - `69 | 402 | pdf  | ... | /api/v1/files/report-exports/69/download-url`
  - `70 | 403 | pptx | ... | /api/v1/files/report-exports/70/download-url`
- 健康检查：`Invoke-WebRequest -UseBasicParsing http://127.0.0.1:18082/actuator/health` 返回 `{"status":"UP"}`。
- 真实后端浏览器 GREEN：在 `frontend/web-console` 目录执行  
  `cmd.exe /d /s /c "set RUN_REAL_BACKEND_E2E=true&& set REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1&& set REAL_BACKEND_ORIGIN=http://127.0.0.1:18082&& npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts"`，结果 `2 passed (PDF + PPTX)`。

结论：

- `UC-04` 现在不只是“替换旧镜像后不再 500”，而是已经补齐“真实文件生成 -> MinIO 入库 -> 下载地址持久化 -> 受控签发下载 URL -> 浏览器实际下载成功”的完整交付链。
- 当前更像生产差距的部分，已经从“能不能导出/下载”转移到更高阶的企业版式治理：统一品牌模板源、PPT 母版/专用页眉页脚部件、PDF 中 SVG Logo 的更完整转换，以及目录/页码与真实分页联动刷新。

## 128. 2026-06-25 UC-06 Office 文档解析基线补齐

本轮目标：把 UC-06 从“文本对象 + 扫描 PDF 可处理，但常见企业 Office 资料仍近似空白”推进到“`.docx/.xlsx` 能以低资源方式直接入库、检索和参与 RAG”的最小生产切片。

已完成：

- `MinioTextObjectLoader` 在原有 `text_extensions` 与 `pdftotext/tesseract` 二进制抽取链之外，新增 `.docx/.xlsx` 的内建 OOXML 解析分支。
- `.docx` 解析路径直接解包 ZIP 中的 `word/document.xml`，提取 `w:t` 段落文本并按段落换行拼接，不依赖额外宿主机命令。
- `.xlsx` 解析路径读取 `xl/workbook.xml`、`xl/_rels/workbook.xml.rels` 与工作表 XML，输出 `sheet 名 + 行内单元格文本`，单元格之间使用制表符连接，优先保障知识检索和引用可读性，而不是在当前切片里追求复杂表格语义恢复。
- 新解析结果继续复用既有 `DocumentParseIndexingService` 持久化链路，因此 `.docx/.xlsx` 一旦抽出文本，就会沿现有 `document_parse_results -> document_chunks -> embeddings -> knowledge_entries_text` 主线落库。
- 本轮保持“低资源、本地可重复”约束，没有新增重型 OCR 服务，也没有引入 GPU 或高内存依赖；复杂版面理解仍留给后续生产级增强。

验证证据：

- RED：`python -m pytest tests/unit/python/test_minio_object_loader.py -q` 首次新增 `.docx/.xlsx` 用例后失败 2 条，均报 `document ... requires OCR or table extraction before text parsing`，证明当前仓库此前确实没有 Office 文档解析能力。
- GREEN：同一命令在实现后通过 `7 passed`，验证：
  - `.docx` 可提取段落文本
  - `.xlsx` 可提取 sheet 名与行文本
  - 既有 PDF/图片 OCR 行为未回退
- 主链回归：`python -m pytest tests/unit/python/test_minio_object_loader.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_document_processor.py -q` 通过 `17 passed`，覆盖：
  - `.docx/.xlsx` 对象加载
  - `document.parse.completed`
  - `document_chunks` 持久化
  - `knowledge_entries_text` 检索索引写入

结论：

- `UC-06` 当前不再只有“文本对象 + 扫描 PDF”两种可用资料形态，而是补上了客户更常见的 Word/Excel 知识文档基础解析能力。
- 这一步提升的是“真实可入库资料覆盖面”，不是最终生产级文档理解；剩余更大的差距仍在高精度 OCR、复杂表格结构恢复、扫描件版面重建、多栏 PDF 和 Office 深层格式语义抽取。

## 129. 2026-06-25 UC-06 Office 表格与 shared strings 解析增强

本轮目标：在已具备 `.docx/.xlsx` Office 基线解析的前提下，再补两类更贴近真实企业资料的短板：Word 表格文本抽取，以及 Excel shared strings / 稀疏单元格定位，避免知识库导入后表格内容被拆散或列错位。

已完成：

- `extract_docx_text()` 不再只扫正文段落，而是按 `w:body` 的直接子节点区分普通段落与 `w:tbl` 表格。
- Word 表格现在会按 `w:tr -> w:tc` 聚合单元格文本，并把同一行单元格用制表符连接，形成稳定的“行级可检索文本”。
- `extract_xlsx_text()` 新增 `sharedStrings.xml` 读取能力，`t="s"` 的单元格会正确解引用 shared string，而不再把索引编号当正文。
- Excel 行解析新增基于单元格引用 `A1/C2/...` 的列号恢复，即使工作表存在空列或稀疏单元格，也能保留正确的列位置信息并输出空占位制表符。
- 本轮仍保持低资源约束，没有引入 `openpyxl/python-docx` 等额外运行时依赖，而是继续基于当前 OOXML ZIP + XML 解析路径演进。

验证证据：

- RED：`python -m pytest tests/unit/python/test_minio_object_loader.py -q` 首次新增用例后失败 2 条：
  - `.docx` 表格被拆成多行单元格文本，而不是按表格行聚合
  - `.xlsx` shared strings 被错误输出为 `0/1/2/...`，且空列未保留
- GREEN：同一命令在实现后通过 `9 passed`，验证：
  - `.docx` 表格可输出 `Level\tAction`
  - `.xlsx` shared strings 可解析为真实文本
  - 稀疏列可保留为空白制表符占位
- 主链回归：`python -m pytest tests/unit/python/test_minio_object_loader.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_document_processor.py -q` 通过 `19 passed`，确认当时的 Word 表格、Excel shared strings 与稀疏列增强没有破坏既有解析、分块、持久化和搜索索引链路。

结论：

- `UC-06` 的 Office 解析能力已经从“能读最小正文”推进到“能较稳定承接真实制度文档里的 Word 表格、以及真实财务台账里的 Excel shared strings / 空列结构”。
- 距离客户生产版本的剩余差距，已经更集中在复杂表格结构语义恢复、图片内表格识别、扫描件版面重建和更高置信度 OCR 上，而不是基础 Office 文本抽取本身。

## 130. 2026-06-25 UC-06 Excel 多 sheet 与行摘要分块增强

本轮目标：把 UC-06 从“Office 文本可抽取、表格不再错位”进一步推进到“Excel 结构化内容更适合知识检索与 RAG 引用”的最小生产切片，重点补齐多 sheet 稳定输出与按表头生成行摘要 chunk。

已完成：

- `MinioTextObjectLoader.extract_xlsx_text()` 在既有 inline strings / shared strings / 稀疏单元格能力上，新增多 sheet 稳定顺序拼接；每个 sheet 会显式输出独立段落，避免多 sheet 内容粘连成一段。
- `DocumentProcessor._table_summary_chunks()` 新增结构化表格摘要分块逻辑：当段落满足“首行为 section title、第二行为表头、后续行为制表符分隔数据行”时，会输出：
  - 1 个 sheet/section 标题 chunk
  - N 个 `Header=Value | Header=Value` 行摘要 chunk
- `DocumentParseIndexingService` 因复用 `DocumentProcessor`，现已能把 `.xlsx` 解析结果直接落为更适合检索与引用的结构化 `document_chunks`，而不再只保存整段原始表格文本。
- `tests/unit/python/test_minio_object_loader.py` 已补多 sheet 稳定顺序用例；`tests/unit/python/test_document_processor.py` 已补结构化行摘要分块用例；`tests/unit/python/test_document_parse_indexing.py` 已更新为断言 Excel 入库结果为 `BudgetSummary + 行摘要 chunks`。

验证证据：

- 回归命令：`python -m pytest tests/unit/python/test_minio_object_loader.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_document_processor.py -q`
- 结果：`21 passed in 0.23s`
- 覆盖点：
  - `.docx` 段落与 Word 表格行抽取
  - `.xlsx` inline strings / shared strings / 稀疏列 / 多 sheet 稳定顺序
  - `DocumentProcessor` 将结构化 Excel 段落转为 `Header=Value` 行摘要 chunk
  - `DocumentParseIndexingService` 持久化 `document_chunks -> embeddings -> knowledge_entries_text` 主链

结论：

- `UC-06` 当前已经从“Office 文本基线可用”推进到“常见 Excel 台账可按行语义入库并参与 RAG 检索”的更实用状态。
- 这一步仍然属于轻量结构恢复，不等于生产级表格理解；后续更大的差距仍集中在跨表头合并单元格语义、复杂财务表版面、扫描件 OCR 与图片表格识别。

## 133. 2026-06-25 UC-06 CSV 行摘要分块增强

本轮目标：把 UC-06 的结构化表格分块能力从 “Excel/制表符表格” 扩展到常见 `.csv` 台账文本，减少客户直接上传导出报表时只落整段原文、检索命中不聚焦的问题。

已完成：

- `DocumentProcessor._table_summary_chunks()` 新增对逗号分隔结构化表格的识别，在保留原有 `\t` 优先逻辑的同时，支持 CSV 头行与数据行解析。
- 对于满足“第一行为标题、第二行为表头、后续行为数据行”的 CSV 段落，现在会产出：
  - 1 个标题 chunk
  - N 个 `Header=Value | Header=Value` 行摘要 chunk
- `DocumentParseIndexingService` 因复用 `DocumentProcessor`，所以 `.csv` 资料也能直接沿 `document_chunks -> embeddings -> knowledge_entries_text` 主链落为结构化知识片段。

验证证据：

- 新增测试：
  - `tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_structured_csv_sections`
  - `tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_csv_document_into_row_summary_chunks`
- 先 RED：

  ```bash
  python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_structured_csv_sections tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_csv_document_into_row_summary_chunks -q
  ```

  结果：`2 failed`，两处都因当前实现仅返回 `1 chunk` 而非结构化 `3 chunks`
- 再 GREEN 回归：

  ```bash
  python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_structured_csv_sections tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_csv_document_into_row_summary_chunks tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q
  ```

  结果：`23 passed in 0.21s`

结论：

- `UC-06` 当前已经不只覆盖 Word/Excel Office 基线，也开始稳定承接客户常见 CSV 台账文本的结构化入库。
- 这一步依然属于轻量结构恢复，尚未触达复杂 CSV quoting、跨行单元格、扫描表格 OCR、图片表格识别和高保真版面重建。

## 134. 2026-06-25 UC-06 quoted CSV 结构化兼容增强

本轮目标：把 UC-06 的 CSV 结构化分块从“简单逗号分隔可用”进一步推进到“quoted CSV 也能稳定工作”，覆盖客户从 ERP/财务系统导出的常见字段里带逗号说明文本的场景。

已完成：

- `DocumentProcessor` 在识别逗号分隔结构化段落时，不再直接 `split(",")`，而是切换为 Python 标准库 `csv.reader`。
- 这使得 `Comment="East, focus region"` 这类带引号且单元格内部含逗号的值，不会再被错误拆成多列。
- tab 分隔表格逻辑保持原样，quoted CSV 解析只在 `delimiter=","` 的路径上启用，尽量收敛改动面。

验证证据：

- 新增测试：
  - `tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_quoted_csv_cells`
  - `tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_quoted_csv_document_into_row_summary_chunks`
- 先 RED：

  ```bash
  python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_quoted_csv_cells tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_quoted_csv_document_into_row_summary_chunks -q
  ```

  结果：`2 failed`，两处都因当前实现只返回 `1 chunk`
- 再 GREEN 回归：

  ```bash
  python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_quoted_csv_cells tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_quoted_csv_document_into_row_summary_chunks tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q
  ```

  结果：`25 passed in 0.19s`

结论：

- `UC-06` 当前的轻量表格结构恢复已经从 Excel、多 sheet、普通 CSV 继续扩展到 quoted CSV，足以覆盖更多真实导出资料。

## 135. 2026-06-25 UC-06 分号 CSV 结构化兼容增强

本轮目标：把 UC-06 的 CSV 结构化分块从“逗号 CSV 可用”继续推进到“分号分隔 CSV 也能稳定工作”，覆盖部分 ERP、财务和欧洲区域导出文件默认使用 `;` 作为分隔符的常见场景。

- RED：先新增两条定向测试，验证 `DocumentProcessor` 与 `DocumentParseIndexingService` 面对 `Department;Amount;Owner;Comment` 这类表头、以及带引号的 `Comment="East; focus region"` 时，不能再退化为单整段 chunk。  
  验证命令：`python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_semicolon_csv_sections tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_semicolon_csv_document_into_row_summary_chunks -q`，首次结果 `2 failed`，失败点都是 `chunks == 1`。
- GREEN：`DocumentProcessor._structured_table_delimiter()` 新增 `;` 识别，`_split_structured_row()` 在 `,` 和 `;` 两种 CSV 分隔场景下统一走 Python 标准库 `csv.reader`，保持 quoted cell 解析一致，避免手写 split 在带分隔符文本中把单元格拆坏。
- 验证：
  - `python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_semicolon_csv_sections tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_semicolon_csv_document_into_row_summary_chunks -q` -> `2 passed in 0.09s`
- `python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q` -> `27 passed in 0.18s`
- 当前 UC-06 的轻量结构化表格恢复已覆盖：Excel 多 sheet / shared strings / sparse cells、普通 CSV、quoted CSV，以及分号分隔 CSV。下一段更值得继续补的仍是更高阶的 OCR、复杂表格结构恢复和扫描件版面理解。

## 136. 2026-06-25 UC-06 CSV 方言探测稳健性增强

本轮目标：修复分号 CSV 在引号备注文本中包含逗号时被误判成逗号 CSV 的问题，避免真实 ERP/财务导出文件因为备注列里的自然语言标点而退化成单整段 chunk。

- RED：先新增两条定向测试，覆盖 `Department;Amount;Owner;Comment` 这类分号表头，同时让 `Comment` 字段为 `"East, focus region"`。  
  验证命令：`python -m pytest tests/unit/python/test_document_processor.py::test_process_document_detects_semicolon_csv_even_when_quoted_cells_contain_commas tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_detects_semicolon_csv_when_quoted_cells_contain_commas -q`，首次结果 `2 failed`，失败点都是 `chunks == 1`，证明当前“先看有没有逗号”的探测策略不稳。
- GREEN：`DocumentProcessor._structured_table_delimiter()` 不再按字符出现顺序粗判，而是分别用 `\t` / `,` / `;` 试解析结构化行，选择“表头列数有效且各行列数稳定”的最佳候选；`_split_structured_row()` 继续统一走 Python 标准库 `csv.reader` 处理 CSV 类分隔，保持 quoted cell 兼容。
- 验证：
  - `python -m pytest tests/unit/python/test_document_processor.py::test_process_document_detects_semicolon_csv_even_when_quoted_cells_contain_commas tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_detects_semicolon_csv_when_quoted_cells_contain_commas -q` -> `2 passed in 0.09s`
- `python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q` -> `29 passed in 0.19s`
- 当前 UC-06 的 CSV 轻量结构化能力已经不只是“支持几种分隔符”，而是具备了最小可用的方言稳健性，能承接更多真实导出资料。更大的生产级差距仍然集中在 OCR、复杂表格结构恢复和扫描件版面理解。

## 137. 2026-06-25 UC-06 UTF-8 BOM CSV 表头清理增强

本轮目标：修复 UTF-8 BOM 污染 CSV 首列表头的问题，避免 Excel/ERP 导出的 `.csv` 文件把第一列名写成 `\ufeffDepartment` 之类的脏值，进一步影响 chunk 内容、检索可读性和引用展示。

- RED：先新增两条定向测试，覆盖 `\ufeffDepartment,Amount,Owner` 这类带 BOM 的 CSV 表头。  
  验证命令：`python -m pytest tests/unit/python/test_document_processor.py::test_process_document_strips_utf8_bom_from_csv_header_cells tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_strips_utf8_bom_from_csv_header_cells -q`，首次结果 `2 failed`，失败差异明确为 `\ufeffDepartment=Sales ...`。
- GREEN：`DocumentProcessor` 在生成表头时新增 `_normalize_header_cell()`，统一对 header cell 执行 `lstrip("\ufeff").strip()`，把 BOM 清理限制在表头归一化层，不影响正文值字段。
- 验证：
  - `python -m pytest tests/unit/python/test_document_processor.py::test_process_document_strips_utf8_bom_from_csv_header_cells tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_strips_utf8_bom_from_csv_header_cells -q` -> `2 passed in 0.09s`
- `python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q` -> `31 passed in 0.21s`
- 当前 UC-06 的 CSV 兼容性已经继续向真实客户导出文件靠近：不仅能识别多种分隔符和 quoted cells，也开始主动清理 BOM 这类常见编码遗留。剩余更大的生产级差距仍集中在 OCR、复杂表格结构恢复和扫描件版面理解。

## 138. 2026-06-25 UC-06 前置说明行后置表头识别增强

本轮目标：修复“标题后先出现一行导出说明/生成时间，真正表头在下一行”时无法进入结构化摘要链路的问题，覆盖客户常见的 Excel/ERP 导出文本在表头前附带说明行的场景。

- RED：先新增两条定向测试，覆盖 `BudgetSummary -> Generated at: ... -> Department,Amount,Owner -> 数据行` 的最小样本。  
  验证命令：`python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_when_note_line_precedes_csv_header tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_csv_when_note_line_precedes_header -q`，首次结果 `2 failed`，失败点都是 `chunks == 1`，证明当前实现把说明行误当表头。
- GREEN：`DocumentProcessor._table_summary_chunks()` 不再把 `lines[1]` 写死为表头，而是通过 `_locate_header_row()` 在标题后的前几行里寻找首个“列数有效、且下一行列数匹配”的候选表头；当前实现刻意保持收敛，只覆盖轻量前置说明行，不直接扩展到复杂多段说明或自由版面。
- 验证：
  - `python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_when_note_line_precedes_csv_header tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_csv_when_note_line_precedes_header -q` -> `2 passed in 0.10s`
- `python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q` -> `33 passed in 0.20s`
- 当前 UC-06 的轻量结构恢复能力已经从“纯干净表格”推进到“可容忍一小段前置说明再识别表头”的更真实导出形态。更大的生产级差距仍集中在 OCR、复杂表格结构恢复和扫描件版面理解。

## 139. 2026-06-25 UC-06 最小双层表头扁平化增强

本轮目标：补齐最常见的双层表头场景，让 `Department,Amount,Amount,Owner` + `,Planned,Actual,` 这类导出不再把第二层表头误当数据行，而是扁平化成 `Amount Planned`、`Amount Actual` 进入结构化知识 chunk。

- RED：先新增两条定向测试，覆盖 `BudgetSummary` 下的双层 CSV 表头和两行数据。  
  验证命令：`python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_two_level_csv_headers tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_csv_with_two_level_headers -q`，首次结果 `2 failed`，失败点都是 `chunks == 4`，说明当前实现把第二层表头当成数据行写入了知识分块。
- GREEN：`DocumentProcessor._locate_header_row()` 新增对最小双层表头的识别与合并。当首层表头下一行列数一致、且存在部分非空子表头时，通过 `_merge_two_level_header_rows()` 将列名扁平化为 `父表头 + 子表头`，并把真实数据起始行后移一行。实现刻意保持收敛，只覆盖最小双层表头，不扩成复杂多级表头推断。
- 验证：
  - `python -m pytest tests/unit/python/test_document_processor.py::test_process_document_builds_row_summaries_for_two_level_csv_headers tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_processes_csv_with_two_level_headers -q` -> `2 passed in 0.10s`
- `python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q` -> `35 passed in 0.23s`
- 当前 UC-06 的轻量结构恢复已经继续从“单层表头”推进到“最小双层表头可扁平化”，更贴近财务预算、经营分析和 ERP 导出报表的真实形态。更大的生产级差距仍集中在 OCR、复杂多级表头、复杂表格结构恢复和扫描件版面理解。

## 140. 2026-06-25 UC-06 重复表头去歧义增强

本轮目标：修复 `Department,Amount,Amount,Amount` 这类重复列表头直接原样进入行摘要的问题，给重复列名补上稳定后缀，避免知识 chunk 中出现多个同名 key，影响检索可读性与引用展示。

- RED：先新增两条定向测试，覆盖重复 `Amount` 列名的最小 CSV 样本。  
  验证命令：`python -m pytest tests/unit/python/test_document_processor.py::test_process_document_disambiguates_duplicate_csv_headers tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_disambiguates_duplicate_csv_headers -q`，首次结果 `2 failed`，失败差异明确为 `Amount=100000 | Amount=120000 | Amount=125000`，说明当前实现未做任何去歧义。
- GREEN：`DocumentProcessor` 新增 `_deduplicate_header_cells()`，在 header 归一化层对重复列名补稳定序号后缀；唯一列名保持原样，重复列名则转成 `Amount #1/#2/#3`。该能力同时作用于普通单层表头和双层表头扁平化后的结果。
- 验证：
  - `python -m pytest tests/unit/python/test_document_processor.py::test_process_document_disambiguates_duplicate_csv_headers tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_disambiguates_duplicate_csv_headers -q` -> `2 passed in 0.10s`
- `python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q` -> `37 passed in 0.25s`
- 当前 UC-06 的结构化列名稳定性已经从“能抽出列名”继续推进到“重复列名也能稳定落盘”，更贴近真实 ERP/财务导出报表。更大的生产级差距仍集中在 OCR、空列表头补位、复杂多级表头、复杂表格结构恢复和扫描件版面理解。

## 141. 2026-06-25 UC-06 空列表头稳定补位增强

本轮目标：修复 `Department,,Owner` 这类存在空列表头的导出在结构化摘要阶段直接退化成整段文本的问题，为空列名补稳定占位名，保证列位信息仍能进入知识 chunk。

- RED：先新增两条定向测试，覆盖中间一列缺少列名、但数据行完整的最小 CSV 样本。  
  验证命令：`python -m pytest tests/unit/python/test_document_processor.py::test_process_document_fills_blank_csv_headers_with_stable_column_names tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_fills_blank_csv_headers_with_stable_column_names -q`，首次结果 `2 failed`，失败点都是 `chunks == 1`，说明当前实现把空列表头场景整体判为非结构化文本。
- GREEN：`DocumentProcessor` 新增 `_normalize_header_cells()`，对空列表头按列位补稳定占位名，如 `Column 2`；同时放宽结构化分隔符候选和表头定位中的“空 header 直接拒绝”规则，让补位后的 header 继续经过重复列名去歧义链路。
- 验证：
  - `python -m pytest tests/unit/python/test_document_processor.py::test_process_document_fills_blank_csv_headers_with_stable_column_names tests/unit/python/test_document_parse_indexing.py::test_parse_indexing_service_fills_blank_csv_headers_with_stable_column_names -q` -> `2 passed in 0.10s`
- `python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py tests/unit/python/test_minio_object_loader.py -q` -> `39 passed in 0.23s`
- 当前 UC-06 的结构化列名恢复已经从“重复列名能去歧义”继续推进到“空列名也能稳定补位”，更贴近真实 Excel/ERP 导出表。更大的生产级差距仍集中在 OCR、复杂多级表头、复杂表格结构恢复和扫描件版面理解。

## 142. 2026-06-25 UC-04 Word 导出页脚页码字段增强

本轮目标：把 Word 导出页脚从静态 `Page 1 of 3` 占位文本推进到真实 `PAGE/NUMPAGES` field，让 Office 在打开文档后可按实际分页刷新页码，更贴近客户可交付文档体验。

- RED：先收紧 `ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument`，不再接受静态页码文本，而是要求 `word/footer1.xml` 包含 `PAGE` 与 `NUMPAGES`。  
  验证命令：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test`，首次结果 `BUILD FAILURE`，失败差异明确为当前页脚仍是：
  `Page 1 of 3`
- GREEN：`ReportApplicationService.footerXml(...)` 从静态页码文本切换为 Word `w:fldSimple w:instr=" PAGE "` 与 `w:fldSimple w:instr=" NUMPAGES "` 字段组合，页脚文本改为 `Page [PAGE] of [NUMPAGES]`，保持现有品牌页脚文案不变。
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test` -> `BUILD SUCCESS`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument+ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument+ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` -> `BUILD SUCCESS`
- 当前 UC-04 已从“页脚里写死一个演示页码”推进到“Word 可交由 Office 按真实分页刷新页码字段”。更大的生产级差距仍集中在高级版式、目录结果刷新、PPT 母版/专用页眉页脚和统一品牌模板治理。
- 剩余更大的差距仍在：
  - 多行 quoted cells
  - 更复杂的 CSV escaping / dialect 自动识别
  - OCR、扫描件、图片表格与高保真版面重建

## 143. 2026-06-25 UC-04 PPT 导出页脚页码增强

本轮目标：把 PPT 导出页脚从只有品牌文案推进到“品牌页脚 + Page X of N”，让下载的演示文稿至少具备基础页码语义，而不是目录页和章节页都缺少页序信息。

- RED：先收紧 `ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument`，要求 `slide1.xml/slide2.xml/slide3.xml` 分别包含 `Page 1 of 3`、`Page 2 of 3`、`Page 3 of 3`。  
  验证命令：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test`，首次结果 `BUILD FAILURE`，失败差异明确为当前 PPT 页脚只有 `Generated by Intelligent Report System`，没有页码。
- GREEN：`ReportApplicationService.pptSlides()` 现在按 `sections.size() + 1` 计算总幻灯片数，并把 `currentSlide/totalSlides` 透传给 `slideXml(...)`；页脚文本升级为 `brand.footer() + "\n" + pageFooter(currentSlide, totalSlides)`，让目录页与各章节页都输出 `Page X of N`。
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` -> `BUILD SUCCESS`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` -> `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- 当前 UC-04 已从“PPT 只有品牌页脚文本”推进到“PPT 具备最小页码语义”，并确认没有破坏 Word/PDF/PPT 三条导出链。更大的生产级差距仍集中在 PPT 母版/占位符体系、统一品牌模板治理，以及 Word/PDF/PPT 更完整的企业版式系统。

## 144. 2026-06-26 UC-04 PDF 目录页码对齐增强

本轮目标：把 PDF 导出从“目录页码只是章节序号推算值”推进到“目录页码与真实 PDF 分页结果一致”，避免客户打开文件时看到 `Section 12 .... 13` 这类明显不可信的目录。

- RED：先收紧 `ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong`，要求长报告 PDF 的目录项不再接受 `12. Section 12 .... 13`，而是必须对齐真实第 3 页。  
  验证命令：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong" test`，首次结果 `BUILD FAILURE`，失败差异明确显示当前 PDF 目录仍输出：
  `12. Section 12 .... 13`
- GREEN：`ReportApplicationService.renderPdf()` 现在先为 TOC 写占位行，再记录每个正文章节在 `bodyLines` 中的真实起始行号，并按 `paginatePdfBodyLines(..., 24)` 的同一分页规则反算页码后回填目录项。这样目录页码开始服从当前 PDF 真实版式，而不再复用固定 `sectionNo + 1` 规则。
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong" test` -> `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` -> `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
- 当前 UC-04 已从“PDF 目录页码是演示值”推进到“PDF 目录页码至少与当前最小分页器的真实结果一致”。更大的生产级差距仍集中在高级企业版式、真正的模板引擎、PPT 母版/占位符体系，以及 Word/PDF/PPT 统一品牌模板治理。

## 145. 2026-06-26 UC-04 PPT 封面页增强

本轮目标：把 PPT 导出从“第一页直接是目录页”推进到“封面页 + 目录页 + 章节页”的更正式汇报结构，缩小当前最小 PPTX 与客户预期企业汇报稿之间的落差。

- RED：先收紧 `ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument` 和 `#exportsPowerPointAsOverviewAndSectionSlides`，要求：
  - `slide1.xml` 必须承载 `Cover`
  - 目录下沉到 `slide2.xml`
  - 整个包新增 `slide4.xml`，使 2 个章节场景变成 `封面 + 目录 + 2 个章节`
  
  验证命令：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides" test`，首次结果 `BUILD FAILURE`，失败差异明确为当前 `slide1.xml` 仍直接输出 `Table of Contents`，且 `[Content_Types].xml` 里还没有 `/ppt/slides/slide4.xml`。
- GREEN：`ReportApplicationService.pptSlides()` 现在按 `sections.size() + 2` 生成最小 deck：
  - `slide1`：封面页
  - `slide2`：目录页
  - `slide3..N`：章节页
  
  同时新增 `coverSlideBody(brand)` 承载 `Cover / companyName / header`，目录页改为 `tocSlideBody(sections, 3)`，让目录中的章节页码对齐新的真实 slide 编号。
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides" test` -> `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong" test` -> `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- 当前 UC-04 已从“PPT 只有目录页和章节页”推进到“PPT 具备最小封面/目录/章节三级结构”。更大的生产级差距仍集中在真正的 Slide Master、占位符体系、统一企业模板治理，以及图表/表格/封底等更完整的企业汇报组件。

## 146. 2026-06-26 UC-04 PPT 最小 Slide Master / Theme 包结构增强

本轮目标：把 PPTX 导出从“只有 slides + presentation 的最小 ZIP”推进到“具备最小 Slide Master / Slide Layout / Theme Office 包结构”，缩小当前导出件与正式企业 PowerPoint 文档之间的结构落差。

- RED：先收紧 `ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument` 和 `#exportsPowerPointAsOverviewAndSectionSlides`，要求：
  - `[Content_Types].xml` 必须声明 `/ppt/slideMasters/slideMaster1.xml`、`/ppt/slideLayouts/slideLayout1.xml`、`/ppt/theme/theme1.xml`
  - `presentation.xml` 必须包含 `p:sldMasterIdLst`
  - `presentation.xml.rels` 必须包含 `slideMaster` 关系
  - `slide1.xml.rels ... slideN.xml.rels` 必须同时包含 `slideLayout` 与 `image`
  
  验证命令：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides" test`，首次结果 `BUILD FAILURE`，失败差异明确为当前 `[Content_Types].xml` 里还没有 `/ppt/slideMasters/slideMaster1.xml`。
- GREEN：`ReportApplicationService.renderPptx()` 现在新增：
  - `ppt/slideMasters/slideMaster1.xml`
  - `ppt/slideMasters/_rels/slideMaster1.xml.rels`
  - `ppt/slideLayouts/slideLayout1.xml`
  - `ppt/slideLayouts/_rels/slideLayout1.xml.rels`
  - `ppt/theme/theme1.xml`
  
  同时：
  - `presentation.xml` 新增 `p:sldMasterIdLst`
  - `presentation.xml.rels` 显式声明 `rIdMaster1 -> slideMasters/slideMaster1.xml`
  - 每个 `slideN.xml.rels` 新增 `slideLayout` 关系，保留原有 `rIdLogo` 图片关系
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core clean test "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides"` -> `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong" test` -> `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- 当前 UC-04 已从“PPT 完全没有母版/主题包结构”推进到“PPT 具备最小 Office 级 master/layout/theme 包结构”。更大的生产级差距仍集中在真正可驱动企业模板的 Slide Master 占位符治理、图表/表格/封底组件、以及 Word/PDF/PPT 统一模板源。

## 147. 2026-06-26 UC-04 PPT 占位符语义增强

本轮目标：把 PPTX 导出从“虽然有 master/layout/theme 包结构，但仍主要是普通 shape 文本框”推进到“master/layout 已具备 title/body/footer 占位符语义骨架”，继续缩小与企业正式模板的结构差距。

- RED：先收紧 `ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument` 和 `#exportsPowerPointAsOverviewAndSectionSlides`，要求：
  - `ppt/slideMasters/slideMaster1.xml` 必须包含 `p:ph type="title"`、`p:ph type="body"`、`p:ph type="ftr"`
  - `ppt/slideLayouts/slideLayout1.xml` 必须包含 `p:ph type="title"`、`p:ph type="body"`、`p:ph type="ftr"`

  验证命令：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides" test`，首次结果 `BUILD FAILURE`，失败差异明确为当前 `slideMaster1.xml` 尚不包含 `type="title"`。
- GREEN：`ReportApplicationService.pptSlideMasterXml()` 与 `pptSlideLayoutXml()` 现已补最小占位符骨架：
  - `slideMaster1.xml` 新增 `Master Title Placeholder`、`Master Body Placeholder`、`Master Footer Placeholder`
  - `slideLayout1.xml` 新增与当前标题区、正文区、页脚区对应的 `Title/Body/Footer Placeholder`
  - 占位符统一使用 `p:ph type="title|body|ftr"`，并带上与当前页面结构一致的基础坐标
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core clean test "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides"` -> `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong" test` -> `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- 当前 UC-04 已从“PPT 只有最小 master/layout/theme 文件”推进到“PPT 已具备 title/body/footer 占位符语义骨架”。更大的生产级差距仍集中在真正让 slide 内容引用 placeholder、统一企业模板源、图表/表格/封底组件，以及更正式的企业版式规则。

## 148. 2026-06-26 UC-04 PPT 内容引用占位符增强

本轮目标：把 PPTX 导出从“master/layout 里已经有占位符，但 slide 内容仍主要是自由文本框”推进到“实际 slide 内容也开始引用 title/body/footer placeholder”，继续向真正可替换母版的企业模板结构收敛。

- RED：先收紧 `ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument` 和 `#exportsPowerPointAsOverviewAndSectionSlides`，要求：
  - `ppt/slides/slide1.xml`、`slide2.xml`、`slide3.xml` 至少包含 `type="title"`、`type="body"`、`type="ftr"`
  - 封面页、目录页、章节页都必须带上对应 placeholder 引用，而不是只有 `slideMaster1.xml/slideLayout1.xml` 才有

  验证命令：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides" test`，首次结果 `BUILD FAILURE`，失败差异明确为当前 `slide1.xml` 尚不包含 `type="title"`。
- GREEN：`ReportApplicationService.slideXml(...)` 现已为实际内容 shape 增补 placeholder 引用：
  - `Title` shape 新增 `p:ph type="title"`
  - `Body` shape 新增 `p:ph type="body" idx="1"`
  - `Footer` shape 新增 `p:ph type="ftr" sz="quarter" idx="10"`
  - 现有封面页、目录页、章节页都统一走这套结构
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core clean test "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument,ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides"` -> `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument,ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument,ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong" test` -> `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- 当前 UC-04 已从“PPT 只有 placeholder 骨架”推进到“PPT 的标题/正文/页脚内容也开始挂接到同一套 placeholder 语义”。更大的生产级差距仍集中在统一企业模板源、专用图表/表格/封底页面，以及更完整的企业版式规则和真正的母版驱动布局。

## 149. 2026-06-26 UC-04 真实后端 PPT 结构验收增强

本轮目标：把 `REQ-REPORT-004 / UC-04` 的真实后端导出验收从“浏览器点击后下载成功”推进到“下载到的 `.pptx` 包内结构、封面/目录/章节内容和 placeholder 语义都与当前源码一致”，避免 `18082` 仍跑旧 smoke 镜像时产生假阴性。

- 环境修正：
  - 先确认旧 `ir-java-smoke` 仍在运行历史镜像 `sha256:ca09be16c971bf68e5b7687b6f9641958a8a325e6644b5525cb32e1c6a894f53`，与当前源码中已经补齐的 `slide4.xml` 和 placeholder 引用增强不一致。
  - 使用当前仓库源码执行 `docker build -f Dockerfile.java -t intelligent-report-system-java-report-core .`，得到新镜像 `sha256:34a6d800724c29ee7121ec4bbc19f5b22918191f0e2f747aafef2b1b7aa3a44b`。
  - 删除并重建 `ir-java-smoke`，保留：
    - `18082:8080`
    - `intelligent-report-system_report-net`
    - `intelligent-report-infra_default`
    - `SPRING_PROFILES_ACTIVE=dev`
    - `DB_JDBC_URL=jdbc:postgresql://postgres:5432/intelligent_report`
    - `ROCKETMQ_ENDPOINT=rocketmq-namesrv:9876`
    - `MINIO_ENDPOINT=http://host.docker.internal:9000`
    - `MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000`
- 真实后端健康验证：
  - `Invoke-WebRequest -UseBasicParsing http://127.0.0.1:18082/actuator/health` 返回 `{"status":"UP"}`。
  - `docker inspect -f "{{.State.Health.Status}}" ir-java-smoke` 返回 `healthy`。
  - `docker logs --tail 80 ir-java-smoke` 显示：
    - `ReportCoreApplication v0.1.0-SNAPSHOT`
    - `The following 1 profile is active: "dev"`
    - `Schema "public" is up to date. No migration necessary.`
- 真实后端导出结构验收：
  - `frontend/web-console/tests/e2e/report-export-real-backend.spec.ts` 已增强为：
    - 下载后将 `.pptx` 落为 `.zip` 再 `Expand-Archive`
    - 校验 `ppt/slides/slide1.xml` 至 `slide4.xml`
    - 校验 `ppt/slideMasters/slideMaster1.xml`
    - 校验 `ppt/slideLayouts/slideLayout1.xml`
    - 校验 `ppt/theme/theme1.xml`
    - 校验封面 `Cover`、目录 `Table of Contents` 与章节正文内容
    - 校验 `type="title|body|ftr"` placeholder 语义
  - 执行：`$env:RUN_REAL_BACKEND_E2E='true'; $env:REAL_BACKEND_API_BASE_URL='http://127.0.0.1:18082/api/v1'; $env:REAL_BACKEND_ORIGIN='http://127.0.0.1:18082'; npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts`
  - 结果：`2 passed (PDF + PPT)`
- 当前 UC-04 已从“真实后端仅证明能下载 PPT”推进到“真实后端已证明输出的是包含封面、目录、章节页、master/layout/theme 和 slide placeholder 语义的正式 PPT 包结构”。更大的生产级差距继续集中在更复杂的企业模板治理、专用图表/表格/封底组件，以及真正由母版驱动的高级版式系统。

## 150. 2026-06-26 UC-04 统一导出版式模板源最小闭环

本轮目标：把 `UC-04` 从“Word/PDF/PPT 各自拼品牌样式”推进到“至少存在一层共享版式模板入口”，让封面标题、目录标题、章节标题前缀和关键字号规则可以通过同一份 `brand.layout` 配置影响三端导出，继续向客户级企业模板治理收敛。

- RED：
  - 在 `ReportApplicationServiceTest` 新增 `appliesSharedEnterpriseLayoutAcrossWordPdfAndPowerPointExports`。
  - 该测试显式传入：
    - `coverTitle=Board Strategy Pack`
    - `tocTitle=Report Outline`
    - `bodyTitlePrefix=Section`
    - `titleFontSize=30`
    - `bodyFontSize=22`
    - `headerFontSize=16`
    - `footerFontSize=12`
  - 首次执行 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#appliesSharedEnterpriseLayoutAcrossWordPdfAndPowerPointExports" test` 失败，差异明确为 `word/styles.xml` 尚不包含 `w:styleId="Title"`，证明此前确实还没有共享版式模板层。
- GREEN：
  - `ReportApplicationService.exportTheme(...)` 新增共享 `ExportLayout`：
    - `coverTitle`
    - `tocTitle`
    - `bodyTitlePrefix`
    - `titleFontSize`
    - `bodyFontSize`
    - `headerFontSize`
    - `footerFontSize`
  - DOCX：
    - `styles.xml` 新增 `Title/Header/Footer` style
    - `Title/Heading1/TOCHeading/Header/Footer` 开始统一承接布局字号
    - `document.xml` 开始读取共享 `coverTitle/tocTitle/bodyTitlePrefix`
  - PDF：
    - 页眉标题与正文目录/章节标题开始读取共享 `coverTitle/tocTitle/bodyTitlePrefix`
  - PPT：
    - 封面 title、目录 title、章节 title 以及 `title/body/header/footer` 字号开始读取共享布局配置
  - 兼容性约束：
    - 未传 `brand.layout` 时，默认保持原先导出行为
    - 仅在显式传入 `layout` 时启用共享版式模板，避免破坏已有真实导出链
- 验证：
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#appliesSharedEnterpriseLayoutAcrossWordPdfAndPowerPointExports" test` -> `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#normalizesBrandThemeConsistentlyAcrossWordPdfAndPowerPointExports+exportsCurrentReportVersionAsEnterpriseWordDocument+exportsCurrentReportVersionAsPdfDocument+exportsCurrentReportVersionAsPowerPointDocument+exportsPowerPointAsOverviewAndSectionSlides+appliesSharedEnterpriseLayoutAcrossWordPdfAndPowerPointExports" test` -> `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
- 当前 UC-04 已从“仅共享品牌归一化字段”推进到“开始共享最小版式模板参数”。更大的生产级差距仍集中在：
  - 更完整的模板配置中心与版本治理
  - 图表/表格/封底等更丰富的企业汇报组件
  - 真正由母版驱动的 PPT 高级版式系统
  - PDF 更正式的分页与版面引擎

## 151. 2026-06-26 UC-04 共享版式模板前端接入闭环

本轮目标：把 `UC-04` 从“后端已支持 `brand.layout`，但页面上无法填写”推进到“报告详情页可真实配置共享版式模板并带入导出请求”，避免企业模板治理停留在 API/服务层而无法被业务用户实际使用。

- RED：
  - 在 `frontend/web-console/tests/e2e/report-generation.spec.ts` 的 `REQ-REPORT-004` 场景中，新增：
    - `封面标题`
    - `目录标题`
    - `章节标题前缀`
    - `标题字号`
    - `正文字号`
    - `页眉字号`
    - `页脚字号`
  - 首次执行 `npx playwright test tests/e2e/report-generation.spec.ts`，失败点明确为 `locator.fill: waiting for getByLabel('封面标题')` 超时，证明页面在本轮前确实还没有共享版式模板表单入口。
- GREEN：
  - `frontend/web-console/src/pages/reports/ReportDetail.vue`
    - 导出表单新增上述 7 个 `brand.layout` 字段
    - 默认表单值补齐最小 layout 初值
    - `createEnterpriseExport()` 继续复用既有导出逻辑，无需额外转换即可把 `brand.layout` 一并透传给后端
  - `frontend/web-console/tests/e2e/report-generation.spec.ts`
    - `REQ-REPORT-004` 现会真实填写 layout 字段，并断言导出请求中的 `brand.layout`
  - `frontend/web-console/src/api/apiContracts.test.ts`
    - Word 导出契约已补 `brand.layout` 透传断言
- 验证：
  - `npx playwright test tests/e2e/report-generation.spec.ts --grep "REQ-REPORT-004"` -> `1 passed`
  - `npx vitest run src/api/apiContracts.test.ts` -> `21 passed`
  - `npx playwright test tests/e2e/report-generation.spec.ts` -> `7 passed`
- 当前 UC-04 已从“共享版式模板只存在于后端对象模型”推进到“页面可实际配置并稳定透传”。接下来更值得继续推进的是：
  - 把 layout 从自由输入收敛成更正式的模板中心/模板选择器
  - 扩大共享模板对图表、表格、封底、备注页等组件的覆盖面
  - 继续把共享模板能力接入真实后端导出下载烟测

## 152. 2026-06-26 UC-04 共享版式模板真实后端导出烟测闭环

本轮目标：把 `UC-04` 从“后端单测通过、前端 mock E2E 透传正确”继续推进到“真实 Java 后端导出链路也能产出对应 PPT 结构”的状态，避免客户现场仍命中旧镜像或旧导出实现。

- RED：
  - 在 `frontend/web-console/tests/e2e/report-export-real-backend.spec.ts` 的真实后端 PPT 用例中，新增页面填写：
    - `封面标题 = Board Strategy Pack`
    - `目录标题 = Report Outline`
    - `章节标题前缀 = Section`
    - `标题字号 = 30`
    - `正文字号 = 22`
    - `页眉字号 = 16`
    - `页脚字号 = 12`
  - 同时把真实导出断言从旧的 `Cover / Table of Contents` 升级为共享版式模板生效断言：
    - `slide1.xml` 包含 `Board Strategy Pack`
    - `slide2.xml` 包含 `Report Outline`
    - `slide3.xml` 包含 `Section 1. Executive Summary`
    - `slide1.xml` 包含 `sz="3000"`
    - `slide2.xml` 包含 `sz="2200"`
  - 首次执行真实后端 E2E：

    ```powershell
    $env:RUN_REAL_BACKEND_E2E='true'
    $env:REAL_BACKEND_API_BASE_URL='http://127.0.0.1:18082/api/v1'
    $env:REAL_BACKEND_ORIGIN='http://127.0.0.1:18082'
    npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts
    ```

    结果 `1 failed, 1 passed`，失败点为解压后的 `slide1.xml` 仍出现旧标题 `真实后端 PPT 导出验收 ...` 与旧封面正文 `Cover`，证明真实环境仍在跑历史镜像。
- GREEN：
  - 使用当前源码重建 Java 镜像：

    ```powershell
    docker build -f Dockerfile.java -t intelligent-report-system-java-report-core .
    ```

    产出新镜像 `sha256:7c01928dcb47f536fffada017bb7a51659192f0c0571a1fb0822e0aa8ee59e75`
  - 按原本本地烟测拓扑替换 `ir-java-smoke`：
    - 保持端口 `18082:8080`
    - 保持 `intelligent-report-infra_default`
    - 保持 `intelligent-report-system_report-net`
    - 保持原有 `DB_JDBC_URL / MINIO_* / ROCKETMQ_ENDPOINT / SPRING_PROFILES_ACTIVE=dev`
  - 替换命令：

    ```powershell
    docker rm -f ir-java-smoke
    docker run -d --name ir-java-smoke --network intelligent-report-infra_default -p 18082:8080 `
      -e DB_JDBC_URL=jdbc:postgresql://postgres:5432/intelligent_report `
      -e MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000 `
      -e JWT_SECRET=local-dev-secret-change-me-32-bytes-minimum `
      -e MINIO_ENDPOINT=http://host.docker.internal:9000 `
      -e SPRING_PROFILES_ACTIVE=dev `
      -e SERVER_PORT=8080 `
      -e MINIO_ROOT_USER=minioadmin `
      -e MINIO_ROOT_PASSWORD=minioadmin123 `
      -e ROCKETMQ_ENDPOINT=rocketmq-namesrv:9876 `
      -e DB_PASSWORD=report123 `
      -e DB_USERNAME=report `
      intelligent-report-system-java-report-core
    docker network connect intelligent-report-system_report-net ir-java-smoke
    ```
  - 新容器日志显示 `ReportCoreApplication v0.1.0-SNAPSHOT`、`The following 1 profile is active: "dev"`、`Schema "public" is up to date. No migration necessary.`，`docker inspect ir-java-smoke --format '{{.State.Health.Status}}'` 返回 `healthy`
  - 重跑真实后端 E2E：

    ```powershell
    $env:RUN_REAL_BACKEND_E2E='true'
    $env:REAL_BACKEND_API_BASE_URL='http://127.0.0.1:18082/api/v1'
    $env:REAL_BACKEND_ORIGIN='http://127.0.0.1:18082'
    npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts
    ```

    结果：`2 passed (9.5s)`
- 结论：
  - 当前共享 `brand.layout` 已从“单测 + mock E2E”推进到“真实 Java 后端浏览器导出烟测”
  - 这次失败的根因不是实现缺失，而是本地 `ir-java-smoke` 环境漂移；已通过重建镜像和替换容器修复
  - 后续凡是新增真实后端导出能力，必须同步确认 `18082` 烟测容器是否已切到当前源码镜像，否则很容易再次出现“代码已支持、真实环境仍旧版本”的假失败

## 121. 2026-06-25 UC-04 DOCX 目录内部跳转增强

本轮目标：把 Word 企业导出从“只有目录文本”推进到“目录可跳转到正文章节”的最小导航结构，让客户下载的 DOCX 更接近正式可用文档，而不是仅有视觉上的目录占位。

已完成：

- `ReportApplicationService.documentXml()` 的目录项不再直接输出纯文本段落，而是改为生成 `w:hyperlink w:anchor="section-N"`。
- Word 正文章节标题段落新增 `w:bookmarkStart/w:bookmarkEnd`，与目录 anchor 一一对应，命名规则为 `section-1`、`section-2` 等。
- 这次增强保持在 DOCX 范围内最小实现，没有扩散修改 PDF/PPTX 导出布局，降低回归风险。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test` 首次失败，断言显示 `word/document.xml` 中缺少 `w:hyperlink w:anchor="section-1"`，证明此前目录仍只是纯文本。
- GREEN：同命令再次执行通过，当前测试已断言 `word/document.xml` 同时包含 `w:hyperlink w:anchor="section-1|2"` 与 `w:bookmarkStart w:id="1|2" w:name="section-1|2"`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest -pl backend/java-report-core test` 通过 31 个测试，确认目录跳转增强没有破坏现有 Word/PDF/PPT 导出与审计相关能力。

仍需继续：

- 当前只是最小内部跳转结构，尚未做到 Word 域代码驱动的自动目录刷新、真实分页驱动页码和更高级样式模板。
- PDF/PPTX 仍没有等价的导航层能力，后续可继续评估是否需要补目录书签、PPT 大纲页或统一企业模板治理。

## 122. 2026-06-25 UC-04 PDF 最小多页分页闭环

本轮目标：把 PDF 企业导出从“单页最小文本流”推进到“能按正文长度切成多页 PDF”的最小分页结构，让较长报告下载后不再全部堆在一页里。

已完成：

- `ReportApplicationService.renderPdf()` 新增按正文行数分页的最小实现：先把目录与正文展开成 `bodyLines`，再按固定行数切分为多个 page chunk。
- 每个分页 chunk 都会生成独立 `/Page` 与 `/Contents` 对象，`/Pages /Kids [...] /Count N` 会与真实页数对齐。
- 每页都保留企业 Logo、页眉、正文区和页脚，并输出 `%% PDF-PAGE N` 调试标记，页脚页码改为真实 `Page X of N`。
- 这轮仍保持轻量实现，没有引入复杂 PDF 排版引擎、自动换行测量或目录自动刷新，只先补“长报告能分页”这条客户最容易直接感知的缺口。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsPdfAcrossMultiplePagesWhenBodyIsLong" test` 首次失败，断言显示 PDF 仍只有 `/Kids [3 0 R] /Count 1`，证明此前实现确实还是单页。
- GREEN：同命令在实现后通过，当前测试已断言长报告 PDF 包含 `/Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3`、`%% PDF-PAGE 1/2/3` 和 `Page 1 of 3` 至 `Page 3 of 3`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test` 通过 32 个测试，确认 PDF 真分页增强没有破坏现有 Word/PDF/PPT 导出、Logo 和审计相关能力。
- 轻门禁复核：`node --test tests/unit/node/delivery_local_smoke_bundle.test.mjs` 通过，当前 P0-P3 统一交付 smoke 编排未受影响。

仍需继续：

- 当前分页仍是按“行数”而不是按真实字体宽度、段落高度或自动换行测量分页，复杂正文和图表版式仍不够正式。
- 目录页码仍是文本语义，不会按真实 PDF 分页结果自动重算目录内容。
- 若继续朝客户生产版推进，下一步更值得补的是统一企业模板治理、Word 目录自动刷新和 PPT 母版能力，而不是继续在最小 PDF 生成器里堆复杂排版技巧。

## 123. 2026-06-25 UC-04 DOCX TOC field 与 Heading 样式增强

本轮目标：把 Word 企业导出从“有目录文本 + 内部跳转”继续推进到“具备 Word 可识别的目录字段和标题样式”，让客户打开 DOCX 时更接近正式报告结构。

已完成：

- `ReportApplicationService.renderDocx()` 现在会额外写出 `word/styles.xml` 和 `word/settings.xml`，并在 `[Content_Types].xml`、`document.xml.rels` 中补齐对应部件声明与关系。
- `document.xml` 中新增 `w:fldSimple w:instr="TOC \o &quot;1-3&quot; \h \z \u"`，作为最小 Word 目录字段。
- 报告标题与章节标题改为使用 `Heading1`，目录标题使用 `TOCHeading`，为 Word 目录刷新和客户手工编辑留出标准样式基础。
- `settings.xml` 打开 `w:updateFields w:val="true"`，提示文档打开时刷新域字段。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test` 首次失败，断言显示 `word/document.xml` 中缺少 `TOC \o "1-3" \h \z \u`，证明此前 Word 仍只有文本目录而没有 TOC field。
- GREEN：同命令在实现后通过，当前测试已断言：
  - `word/document.xml` 包含 `w:fldSimple w:instr="TOC \o &quot;1-3&quot; \h \z \u"`
  - `word/document.xml` 包含 `w:pStyle w:val="Heading1"`
  - `word/styles.xml` 包含 `Heading1` 和 `TOCHeading`
  - `word/settings.xml` 包含 `w:updateFields w:val="true"`
- 回归：`.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test` 通过 32 个测试，确认 TOC field 与样式增强没有破坏 Word/PDF/PPT 当前导出能力。

仍需继续：

- 当前目录字段只是“可刷新”的最小基线，目录结果文本仍由当前生成逻辑先写入，不是完全依赖 Word 域在服务端计算。
- `Heading1` 目前只提供最小结构，没有补企业字体、字号、段前段后、颜色体系等正式主题样式。
- 若继续沿 UC-04 深挖，下一步更值得补的是统一品牌/版式模板治理，以及真正和分页结果联动的目录/页码刷新体验。

## 124. 2026-06-25 UC-04 DOCX 品牌样式参数落地

本轮目标：把 Word 导出里的 `Heading1/TOCHeading` 从“只有结构名的空样式”推进到“至少吃到企业字体和主色”，让品牌模板字段开始真正影响文档视觉。

已完成：

- `ReportApplicationService.stylesXml(brand)` 改为接收企业 `brand` 参数，不再输出固定空样式。
- `Heading1` 与 `TOCHeading` 现在会写入：
  - `w:rFonts w:ascii="{fontFamily}" w:hAnsi="{fontFamily}"`
  - `w:color w:val="{primaryColor}"`
  - `w:b` 粗体
- `primaryColor` 在 Word 样式侧统一规整为 6 位大写十六进制；非法值回退到当前系统品牌默认色 `1F4E79`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument" test` 首次失败，断言显示 `word/styles.xml` 中缺少 `w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"`，证明此前企业字体并没有真正落入 Word 样式。
- GREEN：同命令在实现后通过，当前测试已断言 `word/styles.xml` 同时包含 `w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"` 和 `w:color w:val="1F4E79"`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test` 通过 32 个测试，确认品牌样式参数落地没有破坏 Word/PDF/PPT 当前导出链路。

仍需继续：

- 当前只把 `fontFamily/primaryColor` 落在了 Word 关键标题样式上，还没扩到 Normal、正文、页眉页脚、表格、引用块、段前段后、字号体系等完整企业版式。
- 品牌样式还没有抽象成统一模板源，Word/PDF/PPT 之间仍可能继续漂移。
- 如果继续朝客户生产版推进，更值得优先补的是统一导出版式模板治理，而不是在单个样式节点上继续点状堆字段。

## 125. 2026-06-25 UC-04 PPT 品牌字体与主色参数落地

本轮目标：把 PPT 导出从“结构上有 Header/Footer/Body shape”推进到“关键文本样式开始吃企业品牌参数”，减少 Word 已有品牌感而 PPT 仍显得默认模板的落差。

已完成：

- `ReportApplicationService.slideXml(brand)` 新增对企业 `fontFamily` 与 `primaryColor` 的使用。
- `Title/Header/Footer` 的 `a:rPr` 现在会写入：
  - `a:latin typeface="{fontFamily}"`
  - `a:solidFill -> a:srgbClr val="{primaryColor}"`
- `Title/Body/Header/Footer` 的 `p:spPr` 补了基础白底 `a:solidFill`，让文本块在视觉上更接近企业汇报模板的最小底板。
- 这一轮仍保持轻量，不引入 Slide Master、Theme Part 或复杂占位符体系，只先补“品牌参数真的落到 PPT 文本样式”。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument" test` 首次失败，断言显示 `slide1.xml` 中缺少 `typeface="Aptos"`，证明此前 PPT 的字体样式仍然是硬编码默认值。
- GREEN：同命令在实现后通过，当前测试已断言 `ppt/slides/slide1.xml` 同时包含 `typeface="Aptos"` 与 `val="1F4E79"`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test` 通过 32 个测试，确认 PPT 品牌样式增强没有破坏当前 Word/PDF/PPT 导出链路。

仍需继续：

- 当前 PPT 仍是单页最小演示文稿，不是 Slide Master、主题色板、占位符布局或多页汇报 deck。
- 品牌样式只先落在 Title/Header/Footer，正文层级、目录页、图表占位、备注页和母版体系仍未统一治理。
- 如果继续朝客户生产版推进，下一步更值得补的是统一导出版式模板源，而不是继续在 `slide1.xml` 上分散堆样式片段。
- 若后续再次出现“代码已支持但真实烟测仍报不支持格式”，应优先核对容器镜像版本与当前仓库源码是否一致。

## 126. 2026-06-25 UC-04 导出品牌主题统一归一化

本轮目标：把 Word/PDF/PPT 三条导出路径中各自分散处理的品牌参数，收敛为一份共享主题对象，减少不同格式之间对 `primaryColor/fontFamily/header/footer/logoObjectKey` 的解释漂移。

已完成：

- `ReportApplicationService` 新增共享 `ExportTheme`，把 `companyName/logoObjectKey/header/footer/fontFamily/primaryColor` 的默认值、空值处理与颜色归一化集中在 `exportTheme(...)`。
- Word/PDF/PPT 三种导出以及 fallback `logo.svg` 现在统一消费同一份主题对象，不再一部分走原始 `brand map`、另一部分走单独修正。
- `primaryColor` 统一规整为带 `#` 的大写十六进制色值，例如输入 `#1f4e79` 会稳定收敛为 `#1F4E79`；Word/PPT 在各自 XML 中继续按目标格式落为 `1F4E79`。
- 原有导出结构未重写，仍保持 Word/PDF/PPT 各自的内容组织，只把共享品牌规则往中心收口，控制改动面。

验证证据：

- RED：`.\mvnw.cmd -Dtest=ReportApplicationServiceTest#normalizesBrandThemeConsistentlyAcrossWordPdfAndPowerPointExports -pl backend/java-report-core test` 首次失败，断言显示 `word/media/logo.svg` 仍输出 `primaryColor=#1f4e79`，证明 fallback logo 还在吃原始小写色值。
- GREEN：同命令在实现后通过，断言 Word `styles.xml`、PDF 颜色流、PPT `slide1.xml` 与 Word/PPT fallback logo 元数据已统一落为 `1F4E79 / #1F4E79`。
- 导出回归：`.\mvnw.cmd "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument+exportsCurrentReportVersionAsPdfDocument+exportsPdfAcrossMultiplePagesWhenBodyIsLong+exportsCurrentReportVersionAsPowerPointDocument" -pl backend/java-report-core test` 通过 4 个测试，确认共享主题抽象没有破坏现有 Word/PDF/PPT 导出能力。

仍需继续：

- 当前统一的是品牌字段归一化，不是完整企业版式模板。段落层级、页眉页脚字号体系、表格、图表、封面页、PPT 母版、PDF 真正的版面引擎仍未进入统一治理。
- SVG Logo 到 PDF 图片流的完整转换、统一模板配置中心和可运营的企业品牌管理，仍然是后续继续朝客户生产版推进时的重点缺口。

## 127. 2026-06-25 UC-04 PPT 最小多幻灯片闭环

本轮目标：把 PPT 导出从“单页最小演示文稿”推进到“总览目录页 + 每章节一页”的最小多幻灯片结构，让客户下载后的文稿至少具备像样的章节承载，而不是所有内容堆在一页里。

已完成：

- `ReportApplicationService.renderPptx(...)` 不再固定只写 `slide1.xml`，而是会根据章节数动态生成：
  - `slide1.xml`：目录总览页
  - `slide2..N.xml`：每个章节各自独立的标题/正文/引用页
- `ppt/presentation.xml`、`ppt/_rels/presentation.xml.rels`、`[Content_Types].xml` 已同步改为按真实 slide 数量输出对应的 `sldId`、`Relationship` 和 `Override`。
- 每张幻灯片继续复用现有品牌样式、页眉页脚结构和 Logo 图片关系，不另外引入新的主题系统或母版依赖。
- 章节页正文会承载对应 section 的 `content` 与 `References`，目录页只保留 TOC，不再把所有章节正文重新塞回首页。

验证证据：

- RED：`.\mvnw.cmd -Dtest=ReportApplicationServiceTest#exportsPowerPointAsOverviewAndSectionSlides -pl backend/java-report-core test` 首次失败，断言显示 `[Content_Types].xml` 中不存在 `/ppt/slides/slide2.xml`，证明此前 PPT 仍只有单页结构。
- GREEN：同命令在实现后通过，断言 `slide1.xml/slide2.xml/slide3.xml`、`presentation.xml`、`presentation.xml.rels` 与各 slide relationship 已按多页结构生成。
- 导出回归：`.\mvnw.cmd "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument+exportsPowerPointAsOverviewAndSectionSlides+normalizesBrandThemeConsistentlyAcrossWordPdfAndPowerPointExports+exportsCurrentReportVersionAsEnterpriseWordDocument+exportsCurrentReportVersionAsPdfDocument+exportsPdfAcrossMultiplePagesWhenBodyIsLong" -pl backend/java-report-core test` 通过 6 个测试，确认 PPT 多页增强没有破坏现有 Word/PDF/PPT 与品牌归一化链路。

仍需继续：

- 当前多幻灯片仍是最小规则版，不包含封面页、结尾页、图表页、备注页、母版、主题色板、页码占位符或复杂版面流式分页。
- 如果继续朝客户生产版推进，下一步更值得补的是企业级 PPT 模板治理和图表/表格/封面页组件，而不是单纯继续堆静态 XML 片段。

## 107. 2026-06-25 Higress 分享坏 token 404 边界闭环

本轮目标：把分享访问中“不存在 token 被包装成 500”的错误边界修正为业务可理解的 `404`，并同步确认 Higress 到 Java 的真实业务路由已经生效。

已完成：

- 在 `GlobalExceptionHandler` 中补充 `IllegalArgumentException` 与 `IllegalStateException` 的业务映射：`not found` -> `404`，冲突/过期类非法状态 -> `409`。
- 补充 `GlobalExceptionHandlerTest`，覆盖 `share link not found` 返回 `404`、`share link expired` 返回 `409`，并保留 `SecurityException -> 403`。
- 使用当前源码重建 Java 镜像：`docker build -f Dockerfile.java -t intelligent-report-system-java-report-core .`。
- 删除旧 `ir-java-smoke` 后以新镜像重建，并恢复 `intelligent-report-infra_default` 与 `intelligent-report-system_report-net` 双网络连接。
- 真实复测不存在的分享 token：直连 Java `18082` 与经 Higress `18000` 的响应体、状态码均一致返回 `404 share link not found: nonexistent`。
- 结合更早的 `GET http://127.0.0.1:18000/api/v1/reports -> 401` 结果，可以确认 Higress 外部 `/api/v1/**` 已实际进入 Java 业务核心，而不是停留在 Console 或未绑定状态。

验证证据：

- Java 定向测试：  
  `.\mvnw.cmd -pl backend/java-report-core -Dtest=GlobalExceptionHandlerTest,PermissionApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`，结果 `Tests run: 14, Failures: 0, Errors: 0`。
- 新容器启动验证：`docker logs --tail 50 ir-java-smoke` 显示 `ReportCoreApplication v0.1.0-SNAPSHOT`、`profile=dev`、Flyway schema 已是最新版本。
- 直连 Java GREEN：  
  `curl.exe -i -X POST http://127.0.0.1:18082/api/v1/share-links/nonexistent/access -H "Content-Type: application/json" --data "{}"`  
  返回 `HTTP/1.1 404`，响应体 `{"code":404,"message":"share link not found: nonexistent",...}`。
- Higress GREEN：  
  `curl.exe -i -X POST http://127.0.0.1:18000/api/v1/share-links/nonexistent/access -H "Content-Type: application/json" --data "{}"`  
  返回 `HTTP/1.1 404 Not Found`，header 含 `server: istio-envoy`，响应体与 Java 直连一致。

结论：

- 不存在的分享 token 不再被错误包装成系统 `500`，而是稳定返回可预期的 `404`。
- Higress `/api/v1/** -> java-report-core` 业务路由已具备真实请求级证据，`RELEASE.md` 与 `validation_report.md` 中“尚未绑定”的旧结论应视为过期。
- 后续分享边界仍可继续扩展真实验收，例如密码错误 `403`、过期 token `409`、允许下载与禁止下载的授权分支。

## 108. 2026-06-25 UC-08 最小拖拽画布与位置持久化闭环

本轮目标：把规则编排从“表单式最小画布”推进到“用户能真实拖拽节点并保存布局位置”的可交互画布，补齐原型对规则编排可视化操作的关键观感缺口。

已完成：

- `RuleDesigner.vue` 新增 `Interactive rule canvas`，基于仓库已安装的 `@vue-flow/core` 渲染真实节点/连线画布。
- 规则定义节点新增兼容位置字段 `position { x, y }`；若历史定义未携带位置，则前端按稳定网格生成默认布局，不阻断旧规则打开。
- 画布节点支持真实拖拽；`node-drag-stop` 后会把最新坐标同步回当前 `selectedRule.definition.nodes[].position`。
- `Save canvas changes` 现会连同节点位置一起提交，保证规则定义不仅保存节点/连线/字段，还能保存画布布局。
- 保留原 `Rule canvas editor`、Webhook 配置、审批记录、动作台账与调试/生产运行区域，避免新增画布后回退现有可编辑链路。

验证证据：

- RED：`npm run e2e -- tests/e2e/rule-engine.spec.ts` 首次失败为找不到 `Interactive rule canvas`，证明此前规则页并不存在真实拖拽画布。
- GREEN：同一 E2E 通过 1 个 Chromium 测试，覆盖 `start` 节点拖拽、后续新增 `notify/condition/approval/create_task` 节点、删除节点/连线、保存规则定义，以及 `savePayload.definition.nodes[].position.x/y` 随保存一起回传。
- 前端类型：`npm run typecheck` 通过，确认 `VueFlow` 集成与节点位置扩展没有破坏 TypeScript 契约。

当前边界：

- 当前属于“最小可拖拽画布”，已满足节点拖拽与位置保存，但还没有连线拖拽创建、自动布局、缩放工具栏、分组/子流程节点、节点碰撞约束或更强的可视化合法性提示。
- 独立人工审批流编排、子流程节点和超大规模规则图性能治理，仍是 UC-08 后续继续推进的主要缺口。

## 109. 2026-06-25 UC-06 轻量 OCR / PDF 文本抽取基线闭环

本轮目标：把文档解析从“文本对象可解析、扫描件只能失败”推进到“本地已具备轻量 OCR / PDF 文本抽取时可真实解析，未配置时仍明确失败”的最小生产基线。

已完成：

- `MinioTextObjectLoader` 不再只按扩展名硬拒绝二进制对象，而是先读取对象字节，再按对象类型分流。
- 文本对象继续直接按 UTF-8 读取；PDF/图片对象新增 `binary_text_extractor` 扩展点，默认由 `CommandBinaryTextExtractor` 承接。
- `CommandBinaryTextExtractor` 对 `.pdf` 优先调用 `pdftotext` 做文本抽取；若抽取为空，再回退 `tesseract` OCR。
- 图片类对象（`.png/.jpg/.jpeg/.tif/.tiff/.bmp`）直接走 `tesseract` OCR。
- 若宿主机未安装 `pdftotext` / `tesseract`，或命令执行失败、抽取结果为空，系统仍返回 `document {objectKey} requires OCR or table extraction before text parsing` 或明确的 OCR 失败原因，保留原失败可观测边界，不伪装成成功。
- `DocumentParseIndexingService` 的已有持久化、OpenSearch、Milvus 写入链路保持不变，因此扫描 PDF 一旦提取出文本，就能沿现有 chunk / embedding / index 流程直接闭环。

验证证据：

- RED：`python -m pytest tests/unit/python/test_minio_object_loader.py tests/unit/python/test_document_parse_indexing.py -q` 首次失败为  
  `TypeError: MinioTextObjectLoader.__init__() got an unexpected keyword argument 'binary_text_extractor'`，证明此前对象加载器没有二进制解析扩展能力。
- GREEN：同一命令通过 `11 passed`，覆盖：
  - 文本对象直读不回退；
  - PDF 对象可通过二进制提取器返回真实文本；
  - 命令缺失时保持清晰失败原因；
  - `DocumentParseIndexingService` 在扫描 PDF 有文本输出时会走 `document.parse.completed`、持久化 chunk 并写入搜索索引。
- Python 回归：`python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_minio_object_loader.py tests/unit/python/test_document_parse_indexing.py -q` 通过 `13 passed`，确认文档分块、嵌入载荷和新增 OCR 分流没有相互打架。
- 本机依赖探查：`Get-Command tesseract` 与 `Get-Command pdftotext` 均可解析到本地命令路径，说明当前开发环境已具备轻量命令型 OCR / PDF 文本抽取基础。

当前边界：

- 当前仍属于“轻量解析基线”，不是完整生产级智能文档理解平台。
- 尚未覆盖复杂表格结构恢复、扫描件版面重建、多栏 PDF、印章/水印干扰、图片内表格识别、Office 二进制文档解析和证据级 OCR 置信度分层。
- 当前能力依赖宿主机命令存在；若后续要提升本地一致性，更适合在 S24 Docker 化阶段补一个低资源 OCR sidecar 或工具镜像，并保持 dev/prod profile 隔离。

## 110. 2026-06-25 UC-06 真实 Docker 扫描 PDF 解析闭环

本轮目标：把 UC-06 从“宿主机具备轻量 OCR 基线”推进到“Docker 真实 worker、真实上传接口、真实 PostgreSQL/OpenSearch/Milvus 均可留痕”的可复验证据。

已完成：

- `Dockerfile.python` 补入 `poppler-utils` 与 `tesseract-ocr`，让 `document-parse-worker` 容器内具备 PDF 文本抽取与 OCR 命令，不再依赖宿主机 PATH 偶然可用。
- `tests/unit/python/test_docker_compose_contract.py` 新增 Dockerfile 契约断言，固定 Python 运行时镜像必须包含上述 OCR 依赖；同时补充 `document-parse-worker` Compose 契约，固定 `entrypoint` 与 `postgres/milvus/opensearch/minio/rocketmq` 依赖地址。
- 使用当前源码重建镜像：`docker build -f Dockerfile.python -t intelligent-report-system-python-ai-service .`，镜像构建成功。
- 真实烟测时明确覆盖镜像默认 `uvicorn` entrypoint，改用 `--entrypoint python -m app.document_processing.worker_main` 启动 worker。
- 真实烟测网络从旧结论修正为 `intelligent-report-infra_default`；因为本地正在运行的 `postgres/minio/opensearch/milvus/rocketmq` 都挂在该网络，若只接 `intelligent-report-system_report-net` 会导致 Milvus 等依赖不可达。
- 以知识库 owner `sub=2114` 的 JWT 调用 `POST /api/v1/documents/upload?knowledgeBaseId=1` 上传 `tests/e2e/fixtures/minimal-scan-smoke.pdf`，返回 `documentId=8`、`parseStatus=pending`。
- 轮询 `GET /api/v1/documents/8` 后，状态已回写为 `processed`；说明 Java 上传、RocketMQ 投递、Python worker 消费、MinIO 读取、PostgreSQL/OpenSearch/Milvus 写入与 Java 状态回写链路全部打通。

验证证据：

- RED：`python -m pytest tests/unit/python/test_docker_compose_contract.py -q` 首次失败为 Dockerfile 中缺少 `tesseract-ocr`，证明容器运行时之前并不具备 OCR 命令。
- GREEN：补齐 `Dockerfile.python` 后，`python -m pytest tests/unit/python/test_docker_compose_contract.py tests/unit/python/test_minio_object_loader.py tests/unit/python/test_document_parse_indexing.py -q` 通过 `16 passed`。
- 运行时镜像：`docker build -f Dockerfile.python -t intelligent-report-system-python-ai-service .` 成功，镜像摘要为 `sha256:e53cf1af1e3465fd659e98191cf50f9d17927cbf5587b0b277e2258aa95d0754`。
- 真实 worker 启动：`docker run -d --name ir-document-parse-worker-smoke --network intelligent-report-infra_default --entrypoint python ... intelligent-report-system-python-ai-service -m app.document_processing.worker_main` 成功；日志出现 `INFO:__main__:document parse worker starting`。
- 真实上传状态：上传接口返回 `documentId=8/parseStatus=pending`；随后 `GET /api/v1/documents/8` 返回 `parseStatus=processed`。
- PostgreSQL 证据：
  - `knowledge_documents.id=8`，`parse_status=processed`；
  - `document_parse_results.document_id=8`，`status=processed`；
  - `document_chunks` 1 条，内容包含 `SCAN OCR SMOKE INVOICE 20` 与 `TOTAL 3560 YUAN`；
  - `embeddings.milvus_primary_key=doc_8_chunk_0`，`embedding_model=local-hash-embedding`，`vector_dimension=64`。
- OpenSearch 证据：`knowledge_entries_text` 可按 `documentId=8` 查回 `_id=doc_8_chunk_0`，正文为 `SCAN OCR SMOKE INVOICE 20 / TOTAL 3560 YUAN`。

当前边界：

- 这次闭环证明了“低资源 Docker 环境下可跑通扫描 PDF 的轻量 OCR 链路”，但还不是完整生产级文档理解能力。
- 当前提取仍偏向文本抽取 + 基础 OCR，对复杂表格、多栏版面、印章/水印干扰、扫描件结构恢复和 Office 二进制格式仍无高置信支持。
- 当时前端浏览器侧还缺一条连接真实 Java 后端的 UC-06 自动化 E2E；该缺口已在第 111 节补上基础 real-backend Playwright 验收，并在 2026-06-26 进一步增强为“上传响应 + 页面状态 + 状态接口”三重对齐校验。

## 111. 2026-06-25 UC-06 上传页知识库选择与真实浏览器验收闭环

本轮目标：把 UC-06 从“后端链路已真实可跑，但前端上传页仍硬编码 `knowledgeBaseId=1`、真实浏览器回归缺失”推进到“前端按当前用户可用知识库上传，并具备 real-backend Playwright 自动验收”。

已完成：

- `KnowledgeUpload.vue` 不再把上传目标知识库硬编码为 `1`，而是在页面加载时调用 `GET /api/v1/knowledge-bases?page=1&pageSize=20`，读取当前用户可用知识库列表并默认选择第一项。
- 上传页新增知识库选择下拉；当当前用户没有可用知识库时，会明确展示失败提示，而不是默默把请求发往错误知识库。
- `knowledgeApi` 新增 `listKnowledgeBases()` 契约；`uploadDocument()` 改为显式携带 `knowledgeBaseId` 查询参数，和当前 Java `POST /api/v1/documents/upload` 契约保持一致。
- `knowledge-upload.spec.ts` 新增前端 E2E，验证上传请求使用当前用户可用知识库，而不是硬编码 `1`。
- 上传页轮询窗口从原来的 5 次扩大到 20 次，避免真实 Docker worker 稍慢时页面过早停在 `pending`。
- 新增 `knowledge-upload-real-backend.spec.ts`，使用真实 Java/PostgreSQL/MinIO/RocketMQ/Python worker，通过浏览器上传 `tests/e2e/fixtures/minimal-scan-smoke.pdf`，验证页面最终显示 `minimal-scan-smoke.pdf processed`。

验证证据：

- RED：`npm run e2e -- tests/e2e/knowledge-upload.spec.ts` 新增“较慢异步解析成功状态”用例后首次失败，证明原上传页轮询窗口过短，真实异步解析稍慢就无法显示 `processed`。
- GREEN：扩大轮询窗口后，`npm run e2e -- tests/e2e/knowledge-upload.spec.ts` 通过 `3 passed`，覆盖：
  - 上传请求使用当前用户知识库而不是硬编码 `1`；
  - 解析失败时展示 `failed` 与失败原因；
  - 较慢异步成功路径最终展示 `processed`。
- 契约测试：`npm run test -- src/api/apiContracts.test.ts` 通过 `21 passed`，验证新增 `GET /knowledge-bases` 和带 `knowledgeBaseId` 的上传契约。
- 真实浏览器 E2E：  
  `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/knowledge-upload-real-backend.spec.ts`  
  通过 `1 passed`，证明浏览器上传扫描 PDF 后，可经真实 Java 上传接口、真实 RocketMQ、真实 Python worker 和真实 PostgreSQL/OpenSearch/Milvus 链路回写 `processed`。

当前边界：

- 当前上传页默认选择当前用户的第一个知识库，已消除硬编码 `1` 的主要生产风险，但还没有补“记住最近选择”“知识库状态筛选”“无可用知识库时引导创建”的完整体验。
- 当前 real-backend E2E 已证明主链可回归，但还没有自动断言 PostgreSQL/OpenSearch 的二次证据；数据库与搜索索引留痕仍主要依赖第 110 节的烟测取证。

## 153. 2026-06-26 UC-06 真实后端上传解析验收增强

本轮目标：把 `UC-06 / REQ-KB-002` 的真实浏览器验收从“页面最终显示 processed”推进到“页面状态、上传响应与后端状态接口三者一致”，并顺手清理上传页可见乱码，避免交付界面与自动化证据脱节。

已完成：

- `frontend/web-console/src/pages/knowledge/KnowledgeUpload.vue` 修复可见中文乱码，上传页标题、知识库占位文案、加载失败提示和“未选择知识库”反馈恢复为可交付中文。
- `frontend/web-console/tests/e2e/knowledge-upload-real-backend.spec.ts` 从单纯断言页面 `minimal-scan-smoke.pdf processed`，增强为：
  - 监听真实 `POST /api/v1/documents/upload`
  - 读取上传响应中的 `documentId`
  - 继续通过带业务 JWT 的真实 `GET /api/v1/documents/{documentId}` 轮询，断言 `filename=minimal-scan-smoke.pdf`、`parseStatus=processed`
- `frontend/web-console/src/api/reportApi.ts` 补齐 `EnterpriseBrandTemplate.layout` 前端类型，使既有共享版式模板契约重新与测试、类型检查保持一致。

验证证据：

- `npm run test -- src/api/apiContracts.test.ts` 通过 `21 passed`
- `npm run typecheck` 通过 `vue-tsc --noEmit`
- `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/knowledge-upload-real-backend.spec.ts` 通过 `1 passed (10.9s)`

结论：

- `UC-06` 现在不再只有“真实 worker / DB / 索引侧烟测”与“页面最终展示 processed”的松散证据，而是具备了浏览器上传、真实后端响应、状态查询接口三者对齐的自动化验收闭环。
- 剩余更大的生产级差距继续集中在高精度 OCR、复杂表格结构恢复、扫描件版面重建和多栏文档理解，而不再是基础上传解析链的可验证性。

## 112. 2026-06-25 UC-01 真实 DashScope OpenAI-compatible provider 闭环

本轮目标：把 UC-01 从“OpenAI-compatible provider 代码已接入，但真实外部模型仍未验证”推进到“真实 Docker worker、真实外部 GPT 兼容模型、真实 Java 审计专表均可留痕”的可复验证据。

已完成：

- 先在宿主机直接探测 DashScope OpenAI-compatible 接口，使用 `DASHSCOPE_API_KEY` 对 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` 发起最小请求，确认 `qwen-plus` 可以真实返回内容，排除网络和 Key 本身不可用的干扰。
- 为了沉淀本地可复用入口，新增 `scripts/uc01-real-provider-smoke-lib.mjs` 与 `scripts/uc01-real-provider-smoke.mjs`，把此前临时 `.tmp_uc01_*` 中的 JWT 生成、创建任务和确认大纲逻辑收编为正式 smoke 命令。
- 进一步新增 `scripts/uc01-real-provider-worker-lib.mjs` 与 `scripts/uc01-real-provider-worker.mjs`，把此前手工 `docker run` 重建 `ir-report-generation-worker-smoke` 的真实 provider 切换步骤也收编进仓库脚本。
- 再进一步新增 `scripts/p0-local-smoke-lib.mjs` 与 `scripts/p0-local-smoke.mjs`，把当前最关键的本地 P0 回归编排成固定顺序：`UC-01 real provider worker -> UC-01 strict smoke -> UC-06 real-backend 浏览器上传解析 E2E -> UC-04 real-backend 浏览器导出 E2E`。
- 复查后发现旧 `ir-report-generation-worker-smoke` 虽然 healthy，但环境里仍固定为 `LLM_PROVIDER=local-fallback`，即使宿主机已有 API Key，也不会真实外呼。
- 删除旧 worker 后，以真实外部 provider 参数重新启动 `ir-report-generation-worker-smoke`：
  - `LLM_PROVIDER=openai-compatible`
  - `LLM_MODEL=qwen-plus`
  - `LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1`
  - `LLM_API_KEY=$env:DASHSCOPE_API_KEY`
  - `REPORT_GENERATION_WORKER_PROVIDER=rocketmq`
  - `JAVA_SERVICE_URL=http://ir-java-smoke:8080`
  - `DATABASE_URL=postgresql://report:report123@postgres:5432/intelligent_report`
  - `OPENSEARCH_URL=http://ir-opensearch:9200`
  - `MILVUS_HOST=ir-milvus`
  - 网络继续复用 `intelligent-report-infra_default`
- 通过 Java API 创建并确认一条真实报告任务：`taskId=206`、`reportId=334`。确认大纲后，RocketMQ 将事件投递给 Python worker，worker 使用 DashScope `qwen-plus` 生成正文并回调 Java completion 接口。
- `GET /api/v1/reports/334` 已返回 `status=completed`、`currentVersionId=126`，正文不再是本地 fallback 固定文风，并保留真实 chunk 引用。
- PostgreSQL 已查到：
  - `report_generation_tasks(206)=completed/export/100`
  - `reports(334)=completed,current_version_id=126`
  - `model_invocations(task_id=206)` 为 `provider=openai-compatible`、`model_name=qwen-plus`、`status=succeeded`、`total_tokens=438`、`trace_id=trace_206_73ba3fce6758`

验证证据：

- 宿主机 provider 探针：对 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` 的最小请求成功返回 `DASHSCOPE_OK`，证明 `DASHSCOPE_API_KEY` 与兼容基址可用。
- 运行时日志：新 worker 启动后日志出现 `INFO:__main__:report generation worker starting`，确认容器以 `app.report_generation.worker_main` 进入真实消费模式。
- 真实任务状态：创建并确认 `taskId=206/reportId=334` 后，Java API 查询返回 `completed`。
- PostgreSQL 证据：
  - `report_generation_tasks.id=206`：`status=completed`、`current_stage=export`、`progress=100`
  - `model_invocations.task_id=206`：`provider=openai-compatible`、`model_name=qwen-plus`、`status=succeeded`、`total_tokens=438`
- 与第 11 节的 `local-fallback` smoke 对照后，可以确认当前 UC-01 已不是“只有本地降级链路能跑通”，而是“真实外部 GPT provider + Java 审计专表”已闭环。

当前边界：

- 仓库已新增 `scripts/uc01-real-provider-smoke.mjs` 与 `scripts/uc01-real-provider-smoke-lib.mjs`，可直接创建并确认一条 UC-01 报告生成任务，替代之前的临时 `.tmp_uc01_*` 手工脚本；脚本现已支持轮询 `GET /reports/{reportId}` 直到 `completed/failed`，并可在设置 `UC01_SMOKE_POSTGRES_CONTAINER=ir-postgres` 时自动查询 `model_invocations` 审计专表；开启 `UC01_SMOKE_STRICT_AUDIT=true` 后，还会把“查不到审计证据”“仍走 local-fallback”“审计状态不是 succeeded”直接判为失败。
- 当前仍只验证了单 provider 成功路径；多模型路由、失败切换、provider 配额/限流和更细粒度的成本治理尚未闭环。
- 当前真实 provider worker 仍是按本地开发目的手工 `docker run` 重启的；后续应继续把真实 provider 与 fallback 的切换方式固化到 Compose profile 或更完整的 smoke 脚本中，降低后续重复验证成本。

## 113. 2026-06-25 P0 本地 smoke 套餐补齐 UC-04 导出闭环

本轮目标：把当前仓库内已经跑通的 UC-01 真实 provider 与 UC-06 真实上传解析，继续收束成更接近客户主链的本地回归入口，补上 UC-04 真实导出，避免 P0 只证明“能生成、能上传”，却没有覆盖“能交付文件”。

已完成：

- `scripts/p0-local-smoke-lib.mjs` 新增第 4 步 `uc04-real-backend-e2e`，固定执行 `tests/e2e/report-export-real-backend.spec.ts`。
- `tests/unit/node/p0_local_smoke_bundle.test.mjs` 已同步扩展为 4 步门禁，固定要求 `UC-01 worker -> UC-01 strict smoke -> UC-06 upload E2E -> UC-04 export E2E` 的顺序和参数。
- `docs/skill-chain/DEPLOY.md` 已更新本地 P0 smoke 套餐说明，明确当前固定顺序包含 UC-04 企业 PDF 导出真实浏览器验收。

验证证据：

- RED：`node --test tests/unit/node/p0_local_smoke_bundle.test.mjs` 首次失败为 `3 !== 4`，证明原 P0 套餐确实缺少 UC-04 导出回归步骤。
- GREEN：`node --test tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/uc01_real_provider_worker.test.mjs tests/unit/node/uc01_real_provider_smoke.test.mjs` 通过 `10 passed`。
- 真实本地套餐：  
  `P0_SMOKE_DASHSCOPE_API_KEY=$DASHSCOPE_API_KEY node scripts/p0-local-smoke.mjs`  
  输出 `completedSteps=["uc01-worker","uc01-strict-smoke","uc06-real-backend-e2e","uc04-real-backend-e2e"]`。
- UC-01 strict smoke 真实证据：
  - `taskId=221`
  - `reportId=349`
  - `provider=openai-compatible`
  - `modelName=qwen-plus`
  - `status=succeeded`
  - `totalTokens=508`
  - `traceId=trace_221_b7c7a3fc4c82`
- UC-06 real-backend E2E：`knowledge-upload-real-backend.spec.ts` 结果 `1 passed`。
- UC-04 real-backend E2E：`report-export-real-backend.spec.ts` 结果 `2 passed`，已同时覆盖 PDF 与 PPTX 两条真实导出下载链路。

结论：

- 当前仓库内的 P0 一键本地 smoke 已不再只覆盖“生成 + 上传”，而是具备 `真实外部 GPT provider -> 真实上传解析 -> 真实导出交付` 的最小客户主链回归入口。
- 下一步更值得继续扩入 P0/P1 套餐的是 `UC-03 引用溯源`、`UC-09 分享访问` 和 `UC-12 审计查询`，这样会更接近“客户验收视角的一键闭环”。

## 114. 2026-06-25 P1 本地 smoke 套餐收编引用、分享、审计闭环

本轮目标：把已经单独跑通过的 `UC-03 引用溯源`、`UC-09 分享访问`、`UC-12 审计查询` 三条真实后端验收链，收编成一个可重复执行的本地 P1 smoke 套餐，减少“功能真实存在，但只能靠人工拼命令复验”的摩擦。

已完成：

- 新增 `scripts/p1-local-smoke-lib.mjs`，固定编排三步 real-backend E2E：
  - `uc03-real-backend-e2e -> tests/e2e/report-citation-real-backend.spec.ts`
  - `uc09-real-backend-e2e -> tests/e2e/share-real-backend.spec.ts`
  - `uc12-real-backend-e2e -> tests/e2e/audit-real-backend.spec.ts`
- 新增 `scripts/p1-local-smoke.mjs`，复用与 P0 相同的 Windows `cmd.exe /d /s /c npm ...` 兼容执行方式，输出 `completedSteps/results/plannedSteps` JSON，便于归档。
- 新增 `tests/unit/node/p1_local_smoke_bundle.test.mjs`，固定 P1 套餐的步骤名、命令、参数、工作目录与 real-backend 环境变量。
- `share-real-backend.spec.ts` 的错误密码断言已从旧文案 `访问被拒绝` 调整为当前真实后端统一 403 契约：`code=403`、`message=当前账号无权执行该操作`，避免 E2E 绑死历史文案。
- `docs/skill-chain/DEPLOY.md` 已补充 `node scripts/p1-local-smoke.mjs` 入口说明。

验证证据：

- RED：`node --test tests/unit/node/p1_local_smoke_bundle.test.mjs` 首次失败为 `ERR_MODULE_NOT_FOUND: ... scripts/p1-local-smoke-lib.mjs`，证明 P1 套餐在本轮前并不存在。
- GREEN：`node --test tests/unit/node/p1_local_smoke_bundle.test.mjs` 通过 `1 passed`。
- 真实分享链路回归：
  - 初次执行 `node scripts/p1-local-smoke.mjs` 在 `uc09-real-backend-e2e` 失败，原因不是功能断链，而是 `share-real-backend.spec.ts` 仍断言旧文案 `访问被拒绝`。
  - 调整验收脚本后，`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/share-real-backend.spec.ts` 通过 `1 passed`。
- 真实本地套餐：
  `node scripts/p1-local-smoke.mjs`
  输出 `completedSteps=["uc03-real-backend-e2e","uc09-real-backend-e2e","uc12-real-backend-e2e"]`。
- UC-03 real-backend E2E：`report-citation-real-backend.spec.ts` 结果 `1 passed`。
- UC-09 real-backend E2E：`share-real-backend.spec.ts` 结果 `1 passed`。
- UC-12 real-backend E2E：`audit-real-backend.spec.ts` 结果 `1 passed`。

结论：

- 当前仓库内除了 P0 的“生成/上传/导出”主链外，已经有一条可重复执行的 P1 套餐，覆盖客户更容易直接感知的“引用可追溯、分享可受控、审计有边界”三条真实后端验收链路。
- 下一步如果继续朝“可交付客户生产版本”推进，更适合把 `UC-10 批注协作`、`UC-11 RBAC 管理` 与 `UC-14 版本回滚` 收编成 P2 套餐，形成更完整的交付验收梯队。

## 115. 2026-06-25 P2 本地 smoke 套餐收编协作、RBAC、版本回滚闭环

本轮目标：把已经具备真实后端浏览器验收的 `UC-10 批注协作`、`UC-11 用户与 RBAC`、`UC-14 版本回滚` 三条链，收编成一个可重复执行的 P2 本地 smoke 套餐，让“协作与治理”这层交付能力也拥有仓库内固定回归入口。

已完成：

- 新增 `scripts/p2-local-smoke-lib.mjs`，固定编排三步 real-backend E2E：
  - `uc10-real-backend-e2e -> tests/e2e/report-collaboration-real-backend.spec.ts`
  - `uc11-real-backend-e2e -> tests/e2e/user-rbac-real-backend.spec.ts`
  - `uc14-real-backend-e2e -> tests/e2e/report-version-real-backend.spec.ts`
- 新增 `scripts/p2-local-smoke.mjs`，沿用 P0/P1 的 Windows `cmd.exe /d /s /c npm ...` 兼容执行方式，输出 `completedSteps/results/plannedSteps` JSON，便于归档和后续脚本复用。
- 新增 `tests/unit/node/p2_local_smoke_bundle.test.mjs`，固定 P2 套餐的步骤名、命令、参数、工作目录与 real-backend 环境变量。
- `report-version-real-backend.spec.ts` 已把回滚后的文本断言收紧到 `data-section-content` 正文区域，避免版本差异面板中的“原文：收入增长 8%。` 与正文文本同时命中 strict mode。
- `docs/skill-chain/DEPLOY.md` 已补充 `node scripts/p2-local-smoke.mjs` 入口说明。

验证证据：

- RED：`node --test tests/unit/node/p2_local_smoke_bundle.test.mjs` 首次失败为 `ERR_MODULE_NOT_FOUND: ... scripts/p2-local-smoke-lib.mjs`，证明 P2 套餐在本轮前并不存在。
- GREEN：`node --test tests/unit/node/p2_local_smoke_bundle.test.mjs` 通过 `1 passed`。
- UC-14 单项 RED：初次执行 `node scripts/p2-local-smoke.mjs` 时，`report-version-real-backend.spec.ts` 在回滚后用全页 `getByText('收入增长 8%。')` 命中 2 个节点，既包括正文，也包括差异面板 `原文：收入增长 8%。`，导致 Playwright strict mode 失败。
- UC-14 单项 GREEN：调整正文区断言后，  
  `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/report-version-real-backend.spec.ts`  
  通过 `1 passed`。
- 真实本地套餐：
  `node scripts/p2-local-smoke.mjs`
  输出 `completedSteps=["uc10-real-backend-e2e","uc11-real-backend-e2e","uc14-real-backend-e2e"]`。
- UC-10 real-backend E2E：`report-collaboration-real-backend.spec.ts` 结果 `1 passed`。
- UC-11 real-backend E2E：`user-rbac-real-backend.spec.ts` 结果 `1 passed`。
- UC-14 real-backend E2E：`report-version-real-backend.spec.ts` 结果 `1 passed`。

结论：

- 当前仓库内已经形成三层可重复执行的本地真实回归梯队：
  - `P0`: 生成 / 上传 / 导出
  - `P1`: 引用 / 分享 / 审计
  - `P2`: 协作 / RBAC / 版本回滚
- 下一步如果继续朝“可交付客户生产版本”推进，更适合开始设计 `P3` 套餐，把规则引擎、数据源、Dashboard 这组偏运营与治理的能力也收编成稳定回归入口。

## 116. 2026-06-25 P3 本地 smoke 套餐收编工作台、数据源同步、审批待办闭环

本轮目标：把已经具备真实后端浏览器验收基础的 `UC-13 工作台`、`UC-07 数据源同步`、`UC-08 审批待办` 三条链，收编成一个可重复执行的 P3 本地 smoke 套餐，让“运营与治理”层也拥有仓库内固定回归入口。

已完成：

- 新增 `scripts/p3-local-smoke-lib.mjs`，固定编排三步 real-backend E2E：
  - `uc13-real-backend-e2e -> tests/e2e/dashboard-real-backend.spec.ts`
  - `uc07-real-backend-e2e -> tests/e2e/data-source-sync-real-backend.spec.ts`
  - `uc08-real-backend-e2e -> tests/e2e/approval-inbox-real-backend.spec.ts`
- 新增 `scripts/p3-local-smoke.mjs`，沿用 P0/P1/P2 的 Windows `cmd.exe /d /s /c npm ...` 兼容执行方式，输出 `completedSteps/results/plannedSteps` JSON，便于归档和后续脚本复用。
- 新增 `tests/unit/node/p3_local_smoke_bundle.test.mjs`，固定 P3 套餐的步骤名、命令、参数、工作目录与 real-backend 环境变量。
- 新增 `dashboard-real-backend.spec.ts`，通过真实 Java API + JWT 预置知识库、知识条目、数据源和报告任务，浏览器打开 `/dashboard` 后验证真实指标大于 0，且最近活动包含对应业务操作。
- 新增 `data-source-sync-real-backend.spec.ts`，通过真实后端配置 API 数据源并触发同步；为适配 Java 运行在 Docker 内的真实访问路径，外部 API fixture 从宿主机 `127.0.0.1` 修正为页面填写 `http://host.docker.internal:<port>/reports`，确保 Java 容器可以回连宿主机 mock API。
- 新增 `approval-inbox-real-backend.spec.ts`，通过真实后端创建带 `approval` 节点的规则、提交并触发审批记录，再由浏览器打开 `/rules/approvals` 完成批准动作，并在 `Approved` 视图核验审批意见。
- `docs/skill-chain/DEPLOY.md` 已补充 `node scripts/p3-local-smoke.mjs` 入口说明。

验证证据：

- RED：`node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` 首次失败为 `ERR_MODULE_NOT_FOUND: ... scripts/p3-local-smoke-lib.mjs`，证明 P3 套餐在本轮前并不存在。
- GREEN：`node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` 通过 `1 passed`。
- UC-07 单项 RED：`data-source-sync-real-backend.spec.ts` 初次在真实后端链路下失败，先后暴露出选择器歧义、同步日志行断言过宽，以及 Java 容器无法访问宿主机 `127.0.0.1:<port>` 的外部 API fixture；修正为精确选择器、行级断言，并改用 `host.docker.internal` 后恢复通过。
- UC-08 单项 RED：`approval-inbox-real-backend.spec.ts` 初次失败为审批页点击了历史列表中的第一条卡片，导致批准动作落到错误记录；后续改为按本次生成的 `approvalTitle` 精确定位卡片后恢复通过。
- UC-13 单项 RED：`dashboard-real-backend.spec.ts` 初次在套餐执行时因为最近活动存在多条历史记录而触发 Playwright strict mode；后续改为把断言限定在 `最近活动` 面板并使用更精确的首条匹配后恢复通过。
- 单项 GREEN：
  - `npm run e2e:real-backend -- tests/e2e/dashboard-real-backend.spec.ts` 通过 `1 passed`
  - `npm run e2e:real-backend -- tests/e2e/data-source-sync-real-backend.spec.ts` 通过 `1 passed`
  - `npm run e2e:real-backend -- tests/e2e/approval-inbox-real-backend.spec.ts` 通过 `1 passed`
- 类型回归：`cmd.exe /d /s /c "npm run typecheck"` 在 `frontend/web-console` 通过。
- 真实本地套餐：
  `node scripts/p3-local-smoke.mjs`
  输出 `completedSteps=["uc13-real-backend-e2e","uc07-real-backend-e2e","uc08-real-backend-e2e"]`。

结论：

- 当前仓库内已经形成四层可重复执行的本地真实回归梯队：
  - `P0`: 生成 / 上传 / 导出
  - `P1`: 引用 / 分享 / 审计
  - `P2`: 协作 / RBAC / 版本回滚
  - `P3`: 工作台 / 数据源同步 / 审批待办
- 这意味着 `UC-07`、`UC-08`、`UC-13` 不再只有散落的单项验证，而是具备了固定套餐化的真实浏览器回归入口；剩余更大的生产级差距继续集中在 `UC-04` 高级企业版式、`UC-06` 高精度 OCR/表格版面理解，以及 `UC-08` 独立人工审批流、子流程和更高级画布治理。

## 117. 2026-06-25 UC-08 审批过滤真实后端复核

本轮目标：确认审批待办页的 `ruleId/assigneeRole/approvalTitle` 过滤不是“测试桩通过”，而是在当前真实 Java 容器、PostgreSQL 与浏览器联调下真正生效。

已完成：

- 发现 `ir-java-smoke` 仍运行旧镜像 `sha256:3804fc3d...`，与当前源码中已经支持审批过滤的仓库实现不一致。
- 使用当前源码重新构建 `intelligent-report-system-java-report-core:latest`，并按原容器拓扑替换 `ir-java-smoke`，保留：
  - `18082:8080`
  - `intelligent-report-infra_default`
  - `intelligent-report-system_report-net`
  - 原有 `SPRING_PROFILES_ACTIVE=dev`、PostgreSQL、MinIO、RocketMQ 等环境变量
- 新容器健康检查恢复为 `healthy` 后，直连 `http://127.0.0.1:18082/api/v1` 创建一条目标审批规则和一条干扰审批规则。
- 使用真实 JWT 与真实后端接口验证：
  - `GET /rules/approval-records?status=pending&ruleId=<targetRuleId>&assigneeRole=finance_manager&approvalTitle=Finance%20approval`
  - 返回 `total=1`
  - 响应中仅包含目标规则审批记录，不再混入 `legal_manager` 干扰审批
- 重新执行 `approval-inbox-real-backend.spec.ts`，浏览器侧通过真实后端验证：
  - 待审批卡片聚合展示
  - `Rule ID filter`、`Assignee role filter`、`Approval title filter` 联合过滤
  - `Approve` 动作后待办列表刷新
  - `Approved` 视图仅展示目标审批历史

验证证据：

- 容器替换后：`docker ps --filter "name=ir-java-smoke"` 显示 `IMAGE=intelligent-report-system-java-report-core`，`STATUS=healthy`。
- 真实 API：筛选响应返回 `targetRuleId=65`、`distractorRuleId=66`、`total=1`，唯一记录 `ruleId=65 / assigneeRole=finance_manager / status=pending`。
- 真实浏览器 E2E：`npm run e2e:real-backend -- tests/e2e/approval-inbox-real-backend.spec.ts` 当前已通过 `4 passed (10.9s)`，在原有 `ruleId/assigneeRole/approvalTitle` 基础上，又额外覆盖了 `createdByUserId/approvedByUserId/createdAtFrom/createdAtTo` 扩展运营过滤、批量审批与 `Approved` 视图联动。

## 154. 2026-06-26 UC-08 审批中心扩展运营过滤闭环

本轮目标：把审批待办页从“只够最小按规则/角色/标题筛选”推进到“支持发起人、审批人和创建时间范围过滤”，让规则审批运营面更接近真实客户使用场景，并用真实后端浏览器回归固定证据。

已完成：

- `frontend/web-console/src/pages/rules/ApprovalInbox.vue` 新增：
  - `Created by filter`
  - `Approved by filter`
  - `Created from filter`
  - `Created to filter`
- 前端会把分钟粒度时间输入归一化成完整 UTC 时间串（如 `2026-06-25T08:00 -> 2026-06-25T08:00:00Z`），避免查询参数在浏览器与后端之间语义漂移。
- `frontend/web-console/tests/e2e/approval-inbox.spec.ts` 新增扩展过滤用例，覆盖：
  - `pending` 视图按 `createdByUserId + createdAtFrom + createdAtTo` 收敛记录
  - `approved` 视图继续叠加 `approvedByUserId` 过滤
  - 查询字符串真实携带扩展参数
- `frontend/web-console/tests/e2e/approval-inbox-real-backend.spec.ts` 新增真实后端用例，连接 `http://127.0.0.1:18082` 的 Java 容器，实际创建目标/干扰审批规则并验证扩展过滤、批量审批与批准后历史收敛。

验证证据：

- `npm run e2e -- tests/e2e/approval-inbox.spec.ts` 通过 `3 passed (8.1s)`
- `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/approval-inbox-real-backend.spec.ts` 通过 `4 passed (10.9s)`

结论：

- `UC-08` 的审批中心已经不再只是“最小可看/可批”的待办页，而是具备了更接近运营实际的跨规则过滤维度，并且这些过滤能力已在真实 Java 后端上形成浏览器级回归闭环。
- 剩余更大的生产级差距继续集中在独立人工审批流编排、SLA/催办、子流程节点和更高级画布治理，而不是审批中心基础筛选或批量审批能力本身。

后续闭合：

- 当前审批过滤与批量审批已在真实后端闭环，时间范围、发起人和审批人维度已补齐；剩余更完整的审批运营能力继续集中在 SLA/催办和独立审批流编排。

## 118. 2026-06-25 统一交付本地 smoke 总入口

本轮目标：把已经分别存在的 `P0 / P1 / P2 / P3` 本地 smoke 套餐进一步收束成一个统一入口，降低继续自循环迭代时的验证成本，让“客户交付前全链复验”可以通过一条命令顺序执行。

已完成：

- 新增 `scripts/delivery-local-smoke-lib.mjs`，固定编排 4 个阶段：
  - `p0-local-smoke`
  - `p1-local-smoke`
  - `p2-local-smoke`
  - `p3-local-smoke`
- 新增 `scripts/delivery-local-smoke.mjs`，沿用现有 Node smoke 风格，顺序执行各阶段并统一输出：
  - `completedSteps`
  - `results`
  - `plannedSteps`
- 总入口默认复用当前真实后端与本地基础设施：
  - `http://127.0.0.1:18082/api/v1`
  - `http://127.0.0.1:18082`
  - `ir-postgres`
- `P0` 所需真实 DashScope/OpenAI-compatible key 通过 `DELIVERY_SMOKE_DASHSCOPE_API_KEY` 统一透传到 `scripts/p0-local-smoke.mjs`。
- `docs/skill-chain/DEPLOY.md` 已补充统一总入口命令与环境变量说明。
- 新增 `tests/unit/node/delivery_local_smoke_bundle.test.mjs`，固定总入口的步骤顺序与关键环境变量透传契约。

验证证据：

- RED 形态由“仓库中不存在 delivery bundle 测试与脚本”体现；本轮新增后，`node --test tests/unit/node/delivery_local_smoke_bundle.test.mjs` 通过 `1 passed`。

后续闭合：

- 当前总入口已能把 P0-P3 串成一个固定顺序的本地交付回归命令，但尚未在本轮完整实跑全部 4 个套餐；后续继续推进客户级交付时，可在资源允许窗口执行一次全量套餐，沉淀最新 `results` JSON 作为阶段性交付证据。

## 119. 2026-06-25 P0-P3 统一交付 smoke 实跑通过

本轮目标：不再只停留在“统一总入口脚本存在”，而是实际执行 `delivery-local-smoke.mjs`，验证当前仓库是否已经具备一条可重复、可归档的本地客户交付总回归链。

已完成：

- 使用统一入口执行：

  ```bash
  DELIVERY_SMOKE_DASHSCOPE_API_KEY=$DASHSCOPE_API_KEY node scripts/delivery-local-smoke.mjs
  ```

- 实际顺序跑通：
  - `P0`: 生成 / 上传 / 导出
  - `P1`: 引用 / 分享 / 审计
  - `P2`: 协作 / RBAC / 版本回滚
  - `P3`: 工作台 / 数据源同步 / 审批待办
- 总入口输出 `completedSteps/results/plannedSteps` 完整 JSON，可直接作为当前阶段交付回归证据。
- 本轮实跑中 `P0-P3` 四个阶段全部完成，无需额外修复。

验证证据：

- 总入口 `completedSteps`：
  - `p0-local-smoke`
  - `p1-local-smoke`
  - `p2-local-smoke`
  - `p3-local-smoke`
- `P0` 实跑结果：
  - `uc01-worker`
  - `uc01-strict-smoke`
  - `uc06-real-backend-e2e`
  - `uc04-real-backend-e2e`
  - 其中 `UC-01` 命中真实 `openai-compatible / qwen-plus`，`model_invocations.totalTokens=644`，状态 `succeeded`
- `P1` 实跑结果：
  - `uc03-real-backend-e2e`
  - `uc09-real-backend-e2e`
  - `uc12-real-backend-e2e`
- `P2` 实跑结果：
  - `uc10-real-backend-e2e`
  - `uc11-real-backend-e2e`
  - `uc14-real-backend-e2e`
- `P3` 实跑结果：
  - `uc13-real-backend-e2e`
  - `uc07-real-backend-e2e`
  - `uc08-real-backend-e2e`
  - 其中 `approval-inbox-real-backend.spec.ts` 当前已通过 `4 passed (10.9s)`，覆盖基础审批、扩展运营过滤、批量审批与批准后历史视图联动

后续闭合：

- 当前已经证明 `P0-P3` 存在统一、可重复、真实后端驱动的本地交付回归入口；下一步如果继续朝客户生产版本推进，更值得优先补的是这些仍在矩阵中标记为“部分闭环”的能力本身：
  - `UC-01` 多模型路由与失败切换编排
  - `UC-04` 更高级企业版式与统一品牌模板治理
  - `UC-06` 高精度 OCR / 复杂表格 / 扫描件版面恢复
  - `UC-08` 独立人工审批流、子流程与更完整审批运营维度

## 120. 2026-06-25 UC-01 多模型失败切换最小闭环

本轮目标：把 `UC-01` 从“单个 OpenAI-compatible provider 失败后直接 local-fallback”推进到“至少支持一条真实可验证的候选模型切换链”，缩小报告生成离客户生产版本的差距。

已完成：

- `backend/python-ai-service/app/llm_orchestration/application/openai_compatible_provider.py` 新增 `LLM_MODEL_CANDIDATES` 候选模型链配置。
- 当前最小编排规则为：
  - 按 `LLM_MODEL_CANDIDATES` 顺序尝试模型
  - 任一候选模型成功即返回在线结果
  - 所有候选模型失败后才落 `local-fallback`
- 返回给 Java 的 `modelInvocation` 元数据会保留最终成功模型名，不会把次模型成功误记成主模型。
- 当所有候选模型失败时，`error_message` 会汇总各候选模型失败信息，便于后续审计和运维排障。
- 保持 Java 回调 payload 契约不变，改动仅收敛在 Python provider 选择层与对应单测。

验证证据：

- `tests/unit/python/test_llm_provider.py` 新增：
  - 主模型 `gpt-4.1-mini` 失败后切到 `qwen-plus` 成功
  - `gpt-4.1-mini` 与 `qwen-plus` 全失败后落 `local-fallback`
- `tests/unit/python/test_report_generation_worker.py` 新增：
  - worker 回调给 Java 的 `modelInvocation.model/provider/fallbackUsed/errorMessage` 在 failover 情况下保持正确
- 验证命令：

  ```bash
  python -m pytest tests/unit/python/test_llm_provider.py tests/unit/python/test_report_generation_worker.py -q
  ```

  结果：`9 passed`

后续闭合：

- 当前只补了“候选模型顺序切换”的最小生产切片，尚未实现：
  - 按租户/任务类型/成本等级的动态路由
  - 不同 provider 间切换，而不只是同一 OpenAI-compatible 协议下的多模型切换
  - 熔断、重试预算、provider 健康评分和更强的运行时治理

## 131. 2026-06-25 UC-01 模板上下文驱动的模型路由增强

本轮目标：把 UC-01 从“只有全局 `LLM_MODEL_CANDIDATES` 顺序切换”进一步推进到“可按任务上下文选择不同候选模型链”的更通用生产切片，同时保持现有 Java/Python 回调契约不变。

已完成：

- `LlmGenerationRequest` 新增 `context` 字段，`ReportGenerationWorker` 会把任务 payload 中的 `context` 原样透传给 provider。
- `OpenAiCompatibleProvider` 新增基于任务上下文的候选模型路由逻辑：
  - 先从 `context.templateSnapshot.templateId` 读取模板 ID
  - 若不存在，再回退读取 `context.templateId`
  - 将模板 ID 归一化为环境变量键，例如 `enterprise-board -> LLM_MODEL_ROUTE_TEMPLATE_ENTERPRISE_BOARD`
  - 若命中该环境变量，则优先使用该模板专属候选模型链；否则继续回退到全局 `LLM_MODEL_CANDIDATES`
- 该实现保持通用性，不绑定具体项目，也不要求 Java 先改事件 schema；只要任务上下文里已有模板信息，Python 侧即可做出差异化路由。

验证证据：

- 新增单测：
  - `tests/unit/python/test_llm_provider.py::test_configured_provider_routes_model_candidates_by_template_id`
  - `tests/unit/python/test_report_generation_worker.py::test_report_generation_worker_passes_template_context_to_llm_provider`
- 回归命令：

  ```bash
  python -m pytest tests/unit/python/test_llm_provider.py tests/unit/python/test_report_generation_worker.py -q
  ```

- 结果：`11 passed in 3.40s`
- 覆盖点：
  - 全局候选链 `gpt-4.1-mini -> qwen-plus`
  - 全部失败后 `local-fallback`
  - 基于 `templateSnapshot.templateId=enterprise-board` 切换到 `LLM_MODEL_ROUTE_TEMPLATE_ENTERPRISE_BOARD=qwen-plus,gpt-4.1-mini`
  - worker 把模板上下文透传给 provider，回调给 Java 的 `modelInvocation.model` 保持真实命中模型

结论：

- `UC-01` 当前已经不再只有“单全局候选链 + 失败切换”，而是具备了按模板上下文做差异化模型链路由的最小生产能力。
- 剩余更大的生产级差距仍在：
  - 按租户、任务等级、成本级别、语言或数据敏感级别进行更细粒度路由
  - 不同 provider 之间的切换，而不只是同一 OpenAI-compatible 协议下的模型切换
  - 熔断、配额、重试预算、健康度评分与自动降级治理

## 132. 2026-06-25 UC-01 generationMode 驱动的模型路由增强

本轮目标：继续把 UC-01 的模型路由从“模板级细分”扩展到“任务生成模式级细分”，让同一模板体系之外的自然语言/模板生成等任务形态也能走不同模型链。

已完成：

- Java `ReportApplicationService.generationContext(task)` 新增把 `task.generationMode()` 放入 worker `context`，确保 `report.generation.requested` 事件对 Python 侧显式暴露生成模式。
- Python `OpenAiCompatibleProvider` 的路由顺序进一步增强为：
  1. 先读取 `context.generationMode`
  2. 命中 `LLM_MODEL_ROUTE_GENERATION_MODE_<MODE>` 时优先使用该候选模型链
  3. 未命中时再回退到 `templateSnapshot.templateId / templateId`
  4. 仍未命中则回退全局 `LLM_MODEL_CANDIDATES`
- 这样当前路由能力已经具备“按任务模式粗分流，再按模板细分流”的最小通用层次，不需要为某个具体项目硬编码模型名。

验证证据：

- 新增/更新测试：
  - `tests/unit/python/test_llm_provider.py::test_configured_provider_routes_model_candidates_by_generation_mode`
  - `tests/unit/python/test_report_generation_worker.py::test_report_generation_worker_passes_generation_mode_to_llm_provider`
  - `backend/java-report-core/src/test/java/com/company/report/report/application/ReportApplicationServiceTest#confirmOutlineMovesTaskIntoRetrievalStage`
- Python 回归命令：

  ```bash
  python -m pytest tests/unit/python/test_llm_provider.py tests/unit/python/test_report_generation_worker.py -q
  ```

- 结果：`13 passed in 3.87s`
- Java 契约回归命令：

  ```bash
  .\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#confirmOutlineMovesTaskIntoRetrievalStage" test
  ```

- 结果：`BUILD SUCCESS`
- 覆盖点：
  - `generationMode=template` 命中 `LLM_MODEL_ROUTE_GENERATION_MODE_TEMPLATE=deepseek-r1,gpt-4.1-mini`
  - worker 会把 `generationMode` 与 `templateSnapshot` 一并透传给 provider
  - Java 发出的 worker payload `context` 已显式包含 `generationMode=natural_language`

结论：

- `UC-01` 当前的模型路由已经从“单全局候选链”推进到“生成模式 -> 模板 -> 全局候选链”的分层决策。
- 距离客户生产版本的剩余差距，继续集中在：
  - 按租户、语言、成本、SLA、敏感级别的更细粒度策略
  - provider 间健康度与配额治理
  - 自动熔断、重试预算与运行时观测闭环

## 155. 2026-06-26 UC-04 报告详情导出下载前端闭环

本轮目标：把 `UC-04` 从“报告详情页只提示导出成功”推进到“导出完成后浏览器直接进入受控下载链”，补齐用户在页面侧拿到产物的最后一跳。

已完成：

- `frontend/web-console/src/api/reportApi.ts` 新增 `getExportDownloadUrl(exportFileId)`，映射 `GET /api/v1/files/report-exports/{exportFileId}/download-url`。
- `frontend/web-console/src/pages/reports/ReportDetail.vue` 在 `createEnterpriseExport()` 成功后，若返回 `exportFileId`，会继续请求受控下载地址并使用 `window.location.assign(downloadUrl)` 触发浏览器下载。
- `frontend/web-console/tests/e2e/report-generation.spec.ts` 的 `REQ-REPORT-004` 场景新增受控下载断言，要求页面不仅显示 `导出已完成：report-88-v21.pdf`，还必须实际访问 `/api/v1/files/report-exports/7001/download-url` 与最终文件地址。
- `frontend/web-console/src/api/apiContracts.test.ts` 新增 REQ-REPORT-004 契约，固定 `reportApi.getExportDownloadUrl()` 的 URL 形态。

验证证据：

- RED：`npm run test -- src/api/apiContracts.test.ts` 首次失败为 `reportApi.getExportDownloadUrl is not a function`。
- GREEN：`npm run test -- src/api/apiContracts.test.ts` 通过 `22 passed`。
- RED：`npx playwright test tests/e2e/report-generation.spec.ts --grep "REQ-REPORT-004"` 首次失败为 `controlledDownloadRequested` 一直为 `false`，证明页面先前不会发起受控下载请求。
- GREEN：同命令通过 `1 passed`，并确认 mock 场景下 `/api/v1/files/report-exports/7001/download-url` 与 `/mock-download/report-88-v21.pdf?token=short` 都被实际访问。
- 回归：`npm run typecheck` 通过。

结论：

- 当前 `UC-04` 在报告详情页层面已经从“用户看到成功提示”推进到“用户点击导出后浏览器直接进入受控下载链”。
- 更大的生产级差距继续集中在高级企业版式、统一模板治理、PDF/SVG 更完整转换，以及更丰富的企业汇报组件，而不是前端下载闭环本身。

## 156. 2026-06-26 UC-06 Markdown 竖线表格结构化解析增强

本轮目标：在不直接扩张到重型 OCR 的前提下，继续提升 `UC-06` 对真实知识资料的结构化恢复能力，把常见的 Markdown / wiki 风格竖线表格也纳入 `Header=Value` 行摘要链路。

已完成：

- `backend/python-ai-service/app/document_processing/application/document_processor.py` 新增 Markdown 竖线表格识别：
  - 检测 `| Header | ... |` 表头行
  - 检测 `| --- | ---: |` 这类分隔行
  - 将后续每一行转成稳定的 `Header=Value` chunk
- 保持现有 CSV / TSV / semicolon CSV / 双层表头 / 重复列名 / 空列表头路径不变，仅在明确命中 Markdown 竖线表格时走新逻辑。
- `DocumentParseIndexingService` 无需额外改动，即可复用新 chunk 结构继续写入 `document_chunks`、embedding 与搜索索引。

验证证据：

- RED：`python -m pytest tests/unit/python/test_document_processor.py -k markdown_pipe_tables -q` 首次失败为 `assert 1 == 3`，证明 Markdown 表格先前只会整段落成 1 个 chunk。
- RED：`python -m pytest tests/unit/python/test_document_parse_indexing.py -k markdown_pipe_tables -q` 首次同样失败为 `chunks == 1`。
- GREEN：上述两条命令实现后分别通过。
- 回归：`python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py -q` 通过 `31 passed`，确认既有 CSV / XLSX 结构化能力没有被这次增强破坏。

结论：

- 当前 `UC-06` 已从“Office/CSV 表格可结构化”继续推进到“Markdown / wiki 风格竖线表格也可结构化”，更贴近知识库文档、设计说明和运营手册中的真实资料形态。
- 更大的生产级差距仍集中在高精度 OCR、复杂扫描件版面恢复、多栏理解和更复杂表格结构，而不是这类轻量表格文档本身。

## 157. 2026-06-26 UC-08 审批 SLA 与催办最小闭环

本轮目标：把 `UC-08` 从“可聚合、可筛选、可批准/驳回的审批待办中心”继续推进到“具备最小 SLA 元数据、逾期标记与催办动作”的更完整审批运营切片。

已完成：

- `RuleApprovalRecord` 新增 `slaHours/remindCount/lastRemindedAt` 字段，`pending(...)` 会承接 approval 节点的可选 `slaHours`，`remind(...)` 会累计催办次数并回写最近催办时间。
- `UserRepository` 新增 `findEnabledByRole(role)`，`JdbcUserRepository` 与 `InMemoryUserRepository` 均已实现按角色查询启用用户。
- 审批运营告警接收人已从单纯回退 `createdByUserId` 推进为：优先使用 approval 节点 `assigneeRole` 匹配到的启用用户；没有匹配用户时再回退 `createdByUserId/currentUserId`。
- 审批待办列表已补最小可见性边界：无 `rule:manage` 权限的审批用户在没有显式 `assigneeRole` 查询条件时，会默认按当前用户角色过滤；具备 `rule:manage` 的规则管理员保留跨角色运营全量视图。
- `RuleApplicationService` 的审批响应新增：
  - `slaHours`
  - `slaDueAt`
  - `isOverdue`
  - `remindCount`
  - `lastRemindedAt`
- `RuleController` 新增 `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/reminders`，仅允许对 `pending` 记录发起催办。
- `RuleApprovalSlaScheduler` 新增最小自动 SLA 到期扫描能力，默认由 `rule.approval.sla.scheduler.enabled=false` 关闭，可通过 `runOverdueApprovalsOnce()` 或定时任务扫描 `pending` 审批记录。
- 催办动作当前会：
  - 更新审批记录的 `remindCount/lastRemindedAt`
  - 写入 `rule_approval_reminder_requested` 审计
  - 创建 `rule_approval_reminder_requested` 未读告警
  - 使用 `dedupeKey=rule:{ruleId}:approval:{approvalRecordId}:reminder` 抑制同一审批记录重复催办产生多条未读告警；重复点击仍会累计 `remindCount` 并写入审计，保证操作证据不丢
  - 接收人优先解析 `assigneeRole` 下的启用用户，并在 payload 写入 `recipientSource=assigneeRole`；找不到时回退 `createdByUserId`
- 自动 SLA 到期扫描当前会：
  - 对已逾期的 `pending` 记录创建 `rule_approval_sla_overdue` 未读告警
  - 使用 `dedupeKey=rule:{ruleId}:approval:{approvalRecordId}:sla-overdue` 抑制重复未读告警
  - 与人工催办共用审批告警接收人解析逻辑，优先投递到 `assigneeRole` 对应启用用户
  - 写入 `rule_approval_sla_overdue` 审计
- `frontend/web-console/src/pages/rules/ApprovalInbox.vue` 已补：
  - `Overdue` 逾期标记
  - `SLA due ...`
  - `Reminder count ...`
  - `Last reminder ...`
  - `Remind` 按钮与成功反馈 `Reminder sent for this approval.`
- `frontend/web-console/src/pages/rules/RuleDesigner.vue` 的 approval 节点新增可选 `New approval SLA hours` 输入，保存后写回节点 `slaHours` 定义。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+remindsPendingApprovalRecordAndWritesAuditAndAlert+marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded" test` 首次失败，明确暴露 `slaHours/remindCount/lastRemindedAt/remindApprovalRecord` 缺口。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert" test` 首次失败为 `RuleApprovalSlaScheduler cannot be resolved to a type`，证明自动 SLA 到期扫描此前不存在。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit" test` 首次失败为同一审批记录连续催办生成 `2` 条未读 `rule_approval_reminder_requested` 告警，证明人工催办缺少基础告警节流。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesApprovalReminderAlertToEnabledAssigneeRoleUser" test` 首次失败为 `RuleApplicationService(..., InMemoryUserRepository) is undefined`，证明审批运营告警尚未接入用户仓储和真实审批人路由。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#nonManagersOnlyListApprovalRecordsForTheirRoles" test` 首次失败为 `finance_manager` 用户可看到 `2` 条跨角色 pending 待办，证明审批待办列表缺少按当前审批角色的最小可见性边界。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit" test` 当前已通过 `Tests run: 1, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#nonManagersOnlyListApprovalRecordsForTheirRoles" test` 当前已通过 `Tests run: 1, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesApprovalReminderAlertToEnabledAssigneeRoleUser" test` 当前已通过 `Tests run: 1, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesOverdueSlaAlertToEnabledAssigneeRoleUser" test` 当前已通过 `Tests run: 1, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert" test` 当前已通过 `Tests run: 1, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+remindsPendingApprovalRecordAndWritesAuditAndAlert+marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded+scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert" test` 当前已通过 `Tests run: 4, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+remindsPendingApprovalRecordAndWritesAuditAndAlert+dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit+marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded+scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert+rejectsPendingApprovalRecordAndPreventsRepeatedHandling+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit" test` 当前已通过 `Tests run: 7, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+remindsPendingApprovalRecordAndWritesAuditAndAlert+dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit+routesApprovalReminderAlertToEnabledAssigneeRoleUser+marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded+scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert+routesOverdueSlaAlertToEnabledAssigneeRoleUser+rejectsPendingApprovalRecordAndPreventsRepeatedHandling+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit" test` 当前已通过 `Tests run: 9, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+listsOnlyPendingApprovalRecordsAcrossRules+listsHandledApprovalRecordsByStatusAcrossRules+nonManagersOnlyListApprovalRecordsForTheirRoles+filtersApprovalRecordsByStatusRoleRuleCreatorApproverAndCreatedAt+remindsPendingApprovalRecordAndWritesAuditAndAlert+dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit+routesApprovalReminderAlertToEnabledAssigneeRoleUser+marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded+scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert+routesOverdueSlaAlertToEnabledAssigneeRoleUser+rejectsPendingApprovalRecordAndPreventsRepeatedHandling+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit" test` 当前已通过 `Tests run: 13, Failures: 0, Errors: 0`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreApplicationContextTest,PermissionApplicationServiceTest" test` 当前已通过 `Tests run: 12, Failures: 0, Errors: 0`，确认 Spring 注入与用户仓储接口变更未打断应用上下文和权限服务。
- GREEN：`npm run test -- src/api/apiContracts.test.ts` 当前已通过 `22 passed`，包含 `ruleApi.remindApprovalRecord()` 契约。
- RED：`npx playwright test tests/e2e/approval-inbox.spec.ts --grep "overdue approvals and sends a reminder"` 首次失败为 `Overdue` 断言命中标题歧义，修正为精确匹配后恢复通过。
- GREEN：同一 Playwright 命令当前已通过 `1 passed (7.3s)`，覆盖：
  - 逾期待办展示
  - `Reminder count 0 -> 1`
  - `Last reminder` 时间回显
  - `Remind` 按钮触发 `/reminders` 请求

结论：

- 当前 `UC-08` 的审批中心已经从“最小可查可批”推进到“具备最小 SLA 元数据、逾期提示、手动催办、催办未读告警去重、自动到期告警、基于 `assigneeRole` 的真实启用用户路由，以及非管理员按角色收敛待办可见性”的更完整运营面。
- 这次闭环仍刻意收敛在最小可交付范围：审批运营告警和待办可见性已经能基于 `assigneeRole` 形成单角色闭环，但尚未形成组织架构、多人会签/或签、委托代理、升级策略、升级告警节流或多级 SLA 策略。

后续闭合：

- 下一阶段若继续推进 `UC-08` 生产化，优先项会是：
  - 多审批人分配策略、会签/或签、委托代理与组织架构规则
  - SLA 升级策略、升级告警节流与多级 SLA 策略
  - 更完整的审批编排、子流程与可视化治理

## 158. 2026-06-26 UC-08 审批动作授权闭环

本轮目标：修复审批待办“普通审批人可看到自己的待办，但 API 层仍要求 `rule:manage` 导致不能处理”的权限断层，同时防止非目标角色用户越权批准或催办其他角色审批记录。

已完成：

- `RuleApplicationService.handleApprovalRecord()` 与 `remindApprovalRecord()` 已接入 `ensureApprovalRecordAccess(record)` 服务层业务守卫。
- 服务层守卫规则为：
  - 未设置当前用户的内部调用保持兼容；
  - 拥有 `rule:manage` 的规则管理员可运营全量审批记录；
  - 普通审批用户必须拥有审批记录 `assigneeRole` 对应角色，否则抛出 `SecurityException("approval record access denied: ...")`。
- `RuleController` 将以下审批动作入口从 `rule:manage` 调整为 `rule:debug`，与审批待办查询入口保持一致：
  - `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/actions`
  - `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/reminders`
  - `POST /api/v1/rules/approval-records/batch-actions`
- 批量审批继续复用逐条 `handleApprovalRecord(...)` 链路，因此每条记录都会经过同一服务层角色守卫。
- `ContractSurfaceTest` 新增控制器权限契约，防止后续再次把审批动作入口误收紧到 `rule:manage`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#assigneeRoleUserCanApproveOwnPendingApprovalRecord+assigneeRoleUserCannotHandleOtherRolePendingApprovalRecord" test` 首次失败为 `finance_manager` 用户可处理 `legal_manager` 审批记录，证明服务层缺少角色守卫。
- GREEN：同一服务层授权测试当前通过 `Tests run: 2, Failures: 0, Errors: 0`。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard" test` 首次失败，证明控制器动作入口仍是 `rule:manage`。
- GREEN：控制器权限契约当前通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+listsOnlyPendingApprovalRecordsAcrossRules+listsHandledApprovalRecordsByStatusAcrossRules+nonManagersOnlyListApprovalRecordsForTheirRoles+filtersApprovalRecordsByStatusRoleRuleCreatorApproverAndCreatedAt+remindsPendingApprovalRecordAndWritesAuditAndAlert+dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit+routesApprovalReminderAlertToEnabledAssigneeRoleUser+assigneeRoleUserCanApproveOwnPendingApprovalRecord+assigneeRoleUserCannotHandleOtherRolePendingApprovalRecord+marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded+scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert+routesOverdueSlaAlertToEnabledAssigneeRoleUser+rejectsPendingApprovalRecordAndPreventsRepeatedHandling+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit" test` 当前通过 `Tests run: 15, Failures: 0, Errors: 0`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard,ReportCoreApplicationContextTest,PermissionApplicationServiceTest" test` 当前通过 `Tests run: 13, Failures: 0, Errors: 0`。
- 前端契约：`npm run test -- src/api/apiContracts.test.ts` 当前通过 `22 passed`。

结论：

- `UC-08` 审批中心现在形成了“列表可见性按角色收敛、动作入口允许审批人调用、服务层逐条按 `assigneeRole` 拒绝越权”的最小生产权限闭环。
- 剩余生产级差距不在单记录动作授权，而在组织级审批人映射、多人会签/或签、委托代理、升级策略、多级 SLA 与独立审批流编排。

## 159. 2026-06-26 UC-06 OCR 对齐表格结构化解析增强

本轮目标：在不引入重型 OCR/版面理解服务的前提下，继续提升 `UC-06 / REQ-KB-002` 对扫描件 OCR 文本的可检索性，优先补齐“OCR 已抽取文本里存在多空格对齐表格”的轻量结构恢复。

已完成：

- `DocumentProcessor` 新增对齐空格表格候选识别：当标题后的多行文本均包含 `2+` 连续空格分隔的列，并且至少两行列数一致、列数不少于 2 时，才进入结构化表格链路。
- 对齐表格复用既有 `_locate_header_row()`、表头归一化、重复列名去歧义、空列表头补位和 `Header=Value` 行摘要 chunk 逻辑。
- 普通 CSV、分号 CSV、TSV、Markdown 竖线表格的优先路径保持不变；对齐空格只作为额外候选，避免普通段落或单空格文本被误判为表格。
- `DocumentParseIndexingService` 无需额外改动，即可把 OCR 对齐表格行摘要继续写入 `document_chunks`、embedding 和搜索索引。

验证证据：

- RED：`python -m pytest tests/unit/python/test_document_processor.py -k ocr_aligned_tables -q` 首次失败为 `assert 1 == 3`，证明扫描件对齐表格此前只会整段落成 1 个 chunk。
- RED：`python -m pytest tests/unit/python/test_document_parse_indexing.py -k ocr_aligned_tables -q` 首次失败同样为 `chunks == 1`，证明解析落库与索引链路此前没有结构化该类 OCR 表格。
- GREEN：上述两条命令实现后分别通过。
- 回归：`python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py -q` 当前通过 `33 passed`，确认既有 CSV/XLSX/Markdown/OCR 失败与成功路径未受影响。

结论：

- 当前 `UC-06` 的轻量表格结构恢复已从 Office、CSV、Markdown 表格继续扩展到“扫描件 OCR 文本中的多空格对齐表格”，能把类似发票、台账、清单中的 `Item Amount Owner` 行结构转成可检索、可引用的 `Header=Value` chunk。
- 这一步仍不是完整生产级 OCR 版面理解；剩余差距继续集中在高精度 OCR 置信度、图片内表格识别、复杂合并单元格恢复、多栏版式理解、扫描件版面重建和证据级坐标定位。

## 160. 2026-06-26 UC-04 PDF 导出 SVG Logo 矢量对象增强

本轮目标：把 `UC-04 / REQ-REPORT-004` 的企业 PDF 导出从“PNG/JPEG Logo 可嵌入，SVG Logo 只能回退品牌色块与公司名文本”推进到“常见 SVG Logo 也能进入 PDF 可绘制对象”，减少客户企业品牌规范交付时的可见落差。

已完成：

- `resolvePdfLogoAsset()` 现在会对 `image/svg+xml` 或 `.svg` Logo 生成 PDF Form XObject，而不是退回纯文本品牌标记。
- SVG 最小转换支持：
  - 读取 `<svg width/height>` 作为 PDF Form XObject `BBox`；
  - 读取 `fill="#RRGGBB"` 作为矢量矩形填充色；
  - 读取第一个 `<text>...</text>` 作为 PDF 文本内容；
  - 在页面资源中继续通过 `/XObject << /Im1 ... >>` 与 `/Im1 Do` 绘制。
- PDF 可读标记区分为：
  - `PDF-LOGO`：纯 fallback 品牌块；
  - `PDF-LOGO-BINARY`：PNG/JPEG 二进制图像；
  - `PDF-LOGO-VECTOR`：SVG 转 PDF Form XObject。
- PNG/JPEG Logo 原有 `/Subtype /Image` 路径保持不变，Word/PPT 的 SVG 媒体嵌入路径也保持不变。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#enterprisePdfExportRendersStoredSvgLogoAsPdfVectorObjectWhenAvailable" test` 首次失败为 PDF 中缺少 `/Subtype /Form`，且仍输出 `PDF-LOGO logoObjectKey=branding/contoso-logo.svg ...`，证明 SVG PDF Logo 此前没有进入可绘制对象。
- GREEN：同一命令实现后通过 `Tests run: 1, Failures: 0, Errors: 0`，断言 PDF 包含 `/Subtype /Form`、`/Im1`、真实 SVG 文本 `REAL-CONTOSO-LOGO` 与 `PDF-LOGO-VECTOR`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPdfDocument+enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable+exportsPdfAcrossMultiplePagesWhenBodyIsLong+exportsCurrentReportVersionAsEnterpriseWordDocument+exportsCurrentReportVersionAsPowerPointDocument" test` 通过 `Tests run: 5, Failures: 0, Errors: 0`，确认基础 PDF、PNG Logo PDF、多页 PDF、Word 与 PPT 导出未回退。

结论：

- 当前 `UC-04` 的 PDF 企业品牌导出不再只支持 PNG/JPEG 实物 Logo；常见简单 SVG Logo 也能作为 PDF 矢量对象参与输出，进一步收敛“企业品牌规范报告”交付缺口。
- 这仍是最小 SVG 子集转换，不是完整 SVG 渲染器；复杂 path、渐变、clipPath、外部字体、多层 transform 与真实模板引擎仍属于后续高级版式/统一模板治理范围。

## 161. 2026-06-26 UC-04 PPTX 企业封底页增强

本轮目标：把 `UC-04 / REQ-REPORT-004` 的 PPTX 企业导出从“封面 + 目录 + 章节页”的最小汇报结构，推进到“封面 + 目录 + 章节页 + 封底页”的更完整客户交付形态。

已完成：

- `pptSlides()` 的总页数从 `sections + 2` 调整为 `sections + 3`，最后追加 `Closing` slide。
- 封底页复用现有 `slideXml()`、Logo、Header、Footer、占位符和页脚页码逻辑，确保：
  - `ppt/slides/slideN.xml` 正常写入；
  - `ppt/slides/_rels/slideN.xml.rels` 继续引用 slide layout 与 `rIdLogo`；
  - `[Content_Types].xml`、`ppt/presentation.xml`、`ppt/_rels/presentation.xml.rels` 自动包含新增封底页；
  - 目录页仍指向第 3 页开始的章节页，封底页不干扰章节页码。
- 封底页正文包含 `Closing`、企业公司名、页眉和页脚文本，满足最小企业品牌收口页诉求。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsPowerPointDocument+exportsPowerPointAsOverviewAndSectionSlides" test` 首次失败为封面仍输出 `Page 1 of 4`，且 `[Content_Types].xml` 缺少 `/ppt/slides/slide5.xml`，证明 PPT 此前没有封底页。
- GREEN：同一命令实现后通过 `Tests run: 2, Failures: 0, Errors: 0`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument+exportsCurrentReportVersionAsPdfDocument+enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable+enterprisePdfExportRendersStoredSvgLogoAsPdfVectorObjectWhenAvailable+exportsPdfAcrossMultiplePagesWhenBodyIsLong+exportsCurrentReportVersionAsPowerPointDocument+exportsPowerPointAsOverviewAndSectionSlides+normalizesBrandThemeConsistentlyAcrossWordPdfAndPowerPointExports+appliesSharedEnterpriseLayoutAcrossWordPdfAndPowerPointExports" test` 当前通过 `Tests run: 9, Failures: 0, Errors: 0`，确认 Word/PDF/PPT 共享导出能力未回退。

结论：

- 当前 PPTX 导出已经从“章节承载骨架”继续接近真实企业汇报交付物，具备封面、目录、章节页和封底页的最小完整结构。
- 剩余生产级差距仍在真正的企业母版占位符治理、图表/表格专页、封底联系信息配置、统一模板源和高级版式规则，而不是基础页面结构本身。

## 162. 2026-06-26 UC-04 PPTX 封底页配置化增强

本轮目标：把 `UC-04 / REQ-REPORT-004` 的 PPTX 封底页从固定 `Closing` 文案推进到可由企业版式模板配置，减少客户交付时对封底标题、说明与联系方式的二次手工改稿。

已完成：

- `brand.layout` 新增 `closingTitle`、`closingMessage`、`closingContact` 三个可选字段。
- PPTX 最后一页标题由 `layout.closingTitle` 控制；未传入时仍保持默认 `Closing`，兼容既有导出。
- PPTX 最后一页正文优先输出 `closingMessage`、企业公司名、页眉与 `closingContact`；未传 `closingContact` 时继续回退既有页脚文本。
- `ExportLayout` 继续作为 Word/PDF/PPT 共享版式入口承接这些字段，避免把封底配置散落到单独 PPT 分支里。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsPowerPointClosingSlideFromEnterpriseLayout" test` 首次失败为封底页仍包含默认 `Closing`，且缺少 `Thank You`，证明封底此前不可配置。
- GREEN：同一命令实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#exportsCurrentReportVersionAsEnterpriseWordDocument+exportsCurrentReportVersionAsPdfDocument+enterprisePdfExportEmbedsStoredLogoObjectWhenAvailable+enterprisePdfExportRendersStoredSvgLogoAsPdfVectorObjectWhenAvailable+exportsPdfAcrossMultiplePagesWhenBodyIsLong+exportsCurrentReportVersionAsPowerPointDocument+exportsPowerPointAsOverviewAndSectionSlides+exportsPowerPointClosingSlideFromEnterpriseLayout+normalizesBrandThemeConsistentlyAcrossWordPdfAndPowerPointExports+appliesSharedEnterpriseLayoutAcrossWordPdfAndPowerPointExports" test` 当前通过 `Tests run: 10, Failures: 0, Errors: 0`。

结论：

- 当前 PPTX 企业导出已经具备“封面 + 目录 + 章节页 + 可配置封底页”的最小客户交付结构，封底标题、收口说明和联系方式可以随企业模板输入变化。
- 剩余生产级差距继续集中在企业母版占位符治理、图表/表格专页、统一模板源、高级版式规则，以及 Word/PDF/PPT 更一致的模板渲染引擎。

## 163. 2026-06-26 UC-06 OCR 键值表单结构化解析增强

本轮目标：继续提升 `UC-06 / REQ-KB-002` 对扫描件 OCR 文本的轻量结构恢复能力，把常见的“字段名 + 多空格 + 字段值”表单式资料转成可检索、可引用的 `Key=Value` 摘要 chunk。

已完成：

- `DocumentProcessor._chunk_text()` 新增表单摘要分支，在结构化表格解析前先识别轻量 OCR 表单。
- `_form_summary_chunks()` 仅在段落包含标题行且后续至少 2 行都能按 `2+` 连续空格拆成严格两列时启用，避免普通段落误判。
- 表单内容会输出为“标题 chunk + `Key=Value | Key=Value` 摘要 chunk”，并继续复用现有 embedding、PostgreSQL chunk、OpenSearch 与向量索引写入链路。
- 三列及以上的 OCR 对齐表格仍会落回既有 `_table_summary_chunks()` 路径，保持上轮表格结构化能力不变。

验证证据：

- RED：`python -m pytest tests/unit/python/test_document_processor.py -k ocr_forms -q` 首次失败为 `assert 3 == 2`，证明 OCR 表单此前按多行普通文本分成 3 个 chunk。
- RED：`python -m pytest tests/unit/python/test_document_parse_indexing.py -k ocr_forms -q` 首次同样失败为 `assert 3 == 2`，证明落库索引链路也没有表单摘要 chunk。
- GREEN：上述两条命令实现后均通过 `1 passed`。
- 回归：`python -m pytest tests/unit/python/test_document_processor.py tests/unit/python/test_document_parse_indexing.py -q` 当前通过 `35 passed`，确认 CSV/XLSX/Markdown/OCR 对齐表格、失败状态和索引链路未受影响。

结论：

- 当前 `UC-06` 的轻量扫描件结构恢复已覆盖 OCR 对齐表格与 OCR 键值表单两类常见资料形态，发票、合同摘要、供应商档案、审批单等文本抽取结果更容易进入 RAG 检索和引用。
- 这仍不是完整生产级 OCR/版面理解；剩余差距继续集中在 OCR 置信度、图片内表格识别、复杂合并单元格、多栏版式、坐标证据与扫描件版面重建。

## 164. 2026-06-26 UC-08 多人审批会签/或签最小闭环

本轮目标：把 `UC-08 / REQ-RULE-001` 的人工审批节点从单一 `assigneeRole` 扩展到多人审批，补齐原型和需求矩阵中“会签/或签”的基础业务闭环。

已完成：

- `approval` 节点现在支持 `assigneeRoles` 数组；保留 `assigneeRole` 兼容旧规则定义。
- `approvalMode` 支持：
  - `all`：每个审批角色生成一条 `RuleApprovalRecord`，所有同一 `runId + nodeId` 的审批记录均 `approved` 后才恢复下游动作。
  - `any`：每个审批角色生成一条 `RuleApprovalRecord`，第一条批准即可恢复下游动作；后续同组记录批准不会重复触发下游 `notify/create_task/webhook`。
- Debug trace 会输出 `assigneeRole`、`assigneeRoles`、`approvalMode`、`approvalTitle` 与 `pendingApproval`，让规则调试和后续文档/原型验收能看见多人审批语义。
- 生产运行遇到多人审批节点时仍复用现有审批记录表模型，一人一条记录，不新增数据库表结构，降低对 JDBC/内存仓储和既有审批待办页面的破坏面。
- 既有单人审批、审批提醒、SLA 逾期、角色权限过滤、批量审批与审批后恢复下游动作的旧链路保持兼容。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions" test` 首次失败为 `BusinessException`，证明规则校验此前拒绝 `assigneeRoles`。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenApprovalNodeHasAssigneeRoles_thenMarksRolesAndModeInTrace" test` 首次失败为 `BusinessException`，证明 debug trace 此前无法表达多人审批节点。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenApprovalNodeHasAssigneeRoles_thenMarksRolesAndModeInTrace,RuleApplicationServiceTest#approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions" test` 通过 `Tests run: 3, Failures: 0, Errors: 0`。
- 审批回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+stopsDownstreamActionsWhenProductionRunHitsApprovalNode+approvesPendingApprovalRecordAndWritesAudit+approvingPendingApprovalRecordResumesDownstreamActions+approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions+listsOnlyPendingApprovalRecordsAcrossRules+listsHandledApprovalRecordsByStatusAcrossRules+nonManagersOnlyListApprovalRecordsForTheirRoles+filtersApprovalRecordsByStatusRoleRuleCreatorApproverAndCreatedAt+remindsPendingApprovalRecordAndWritesAuditAndAlert+dedupesUnreadApprovalReminderAlertWhileKeepingReminderAudit+routesApprovalReminderAlertToEnabledAssigneeRoleUser+assigneeRoleUserCanApproveOwnPendingApprovalRecord+assigneeRoleUserCannotHandleOtherRolePendingApprovalRecord+marksPendingApprovalRecordAsOverdueWhenSlaIsExceeded+scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert+routesOverdueSlaAlertToEnabledAssigneeRoleUser+rejectsPendingApprovalRecordAndPreventsRepeatedHandling+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit" test` 通过 `Tests run: 20, Failures: 0, Errors: 0`。
- 领域回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest" test` 通过 `Tests run: 10, Failures: 0, Errors: 0`。

结论：

- 当前 `UC-08` 已具备最小可交付的多人审批网关能力：规则可配置多人角色、生产运行可创建多条待办、会签按全员通过恢复、或签按首个通过恢复且避免重复执行下游动作。
- 剩余生产级差距仍在组织级审批人自动映射、委托代理、审批组可视化状态、拒绝后的同组记录收敛策略、多级审批流、审批 SLA 升级策略，以及前端规则编排器对 `assigneeRoles/approvalMode` 的完整配置体验。

## 165. 2026-06-26 UC-08 规则编排器多人审批配置闭环

本轮目标：把上一节后端已支持的 `assigneeRoles/approvalMode` 暴露到前端规则编排器，避免多人审批能力停留在手写 JSON 或接口层。

已完成：

- `RuleDesigner.vue` 的 approval 节点新增“New approval assignee roles”输入，支持用逗号录入多个角色。
- 新增“New approval mode”下拉，支持选择 `all` 会签或 `any` 或签。
- 节点卡片、Vue Flow 节点摘要和保存 payload 均展示/写入 `approval {mode} {roles} -> {title}`。
- 保存规则定义时写入：
  - `assigneeRoles: ["finance_manager", "legal_manager"]`
  - `approvalMode: "all" | "any"`
  - 同时保留 `assigneeRole` 为首个角色，兼容旧列表展示与旧规则处理逻辑。

验证证据：

- RED：`npx playwright test tests/e2e/rule-engine.spec.ts` 首次失败在 `getByLabel('New approval assignee roles')` 超时，证明前端此前没有多人审批角色输入控件。
- GREEN：同一命令实现后通过 `1 passed`，覆盖新增 approval 节点、展示摘要、保存 canvas payload 中的 `assigneeRoles/approvalMode`。
- 类型检查：`npm run typecheck` 通过 `vue-tsc --noEmit`。

结论：

- 当前 `UC-08` 的多人审批不再只是后端能力，规则编排页面已经能配置会签/或签节点并保存到规则定义，原型主流程可进入真实多人审批配置闭环。
- 剩余生产级差距收敛为组织/人员目录映射、审批组聚合 UI、多人审批记录联动状态、拒绝后的同组收敛策略、委托代理、多级审批流与 SLA 升级策略。

## 166. 2026-06-26 UC-08 审批组聚合状态闭环

本轮目标：多人审批已能配置和执行后，继续补齐审批运营视角，让审批人和规则管理员能在待办列表中看见“这是一组会签/或签、共几人、已批几人、还差几人”。

已完成：

- 后端审批记录响应新增组状态字段：
  - `approvalGroupKey`
  - `approvalMode`
  - `assigneeRoles`
  - `approvalGroupTotalCount`
  - `approvalGroupApprovedCount`
  - `approvalGroupPendingCount`
- 组 key 按 `ruleId + runId + nodeId` 生成，复用现有一人一条 `RuleApprovalRecord` 模型，不新增表结构。
- 响应组状态会基于同组审批记录实时聚合；会签首人批准后列表可显示 `1/2 approved, 1 pending`。
- 审批待办页展示：
  - `Group all 1/2 approved, 1 pending`
  - `Group roles finance_manager, legal_manager`
- 旧单人审批记录仍按原来的字段显示，组字段不影响既有筛选、催办、批准、驳回和批量审批。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions" test` 首次失败为响应缺少 `approvalMode=all`，证明此前没有审批组响应契约。
- GREEN：同一 Java 测试实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- RED：`npx playwright test tests/e2e/approval-inbox.spec.ts --grep "shows pending approvals"` 首次失败为找不到 `Group all 1/2 approved, 1 pending`，证明前端此前没有审批组展示。
- GREEN：同一前端 E2E 实现后通过 `1 passed`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions+createsPendingApprovalRecordWhenProductionRunHitsApprovalNode+listsOnlyPendingApprovalRecordsAcrossRules+filtersApprovalRecordsByStatusRoleRuleCreatorApproverAndCreatedAt+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit" test` 通过 `Tests run: 6, Failures: 0, Errors: 0`。
- 前端回归：`npx playwright test tests/e2e/approval-inbox.spec.ts` 通过 `5 passed`；`npm run typecheck` 通过。

结论：

- 当前 `UC-08` 多人审批已从“能配置、能执行”推进到“运营列表可理解组进度”的更可交付状态，审批人能判断会签/或签模式、全组角色和当前通过进度。
- 剩余生产级差距继续集中在组织/人员目录映射、审批组批量联动处理、拒绝后的同组记录自动收敛、委托代理、多级审批流与 SLA 升级策略。

## 167. 2026-06-26 UC-08 拒绝后审批组自动收敛闭环

本轮目标：多人审批组中任一审批人驳回后，同一 `ruleId + runId + nodeId` 下的其他 `pending` 待办不能继续悬挂在审批中心，避免审批人继续处理已被组内驳回终止的审批任务。

已完成：

- 后端在 `handleApprovalRecord(... reject ...)` 分支中自动关闭同组其他 `pending` 审批记录。
- 自动关闭记录使用新状态 `closed`，并保留 `approvalComment=closed because approval group was rejected`，用于区分“人工驳回”和“系统收敛关闭”。
- 自动关闭 sibling 时写入 `rule_node_approval_closed` 审计，记录 `approvalRecordId/runId/nodeId/status/reason`。
- 审批组响应新增：
  - `approvalGroupRejectedCount`
  - `approvalGroupClosedCount`
- 全局审批记录查询状态白名单扩展为 `pending|approved|rejected|closed`。
- 审批待办页新增 `Closed` 状态切换，可查看自动关闭历史；组状态文案在存在拒绝/关闭记录时展示 `rejected/closed` 计数。
- 前端 API 类型和契约测试已允许 `status=closed`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup" test` 首次失败为响应缺少 `approvalGroupRejectedCount=1`，且同组 pending 仍未收敛。
- GREEN：同一 Java 目标测试实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- RED：`npx playwright test tests/e2e/approval-inbox.spec.ts --grep "shows reject fallback"` 首次失败为找不到 `Closed` 按钮，证明前端此前无法查看自动关闭历史。
- GREEN：同一前端 E2E 实现后通过 `1 passed`，覆盖 Closed tab、关闭原因、`Status closed` 和组内 rejected/closed 计数。
- Java 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup+approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions+rejectsPendingApprovalRecordAndPreventsRepeatedHandling+listsHandledApprovalRecordsByStatusAcrossRules+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit" test` 通过 `Tests run: 6, Failures: 0, Errors: 0`。
- 前端回归：`npx playwright test tests/e2e/approval-inbox.spec.ts` 通过 `5 passed`；`npm run typecheck` 通过。

结论：

- 当前 `UC-08` 多人审批已从“组状态可见”推进到“驳回后组内待办自动收敛”，审批中心不会继续悬挂已无业务意义的同组 pending 任务。
- 剩余生产级差距继续集中在组织/人员目录映射、审批组批量联动处理、委托代理、多级审批流与 SLA 升级策略。

## 168. 2026-06-26 UC-08 审批委托代理最小闭环

本轮目标：在不引入完整组织架构和代理规则表的前提下，先补齐审批节点级 `delegateRole`，让业务可配置“原审批角色临时/长期由代理角色处理”的最小生产闭环。

已完成：

- `approval` 节点支持可选 `delegateRole`。
- 生产运行生成 `RuleApprovalRecord` 时会把 `delegateRole` 作为待办快照保存，避免规则后续变更影响历史待办授权。
- 非管理员审批人仍只能处理本人角色待办；若当前用户不具备 `assigneeRole` 但具备该记录的 `delegateRole`，也允许处理该审批记录。
- 审批记录响应新增：
  - `delegateRole`
  - `handledByDelegate`
- 规则 debug trace 会输出 `delegateRole`，便于调试和验收时看见代理配置。
- PostgreSQL 迁移新增 `rule_approval_records.delegate_role`，JDBC insert/select/update 和 mapper 均已同步。
- 规则编排器新增 “New approval delegate role” 输入；approval 节点摘要和保存 payload 均包含代理角色。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRoleUserCanApproveDelegatedPendingApprovalRecord" test` 首次失败为 `SecurityException: approval record access denied`，证明此前代理角色不能处理原角色待办。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenApprovalNodeHasDelegateRole_thenMarksDelegateInTrace" test` 首次失败为 `expected: <finance_delegate> but was: <null>`，证明此前 trace 不暴露代理配置。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRoleUserCanApproveDelegatedPendingApprovalRecord,RuleDomainServiceTest#executeDebug_whenApprovalNodeHasDelegateRole_thenMarksDelegateInTrace" test` 通过 `Tests run: 2, Failures: 0, Errors: 0`。
- 前端 RED：`npx playwright test tests/e2e/rule-engine.spec.ts` 首次失败为找不到 `New approval delegate role` 输入框。
- 前端 GREEN：同一 E2E 实现后通过 `1 passed`，覆盖代理角色输入、节点摘要和保存 payload 的 `delegateRole`。
- 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRoleUserCanApproveDelegatedPendingApprovalRecord+assigneeRoleUserCanApproveOwnPendingApprovalRecord+assigneeRoleUserCannotHandleOtherRolePendingApprovalRecord+batchApprovesPendingApprovalRecordsAcrossRulesAndWritesAudit+rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup,RuleDomainServiceTest#executeDebug_whenApprovalNodeHasDelegateRole_thenMarksDelegateInTrace+executeDebug_whenApprovalNodeHasAssigneeRoles_thenMarksRolesAndModeInTrace" test` 通过 `Tests run: 7, Failures: 0, Errors: 0`。
- 类型检查：`npm run typecheck` 通过 `vue-tsc --noEmit`。

结论：

- 当前 `UC-08` 已从“多人审批组状态与驳回收敛”推进到“节点级委托代理可配置、可执行、可审计、可前端保存”的最小生产闭环。
- 剩余生产级差距继续集中在组织/人员目录映射、代理有效期/替班日历、审批组批量联动处理、多级审批流与 SLA 升级策略。

## 169. 2026-06-26 UC-08 审批代理有效期最小闭环

本轮目标：在节点级 `delegateRole` 已可配置和执行的基础上，补齐最小有效期窗口，避免代理角色永久生效，满足临时授权/替班审批的基础生产约束。

已完成：

- `approval` 节点新增可选 `delegateActiveFrom` 和 `delegateActiveTo`，使用 ISO-8601 时间字符串。
- 生产运行生成 `RuleApprovalRecord` 时会快照代理有效期窗口，避免规则后续修改影响历史待办授权。
- 非管理员审批人仍按 `assigneeRole` 优先授权；仅拥有 `delegateRole` 的用户只有在当前时间处于 `delegateActiveFrom..delegateActiveTo` 窗口内才可处理待办。
- 未配置窗口时保持原有 `delegateRole` 行为，兼容已有长期代理配置。
- 审批记录响应新增 `delegateActiveFrom/delegateActiveTo`，便于前端和审计核查代理窗口。
- 规则 debug trace 输出 `delegateActiveFrom/delegateActiveTo`，规则调试阶段即可看见代理窗口配置。
- PostgreSQL 迁移新增 `rule_approval_records.delegate_active_from/delegate_active_to`，JDBC insert/select/update 和 mapper 已同步。
- 规则编排器新增 “New approval delegate active from/to” 输入；approval 节点摘要和保存 payload 均包含代理有效期窗口。

验证证据：

- RED：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRoleUserCanApproveOnlyInsideDelegateActiveWindow+delegateRoleUserCannotApproveOutsideDelegateActiveWindow,RuleDomainServiceTest#executeDebug_whenApprovalNodeHasDelegateActiveWindow_thenMarksWindowInTrace" test` 首次有效失败为：过期代理仍可审批、响应缺少 `delegateActiveFrom`、trace 中窗口字段为 `null`。
- GREEN：同一 Java 目标测试实现后通过 `Tests run: 3, Failures: 0, Errors: 0`。
- 前端 RED：`npx playwright test tests/e2e/rule-engine.spec.ts` 首次失败为找不到 `New approval delegate active from` 输入框。
- 前端 GREEN：同一 E2E 实现后通过 `1 passed`，覆盖代理有效期输入、节点摘要和保存 payload 的 `delegateActiveFrom/delegateActiveTo`。
- 审批回归：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRoleUserCanApproveDelegatedPendingApprovalRecord+delegateRoleUserCanApproveOnlyInsideDelegateActiveWindow+delegateRoleUserCannotApproveOutsideDelegateActiveWindow+assigneeRoleUserCanApproveOwnPendingApprovalRecord+assigneeRoleUserCannotHandleOtherRolePendingApprovalRecord+rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup,RuleDomainServiceTest#executeDebug_whenApprovalNodeHasDelegateRole_thenMarksDelegateInTrace+executeDebug_whenApprovalNodeHasDelegateActiveWindow_thenMarksWindowInTrace" test` 通过 `Tests run: 8, Failures: 0, Errors: 0`。
- 类型检查：`npm run typecheck` 通过 `vue-tsc --noEmit`。

结论：

- 当前 `UC-08` 已从“节点级委托代理可配置、可执行”推进到“代理授权可按有效期收敛”的更贴近生产审批场景闭环。
- 剩余生产级差距继续集中在组织/人员目录映射、代理规则表/替班日历、审批组批量联动处理、多级审批流与 SLA 升级策略。

## 170. 2026-06-26 UC-08 审批中心代理窗口可视化闭环

本轮目标：后端已对 `delegateRole` 和代理有效期做授权控制后，补齐审批中心运营可见性，避免代理审批规则只存在于接口数据和审计中、业务人员在待办卡片上看不见代理授权边界。

已完成：

- 审批待办卡片展示 `Delegate {delegateRole}`。
- 当审批记录包含 `delegateActiveFrom/delegateActiveTo` 时，卡片展示 `Delegate window {from}..{to}`。
- 时间展示复用审批中心既有 `formatDate()`，与 Created/Handled/SLA due 保持一致。
- 未配置代理或代理窗口的普通审批记录不展示额外行，避免干扰既有单人/多人审批列表。

验证证据：

- RED：`npx playwright test tests/e2e/approval-inbox.spec.ts --grep "shows pending approvals"` 首次失败为找不到 `Delegate finance_delegate`，证明审批中心此前不可见代理信息。
- GREEN：同一 E2E 实现后通过 `1 passed`。
- 回归：`npx playwright test tests/e2e/approval-inbox.spec.ts` 通过 `5 passed`。
- 类型检查：`npm run typecheck` 通过 `vue-tsc --noEmit`。

结论：

- 当前 `UC-08` 的代理审批能力不只在后端可执行、可授权，也能在审批中心被运营人员直接审计代理角色和有效期窗口。
- 剩余生产级差距继续集中在组织/人员目录映射、代理规则表/替班日历、审批组批量联动处理、多级审批流与 SLA 升级策略。

## 171. 2026-06-26 UC-08 代理审批待办可见性闭环

本轮目标：补齐代理人从审批中心进入待办的入口。此前 `delegateRole` 已可授权处理记录，但非管理员审批中心列表默认只按 `assigneeRole` 收敛，代理人可能无法看到自己可处理的有效代理待办。

已完成：

- 审批记录查询过滤器新增当前用户可见角色集合和可见性判断时间。
- 非管理员审批中心列表现在按 `assigneeRole in 当前角色` 或 `delegateRole in 当前角色且代理窗口当前有效` 返回待办。
- 过期代理窗口的待办不会暴露给代理角色用户。
- 显式传入 `assigneeRole` 筛选时仍保持原来的精确角色筛选语义，避免破坏运营筛选。
- InMemory 与 JDBC 仓储查询逻辑已同步，真实 PostgreSQL 查询使用 `assignee_role IN (...) OR delegate_role IN (...)` 并检查 `delegate_active_from/delegate_active_to`。

验证证据：

- RED：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRoleUserListsOnlyActiveDelegatedApprovalRecords" test` 首次失败为 `expected: 2L but was: 1L`，证明代理用户只能看到自己角色待办，看不到有效代理待办。
- GREEN：同一目标测试实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 回归：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#nonManagersOnlyListApprovalRecordsForTheirRoles+delegateRoleUserListsOnlyActiveDelegatedApprovalRecords+delegateRoleUserCanApproveDelegatedPendingApprovalRecord+delegateRoleUserCanApproveOnlyInsideDelegateActiveWindow+delegateRoleUserCannotApproveOutsideDelegateActiveWindow+filtersApprovalRecordsByStatusRoleRuleCreatorApproverAndCreatedAt+assigneeRoleUserCanApproveOwnPendingApprovalRecord+assigneeRoleUserCannotHandleOtherRolePendingApprovalRecord" test` 通过 `Tests run: 8, Failures: 0, Errors: 0`。

结论：

- 当前 `UC-08` 的委托代理能力已从“能配置、能授权处理”推进到“代理人能在审批中心看到当前有效代理待办并进入处理”的完整入口闭环。
- 剩余生产级差距继续集中在组织/人员目录映射、代理规则表/替班日历、审批组批量联动处理、多级审批流与 SLA 升级策略。

## 172. 2026-06-26 UC-08 审批角色人员目录映射闭环

本轮目标：把审批中心从只展示 `assigneeRole/delegateRole` 的角色字符串，推进到能展示该角色在权限用户目录中解析出的启用审批人，方便业务人员确认“谁可以处理这条待办”。

已完成：

- 后端审批记录响应新增：
  - `assigneeUsers`
  - `delegateUsers`
- 人员映射复用现有 `UserRepository.findEnabledByRole(role)`，只返回启用用户，自动排除 disabled 用户。
- 响应中的人员对象只包含 `userId/username/displayName/role`，不暴露 `status`、密码或其他内部字段。
- 未配置用户仓储、角色为空或没有启用用户时返回空数组，不影响既有审批待办查询、审批授权和代理窗口逻辑。
- 审批待办页新增候选审批人和代理人展示：
  - `Candidate approvers Finance Approver (fin.approver)`
  - `Delegate users Finance Delegate (fin.delegate)`

验证证据：

- RED：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalRecordResponseIncludesEnabledAssigneeAndDelegateUsers" test` 首次有效失败为 `Expecting actual not to be null`，证明审批记录响应此前没有 `assigneeUsers` 人员映射。
- GREEN：同一 Java 目标测试实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 前端 RED：`npx playwright test tests/e2e/approval-inbox.spec.ts --grep "shows pending approvals"` 首次失败为找不到 `Candidate approvers Finance Approver (fin.approver)`。
- 前端 GREEN：同一 E2E 实现后通过 `1 passed`。

结论：

- 当前 `UC-08` 已从“角色可见、代理可见”推进到“角色背后的启用审批人可见”，审批中心具备最小组织人员目录映射能力。
- 剩余生产级差距继续集中在代理规则表/替班日历、审批组批量联动处理、多级审批流、SLA 升级策略，以及组织目录的部门/岗位/上级关系建模。

## 173. 2026-06-26 UC-08 或签审批组通过后自动收敛闭环

本轮目标：补齐 `approvalMode=any` 的审批组联动语义。此前或签首人批准后会恢复下游动作，但同组其他 `pending` 待办仍可悬挂在审批中心，容易造成重复处理和业务误判。

已完成：

- `approvalMode=any` 下，首个审批记录批准并恢复下游动作后，系统会自动关闭同组其他 `pending` 审批记录。
- 被关闭的同组记录状态为 `closed`，原因写入 `approvalComment=closed because approval group was approved by any assignee`。
- 被关闭记录不能再执行 approve/reject，继续复用既有 `approval record already handled` 守卫。
- 关闭动作写入 `rule_node_approval_closed` 审计，保留 `approvalRecordId/runId/nodeId/status/reason`。
- `approvalMode=all` 的会签语义保持不变，仍需所有审批角色批准后才恢复下游。
- 审批待办 E2E 增强：批准或签组中的一条待办后，切换到 Closed 状态可看到被系统关闭的同组记录和组状态 `Group any 1/2 approved, 0 pending, 1 closed`。

验证证据：

- RED：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions" test` 首次失败为同组 `legal_manager` 记录仍是 `status=pending`。
- GREEN：同一目标测试实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 前端：`npx playwright test tests/e2e/approval-inbox.spec.ts --grep "shows pending approvals"` 通过 `1 passed`，覆盖或签通过后的 Closed 历史可见性。

结论：

- 当前 `UC-08` 的或签审批不再只恢复下游动作，也会把同组剩余待办收敛为 closed，审批中心不会继续悬挂已被或签通过终止的待办。
- 剩余生产级差距继续集中在代理规则表/替班日历、多级审批流、SLA 升级策略、组织树选择器，以及更完整的审批流设计器。

## 174. 2026-06-26 UC-08 审批代理规则表最小闭环

本轮目标：把此前只能写在 approval 节点上的 `delegateRole/delegateActiveFrom/delegateActiveTo`，推进为可独立维护的全局代理规则表。这样业务可以配置“某个审批角色在某段时间由代理角色处理”，而不用修改每一条规则定义。

已完成：

- 新增领域模型 `RuleApprovalDelegateRule`，表达：
  - `assigneeRole`
  - `delegateRole`
  - `activeFrom/activeTo`
  - `status`
  - `reason`
  - `createdByUserId/createdAt`
- `RuleApplicationService.createApprovalDelegateRule()` 支持创建 enabled 代理规则，并校验：
  - `assigneeRole` 必填
  - `delegateRole` 必填
  - 代理角色不能等于原审批角色
  - `activeFrom <= activeTo`
- `RuleRepository` 新增代理规则保存和当前有效规则查询能力；`InMemoryRuleRepository` 与 `JdbcRuleRepository` 均已实现。
- 新增 Flyway 迁移 `V038__rule_approval_delegate_rules.sql`，创建 `rule_approval_delegate_rules` 表和按角色/状态/有效期的查询索引。
- 生产运行创建审批待办时：
  - 节点显式配置 `delegateRole` 时仍优先使用节点级快照。
  - 节点未配置代理时，自动查询当前有效的全局代理规则，并把 `delegateRole/activeFrom/activeTo` 快照到 `RuleApprovalRecord`。
  - 过期代理规则不会应用到新建待办。

验证证据：

- RED：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#activeDelegateRuleAppliesWhenApprovalNodeHasNoDelegateRole" test` 首次失败为 `createApprovalDelegateRule(...)` 未定义，证明此前没有独立代理规则表入口。
- GREEN：同一目标测试实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 边界回归：`./mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#activeDelegateRuleAppliesWhenApprovalNodeHasNoDelegateRole+expiredDelegateRuleDoesNotApplyToNewApprovalRecords" test` 通过 `Tests run: 2, Failures: 0, Errors: 0`。

结论：

- 当前 `UC-08` 已从“节点级代理配置”推进到“全局代理规则表可驱动新建审批待办”的最小生产闭环。
- 剩余生产级差距继续集中在代理规则前端管理页、替班日历、组织树选择器、多级审批流和 SLA 升级策略。

## 175. 2026-06-26 UC-08 审批代理规则管理入口最小闭环

本轮目标：把已存在的全局代理规则创建能力，从服务层补齐到 REST API、前端 API 契约和页面操作入口，避免代理规则只能由后端测试或内部调用创建。

已完成：

- `RuleController` 新增 `POST /api/v1/rules/approval-delegate-rules`。
- 该入口使用 `@RequiresPermission("rule:manage")`，与规则管理配置类能力保持一致，不放到普通审批待办动作权限中。
- 前端 `ruleApi.createApprovalDelegateRule()` 调用 `/rules/approval-delegate-rules`，请求体保持 JSON：
  - `assigneeRole`
  - `delegateRole`
  - `activeFrom`
  - `activeTo`
  - `reason`
- 新增 `/rules/delegate-rules` 页面，可从侧边菜单 `Approval delegates` 进入。
- 页面支持填写原审批角色、代理角色、有效期和原因，提交成功后展示 `Delegate rule created: {assigneeRole} -> {delegateRole}` 与规则状态。
- `App.vue` 导航壳从历史乱码恢复为可读 UTF-8 中文菜单，并保留新增代理规则入口。

验证证据：

- 后端 RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalDelegateRuleCreationUsesManagePermission" test` 首次失败为缺少 `@PostMapping("/approval-delegate-rules")`。
- 后端 GREEN：同一目标测试实现后通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 前端 API RED：`npm run test -- src/api/apiContracts.test.ts` 首次失败为 `ruleApi.createApprovalDelegateRule is not a function`。
- 前端 API GREEN：同一命令实现后通过 `23 passed`。
- 前端 E2E RED：`npx playwright test tests/e2e/approval-delegate-rules.spec.ts` 首次失败为找不到菜单项 `Approval delegates`。
- 前端 E2E GREEN：同一 E2E 实现后通过 `1 passed`。
- 回归验证：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalDelegateRuleCreationUsesManagePermission+ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard,RuleApplicationServiceTest#activeDelegateRuleAppliesWhenApprovalNodeHasNoDelegateRole+expiredDelegateRuleDoesNotApplyToNewApprovalRecords+approvalRecordResponseIncludesEnabledAssigneeAndDelegateUsers+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions" test` 通过 `Tests run: 6, Failures: 0, Errors: 0`。
- 前端回归：`npm run typecheck` 通过；`npx playwright test tests/e2e/approval-inbox.spec.ts tests/e2e/approval-delegate-rules.spec.ts` 通过 `6 passed`。

结论：

- 当前 `UC-08` 已具备“页面创建全局审批代理规则 -> 后端保存 -> 后续新建审批待办自动应用有效代理规则”的最小管理闭环。
- 剩余生产级差距继续集中在代理规则列表、停用/编辑、替班日历、组织树选择器、多级审批流、SLA 升级策略，以及更完整的审批流设计器。

## 176. 2026-06-26 UC-08 审批代理规则列表与停用最小闭环

本轮目标：把全局审批代理规则从“只能创建”推进到“可运营查看、可停用，并且停用后不再影响新建审批待办”的最小闭环。

已完成：

- `RuleController` 新增 `GET /api/v1/rules/approval-delegate-rules`，支持按 `status/assigneeRole` 过滤并分页返回代理规则。
- `RuleController` 新增 `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/disable`，继续由 `@RequiresPermission("rule:manage")` 保护。
- `RuleApplicationService.listApprovalDelegateRules()` 与 `disableApprovalDelegateRule()` 已落地，停用会把规则状态改为 `disabled` 并保留停用原因。
- `InMemoryRuleRepository` 与 `JdbcRuleRepository` 均支持代理规则按 ID 查询、筛选分页、计数和更新。
- 新建审批待办只应用 `enabled` 且当前有效的代理规则；已停用规则不会再快照到新的审批记录。
- 前端 `ruleApi` 新增列表与停用接口契约。
- `/rules/delegate-rules` 页面进入后自动加载代理规则列表；创建成功后刷新列表；每条 enabled 规则可填写停用原因并点击 `Disable` 停用，页面即时显示 `Status disabled`。

验证证据：

- 后端目标 GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsAndDisablesApprovalDelegateRulesForOperations,ContractSurfaceTest#ruleApprovalDelegateRuleOperationsUseManagePermission" test` 通过 `Tests run: 2, Failures: 0, Errors: 0`。
- 前端 API GREEN：`npm run test -- src/api/apiContracts.test.ts` 通过 `24 passed`。
- 前端 E2E GREEN：`npx playwright test tests/e2e/approval-delegate-rules.spec.ts` 通过 `1 passed`。
- 后端相关回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalDelegateRuleCreationUsesManagePermission+ruleApprovalDelegateRuleOperationsUseManagePermission+ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard,RuleApplicationServiceTest#listsAndDisablesApprovalDelegateRulesForOperations+activeDelegateRuleAppliesWhenApprovalNodeHasNoDelegateRole+expiredDelegateRuleDoesNotApplyToNewApprovalRecords+approvalRecordResponseIncludesEnabledAssigneeAndDelegateUsers+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions" test` 通过 `Tests run: 8, Failures: 0, Errors: 0`。
- 前端类型检查：`npm run typecheck` 通过。
- 前端审批相关回归：`npx playwright test tests/e2e/approval-inbox.spec.ts tests/e2e/approval-delegate-rules.spec.ts` 通过 `6 passed`。

结论：

- 当前 `UC-08` 的全局审批代理规则已具备“创建 -> 列表运营查看 -> 停用 -> 后续新待办不再应用”的最小生产闭环。
- 剩余生产级差距继续集中在代理规则编辑/重新启用、替班日历、组织树/岗位选择器、多级审批流、SLA 升级策略、完整审批流设计器，以及真实后端浏览器 smoke 对该页面的补充。

## 177. 2026-06-26 UC-08 审批代理规则重新启用最小闭环

本轮目标：补齐全局审批代理规则的可逆运营动作。上一轮已经支持停用，但业务代理规则常见场景是临时关闭后再次恢复；如果只能停用不能重新启用，运营人员会被迫重复创建规则，造成规则表膨胀和审计口径不清。

已完成：

- `RuleApprovalDelegateRule` 新增 `enable(reason)` 状态切换，保留原规则 ID、审批角色、代理角色、有效期和创建人信息。
- `RuleApplicationService.enableApprovalDelegateRule()` 支持把 disabled 代理规则恢复为 `enabled`，并更新运营原因。
- `RuleController` 新增 `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/enable`，继续使用 `@RequiresPermission("rule:manage")`。
- 前端 `ruleApi.enableApprovalDelegateRule()` 固化启用接口契约。
- `/rules/delegate-rules` 页面在 disabled 规则卡片上展示 `Enable reason` 和 `Enable` 操作，成功后显示 `Delegate rule enabled: {assigneeRole} -> {delegateRole}`，并把卡片状态更新为 `Status enabled`。
- 重新启用后，后续新建审批待办会再次应用该全局代理规则，证明状态切换影响真实业务创建链路，而不只是页面字段变化。

验证证据：

- 后端 RED：目标测试首次失败为 `enableApprovalDelegateRule(...)` 未定义，合同测试首次失败为缺少 `@PostMapping("/approval-delegate-rules/{delegateRuleId}/enable")`。
- 前端 API RED：`npm run test -- src/api/apiContracts.test.ts` 首次失败为 `ruleApi.enableApprovalDelegateRule is not a function`。
- 后端 GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#reEnablesApprovalDelegateRulesForOperations,ContractSurfaceTest#ruleApprovalDelegateRuleOperationsUseManagePermission" test` 通过 `Tests run: 2, Failures: 0, Errors: 0`。
- 前端 API GREEN：`npm run test -- src/api/apiContracts.test.ts` 通过 `24 passed`。
- 前端 E2E GREEN：`npx playwright test tests/e2e/approval-delegate-rules.spec.ts` 通过 `1 passed`。
- 后端相关回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalDelegateRuleCreationUsesManagePermission+ruleApprovalDelegateRuleOperationsUseManagePermission+ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard,RuleApplicationServiceTest#listsAndDisablesApprovalDelegateRulesForOperations+reEnablesApprovalDelegateRulesForOperations+activeDelegateRuleAppliesWhenApprovalNodeHasNoDelegateRole+expiredDelegateRuleDoesNotApplyToNewApprovalRecords+approvalRecordResponseIncludesEnabledAssigneeAndDelegateUsers+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions" test` 通过 `Tests run: 9, Failures: 0, Errors: 0`。
- 前端类型检查：`npm run typecheck` 通过。
- 前端审批相关回归：`npx playwright test tests/e2e/approval-inbox.spec.ts tests/e2e/approval-delegate-rules.spec.ts` 通过 `6 passed`。

结论：

- 当前 `UC-08` 的全局审批代理规则已具备“创建 -> 列表 -> 停用 -> 重新启用 -> 后续新待办恢复应用”的可逆运营闭环。
- 剩余生产级差距继续集中在代理规则编辑、替班日历、组织树/岗位选择器、多级审批流、SLA 升级策略、完整审批流设计器，以及真实后端浏览器 smoke 对代理规则页的补充。

## 178. 2026-06-26 UC-08 审批代理规则编辑最小闭环

本轮目标：补齐全局审批代理规则的编辑能力。此前代理规则已经能创建、停用、重新启用，但如果审批角色、代理角色或有效期填错，只能停用后重新创建，既影响运营效率，也让审计轨迹变得碎片化。

已完成：

- `RuleApprovalDelegateRule` 新增 `update(...)`，保留原规则 ID、状态、创建人和创建时间，只调整审批角色、代理角色、有效期和原因。
- `RuleApplicationService.updateApprovalDelegateRule()` 使用与创建入口一致的校验规则：
  - `assigneeRole` 必填
  - `delegateRole` 必填
  - 代理角色不能等于原审批角色
  - `activeFrom <= activeTo`
- `RuleController` 新增 `PUT /api/v1/rules/approval-delegate-rules/{delegateRuleId}`，继续使用 `@RequiresPermission("rule:manage")`。
- 前端 `ruleApi.updateApprovalDelegateRule()` 固化 PUT 契约。
- `/rules/delegate-rules` 页面在每条规则卡片上提供 `Edit`，进入编辑态后可修改：
  - `Edit assignee role`
  - `Edit delegate role`
  - `Edit active from`
  - `Edit active to`
  - `Edit reason`
- 保存成功后页面显示 `Delegate rule updated: {assigneeRole} -> {delegateRole}`，并更新卡片中的角色、窗口和原因。
- 编辑后的 enabled 规则会影响后续新建审批待办：原审批角色不再应用旧代理，新审批角色会应用更新后的代理角色。

验证证据：

- 后端 RED：目标测试首次失败为 `updateApprovalDelegateRule(...)` 未定义，合同测试首次失败为缺少 `@PutMapping("/approval-delegate-rules/{delegateRuleId}")`。
- 前端 API RED：`npm run test -- src/api/apiContracts.test.ts` 首次失败为 `ruleApi.updateApprovalDelegateRule is not a function`。
- 后端 GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#updatesApprovalDelegateRulesForOperations,ContractSurfaceTest#ruleApprovalDelegateRuleOperationsUseManagePermission" test` 通过 `Tests run: 2, Failures: 0, Errors: 0`。
- 前端 API GREEN：`npm run test -- src/api/apiContracts.test.ts` 通过 `24 passed`。
- 前端 E2E GREEN：`npx playwright test tests/e2e/approval-delegate-rules.spec.ts` 通过 `1 passed`。
- 后端相关回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalDelegateRuleCreationUsesManagePermission+ruleApprovalDelegateRuleOperationsUseManagePermission+ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard,RuleApplicationServiceTest#listsAndDisablesApprovalDelegateRulesForOperations+reEnablesApprovalDelegateRulesForOperations+updatesApprovalDelegateRulesForOperations+activeDelegateRuleAppliesWhenApprovalNodeHasNoDelegateRole+expiredDelegateRuleDoesNotApplyToNewApprovalRecords+approvalRecordResponseIncludesEnabledAssigneeAndDelegateUsers+approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions" test` 通过 `Tests run: 10, Failures: 0, Errors: 0`。
- 前端类型检查：`npm run typecheck` 通过。
- 前端审批相关回归：`npx playwright test tests/e2e/approval-inbox.spec.ts tests/e2e/approval-delegate-rules.spec.ts` 通过 `6 passed`。

结论：

- 当前 `UC-08` 的全局审批代理规则已具备“创建 -> 列表 -> 编辑 -> 停用 -> 重新启用 -> 后续新待办按最新配置应用”的基础运营闭环。
- 剩余生产级差距继续集中在替班日历、组织树/岗位选择器、多级审批流、SLA 升级策略、完整审批流设计器，以及真实后端浏览器 smoke 对代理规则页的补充。

## 179. 2026-06-26 UC-08 审批代理规则真实后端浏览器烟测闭环

本轮目标：把上一轮“代理规则编辑最小闭环”从本地 mock E2E 推进到真实 Java 容器 + PostgreSQL + 浏览器 Playwright 验收，确保 P3 smoke bundle 可以复现代理规则创建、编辑、停用、重新启用的完整运营链路。

已完成：

- `frontend/web-console/tests/e2e/approval-delegate-rules-real-backend.spec.ts` 覆盖真实后端 `/rules/delegate-rules` 页面：
  - 创建全局审批代理规则。
  - 编辑 `assigneeRole/delegateRole/activeFrom/activeTo/reason`。
  - 停用规则并填写停用原因。
  - 重新启用规则并填写启用原因。
  - 通过真实 API 查询断言最终 `assigneeRole/delegateRole/activeFrom/activeTo/status/reason`。
- `scripts/p3-local-smoke-lib.mjs` 的 P3 smoke bundle 已包含 `uc08-delegate-rules-real-backend-e2e`，与 UC-13 Dashboard、UC-07 数据源同步、UC-08 审批中心真实后端 E2E 一起顺序执行。
- 修复真实 PostgreSQL 暴露的 JDBC 缺陷：`JdbcRuleRepository.updateApprovalDelegateRule()` 现在会更新 `assignee_role/delegate_role/active_from/active_to/status/reason`，不再只更新状态和原因。
- 修复本地 Flyway 校验基线：`V034__rule_approval_records.sql` 回到审批记录基础表职责，避免与后续 SLA 字段迁移混杂导致已部署本地库 checksum 不一致。
- 重新构建并替换 `ir-java-smoke`，使用 `SPRING_PROFILES_ACTIVE=dev` 连接本地 Docker PostgreSQL、RocketMQ、MinIO；`/actuator/health` 返回 `{"status":"UP"}`。

验证证据：

- RED：真实后端 Playwright 初次失败，页面编辑后仍显示旧角色；数据库查询确认仅 `reason` 更新，`assignee_role/delegate_role/active_from/active_to` 未更新。
- RED：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#updatesApprovalDelegateRuleFieldsInPostgres" test` 首次失败为 `assigneeRole` 未更新。
- GREEN：同一 PostgreSQL 集成测试通过 `Tests run: 1, Failures: 0, Errors: 0`。
- Docker：`docker build -f Dockerfile.java -t intelligent-report-system-java-report-core .` 构建成功；替换 `ir-java-smoke` 后健康检查返回 `{"status":"UP"}`，Flyway 校验 38 个迁移且 schema 当前版本为 038。
- 真实后端 E2E：`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/approval-delegate-rules-real-backend.spec.ts` 通过 `1 passed`。
- P3 bundle：`node scripts/p3-local-smoke.mjs` 通过，完成 `uc13-real-backend-e2e`、`uc07-real-backend-e2e`、`uc08-real-backend-e2e`、`uc08-delegate-rules-real-backend-e2e`，共 7 条真实后端浏览器用例通过。
- 回归：`node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` 通过 `1 pass`；`npm run typecheck` 通过；`npx playwright test tests/e2e/approval-delegate-rules.spec.ts` 通过 `1 passed`。

结论：

- 当前 `UC-08` 的全局审批代理规则已经具备真实后端浏览器验收证据，P3 smoke bundle 可自动复现“创建 -> 编辑 -> 停用 -> 重新启用 -> API 校验持久化字段”的闭环。
- 剩余生产级差距继续集中在替班日历、组织树/岗位选择器、多级审批流、SLA 升级策略和完整审批流设计器；这些属于更高阶业务能力，不阻塞当前代理规则表基础运营链路的真实后端验收。

## 180. 2026-06-26 UC-09 分享链路审计证据闭环

本轮目标：补齐 UC-09 外部分享访问的真实 PostgreSQL 审计证据。此前真实浏览器 E2E 已验证外部用户可通过分享密码只读访问报告、错误密码返回 403、允许下载时获取短期 URL，但缺少数据库级断言证明 `share_access_failed/share_report_view/share_export_download` 三类审计记录真实落库且可追溯。

已完成：

- `PermissionApplicationService.sharedExportDownloadUrl()` 不再在下载授权校验刚通过时提前写成功审计，而是在找到导出文件并生成短期 URL 后写入 `share_export_download`。
- 分享下载成功审计的 `detail` 现在包含：
  - `visitor`
  - `shareToken`
  - `reportId`
  - `exportFileId`
  - `downloadPolicy=share_presigned_url`
- 分享访问相关审计的 `actor_user_id` 统一绑定 `shareLink.createdBy()`，避免外部匿名访问因没有 `CurrentUser` 而落成默认用户 `1L`。
- 新增 PostgreSQL 集成测试 `JdbcReportGenerationTaskRepositoryPostgresIT#writesShareViewAndDownloadAuditEvidenceForExternalAccessInPostgres`，模拟外部无登录态访问：
  - 错误密码访问只读报告，断言 `share_access_failed/result=failed/reason=invalid_password/visitor=external@example.com`。
  - 正确密码查看只读报告，断言 `share_report_view/result=succeeded/visitor=external@example.com`。
  - 正确密码获取分享导出下载 URL，断言 `share_export_download/result=succeeded/exportFileId/downloadPolicy`。
  - 三类审计均断言 `resource_type=share_link`、`resource_id=shareLinkId`、`actor_user_id=createdBy`、`detail.shareToken` 和 `detail.reportId`。
- `scripts/p1-local-smoke-lib.mjs` 的 P1 smoke bundle 新增 `uc09-share-audit-postgres-it`，让 UC-09 的真实浏览器链路和真实 PostgreSQL 审计链路一起进入本地可重复验收。
- `scripts/local-smoke-runner.mjs` 抽出本地 smoke 命令执行器，Windows 下会将 `npm` 和 `.cmd` wrapper 转为 `cmd.exe /d /s /c ...`，修复 P1 smoke 新增 Maven wrapper 后在 Windows 上 `spawn EINVAL` 的执行问题。

验证证据：

- RED：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#writesShareViewAndDownloadAuditEvidenceForExternalAccessInPostgres" test` 首次失败，数据库实际 `actor_user_id=1`，且 `share_export_download` 的 `detail.exportFileId/downloadPolicy` 为空。
- GREEN：同一 PostgreSQL 集成测试通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 分享相关 PostgreSQL 回归：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#protectsPasswordShareAndWritesAccessAuditInPostgres+persistsShareDownloadPolicyAndCreatesSharedDownloadUrlInPostgres+writesShareViewAndDownloadAuditEvidenceForExternalAccessInPostgres" test` 通过 `Tests run: 3, Failures: 0, Errors: 0`。
- 分享服务单测回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest" test` 通过 `Tests run: 11, Failures: 0, Errors: 0`。
- P1 bundle 契约：`node --test tests/unit/node/p1_local_smoke_bundle.test.mjs` 通过 `1 pass`，确认 P1 smoke 已包含 `uc09-share-audit-postgres-it`。
- Smoke runner 回归：`node --test tests/unit/node/local_smoke_runner.test.mjs tests/unit/node/p1_local_smoke_bundle.test.mjs` 通过 `3 pass`；`node --test tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/p2_local_smoke_bundle.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs` 通过 `4 pass`。
- P1 全量 smoke：`node scripts/p1-local-smoke.mjs` 通过，顺序完成 `uc03-real-backend-e2e`、`uc09-real-backend-e2e`、`uc09-share-audit-postgres-it`、`uc12-real-backend-e2e`；其中 UC-09 审计 PostgreSQL 步骤返回 `Tests run: 1, Failures: 0, Errors: 0`。

结论：

- 当前 `UC-09` 不再只证明“外部页面可访问、下载 URL 可返回”，还具备真实 PostgreSQL 审计证据：失败访问、只读查看、导出下载三类事件均可按分享链接、报告、访问者、所有者和导出文件回溯。
- 分享链接管理页的撤销、复制和授权开关基础体验已在第 182 节闭合，外部访问水印已在第 183 节闭合；剩余生产级差距继续集中在更细粒度的分享有效范围控制。

## 181. 2026-06-26 UC-09 分享访问限流闭环

本轮目标：补齐外部分享访问的基础撞库防护。此前 UC-09 已具备密码校验、失败审计、只读详情、受控下载和真实 PostgreSQL 审计证据，但同一外部 visitor 可以无限尝试分享密码，仍不够接近客户生产安全边界。

已完成：

- `PermissionApplicationService` 在 `/share-links/{shareToken}/access`、`/share-links/{shareToken}/report`、`/share-links/{shareToken}/exports/{exportFileId}/download-url` 三个分享访问入口统一执行限流检查。
- 限流规则：同一 `share_link` + 同一 `visitor` 在最近 15 分钟内出现 5 次 `share_access_failed` 且 `reason=invalid_password` 后，后续访问会被拒绝，即使第 6 次提交正确密码也会返回 `SecurityException("share access rate limited: ...")`。
- 限流审计：触发限流时写入 `share_access_rate_limited`，`detail` 包含 `reason=too_many_invalid_password_attempts`、`visitor`、`failedAttempts`、`windowMinutes`、`shareToken` 和 `reportId`，`actor_user_id` 仍绑定分享创建人，便于客户按报告责任人回溯。
- `AuditRepository` 新增按 `resource_type/resource_id/operation_type/detail` 和时间窗口计数的默认查询；`JdbcAuditRepository` 使用 PostgreSQL JSONB `detail ->> ? = ?` 下推统计，避免在生产库全量拉取审计日志。
- `scripts/p1-local-smoke-lib.mjs` 的 `uc09-share-audit-postgres-it` 已扩展为同时运行分享审计证据与分享限流 PostgreSQL 集成测试。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#rateLimitsRepeatedInvalidSharePasswordAttemptsByVisitor" test` 首次失败为连续 5 次错误密码后，正确密码仍可访问，说明原实现没有限流。
- GREEN：同一单测通过 `Tests run: 1, Failures: 0, Errors: 0`。
- PostgreSQL 证据：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#rateLimitsRepeatedInvalidSharePasswordAttemptsInPostgres" test` 通过 `Tests run: 1, Failures: 0, Errors: 0`，验证 `share_access_rate_limited` 真实落库，且 `reason/visitor/failedAttempts/windowMinutes` 可查。
- 分享服务回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest" test` 通过 `Tests run: 12, Failures: 0, Errors: 0`。
- 分享相关 PostgreSQL 回归：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#protectsPasswordShareAndWritesAccessAuditInPostgres+persistsShareDownloadPolicyAndCreatesSharedDownloadUrlInPostgres+writesShareViewAndDownloadAuditEvidenceForExternalAccessInPostgres+rateLimitsRepeatedInvalidSharePasswordAttemptsInPostgres" test` 通过 `Tests run: 4, Failures: 0, Errors: 0`。
- 真实分享浏览器回归：`RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18082 npm run e2e:real-backend -- tests/e2e/share-real-backend.spec.ts` 通过 `1 passed`，确认正常外部分享访问和下载路径未被误伤。
- P1 bundle 契约：`node --test tests/unit/node/local_smoke_runner.test.mjs tests/unit/node/p1_local_smoke_bundle.test.mjs` 通过 `3 pass`。

结论：

- 当前 `UC-09` 已从“分享可访问、可下载、可审计”进一步推进到“基础撞库防护可验证”，外部分享密码错误尝试会被按 visitor 窗口限流并形成审计证据。
- 分享链接管理页的撤销、复制和授权开关基础体验已在第 182 节闭合，外部访问水印已在第 183 节闭合；当时剩余生产级差距集中在 IP/User-Agent 维度风控、验证码/挑战机制，以及更细粒度的分享有效范围控制，其中 IP/User-Agent 维度风控已在第 184 节闭合。

## 182. 2026-06-26 UC-09 分享链接管理页基础运营闭环

本轮目标：补齐报告 owner 在报告详情页创建、复制和撤销分享链接的基础运营体验。此前 UC-09 已具备外部分享访问、下载授权、审计证据和限流，但 owner 侧仍缺少可操作入口，客户验收时需要离开页面或依赖接口才能管理分享链接。

已完成：

- `frontend/web-console/src/pages/reports/ReportDetail.vue` 新增“分享链接管理”面板：
  - 可输入分享密码。
  - 可填写分享有效期。
  - 可勾选允许外部下载。
  - 创建后展示当前分享链接和状态。
  - 支持复制分享链接到剪贴板。
  - 支持撤销当前分享链接并刷新状态。
- `frontend/web-console/src/api/shareApi.ts` 新增 owner 侧 `revokeShareLink()` API，并把 `createShareLink()` 响应类型扩展为完整分享链接摘要。
- `backend/java-report-core/src/main/java/com/company/report/permission/interfaces/rest/PermissionController.java` 暴露 `POST /api/v1/share-links/{shareToken}/revoke`。
- `backend/java-report-core/src/main/java/com/company/report/shared/security/JwtAuthenticationFilter.java` 收窄公开分享路径白名单：仅 `/access`、`/report` 和 `/exports/{id}/download-url` 跳过 JWT；`/revoke` 必须走认证和 `report:share` 权限，避免管理动作被匿名外部访问绕过。
- `frontend/web-console/tests/e2e/report-generation.spec.ts` 新增报告详情分享管理 Playwright 用例，覆盖创建 payload、链接展示、复制和撤销。
- `backend/java-report-core/src/test/java/com/company/report/contract/ContractSurfaceTest.java` 新增分享管理 surface 断言，锁定撤销路由权限和 JWT 白名单边界。

验证证据：

- RED：`npm run test -- apiContracts` 首次失败为 `shareApi.revokeShareLink is not a function`。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#shareManagementExposesOwnerCreateAndRevokeEndpoints" test` 首次失败为缺少 `@PostMapping("/share-links/{shareToken}/revoke")`。
- RED：`npm run e2e -- report-generation.spec.ts --grep "创建、复制和撤销分享链接"` 首次失败为报告详情页找不到“分享密码”输入框，说明 owner 侧管理面板不存在。
- GREEN：`npm run test -- apiContracts` 通过 `25 passed`。
- GREEN：`npm run e2e -- report-generation.spec.ts --grep "创建、复制和撤销分享链接"` 通过 `1 passed`。
- 报告详情相关前端回归：`npm run e2e -- report-generation.spec.ts --grep "报告详情"` 通过 `5 passed`。
- 前端类型检查：`npm run typecheck` 通过。
- 后端分享与安全回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#shareManagementExposesOwnerCreateAndRevokeEndpoints,PermissionApplicationServiceTest" test` 通过 `Tests run: 13, Failures: 0, Errors: 0`。
- 追加安全 surface 回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#shareManagementExposesOwnerCreateAndRevokeEndpoints" test` 通过 `Tests run: 1, Failures: 0, Errors: 0`，确认 JWT filter 没有继续按 `path.startsWith("/api/v1/share-links/")` 粗放跳过全部分享路径。

结论：

- 当前 `UC-09` 已补齐报告详情页 owner 侧基础分享运营闭环：创建、复制、撤销、下载授权配置均可从页面完成，并有前端 E2E、API 契约和后端安全 surface 证据。
- 当时剩余生产级差距继续集中在 IP/User-Agent 维度风控、验证码/挑战机制，以及更细粒度分享有效范围控制；其中 IP/User-Agent 维度风控已在第 184 节闭合。

## 183. 2026-06-26 UC-09 外部访问水印闭环

本轮目标：补齐外部分享只读页面的基础防泄露水印。此前 UC-09 已具备密码、撤销、下载授权、限流、审计和 owner 侧管理入口，但外部用户打开报告后页面上没有可见水印，不利于客户生产分享场景中的责任提示和截图传播约束。

已完成：

- `frontend/web-console/src/pages/share/ShareAccess.vue` 在外部分享报告打开后渲染固定背景水印。
- 水印内容包含：
  - `外部只读`
  - `报告 {reportId}`
  - `{shareToken}`
- 水印设置 `pointer-events: none`，不拦截正文浏览或下载按钮点击。
- 水印层位于页面内容下方，正文、下载区、错误提示和空状态保持可读。
- `frontend/web-console/tests/e2e/share-and-permission.spec.ts` 在外部只读详情用例中新增水印断言，确认水印随分享报告响应出现且包含 `shareToken/reportId`。

验证证据：

- RED：`npm run e2e -- share-and-permission.spec.ts --grep "外部用户只能查看分享报告只读详情"` 首次失败为找不到 `external-share-watermark`，说明页面原本没有水印。
- GREEN：同一目标用例通过 `1 passed`。
- 分享与权限前端回归：`npm run e2e -- share-and-permission.spec.ts` 通过 `7 passed`，确认水印没有影响过期、密码错误、只读详情、允许下载、拒绝下载、无权限管理页和用户批量导入禁用路径。
- 前端类型检查：`npm run typecheck` 通过。

结论：

- 当前 `UC-09` 外部分享页面已具备基础可见水印，水印包含报告和分享链接追踪信息，并通过浏览器 E2E 验证不会影响只读访问和下载交互。
- 当时剩余生产级差距继续集中在 IP/User-Agent 维度风控、验证码/挑战机制，以及更细粒度分享有效范围控制；其中 IP/User-Agent 维度风控已在第 184 节闭合。

## 184. 2026-06-26 UC-09 分享访问 IP/User-Agent 风控闭环

本轮目标：补齐外部分享访问的基础风险上下文，避免只按 visitor 维度限流，导致同一客户端更换 visitor 后继续撞库。

已完成：

- `PermissionController` 在 `/share-links/{shareToken}/access`、`/report`、`/exports/{exportFileId}/download-url` 三个外部分享入口统一注入风险上下文。
- 客户端 IP 提取顺序为 `X-Forwarded-For` 首个 IP、`X-Real-IP`、`remoteAddr`；`User-Agent` 从请求头读取，覆盖 body 中不可信的同名字段。
- `PermissionApplicationService` 在分享失败、成功、下载和限流审计详情中保留 `clientIp`、`userAgent` 和 `riskFingerprint`。
- 限流策略保持原 visitor 维度兼容，同时新增 `riskFingerprint=sha256(clientIp|userAgent)` 维度；同一 IP+UA 即使 visitor 不同，15 分钟内 5 次密码错误后也会触发短时拒绝。
- PostgreSQL 集成测试已覆盖 `operation_logs.detail ->> 'riskFingerprint'` 的真实 JSONB 查询统计，P1 smoke 的 `uc09-share-audit-postgres-it` 已收编该用例。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest#rateLimitsRepeatedInvalidSharePasswordAttemptsByRiskFingerprint test` 首次失败为第 6 次同一 IP+UA、不同 visitor 的正确密码访问没有被拦截。
- GREEN：同一目标用例通过 `Tests run: 1, Failures: 0, Errors: 0`。
- RED：`.\mvnw.cmd -pl backend/java-report-core -Dtest=ContractSurfaceTest#externalShareAccessEnrichesRiskContextFromRequestHeaders test` 首次失败为控制器缺少 `HttpServletRequest` 和头部提取。
- GREEN：同一契约用例通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 真实 PostgreSQL 回归：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#rateLimitsRepeatedInvalidSharePasswordAttemptsByRiskFingerprintInPostgres" test` 通过 `Tests run: 1, Failures: 0, Errors: 0`，Flyway 验证 38 个迁移且数据库为 PostgreSQL 16.14。

结论：

- 当前 `UC-09` 分享访问已具备 visitor + IP/User-Agent 双维度基础撞库防护，并能在审计中回溯外部访问风险上下文。
- 剩余生产级差距继续集中在验证码/挑战机制，以及更细粒度分享有效范围控制。

## 185. 2026-06-26 UC-09 分享限流 HTTP 语义闭环

本轮目标：把分享访问限流从普通权限拒绝中区分出来，为后续验证码/挑战机制和网关策略提供稳定 HTTP 边界。

已完成：

- `GlobalExceptionHandler` 对 `share access rate limited` 前缀的 `SecurityException` 返回 HTTP `429`。
- 响应 body 使用 `code=429`、`message=share access rate limited`，普通分享密码错误和其他权限拒绝继续保持 HTTP `403`。
- `ShareAccess.vue` 将 `share access rate limited` 映射为面向外部用户的中文提示“尝试次数过多，请稍后再试”，避免把内部英文错误直接暴露给客户访问者。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=GlobalExceptionHandlerTest#mapsShareRateLimitSecurityExceptionToTooManyRequestsResponse" test` 首次失败为期望 `429` 但实际 `403`。
- GREEN：同一目标用例通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 异常处理回归：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=GlobalExceptionHandlerTest" test` 通过 `Tests run: 4, Failures: 0, Errors: 0`，确认普通 403、404、409 没有回退。
- 前端 RED/GREEN：`npm run e2e -- share-and-permission.spec.ts --grep "分享访问触发限流"` 首次失败为页面未出现“尝试次数过多，请稍后再试”，补充映射后通过 `1 passed`。

结论：

- 当前分享访问限流已有可被前端、Higress 或后续 CAPTCHA/挑战策略识别的 HTTP 429 边界。
- 剩余生产级差距继续集中在真正的验证码/挑战交互，以及更细粒度分享有效范围控制。

## 186. 2026-06-26 UC-09 分享下载格式范围控制闭环

本轮目标：补齐外部分享下载的细粒度范围控制，避免 owner 一旦开启 `allowDownload` 就把该报告所有已完成导出文件都暴露给外部访问者。

已完成：

- `ShareLink` 新增 `allowedDownloadFormats`，空列表保持历史兼容，表示不限制格式。
- 新增 Flyway `V039__share_link_download_format_scope.sql`，在 `share_links` 上增加 `allowed_download_formats JSONB` 和 GIN 索引。
- `PermissionApplicationService` 创建分享时读取 `allowedDownloadFormats`，分享详情只返回允许格式的导出摘要，下载短链接口二次校验导出文件格式。
- 格式不在允许范围内时写入 `share_download_denied`，审计详情包含 `reason=download_format_not_allowed`、`exportFileId` 和 `format`。
- 报告详情页 owner 侧分享面板在开启外部下载后可选择 Markdown/PDF/Word/PPT 允许格式，创建分享 payload 会提交 `allowedDownloadFormats`。
- P1 smoke 的 `uc09-share-audit-postgres-it` 已收编真实 PostgreSQL 格式范围用例。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#shareDownloadCanBeLimitedToAllowedExportFormats" test` 首次失败为分享详情返回了 Markdown 和 PDF 两个导出文件，证明缺少格式范围过滤。
- GREEN：同一目标用例通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 真实 PostgreSQL RED/GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsShareDownloadFormatScopeAndEnforcesItInPostgres" test` 首次暴露 JDBC 仓储未适配新字段；补充 V039 和 JSONB 读写后通过 `Tests run: 1, Failures: 0, Errors: 0`，Flyway 成功迁移到 v039。
- 前端 RED/GREEN：`npm run e2e -- report-generation.spec.ts --grep "创建、复制和撤销分享链接"` 首次失败为找不到“允许下载格式 Markdown”控件；补充格式多选后通过 `1 passed`。

结论：

- 当前 `UC-09` 已具备分享下载格式范围控制：外部详情不会展示未授权格式，直接调用未授权导出下载接口也会被拒绝并审计。
- 剩余生产级差距继续集中在验证码/挑战交互，以及单次链接等更高阶分享范围控制。

## 188. 2026-06-26 UC-09 分享访问邮箱与域名范围闭环

本轮目标：补齐外部分享链接的 visitor 范围控制，让 owner 可以限制可访问邮箱或邮箱域名，降低分享链接被非目标外部人员转发访问的风险。

已完成：

- `ShareLink` 新增 `allowedVisitors` 和 `allowedVisitorDomains`，空列表表示不限制，保持历史兼容。
- 新增 Flyway `V041__share_link_visitor_scope.sql`，在 `share_links` 上增加 `allowed_visitors` 与 `allowed_visitor_domains` JSONB 字段和 GIN 索引。
- `PermissionApplicationService` 创建分享时读取 `allowedVisitors/allowedVisitorDomains`；外部只读报告浏览成功前校验 `visitor`，允许精确邮箱或匹配邮箱域名。
- visitor 不在范围内时写入 `share_access_scope_denied` 审计，详情包含 `reason=visitor_not_allowed`、`visitor`、`shareToken` 和 `reportId`。
- 报告详情页 owner 侧分享面板新增“允许访问邮箱”和“允许访问域名”输入，创建分享 payload 会提交数组形式的 `allowedVisitors/allowedVisitorDomains`。
- P1 smoke 的 `uc09-share-audit-postgres-it` 已收编真实 PostgreSQL visitor 范围用例。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#shareAccessCanBeScopedToAllowedVisitorsAndDomains" test` 首次失败为非法 `intruder@evil.com` 未被拒绝。
- GREEN：同一目标用例通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 真实 PostgreSQL：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsShareVisitorScopeAndEnforcesItInPostgres" test` 通过 `Tests run: 1, Failures: 0, Errors: 0`，Flyway 成功迁移到 v041。

结论：

- 当前 `UC-09` 已具备分享访问邮箱/域名范围控制：目标邮箱和目标域名可访问，非授权 visitor 会被拒绝并形成 PostgreSQL 审计证据。
- 验证码/挑战交互已在第 190 节补齐最小闭环。

## 190. 2026-06-26 UC-09 分享访问挑战机制闭环

本轮目标：补齐外部分享访问在连续密码错误后的挑战机制，避免仅靠最终限流兜底，给后续接入真实 CAPTCHA、Higress 策略或第三方风控留出稳定后端边界。

已完成：

- `PermissionApplicationService` 在同一 visitor 或同一 IP/User-Agent 风险指纹 15 分钟内累计 3 次密码错误后，要求提交文本挑战 `challengeAnswer=REPORT`。
- 未提交或答错挑战时抛出 `share access challenge required`，`GlobalExceptionHandler` 将其映射为 HTTP `428`，区别于普通 `403` 和硬限流 `429`。
- 挑战失败写入 `share_access_challenge_required` 审计，详情包含 `reason=challenge_required`、`challengeType=text`、`challengePrompt=Type REPORT to continue`、`failedAttempts`、`windowMinutes` 以及 visitor/risk 上下文。
- 原 5 次密码错误硬限流仍保留，challenge 是限流前的交互式门槛，不替代最终拒绝策略。
- 外部分享页在收到 `share access challenge required` 后展示“访问验证”输入，提交 challenge 后可继续访问只读报告。
- P1 smoke 的 `uc09-share-audit-postgres-it` 已收编真实 PostgreSQL challenge 用例。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#requiresChallengeAfterRepeatedInvalidSharePasswordAttemptsByVisitor" test` 首次失败为第 4 次正确密码访问未抛出 challenge。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=GlobalExceptionHandlerTest#mapsShareChallengeSecurityExceptionToPreconditionRequiredResponse" test` 首次失败为期望 `428` 但实际 `403`。
- RED：`npm run e2e -- share-and-permission.spec.ts --grep "触发挑战"` 首次失败为页面未显示 challenge 提示。
- GREEN：上述三个目标用例均已通过。

结论：

- 当前 `UC-09` 已具备分享访问挑战机制的最小生产闭环：连续错误后先要求 challenge，答对后继续原密码校验，未答对会拒绝并形成审计证据。
- 后续若接入真实 CAPTCHA 服务或 Higress 风控，可复用 `share access challenge required` / HTTP `428` 边界和 `share_access_challenge_required` 审计事件。

## 189. 2026-06-26 UC-09 单次分享链接闭环

本轮目标：补齐外部分享链接的单次访问策略，避免敏感报告链接在首次成功浏览后继续被转发复用。

已完成：

- `ShareLink` 新增 `singleUse`，默认 `false` 保持历史兼容。
- 新增 Flyway `V042__share_link_single_use.sql`，在 `share_links` 上增加 `single_use` 字段和状态/单次策略索引。
- `PermissionApplicationService` 创建分享时读取 `singleUse`，外部只读报告浏览成功前统计已有 `share_report_view` 成功记录；`singleUse=true` 且已成功浏览过时拒绝后续浏览。
- 单次链接已消费时写入 `share_access_single_use_consumed` 审计，详情包含 `reason=single_use_consumed`、`accessCount`、`shareToken`、`reportId` 和 visitor 风险上下文。
- 报告详情页 owner 侧分享面板新增“单次访问链接”开关，创建分享 payload 会提交 `singleUse`。
- P1 smoke 的 `uc09-share-audit-postgres-it` 已收编真实 PostgreSQL 单次分享用例。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#singleUseShareCanOnlyBeViewedOnce" test` 首次失败为第二次访问未抛出 `share single use already consumed`。
- GREEN：同一目标用例通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 真实 PostgreSQL：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsSingleUseShareAndEnforcesItInPostgres" test` 通过 `Tests run: 1, Failures: 0, Errors: 0`，Flyway 成功迁移到 v042。

结论：

- 当前 `UC-09` 已具备单次分享链接控制：外部只读报告首次成功浏览后，后续浏览会被拒绝并形成 PostgreSQL 审计证据。
- 剩余生产级差距继续集中在验证码/挑战交互。

## 187. 2026-06-26 UC-09 分享最大访问次数闭环

本轮目标：补齐外部分享链接的最大访问次数控制，避免同一分享链接在密码泄露或转发后被无限次浏览。

已完成：

- `ShareLink` 新增 `maxAccessCount`，`0` 表示不限制，保持历史兼容。
- 新增 Flyway `V040__share_link_max_access_count.sql`，在 `share_links` 上增加 `max_access_count` 字段和状态/次数索引。
- `PermissionApplicationService` 创建分享时读取 `maxAccessCount`，外部只读报告浏览成功前统计该分享链接已有 `share_report_view` 成功次数；达到上限时拒绝访问。
- 超过访问次数时写入 `share_access_limit_exceeded` 审计，详情包含 `reason=max_access_count_exceeded`、`maxAccessCount`、`accessCount`、`shareToken` 和 `reportId`。
- 报告详情页 owner 侧分享面板新增“最大访问次数”数字输入，创建分享 payload 会提交正整数 `maxAccessCount`。
- P1 smoke 的 `uc09-share-audit-postgres-it` 已收编真实 PostgreSQL 最大访问次数用例。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#shareAccessCanBeLimitedByMaximumViewCount" test` 首次失败为第二次访问未抛出 `share access count exceeded`，证明缺少访问次数限制。
- GREEN：同一目标用例通过 `Tests run: 1, Failures: 0, Errors: 0`。
- 真实 PostgreSQL：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsShareMaxAccessCountAndEnforcesItInPostgres" test` 通过 `Tests run: 1, Failures: 0, Errors: 0`，Flyway 成功迁移到 v040。
- 前端合约与类型：`npm run test -- apiContracts` 通过 `25 passed`；`npm run typecheck` 通过。
- 前端 E2E：`npm run e2e -- report-generation.spec.ts --grep "创建、复制和撤销分享链接"` 通过 `1 passed`，确认 owner 侧可配置最大访问次数并进入请求体。

结论：

- 当前 `UC-09` 已具备分享最大访问次数控制：只读报告浏览会按成功访问次数限制，超过上限会被拒绝并形成 PostgreSQL 审计证据。
- 剩余生产级差距继续集中在验证码/挑战交互，以及访问域名/邮箱、单次链接等更高阶分享范围控制。

## 191. 2026-06-26 UC-08 审批代理替班星期最小闭环

本轮目标：把全局审批代理规则从“只按有效期生效”推进到“可按星期生效”的最小替班日历闭环，解决代理规则在非排班日仍会被应用的问题。

已完成：

- `RuleApprovalDelegateRule` 新增 `activeWeekdays` 字段，空列表保持历史兼容，表示每天生效。
- 创建和编辑审批代理规则时解析 `activeWeekdays`，统一归一化为 Java `DayOfWeek.name()` 格式，并拒绝非法星期值。
- `InMemoryRuleRepository.findActiveApprovalDelegateRule()` 与 `JdbcRuleRepository.findActiveApprovalDelegateRule()` 均按当前日期星期过滤代理规则。
- PostgreSQL 新增 Flyway `V043__rule_approval_delegate_active_weekdays.sql`，在 `rule_approval_delegate_rules` 上持久化 `active_weekdays JSONB`。
- 前端 `/rules/delegate-rules` 新增创建和编辑输入项 `Delegate active weekdays` / `Edit active weekdays`，支持以逗号录入 `MONDAY,WEDNESDAY`，列表卡片展示 `Weekdays ...`。
- 前端 API 契约和真实后端 E2E 均已扩展 `activeWeekdays`，避免页面、契约和后端持久化脱节。

验证证据：

- RED：`RuleApplicationServiceTest#delegateRuleOnlyAppliesOnConfiguredWeekdays` 首次失败为非匹配星期仍应用了 `delegateRole`。
- RED：`approval-delegate-rules.spec.ts` 首次失败为页面找不到 `Delegate active weekdays`。
- RED：PostgreSQL 集成测试首次暴露 JSONB `?` 操作符被 JDBC 识别为占位符；改用 `jsonb_exists(active_weekdays, ?)` 后通过。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` 通过 62 个测试。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#updatesApprovalDelegateRuleFieldsInPostgres+activeWeekdayDelegateRuleDoesNotApplyOnUnmatchedDayInPostgres" test` 通过 2 个真实 PostgreSQL 测试，Flyway 验证 43 个迁移。
- GREEN：`npm run test -- apiContracts` 通过 25 个测试；`npm run e2e -- approval-delegate-rules.spec.ts` 通过 1 个 E2E；`npm run typecheck` 通过；`node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` 通过 1 个测试。

结论：

- 当前 `UC-08` 的审批代理规则已补齐 `activeWeekdays` 最小替班日历能力：代理规则可按星期窗口生效，非匹配星期不会影响新建审批待办。
- 剩余生产级差距继续集中在完整排班日历 UI、组织树/岗位选择器、多级审批流、SLA 升级策略和完整审批流设计器；这些属于更高阶审批治理能力，不阻塞当前 `activeWeekdays` 最小闭环。

## 192. 2026-06-26 UC-08 审批组织目录字段最小闭环

本轮目标：把审批角色背后的人员目录从“只有用户和角色”推进到“可携带部门/岗位信息”，并在审批代理规则页给出按角色匹配的候选审批人提示，减少业务管理员盲填角色字符串的风险。

已完成：

- `UserAccount` 新增 `department` 与 `position` 字段，旧的 `UserAccount.enabled(username, displayName, roles)` 保持兼容，默认部门/岗位为空。
- `PermissionApplicationService.addUser()`、`batchImportUsers()`、`users()` 响应均支持部门/岗位字段。
- `JdbcUserRepository` 对 `user_accounts.department` 与 `user_accounts.position` 完成插入、更新、查询和 `findEnabledByRole()` 读取。
- 新增 Flyway `V044__user_account_organization_fields.sql`，为 `user_accounts` 增加部门/岗位列和启用用户部门岗位索引。
- 前端 `adminApi` 用户类型与批量导入契约支持 `department/position`。
- 审批代理规则页 `/rules/delegate-rules` 会读取 `/api/v1/users`，并在填写 `assigneeRole/delegateRole` 时展示匹配角色的候选人、部门和岗位。

验证证据：

- RED：`PermissionApplicationServiceTest#createsListsAndUpdatesUsersFromRepositoryState+batchImportsUsersAndReportsDuplicatesWithoutOverwritingExistingAccounts` 首次因 `UserAccount.department()/position()` 不存在失败。
- RED：`npm run e2e -- approval-delegate-rules.spec.ts` 首次因找不到 `Assignee candidates Fiona Manager / Finance Center / Finance Manager` 失败。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#createsListsAndUpdatesUsersFromRepositoryState+batchImportsUsersAndReportsDuplicatesWithoutOverwritingExistingAccounts" test` 通过 2 个测试。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest" test` 通过 18 个测试。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsUsersAndRbacMatrixInPostgres" test` 通过 1 个真实 PostgreSQL 测试，Flyway 从 v043 迁移到 v044 并验证 44 个迁移。
- GREEN：`npm run e2e -- approval-delegate-rules.spec.ts` 通过 1 个 E2E；`npm run test -- apiContracts` 通过 25 个测试；`npm run typecheck` 通过。

结论：

- 当前 `UC-08` 已具备最小组织目录字段能力：审批相关页面可以从真实用户目录读取部门/岗位信息，并按角色展示候选审批人与代理人。
- 剩余生产级差距继续集中在真正的组织树/岗位选择器组件、部门层级/上级关系建模、完整排班日历 UI、多级审批流、SLA 升级策略和完整审批流设计器。

## 193. 2026-06-26 UC-08 审批代理组织角色选择器闭环

本轮目标：在已具备用户部门/岗位字段和候选人提示的基础上，把审批代理规则页从“只能手填角色字符串”推进到“可从组织目录推导出的角色选项中选择”，降低业务管理员录入错误。

已完成：

- `/rules/delegate-rules` 创建表单新增 `Assignee organization role` 与 `Delegate organization role` 选择器。
- 编辑表单新增 `Edit assignee organization role` 与 `Edit delegate organization role` 选择器。
- 选择器复用现有 `/api/v1/users` 数据，不新增后端表和接口；只使用启用用户的 `department/position/roles/displayName` 推导去重角色选项。
- 选项文案格式为 `部门 / 岗位 / 角色 / 人员`，选择后直接回填原有 `assigneeRole/delegateRole` 字段，保持 API 契约不变。
- 原手填输入与候选人提示保留，支持目录未覆盖角色时继续手动录入。

验证证据：

- RED：`npm run e2e -- approval-delegate-rules.spec.ts` 首次失败在找不到 `Assignee organization role`，证明页面没有组织角色选择器。
- GREEN：同一 E2E 通过 `1 passed`，覆盖选择组织角色回填 `finance_manager`、候选人提示、创建/编辑/停用/启用主链路，以及编辑态两个组织角色选择器可见。
- GREEN：`npm run test -- apiContracts` 通过 25 个测试，确认 API 契约未因选择器变更漂移。
- GREEN：`npm run typecheck` 通过，确认 Vue 3 + TypeScript 类型检查通过。

结论：

- 当前 `UC-08` 的审批代理规则页已具备最小组织角色选择能力：管理员可以从用户目录推导出的部门/岗位/角色选项选择审批角色和代理角色，同时保留手工兜底。
- 剩余生产级差距继续集中在真正的组织树/部门层级/上级关系建模、完整排班日历 UI、多级审批流、SLA 升级策略和完整审批流设计器。

## 194. 2026-06-26 UC-08 规则编排器审批组织角色选择器闭环

本轮目标：把上一轮审批代理规则页的组织目录选角能力同步到规则编排器，避免 approval 节点仍只能手填审批角色和代理角色，导致规则设计与运营配置体验不一致。

已完成：

- `RuleDesigner.vue` 的 approval 节点新增 `New approval assignee organization role` 选择器。
- `RuleDesigner.vue` 的 approval 节点新增 `New approval delegate organization role` 选择器。
- 选择器复用原有 `adminApi.listUsers({ page: 1, pageSize: 20 })` 启用用户目录，不新增后端接口。
- 审批角色选择器会把选中的角色追加到 `New approval assignee roles`，保留多人会签/或签录入能力。
- 代理角色选择器会直接回填 `New approval delegate role`，保留手工录入兜底。
- 选项文案统一为 `部门 / 岗位 / 角色 / 人员`，和审批代理规则页保持一致。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败在找不到 `New approval assignee organization role`，证明规则编排器没有组织角色选择器。
- GREEN：同一 E2E 通过 `1 passed`，覆盖从组织目录选择 `finance_manager` 回填审批角色、代理角色目录选项可见、approval 节点保存 payload 仍包含 `assigneeRoles/delegateRole/delegateActiveFrom/delegateActiveTo`。
- GREEN：`npm run test -- apiContracts` 通过 25 个测试，确认 API 合同不变。
- GREEN：`npm run typecheck` 通过，确认 Vue 3 + TypeScript 类型检查通过。

结论：

- 当前 `UC-08` 的规则设计器和审批代理规则页都已具备最小组织角色选择能力，业务人员可以在主要审批配置入口看到部门/岗位/角色/人员上下文，而不是完全依赖裸角色字符串。
- 剩余生产级差距继续集中在真正的组织树/岗位字典/上级关系建模、完整排班日历 UI、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 195. 2026-06-29 UC-08 只读组织目录 API 最小闭环

本轮目标：把前端从 `/users` 临时推导组织角色的过渡状态，推进到后端提供稳定的只读组织目录契约，为后续组织树选择器、岗位字典和审批流配置打基础。

已完成：

- `PermissionApplicationService.organizationDirectory()` 基于启用用户聚合只读组织目录，返回 `departments` 与 `roles` 两个视图。
- `departments` 按部门、岗位分组，岗位下包含去重角色和不含密码/内部字段的用户摘要。
- `roles` 按角色分组，返回角色关联的部门、岗位和用户摘要。
- 禁用用户不会进入组织目录，避免已停用账号继续出现在审批选角和代理配置候选集中。
- `PermissionController` 新增 `GET /api/v1/organization-directory`，使用 `permission:read` 权限保护。
- 前端 `adminApi.organizationDirectory()` 与 `OrganizationDirectory` 类型已固化，后续页面可直接读取正式目录契约。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#buildsOrganizationDirectoryFromEnabledUsers" test` 首次因 `organizationDirectory()` 未定义失败。
- GREEN：同一后端目标测试通过 `1` 条，覆盖部门/岗位/角色分组、禁用用户过滤和用户摘要不泄露密码字段。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest" test` 通过 `19` 条。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test` 通过 `13` 条，锁定 `permission:read` 和 `/organization-directory` 控制器契约。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条，确认前端 API 契约包含组织目录端点。
- GREEN：`npm run typecheck` 通过，确认 Vue 3 + TypeScript 类型检查通过。

结论：

- 当前 `UC-08` 已具备稳定的只读组织目录 API，审批代理规则页和规则编排器后续可以从正式目录契约读取部门/岗位/角色/人员视图，而不是继续依赖 `/users` 页面列表临时推导。
- 剩余生产级差距继续集中在持久化组织树、岗位字典、上级关系/汇报线建模、完整排班日历 UI、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 196. 2026-06-29 UC-08 审批页面消费组织目录 API 闭环

本轮目标：把已上线的只读组织目录 API 接入审批代理规则页和规则编排器，停止在审批角色选择场景中继续从 `/users` 列表临时推导角色目录。

已完成：

- `ApprovalDelegateRules.vue` 改为调用 `adminApi.organizationDirectory()`，角色下拉和候选人提示均来自 `/api/v1/organization-directory`。
- `RuleDesigner.vue` 的 approval 节点角色选择器改为读取 `adminApi.organizationDirectory()`。
- `RuleDesigner.vue` 仍保留 `/users?page=1&pageSize=20` 给 `create_task` 的人员指派选择使用，避免把“人员选择”和“审批角色目录”混成一个数据源。
- `approval-delegate-rules.spec.ts` 与 `rule-engine.spec.ts` 均改为用 `/organization-directory` 提供审批角色目录；其中 `rule-engine.spec.ts` 的 `/users` mock 只保留任务指派人员，确保测试能区分两个数据源。

验证证据：

- RED：`npm run e2e -- approval-delegate-rules.spec.ts` 首次失败在找不到 `Finance Center / Finance Manager / finance_manager / Fiona Manager`，证明页面仍依赖旧 `/users` 推导。
- GREEN：同一 E2E 通过 `1 passed`，审批代理规则页已从正式组织目录 API 展示角色选项和候选人提示。
- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败在找不到 `Finance Center / Finance Manager / finance_manager / Fiona Manager`，证明规则编排器 approval 节点仍依赖旧用户列表。
- GREEN：同一 E2E 通过 `1 passed`，规则编排器 approval 节点已从正式组织目录 API 选择审批角色和代理角色，同时 `create_task` 人员指派仍可从 `/users` 选择 `Finance Reviewer`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- GREEN：`npm run typecheck` 通过。

结论：

- 当前 `UC-08` 的两个主要审批角色配置入口已经消费正式只读组织目录 API，前端不再把审批角色选择绑定到分页用户列表。
- 剩余生产级差距继续集中在持久化组织树、岗位字典、上级关系/汇报线建模、完整排班日历 UI、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 197. 2026-06-29 UC-08 组织树主数据持久化骨架闭环

本轮目标：把只读组织目录从“完全由用户账号反推”推进到具备持久化组织树、岗位字典和上级/负责人字段的最小主数据骨架，为后续组织维护页、组织树选择器和多级审批流打底。

已完成：

- 新增 `OrganizationUnit` 与 `OrganizationPosition` 领域模型。
- 新增 `OrganizationDirectoryRepository` 抽象，以及 `JdbcOrganizationDirectoryRepository` 生产实现。
- 新增 Flyway `V045__organization_directory_master_data.sql`：
  - `organization_units` 支持 `code/name/parent_id/unit_type/status/sort_order`。
  - `organization_positions` 支持 `organization_unit_id/code/name/roles/manager_user_id/status/sort_order`。
  - 增加启用组织单元父子索引、岗位组织单元索引和岗位角色 GIN 索引。
- `PermissionApplicationService.organizationDirectory()` 在保留 `departments` 与 `roles` 兼容视图的同时新增 `organizationTree`。
- `organizationTree` 过滤 disabled 组织单元和其下岗位，返回组织层级、岗位、角色和 `managerUserId`。
- `UserAccount` 补回旧构造器兼容，避免历史测试和旧调用点因新增部门/岗位字段无法编译。
- 修复 `RuleApplicationServiceTest.java` 文件头 BOM，解除 Java testCompile 阻塞。
- 前端 `OrganizationDirectory` 类型新增可选 `organizationTree`，不影响现有审批页面消费 `roles` 视图。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#includesPersistedOrganizationTreeInOrganizationDirectory" test` 首次因 `OrganizationUnit/OrganizationPosition/OrganizationDirectoryRepository` 和新构造器不存在失败。
- GREEN：同一目标测试通过 `1` 条，覆盖 `HQ -> Finance Center -> Finance Risk Team` 树、岗位角色、`managerUserId` 和 disabled 组织过滤。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest" test` 通过 `20` 条。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test` 通过 `13` 条。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsOrganizationDirectoryTreeInPostgres" test` 通过 `1` 条，Flyway 成功从 v044 迁移到 v045。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- approval-delegate-rules.spec.ts rule-engine.spec.ts` 通过 `2` 条。

结论：

- 当前 `UC-08` 已具备组织树主数据的最小持久化骨架，目录 API 能同时服务旧的角色选择视图和后续组织树选择器。
- 剩余生产级差距继续集中在组织主数据维护页、用户与岗位/组织单元的正式关联表、完整排班日历 UI、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 198. 2026-06-29 UC-08 组织主数据维护最小闭环

本轮目标：在已有组织树主数据骨架上，补齐业务管理员可以从前端创建组织单元和岗位的最小维护链路，避免组织目录只能依赖数据库脚本初始化。

已完成：

- `PermissionApplicationService` 新增 `createOrganizationUnit()` 与 `createOrganizationPosition()`，分别校验必填字段并通过 `OrganizationDirectoryRepository` 写入组织单元和岗位。
- `PermissionController` 新增 `POST /api/v1/organization-units` 与 `POST /api/v1/organization-positions`，均使用 `user:manage` 权限保护。
- `JdbcOrganizationDirectoryRepository` 支持保存组织单元和岗位，岗位角色以 PostgreSQL `text[]` 持久化。
- 前端 `adminApi` 新增 `CreateOrganizationUnitRequest`、`CreateOrganizationPositionRequest`、`createOrganizationUnit()` 与 `createOrganizationPosition()`。
- 新增 `/admin/organizations` 组织维护页和侧边栏入口 `Organization management`，支持刷新组织树、创建组织单元、创建岗位，并在创建后重新读取 `/organization-directory`。
- 新增 `organization-management.spec.ts` 覆盖菜单入口、组织单元创建 payload、岗位创建 payload 和组织树刷新显示。

验证证据：

- RED：`npm run test -- apiContracts` 首次失败在 `adminApi.createOrganizationUnit is not a function`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条，锁定 `/organization-units` 与 `/organization-positions` 前端调用契约。
- RED：`npm run e2e -- organization-management.spec.ts` 首次失败在找不到菜单项 `Organization management`。
- GREEN：同一 E2E 通过 `1` 条，覆盖组织维护页完整前端旅程。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest" test` 通过 `21` 条。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test` 通过 `14` 条，锁定 `user:manage` 与两个维护端点。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#createsOrganizationMaintenanceRecordsThroughServiceInPostgres" test` 通过 `1` 条，Flyway schema 已在 v045，服务创建的数据可真实写入 PostgreSQL 并被 `organizationTree` 读出。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- organization-management.spec.ts approval-delegate-rules.spec.ts rule-engine.spec.ts` 通过 `3` 条，确认组织维护页未破坏审批代理和规则编排角色选择旅程。

结论：

- 当前 `UC-08` 已从只读组织目录推进到可维护组织单元和岗位的最小闭环，业务管理员可以通过页面维护组织树基础主数据。
- 剩余生产级差距继续集中在编辑/删除、拖拽调整组织层级、用户与岗位/组织单元正式关联表、岗位任职有效期、批量导入、排班日历、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 199. 2026-06-29 UC-08 用户岗位任职关系最小闭环

本轮目标：把组织目录从“岗位主数据 + 用户表部门/岗位字符串兜底”推进到具备正式用户-岗位任职关系，支撑后续组织树选人、多级审批和岗位职责治理。

已完成：

- 新增 `OrganizationPositionAssignment` 领域模型，表示用户与组织岗位的启用任职关系。
- `OrganizationDirectoryRepository` 新增 `findEnabledPositionAssignments()` 与 `savePositionAssignment()`。
- `JdbcOrganizationDirectoryRepository` 支持读写 `organization_position_assignments`。
- 新增 Flyway `V046__organization_position_assignments.sql`：
  - `organization_position_assignments.user_id` 关联 `user_accounts`。
  - `position_id` 关联 `organization_positions`。
  - 支持 `primary_position`、`status`、唯一约束和用户/岗位索引。
- `PermissionApplicationService.assignUserToOrganizationPosition()` 支持创建用户岗位任职关系，并校验用户和岗位存在。
- `organizationDirectory()` 现在优先使用正式岗位任职关系聚合部门、岗位、角色、用户；没有正式任职关系的用户仍按 `user_accounts.department/position` 旧字段兜底，保持兼容。
- `organizationTree.positions[]` 新增 `users`，可直接展示岗位下的任职用户。
- `PermissionController` 新增 `POST /api/v1/organization-position-assignments`，使用 `user:manage` 权限保护。
- 前端 `adminApi.assignUserToOrganizationPosition()`、`OrganizationPositionAssignment` 类型和组织维护页任职分配表单已补齐。
- `organization-management.spec.ts` 扩展覆盖用户分配到岗位后的 payload、刷新和岗位用户展示。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#assignsUsersToOrganizationPositionsForDirectoryAggregation" test` 首次失败在 `assignUserToOrganizationPosition()` 未定义。
- GREEN：同一目标测试通过 `1` 条，覆盖正式岗位任职进入 `departments/roles/organizationTree.positions.users`。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#organizationMaintenanceUsesUserManagePermissionEndpoints" test` 首次失败在缺少 `/organization-position-assignments`。
- GREEN：同一契约测试通过 `1` 条，锁定 `user:manage` 与控制器调用。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsOrganizationPositionAssignmentsThroughServiceInPostgres" test` 通过 `1` 条，Flyway 成功迁移到 v046。
- RED：`npm run test -- apiContracts` 首次失败在 `adminApi.assignUserToOrganizationPosition is not a function`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- RED：`npm run e2e -- organization-management.spec.ts` 首次失败在找不到 `Assignment user id`。
- GREEN：同一 E2E 通过 `1` 条。
- GREEN：`npm run typecheck` 通过。

结论：

- 当前 `UC-08` 已具备正式用户-岗位任职关系的最小闭环，审批组织目录不再只能依赖用户表字符串字段。
- 剩余生产级差距继续集中在任职关系编辑/停用、主岗位唯一性治理、任职有效期、用户选择器、批量导入、拖拽组织层级、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 200. 2026-06-29 UC-08 任职关系停用与主岗位唯一性闭环

本轮目标：在正式用户-岗位任职关系基础上补齐生命周期治理，保证同一用户只有一个启用主岗位，并允许管理员停用具体任职关系，避免离岗/调岗后旧岗位继续参与审批角色聚合。

已完成：

- `OrganizationDirectoryRepository` 新增 `findPositionAssignmentById()` 与 `disablePositionAssignment()`。
- `JdbcOrganizationDirectoryRepository.savePositionAssignment()` 在保存新的 `primary=true` 启用任职前，会将同一用户既有启用主岗位置为 `primary_position = false`，实现单主岗位约束。
- `JdbcOrganizationDirectoryRepository.disablePositionAssignment()` 支持将任职关系状态置为 `disabled`。
- `PermissionApplicationService.disableOrganizationPositionAssignment()` 支持停用任职关系，并对已停用任职保持幂等返回。
- `organizationDirectory()` 只聚合启用任职关系；被停用的岗位任职不再进入 `departments/roles/organizationTree.positions.users`，岗位主数据本身仍保留。
- `PermissionController` 新增 `POST /api/v1/organization-position-assignments/{assignmentId}/disable`，使用 `user:manage` 权限保护。
- 前端 `adminApi.disableOrganizationPositionAssignment()`、组织维护页停用任职表单和 E2E 停用旅程已补齐。

验证证据：

- RED/GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#enforcesSinglePrimaryPositionAndDisablesAssignments" test` 通过 `1` 条，覆盖单主岗位、停用后目录聚合移除和旧任职仍启用。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#organizationMaintenanceUsesUserManagePermissionEndpoints" test` 首次失败在缺少 `/organization-position-assignments/{assignmentId}/disable`。
- GREEN：同一契约测试通过 `1` 条，锁定 `user:manage`、路由和服务调用。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#disablesOrganizationPositionAssignmentsAndKeepsSinglePrimaryInPostgres" test` 通过 `1` 条，真实 PostgreSQL/Flyway v046 验证主岗位覆盖、停用状态和目录聚合。
- RED：`npm run test -- apiContracts` 首次失败在 `adminApi.disableOrganizationPositionAssignment is not a function`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- organization-management.spec.ts` 通过 `1` 条，覆盖页面停用任职、payload 和刷新后岗位用户移除。

结论：

- 当前 `UC-08` 已具备任职关系新增、单主岗位治理和停用的最小生产闭环，能支撑组织树选人、审批角色聚合和后续多级审批的基础数据一致性。
- 剩余生产级差距继续集中在任职编辑、任职有效期、用户选择器、批量导入、组织层级调整、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 201. 2026-06-29 UC-08 任职有效期闭环

本轮目标：在用户-岗位正式任职关系上补齐 `activeFrom/activeTo` 生效窗口，避免未来入职、已离岗或临时任职过期的人员继续进入审批组织目录和角色聚合。

已完成：

- `OrganizationPositionAssignment` 新增 `activeFrom` 与 `activeTo`。
- 新增 Flyway `V047__organization_position_assignment_active_window.sql`，为 `organization_position_assignments` 增加 `active_from/active_to` 和启用任职窗口索引。
- `PermissionApplicationService.assignUserToOrganizationPosition()` 支持从请求中解析 `activeFrom/activeTo` 并在响应中返回。
- `organizationDirectory()` 仅聚合当前时间落在有效窗口内的启用正式任职；未来/过期任职不会进入 `departments/roles/organizationTree.positions.users`。
- 修复正式任职用户 fallback 语义：只要用户存在启用正式任职关系，即使当前窗口未生效，也不会再回落到旧 `user_accounts.department/position` 字段，避免过期/未来任职人员以“无部门/无岗位”形式误入审批目录。
- `JdbcOrganizationDirectoryRepository` 已读写 `active_from/active_to`。
- 前端 `adminApi.AssignUserToOrganizationPositionRequest`、`OrganizationPositionAssignment` 类型和组织维护页任职分配表单已支持有效期输入。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#filtersOrganizationPositionAssignmentsByActiveWindow" test` 首次失败在任职响应缺少 `activeFrom`。
- GREEN：同一目标测试通过 `1` 条，覆盖当前有效任职进入目录、未来/过期任职不进入目录，以及正式任职用户不再走旧字段 fallback。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#filtersOrganizationPositionAssignmentsByActiveWindowInPostgres" test` 通过 `1` 条，Flyway 成功迁移到 v047。
- RED：`npm run typecheck` 首次失败在 `AssignUserToOrganizationPositionRequest` 缺少 `activeFrom`。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- GREEN：`npm run e2e -- organization-management.spec.ts` 通过 `1` 条，覆盖页面填写有效期并提交 ISO payload。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest,ContractSurfaceTest" test` 通过 `38` 条。

结论：

- 当前 `UC-08` 的组织任职关系已具备新增、单主岗位、停用和有效期治理，审批目录可避免展示未来/过期任职人员。
- 剩余生产级差距继续集中在任职编辑、用户选择器、批量导入、组织层级调整、完整排班日历、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 202. 2026-06-29 UC-08 任职关系编辑闭环

本轮目标：在已具备新增、停用、单主岗位和有效期治理的基础上，补齐管理员修改既有任职关系的能力，避免因有效期或主岗标记变更只能停用后重建。

已完成：

- `OrganizationDirectoryRepository` 新增 `updatePositionAssignment()`，以领域模型为单位更新任职关系。
- `PermissionApplicationService.updateOrganizationPositionAssignment()` 支持局部更新 `primary/activeFrom/activeTo`，未传字段保持原值；已停用任职禁止继续编辑。
- `JdbcOrganizationDirectoryRepository.updatePositionAssignment()` 支持真实 PostgreSQL 更新 `primary_position/active_from/active_to`；当更新为 `primary=true` 时，会将同一用户其他启用主岗位置为非主岗。
- `PermissionController` 新增 `PUT /api/v1/organization-position-assignments/{assignmentId}`，继续使用 `user:manage` 权限保护。
- 前端 `adminApi.updateOrganizationPositionAssignment()`、组织维护页编辑任职表单、Playwright 编辑旅程已补齐。
- 组织目录保持既有语义：只聚合当前有效窗口内的启用任职；岗位主数据即使没有用户也仍在组织树中展示。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#updatesOrganizationPositionAssignmentWindowAndPrimaryFlag" test` 首次失败在 `updateOrganizationPositionAssignment()` 未定义。
- GREEN：同一目标测试通过 `1` 条，覆盖更新有效期、更新主岗标记、同用户旧主岗自动取消，以及更新后的目录聚合。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#organizationMaintenanceUsesUserManagePermissionEndpoints" test` 首次失败在缺少 `PUT /organization-position-assignments/{assignmentId}`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#updatesOrganizationPositionAssignmentWindowAndPrimaryFlag,ContractSurfaceTest#organizationMaintenanceUsesUserManagePermissionEndpoints" test` 通过 `2` 条。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#updatesOrganizationPositionAssignmentWindowAndPrimaryInPostgres" test` 通过 `1` 条，真实 PostgreSQL/Flyway v047 验证更新 SQL、单主岗排他和目录聚合。
- RED：`npm run test -- apiContracts` 首次失败在 `adminApi.updateOrganizationPositionAssignment is not a function`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- RED：`npm run e2e -- organization-management.spec.ts` 首次失败在找不到 `Update assignment id`。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- organization-management.spec.ts` 通过 `1` 条，覆盖编辑表单、PUT payload、成功消息和刷新链路。

结论：

- 当前 `UC-08` 的组织任职关系已具备新增、编辑、停用、单主岗位和有效期治理，审批目录的数据一致性继续向生产可交付靠近。
- 剩余生产级差距继续集中在用户选择器、批量导入、组织层级调整、完整排班日历、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 203. 2026-06-29 UC-08 任职分配用户与岗位选择器闭环

本轮目标：把组织维护页的任职分配从“手填 userId/positionId”推进到“可从当前启用用户和组织树岗位中选择”，降低客户验收和日常运维时的误填风险。

已完成：

- `OrganizationManage.vue` 在加载组织目录时并行读取 `/users?page=1&pageSize=100`，仅将启用用户作为任职分配候选人。
- 任职分配表单新增 `Assignment user selector`，展示 `displayName / username / department / position`，选择后回填既有 `assignmentForm.userId`。
- 任职分配表单新增 `Assignment position selector`，从 `organizationTree` 递归提取岗位选项，展示 `unitName / code / name`，选择后回填既有 `assignmentForm.positionId`。
- 原 `User ID` 和 `Position ID` 输入仍保留为兜底入口，避免目录未覆盖或临时排障时无法录入。
- 既有创建任职 payload 不变，继续提交 `userId/positionId/primary/activeFrom/activeTo`。

验证证据：

- RED：`npm run e2e -- organization-management.spec.ts` 首次失败在找不到 `Assignment user selector`，证明页面此前只能手填 ID。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- organization-management.spec.ts` 通过 `1` 条，覆盖从用户下拉和岗位下拉选择后仍提交正确 `userId=71`、`positionId=10`。

结论：

- 当前组织维护页已具备任职新增、编辑、停用、有效期治理和可选用户/岗位的基础运维体验。
- 剩余生产级差距继续集中在任职批量导入、组织层级调整、完整排班日历、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 204. 2026-06-29 UC-08 任职批量导入闭环

本轮目标：把用户-岗位任职关系从单条维护推进到批量导入，支持管理员一次导入多条任职关系，并逐条返回成功/失败结果，降低初始化组织主数据和批量调岗成本。

已完成：

- `PermissionApplicationService.batchImportOrganizationPositionAssignments()` 支持批量导入 `assignments[]`。
- 每行导入复用正式任职模型，支持 `userId/positionId/primary/activeFrom/activeTo`。
- 批量导入逐条返回 `imported/failed`，失败原因包含 `user_id_required`、`position_id_required`、`user_not_found`、`position_not_found`。
- 成功导入仍复用 `JdbcOrganizationDirectoryRepository.savePositionAssignment()` 的主岗唯一性治理：同一用户后导入的 `primary=true` 会取消旧启用主岗。
- `PermissionController` 新增 `POST /api/v1/organization-position-assignments/batch-import`，继续使用 `user:manage` 权限保护，并放在 `{assignmentId}` 路由之前避免路径冲突。
- 前端 `adminApi.batchImportOrganizationPositionAssignments()` 和组织维护页批量导入文本入口已补齐，格式为 `userId,positionId,primary,activeFrom,activeTo`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#batchImportsOrganizationPositionAssignmentsWithRowResults" test` 首次失败在 `batchImportOrganizationPositionAssignments()` 未定义。
- GREEN：同一目标测试通过 `1` 条，覆盖成功行、失败行、失败原因、主岗唯一性和目录聚合。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#organizationMaintenanceUsesUserManagePermissionEndpoints" test` 首次失败在缺少 `/organization-position-assignments/batch-import`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#batchImportsOrganizationPositionAssignmentsWithRowResults,ContractSurfaceTest#organizationMaintenanceUsesUserManagePermissionEndpoints" test` 通过 `2` 条。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#batchImportsOrganizationPositionAssignmentsInPostgres" test` 通过 `1` 条，真实 PostgreSQL/Flyway v047 验证批量落库、主岗排他和目录聚合。
- RED：`npm run test -- apiContracts` 首次失败在 `adminApi.batchImportOrganizationPositionAssignments is not a function`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- RED：`npm run e2e -- organization-management.spec.ts` 首次失败在找不到 `Batch import position assignments`。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- organization-management.spec.ts` 通过 `1` 条，覆盖文本导入、ISO 时间转换、payload 和成功/失败统计提示。

结论：

- 当前组织维护页已具备任职新增、批量导入、编辑、停用、有效期治理、主岗唯一性和用户/岗位选择器，组织任职主数据维护能力进一步接近客户生产交付。
- 剩余生产级差距继续集中在完整排班日历、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 205. 2026-06-29 UC-08 组织层级调整闭环

本轮目标：把组织维护页从“只能创建组织单元”推进到“可调整既有组织单元的名称、父级、类型和排序”，支撑客户后续组织重组、部门归并和审批组织树维护。

已完成：

- `OrganizationDirectoryRepository` 新增 `findUnitById()` 与 `updateUnit()`。
- `PermissionApplicationService.updateOrganizationUnit()` 支持局部更新组织单元 `name/parentId/unitType/sortOrder`，保留不可变 `code/status`。
- 更新父级时校验自引用、父级存在和子级回挂，避免组织树形成环。
- `JdbcOrganizationDirectoryRepository.updateUnit()` 支持真实 PostgreSQL 更新 `organization_units.name/parent_id/unit_type/sort_order`。
- `PermissionController` 新增 `PUT /api/v1/organization-units/{unitId}`，继续使用 `user:manage` 权限保护。
- 前端 `adminApi.updateOrganizationUnit()` 和组织维护页 `Update Organization Unit` 表单已补齐，提交后刷新 `/organization-directory` 并展示最新树。
- `organizationTree` 响应现在包含 `sortOrder`，便于前端和后续拖拽排序能力承接。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#updatesOrganizationUnitHierarchyForDirectoryMaintenance,ContractSurfaceTest#organizationMaintenanceUsesUserManagePermissionEndpoints" test` 首次失败在 `updateOrganizationUnit()` 未定义和缺少 `PUT /organization-units/{unitId}`。
- GREEN：同一目标测试通过 `2` 条，覆盖服务更新、目录层级刷新和控制器契约。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#updatesOrganizationUnitHierarchyInPostgres" test` 通过 `1` 条，真实 PostgreSQL/Flyway v047 验证 parent/name/type/sort 持久化和组织树读取。
- RED：`npm run test -- apiContracts` 首次失败在 `adminApi.updateOrganizationUnit is not a function`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条。
- RED：`npm run e2e -- organization-management.spec.ts` 首次失败在找不到 `Update organization unit id`。
- GREEN：`npm run e2e -- organization-management.spec.ts` 通过 `1` 条，覆盖页面 PUT payload、成功消息和组织树刷新。

结论：

- 当前组织维护页已具备组织单元创建、层级调整、岗位创建、任职新增、批量导入、编辑、停用、有效期治理、主岗唯一性和用户/岗位选择器，组织主数据维护能力进一步接近客户生产交付。
- 剩余生产级差距继续集中在完整排班日历、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 206. 2026-06-29 UC-08 审批代理指定日期排班闭环

本轮目标：把全局审批代理规则从“只支持有效期窗口和星期限制”推进到“可指定具体生效日期”，覆盖节假日、临时值班和短期替班等真实排班场景。

已完成：

- `RuleApprovalDelegateRule` 新增 `activeDates`，使用 ISO `yyyy-MM-dd` 字符串列表表达指定生效日期。
- `RuleApplicationService.createApprovalDelegateRule()` 与 `updateApprovalDelegateRule()` 支持接收、去重和校验 `activeDates`，响应中回显 `activeDates`。
- 代理规则匹配逻辑同时尊重 `activeFrom/activeTo`、`activeWeekdays` 和 `activeDates`：日期列表为空表示不限制；非空时必须命中当天日期。
- `InMemoryRuleRepository.findActiveApprovalDelegateRule()` 已按当前日期过滤代理规则。
- `JdbcRuleRepository` 已读写 `rule_approval_delegate_rules.active_dates`，真实 PostgreSQL 激活查询使用 `jsonb_exists(active_dates, currentDate)` 过滤。
- 新增 Flyway `V048__rule_approval_delegate_active_dates.sql`，为代理规则表增加 `active_dates JSONB NOT NULL DEFAULT '[]'::jsonb` 和 GIN 索引。
- 前端 `ruleApi` 创建/编辑代理规则 payload 支持 `activeDates`。
- `ApprovalDelegateRules.vue` 新增创建态 `Delegate active dates`、编辑态 `Edit active dates` 输入框，按逗号拆分日期并在卡片中展示 `Dates ...`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRuleOnlyAppliesOnConfiguredActiveDates" test` 首次失败在响应缺少 `activeDates`，证明旧实现未解析/回显指定日期。
- GREEN：同一目标测试通过 `1` 条，覆盖“只配明天今天不代理，改成今天后新审批记录应用代理”。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#delegateRuleOnlyAppliesOnConfiguredActiveDates,RuleApplicationServiceTest#delegateRuleOnlyAppliesOnConfiguredWeekdays,RuleApplicationServiceTest#updatesApprovalDelegateRulesForOperations" test` 通过 `3` 条，覆盖日期、星期和编辑回归。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#updatesApprovalDelegateRuleFieldsInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#activeWeekdayDelegateRuleDoesNotApplyOnUnmatchedDayInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#activeDateDelegateRuleDoesNotApplyOnUnmatchedDateInPostgres" test` 通过 `3` 条，Flyway 已验证到 v048。
- RED：`npm run e2e -- approval-delegate-rules.spec.ts` 首次失败在找不到 `Delegate active dates` 输入框。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条；`npm run typecheck` 通过；`npm run e2e -- approval-delegate-rules.spec.ts` 通过 `1` 条。

结论：

- 当前 `UC-08` 的全局审批代理规则已具备有效期窗口、星期和指定日期三类排班约束，能覆盖最小临时值班/节假日替班业务闭环。
- 剩余生产级差距收窄为可视化排班日历视图、日期冲突检测、批量导入/导出排班、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 207. 2026-06-29 UC-08 审批代理排班冲突检测闭环

本轮目标：在审批代理规则已有有效期、星期和指定日期排班能力的基础上，阻止同一审批角色出现多个启用规则的重叠排班，降低生产环境代理错派和规则歧义风险。

已完成：

- `RuleRepository` 新增 `findEnabledApprovalDelegateRulesByAssigneeRole()`，供冲突检测读取同审批角色的启用代理规则。
- `InMemoryRuleRepository` 与 `JdbcRuleRepository` 均实现同审批角色启用代理规则查询。
- `RuleApplicationService.createApprovalDelegateRule()` 与 `updateApprovalDelegateRule()` 在保存前执行排班冲突校验。
- 冲突判断覆盖同一 `assigneeRole`、启用状态、有效期窗口重叠、`activeWeekdays` 重叠和 `activeDates` 重叠；空星期或空日期表示不限制，会与任意限制集合发生重叠。
- 更新规则时会排除当前规则自身，避免编辑原规则时误报自冲突。
- 前端审批代理规则页已覆盖后端 `400` 冲突错误展示，管理员能看到冲突规则 ID。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalDelegateRuleScheduleConflictsForSameAssigneeRole,RuleApplicationServiceTest#rejectsApprovalDelegateRuleUpdateScheduleConflictsForSameAssigneeRole" test` 首次失败在未抛出异常，证明旧实现允许重叠排班。
- GREEN：同一目标测试通过 `2` 条，覆盖新增冲突和更新冲突。
- RED：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#rejectsApprovalDelegateRuleScheduleConflictsInPostgres" test` 首次失败在未抛出异常。
- GREEN：同一 PostgreSQL 集成测试通过 `1` 条，真实数据库/Flyway v048 验证冲突检测。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalDelegateRuleScheduleConflictsForSameAssigneeRole,RuleApplicationServiceTest#rejectsApprovalDelegateRuleUpdateScheduleConflictsForSameAssigneeRole,RuleApplicationServiceTest#delegateRuleOnlyAppliesOnConfiguredActiveDates,RuleApplicationServiceTest#delegateRuleOnlyAppliesOnConfiguredWeekdays,RuleApplicationServiceTest#updatesApprovalDelegateRulesForOperations" test` 通过 `5` 条。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#rejectsApprovalDelegateRuleScheduleConflictsInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#updatesApprovalDelegateRuleFieldsInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#activeWeekdayDelegateRuleDoesNotApplyOnUnmatchedDayInPostgres,JdbcReportGenerationTaskRepositoryPostgresIT#activeDateDelegateRuleDoesNotApplyOnUnmatchedDateInPostgres" test` 通过 `4` 条。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条；`npm run typecheck` 通过；`npm run e2e -- approval-delegate-rules.spec.ts` 通过 `2` 条。

结论：

- 当前 `UC-08` 的全局审批代理规则已具备有效期窗口、星期、指定日期和同角色重叠冲突检测，能支撑临时值班/节假日替班的最小生产闭环。
- 剩余生产级差距继续保留为可视化排班日历视图、批量导入/导出排班、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 208. 2026-06-29 UC-08 审批代理批量导入与导出闭环

本轮目标：在审批代理规则已具备日期排班与冲突检测的基础上，补齐客户初始化节假日值班表、批量迁移代理规则和导出核对的最小生产能力。

已完成：

- `RuleApplicationService.batchImportApprovalDelegateRules()` 支持 `{ rules: [...] }` JSON 批量导入。
- 批量导入逐行复用单条 `createApprovalDelegateRule()` 的字段校验、日期/星期解析和同角色排班冲突检测。
- 批量导入逐行返回 `rowNumber/status/reason/delegateRuleId/assigneeRole/delegateRole`，成功行落库，失败行不阻断后续行。
- `RuleController` 新增 `POST /api/v1/rules/approval-delegate-rules/batch-import`，继续使用 `rule:manage` 权限，并放在 `{delegateRuleId}` 路由之前避免路径冲突。
- 前端 `ruleApi.batchImportApprovalDelegateRules()` 已接入后端批量导入端点。
- `ApprovalDelegateRules.vue` 新增文本批量导入入口，格式为 `assigneeRole,delegateRole,activeFrom,activeTo,activeWeekdays,activeDates,reason`；星期和日期字段内部使用 `|` 分隔多值。
- 页面展示逐行导入结果和失败原因，导入后自动刷新规则列表。
- 页面新增 `Export delegate rules`，可将当前列表导出为 `approval-delegate-rules.csv`，用于客户核对和离线留档。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#batchImportsApprovalDelegateRulesWithRowResults" test` 首次失败在 `batchImportApprovalDelegateRules()` 未定义。
- GREEN：同一服务测试通过 `1` 条，覆盖成功行、冲突失败行、必填失败行和最终规则总数。
- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalDelegateRuleOperationsUseManagePermission" test` 首次失败在缺少 `/approval-delegate-rules/batch-import`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#batchImportsApprovalDelegateRulesWithRowResults,ContractSurfaceTest#ruleApprovalDelegateRuleOperationsUseManagePermission" test` 通过 `2` 条。
- GREEN：`$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core "-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#batchImportsApprovalDelegateRulesInPostgres" test` 通过 `1` 条，真实 PostgreSQL/Flyway v048 验证成功行落库、冲突行和必填失败行不落库。
- RED：`npm run test -- apiContracts` 首次失败在 `ruleApi.batchImportApprovalDelegateRules is not a function`。
- GREEN：`npm run test -- apiContracts` 通过 `26` 条；`npm run typecheck` 通过。
- RED：`npm run e2e -- approval-delegate-rules.spec.ts` 首次失败在找不到 `Batch import delegate rules`。
- GREEN：`npm run e2e -- approval-delegate-rules.spec.ts` 通过 `2` 条，覆盖导入 payload、逐行失败展示、导入后列表刷新和 CSV 下载文件名。

结论：

- 当前 `UC-08` 的全局审批代理规则已具备有效期窗口、星期、指定日期、同角色重叠冲突检测、批量导入和导出核对能力，能支撑节假日/临时值班表的初始化、运维和验收核查。
- 剩余生产级差距继续保留为可视化排班日历视图、多级审批流、SLA 升级策略和独立人工审批流设计器。

## 209. 2026-06-29 UC-08 审批代理可视化排班日历闭环

本轮目标：把审批代理规则从列表式维护推进到可按日期查看排班，让客户能直观看到节假日、临时值班和周期性代理安排。

已完成：

- `ApprovalDelegateRules.vue` 新增 `Delegate schedule calendar` 区域。
- 启用状态且配置了 `activeDates` 的代理规则会按日期分组展示为日历卡片。
- 未配置 `activeDates` 的启用规则进入 `Recurring / window based` 区域，展示周期性星期和有效期窗口。
- 日历视图复用已有 `listApprovalDelegateRules()` 数据，不新增后端接口或数据库结构。
- 原有创建、编辑、禁用、启用、批量导入、导出和冲突错误展示能力保持不变。

验证证据：

- RED：`npm run e2e -- approval-delegate-rules.spec.ts` 首次失败在找不到 `Delegate schedule calendar` 标题，证明旧页面没有可视化日历。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- approval-delegate-rules.spec.ts` 通过 `3` 条，覆盖日期卡片、周期性规则区域、冲突错误展示、批量导入/导出和原有创建编辑流程。

结论：

- 当前 `UC-08` 的全局审批代理规则已具备有效期窗口、星期、指定日期、同角色重叠冲突检测、批量导入、导出核对和可视化排班查看能力。
- 剩余生产级差距继续保留为多级审批流、SLA 升级策略和独立人工审批流设计器。

## 210. 2026-06-29 UC-08 审批 SLA 升级策略闭环

本轮目标：让审批节点在超期后不只产生 overdue 提醒，还能按节点配置升级到上级/兜底角色，支撑生产中的审批催办和责任升级。

已完成：

- 审批节点定义新增 `slaEscalationRole`，用于配置 SLA 超期升级角色。
- `RuleApplicationService.createApprovalSlaOverdueAlert()` 在审批记录超期时反查规则节点配置，优先把告警发给 `slaEscalationRole` 下的启用用户。
- 若未配置升级角色或该角色没有启用用户，则保留原有 assignee role / createdBy fallback 路由。
- SLA 超期告警 payload 新增 `escalationRole` 和 `recipientSource=slaEscalationRole`。
- SLA 超期审计明细新增 `recipientSource` 和 `escalationRole`，便于后续审计追踪。
- `RuleDesigner.vue` 新增 `New approval SLA escalation role` 输入框，保存规则时写入审批节点定义。
- 规则画布节点摘要展示 `escalate <role>`，方便业务人员检查升级配置。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesOverdueSlaAlertToConfiguredEscalationRoleUser" test` 首次失败在 SLA 超期告警仍发送给原审批人 `77`，而不是升级角色用户 `88`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesOverdueSlaAlertToConfiguredEscalationRoleUser,RuleApplicationServiceTest#routesOverdueSlaAlertToEnabledAssigneeRoleUser,RuleApplicationServiceTest#scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert" test` 通过 `3` 条，覆盖升级路由、旧 assignee fallback 和 dedupe 扫描。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- rule-engine.spec.ts` 通过 `1` 条，覆盖前端配置 `slaEscalationRole`、节点摘要展示和保存 payload。

结论：

- 当前 `UC-08` 审批治理已具备 SLA 到期识别、超期告警、告警去重、审批角色路由和配置化升级角色能力。
- 剩余生产级差距继续保留为更完整的多级审批流和独立人工审批流设计器。

## 211. 2026-06-29 UC-08 顺序多级审批流闭环

本轮目标：把规则生产运行中的人工审批从“单个审批节点批准后直接继续执行下游动作”推进到“可以按 trace 中的多个 approval 节点逐级阻断和恢复”，避免财务审批通过后绕过法务、总监等后续审批关卡。

已完成：

- `RuleApplicationService.executeRuleActions()` 在审批恢复链路中遇到后续 `approval` 节点时，会创建该节点的待审批记录并立即停住下游动作。
- 同一 `ruleId/runId/nodeId` 已存在审批记录时不会重复创建，避免重复批准或重复恢复导致同一审批关卡重复落待办。
- 既有首个审批节点阻断语义保持不变：生产运行首次命中 approval 时仍只创建第一关审批记录，不执行后续 `notify/create_task/webhook`。
- 既有多人会签/或签语义保持不变：同一个 approval 节点内的 `all/any` 仍按审批组汇总决定是否恢复，后续 approval 节点作为下一关独立生成。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval" test` 首次失败在财务审批通过后审批记录总数仍为 `1`，没有生成 `legalApproval` 待办。
- GREEN：同一测试通过 `1` 条，覆盖 `financeApproval -> legalApproval -> notifyApproved` 的顺序审批：首轮只生成财务待办，财务批准后只生成法务待办且不通知，法务批准后才创建 `rule_action_notify` 告警。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvingPendingApprovalRecordResumesDownstreamActions,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions,RuleApplicationServiceTest#approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions,RuleApplicationServiceTest#rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup" test` 通过 `5` 条，确认单审批恢复、顺序多级审批、会签等待、或签收敛和驳回关闭没有回归。

结论：

- 当前 `UC-08` 已具备最小生产级顺序多级审批闸门：多个审批节点可以按规则 trace 逐级创建待办并阻断下游动作，最后一关批准后才恢复动作执行。
- 剩余生产级差距继续保留为独立人工审批流设计器、驳回后的补件/重新提交业务闭环、子流程节点、多级 SLA 策略和更高阶画布约束。

## 212. 2026-06-29 UC-08 审批驳回可配置回退分支闭环

本轮目标：把审批驳回从“只生成固定驳回反馈告警”推进到“可按规则连线配置进入 rejected 分支”，让业务人员能在画布中把驳回接到补件任务、通知或其他后续动作。

已完成：

- `RuleApplicationService.handleApprovalRecord()` 在 `action=reject` 时会先执行当前审批节点的 `condition=rejected` 出边，再保留原有驳回反馈告警。
- 新增 `executeRejectedApprovalBranch()`，会从被驳回的 approval 节点沿 `rejected` 连线继续执行该分支。
- 驳回分支支持执行 `notify/create_task/webhook` action 节点；如果分支再次遇到 `approval` 节点，则创建下一条待审批记录并停止，避免绕过人工闸门。
- 新增 `executeActionNode()` 复用正常流转中的 action 执行逻辑，避免驳回分支和批准后续跑出现两套动作语义。
- 新增 `nextNodeId(definition, source, condition)`，支持按连线 `condition` 精确选择分支；驳回分支不会落到默认批准出边。
- 前端规则画布 E2E 已覆盖 `New edge condition = rejected`，保存 payload 中包含 `{ source: 'financeApproval', target: 'createReviewTask', condition: 'rejected' }`。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly" test` 首次失败在没有创建 `rule_action_notify`，证明旧实现没有执行驳回配置分支。
- GREEN：同一测试通过 `1` 条，覆盖 `financeApproval -> notifyApproved` 默认边与 `financeApproval -> notifyRejected(condition=rejected)` 并存时，驳回只执行 `notifyRejected`，不会执行默认批准分支。
- GREEN：审批邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions,RuleApplicationServiceTest#approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions,RuleApplicationServiceTest#rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup" test` 通过 `5` 条，确认驳回分支、顺序多级审批、会签/或签和驳回关闭没有相互破坏。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- rule-engine.spec.ts` 通过 `1` 条，覆盖前端配置驳回连线并保存。

结论：

- 当前 `UC-08` 已具备最小生产级可配置驳回分支：业务可在规则定义中通过 `condition=rejected` 指定驳回后的补件任务、通知或其他动作。
- 剩余生产级差距继续保留为独立人工审批流设计器、驳回后的完整补件/重新提交状态机、子流程真实嵌套执行和更高阶画布约束。

## 213. 2026-06-29 UC-08 多级 SLA 升级策略闭环

本轮目标：把审批 SLA 从“单个超期升级角色”推进到“可配置多档升级策略”，支撑生产中 4 小时升级主管、8 小时升级总监等分级催办场景。

已完成：

- approval 节点新增 `slaEscalations` 配置，格式为 `[{ afterHours, role }]`，用于定义多档 SLA 升级策略。
- `RuleApplicationService.createApprovalSlaOverdueAlert()` 会根据审批记录创建时间计算已等待小时数，选择已命中的最高 `afterHours` 策略。
- 命中多档策略后，SLA 超期告警优先发给该策略 `role` 下的启用用户，并在 payload 与审计中写入 `recipientSource=slaEscalationPolicy`、`escalationRole`、`escalationAfterHours`。
- 旧字段 `slaEscalationRole` 继续兼容；没有命中 `slaEscalations` 时仍按旧单角色升级，再回退到审批角色或创建人。
- `RuleDomainService` 对 `slaEscalations` 做结构校验，拒绝缺少正整数 `afterHours` 或缺少 `role` 的非法策略；debug trace 会回显策略数组。
- 前端规则画布新增 `New approval SLA escalation policies` 输入，支持 `4:finance_director, 8:risk_vp` 这类文本录入，并保存为结构化 `slaEscalations`。
- 规则画布节点摘要会展示 `policies 4h:finance_director, 8h:risk_vp`，便于业务人员在保存前检查策略。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` 首次失败，超过 9 小时的审批仍发送给原审批人 `77`，证明旧实现没有多档策略。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy,RuleApplicationServiceTest#routesOverdueSlaAlertToConfiguredEscalationRoleUser" test` 通过 `2` 条，覆盖多档策略和旧单角色兼容。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy,RuleApplicationServiceTest#routesOverdueSlaAlertToConfiguredEscalationRoleUser,RuleApplicationServiceTest#routesOverdueSlaAlertToEnabledAssigneeRoleUser,RuleApplicationServiceTest#scansOverdueApprovalRecordsAndCreatesDedupedSlaAlert,RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval" test` 通过 `6` 条，确认多级 SLA、旧路由、去重扫描、驳回分支和顺序多级审批没有回归。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- rule-engine.spec.ts` 通过 `1` 条，覆盖前端多档 SLA 策略输入、节点摘要和保存 payload。

结论：

- 当前 `UC-08` 已具备最小生产级多级 SLA 升级：审批超期可以按配置逐档升级到不同角色，同时保留旧单角色升级和审批角色回退。
- 剩余生产级差距继续保留为独立人工审批流设计器、驳回后的完整补件/重新提交状态机、子流程真实嵌套执行和更高阶画布约束。

## 214. 2026-06-29 UC-08 子流程节点建模与调试闭环

本轮目标：先把规则画布从“不认识子流程节点”推进到“可建模、可保存、可调试 trace 标记引用规则”，为后续真实嵌套执行和独立审批流设计器打地基。

已完成：

- `RuleDomainService` 新增 `subprocess` 节点 schema 支持，要求 `subprocessRuleId` 为正整数。
- debug trace 命中 `subprocess` 节点时，会输出 `subprocessPending=true`、`subprocessRuleId` 和可选 `subprocessName`，明确当前节点是待子流程执行的编排占位。
- `RuleDomainServiceTest` 新增子流程节点 trace 回归；同时将该测试类历史乱码 `DisplayName` 清理为 ASCII，避免 clean test 在 Windows 编码环境下再次因半截注解字符串失败。
- 前端规则画布新增 `subprocess` 节点类型。
- `RuleDesigner.vue` 新增 `New subprocess rule id` 与 `New subprocess name` 输入，并在节点摘要中展示 `subprocess <ruleId> -> <name>`。
- 规则画布保存 payload 会包含 `subprocessRuleId/subprocessName`，便于后端规则定义持久化和后续执行器扩展。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core clean test "-Dtest=RuleDomainServiceTest#executeDebug_whenSubprocessNodePresent_thenMarksReferencedRuleInTrace"` 首次进入有效业务红灯时抛出 `RULE_VALIDATION_FAILED`，证明旧 schema 不接纳 `subprocess`。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenSubprocessNodePresent_thenMarksReferencedRuleInTrace" test` 通过 `1` 条，验证子流程 trace。
- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleDomainServiceTest#executeDebug_whenSubprocessNodePresent_thenMarksReferencedRuleInTrace+executeDebug_whenApprovalNodePresent_thenMarksPendingApprovalInTrace+executeDebug_whenBranchNodeMatches_thenOnlyEvaluatesSelectedPath+executeDebug_whenAggregateNodeFeedsCondition_thenUsesDerivedValue" test` 通过 `4` 条，确认子流程建模没有破坏 approval、branch、aggregate 调试链。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- rule-engine.spec.ts` 通过 `1` 条，覆盖前端新增子流程节点、摘要展示和保存 payload。

结论：

- 当前 `UC-08` 已具备最小子流程建模闭环：业务人员可以在规则画布中添加子流程节点，并在 debug trace 中看到引用规则信息。
- 剩余生产级差距继续保留为独立人工审批流设计器、驳回后的完整补件/重新提交状态机、跨流程审批恢复、深层递归深度/超时治理和更高阶画布约束。

## 215. 2026-06-29 UC-08 子流程真实执行与父子运行审计闭环

本轮目标：把 `subprocess` 从“可建模、可调试 trace 的占位节点”推进到“生产运行命中后可真实执行被引用规则”，并留下父子运行关联审计。

已完成：

- `RuleApplicationService.executeRuleActions()` 命中 `subprocess` 节点时，会读取 `subprocessRuleId` 指向的规则并校验其已发布。
- 子流程执行复用父运行输入，生成独立的子规则 `production` run，并继续执行子规则内的 `notify/create_task/webhook/approval/subprocess` 节点。
- 子流程成功后写入 `rule_subprocess_run` 审计，包含 `parentRuleId/parentRunId/nodeId/subprocessRuleId/subprocessRunId/subprocessVersionId`。
- 子流程失败时会生成失败的子规则 production run，写入失败审计并向父执行抛出异常，由既有父规则失败路径落失败 run 和生产运行审计。
- 禁止子流程直接引用父规则自身，避免最小闭环中出现直接递归。
- rejected 分支执行器也复用 `executeSubprocessNode()`，因此驳回分支中配置子流程时具备相同真实执行语义。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit" test` 首次失败在子规则 runs 为空，证明旧实现只是跳过 `subprocess` 节点。
- GREEN：同一测试通过 `1` 条，验证父规则执行后生成子规则 production run、子规则 notify 告警、`rule_subprocess_run` 父子关联审计，且告警 payload 不泄露 `sample/secretPrompt`。

结论：

- 当前 `UC-08` 已具备最小生产级子流程真实执行闭环：父规则可以调用已发布子规则，子规则有独立运行记录和动作执行结果，父子关系可审计追踪。
- 剩余生产级差距继续保留为独立人工审批流设计器、驳回后的完整补件/重新提交状态机、跨流程审批恢复/深层递归治理和更高阶画布约束。

## 216. 2026-06-29 UC-08 子流程循环防护闭环

本轮目标：在子流程真实执行上线后，补齐最关键的执行安全边界：阻止 A 调 B、B 再调 A 这类间接循环导致运行栈溢出或资源失控。

已完成：

- `RuleApplicationService.executeRuleActions()` 初始化并传递子流程执行栈。
- `executeSubprocessNode()` 在调用子规则前检查 `subprocessStack`，发现目标规则已经在当前调用链中时，抛出 `IllegalStateException("subprocess cycle detected...")`。
- 子流程递归执行时复制当前执行栈再追加子规则 ID，避免不同分支之间互相污染。
- 循环失败会进入既有子流程失败审计，`rule_subprocess_run` 的 `errorMessage` 中保留 `subprocess cycle` 证据，父规则生产运行也走既有失败路径。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence" test` 首次失败为 `StackOverflowError`，证明旧实现没有间接循环防护。
- GREEN：同一测试通过 `1` 条，验证 A -> B -> A 时抛出可解释的 `IllegalStateException`，并写入包含 `subprocess cycle` 的失败审计。

结论：

- 当前 `UC-08` 子流程真实执行已具备最小循环防护，不再让间接引用循环演变为栈溢出。
- 剩余生产级差距继续保留为独立人工审批流设计器、驳回后的完整补件/重新提交状态机、跨流程审批恢复、深层递归深度/超时治理和更高阶画布约束。

## 217. 2026-06-29 UC-08 子流程递归深度限制闭环

本轮目标：在循环防护之外，继续补齐非循环深链的生产安全边界，避免 A -> B -> C -> ... 这类过深子流程链路消耗过多本地和客户生产资源。

已完成：

- `RuleApplicationService` 新增子流程执行栈最大深度常量 `MAX_SUBPROCESS_STACK_DEPTH=8`。
- `executeSubprocessNode()` 在读取子规则前检查当前 `subprocessStack` 深度；达到上限时抛出 `IllegalStateException("subprocess depth limit exceeded...")`。
- 深度超限沿用既有子流程失败路径，写入 `rule_subprocess_run` 失败审计，并保留 `subprocess depth` 错误证据。
- 该限制不影响 1 层真实子流程执行、间接循环检测、驳回分支子流程执行、多级 SLA 和顺序多级审批邻近路径。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence" test` 首次失败为未抛出异常，证明旧实现没有非循环深链深度限制。
- GREEN：同一测试通过 `1` 条，验证 9 层线性子流程链会抛出 `subprocess depth` 异常，并在 `rule_subprocess_run` 失败审计中保留证据。
- GREEN：邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval" test` 通过 `6` 条。

结论：

- 当前 `UC-08` 子流程真实执行已具备最小深度治理：循环引用和非循环过深链路都能被阻断，并留下审计证据。
- 剩余生产级差距继续保留为独立人工审批流设计器、驳回后的完整补件/重新提交状态机、跨流程审批恢复、子流程运行拓扑 UI、超时/节点数配额和更高阶画布约束。

## 218. 2026-06-29 UC-08 子流程运行拓扑查询闭环

本轮目标：在子流程真实执行、循环防护和深度限制之后，补齐生产排障所需的父子运行追踪入口，让运维人员能从父规则运行反查它触发了哪些子流程运行。

已完成：

- `AuditRepository` 新增 `findByResourceOperationAndDetail()` 默认查询能力，用于按 `resourceType/resourceId/operationType/detail` 精确读取审计记录。
- `JdbcAuditRepository` 使用 PostgreSQL JSONB `detail ->> ?` 查询实现同名方法，避免真实库退化为全量审计扫描。
- `RuleApplicationService.subprocessRunTopology(ruleId, runId)` 基于既有 `rule_subprocess_run` 审计组装拓扑，返回 `nodes` 与 `edges`：
  - `nodes` 包含父运行和子流程运行的 `runId/ruleId/versionId/role/status/runType/durationMs/errorMessage`。
  - `edges` 包含 `parentRunId/subprocessRunId/nodeId/subprocessRuleId/subprocessVersionId/result/errorMessage/createdAt`。
- `RuleController` 新增只读端点 `GET /api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology`，使用 `rule:debug` 权限。
- 前端 `ruleApi.subprocessRunTopology(ruleId, runId)` 已纳入 API 契约，后续 UI 可直接消费该拓扑。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun" test` 首次失败在 `subprocessRunTopology(Long, Long)` 不存在，证明旧实现没有父子运行拓扑查询入口。
- GREEN：同一测试通过 `1` 条，验证父运行、子运行节点和 `riskSubprocess` 父子边均可从审计构建。
- GREEN：后端邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun,RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,ContractSurfaceTest#ruleSubprocessRunTopologyUsesDebugPermissionEndpoint" test` 通过 `5` 条。
- GREEN：`npm run test -- src/api/apiContracts.test.ts` 通过 `26` 条。
- GREEN：`npm run typecheck` 通过。

结论：

- 当前 `UC-08` 子流程生产运行已具备最小可追踪拓扑查询能力：父规则运行可以反查子流程运行和触发节点，排障不再只能翻审计明细。
- 剩余生产级差距继续保留为拓扑 UI 展示、跨流程审批恢复、执行耗时/节点数配额、独立人工审批流设计器、完整补件/重新提交状态机和更高阶画布约束。

## 219. 2026-06-29 UC-08 子流程运行拓扑 UI 闭环

本轮目标：在子流程拓扑查询 API 已闭合的基础上，把父子运行追踪入口接入规则调试页，让业务/运维人员不用直接查审计表或调用接口，也能从运行历史查看子流程节点和父子边。

已完成：

- `RuleDesigner.vue` 在运行历史存在时展示 `Run topology` 区域，并为每条运行提供 `Topology <runId>` 操作。
- 点击拓扑按钮会调用 `ruleApi.subprocessRunTopology(ruleId, runId)`，读取 `GET /api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology` 的结果。
- 页面新增 `Subprocess topology` 展示区，按节点展示 `parent/subprocess run`、规则 ID 和状态，按边展示触发节点、父运行、子运行和执行结果。
- 切换规则时会清空旧拓扑，避免跨规则残留误导排障。
- 本轮没有新增图形库，先用可读列表闭合生产排障最小能力，降低前端复杂度和交付风险。

验证证据：

- RED：`npm run e2e -- rule-engine.spec.ts` 首次失败在等待 `Topology 100`，证明旧页面缺少子流程拓扑入口。
- GREEN：`npm run typecheck` 通过，确认 Vue 3 + TypeScript 模板和新增类型无编译错误。
- GREEN：`npm run e2e -- rule-engine.spec.ts` 通过 `1` 条，覆盖从生产运行历史点击 `Topology 100`，并看到 `Subprocess topology`、`parent run 100`、`subprocess run 200` 和 `riskSubprocess · 100 -> 200`。
- GREEN：`npm run test -- src/api/apiContracts.test.ts` 通过 `26` 条，确认拓扑 API 契约仍然稳定。

结论：

- 当前 `UC-08` 子流程运行已具备最小可视化追踪闭环：父规则运行、子流程运行和触发节点可以在规则页面直接查看，满足生产排障入口的基础要求。
- 剩余生产级差距继续保留为跨流程审批恢复、执行耗时/节点数配额、独立人工审批流设计器、完整补件/重新提交状态机和更高阶画布约束。

## 220. 2026-06-29 UC-08 规则生产运行节点数配额闭环

本轮目标：在子流程深度限制和拓扑排障能力之外，继续补齐生产执行资源保护边界，避免超长但无环的规则画布在客户环境中生成过长 trace 并触发大量动作执行。

已完成：

- `RuleApplicationService` 新增 `MAX_PRODUCTION_TRACE_NODES=128`，作为生产运行单次 debug trace 的节点预算。
- `execute(ruleId, request)` 在 `domainService.executeDebug()` 返回后、保存成功 production run 前执行 `ensureProductionTraceWithinBudget(output)`。
- 当 `evaluatedNodes` 超过预算时，系统抛出 `IllegalStateException("rule execution node budget exceeded...")`。
- 超预算失败复用既有 `rule_production_run` failed 审计路径，保留 `runType=production`、`versionId`、失败 runId 和 `errorMessage`，不会先写入误导性的 succeeded production run。
- 本轮先闭合可稳定验证的节点数配额；耗时预算后续可在同一执行预算模型上通过可注入时钟/计时器继续扩展。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence" test` 首次失败在 `Expecting code to raise a throwable`，证明旧实现允许 129 个节点的线性规则生产运行成功。
- GREEN：同一测试通过 `1` 条，验证 129 节点线性规则被拒绝，并在 `rule_production_run` failed 审计中保留 `rule execution node budget exceeded`。
- GREEN：后端邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence,RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` 通过 `7` 条。

结论：

- 当前 `UC-08` 规则生产运行已具备最小节点数配额治理：无环但超长的规则画布会被阻断并留下审计证据，降低生产环境资源失控风险。
- 剩余生产级差距继续保留为跨流程审批恢复、执行耗时预算、独立人工审批流设计器、完整补件/重新提交状态机和更高阶画布约束。

## 221. 2026-06-29 UC-08 规则生产运行耗时预算闭环

本轮目标：在节点数配额之外，继续补齐生产执行耗时保护，避免 debug 计算已经明显超时的规则继续保存成功运行并触发后续动作副作用。

已完成：

- `RuleApplicationService` 新增 `MAX_PRODUCTION_EXECUTION_MILLIS=30000`，作为生产运行 debug 阶段的最大耗时预算。
- `RuleApplicationService` 增加可注入 `LongSupplier nanoTimeSource`，生产默认仍使用 `System::nanoTime`，测试可使用可控时间源稳定验证超时路径。
- `execute(ruleId, request)` 在 `domainService.executeDebug()` 返回后、保存 succeeded production run 前执行 `ensureProductionExecutionWithinDurationBudget(startedAt)`。
- 当耗时超过预算时，系统抛出 `IllegalStateException("rule execution duration budget exceeded...")`。
- 超时失败复用既有 `rule_production_run` failed 审计路径，保留失败 run 和 `errorMessage`，不会先写入 succeeded production run，也不会继续触发动作执行。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunRejectsRuleExecutionBeyondDurationBudgetWithAuditEvidence" test` 首次失败在缺少带 `LongSupplier` 的构造器，证明旧实现没有可测试的耗时预算边界。
- GREEN：同一测试通过 `1` 条，验证模拟 31 秒耗时后生产运行被拒绝，并在 `rule_production_run` failed 审计中保留 `rule execution duration budget exceeded`。
- GREEN：后端邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#productionRunRejectsRuleExecutionBeyondDurationBudgetWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence,RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` 通过 `8` 条。

结论：

- 当前 `UC-08` 规则生产运行已具备最小资源预算治理：节点数超限和耗时超限都会在动作执行前失败并留下审计证据。
- 剩余生产级差距继续保留为跨流程审批恢复、独立人工审批流设计器、完整补件/重新提交状态机和更高阶画布约束。

## 222. 2026-06-29 UC-08 跨流程审批恢复闭环

本轮目标：补齐父规则调用含审批子流程时的业务一致性边界，避免子流程审批未通过时父流程继续执行子流程后的通知、任务、Webhook 等下游动作。

已完成：

- `executeSubprocessNode()` 从无返回值改为返回是否已完成子流程；当子流程生成 pending 审批记录时，写入 `rule_subprocess_run` pending 审计并中断父流程下游执行。
- pending 审计保留 `parentRuleId/parentRunId/nodeId/subprocessRuleId/subprocessRunId/subprocessVersionId/pendingReason=approval`，作为父子流程恢复点。
- `resumeRuleActionsAfterApproval()` 在子流程审批通过并恢复子流程自身下游后，会查找对应 pending 子流程审计，定位父规则、父运行和父 subprocess 节点。
- 父流程恢复时从父 subprocess 节点之后继续执行下游动作，并追加一条 `rule_subprocess_run` succeeded 审计，记录 `resumedFromPendingLogId`。
- rejected 分支中的子流程调用同样尊重 pending 语义，遇到子流程审批时停止当前分支，避免驳回补件链路提前继续。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#parentProductionRunWaitsForSubprocessApprovalBeforeContinuingDownstreamActions" test` 首次失败在父流程提前触发 `parentNotify`，证明旧实现没有等待子流程审批。
- GREEN：同一测试通过 `1` 条，验证父执行后仅产生子审批待办且不会触发 `parentNotify`；批准子审批后，系统先触发 `childNotify`，再恢复父流程触发 `parentNotify`，并留下 `rule_subprocess_run` succeeded 审计。
- GREEN：后端邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#parentProductionRunWaitsForSubprocessApprovalBeforeContinuingDownstreamActions,RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleExecutionBeyondDurationBudgetWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` 通过 `10` 条。

结论：

- 当前 `UC-08` 子流程已具备最小跨流程审批恢复能力：父流程会等待子流程审批闭环后再继续下游，避免审批闸门被子流程边界绕过。
- 剩余生产级差距继续保留为独立人工审批流设计器、完整补件/重新提交状态机和更高阶画布约束。

## 223. 2026-06-29 UC-08 子流程待审批运行状态闭环

本轮目标：补齐跨流程审批恢复后的客户可见状态语义，避免含审批的子流程已经阻断父流程时，子流程运行列表和拓扑仍显示 `succeeded`，造成运维和业务误判。

已完成：

- `RuleDebugRun` 新增 `withStatus()`，支持在同一运行实例上更新业务状态。
- `RuleRepository` 新增 `updateDebugRun()`，`InMemoryRuleRepository` 和 `JdbcRuleRepository` 均已实现，保证内存测试路径和真实 PostgreSQL 路径一致。
- `executeSubprocessNode()` 在发现子流程生成 pending approval 后，会把子流程 production run 状态更新为 `pending_approval`，再写入 `rule_subprocess_run` pending 审计并中断父流程下游。
- `resumeParentRuleActionsAfterSubprocessApproval()` 在子流程审批完成并恢复父流程前，会把对应子流程 run 状态恢复为 `succeeded`。
- 运行列表和拓扑继续复用 `RuleDebugRun.status()`，无需新增 API 字段即可展示 `pending_approval` / `succeeded` 状态变化。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#subprocessRunWithPendingApprovalIsMarkedPendingApprovalUntilApprovalCompletes" test` 首次失败在子流程 run 仍为 `status=succeeded`，证明旧实现会误报待审批子流程为成功。
- GREEN：同一测试通过 `1` 条，验证子流程等待审批时运行状态为 `pending_approval`，审批通过后同一 `runId` 恢复为 `succeeded`。
- GREEN：后端邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#subprocessRunWithPendingApprovalIsMarkedPendingApprovalUntilApprovalCompletes,RuleApplicationServiceTest#parentProductionRunWaitsForSubprocessApprovalBeforeContinuingDownstreamActions,RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleExecutionBeyondDurationBudgetWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` 通过 `11` 条。

结论：

- 当前 `UC-08` 子流程待审批状态已具备最小生产语义闭环：子流程等待审批时不再误显示为成功，审批完成后再回到成功状态，便于运行列表、拓扑 UI 和审计排障保持一致。
- 剩余生产级差距继续保留为独立人工审批流设计器、完整补件/重新提交状态机、pending 审计查询优化和更高阶画布约束。

## 224. 2026-06-29 UC-08 子流程审批恢复审计查询优化闭环

本轮目标：修复跨流程审批恢复中的生产规模风险，避免审批通过后为了定位父流程恢复点而扫描全量审计日志。

已完成：

- `AuditRepository` 新增 `findByOperationAndDetail(operationType, detailKey, detailValue)`，用于按操作类型和审计明细字段查询。
- `JdbcAuditRepository` 使用 PostgreSQL JSONB `detail ->> ? = ?` 实现同名方法，真实数据库路径不再回退到全量 `operation_logs` 扫描。
- `resumeParentRuleActionsAfterSubprocessApproval()` 改为按 `operationType=rule_subprocess_run` 与 `detail.subprocessRunId` 精确查询 pending link，再过滤 `resourceType=rule` 和 `result=pending`。
- 测试新增 `QueryOnlyAuditRepository`，禁止恢复路径调用 `findPage(1, Integer.MAX_VALUE)`，锁定“不全量扫描”的行为边界。

验证证据：

- RED：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#subprocessApprovalResumeQueriesPendingAuditBySubprocessRunIdWithoutFullAuditScan" test` 首次失败于 `full audit scan is not allowed for subprocess approval resume`，证明旧恢复路径确实扫描全量审计。
- GREEN：同一测试通过 `1` 条，验证恢复路径调用 detail 查询且 `fullScanCount=0`，父流程下游 `parentNotify` 正常恢复。
- GREEN：后端邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#subprocessApprovalResumeQueriesPendingAuditBySubprocessRunIdWithoutFullAuditScan,RuleApplicationServiceTest#subprocessRunWithPendingApprovalIsMarkedPendingApprovalUntilApprovalCompletes,RuleApplicationServiceTest#parentProductionRunWaitsForSubprocessApprovalBeforeContinuingDownstreamActions,RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleExecutionBeyondDurationBudgetWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` 通过 `12` 条。

结论：

- 当前 `UC-08` 跨流程审批恢复已具备最小生产级查询边界：恢复父流程时按子流程 run 精确查 pending 审计，不再依赖全量审计扫描。
- 剩余生产级差距继续保留为独立人工审批流设计器、完整补件/重新提交状态机和更高阶画布约束。

## 225. 2026-06-29 UC-08 审批驳回补件重新提交闭环

本轮目标：补齐审批被驳回后的“补件要求 -> 用户重新提交 -> 生成新待审批记录”最小生产闭环，避免驳回后只能停留在终态，无法回到业务可继续处理的审批流程。

已完成：

- `RuleApplicationService.handleApprovalRecord()` 在 `action=reject` 时，保留原审批记录为 `rejected`，并创建一条辅助 `supplement_required` 记录表达补件要求。
- 补件辅助记录不绑定 `assigneeRole`，避免污染审批角色聚合、会签/或签统计和待办角色列表。
- 新增 `RuleApplicationService.submitApprovalSupplement(ruleId, rejectedApprovalRecordId, request)`：只接受源记录为 `rejected` 的审批记录；将对应补件记录更新为 `resubmitted`；再为同一规则运行、节点和审批角色创建新的 `pending` 审批记录。
- 新增 REST 端点 `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplements`，使用 `rule:debug` 权限，并复用服务层状态机约束。
- 审批记录查询状态白名单补充 `supplement_required` 与 `resubmitted`，便于审批中心按补件状态筛选。
- 驳回、补件要求和重新提交分别写入审计：`rule_approval_supplement_required`、`rule_approval_supplement_resubmitted`。
- 审批分组统计调整为：活跃审批只统计当前 pending/approved 组；历史终态展示历史组；补件辅助记录不参与审批组统计，避免回归影响会签/或签和驳回关闭兄弟待办。

验证证据：

- GREEN：`.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectedApprovalCreatesSupplementRequestAndResubmissionCreatesNewPendingApproval" test` 通过 `1` 条，覆盖驳回后生成补件要求、补件重新提交后生成新待审批记录。
- GREEN：审批邻近回归 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectedApprovalCreatesSupplementRequestAndResubmissionCreatesNewPendingApproval,RuleApplicationServiceTest#rejectedApprovalExecutesConfiguredRejectedBranchOnly,RuleApplicationServiceTest#rejectingOneApprovalClosesSiblingPendingApprovalRecordsInSameGroup,RuleApplicationServiceTest#sequentialApprovalNodesCreateNextLevelOnlyAfterPriorApproval,RuleApplicationServiceTest#approvalAllModeWaitsForEveryAssigneeBeforeResumingDownstreamActions,RuleApplicationServiceTest#approvalAnyModeResumesAfterFirstAssigneeAndDoesNotRepeatDownstreamActions,RuleApplicationServiceTest#subprocessApprovalResumeQueriesPendingAuditBySubprocessRunIdWithoutFullAuditScan,RuleApplicationServiceTest#subprocessRunWithPendingApprovalIsMarkedPendingApprovalUntilApprovalCompletes,RuleApplicationServiceTest#parentProductionRunWaitsForSubprocessApprovalBeforeContinuingDownstreamActions,RuleApplicationServiceTest#productionRunExecutesSubprocessRuleAndWritesParentChildAudit,RuleApplicationServiceTest#listsSubprocessRunTopologyForParentProductionRun,RuleApplicationServiceTest#productionRunRejectsIndirectSubprocessCycleWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsSubprocessDepthBeyondProductionLimitWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleExecutionBeyondDurationBudgetWithAuditEvidence,RuleApplicationServiceTest#productionRunRejectsRuleTraceBeyondNodeBudgetWithAuditEvidence,RuleApplicationServiceTest#routesOverdueSlaAlertToHighestMatchedEscalationPolicy" test` 通过 `16` 条。
- GREEN：合同测试 `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard" test` 通过 `1` 条，锁定补件接口权限、服务调用和状态筛选枚举。

结论：

- 当前 `UC-08` 已具备审批驳回后的最小补件/重新提交后端闭环，业务人员可以从驳回态恢复到新的待审批态，并保留审计证据。
- 剩余缺口：前端审批待办/审批详情尚未接入补件提交表单；独立人工审批流设计器、可复用审批模板和更高阶画布约束仍需后续迭代。

## 226. 2026-06-29 UC-08 审批补件重新提交前端闭环

本轮目标：把 225 已完成的后端补件状态机接入原型用户路径，让业务人员可以在审批中心从驳回历史直接提交补件，并看到审批重新回到待处理状态。

已完成：

- `ruleApi.submitApprovalSupplement(ruleId, approvalRecordId, payload)` 接入 `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplements`。
- `ruleApi.listPendingApprovalRecords()` 状态类型扩展为 `pending/approved/rejected/closed/supplement_required/resubmitted`，与后端白名单保持一致。
- `ApprovalInbox.vue` 增加 `Supplement required` 与 `Resubmitted` 状态视图，便于业务/运营查看补件要求和已重新提交记录。
- `Rejected` 视图中的驳回记录新增补件说明和证据 URL 输入，点击 `Submit supplement` 后调用补件接口、刷新当前列表，并提示 `Supplement submitted and approval returned to pending review.`。
- 补件表单只出现在 `rejected` 记录上，不影响 pending 的批准、驳回、提醒和批量审批动作。

验证证据：

- RED：`npm run test -- src/api/apiContracts.test.ts` 首次失败于 `ruleApi.submitApprovalSupplement is not a function`。
- RED：`npm run e2e -- approval-inbox.spec.ts -g "submits supplement"` 首次失败于找不到 `Supplement comment` 输入。
- GREEN：`npm run test -- src/api/apiContracts.test.ts` 通过 `26` 条，锁定补件 API、补件状态查询和既有前端 API 契约。
- GREEN：`npm run e2e -- approval-inbox.spec.ts -g "submits supplement"` 通过 `1` 条，覆盖 Rejected 视图补件提交、payload、Pending 视图刷新和 Resubmitted 视图展示。
- GREEN：`npm run typecheck` 通过。
- GREEN：`npm run e2e -- approval-inbox.spec.ts` 通过 `6` 条，确认批准、驳回、补件、提醒、筛选和批量审批路径没有回归。

结论：

- 当前 `UC-08` 审批驳回补件/重新提交已经具备前后端最小可交付闭环，原型用户可以完成“驳回 -> 补件 -> 新待审批”的端到端操作。
- 剩余缺口：独立人工审批流设计器、可复用审批模板、补件附件真实上传选择器和更高阶画布约束仍需后续迭代。
