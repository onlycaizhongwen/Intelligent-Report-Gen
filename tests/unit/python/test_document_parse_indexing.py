import io
import sqlite3
import urllib.error

import pytest

from app.document_processing.application.parse_indexing_service import (
    DocumentParseIndexingService,
    OpenSearchIndex,
)


@pytest.mark.asyncio
async def test_parse_indexing_service_persists_chunks_embeddings_and_search_index():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection)
    search_index = RecordingSearchIndex()
    vector_index = RecordingVectorIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        vector_index=vector_index,
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "99",
            "payload": {
                "documentId": 99,
                "objectKey": "knowledge/demo/customer-risk.txt",
                "knowledgeBaseId": 7,
                "text": "East revenue increased 12%.\n\nReceivables aging requires follow-up.",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["documentId"] == 99
    assert result["payload"]["chunks"] == 2
    assert result["payload"]["persistenceStatus"] == "persisted"
    assert result["payload"]["searchIndexStatus"] == "indexed"

    parse_rows = connection.execute("SELECT * FROM document_parse_results").fetchall()
    chunk_rows = connection.execute("SELECT * FROM document_chunks ORDER BY chunk_index").fetchall()
    embedding_rows = connection.execute("SELECT * FROM embeddings ORDER BY chunk_id").fetchall()

    assert len(parse_rows) == 1
    assert parse_rows[0]["document_id"] == 99
    assert parse_rows[0]["status"] == "processed"
    document_rows = connection.execute("SELECT * FROM knowledge_documents WHERE id = 99").fetchall()
    assert document_rows[0]["parse_status"] == "processed"
    assert len(chunk_rows) == 2
    assert chunk_rows[0]["content"] == "East revenue increased 12%."
    assert chunk_rows[0]["embedding_status"] == "embedded"
    assert len(embedding_rows) == 2
    assert embedding_rows[0]["embedding_model"] == "local-hash-embedding"
    assert embedding_rows[0]["vector_dimension"] == 64
    assert embedding_rows[0]["milvus_collection"] == "knowledge_chunks"
    assert embedding_rows[0]["milvus_primary_key"] == "milvus_doc_99_chunk_0"
    assert vector_index.chunks[0]["chunkId"] == "doc_99_chunk_0"
    assert vector_index.chunks[0]["embeddingVector"] == result["payload"]["knowledgeChunks"][0]["embeddingVector"]
    assert search_index.documents[0]["index"] == "knowledge_entries_text"
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 7
    assert search_index.documents[0]["body"]["content"] == "East revenue increased 12%."


@pytest.mark.asyncio
async def test_parse_indexing_service_reports_skipped_persistence_when_database_is_not_configured():
    service = DocumentParseIndexingService(db_connection_factory=None, search_index=None)

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "100",
            "payload": {
                "documentId": 100,
                "objectKey": "knowledge/demo/no-db.txt",
                "text": "No database configured.",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["persistenceStatus"] == "skipped"
    assert result["payload"]["searchIndexStatus"] == "skipped"


@pytest.mark.asyncio
async def test_parse_indexing_service_loads_text_from_object_storage_when_payload_has_no_text():
    service = DocumentParseIndexingService(
        object_loader=RecordingObjectLoader({"knowledge/demo/storage.txt": "Loaded from MinIO."})
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "101",
            "payload": {
                "documentId": 101,
                "bucket": "report-artifacts",
                "objectKey": "knowledge/demo/storage.txt",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["knowledgeChunks"][0]["content"] == "Loaded from MinIO."


@pytest.mark.asyncio
async def test_parse_indexing_service_persists_failed_status_when_ocr_is_required():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=102)
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=RecordingSearchIndex(),
        object_loader=FailingObjectLoader(
            "document knowledge/scanned.pdf requires OCR or table extraction before text parsing"
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "102",
            "payload": {
                "documentId": 102,
                "bucket": "report-artifacts",
                "objectKey": "knowledge/scanned.pdf",
            },
        }
    )

    assert result["eventType"] == "document.parse.failed"
    assert result["payload"]["status"] == "failed"
    assert (
        result["payload"]["failureReason"]
        == "document knowledge/scanned.pdf requires OCR or table extraction before text parsing"
    )
    assert result["payload"]["persistenceStatus"] == "persisted"
    assert result["payload"]["searchIndexStatus"] == "skipped"

    parse_rows = connection.execute("SELECT * FROM document_parse_results WHERE document_id = 102").fetchall()
    document_rows = connection.execute("SELECT * FROM knowledge_documents WHERE id = 102").fetchall()
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 102").fetchall()

    assert parse_rows[0]["status"] == "failed"
    assert (
        parse_rows[0]["failure_reason"]
        == "document knowledge/scanned.pdf requires OCR or table extraction before text parsing"
    )
    assert document_rows[0]["parse_status"] == "failed"
    assert (
        document_rows[0]["parse_failure_reason"]
        == "document knowledge/scanned.pdf requires OCR or table extraction before text parsing"
    )
    assert chunk_rows == []


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_scanned_pdf_when_ocr_loader_is_configured():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=103)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {"knowledge/scanned.pdf": "Scanned invoice number INV-2026-001.\n\nTax amount is 3560 yuan."}
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "103",
            "payload": {
                "documentId": 103,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 8,
                "objectKey": "knowledge/scanned.pdf",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 2
    assert result["payload"]["persistenceStatus"] == "persisted"
    assert result["payload"]["searchIndexStatus"] == "indexed"
    assert result["payload"]["knowledgeChunks"][0]["content"] == "Scanned invoice number INV-2026-001."
    assert result["payload"]["knowledgeChunks"][1]["content"] == "Tax amount is 3560 yuan."

    parse_rows = connection.execute("SELECT * FROM document_parse_results WHERE document_id = 103").fetchall()
    document_rows = connection.execute("SELECT * FROM knowledge_documents WHERE id = 103").fetchall()
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 103 ORDER BY chunk_index").fetchall()

    assert parse_rows[0]["status"] == "processed"
    assert document_rows[0]["parse_status"] == "processed"
    assert chunk_rows[0]["content"] == "Scanned invoice number INV-2026-001."
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 8


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_docx_document_into_chunks_and_search_index():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=104)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {"knowledge/policy.docx": "Policy overview\n\nApproval matrix\n\nRisk classification controls"}
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "104",
            "payload": {
                "documentId": 104,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 9,
                "objectKey": "knowledge/policy.docx",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert result["payload"]["knowledgeChunks"][0]["sourceTitle"] == "policy.docx"
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 104 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "Policy overview",
        "Approval matrix",
        "Risk classification controls",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 9


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_xlsx_document_into_chunks_and_search_index():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=105)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {"knowledge/budget.xlsx": "BudgetSummary\nDepartment\tAmount\nSales\t120000\nDelivery\t86000"}
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "105",
            "payload": {
                "documentId": 105,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 10,
                "objectKey": "knowledge/budget.xlsx",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000",
        "Department=Delivery | Amount=86000",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 105 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000",
        "Department=Delivery | Amount=86000",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 10


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_csv_document_into_row_summary_chunks():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=106)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {"knowledge/budget.csv": "BudgetSummary\nDepartment,Amount,Owner\nSales,120000,Alice\nDelivery,86000,Bob"}
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "106",
            "payload": {
                "documentId": 106,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 11,
                "objectKey": "knowledge/budget.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 106 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 11


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_quoted_csv_document_into_row_summary_chunks():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=107)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-quoted.csv": (
                    "BudgetSummary\n"
                    "Department,Amount,Owner,Comment\n"
                    'Sales,120000,Alice,"East, focus region"\n'
                    'Delivery,86000,Bob,"Needs follow-up"'
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "107",
            "payload": {
                "documentId": 107,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 12,
                "objectKey": "knowledge/budget-quoted.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East, focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 107 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East, focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 12


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_semicolon_csv_document_into_row_summary_chunks():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=108)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-semicolon.csv": (
                    "BudgetSummary\n"
                    "Department;Amount;Owner;Comment\n"
                    'Sales;120000;Alice;"East; focus region"\n'
                    'Delivery;86000;Bob;"Needs follow-up"'
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "108",
            "payload": {
                "documentId": 108,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 13,
                "objectKey": "knowledge/budget-semicolon.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East; focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 108 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East; focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 13


@pytest.mark.asyncio
async def test_parse_indexing_service_detects_semicolon_csv_when_quoted_cells_contain_commas():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=109)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-semicolon-comma.csv": (
                    "BudgetSummary\n"
                    "Department;Amount;Owner;Comment\n"
                    'Sales;120000;Alice;"East, focus region"\n'
                    'Delivery;86000;Bob;"Needs follow-up"'
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "109",
            "payload": {
                "documentId": 109,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 14,
                "objectKey": "knowledge/budget-semicolon-comma.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East, focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 109 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East, focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 14


@pytest.mark.asyncio
async def test_parse_indexing_service_strips_utf8_bom_from_csv_header_cells():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=110)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-bom.csv": (
                    "BudgetSummary\n"
                    "\ufeffDepartment,Amount,Owner\n"
                    "Sales,120000,Alice\n"
                    "Delivery,86000,Bob"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "110",
            "payload": {
                "documentId": 110,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 15,
                "objectKey": "knowledge/budget-bom.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 110 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 15


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_csv_when_note_line_precedes_header():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=111)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-with-note.csv": (
                    "BudgetSummary\n"
                    "Generated at: 2026-06-25 10:30\n"
                    "Department,Amount,Owner\n"
                    "Sales,120000,Alice\n"
                    "Delivery,86000,Bob"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "111",
            "payload": {
                "documentId": 111,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 16,
                "objectKey": "knowledge/budget-with-note.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 111 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 16


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_csv_with_two_level_headers():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=112)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-two-level.csv": (
                    "BudgetSummary\n"
                    "Department,Amount,Amount,Owner\n"
                    ",Planned,Actual,\n"
                    "Sales,100000,120000,Alice\n"
                    "Delivery,80000,86000,Bob"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "112",
            "payload": {
                "documentId": 112,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 17,
                "objectKey": "knowledge/budget-two-level.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount Planned=100000 | Amount Actual=120000 | Owner=Alice",
        "Department=Delivery | Amount Planned=80000 | Amount Actual=86000 | Owner=Bob",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 112 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount Planned=100000 | Amount Actual=120000 | Owner=Alice",
        "Department=Delivery | Amount Planned=80000 | Amount Actual=86000 | Owner=Bob",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 17


@pytest.mark.asyncio
async def test_parse_indexing_service_disambiguates_duplicate_csv_headers():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=113)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-duplicate-headers.csv": (
                    "BudgetSummary\n"
                    "Department,Amount,Amount,Amount\n"
                    "Sales,100000,120000,125000\n"
                    "Delivery,80000,86000,88000"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "113",
            "payload": {
                "documentId": 113,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 18,
                "objectKey": "knowledge/budget-duplicate-headers.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount #1=100000 | Amount #2=120000 | Amount #3=125000",
        "Department=Delivery | Amount #1=80000 | Amount #2=86000 | Amount #3=88000",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 113 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount #1=100000 | Amount #2=120000 | Amount #3=125000",
        "Department=Delivery | Amount #1=80000 | Amount #2=86000 | Amount #3=88000",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 18


@pytest.mark.asyncio
async def test_parse_indexing_service_fills_blank_csv_headers_with_stable_column_names():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=114)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget-blank-header.csv": (
                    "BudgetSummary\n"
                    "Department,,Owner\n"
                    "Sales,120000,Alice\n"
                    "Delivery,86000,Bob"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "114",
            "payload": {
                "documentId": 114,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 19,
                "objectKey": "knowledge/budget-blank-header.csv",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Column 2=120000 | Owner=Alice",
        "Department=Delivery | Column 2=86000 | Owner=Bob",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 114 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Column 2=120000 | Owner=Alice",
        "Department=Delivery | Column 2=86000 | Owner=Bob",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 19


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_markdown_pipe_tables_into_row_summary_chunks():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=115)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/budget.md": (
                    "BudgetSummary\n"
                    "| Department | Amount | Owner |\n"
                    "| --- | ---: | --- |\n"
                    "| Sales | 120000 | Alice |\n"
                    "| Delivery | 86000 | Bob |"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "115",
            "payload": {
                "documentId": 115,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 20,
                "objectKey": "knowledge/budget.md",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 115 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 20


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_ocr_aligned_tables_into_row_summary_chunks():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=116)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/scanned-table.pdf": (
                    "ScannedInvoiceTable\n"
                    "Item        Amount    Owner\n"
                    "Revenue     120000    Alice\n"
                    "Delivery    86000     Bob"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "116",
            "payload": {
                "documentId": 116,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 21,
                "objectKey": "knowledge/scanned-table.pdf",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 3
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "ScannedInvoiceTable",
        "Item=Revenue | Amount=120000 | Owner=Alice",
        "Item=Delivery | Amount=86000 | Owner=Bob",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 116 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "ScannedInvoiceTable",
        "Item=Revenue | Amount=120000 | Owner=Alice",
        "Item=Delivery | Amount=86000 | Owner=Bob",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 21
    assert search_index.documents[1]["body"]["content"] == "Item=Revenue | Amount=120000 | Owner=Alice"


@pytest.mark.asyncio
async def test_parse_indexing_service_processes_ocr_forms_into_key_value_chunks():
    connection = sqlite3.connect(":memory:")
    connection.row_factory = sqlite3.Row
    create_sqlite_schema(connection, document_id=117)
    search_index = RecordingSearchIndex()
    service = DocumentParseIndexingService(
        db_connection_factory=lambda: connection,
        search_index=search_index,
        object_loader=RecordingObjectLoader(
            {
                "knowledge/scanned-form.pdf": (
                    "ScannedInvoiceForm\n"
                    "InvoiceNumber    INV-2026-001\n"
                    "Supplier         Contoso Manufacturing\n"
                    "TotalAmount      3560.00"
                )
            }
        ),
    )

    result = await service.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "117",
            "payload": {
                "documentId": 117,
                "bucket": "report-artifacts",
                "knowledgeBaseId": 22,
                "objectKey": "knowledge/scanned-form.pdf",
            },
        }
    )

    assert result["eventType"] == "document.parse.completed"
    assert result["payload"]["status"] == "processed"
    assert result["payload"]["chunks"] == 2
    assert [chunk["content"] for chunk in result["payload"]["knowledgeChunks"]] == [
        "ScannedInvoiceForm",
        "InvoiceNumber=INV-2026-001 | Supplier=Contoso Manufacturing | TotalAmount=3560.00",
    ]
    chunk_rows = connection.execute("SELECT * FROM document_chunks WHERE document_id = 117 ORDER BY chunk_index").fetchall()
    assert [row["content"] for row in chunk_rows] == [
        "ScannedInvoiceForm",
        "InvoiceNumber=INV-2026-001 | Supplier=Contoso Manufacturing | TotalAmount=3560.00",
    ]
    assert search_index.documents[0]["body"]["knowledgeBaseId"] == 22
    assert (
        search_index.documents[1]["body"]["content"]
        == "InvoiceNumber=INV-2026-001 | Supplier=Contoso Manufacturing | TotalAmount=3560.00"
    )


