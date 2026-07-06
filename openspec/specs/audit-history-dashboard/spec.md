# audit-history-dashboard Specification

## Requirements

### Requirement: 个人历史记录

系统 SHALL 支持登录用户查看自己的操作历史，并与管理员全局审计日志保持权限边界。

#### Scenario: 普通用户查看个人历史

- WHEN 普通用户打开历史记录
- THEN 系统仅展示该用户有权查看的个人操作记录

### Requirement: 管理员操作审计

系统 SHALL 支持管理员查看创建、编辑、删除、分享、导出、访问和关键配置变更等全局操作日志。

#### Scenario: 管理员筛选操作日志

- WHEN 管理员按操作类型或关键词筛选日志
- THEN 系统返回符合条件的审计记录并保留分页信息

### Requirement: 模型调用审计

系统 SHALL 对 Prompt、检索上下文、生成过程、模型调用参数、模型响应和调用结果进行完整审计。

#### Scenario: 查看模型调用详情

- WHEN 管理员查看某次模型调用记录
- THEN 系统展示 Prompt、上下文摘要、模型参数、响应摘要、耗时和结果

### Requirement: 工作台指标总览

系统 SHALL 支持具备访问权限的管理角色查看本月报告、知识条目、活跃数据源、引用命中率、活跃用户、报告趋势、知识库引用排行和最近活动。

#### Scenario: 管理角色切换时间范围

- WHEN 用户在工作台选择今天、近 7 天、近 30 天或自定义时间范围
- THEN 系统刷新指标、趋势、排行和最近活动

#### Scenario: 普通角色访问工作台

- WHEN 普通分析师或查看者访问工作台
- THEN 系统按 RBAC 返回授权范围内的数据或拒绝访问

## Traceability

| 项 | 内容 |
| --- | --- |
| Requirement ID | REQ-AUDIT-001, REQ-DASH-001 |
| User Journey | STEP-13, STEP-14, STEP-15, STEP-25, STEP-28, STEP-29 |
| Use Case | UC-12, UC-13 |
| Domain Model | 操作日志、日志详情、工作台指标、趋势点、排行项、活动记录、模型调用记录 |
