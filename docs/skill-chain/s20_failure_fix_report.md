# S20 失败项修复报告

## 修复范围

- 认证入口归属：登录由 Higress / OIDC 外部入口负责；Java 不新增生产用户名密码登录入口；Java 提供 `/api/v1/auth/me` 返回当前用户、角色、权限与认证来源。
- 文档上传归属：`POST /api/v1/documents/upload` 由 Java 业务核心承接外部请求，负责 RBAC、对象元数据、审计与 `document.parse.requested` 事件；Python 保留内部解析执行能力。
- SSE 事件格式：Java 报告任务流、Python AI 流、Vue `useSseStream` 统一使用 `stage | delta | references | error | done` JSON 事件。

## 修复明细

| 原失败项 | 修复结果 | 证据 |
| --- | --- | --- |
| `/api/v1/auth/login` 契约不清 | 改为外部 Higress/OIDC 登录；Java 暴露 `/api/v1/auth/me`，前端通过 `authApi.me` 获取当前用户 | `PermissionController.currentUser`、`Login.vue`、`authApi.ts`、`docs/skill-chain/api_contract.md` |
| `/api/v1/documents/upload` Java 编排缺失 | 新增 Java multipart 上传入口，串联 MinIO 对象写入、PostgreSQL 元数据、RocketMQ `document.parse.requested` 事件 | `KnowledgeController.uploadDocument`、`KnowledgeApplicationService.uploadDocument`、`MinioDocumentStorage`、`JdbcKnowledgeDocumentRepository` |
| SSE 字段不统一 | Java `SseEvent`、Python `create_sse_event`、前端 `UnifiedSseEvent` 使用统一字段 | `SseEvent.java`、`backend/python-ai-service/app/main.py`、`useSseStream.ts` |

## 已验证结果

| 验证项 | 命令或方式 | 结果 |
| --- | --- | --- |
| Java 单元测试 | `.\mvnw.cmd -pl backend/java-report-core test` | 14 个测试通过，0 失败，0 错误 |
| 上传接口烟测 | `POST http://localhost:18080/api/v1/documents/upload` | HTTP 200，返回 `documentId=2`、`fileObjectId=3`、`parseStatus=pending` |
| PostgreSQL 持久化 | 查询 `file_objects`、`knowledge_documents` | 文件对象与文档元数据已写入 |
| MinIO 对象存在 | S3 HEAD Object | HTTP 200 |
| RocketMQ topic | `mqadmin topicStatus -n rocketmq-namesrv:9876 -t document_parse_requested` | topic 存在并有 offset |
| OpenSearch 本地烟测 | 临时索引写读删 | 已通过，见 `local_gateway_search_status.md` |
| Higress 本地启动 | Console/Gateway/Envoy ready | 已通过，见 `local_gateway_search_status.md` |

## 残余边界

- S20 失败项已修复，但当前系统仍是阶段性骨架版本。
- 报告生成、RAG、导出、分享协作、审计聚合等业务闭环需要按 `delivery_closure_plan.md` 继续实现。
- Higress 已启动，但 `/api/v1/** -> java-report-core` 业务路由尚未绑定；不能把网关业务烟测视为已完成。