def test_open_search_index_deletes_document_with_idempotent_not_found(monkeypatch):
    requests = []

    def fake_urlopen(request, timeout):
        requests.append({"url": request.full_url, "method": request.get_method(), "timeout": timeout})
        if len(requests) == 1:
            return FakeHttpResponse(200)
        raise urllib.error.HTTPError(request.full_url, 404, "missing", {}, io.BytesIO())

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    index = OpenSearchIndex("http://opensearch:9200", timeout=3.0)

    index.delete_document(index="knowledge_entries_text", document_id="doc_1_chunk_0")
    index.delete_document(index="knowledge_entries_text", document_id="already_deleted")

    assert requests == [
        {
            "url": "http://opensearch:9200/knowledge_entries_text/_doc/doc_1_chunk_0",
            "method": "DELETE",
            "timeout": 3.0,
        },
        {
            "url": "http://opensearch:9200/knowledge_entries_text/_doc/already_deleted",
            "method": "DELETE",
            "timeout": 3.0,
        },
    ]


class RecordingSearchIndex:
    def __init__(self):
        self.documents = []

    def index_document(self, *, index: str, document_id: str, body: dict) -> None:
        self.documents.append({"index": index, "documentId": document_id, "body": body})


class RecordingVectorIndex:
    def __init__(self):
        self.chunks = []

    def upsert_chunk(self, chunk: dict) -> str:
        self.chunks.append(chunk)
        return "milvus_" + chunk["chunkId"]


class RecordingObjectLoader:
    def __init__(self, objects):
        self.objects = objects

    def load(self, object_key: str, bucket: str | None = None) -> str:
        return self.objects[object_key]


class FailingObjectLoader:
    def __init__(self, failure_reason: str):
        self.failure_reason = failure_reason

    def load(self, object_key: str, bucket: str | None = None) -> str:
        raise ValueError(self.failure_reason)


class FakeHttpResponse:
    def __init__(self, status):
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False


def create_sqlite_schema(connection: sqlite3.Connection, document_id: int = 99) -> None:
    connection.executescript(
        """
        CREATE TABLE document_parse_results (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          document_id INTEGER NOT NULL,
          parse_type TEXT NOT NULL DEFAULT 'text',
          result_payload TEXT NOT NULL DEFAULT '{}',
          confidence REAL,
          status TEXT NOT NULL,
          failure_reason TEXT,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

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

        CREATE TABLE knowledge_documents (
          id INTEGER PRIMARY KEY,
          parse_status TEXT NOT NULL DEFAULT 'pending',
          parse_failure_reason TEXT,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        """
    )
    connection.execute("INSERT INTO knowledge_documents(id, parse_status) VALUES (?, 'pending')", (document_id,))
