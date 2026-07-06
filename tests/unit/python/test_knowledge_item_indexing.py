import sqlite3

import pytest

from app.document_processing.application.knowledge_item_indexing_service import KnowledgeItemIndexingService
from tests.unit.python.test_document_parse_indexing import RecordingSearchIndex, RecordingVectorIndex, create_sqlite_schema


@pytest.mark.asyncio
async def test_indexing_service_indexes_manual_knowledge_item_with_item_id():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection)
    search_index = RecordingSearchIndex()
    vector_index = RecordingVectorIndex()
    service = KnowledgeItemIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        vector_index=vector_index,
    )

    result = await service.handle(
        {
            "eventType": "knowledge.item.index_requested",
            "eventKey": "501",
            "payload": {
                "knowledgeItemId": 501,
                "knowledgeBaseId": 7,
                "title": "市场洞察",
                "content": "收入增长 12%。\n\n应收账款账龄需要跟进。",
                "sourceType": "batch_import",
            },
        }
    )

    assert result["eventType"] == "knowledge.item.index_completed"
    assert result["payload"]["knowledgeItemId"] == 501
    assert result["payload"]["chunks"] == 2
    assert result["payload"]["persistenceStatus"] == "persisted"
    assert result["payload"]["searchIndexStatus"] == "indexed"

    chunk_rows = connection.execute("SELECT * FROM document_chunks ORDER BY chunk_index").fetchall()
    embedding_rows = connection.execute("SELECT * FROM embeddings ORDER BY chunk_id").fetchall()

    assert [row["knowledge_item_id"] for row in chunk_rows] == [501, 501]
    assert chunk_rows[0]["document_id"] == 501
    assert chunk_rows[0]["content"] == "收入增长 12%。"
    assert len(embedding_rows) == 2
    assert vector_index.chunks[0]["chunkId"] == "item_501_chunk_0"
    assert search_index.documents[0]["documentId"] == "item_501_chunk_0"
    assert search_index.documents[0]["body"]["knowledgeItemId"] == 501
    assert search_index.documents[0]["body"]["sourceType"] == "batch_import"


@pytest.mark.asyncio
async def test_indexing_service_skips_unrelated_events():
    service = KnowledgeItemIndexingService()

    result = await service.handle({"eventType": "knowledge.item.deleted", "payload": {}})

    assert result["eventType"] == "knowledge.item.index_skipped"
    assert result["payload"]["reason"] == "unsupported_event"
