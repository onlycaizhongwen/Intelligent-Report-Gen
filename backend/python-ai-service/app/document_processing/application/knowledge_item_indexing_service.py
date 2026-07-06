import hashlib
import json
from collections.abc import Callable
from typing import Any

from app.document_processing.application.document_processor import DocumentProcessor
from app.document_processing.application.parse_indexing_service import execute, last_insert_id


class KnowledgeItemIndexingService:
    """Index manually entered or batch-imported knowledge items into DB, OpenSearch and Milvus."""

    def __init__(
        self,
        db_connection_factory: Callable[[], Any] | None = None,
        search_index: Any | None = None,
        vector_index: Any | None = None,
    ):
        self.db_connection_factory = db_connection_factory
        self.search_index = search_index
        self.vector_index = vector_index

    async def handle(self, event: dict) -> dict:
        if event.get("eventType") != "knowledge.item.index_requested":
            return {
                "eventType": "knowledge.item.index_skipped",
                "eventKey": str(event.get("eventKey") or ""),
                "payload": {"reason": "unsupported_event"},
            }
        payload = event.get("payload") or {}
        knowledge_item_id = int(payload["knowledgeItemId"])
        knowledge_base_id = payload.get("knowledgeBaseId")
        chunks = build_chunks(payload)
        persistence_status = self._persist_chunks(knowledge_item_id, chunks)
        search_status = self._index_search(knowledge_item_id, knowledge_base_id, payload, chunks)
        return {
            "eventType": "knowledge.item.index_completed",
            "eventKey": str(event.get("eventKey") or knowledge_item_id),
            "payload": {
                "knowledgeItemId": knowledge_item_id,
                "knowledgeBaseId": knowledge_base_id,
                "chunks": len(chunks),
                "persistenceStatus": persistence_status,
                "searchIndexStatus": search_status,
            },
        }

    def _persist_chunks(self, knowledge_item_id: int, chunks: list[dict]) -> str:
        if self.db_connection_factory is None:
            self._index_vectors(chunks)
            return "skipped"
        connection = self.db_connection_factory()
        try:
            chunk_rows = []
            for chunk in chunks:
                cursor = execute(
                    connection,
                    """
                    INSERT INTO document_chunks
                    (document_id, knowledge_item_id, chunk_index, content, position_payload, parse_confidence, embedding_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        int(chunk["documentId"]),
                        knowledge_item_id,
                        int(chunk["chunkIndex"]),
                        str(chunk["content"]),
                        json.dumps({"sourceTitle": chunk.get("sourceTitle")}, ensure_ascii=False),
                        float(chunk.get("parseConfidence") or 0.0),
                        str(chunk.get("embeddingStatus") or "pending"),
                    ),
                )
                chunk_rows.append(last_insert_id(cursor, connection))
            vector_refs = self._index_vectors(chunks)
            for chunk, row_id, vector_ref in zip(chunks, chunk_rows, vector_refs):
                execute(
                    connection,
                    """
                    INSERT INTO embeddings
                    (chunk_id, embedding_model, vector_dimension, milvus_collection, milvus_primary_key, content_hash)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (
                        row_id,
                        str(chunk["embeddingModel"]),
                        int(chunk["embeddingDimension"]),
                        "knowledge_chunks",
                        vector_ref,
                        hashlib.sha256(str(chunk["content"]).encode("utf-8")).hexdigest(),
                    ),
                )
            connection.commit()
            return "persisted"
        except Exception:
            connection.rollback()
            raise

    def _index_vectors(self, chunks: list[dict]) -> list[str]:
        refs = []
        for chunk in chunks:
            if self.vector_index is None:
                refs.append(str(chunk["vectorRef"]))
            else:
                refs.append(str(self.vector_index.upsert_chunk(chunk)))
        return refs

    def _index_search(self, knowledge_item_id: int, knowledge_base_id: Any, payload: dict, chunks: list[dict]) -> str:
        if self.search_index is None:
            return "skipped"
        for chunk in chunks:
            self.search_index.index_document(
                index="knowledge_entries_text",
                document_id=str(chunk["chunkId"]),
                body={
                    "documentId": int(chunk["documentId"]),
                    "knowledgeBaseId": knowledge_base_id,
                    "knowledgeItemId": knowledge_item_id,
                    "chunkId": chunk["chunkId"],
                    "title": payload.get("title"),
                    "content": chunk["content"],
                    "sourceType": payload.get("sourceType") or "manual_knowledge_item",
                    "embeddingModel": chunk.get("embeddingModel"),
                    "vectorRef": chunk.get("vectorRef"),
                },
            )
        return "indexed"


def build_chunks(payload: dict) -> list[dict]:
    knowledge_item_id = int(payload["knowledgeItemId"])
    title = str(payload.get("title") or f"knowledge item {knowledge_item_id}")
    processor = DocumentProcessor(object_loader=lambda _: str(payload.get("content") or ""))
    raw_chunks = processor._chunk_text(str(payload.get("content") or ""))
    return [
        {
            **processor._to_chunk_payload(knowledge_item_id, f"knowledge-item/{knowledge_item_id}", index, content),
            "chunkId": f"item_{knowledge_item_id}_chunk_{index}",
            "sourceTitle": title,
        }
        for index, content in enumerate(raw_chunks)
    ]
