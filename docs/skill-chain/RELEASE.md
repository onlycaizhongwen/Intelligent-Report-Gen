# 智能报告生成系统 v0.1.0 发布说明

**发布日期**：2026-06-22  
**版本类型**：阶段性全栈骨架版本  
**兼容性**：首次发布，无历史升级兼容问题

## 本次更新概览

本版本完成从 S8 到 S30 的全栈交付骨架：安全设计、数据库、迁移、种子数据、Java 业务核心、Python AI 服务、Vue 3 前端、契约校验、测试场景、容器化、CI、部署和监控。所有功能均以冻结后的 OpenSpec baseline 为约束。

## 核心亮点

### 1. 智能报告生成

- 支持自然语言创建报告生成任务，关联 `REQ-REPORT-001`。
- 支持大纲确认后进入正文生成，关联 `REQ-REPORT-002`。
- 保留三阶段流式生成和模型调用审计能力，关联 `REQ-AI-001`。

### 2. 知识库与企业数据源

- 覆盖知识库、知识条目、文件解析、OCR/图片表格识别预留和企业数据源配置，关联 `REQ-KB-001`、`REQ-KB-002`、`REQ-KB-003`。
- 采用 MinIO 存储文档对象，Milvus 承载向量检索，OpenSearch 承载全文检索。

### 3. 引用、导出与版本

- 支持引用来源、证据可信度评分、企业品牌导出模板和报告版本管理，关联 `REQ-REPORT-003`、`REQ-REPORT-004`、`REQ-REPORT-005`。

### 4. 权限、分享与审计

- 使用 RBAC 权限模型并保留扩展能力，关联 `REQ-AUTH-001`。
- 支持报告分享链接、外部用户访问控制、批注评论与任务指派，关联 `REQ-COLLAB-001`、`REQ-COLLAB-002`。
- 保留 Prompt、检索上下文、生成过程和模型调用完整审计，关联 `REQ-AUDIT-001`。

### 5. Higress 三合一网关

- 使用 Higress 作为 K8s 入口网关、微服务网关和 AI 网关。
- 保留多模型 Fallback、Token 级限流、Prompt 安全过滤、语义缓存、MCP Server 代理和 Prometheus 指标能力。
- 外部 `/api/v1/**` 统一路由到 Java 业务核心；Python AI 服务接口仅作为内部服务或 RocketMQ 执行面，避免绕过 RBAC、任务状态和审计闭环。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3 + TypeScript、Element Plus、Pinia、TanStack Query for Vue、Axios |
| Java 后端 | Java 17、Spring Boot 3、MyBatis Plus、Flyway |
| AI 服务 | Python 3.11、FastAPI |
| 主数据 | PostgreSQL 16 |
| 消息队列 | RocketMQ |
| 对象存储 | MinIO |
| 向量检索 | Milvus |
| 全文检索 | OpenSearch |
| 网关 | Higress |
| 监控 | Prometheus、Grafana、Loki、Promtail |

## 部署方式

快速验证：

```bash
cp .env.example .env
docker compose up -d --build
```

详细步骤见 [DEPLOY.md](DEPLOY.md)。

## 已知问题

- 本地 Docker 上传烟测已复用 PostgreSQL、MinIO 与 RocketMQ 并通过；OpenSearch 与 Higress 已于 2026-06-23 按最小资源策略补充部署，状态见 [local_gateway_search_status.md](local_gateway_search_status.md)。
- 当前仍是阶段性全栈骨架版本，报告生成、Python AI 执行链、导出、RBAC 分享协作、审计聚合和企业数据源同步等深层业务能力需要继续迭代。
- Higress `/api/v1/** -> java-report-core` 业务路由已于 2026-06-25 完成真实验证：`GET /api/v1/reports` 经过 `http://127.0.0.1:18000` 返回 Java 业务 `401` JSON，`POST /api/v1/share-links/nonexistent/access` 在 `18000` 与 `18082` 均返回一致的 `404 share link not found`，说明网关已进入 Java 业务链路；后续仍需继续补更完整的外部端到端场景验收。
- OpenSearch 已完成基础写读删烟测，但尚未接入知识解析后的全文索引业务链路。

## 范围说明

本版本不包含多租户、私有化部署、高可用、灾备、报告人工审核发布流程或本地 GPU/vLLM 推理。

[OK] Skill S29 completed
