# rule-engine API Delta

## ADDED Requirements

### Requirement: 规则引擎 API

系统 SHALL 提供规则列表、规则创建、规则保存和规则调试执行 API。

#### Scenario: 保存规则画布

- WHEN 客户端调用 `PUT /api/v1/rules/{ruleId}` 并提交节点与连线
- THEN 系统保存规则版本并返回校验结果

#### Scenario: 执行规则调试

- WHEN 客户端调用 `POST /api/v1/rules/{ruleId}/debug-runs`
- THEN 系统返回调试执行 ID、状态、节点日志和错误列表
