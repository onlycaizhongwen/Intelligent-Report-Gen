# knowledge-base-ingestion Specification

## Requirements

### Requirement: 知识库与知识条目管理

系统 SHALL 支持知识库切换、自定义知识库创建、知识条目搜索、筛选、分页、查看、更新和删除。

#### Scenario: 搜索筛选知识条目

- WHEN 用户输入关键词或选择类型筛选
- THEN 系统返回符合条件的知识条目列表并保持分页信息

#### Scenario: 删除被引用知识条目

- WHEN 用户尝试删除已被报告引用的知识条目
- THEN 系统提示引用影响并要求用户确认或取消

### Requirement: 手动录入知识条目

系统 SHALL 支持用户填写标题、所属知识库、内容类型、标签和内容后手动录入知识条目。

#### Scenario: 手动条目入库

- WHEN 用户提交完整知识条目
- THEN 系统保存知识条目并更新索引状态

### Requirement: 文件上传解析入库

系统 SHALL 支持 PDF、Excel、Word、CSV、TXT 文件上传解析，并支持 OCR、图片表格识别和扫描件处理。

#### Scenario: 扫描件解析成功

- WHEN 用户上传扫描件并确认入库
- THEN Java 业务核心写入对象存储和元数据
- AND Python AI 服务异步生成解析结果、知识条目和可检索索引

#### Scenario: OCR 或表格识别失败

- WHEN 文件解析过程发生 OCR 或表格识别失败
- THEN 系统记录失败原因
- AND 前端展示可修正或重试状态

### Requirement: 企业数据源对接

系统 SHALL 支持企业内部数据库、ERP、OA、财务系统、API 和定时拉取等数据源配置、测试连接和同步状态管理。

#### Scenario: 数据源连接失败

- WHEN 用户提交错误的数据源连接参数并测试连接
- THEN 系统返回失败原因
- AND 系统不得将该数据源标记为可同步

#### Scenario: 数据源同步异常

- WHEN 数据源自动同步失败
- THEN 系统记录同步异常
- AND 异常可在活动或审计记录中追踪

### Requirement: Java 与 Python 解耦执行

系统 SHALL 由 Java 业务核心承接对外文档上传请求，并将解析执行解耦给 Python AI 服务。

#### Scenario: 上传后发布解析事件

- WHEN Java 业务核心完成文件校验、对象存储和元数据登记
- THEN Java 发布 `document.parse.requested` 业务事件
- AND Python AI 服务异步执行 OCR、表格识别、扫描件处理、分块和索引

## Traceability

| 项 | 内容 |
| --- | --- |
| Requirement ID | REQ-KB-001, REQ-KB-002, REQ-KB-003 |
| User Journey | STEP-07, STEP-08, STEP-09, STEP-10, STEP-11, STEP-12 |
| Use Case | UC-05, UC-06, UC-07 |
| Domain Model | 知识库、知识条目、知识文档、文档片段、Embedding、数据源连接、同步任务 |
