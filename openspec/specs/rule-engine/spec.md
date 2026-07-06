# rule-engine Specification

## Requirements

### Requirement: 规则创建与维护

系统 SHALL 支持管理员创建规则、从模板创建规则、导入规则、保存规则和运行规则。

#### Scenario: 新建规则草稿

- WHEN 管理员点击新建规则
- THEN 系统创建可编辑的规则草稿

### Requirement: 节点配置与连线

系统 SHALL 支持规则节点选择、拖拽、参数配置、输入输出端口连线和连线合法性校验。

#### Scenario: 非法连线被拒绝

- WHEN 管理员连接不兼容的节点端口
- THEN 系统拒绝保存该连线并提示原因

### Requirement: 调试执行

系统 SHALL 支持执行调试、单步执行和清空日志，并记录节点输入、输出、执行状态、耗时和错误信息。

#### Scenario: 单步执行规则节点

- WHEN 管理员触发单步执行
- THEN 系统执行当前节点并追加节点执行日志

### Requirement: 规则节点契约

系统 SHALL 为规则节点维护节点类型、标题、位置、参数、输入端口和输出端口等契约字段，供后续 API 和测试细化。

#### Scenario: 查看节点参数

- WHEN 管理员选择画布中的规则节点
- THEN 系统展示该节点的类型、参数、输入端口和输出端口

## Traceability

| 项 | 内容 |
| --- | --- |
| Requirement ID | REQ-RULE-001 |
| User Journey | STEP-16, STEP-17, STEP-18, STEP-19 |
| Use Case | UC-08 |
| Domain Model | 规则、规则节点、规则连线、规则版本、规则执行记录、节点执行日志 |
