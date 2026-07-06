<!-- skill: S3 -->
<skill id="S3" name="domain_modeling">

# 技能：领域建模

## Meta
- DependsOn: S1, S2
- Category: design
- Status: stable

## 一句话描述
根据用户需求和旅程，建立领域驱动设计（DDD）模型。

## 输入
- `docs/skill-chain/requirements.md`：结构化需求文档
- `docs/skill-chain/user_journey.md`：用户旅程文档

## 输出
- `docs/skill-chain/domain_model.md`：领域模型文档（中文 Markdown）
- `openspec/project.md`：OpenSpec 项目说明（目标、范围、术语、非目标）
- `openspec/specs/`：OpenSpec capability 草案目录

## Prompt

你是一位领域驱动设计（DDD）专家。
请完成以下工作：

1. **识别聚合根（Aggregate Root）**：

   | 聚合根 | 所属限界上下文 | 核心职责 | 包含实体 |
   |--------|----------------|----------|----------|

2. **识别实体（Entity）**：

   | 实体名称 | 所属聚合 | 唯一标识 | 核心属性 | 业务行为 |
   |----------|----------|----------|----------|----------|

3. **识别值对象（Value Object）**：

   | 值对象 | 描述 | 属性 | 不可变性 |
   |--------|------|------|----------|

4. **领域关系（Relationship）**：

   | 关系类型 | 源实体 | 目标实体 | 基数 | 描述 |
   |----------|--------|----------|------|------|

5. **如果是 AI 项目，额外建模 AI 领域对象**：

   | AI 领域对象 | 类型 | 描述 | 与业务实体的关系 |
   |--------------|------|------|------------------|
   | 文档（Document） | 聚合根 | 用户上传的知识文档 | 属于某个知识库 |
   | Embedding（向量） | 值对象 | 文档片段的向量表示 | 归属于文档 |
   | 检索结果（Retrieval） | 领域事件 | 向量检索返回的结果 | 触发 AI 生成 |
   | Prompt 模板 | 值对象 | 系统预设的提示词模板 | 被检索结果引用 |
   | LLM 响应 | 领域事件 | 大模型返回的内容 | 包含引用来源 |

6. **限界上下文（Bounded Context）划分**：

   | 限界上下文 | 包含聚合根 | 核心职责 |
   |-------------|------------|----------|

7. **生成 OpenSpec capability 草案**：

   - 若 `openspec/` 不存在，必须初始化 `openspec/project.md` 与 `openspec/specs/` 目录结构。
   - 若 `openspec/` 已存在，必须基于现有 OpenSpec 增量更新；不得覆盖已确认的 project、requirement 或 scenario。
   - 为每个核心业务能力生成一个 `openspec/specs/<capability>/spec.md` 草案。
   - 每个 requirement 必须使用 `SHALL` 表达强约束。
   - 每个 requirement 至少包含一个 `Scenario`，用于后续测试和验收。
   - 每个 capability 必须能追溯到 `docs/skill-chain/requirements.md`、`docs/skill-chain/user_journey.md` 和 `docs/skill-chain/domain_model.md`。

   模板：
   ```md
   ## ADDED Requirements

   ### Requirement: <能力名称>
   系统 SHALL <可验证的行为约束>。

   #### Scenario: <场景名称>
   - WHEN <触发条件>
   - THEN <可观察结果>
   ```

## 行为规则

- ✅ 所有模型元素必须可追溯至需求
- ✅ 必须生成 OpenSpec project 与 capability 草案
- ✅ `openspec/` 不存在时必须初始化，已存在时必须增量更新
- ✅ OpenSpec requirement 必须使用 SHALL，scenario 必须可测试
- ✅ AI 项目必须包含向量/Embedding 相关建模
- ✅ 使用中文命名领域对象
- ❌ 不得覆盖已确认的 OpenSpec project、requirement 或 scenario
- ❌ 不得包含技术实现细节（如数据库、框架）
- ❌ 不得混淆实体和值对象的边界

## 使用示例

```
加载 <skill id="S3">，输入：docs/skill-chain/requirements.md 和 docs/skill-chain/user_journey.md
请建立完整的领域模型，包含 AI 检索相关的领域对象。
```
</skill>
<!-- end -->
