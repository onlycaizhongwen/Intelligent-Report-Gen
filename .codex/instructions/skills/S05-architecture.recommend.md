<!-- skill: S5 -->
<skill id="S5" name="architecture.recommend">

# 技能：架构推荐

## Meta
- DependsOn: S4
- Category: architecture
- Status: stable

## 一句话描述
根据需求文档，推荐合适的系统架构方案。

## 输入
- `docs/skill-chain/requirements.md`：结构化需求文档
- `openspec/project.md`：OpenSpec 项目说明
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `docs/skill-chain/architecture.md`：架构设计文档（中文 Markdown）

## Prompt

你是一位资深系统架构师。
请根据需求文档，完成以下工作：

1. **项目类型判断**：
   - 传统 Web 项目 → 推荐单体 / 微服务架构
   - AI 项目 → 推荐 LLM Gateway + 推理服务 + 向量检索架构
   - 混合项目 → 推荐多语言异构架构

2. **架构风格推荐**：

   | 架构风格 | 适用场景 | 核心优势 | 潜在挑战 |
   |-----------|----------|----------|----------|

   - 必须判断采用模块化单体、少量服务、微服务还是混合异构架构，并给出边界依据。
   - 不得只因为项目包含 AI 或 Java 就默认拆成微服务；必须说明规模、团队、发布节奏、数据一致性和扩缩容诉求。

3. **系统分层设计与架构图**：

   必须输出至少四张可读的 Mermaid 架构图：

   - **C4 Context 图**：展示用户、外部系统、Higress 网关、核心系统、AI/模型供应商、存储系统之间的关系。
   - **C4 Container / 服务容器图**：展示前端、Higress、Java 业务服务、Python AI 服务、数据库、缓存、向量库、对象存储、消息队列之间的调用和数据流。
   - **分层架构图（Layered Architecture）**：展示访问入口层、网关层、应用编排层、领域层、AI 能力层、异步任务层、数据与基础设施层、可观测与安全层之间的依赖关系。
   - **Component Diagram（组件图）**：展示 Java 业务核心内部组件、Python AI 服务内部组件、Higress 网关能力、异步 Worker、存储组件之间的组件级协作关系；必须体现 DDD 模块边界和 AI/RAG 组件边界。
     - Java 业务核心组件必须至少覆盖 OpenSpec capability / DDD 上下文：报告生成、溯源导出版本、知识库入库、规则引擎、权限协作、审计工作台。
     - 如权限协作包含批注、任务指派或通知提醒，必须体现任务/站内通知边界；外部通知渠道（邮件、短信、企业微信等）仅作为后续扩展问题，不得默认纳入当前范围。
     - 如 S1/S3/S4 包含企业数据源对接，必须体现数据源同步模块或 Worker。
     - 如需求包含引用评分或证据评估，必须体现“证据可信度评分 / 引用质量评估”组件。

   Mermaid 质量要求：
   - 使用 `flowchart LR` 或 `flowchart TB`，节点命名必须是中文业务名 + 技术名，避免 `A/B/C` 这类无意义节点。
   - 使用 `subgraph` 分组：用户入口、网关层、业务域、AI 域、数据与基础设施、外部模型。
   - 边必须标注协议或数据含义，例如 `HTTPS`、`SSE`、`gRPC`、`事件`、`向量检索`。
   - 图中不得超过 20 个节点；复杂内容拆成多张图，不要堆成一张大图。
   - 图后必须给出“图例说明”和“关键链路说明”。
   - 所有架构图必须使用 **Mermaid**；禁止使用 ASCII 图、PlantUML、Graphviz 或图片替代。
   - Mermaid 图必须能在 GitHub / Typora / Obsidian 中正常渲染。

4. **核心组件清单**：

   | 组件名称 | 职责 | 技术选型建议 |
   |----------|------|--------------|

   - 当项目包含 Java / Spring Cloud Alibaba、Kubernetes、微服务治理或 AI 网关诉求时，网关组件必须将 **Higress** 纳入候选并按实际约束评估。
   - Higress 定位必须写清楚：阿里巴巴开源、基于 Envoy + Istio 构建的云原生 AI 原生 API 网关，是 **K8s 集群入口网关 + 微服务网关 + AI 网关** 的三合一方案。
   - Higress 职责至少覆盖：Kubernetes Ingress / Gateway API 入口、Nacos/Eureka/Consul 服务发现、Dubbo/Sentinel 微服务治理、大模型 API 统一代理、Token 级限流/计费、Prompt 安全过滤、语义缓存、MCP Server 代理、安全认证与可观测。

