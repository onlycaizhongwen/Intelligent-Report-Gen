# audit-history-dashboard API Delta

## ADDED Requirements

### Requirement: 审计历史与工作台 API

系统 SHALL 提供个人历史、管理员审计、模型调用审计和工作台指标 API。

#### Scenario: 查询个人历史

- WHEN 客户端调用 `GET /api/v1/history`
- THEN 系统返回当前用户有权查看的历史记录

#### Scenario: 查询管理员审计

- WHEN 管理员调用 `GET /api/v1/audit-logs`
- THEN 系统返回全局审计日志并支持筛选和分页

#### Scenario: 查询工作台指标

- WHEN 客户端调用 `GET /api/v1/dashboard/metrics`
- THEN 系统返回指标卡片、趋势、排行和最近活动
