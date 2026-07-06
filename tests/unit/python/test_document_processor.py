import pytest

from app.document_processing.application.document_processor import DocumentProcessor


@pytest.mark.asyncio
async def test_process_document_fails_when_object_key_missing():
    processor = DocumentProcessor()

    result = await processor.process_document(1, "")

    assert result["documentId"] == 1
    assert result["status"] == "failed"
    assert result["failureReason"]


@pytest.mark.asyncio
async def test_process_document_returns_chunks_and_embedding_model():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "Revenue in East China increased by 12% year over year.\n\n"
            "Gross margin improved because service revenue grew faster than hardware."
        )
    )

    result = await processor.process_document(1, "reports/demo.pdf")

    assert result["status"] == "processed"
    assert result["chunks"] >= 1
    assert result["embeddingModel"]
    assert result["embeddingDimension"] == 64
    assert result["knowledgeChunks"][0] == {
        "chunkId": "doc_1_chunk_0",
        "documentId": 1,
        "chunkIndex": 0,
        "sourceTitle": "demo.pdf",
        "content": "Revenue in East China increased by 12% year over year.",
        "parseConfidence": 0.98,
        "embeddingStatus": "embedded",
        "embeddingModel": "local-hash-embedding",
        "embeddingDimension": 64,
        "vectorRef": result["knowledgeChunks"][0]["vectorRef"],
        "embeddingVector": result["knowledgeChunks"][0]["embeddingVector"],
    }
    assert result["knowledgeChunks"][1] == {
        "chunkId": "doc_1_chunk_1",
        "documentId": 1,
        "chunkIndex": 1,
        "sourceTitle": "demo.pdf",
        "content": "Gross margin improved because service revenue grew faster than hardware.",
        "parseConfidence": 0.98,
        "embeddingStatus": "embedded",
        "embeddingModel": "local-hash-embedding",
        "embeddingDimension": 64,
        "vectorRef": result["knowledgeChunks"][1]["vectorRef"],
        "embeddingVector": result["knowledgeChunks"][1]["embeddingVector"],
    }
    assert result["knowledgeChunks"][0]["vectorRef"].startswith("emb_")
    assert len(result["knowledgeChunks"][0]["embeddingVector"]) == 64


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_for_structured_xlsx_sections():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "RevenueByRegion\n"
            "Region\tAmount\n"
            "East\t120000\n"
            "West\t98000\n\n"
            "RiskRegister\n"
            "Level\tOwner\tAction\n"
            "High\tCFO\tManual review"
        )
    )

    result = await processor.process_document(2, "knowledge/workbook.xlsx")

    assert result["status"] == "processed"
    assert result["chunks"] == 5
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "RevenueByRegion",
        "Region=East | Amount=120000",
        "Region=West | Amount=98000",
        "RiskRegister",
        "Level=High | Owner=CFO | Action=Manual review",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_for_structured_csv_sections():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Department,Amount,Owner\n"
            "Sales,120000,Alice\n"
            "Delivery,86000,Bob"
        )
    )

    result = await processor.process_document(3, "knowledge/budget.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_for_quoted_csv_cells():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Department,Amount,Owner,Comment\n"
            'Sales,120000,Alice,"East, focus region"\n'
            'Delivery,86000,Bob,"Needs follow-up"'
        )
    )

    result = await processor.process_document(4, "knowledge/budget-quoted.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East, focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_for_semicolon_csv_sections():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Department;Amount;Owner;Comment\n"
            'Sales;120000;Alice;"East; focus region"\n'
            'Delivery;86000;Bob;"Needs follow-up"'
        )
    )

    result = await processor.process_document(5, "knowledge/budget-semicolon.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East; focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]


@pytest.mark.asyncio
async def test_process_document_detects_semicolon_csv_even_when_quoted_cells_contain_commas():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Department;Amount;Owner;Comment\n"
            'Sales;120000;Alice;"East, focus region"\n'
            'Delivery;86000;Bob;"Needs follow-up"'
        )
    )

    result = await processor.process_document(6, "knowledge/budget-semicolon-comma.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice | Comment=East, focus region",
        "Department=Delivery | Amount=86000 | Owner=Bob | Comment=Needs follow-up",
    ]


