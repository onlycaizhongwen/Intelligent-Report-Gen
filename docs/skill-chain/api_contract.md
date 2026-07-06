# 智能报告生成系统 API 契约文档

> Skill：S4 api_contract.gen  
> 输入：`docs/skill-chain/domain_model.md`、`openspec/specs/`  
> OpenSpec change：`openspec/changes/api-contract-v1/`  
> 状态：S4 草案，待用户审批后进入 S5

## 1. RESTful API 设计原则

| 原则 | 约束 |
|------|------|
| 资源命名 | 使用复数名词，例如 `/api/v1/reports`、`/api/v1/knowledge-bases` |
| HTTP 方法 | `GET` 查询，`POST` 创建或动作提交，`PUT` 整体更新，`DELETE` 删除 |
| 版本前缀 | 所有业务接口统一使用 `/api/v1` |
| 响应结构 | 所有非 SSE 接口使用统一响应结构 |
| 分页参数 | 列表接口统一支持 `page`、`pageSize`，按需支持 `keyword`、`status`、`type` |
| 审计要求 | 关键写操作必须产生操作日志或可追溯审计记录 |
| OpenSpec 映射 | 每个接口必须映射至少一个 OpenSpec capability / requirement / scenario |

## 2. 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-06-18T00:00:00Z"
}
```

## 3. 分页响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 10,
    "total": 0
  },
  "timestamp": "2026-06-18T00:00:00Z"
}
```

## 4. SSE 流式响应格式

```text
data: {"type":"stage","taskId":"task_001","content":"检索知识库","stage":"retrieval","references":[],"progress":0.3,"errorCode":null,"traceId":"trace_001"}
data: {"type":"delta","taskId":"task_001","content":"本季度","stage":"writing","references":[],"progress":0.6,"errorCode":null,"traceId":"trace_001"}
data: {"type":"references","taskId":"task_001","content":"","stage":"writing","references":[{"referenceId":"ref_001","title":"华东区销售数据"}],"progress":null,"errorCode":null,"traceId":"trace_001"}
data: {"type":"done","taskId":"task_001","content":"completed","stage":"export","references":[],"progress":1.0,"errorCode":null,"traceId":"trace_001"}
data: {"type":"error","taskId":"task_001","content":"AI 服务繁忙，请稍后重试","stage":null,"references":[],"progress":null,"errorCode":"AI_MODEL_UNAVAILABLE","traceId":"trace_001"}
```

