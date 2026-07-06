# Closure 316: Higress 代表性授权矩阵

## 范围

- Requirements: `REQ-AUDIT-001`, `REQ-DASH-001`, `REQ-REPORT-*`, `REQ-KB-*`, `REQ-RULE-001`
- User journey: 客户通过 Higress 网关访问报告、知识库、规则、审计、仪表盘和通知能力，必须在网关入口保留 Java RBAC 边界。
- Production gap: 之前 Higress smoke 已覆盖权限矩阵和数据源代表路由，但多个模块仍缺少网关级 `401/403/200` 传播证据。

## 结果

- 新增 `buildHigressRepresentativeAuthorizationMatrixChecks()`。
- 新增 6 个模块的代表性授权矩阵：
  - `GET /api/v1/reports?page=1&pageSize=1` -> `report:read`
  - `GET /api/v1/knowledge-bases?page=1&pageSize=1` -> `knowledge:manage`
  - `GET /api/v1/rules?page=1&pageSize=1` -> `rule:manage`
  - `GET /api/v1/audit-logs?page=1&pageSize=1` -> `audit:read`
  - `GET /api/v1/dashboard/overview?range=last7days` -> `dashboard:read`
  - `GET /api/v1/system-alerts?page=1&pageSize=1` -> `notification:read`
- 每个模块都检查：
  - 缺 token 返回 `401`
  - token 有效但权限不足返回 `403`
  - token 携带目标权限返回 `200`
- `runHigressGatewaySmoke()` 已自动纳入该矩阵，后续本地 Higress smoke 会持续覆盖这些模块。

## 验证

- RED:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`
  - 首次失败原因：`buildHigressRepresentativeAuthorizationMatrixChecks` 未导出。
- GREEN:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`，`7/7` 通过。
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`，`passed=true`。
  - 新增 18 个代表性授权检查全部通过；报告、知识库、规则、审计、仪表盘、通知均验证了 `401/403/200`。

## 剩余风险

- 当前是代表性矩阵，不是完整 endpoint-by-endpoint 授权矩阵。
- 生产 OIDC 登录、TLS 证书、WAF 行为和 K8s Gateway API 资源仍属于后续生产硬化工作。
