import json
import os
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from app.rag_retrieval.application.local_rag_runtime import EvidenceChunk, LocalRagRuntime


@dataclass(frozen=True)
class LlmGenerationRequest:
    question: str
    chunks: list[EvidenceChunk]
    trace_id: str | None = None
    context: dict[str, Any] | None = None


@dataclass(frozen=True)
class LlmGenerationResult:
    content: str
    provider: str
    model: str
    status: str
    latency_ms: int
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None
    fallback_used: bool = False
    error_message: str | None = None
    routing_policy: dict[str, Any] | None = None


class OpenAiCompatibleProvider:
    def __init__(
        self,
        *,
        provider: str,
        model: str,
        model_candidates: list[str] | None = None,
        base_url: str,
        api_key: str,
        timeout: float = 30.0,
        fallback_runtime: LocalRagRuntime | None = None,
    ):
        self.provider = provider
        self.model = model
        self.model_candidates = model_candidates or [model]
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout
        self.fallback_runtime = fallback_runtime or LocalRagRuntime()

    def generate(self, request: LlmGenerationRequest) -> LlmGenerationResult:
        if self.provider != "openai-compatible" or not self.api_key:
            return self._fallback(request)

        started = time.monotonic()
        failures: list[str] = []
        route = configured_route_model(request.context or {})
        candidates = route["candidates"] or self.model_candidates
        routing_policy = route["policy"]
        for candidate in candidates:
            try:
                body = self._invoke_candidate(candidate, request)
            except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as exc:
                failures.append(f"{candidate}: {exc}")
                continue

            usage = body.get("usage") if isinstance(body, dict) else {}
            return LlmGenerationResult(
                content=self._content_from_response(body),
                provider=self.provider,
                model=candidate,
                status="succeeded",
                latency_ms=int((time.monotonic() - started) * 1000),
                prompt_tokens=self._int_or_none(usage, "prompt_tokens"),
                completion_tokens=self._int_or_none(usage, "completion_tokens"),
                total_tokens=self._int_or_none(usage, "total_tokens"),
                routing_policy=routing_policy,
            )

        fallback = self._fallback(request)
        return LlmGenerationResult(
            content=fallback.content,
            provider=fallback.provider,
            model=fallback.model,
            status="fallback_succeeded",
            latency_ms=int((time.monotonic() - started) * 1000),
            fallback_used=True,
            error_message=" | ".join(failures) if failures else None,
            routing_policy=routing_policy,
        )

    def _model_candidates_for_request(self, request: LlmGenerationRequest) -> list[str]:
        route_candidates = configured_route_model_candidates(request.context or {})
        return route_candidates or self.model_candidates

    def _invoke_candidate(self, model: str, request: LlmGenerationRequest) -> dict[str, Any]:
        payload = {
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "You generate concise enterprise report sections. "
                        "Use only the provided evidence and keep citations grounded."
                    ),
                },
                {"role": "user", "content": self._user_prompt(request)},
            ],
            "temperature": 0.2,
        }
        http_request = urllib.request.Request(
            f"{self.base_url}/chat/completions",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Authorization": "Bearer " + self.api_key,
                "Content-Type": "application/json",
            },
            method="POST",
        )
        with urllib.request.urlopen(http_request, timeout=self.timeout) as response:
            return json.loads(response.read().decode("utf-8"))

    def _fallback(self, request: LlmGenerationRequest) -> LlmGenerationResult:
        started = time.monotonic()
        return LlmGenerationResult(
            content=self.fallback_runtime.answer_delta(request.question, request.chunks),
            provider="local-fallback",
            model="local-rag-fallback",
            status="succeeded",
            latency_ms=int((time.monotonic() - started) * 1000),
            fallback_used=True,
        )

    def _user_prompt(self, request: LlmGenerationRequest) -> str:
        evidence = []
        for index, chunk in enumerate(request.chunks, start=1):
            evidence.append(
                f"[{index}] source={chunk.source_title}; id={chunk.chunk_id}; "
                f"quality={chunk.score:.4f}; content={chunk.content}"
            )
        return "Question:\n" + request.question + "\n\nEvidence:\n" + "\n".join(evidence)

    @staticmethod
    def _content_from_response(body: dict[str, Any]) -> str:
        choices = body.get("choices") if isinstance(body, dict) else []
        if not choices:
            return ""
        message = choices[0].get("message") if isinstance(choices[0], dict) else {}
        return str(message.get("content") or "")

    @staticmethod
    def _int_or_none(value: Any, key: str) -> int | None:
        if not isinstance(value, dict) or value.get(key) is None:
            return None
        return int(value[key])


