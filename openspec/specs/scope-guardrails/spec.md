# scope-guardrails Specification

## Requirements

### Requirement: 当前阶段范围排除项

系统 SHALL NOT 在当前阶段引入多租户、私有化部署、高可用、灾备或报告人工审核发布流程。

#### Scenario: 后续设计检查范围排除项

- WHEN 后续 Skill 生成架构、模块、API 或代码设计
- THEN 设计不得默认加入多租户、私有化部署、高可用、灾备或报告人工审核发布

### Requirement: 历史审核样例不进入范围

系统 SHALL 将历史记录中的“审核”文本仅视为样例数据，不得将其解释为当前报告人工审核发布需求。

#### Scenario: 处理历史审核样例

- WHEN 原型或种子数据出现“审核”字样
- THEN 系统仅按历史操作类型展示
- AND 不新增审核流、审批节点或发布审批状态

### Requirement: 本地开发环境约束

系统 SHALL 区分生产环境和本地开发环境，本地开发优先通过 Docker Compose 复用或最小资源部署依赖。

#### Scenario: 本地依赖缺失

- WHEN 本地 Docker 中缺少必要依赖
- THEN 系统可以补充最小资源容器
- AND 新增依赖前必须遵循本地资源最小化、端口冲突规避和配置隔离原则

## Traceability

| 项 | 内容 |
| --- | --- |
| Requirement ID | REQ-SCOPE-001 |
| User Journey | STEP-29, 全局 |
| Use Case | 全局护栏 |
| Domain Model | 范围约束、本地环境约束 |
