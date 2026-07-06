# Closure 269: 分享端点字段级契约补齐

## 目标

把公开分享链路中的报告只读详情和受控下载 URL 从“结构化对象占位”推进到字段级可审查契约，支撑外部用户分享访问、前端联调和客户验收。

## 变更

- 在 `ContractSurfaceTest` 新增 `publicShareEndpointsDeclareFieldLevelResponseContracts`。
- 要求以下端点的 `responseBody.data.properties` 明确列出关键字段：
  - `POST /api/v1/share-links/{shareToken}/report`
  - `POST /api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url`
- 在 `docs/skill-chain/api_contract.md` 的 `13.2` 结构化补遗中补齐字段级 schema：
  - 分享报告详情：`accessGranted`、`shareToken`、`reportId`、`title`、`status`、`currentVersionId`、`sections`、`allowDownload`、`exports`
  - 分享下载 URL：`exportFileId`、`reportId`、`fileName`、`contentType`、`sizeBytes`、`downloadPolicy`、`downloadUrl`、`expiresAt`

## RED 证据

目标测试先失败于：

```text
POST /api/v1/share-links/{shareToken}/report responseBody.data.properties
Expecting value to be true but was false
```

这证明原契约只有通用 `data` 对象描述，无法审查外部分享响应字段。

## GREEN 证据

目标测试：

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#publicShareEndpointsDeclareFieldLevelResponseContracts" test
```

结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`

契约测试：

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test
```

结果：`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`

核心回归：

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest,ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest,SecurityRuntimeContractTest,ShareLinkSecurityRuntimeContractTest" test
```

结果：`Tests run: 67, Failures: 0, Errors: 0, Skipped: 0`

## 剩余风险

- 本轮只深化公开分享链路的两个客户可见端点。其他 13.2 补遗端点仍可按 DTO 和页面联调优先级继续补字段级 schema。
- 当前验证是文档契约与安全邻近回归，未执行真实 Higress 路由烟测。
