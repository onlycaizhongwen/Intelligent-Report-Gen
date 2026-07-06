import json
import os
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

from app.llm_orchestration.application.openai_compatible_provider import (
    LlmGenerationRequest,
    configured_llm_provider,
)
from app.rag_retrieval.application.local_rag_runtime import EvidenceChunk


def test_configured_provider_falls_back_when_api_key_is_missing(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt")
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("CUSTOM_LLM_API_KEY", raising=False)
    monkeypatch.delenv("AI_API_KEY", raising=False)

    result = configured_llm_provider().generate(
        LlmGenerationRequest(
            question="Summarize risk",
            chunks=[EvidenceChunk("chunk-1", "Risk memo", "Receivables risk increased.", 0.9)],
        )
    )

    assert result.provider == "local-fallback"
    assert result.model == "local-rag-fallback"
    assert result.fallback_used is True
    assert "Receivables risk increased." in result.content


def test_openai_compatible_provider_posts_chat_completions(monkeypatch):
    server = RecordingOpenAiServer()
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.setenv("LLM_API_KEY", "test-key")
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("CUSTOM_LLM_API_KEY", raising=False)
    monkeypatch.delenv("AI_API_KEY", raising=False)

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize receivables",
                chunks=[
                    EvidenceChunk(
                        "doc_1_chunk_0",
                        "Receivables memo",
                        "Receivables aging requires follow-up.",
                        0.94,
                    )
                ],
                trace_id="trace-test",
            )
        )
    finally:
        server.stop()

    assert result.provider == "openai-compatible"
    assert result.model == "gpt-4.1-mini"
    assert result.content == "Online model summary."
    assert result.prompt_tokens == 11
    assert result.completion_tokens == 7
    assert server.requests[0]["path"] == "/chat/completions"
    assert server.requests[0]["authorization"] == "Bearer test-key"
    assert server.requests[0]["body"]["model"] == "gpt-4.1-mini"
    assert "Receivables aging requires follow-up." in server.requests[0]["body"]["messages"][1]["content"]


def test_configured_provider_fails_over_to_secondary_model_candidate(monkeypatch):
    server = RecordingOpenAiServer(
        responses_by_model={
            "gpt-4.1-mini": {
                "status": 500,
                "body": {"error": {"message": "primary model unavailable"}},
            },
            "qwen-plus": {
                "status": 200,
                "body": {
                    "choices": [{"message": {"content": "Secondary model summary."}}],
                    "usage": {"prompt_tokens": 13, "completion_tokens": 9, "total_tokens": 22},
                },
            },
        }
    )
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_CANDIDATES", "gpt-4.1-mini,qwen-plus")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.setenv("LLM_API_KEY", "test-key")

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize receivables",
                chunks=[
                    EvidenceChunk(
                        "doc_1_chunk_0",
                        "Receivables memo",
                        "Receivables aging requires follow-up.",
                        0.94,
                    )
                ],
                trace_id="trace-failover",
            )
        )
    finally:
        server.stop()

    assert result.provider == "openai-compatible"
    assert result.model == "qwen-plus"
    assert result.content == "Secondary model summary."
    assert result.fallback_used is False
    assert result.total_tokens == 22
    assert result.error_message is None
    assert [request["body"]["model"] for request in server.requests] == ["gpt-4.1-mini", "qwen-plus"]


def test_configured_provider_uses_local_fallback_after_all_model_candidates_fail(monkeypatch):
    server = RecordingOpenAiServer(
        responses_by_model={
            "gpt-4.1-mini": {
                "status": 500,
                "body": {"error": {"message": "primary model unavailable"}},
            },
            "qwen-plus": {
                "status": 503,
                "body": {"error": {"message": "secondary model unavailable"}},
            },
        }
    )
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_CANDIDATES", "gpt-4.1-mini,qwen-plus")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.setenv("LLM_API_KEY", "test-key")

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize receivables",
                chunks=[
                    EvidenceChunk(
                        "doc_1_chunk_0",
                        "Receivables memo",
                        "Receivables aging requires follow-up.",
                        0.94,
                    )
                ],
                trace_id="trace-double-fail",
            )
        )
    finally:
        server.stop()

    assert result.provider == "local-fallback"
    assert result.model == "local-rag-fallback"
    assert result.fallback_used is True
    assert "Receivables aging requires follow-up." in result.content
    assert "gpt-4.1-mini" in str(result.error_message)
    assert "qwen-plus" in str(result.error_message)
    assert [request["body"]["model"] for request in server.requests] == ["gpt-4.1-mini", "qwen-plus"]


def test_configured_provider_uses_standard_openai_api_key_alias_when_llm_api_key_missing(monkeypatch):
    server = RecordingOpenAiServer()
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "alias-openai-key")
    monkeypatch.delenv("CUSTOM_LLM_API_KEY", raising=False)
    monkeypatch.delenv("AI_API_KEY", raising=False)

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize margin trend",
                chunks=[EvidenceChunk("doc_2_chunk_0", "Margin memo", "Gross margin improved.", 0.91)],
            )
        )
    finally:
        server.stop()

    assert result.provider == "openai-compatible"
    assert server.requests[0]["authorization"] == "Bearer alias-openai-key"


def test_configured_provider_uses_local_custom_api_key_alias_when_llm_api_key_missing(monkeypatch):
    server = RecordingOpenAiServer()
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setenv("CUSTOM_LLM_API_KEY", "alias-custom-key")
    monkeypatch.delenv("AI_API_KEY", raising=False)

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize backlog risk",
                chunks=[EvidenceChunk("doc_3_chunk_0", "Backlog memo", "Backlog risk increased.", 0.88)],
            )
        )
    finally:
        server.stop()

    assert result.provider == "openai-compatible"
    assert server.requests[0]["authorization"] == "Bearer alias-custom-key"