def configured_llm_provider() -> OpenAiCompatibleProvider:
    provider = os.getenv("LLM_PROVIDER", "openai-compatible").strip() or "openai-compatible"
    model = os.getenv("LLM_MODEL", "gpt").strip() or "gpt"
    model_candidates = configured_model_candidates(model)
    base_url = os.getenv("LLM_BASE_URL", "").strip() or "https://api.openai.com/v1"
    api_key = first_present_env("LLM_API_KEY", "OPENAI_API_KEY", "CUSTOM_LLM_API_KEY", "AI_API_KEY")
    timeout = float(os.getenv("LLM_TIMEOUT_SECONDS", "30"))
    return OpenAiCompatibleProvider(
        provider=provider,
        model=model,
        model_candidates=model_candidates,
        base_url=base_url,
        api_key=api_key,
        timeout=timeout,
    )


def first_present_env(*names: str) -> str:
    for name in names:
        value = os.getenv(name, "").strip()
        if value:
            return value
    return ""


def configured_model_candidates(primary_model: str) -> list[str]:
    raw = os.getenv("LLM_MODEL_CANDIDATES", "").strip()
    if not raw:
        return [primary_model]

    candidates: list[str] = []
    for item in raw.split(","):
        candidate = item.strip()
        if candidate and candidate not in candidates:
            candidates.append(candidate)
    if primary_model and primary_model not in candidates:
        candidates.insert(0, primary_model)
    return candidates or [primary_model]


def configured_route_model_candidates(context: dict[str, Any]) -> list[str]:
    return configured_route_model(context)["candidates"]


def configured_route_model(context: dict[str, Any]) -> dict[str, Any]:
    for dimension, key in route_keys_from_context(context):
        route_env = "LLM_MODEL_ROUTE_" + dimension.upper() + "_" + sanitize_route_key(key)
        route_candidates = parse_route_candidates(os.getenv(route_env, "").strip())
        if route_candidates:
            return {
                "candidates": route_candidates,
                "policy": {
                    "dimension": dimension,
                    "key": key,
                    "env": route_env,
                    "candidates": route_candidates,
                },
            }
    return {"candidates": [], "policy": None}


def route_keys_from_context(context: dict[str, Any]) -> list[tuple[str, str]]:
    route_keys: list[tuple[str, str]] = []
    for dimension, field_names in (
        ("tenant", ("tenant", "tenantId")),
        ("scenario", ("scenario", "scenarioCode")),
        ("cost", ("costPolicy", "costTier", "cost")),
        ("generation_mode", ("generationMode",)),
    ):
        value = first_context_value(context, *field_names)
        if value:
            route_keys.append((dimension, value))

    template_id = template_id_from_context(context)
    if template_id:
        route_keys.append(("template", template_id))
    return route_keys


def first_context_value(context: dict[str, Any], *names: str) -> str:
    for name in names:
        value = context.get(name)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def generation_mode_from_context(context: dict[str, Any]) -> str:
    generation_mode = context.get("generationMode")
    if generation_mode is None or not str(generation_mode).strip():
        return ""
    return str(generation_mode).strip()


def parse_route_candidates(raw: str) -> list[str]:
    if not raw:
        return []
    candidates: list[str] = []
    for item in raw.split(","):
        candidate = item.strip()
        if candidate and candidate not in candidates:
            candidates.append(candidate)
    return candidates


def template_id_from_context(context: dict[str, Any]) -> str:
    template_snapshot = context.get("templateSnapshot")
    if isinstance(template_snapshot, dict):
        template_id = template_snapshot.get("templateId")
        if template_id is not None and str(template_id).strip():
            return str(template_id).strip()

    template_id = context.get("templateId")
    if template_id is None or not str(template_id).strip():
        return ""
    return str(template_id).strip()


def sanitize_route_key(value: str) -> str:
    characters: list[str] = []
    for char in value.strip():
        if char.isalnum():
            characters.append(char.upper())
        else:
            characters.append("_")
    return "".join(characters)
