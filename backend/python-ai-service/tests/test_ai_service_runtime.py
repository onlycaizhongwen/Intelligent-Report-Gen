import json
import os

from fastapi.testclient import TestClient

os.environ.setdefault("JWT_SECRET", "test-secret")

from app.main import app
import app.main as main_module
from app.rag_retrieval.application.local_rag_runtime import EvidenceChunk, LocalRagRuntime
from app.report_generation.application.generation_worker import ReportGenerationWorker
from app.shared_kernel.security import get_current_user


def override_user():
    return {"user_id": 1001, "roles": ["analyst"], "permissions": ["ai:invoke"]}


app.dependency_overrides[get_current_user] = override_user
client = TestClient(app)


def test_embedding_uses_deterministic_vector_reference_from_query():
    response = client.post("/api/v1/embeddings", json={"query": "east revenue risk", "top_k": 3})

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["model"] == "local-hash-embedding"
    assert data["dimension"] == 64
    assert data["vectorRef"].startswith("emb_")
    assert data["vectorRef"] != "chunk-demo"
    assert len(data["vector"]) == 64
    assert any(value != 0 for value in data["vector"])


def test_health_endpoint_supports_container_healthcheck():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_upload_endpoint_accepts_multipart_document():
    response = client.post(
        "/api/v1/documents/upload",
        files={"file": ("sample.txt", b"hello", "text/plain")},
    )

    assert response.status_code == 200
    assert response.json()["data"]["filename"] == "sample.txt"


def test_document_search_uses_configured_rag_retriever():
    original_runtime = main_module.rag_runtime
    main_module.rag_runtime = LocalRagRuntime(
        retriever=RecordingRetriever([
            EvidenceChunk(
                chunk_id="doc_990003_chunk_1",
                source_title="Indexed smoke document",
                content="Receivables aging requires follow-up.",
                score=0.92,
            )
        ])
    )
    try:
        response = client.post(
            "/api/v1/documents/search",
            json={"query": "receivables aging", "top_k": 5, "context": {"knowledgeBaseId": 1}},
        )
    finally:
        main_module.rag_runtime = original_runtime

    assert response.status_code == 200
    data = response.json()["data"]
    assert data[0]["chunkId"] == "doc_990003_chunk_1"
    assert data[0]["sourceTitle"] == "Indexed smoke document"


def test_internal_parse_event_endpoint_returns_chunks_for_indexing():
    response = client.post(
        "/api/v1/internal/document-parse-events",
        json={
            "eventType": "document.parse.requested",
            "eventKey": "77",
            "payload": {
                "documentId": 77,
                "objectKey": "knowledge/demo/customer-risk.txt",
                "text": "Customer churn risk increased in the east region.\n\nReceivables aging needs follow-up.",
            },
        },
    )

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["eventType"] == "document.parse.completed"
    assert data["eventKey"] == "77"
    assert data["payload"]["documentId"] == 77
    assert data["payload"]["chunks"] == 2
    assert data["payload"]["persistenceStatus"] == "skipped"
    assert data["payload"]["searchIndexStatus"] == "skipped"
    assert data["payload"]["embeddingModel"] == "local-hash-embedding"
    assert data["payload"]["knowledgeChunks"][0]["chunkId"] == "doc_77_chunk_0"
    assert data["payload"]["knowledgeChunks"][0]["vectorRef"].startswith("emb_")


def test_internal_report_generation_event_posts_completion_callback(monkeypatch):
    completion_client = RecordingCompletionClient()
    monkeypatch.setattr(
        main_module,
        "ReportGenerationWorker",
        lambda rag_runtime: ReportGenerationWorker(
            rag_runtime=LocalRagRuntime(retriever=RecordingRetriever([
                EvidenceChunk(
                    chunk_id="doc_990004_chunk_1",
                    source_title="rag.txt",
                    content="Receivables aging requires follow-up.",
                    score=0.92,
                )
            ])),
            completion_client=completion_client,
        ),
    )

    response = client.post(
        "/api/v1/internal/report-generation-events",
        json={
            "eventType": "report.generation.requested",
            "eventKey": "501",
            "payload": {
                "taskId": 501,
                "reportId": 88,
                "question": "Summarize receivables aging risk",
                "context": {"knowledgeBaseId": 1},
            },
        },
    )

    assert response.status_code == 200
    assert response.json()["data"]["eventType"] == "report.generation.completed"
    assert completion_client.calls[0]["task_id"] == 501


class RecordingRetriever:
    def __init__(self, chunks=None):
        self.chunks = chunks or []

    def search(self, *, question, query_vector, top_k, context):
        return self.chunks


class RecordingCompletionClient:
    def __init__(self):
        self.calls = []

    def complete(self, *, task_id, payload):
        self.calls.append({"task_id": task_id, "payload": payload})
        return {"taskId": task_id, "status": "completed", "versionId": 12}


def test_chat_stream_references_context_evidence_without_demo_ids():
    response = client.post(
        "/api/v1/chat",
        json={
            "question": "Summarize east revenue risk",
            "context": {
                "taskId": "task-1001",
                "knowledgeChunks": [
                    {
                        "chunkId": "kb-77",
                        "sourceTitle": "East revenue workbook",
                        "content": "East revenue grew 12 percent but receivables risk increased.",
                        "score": 0.93,
                    },
                    {
                        "chunkId": "kb-88",
                        "sourceTitle": "West cost memo",
                        "content": "West region cost stayed flat.",
                        "score": 0.51,
                    },
                ],
            },
        },
    )

    assert response.status_code == 200
    events = [
        json.loads(line.removeprefix("data: "))
        for line in response.text.splitlines()
        if line.startswith("data: ")
    ]

    reference_event = next(event for event in events if event["type"] == "references")
    delta_event = next(event for event in events if event["type"] == "delta")

    assert reference_event["taskId"] == "task-1001"
    assert reference_event["traceId"].startswith("trace_task-1001_")
    assert reference_event["traceId"] != "trace-demo"
    assert reference_event["references"][0]["referenceId"] == "kb-77"
    assert reference_event["references"][0]["sourceTitle"] == "East revenue workbook"
    assert reference_event["references"][0]["qualityScore"] >= 0.9
    assert "ref_demo" not in response.text
    assert "chunk-demo" not in response.text
    assert "East revenue" in delta_event["content"]
