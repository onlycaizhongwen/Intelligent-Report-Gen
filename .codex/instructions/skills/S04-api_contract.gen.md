<!-- skill: S4 -->
<skill id="S4" name="api_contract.gen">

# 技能：API 契约生成

## Meta
- DependsOn: S3
- Category: design
- Status: stable

## 一句话描述
根据领域模型，生成完整的 API 接口文档。

## 输入
- `docs/skill-chain/domain_model.md`：领域模型文档
- `openspec/specs/`：OpenSpec capability 草案目录

## 输出
- `docs/skill-chain/api_contract.md`：API 接口文档（中文 Markdown）
- `openspec/changes/<change-id>/proposal.md`：OpenSpec 变更说明
- `openspec/changes/<change-id>/tasks.md`：OpenSpec 实施任务
- `openspec/changes/<change-id>/specs/`：API 契约对应的 OpenSpec delta

## Prompt

你是一位资深 API 架构师。
请基于领域模型，完成以下工作：

1. **RESTful API 设计原则**：
   - 资源命名使用复数名词（如 /users, /documents）
   - 使用正确的 HTTP 方法（GET/POST/PUT/DELETE）
   - 统一的响应结构

2. **统一响应格式**：

   ```json
   {
     "code": 200,
     "message": "操作成功",
     "data": { ... },
     "timestamp": "2024-01-01T00:00:00Z"
   }
   ```

3. **API 列表与 JSON 契约**：

   | 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
   |----------|------|------|----------|----------|--------|

   - API 总览可以使用 Markdown 表格。
   - 每个接口的请求参数和响应数据必须使用 `json` 代码块描述，禁止只用表格描述。
   - JSON 契约必须采用可检查结构，至少包含：
     - `method`
     - `path`
     - `contentType`
     - `pathParams`
     - `queryParams`
     - `headers`
     - `requestBody`
     - `responseBody`
     - `statusCodes`
   - `requestBody` 和 `responseBody` 必须写明字段名、类型、是否必填、中文说明、枚举值或嵌套结构。
   - 对于 GET/DELETE 等无 Body 的接口，`requestBody` 必须显式为 `null`，不得省略。
   - 对于 SSE 接口，`responseBody` 必须描述事件 JSON 结构和事件类型。

   JSON 契约示例：

   ```json
   {
     "method": "POST",
     "path": "/api/v1/reports/generation-tasks",
     "contentType": "application/json",
     "pathParams": {},
     "queryParams": {},
     "headers": {
       "Authorization": {
         "type": "string",
         "required": true,
         "description": "访问令牌"
       }
     },
     "requestBody": {
       "topic": {
         "type": "string",
         "required": true,
         "description": "报告主题或自然语言需求"
       }
     },
     "responseBody": {
       "code": { "type": "integer", "description": "响应码" },
       "message": { "type": "string", "description": "响应消息" },
       "data": {
         "type": "object",
         "properties": {
           "taskId": { "type": "string", "description": "生成任务 ID" }
         }
       },
       "timestamp": { "type": "string", "description": "响应时间" }
     },
     "statusCodes": [200, 400, 401, 403, 503]
   }
   ```

4. **如果是 AI 项目，额外包含 AI 相关接口**：

   | 接口路径 | 方法 | 描述 | 特殊说明 |
   |----------|------|------|----------|
   | /api/v1/chat | POST | 对话接口 | 支持 SSE 流式返回 |
   | /api/v1/documents/upload | POST | 文档上传 | 支持 PDF/Word/TXT |
   | /api/v1/documents/search | POST | 语义搜索 | 向量相似度检索 |
   | /api/v1/embeddings | POST | 生成向量 | 文本转 Embedding |

   SSE 流式响应格式：
   ```
   data: {"type": "token", "content": "你"}
   data: {"type": "token", "content": "好"}
   data: {"type": "done", "references": [...]}
   ```

5. **错误码定义**：

   | 错误码 | 描述 | 处理建议 |
   |--------|------|----------|
   | 400 | 请求参数错误 | 检查请求参数 |
   | 401 | 未授权 | 检查 Token |
   | 403 | 无权限 | 检查角色 |
   | 404 | 资源不存在 | 检查资源 ID |
   | 429 | 请求过于频繁 | 限流降级 |
   | 500 | 服务器内部错误 | 联系管理员 |
   | 503 | 服务不可用 | AI 服务繁忙，请稍后重试 |

6. **OpenSpec 映射**：

   | API 接口 | OpenSpec capability | Requirement | Scenario | 说明 |
   |----------|---------------------|-------------|----------|------|

   - 每个 API 必须映射到至少一个 OpenSpec requirement 或 scenario。
   - 如 API 契约引入新能力，必须在 `openspec/changes/<change-id>/specs/` 中补充 delta。
   - `proposal.md` 必须说明变更动机、影响范围和不做事项。
   - `tasks.md` 必须列出 S8-S30 下游需要实现、测试、部署或文档化的任务。

## 行为规则

- ✅ 所有接口必须有完整的请求/响应定义
- ✅ 请求参数和响应数据必须提供 JSON 契约，表格只能作为总览或辅助说明
- ✅ 每个接口必须映射 OpenSpec capability / requirement / scenario
- ✅ API 契约变更必须同步生成 OpenSpec change/delta
- ✅ 路径命名必须符合 RESTful 规范
- ✅ AI 项目必须包含 SSE 流式接口定义
- ✅ 所有字段描述使用中文
- ❌ 不得出现未定义的响应结构
- ❌ 不得跳过错误码定义

## 使用示例

```
加载 <skill id="S4">，输入：docs/skill-chain/domain_model.md
请生成完整的 RESTful API 契约文档，包含 AI 检索接口。
```
</skill>
<!-- end -->
