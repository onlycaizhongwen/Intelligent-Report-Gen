<!-- skill: S6 -->
<skill id="S6" name="tech_stack.selector">

# 技能：技术栈选型

## Meta
- DependsOn: S5
- Category: architecture
- Status: stable

## 一句话描述
根据架构方案，推荐完整的技术栈选型（支持 Python / Go / Java 三生态混合选型）。

## 输入
- `docs/skill-chain/architecture.md`：架构设计文档
- `openspec/project.md`：OpenSpec 项目说明
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `docs/skill-chain/tech_stack.md`：技术栈选型文档（中文 Markdown）

## Prompt

你是一位资深技术选型专家。
请根据架构文档，为项目选择技术栈。

**重要：支持跨生态混合选型，根据项目场景灵活搭配 Python + Go + Java。**

技术选型必须追溯 OpenSpec：
- 每个核心技术选择必须说明覆盖的 OpenSpec capability / requirement / scenario。
- 如果 OpenSpec capability 与架构建议冲突，必须暂停并输出冲突报告，不得用技术偏好覆盖需求约束。
- S7 获批后 OpenSpec baseline 冻结，S8-S30 不得偏离本阶段确定的技术与能力映射。

项目约束覆盖规则：
- 若用户、S5 架构文档或既有 S6 产物已明确指定某项技术，必须以该项目约束作为主选型，不得被模板偏好覆盖。
- 若项目约束要求线上模型优先并经 AI Gateway 代理，则本地 vLLM/Ollama/GPU 推理仅作后续适配目标；未明确选择本地推理时不得作为默认交付技术。
- 若项目约束选择 PostgreSQL 16，则 MySQL 仅作为组织标准化时的替代方案。
- 若项目约束选择 RabbitMQ，则 Kafka/RocketMQ 仅作为高吞吐或组织标准化替代方案。

# 核心规则（强制执行）

### 7. 本地开发环境约束（强制执行）

技术选型必须区分 **生产环境** 与 **本地开发环境**：

1. **环境依赖分级**：
   - 生产环境：可使用 Higress / K8s / 托管云服务 / 分布式中间件。
   - 本地开发：**优先使用 Docker Compose 一键拉起**。

2. **本地复用原则**：
   - 若项目未明确指定中间件版本，优先复用本地 Docker 里面的依赖；如果不能复用，本地开发需要按最小资源进行 Docker 部署。
   - 数据库：优先 PostgreSQL / MySQL 单实例容器。
   - 缓存：优先 Redis 单实例容器。
   - 消息队列：优先 RocketMQ / Redis Streams 容器。
   - 向量库：优先 Milvus Standalone / Chroma / Qdrant 容器。

3. **资源最小化原则**：
   - 本地禁止推荐需要 GPU、高内存（>4G）、多节点部署的中间件。
   - 若某技术本地 Docker 无法运行（如某些复杂的 ES 插件或 K8s Operator），**必须降级为替代方案**：
     - 例如：Elasticsearch → PostgreSQL 全文检索 或 OpenSearch 轻量版。

4. **配置隔离**：
   - 必须区分 `application-dev.yml`（本地 H2/内存/容器）与 `application-prod.yml`（生产数据源）。
   - 禁止本地开发连接生产数据库。

---

### 一、前端技术栈

**前端生态选择规则：**
- 若用户、S5 架构文档或项目约束已明确指定前端生态（例如 Vue 3、React、Next.js），S6 必须以该生态作为主选型，并同步调整 UI 组件库、路由、状态管理、请求库、规则画布和 OpenSpec 技术映射。
- 若项目约束选择某一前端生态，默认配套必须跟随该生态选择：Vue 3 可配套 Element Plus / Vue Router / Pinia / TanStack Query for Vue / Axios / Vue Flow；React 可配套 Ant Design 或 MUI / React Router / Redux Toolkit 或 Zustand / Axios / React Flow。
- 不得把 React、Vue 或任一固定前端栈硬编码为通用默认；当 S6 主选型确定后，不得在产物中残留另一生态作为主方案。
- 不得在同一维度同时选择 Vue 和 React 作为主方案；每个维度只能保留一个主力技术栈。

