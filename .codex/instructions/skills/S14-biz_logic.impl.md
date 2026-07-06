<!-- skill: S14 -->
<skill id="S14" name="biz_logic.impl">

# 技能：业务逻辑实现

## Meta
- DependsOn: S12, S6, S7
- Category: backend
- Status: stable

## 一句话描述
实现核心业务逻辑，包含传统业务规则和 AI 推理逻辑。

## 输入
- `backend/`：已有 DDD 后端代码目录
- `docs/skill-chain/tech_stack.md`（技术栈选型文档）
- `docs/skill-chain/module_design.md`：服务边界与模块设计文档（如存在，必须遵循）
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- S7 DDD 目录结构下的应用层、领域层和基础设施补充代码
- `application/`：业务用例编排、事务边界、Command / Query Handler
- `domain/`：聚合、值对象、领域服务、领域事件中的业务规则
- `infrastructure/`：外部模型、向量库、对象存储、消息队列、第三方服务适配

## Prompt

你是一位资深后端工程师。
请根据已有 DDD 代码骨架，补充完整的应用层与领域层业务逻辑。

**根据 docs/skill-chain/tech_stack.md 中的选型，选择对应语言实现：**

实现前必须遵循 `docs/skill-chain/module_design.md`：
- 必须使用 S7 输出的 DDD 目录结构；不得把业务逻辑集中写入平铺 `services/` 目录。
- 应用层负责编排用例、事务、权限上下文和跨聚合协调；领域层负责业务不变量、聚合行为、值对象校验和领域事件。
- 基础设施层负责数据库、对象存储、向量库、模型 API、消息队列和外部服务适配。
- 业务规则只能写在对应领域服务或领域模块中。
- 跨服务流程必须使用 S7 指定的通信模式（HTTP/gRPC、消息队列、事件驱动、SSE/WebSocket、对象存储回调）。
- 长耗时 AI 流程（文档解析、Embedding、向量检索、报告生成、LLM/Agent 编排）按 S7 结论决定独立服务或模块，不得随意合并或拆分。
- 如果 S7 将权限、审计、计费定义为横切能力，必须按共享模块、独立服务或 Higress 网关能力分别实现。
- 每条业务规则、AI 流程和异常分支必须追溯 OpenSpec requirement / scenario。

---

### Java 版（Spring Boot / Spring Cloud Alibaba）

Application Service 示例：
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadDocumentUseCase {
    private final DocumentRepository documentRepository;
    private final FileStoragePort fileStoragePort;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public Long handle(UploadDocumentCommand command) {
        // 1. 保存文件到对象存储
        String filePath = fileStoragePort.store(command.file());
        // 2. 调用领域聚合创建文档，保证业务不变量在领域层
        Document doc = Document.create(command.title(), filePath, command.userId());
        documentRepository.save(doc);
        // 3. 发布领域事件，异步触发解析/切片/Embedding
        eventPublisher.publish(new DocumentUploadedEvent(doc.getId()));
        return doc.getId();
    }
}
```

Domain Aggregate 示例：
```java
public class Document {
    private DocumentId id;
    private UserId ownerId;
    private DocumentStatus status;

    public static Document create(String title, String filePath, Long userId) {
        if (title == null || title.isBlank()) {
            throw new DomainException("文档标题不能为空");
        }
        Document doc = new Document();
        doc.ownerId = new UserId(userId);
        doc.status = DocumentStatus.PROCESSING;
        return doc;
    }
}
```

AI 检索服务（Java 调用 Python）：
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {
    private final WebClient aiWebClient;  // 调用 Python FastAPI
    private final DocumentRepository documentRepository;

    public SearchResult search(String query, Long userId) {
        // 调用 Python 服务的 Embedding + 检索
        RagSearchRequest request = new RagSearchRequest(query, userId);
        RagSearchResponse response = aiWebClient.post()
                .uri("/api/v1/ai/search")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RagSearchResponse.class)
                .block();
        return SearchResult.builder()
                .chunks(response.getChunks())
                .tokens(response.getTokensUsed())
                .build();
    }
}
```