## 5. API 总览

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/generation-tasks` | POST | 创建自然语言报告生成任务 | JSON Body | 生成任务 | 200/400/401/403/503 |
| `/api/v1/reports/template-generation-tasks` | POST | 创建模板填报报告生成任务 | JSON Body | 生成任务 | 200/400/401/403/503 |
| `/api/v1/reports/generation-tasks/{taskId}/outline` | PUT | 确认或修改报告大纲 | Path + JSON Body | 大纲确认结果 | 200/400/401/403/404 |
| `/api/v1/reports/generation-tasks/{taskId}/stream` | GET | 订阅报告生成 SSE 流 | Path | SSE 事件流 | 200/401/403/404/503 |
| `/api/v1/chat` | POST | AI 对话式报告生成入口 | JSON Body | SSE 事件流或生成任务 | 200/400/401/403/503 |
| `/api/v1/reports` | GET | 查询我的报告或授权报告列表 | Query | 分页报告列表 | 200/401/403 |
| `/api/v1/reports/{reportId}` | GET | 查看报告详情 | Path | 报告详情 | 200/401/403/404 |
| `/api/v1/reports/{reportId}/references/{referenceId}` | GET | 查看引用来源与评分 | Path | 引用来源详情 | 200/401/403/404 |
| `/api/v1/reports/{reportId}/exports` | POST | 创建报告导出任务 | Path + JSON Body | 导出文件 | 200/400/401/403/404 |
| `/api/v1/reports/{reportId}/versions` | GET | 查看报告版本列表 | Path | 版本列表 | 200/401/403/404 |
| `/api/v1/reports/{reportId}/versions/{versionId}/rollback` | POST | 回滚报告版本 | Path | 新版本 | 200/401/403/404 |
| `/api/v1/knowledge-bases` | GET | 查询知识库列表 | Query | 知识库列表 | 200/401/403 |
| `/api/v1/knowledge-bases` | POST | 新建知识库 | JSON Body | 知识库 | 200/400/401/403 |
| `/api/v1/knowledge-items` | GET | 搜索筛选知识条目 | Query | 分页知识条目 | 200/401/403 |
| `/api/v1/knowledge-items` | POST | 手动录入知识条目 | JSON Body | 知识条目 | 200/400/401/403 |
| `/api/v1/knowledge-items/{itemId}` | DELETE | 删除知识条目 | Path + Query | 删除结果 | 200/400/401/403/404 |
| `/api/v1/documents/upload` | POST | 上传文件并触发解析入库 | Multipart | 文档解析任务 | 200/400/401/403 |
| `/api/v1/documents/search` | POST | 语义搜索知识文档 | JSON Body | 检索结果 | 200/400/401/403 |
| `/api/v1/embeddings` | POST | 生成文本 Embedding | JSON Body | Embedding 结果 | 200/400/401/403/503 |
| `/api/v1/data-sources/test-connection` | POST | 测试数据源连接 | JSON Body | 测试结果 | 200/400/401/403 |
| `/api/v1/data-sources` | POST | 保存数据源配置 | JSON Body | 数据源连接 | 200/400/401/403 |
| `/api/v1/data-sources/presets` | GET | 查询 ERP/OA/财务数据源模板预设 | Header | 模板预设列表 | 200/401/403 |
| `/api/v1/data-sources/credentials/reencrypt` | POST | 维护重加密数据源凭证 | JSON Body | 重加密结果 | 200/400/401/403 |
| `/api/v1/data-sources/profile-drift` | GET | 审计历史 API profile 配置漂移 | Query | 漂移审计结果 | 200/400/401/403 |
| `/api/v1/rules` | GET | 查询规则列表 | Query | 分页规则列表 | 200/401/403 |
| `/api/v1/rules` | POST | 创建规则草稿 | JSON Body | 规则 | 200/400/401/403 |
| `/api/v1/rules/{ruleId}` | PUT | 保存规则节点、连线和参数 | Path + JSON Body | 规则 | 200/400/401/403/404 |
| `/api/v1/rules/{ruleId}/debug-runs` | POST | 执行规则调试或单步执行 | Path + JSON Body | 调试记录 | 200/400/401/403/404 |
| `/api/v1/users` | GET | 查询用户列表 | Query | 分页用户列表 | 200/401/403 |
| `/api/v1/users` | POST | 添加用户 | JSON Body | 用户 | 200/400/401/403 |
| `/api/v1/users/{userId}/status` | PUT | 启用或禁用用户 | Path + JSON Body | 用户状态 | 200/400/401/403/404 |
| `/api/v1/roles/permission-matrix` | GET | 查看 RBAC 权限矩阵 | Query | 权限矩阵 | 200/401/403 |
| `/api/v1/reports/{reportId}/share-links` | POST | 生成报告分享链接 | Path + JSON Body | 分享链接 | 200/400/401/403/404 |
| `/api/v1/share-links/{shareToken}/access` | POST | 外部用户访问分享链接 | Path + JSON Body | 分享访问结果 | 200/400/403/404 |
| `/api/v1/reports/{reportId}/annotations` | POST | 添加批注并可指派任务 | Path + JSON Body | 批注 | 200/400/401/403/404 |
| `/api/v1/tasks/{taskId}/status` | PUT | 更新批注任务状态 | Path + JSON Body | 任务 | 200/400/401/403/404 |
| `/api/v1/history` | GET | 查看个人历史记录 | Query | 分页历史记录 | 200/401 |
| `/api/v1/audit-logs` | GET | 管理员查询操作审计日志 | Query | 分页审计日志 | 200/401/403 |
| `/api/v1/audit-logs/model-invocations/{invocationId}` | GET | 查询模型调用审计详情 | Path | 模型调用记录 | 200/401/403/404 |
| `/api/v1/dashboard/overview` | GET | 查询工作台指标总览 | Query | 工作台指标 | 200/401/403 |

## 6. API JSON 契约清单

> 评审规则：本节是接口请求/响应的主契约；后续分组表格仅作阅读辅助。每个对象均包含 method、path、contentType、pathParams、queryParams、headers、requestBody、responseBody、statusCodes。
```json
[
    {
        "method": "POST",
        "path": "/api/v1/reports/generation-tasks",
        "description": "创建自然语言报告生成任务",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "topic": {
                "type": "string",
                "required": true,
                "description": "报告主题或自然语言需求"
            },
            "timeRange": {
                "type": "object",
                "required": false,
                "description": "报告时间范围"
            },
            "dimensions": {
                "type": "array",
                "required": false,
                "description": "分析维度"
            },
            "focusPoints": {
                "type": "array",
                "required": false,
                "description": "重点关注"
            },
            "style": {
                "type": "string",
                "required": false,
                "description": "报告风格"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "created natural-language report generation task",
                "properties": {
                    "taskId": {
                        "type": "integer",
                        "required": true,
                        "description": "report generation task id"
                    },
                    "reportId": {
                        "type": "integer",
                        "required": false,
                        "description": "bound report id; may be null before outline confirmation"
                    },
                    "status": {
                        "type": "string",
                        "required": true,
                        "description": "task status"
                    },
                    "currentStage": {
                        "type": "string",
                        "required": false,
                        "description": "current generation stage"
                    },
                    "progress": {
                        "type": "integer",
                        "required": true,
                        "description": "generation progress from 0 to 100"
                    },
                    "traceId": {
                        "type": "string",
                        "required": true,
                        "description": "generation trace id"
                    },
                    "outline": {
                        "type": "object",
                        "required": false,
                        "description": "outline or confirmed outline payload"
                    },
                    "failureReason": {
                        "type": "string",
                        "required": false,
                        "description": "failure reason when task failed or is retryable"
                    },
                    "createdAt": {
                        "type": "string",
                        "required": true,
                        "description": "task creation timestamp in ISO-8601 format"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            503
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/reports/template-generation-tasks",
        "description": "创建模板填报报告生成任务",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "templateId": {
                "type": "string",
                "required": true,
                "description": "报告模板 ID"
            },
            "period": {
                "type": "string",
                "required": true,
                "description": "报告周期"
            },
            "targetScope": {
                "type": "string",
                "required": true,
                "description": "对象范围"
            },
            "focusPoints": {
                "type": "array",
                "required": false,
                "description": "关注重点"
            },
            "style": {
                "type": "string",
                "required": false,
                "description": "报告风格"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "created template report generation task",
                "properties": {
                    "taskId": {
                        "type": "integer",
                        "required": true,
                        "description": "report generation task id"
                    },
                    "reportId": {
                        "type": "integer",
                        "required": false,
                        "description": "bound report id; may be null before outline confirmation"
                    },
                    "status": {
                        "type": "string",
                        "required": true,
                        "description": "task status"
                    },
                    "currentStage": {
                        "type": "string",
                        "required": false,
                        "description": "current generation stage"
                    },
                    "progress": {
                        "type": "integer",
                        "required": true,
                        "description": "generation progress from 0 to 100"
                    },
                    "traceId": {
                        "type": "string",
                        "required": true,
                        "description": "generation trace id"
                    },
                    "outline": {
                        "type": "object",
                        "required": false,
                        "description": "outline or confirmed outline payload"
                    },
                    "failureReason": {
                        "type": "string",
                        "required": false,
                        "description": "failure reason when task failed or is retryable"
                    },
                    "createdAt": {
                        "type": "string",
                        "required": true,
                        "description": "task creation timestamp in ISO-8601 format"
                    },
                    "templateSnapshot": {
                        "type": "object",
                        "required": true,
                        "description": "validated template parameter snapshot"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            503
        ]
    },
    {
        "method": "PUT",
        "path": "/api/v1/reports/generation-tasks/{taskId}/outline",
        "description": "确认或修改报告大纲",
        "contentType": "application/json",
        "pathParams": {
            "taskId": {
                "type": "string",
                "required": true,
                "description": "生成任务 ID"
            }
        },
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "outline": {
                "type": "object",
                "required": true,
                "description": "用户确认或修改后的大纲"
            },
            "confirmed": {
                "type": "boolean",
                "required": true,
                "description": "是否确认进入正文生成"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "confirmed outline result",
                "properties": {
                    "taskId": {
                        "type": "integer",
                        "required": true,
                        "description": "report generation task id"
                    },
                    "reportId": {
                        "type": "integer",
                        "required": false,
                        "description": "bound report id; may be null before outline confirmation"
                    },
                    "status": {
                        "type": "string",
                        "required": true,
                        "description": "task status"
                    },
                    "currentStage": {
                        "type": "string",
                        "required": false,
                        "description": "current generation stage"
                    },
                    "progress": {
                        "type": "integer",
                        "required": true,
                        "description": "generation progress from 0 to 100"
                    },
                    "traceId": {
                        "type": "string",
                        "required": true,
                        "description": "generation trace id"
                    },
                    "outline": {
                        "type": "object",
                        "required": false,
                        "description": "outline or confirmed outline payload"
                    },
                    "failureReason": {
                        "type": "string",
                        "required": false,
                        "description": "failure reason when task failed or is retryable"
                    },
                    "createdAt": {
                        "type": "string",
                        "required": true,
                        "description": "task creation timestamp in ISO-8601 format"
                    },
                    "confirmed": {
                        "type": "boolean",
                        "required": true,
                        "description": "whether outline was confirmed"
                    },
                    "nextStage": {
                        "type": "string",
                        "required": true,
                        "description": "next generation stage after outline confirmation"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/reports/generation-tasks/{taskId}/stream",
        "description": "订阅报告生成 SSE 流",
        "contentType": "text/event-stream",
        "pathParams": {
            "taskId": {
                "type": "string",
                "required": true,
                "description": "生成任务 ID"
            }
        },
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            },
            "Last-Event-ID": {
                "type": "string",
                "required": false,
                "description": "断线重连事件 ID"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "eventStream": "text/event-stream",
            "eventBody": {
                "type": {
                    "type": "string",
                    "required": true,
                    "description": "事件类型 stage/delta/references/error/done"
                },
                "content": {
                    "type": "string",
                    "required": false,
                    "description": "文本片段"
                },
                "stage": {
                    "type": "string",
                    "required": false,
                    "description": "生成阶段"
                },
                "references": {
                    "type": "array",
                    "required": false,
                    "description": "引用来源列表"
                },
                "reportId": {
                    "type": "string",
                    "required": false,
                    "description": "完成后的报告 ID"
                }
            }
        },
        "statusCodes": [
            200,
            401,
            403,
            404,
            503
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/chat",
        "description": "AI 对话式报告生成入口",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "message": {
                "type": "string",
                "required": true,
                "description": "用户对话输入或报告需求"
            },
            "conversationId": {
                "type": "string",
                "required": false,
                "description": "对话 ID"
            },
            "mode": {
                "type": "string",
                "required": false,
                "description": "report 或 chat"
            },
            "stream": {
                "type": "boolean",
                "required": false,
                "description": "是否 SSE 流式返回"
            },
            "context": {
                "type": "object",
                "required": false,
                "description": "上下文"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "type": {
                        "type": "string",
                        "required": true,
                        "description": "响应类型或 SSE 事件类型"
                    },
                    "conversationId": {
                        "type": "string",
                        "required": false,
                        "description": "对话 ID"
                    },
                    "taskId": {
                        "type": "string",
                        "required": false,
                        "description": "报告生成任务 ID"
                    },
                    "content": {
                        "type": "string",
                        "required": false,
                        "description": "AI 回复或文本片段"
                    },
                    "references": {
                        "type": "array",
                        "required": false,
                        "description": "引用来源"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            503
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/reports",
        "description": "查询我的报告或授权报告列表",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,total,page,pageSize"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/reports/{reportId}",
        "description": "查看报告详情",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "reportId,title,content,references,currentVersionId"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/reports/{reportId}/references/{referenceId}",
        "description": "查看引用来源与评分",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "referenceId,sourceTitle,sourceType,snapshot,score,anchor"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403,
            404
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/reports/{reportId}/exports",
        "description": "创建报告导出任务",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:reportId body:format,templateId,includeToc,brandProfileId"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "exportId,fileName,format,downloadUrl,createdAt"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/reports/{reportId}/versions",
        "description": "查看报告版本列表",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,currentVersionId"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403,
            404
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/reports/{reportId}/versions/{versionId}/rollback",
        "description": "回滚报告版本",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "reportId,newVersionId,sourceVersionId"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/knowledge-bases",
        "description": "查询知识库列表",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/knowledge-bases",
        "description": "新建知识库",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:name,type,description"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "knowledgeBaseId,name,status"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/knowledge-items",
        "description": "搜索筛选知识条目",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,total,page,pageSize"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/knowledge-items",
        "description": "手动录入知识条目",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:title,knowledgeBaseId,contentType,tags,content"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "itemId,indexStatus,createdAt"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "DELETE",
        "path": "/api/v1/knowledge-items/{itemId}",
        "description": "删除知识条目",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "deleted,impact"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/documents/upload",
        "description": "上传文件并触发解析入库",
        "contentType": "multipart/form-data",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:file,knowledgeBaseId,tags,enableOcr,enableTableRecognition"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "documentId,parseTaskId,parseStatus"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/documents/search",
        "description": "语义搜索知识文档",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:query,knowledgeBaseIds,topK"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,retrievalId"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/embeddings",
        "description": "生成文本 Embedding",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:texts,model"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "embeddings,model,dimension"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            503
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/data-sources/test-connection",
        "description": "测试数据源连接",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:sourceType,connectionConfig,syncScope"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "success,message,checkedAt"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/data-sources",
        "description": "保存数据源配置",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:name,sourceType,connectionConfig,syncPolicy,targetKnowledgeBaseId"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "dataSourceId,status,lastTestResult"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/data-sources/presets",
        "description": "查询 ERP/OA/财务数据源模板预设",
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
        "requestBody": {},
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "array",
                "required": true,
                "description": "ERP/OA/财务数据源模板预设列表",
                "items": {
                    "type": "object",
                    "properties": {
                        "summary": {
                            "type": "object",
                            "required": true,
                            "description": "presetId,displayName,category,sourceType,endpoint,username,syncQuery,fieldMapping,cursorColumn,scheduleEnabled,scheduleIntervalSeconds,maxRetryCount"
                        }
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/rules",
        "description": "查询规则列表",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,total"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/rules",
        "description": "创建规则草稿",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:name,source,templateId"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "ruleId,status,versionId"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "PUT",
        "path": "/api/v1/rules/{ruleId}",
        "description": "保存规则节点、连线和参数",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:ruleId body:nodes,edges,validateOnly"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "ruleId,valid,validationErrors,versionId"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/rules/{ruleId}/debug-runs",
        "description": "执行规则调试或单步执行",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:ruleId body:mode,startNodeId,input"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "debugRunId,status,nodeLogs,errors"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/users",
        "description": "查询用户列表",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,total"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/users",
        "description": "添加用户",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "body:username,displayName,roleIds,department"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "userId,status"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403
        ]
    },
    {
        "method": "PUT",
        "path": "/api/v1/users/{userId}/status",
        "description": "启用或禁用用户",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:userId body:status,reason"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "userId,status"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/roles/permission-matrix",
        "description": "查看 RBAC 权限矩阵",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "roles,permissions,matrix"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/reports/{reportId}/share-links",
        "description": "生成报告分享链接",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:reportId body:scope,targetIds,password,expiresAt"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "shareLinkId,shareUrl,expiresAt,accessPolicy"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/share-links/{shareToken}/access",
        "description": "外部用户访问分享链接",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": false,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:shareToken body:password,visitorName"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "accessGranted,report,deniedReason"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            403,
            404
        ]
    },
    {
        "method": "POST",
        "path": "/api/v1/reports/{reportId}/annotations",
        "description": "添加批注并可指派任务",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:reportId body:content,assigneeUserId,anchor.sectionId,anchor.startOffset,anchor.endOffset,anchor.selectedText,dueDate"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "annotationId,taskId,status,notificationId,notificationRecipientUserId,notificationType"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "PUT",
        "path": "/api/v1/tasks/{taskId}/status",
        "description": "更新批注任务状态",
        "contentType": "application/json",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "summary": {
                "type": "object",
                "required": false,
                "description": "path:taskId body:status,comment"
            }
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "taskId,status,updatedAt"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            400,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/history",
        "description": "查看个人历史记录",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,total"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/audit-logs",
        "description": "管理员查询操作审计日志",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "items,total"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/audit-logs/model-invocations/{invocationId}",
        "description": "查询模型调用审计详情",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "invocationId,model,promptSnapshot,contextSegments,parameters,response,durationMs,result"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403,
            404
        ]
    },
    {
        "method": "GET",
        "path": "/api/v1/dashboard/overview",
        "description": "查询工作台指标总览",
        "contentType": "none",
        "pathParams": {},
        "queryParams": {},
        "headers": {
            "Authorization": {
                "type": "string",
                "required": true,
                "description": "访问令牌，外部分享访问接口可为空"
            }
        },
        "requestBody": {
            "type": "none",
            "required": false,
            "description": "no request body"
        },
        "responseBody": {
            "code": {
                "type": "integer",
                "required": true,
                "description": "响应码"
            },
            "message": {
                "type": "string",
                "required": true,
                "description": "响应消息"
            },
            "data": {
                "type": "object",
                "required": true,
                "description": "业务响应数据",
                "properties": {
                    "summary": {
                        "type": "object",
                        "required": true,
                        "description": "cards,reportTrend,knowledgeRank,recentActivities"
                    }
                }
            },
            "timestamp": {
                "type": "string",
                "required": true,
                "description": "响应时间"
            }
        },
        "statusCodes": [
            200,
            401,
            403
        ]
    }
]
```
## 6. 报告生成 API

### 6.1 创建自然语言报告生成任务

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/generation-tasks` | POST | 创建自然语言报告生成任务 | JSON Body | 生成任务 | 200/400/401/403/503 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `topic` | string | 是 | 报告主题或用户自然语言需求 |
| `timeRange` | object | 否 | 报告时间范围，包含 `startDate`、`endDate` 或自然语言周期 |
| `dimensions` | string[] | 否 | 分析维度 |
| `focusPoints` | string[] | 否 | 重点关注内容 |
| `style` | string | 否 | 报告风格，如标准专业、高管摘要、详细分析 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `taskId` | string | 生成任务 ID |
| `status` | string | 任务状态：`outline_pending`、`generating`、`failed`、`completed` |
| `outline` | object | AI 初步生成的大纲 |
| `createdAt` | string | 创建时间 |

### 6.2 创建模板填报报告生成任务

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/template-generation-tasks` | POST | 创建模板填报报告生成任务 | JSON Body | 生成任务 | 200/400/401/403/503 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `templateId` | string | 是 | 报告模板 ID |
| `period` | string | 是 | 报告周期 |
| `targetScope` | string | 是 | 对象范围，如华东区、全公司、产品线 |
| `focusPoints` | string[] | 否 | 关注重点 |
| `style` | string | 否 | 报告风格 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `taskId` | string | 生成任务 ID |
| `templateSnapshot` | object | 模板参数快照 |
| `outline` | object | 待确认大纲 |
| `status` | string | 任务状态 |

### 6.3 确认或修改报告大纲

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/generation-tasks/{taskId}/outline` | PUT | 确认或修改报告大纲 | Path + JSON Body | 大纲确认结果 | 200/400/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `taskId` | string | 是 | 生成任务 ID |
| `outline` | object | 是 | 用户确认或修改后的大纲 |
| `confirmed` | boolean | 是 | 是否确认进入正文生成 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `taskId` | string | 生成任务 ID |
| `status` | string | 更新后的任务状态 |
| `nextStage` | string | 下一阶段，如 `retrieving` |

### 6.4 订阅报告生成 SSE 流

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/generation-tasks/{taskId}/stream` | GET | 订阅报告生成 SSE 流 | Path | SSE 事件流 | 200/401/403/404/503 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `taskId` | string | 是 | 生成任务 ID |
| `Last-Event-ID` | header string | 否 | 断线重连事件 ID |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `type` | string | SSE 事件类型：`stage`、`delta`、`references`、`error`、`done` |
| `content` | string | 流式生成文本片段 |
| `stage` | string | 当前生成阶段 |
| `references` | array | 引用来源列表 |
| `reportId` | string | 完成后生成的报告 ID |

### 6.5 AI 对话式报告生成入口

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/chat` | POST | 对话式提交报告需求，支持直接返回 SSE 流或创建生成任务 | JSON Body | SSE 事件流或生成任务 | 200/400/401/403/503 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `message` | string | 是 | 用户对话输入或报告需求 |
| `conversationId` | string | 否 | 对话 ID，用于连续对话 |
| `mode` | string | 否 | `report` 或 `chat`，当前优先用于报告生成 |
| `stream` | boolean | 否 | 是否使用 SSE 流式返回 |
| `context` | object | 否 | 可选上下文，如报告类型、数据范围、分析维度 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `type` | string | 非流式时为响应类型；流式时为 SSE 事件类型 |
| `conversationId` | string | 对话 ID |
| `taskId` | string | 关联的报告生成任务 ID |
| `content` | string | AI 回复或流式文本片段 |
| `references` | array | 引用来源列表 |

## 7. 报告查看、引用、导出与版本 API

### 7.1 查询报告列表

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports` | GET | 查询我的报告或授权报告列表 | Query | 分页报告列表 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `pageSize` | integer | 否 | 每页数量 |
| `keyword` | string | 否 | 报告名称关键词 |
| `status` | string | 否 | 报告状态 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 报告摘要列表 |
| `total` | integer | 总数 |
| `page` | integer | 当前页码 |
| `pageSize` | integer | 每页数量 |

### 7.2 查看报告详情

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/{reportId}` | GET | 查看报告正文、引用和当前版本 | Path | 报告详情 | 200/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `reportId` | string | 是 | 报告 ID |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `reportId` | string | 报告 ID |
| `title` | string | 报告标题 |
| `content` | string | 报告正文 |
| `references` | array | 引用来源摘要 |
| `currentVersionId` | string | 当前版本 ID |

### 7.3 查看引用来源与评分

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/{reportId}/references/{referenceId}` | GET | 查看引用来源、快照和质量评估 | Path | 引用来源详情 | 200/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `reportId` | string | 是 | 报告 ID |
| `referenceId` | string | 是 | 引用来源 ID |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `referenceId` | string | 引用来源 ID |
| `sourceTitle` | string | 来源名称 |
| `sourceType` | string | 来源类型 |
| `snapshot` | string | 来源快照 |
| `score` | object | 证据可信度评分 |
| `anchor` | object | 正文与来源定位信息 |

### 7.4 创建报告导出任务

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/{reportId}/exports` | POST | 按企业规范导出报告 | Path + JSON Body | 导出文件 | 200/400/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `reportId` | string | 是 | 报告 ID |
| `format` | string | 是 | `PDF`、`Word`、`PPT`、`Markdown` |
| `templateId` | string | 否 | 企业模板 ID |
| `includeToc` | boolean | 否 | 是否包含目录 |
| `brandProfileId` | string | 否 | 企业品牌规范 ID |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `exportId` | string | 导出文件 ID |
| `fileName` | string | 文件名 |
| `format` | string | 导出格式 |
| `downloadUrl` | string | 下载地址 |
| `createdAt` | string | 创建时间 |

### 7.5 查看报告版本列表

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/{reportId}/versions` | GET | 查看报告版本历史 | Path | 版本列表 | 200/401/403/404 |

请求参数：

```json
{
  "path": {
    "reportId": "42"
  }
}
```

响应数据：

```json
[
  {
    "versionId": 9003,
    "reportId": 42,
    "versionNo": 3,
    "createdBy": 1001,
    "changeReason": "rollback",
    "current": true,
    "createdAt": "2026-06-23T20:24:45+08:00"
  }
]
```

### 7.6 回滚报告版本

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/{reportId}/versions/{versionId}/rollback` | POST | 回滚到指定历史版本 | Path | 新版本 | 200/401/403/404 |

请求参数：

```json
{
  "path": {
    "reportId": "42",
    "versionId": "9001"
  }
}
```

响应数据：

```json
{
  "reportId": 42,
  "sourceVersionId": 9001,
  "newVersionId": 9003,
  "currentVersionId": 9003,
  "changeReason": "rollback"
}
```

## 8. 知识库、文档与数据源 API

### 8.1 查询知识库列表

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/knowledge-bases` | GET | 查询知识库列表 | Query | 知识库列表 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `keyword` | string | 否 | 知识库名称关键词 |
| `type` | string | 否 | 知识库类型 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 知识库摘要列表 |

### 8.2 新建知识库

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/knowledge-bases` | POST | 新建自定义知识库 | JSON Body | 知识库 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `name` | string | 是 | 知识库名称 |
| `type` | string | 是 | 知识库类型 |
| `description` | string | 否 | 描述 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `knowledgeBaseId` | string | 知识库 ID |
| `name` | string | 知识库名称 |
| `status` | string | 状态 |

### 8.3 搜索筛选知识条目

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/knowledge-items` | GET | 搜索、筛选和分页查询知识条目 | Query | 分页知识条目 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `knowledgeBaseId` | string | 否 | 知识库 ID |
| `keyword` | string | 否 | 搜索关键词 |
| `type` | string | 否 | 条目类型 |
| `page` | integer | 否 | 页码 |
| `pageSize` | integer | 否 | 每页数量 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 知识条目摘要 |
| `total` | integer | 总数 |
| `page` | integer | 当前页 |
| `pageSize` | integer | 每页数量 |

### 8.4 手动录入知识条目

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/knowledge-items` | POST | 手动录入知识条目 | JSON Body | 知识条目 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `title` | string | 是 | 条目标题 |
| `knowledgeBaseId` | string | 是 | 所属知识库 |
| `contentType` | string | 是 | 内容类型 |
| `tags` | string[] | 否 | 标签 |
| `content` | string | 是 | 条目内容 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `itemId` | string | 知识条目 ID |
| `indexStatus` | string | 索引状态 |
| `createdAt` | string | 创建时间 |

### 8.5 删除知识条目

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/knowledge-items/{itemId}` | DELETE | 删除知识条目，支持影响确认 | Path + Query | 删除结果 | 200/400/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `itemId` | string | 是 | 知识条目 ID |
| `confirmImpact` | boolean | 否 | 被引用时是否确认删除 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `deleted` | boolean | 是否已删除 |
| `impact` | object | 被引用影响信息 |

### 8.6 上传文件并触发解析入库

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/documents/upload` | POST | 上传文件并解析入库 | Multipart | 文档解析任务 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `file` | file | 是 | PDF、Excel、Word、CSV、TXT 或扫描件 |
| `knowledgeBaseId` | string | 是 | 目标知识库 |
| `tags` | string[] | 否 | 标签 |
| `enableOcr` | boolean | 否 | 是否启用 OCR |
| `enableTableRecognition` | boolean | 否 | 是否启用图片表格识别 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `documentId` | string | 文档 ID |
| `parseTaskId` | string | 解析任务 ID |
| `parseStatus` | string | 解析状态 |

### 8.7 语义搜索知识文档

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/documents/search` | POST | 语义搜索知识文档 | JSON Body | 检索结果 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `query` | string | 是 | 检索问题 |
| `knowledgeBaseIds` | string[] | 否 | 限定知识库 |
| `topK` | integer | 否 | 返回条数 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 检索结果 |
| `retrievalId` | string | 检索会话 ID |

### 8.8 生成文本 Embedding

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/embeddings` | POST | 生成文本向量 | JSON Body | Embedding 结果 | 200/400/401/403/503 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `texts` | string[] | 是 | 待向量化文本 |
| `model` | string | 否 | Embedding 模型标识 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `embeddings` | array | 向量结果 |
| `model` | string | 使用的模型 |
| `dimension` | integer | 向量维度 |

### 8.9 测试数据源连接

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/data-sources/test-connection` | POST | 测试企业数据源连接 | JSON Body | 测试结果 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `sourceType` | string | 是 | 数据源类型：数据库、ERP、OA、财务系统、API |
| `endpoint` | string | 是 | 已保存数据源的连接端点必须命中 `DATA_SOURCE_ENDPOINT_ALLOWLIST`；不允许时测试连接返回失败且不得出站访问 |
| `connectionConfig` | object | 是 | 连接配置，响应和审计中不得明文返回密钥 |
| `syncScope` | object | 否 | 同步范围 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `success` | boolean | 是否连接成功 |
| `message` | string | 测试结果说明 |
| `checkedAt` | string | 测试时间 |

### 8.10 保存数据源配置

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/data-sources` | POST | 保存数据源配置和同步策略 | JSON Body | 数据源连接 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `name` | string | 是 | 数据源名称 |
| `sourceType` | string | 是 | 数据源类型 |
| `endpoint` | string | 是 | API/JDBC 连接端点；HTTP、PostgreSQL JDBC、MySQL JDBC 主机必须命中 `DATA_SOURCE_ENDPOINT_ALLOWLIST` |
| `connectionConfig` | object | 是 | 连接配置 |
| `syncPolicy` | object | 否 | 同步策略 |
| `targetKnowledgeBaseId` | string | 是 | 目标知识库 |
| `fieldMapping.profileId` | string | 否 | API 数据源 schema profile；当前支持 `oa-documents`、`finance-vouchers`，设置后保存阶段会校验对应 rows/title/content/cursor/auth/header 和 `cursorColumn` 规则 |
| `fieldMapping.rowsPath` | string | 条件必填 | 当 `sourceType=api` 且配置目标知识库时必填，指向响应中的列表节点 |
| `fieldMapping.titleField` | string | 条件必填 | 当 `sourceType=api` 且配置目标知识库时必填，映射知识条目标题字段 |
| `fieldMapping.contentField` | string | 条件必填 | 当 `sourceType=api` 且配置目标知识库时必填，映射知识条目正文/内容字段 |
| `fieldMapping.cursorField` | string | 否 | API 响应行内增量游标字段；内置 profile 会校验固定值 |
| `fieldMapping.method` | string | 否 | API 请求方法，仅允许 `GET` 或 `POST` |
| `fieldMapping.authType` | string | 否 | 认证方式，仅允许 `bearer`、`api_key`、`basic`、`none` |
| `fieldMapping.apiKeyHeader` | string | 否 | API Key Header 名称；禁止覆盖保留 Header |
| `fieldMapping.maxPages` | integer | 否 | 分页最大页数，必须为 1-100 |
| `fieldMapping.pageStart` | integer | 否 | 分页起始页码，必须大于等于 0 |
| `fieldMapping.pageSize` | integer | 否 | 分页大小，必须为 1-1000 |
| `fieldMapping.headers` | object | 否 | 自定义 Header；禁止覆盖 `Authorization`、`Content-Length`、`Host` |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `dataSourceId` | string | 数据源 ID |
| `status` | string | 数据源状态 |
| `lastTestResult` | object | 最近测试结果 |

安全约束：

- `POST /api/v1/data-sources` 会在保存前校验 endpoint allowlist，未授权主机返回 `403`。
- API 数据源会在保存前校验高级 `fieldMapping`，非法 method/authType/page/header 配置返回 `400`。
- API 数据源设置 `fieldMapping.profileId` 后只允许内置 profile；`oa-documents` 固定 `rowsPath=data.documents/titleField=documentNo/contentField=content/cursorField=id/cursorColumn=id/method=GET/authType=bearer`，`finance-vouchers` 固定 `rowsPath=data.vouchers/titleField=voucherNo/contentField=summary/cursorField=voucherId/cursorColumn=voucherId/method=POST/authType=api_key/apiKeyHeader=X-API-Key/headers.X-Tenant=finance`。
- `POST /api/v1/data-sources/{dataSourceId}/sync-runs` 会在同步执行前复核已保存 endpoint 和 API `fieldMapping`/profile/cursor 配置；即使请求体提供 `sampleRows`，也不能绕过 allowlist 或历史配置漂移校验。
- `application-dev.yml` 默认仅放行本地 loopback 和 Docker 开发服务名；`application-prod.yml` 必须通过 `DATA_SOURCE_ENDPOINT_ALLOWLIST` 显式声明生产可访问源。

### 8.11 查询数据源模板预设

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/data-sources/presets` | GET | 查询 ERP/OA/财务数据源模板预设 | Header | 模板预设列表 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `Authorization` | string | 是 | 登录用户访问令牌 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `presetId` | string | 模板 ID，例如 `erp-postgresql`、`oa-api`、`finance-api` |
| `displayName` | string | 模板展示名称 |
| `category` | string | 业务系统分类：ERP、OA、finance |
| `sourceType` | string | 实际保存时使用的数据源类型，例如 `postgresql` 或 `api` |
| `endpoint` | string | 示例连接地址，保存前可按企业环境调整 |
| `username` | string | 示例账号标识，不包含密钥 |
| `syncQuery` | string | 数据库类同步 SQL 示例 |
| `fieldMapping` | object | API 类 profile、行路径、标题、正文、游标、分页和认证映射 |
| `cursorColumn` | string | 增量游标字段；内置 API profile 会要求它与 profile 的 `fieldMapping.cursorField` 一致 |
| `scheduleEnabled` | boolean | 默认是否启用定时同步 |
| `scheduleIntervalSeconds` | integer | 推荐同步间隔 |
| `maxRetryCount` | integer | 推荐最大失败重试次数 |

### 8.12 维护重加密数据源凭证

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/data-sources/credentials/reencrypt` | POST | 扫描并将旧密钥或旧格式数据源凭证重加密为当前活动密钥 | JSON Body | 重加密结果 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `limit` | integer | 否 | 单次扫描上限，默认 100，后端最小钳制为 1 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `scannedCount` | integer | 本次扫描到的带凭证数据源数量 |
| `migratedCount` | integer | 本次完成重加密的数据源凭证数量 |

### 8.13 审计历史 API profile 配置漂移

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/data-sources/profile-drift` | GET | 扫描已保存 API 数据源，找出与当前内置 profile 规则不一致的历史配置 | Query | 漂移审计结果 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `limit` | integer | 否 | 单次扫描上限，默认 100，后端最小钳制为 1 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `scannedCount` | integer | 本次扫描的 API profile 数据源数量 |
| `driftCount` | integer | 发现的漂移数据源数量 |
| `items` | array | 漂移明细，不包含任何凭证明文或密文 |
| `items[].dataSourceId` | integer | 数据源 ID |
| `items[].name` | string | 数据源名称 |
| `items[].sourceType` | string | 数据源类型，当前为 `api` |
| `items[].profileId` | string | 当前映射声明的内置 profile ID |
| `items[].cursorColumn` | string | 已保存增量游标字段 |
| `items[].failureReason` | string | 与当前 profile 规则不一致的具体原因 |

## 9. 规则引擎 API

### 9.1 查询规则列表

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/rules` | GET | 查询规则列表 | Query | 分页规则列表 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `page` | integer | 否 | 页码 |
| `pageSize` | integer | 否 | 每页数量 |
| `keyword` | string | 否 | 规则名称关键词 |
| `status` | string | 否 | 规则状态 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 规则摘要列表 |
| `total` | integer | 总数 |

### 9.2 创建规则草稿

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/rules` | POST | 创建规则草稿 | JSON Body | 规则 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `name` | string | 是 | 规则名称 |
| `source` | string | 否 | 创建来源：空白、模板、导入 |
| `templateId` | string | 否 | 模板 ID |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `ruleId` | string | 规则 ID |
| `status` | string | 规则状态 |
| `versionId` | string | 规则版本 ID |

### 9.3 保存规则节点、连线和参数

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/rules/{ruleId}` | PUT | 保存规则节点、连线和参数 | Path + JSON Body | 规则 | 200/400/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `ruleId` | string | 是 | 规则 ID |
| `nodes` | array | 是 | 节点列表，包含节点类型、标题、位置、参数、输入输出端口 |
| `edges` | array | 是 | 连线列表，包含起点、终点、条件 |
| `validateOnly` | boolean | 否 | 是否仅校验不保存 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `ruleId` | string | 规则 ID |
| `valid` | boolean | 是否校验通过 |
| `validationErrors` | array | 校验错误列表 |
| `versionId` | string | 新规则版本 ID |

### 9.4 执行规则调试或单步执行

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/rules/{ruleId}/debug-runs` | POST | 执行调试或单步执行 | Path + JSON Body | 调试记录 | 200/400/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `ruleId` | string | 是 | 规则 ID |
| `mode` | string | 是 | `full` 或 `step` |
| `startNodeId` | string | 否 | 单步执行起始节点 |
| `input` | object | 是 | 测试输入 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `debugRunId` | string | 调试执行 ID |
| `status` | string | 执行状态 |
| `nodeLogs` | array | 节点执行日志 |
| `errors` | array | 错误列表 |

## 10. 权限、分享与协作 API

### 10.1 查询用户列表

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/users` | GET | 查询用户列表 | Query | 分页用户列表 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `page` | integer | 否 | 页码 |
| `pageSize` | integer | 否 | 每页数量 |
| `keyword` | string | 否 | 用户关键词 |
| `role` | string | 否 | 角色 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 用户摘要列表 |
| `total` | integer | 总数 |

### 10.2 添加用户

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/users` | POST | 添加用户并分配角色部门 | JSON Body | 用户 | 200/400/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `username` | string | 是 | 登录用户名 |
| `displayName` | string | 是 | 姓名 |
| `roleIds` | string[] | 是 | 角色 ID 列表 |
| `department` | string | 否 | 部门 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `userId` | string | 用户 ID |
| `status` | string | 用户状态 |

### 10.3 启用或禁用用户

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/users/{userId}/status` | PUT | 启用或禁用用户 | Path + JSON Body | 用户状态 | 200/400/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `userId` | string | 是 | 用户 ID |
| `status` | string | 是 | `enabled` 或 `disabled` |
| `reason` | string | 否 | 操作原因 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `userId` | string | 用户 ID |
| `status` | string | 更新后的用户状态 |

### 10.4 查看 RBAC 权限矩阵

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/roles/permission-matrix` | GET | 查看角色权限矩阵 | Query | 权限矩阵 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `roleIds` | string[] | 否 | 指定角色 ID |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `roles` | array | 角色列表 |
| `permissions` | array | 权限项列表 |
| `matrix` | array | 角色与权限项关系 |

### 10.5 生成报告分享链接

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/{reportId}/share-links` | POST | 生成内部或外部分享链接 | Path + JSON Body | 分享链接 | 200/400/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `reportId` | string | 是 | 报告 ID |
| `scope` | string | 是 | `users`、`departments`、`external` |
| `targetIds` | string[] | 否 | 内部用户或部门 ID |
| `password` | string | 否 | 外部访问密码 |
| `expiresAt` | string | 否 | 有效期 |
| `allowDownload` | boolean | 否 | 是否允许外部用户下载已生成的导出文件，默认 false |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `shareLinkId` | string | 分享链接 ID |
| `shareUrl` | string | 分享地址 |
| `expiresAt` | string | 有效期 |
| `accessPolicy` | object | 访问控制策略摘要 |

### 10.6 外部用户访问分享链接

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/share-links/{shareToken}/access` | POST | 外部用户访问分享链接 | Path + JSON Body | 分享访问结果 | 200/400/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `shareToken` | string | 是 | 分享令牌 |
| `password` | string | 否 | 访问密码 |
| `visitorName` | string | 否 | 外部访问者名称 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `accessGranted` | boolean | 是否允许访问 |
| `reportId` | string | 授权报告 ID |
| `shareToken` | string | 分享令牌 |
| `allowDownload` | boolean | 是否允许通过分享链接下载导出文件 |
| `deniedReason` | string | 拒绝原因 |

### 10.6.1 外部用户读取分享报告详情

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/share-links/{shareToken}/report` | POST | 外部用户读取授权报告只读详情 | Path + JSON Body | 报告只读详情 | 200/400/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `shareToken` | string | 是 | 分享令牌 |
| `password` | string | 否 | 访问密码 |
| `visitor` | string | 否 | 外部访问者标识 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `accessGranted` | boolean | 是否允许访问 |
| `shareToken` | string | 分享令牌 |
| `reportId` | string | 报告 ID |
| `title` | string | 报告标题 |
| `status` | string | 报告状态 |
| `currentVersionId` | string | 当前版本 ID |
| `sections` | array | 报告章节，只读内容 |
| `allowDownload` | boolean | 是否允许外部下载 |
| `exports` | array | 可下载导出文件摘要；仅在 `allowDownload=true` 时返回已完成文件 |

### 10.6.2 外部用户获取分享导出下载 URL

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url` | POST | 外部用户获取受控短期下载 URL | Path + JSON Body | 短期下载 URL | 200/400/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `shareToken` | string | 是 | 分享令牌 |
| `exportFileId` | string | 是 | 导出文件 ID |
| `password` | string | 否 | 访问密码 |
| `visitor` | string | 否 | 外部访问者标识 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `exportFileId` | string | 导出文件 ID |
| `reportId` | string | 报告 ID |
| `fileName` | string | 文件名 |
| `contentType` | string | 文件类型 |
| `sizeBytes` | number | 文件大小 |
| `downloadPolicy` | string | 固定为 `share_presigned_url` |
| `downloadUrl` | string | 短期下载 URL |
| `expiresAt` | string | 下载 URL 过期时间 |

### 10.7 添加批注并可指派任务

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/reports/{reportId}/annotations` | POST | 添加批注、评论并可指派任务 | Path + JSON Body | 批注 | 200/400/401/403/404 |

请求参数：

```json
{
  "path": {
    "reportId": "42"
  },
  "body": {
    "content": "请核对该处数据来源",
    "assigneeUserId": 1002,
    "anchor": {
      "sectionId": "summary",
      "startOffset": 18,
      "endOffset": 31,
      "selectedText": "revenue growth"
    },
    "dueDate": "2026-06-30"
  }
}
```

响应数据：

```json
{
  "annotationId": 501,
  "reportId": 42,
  "createdBy": 1001,
  "assigneeUserId": 1002,
  "content": "请核对该处数据来源",
  "anchor": {
    "sectionId": "summary",
    "startOffset": 18,
    "endOffset": 31,
    "selectedText": "revenue growth"
  },
  "status": "open",
  "taskId": 7001,
  "taskStatus": "open",
  "notificationId": 9001,
  "notificationRecipientUserId": 1002,
  "notificationType": "collaboration_task_assigned"
}
```

### 10.8 更新批注任务状态

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/tasks/{taskId}/status` | PUT | 更新任务处理状态 | Path + JSON Body | 任务 | 200/400/401/403/404 |

请求参数：

```json
{
  "path": {
    "taskId": "7001"
  },
  "body": {
    "status": "in_progress",
    "comment": "已开始核对"
  }
}
```

`status` 允许值：`open`、`in_progress`、`done`、`rejected`。

响应数据：

```json
{
  "taskId": 7001,
  "reportId": 42,
  "annotationId": 501,
  "assigneeUserId": 1002,
  "status": "in_progress",
  "notificationId": 9002,
  "notificationRecipientUserId": 1001,
  "notificationType": "collaboration_task_status_changed"
}
```

## 11. 审计、历史与工作台 API

### 11.1 查看个人历史记录

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/history` | GET | 查看当前用户个人历史记录 | Query | 分页历史记录 | 200/401 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `page` | integer | 否 | 页码 |
| `pageSize` | integer | 否 | 每页数量 |
| `operationType` | string | 否 | 操作类型 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 个人历史列表 |
| `total` | integer | 总数 |

### 11.2 管理员查询操作审计日志

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/audit-logs` | GET | 查询全局操作审计日志 | Query | 分页审计日志 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `page` | integer | 否 | 页码 |
| `pageSize` | integer | 否 | 每页数量 |
| `operationType` | string | 否 | 操作类型 |
| `keyword` | string | 否 | 搜索关键词 |
| `startTime` | string | 否 | 开始时间 |
| `endTime` | string | 否 | 结束时间 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `items` | array | 审计日志列表 |
| `total` | integer | 总数 |

### 11.3 查询模型调用审计详情

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/audit-logs/model-invocations/{invocationId}` | GET | 查询模型调用审计详情 | Path | 模型调用记录 | 200/401/403/404 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `invocationId` | string | 是 | 模型调用记录 ID |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `invocationId` | string | 模型调用记录 ID |
| `model` | string | 模型标识 |
| `promptSnapshot` | object | Prompt 快照 |
| `contextSegments` | array | 检索上下文 |
| `parameters` | object | 模型参数 |
| `response` | object | 模型响应 |
| `durationMs` | integer | 耗时 |
| `result` | string | 调用结果 |

### 11.4 查询工作台指标总览

| 接口路径 | 方法 | 描述 | 请求参数 | 响应数据 | 状态码 |
|----------|------|------|----------|----------|--------|
| `/api/v1/dashboard/overview` | GET | 查询工作台指标、趋势、排行和活动 | Query | 工作台指标 | 200/401/403 |

请求参数：

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| `range` | string | 否 | `today`、`last7days`、`last30days`、`custom` |
| `startDate` | string | 否 | 自定义开始日期 |
| `endDate` | string | 否 | 自定义结束日期 |

响应数据：

| 字段名 | 类型 | 描述 |
|--------|------|------|
| `cards` | object | 指标卡片 |
| `reportTrend` | array | 报告生成趋势 |
| `knowledgeRank` | array | 知识库引用排行 |
| `recentActivities` | array | 最近活动 |

## 12. 错误码定义

| 错误码 | 描述 | 处理建议 |
|--------|------|----------|
| 200 | 操作成功 | 无需处理 |
| 400 | 请求参数错误 | 检查请求参数 |
| 401 | 未授权 | 检查 Token |
| 403 | 无权限 | 检查角色和资源授权 |
| 404 | 资源不存在 | 检查资源 ID |
| 409 | 资源状态冲突 | 检查当前状态后重试 |
| 422 | 业务规则校验失败 | 根据提示修正业务输入 |
| 429 | 请求过于频繁 | 限流降级或稍后重试 |
| 500 | 服务器内部错误 | 联系管理员 |
| 503 | 服务不可用 | AI 服务繁忙，请稍后重试 |
| `OUTLINE_NOT_CONFIRMED` | 大纲未确认 | 先确认报告大纲 |
| `AI_MODEL_UNAVAILABLE` | AI 模型不可用 | 稍后重试或切换可用模型策略 |
| `RETRIEVAL_EMPTY` | 检索无结果 | 调整报告范围或补充知识库 |
| `EXPORT_TEMPLATE_MISSING` | 企业导出模板缺失 | 配置导出模板 |
| `KNOWLEDGE_ITEM_REFERENCED` | 知识条目已被引用 | 确认影响后再删除 |
| `RULE_VALIDATION_FAILED` | 规则校验失败 | 修复节点参数或连线 |
| `SHARE_LINK_EXPIRED` | 分享链接已过期 | 重新生成分享链接 |
| `SHARE_PASSWORD_INVALID` | 分享密码错误 | 检查访问密码 |
| `ASSIGNEE_NO_ACCESS` | 被指派人无访问权限 | 调整报告授权或处理人 |

## 13. OpenSpec 映射

| API 接口 | OpenSpec capability | Requirement | Scenario | 说明 |
|----------|---------------------|-------------|----------|------|
| `POST /api/v1/reports/generation-tasks` | `report-generation` | 智能生成报告 | 自然语言创建报告生成任务 | 创建自然语言生成任务 |
| `POST /api/v1/reports/template-generation-tasks` | `report-generation` | 模板填报生成报告 | 模板参数完整时生成报告、模板参数缺失时拒绝提交 | 创建模板生成任务 |
| `PUT /api/v1/reports/generation-tasks/{taskId}/outline` | `report-generation` | 大纲确认 | 用户确认大纲后开始正文生成 | 确认或修改大纲 |
| `GET /api/v1/reports/generation-tasks/{taskId}/stream` | `report-generation` | 三阶段流式生成、AI 模型调用与多模型路由预留 | 生成过程中查看阶段进度、线上 GPT 调用被记录 | SSE 流式生成 |
| `POST /api/v1/chat` | `report-generation` | 智能生成报告、AI 模型调用与多模型路由预留 | 自然语言创建报告生成任务、线上 GPT 调用被记录 | AI 对话式生成入口 |
| `GET /api/v1/reports` | `report-generation` | 智能生成报告 | 自然语言创建报告生成任务 | 我的报告与授权报告列表 |
| `GET /api/v1/reports/{reportId}` | `report-citation-export-version` | 溯源引用双向联动 | 点击正文引用查看来源 | 查看报告详情 |
| `GET /api/v1/reports/{reportId}/references/{referenceId}` | `report-citation-export-version` | 溯源引用双向联动、证据可信度与引用质量评估 | 查看引用质量 | 引用来源详情 |
| `POST /api/v1/reports/{reportId}/exports` | `report-citation-export-version` | 企业规范导出 | 导出 PDF 报告、企业模板缺失 | 导出报告 |
| `GET /api/v1/reports/{reportId}/versions` | `report-citation-export-version` | 报告版本管理 | 回滚报告版本 | 查看版本 |
| `POST /api/v1/reports/{reportId}/versions/{versionId}/rollback` | `report-citation-export-version` | 报告版本管理 | 回滚报告版本 | 版本回滚 |
| `GET /api/v1/knowledge-bases` | `knowledge-base-ingestion` | 知识库与知识条目管理 | 搜索筛选知识条目 | 知识库列表 |
| `POST /api/v1/knowledge-bases` | `knowledge-base-ingestion` | 知识库与知识条目管理 | 搜索筛选知识条目 | 新建知识库 |
| `GET /api/v1/knowledge-items` | `knowledge-base-ingestion` | 知识库与知识条目管理 | 搜索筛选知识条目 | 条目查询 |
| `POST /api/v1/knowledge-items` | `knowledge-base-ingestion` | 手动录入知识条目 | 手动录入成功 | 手动录入 |
| `DELETE /api/v1/knowledge-items/{itemId}` | `knowledge-base-ingestion` | 知识库与知识条目管理 | 删除被引用知识条目 | 删除条目 |
| `POST /api/v1/documents/upload` | `knowledge-base-ingestion` | 文件上传解析入库 | 扫描件解析成功、OCR 或表格识别失败 | 文档上传解析 |
| `POST /api/v1/documents/search` | `knowledge-base-ingestion` | 文件上传解析入库 | 扫描件解析成功 | 语义搜索 |
| `POST /api/v1/embeddings` | `knowledge-base-ingestion` | 文件上传解析入库 | 扫描件解析成功 | Embedding 生成 |
| `POST /api/v1/data-sources/test-connection` | `knowledge-base-ingestion` | 企业数据源对接 | 测试连接失败 | 测试连接 |
| `POST /api/v1/data-sources` | `knowledge-base-ingestion` | 企业数据源对接 | 数据源同步异常 | 保存配置 |
| `GET /api/v1/data-sources/presets` | `knowledge-base-ingestion` | 企业数据源对接 | ERP/OA/财务模板预设 | 查询模板预设 |
| `POST /api/v1/data-sources/credentials/reencrypt` | `knowledge-base-ingestion` | 企业数据源对接 | 凭证轮换维护 | 凭证重加密 |
| `GET /api/v1/rules` | `rule-engine` | 规则编排 | 创建规则 | 规则列表 |
| `POST /api/v1/rules` | `rule-engine` | 规则编排 | 创建规则 | 创建草稿 |
| `PUT /api/v1/rules/{ruleId}` | `rule-engine` | 节点配置与连线、规则节点契约 | 非法连线被拒绝、查看节点配置 | 保存规则 |
| `POST /api/v1/rules/{ruleId}/debug-runs` | `rule-engine` | 规则调试执行 | 单步执行节点、节点参数缺失 | 调试执行 |
| `GET /api/v1/users` | `permission-collaboration` | RBAC 用户与角色管理 | 查看权限矩阵 | 查询用户 |
| `POST /api/v1/users` | `permission-collaboration` | RBAC 用户与角色管理 | 查看权限矩阵 | 添加用户 |
| `PUT /api/v1/users/{userId}/status` | `permission-collaboration` | RBAC 用户与角色管理 | 禁用用户 | 启停账号 |
| `GET /api/v1/roles/permission-matrix` | `permission-collaboration` | RBAC 用户与角色管理 | 查看权限矩阵 | 权限矩阵 |
| `POST /api/v1/reports/{reportId}/share-links` | `permission-collaboration` | 报告分享链接 | 外部用户访问有效链接 | 生成分享 |
| `POST /api/v1/share-links/{shareToken}/access` | `permission-collaboration` | 报告分享链接 | 分享链接过期或密码错误 | 外部访问 |
| `POST /api/v1/reports/{reportId}/annotations` | `permission-collaboration` | 批注评论与任务指派 | 提交批注并指派任务、被指派人无访问权限 | 添加批注 |
| `PUT /api/v1/tasks/{taskId}/status` | `permission-collaboration` | 批注评论与任务指派 | 提交批注并指派任务 | 更新任务 |
| `GET /api/v1/history` | `audit-history-dashboard` | 个人历史记录 | 普通用户查看个人历史 | 个人历史 |
| `GET /api/v1/audit-logs` | `audit-history-dashboard` | 管理员操作审计 | 管理员筛选操作日志 | 审计日志 |
| `GET /api/v1/audit-logs/model-invocations/{invocationId}` | `audit-history-dashboard` | AI 生成过程审计 | 查询模型调用审计 | 模型审计 |
| `GET /api/v1/dashboard/overview` | `audit-history-dashboard` | 工作台指标总览 | 切换时间范围、普通角色访问工作台 | 工作台 |

### 13.1 后端实际端点覆盖补遗

> 依据当前 Java Controller 反向扫描生成，用于防止 API 契约与后端实现漂移。后续细化请求/响应 JSON 时，以本补遗清单作为覆盖基线。

| Endpoint | Module | Security intent | Purpose | Notes |
| --- | --- | --- | --- | --- |
| `POST /api/v1/knowledge-items/batch-import` | `knowledge-base-ingestion` | `knowledge:manage` | 批量导入知识条目 | 返回导入成功/失败明细 |
| `GET /api/v1/documents/{documentId}` | `knowledge-base-ingestion` | `knowledge:upload` | 查看文档解析状态与元数据 | 文档上传后的查询入口 |
| `POST /api/v1/data-sources/{dataSourceId}/sync-runs` | `knowledge-base-ingestion` | `datasource:manage` | 触发企业数据源同步 | 本地/生产均由 Java 承接同步编排 |
| `GET /api/v1/data-sources/{dataSourceId}/sync-runs` | `knowledge-base-ingestion` | `datasource:manage` | 查询企业数据源同步记录 | 用于运维排障与审计 |
| `GET /api/v1/data-sources/presets` | `knowledge-base-ingestion` | `datasource:manage` | 查询企业数据源模板预设 | 返回 ERP/OA/财务模板，不包含密钥 |
| `POST /api/v1/data-sources/credentials/reencrypt` | `knowledge-base-ingestion` | `datasource:manage` | 维护重加密旧密钥数据源凭证 | 不返回任何凭证明文或密文 |
| `GET /api/v1/data-sources/profile-drift` | `knowledge-base-ingestion` | `datasource:manage` | 审计历史 API profile 配置漂移 | 不返回任何凭证明文或密文 |
| `GET /api/v1/system-alerts` | `audit-history-dashboard` | `notification:read` | 查询系统告警与通知 | 工作台通知入口 |
| `GET /api/v1/auth/me` | `permission-collaboration` | authenticated | 获取当前认证用户 RBAC 上下文 | Higress/OIDC 登录后读取 Java 权限上下文 |
| `POST /api/v1/users/batch-import` | `permission-collaboration` | `user:manage` | 批量导入用户 | 支持组织与角色初始化 |
| `GET /api/v1/organization-directory` | `permission-collaboration` | `permission:read` | 查询组织、岗位、可选审批人目录 | 规则和协作选人复用 |
| `POST /api/v1/organization-units` | `permission-collaboration` | `user:manage` | 创建组织单元 | 组织目录维护 |
| `PUT /api/v1/organization-units/{unitId}` | `permission-collaboration` | `user:manage` | 更新组织单元 | 禁用组织不可更新 |
| `POST /api/v1/organization-positions` | `permission-collaboration` | `user:manage` | 创建组织岗位 | 岗位可绑定角色 |
| `POST /api/v1/organization-position-assignments` | `permission-collaboration` | `user:manage` | 分配用户到组织岗位 | 支持主岗标记 |
| `POST /api/v1/organization-position-assignments/batch-import` | `permission-collaboration` | `user:manage` | 批量导入任职关系 | 返回逐条导入结果 |
| `PUT /api/v1/organization-position-assignments/{assignmentId}` | `permission-collaboration` | `user:manage` | 更新任职关系 | 禁用任职关系不可更新 |
| `POST /api/v1/organization-position-assignments/{assignmentId}/disable` | `permission-collaboration` | `user:manage` | 停用任职关系 | 保留历史组织信息 |
| `POST /api/v1/share-links/{shareToken}/revoke` | `permission-collaboration` | `report:share` | 撤销分享链接 | 仅分享创建者可撤销 |
| `GET /api/v1/files/report-exports/{exportFileId}/download-url` | `report-generation` | `report:export` | 获取报告导出文件下载 URL | 内部授权下载入口 |
| `GET /api/v1/report-templates` | `report-generation` | `report:create` | 查询报告模板 | 模板填报入口 |
| `POST /api/v1/enterprise-export-templates` | `report-generation` | `report:template:manage` | 创建企业导出模板 | 企业品牌规范治理 |
| `GET /api/v1/enterprise-export-templates` | `report-generation` | `report:template:manage` | 查询企业导出模板列表 | 支持按状态筛选 |
| `PUT /api/v1/enterprise-export-templates/{templateId}` | `report-generation` | `report:template:manage` | 更新企业导出模板并生成新版本 | 保留版本治理 |
| `GET /api/v1/enterprise-export-templates/{templateId}/versions` | `report-generation` | `report:template:manage` | 查看企业导出模板版本历史 | 导出审计可追溯 |
| `POST /api/v1/enterprise-export-templates/{templateId}/disable` | `report-generation` | `report:template:manage` | 停用企业导出模板 | 禁止新导出使用 |
| `POST /api/v1/enterprise-export-templates/{templateId}/enable` | `report-generation` | `report:template:manage` | 启用企业导出模板 | 恢复模板可用性 |
| `POST /api/v1/reports/generation-tasks/{taskId}/failure` | `report-generation` | `report:create` | Worker 回传生成失败 | 受控回调入口 |
| `POST /api/v1/reports/generation-tasks/{taskId}/completion` | `report-generation` | `report:create` | Worker 回传生成完成 | 写入引用和模型调用审计 |
| `POST /api/v1/reports/generation-tasks/{taskId}/retry` | `report-generation` | `report:create` | 重试失败的生成任务 | 保留任务闭环 |
| `GET /api/v1/reports/{reportId}/exports/{exportFileId}` | `report-generation` | `report:export` | 查询报告导出文件状态 | 下载前状态查询 |
| `GET /api/v1/reports/{reportId}/versions/diff` | `report-generation` | `report:read` | 比较两个报告版本差异 | 版本治理能力 |
| `POST /api/v1/rules/approval-delegate-rules` | `rule-engine` | `rule:manage` | 创建审批代理规则 | 审批代办治理 |
| `GET /api/v1/rules/approval-delegate-rules` | `rule-engine` | `rule:manage` | 查询审批代理规则 | 支持列表运营 |
| `POST /api/v1/rules/approval-templates` | `rule-engine` | `rule:manage` | 创建审批模板 | 规则设计复用 |
| `GET /api/v1/rules/approval-templates` | `rule-engine` | `rule:manage` | 查询审批模板 | 规则设计器模板选择 |
| `GET /api/v1/rules/approval-templates/{templateId}/usage` | `rule-engine` | `rule:manage` | 查询审批模板引用影响 | 停用前影响分析 |
| `PUT /api/v1/rules/approval-templates/{templateId}` | `rule-engine` | `rule:manage` | 更新审批模板并生成版本 | 模板版本治理 |
| `GET /api/v1/rules/approval-templates/{templateId}/versions` | `rule-engine` | `rule:manage` | 查看审批模板版本列表 | 版本历史 |
| `GET /api/v1/rules/approval-templates/{templateId}/versions/diff` | `rule-engine` | `rule:manage` | 比较审批模板版本差异 | 变更审阅 |
| `POST /api/v1/rules/approval-templates/{templateId}/versions/{version}/rollback` | `rule-engine` | `rule:manage` | 回滚审批模板版本 | 版本恢复 |
| `GET /api/v1/rules/approval-templates/{templateId}/versions/{version}` | `rule-engine` | `rule:manage` | 查看指定审批模板版本 | 版本详情 |
| `POST /api/v1/rules/approval-templates/{templateId}/disable` | `rule-engine` | `rule:manage` | 停用审批模板 | 禁止新规则套用 |
| `POST /api/v1/rules/approval-templates/{templateId}/enable` | `rule-engine` | `rule:manage` | 启用审批模板 | 恢复模板可用性 |
| `POST /api/v1/rules/approval-delegate-rules/batch-import` | `rule-engine` | `rule:manage` | 批量导入审批代理规则 | 组织初始化 |
| `PUT /api/v1/rules/approval-delegate-rules/{delegateRuleId}` | `rule-engine` | `rule:manage` | 更新审批代理规则 | 代理规则维护 |
| `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/disable` | `rule-engine` | `rule:manage` | 停用审批代理规则 | 后续待办不再应用 |
| `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/enable` | `rule-engine` | `rule:manage` | 启用审批代理规则 | 恢复代理关系 |
| `POST /api/v1/rules/{ruleId}/review-submissions` | `rule-engine` | `rule:manage` | 提交规则评审 | 规则发布治理 |
| `POST /api/v1/rules/{ruleId}/approvals` | `rule-engine` | `rule:manage` | 审批规则发布 | 规则上线控制 |
| `POST /api/v1/rules/{ruleId}/runs` | `rule-engine` | `rule:debug` | 执行规则运行 | 调试或实际执行 |
| `GET /api/v1/rules/{ruleId}/runs` | `rule-engine` | `rule:debug` | 查询规则运行历史 | 调试追踪 |
| `GET /api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology` | `rule-engine` | `rule:debug` | 查看子流程拓扑 | 运行链路定位 |
| `GET /api/v1/rules/{ruleId}/approval-records` | `rule-engine` | `rule:debug` | 查询规则审批记录 | 审批中心数据 |
| `GET /api/v1/rules/approval-records` | `rule-engine` | `rule:debug` | 查询全局审批记录 | 待办中心数据 |
| `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/actions` | `rule-engine` | `rule:debug` | 提交审批动作 | approve/reject 等动作 |
| `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplements` | `rule-engine` | `rule:debug` | 补充审批材料 | 审批补充闭环 |
| `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments` | `rule-engine` | `rule:debug` | 上传审批补充附件 | MinIO 证据材料 |
| `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/reminders` | `rule-engine` | `rule:debug` | 发送审批提醒 | SLA 与提醒能力 |
| `POST /api/v1/rules/approval-records/batch-actions` | `rule-engine` | `rule:debug` | 批量处理审批记录 | 批量审批 |
| `GET /api/v1/rules/{ruleId}/action-executions` | `rule-engine` | `rule:debug` | 查询动作执行记录 | Webhook/动作排障 |
| `GET /api/v1/rules/{ruleId}/metrics` | `rule-engine` | `rule:debug` | 查询规则运行指标 | 可观测指标 |
| `PUT /api/v1/rules/{ruleId}/schedule` | `rule-engine` | `rule:manage` | 配置规则调度 | 定时执行治理 |
| `POST /api/v1/rules/{ruleId}/schedule/retry` | `rule-engine` | `rule:manage` | 重试规则调度 | 调度失败恢复 |
| `POST /api/v1/rules/{ruleId}/action-executions/{actionExecutionId}/retry` | `rule-engine` | `rule:manage` | 重试单个动作执行 | 动作失败恢复 |
| `POST /api/v1/rules/{ruleId}/action-executions/batch` | `rule-engine` | `rule:manage` | 批量触发动作执行 | 运维批处理 |

### 13.2 ????????? JSON ????

<!-- API_CONTRACT_STRUCTURED_BACKEND_SUPPLEMENT_START -->
<!-- legacy-test-marker: ### 13.2 鍚庣瀹為檯绔偣缁撴瀯鍖?JSON 濂戠害琛ラ仐 -->

> ???? 13.1 ???????????????????/?? JSON ????????????? `method`?`path`?`contentType`?`pathParams`?`queryParams`?`headers`?`requestBody`?`responseBody`?`statusCodes`?????? schema ???????????????????????

```json
[
  {
    "method": "POST",
    "path": "/api/v1/knowledge-items/batch-import",
    "description": "批量导入知识条目",
    "module": "knowledge-base-ingestion",
    "securityIntent": "knowledge:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "knowledge item batch import summary",
        "properties": {
          "knowledgeBaseId": {
            "type": "integer",
            "required": true,
            "description": "target knowledge base id"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total submitted rows"
          },
          "imported": {
            "type": "integer",
            "required": true,
            "description": "successfully imported row count"
          },
          "failed": {
            "type": "integer",
            "required": true,
            "description": "failed row count"
          },
          "items": {
            "type": "array",
            "required": true,
            "description": "per-row import results",
            "items": {
              "type": "object",
              "required": true,
              "description": "knowledge item import row result",
              "properties": {
                "title": {
                  "type": "string",
                  "required": true,
                  "description": "source row title after trimming"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "row import status, imported or failed"
                },
                "reason": {
                  "type": "string",
                  "required": false,
                  "description": "failure reason such as title_required, content_required, or duplicate_title"
                },
                "itemId": {
                  "type": "integer",
                  "required": false,
                  "description": "created knowledge item id when imported"
                },
                "knowledgeBaseId": {
                  "type": "integer",
                  "required": false,
                  "description": "owning knowledge base id when imported"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/documents/{documentId}",
    "description": "查看文档解析状态与元数据",
    "module": "knowledge-base-ingestion",
    "securityIntent": "knowledge:upload",
    "contentType": "none",
    "pathParams": {
      "documentId": {
        "type": "string",
        "required": true,
        "description": "documentId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "uploaded document parse status metadata",
        "properties": {
          "documentId": {
            "type": "integer",
            "required": true,
            "description": "knowledge document id"
          },
          "fileObjectId": {
            "type": "integer",
            "required": false,
            "description": "stored file object id"
          },
          "knowledgeBaseId": {
            "type": "integer",
            "required": true,
            "description": "owning knowledge base id"
          },
          "filename": {
            "type": "string",
            "required": true,
            "description": "uploaded file name"
          },
          "fileType": {
            "type": "string",
            "required": true,
            "description": "detected file type"
          },
          "size": {
            "type": "integer",
            "required": true,
            "description": "uploaded file size in bytes"
          },
          "contentType": {
            "type": "string",
            "required": false,
            "description": "uploaded file content type"
          },
          "bucket": {
            "type": "string",
            "required": false,
            "description": "object storage bucket"
          },
          "objectKey": {
            "type": "string",
            "required": false,
            "description": "object storage key"
          },
          "parseStatus": {
            "type": "string",
            "required": true,
            "description": "async parse status, for example pending, processed, or failed"
          },
          "parseFailureReason": {
            "type": "string",
            "required": false,
            "description": "parse failure reason when parseStatus is failed"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/data-sources/test-connection",
    "description": "测试企业数据源连接",
    "module": "knowledge-base-ingestion",
    "securityIntent": "datasource:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT"
      }
    },
    "requestBody": {
      "dataSourceId": {
        "type": "integer",
        "required": true,
        "description": "data source identifier to test"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "data source connection test result",
        "properties": {
          "dataSourceId": {
            "type": "integer",
            "required": true,
            "description": "tested data source identifier"
          },
          "success": {
            "type": "boolean",
            "required": true,
            "description": "whether the configured source is reachable"
          },
          "message": {
            "type": "string",
            "required": true,
            "description": "connection test result message"
          },
          "sourceType": {
            "type": "string",
            "required": true,
            "description": "data source type, for example postgresql, mysql, or api"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/data-sources/presets",
    "description": "查询企业数据源模板预设",
    "module": "knowledge-base-ingestion",
    "securityIntent": "datasource:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "array",
        "required": true,
        "description": "ERP/OA/finance data-source presets",
        "items": {
          "type": "object",
          "required": true,
          "properties": {
            "presetId": {
              "type": "string",
              "required": true,
              "description": "stable preset identifier"
            },
            "displayName": {
              "type": "string",
              "required": true,
              "description": "preset display name"
            },
            "category": {
              "type": "string",
              "required": true,
              "description": "business system category"
            },
            "sourceType": {
              "type": "string",
              "required": true,
              "description": "data source type used when saving the configuration"
            },
            "endpoint": {
              "type": "string",
              "required": true,
              "description": "editable example endpoint"
            },
            "username": {
              "type": "string",
              "required": false,
              "description": "example account name or token alias"
            },
            "syncQuery": {
              "type": "string",
              "required": false,
              "description": "database sync query example"
            },
            "fieldMapping": {
              "type": "object",
              "required": false,
              "description": "API profile, rows/title/content/cursor/pagination/auth mapping. Built-in profiles include oa-documents and finance-vouchers."
            },
            "cursorColumn": {
              "type": "string",
              "required": false,
              "description": "incremental cursor column or field"
            },
            "scheduleEnabled": {
              "type": "boolean",
              "required": true,
              "description": "whether scheduled sync is enabled by default"
            },
            "scheduleIntervalSeconds": {
              "type": "integer",
              "required": true,
              "description": "recommended sync interval"
            },
            "maxRetryCount": {
              "type": "integer",
              "required": true,
              "description": "recommended maximum retry count"
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/data-sources",
    "description": "保存企业数据源配置和同步策略",
    "module": "knowledge-base-ingestion",
    "securityIntent": "datasource:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT"
      }
    },
    "requestBody": {
      "name": {
        "type": "string",
        "required": true,
        "description": "data source name"
      },
      "sourceType": {
        "type": "string",
        "required": true,
        "description": "data source type, for example postgresql, mysql, or api"
      },
      "endpoint": {
        "type": "string",
        "required": true,
        "description": "JDBC URL or HTTP API endpoint"
      },
      "username": {
        "type": "string",
        "required": false,
        "description": "connection username"
      },
      "password": {
        "type": "string",
        "required": false,
        "description": "connection secret; encrypted before persistence"
      },
      "knowledgeBaseId": {
        "type": "integer",
        "required": false,
        "description": "target knowledge base identifier"
      },
      "syncQuery": {
        "type": "string",
        "required": false,
        "description": "SQL sync query for JDBC sources"
      },
      "fieldMapping": {
        "type": "object",
        "required": false,
        "description": "API or row field mapping settings. For API sources targeting a knowledge base, rowsPath, titleField, and contentField are required. Optional profileId supports built-in schema profiles oa-documents and finance-vouchers with save-time profile validation, including cursorColumn alignment."
      },
      "cursorColumn": {
        "type": "string",
        "required": false,
        "description": "incremental cursor column"
      },
      "scheduleEnabled": {
        "type": "boolean",
        "required": false,
        "description": "whether scheduled sync is enabled"
      },
      "scheduleIntervalSeconds": {
        "type": "integer",
        "required": false,
        "description": "scheduled sync interval in seconds; minimum 30 when enabled"
      },
      "nextRunAt": {
        "type": "string",
        "required": false,
        "description": "first scheduled run timestamp"
      },
      "maxRetryCount": {
        "type": "integer",
        "required": false,
        "description": "maximum retry count for failed scheduled sync"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "saved data source configuration",
        "properties": {
          "dataSourceId": {
            "type": "integer",
            "required": true,
            "description": "data source identifier"
          },
          "ownerUserId": {
            "type": "integer",
            "required": true,
            "description": "owner user identifier"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "data source name"
          },
          "sourceType": {
            "type": "string",
            "required": true,
            "description": "data source type"
          },
          "endpoint": {
            "type": "string",
            "required": true,
            "description": "JDBC URL or HTTP API endpoint"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "data source lifecycle status"
          },
          "knowledgeBaseId": {
            "type": "integer",
            "required": false,
            "description": "target knowledge base identifier"
          },
          "syncQuery": {
            "type": "string",
            "required": false,
            "description": "configured SQL sync query"
          },
          "fieldMapping": {
            "type": "object",
            "required": true,
            "description": "parsed field mapping configuration"
          },
          "cursorColumn": {
            "type": "string",
            "required": false,
            "description": "incremental cursor column"
          },
          "lastCursor": {
            "type": "string",
            "required": false,
            "description": "last successful cursor value"
          },
          "scheduleEnabled": {
            "type": "boolean",
            "required": true,
            "description": "whether scheduled sync is enabled"
          },
          "scheduleIntervalSeconds": {
            "type": "integer",
            "required": false,
            "description": "scheduled sync interval in seconds"
          },
          "nextRunAt": {
            "type": "string",
            "required": false,
            "description": "next scheduled sync timestamp"
          },
          "failureCount": {
            "type": "integer",
            "required": true,
            "description": "consecutive scheduled sync failure count"
          },
          "maxRetryCount": {
            "type": "integer",
            "required": true,
            "description": "maximum retry count before manual intervention"
          },
          "credentialConfigured": {
            "type": "boolean",
            "required": true,
            "description": "whether a credential secret is configured"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/data-sources/credentials/reencrypt",
    "description": "维护重加密旧密钥数据源凭证",
    "module": "knowledge-base-ingestion",
    "securityIntent": "datasource:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT"
      }
    },
    "requestBody": {
      "limit": {
        "type": "integer",
        "required": false,
        "description": "maximum number of credentialed data sources to scan; defaults to 100 and is clamped to at least 1"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "data source credential re-encryption result without secret values",
        "properties": {
          "scannedCount": {
            "type": "integer",
            "required": true,
            "description": "number of credentialed data sources scanned"
          },
          "migratedCount": {
            "type": "integer",
            "required": true,
            "description": "number of credentials re-encrypted onto the active key"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/data-sources/profile-drift",
    "description": "Audit saved API data sources whose profile mapping no longer matches current built-in profile rules",
    "module": "knowledge-base-ingestion",
    "securityIntent": "datasource:manage",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {
      "limit": {
        "type": "integer",
        "required": false,
        "description": "maximum number of API profile data sources to scan; defaults to 100 and is clamped to at least 1"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "API profile drift audit result without secret values",
        "properties": {
          "scannedCount": {
            "type": "integer",
            "required": true,
            "description": "number of API profile data sources scanned"
          },
          "driftCount": {
            "type": "integer",
            "required": true,
            "description": "number of drifted data sources found"
          },
          "items": {
            "type": "array",
            "required": true,
            "description": "drifted data-source details",
            "items": {
              "type": "object",
              "properties": {
                "dataSourceId": {
                  "type": "integer",
                  "required": true,
                  "description": "data source identifier"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "data source display name"
                },
                "sourceType": {
                  "type": "string",
                  "required": true,
                  "description": "data source type"
                },
                "profileId": {
                  "type": "string",
                  "required": true,
                  "description": "built-in API profile id"
                },
                "cursorColumn": {
                  "type": "string",
                  "required": false,
                  "description": "saved incremental cursor column"
                },
                "failureReason": {
                  "type": "string",
                  "required": true,
                  "description": "profile validation failure reason"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/data-sources/{dataSourceId}/sync-runs",
    "description": "触发企业数据源同步",
    "module": "knowledge-base-ingestion",
    "securityIntent": "datasource:manage",
    "contentType": "application/json",
    "pathParams": {
      "dataSourceId": {
        "type": "string",
        "required": true,
        "description": "dataSourceId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "data source sync run result",
        "properties": {
          "syncRunId": {
            "type": "integer",
            "required": true,
            "description": "sync run identifier"
          },
          "dataSourceId": {
            "type": "integer",
            "required": true,
            "description": "data source identifier"
          },
          "mode": {
            "type": "string",
            "required": true,
            "description": "sync mode, for example manual, manual_retry, scheduled, or skipped"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "sync status, for example succeeded, failed, or skipped"
          },
          "processedRows": {
            "type": "integer",
            "required": true,
            "description": "number of rows imported into knowledge items"
          },
          "failureReason": {
            "type": "string",
            "required": false,
            "description": "failure reason when status is failed"
          },
          "message": {
            "type": "string",
            "required": true,
            "description": "human-readable sync result message"
          },
          "lastCursor": {
            "type": "string",
            "required": false,
            "description": "cursor value after this sync run"
          },
          "startedAt": {
            "type": "string",
            "required": true,
            "description": "sync run start timestamp"
          },
          "finishedAt": {
            "type": "string",
            "required": true,
            "description": "sync run finish timestamp"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/data-sources/{dataSourceId}/sync-runs",
    "description": "查询企业数据源同步记录",
    "module": "knowledge-base-ingestion",
    "securityIntent": "datasource:manage",
    "contentType": "none",
    "pathParams": {
      "dataSourceId": {
        "type": "string",
        "required": true,
        "description": "dataSourceId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged data source sync runs",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "sync run records",
            "items": {
              "type": "object",
              "properties": {
                "syncRunId": {
                  "type": "integer",
                  "required": true,
                  "description": "sync run identifier"
                },
                "dataSourceId": {
                  "type": "integer",
                  "required": true,
                  "description": "data source identifier"
                },
                "mode": {
                  "type": "string",
                  "required": true,
                  "description": "sync mode"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "sync status"
                },
                "processedRows": {
                  "type": "integer",
                  "required": true,
                  "description": "number of rows imported into knowledge items"
                },
                "failureReason": {
                  "type": "string",
                  "required": false,
                  "description": "failure reason when status is failed"
                },
                "message": {
                  "type": "string",
                  "required": true,
                  "description": "human-readable sync result message"
                },
                "lastCursor": {
                  "type": "string",
                  "required": false,
                  "description": "cursor value after this sync run"
                },
                "startedAt": {
                  "type": "string",
                  "required": true,
                  "description": "sync run start timestamp"
                },
                "finishedAt": {
                  "type": "string",
                  "required": true,
                  "description": "sync run finish timestamp"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total sync run count"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/system-alerts",
    "description": "查询系统告警与通知",
    "module": "audit-history-dashboard",
    "securityIntent": "notification:read",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged system alerts for current user",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "system alert items",
            "items": {
              "type": "object",
              "properties": {
                "alertId": {
                  "type": "integer",
                  "required": true,
                  "description": "system alert id"
                },
                "recipientUserId": {
                  "type": "integer",
                  "required": true,
                  "description": "recipient user id"
                },
                "type": {
                  "type": "string",
                  "required": true,
                  "description": "alert type"
                },
                "severity": {
                  "type": "string",
                  "required": true,
                  "description": "alert severity"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "alert read status"
                },
                "resourceType": {
                  "type": "string",
                  "required": true,
                  "description": "related resource type"
                },
                "resourceId": {
                  "type": "integer",
                  "required": false,
                  "description": "related resource id"
                },
                "payload": {
                  "type": "object",
                  "required": true,
                  "description": "sanitized alert payload"
                },
                "createdAt": {
                  "type": "string",
                  "required": true,
                  "description": "alert creation time"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total alerts"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/auth/me",
    "description": "获取当前认证用户 RBAC 上下文",
    "module": "permission-collaboration",
    "securityIntent": "authenticated",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "current authenticated RBAC profile",
        "properties": {
          "userId": {
            "type": "integer",
            "required": true,
            "description": "current user identifier"
          },
          "displayName": {
            "type": "string",
            "required": true,
            "description": "current user display name"
          },
          "roles": {
            "type": "array",
            "required": true,
            "description": "current user roles",
            "items": {
              "type": "string"
            }
          },
          "permissions": {
            "type": "array",
            "required": true,
            "description": "current user permissions",
            "items": {
              "type": "string"
            }
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "current account status, for example enabled or disabled"
          },
          "authProvider": {
            "type": "string",
            "required": true,
            "description": "authentication provider identifier"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/users/batch-import",
    "description": "批量导入用户",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "user batch import result",
        "properties": {
          "imported": {
            "type": "integer",
            "required": true,
            "description": "number of imported users"
          },
          "failed": {
            "type": "integer",
            "required": true,
            "description": "number of failed user rows"
          },
          "items": {
            "type": "array",
            "required": true,
            "description": "per-row import results",
            "items": {
              "type": "object",
              "properties": {
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "username from import row"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "row import status, for example imported or failed"
                },
                "reason": {
                  "type": "string",
                  "required": false,
                  "description": "failure reason for failed row"
                },
                "userId": {
                  "type": "integer",
                  "required": false,
                  "description": "created user identifier for imported row"
                },
                "displayName": {
                  "type": "string",
                  "required": false,
                  "description": "created user display name"
                },
                "department": {
                  "type": "string",
                  "required": false,
                  "description": "created user department"
                },
                "position": {
                  "type": "string",
                  "required": false,
                  "description": "created user position"
                },
                "roles": {
                  "type": "array",
                  "required": false,
                  "description": "created user roles",
                  "items": {
                    "type": "string"
                  }
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/organization-directory",
    "description": "查询组织、岗位、可选审批人目录",
    "module": "permission-collaboration",
    "securityIntent": "permission:read",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization directory grouped for approver and role selection",
        "properties": {
          "departments": {
            "type": "array",
            "required": true,
            "description": "users grouped by department and position",
            "items": {
              "type": "object",
              "properties": {
                "department": {
                  "type": "string",
                  "required": true,
                  "description": "department name"
                },
                "positions": {
                  "type": "array",
                  "required": true,
                  "description": "positions in this department",
                  "items": {
                    "type": "object",
                    "properties": {
                      "position": {
                        "type": "string",
                        "required": true,
                        "description": "position name"
                      },
                      "roles": {
                        "type": "array",
                        "required": true,
                        "description": "roles present in this position",
                        "items": {
                          "type": "string"
                        }
                      },
                      "users": {
                        "type": "array",
                        "required": true,
                        "description": "users in this position",
                        "items": {
                          "type": "object",
                          "properties": {
                            "userId": {
                              "type": "integer",
                              "required": true,
                              "description": "user identifier"
                            },
                            "username": {
                              "type": "string",
                              "required": true,
                              "description": "login username"
                            },
                            "displayName": {
                              "type": "string",
                              "required": true,
                              "description": "user display name"
                            },
                            "roles": {
                              "type": "array",
                              "required": true,
                              "description": "user roles",
                              "items": {
                                "type": "string"
                              }
                            },
                            "department": {
                              "type": "string",
                              "required": false,
                              "description": "department name"
                            },
                            "position": {
                              "type": "string",
                              "required": false,
                              "description": "position name"
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          },
          "roles": {
            "type": "array",
            "required": true,
            "description": "users grouped by role",
            "items": {
              "type": "object",
              "properties": {
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "role code"
                },
                "department": {
                  "type": "string",
                  "required": true,
                  "description": "departments containing this role"
                },
                "position": {
                  "type": "string",
                  "required": true,
                  "description": "positions containing this role"
                },
                "users": {
                  "type": "array",
                  "required": true,
                  "description": "users with this role",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user identifier"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "login username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "user display name"
                      },
                      "roles": {
                        "type": "array",
                        "required": true,
                        "description": "user roles",
                        "items": {
                          "type": "string"
                        }
                      },
                      "department": {
                        "type": "string",
                        "required": false,
                        "description": "department name"
                      },
                      "position": {
                        "type": "string",
                        "required": false,
                        "description": "position name"
                      }
                    }
                  }
                }
              }
            }
          },
          "organizationTree": {
            "type": "array",
            "required": true,
            "description": "hierarchical organization tree",
            "items": {
              "type": "object",
              "properties": {
                "unitId": {
                  "type": "integer",
                  "required": true,
                  "description": "organization unit identifier"
                },
                "code": {
                  "type": "string",
                  "required": true,
                  "description": "organization unit code"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "organization unit name"
                },
                "parentId": {
                  "type": "integer",
                  "required": false,
                  "description": "parent organization unit identifier"
                },
                "unitType": {
                  "type": "string",
                  "required": true,
                  "description": "organization unit type"
                },
                "sortOrder": {
                  "type": "integer",
                  "required": true,
                  "description": "display sort order"
                },
                "positions": {
                  "type": "array",
                  "required": true,
                  "description": "positions under this unit",
                  "items": {
                    "type": "object",
                    "properties": {
                      "positionId": {
                        "type": "integer",
                        "required": true,
                        "description": "position identifier"
                      },
                      "organizationUnitId": {
                        "type": "integer",
                        "required": true,
                        "description": "owning organization unit identifier"
                      },
                      "code": {
                        "type": "string",
                        "required": true,
                        "description": "position code"
                      },
                      "name": {
                        "type": "string",
                        "required": true,
                        "description": "position name"
                      },
                      "roles": {
                        "type": "array",
                        "required": true,
                        "description": "roles granted by this position",
                        "items": {
                          "type": "string"
                        }
                      },
                      "managerUserId": {
                        "type": "integer",
                        "required": false,
                        "description": "manager user identifier"
                      },
                      "users": {
                        "type": "array",
                        "required": true,
                        "description": "users assigned to this position",
                        "items": {
                          "type": "object",
                          "properties": {
                            "userId": {
                              "type": "integer",
                              "required": true,
                              "description": "user identifier"
                            },
                            "username": {
                              "type": "string",
                              "required": true,
                              "description": "login username"
                            },
                            "displayName": {
                              "type": "string",
                              "required": true,
                              "description": "user display name"
                            },
                            "roles": {
                              "type": "array",
                              "required": true,
                              "description": "user roles",
                              "items": {
                                "type": "string"
                              }
                            },
                            "department": {
                              "type": "string",
                              "required": false,
                              "description": "department name"
                            },
                            "position": {
                              "type": "string",
                              "required": false,
                              "description": "position name"
                            }
                          }
                        }
                      }
                    }
                  }
                },
                "children": {
                  "type": "array",
                  "required": true,
                  "description": "child organization units",
                  "items": {
                    "type": "object"
                  }
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/organization-units",
    "description": "创建组织单元",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization unit",
        "properties": {
          "unitId": {
            "type": "integer",
            "required": true,
            "description": "organization unit identifier"
          },
          "code": {
            "type": "string",
            "required": true,
            "description": "organization unit code"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "organization unit name"
          },
          "parentId": {
            "type": "integer",
            "required": false,
            "description": "parent organization unit identifier"
          },
          "unitType": {
            "type": "string",
            "required": true,
            "description": "organization unit type"
          },
          "sortOrder": {
            "type": "integer",
            "required": true,
            "description": "display sort order"
          },
          "positions": {
            "type": "array",
            "required": true,
            "description": "positions under this unit",
            "items": {
              "type": "object",
              "properties": {
                "positionId": {
                  "type": "integer",
                  "required": true,
                  "description": "position identifier"
                },
                "organizationUnitId": {
                  "type": "integer",
                  "required": true,
                  "description": "owning organization unit identifier"
                },
                "code": {
                  "type": "string",
                  "required": true,
                  "description": "position code"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "position name"
                },
                "roles": {
                  "type": "array",
                  "required": true,
                  "description": "roles granted by this position",
                  "items": {
                    "type": "string"
                  }
                },
                "managerUserId": {
                  "type": "integer",
                  "required": false,
                  "description": "manager user identifier"
                },
                "users": {
                  "type": "array",
                  "required": true,
                  "description": "users assigned to this position",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user identifier"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "login username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "user display name"
                      },
                      "roles": {
                        "type": "array",
                        "required": true,
                        "description": "user roles",
                        "items": {
                          "type": "string"
                        }
                      },
                      "department": {
                        "type": "string",
                        "required": false,
                        "description": "department name"
                      },
                      "position": {
                        "type": "string",
                        "required": false,
                        "description": "position name"
                      }
                    }
                  }
                }
              }
            }
          },
          "children": {
            "type": "array",
            "required": true,
            "description": "child organization units",
            "items": {
              "type": "object"
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "PUT",
    "path": "/api/v1/organization-units/{unitId}",
    "description": "更新组织单元",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {
      "unitId": {
        "type": "string",
        "required": true,
        "description": "unitId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization unit",
        "properties": {
          "unitId": {
            "type": "integer",
            "required": true,
            "description": "organization unit identifier"
          },
          "code": {
            "type": "string",
            "required": true,
            "description": "organization unit code"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "organization unit name"
          },
          "parentId": {
            "type": "integer",
            "required": false,
            "description": "parent organization unit identifier"
          },
          "unitType": {
            "type": "string",
            "required": true,
            "description": "organization unit type"
          },
          "sortOrder": {
            "type": "integer",
            "required": true,
            "description": "display sort order"
          },
          "positions": {
            "type": "array",
            "required": true,
            "description": "positions under this unit",
            "items": {
              "type": "object",
              "properties": {
                "positionId": {
                  "type": "integer",
                  "required": true,
                  "description": "position identifier"
                },
                "organizationUnitId": {
                  "type": "integer",
                  "required": true,
                  "description": "owning organization unit identifier"
                },
                "code": {
                  "type": "string",
                  "required": true,
                  "description": "position code"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "position name"
                },
                "roles": {
                  "type": "array",
                  "required": true,
                  "description": "roles granted by this position",
                  "items": {
                    "type": "string"
                  }
                },
                "managerUserId": {
                  "type": "integer",
                  "required": false,
                  "description": "manager user identifier"
                },
                "users": {
                  "type": "array",
                  "required": true,
                  "description": "users assigned to this position",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user identifier"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "login username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "user display name"
                      },
                      "roles": {
                        "type": "array",
                        "required": true,
                        "description": "user roles",
                        "items": {
                          "type": "string"
                        }
                      },
                      "department": {
                        "type": "string",
                        "required": false,
                        "description": "department name"
                      },
                      "position": {
                        "type": "string",
                        "required": false,
                        "description": "position name"
                      }
                    }
                  }
                }
              }
            }
          },
          "children": {
            "type": "array",
            "required": true,
            "description": "child organization units",
            "items": {
              "type": "object"
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments",
    "description": "上传审批补充附件",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "multipart/form-data",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      },
      "approvalRecordId": {
        "type": "string",
        "required": true,
        "description": "approvalRecordId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT"
      },
      "Content-Type": {
        "type": "string",
        "required": true,
        "description": "multipart/form-data"
      }
    },
    "requestBody": {
      "file": {
        "type": "binary",
        "required": true,
        "description": "supplement attachment file"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "stored supplement attachment metadata",
        "properties": {
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "approvalRecordId": {
            "type": "integer",
            "required": true,
            "description": "approval record id"
          },
          "fileName": {
            "type": "string",
            "required": true,
            "description": "original uploaded file name"
          },
          "bucket": {
            "type": "string",
            "required": true,
            "description": "object storage bucket"
          },
          "objectKey": {
            "type": "string",
            "required": true,
            "description": "object storage key"
          },
          "contentType": {
            "type": "string",
            "required": true,
            "description": "uploaded content type"
          },
          "sizeBytes": {
            "type": "integer",
            "required": true,
            "description": "uploaded file size"
          },
          "evidenceUrl": {
            "type": "string",
            "required": true,
            "description": "minio evidence URL for supplement submission"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404,
      500
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/organization-positions",
    "description": "创建组织岗位",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization position",
        "properties": {
          "positionId": {
            "type": "integer",
            "required": true,
            "description": "position identifier"
          },
          "organizationUnitId": {
            "type": "integer",
            "required": true,
            "description": "owning organization unit identifier"
          },
          "code": {
            "type": "string",
            "required": true,
            "description": "position code"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "position name"
          },
          "roles": {
            "type": "array",
            "required": true,
            "description": "roles granted by this position",
            "items": {
              "type": "string"
            }
          },
          "managerUserId": {
            "type": "integer",
            "required": false,
            "description": "manager user identifier"
          },
          "users": {
            "type": "array",
            "required": true,
            "description": "users assigned to this position",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": true,
                  "description": "user identifier"
                },
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "login username"
                },
                "displayName": {
                  "type": "string",
                  "required": true,
                  "description": "user display name"
                },
                "roles": {
                  "type": "array",
                  "required": true,
                  "description": "user roles",
                  "items": {
                    "type": "string"
                  }
                },
                "department": {
                  "type": "string",
                  "required": false,
                  "description": "department name"
                },
                "position": {
                  "type": "string",
                  "required": false,
                  "description": "position name"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/organization-position-assignments",
    "description": "分配用户到组织岗位",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization position assignment",
        "properties": {
          "assignmentId": {
            "type": "integer",
            "required": true,
            "description": "assignment identifier"
          },
          "userId": {
            "type": "integer",
            "required": true,
            "description": "assigned user identifier"
          },
          "positionId": {
            "type": "integer",
            "required": true,
            "description": "assigned position identifier"
          },
          "primary": {
            "type": "boolean",
            "required": true,
            "description": "whether this is the primary position assignment"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "assignment status"
          },
          "activeFrom": {
            "type": "string",
            "required": false,
            "description": "assignment start timestamp"
          },
          "activeTo": {
            "type": "string",
            "required": false,
            "description": "assignment end timestamp"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/organization-position-assignments/batch-import",
    "description": "批量导入任职关系",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization position assignment batch import result",
        "properties": {
          "imported": {
            "type": "integer",
            "required": true,
            "description": "number of imported assignments"
          },
          "failed": {
            "type": "integer",
            "required": true,
            "description": "number of failed assignment rows"
          },
          "items": {
            "type": "array",
            "required": true,
            "description": "per-row assignment import results",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": false,
                  "description": "source user identifier"
                },
                "positionId": {
                  "type": "integer",
                  "required": false,
                  "description": "source position identifier"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "row import status, for example imported or failed"
                },
                "reason": {
                  "type": "string",
                  "required": false,
                  "description": "failure reason for failed row"
                },
                "assignmentId": {
                  "type": "integer",
                  "required": false,
                  "description": "created assignment identifier"
                },
                "primary": {
                  "type": "boolean",
                  "required": false,
                  "description": "whether this is the primary position assignment"
                },
                "activeFrom": {
                  "type": "string",
                  "required": false,
                  "description": "assignment start timestamp"
                },
                "activeTo": {
                  "type": "string",
                  "required": false,
                  "description": "assignment end timestamp"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "PUT",
    "path": "/api/v1/organization-position-assignments/{assignmentId}",
    "description": "更新任职关系",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {
      "assignmentId": {
        "type": "string",
        "required": true,
        "description": "assignmentId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization position assignment",
        "properties": {
          "assignmentId": {
            "type": "integer",
            "required": true,
            "description": "assignment identifier"
          },
          "userId": {
            "type": "integer",
            "required": true,
            "description": "assigned user identifier"
          },
          "positionId": {
            "type": "integer",
            "required": true,
            "description": "assigned position identifier"
          },
          "primary": {
            "type": "boolean",
            "required": true,
            "description": "whether this is the primary position assignment"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "assignment status"
          },
          "activeFrom": {
            "type": "string",
            "required": false,
            "description": "assignment start timestamp"
          },
          "activeTo": {
            "type": "string",
            "required": false,
            "description": "assignment end timestamp"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/organization-position-assignments/{assignmentId}/disable",
    "description": "停用任职关系",
    "module": "permission-collaboration",
    "securityIntent": "user:manage",
    "contentType": "application/json",
    "pathParams": {
      "assignmentId": {
        "type": "string",
        "required": true,
        "description": "assignmentId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "organization position assignment",
        "properties": {
          "assignmentId": {
            "type": "integer",
            "required": true,
            "description": "assignment identifier"
          },
          "userId": {
            "type": "integer",
            "required": true,
            "description": "assigned user identifier"
          },
          "positionId": {
            "type": "integer",
            "required": true,
            "description": "assigned position identifier"
          },
          "primary": {
            "type": "boolean",
            "required": true,
            "description": "whether this is the primary position assignment"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "assignment status"
          },
          "activeFrom": {
            "type": "string",
            "required": false,
            "description": "assignment start timestamp"
          },
          "activeTo": {
            "type": "string",
            "required": false,
            "description": "assignment end timestamp"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/share-links/{shareToken}/revoke",
    "description": "撤销分享链接",
    "module": "permission-collaboration",
    "securityIntent": "report:share",
    "contentType": "application/json",
    "pathParams": {
      "shareToken": {
        "type": "string",
        "required": true,
        "description": "shareToken path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "revoked share link",
        "properties": {
          "shareLinkId": {
            "type": "integer",
            "required": true,
            "description": "share link id"
          },
          "reportId": {
            "type": "integer",
            "required": true,
            "description": "shared report id"
          },
          "createdBy": {
            "type": "integer",
            "required": true,
            "description": "share creator user id"
          },
          "shareToken": {
            "type": "string",
            "required": true,
            "description": "opaque share token"
          },
          "shareUrl": {
            "type": "string",
            "required": true,
            "description": "front-end share path"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "share status; revoked after this operation"
          },
          "expiresAt": {
            "type": "string",
            "required": false,
            "description": "share expiration time"
          },
          "passwordRequired": {
            "type": "boolean",
            "required": true,
            "description": "whether password challenge is required"
          },
          "allowDownload": {
            "type": "boolean",
            "required": true,
            "description": "whether shared downloads are allowed"
          },
          "allowedDownloadFormats": {
            "type": "array",
            "required": true,
            "description": "allowed export formats",
            "items": {
              "type": "string"
            }
          },
          "maxAccessCount": {
            "type": "integer",
            "required": false,
            "description": "maximum access count"
          },
          "allowedVisitors": {
            "type": "array",
            "required": true,
            "description": "allowed visitor identifiers",
            "items": {
              "type": "string"
            }
          },
          "allowedVisitorDomains": {
            "type": "array",
            "required": true,
            "description": "allowed visitor domains",
            "items": {
              "type": "string"
            }
          },
          "singleUse": {
            "type": "boolean",
            "required": true,
            "description": "whether the share is single-use"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/files/report-exports/{exportFileId}/download-url",
    "description": "获取报告导出文件下载 URL",
    "module": "report-generation",
    "securityIntent": "report:export",
    "contentType": "none",
    "pathParams": {
      "exportFileId": {
        "type": "string",
        "required": true,
        "description": "exportFileId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "fresh report export presigned download URL",
        "properties": {
          "exportFileId": {
            "type": "integer",
            "required": true,
            "description": "report export file id"
          },
          "reportId": {
            "type": "integer",
            "required": true,
            "description": "report id"
          },
          "fileName": {
            "type": "string",
            "required": true,
            "description": "exported file name"
          },
          "contentType": {
            "type": "string",
            "required": true,
            "description": "exported file content type"
          },
          "sizeBytes": {
            "type": "integer",
            "required": true,
            "description": "exported file size in bytes"
          },
          "downloadUrl": {
            "type": "string",
            "required": true,
            "description": "presigned object storage download URL"
          },
          "expiresAt": {
            "type": "string",
            "required": true,
            "description": "download URL expiration timestamp"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/report-templates",
    "description": "查询报告模板",
    "module": "report-generation",
    "securityIntent": "report:create",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "array",
        "required": true,
        "description": "active report templates for template-based generation",
        "items": {
          "type": "object",
          "properties": {
            "templateId": {
              "type": "string",
              "required": true,
              "description": "template identifier"
            },
            "name": {
              "type": "string",
              "required": true,
              "description": "template display name"
            },
            "category": {
              "type": "string",
              "required": true,
              "description": "template category"
            },
            "version": {
              "type": "string",
              "required": true,
              "description": "template version"
            },
            "status": {
              "type": "string",
              "required": true,
              "description": "template status"
            },
            "fields": {
              "type": "array",
              "required": true,
              "description": "template input fields",
              "items": {
                "type": "object",
                "properties": {
                  "fieldKey": {
                    "type": "string",
                    "required": true,
                    "description": "field key"
                  },
                  "label": {
                    "type": "string",
                    "required": true,
                    "description": "field label"
                  },
                  "type": {
                    "type": "string",
                    "required": true,
                    "description": "field input type"
                  },
                  "required": {
                    "type": "boolean",
                    "required": true,
                    "description": "whether the field is required"
                  },
                  "options": {
                    "type": "array",
                    "required": true,
                    "description": "select options",
                    "items": {
                      "type": "string"
                    }
                  },
                  "defaultValue": {
                    "type": "string",
                    "required": false,
                    "description": "default field value"
                  },
                  "helpText": {
                    "type": "string",
                    "required": false,
                    "description": "field helper text"
                  }
                }
              }
            },
            "outlineSchema": {
              "type": "object",
              "required": true,
              "description": "template outline schema"
            },
            "updatedAt": {
              "type": "string",
              "required": false,
              "description": "template update time"
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/enterprise-export-templates",
    "description": "创建企业导出模板",
    "module": "report-generation",
    "securityIntent": "report:template:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "created enterprise export template",
        "properties": {
          "id": {
            "type": "integer",
            "required": false,
            "description": "database primary key"
          },
          "templateId": {
            "type": "string",
            "required": true,
            "description": "enterprise export template business id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "enterprise export template name"
          },
          "version": {
            "type": "string",
            "required": true,
            "description": "version label, for example v1"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template lifecycle status, for example active or disabled"
          },
          "brandSnapshot": {
            "type": "object",
            "required": true,
            "description": "normalized enterprise brand snapshot used for export rendering"
          },
          "createdBy": {
            "type": "integer",
            "required": false,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": false,
            "description": "creation timestamp in ISO-8601 format"
          },
          "updatedAt": {
            "type": "string",
            "required": false,
            "description": "last update timestamp in ISO-8601 format"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/enterprise-export-templates",
    "description": "查询企业导出模板列表",
    "module": "report-generation",
    "securityIntent": "report:template:manage",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged enterprise export templates",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "enterprise export template items",
            "items": {
              "type": "object",
              "required": true,
              "description": "enterprise export template item",
              "properties": {
                "id": {
                  "type": "integer",
                  "required": false,
                  "description": "database primary key"
                },
                "templateId": {
                  "type": "string",
                  "required": true,
                  "description": "enterprise export template business id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "enterprise export template name"
                },
                "version": {
                  "type": "string",
                  "required": true,
                  "description": "version label, for example v1"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "template lifecycle status, for example active or disabled"
                },
                "brandSnapshot": {
                  "type": "object",
                  "required": true,
                  "description": "normalized enterprise brand snapshot used for export rendering"
                },
                "createdBy": {
                  "type": "integer",
                  "required": false,
                  "description": "creator user id"
                },
                "createdAt": {
                  "type": "string",
                  "required": false,
                  "description": "creation timestamp in ISO-8601 format"
                },
                "updatedAt": {
                  "type": "string",
                  "required": false,
                  "description": "last update timestamp in ISO-8601 format"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total matching templates"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "PUT",
    "path": "/api/v1/enterprise-export-templates/{templateId}",
    "description": "更新企业导出模板并生成新版本",
    "module": "report-generation",
    "securityIntent": "report:template:manage",
    "contentType": "application/json",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "updated enterprise export template",
        "properties": {
          "id": {
            "type": "integer",
            "required": false,
            "description": "database primary key"
          },
          "templateId": {
            "type": "string",
            "required": true,
            "description": "enterprise export template business id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "enterprise export template name"
          },
          "version": {
            "type": "string",
            "required": true,
            "description": "version label, for example v1"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template lifecycle status, for example active or disabled"
          },
          "brandSnapshot": {
            "type": "object",
            "required": true,
            "description": "normalized enterprise brand snapshot used for export rendering"
          },
          "createdBy": {
            "type": "integer",
            "required": false,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": false,
            "description": "creation timestamp in ISO-8601 format"
          },
          "updatedAt": {
            "type": "string",
            "required": false,
            "description": "last update timestamp in ISO-8601 format"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/enterprise-export-templates/{templateId}/versions",
    "description": "查看企业导出模板版本历史",
    "module": "report-generation",
    "securityIntent": "report:template:manage",
    "contentType": "none",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "array",
        "required": true,
        "description": "enterprise export template version history items",
        "items": {
          "type": "object",
          "required": true,
          "description": "enterprise export template item",
          "properties": {
            "id": {
              "type": "integer",
              "required": false,
              "description": "database primary key"
            },
            "templateId": {
              "type": "string",
              "required": true,
              "description": "enterprise export template business id"
            },
            "name": {
              "type": "string",
              "required": true,
              "description": "enterprise export template name"
            },
            "version": {
              "type": "string",
              "required": true,
              "description": "version label, for example v1"
            },
            "status": {
              "type": "string",
              "required": true,
              "description": "template lifecycle status, for example active or disabled"
            },
            "brandSnapshot": {
              "type": "object",
              "required": true,
              "description": "normalized enterprise brand snapshot used for export rendering"
            },
            "createdBy": {
              "type": "integer",
              "required": false,
              "description": "creator user id"
            },
            "createdAt": {
              "type": "string",
              "required": false,
              "description": "creation timestamp in ISO-8601 format"
            },
            "updatedAt": {
              "type": "string",
              "required": false,
              "description": "last update timestamp in ISO-8601 format"
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/enterprise-export-templates/{templateId}/disable",
    "description": "停用企业导出模板",
    "module": "report-generation",
    "securityIntent": "report:template:manage",
    "contentType": "application/json",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "disabled enterprise export template",
        "properties": {
          "id": {
            "type": "integer",
            "required": false,
            "description": "database primary key"
          },
          "templateId": {
            "type": "string",
            "required": true,
            "description": "enterprise export template business id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "enterprise export template name"
          },
          "version": {
            "type": "string",
            "required": true,
            "description": "version label, for example v1"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template lifecycle status, for example active or disabled"
          },
          "brandSnapshot": {
            "type": "object",
            "required": true,
            "description": "normalized enterprise brand snapshot used for export rendering"
          },
          "createdBy": {
            "type": "integer",
            "required": false,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": false,
            "description": "creation timestamp in ISO-8601 format"
          },
          "updatedAt": {
            "type": "string",
            "required": false,
            "description": "last update timestamp in ISO-8601 format"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/enterprise-export-templates/{templateId}/enable",
    "description": "启用企业导出模板",
    "module": "report-generation",
    "securityIntent": "report:template:manage",
    "contentType": "application/json",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "enabled enterprise export template",
        "properties": {
          "id": {
            "type": "integer",
            "required": false,
            "description": "database primary key"
          },
          "templateId": {
            "type": "string",
            "required": true,
            "description": "enterprise export template business id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "enterprise export template name"
          },
          "version": {
            "type": "string",
            "required": true,
            "description": "version label, for example v1"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template lifecycle status, for example active or disabled"
          },
          "brandSnapshot": {
            "type": "object",
            "required": true,
            "description": "normalized enterprise brand snapshot used for export rendering"
          },
          "createdBy": {
            "type": "integer",
            "required": false,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": false,
            "description": "creation timestamp in ISO-8601 format"
          },
          "updatedAt": {
            "type": "string",
            "required": false,
            "description": "last update timestamp in ISO-8601 format"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/reports/generation-tasks/{taskId}/failure",
    "description": "Worker 回传生成失败",
    "module": "report-generation",
    "securityIntent": "report:create",
    "contentType": "application/json",
    "pathParams": {
      "taskId": {
        "type": "string",
        "required": true,
        "description": "taskId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "failed report generation task",
        "properties": {
          "taskId": {
            "type": "integer",
            "required": true,
            "description": "report generation task id"
          },
          "reportId": {
            "type": "integer",
            "required": false,
            "description": "bound report id; may be null before outline confirmation"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "task status"
          },
          "currentStage": {
            "type": "string",
            "required": false,
            "description": "current generation stage"
          },
          "progress": {
            "type": "integer",
            "required": true,
            "description": "generation progress from 0 to 100"
          },
          "traceId": {
            "type": "string",
            "required": true,
            "description": "generation trace id"
          },
          "outline": {
            "type": "object",
            "required": false,
            "description": "outline or confirmed outline payload"
          },
          "failureReason": {
            "type": "string",
            "required": false,
            "description": "failure reason when task failed or is retryable"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "task creation timestamp in ISO-8601 format"
          },
          "errorCode": {
            "type": "string",
            "required": true,
            "description": "worker supplied failure error code"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/reports/generation-tasks/{taskId}/completion",
    "description": "Worker 回传生成完成",
    "module": "report-generation",
    "securityIntent": "report:create",
    "contentType": "application/json",
    "pathParams": {
      "taskId": {
        "type": "string",
        "required": true,
        "description": "taskId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "completed report generation task",
        "properties": {
          "taskId": {
            "type": "integer",
            "required": true,
            "description": "report generation task id"
          },
          "reportId": {
            "type": "integer",
            "required": false,
            "description": "bound report id; may be null before outline confirmation"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "task status"
          },
          "currentStage": {
            "type": "string",
            "required": false,
            "description": "current generation stage"
          },
          "progress": {
            "type": "integer",
            "required": true,
            "description": "generation progress from 0 to 100"
          },
          "traceId": {
            "type": "string",
            "required": true,
            "description": "generation trace id"
          },
          "outline": {
            "type": "object",
            "required": false,
            "description": "outline or confirmed outline payload"
          },
          "failureReason": {
            "type": "string",
            "required": false,
            "description": "failure reason when task failed or is retryable"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "task creation timestamp in ISO-8601 format"
          },
          "versionId": {
            "type": "integer",
            "required": true,
            "description": "created report version id"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/reports/generation-tasks/{taskId}/retry",
    "description": "重试失败的生成任务",
    "module": "report-generation",
    "securityIntent": "report:create",
    "contentType": "application/json",
    "pathParams": {
      "taskId": {
        "type": "string",
        "required": true,
        "description": "taskId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "retried report generation task",
        "properties": {
          "taskId": {
            "type": "integer",
            "required": true,
            "description": "report generation task id"
          },
          "reportId": {
            "type": "integer",
            "required": false,
            "description": "bound report id; may be null before outline confirmation"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "task status"
          },
          "currentStage": {
            "type": "string",
            "required": false,
            "description": "current generation stage"
          },
          "progress": {
            "type": "integer",
            "required": true,
            "description": "generation progress from 0 to 100"
          },
          "traceId": {
            "type": "string",
            "required": true,
            "description": "generation trace id"
          },
          "outline": {
            "type": "object",
            "required": false,
            "description": "outline or confirmed outline payload"
          },
          "failureReason": {
            "type": "string",
            "required": false,
            "description": "failure reason when task failed or is retryable"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "task creation timestamp in ISO-8601 format"
          },
          "retryReason": {
            "type": "string",
            "required": false,
            "description": "requested retry reason"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/reports/{reportId}/exports/{exportFileId}",
    "description": "查询报告导出文件状态",
    "module": "report-generation",
    "securityIntent": "report:export",
    "contentType": "none",
    "pathParams": {
      "reportId": {
        "type": "string",
        "required": true,
        "description": "reportId path parameter"
      },
      "exportFileId": {
        "type": "string",
        "required": true,
        "description": "exportFileId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "report export status and controlled download metadata",
        "properties": {
          "reportId": {
            "type": "integer",
            "required": true,
            "description": "report id"
          },
          "exportFileId": {
            "type": "integer",
            "required": true,
            "description": "report export file id"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "export status, for example completed or expired"
          },
          "format": {
            "type": "string",
            "required": false,
            "description": "export format, for example markdown, docx, pdf, or pptx"
          },
          "templateId": {
            "type": "string",
            "required": false,
            "description": "export template id"
          },
          "downloadPolicy": {
            "type": "string",
            "required": true,
            "description": "download policy, for example presigned_url"
          },
          "bucket": {
            "type": "string",
            "required": false,
            "description": "object storage bucket"
          },
          "objectKey": {
            "type": "string",
            "required": false,
            "description": "object storage key"
          },
          "fileName": {
            "type": "string",
            "required": false,
            "description": "exported file name"
          },
          "contentType": {
            "type": "string",
            "required": false,
            "description": "exported file content type"
          },
          "sizeBytes": {
            "type": "integer",
            "required": false,
            "description": "exported file size in bytes"
          },
          "downloadUrl": {
            "type": "string",
            "required": false,
            "description": "controlled download URL endpoint"
          },
          "expiresAt": {
            "type": "string",
            "required": false,
            "description": "download URL expiration timestamp"
          },
          "brandSnapshot": {
            "type": "object",
            "required": false,
            "description": "enterprise brand snapshot for DOCX/PDF/PPTX exports"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/reports/{reportId}/versions/diff",
    "description": "比较两个报告版本差异",
    "module": "report-generation",
    "securityIntent": "report:read",
    "contentType": "none",
    "pathParams": {
      "reportId": {
        "type": "string",
        "required": true,
        "description": "reportId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "report version diff",
        "properties": {
          "reportId": {
            "type": "integer",
            "required": true,
            "description": "report id"
          },
          "baseVersionId": {
            "type": "integer",
            "required": true,
            "description": "base version id"
          },
          "targetVersionId": {
            "type": "integer",
            "required": true,
            "description": "target version id"
          },
          "summary": {
            "type": "object",
            "required": true,
            "description": "diff summary counts",
            "properties": {
              "added": {
                "type": "integer",
                "required": true,
                "description": "added section count"
              },
              "removed": {
                "type": "integer",
                "required": true,
                "description": "removed section count"
              },
              "modified": {
                "type": "integer",
                "required": true,
                "description": "modified section count"
              },
              "unchanged": {
                "type": "integer",
                "required": true,
                "description": "unchanged section count"
              }
            }
          },
          "changes": {
            "type": "array",
            "required": true,
            "description": "section-level changes",
            "items": {
              "type": "object",
              "properties": {
                "changeType": {
                  "type": "string",
                  "required": true,
                  "description": "added, removed, or modified"
                },
                "heading": {
                  "type": "string",
                  "required": true,
                  "description": "section heading"
                },
                "baseContent": {
                  "type": "string",
                  "required": false,
                  "description": "base version section content"
                },
                "targetContent": {
                  "type": "string",
                  "required": false,
                  "description": "target version section content"
                },
                "baseCitations": {
                  "type": "array",
                  "required": true,
                  "description": "base version citations",
                  "items": {
                    "type": "object"
                  }
                },
                "targetCitations": {
                  "type": "array",
                  "required": true,
                  "description": "target version citations",
                  "items": {
                    "type": "object"
                  }
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-delegate-rules",
    "description": "创建审批代理规则",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval delegate rule",
        "properties": {
          "delegateRuleId": {
            "type": "integer",
            "required": true,
            "description": "approval delegate rule id"
          },
          "assigneeRole": {
            "type": "string",
            "required": true,
            "description": "original assignee role"
          },
          "delegateRole": {
            "type": "string",
            "required": true,
            "description": "delegate role"
          },
          "activeFrom": {
            "type": "string",
            "required": false,
            "description": "delegate active start time"
          },
          "activeTo": {
            "type": "string",
            "required": false,
            "description": "delegate active end time"
          },
          "activeWeekdays": {
            "type": "array",
            "required": true,
            "description": "active weekdays",
            "items": {
              "type": "string"
            }
          },
          "activeDates": {
            "type": "array",
            "required": true,
            "description": "active dates",
            "items": {
              "type": "string"
            }
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "delegate rule status"
          },
          "reason": {
            "type": "string",
            "required": false,
            "description": "change reason"
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/approval-delegate-rules",
    "description": "查询审批代理规则",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged approval delegate rules",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "page items",
            "items": {
              "type": "object",
              "properties": {
                "delegateRuleId": {
                  "type": "integer",
                  "required": true,
                  "description": "approval delegate rule id"
                },
                "assigneeRole": {
                  "type": "string",
                  "required": true,
                  "description": "original assignee role"
                },
                "delegateRole": {
                  "type": "string",
                  "required": true,
                  "description": "delegate role"
                },
                "activeFrom": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active start time"
                },
                "activeTo": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active end time"
                },
                "activeWeekdays": {
                  "type": "array",
                  "required": true,
                  "description": "active weekdays",
                  "items": {
                    "type": "string"
                  }
                },
                "activeDates": {
                  "type": "array",
                  "required": true,
                  "description": "active dates",
                  "items": {
                    "type": "string"
                  }
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "delegate rule status"
                },
                "reason": {
                  "type": "string",
                  "required": false,
                  "description": "change reason"
                },
                "createdByUserId": {
                  "type": "integer",
                  "required": true,
                  "description": "creator user id"
                },
                "createdAt": {
                  "type": "string",
                  "required": true,
                  "description": "creation time"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total items"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-templates",
    "description": "创建审批模板",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval template",
        "properties": {
          "approvalTemplateId": {
            "type": "integer",
            "required": true,
            "description": "approval template id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "template name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "template description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template status"
          },
          "version": {
            "type": "integer",
            "required": true,
            "description": "template version"
          },
          "steps": {
            "type": "array",
            "required": true,
            "description": "approval steps",
            "items": {
              "type": "object"
            }
          },
          "usageCount": {
            "type": "integer",
            "required": true,
            "description": "referencing rule count"
          },
          "usageRules": {
            "type": "array",
            "required": true,
            "description": "referencing rules",
            "items": {
              "type": "object",
              "properties": {
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "rule name"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "rule status"
                }
              }
            }
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "updatedAt": {
            "type": "string",
            "required": true,
            "description": "update time"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/approval-templates",
    "description": "查询审批模板",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged approval templates",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "page items",
            "items": {
              "type": "object",
              "properties": {
                "approvalTemplateId": {
                  "type": "integer",
                  "required": true,
                  "description": "approval template id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "template name"
                },
                "description": {
                  "type": "string",
                  "required": true,
                  "description": "template description"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "template status"
                },
                "version": {
                  "type": "integer",
                  "required": true,
                  "description": "template version"
                },
                "steps": {
                  "type": "array",
                  "required": true,
                  "description": "approval steps",
                  "items": {
                    "type": "object"
                  }
                },
                "usageCount": {
                  "type": "integer",
                  "required": true,
                  "description": "referencing rule count"
                },
                "usageRules": {
                  "type": "array",
                  "required": true,
                  "description": "referencing rules",
                  "items": {
                    "type": "object",
                    "properties": {
                      "ruleId": {
                        "type": "integer",
                        "required": true,
                        "description": "rule id"
                      },
                      "name": {
                        "type": "string",
                        "required": true,
                        "description": "rule name"
                      },
                      "status": {
                        "type": "string",
                        "required": true,
                        "description": "rule status"
                      }
                    }
                  }
                },
                "createdByUserId": {
                  "type": "integer",
                  "required": true,
                  "description": "creator user id"
                },
                "createdAt": {
                  "type": "string",
                  "required": true,
                  "description": "creation time"
                },
                "updatedAt": {
                  "type": "string",
                  "required": true,
                  "description": "update time"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total items"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/approval-templates/{templateId}/usage",
    "description": "查询审批模板引用影响",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "none",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged approval template usage rules",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "page items",
            "items": {
              "type": "object",
              "properties": {
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "rule name"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "rule status"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total items"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "PUT",
    "path": "/api/v1/rules/approval-templates/{templateId}",
    "description": "更新审批模板并生成版本",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval template",
        "properties": {
          "approvalTemplateId": {
            "type": "integer",
            "required": true,
            "description": "approval template id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "template name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "template description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template status"
          },
          "version": {
            "type": "integer",
            "required": true,
            "description": "template version"
          },
          "steps": {
            "type": "array",
            "required": true,
            "description": "approval steps",
            "items": {
              "type": "object"
            }
          },
          "usageCount": {
            "type": "integer",
            "required": true,
            "description": "referencing rule count"
          },
          "usageRules": {
            "type": "array",
            "required": true,
            "description": "referencing rules",
            "items": {
              "type": "object",
              "properties": {
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "rule name"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "rule status"
                }
              }
            }
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "updatedAt": {
            "type": "string",
            "required": true,
            "description": "update time"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/approval-templates/{templateId}/versions",
    "description": "查看审批模板版本列表",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "none",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "array",
        "required": true,
        "description": "approval template versions",
        "items": {
          "type": "object",
          "properties": {
            "approvalTemplateId": {
              "type": "integer",
              "required": true,
              "description": "approval template id"
            },
            "name": {
              "type": "string",
              "required": true,
              "description": "template name"
            },
            "description": {
              "type": "string",
              "required": true,
              "description": "template description"
            },
            "status": {
              "type": "string",
              "required": true,
              "description": "template status"
            },
            "version": {
              "type": "integer",
              "required": true,
              "description": "template version"
            },
            "steps": {
              "type": "array",
              "required": true,
              "description": "approval steps",
              "items": {
                "type": "object"
              }
            },
            "usageCount": {
              "type": "integer",
              "required": true,
              "description": "referencing rule count"
            },
            "usageRules": {
              "type": "array",
              "required": true,
              "description": "referencing rules",
              "items": {
                "type": "object",
                "properties": {
                  "ruleId": {
                    "type": "integer",
                    "required": true,
                    "description": "rule id"
                  },
                  "name": {
                    "type": "string",
                    "required": true,
                    "description": "rule name"
                  },
                  "status": {
                    "type": "string",
                    "required": true,
                    "description": "rule status"
                  }
                }
              }
            },
            "createdByUserId": {
              "type": "integer",
              "required": true,
              "description": "creator user id"
            },
            "createdAt": {
              "type": "string",
              "required": true,
              "description": "creation time"
            },
            "updatedAt": {
              "type": "string",
              "required": true,
              "description": "update time"
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/approval-templates/{templateId}/versions/diff",
    "description": "比较审批模板版本差异",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "none",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval template version diff",
        "properties": {
          "approvalTemplateId": {
            "type": "integer",
            "required": true,
            "description": "approval template id"
          },
          "baseVersion": {
            "type": "integer",
            "required": true,
            "description": "base version"
          },
          "targetVersion": {
            "type": "integer",
            "required": true,
            "description": "target version"
          },
          "summary": {
            "type": "object",
            "required": true,
            "description": "diff summary counts",
            "properties": {
              "added": {
                "type": "integer",
                "required": true,
                "description": "added step count"
              },
              "removed": {
                "type": "integer",
                "required": true,
                "description": "removed step count"
              },
              "modified": {
                "type": "integer",
                "required": true,
                "description": "modified step count"
              },
              "unchanged": {
                "type": "integer",
                "required": true,
                "description": "unchanged step count"
              }
            }
          },
          "changes": {
            "type": "array",
            "required": true,
            "description": "step-level changes",
            "items": {
              "type": "object",
              "properties": {
                "changeType": {
                  "type": "string",
                  "required": true,
                  "description": "added, removed, or modified"
                },
                "stepId": {
                  "type": "string",
                  "required": true,
                  "description": "step id"
                },
                "baseStep": {
                  "type": "object",
                  "required": false,
                  "description": "base step snapshot"
                },
                "targetStep": {
                  "type": "object",
                  "required": false,
                  "description": "target step snapshot"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-templates/{templateId}/versions/{version}/rollback",
    "description": "回滚审批模板版本",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      },
      "version": {
        "type": "string",
        "required": true,
        "description": "version path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval template rollback result",
        "properties": {
          "approvalTemplateId": {
            "type": "integer",
            "required": true,
            "description": "approval template id"
          },
          "sourceVersion": {
            "type": "integer",
            "required": true,
            "description": "source version"
          },
          "newVersion": {
            "type": "integer",
            "required": true,
            "description": "new version"
          },
          "currentVersion": {
            "type": "integer",
            "required": true,
            "description": "current version"
          },
          "changeReason": {
            "type": "string",
            "required": true,
            "description": "change reason"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/approval-templates/{templateId}/versions/{version}",
    "description": "查看指定审批模板版本",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "none",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      },
      "version": {
        "type": "string",
        "required": true,
        "description": "version path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval template",
        "properties": {
          "approvalTemplateId": {
            "type": "integer",
            "required": true,
            "description": "approval template id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "template name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "template description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template status"
          },
          "version": {
            "type": "integer",
            "required": true,
            "description": "template version"
          },
          "steps": {
            "type": "array",
            "required": true,
            "description": "approval steps",
            "items": {
              "type": "object"
            }
          },
          "usageCount": {
            "type": "integer",
            "required": true,
            "description": "referencing rule count"
          },
          "usageRules": {
            "type": "array",
            "required": true,
            "description": "referencing rules",
            "items": {
              "type": "object",
              "properties": {
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "rule name"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "rule status"
                }
              }
            }
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "updatedAt": {
            "type": "string",
            "required": true,
            "description": "update time"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-templates/{templateId}/disable",
    "description": "停用审批模板",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "disabled approval template with usage impact",
        "properties": {
          "approvalTemplateId": {
            "type": "integer",
            "required": true,
            "description": "approval template id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "template name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "template description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template status"
          },
          "version": {
            "type": "integer",
            "required": true,
            "description": "template version"
          },
          "steps": {
            "type": "array",
            "required": true,
            "description": "approval steps",
            "items": {
              "type": "object"
            }
          },
          "usageCount": {
            "type": "integer",
            "required": true,
            "description": "referencing rule count"
          },
          "usageRules": {
            "type": "array",
            "required": true,
            "description": "referencing rules",
            "items": {
              "type": "object",
              "properties": {
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "rule name"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "rule status"
                }
              }
            }
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "updatedAt": {
            "type": "string",
            "required": true,
            "description": "update time"
          },
          "impact": {
            "type": "object",
            "required": true,
            "description": "usage impact",
            "properties": {
              "usageCount": {
                "type": "integer",
                "required": true,
                "description": "referencing rule count"
              },
              "usageRules": {
                "type": "array",
                "required": true,
                "description": "referencing rules",
                "items": {
                  "type": "object",
                  "properties": {
                    "ruleId": {
                      "type": "integer",
                      "required": true,
                      "description": "rule id"
                    },
                    "name": {
                      "type": "string",
                      "required": true,
                      "description": "rule name"
                    },
                    "status": {
                      "type": "string",
                      "required": true,
                      "description": "rule status"
                    }
                  }
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-templates/{templateId}/enable",
    "description": "启用审批模板",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "templateId": {
        "type": "string",
        "required": true,
        "description": "templateId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "enabled approval template with usage impact",
        "properties": {
          "approvalTemplateId": {
            "type": "integer",
            "required": true,
            "description": "approval template id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "template name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "template description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "template status"
          },
          "version": {
            "type": "integer",
            "required": true,
            "description": "template version"
          },
          "steps": {
            "type": "array",
            "required": true,
            "description": "approval steps",
            "items": {
              "type": "object"
            }
          },
          "usageCount": {
            "type": "integer",
            "required": true,
            "description": "referencing rule count"
          },
          "usageRules": {
            "type": "array",
            "required": true,
            "description": "referencing rules",
            "items": {
              "type": "object",
              "properties": {
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "name": {
                  "type": "string",
                  "required": true,
                  "description": "rule name"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "rule status"
                }
              }
            }
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "updatedAt": {
            "type": "string",
            "required": true,
            "description": "update time"
          },
          "impact": {
            "type": "object",
            "required": true,
            "description": "usage impact",
            "properties": {
              "usageCount": {
                "type": "integer",
                "required": true,
                "description": "referencing rule count"
              },
              "usageRules": {
                "type": "array",
                "required": true,
                "description": "referencing rules",
                "items": {
                  "type": "object",
                  "properties": {
                    "ruleId": {
                      "type": "integer",
                      "required": true,
                      "description": "rule id"
                    },
                    "name": {
                      "type": "string",
                      "required": true,
                      "description": "rule name"
                    },
                    "status": {
                      "type": "string",
                      "required": true,
                      "description": "rule status"
                    }
                  }
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-delegate-rules/batch-import",
    "description": "批量导入审批代理规则",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval delegate rule batch import result",
        "properties": {
          "imported": {
            "type": "integer",
            "required": true,
            "description": "imported row count"
          },
          "failed": {
            "type": "integer",
            "required": true,
            "description": "failed row count"
          },
          "results": {
            "type": "array",
            "required": true,
            "description": "row-level import results",
            "items": {
              "type": "object",
              "properties": {
                "rowNumber": {
                  "type": "integer",
                  "required": true,
                  "description": "source row number"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "row import status"
                },
                "reason": {
                  "type": "string",
                  "required": false,
                  "description": "failure reason"
                },
                "delegateRuleId": {
                  "type": "integer",
                  "required": false,
                  "description": "created delegate rule id"
                },
                "assigneeRole": {
                  "type": "string",
                  "required": false,
                  "description": "original assignee role"
                },
                "delegateRole": {
                  "type": "string",
                  "required": false,
                  "description": "delegate role"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "PUT",
    "path": "/api/v1/rules/approval-delegate-rules/{delegateRuleId}",
    "description": "更新审批代理规则",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "delegateRuleId": {
        "type": "string",
        "required": true,
        "description": "delegateRuleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval delegate rule",
        "properties": {
          "delegateRuleId": {
            "type": "integer",
            "required": true,
            "description": "approval delegate rule id"
          },
          "assigneeRole": {
            "type": "string",
            "required": true,
            "description": "original assignee role"
          },
          "delegateRole": {
            "type": "string",
            "required": true,
            "description": "delegate role"
          },
          "activeFrom": {
            "type": "string",
            "required": false,
            "description": "delegate active start time"
          },
          "activeTo": {
            "type": "string",
            "required": false,
            "description": "delegate active end time"
          },
          "activeWeekdays": {
            "type": "array",
            "required": true,
            "description": "active weekdays",
            "items": {
              "type": "string"
            }
          },
          "activeDates": {
            "type": "array",
            "required": true,
            "description": "active dates",
            "items": {
              "type": "string"
            }
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "delegate rule status"
          },
          "reason": {
            "type": "string",
            "required": false,
            "description": "change reason"
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-delegate-rules/{delegateRuleId}/disable",
    "description": "停用审批代理规则",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "delegateRuleId": {
        "type": "string",
        "required": true,
        "description": "delegateRuleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval delegate rule",
        "properties": {
          "delegateRuleId": {
            "type": "integer",
            "required": true,
            "description": "approval delegate rule id"
          },
          "assigneeRole": {
            "type": "string",
            "required": true,
            "description": "original assignee role"
          },
          "delegateRole": {
            "type": "string",
            "required": true,
            "description": "delegate role"
          },
          "activeFrom": {
            "type": "string",
            "required": false,
            "description": "delegate active start time"
          },
          "activeTo": {
            "type": "string",
            "required": false,
            "description": "delegate active end time"
          },
          "activeWeekdays": {
            "type": "array",
            "required": true,
            "description": "active weekdays",
            "items": {
              "type": "string"
            }
          },
          "activeDates": {
            "type": "array",
            "required": true,
            "description": "active dates",
            "items": {
              "type": "string"
            }
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "delegate rule status"
          },
          "reason": {
            "type": "string",
            "required": false,
            "description": "change reason"
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-delegate-rules/{delegateRuleId}/enable",
    "description": "启用审批代理规则",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "delegateRuleId": {
        "type": "string",
        "required": true,
        "description": "delegateRuleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approval delegate rule",
        "properties": {
          "delegateRuleId": {
            "type": "integer",
            "required": true,
            "description": "approval delegate rule id"
          },
          "assigneeRole": {
            "type": "string",
            "required": true,
            "description": "original assignee role"
          },
          "delegateRole": {
            "type": "string",
            "required": true,
            "description": "delegate role"
          },
          "activeFrom": {
            "type": "string",
            "required": false,
            "description": "delegate active start time"
          },
          "activeTo": {
            "type": "string",
            "required": false,
            "description": "delegate active end time"
          },
          "activeWeekdays": {
            "type": "array",
            "required": true,
            "description": "active weekdays",
            "items": {
              "type": "string"
            }
          },
          "activeDates": {
            "type": "array",
            "required": true,
            "description": "active dates",
            "items": {
              "type": "string"
            }
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "delegate rule status"
          },
          "reason": {
            "type": "string",
            "required": false,
            "description": "change reason"
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/review-submissions",
    "description": "提交规则评审",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "rule submitted for review",
        "properties": {
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "rule name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "rule description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "rule status"
          },
          "definition": {
            "type": "object",
            "required": true,
            "description": "rule definition"
          },
          "versionId": {
            "type": "integer",
            "required": true,
            "description": "current rule version id"
          },
          "scheduleEnabled": {
            "type": "boolean",
            "required": true,
            "description": "whether schedule is enabled"
          },
          "scheduleIntervalSeconds": {
            "type": "integer",
            "required": false,
            "description": "schedule interval in seconds"
          },
          "nextRunAt": {
            "type": "string",
            "required": true,
            "description": "next scheduled run time"
          },
          "failureCount": {
            "type": "integer",
            "required": true,
            "description": "current schedule failure count"
          },
          "maxRetryCount": {
            "type": "integer",
            "required": true,
            "description": "maximum schedule retry count"
          },
          "scheduleInput": {
            "type": "object",
            "required": true,
            "description": "scheduled execution input"
          },
          "reviewComment": {
            "type": "string",
            "required": false,
            "description": "review submission comment"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/approvals",
    "description": "审批规则发布",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "approved and published rule",
        "properties": {
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "rule name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "rule description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "rule status"
          },
          "definition": {
            "type": "object",
            "required": true,
            "description": "rule definition"
          },
          "versionId": {
            "type": "integer",
            "required": true,
            "description": "current rule version id"
          },
          "scheduleEnabled": {
            "type": "boolean",
            "required": true,
            "description": "whether schedule is enabled"
          },
          "scheduleIntervalSeconds": {
            "type": "integer",
            "required": false,
            "description": "schedule interval in seconds"
          },
          "nextRunAt": {
            "type": "string",
            "required": true,
            "description": "next scheduled run time"
          },
          "failureCount": {
            "type": "integer",
            "required": true,
            "description": "current schedule failure count"
          },
          "maxRetryCount": {
            "type": "integer",
            "required": true,
            "description": "maximum schedule retry count"
          },
          "scheduleInput": {
            "type": "object",
            "required": true,
            "description": "scheduled execution input"
          },
          "approvalComment": {
            "type": "string",
            "required": false,
            "description": "approval comment"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/runs",
    "description": "执行规则运行",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "rule production run",
        "properties": {
          "debugRunId": {
            "type": "integer",
            "required": false,
            "description": "production run id returned by execute endpoint"
          },
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "versionId": {
            "type": "integer",
            "required": true,
            "description": "rule version id"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "run status"
          },
          "runType": {
            "type": "string",
            "required": true,
            "description": "run type"
          },
          "triggeredByUserId": {
            "type": "integer",
            "required": false,
            "description": "triggering user id"
          },
          "durationMs": {
            "type": "integer",
            "required": false,
            "description": "duration in milliseconds"
          },
          "errorMessage": {
            "type": "string",
            "required": false,
            "description": "run error message"
          },
          "matched": {
            "type": "boolean",
            "required": false,
            "description": "condition match result"
          },
          "input": {
            "type": "object",
            "required": false,
            "description": "run input"
          },
          "output": {
            "type": "object",
            "required": true,
            "description": "run output"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/{ruleId}/runs",
    "description": "查询规则运行历史",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "none",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged rule runs",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "page items",
            "items": {
              "type": "object",
              "properties": {
                "runId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule run id"
                },
                "debugRunId": {
                  "type": "integer",
                  "required": false,
                  "description": "production run id returned by execute endpoint"
                },
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "versionId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule version id"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "run status"
                },
                "runType": {
                  "type": "string",
                  "required": true,
                  "description": "run type"
                },
                "triggeredByUserId": {
                  "type": "integer",
                  "required": false,
                  "description": "triggering user id"
                },
                "durationMs": {
                  "type": "integer",
                  "required": false,
                  "description": "duration in milliseconds"
                },
                "errorMessage": {
                  "type": "string",
                  "required": false,
                  "description": "run error message"
                },
                "matched": {
                  "type": "boolean",
                  "required": false,
                  "description": "condition match result"
                },
                "input": {
                  "type": "object",
                  "required": false,
                  "description": "run input"
                },
                "output": {
                  "type": "object",
                  "required": true,
                  "description": "run output"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total items"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology",
    "description": "查看子流程拓扑",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "none",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      },
      "runId": {
        "type": "string",
        "required": true,
        "description": "runId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "rule subprocess topology",
        "properties": {
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "runId": {
            "type": "integer",
            "required": true,
            "description": "run id"
          },
          "nodes": {
            "type": "array",
            "required": true,
            "description": "topology nodes",
            "items": {
              "type": "object",
              "properties": {
                "runId": {
                  "type": "integer",
                  "required": true,
                  "description": "run id"
                },
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "versionId": {
                  "type": "integer",
                  "required": true,
                  "description": "version id"
                },
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "topology role"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "run status"
                },
                "runType": {
                  "type": "string",
                  "required": true,
                  "description": "run type"
                },
                "durationMs": {
                  "type": "integer",
                  "required": false,
                  "description": "duration in milliseconds"
                },
                "errorMessage": {
                  "type": "string",
                  "required": false,
                  "description": "run error message"
                }
              }
            }
          },
          "edges": {
            "type": "array",
            "required": true,
            "description": "topology edges",
            "items": {
              "type": "object",
              "properties": {
                "operationLogId": {
                  "type": "integer",
                  "required": true,
                  "description": "operation log id"
                },
                "parentRuleId": {
                  "type": "integer",
                  "required": false,
                  "description": "parent rule id"
                },
                "parentRunId": {
                  "type": "integer",
                  "required": false,
                  "description": "parent run id"
                },
                "nodeId": {
                  "type": "string",
                  "required": true,
                  "description": "node id"
                },
                "subprocessRuleId": {
                  "type": "integer",
                  "required": false,
                  "description": "subprocess rule id"
                },
                "subprocessRunId": {
                  "type": "integer",
                  "required": false,
                  "description": "subprocess run id"
                },
                "subprocessVersionId": {
                  "type": "integer",
                  "required": false,
                  "description": "subprocess version id"
                },
                "result": {
                  "type": "string",
                  "required": true,
                  "description": "subprocess result"
                },
                "errorMessage": {
                  "type": "string",
                  "required": false,
                  "description": "subprocess error message"
                },
                "createdAt": {
                  "type": "string",
                  "required": true,
                  "description": "operation creation time"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/{ruleId}/approval-records",
    "description": "查询规则审批记录",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "none",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged approval records for rule",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "page items",
            "items": {
              "type": "object",
              "properties": {
                "approvalRecordId": {
                  "type": "integer",
                  "required": true,
                  "description": "approval record id"
                },
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "runId": {
                  "type": "integer",
                  "required": true,
                  "description": "run id"
                },
                "nodeId": {
                  "type": "string",
                  "required": true,
                  "description": "node id"
                },
                "assigneeRole": {
                  "type": "string",
                  "required": false,
                  "description": "assignee role"
                },
                "delegateRole": {
                  "type": "string",
                  "required": false,
                  "description": "delegate role"
                },
                "assigneeUsers": {
                  "type": "array",
                  "required": true,
                  "description": "assignee users",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user id"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "display name"
                      },
                      "role": {
                        "type": "string",
                        "required": true,
                        "description": "role"
                      }
                    }
                  }
                },
                "delegateUsers": {
                  "type": "array",
                  "required": true,
                  "description": "delegate users",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user id"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "display name"
                      },
                      "role": {
                        "type": "string",
                        "required": true,
                        "description": "role"
                      }
                    }
                  }
                },
                "delegateActiveFrom": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active start time"
                },
                "delegateActiveTo": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active end time"
                },
                "handledByDelegate": {
                  "type": "boolean",
                  "required": true,
                  "description": "whether handled by delegate"
                },
                "approvalTitle": {
                  "type": "string",
                  "required": false,
                  "description": "approval title"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "approval status"
                },
                "createdByUserId": {
                  "type": "integer",
                  "required": true,
                  "description": "creator user id"
                },
                "approvedByUserId": {
                  "type": "integer",
                  "required": false,
                  "description": "approver user id"
                },
                "approvalComment": {
                  "type": "string",
                  "required": false,
                  "description": "approval comment"
                },
                "approvedAt": {
                  "type": "string",
                  "required": false,
                  "description": "approval time"
                },
                "createdAt": {
                  "type": "string",
                  "required": true,
                  "description": "creation time"
                },
                "slaHours": {
                  "type": "integer",
                  "required": false,
                  "description": "SLA hours"
                },
                "remindCount": {
                  "type": "integer",
                  "required": true,
                  "description": "reminder count"
                },
                "lastRemindedAt": {
                  "type": "string",
                  "required": false,
                  "description": "last reminder time"
                },
                "slaDueAt": {
                  "type": "string",
                  "required": false,
                  "description": "SLA due time"
                },
                "isOverdue": {
                  "type": "boolean",
                  "required": true,
                  "description": "whether approval is overdue"
                },
                "approvalGroupKey": {
                  "type": "string",
                  "required": true,
                  "description": "approval group key"
                },
                "approvalMode": {
                  "type": "string",
                  "required": true,
                  "description": "approval mode"
                },
                "assigneeRoles": {
                  "type": "array",
                  "required": true,
                  "description": "assignee roles",
                  "items": {
                    "type": "string"
                  }
                },
                "approvalGroupTotalCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group total count"
                },
                "approvalGroupApprovedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group approved count"
                },
                "approvalGroupPendingCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group pending count"
                },
                "approvalGroupRejectedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group rejected count"
                },
                "approvalGroupClosedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group closed count"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total items"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/approval-records",
    "description": "查询全局审批记录",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "none",
    "pathParams": {},
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged approval records by status",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "page items",
            "items": {
              "type": "object",
              "properties": {
                "approvalRecordId": {
                  "type": "integer",
                  "required": true,
                  "description": "approval record id"
                },
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "runId": {
                  "type": "integer",
                  "required": true,
                  "description": "run id"
                },
                "nodeId": {
                  "type": "string",
                  "required": true,
                  "description": "node id"
                },
                "assigneeRole": {
                  "type": "string",
                  "required": false,
                  "description": "assignee role"
                },
                "delegateRole": {
                  "type": "string",
                  "required": false,
                  "description": "delegate role"
                },
                "assigneeUsers": {
                  "type": "array",
                  "required": true,
                  "description": "assignee users",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user id"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "display name"
                      },
                      "role": {
                        "type": "string",
                        "required": true,
                        "description": "role"
                      }
                    }
                  }
                },
                "delegateUsers": {
                  "type": "array",
                  "required": true,
                  "description": "delegate users",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user id"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "display name"
                      },
                      "role": {
                        "type": "string",
                        "required": true,
                        "description": "role"
                      }
                    }
                  }
                },
                "delegateActiveFrom": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active start time"
                },
                "delegateActiveTo": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active end time"
                },
                "handledByDelegate": {
                  "type": "boolean",
                  "required": true,
                  "description": "whether handled by delegate"
                },
                "approvalTitle": {
                  "type": "string",
                  "required": false,
                  "description": "approval title"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "approval status"
                },
                "createdByUserId": {
                  "type": "integer",
                  "required": true,
                  "description": "creator user id"
                },
                "approvedByUserId": {
                  "type": "integer",
                  "required": false,
                  "description": "approver user id"
                },
                "approvalComment": {
                  "type": "string",
                  "required": false,
                  "description": "approval comment"
                },
                "approvedAt": {
                  "type": "string",
                  "required": false,
                  "description": "approval time"
                },
                "createdAt": {
                  "type": "string",
                  "required": true,
                  "description": "creation time"
                },
                "slaHours": {
                  "type": "integer",
                  "required": false,
                  "description": "SLA hours"
                },
                "remindCount": {
                  "type": "integer",
                  "required": true,
                  "description": "reminder count"
                },
                "lastRemindedAt": {
                  "type": "string",
                  "required": false,
                  "description": "last reminder time"
                },
                "slaDueAt": {
                  "type": "string",
                  "required": false,
                  "description": "SLA due time"
                },
                "isOverdue": {
                  "type": "boolean",
                  "required": true,
                  "description": "whether approval is overdue"
                },
                "approvalGroupKey": {
                  "type": "string",
                  "required": true,
                  "description": "approval group key"
                },
                "approvalMode": {
                  "type": "string",
                  "required": true,
                  "description": "approval mode"
                },
                "assigneeRoles": {
                  "type": "array",
                  "required": true,
                  "description": "assignee roles",
                  "items": {
                    "type": "string"
                  }
                },
                "approvalGroupTotalCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group total count"
                },
                "approvalGroupApprovedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group approved count"
                },
                "approvalGroupPendingCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group pending count"
                },
                "approvalGroupRejectedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group rejected count"
                },
                "approvalGroupClosedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group closed count"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total items"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/actions",
    "description": "提交审批动作",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      },
      "approvalRecordId": {
        "type": "string",
        "required": true,
        "description": "approvalRecordId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "handled approval record",
        "properties": {
          "approvalRecordId": {
            "type": "integer",
            "required": true,
            "description": "approval record id"
          },
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "runId": {
            "type": "integer",
            "required": true,
            "description": "run id"
          },
          "nodeId": {
            "type": "string",
            "required": true,
            "description": "node id"
          },
          "assigneeRole": {
            "type": "string",
            "required": false,
            "description": "assignee role"
          },
          "delegateRole": {
            "type": "string",
            "required": false,
            "description": "delegate role"
          },
          "assigneeUsers": {
            "type": "array",
            "required": true,
            "description": "assignee users",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": true,
                  "description": "user id"
                },
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "username"
                },
                "displayName": {
                  "type": "string",
                  "required": true,
                  "description": "display name"
                },
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "role"
                }
              }
            }
          },
          "delegateUsers": {
            "type": "array",
            "required": true,
            "description": "delegate users",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": true,
                  "description": "user id"
                },
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "username"
                },
                "displayName": {
                  "type": "string",
                  "required": true,
                  "description": "display name"
                },
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "role"
                }
              }
            }
          },
          "delegateActiveFrom": {
            "type": "string",
            "required": false,
            "description": "delegate active start time"
          },
          "delegateActiveTo": {
            "type": "string",
            "required": false,
            "description": "delegate active end time"
          },
          "handledByDelegate": {
            "type": "boolean",
            "required": true,
            "description": "whether handled by delegate"
          },
          "approvalTitle": {
            "type": "string",
            "required": false,
            "description": "approval title"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "approval status"
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "approvedByUserId": {
            "type": "integer",
            "required": false,
            "description": "approver user id"
          },
          "approvalComment": {
            "type": "string",
            "required": false,
            "description": "approval comment"
          },
          "approvedAt": {
            "type": "string",
            "required": false,
            "description": "approval time"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "slaHours": {
            "type": "integer",
            "required": false,
            "description": "SLA hours"
          },
          "remindCount": {
            "type": "integer",
            "required": true,
            "description": "reminder count"
          },
          "lastRemindedAt": {
            "type": "string",
            "required": false,
            "description": "last reminder time"
          },
          "slaDueAt": {
            "type": "string",
            "required": false,
            "description": "SLA due time"
          },
          "isOverdue": {
            "type": "boolean",
            "required": true,
            "description": "whether approval is overdue"
          },
          "approvalGroupKey": {
            "type": "string",
            "required": true,
            "description": "approval group key"
          },
          "approvalMode": {
            "type": "string",
            "required": true,
            "description": "approval mode"
          },
          "assigneeRoles": {
            "type": "array",
            "required": true,
            "description": "assignee roles",
            "items": {
              "type": "string"
            }
          },
          "approvalGroupTotalCount": {
            "type": "integer",
            "required": true,
            "description": "approval group total count"
          },
          "approvalGroupApprovedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group approved count"
          },
          "approvalGroupPendingCount": {
            "type": "integer",
            "required": true,
            "description": "approval group pending count"
          },
          "approvalGroupRejectedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group rejected count"
          },
          "approvalGroupClosedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group closed count"
          },
          "supplementStatus": {
            "type": "string",
            "required": false,
            "description": "supplement status after rejection"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplements",
    "description": "补充审批材料",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      },
      "approvalRecordId": {
        "type": "string",
        "required": true,
        "description": "approvalRecordId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "supplemented approval record",
        "properties": {
          "approvalRecordId": {
            "type": "integer",
            "required": true,
            "description": "approval record id"
          },
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "runId": {
            "type": "integer",
            "required": true,
            "description": "run id"
          },
          "nodeId": {
            "type": "string",
            "required": true,
            "description": "node id"
          },
          "assigneeRole": {
            "type": "string",
            "required": false,
            "description": "assignee role"
          },
          "delegateRole": {
            "type": "string",
            "required": false,
            "description": "delegate role"
          },
          "assigneeUsers": {
            "type": "array",
            "required": true,
            "description": "assignee users",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": true,
                  "description": "user id"
                },
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "username"
                },
                "displayName": {
                  "type": "string",
                  "required": true,
                  "description": "display name"
                },
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "role"
                }
              }
            }
          },
          "delegateUsers": {
            "type": "array",
            "required": true,
            "description": "delegate users",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": true,
                  "description": "user id"
                },
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "username"
                },
                "displayName": {
                  "type": "string",
                  "required": true,
                  "description": "display name"
                },
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "role"
                }
              }
            }
          },
          "delegateActiveFrom": {
            "type": "string",
            "required": false,
            "description": "delegate active start time"
          },
          "delegateActiveTo": {
            "type": "string",
            "required": false,
            "description": "delegate active end time"
          },
          "handledByDelegate": {
            "type": "boolean",
            "required": true,
            "description": "whether handled by delegate"
          },
          "approvalTitle": {
            "type": "string",
            "required": false,
            "description": "approval title"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "approval status"
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "approvedByUserId": {
            "type": "integer",
            "required": false,
            "description": "approver user id"
          },
          "approvalComment": {
            "type": "string",
            "required": false,
            "description": "approval comment"
          },
          "approvedAt": {
            "type": "string",
            "required": false,
            "description": "approval time"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "slaHours": {
            "type": "integer",
            "required": false,
            "description": "SLA hours"
          },
          "remindCount": {
            "type": "integer",
            "required": true,
            "description": "reminder count"
          },
          "lastRemindedAt": {
            "type": "string",
            "required": false,
            "description": "last reminder time"
          },
          "slaDueAt": {
            "type": "string",
            "required": false,
            "description": "SLA due time"
          },
          "isOverdue": {
            "type": "boolean",
            "required": true,
            "description": "whether approval is overdue"
          },
          "approvalGroupKey": {
            "type": "string",
            "required": true,
            "description": "approval group key"
          },
          "approvalMode": {
            "type": "string",
            "required": true,
            "description": "approval mode"
          },
          "assigneeRoles": {
            "type": "array",
            "required": true,
            "description": "assignee roles",
            "items": {
              "type": "string"
            }
          },
          "approvalGroupTotalCount": {
            "type": "integer",
            "required": true,
            "description": "approval group total count"
          },
          "approvalGroupApprovedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group approved count"
          },
          "approvalGroupPendingCount": {
            "type": "integer",
            "required": true,
            "description": "approval group pending count"
          },
          "approvalGroupRejectedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group rejected count"
          },
          "approvalGroupClosedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group closed count"
          },
          "sourceRejectedApprovalRecordId": {
            "type": "integer",
            "required": true,
            "description": "source rejected approval record id"
          },
          "newApprovalRecordId": {
            "type": "integer",
            "required": true,
            "description": "new pending approval record id"
          },
          "supplementStatus": {
            "type": "string",
            "required": true,
            "description": "supplement status"
          },
          "evidenceUrl": {
            "type": "string",
            "required": false,
            "description": "supplement evidence URL"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/reminders",
    "description": "发送审批提醒",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      },
      "approvalRecordId": {
        "type": "string",
        "required": true,
        "description": "approvalRecordId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "reminded approval record",
        "properties": {
          "approvalRecordId": {
            "type": "integer",
            "required": true,
            "description": "approval record id"
          },
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "runId": {
            "type": "integer",
            "required": true,
            "description": "run id"
          },
          "nodeId": {
            "type": "string",
            "required": true,
            "description": "node id"
          },
          "assigneeRole": {
            "type": "string",
            "required": false,
            "description": "assignee role"
          },
          "delegateRole": {
            "type": "string",
            "required": false,
            "description": "delegate role"
          },
          "assigneeUsers": {
            "type": "array",
            "required": true,
            "description": "assignee users",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": true,
                  "description": "user id"
                },
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "username"
                },
                "displayName": {
                  "type": "string",
                  "required": true,
                  "description": "display name"
                },
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "role"
                }
              }
            }
          },
          "delegateUsers": {
            "type": "array",
            "required": true,
            "description": "delegate users",
            "items": {
              "type": "object",
              "properties": {
                "userId": {
                  "type": "integer",
                  "required": true,
                  "description": "user id"
                },
                "username": {
                  "type": "string",
                  "required": true,
                  "description": "username"
                },
                "displayName": {
                  "type": "string",
                  "required": true,
                  "description": "display name"
                },
                "role": {
                  "type": "string",
                  "required": true,
                  "description": "role"
                }
              }
            }
          },
          "delegateActiveFrom": {
            "type": "string",
            "required": false,
            "description": "delegate active start time"
          },
          "delegateActiveTo": {
            "type": "string",
            "required": false,
            "description": "delegate active end time"
          },
          "handledByDelegate": {
            "type": "boolean",
            "required": true,
            "description": "whether handled by delegate"
          },
          "approvalTitle": {
            "type": "string",
            "required": false,
            "description": "approval title"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "approval status"
          },
          "createdByUserId": {
            "type": "integer",
            "required": true,
            "description": "creator user id"
          },
          "approvedByUserId": {
            "type": "integer",
            "required": false,
            "description": "approver user id"
          },
          "approvalComment": {
            "type": "string",
            "required": false,
            "description": "approval comment"
          },
          "approvedAt": {
            "type": "string",
            "required": false,
            "description": "approval time"
          },
          "createdAt": {
            "type": "string",
            "required": true,
            "description": "creation time"
          },
          "slaHours": {
            "type": "integer",
            "required": false,
            "description": "SLA hours"
          },
          "remindCount": {
            "type": "integer",
            "required": true,
            "description": "reminder count"
          },
          "lastRemindedAt": {
            "type": "string",
            "required": false,
            "description": "last reminder time"
          },
          "slaDueAt": {
            "type": "string",
            "required": false,
            "description": "SLA due time"
          },
          "isOverdue": {
            "type": "boolean",
            "required": true,
            "description": "whether approval is overdue"
          },
          "approvalGroupKey": {
            "type": "string",
            "required": true,
            "description": "approval group key"
          },
          "approvalMode": {
            "type": "string",
            "required": true,
            "description": "approval mode"
          },
          "assigneeRoles": {
            "type": "array",
            "required": true,
            "description": "assignee roles",
            "items": {
              "type": "string"
            }
          },
          "approvalGroupTotalCount": {
            "type": "integer",
            "required": true,
            "description": "approval group total count"
          },
          "approvalGroupApprovedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group approved count"
          },
          "approvalGroupPendingCount": {
            "type": "integer",
            "required": true,
            "description": "approval group pending count"
          },
          "approvalGroupRejectedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group rejected count"
          },
          "approvalGroupClosedCount": {
            "type": "integer",
            "required": true,
            "description": "approval group closed count"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/approval-records/batch-actions",
    "description": "批量处理审批记录",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "application/json",
    "pathParams": {},
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "batch approval handling result",
        "properties": {
          "action": {
            "type": "string",
            "required": true,
            "description": "batch approval action"
          },
          "requestedCount": {
            "type": "integer",
            "required": true,
            "description": "requested count"
          },
          "succeededCount": {
            "type": "integer",
            "required": true,
            "description": "succeeded count"
          },
          "failedCount": {
            "type": "integer",
            "required": true,
            "description": "failed count"
          },
          "items": {
            "type": "array",
            "required": true,
            "description": "batch results",
            "items": {
              "type": "object",
              "properties": {
                "approvalRecordId": {
                  "type": "integer",
                  "required": true,
                  "description": "approval record id"
                },
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "runId": {
                  "type": "integer",
                  "required": true,
                  "description": "run id"
                },
                "nodeId": {
                  "type": "string",
                  "required": true,
                  "description": "node id"
                },
                "assigneeRole": {
                  "type": "string",
                  "required": false,
                  "description": "assignee role"
                },
                "delegateRole": {
                  "type": "string",
                  "required": false,
                  "description": "delegate role"
                },
                "assigneeUsers": {
                  "type": "array",
                  "required": true,
                  "description": "assignee users",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user id"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "display name"
                      },
                      "role": {
                        "type": "string",
                        "required": true,
                        "description": "role"
                      }
                    }
                  }
                },
                "delegateUsers": {
                  "type": "array",
                  "required": true,
                  "description": "delegate users",
                  "items": {
                    "type": "object",
                    "properties": {
                      "userId": {
                        "type": "integer",
                        "required": true,
                        "description": "user id"
                      },
                      "username": {
                        "type": "string",
                        "required": true,
                        "description": "username"
                      },
                      "displayName": {
                        "type": "string",
                        "required": true,
                        "description": "display name"
                      },
                      "role": {
                        "type": "string",
                        "required": true,
                        "description": "role"
                      }
                    }
                  }
                },
                "delegateActiveFrom": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active start time"
                },
                "delegateActiveTo": {
                  "type": "string",
                  "required": false,
                  "description": "delegate active end time"
                },
                "handledByDelegate": {
                  "type": "boolean",
                  "required": true,
                  "description": "whether handled by delegate"
                },
                "approvalTitle": {
                  "type": "string",
                  "required": false,
                  "description": "approval title"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "approval status"
                },
                "createdByUserId": {
                  "type": "integer",
                  "required": true,
                  "description": "creator user id"
                },
                "approvedByUserId": {
                  "type": "integer",
                  "required": false,
                  "description": "approver user id"
                },
                "approvalComment": {
                  "type": "string",
                  "required": false,
                  "description": "approval comment"
                },
                "approvedAt": {
                  "type": "string",
                  "required": false,
                  "description": "approval time"
                },
                "createdAt": {
                  "type": "string",
                  "required": true,
                  "description": "creation time"
                },
                "slaHours": {
                  "type": "integer",
                  "required": false,
                  "description": "SLA hours"
                },
                "remindCount": {
                  "type": "integer",
                  "required": true,
                  "description": "reminder count"
                },
                "lastRemindedAt": {
                  "type": "string",
                  "required": false,
                  "description": "last reminder time"
                },
                "slaDueAt": {
                  "type": "string",
                  "required": false,
                  "description": "SLA due time"
                },
                "isOverdue": {
                  "type": "boolean",
                  "required": true,
                  "description": "whether approval is overdue"
                },
                "approvalGroupKey": {
                  "type": "string",
                  "required": true,
                  "description": "approval group key"
                },
                "approvalMode": {
                  "type": "string",
                  "required": true,
                  "description": "approval mode"
                },
                "assigneeRoles": {
                  "type": "array",
                  "required": true,
                  "description": "assignee roles",
                  "items": {
                    "type": "string"
                  }
                },
                "approvalGroupTotalCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group total count"
                },
                "approvalGroupApprovedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group approved count"
                },
                "approvalGroupPendingCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group pending count"
                },
                "approvalGroupRejectedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group rejected count"
                },
                "approvalGroupClosedCount": {
                  "type": "integer",
                  "required": true,
                  "description": "approval group closed count"
                },
                "result": {
                  "type": "string",
                  "required": true,
                  "description": "batch item result"
                },
                "errorMessage": {
                  "type": "string",
                  "required": false,
                  "description": "batch item error message"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/{ruleId}/action-executions",
    "description": "查询动作执行记录",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "none",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {
      "page": {
        "type": "integer",
        "required": false,
        "description": "page number"
      },
      "pageSize": {
        "type": "integer",
        "required": false,
        "description": "page size"
      }
    },
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "paged rule action executions",
        "properties": {
          "items": {
            "type": "array",
            "required": true,
            "description": "page items",
            "items": {
              "type": "object",
              "properties": {
                "actionExecutionId": {
                  "type": "integer",
                  "required": true,
                  "description": "action execution id"
                },
                "sourceActionExecutionId": {
                  "type": "integer",
                  "required": false,
                  "description": "source action execution id"
                },
                "ruleId": {
                  "type": "integer",
                  "required": true,
                  "description": "rule id"
                },
                "runId": {
                  "type": "integer",
                  "required": true,
                  "description": "run id"
                },
                "nodeId": {
                  "type": "string",
                  "required": true,
                  "description": "node id"
                },
                "actionType": {
                  "type": "string",
                  "required": true,
                  "description": "action type"
                },
                "status": {
                  "type": "string",
                  "required": true,
                  "description": "action status"
                },
                "attempt": {
                  "type": "integer",
                  "required": true,
                  "description": "attempt count"
                },
                "maxRetryCount": {
                  "type": "integer",
                  "required": true,
                  "description": "max retry count"
                },
                "endpoint": {
                  "type": "string",
                  "required": false,
                  "description": "webhook endpoint"
                },
                "idempotencyKey": {
                  "type": "string",
                  "required": false,
                  "description": "idempotency key"
                },
                "nextRetryAt": {
                  "type": "string",
                  "required": true,
                  "description": "next retry time"
                },
                "errorMessage": {
                  "type": "string",
                  "required": false,
                  "description": "error message"
                },
                "metadata": {
                  "type": "object",
                  "required": true,
                  "description": "execution metadata"
                }
              }
            }
          },
          "page": {
            "type": "integer",
            "required": true,
            "description": "current page number"
          },
          "pageSize": {
            "type": "integer",
            "required": true,
            "description": "page size"
          },
          "total": {
            "type": "integer",
            "required": true,
            "description": "total items"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "GET",
    "path": "/api/v1/rules/{ruleId}/metrics",
    "description": "查询规则运行指标",
    "module": "rule-engine",
    "securityIntent": "rule:debug",
    "contentType": "none",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {},
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "rule run metrics",
        "properties": {
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "totalRuns": {
            "type": "integer",
            "required": true,
            "description": "total run count"
          },
          "succeededRuns": {
            "type": "integer",
            "required": true,
            "description": "succeeded run count"
          },
          "failedRuns": {
            "type": "integer",
            "required": true,
            "description": "failed run count"
          },
          "successRate": {
            "type": "number",
            "required": true,
            "description": "success rate"
          },
          "averageDurationMs": {
            "type": "number",
            "required": true,
            "description": "average duration in milliseconds"
          },
          "lastStatus": {
            "type": "string",
            "required": false,
            "description": "last run status"
          },
          "lastErrorMessage": {
            "type": "string",
            "required": false,
            "description": "last error message"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      401,
      403,
      404
    ]
  },
  {
    "method": "PUT",
    "path": "/api/v1/rules/{ruleId}/schedule",
    "description": "配置规则调度",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "rule schedule configuration",
        "properties": {
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "rule name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "rule description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "rule status"
          },
          "definition": {
            "type": "object",
            "required": true,
            "description": "rule definition"
          },
          "versionId": {
            "type": "integer",
            "required": true,
            "description": "current rule version id"
          },
          "scheduleEnabled": {
            "type": "boolean",
            "required": true,
            "description": "whether schedule is enabled"
          },
          "scheduleIntervalSeconds": {
            "type": "integer",
            "required": false,
            "description": "schedule interval in seconds"
          },
          "nextRunAt": {
            "type": "string",
            "required": true,
            "description": "next scheduled run time"
          },
          "failureCount": {
            "type": "integer",
            "required": true,
            "description": "current schedule failure count"
          },
          "maxRetryCount": {
            "type": "integer",
            "required": true,
            "description": "maximum schedule retry count"
          },
          "scheduleInput": {
            "type": "object",
            "required": true,
            "description": "scheduled execution input"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/schedule/retry",
    "description": "重试规则调度",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "rule schedule retry state",
        "properties": {
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "name": {
            "type": "string",
            "required": true,
            "description": "rule name"
          },
          "description": {
            "type": "string",
            "required": true,
            "description": "rule description"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "rule status"
          },
          "definition": {
            "type": "object",
            "required": true,
            "description": "rule definition"
          },
          "versionId": {
            "type": "integer",
            "required": true,
            "description": "current rule version id"
          },
          "scheduleEnabled": {
            "type": "boolean",
            "required": true,
            "description": "whether schedule is enabled"
          },
          "scheduleIntervalSeconds": {
            "type": "integer",
            "required": false,
            "description": "schedule interval in seconds"
          },
          "nextRunAt": {
            "type": "string",
            "required": true,
            "description": "next scheduled run time"
          },
          "failureCount": {
            "type": "integer",
            "required": true,
            "description": "current schedule failure count"
          },
          "maxRetryCount": {
            "type": "integer",
            "required": true,
            "description": "maximum schedule retry count"
          },
          "scheduleInput": {
            "type": "object",
            "required": true,
            "description": "scheduled execution input"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/action-executions/{actionExecutionId}/retry",
    "description": "重试单个动作执行",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      },
      "actionExecutionId": {
        "type": "string",
        "required": true,
        "description": "actionExecutionId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "retried rule action execution",
        "properties": {
          "actionExecutionId": {
            "type": "integer",
            "required": true,
            "description": "action execution id"
          },
          "sourceActionExecutionId": {
            "type": "integer",
            "required": false,
            "description": "source action execution id"
          },
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "runId": {
            "type": "integer",
            "required": true,
            "description": "run id"
          },
          "nodeId": {
            "type": "string",
            "required": true,
            "description": "node id"
          },
          "actionType": {
            "type": "string",
            "required": true,
            "description": "action type"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "action status"
          },
          "attempt": {
            "type": "integer",
            "required": true,
            "description": "attempt count"
          },
          "maxRetryCount": {
            "type": "integer",
            "required": true,
            "description": "max retry count"
          },
          "endpoint": {
            "type": "string",
            "required": false,
            "description": "webhook endpoint"
          },
          "idempotencyKey": {
            "type": "string",
            "required": false,
            "description": "idempotency key"
          },
          "nextRetryAt": {
            "type": "string",
            "required": true,
            "description": "next retry time"
          },
          "errorMessage": {
            "type": "string",
            "required": false,
            "description": "error message"
          },
          "metadata": {
            "type": "object",
            "required": true,
            "description": "execution metadata"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/rules/{ruleId}/action-executions/batch",
    "description": "批量触发动作执行",
    "module": "rule-engine",
    "securityIntent": "rule:manage",
    "contentType": "application/json",
    "pathParams": {
      "ruleId": {
        "type": "string",
        "required": true,
        "description": "ruleId path parameter"
      }
    },
    "queryParams": {},
    "headers": {
      "Authorization": {
        "type": "string",
        "required": true,
        "description": "Bearer JWT; optional only for public share POST endpoints"
      }
    },
    "requestBody": {
      "payload": {
        "type": "object",
        "required": false,
        "description": "business request body; fields follow backend controller contract"
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "batch action execution handling result",
        "properties": {
          "operation": {
            "type": "string",
            "required": true,
            "description": "batch operation"
          },
          "ruleId": {
            "type": "integer",
            "required": true,
            "description": "rule id"
          },
          "requestedCount": {
            "type": "integer",
            "required": true,
            "description": "requested count"
          },
          "succeededCount": {
            "type": "integer",
            "required": true,
            "description": "succeeded count"
          },
          "failedCount": {
            "type": "integer",
            "required": true,
            "description": "failed count"
          },
          "items": {
            "type": "array",
            "required": true,
            "description": "batch results",
            "items": {
              "type": "object",
              "properties": {
                "actionExecutionId": {
                  "type": "integer",
                  "required": true,
                  "description": "action execution id"
                },
                "result": {
                  "type": "string",
                  "required": true,
                  "description": "batch item result"
                },
                "handledActionExecutionId": {
                  "type": "integer",
                  "required": false,
                  "description": "handled action execution id"
                },
                "status": {
                  "type": "string",
                  "required": false,
                  "description": "handled status"
                },
                "errorMessage": {
                  "type": "string",
                  "required": false,
                  "description": "batch item error message"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      401,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/share-links/{shareToken}/report",
    "description": "external visitor reads authorized shared report detail",
    "module": "permission-collaboration",
    "securityIntent": "public-share-link-controlled",
    "contentType": "application/json",
    "pathParams": {
      "shareToken": {
        "type": "string",
        "required": true,
        "description": "share token"
      }
    },
    "queryParams": {},
    "headers": {
      "X-Request-Id": {
        "type": "string",
        "required": false,
        "description": "request trace id"
      }
    },
    "requestBody": {
      "type": "object",
      "required": false,
      "description": "share access context and optional password",
      "properties": {
        "password": {
          "type": "string",
          "required": false,
          "description": "optional share password"
        },
        "visitorName": {
          "type": "string",
          "required": false,
          "description": "external visitor display name"
        },
        "visitorEmail": {
          "type": "string",
          "required": false,
          "description": "external visitor email"
        },
        "challengeToken": {
          "type": "string",
          "required": false,
          "description": "risk challenge token when required"
        }
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "shared report readonly detail",
        "properties": {
          "accessGranted": {
            "type": "boolean",
            "required": true,
            "description": "whether the share access check succeeded"
          },
          "shareToken": {
            "type": "string",
            "required": true,
            "description": "share token used for this readonly session"
          },
          "reportId": {
            "type": "integer",
            "required": true,
            "description": "shared report id"
          },
          "title": {
            "type": "string",
            "required": true,
            "description": "report title"
          },
          "status": {
            "type": "string",
            "required": true,
            "description": "report lifecycle status"
          },
          "currentVersionId": {
            "type": "integer",
            "required": false,
            "description": "current report version id"
          },
          "sections": {
            "type": "array",
            "required": true,
            "description": "readonly current report sections",
            "items": {
              "type": "object"
            }
          },
          "allowDownload": {
            "type": "boolean",
            "required": true,
            "description": "whether the share policy allows export download"
          },
          "exports": {
            "type": "array",
            "required": true,
            "description": "downloadable completed export summaries when allowDownload is true",
            "items": {
              "type": "object",
              "properties": {
                "exportFileId": {
                  "type": "integer",
                  "required": true,
                  "description": "export file id"
                },
                "fileName": {
                  "type": "string",
                  "required": true,
                  "description": "export file name"
                },
                "format": {
                  "type": "string",
                  "required": true,
                  "description": "export format"
                },
                "contentType": {
                  "type": "string",
                  "required": false,
                  "description": "export content type"
                },
                "sizeBytes": {
                  "type": "integer",
                  "required": false,
                  "description": "export file size in bytes"
                }
              }
            }
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      403,
      404
    ]
  },
  {
    "method": "POST",
    "path": "/api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url",
    "description": "external visitor obtains controlled short-lived export download url",
    "module": "permission-collaboration",
    "securityIntent": "public-share-link-controlled",
    "contentType": "application/json",
    "pathParams": {
      "shareToken": {
        "type": "string",
        "required": true,
        "description": "share token"
      },
      "exportFileId": {
        "type": "integer",
        "required": true,
        "description": "export file id"
      }
    },
    "queryParams": {},
    "headers": {
      "X-Request-Id": {
        "type": "string",
        "required": false,
        "description": "request trace id"
      }
    },
    "requestBody": {
      "type": "object",
      "required": false,
      "description": "share access context and optional password",
      "properties": {
        "password": {
          "type": "string",
          "required": false,
          "description": "optional share password"
        },
        "visitorName": {
          "type": "string",
          "required": false,
          "description": "external visitor display name"
        },
        "visitorEmail": {
          "type": "string",
          "required": false,
          "description": "external visitor email"
        },
        "challengeToken": {
          "type": "string",
          "required": false,
          "description": "risk challenge token when required"
        }
      }
    },
    "responseBody": {
      "code": {
        "type": "integer",
        "required": true,
        "description": "response code"
      },
      "message": {
        "type": "string",
        "required": true,
        "description": "response message"
      },
      "data": {
        "type": "object",
        "required": true,
        "description": "short-lived shared export download metadata",
        "properties": {
          "exportFileId": {
            "type": "integer",
            "required": true,
            "description": "export file id"
          },
          "reportId": {
            "type": "integer",
            "required": true,
            "description": "report id owning the export"
          },
          "fileName": {
            "type": "string",
            "required": true,
            "description": "export file name"
          },
          "contentType": {
            "type": "string",
            "required": false,
            "description": "export content type"
          },
          "sizeBytes": {
            "type": "integer",
            "required": false,
            "description": "export file size in bytes"
          },
          "downloadPolicy": {
            "type": "string",
            "required": true,
            "description": "fixed policy value share_presigned_url"
          },
          "downloadUrl": {
            "type": "string",
            "required": true,
            "description": "short-lived presigned download URL"
          },
          "expiresAt": {
            "type": "string",
            "required": true,
            "description": "download URL expiry timestamp"
          }
        }
      },
      "timestamp": {
        "type": "string",
        "required": true,
        "description": "response timestamp"
      }
    },
    "statusCodes": [
      200,
      400,
      403,
      404
    ]
  }
]
```

<!-- API_CONTRACT_STRUCTURED_BACKEND_SUPPLEMENT_END -->

## 14. S4 契约边界

| 边界 | 说明 |
|------|------|
| 不生成代码 | 本阶段只生成 API 契约与 OpenSpec change，不生成后端、前端或数据库代码 |
| 不冻结架构 | 技术架构、服务拆分、网关和部署在 S5-S7 细化 |
| 不加入审批流 | API 契约 SHALL NOT 将历史样例中的“审核”转化为报告人工审核发布接口 |
| 不引入多租户 | API 契约 SHALL NOT 设计租户维度 |
| 保留扩展 | 多模型路由、权限细粒度扩展和通知渠道在后续阶段细化 |

## 15. S20 修订条款：认证、上传与 SSE 归属

### 15.1 认证入口归属

登录由 Higress / OIDC 外部入口负责，本系统 Java 业务核心不提供默认用户名密码登录接口。前端登录页只负责跳转统一认证入口；认证完成后调用 Java：

```json
{
  "method": "GET",
  "path": "/api/v1/auth/me",
  "description": "读取当前 Higress/OIDC 认证用户与 Java RBAC 上下文",
  "responseBody": {
    "code": 200,
    "message": "操作成功",
    "data": {
      "userId": 1,
      "displayName": "当前用户",
      "roles": ["ADMIN"],
      "permissions": ["report:create", "report:read"],
      "authProvider": "higress-oidc"
    }
  }
}
```

### 15.2 文档上传归属

`POST /api/v1/documents/upload` 对外由 Java 业务核心承接。Java 必须完成 RBAC 校验、对象元数据登记、审计记录和 `document.parse.requested` 事件发布；Python AI 服务作为异步解析执行方，可直接消费 RocketMQ，不需要通过 Java 同步调用。

### 15.3 SSE 统一事件 Schema

Java 报告任务流和 Python AI 内部流必须使用以下事件类型：`stage`、`delta`、`references`、`error`、`done`。禁止继续使用旧事件类型 `token`、`reference`。

```json
{
  "type": "stage | delta | references | error | done",
  "taskId": "task-001",
  "content": "text",
  "stage": "retrieval | analysis | writing | export",
  "references": [],
  "progress": 0.6,
  "errorCode": null,
  "traceId": "trace-001"
}
```
