# 上传基础设施补强状态

日期：2026-06-22

## 当前结论

`POST /api/v1/documents/upload` 已从骨架补强为真实上传编排，并已在本地复用 Docker 中间件完成写入烟测：Java 业务核心接收 multipart 文件，写入 MinIO，持久化 PostgreSQL 元数据，并发布文档解析事件。业务事件类型保持 `document.parse.requested`，RocketMQ 物理 topic 使用合法名称 `document_parse_requested`，事件类型作为 tag 与消息体字段保留。

## 已复用的本地 Docker 基础设施

| 组件 | 容器 | 本地端口 | 状态 |
| --- | --- | --- | --- |
| PostgreSQL | `ir-postgres` | `5432` | running, healthy |
| MinIO | `ir-minio` | `9000`, `9001` | running, healthy |
| RocketMQ NameServer | `ir-rocketmq-namesrv` | `9876` | running, healthy |
| RocketMQ Broker | `ir-rocketmq-broker` | `10909`, `10911`, `10912` | running, healthy |

上传链路烟测本身未启动、创建或部署任何新容器。2026-06-23 已按用户确认补充最小化部署 OpenSearch 与 Higress，状态见 `docs/skill-chain/local_gateway_search_status.md`。

## 代码补强

| 能力 | 状态 | 文件 |
| --- | --- | --- |
| MinIO 对象写入 | 已实现真实 `MinioClient` 适配，支持 bucket 准备和对象写入 | `MinioDocumentStorage`、`MinioConfig` |
| PostgreSQL 元数据持久化 | 已实现 `file_objects` 与 `knowledge_documents` 最小上传元数据落库 | `JdbcKnowledgeDocumentRepository`、`V001__upload_infrastructure.sql` |
| RocketMQ 事件发布 | 已实现 RocketMQ 4.9.7 producer，并改为 Spring 托管生命周期；物理 topic 为 `document_parse_requested`，业务 tag 为 `document.parse.requested` | `RocketMqProducerAdapter`、`RocketMqDomainEventPublisher` |
| 上传编排 | 已串联存储、落库、事件发布，返回文档元数据 | `KnowledgeApplicationService.uploadDocument` |

## 验证证据

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| RocketMQ 发布器单测 | `.\mvnw.cmd -pl backend/java-report-core -Dtest=RocketMqDomainEventPublisherTest test` | 1 个测试通过，验证物理 topic 与业务事件类型分离 |
| Java 模块回归 | `.\mvnw.cmd -pl backend/java-report-core test` | 14 个测试通过，0 失败，0 错误 |
| Java 服务健康检查 | `GET http://localhost:18080/actuator/health` | HTTP 200，`status=UP` |
| 上传接口烟测 | `POST http://localhost:18080/api/v1/documents/upload` | HTTP 200，返回 `documentId=2`、`fileObjectId=3`、`parseStatus=pending` |
| PostgreSQL 持久化 | 查询 `file_objects`、`knowledge_documents` | `file_objects.id=3`、`knowledge_documents.id=2`，文件名 `smoke-upload-20260622.txt`，上传人 `7` |
| MinIO 对象存在性 | S3 HEAD Object | HTTP 200，`Content-Length=35`，`Content-Type=text/plain` |
| RocketMQ topic 状态 | `mqadmin topicStatus -n rocketmq-namesrv:9876 -t document_parse_requested` | topic 存在，队列 3 `Max Offset=1`，更新时间 `2026-06-22 14:20:44` |

## 当前烟测策略

本地集成烟测已优先复用已有 Docker 基础设施：PostgreSQL、MinIO、RocketMQ、Milvus、Redis 与 etcd。OpenSearch 与 Higress 已按最小资源配置补部署；上传链路不依赖它们，全文检索与网关路由闭环需要单独执行对应烟测。
