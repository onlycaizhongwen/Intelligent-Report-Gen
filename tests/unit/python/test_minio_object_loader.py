import pytest

from app.document_processing.application.object_loader import CommandBinaryTextExtractor, MinioTextObjectLoader


def test_minio_text_loader_reads_utf8_object_from_bucket():
    client = RecordingMinioClient({"report-artifacts/knowledge/demo.txt": b"hello\nworld"})
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    assert loader.load("knowledge/demo.txt") == "hello\nworld"
    assert client.requests == [("report-artifacts", "knowledge/demo.txt")]


def test_minio_text_loader_uses_payload_bucket_when_present():
    client = RecordingMinioClient({"custom/knowledge/demo.txt": b"custom bucket"})
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    assert loader.load("knowledge/demo.txt", bucket="custom") == "custom bucket"


def test_minio_text_loader_rejects_binary_documents_with_clear_reason():
    client = RecordingMinioClient({"report-artifacts/knowledge/scan.pdf": b"%PDF"})
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    with pytest.raises(ValueError, match="requires OCR or table extraction"):
        loader.load("knowledge/scan.pdf")


def test_minio_text_loader_extracts_text_from_pdf_with_binary_extractor():
    client = RecordingMinioClient({"report-artifacts/knowledge/scan.pdf": b"%PDF-1.4 binary body"})
    extractor = RecordingBinaryExtractor({"knowledge/scan.pdf": "Scanned contract total amount is 128000 yuan."})
    loader = MinioTextObjectLoader(
        client=client,
        default_bucket="report-artifacts",
        binary_text_extractor=extractor,
    )

    assert loader.load("knowledge/scan.pdf") == "Scanned contract total amount is 128000 yuan."
    assert extractor.calls == [("knowledge/scan.pdf", ".pdf", b"%PDF-1.4 binary body")]


def test_command_binary_text_extractor_reports_clear_reason_when_command_missing():
    extractor = CommandBinaryTextExtractor(pdftotext_path="missing-pdftotext", tesseract_path="missing-tesseract")

    with pytest.raises(ValueError, match="requires OCR or table extraction"):
        extractor.extract("knowledge/scan.pdf", ".pdf", b"%PDF-1.4 body")


def test_minio_text_loader_extracts_text_from_docx_without_external_ocr_commands():
    client = RecordingMinioClient(
        {
            "report-artifacts/knowledge/policy.docx": build_minimal_docx(
                ["Section 1 Risk governance", "Section 2 Approval delegation"]
            )
        }
    )
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    assert loader.load("knowledge/policy.docx") == "Section 1 Risk governance\nSection 2 Approval delegation"


def test_minio_text_loader_extracts_rows_from_xlsx_without_external_ocr_commands():
    client = RecordingMinioClient(
        {
            "report-artifacts/knowledge/finance.xlsx": build_minimal_xlsx(
                sheet_name="BudgetSummary",
                rows=[
                    ["Department", "Amount"],
                    ["Sales", "120000"],
                    ["Delivery", "86000"],
                ],
            )
        }
    )
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    assert loader.load("knowledge/finance.xlsx") == "BudgetSummary\nDepartment\tAmount\nSales\t120000\nDelivery\t86000"


def test_minio_text_loader_extracts_docx_table_cells_into_searchable_rows():
    client = RecordingMinioClient(
        {
            "report-artifacts/knowledge/rules.docx": build_minimal_docx(
                ["Risk control matrix"],
                table_rows=[
                    ["Level", "Action"],
                    ["High", "Manual review"],
                ],
            )
        }
    )
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    assert loader.load("knowledge/rules.docx") == "Risk control matrix\nLevel\tAction\nHigh\tManual review"


def test_minio_text_loader_extracts_xlsx_shared_strings_and_preserves_sparse_cells():
    client = RecordingMinioClient(
        {
            "report-artifacts/knowledge/ledger.xlsx": build_minimal_xlsx(
                sheet_name="Ledger",
                rows=[
                    ["Department", "CostCenter", "Amount"],
                    ["Sales", "", "128000"],
                ],
                use_shared_strings=True,
            )
        }
    )
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    assert loader.load("knowledge/ledger.xlsx") == "Ledger\nDepartment\tCostCenter\tAmount\nSales\t\t128000"


def test_minio_text_loader_extracts_multiple_xlsx_sheets_in_stable_order():
    client = RecordingMinioClient(
        {
            "report-artifacts/knowledge/workbook.xlsx": build_minimal_xlsx(
                sheets=[
                    {
                        "sheet_name": "RevenueByRegion",
                        "rows": [
                            ["Region", "Amount"],
                            ["East", "120000"],
                            ["West", "98000"],
                        ],
                    },
                    {
                        "sheet_name": "RiskRegister",
                        "rows": [
                            ["Level", "Owner", "Action"],
                            ["High", "CFO", "Manual review"],
                        ],
                        "use_shared_strings": True,
                    },
                ]
            )
        }
    )
    loader = MinioTextObjectLoader(client=client, default_bucket="report-artifacts")

    assert loader.load("knowledge/workbook.xlsx") == (
        "RevenueByRegion\n"
        "Region\tAmount\n"
        "East\t120000\n"
        "West\t98000\n\n"
        "RiskRegister\n"
        "Level\tOwner\tAction\n"
        "High\tCFO\tManual review"
    )


