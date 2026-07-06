import json
import os
import urllib.request
from datetime import datetime, timedelta, timezone
from typing import Any

from jose import jwt

from app.llm_orchestration.application.openai_compatible_provider import LlmGenerationRequest, configured_llm_provider
from app.rag_retrieval.application.indexed_rag_retriever import configured_indexed_retriever
from app.rag_retrieval.application.local_rag_runtime import LocalRagRuntime


class ReportGenerationWorker:
    def __init__(
        self,
        rag_runtime: LocalRagRuntime | None = None,
        completion_client: Any | None = None,
        llm_provider: Any | None = None,
    ):
        self.rag_runtime = rag_runtime or LocalRagRuntime(retriever=configured_indexed_retriever())
        self.completion_client = completion_client or configured_completion_client()
        self.llm_provider = llm_provider or configured_llm_provider()

    async def handle(self, event: dict) -> dict:
        payload = event.get("payload") or {}
        task_id = int(payload["taskId"])
        question = str(payload.get("question") or payload.get("topic") or "")
        context = payload.get("context") if isinstance(payload.get("context"), dict) else {}
        chunks = self.rag_runtime.retrieve(question, context, int(payload.get("topK") or 5))
        references, trace_id = self.rag_runtime.references(str(task_id), chunks)
        generation = self.llm_provider.generate(
            LlmGenerationRequest(question=question, chunks=chunks, trace_id=trace_id, context=context)
        )
        sections = [
            {
                "heading": "Executive summary",
                "content": generation.content,
                "citations": references,
            }
        ]
        completion_payload = {
            "sections": sections,
            "references": references,
            "modelInvocation": {
                "provider": generation.provider,
                "model": generation.model,
                "status": generation.status,
                "traceId": trace_id,
                "promptTokens": generation.prompt_tokens,
                "completionTokens": generation.completion_tokens,
                "inputTokens": generation.prompt_tokens,
                "outputTokens": generation.completion_tokens,
                "totalTokens": generation.total_tokens,
                "latencyMs": generation.latency_ms,
                "fallbackUsed": generation.fallback_used,
                "errorMessage": generation.error_message,
                "routingPolicy": getattr(generation, "routing_policy", None),
            },
        }
        callback_result = self.completion_client.complete(task_id=task_id, payload=completion_payload)
        return {
            "eventType": "report.generation.completed",
            "eventKey": str(event.get("eventKey") or task_id),
            "payload": {
                "taskId": task_id,
                "reportId": payload.get("reportId"),
                "sections": len(sections),
                "references": len(references),
                "callbackResult": callback_result,
            },
        }


class JavaCompletionClient:
    def __init__(self, base_url: str, token: str | None = None, timeout: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout = timeout

    def complete(self, *, task_id: int, payload: dict) -> dict:
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        request = urllib.request.Request(
            f"{self.base_url}/api/v1/reports/generation-tasks/{task_id}/completion",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            body = json.loads(response.read().decode("utf-8"))
        return body.get("data") if isinstance(body, dict) and "data" in body else body


def configured_completion_client() -> JavaCompletionClient:
    base_url = os.getenv("JAVA_SERVICE_URL", "").strip() or os.getenv("REPORT_CORE_URL", "").strip()
    if not base_url:
        raise RuntimeError("JAVA_SERVICE_URL is required for report generation completion callback")
    token = os.getenv("JAVA_SERVICE_TOKEN", "").strip() or generated_service_token()
    if not token:
        raise RuntimeError("JAVA_SERVICE_TOKEN or JWT_SECRET is required for report generation completion callback")
    return JavaCompletionClient(base_url=base_url, token=token)


def generated_service_token() -> str | None:
    secret = os.getenv("JWT_SECRET", "").strip()
    if not secret:
        return None

    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(seconds=int(os.getenv("JAVA_SERVICE_TOKEN_TTL_SECONDS", "7200")))
    payload = {
        "sub": os.getenv("JAVA_SERVICE_TOKEN_SUBJECT", "1"),
        "roles": csv_env("JAVA_SERVICE_TOKEN_ROLES", ["ADMIN"]),
        "permissions": csv_env("JAVA_SERVICE_TOKEN_PERMISSIONS", ["report:create"]),
        "status": os.getenv("JAVA_SERVICE_TOKEN_STATUS", "enabled").strip() or "enabled",
        "iat": int(now.timestamp()),
        "exp": int(expires_at.timestamp()),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def csv_env(name: str, default: list[str]) -> list[str]:
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    return [item.strip() for item in raw.split(",") if item.strip()]
