<!-- skill: S9 -->
<skill id="S9" name="schema.design">

# 技能：数据库设计

## Meta
- DependsOn: S3
- Category: database
- Status: stable

## 一句话描述
根据领域模型，设计数据库表结构和向量数据库 Schema。

## 输入
- `docs/skill-chain/domain_model.md`：领域模型文档
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `schema.sql`：数据库建表脚本（含向量数据库 Schema）

## Prompt

你是一位资深数据库架构师。
请完成以下工作：

数据库设计必须追溯 OpenSpec：每张表、关键字段、索引和约束必须标注支撑的 requirement / scenario。

1. **关系型数据库表设计（MySQL / PostgreSQL）**：

   表结构格式：
   ```sql
   CREATE TABLE `users` (
     `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
     `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
     `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
     `role` ENUM('admin', 'user', 'viewer') DEFAULT 'user' COMMENT '角色',
     `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     INDEX idx_username (username)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
   ```

   表清单：
   | 表名 | 描述 | 核心字段 | 索引 |
   |------|------|----------|------|

2. **向量数据库 Schema 设计（Milvus / Qdrant）**：

   Milvus Collection 示例：
   ```sql
   CREATE COLLECTION knowledge_chunks (
     id BIGINT PRIMARY KEY,
     document_id BIGINT NOT NULL,
     chunk_index INT NOT NULL,
     content TEXT NOT NULL,
     embedding FLOAT_VECTOR(1024),  -- BGE-M3 维度
     metadata JSON,
     created_at TIMESTAMP
   );

   CREATE INDEX idx_document ON knowledge_chunks(document_id);
   ```

   | Collection | 维度 | 索引类型 | 描述 |
   |-------------|------|----------|------|

3. **如果是 AI 项目，额外设计 AI 相关表**：

   | 表名 | 描述 | 关键字段 |
   |------|------|----------|
   | documents | 用户上传的文档 | id, title, file_path, status, user_id |
   | document_chunks | 文档切片 | id, document_id, content, chunk_index, embedding_id |
   | embeddings | 向量记录 | id, chunk_id, vector, model_name |
   | conversations | 对话会话 | id, user_id, title, created_at |
   | messages | 对话消息 | id, conversation_id, role, content, tokens, timestamp |
   | prompt_templates | Prompt 模板 | id, name, template, version, scene |
   | llm_models | 模型配置 | id, name, provider, api_endpoint, max_tokens |
   | token_usage | Token 用量统计 | id, user_id, model, tokens, cost, timestamp |

4. **数据库设计原则**：

   | 原则 | 说明 |
   |------|------|
   | 主键使用 BIGINT AUTO_INCREMENT | 性能 + 可扩展 |
   | 外键用逻辑约束，不用物理外键 | 避免性能瓶颈 |
   | 大表加索引，小表不用 | 避免索引膨胀 |
   | 向量字段单独存储 | 方便扩展和迁移 |
   | 软删除用 deleted_at 字段 | 数据可恢复 |

## 行为规则

- ✅ 所有字段必须有 COMMENT 中文注释
- ✅ 每张表必须能追溯到 OpenSpec capability / requirement，或明确说明为技术支撑表
- ✅ 向量维度必须与选定的 Embedding 模型匹配（BGE-M3=1024, BERT=768）
- ✅ AI 项目必须包含向量 Collection 设计
- ✅ 必须包含索引设计
- ❌ 不得使用 SELECT *（但此处是建表，强调后续代码禁止 SELECT *）
- ❌ 不得使用物理外键约束

## 使用示例

```
加载 <skill id="S9">，输入：docs/skill-chain/domain_model.md
请设计数据库表结构和 Milvus 向量 Collection。
```
</skill>
<!-- end -->
