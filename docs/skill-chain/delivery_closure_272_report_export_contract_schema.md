# Closure 272: Report Export Contract Schema

## 范围

- 需求链路：`UC-04` / `REQ-REPORT-004`
- 阶段产物：`S4 API Contract`
- 目标端点：
  - `GET /api/v1/reports/{reportId}/exports/{exportFileId}`
  - `GET /api/v1/files/report-exports/{exportFileId}/download-url`

## 结果

报告导出状态和受控下载 URL 接口不再只声明泛化的 `business response data`，而是按后端真实返回补齐字段级 `responseBody.data.properties`。

- 导出状态字段：`reportId`、`exportFileId`、`status`、`format`、`templateId`、`downloadPolicy`、`bucket`、`objectKey`、`fileName`、`contentType`、`sizeBytes`、`downloadUrl`、`expiresAt`、`brandSnapshot`
- 下载 URL 字段：`exportFileId`、`reportId`、`fileName`、`contentType`、`sizeBytes`、`downloadUrl`、`expiresAt`
- `brandSnapshot` 只在 DOCX/PDF/PPTX 等企业导出格式存在，因此在合同中标记为非必填。

## 代码与文档证据

- 合同守护测试：`ContractSurfaceTest#reportExportEndpointsDeclareFieldLevelResponseContracts`
- API 合同：`docs/skill-chain/api_contract.md` section `13.2`
- 后端字段来源：`ReportApplicationService#createExport`、`getExportStatus`、`createExportDownloadUrl`

## TDD 证据

RED:

```text
ContractSurfaceTest.reportExportEndpointsDeclareFieldLevelResponseContracts
GET /api/v1/reports/{reportId}/exports/{exportFileId} responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#reportExportEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 剩余缺口

- 本轮未重新执行真实 MinIO 下载、Higress 路由或浏览器下载烟测。
- 高保真 DOCX/PDF/PPTX 渲染、企业模板管理 UI 和网关级权限烟测仍是后续生产硬化项。
