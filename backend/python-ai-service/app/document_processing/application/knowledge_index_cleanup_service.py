from collections.abc import Callable
from typing import Any

from app.document_processing.application.parse_indexing_service import execute


class KnowledgeIndexCleanupService:
    """Delete search/vector index entries after a knowledge item is deleted."""

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
        if event.get("eventType") != "knowledge.item.deleted":
            return {
                "eventType": "knowledge.item.cleanup_skipped",
                "eventKey": str(event.get("eventKey") or ""),
                "payload": {"reason": "unsupported_event"},
            }
        payload = event.get("payload") or {}
        knowledge_item_id = int(payload["knowledgeItemId"])
        chunk_refs = self._load_chunk_refs(knowledge_item_id)
        chunk_ids = [chunk["chunkId"] for chunk in chunk_refs]
        persistence_status = self._soft_delete_chunks(knowledge_item_id, [chunk["rowId"] for chunk in chunk_refs])
        search_status = self._delete_search_documents(chunk_ids)
        vector_status = self._delete_vectors(chunk_ids)
        return {
            "eventType": "knowledge.item.cleanup_completed",
            "eventKey": str(event.get("eventKey") or knowledge_item_id),
            "payload": {
                "knowledgeItemId": knowledge_item_id,
                "chunks": len(chunk_refs),
                "searchIndexStatus": search_status,
                "vectorIndexStatus": vector_status,
                "persistenceStatus": persistence_status,
            },
        }

    def _load_chunk_refs(self, knowledge_item_id: int) -> list[dict[str, Any]]:
        if self.db_connection_factory is None:
            return []
        connection = self.db_connection_factory()
        rows = execute(
            connection,
            """
            SELECT c.id AS row_id, c.document_id, c.chunk_index, e.milvus_primary_key
            FROM document_chunks c
            LEFT JOIN embeddings e ON e.chunk_id = c.id
            WHERE c.knowledge_item_id = ? AND c.deleted_at IS NULL
            ORDER BY c.id
            """,
            (knowledge_item_id,),
        ).fetchall()
        return [
            {
                "rowId": int(row["row_id"] if row_has_key(row, "row_id") else row[0]),
                "chunkId": str((row["milvus_primary_key"] if row_has_key(row, "milvus_primary_key") else row[3])
                               or f"doc_{row['document_id'] if row_has_key(row, 'document_id') else row[1]}_chunk_{row['chunk_index'] if row_has_key(row, 'chunk_index') else row[2]}"),
            }
            for row in rows
        ]

    def _soft_delete_chunks(self, knowledge_item_id: int, row_ids: list[int]) -> str:
        if self.db_connection_factory is None:
            return "skipped"
        connection = self.db_connection_factory()
        try:
            if row_ids:
                placeholders = ",".join("?" for _ in row_ids)
                sql = f"DELETE FROM embeddings WHERE chunk_id IN ({placeholders})"
                execute(connection, sql, tuple(row_ids))
            execute(
                connection,
                """
                UPDATE document_chunks
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE knowledge_item_id = ? AND deleted_at IS NULL
                """,
                (knowledge_item_id,),
            )
            connection.commit()
            return "soft_deleted"
        except Exception:
            connection.rollback()
            raise

    def _delete_search_documents(self, chunk_ids: list[str]) -> str:
        if self.search_index is None:
            return "skipped"
        for chunk_id in chunk_ids:
            self.search_index.delete_document(index="knowledge_entries_text", document_id=chunk_id)
        return "deleted"

    def _delete_vectors(self, chunk_ids: list[str]) -> str:
        if self.vector_index is None:
            return "skipped"
        self.vector_index.delete_chunks(chunk_ids)
        return "deleted"


def row_has_key(row: Any, key: str) -> bool:
    try:
        row[key]
        return True
    except Exception:
        return False
