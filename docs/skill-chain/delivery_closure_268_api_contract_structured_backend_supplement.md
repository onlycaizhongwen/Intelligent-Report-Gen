# Closure 268: API 结构化契约补遗守卫

## 目标

补齐 S4 API 契约中后端实际 Controller 端点的结构化 JSON 请求/响应描述，避免端点只出现在表格里，导致评审和后续生成阶段无法稳定检查。

## 变更

- 在 `docs/skill-chain/api_contract.md` 增加 `13.2` 后端实际端点结构化 JSON 契约补遗。
- 为补遗段增加 ASCII 锚点：`API_CONTRACT_STRUCTURED_BACKEND_SUPPLEMENT_START/END`，降低中文标题编码差异导致的测试失效风险。
- 将主契约中无请求体端点的 `requestBody: null` 统一改为结构化对象：`type=none`、`required=false`。
- 补齐两个分享访问端点的结构化契约：
  - `POST /api/v1/share-links/{shareToken}/report`
  - `POST /api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url`
- 增强 `ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract`，要求所有 Java Controller 端点必须存在结构化 JSON 契约，并校验 `method/path/requestBody/responseBody/statusCodes` 基本形状。

## RED 证据

初始目标测试失败，显示结构化契约缺口：

- 第一轮：找不到 `13.2` 补遗段 marker，根因为中文标题在当前 Maven/JVM 编码环境下不稳定。
- 第二轮：主契约中 16 个无请求体端点的 `requestBody` 为 `null`，不满足结构化契约形状。
- 第三轮：缺少 2 个实际 Controller 端点的结构化契约：
  - `POST /api/v1/share-links/{shareToken}/report`
  - `POST /api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url`

## GREEN 证据

目标测试：

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test
```

结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`

契约测试：

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test
```

结果：`Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`

核心回归：

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest,ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest,SecurityRuntimeContractTest,ShareLinkSecurityRuntimeContractTest" test
```

结果：`Tests run: 66, Failures: 0, Errors: 0, Skipped: 0`

## 剩余风险

- 13.2 补遗中部分端点仍是通用 schema 骨架，字段级语义可继续按 Controller 入参和响应 DTO 深化。
- 当前文档仍存在历史编码乱码，本轮只处理影响契约守卫的定位稳定性和结构完整性，未做全量编码修复。
