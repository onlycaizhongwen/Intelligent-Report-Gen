import sqlite3

import pytest

from app.document_processing.application.knowledge_index_cleanup_service import KnowledgeIndexCleanupService


@pytest.mark.asyncio
async def test_cleanup_service_deletes_indexes_for_deleted_knowledge_item():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection)
    search_index = RecordingSearchIndex()
    vector_index = RecordingVectorIndex()
    service = KnowledgeIndexCleanupService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        vector_index=vector_index,
    )

    result = await service.handle(
        {
            "eventType": "knowledge.item.deleted",
            "eventKey": "501",
            "payload": {
                "knowledgeItemId": 501,
                "knowledgeBaseId": 7,
                "title": "obsolete policy",
                "referenceCount": 0,
            },
        }
    )

    assert result == {
        "eventType": "knowledge.item.cleanup_completed",
        "eventKey": "501",
        "payload": {
            "knowledgeItemId": 501,
            "chunks": 2,
            "searchIndexStatus": "deleted",
            "vectorIndexStatus": "deleted",
            "persistenceStatus": "soft_deleted",
        },
    }
    assert search_index.deleted_documents == [
        ("knowledge_entries_text", "doc_900_chunk_0"),
        ("knowledge_entries_text", "doc_900_chunk_1"),
    ]
    assert vector_index.deleted == ["doc_900_chunk_0", "doc_900_chunk_1"]
    assert connection.execute("SELECT COUNT(*) FROM embeddings").fetchone()[0] == 0
    assert connection.execute("SELECT COUNT(*) FROM document_chunks WHERE deleted_at IS NOT NULL").fetchone()[0] == 2


@pytest.mark.asyncio
async def test_cleanup_service_skips_unrelated_events():
    service = KnowledgeIndexCleanupService()

    result = await service.handle({"eventType": "document.parse.requested", "payload": {}})

    assert result["eventType"] == "knowledge.item.cleanup_skipped"
    assert result["payload"]["reason"] == "unsupported_event"


class RecordingSearchIndex:
    def __init__(self):
        self.deleted_documents = []

    def delete_document(self, *, index: str, document_id: str) -> None:
        self.deleted_documents.append((index, document_id))


class RecordingVectorIndex:
    def __init__(self):
        self.deleted = []

    def delete_chunks(self, chunk_ids: list[str]) -> None:
        self.deleted.extend(chunk_ids)


def create_sqlite_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE document_chunks (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          document_id INTEGER NOT NULL,
          knowledge_item_id INTEGER,
          chunk_index INTEGER NOT NULL,
          content TEXT NOT NULL,
          page_no INTEGER,
          position_payload TEXT NOT NULL DEFAULT '{}',
          parse_confidence REAL,
          embedding_status TEXT NOT NULL DEFAULT 'pending',
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          deleted_at TEXT
        );

        CREATE TABLE embeddings (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          chunk_id INTEGER NOT NULL,
          embedding_model TEXT NOT NULL,
          vector_dimension INTEGER NOT NULL,
          milvus_collection TEXT NOT NULL,
          milvus_primary_key TEXT NOT NULL,
          content_hash TEXT NOT NULL,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        INSERT INTO document_chunks(id, document_id, knowledge_item_id, chunk_index, content)
        VALUES
          (10, 900, 501, 0, 'obsolete chunk 0'),
          (11, 900, 501, 1, 'obsolete chunk 1'),
          (12, 901, 777, 0, 'keep chunk');

        INSERT INTO embeddings(chunk_id, embedding_model, vector_dimension, milvus_collection, milvus_primary_key, content_hash)
        VALUES
          (10, 'local', 64, 'knowledge_chunks', 'doc_900_chunk_0', 'hash-a'),
          (11, 'local', 64, 'knowledge_chunks', 'doc_900_chunk_1', 'hash-b');
        """
    )
