# api-contract-v1 Proposal

## 背景

S3 已完成领域模型与 capability 草案。S4 需要将报告生成、知识库、规则引擎、权限协作、审计工作台等能力转化为可实现、可测试、可追溯的 API 契约，作为后续架构设计、模块拆分、前后端开发和契约校验的共同依据。

## 变更范围

| 范围 | 内容 |
| --- | --- |
| API 契约 | 新增统一响应格式、SSE 流式响应格式、错误码和接口清单 |
| OpenSpec | 为 API 契约新增 capability delta，映射既有 requirement 与 scenario |
| 下游 Skill | S5 架构、S6 技术栈、S7 模块、S8 安全、S12 后端、S16 前端、S20 契约校验均需以本契约为约束 |

## 非范围

- 不生成后端、前端、数据库、Docker、CI 或监控代码。
- 不引入多租户、私有化部署、高可用、灾备或报告人工审核发布流程。
- 不冻结最终实现细节；S4 产物是审批前 API 契约草案。

## 关键决策

- API 请求参数和响应参数必须使用 JSON 契约描述，Markdown 表格只能作为总览。
- 外部业务 API 默认由 Java 业务核心归口。
- Python AI 服务接口默认作为内部或受控执行面，不作为公开报告任务闭环入口。
- SSE 事件统一为 `stage | delta | references | error | done`。
