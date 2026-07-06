import hashlib
import math
import re
from dataclasses import dataclass
from typing import Any


VECTOR_DIMENSION = 64


@dataclass(frozen=True)
class EvidenceChunk:
    chunk_id: str
    source_title: str
    content: str
    score: float


class LocalRagRuntime:
    """Deterministic local RAG runtime for dev/test and controlled fallback."""

    def __init__(self, retriever: Any | None = None):
        self.retriever = retriever

    def embedding(self, text: str) -> dict[str, Any]:
        normalized = normalize_text(text)
        vector = [0.0 for _ in range(VECTOR_DIMENSION)]
        for token in tokenize(normalized):
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = digest[0] % VECTOR_DIMENSION
            sign = 1.0 if digest[1] % 2 == 0 else -1.0
            vector[index] += sign
        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        vector = [round(value / norm, 6) for value in vector]
        return {
            "model": "local-hash-embedding",
            "dimension": VECTOR_DIMENSION,
            "vectorRef": "emb_" + hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:16],
            "vector": vector,
        }

    def retrieve(self, question: str, context: dict[str, Any] | None, top_k: int = 5) -> list[EvidenceChunk]:
        if self.retriever is not None:
            embedding = self.embedding(question)
            retrieved = self.retriever.search(
                question=question,
                query_vector=embedding["vector"],
                top_k=max(top_k, 1),
                context=context or {},
            )
            if retrieved:
                return retrieved[: max(top_k, 1)]

        chunks = [to_chunk(item) for item in (context or {}).get("knowledgeChunks", [])]
        query_tokens = set(tokenize(question))
        ranked = sorted(
            chunks,
            key=lambda chunk: (overlap_score(query_tokens, chunk), chunk.score),
            reverse=True,
        )
        return ranked[: max(top_k, 1)]

    def references(self, task_id: str, chunks: list[EvidenceChunk]) -> tuple[list[dict[str, Any]], str]:
        references = []
        for chunk in chunks:
            references.append(
                {
                    "referenceId": chunk.chunk_id,
                    "sourceTitle": chunk.source_title,
                    "sourceType": "knowledge_chunk",
                    "snapshot": chunk.content[:240],
                    "qualityScore": round(min(max(chunk.score, 0.0), 1.0), 4),
                }
            )
        joined_ids = "_".join(reference["referenceId"] for reference in references) or "none"
        trace_id = "trace_" + task_id + "_" + hashlib.sha256(joined_ids.encode("utf-8")).hexdigest()[:12]
        return references, trace_id

    def answer_delta(self, question: str, chunks: list[EvidenceChunk]) -> str:
        if not chunks:
            return "No authorized knowledge evidence was found for this request."
        primary = chunks[0]
        return f"Based on {primary.source_title}: {primary.content}"


def to_chunk(value: dict[str, Any]) -> EvidenceChunk:
    chunk_id = str(value.get("chunkId") or value.get("referenceId") or stable_id(value))
    return EvidenceChunk(
        chunk_id=chunk_id,
        source_title=str(value.get("sourceTitle") or value.get("title") or "Knowledge evidence"),
        content=str(value.get("content") or value.get("snapshot") or ""),
        score=float(value.get("score") or value.get("qualityScore") or 0.5),
    )


def stable_id(value: dict[str, Any]) -> str:
    raw = f"{value.get('sourceTitle', '')}:{value.get('content', '')}"
    return "kb_" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:12]


def overlap_score(query_tokens: set[str], chunk: EvidenceChunk) -> float:
    content_tokens = set(tokenize(chunk.content + " " + chunk.source_title))
    if not query_tokens:
        return 0.0
    return len(query_tokens & content_tokens) / len(query_tokens)


def normalize_text(text: str) -> str:
    return " ".join((text or "").strip().lower().split())


def tokenize(text: str) -> list[str]:
    return re.findall(r"[a-z0-9\u4e00-\u9fff]+", normalize_text(text))