class RecordingMinioClient:
    def __init__(self, objects):
        self.objects = objects
        self.requests = []

    def get_object(self, bucket, object_name):
        self.requests.append((bucket, object_name))
        return RecordingObject(self.objects[f"{bucket}/{object_name}"])


class RecordingObject:
    def __init__(self, body):
        self.body = body

    def read(self):
        return self.body

    def close(self):
        pass

    def release_conn(self):
        pass


class RecordingBinaryExtractor:
    def __init__(self, extracted_text):
        self.extracted_text = extracted_text
        self.calls = []

    def extract(self, object_key, suffix, content_bytes):
        self.calls.append((object_key, suffix, content_bytes))
        return self.extracted_text[object_key]


def build_minimal_docx(paragraphs: list[str], table_rows: list[list[str]] | None = None) -> bytes:
    import io
    import zipfile

    body = "".join(f"<w:p><w:r><w:t>{text}</w:t></w:r></w:p>" for text in paragraphs)
    if table_rows:
        body += (
            "<w:tbl>"
            + "".join(
                "<w:tr>"
                + "".join(f"<w:tc><w:p><w:r><w:t>{cell}</w:t></w:r></w:p></w:tc>" for cell in row)
                + "</w:tr>"
                for row in table_rows
            )
            + "</w:tbl>"
        )
    document_xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        f"<w:body>{body}</w:body>"
        "</w:document>"
    )
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        "</Types>"
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="word/document.xml"/>'
        "</Relationships>"
    )

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", rels)
        archive.writestr("word/document.xml", document_xml)
    return buffer.getvalue()


def build_minimal_xlsx(
    sheet_name: str | None = None,
    rows: list[list[str]] | None = None,
    use_shared_strings: bool = False,
    sheets: list[dict] | None = None,
) -> bytes:
    import io
    import zipfile

    sheet_specs = sheets or [{"sheet_name": sheet_name, "rows": rows or [], "use_shared_strings": use_shared_strings}]
    shared_lookup: dict[str, int] = {}
    shared_items: list[str] = []

    def shared_index(value: str) -> int:
        if value not in shared_lookup:
            shared_lookup[value] = len(shared_items)
            shared_items.append(value)
        return shared_lookup[value]

    workbook_sheets = []
    workbook_relationships = []
    worksheet_entries: list[tuple[str, str]] = []
    for sheet_index, spec in enumerate(sheet_specs, start=1):
        sheet_rows = []
        for row_index, row in enumerate(spec["rows"], start=1):
            cells = []
            for col_index, value in enumerate(row, start=1):
                if value == "":
                    continue
                cell_ref = f"{column_letters(col_index)}{row_index}"
                if spec.get("use_shared_strings", False):
                    cells.append(f'<c r="{cell_ref}" t="s"><v>{shared_index(value)}</v></c>')
                else:
                    cells.append(f'<c r="{cell_ref}" t="inlineStr"><is><t>{value}</t></is></c>')
            sheet_rows.append(f'<row r="{row_index}">{"".join(cells)}</row>')
        sheet_xml = (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            f'<sheetData>{"".join(sheet_rows)}</sheetData>'
            "</worksheet>"
        )
        sheet_file = f"worksheets/sheet{sheet_index}.xml"
        worksheet_entries.append((f"xl/{sheet_file}", sheet_xml))
        workbook_sheets.append(
            f'<sheet name="{spec["sheet_name"]}" sheetId="{sheet_index}" r:id="rId{sheet_index}"/>'
        )
        workbook_relationships.append(
            '<Relationship Id="rId{index}" '
            'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
            'Target="{target}"/>'.format(index=sheet_index, target=sheet_file)
        )
    workbook_xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
        f'<sheets>{"".join(workbook_sheets)}</sheets>'
        "</workbook>"
    )
    workbook_rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        f'{"".join(workbook_relationships)}'
        "</Relationships>"
    )
    root_rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="xl/workbook.xml"/>'
        "</Relationships>"
    )
    content_types = (
        (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
        )
        + "".join(
            '<Override PartName="/xl/worksheets/sheet{index}.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'.format(
                index=index
            )
            for index in range(1, len(sheet_specs) + 1)
        )
        + "</Types>"
    )

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", root_rels)
        archive.writestr("xl/workbook.xml", workbook_xml)
        archive.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
        for worksheet_path, worksheet_xml in worksheet_entries:
            archive.writestr(worksheet_path, worksheet_xml)
        if shared_items:
            shared_xml = (
                '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
                f'count="{len(shared_items)}" uniqueCount="{len(shared_items)}">'
                + "".join(f"<si><t>{item}</t></si>" for item in shared_items)
                + "</sst>"
            )
            archive.writestr("xl/sharedStrings.xml", shared_xml)
    return buffer.getvalue()


def column_letters(index: int) -> str:
    result = ""
    current = index
    while current > 0:
        current, remainder = divmod(current - 1, 26)
        result = chr(65 + remainder) + result
    return result
