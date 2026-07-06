# 智能报告生成系统用户旅程文档

> Skill：S2 user_journey.map  
> 输入：`docs/skill-chain/requirements.md`、`原型/*.html`、`.omx/prototype-extract.json`  
> 当前用途：按原型页面反推用户旅程，并映射到 Requirement ID，作为后续开发和验收依据。

## 1. 原型逆向 Step Journey

| Step | 页面 | 原型证据 | 用户动作 | 旅程目标 | Requirement ID |
| --- | --- | --- | --- | --- | --- |
| STEP-01 | `index.html` | AI 对话输入、报告需求输入框、发送按钮 | Input / Click | 从自然语言发起报告生成 | REQ-REPORT-001, REQ-REPORT-002 |
| STEP-02 | `index.html` | 模板、周期、对象范围、关注重点、风格、一键生成报告 | Input / Click | 通过模板填报生成标准化报告 | REQ-REPORT-001, REQ-REPORT-002 |
| STEP-03 | `index.html` | 报告预览、流式实时预览、AI 驱动、数据可溯源 | View | 查看生成状态和流式正文 | REQ-REPORT-002 |
| STEP-04 | `index.html` | 溯源引用、规则库、财报库、法规库、调研库 | Click / View | 校验报告证据来源和引用质量 | REQ-REPORT-003 |
| STEP-05 | `index.html` | PDF、Word、PPT、Markdown 导出 | Click | 导出企业规范报告文件 | REQ-REPORT-004 |
| STEP-06 | `index.html` | 版本历史、当前版本、回滚按钮 | View / Click | 查看并恢复历史版本 | REQ-REPORT-005 |
| STEP-07 | `knowledge-base.html` | 规则库、财报库、法规库、调研库、新建知识库 | Click | 维护知识库分类 | REQ-KB-001 |
| STEP-08 | `knowledge-base.html` | 知识条目表格、查看、删除 | View / Click | 管理知识条目 | REQ-KB-001 |
| STEP-09 | `knowledge-base.html` | 类型筛选、搜索、分页 | Input / Click | 搜索筛选知识条目 | REQ-KB-001 |
| STEP-10 | `knowledge-base.html` | 手动录入、标题、所属知识库、内容类型、标签、内容 | Input / Click | 手动录入知识条目 | REQ-KB-001 |
| STEP-11 | `knowledge-base.html` | 文件上传、PDF/Excel/Word/CSV/TXT | Click / Input | 上传文件并解析入库 | REQ-KB-002 |
| STEP-12 | `knowledge-base.html` | 数据源对接配置、数据库、API、定时拉取、测试连接 | Input / Click | 配置企业数据源同步 | REQ-KB-003 |
| STEP-13 | `dashboard.html` | 本月报告产出、知识条目总数、活跃数据源、引用命中率、活跃用户 | View | 查看系统运行概览 | REQ-DASH-001 |
| STEP-14 | `dashboard.html` | 今天、近 7 天、近 30 天、自定义、报告生成趋势 | Click / View | 按时间范围分析报告产出 | REQ-DASH-001 |
| STEP-15 | `dashboard.html` | 知识库引用排行、最近活动、查看全部 | View / Click | 复盘知识库使用和系统活动 | REQ-DASH-001, REQ-AUDIT-001 |
| STEP-16 | `rule-engine.html` | 规则列表、新建、从模板创建、导入规则、保存、运行 | Click | 创建和维护规则 | REQ-RULE-001 |
| STEP-17 | `rule-engine.html` | 输入、解析、分类、关联、输出节点 | Click / Input | 编排规则节点 | REQ-RULE-001 |
| STEP-18 | `rule-engine.html` | `createNode`、`startConnection`、`drawLines`、画布连线 | Click / Drag | 在画布中建立规则流程 | REQ-RULE-001 |
| STEP-19 | `rule-engine.html` | 调试面板、执行调试、单步执行、清空日志 | Click / View | 验证规则执行结果 | REQ-RULE-001 |
| STEP-20 | `user-permission.html` | 用户管理、添加用户、批量导入、角色筛选 | Input / Click | 管理用户账号 | REQ-AUTH-001 |
| STEP-21 | `user-permission.html` | 用户名、姓名、角色、部门、状态、编辑、禁用、启用 | View / Click | 控制账号状态 | REQ-AUTH-001 |
| STEP-22 | `user-permission.html` | 权限矩阵、管理员、高级分析师、分析师、查看者 | View | 核对 RBAC 权限 | REQ-AUTH-001 |
| STEP-23 | `user-permission.html` | 报告协作、分享、访问密码、有效期、生成 | Input / Click | 生成外部分享链接 | REQ-COLLAB-001 |
| STEP-24 | `user-permission.html` | 添加批注、指派给、提交批注、新建任务 | Input / Click | 通过批注和任务协作 | REQ-COLLAB-002 |
| STEP-25 | `user-permission.html` | 操作日志、创建、编辑、删除、分享、导出 | Click / View | 管理员查看全局审计 | REQ-AUDIT-001 |
| STEP-26 | `my-reports.html` | 我的报告、新建报告、报告名称、生成方式、状态、操作 | View / Click | 管理个人或授权报告 | REQ-REPORT-001, REQ-REPORT-005 |
| STEP-27 | `my-reports.html` | 每页 5/10/20 条、分页按钮 | Click | 浏览更多报告 | REQ-REPORT-001 |
| STEP-28 | `history.html` | 历史记录、时间、操作类型、内容、操作人、详情 | View / Click | 查看个人操作历史 | REQ-AUDIT-001 |
| STEP-29 | `history.html` | 智能生成、模板填报、导出、数据上传、审核样例 | View | 复盘历史行为；审核仅为样例，不进入审批范围 | REQ-AUDIT-001, REQ-SCOPE-001 |
| STEP-30 | 全局导航 | 报告中心、管理中心、角色切换、通知、设置 | Click / View | 跨页面导航并按角色显示权限 | REQ-AUTH-001, REQ-COLLAB-002 |
| STEP-31 | 列表页通用 | 表格、分页、搜索、行操作 | Input / Click | 补全搜索、查看、分页等列表旅程 | REQ-KB-001, REQ-AUDIT-001 |

