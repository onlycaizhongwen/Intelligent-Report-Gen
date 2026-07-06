# api-contract-v1 Tasks

## S4 API 契约任务

- [x] 定义统一响应格式、分页格式、错误码格式。
- [x] 定义 SSE JSON 事件格式。
- [x] 为报告生成、知识库、规则、权限协作、审计工作台输出 JSON 请求/响应契约。
- [x] 映射 OpenSpec capability、Requirement ID 与 UC。

## S5-S7 下游约束

- [x] S5 架构设计必须引用 API capability 分组，明确报告生成、知识库、规则、权限协作、审计工作台边界。
- [x] S5 架构图必须使用 Mermaid，不得使用 ASCII 图或纯文本箭头图。
- [x] S6 技术栈选择必须覆盖 SSE、文件上传、OCR/表格/扫描件处理、模型调用审计和 OpenSpec 契约维护。
- [x] S6 如选择 Java 网关方案，必须评估 Higress 作为 K8s Ingress、微服务网关和 AI 网关候选。
- [x] S7 模块设计必须保持 DDD 分层和限界上下文边界。

## S8-S20 实现与测试任务

- [x] S8 安全设计必须细化 RBAC、分享链接、外部访问、导出权限、工作台访问权限和模型调用审计权限。
- [x] S12 后端实现必须覆盖 `docs/skill-chain/api_contract.md` 中定义的核心 API 路径、请求、响应和错误码。
- [x] S12/S19/S20 必须以 API JSON 契约清单作为请求/响应字段准线。
- [x] S12/S14 必须支持 `/api/v1/reports/generation-tasks/{taskId}/stream` SSE 流式事件。
- [x] S14 必须记录 Prompt、检索上下文、模型参数、模型响应、耗时和调用结果。
- [x] S14 必须实现规则节点 schema、连线校验、调试执行和节点日志骨架。
- [x] S16/S19 前端页面和 API client 必须按照 S4 契约调用接口。
- [x] S20 契约校验必须检查 API 路径、HTTP 方法、请求字段、响应字段、状态码和 OpenSpec 映射。

## 护栏

- [x] 不得在 S7 批准前进入 S8 或任何代码生成阶段。
- [x] 不得加入报告人工审核发布流程。
- [x] 不得默认加入多租户、私有化部署、高可用或灾备。
- [x] 所有后续 Markdown 文档输出到 `docs/skill-chain/`，OpenSpec 保留在 `openspec/`。