---

### Python 版（FastAPI + LlamaIndex）

文档处理服务：
```python
class DocumentProcessor:
    def __init__(self, llm: LLM, embed_model: BaseEmbedding):
        self.llm = llm
        self.embed_model = embed_model
        self.parser = UnstructuredParser()

    async def process_document(self, doc_id: int, file_path: str):
        """文档 → 解析 → 切片 → Embedding → Milvus"""
        try:
            # 1. 解析文档
            chunks = await self.parser.parse(file_path)
            # 2. 生成向量
            embeddings = await self.embed_model.aencode([c.text for c in chunks])
            # 3. 存入 Milvus
            await milvus_client.insert(
                collection="knowledge_chunks",
                data=[
                    {"document_id": doc_id, "chunk_index": i,
                     "content": c.text, "embedding": e.tolist()}
                    for i, (c, e) in enumerate(zip(chunks, embeddings))
                ]
            )
            # 4. 更新文档状态
            await db.execute(
                "UPDATE documents SET status = 'ready' WHERE id = :id",
                {"id": doc_id}
            )
        except Exception as e:
            logger.error(f"文档处理失败: {doc_id}, 错误: {e}")
            await db.execute(
                "UPDATE documents SET status = 'failed' WHERE id = :id",
                {"id": doc_id}
            )

class RagService:
    def __init__(self, llm: LLM, retriever: BaseRetriever):
        self.llm = llm
        self.retriever = retriever

    async def answer(self, question: str, user_id: int) -> AsyncGenerator[str, None]:
        """RAG 核心流程：检索 → 增强 → 生成"""
        # 1. 检索相关文档片段
        chunks = await self.retriever.aretrieve(question, top_k=5)
        if not chunks:
            yield "抱歉，未找到相关信息。"
            return
        # 2. 构建增强上下文
        context = "\n\n".join([f"[来源: {c.metadata['title']}]\n{c.text}" for c in chunks])
        # 3. 构建 Prompt
        prompt = PROMPT_TEMPLATES["qa"].format(context=context, question=question)
        # 4. 流式生成回答
        async for token in self.llm.astream(prompt):
            yield token
```

---

### Go 版（可选：Gin 自研轻量网关 + 业务服务）

仅当 `docs/skill-chain/tech_stack.md` 明确拒绝 Higress，并选择自研轻量代理时，才生成 Go Gin 网关代码。
如 S6/S7 明确选择 Higress，则由 Higress 承担网关职责；业务逻辑实现不得再强行生成未被 S6/S7 选中的网关代码。

网关路由 + 转发：
```go
func SetupRouter() *gin.Engine {
    r := gin.Default()
    // 中间件链
    r.Use(
        CORS(),
        RateLimit(100, time.Minute),  // 限流
        AuthMiddleware(),              // 鉴权
        LoggingMiddleware(),           // 日志
    )
    // 路由分组
    api := r.Group("/api/v1")
    {
        // 用户服务（转发到 Java 后端）
        users := api.Group("/users")
        users.GET("/:id", ProxyTo("http://user-service:8080"))
        users.POST("", ProxyTo("http://user-service:8080"))
        // AI 服务（转发到 Python 后端）
        ai := api.Group("/ai")
        ai.POST("/chat", ProxyTo("http://ai-service:8000"))
        ai.POST("/search", ProxyTo("http://ai-service:8000"))
        // 文档服务（转发到 Python 后端）
        docs := api.Group("/documents")
        docs.POST("/upload", ProxyTo("http://ai-service:8000"))
        docs.GET("", ProxyTo("http://user-service:8080"))
    }
    return r
}

// 反向代理
func ProxyTo(target string) gin.HandlerFunc {
    return func(c *gin.Context) {
        // 复制请求头、路径、Body
        // 转发到目标服务
        // 将响应写回客户端
    }
}
```

