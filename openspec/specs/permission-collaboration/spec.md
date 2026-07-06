# permission-collaboration Specification

## Requirements

### Requirement: RBAC 用户与角色管理

系统 SHALL 支持管理员添加用户、批量导入用户、筛选角色、编辑用户、启用或禁用账号，并维护角色权限矩阵。

#### Scenario: 禁用用户

- WHEN 管理员禁用某个用户
- THEN 系统阻止该用户继续执行需登录操作
- AND 系统记录审计日志

#### Scenario: 查看权限矩阵

- WHEN 管理员查看权限矩阵
- THEN 系统展示管理员、高级分析师、分析师、查看者与权限项的对应关系

### Requirement: 外部认证与当前用户 Profile

系统 SHALL 将登录入口交由 Higress / OIDC 承担，Java 业务核心 SHALL 提供当前用户 Profile、角色、权限和认证来源查询能力。

#### Scenario: 外部认证后读取当前用户

- WHEN 用户经 Higress/OIDC 完成认证后访问业务系统
- THEN Java 业务核心返回当前用户 Profile、角色、权限和认证来源
- AND 系统不提供默认本地用户名密码登录入口作为生产认证入口

### Requirement: 报告分享链接

系统 SHALL 支持具备分享权限的用户按指定用户、部门或外部用户生成报告分享链接，并配置访问密码、有效期和访问控制。

#### Scenario: 外部用户访问有效分享

- WHEN 外部用户打开未过期且凭证正确的分享链接
- THEN 系统允许其查看授权报告内容
- AND 系统记录访问日志

#### Scenario: 分享链接过期或密码错误

- WHEN 外部用户访问过期链接或输入错误密码
- THEN 系统拒绝访问
- AND 系统记录失败原因

### Requirement: 批注评论与任务指派

系统 SHALL 支持用户对报告内容添加批注、提交评论、指派任务并产生站内通知。

#### Scenario: 提交批注并指派任务

- WHEN 用户输入批注内容、选择处理人，并提交包含 `sectionId/startOffset/endOffset/selectedText` 的文本锚点
- THEN 系统创建批注和任务
- AND 系统生成站内通知

#### Scenario: 批注缺少文本 offset 锚点

- WHEN 用户提交批注但未提供 `startOffset`、`endOffset` 或 `selectedText`
- THEN 系统拒绝创建批注和任务
- AND 系统提示锚点字段不完整

#### Scenario: 更新任务状态

- WHEN 任务负责人或报告所有者将任务状态更新为 `open`、`in_progress`、`done` 或 `rejected`
- THEN 系统保存任务状态
- AND 系统向协作对方生成站内通知

#### Scenario: 被指派人无访问权限

- WHEN 用户将任务指派给无报告访问权限的处理人
- THEN 系统提示权限不足
- AND 系统拒绝创建无效任务

## Traceability

| 项 | 内容 |
| --- | --- |
| Requirement ID | REQ-AUTH-001, REQ-COLLAB-001, REQ-COLLAB-002 |
| User Journey | STEP-20, STEP-21, STEP-22, STEP-23, STEP-24, STEP-30 |
| Use Case | UC-09, UC-10, UC-11 |
| Domain Model | 用户、角色、权限项、分享链接、访问记录、批注、评论、任务、通知 |
