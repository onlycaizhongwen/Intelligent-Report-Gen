<!-- skill: S21 -->
<skill id="S21" name="unit_test.gen">

# 技能：单元测试生成

## Meta
- DependsOn: S14
- Category: quality
- Status: stable

## 一句话描述
根据业务逻辑代码，生成单元测试用例。

## 输入
- `backend/`：DDD 后端代码目录
- `openspec/specs/`：OpenSpec capability 目录

## 输出
- `tests/unit/`（单元测试用例文件）

## Prompt

你是一位资深测试工程师。
请根据 Service 层代码，生成单元测试。

测试必须从 OpenSpec scenario 推导：每个 `Scenario` 至少对应一个单元测试、集成测试或明确的不可自动化说明。

---

### Java 版（JUnit 5 + Mockito）

```java
@Slf4j
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    @DisplayName("上传文档 - 成功")
    void uploadDocument_Success() throws Exception {
        // 准备
        MultipartFile file = new MockMultipartFile(
                "test.pdf", "test.pdf", "application/pdf", "测试内容".getBytes()
        );
        when(documentRepository.save(any(Document.class)))
                .thenReturn(Document.builder().id(1L).build());

        // 执行
        Long docId = documentService.uploadDocument(file, 1L);

        // 验证
        assertNotNull(docId);
        assertEquals(1L, docId);
        verify(documentRepository).save(any(Document.class));
        verify(embeddingService).processAsync(eq(1L));
    }

    @Test
    @DisplayName("删除文档 - 文档不存在")
    void deleteDocument_NotFound() {
        when(documentRepository.findByIdAndUserId(999L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                documentService.deleteDocument(999L, 1L)
        );
    }

    @Test
    @DisplayName("检索文档 - 返回相关片段")
    void searchDocuments_ReturnsRelevantChunks() {
        // Mock 向量检索结果
        SearchResult mockResult = new SearchResult();
        mockResult.setChunks(List.of(
                new ChunkDTO("片段1内容", 0.95, "测试文档")
        ));
        when(ragSearchService.search("测试问题"))
                .thenReturn(mockResult);

        SearchResult result = documentService.search("测试问题", 1L);

        assertNotNull(result);
        assertEquals(1, result.getChunks().size());
        assertEquals("片段1内容", result.getChunks().get(0).getContent());
    }
}
```

---

### Python 版（Pytest + Async）

```python
import pytest
from unittest.mock import AsyncMock, patch
from services.document_service import DocumentService

@pytest.fixture
def doc_service():
    return DocumentService()

@pytest.mark.asyncio
async def test_upload_document_success(doc_service):
    """上传文档 - 成功"""
    mock_file = AsyncMock()
    mock_file.filename = "test.pdf"
    mock_file.read.return_value = b"PDF 内容"

    with patch("services.document_service.file_storage.save", return_value="/uploads/test.pdf"), \
         patch("services.document_service.document_processor.process_async") as mock_process:
        doc_id = await doc_service.upload(mock_file, user_id=1)
        assert doc_id is not None
        mock_process.assert_called_once_with(doc_id)

@pytest.mark.asyncio
async def test_delete_document_not_found(doc_service):
    """删除文档 - 文档不存在"""
    with patch("services.document_service.db.fetch_one", return_value=None):
        with pytest.raises(ResourceNotFound):
            await doc_service.delete(999, user_id=1)

@pytest.mark.asyncio
async def test_chat_stream_returns_tokens(doc_service):
    """对话流式接口 - 返回 Token 流"""
    mock_llm = AsyncMock()
    mock_llm.astream.return_value = ["你", "好", "！"]

    with patch("services.document_service.llm", mock_llm), \
         patch("services.document_service.retriever.aretrieve", return_value=[]):
        tokens = []
        async for token in doc_service.chat_stream("你好"):
            tokens.append(token)
        assert len(tokens) > 0
        assert "".join(tokens) == "你好！"

@pytest.mark.asyncio
async def test_embedding_batches(doc_service):
    """Embedding 批量生成"""
    texts = ["文本1", "文本2", "文本3"]
    mock_model = AsyncMock()
    mock_model.aencode.return_value = [[0.1] * 1024] * 3

    with patch("services.document_service.embed_model", mock_model):
        embeddings = await doc_service.batch_embed(texts)
        assert len(embeddings) == 3
        assert len(embeddings[0]) == 1024
```

---

### Go 版（testing + testify）

```go
func TestGetUserByID(t *testing.T) {
    mockRepo := new(MockUserRepository)
    service := NewUserService(mockRepo)

    t.Run("用户存在", func(t *testing.T) {
        mockRepo.On("FindByID", 1).Return(&User{ID: 1, Username: "test"}, nil)

        user, err := service.GetByID(1)
        assert.NoError(t, err)
        assert.Equal(t, "test", user.Username)
    })

    t.Run("用户不存在", func(t *testing.T) {
        mockRepo.On("FindByID", 999).Return(nil, ErrNotFound)

        _, err := service.GetByID(999)
        assert.Error(t, err)
        assert.Equal(t, ErrNotFound, err)
    })
}

func TestProxyRequest(t *testing.T) {
    t.Run("转发成功", func(t *testing.T) {
        // Mock HTTP 响应
        server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
            w.WriteHeader(200)
            w.Write([]byte(`{"code":200,"data":"ok"}`))
        }))
        defer server.Close()

        result, err := ProxyTo(server.URL)()
        assert.NoError(t, err)
    })
}
```

---

### AI 项目额外测试

| 测试类型 | 测试内容 | 工具 |
|----------|----------|------|
| Embedding 一致性 | 同一文本多次编码，向量余弦相似度 > 0.99 | NumPy |
| RAG 召回率 | 标准问题集 → 检索命中率 | pytest |
| LLM 响应格式 | 输出是否符合预期 JSON Schema | Pydantic |
| Prompt 注入防御 | 注入攻击样本 → 是否被正确拦截 | 安全测试集 |
| Token 计费 | 请求 → Token 计数是否准确 | 单元测试断言 |
| 流式完整性 | SSE 流 → 是否收到 done 事件 | EventSource 测试 |

## 行为规则

- ✅ 测试覆盖率必须 ≥ 80%
- ✅ 每个 Service 方法至少一个测试用例
- ✅ AI 项目必须测试 Embedding 和 RAG 流程
- ✅ OpenSpec scenario 必须有测试覆盖或明确说明不可自动化原因
- ✅ 必须使用 Mock（不依赖外部服务）
- ✅ 测试必须是中文描述（@DisplayName / 中文 docstring）
- ❌ 不得测试私有方法
- ❌ 不得依赖数据库 / 外部 API（全部 Mock）

## 使用示例

```
加载 <skill id="S21">，输入：backend/
请为所有 Service 生成单元测试，包含 RAG 检索和 Embedding 测试。
```
</skill>
<!-- end -->
