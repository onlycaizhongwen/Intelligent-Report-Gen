<!-- skill: S15 -->
<skill id="S15" name="error_handling.design">

# 技能：错误处理设计

## Meta
- DependsOn: S12
- Category: backend
- Status: stable

## 一句话描述
设计统一的错误码体系与异常处理规范。

## 输入
- `backend/`：已有 DDD 后端代码目录
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `backend/`：异常处理代码，按 S7 DDD 目录结构写入对应层

## Prompt

你是一位资深后端工程师。
请完成以下工作：

错误处理必须从 OpenSpec 推导：
- 每个错误码、异常类型和用户提示必须标注对应 requirement / scenario。
- OpenSpec 未定义但实现中发现的异常分支，必须列为待补充 OpenSpec change，不得静默新增业务语义。

### 错误码体系设计

| 错误码范围 | 类别 | 示例 |
|------------|------|------|
| 400-499 | 客户端错误 | 400=参数错误, 401=未授权, 403=无权限 |
| 500-599 | 服务端错误 | 500=内部错误, 503=服务不可用 |
| 1000+ | 业务错误 | 1001=用户不存在, 1002=文档处理中 |
| 2000+ | AI 专项错误 | 2001=模型超时, 2002=Token 超限, 2003=Prompt 被拦截 |

### Java 版（Spring Boot 全局异常处理）

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(ResourceNotFoundException e) {
        log.warn("资源不存在: {}", e.getMessage());
        return ResponseEntity.status(404)
                .body(ApiResponse.error(1001, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArg(IllegalArgumentException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "系统繁忙，请稍后重试"));
    }
}

// 自定义业务异常
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
    // getter ...
}
```

### Python 版（FastAPI 异常处理器）

```python
from fastapi import Request
from fastapi.responses import JSONResponse

class BusinessException(Exception):
    def __init__(self, code: int, message: str):
        self.code = code
        self.message = message

async def business_exception_handler(request: Request, exc: BusinessException):
    return JSONResponse(
        status_code=400,
        content={"code": exc.code, "message": exc.message, "data": None}
    )

# AI 专项异常
class LLMTimeoutException(Exception):
    def __init__(self):
        super().__init__("模型响应超时，请稍后重试")

class TokenLimitException(Exception):
    def __init__(self, current: int, limit: int):
        super().__init__(f"Token 用量超限: {current}/{limit}")

class PromptInjectionException(Exception):
    def __init__(self):
        super().__init__("检测到不安全的输入内容")
```

### Go 版（Gin 错误处理）

```go
type AppError struct {
    Code    int    `json:"code"`
    Message string `json:"message"`
    Err     error  `json:"-"`
}

func (e *AppError) Error() string {
    return e.Message
}

func ErrorHandler() gin.HandlerFunc {
    return func(c *gin.Context) {
        defer func() {
            if err := recover(); err != nil {
                log.Error("Panic recovered: %v", err)
                c.JSON(500, ApiResponse{
                    Code:    500,
                    Message: "系统内部错误",
                })
            }
        }()
        c.Next()

        // 检查是否有业务错误
        if len(c.Errors) > 0 {
            err := c.Errors.Last().Err
            if appErr, ok := err.(*AppError); ok {
                c.JSON(appErr.Code, ApiResponse{
                    Code:    appErr.Code,
                    Message: appErr.Message,
                })
            } else {
                c.JSON(500, ApiResponse{
                    Code:    500,
                    Message: "操作失败",
                })
            }
        }
    }
}
```

### AI 项目额外错误处理

| 异常类型 | 触发场景 | 处理方式 | 用户提示 |
|----------|----------|----------|----------|
| LLM 超时 | 模型响应超过 30 秒 | 降级到缓存 / 默认回答 | "服务繁忙，请稍后重试" |
| Token 超限 | 单次请求超过模型限制 | 截断上下文 / 切换模型 | "问题太长，请精简后重试" |
| Prompt 注入 | 用户输入触发安全规则 | 拒绝执行 + 记录日志 | "检测到不安全的输入" |
| 向量检索无结果 | 知识库无相关内容 | 降级到 LLM 通用知识 | "未找到相关信息，将以通用知识回答" |
| 文档解析失败 | 文件格式不支持 / 损坏 | 重试 + 人工介入 | "文档解析失败，请重新上传" |

## 行为规则

- ✅ 所有错误必须有中文提示
- ✅ 错误码必须唯一且有文档
- ✅ 错误码和异常分支必须追溯 OpenSpec requirement / scenario
- ✅ AI 项目必须包含 LLM 超时 / Token 超限处理
- ✅ 不得向用户暴露系统内部错误详情
- ✅ 所有异常必须记录日志（含堆栈）
- ❌ 不得使用通用 Exception 捕获后不处理
- ❌ 不得新增 OpenSpec 未定义的业务错误语义
- ❌ 不得返回 null（使用 Optional / Maybe）

## 使用示例

```
加载 <skill id="S15">，输入：backend/、openspec/specs/
请设计统一的错误处理机制，包含 AI 服务专项异常处理。
```
</skill>
<!-- end -->
