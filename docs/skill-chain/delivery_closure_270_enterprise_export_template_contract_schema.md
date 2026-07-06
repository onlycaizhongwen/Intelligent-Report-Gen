# Closure 270: Enterprise Export Template Contract Schema

## 范围

- 需求链路：`UC-04` / `REQ-REPORT-004`
- 阶段产物：`S4 API Contract`
- 目标端点：
  - `POST /api/v1/enterprise-export-templates`
  - `GET /api/v1/enterprise-export-templates`
  - `PUT /api/v1/enterprise-export-templates/{templateId}`
  - `GET /api/v1/enterprise-export-templates/{templateId}/versions`
  - `POST /api/v1/enterprise-export-templates/{templateId}/disable`
  - `POST /api/v1/enterprise-export-templates/{templateId}/enable`

## 结果

企业导出模板治理接口不再只声明泛化的 `business response data`，而是补齐字段级 `responseBody.data` schema。

- 模板对象字段：`id`、`templateId`、`name`、`version`、`status`、`brandSnapshot`、`createdBy`、`createdAt`、`updatedAt`
- 分页列表字段：`items`、`page`、`pageSize`、`total`
- 版本历史接口保持后端真实返回形态：`responseBody.data.type = array`

## 代码与文档证据

- 合同守护测试：`ContractSurfaceTest#enterpriseExportTemplateEndpointsDeclareFieldLevelResponseContracts`
- API 合同：`docs/skill-chain/api_contract.md` section `13.2`
- 后端字段来源：`EnterpriseExportTemplate#toResponse`
- 分页壳来源：`PageResponse`

## TDD 证据

RED:

```text
ContractSurfaceTest.enterpriseExportTemplateEndpointsDeclareFieldLevelResponseContracts
POST /api/v1/enterprise-export-templates responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#enterpriseExportTemplateEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 剩余缺口

- 企业导出模板集中管理 UI 仍未进入本轮实现。
- 真实浏览器管理流程仍需在前端设计确认后补齐。
- DOCX/PDF/PPTX 高保真企业模板渲染仍是后续生产级硬化项。
- Higress 网关级权限与路由烟测仍需在本地网关环境确认或部署后执行。
