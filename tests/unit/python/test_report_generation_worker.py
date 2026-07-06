import pytest

from app.rag_retrieval.application.local_rag_runtime import EvidenceChunk, LocalRagRuntime
from app.report_generation.application.generation_worker import ReportGenerationWorker


@pytest.mark.asyncio
async def test_report_generation_worker_posts_completion_to_java_callback(monkeypatch):
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("CUSTOM_LLM_API_KEY", raising=False)
    monkeypatch.delenv("AI_API_KEY", raising=False)
    completion_client = RecordingCompletionClient()
    worker = ReportGenerationWorker(
        rag_runtime=LocalRagRuntime(retriever=RecordingRetriever()),
        completion_client=completion_client,
    )

    result = await worker.handle(
        {
            "eventType": "report.generation.requested",
            "eventKey": "501",
            "payload": {
                "taskId": 501,
                "reportId": 88,
                "question": "Summarize receivables aging risk",
                "context": {"knowledgeBaseId": 1},
            },
        }
    )

    assert result["eventType"] == "report.generation.completed"
    assert completion_client.calls[0]["task_id"] == 501
    callback_payload = completion_client.calls[0]["payload"]
    assert callback_payload["sections"][0]["heading"] == "Executive summary"
    assert "Receivables aging requires follow-up." in callback_payload["sections"][0]["content"]
    assert callback_payload["sections"][0]["citations"][0]["referenceId"] == "doc_990004_chunk_1"
    assert callback_payload["references"][0]["referenceId"] == "doc_990004_chunk_1"
    assert callback_payload["modelInvocation"]["provider"] == "local-fallback"
    assert callback_payload["modelInvocation"]["status"] == "succeeded"


@pytest.mark.asyncio
async def test_report_generation_worker_passes_online_model_result_and_token_audit_fields():
    completion_client = RecordingCompletionClient()
    worker = ReportGenerationWorker(
        rag_runtime=LocalRagRuntime(retriever=RecordingRetriever()),
        completion_client=completion_client,
        llm_provider=RecordingLlmProvider(),
    )

    await worker.handle(
        {
            "eventType": "report.generation.requested",
            "eventKey": "502",
            "payload": {
                "taskId": 502,
                "reportId": 89,
                "question": "Summarize receivables aging risk",
                "context": {"knowledgeBaseId": 1},
            },
        }
    )

    callback_payload = completion_client.calls[0]["payload"]
    assert callback_payload["sections"][0]["content"] == "Online model generated report section."
    assert callback_payload["modelInvocation"]["provider"] == "openai-compatible"
    assert callback_payload["modelInvocation"]["model"] == "gpt-4.1-mini"
    assert callback_payload["modelInvocation"]["inputTokens"] == 11
    assert callback_payload["modelInvocation"]["outputTokens"] == 7
    assert callback_payload["modelInvocation"]["totalTokens"] == 18
    assert callback_payload["modelInvocation"]["latencyMs"] == 123


@pytest.mark.asyncio
async def test_report_generation_worker_preserves_failover_model_metadata():
    completion_client = RecordingCompletionClient()
    worker = ReportGenerationWorker(
        rag_runtime=LocalRagRuntime(retriever=RecordingRetriever()),
        completion_client=completion_client,
        llm_provider=RecordingFailoverLlmProvider(),
    )

    await worker.handle(
        {
            "eventType": "report.generation.requested",
            "eventKey": "503",
            "payload": {
                "taskId": 503,
                "reportId": 90,
                "question": "Summarize receivables aging risk",
                "context": {"knowledgeBaseId": 1},
            },
        }
    )

    callback_payload = completion_client.calls[0]["payload"]
    assert callback_payload["sections"][0]["content"] == "Secondary model generated report section."
    assert callback_payload["modelInvocation"]["provider"] == "openai-compatible"
    assert callback_payload["modelInvocation"]["model"] == "qwen-plus"
    assert callback_payload["modelInvocation"]["status"] == "succeeded"
    assert callback_payload["modelInvocation"]["fallbackUsed"] is False
    assert callback_payload["modelInvocation"]["errorMessage"] is None
    assert callback_payload["modelInvocation"]["totalTokens"] == 17


@pytest.mark.asyncio
async def test_report_generation_worker_passes_template_context_to_llm_provider():
    completion_client = RecordingCompletionClient()
    llm_provider = RecordingContextAwareLlmProvider()
    worker = ReportGenerationWorker(
        rag_runtime=LocalRagRuntime(retriever=RecordingRetriever()),
        completion_client=completion_client,
        llm_provider=llm_provider,
    )

    await worker.handle(
        {
            "eventType": "report.generation.requested",
            "eventKey": "504",
            "payload": {
                "taskId": 504,
                "reportId": 91,
                "question": "Summarize board risks",
                "context": {
                    "knowledgeBaseId": 1,
                    "templateSnapshot": {"templateId": "enterprise-board", "name": "Board briefing"},
                },
            },
        }
    )

    assert llm_provider.requests[0].context == {
        "knowledgeBaseId": 1,
        "templateSnapshot": {"templateId": "enterprise-board", "name": "Board briefing"},
    }
    callback_payload = completion_client.calls[0]["payload"]
    assert callback_payload["modelInvocation"]["model"] == "qwen-plus"
    assert callback_payload["sections"][0]["content"] == "Board route generated report section."


