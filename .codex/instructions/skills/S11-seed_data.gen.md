<!-- skill: S11 -->
<skill id="S11" name="seed_data.gen">

# 技能：种子数据生成

## Meta
- DependsOn: S9
- Category: database
- Status: stable

## 一句话描述
根据数据库 Schema，生成初始化示例数据。

## 输入
- `schema.sql`：数据库建表脚本
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `seed.sql`：示例数据脚本

## Prompt

你是一位资深数据库工程师。
请完成以下工作：

1. **生成 INSERT 示例数据**：

   ```sql
   -- 用户示例数据
   INSERT INTO `users` (`username`, `password_hash`, `role`) VALUES
     ('admin', '$2a$10$...(bcrypt hash)', 'admin'),
     ('zhangsan', '$2a$10$...(bcrypt hash)', 'user'),
     ('lisi', '$2a$10$...(bcrypt hash)', 'user');
   ```

2. **示例数据表格说明**：

   | 表名 | 插入记录数 | 说明 |
   |------|-----------|------|

3. **如果是 AI 项目，额外生成 AI 相关示例数据**：

   ```sql
   -- 文档示例
   INSERT INTO `documents` (`title`, `file_path`, `status`, `user_id`) VALUES
     ('公司员工手册', '/uploads/hr/handbook.pdf', 'processed', 1),
     ('产品需求文档', '/uploads/product/prd.docx', 'processed', 2);

   -- Prompt 模板示例
   INSERT INTO `prompt_templates` (`name`, `template`, `version`, `scene`) VALUES
     ('知识库问答', '请根据以下参考文档回答问题：\n{context}\n\n问题：{question}', '1.0', 'qa'),
     ('文档摘要', '请用200字以内总结以下文档内容：\n{content}', '1.0', 'summary');
   ```

4. **外键关联数据必须按顺序插入**：

   ```sql
   -- 先插入文档，再插入切片（逻辑关联）
   INSERT INTO `document_chunks` (`document_id`, `chunk_index`, `content`) VALUES
     (1, 0, '公司员工手册第一章内容...'),
     (1, 1, '公司员工手册第二章内容...');
   ```

5. **OpenSpec 场景覆盖**：

   | 示例数据集 | 覆盖表 | OpenSpec capability | Requirement / Scenario | 说明 |
   |------------|--------|---------------------|------------------------|------|

   - 示例数据必须覆盖 OpenSpec 中定义的核心 happy path 和关键异常场景。
   - 不允许为了演示方便新增 OpenSpec 未定义的业务类型、角色或流程。

## 行为规则

- ✅ 密码必须使用 BCrypt 哈希（不可用明文）
- ✅ 示例数据必须覆盖所有表
- ✅ 示例数据必须覆盖 OpenSpec 核心 scenario
- ✅ AI 项目必须包含文档和 Prompt 模板数据
- ✅ 数据之间要有逻辑关联
- ❌ 不得使用真实用户信息
- ❌ 不得引入 OpenSpec 未定义的角色、状态或业务流程
- ❌ 不得包含敏感数据

## 使用示例

```
加载 <skill id="S11">，输入：schema.sql、openspec/specs/
请生成示例数据，包含 AI 知识库相关的文档和 Prompt 模板。
```
</skill>
<!-- end -->
