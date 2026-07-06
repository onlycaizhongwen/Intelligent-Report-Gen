# Closure 314: API 数据源客户 profile catalog

## 范围

- Requirement: `REQ-KB-003`
- User journey: 管理员配置企业内部数据库、ERP、OA、财务/API 数据源，并让同步链路按受治理的字段映射导入知识库。
- Production gap: 之前 API profile 规则硬编码为 `oa-documents` 和 `finance-vouchers`，客户现场新增 CRM、采购、工单等 API schema 时只能绕开 profile 治理或改代码。

## 结果

- 新增 `ApiDataSourceProfileCatalog`，统一承载内置 profile 和客户配置 profile。
- 新增配置入口：
  - `knowledge.data-source.profile-catalog-json`
  - `DATA_SOURCE_PROFILE_CATALOG_JSON`
- `KnowledgeApplicationService` 的保存校验、同步前复核、漂移审计和确认修复都改为读取同一个 catalog。
- 仍保留内置 `oa-documents` / `finance-vouchers` 行为，客户 profile 会覆盖或追加 catalog 条目。
- profile 修复响应仍不返回任何凭证明文或密文。

## 客户 Profile JSON 形状

```json
[
  {
    "profileId": "customer-crm-tickets",
    "rowsPath": "data.tickets",
    "titleField": "ticketNo",
    "contentField": "description",
    "cursorField": "updatedAt",
    "cursorColumn": "updatedAt",
    "method": "GET",
    "authType": "bearer",
    "headers": {
      "X-System": "crm"
    }
  }
]
```

## 验证

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#usesCustomerApiProfileCatalogForSaveAuditRepairAndSync" test`
  - 首次失败原因：`ApiDataSourceProfileCatalog` 和 catalog-aware 构造入口不存在。
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#usesCustomerApiProfileCatalogForSaveAuditRepairAndSync" test`，`1/1` 通过。
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest" test`，`40/40` 通过。
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest,ReportCoreApplicationContextTest" test`，`37/37` 通过。
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreProdProfileContextTest" test`，`1/1` 通过；测试显式提供生产必填的 `knowledge.data-source.endpoint-allowlist` 和空 profile catalog。
  - `.\mvnw.cmd -pl backend/java-report-core test`，`299/299` 通过。
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs`，`8/8` 通过。
  - `.\mvnw.cmd -pl backend/java-report-core -DskipTests package`，构建通过。
  - 替换 `ir-java-smoke:/app/app.jar` 并重启后健康检查恢复为 `healthy`。
  - `node scripts/data-source-enterprise-docker-smoke.mjs`，`passed=true`；ERP/OA/finance 三类数据源同步成功，profile drift 为 `0`，repair dry-run 不泄露凭据。
  - `node scripts/higress-gateway-smoke.mjs`，`passed=true`；`/api/v1/chat` 仍由 Java 鉴权拦截，profile drift/repair 路由经 Higress 进入 Java。

## 剩余风险

- 仍未提供批量确认修复多个 drifted 数据源的操作策略。
- 生产 OIDC/TLS/WAF 和完整网关授权矩阵仍属于后续生产硬化工作。
