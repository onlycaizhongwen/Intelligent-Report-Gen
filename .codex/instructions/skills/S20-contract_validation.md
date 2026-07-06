<!-- skill: S20 -->
<skill id="S20" name="contract_validation">

# 技能：契约校验

## Meta
- DependsOn: S4, S12, S19
- Category: quality
- Status: stable

## 一句话描述
校验 OpenSpec、API 契约、后端接口实现和前端 API 客户端是否严格一致。

## 输入
- `docs/skill-chain/api_contract.md`：API 接口文档
- `openspec/specs/`：OpenSpec capability 目录
- `backend/`：DDD 后端代码目录
- `api/`（前端 API 客户端代码）

## 输出
- `docs/skill-chain/validation_report.md`：契约校验报告（中文 Markdown）

## Prompt

你是一位资深质量保障工程师。
请逐项校验以下内容，输出详细报告。

### 校验清单

1. **路径一致性**：
   | API 契约路径 | 后端实现 | 前端调用 | 状态 |
   |---------------|----------|----------|------|

2. **HTTP 方法一致性**：
   | 接口 | 契约方法 | 后端方法 | 前端方法 | 状态 |
   |------|----------|----------|----------|------|

3. **请求参数一致性**：
   | 参数 | 契约类型 | 后端接收类型 | 前端发送类型 | 状态 |
   |------|----------|-------------|-------------|------|

4. **响应结构一致性**：
   | 字段 | 契约定义 | 后端实际返回 | 前端期望 | 状态 |
   |------|----------|-------------|----------|------|

5. **错误码覆盖**：
   | 错误码 | 契约定义 | 后端实现 | 前端处理 | 状态 |
   |--------|----------|----------|----------|------|

6. **如果是 AI 项目，额外校验 SSE 流**：

   | 检查项 | 契约定义 | 后端实现 | 前端处理 | 状态 |
   |--------|----------|----------|----------|------|
   | SSE 事件格式 | `data: {type, content}` | 后端发送格式 | EventSource 解析 | ✅/❌ |
   | 流式结束标记 | `data: {type: "done"}` | 后端发送 | 前端识别 | ✅/❌ |
   | 引用来源传递 | references 字段 | 后端附加 | 前端展示 | ✅/❌ |

7. **OpenSpec 一致性校验**：

   | OpenSpec Requirement / Scenario | API 契约 | 后端实现 | 前端调用 | 测试覆盖 | 状态 |
   |---------------------------------|----------|----------|----------|----------|------|

   - OpenSpec 中每个 `SHALL` 必须有 API、代码或测试覆盖。
   - API 契约中每个接口必须能追溯到 OpenSpec requirement / scenario。
   - 任何实现偏离 OpenSpec 的地方必须标记为失败项。

## 报告格式

```markdown
# OpenSpec + API 契约校验报告

## 概要
- 校验时间：2024-01-01 12:00:00
- 总接口数：XX
- 通过：XX
- 失败：XX
- 警告：XX

## 失败项（必须修复）

### ❌ [GET] /api/v1/users/{id}
- 问题：后端返回字段 `created_at`，契约定义为 `createdAt`
- 影响：前端类型不匹配
- 建议：后端使用 `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` 或前端适配

## 警告项（建议修复）

### ⚠️ [POST] /api/v1/documents/upload
- 问题：前端超时设置为 120s，后端处理可能超过 120s
- 建议：后端改为异步处理 + 轮询状态

## 通过项

### ✅ [GET] /api/v1/health
- 路径、方法、响应结构完全一致
```

## 行为规则

- ✅ 必须逐项检查，不得遗漏
- ✅ 必须校验 OpenSpec requirement / scenario 覆盖情况
- ✅ 报告必须是中文 Markdown 表格
- ✅ AI 项目必须校验 SSE 流格式
- ✅ 必须给出修复建议
- ❌ 不得忽略错误码差异
- ❌ 不得忽略 OpenSpec 与代码实现差异
- ❌ 不得只看代码声明（要看实际实现）

## 使用示例

```
加载 <skill id="S20">，输入：openspec/specs/、docs/skill-chain/api_contract.md、backend/、api/
请校验 OpenSpec、前后端 API 契约一致性，包含 SSE 流式接口。
```
</skill>
<!-- end -->
