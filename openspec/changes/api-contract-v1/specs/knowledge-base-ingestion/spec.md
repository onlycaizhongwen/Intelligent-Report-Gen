# knowledge-base-ingestion API Delta

## ADDED Requirements

### Requirement: 知识库与文档上传 API

系统 SHALL 提供知识库、知识条目、文档上传、解析状态、数据源测试连接和同步配置 API。

#### Scenario: 上传文档

- WHEN 客户端调用 `POST /api/v1/documents/upload`
- THEN Java 业务核心保存对象和元数据
- AND 系统发布 `document.parse.requested` 事件给 Python AI 服务

#### Scenario: 查询知识条目

- WHEN 客户端调用 `GET /api/v1/knowledge-items`
- THEN 系统返回符合筛选条件的知识条目分页结果

#### Scenario: 测试数据源连接

- WHEN 客户端调用 `POST /api/v1/data-sources/test-connection`
- THEN 系统返回连接是否成功和失败原因
