<!-- skill: S10 -->
<skill id="S10" name="migration.gen">

# 技能：数据库迁移脚本生成

## Meta
- DependsOn: S9
- Category: database
- Status: stable

## 一句话描述
根据数据库 Schema，生成可重复执行的迁移脚本。

## 输入
- `schema.sql`：数据库建表脚本
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `migrations/`：迁移脚本目录

## Prompt

你是一位资深数据库工程师。
请完成以下工作：

1. **生成迁移脚本**（按版本号命名）：

   `migrations/V001__init_schema.sql`：
   ```sql
   -- 初始 Schema 创建
   -- 执行：mysql -u root -p mydb < V001__init_schema.sql

   CREATE TABLE IF NOT EXISTS `users` ( ... );
   CREATE TABLE IF NOT EXISTS `documents` ( ... );
   -- ... 所有建表语句
   ```

2. **生成回滚脚本**：

   `migrations/V001__rollback.sql`：
   ```sql
   -- 回滚初始 Schema
   DROP TABLE IF EXISTS `users`;
   DROP TABLE IF EXISTS `documents`;
   -- ... 按反向顺序删除
   ```

3. **如果是 AI 项目，额外生成向量数据库迁移**：

   `migrations/V002__init_vector_store.sql`：
   ```sql
   -- Milvus Collection 创建
   CREATE COLLECTION IF NOT EXISTS knowledge_chunks ( ... );
   CREATE INDEX IF NOT EXISTS idx_vector ON knowledge_chunks (embedding);
   ```

4. **迁移脚本命名规范**：

   | 版本 | 描述 | 文件名 |
   |------|------|--------|
   | V001 | 初始 Schema | V001__init_schema.sql |
   | V002 | 向量存储 | V002__init_vector_store.sql |
   | V003 | 索引优化 | V003__add_indexes.sql |
   | V004 | 字段新增 | V004__add_columns.sql |

5. **OpenSpec 追溯**：

   | 迁移文件 | 表 / Collection / 索引 | OpenSpec capability | Requirement / Scenario |
   |----------|-------------------------|---------------------|------------------------|

   - 每个建表、字段、索引、向量集合或约束必须能追溯到 OpenSpec requirement / scenario，或明确标注为技术支撑项。
   - 如果 schema 与 OpenSpec 不一致，必须暂停并输出冲突报告，不得直接生成迁移。

## 行为规则

- ✅ 所有迁移脚本必须可重复执行（使用 IF NOT EXISTS）
- ✅ 回滚脚本必须存在且有效
- ✅ 向量数据库迁移必须独立成文件
- ✅ 迁移内容必须追溯 OpenSpec requirement / scenario
- ✅ 脚本必须包含注释说明
- ❌ 不得修改已执行的迁移（只能新增）
- ❌ 不得生成与 OpenSpec frozen baseline 冲突的结构变更
- ❌ 不得包含 DROP TABLE 在生产环境（除非明确标注）

## 使用示例

```
加载 <skill id="S10">，输入：schema.sql、openspec/specs/
请生成数据库迁移脚本，包含向量数据库初始化。
```
</skill>
<!-- end -->
