import hashlib
import json
import os
import time
import urllib.request

import psycopg
import pytest
from pymilvus import MilvusClient

from app.document_processing.application.knowledge_index_cleanup_service import KnowledgeIndexCleanupService
from app.document_processing.application.parse_indexing_service import OpenSearchIndex
from app.document_processing.application.vector_index import MilvusVectorIndex


pytestmark = pytest.mark.skipif(
    os.getenv("RUN_KNOWLEDGE_CLEANUP_SMOKE", "").lower() != "true",
    reason="requires local PostgreSQL, OpenSearch and Milvus containers",
)


@pytest.mark.asyncio
async def test_cleanup_service_deletes_real_postgres_opensearch_and_milvus_entries():
    database_url = os.getenv("DATABASE_URL", "postgresql://report:report123@localhost:5432/intelligent_report")
    opensearch_url = os.getenv("OPENSEARCH_URL", "http://localhost:9200")
    milvus_uri = os.getenv("MILVUS_URI", "http://localhost:19530")
    knowledge_item_id = int(time.time() * 1000) % 1_000_000_000
    document_id = knowledge_item_id + 10_000
    chunk_id = f"doc_{document_id}_chunk_0"

    milvus_client = MilvusClient(uri=milvus_uri)
    vector_index = MilvusVectorIndex(client=milvus_client)
    search_index = OpenSearchIndex(opensearch_url)

    with psycopg.connect(database_url) as connection:
        cleanup_database_rows(connection, document_id, knowledge_item_id)
        row_id = insert_chunk_and_embedding(connection, document_id, knowledge_item_id, chunk_id)

    search_index.index_document(
        index="knowledge_entries_text",
        document_id=chunk_id,
        body={
            "documentId": document_id,
            "knowledgeBaseId": 1,
            "knowledgeItemId": knowledge_item_id,
            "chunkId": chunk_id,
            "title": "cleanup smoke",
            "content": "cleanup smoke evidence",
            "sourceType": "manual_knowledge_item",
            "embeddingModel": "local-hash-embedding",
            "vectorRef": chunk_id,
        },
    )
    vector_index.upsert_chunk(
        {
            "chunkId": chunk_id,
            "documentId": document_id,
            "chunkIndex": 0,
            "content": "cleanup smoke evidence",
            "embeddingModel": "local-hash-embedding",
            "embeddingVector": [0.125] * 64,
        }
    )

    service = KnowledgeIndexCleanupService(
        db_connection_factory=lambda: psycopg.connect(database_url),
        search_index=search_index,
        vector_index=vector_index,
    )

    result = await service.handle(
        {
            "eventType": "knowledge.item.deleted",
            "eventKey": str(knowledge_item_id),
            "payload": {"knowledgeItemId": knowledge_item_id, "knowledgeBaseId": 1},
        }
    )

    assert result["payload"]["chunks"] == 1
    assert result["payload"]["persistenceStatus"] == "soft_deleted"
    assert result["payload"]["searchIndexStatus"] == "deleted"
    assert result["payload"]["vectorIndexStatus"] == "deleted"

    with psycopg.connect(database_url) as connection:
        chunk_row = connection.execute("SELECT deleted_at FROM document_chunks WHERE id = %s", (row_id,)).fetchone()
        embedding_count = connection.execute("SELECT COUNT(*) FROM embeddings WHERE chunk_id = %s", (row_id,)).fetchone()[0]
        assert chunk_row[0] is not None
        assert embedding_count == 0
        cleanup_database_rows(connection, document_id, knowledge_item_id)

    assert opensearch_exists(opensearch_url, chunk_id) is False
    assert milvus_client.query(
        collection_name="knowledge_chunks",
        filter=f'chunk_id == "{chunk_id}"',
        output_fields=["chunk_id"],
    ) == []


def insert_chunk_and_embedding(connection, document_id: int, knowledge_item_id: int, chunk_id: str) -> int:
    row = connection.execute(
        """
        INSERT INTO document_chunks(document_id, knowledge_item_id, chunk_index, content, position_payload, parse_confidence, embedding_status)
        VALUES (%s, %s, 0, %s, '{}'::jsonb, 0.99, 'embedded')
        RETURNING id
        """,
        (document_id, knowledge_item_id, "cleanup smoke evidence"),
    ).fetchone()
    row_id = row[0]
    connection.execute(
        """
        INSERT INTO embeddings(chunk_id, embedding_model, vector_dimension, milvus_collection, milvus_primary_key, content_hash)
        VALUES (%s, 'local-hash-embedding', 64, 'knowledge_chunks', %s, %s)
        """,
        (row_id, chunk_id, hashlib.sha256(b"cleanup smoke evidence").hexdigest()),
    )
    connection.commit()
    return row_id


def cleanup_database_rows(connection, document_id: int, knowledge_item_id: int) -> None:
    rows = connection.execute(
        "SELECT id FROM document_chunks WHERE document_id = %s OR knowledge_item_id = %s",
        (document_id, knowledge_item_id),
    ).fetchall()
    chunk_row_ids = [row[0] for row in rows]
    if chunk_row_ids:
        connection.execute("DELETE FROM embeddings WHERE chunk_id = ANY(%s)", (chunk_row_ids,))
    connection.execute("DELETE FROM document_chunks WHERE document_id = %s OR knowledge_item_id = %s", (document_id, knowledge_item_id))
    connection.commit()


def opensearch_exists(opensearch_url: str, chunk_id: str) -> bool:
    request = urllib.request.Request(f"{opensearch_url.rstrip('/')}/knowledge_entries_text/_doc/{chunk_id}")
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            payload = json.loads(response.read().decode("utf-8"))
            return bool(payload.get("found"))
    except Exception:
        return False
