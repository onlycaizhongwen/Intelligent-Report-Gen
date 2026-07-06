# Delivery Progress 2026-06-23 - Python AI Runtime

## Scope

- Replaced fixed Python AI embedding output with deterministic local hash embeddings.
- Replaced fixed chat SSE reference output with context-derived evidence references.
- Added a local RAG runtime that ranks request-scoped `knowledgeChunks` and emits traceable references.
- Kept `/api/v1/chat` as a controlled internal AI stream surface; Java still owns external report task orchestration.

## Validation Evidence

- RED: `python -m pytest backend/python-ai-service/tests/test_ai_service_runtime.py`
  - Failed because `/api/v1/embeddings` returned `BGE-M3/chunk-demo`.
  - Failed because `/api/v1/chat` emitted `trace-demo/ref_demo`.
- GREEN: `python -m pytest backend/python-ai-service/tests/test_ai_service_runtime.py`
  - Tests run: 2, Failures: 0, Errors: 0.
- Service regression: `python -m pytest backend/python-ai-service`
  - Tests run: 2, Failures: 0, Errors: 0.
- Residual demo scan: `rg "chunk-demo|ref_demo|trace-demo|demo" backend/python-ai-service -g "*.py"`
  - Only test anti-regression assertions remain.

## Remaining Risks

- The local embedding is deterministic and testable, but it is not a production semantic embedding model.
- Retrieval currently uses request-scoped context evidence; direct OpenSearch/Milvus integration remains a later production hardening slice.
- Streaming output is evidence-grounded, but full model routing/fallback to external GPT providers is not implemented in this slice.
