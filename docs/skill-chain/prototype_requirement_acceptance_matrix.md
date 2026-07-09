# 原型需求交付验收矩阵

> 目标：从原型页面、S1/S2 需求旅程和当前工程证据出发，持续判断系统距离“客户可交付生产版本”的真实差距。
> 验收原则：页面存在、接口存在、测试骨架存在都不等于交付完成；必须能按原型旅程跑通业务闭环，并留下数据库、对象存储、网关、审计或端到端测试证据。

## 当前 P0/P1 闭环矩阵

| 页面 | 需求 ID | 原型功能 | 验收标准 | 验证证据 | 状态 | 下一步 |
| --- | --- | --- | --- | --- | --- | --- |
| 我的报告 | REQ-REPORT-LIST-001 | 独立“我的报告”列表，包含总数、每页选项、新建报告、报告名称、生成方式、创建时间、状态、操作 | `/reports` 是独立列表页；菜单“我的报告”不得跳转到智能生成页 | `frontend/web-console/tests/e2e/prototype-alignment.spec.ts` | 待验证 | 按 Playwright 回归闭环 |
| 历史记录 | REQ-AUDIT-HISTORY-001 | 用户可理解的个人历史列表，管理员审计作为子入口 | `/history` 展示“历史记录”；包含时间、操作类型、内容、操作人、操作；管理员审计是子 tab 或管理入口 | `frontend/web-console/tests/e2e/prototype-alignment.spec.ts` | 待验证 | 按 Playwright 回归闭环 |
| 本地预览 401 | REQ-LOCAL-PREVIEW-001 | local-preview 下可浏览管理页面，不暴露后端登录错误 | `/dashboard`、`/admin/users`、企业导出模板、审批待办、审批委托、审批模板、组织管理均不得展示“请登录后继续操作” | `frontend/web-console/tests/e2e/prototype-alignment.spec.ts` | 待验证 | 每个管理页使用统一降级策略 |
| 规则引擎 | REQ-RULE-PROTOTYPE-001 | 规则列表、从模板创建、导入规则、画布、节点库、调试面板 | local-preview 下展示样例规则“销售考核规则、财务数据提取规则、法规文本分类规则”；展示节点库和调试文本 | `frontend/web-console/tests/e2e/prototype-alignment.spec.ts` | 待验证 | 保持真实接口成功时优先使用后端数据 |
| 权限协作 | REQ-PERMISSION-COLLAB-001 | 用户管理、权限矩阵、报告协作、操作日志聚合 | `/admin/users` 页面以“权限协作”为入口，四个功能作为 tab 或同页区块可见 | `frontend/web-console/tests/e2e/prototype-alignment.spec.ts` | 待验证 | 后续接入真实权限矩阵与协作数据 |
| 文档矩阵 | REQ-DOC-MATRIX-001 | 验收矩阵中文可读、可持续追加证据 | 文件必须为 UTF-8 中文，不包含常见 mojibake 字符；保留页面、需求、验收标准、验证证据列 | `tests/unit/node/prototype_acceptance_matrix_encoding.test.mjs` | 待验证 | 每次闭环交付追加一行证据 |

## 闭环更新规则

1. 每完成一个可交付闭环，必须追加或更新矩阵行，写明需求 ID、验收标准和验证证据。
2. 验证证据优先使用可复跑命令：Playwright、单元测试、接口测试、Docker 冒烟、网关冒烟。
3. 若只完成页面骨架或本地示例数据，状态只能标为“待真实链路验证”。
4. 若真实业务链路已通过，状态可更新为“已验证”，并补充数据落库、对象存储、消息队列、网关或审计证据。
5. 禁止把乱码、英文占位表头或不可复跑口头结论写入本矩阵。