5. **如果是 AI 项目，额外设计 AI 架构**：

   | AI 组件 | 职责 | 推荐技术 |
   |----------|------|----------|
   | LLM Gateway | 统一接入 OpenAI、通义千问等大模型 API；支持多模型 Fallback、Token 级限流/计费、Prompt 安全过滤、语义缓存、MCP Server 代理与可观测 | **Higress** |
   | RAG 检索服务 | 向量检索 + 上下文增强 + 证据可信度评分(confidence/score/source) | Python FastAPI + LlamaIndex |
   | Embedding 服务 | 文本向量化 | Python + BERT / BGE-M3 |
   | Agent 编排引擎 | 多步骤任务规划 | LangGraph / CrewAI |
   | 知识库管理 | 文档解析 + 切片 + 元数据索引 | Unstructured / LangChain |

   ### AI 访问边界（强制）
   - ❌ 前端 / 外部分享 **禁止直连** Python AI 服务。
   - ✅ 所有 AI 生成请求须经 **Java 业务核心** 完成鉴权、任务创建、审计上下文编排后，再调用 Python AI 服务（gRPC / HTTP）。
   - Python AI 服务仅接受来自 Java 业务核心的内部调用。

   ### 模型供应商出口治理（强制）
   - 线上多模型供应商（OpenAI / Anthropic / 通义千问 / 本地 vLLM）**必须经 Higress AI Gateway 统一代理**。
   - Higress 负责：Multi-model fallback、Token 限流/计费、Prompt 安全过滤、语义缓存、可观测。
   - 禁止 Python / Java 服务直接 hard-code 外部 LLM API Key 直连外网。

   ### RAG 引用质量要求（强制）
   - 检索结果必须附带：向量相似度 score、来源文档及段落、置信等级(confidence_level)。
   - 生成答案必须标注引用来源。
   - 当 top-k 最大 score < 阈值时，须声明“知识库依据不足”，禁止编造。

6. **服务间通信方式**：

   | 通信场景 | 协议 | 说明 |
   |----------|------|------|
   | 前端 → 网关 | HTTPS / WebSocket | REST / SSE |
   | 网关 → 微服务 | gRPC / HTTP | 同步调用 |
   | 微服务 → AI 服务 | gRPC / HTTP | 异步 + 回调 |
   | 服务间事件 | Kafka / RabbitMQ /EventBus/rocketMQ | 异步解耦 |

   - 必须补充异步任务可靠性设计：任务状态机、幂等键、重试策略、死信队列、Outbox 事件一致性、进度回写和审计闭环。
   - 对报告生成、文档解析、导出、数据源同步这类长耗时任务，必须说明同步 API 与异步 Worker 的边界。
   - 报告导出初期优先设计为 Java 业务核心内的导出模块 + 异步 Worker；只有当模板渲染、Office/PDF 转换、字体环境或资源隔离复杂时，才建议拆成独立服务。

7. **架构决策记录（ADR）**：

   | 决策 | 选择 | 理由 | 替代方案 |
   |------|------|------|----------|
   | 本地开发环境 | Docker Compose 最小集 | 保证开发环境一致性，避免“在我这能跑” | 手动安装中间件 / 虚拟机 |

   - 必须将“本地开发环境”作为非功能性需求写入 ADR；生产环境可采用 K8s / 云服务 / 分布式中间件，但本地开发优先 Docker Compose 最小集。

8. **服务边界初判**：

   | 候选服务边界 | 推荐形态（模块/独立服务） | 判断依据 | 下游交给 S7 细化的问题 |
   |--------------|--------------------------|----------|--------------------------|

## 行为规则

- ✅ 架构必须匹配项目规模和团队能力
- ✅ 必须输出可读的 Mermaid C4 Context 图、Container / 服务容器图、分层架构图、Component Diagram（组件图）
- ✅ 架构图必须分组、标注协议/数据流、使用有意义中文节点名
- ✅ 必须给出模块化单体 / 少量服务 / 微服务 / 混合异构的选择依据
- ✅ AI 项目必须包含 LLM Gateway 和 RAG 架构
- ✅ Java / Spring Cloud Alibaba 项目如需要 API 网关或 AI Gateway，必须评估 **Higress**，并说明是否采用或拒绝
- ✅ 选择 Higress 时，必须说明其三合一定位：K8s Ingress / Gateway 入口、微服务网关、AI 网关
- ✅ 必须说明前端不得直连 Python AI 服务，模型出口经 Higress AI 网关治理
- ✅ Component Diagram 必须覆盖 OpenSpec capability 对应的主要业务组件，不能只画报告/知识库/权限/审计
- ✅ 长耗时任务必须包含可靠性设计：状态机、幂等、重试、死信、Outbox、进度回写、审计闭环
- ✅ 报告导出服务边界必须克制：初期模块 + Worker，复杂后再独立服务化
- ✅ 必须在 ADR 中将“本地开发环境”作为非功能性需求决策，默认选择 Docker Compose 最小集，并说明替代方案
- ✅ 必须给出决策理由
- ✅ 输出必须是中文 Markdown
- ❌ 禁止过度设计（小项目不推荐微服务）
- ❌ 禁止输出只有 ASCII 方框或节点名无意义的架构图
- ❌ 禁止忽略 AI 项目的推理延迟问题

## 使用示例

```
加载 <skill id="S5">，输入：docs/skill-chain/requirements.md
请基于输入的需求、OpenSpec 和项目约束推荐合适的架构方案。
```
</skill>
<!-- end -->
