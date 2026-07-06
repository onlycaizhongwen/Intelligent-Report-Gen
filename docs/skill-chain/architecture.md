# 智能报告生成系统架构设计

> Skill：S5 architecture.design  
> 约束：所有架构图必须使用 Mermaid；后端代码骨架遵循 DDD 分层；文档集中在 `docs/skill-chain/`。

## 1. 架构目标

系统采用 Java DDD 模块化单体业务核心 + Python AI 服务 + Higress 统一网关 + RocketMQ 异步任务 + PostgreSQL/Redis/OpenSearch/Milvus/MinIO 基础设施。

核心目标：

- 让报告生成、知识入库、RAG、导出、权限协作和审计形成闭环。
- 外部业务 API 统一进入 Java 业务核心，避免 Python AI 服务绕过任务状态、RBAC 和审计。
- AI 相关能力与业务事务解耦，通过 RocketMQ 和受控内部调用协作。
- 本地开发通过 Docker Compose 复用或最小化部署依赖。

## 2. 分层架构图

```mermaid
flowchart TB
  User["浏览器 / 外部分享访问"]
  Gateway["Higress<br/>入口网关 + 微服务网关 + AI Gateway"]

  subgraph Frontend["前端层"]
    Web["Vue 3 + TypeScript Web Console"]
  end

  subgraph Java["Java 业务核心"]
    Interfaces["interfaces<br/>Controller / DTO / SSE"]
    Application["application<br/>UseCase / 事务编排"]
    Domain["domain<br/>Aggregate / Entity / Domain Service"]
    Infra["infrastructure<br/>DB / MQ / S3 / Gateway Adapter"]
  end

  subgraph AI["Python AI 服务"]
    AiApi["内部 API / MQ Consumer"]
    Parse["文档解析 OCR / 表格 / 扫描件"]
    Rag["RAG 检索 / 重排 / 上下文压缩"]
    Llm["模型调用 / Prompt Guard / 引用评分"]
  end

  subgraph Data["数据与中间件"]
    PG["PostgreSQL"]
    Redis["Redis"]
    MQ["RocketMQ"]
    MinIO["MinIO"]
    OS["OpenSearch"]
    Milvus["Milvus"]
  end

  User --> Web --> Gateway --> Interfaces
  Interfaces --> Application --> Domain
  Application --> Infra
  Infra --> PG
  Infra --> Redis
  Infra --> MQ
  Infra --> MinIO
  MQ --> AiApi
  AiApi --> Parse
  AiApi --> Rag
  AiApi --> Llm
  Parse --> MinIO
  Rag --> OS
  Rag --> Milvus
  Llm --> Gateway
```

## 3. C4 Container 图

```mermaid
flowchart LR
  Person["用户 / 外部协作用户"]
  Web["web-console<br/>Vue 3 + TypeScript"]
  Gateway["Higress<br/>Gateway / WAF / AI Gateway"]
  Java["java-report-core<br/>Spring Boot 3 DDD"]
  Python["python-ai-service<br/>FastAPI AI Pipeline"]
  PG["PostgreSQL"]
  MQ["RocketMQ"]
  MinIO["MinIO"]
  Search["OpenSearch"]
  Vector["Milvus"]
  Redis["Redis"]

  Person --> Web
  Web --> Gateway
  Gateway --> Java
  Java --> PG
  Java --> Redis
  Java --> MQ
  Java --> MinIO
  MQ --> Python
  Java -.internal controlled call.-> Python
  Python --> Search
  Python --> Vector
  Python --> MinIO
  Python --> Gateway
```

## 4. 组件图

```mermaid
flowchart TB
  subgraph Java["java-report-core"]
    Report["report<br/>任务/大纲/正文/SSE"]
    Citation["citation<br/>引用/导出/版本"]
    Knowledge["knowledge<br/>知识库/上传/数据源"]
    Permission["permission<br/>RBAC/分享/协作"]
    Rule["rule<br/>规则画布/调试"]
    Audit["audit<br/>历史/审计/仪表盘"]
    Shared["shared<br/>安全/API/事件/错误"]
  end

  subgraph Python["python-ai-service"]
    Doc["document_processing"]
    Retrieval["rag_retrieval"]
    LLM["llm_orchestration"]
    Score["citation_evaluation"]
    Kernel["shared_kernel"]
  end

  Report --> Knowledge
  Report --> Citation
  Report --> Permission
  Report --> Audit
  Knowledge --> Audit
  Permission --> Audit
  Rule --> Audit
  Shared --> Report
  Shared --> Knowledge
  Shared --> Permission
  Doc --> Retrieval
  Retrieval --> LLM
  LLM --> Score
  Kernel --> Doc
  Kernel --> Retrieval
  Kernel --> LLM
```

## 5. 关键通信

| 通信 | 协议 | 说明 |
| --- | --- | --- |
| 前端到 Higress | HTTPS / SSE | 浏览器和外部分享统一进入网关 |
| Higress 到 Java | HTTP REST / SSE | Java 是外部业务 API 归口 |
| Java 到 Python | RocketMQ 优先，内部 REST 受控补充 | 长耗时 AI 流程解耦 |
| Python 到模型供应商 | HTTPS via Higress AI Gateway | 便于 Prompt 安全、Token 限流、多模型路由 |
| Java/Python 到存储 | JDBC / S3 / HTTP / SDK | 主数据、对象、全文、向量 |

## 6. ADR 摘要

| ADR | 决策 | 理由 |
| --- | --- | --- |
| ADR-001 | Java DDD 模块化单体 + Python AI 服务 | 保持业务一致性，同时隔离 AI 技术栈 |
| ADR-002 | Higress | 覆盖入口、微服务网关、AI Gateway、安全和可观测 |
| ADR-003 | PostgreSQL 作为主数据 | 适合业务数据、审计、JSON 扩展和复杂查询 |
| ADR-004 | RocketMQ 异步任务 | 支撑生成、解析、导出、同步任务的重试和补偿 |
| ADR-005 | Python AI 服务可直接消费 RocketMQ | 避免 AI 执行链与 Java 服务强耦合 |
| ADR-006 | 线上 GPT 优先，保留多模型路由 | 满足当前使用约束并保留演进能力 |

## 7. 范围护栏

当前阶段不引入多租户、私有化部署、高可用、灾备或报告人工审核发布流程。历史样例中的“审核”只视为样例文本，不映射为发布审批能力。
