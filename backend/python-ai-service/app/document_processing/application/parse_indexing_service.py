import hashlib
import json
import os
import sqlite3
import urllib.error
import urllib.request
from collections.abc import Callable
from typing import Any

from app.document_processing.application.document_processor import DocumentProcessor
from app.document_processing.application.parse_consumer import DocumentParseConsumer


class DocumentParseIndexingService:
    """Parse a document event and persist/index the resulting chunks when adapters are configured."""

    def __init__(
        self,
        db_connection_factory: Callable[[], Any] | None = None,
        search_index: Any | None = None,
        object_loader: Any | None = None,
        vector_index: Any | None = None,
    ):
        self.db_connection_factory = db_connection_factory
        self.search_index = search_index
        self.object_loader = object_loader
        self.vector_index = vector_index

    async def handle(self, event: dict) -> dict:
        payload = event.get("payload") or {}
        processor = DocumentProcessor(object_loader=self._load_text(payload))
        try:
            result = await DocumentParseConsumer(processor).handle(event)
        except Exception as exc:
            result = self._failed_result(event, payload, str(exc))
        result_payload = result.get("payload") or {}
        if result.get("eventType") == "document.parse.failed":
            result_payload["persistenceStatus"] = self._persist(payload, result_payload)
            result_payload["searchIndexStatus"] = "skipped"
            return result
        if result.get("eventType") != "document.parse.completed":
            result_payload["persistenceStatus"] = "skipped"
            result_payload["searchIndexStatus"] = "skipped"
            return result

        persistence_status = self._persist(payload, result_payload)
        result_payload["persistenceStatus"] = persistence_status
        result_payload["searchIndexStatus"] = self._index(payload, result_payload)
        return result

    def _failed_result(self, event: dict, payload: dict, failure_reason: str) -> dict:
        return {
            "eventType": "document.parse.failed",
            "eventKey": str(event.get("eventKey", "")),
            "payload": {
                "documentId": payload.get("documentId"),
                "status": "failed",
                "failureReason": failure_reason,
                "knowledgeChunks": [],
            },
        }

    def _load_text(self, payload: dict) -> Callable[[str], str]:
        def load(object_key: str) -> str:
            if str(payload.get("text", "")).strip():
                return str(payload.get("text"))
            if self.object_loader is None:
                return ""
            return self.object_loader.load(object_key, bucket=payload.get("bucket"))

        return load

    def _persist(self, source_payload: dict, result_payload: dict) -> str:
        if self.db_connection_factory is None:
            return "skipped"
        connection = self.db_connection_factory()
        try:
            self._insert_parse_result(connection, result_payload)
            chunk_ids = self._insert_chunks(connection, result_payload)
            vector_refs = self._index_vectors(result_payload)
            self._insert_embeddings(connection, result_payload, chunk_ids, vector_refs)
            self._update_document_status(connection, result_payload)
            connection.commit()
            return "persisted"
        except Exception:
            connection.rollback()
            raise

    def _insert_parse_result(self, connection: Any, result_payload: dict) -> None:
        execute(
            connection,
            """
            INSERT INTO document_parse_results
            (document_id, parse_type, result_payload, confidence, status, failure_reason)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                int(result_payload["documentId"]),
                "text",
                json.dumps(result_payload, ensure_ascii=False),
                average_confidence(result_payload.get("knowledgeChunks", [])),
                str(result_payload.get("status", "processed")),
                result_payload.get("failureReason"),
            ),
        )

    def _insert_chunks(self, connection: Any, result_payload: dict) -> list[int]:
        chunk_ids: list[int] = []
        for chunk in result_payload.get("knowledgeChunks", []):
            cursor = execute(
                connection,
                """
                INSERT INTO document_chunks
                (document_id, chunk_index, content, position_payload, parse_confidence, embedding_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    int(chunk["documentId"]),
                    int(chunk["chunkIndex"]),
                    str(chunk["content"]),
                    json.dumps({"sourceTitle": chunk.get("sourceTitle")}, ensure_ascii=False),
                    float(chunk.get("parseConfidence") or 0.0),
                    str(chunk.get("embeddingStatus") or "pending"),
                ),
            )
            chunk_ids.append(last_insert_id(cursor, connection))
        return chunk_ids

    def _index_vectors(self, result_payload: dict) -> list[str]:
        refs: list[str] = []
        for chunk in result_payload.get("knowledgeChunks", []):
            if self.vector_index is None:
                refs.append(str(chunk["vectorRef"]))
                continue
            refs.append(str(self.vector_index.upsert_chunk(chunk)))
        return refs

    def _insert_embeddings(self, connection: Any, result_payload: dict, chunk_ids: list[int], vector_refs: list[str]) -> None:
        for chunk, chunk_id, vector_ref in zip(result_payload.get("knowledgeChunks", []), chunk_ids, vector_refs):
            execute(
                connection,
                """
                INSERT INTO embeddings
                (chunk_id, embedding_model, vector_dimension, milvus_collection, milvus_primary_key, content_hash)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    chunk_id,
                    str(chunk["embeddingModel"]),
                    int(chunk["embeddingDimension"]),
                    "knowledge_chunks",
                    vector_ref,
                    hashlib.sha256(str(chunk["content"]).encode("utf-8")).hexdigest(),
                ),
            )

    def _update_document_status(self, connection: Any, result_payload: dict) -> None:
        execute(
            connection,
            """
            UPDATE knowledge_documents
            SET parse_status = ?, parse_failure_reason = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (
                str(result_payload.get("status", "processed")),
                result_payload.get("failureReason"),
                int(result_payload["documentId"]),
            ),
        )

    def _index(self, source_payload: dict, result_payload: dict) -> str:
        if self.search_index is None:
            return "skipped"
        for chunk in result_payload.get("knowledgeChunks", []):
            self.search_index.index_document(
                index="knowledge_entries_text",
                document_id=str(chunk["chunkId"]),
                body={
                    "documentId": int(result_payload["documentId"]),
                    "knowledgeBaseId": source_payload.get("knowledgeBaseId"),
                    "chunkId": chunk["chunkId"],
                    "title": chunk.get("sourceTitle"),
                    "content": chunk["content"],
                    "sourceType": "document_chunk",
                    "embeddingModel": chunk.get("embeddingModel"),
                    "vectorRef": chunk.get("vectorRef"),
                },
            )
        return "indexed"


