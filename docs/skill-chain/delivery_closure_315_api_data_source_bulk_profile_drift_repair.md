# Closure 315: API 数据源批量 profile drift repair

## 范围

- Requirement: `REQ-KB-003`
- User journey: 管理员治理企业 ERP/OA/财务/API 数据源，并在 profile catalog 规则变化后批量修复历史漂移配置。
- Production gap: Closure 312/313/314 已具备审计、单条修复和客户 catalog，但多个 drifted 数据源仍需要逐条人工操作，缺少批量 dry-run 与确认修复策略。

## 结果

- 新增 `POST /api/v1/data-sources/profile-drift/repair`，受 `datasource:manage` 保护。
- 请求默认 dry-run：
  - `confirmed=false` 或未传时只返回批量预览，不落库。
  - `confirmed=true` 时逐项复用单条 `repairDataSourceProfileDrift` 修复链路。
- 响应返回：
  - `scannedCount`
  - `driftCount`
  - `repairedCount`
  - `failedCount`
  - `requiresConfirmation`
  - `items`
- 单项失败不会中断整批响应；失败项只返回错误原因。
- 响应与审计路径不返回任何凭证明文或密文。

## 验证

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#previewsAndConfirmsBulkApiDataSourceProfileDriftRepair" test`
  - 首次失败原因：`repairDataSourceProfileDriftBatch(Map<String,Object>)` 不存在。
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts,ContractSurfaceTest#dataSourceCredentialMaintenanceEndpointRequiresDataSourceManagePermission" test`
  - 首次失败原因：结构化 API 合同缺少 `POST /api/v1/data-sources/profile-drift/repair`。
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#previewsAndConfirmsBulkApiDataSourceProfileDriftRepair" test`，`1/1` 通过。
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts,ContractSurfaceTest#dataSourceCredentialMaintenanceEndpointRequiresDataSourceManagePermission" test`，`2/2` 通过。
- Regression:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest" test`，`41/41` 通过。
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test`，`36/36` 通过。
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs`，`8/8` 通过。
  - `.\mvnw.cmd -pl backend/java-report-core test`，`300/300` 通过。
  - `git diff --check` 无 whitespace error，仅输出 Windows 换行提示。
- Build/runtime:
  - `.\mvnw.cmd -pl backend/java-report-core -DskipTests package` 构建通过。
  - 替换 `ir-java-smoke:/app/app.jar` 并重启后，Docker health 从 `starting` 恢复为 `healthy`。
- Live Docker smoke:
  - `node scripts/data-source-enterprise-docker-smoke.mjs`，`passed=true`；ERP MySQL、OA API、Finance API 三类数据源均连接成功并同步 `2` 行，profile drift 为 `0`，单条 repair preview 不泄露 `enc:v1:`。
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`，`passed=true`；新增 `data-source-profile-drift-bulk-repair-preview-through-higress` 通过 Higress 返回 `200`，响应包含 `scannedCount/driftCount/repairedCount/failedCount/requiresConfirmation/items`，`/api/v1/chat` 仍由 Java 鉴权拦截。

## 剩余风险

- 生产 OIDC/TLS/WAF 与完整网关授权矩阵仍属于后续生产硬化工作。
