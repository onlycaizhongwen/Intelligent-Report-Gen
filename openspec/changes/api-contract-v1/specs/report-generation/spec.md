# report-generation API Delta

## ADDED Requirements

### Requirement: 报告生成 API

系统 SHALL 提供创建自然语言生成任务、创建模板生成任务、确认大纲和订阅 SSE 流式生成的 API。

#### Scenario: 创建自然语言生成任务

- WHEN 客户端调用 `POST /api/v1/reports/generation-tasks`
- THEN 系统返回生成任务 ID、状态、待确认大纲和创建时间

#### Scenario: 确认大纲

- WHEN 客户端调用 `POST /api/v1/reports/generation-tasks/{taskId}/outline/confirm`
- THEN 系统保存大纲快照并进入正文生成阶段

#### Scenario: 订阅 SSE

- WHEN 客户端调用 `GET /api/v1/reports/generation-tasks/{taskId}/stream`
- THEN 系统以 SSE 返回阶段、文本、引用、完成或错误信息