@pytest.mark.asyncio
async def test_report_generation_worker_passes_generation_mode_to_llm_provider():
    completion_client = RecordingCompletionClient()
    llm_provider = RecordingContextAwareLlmProvider()
    worker = ReportGenerationWorker(
        rag_runtime=LocalRagRuntime(retriever=RecordingRetriever()),
        completion_client=completion_client,
        llm_provider=llm_provider,
    )

    await worker.handle(
        {
            "eventType": "report.generation.requested",
            "eventKey": "505",
            "payload": {
                "taskId": 505,
                "reportId": 92,
                "question": "Summarize template risks",
                "context": {
                    "knowledgeBaseId": 1,
                    "generationMode": "template",
                    "templateSnapshot": {"templateId": "enterprise-board", "name": "Board briefing"},
                },
            },
        }
    )

    assert llm_provider.requests[0].context == {
        "knowledgeBaseId": 1,
        "generationMode": "template",
        "templateSnapshot": {"templateId": "enterprise-board", "name": "Board briefing"},
    }


@pytest.mark.asyncio
async def test_report_generation_worker_includes_model_routing_policy_in_audit_payload():
    completion_client = RecordingCompletionClient()
    worker = ReportGenerationWorker(
        rag_runtime=LocalRagRuntime(retriever=RecordingRetriever()),
        completion_client=completion_client,
        llm_provider=RecordingRoutedLlmProvider(),
    )

    await worker.handle(
        {
            "eventType": "report.generation.requested",
            "eventKey": "506",
            "payload": {
                "taskId": 506,
                "reportId": 93,
                "question": "Summarize tenant risks",
                "context": {
                    "knowledgeBaseId": 1,
                    "tenant": "finance",
                    "scenario": "board-review",
                    "costPolicy": "low",
                },
            },
        }
    )

    model_invocation = completion_client.calls[0]["payload"]["modelInvocation"]
    assert model_invocation["routingPolicy"] == {
        "dimension": "tenant",
        "key": "finance",
        "env": "LLM_MODEL_ROUTE_TENANT_FINANCE",
        "candidates": ["tenant-model", "gpt-4.1-mini"],
    }


class RecordingRetriever:
    def search(self, *, question, query_vector, top_k, context):
        return [
            EvidenceChunk(
                chunk_id="doc_990004_chunk_1",
                source_title="rag.txt",
                content="Receivables aging requires follow-up.",
                score=0.92,
            )
        ]


class RecordingCompletionClient:
    def __init__(self):
        self.calls = []

    def complete(self, *, task_id, payload):
        self.calls.append({"task_id": task_id, "payload": payload})
        return {"taskId": task_id, "status": "completed", "versionId": 12}


class RecordingLlmProvider:
    def generate(self, request):
        return RecordingGenerationResult()


class RecordingGenerationResult:
    content = "Online model generated report section."
    provider = "openai-compatible"
    model = "gpt-4.1-mini"
    status = "succeeded"
    prompt_tokens = 11
    completion_tokens = 7
    total_tokens = 18
    latency_ms = 123
    fallback_used = False
    error_message = None


class RecordingFailoverLlmProvider:
    def generate(self, request):
        return RecordingFailoverGenerationResult()


class RecordingFailoverGenerationResult:
    content = "Secondary model generated report section."
    provider = "openai-compatible"
    model = "qwen-plus"
    status = "succeeded"
    prompt_tokens = 10
    completion_tokens = 7
    total_tokens = 17
    latency_ms = 156
    fallback_used = False
    error_message = None


class RecordingContextAwareLlmProvider:
    def __init__(self):
        self.requests = []

    def generate(self, request):
        self.requests.append(request)
        return RecordingContextAwareGenerationResult()


class RecordingContextAwareGenerationResult:
    content = "Board route generated report section."
    provider = "openai-compatible"
    model = "qwen-plus"
    status = "succeeded"
    prompt_tokens = 14
    completion_tokens = 9
    total_tokens = 23
    latency_ms = 167
    fallback_used = False
    error_message = None


class RecordingRoutedLlmProvider:
    def generate(self, request):
        return RecordingRoutedGenerationResult()


class RecordingRoutedGenerationResult:
    content = "Tenant routed generated report section."
    provider = "openai-compatible"
    model = "tenant-model"
    status = "succeeded"
    prompt_tokens = 16
    completion_tokens = 9
    total_tokens = 25
    latency_ms = 141
    fallback_used = False
    error_message = None
    routing_policy = {
        "dimension": "tenant",
        "key": "finance",
        "env": "LLM_MODEL_ROUTE_TENANT_FINANCE",
        "candidates": ["tenant-model", "gpt-4.1-mini"],
    }
