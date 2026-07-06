# Closure 271: Report Generation Task Contract Schema

## 范围

- 需求链路：`UC-01` / `UC-02` / `REQ-REPORT-001` / `REQ-REPORT-002`
- 阶段产物：`S4 API Contract`
- 目标端点：
  - `POST /api/v1/reports/generation-tasks`
  - `POST /api/v1/reports/template-generation-tasks`
  - `PUT /api/v1/reports/generation-tasks/{taskId}/outline`
  - `POST /api/v1/reports/generation-tasks/{taskId}/completion`
  - `POST /api/v1/reports/generation-tasks/{taskId}/failure`
  - `POST /api/v1/reports/generation-tasks/{taskId}/retry`

## 结果

报告生成主路径接口不再停留在泛化或局部字段 schema，而是按后端真实响应补齐字段级 `responseBody.data.properties`。

- 基础任务字段：`taskId`、`reportId`、`status`、`currentStage`、`progress`、`traceId`、`outline`、`failureReason`、`createdAt`
- 模板任务附加字段：`templateSnapshot`
- 大纲确认附加字段：`confirmed`、`nextStage`
- Worker 完成附加字段：`versionId`
- Worker 失败附加字段：`errorCode`
- 重试附加字段：`retryReason`

## 代码与文档证据

- 合同守护测试：`ContractSurfaceTest#reportGenerationTaskEndpointsDeclareFieldLevelResponseContracts`
- API 合同：`docs/skill-chain/api_contract.md` section `6` and `13.2`
- 后端字段来源：`ReportGenerationTask#toResponse`
- 附加字段来源：`ReportApplicationService#createTemplateTask`、`confirmOutline`、`completeGenerationTaskFromWorker`、`failGenerationTask`、`retryGenerationTask`

## TDD 证据

RED:

```text
ContractSurfaceTest.reportGenerationTaskEndpointsDeclareFieldLevelResponseContracts
POST /api/v1/reports/generation-tasks responseBody.data.reportId
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#reportGenerationTaskEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 剩余缺口

- SSE 事件流仍需进一步细化事件级 schema。
- 真实网关链路、浏览器级 401/403/200 覆盖、外部模型额度和供应商健康评分仍是后续生产硬化项。
- 本轮只收敛 API 合同审查性，不新增运行时能力。
