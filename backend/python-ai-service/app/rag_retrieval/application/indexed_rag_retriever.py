import json
import os
import urllib.parse
import urllib.request
from typing import Any

from app.rag_retrieval.application.local_rag_runtime import EvidenceChunk


class IndexedRagRetriever:
    def __init__(self, search_index: Any | None = None, vector_index: Any | None = None):
        self.search_index = search_index
        self.vector_index = vector_index

    def search(self, *, question: str, query_vector: list[float], top_k: int, context: dict | None = None) -> list[EvidenceChunk]:
        knowledge_base_id = (context or {}).get("knowledgeBaseId")
        hits: dict[str, EvidenceChunk] = {}

        if self.search_index is not None:
            for hit in self.search_index.search(query=question, top_k=top_k, knowledge_base_id=knowledge_base_id):
                chunk = to_evidence_chunk(hit)
                hits[chunk.chunk_id] = chunk

        if self.vector_index is not None:
            for hit in self.vector_index.search(query_vector=query_vector, top_k=top_k, knowledge_base_id=knowledge_base_id):
                chunk = to_evidence_chunk(hit)
                current = hits.get(chunk.chunk_id)
                if current is None or chunk.score > current.score:
                    hits[chunk.chunk_id] = chunk

        return sorted(hits.values(), key=lambda chunk: chunk.score, reverse=True)[: max(top_k, 1)]


class OpenSearchKnowledgeIndex:
    def __init__(self, base_url: str, index_name: str = "knowledge_entries_text", timeout: float = 5.0):
        self.base_url = base_url.rstrip("/")
        self.index_name = index_name
        self.timeout = timeout

    def search(self, *, query: str, top_k: int, knowledge_base_id: int | None = None) -> list[dict]:
        must: list[dict] = [{"multi_match": {"query": query, "fields": ["title^2", "content"]}}]
        filters: list[dict] = []
        if knowledge_base_id is not None:
            filters.append({"term": {"knowledgeBaseId": int(knowledge_base_id)}})

        request_body = {
            "size": max(top_k, 1),
            "query": {
                "bool": {
                    "must": must,
                    "filter": filters,
                }
            },
        }
        request = urllib.request.Request(
            f"{self.base_url}/{urllib.parse.quote(self.index_name)}/_search",
            data=json.dumps(request_body, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            payload = json.loads(response.read().decode("utf-8"))
        return [
            {
                "chunkId": hit["_source"].get("chunkId") or hit["_id"],
                "sourceTitle": hit["_source"].get("title") or "Knowledge evidence",
                "content": hit["_source"].get("content") or "",
                "score": normalize_score(hit.get("_score")),
            }
            for hit in payload.get("hits", {}).get("hits", [])
        ]


class MilvusKnowledgeVectorIndex:
    def __init__(self, client: Any, collection_name: str = "knowledge_chunks"):
        self.client = client
        self.collection_name = collection_name

    def search(self, *, query_vector: list[float], top_k: int, knowledge_base_id: int | None = None) -> list[dict]:
        if not self.client.has_collection(self.collection_name):
            return []

        results = self.client.search(
            collection_name=self.collection_name,
            data=[[float(value) for value in query_vector]],
            limit=max(top_k, 1),
            output_fields=["chunk_id", "document_id", "chunk_index", "content", "embedding_model"],
        )
        hits = results[0] if results else []
        return [
            {
                "chunkId": hit.get("entity", {}).get("chunk_id") or hit.get("id"),
                "sourceTitle": f"Document {hit.get('entity', {}).get('document_id')}",
                "content": hit.get("entity", {}).get("content") or "",
                "score": normalize_score(hit.get("distance") or hit.get("score")),
            }
            for hit in hits
        ]


def configured_indexed_retriever() -> IndexedRagRetriever | None:
    search_index = None
    vector_index = None

    opensearch_url = os.getenv("OPENSEARCH_URL", "").strip()
    if opensearch_url:
        search_index = OpenSearchKnowledgeIndex(opensearch_url)

    milvus_host = os.getenv("MILVUS_HOST", "").strip()
    milvus_port = os.getenv("MILVUS_PORT", "19530").strip()
    if milvus_host:
        from pymilvus import MilvusClient

        vector_index = MilvusKnowledgeVectorIndex(MilvusClient(uri=f"http://{milvus_host}:{milvus_port}"))

    if search_index is None and vector_index is None:
        return None
    return IndexedRagRetriever(search_index=search_index, vector_index=vector_index)


def to_evidence_chunk(hit: dict) -> EvidenceChunk:
    return EvidenceChunk(
        chunk_id=str(hit.get("chunkId") or hit.get("referenceId")),
        source_title=str(hit.get("sourceTitle") or hit.get("title") or "Knowledge evidence"),
        content=str(hit.get("content") or hit.get("snapshot") or ""),
        score=float(hit.get("score") or hit.get("qualityScore") or 0.0),
    )


def normalize_score(value: Any) -> float:
    if value is None:
        return 0.0
    score = float(value)
    if score > 1.0:
        return round(score / (score + 1.0), 6)
    return round(max(score, 0.0), 6)
