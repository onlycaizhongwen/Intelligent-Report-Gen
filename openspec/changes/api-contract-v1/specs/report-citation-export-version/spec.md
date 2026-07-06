# report-citation-export-version API Delta

## ADDED Requirements

### Requirement: 引用导出版本 API

系统 SHALL 提供报告详情、引用来源、企业规范导出、版本列表和版本回滚 API。

#### Scenario: 查询引用来源

- WHEN 客户端调用 `GET /api/v1/reports/{reportId}/references/{referenceId}`
- THEN 系统返回来源快照、来源类型、证据评分和引用锚点

#### Scenario: 创建导出任务

- WHEN 客户端调用 `POST /api/v1/reports/{reportId}/exports`
- THEN 系统返回导出文件 ID、状态和下载策略

#### Scenario: 回滚版本

- WHEN 客户端调用 `POST /api/v1/reports/{reportId}/versions/{versionId}/rollback`
- THEN 系统创建新的当前版本并返回版本 ID
