# report-generation Specification

## Requirements

### Requirement: 自然语言报告生成任务

系统 SHALL 支持具备创建报告权限的用户通过自然语言提交报告主题、时间范围、分析维度和重点关注，并创建可追踪的报告生成任务。

#### Scenario: 用户提交自然语言需求

- WHEN 用户输入报告需求并提交
- THEN 系统创建报告生成任务
- AND 系统记录生成方式、用户输入、创建人、traceId 和初始状态

### Requirement: 模板填报生成任务

系统 SHALL 支持具备创建报告权限的用户选择模板并填写周期、对象范围、关注重点和报告风格后生成标准化报告。

#### Scenario: 模板参数完整时生成报告

- WHEN 用户选择模板并填写必填参数后提交
- THEN 系统创建模板填报生成任务
- AND 系统保存模板版本、字段参数和参数快照

#### Scenario: 模板参数缺失时拒绝提交

- WHEN 用户提交缺失必填字段的模板参数
- THEN 系统拒绝进入生成流程
- AND 系统返回缺失字段清单

### Requirement: 大纲确认

系统 SHALL 在生成正文前先生成报告大纲，并要求用户确认或修改大纲后再进入正文生成。

#### Scenario: 用户确认大纲后开始正文生成

- WHEN 用户确认报告大纲
- THEN 系统保存大纲快照
- AND 系统启动检索知识库、分析数据、生成报告三个阶段

### Requirement: 三阶段流式生成

系统 SHALL 展示检索知识库、分析数据、生成报告三个阶段的生成进度，并支持报告正文逐段可见。

#### Scenario: 生成过程中查看阶段进度

- WHEN 报告生成任务正在执行
- THEN 用户可查看当前阶段、进度和已生成正文片段
- AND SSE 事件 SHALL 使用统一 JSON schema

### Requirement: 线上 GPT 与多模型扩展

系统 SHALL 当前优先调用线上 GPT 模型，并在模型调用记录中保留模型标识，以支持后续多模型路由扩展。

#### Scenario: 线上 GPT 调用被记录

- WHEN 系统调用线上 GPT 生成报告内容
- THEN 系统记录模型标识、Prompt、上下文摘要、参数、响应摘要、耗时和调用结果

### Requirement: 生成失败可见且可重试

系统 SHALL 在输入不足、检索无结果或模型调用失败时给出可见提示，并保留失败原因。

#### Scenario: 模型调用失败

- WHEN 模型调用超时或返回错误
- THEN 报告生成任务进入 `failed` 或 `retryable`
- AND 用户可查看失败原因和重试入口

### Requirement: SSE JSON 契约

系统 SHALL 为 Java 报告任务流和 Python AI 内部流使用统一 SSE JSON 事件格式。

#### Scenario: 流式生成事件被统一消费

- WHEN Java 报告任务流或 Python AI 内部流推送生成事件
- THEN 事件必须使用 `stage`、`delta`、`references`、`error`、`done` 类型
- AND JSON 字段至少包含 `type`、`taskId`、`content`、`stage`、`references`、`progress`、`errorCode`、`traceId`

## Traceability

| 项 | 内容 |
| --- | --- |
| Requirement ID | REQ-REPORT-001, REQ-REPORT-002, REQ-AI-001 |
| User Journey | STEP-01, STEP-02, STEP-03 |
| Use Case | UC-01, UC-02 |
| Domain Model | 报告、报告生成任务、报告模板、报告大纲、检索会话、模型调用记录 |