---

### BERT 文本分析（混合场景）

Java 端（DJL 调用 BERT）：
```java
@Slf4j
@Service
public class TextAnalysisService {
    private final Predictor<String, float[]> embeddingPredictor;
    private final Predictor<String, String> sentimentPredictor;

    public TextAnalysisService() throws ModelException, IOException {
        // 加载 BERT 模型（中文）
        Model model = ModelZoo.loadModel(
                Criteria.builder()
                        .setTypes(String.class, float[].class)
                        .optModelUrls("djl://ai.djl.huggingface/bert-base-chinese")
                        .build()
        );
        this.embeddingPredictor = model.newPredictor();
        // 情感分析模型
        Model sentimentModel = ModelZoo.loadModel(
                Criteria.builder()
                        .setTypes(String.class, String.class)
                        .optModelUrls("djl://ai.djl.huggingface/bert-base-chinese-sentiment")
                        .build()
        );
        this.sentimentPredictor = sentimentModel.newPredictor();
    }

    public float[] getEmbedding(String text) {
        try {
            return embeddingPredictor.predict(text);
        } catch (Exception e) {
            log.error("Embedding 生成失败: {}", e.getMessage());
            throw new BusinessException("文本向量化失败");
        }
    }

    public String analyzeSentiment(String text) {
        try {
            return sentimentPredictor.predict(text);
        } catch (Exception e) {
            log.error("情感分析失败: {}", e.getMessage());
            return "NEUTRAL";
        }
    }
}
```

Python 端（Transformers 调用 BERT）：
```python
from transformers import AutoTokenizer, AutoModel
import torch

class BERTEmbedding:
    def __init__(self, model_name="bert-base-chinese"):
        self.tokenizer = AutoTokenizer.from_pretrained(model_name)
        self.model = AutoModel.from_pretrained(model_name)
        self.model.eval()

    def encode(self, texts: list[str]) -> torch.Tensor:
        inputs = self.tokenizer(
            texts, padding=True, truncation=True,
            max_length=512, return_tensors="pt"
        )
        with torch.no_grad():
            outputs = self.model(**inputs)
        # 使用 [CLS] token 的表示作为句子向量
        embeddings = outputs.last_hidden_state[:, 0, :]
        return embeddings
```

---

## 行为规则

- ✅ 业务逻辑与数据访问分离（Service ≠ Repository）
- ✅ AI 项目必须实现 RAG 检索增强流程
- ✅ AI 项目必须支持 SSE 流式输出
- ✅ 所有外部调用必须有超时和重试
- ✅ 所有操作必须有日志记录
- ✅ BERT / Embedding 调用必须有降级方案
- ✅ 必须遵循 S7 的领域边界、服务边界、部署边界和通信模式
- ✅ 必须遵循 S7 的 DDD 代码骨架目录蓝图
- ✅ 应用层、领域层、基础设施层职责必须清晰分离
- ✅ 业务规则和 AI 流程必须覆盖 OpenSpec requirement / scenario
- ✅ 如技术栈选择 Higress，网关路由、限流、鉴权、模型 API 治理应在 Higress 配置/部署阶段体现，不在 S14 中生成 Go Gin 网关业务代码
- ❌ 不得在 Service 中写 SQL
- ❌ 不得把业务规则写在 Controller、Repository、ORM Entity 或基础设施适配器中
- ❌ 不得让领域层依赖 Web 框架、数据库框架、外部 SDK
- ❌ 不得继续使用平铺 `services/` 目录承载所有业务逻辑
- ❌ 不得同步调用耗时 AI 操作（必须异步）
- ❌ 不得忽略异常（必须捕获并记录）

## 使用示例

```
加载 <skill id="S14">，输入：backend/、docs/skill-chain/tech_stack.md 和 docs/skill-chain/module_design.md
请补充完整的业务逻辑，包含文档上传处理和 RAG 检索问答。
```
</skill>
<!-- end -->
