# Closure 273: Knowledge Document Contract Schema

## 范围

- 需求链路：`UC-06` / `REQ-KB-001` / `REQ-KB-002`
- 阶段产物：`S4 API Contract`
- 目标端点：
  - `GET /api/v1/documents/{documentId}`
  - `POST /api/v1/knowledge-items/batch-import`

## 结果

知识库文档状态和知识条目批量导入接口不再只声明泛化的 `business response data`，而是按后端真实返回补齐字段级 `responseBody.data.properties`。

- 文档状态字段：`documentId`、`fileObjectId`、`knowledgeBaseId`、`filename`、`fileType`、`size`、`contentType`、`bucket`、`objectKey`、`parseStatus`、`parseFailureReason`
- 批量导入汇总字段：`knowledgeBaseId`、`total`、`imported`、`failed`、`items`
- 批量导入逐行字段：`title`、`status`、`reason`、`itemId`、`knowledgeBaseId`

## 代码与文档证据

- 合同守护测试：`ContractSurfaceTest#knowledgeDocumentEndpointsDeclareFieldLevelResponseContracts`
- API 合同：`docs/skill-chain/api_contract.md` section `13.2`
- 后端字段来源：`KnowledgeApplicationService#toMetadata`、`batchImportItems`

## TDD 证据

RED:

```text
ContractSurfaceTest.knowledgeDocumentEndpointsDeclareFieldLevelResponseContracts
GET /api/v1/documents/{documentId} responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#knowledgeDocumentEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 剩余缺口

- 本轮未重新执行真实 Docker OCR、OpenSearch、Milvus 或浏览器上传烟测。
- 数据源同步端点仍有泛化 schema，后续可继续按 `REQ-KB-003` 收敛。