| 维度 | 选项 | 适用场景 |
|------|------|----------|
| **框架** | Vue 3+ / React 18+ / Next.js / Nuxt.js | 按用户或上游约束选择；未指定时根据团队生态、原型和组件需求评估 |
| **语言** | TypeScript | 类型安全，大型项目必备 |
| **UI 组件库** | Element Plus / Ant Design / MUI | Vue 3 生态选 Element Plus；React 生态选 Ant Design/MUI |
| **状态管理** | Pinia / Redux Toolkit / Zustand | Vue 3 配套 Pinia；React 生态选 Redux Toolkit / Zustand |
| **路由** | Vue Router / React Router | 跟随前端框架配套选择 |
| **请求库** | Axios / Fetch / TanStack Query / SWR | 跟随前端框架与服务端状态管理需求选择 |
| **规则画布** | Vue Flow / React Flow | 跟随前端框架配套选择 |
| **AI UI（如需要）** | Streamlit / Gradio / Vercel AI SDK | AI Demo 快速构建 |

---

### 二、Python 生态（AI 核心）

| 维度 | 推荐技术 | 说明 |
|------|----------|------|
| **后端框架** | FastAPI / Flask / Django / Litestar / Sanic | 按性能、团队经验、异步能力和生态约束选择 |
| **AI 框架** | LangChain / LlamaIndex / Semantic Kernel / Haystack / DSPy | **LlamaIndex 做 RAG**，**LangChain 做 Agent** |
| **Agent 框架** | LangGraph / CrewAI / AutoGen / OpenDevin / MetaGPT | **LangGraph 官方 Agent 编排** |
| **向量数据库** | Milvus / Qdrant / Pinecone / Weaviate / Chroma / FAISS | 按数据规模、部署方式、运维能力和检索能力选择 |
| **Embedding 模型** | BGE-M3 / BGE-Large / M3E / text-embedding-3 / Cohere / Jina | **BGE-M3 中文最强开源** |
| **LLM 统一接口** | LiteLLM | 统一 OpenAI / Anthropic / Gemini 接口 |
| **推理加速** | vLLM / TGI / TensorRT-LLM / Ollama / Ray Serve | 仅在明确选择本地推理时评估；未明确选择时不得默认启用 |
| **模型微调** | PEFT / LoRA / QLoRA / Axolotl | 轻量级微调 |
| **BERT 系列** | transformers (bert-base-chinese / roberta-base / bge-large-zh) | 文本分类 / NER / 相似度 / Embedding |
| **文档解析** | Unstructured / PyMuPDF / python-docx | 支持 PDF / Word / TXT |
| **任务队列** | Celery + Redis / ARQ / Ray | 异步任务处理 |
| **Prompt 管理** | PromptLayer / LangSmith / LangFuse（开源）/ Helicone / PortKey | Tracing + 版本管理 + Token 计费 |
| **约束解码** | Guidance / Outlines | 结构化输出控制 |

---

### 三、Go 生态（高性能网关 / 边缘计算）

| 维度 | 推荐技术 | 说明 |
|------|----------|------|
| **后端框架** | Gin / Echo / Fiber / Chi / Kratos | **Gin 最流行**，Kratos（B站微服务框架） |
| **AI SDK** | langchaingo / go-openai / anthropic-go / ollama-go / go-huggingface | Go 调用 LLM API |
| **Agent / 编排** | Temporal Go SDK / Cadence | 长时工作流编排 |
| **向量数据库** | Milvus Go SDK / Qdrant HTTP API | Go 操作向量库 |
| **推理调用** | gRPC → vLLM / triton-inference-server / onnx-go | Go 通过 gRPC 调用推理服务 |
| **任务队列** | Asynq / Machinery / NATS JetStream | Redis 异步队列 |
| **适用场景** | API 网关 / WebSocket 实时通信 / 边缘加速 / 高并发代理 | Go 的并发优势 |

---

### 四、Java 生态（企业级中台）

| 维度 | 推荐技术 | 说明 |
|------|----------|------|
| **后端框架** | Spring Boot 3+ / Quarkus / Micronaut / Vert.x | 按企业生态、启动性能、团队经验和部署约束选择 |
| **微服务治理** | **Spring Cloud Alibaba**（Nacos 注册配置 + Sentinel 限流 + Seata 分布式事务） / Spring Cloud Netflix | 按注册配置、限流熔断、事务治理和组织生态选择 |
| **API 网关 / AI Gateway** | **Higress** / Spring Cloud Gateway / Apache APISIX / Kong | Java + Spring Cloud Alibaba + Kubernetes + AI 场景必须评估 **Higress**；它是阿里巴巴开源、基于 Envoy + Istio 的 K8s 入口网关 + 微服务网关 + AI 网关三合一方案；轻量 JVM 内嵌网关场景可评估 Spring Cloud Gateway |
| **AI 模块** | **Spring AI**（ChatClient / Embedding / RAG）/ langchain4j / Semantic Kernel Java / LangGraph4j | **Spring AI 官方**，统一接入多模型 |
| **向量数据库** | Milvus Java SDK / Elasticsearch 8.x（内置向量检索）/ OpenSearch / Qdrant HTTP | **ES 8.x 向量检索**零额外运维 |
| **BERT 接入** | **DJL（Deep Java Library）** + djl-huggingface（bert-base-chinese / roberta） / ONNX Runtime Java | Java 原生推理 BERT |
| **Agent 编排** | LangGraph4j / Spring State Machine / Temporal Java SDK | 复杂 Agent 流程 |
| **ORM** | MyBatis Plus / JPA / Hibernate | 按团队习惯、领域模型复杂度和数据库访问模式选择 |
| **企业集成** | Apache Camel / Spring Integration / **Debezium（CDC 实时索引）** | 企业系统集成 + 数据实时同步到向量库 |
| **消息队列** | RabbitMQ / Kafka / RocketMQ | RabbitMQ 适合任务异步、重试和 DLQ；Kafka 适合高吞吐数据流；RocketMQ 适合阿里生态和事务消息 |
| **缓存** | Redis / Caffeine | Redis 分布式，Caffeine 本地 |

