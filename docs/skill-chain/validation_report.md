# OpenSpec + API 契约校验报告

## 概要

- 校验时间：2026-06-22
- 校验范围：OpenSpec specs、S4 API 契约、Java Spring Boot 控制器、Python FastAPI AI 服务、Vue 3 API 客户端与 SSE 组合式函数
- 本轮目标：修复 S20 暴露的 3 个失败项
- 结论：3 个失败项已补齐为可追溯契约与代码实现；Maven Wrapper 已补充并通过 Java 单元测试

## 已修复失败项

| 原失败项 | 当前状态 | 关键证据 |
| --- | --- | --- |
| `/api/v1/auth/login` 归属不清 | 通过。登录归 Higress/OIDC 外部入口，Java 暴露 `/api/v1/auth/me` | `docs/skill-chain/api_contract.md`、`PermissionController.currentUser`、`Login.vue`、`authApi.ts` |
| `/api/v1/documents/upload` Java 编排缺失 | 通过。Java 接收 multipart 上传，写入 MinIO，持久化 PostgreSQL 元数据，并发布文档解析事件；RocketMQ 物理 topic 为 `document_parse_requested`，业务事件类型/tag 为 `document.parse.requested` | `KnowledgeController.uploadDocument`、`KnowledgeApplicationService.uploadDocument`、`MinioDocumentStorage`、`JdbcKnowledgeDocumentRepository`、`RocketMqProducerAdapter`、`knowledgeApi.uploadDocument` |
| SSE 事件格式不统一 | 通过。统一为 `stage | delta | references | error | done` JSON schema | `SseEvent.java`、`backend/python-ai-service/app/main.py`、`useSseStream.ts`、`docs/skill-chain/api_contract.md` |

## API 一致性复核

| 接口 | 契约 | 后端实现 | 前端调用 | 状态 |
| --- | --- | --- | --- | --- |
| `GET /api/v1/auth/me` | 当前用户 Profile | Java `PermissionController.currentUser` | `authApi.me` / `authStore.loadCurrentUser` | 通过 |
| `POST /api/v1/documents/upload` | Multipart 文件上传 | Java `KnowledgeController.uploadDocument` | `knowledgeApi.uploadDocument` | 通过 |
| `GET /api/v1/reports/generation-tasks/{taskId}/stream` | SSE 事件流 | Java `SseEvent` | `useSseStream` | 通过 |
| `POST /api/v1/chat` | Python AI 内部 SSE 入口 | Python `create_sse_event` | 对外不作为主报告任务入口 | 通过，归属已澄清 |

## SSE Schema

```json
{
  "type": "stage | delta | references | error | done",
  "taskId": "task-001",
  "content": "text",
  "stage": "retrieval | analysis | writing | export",
  "references": [],
  "progress": 0.6,
  "errorCode": null,
  "traceId": "trace-001"
}
```

## 残余风险

| 风险 | 影响 | 建议 |
| --- | --- | --- |
| Maven 依赖环境 | 已通过 Maven Wrapper 执行 Java 测试；IDEA 可继续使用项目 wrapper 与本地 settings | CI 使用 `./mvnw`，本地可按 IDEA 配置使用企业 Maven settings |
| 本地 Docker 集成烟测 | 通过。已复用本地 Docker 中已有 PostgreSQL、MinIO、RocketMQ，上传接口 HTTP 200，DB/MinIO/RocketMQ 均有写入证据；2026-06-23 已补充最小化部署 OpenSearch/Higress，且 2026-06-25 已验证 Higress `/api/v1/**` 真实进入 Java 业务核心 | 上传状态见 `docs/skill-chain/upload_infrastructure_status.md`；网关与全文检索状态见 `docs/skill-chain/local_gateway_search_status.md` |
| Python `/api/v1/chat` 对外暴露边界 | 已收敛。Higress 外部路由不再直连 Python AI 服务，`/api/v1/**` 统一进入 Java 业务核心 | `config/higress/routes.yaml` 仅公开 `java-report-core`；Python `/api/v1/chat` 保留为内部服务/RocketMQ 执行面 |

## 结论

S20 三个失败项已按“契约先行、Java 业务核心归口、Python AI 解耦执行、前端统一消费”的方向修复。Java Maven 测试已通过；MinIO/PostgreSQL/RocketMQ 本地 Docker 写入烟测已通过。OpenSearch 已完成写读删烟测；Higress 网关与 Console 已启动并可访问，且 `GET http://127.0.0.1:18000/api/v1/reports` 已返回 Java 业务 `401` JSON，`POST http://127.0.0.1:18000/api/v1/share-links/nonexistent/access` 与 `POST http://127.0.0.1:18082/api/v1/share-links/nonexistent/access` 已一致返回 `404 share link not found: nonexistent`，说明 `/api/v1/** -> java-report-core` 业务路由已完成真实绑定验证。S22 Playwright E2E 已补充可执行配置与 SSE、上传、分享、权限场景，后续可继续扩展真实后端联调覆盖。