def test_configured_provider_routes_model_candidates_by_template_id(monkeypatch):
    server = RecordingOpenAiServer(
        responses_by_model={
            "qwen-plus": {
                "status": 200,
                "body": {
                    "choices": [{"message": {"content": "Template specific model summary."}}],
                    "usage": {"prompt_tokens": 12, "completion_tokens": 8, "total_tokens": 20},
                },
            }
        }
    )
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_CANDIDATES", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_ROUTE_TEMPLATE_ENTERPRISE_BOARD", "qwen-plus,gpt-4.1-mini")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.setenv("LLM_API_KEY", "test-key")

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize board report risks",
                chunks=[
                    EvidenceChunk(
                        "doc_4_chunk_0",
                        "Board memo",
                        "Board pack requires executive phrasing.",
                        0.95,
                    )
                ],
                trace_id="trace-template-route",
                context={"templateId": "enterprise-board"},
            )
        )
    finally:
        server.stop()

    assert result.provider == "openai-compatible"
    assert result.model == "qwen-plus"
    assert result.content == "Template specific model summary."
    assert [request["body"]["model"] for request in server.requests] == ["qwen-plus"]


def test_configured_provider_routes_model_candidates_by_generation_mode(monkeypatch):
    server = RecordingOpenAiServer(
        responses_by_model={
            "deepseek-r1": {
                "status": 200,
                "body": {
                    "choices": [{"message": {"content": "Generation mode specific model summary."}}],
                    "usage": {"prompt_tokens": 15, "completion_tokens": 10, "total_tokens": 25},
                },
            }
        }
    )
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_CANDIDATES", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_ROUTE_GENERATION_MODE_TEMPLATE", "deepseek-r1,gpt-4.1-mini")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.setenv("LLM_API_KEY", "test-key")

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize template report risks",
                chunks=[
                    EvidenceChunk(
                        "doc_5_chunk_0",
                        "Template memo",
                        "Template generation needs more deliberate reasoning.",
                        0.93,
                    )
                ],
                trace_id="trace-mode-route",
                context={"generationMode": "template"},
            )
        )
    finally:
        server.stop()

    assert result.provider == "openai-compatible"
    assert result.model == "deepseek-r1"
    assert result.content == "Generation mode specific model summary."
    assert [request["body"]["model"] for request in server.requests] == ["deepseek-r1"]


def test_configured_provider_routes_model_candidates_by_tenant_scenario_and_cost_policy(monkeypatch):
    server = RecordingOpenAiServer(
        responses_by_model={
            "tenant-model": {
                "status": 200,
                "body": {
                    "choices": [{"message": {"content": "Tenant routed summary."}}],
                    "usage": {"prompt_tokens": 17, "completion_tokens": 11, "total_tokens": 28},
                },
            }
        }
    )
    server.start()
    monkeypatch.setenv("LLM_PROVIDER", "openai-compatible")
    monkeypatch.setenv("LLM_MODEL", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_CANDIDATES", "gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_ROUTE_TENANT_FINANCE", "tenant-model,gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_ROUTE_SCENARIO_BOARD_REVIEW", "scenario-model,gpt-4.1-mini")
    monkeypatch.setenv("LLM_MODEL_ROUTE_COST_LOW", "cheap-model,gpt-4.1-mini")
    monkeypatch.setenv("LLM_BASE_URL", server.base_url)
    monkeypatch.setenv("LLM_API_KEY", "test-key")

    try:
        result = configured_llm_provider().generate(
            LlmGenerationRequest(
                question="Summarize tenant risks",
                chunks=[EvidenceChunk("doc_6_chunk_0", "Tenant memo", "Tenant-specific policy applies.", 0.91)],
                trace_id="trace-tenant-route",
                context={"tenant": "finance", "scenario": "board-review", "costPolicy": "low"},
            )
        )
    finally:
        server.stop()

    assert result.provider == "openai-compatible"
    assert result.model == "tenant-model"
    assert result.routing_policy == {
        "dimension": "tenant",
        "key": "finance",
        "env": "LLM_MODEL_ROUTE_TENANT_FINANCE",
        "candidates": ["tenant-model", "gpt-4.1-mini"],
    }
    assert [request["body"]["model"] for request in server.requests] == ["tenant-model"]


class RecordingOpenAiServer:
    def __init__(self, responses_by_model=None):
        self.requests = []
        self.responses_by_model = responses_by_model or {}
        self.httpd = HTTPServer(("127.0.0.1", 0), self._handler())
        self.base_url = f"http://127.0.0.1:{self.httpd.server_port}"
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)

    def start(self):
        self.thread.start()

    def stop(self):
        self.httpd.shutdown()
        self.thread.join(timeout=2)
        self.httpd.server_close()

    def _handler(self):
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers.get("content-length", "0"))).decode("utf-8"))
                model = body.get("model")
                outer.requests.append(
                    {
                        "path": self.path,
                        "authorization": self.headers.get("Authorization"),
                        "body": body,
                    }
                )
                response = outer.responses_by_model.get(model) or {
                    "status": 200,
                    "body": {
                        "choices": [{"message": {"content": "Online model summary."}}],
                        "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18},
                    },
                }
                self.send_response(response["status"])
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(response["body"]).encode("utf-8"))

            def log_message(self, format, *args):
                return

        return Handler
