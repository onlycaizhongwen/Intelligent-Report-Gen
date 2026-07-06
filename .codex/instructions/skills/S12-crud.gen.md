<!-- skill: S12 -->
<skill id="S12" name="crud.gen">

# 技能：CRUD 接口生成

## Meta
- DependsOn: S4, S6, S7, S9
- Category: backend
- Status: stable

## 一句话描述
根据 API 契约和数据库 Schema，生成标准的增删改查接口代码。

## 输入
- `docs/skill-chain/api_contract.md`：API 接口文档
- `schema.sql`：数据库建表脚本
- `docs/skill-chain/tech_stack.md`：技术栈选型文档
- `docs/skill-chain/module_design.md`：服务边界与模块设计文档（如存在，必须遵循）
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- DDD 目录结构下的接口层、应用层、领域层、基础设施层代码
- `interfaces/rest/` 或 `interfaces/api/`：控制器、路由、Request、Response、DTO
- `application/`：ApplicationService、Command、Query、UseCase
- `domain/model/`：Aggregate、Entity、ValueObject、DomainEvent
- `domain/repository/`：Repository 接口
- `infrastructure/persistence/`：Repository 实现、Mapper、PO / ORM 模型

## Prompt

你是一位资深后端工程师。
请按 API 契约、数据库 Schema 和 S7 DDD 目录蓝图，生成 DDD 分层代码。

生成前必须读取 `docs/skill-chain/module_design.md` 中的领域边界、服务边界、部署单元和数据所有权：
- 只在拥有该数据所有权的服务内生成写接口。
- 跨服务数据访问必须通过 API / gRPC / 事件订阅，不得直接访问其他服务数据库。
- 如果 S7 将某能力判定为模块而非独立服务，不得擅自拆成新服务。
- 如果 S7 选择 Higress 作为网关，不得在 CRUD 阶段生成 Go Gin 网关 CRUD 代理。
- 每个 Controller / Service / Repository 必须标注覆盖的 OpenSpec requirement / scenario。
- 必须使用 S7 输出的 DDD 目录结构；不得退回传统 `controllers/`、`services/`、`repositories/` 平铺目录。
- Controller / Router 只能做协议适配和参数转换；业务编排放在 `application/`，领域规则放在 `domain/`，数据库实现放在 `infrastructure/`。

**根据 docs/skill-chain/tech_stack.md 中的技术选型，选择对应语言生成：**

### Java 版（Spring Boot / Spring Cloud Alibaba）

DDD 目录示例：
```text
src/main/java/com/example/report/reporting/
  interfaces/rest/ReportController.java
  interfaces/rest/dto/CreateReportRequest.java
  application/CreateReportUseCase.java
  application/ReportApplicationService.java
  domain/model/Report.java
  domain/model/ReportStatus.java
  domain/repository/ReportRepository.java
  infrastructure/persistence/JpaReportRepository.java
  infrastructure/persistence/ReportJpaEntity.java
```

Controller 示例：
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
```

Application Service 示例：
```java
@Service
public class GetUserUseCase {
    private final UserRepository userRepository;

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
```

Domain Repository 接口示例：
```java
public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
}
```

统一响应类：
```java
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;
}
```

### Python 版（FastAPI）

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

class UserResponse(BaseModel):
    id: int
    username: str
    role: str

@app.get("/api/v1/users/{user_id}", response_model=ApiResponse[UserResponse])
async def get_user(user_id: int):
    user = await user_service.get_by_id(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    return ApiResponse(data=user)
```

### Go 版（Gin）

```go
func GetUser(c *gin.Context) {
    userID := c.Param("id")
    user, err := userService.GetByID(userID)
    if err != nil {
        c.JSON(404, ApiResponse{
            Code:    404,
            Message: "用户不存在",
        })
        return
    }
    c.JSON(200, ApiResponse{Code: 200, Data: user})
}
```

### AI 项目额外接口（如需要）

**SSE 流式对话接口（Python FastAPI）**：
```python
from fastapi.responses import StreamingResponse

@app.post("/api/v1/chat")
async def chat(request: ChatRequest):
    async def generate():
        async for token in llm_service.stream_answer(request.question, request.context):
            yield f"data: {json.dumps({'type': 'token', 'content': token})}\n\n"
        yield f"data: {json.dumps({'type': 'done', 'references': references})}\n\n"
    return StreamingResponse(generate(), media_type="text/event-stream")
```

**文档上传接口**：
```python
@app.post("/api/v1/documents/upload")
async def upload_document(file: UploadFile, user_id: int = Depends(get_current_user)):
    # 保存文件 → 解析 → 切片 → Embedding → 存入 Milvus
    doc_id = await document_service.process_upload(file, user_id)
    return ApiResponse(data={"document_id": doc_id})
```

**语义搜索接口**：
```python
@app.post("/api/v1/documents/search")
async def search_documents(request: SearchRequest):
    # 1. 将查询转为向量
    query_embedding = await embedding_service.encode(request.query)
    # 2. Milvus 向量检索
    results = await milvus_service.search(query_embedding, top_k=5)
    # 3. 返回相关文档片段
    return ApiResponse(data=results)
```

## 行为规则

- ✅ 必须严格遵循 API 契约中的路径和参数
- ✅ 必须使用统一响应结构（ApiResponse）
- ✅ 必须包含完整的错误处理
- ✅ AI 项目必须包含 SSE 流式接口
- ✅ 必须遵循 S7 的服务边界、数据所有权和部署单元结论
- ✅ 必须遵循 S7 的 DDD 代码骨架目录蓝图
- ✅ Controller / Router、Application、Domain、Infrastructure 职责必须分离
- ✅ 每个接口实现必须追溯 OpenSpec requirement / scenario
- ✅ 所有代码必须有中文注释
- ❌ 不得直接暴露数据库模型（使用 DTO / VO 转换）
- ❌ 不得在 Controller 中写业务逻辑
- ❌ 不得生成传统 `controllers/`、`services/`、`repositories/` 平铺主结构
- ❌ 不得让领域层依赖基础设施层、Web 框架或 ORM 模型
- ❌ 不得使用 SELECT *（使用指定字段）
- ❌ 不得绕过服务边界直接访问其他服务数据库

## 使用示例

```
加载 <skill id="S12">，输入：docs/skill-chain/api_contract.md、schema.sql、docs/skill-chain/tech_stack.md 和 docs/skill-chain/module_design.md
请生成 Java Spring Boot 版本的 CRUD 接口代码，包含 AI 检索接口。
```
</skill>
<!-- end -->
