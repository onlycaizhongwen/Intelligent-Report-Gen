# report-citation-export-version Specification

## Requirements

### Requirement: 引用锚点与来源双向定位

系统 SHALL 支持报告正文引用标记与来源列表双向定位，并展示来源摘要、来源类型和来源快照。

#### Scenario: 点击正文引用

- WHEN 用户点击正文引用锚点
- THEN 系统定位对应引用来源并高亮来源信息

#### Scenario: 点击来源列表

- WHEN 用户点击来源列表中的来源
- THEN 系统定位并高亮关联报告段落

### Requirement: 引用质量评估

系统 SHALL 为引用来源提供证据可信度评分或引用质量评估，帮助用户判断结论依据。

#### Scenario: 查看来源质量

- WHEN 用户展开引用来源详情
- THEN 系统展示评分、评分依据或质量说明

### Requirement: 企业规范导出

系统 SHALL 支持 PDF、Word、PPT、Markdown 导出，并支持导出版式、模板、目录、页眉页脚和企业品牌规范。

#### Scenario: 导出模板存在

- WHEN 用户选择格式和企业模板后提交导出
- THEN 系统基于当前报告版本生成真实文件
- AND 系统返回导出状态和下载地址

#### Scenario: 导出模板缺失

- WHEN 用户请求使用不存在或未授权的导出模板
- THEN 系统拒绝导出
- AND 系统返回明确错误原因，不得静默降级为默认模板

### Requirement: 报告版本管理

系统 SHALL 在报告生成和回滚时保存版本，支持用户查看历史版本并回滚到指定版本。

#### Scenario: 回滚报告版本

- WHEN 用户选择历史版本并确认回滚
- THEN 系统基于历史版本创建新的当前版本
- AND 系统记录回滚审计事件

## Traceability

| 项 | 内容 |
| --- | --- |
| Requirement ID | REQ-REPORT-003, REQ-REPORT-004, REQ-REPORT-005 |
| User Journey | STEP-04, STEP-05, STEP-06 |
| Use Case | UC-03, UC-04, UC-14 |
| Domain Model | 报告、报告版本、导出文件、引用来源、引用锚点、证据评分 |