---

### 五、通用基础设施（三生态共用）

| 维度 | 推荐技术 | 说明 |
|------|----------|------|
| **数据库** | PostgreSQL 16+ / MySQL 8.0 | PostgreSQL 功能更强；MySQL 适合组织既有标准 |
| **缓存** | Redis 7.x | 缓存 + 分布式锁 + Session |
| **消息队列** | RabbitMQ / Kafka / RocketMQ | RabbitMQ 适合任务异步、重试和 DLQ；Kafka 适合高吞吐数据流 |
| **容器化** | Docker / Docker Compose | 开发环境标准化 |
| **编排** | Kubernetes | 生产环境弹性伸缩 |
| **API 网关** | **Higress** / Spring Cloud Gateway / Kong / Nginx / Go Gin | 统一入口；企业 Java / Spring Cloud Alibaba / Kubernetes / AI Gateway 场景必须评估 Higress，是否采用取决于部署和治理约束 |
| **监控** | Prometheus + Grafana | 指标采集 + 可视化 |
| **日志** | ELK / Loki + Promtail | 日志聚合 |
| **CI/CD** | GitHub Actions / GitLab CI / Jenkins | 持续集成 |
| **配置中心** | Nacos / Apollo / Consul | 统一配置管理 |

---

### 六、混合选型推荐方案（根据项目场景）

| 项目场景 | 推荐方案 | 理由 |
|----------|----------|------|
| **AI 中台 + 企业业务** | Java（Spring Cloud Alibaba 微服务）+ Python（FastAPI 推理）+ **Higress（API 网关 / AI Gateway）** | 业务稳定 + AI 灵活 + 统一流量治理和模型 API 治理 |
| **RAG 知识库系统** | Python（LlamaIndex 检索）+ **Higress（API 网关 / AI Gateway）** + Java（Spring Boot 管理后台） | Python AI 生态最强 + Java 企业级 + Higress 统一入口与 AI 网关能力 |
| **Agent 自动化平台** | Python（LangGraph Agent）+ Go（WebSocket 实时通信）+ Java（Quarkus 任务调度） | Agent 编排 + 实时推送 + 调度可靠 |
| **多模态 AI 应用** | Python（多模态推理）+ Java（Spring AI 统一接入）+ Go（Fiber 边缘加速） | 模型灵活 + 统一接口 + 边缘性能 |
| **传统系统 AI 增强** | Java（现有 Spring Boot）+ Python（FastAPI AI 微服务）+ Milvus（共享向量库） | 最小改动 + AI 能力注入 |
| **AI SaaS 平台** | Go（Gin 主服务）+ Python（vLLM 推理集群）+ Vue 3 或 React（按项目约束选择前端） | Go 全栈 + Python 专注推理；前端生态不得覆盖用户指定约束 |
| **企业知识管理** | Java（Spring Cloud Alibaba + ES 向量）+ Python（BERT Embedding）+ Vue 3（管理前端） | 企业级治理 + 中文语义理解 |

### Higress 选型规则（Java / Kubernetes / AI 网关场景）