## 2. 用例清单

| UC | 用例 | 参与者 | 主流程 | 预期结果 |
| --- | --- | --- | --- | --- |
| UC-01 | 智能生成报告 | 业务分析师、分析师、高级分析师 | 输入自然语言需求 -> AI 生成大纲 -> 用户确认 -> 检索知识库 -> 分析数据 -> 流式生成报告 -> 保存版本 | 生成带引用、版本和审计记录的报告 |
| UC-02 | 模板填报生成报告 | 管理人员、分析师、高级分析师 | 选择模板 -> 填写周期、对象范围、关注重点、风格 -> 一键生成 -> 确认大纲 -> 输出报告 | 生成标准化报告 |
| UC-03 | 校验溯源引用 | 查看者、分析师、管理人员 | 打开报告 -> 点击正文引用或来源 -> 查看来源摘要、类型、评分 | 用户能判断结论依据 |
| UC-04 | 导出企业规范报告 | 业务分析师、管理人员、分析师 | 选择格式 -> 应用版式和品牌规范 -> 生成文件 -> 记录日志 | 输出企业规范报告文件 |
| UC-05 | 管理知识条目 | 管理员、高级分析师 | 进入知识库 -> 搜索筛选 -> 查看详情 -> 新增/删除/更新 -> 更新索引 | 知识条目可被 RAG 检索 |
| UC-06 | 上传并解析文档 | 管理员、高级分析师、业务人员 | 上传文件 -> OCR/表格/扫描件解析 -> 生成知识条目 -> 确认入库 -> 建立索引 | 文件内容进入知识库 |
| UC-07 | 配置企业数据源 | 管理员、高级分析师 | 选择数据源类型 -> 填连接参数 -> 测试连接 -> 保存同步策略 | 数据源持续同步 |
| UC-08 | 编排并调试规则 | 管理员 | 新建规则 -> 拖拽节点 -> 连线 -> 配置参数 -> 保存 -> 调试 -> 查看日志 | 规则可用于入库解析 |
| UC-09 | 分享报告给外部用户 | 高级分析师、分析师、管理员、外部协作用户 | 设置分享范围、密码和有效期 -> 生成链接 -> 外部访问 -> 记录访问日志 | 外部用户受控查看报告 |
| UC-10 | 批注与任务协作 | 分析师、高级分析师、查看者、业务人员 | 选中文本 -> 添加批注 -> 指派处理人 -> 生成任务 -> 状态流转 -> 站内通知 | 形成协作闭环 |
| UC-11 | 管理用户与 RBAC | 管理员 | 添加用户 -> 分配角色部门 -> 启停账号 -> 查看权限矩阵 -> 记录日志 | RBAC 权限生效且可审计 |
| UC-12 | 查看历史与审计日志 | 普通用户、管理员 | 普通用户看个人历史；管理员看全局审计和模型调用审计 | 历史和审计边界清晰 |
| UC-13 | 查看工作台仪表板 | 管理员、管理人员、高级分析师 | 进入工作台 -> 选时间范围 -> 查看指标、趋势、排行、活动 | 管理角色获得运行概览 |
| UC-14 | 回滚报告版本 | 报告创建者、高级分析师、管理员 | 打开版本历史 -> 选择版本 -> 回滚 -> 生成新版本记录 | 报告恢复并保留审计 |

## 3. 后续开发规则

- 每个页面作为一个验收 Step，必须保留用户动作：`Click`、`Input`、`View`、`Drag`。
- 若页面结构暗示 CRUD、分页、筛选、行操作，即使原型按钮文案不完整，也必须在旅程中补齐。
- 每个开发任务必须映射到 Requirement ID 和 UC。
- 不能只交付页面或接口骨架，必须能跑通对应用户旅程。
