# scope-guardrails API Delta

## ADDED Requirements

### Requirement: API 范围护栏

系统 SHALL NOT 在 API 契约中引入多租户、私有化部署、高可用、灾备或报告人工审核发布流程。

#### Scenario: API 契约检查范围排除项

- WHEN 审核 API 契约
- THEN 不应发现租户 API、私有化部署 API、高可用灾备 API 或报告审批发布 API

### Requirement: API JSON 契约

系统 SHALL 使用 JSON 契约描述每个 API 的请求参数和响应参数，Markdown 表格只能作为总览或辅助说明。

#### Scenario: 审核 API JSON 契约

- WHEN 审核 `docs/skill-chain/api_contract.md` 中任一 API
- THEN 每个 API 应包含 `method`、`path`、`contentType`、`pathParams`、`queryParams`、`headers`、`requestBody`、`responseBody` 和 `statusCodes`