| 能力域 | Higress 必须覆盖的能力 | 选型说明 |
|--------|------------------------|----------|
| K8s 入口网关 | 兼容 Kubernetes Ingress API / Gateway API，支持 gRPC、WebSocket、HTTP/2，可替代 Nginx Ingress Controller | 有 Kubernetes 集群入口、南北向流量统一入口诉求时评估 |
| 微服务网关 | 对接 Nacos / Eureka / Consul 服务发现，集成 Dubbo / Sentinel，支持 API 路由、灰度发布、限流熔断 | Java / Spring Cloud Alibaba / Dubbo 体系可评估 |
| AI 网关 | 统一代理 OpenAI、通义千问等 100+ 大模型 API，支持多模型 Fallback、Token 级限流/计费、Prompt 安全过滤、语义缓存、MCP Server 代理 | AI 中台、RAG、Agent、LLM Gateway 场景可评估 |
| 安全与可观测 | WAF、JWT/OIDC 认证鉴权、HTTPS 证书自动续签、Prometheus Metrics、链路追踪、Wasm 插件热更新（Go/Rust/JS） | 生产级网关必须输出安全与观测方案 |

---

### 七、输出要求

1. 明确标注：项目类型属于「传统项目」/「AI 项目」/「混合项目」
2. 给出**完整技术栈表格**，包含：前端 / 后端 / AI / 数据库 / 中间件 / 部署
3. 每个选型必须包含**理由**
4. 如果是混合选型，必须给出**服务间通信协议**和**数据流转图**
5. 给出**技术栈架构图**，必须使用 Mermaid
6. 如选择 Java / Spring Cloud Alibaba / Kubernetes / AI 项目，必须明确说明是否采用 Higress；若不采用，必须给出拒绝理由
7. 必须给出**技术栈到服务边界映射表**，说明每个候选服务使用 Java、Python、Go、Higress 或共享基础设施的原因
8. 必须给出 **OpenSpec capability 到技术栈映射表**，确保每个核心能力都有实现技术依据

### 八、技术栈到服务边界映射

| 服务/模块边界 | 推荐技术 | 部署形态 | 通信协议 | 选择理由 |
|---------------|----------|----------|----------|----------|

- Java 通常适合承载企业业务、事务一致性、权限、管理后台、微服务治理。
- Python 通常适合承载 AI 推理、RAG、Embedding、文档解析、Agent 编排等算法/模型密集能力。
- Higress 在被选用时承载统一入口、Ingress/Gateway、微服务路由治理、AI Gateway、MCP Server 代理、安全与可观测。
- Go 仅在明确需要自研高并发边缘代理、WebSocket 中继或非 Higress 的轻量网关时使用。

### 九、OpenSpec capability 到技术栈映射

| OpenSpec capability | Requirement / Scenario | 推荐技术 | 服务/部署边界 | 选择理由 |
|---------------------|------------------------|----------|---------------|----------|

## 行为规则

- ✅ 必须根据项目类型选择技术栈
- ✅ 若用户或上游阶段已指定前端生态，必须以指定生态作为主选型，并同步调整 UI 组件库、路由、状态管理、请求库、规则画布和技术映射
- ✅ 若项目约束选择 Vue 3 + TypeScript，产物中不得把 React 18、Ant Design、React Router、Redux Toolkit 或 React Flow 写成主方案；反之亦然
- ✅ 每个核心技术选择必须追溯 OpenSpec capability / requirement / scenario
- ✅ AI 项目必须包含向量数据库和 Embedding 模型
- ✅ 混合项目必须明确服务间通信方式
- ✅ Java / Spring Cloud Alibaba 技术栈中涉及统一入口、Ingress、API 网关、AI Gateway、LLM Gateway 或模型 API 治理时，必须将 **Higress** 纳入候选并给出采用或拒绝理由
- ✅ 选择 Higress 时，必须覆盖 K8s 入口网关、微服务网关、AI 网关、安全与可观测四类能力
- ✅ 必须区分生产环境与本地开发环境；本地开发优先复用已有 Docker 依赖，不能复用时按最小资源 Docker 部署
- ✅ 本地开发配置必须与生产配置隔离，明确 `application-dev.yml` 与 `application-prod.yml` 边界，禁止本地连接生产数据库
- ✅ 必须为 S7 输出可使用的服务边界技术映射，避免后续模块设计重新猜技术栈
- ✅ 所有选型必须有中文理由
- ✅ 优先选择社区活跃、文档完善的技术
- ❌ 不得选择与 OpenSpec frozen baseline 冲突的技术路径
- ❌ 禁止选择过时或不维护的技术
- ❌ 禁止混合使用冲突的技术栈
- ❌ 禁止"什么都选"（每个维度只选一个主力方案）

## 使用示例

```
加载 <skill id="S6">，输入：docs/skill-chain/architecture.md、openspec/project.md、openspec/specs/
请基于 docs/skill-chain/architecture.md、OpenSpec 和用户约束输出完整技术选型方案，包含服务间通信协议。
```
</skill>
<!-- end -->
