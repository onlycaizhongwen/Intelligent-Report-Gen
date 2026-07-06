# permission-collaboration API Delta

## ADDED Requirements

### Requirement: 权限协作 API

系统 SHALL 提供用户管理、权限矩阵、分享链接、外部访问、批注和任务状态 API。

#### Scenario: 创建分享链接

- WHEN 客户端调用 `POST /api/v1/reports/{reportId}/share-links`
- THEN 系统返回分享链接 ID、分享地址、有效期和访问策略摘要

#### Scenario: 通过 API 添加批注并指派任务

- WHEN 客户端调用 `POST /api/v1/reports/{reportId}/annotations`
- THEN 系统返回批注 ID、关联任务 ID 和批注状态

#### Scenario: 批注 API 使用文本 offset 锚点

- WHEN 客户端调用 `POST /api/v1/reports/{reportId}/annotations`
- THEN 请求体 SHALL 包含 `content`、`assigneeUserId` 和 `anchor.startOffset/endOffset/selectedText`
- AND 响应 SHALL 返回 `notificationId`、`notificationRecipientUserId` 和 `notificationType`

#### Scenario: 任务状态 API 返回通知摘要

- WHEN 客户端调用 `PUT /api/v1/tasks/{taskId}/status`
- THEN 请求体 SHALL 使用 `open`、`in_progress`、`done` 或 `rejected`
- AND 响应 SHALL 返回更新后的任务状态和通知摘要