class OpenSearchIndex:
    def __init__(self, base_url: str, timeout: float = 5.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def index_document(self, *, index: str, document_id: str, body: dict) -> None:
        request = urllib.request.Request(
            f"{self.base_url}/{index}/_doc/{document_id}",
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="PUT",
        )
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            if response.status >= 400:
                raise RuntimeError(f"OpenSearch indexing failed with HTTP {response.status}")

    def delete_document(self, *, index: str, document_id: str) -> None:
        request = urllib.request.Request(
            f"{self.base_url}/{index}/_doc/{document_id}",
            method="DELETE",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                if response.status >= 400:
                    raise RuntimeError(f"OpenSearch delete failed with HTTP {response.status}")
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return
            raise


def configured_search_index() -> OpenSearchIndex | None:
    url = os.getenv("OPENSEARCH_URL", "").strip()
    return OpenSearchIndex(url) if url else None


def configured_db_connection_factory() -> Callable[[], Any] | None:
    database_url = os.getenv("DATABASE_URL", "").strip()
    if not database_url:
        return None

    def connect() -> Any:
        import psycopg

        return psycopg.connect(database_url)

    return connect


def sqlite_connection_factory(path: str) -> Callable[[], sqlite3.Connection]:
    def connect() -> sqlite3.Connection:
        connection = sqlite3.connect(path)
        connection.row_factory = sqlite3.Row
        return connection

    return connect


def execute(connection: Any, sql: str, params: tuple) -> Any:
    statement = sql
    if connection.__class__.__module__.startswith("psycopg"):
        statement = statement.replace("?", "%s")
    return connection.execute(statement, params)


def last_insert_id(cursor: Any, connection: Any) -> int:
    if hasattr(cursor, "lastrowid") and cursor.lastrowid is not None:
        return int(cursor.lastrowid)
    row = connection.execute("SELECT LASTVAL()").fetchone()
    return int(row[0])


def average_confidence(chunks: list[dict]) -> float:
    if not chunks:
        return 0.0
    return sum(float(chunk.get("parseConfidence") or 0.0) for chunk in chunks) / len(chunks)