@pytest.mark.asyncio
async def test_process_document_strips_utf8_bom_from_csv_header_cells():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "\ufeffDepartment,Amount,Owner\n"
            "Sales,120000,Alice\n"
            "Delivery,86000,Bob"
        )
    )

    result = await processor.process_document(7, "knowledge/budget-bom.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_when_note_line_precedes_csv_header():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Generated at: 2026-06-25 10:30\n"
            "Department,Amount,Owner\n"
            "Sales,120000,Alice\n"
            "Delivery,86000,Bob"
        )
    )

    result = await processor.process_document(8, "knowledge/budget-with-note.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_for_two_level_csv_headers():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Department,Amount,Amount,Owner\n"
            ",Planned,Actual,\n"
            "Sales,100000,120000,Alice\n"
            "Delivery,80000,86000,Bob"
        )
    )

    result = await processor.process_document(9, "knowledge/budget-two-level.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount Planned=100000 | Amount Actual=120000 | Owner=Alice",
        "Department=Delivery | Amount Planned=80000 | Amount Actual=86000 | Owner=Bob",
    ]


@pytest.mark.asyncio
async def test_process_document_disambiguates_duplicate_csv_headers():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Department,Amount,Amount,Amount\n"
            "Sales,100000,120000,125000\n"
            "Delivery,80000,86000,88000"
        )
    )

    result = await processor.process_document(10, "knowledge/budget-duplicate-headers.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount #1=100000 | Amount #2=120000 | Amount #3=125000",
        "Department=Delivery | Amount #1=80000 | Amount #2=86000 | Amount #3=88000",
    ]


@pytest.mark.asyncio
async def test_process_document_fills_blank_csv_headers_with_stable_column_names():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "Department,,Owner\n"
            "Sales,120000,Alice\n"
            "Delivery,86000,Bob"
        )
    )

    result = await processor.process_document(11, "knowledge/budget-blank-header.csv")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Column 2=120000 | Owner=Alice",
        "Department=Delivery | Column 2=86000 | Owner=Bob",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_for_markdown_pipe_tables():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "BudgetSummary\n"
            "| Department | Amount | Owner |\n"
            "| --- | ---: | --- |\n"
            "| Sales | 120000 | Alice |\n"
            "| Delivery | 86000 | Bob |"
        )
    )

    result = await processor.process_document(12, "knowledge/budget.md")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "BudgetSummary",
        "Department=Sales | Amount=120000 | Owner=Alice",
        "Department=Delivery | Amount=86000 | Owner=Bob",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_row_summaries_for_ocr_aligned_tables():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "ScannedInvoiceTable\n"
            "Item        Amount    Owner\n"
            "Revenue     120000    Alice\n"
            "Delivery    86000     Bob"
        )
    )

    result = await processor.process_document(13, "knowledge/scanned-table.pdf")

    assert result["status"] == "processed"
    assert result["chunks"] == 3
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "ScannedInvoiceTable",
        "Item=Revenue | Amount=120000 | Owner=Alice",
        "Item=Delivery | Amount=86000 | Owner=Bob",
    ]


@pytest.mark.asyncio
async def test_process_document_builds_key_value_summary_for_ocr_forms():
    processor = DocumentProcessor(
        object_loader=lambda object_key: (
            "ScannedInvoiceForm\n"
            "InvoiceNumber    INV-2026-001\n"
            "Supplier         Contoso Manufacturing\n"
            "TotalAmount      3560.00"
        )
    )

    result = await processor.process_document(14, "knowledge/scanned-form.pdf")

    assert result["status"] == "processed"
    assert result["chunks"] == 2
    assert [chunk["content"] for chunk in result["knowledgeChunks"]] == [
        "ScannedInvoiceForm",
        "InvoiceNumber=INV-2026-001 | Supplier=Contoso Manufacturing | TotalAmount=3560.00",
    ]
