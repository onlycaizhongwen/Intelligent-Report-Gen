import os
import io
import subprocess
import tempfile
import zipfile
from xml.etree import ElementTree
from pathlib import Path, PurePosixPath
from typing import Any, Protocol


class BinaryTextExtractor(Protocol):
    def extract(self, object_key: str, suffix: str, content_bytes: bytes) -> str:
        ...


class CommandBinaryTextExtractor:
    supported_suffixes = {".pdf", ".png", ".jpg", ".jpeg", ".tif", ".tiff", ".bmp"}
    image_suffixes = {".png", ".jpg", ".jpeg", ".tif", ".tiff", ".bmp"}

    def __init__(self, pdftotext_path: str | None = None, tesseract_path: str | None = None):
        self.pdftotext_path = pdftotext_path or os.getenv("PDFTOTEXT_BIN", "pdftotext")
        self.tesseract_path = tesseract_path or os.getenv("TESSERACT_BIN", "tesseract")

    def extract(self, object_key: str, suffix: str, content_bytes: bytes) -> str:
        normalized_suffix = suffix.lower()
        if normalized_suffix not in self.supported_suffixes:
            raise ValueError(f"document {object_key} requires OCR or table extraction before text parsing")

        if normalized_suffix == ".pdf":
            text = self._extract_pdf_text(object_key, content_bytes)
            if text.strip():
                return text
            return self._run_ocr(object_key, normalized_suffix, content_bytes)

        return self._run_ocr(object_key, normalized_suffix, content_bytes)

    def _extract_pdf_text(self, object_key: str, content_bytes: bytes) -> str:
        suffix = PurePosixPath(object_key).suffix.lower() or ".pdf"
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as source_file:
            source_file.write(content_bytes)
            source_path = Path(source_file.name)
        with tempfile.NamedTemporaryFile(delete=False, suffix=".txt") as target_file:
            target_path = Path(target_file.name)

        try:
            completed = subprocess.run(
                [self.pdftotext_path, str(source_path), str(target_path)],
                check=False,
                capture_output=True,
                text=True,
            )
            if completed.returncode != 0:
                raise ValueError(
                    f"document {object_key} OCR extraction failed: pdftotext exited with {completed.returncode}"
                )
            return target_path.read_text(encoding="utf-8", errors="ignore").strip()
        except FileNotFoundError as exc:
            raise ValueError(f"document {object_key} requires OCR or table extraction before text parsing") from exc
        finally:
            source_path.unlink(missing_ok=True)
            target_path.unlink(missing_ok=True)

    def _run_ocr(self, object_key: str, suffix: str, content_bytes: bytes) -> str:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as source_file:
            source_file.write(content_bytes)
            source_path = Path(source_file.name)
        output_base = source_path.with_suffix("")
        output_txt = output_base.with_suffix(".txt")

        try:
            completed = subprocess.run(
                [self.tesseract_path, str(source_path), str(output_base)],
                check=False,
                capture_output=True,
                text=True,
            )
            if completed.returncode != 0:
                raise ValueError(
                    f"document {object_key} OCR extraction failed: tesseract exited with {completed.returncode}"
                )
            text = output_txt.read_text(encoding="utf-8", errors="ignore").strip() if output_txt.exists() else ""
            if not text:
                raise ValueError(f"document {object_key} requires OCR or table extraction before text parsing")
            return text
        except FileNotFoundError as exc:
            raise ValueError(f"document {object_key} requires OCR or table extraction before text parsing") from exc
        finally:
            source_path.unlink(missing_ok=True)
            output_txt.unlink(missing_ok=True)


class MinioTextObjectLoader:
    """Load text documents from MinIO for the document parse worker."""

    text_extensions = {".txt", ".md", ".markdown", ".csv", ".json", ".log"}
    office_extensions = {".docx", ".xlsx"}

    def __init__(
        self,
        client: Any,
        default_bucket: str,
        binary_text_extractor: BinaryTextExtractor | None = None,
    ):
        self.client = client
        self.default_bucket = default_bucket
        self.binary_text_extractor = binary_text_extractor

    def load(self, object_key: str, bucket: str | None = None) -> str:
        suffix = PurePosixPath(object_key).suffix.lower()
        response = self.client.get_object(bucket or self.default_bucket, object_key)
        try:
            content_bytes = response.read()
        finally:
            response.close()
            response.release_conn()

        if not suffix or suffix in self.text_extensions:
            return content_bytes.decode("utf-8")

        if suffix in self.office_extensions:
            return self._extract_office_text(object_key, suffix, content_bytes)

        if self.binary_text_extractor is None:
            raise ValueError(f"document {object_key} requires OCR or table extraction before text parsing")

        return self.binary_text_extractor.extract(object_key, suffix, content_bytes)

    def _extract_office_text(self, object_key: str, suffix: str, content_bytes: bytes) -> str:
        try:
            if suffix == ".docx":
                text = extract_docx_text(content_bytes)
            elif suffix == ".xlsx":
                text = extract_xlsx_text(content_bytes)
            else:
                text = ""
        except (KeyError, ValueError, zipfile.BadZipFile, ElementTree.ParseError) as exc:
            raise ValueError(f"document {object_key} office extraction failed: {exc}") from exc

        normalized = text.strip()
        if not normalized:
            raise ValueError(f"document {object_key} requires OCR or table extraction before text parsing")
        return normalized


def configured_object_loader() -> MinioTextObjectLoader | None:
    endpoint = os.getenv("MINIO_ENDPOINT", "").strip()
    access_key = os.getenv("MINIO_ROOT_USER", "").strip()
    secret_key = os.getenv("MINIO_ROOT_PASSWORD", "").strip()
    bucket = os.getenv("MINIO_BUCKET", "report-artifacts").strip()
    if not endpoint or not access_key or not secret_key:
        return None

    from minio import Minio

    normalized = endpoint.removeprefix("http://").removeprefix("https://")
    secure = endpoint.startswith("https://")
    return MinioTextObjectLoader(
        client=Minio(normalized, access_key=access_key, secret_key=secret_key, secure=secure),
        default_bucket=bucket,
        binary_text_extractor=CommandBinaryTextExtractor(),
    )


WORD_NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
SHEET_NS = {
    "s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
}


def extract_docx_text(content_bytes: bytes) -> str:
    with zipfile.ZipFile(io.BytesIO(content_bytes)) as archive:
        document_xml = archive.read("word/document.xml")
    root = ElementTree.fromstring(document_xml)
    lines: list[str] = []
    body = root.find("w:body", WORD_NS)
    if body is None:
        return ""

    for child in body:
        tag_name = local_name(child.tag)
        if tag_name == "p":
            merged = paragraph_text(child)
            if merged:
                lines.append(merged)
        elif tag_name == "tbl":
            for row in child.findall("w:tr", WORD_NS):
                cells = [paragraphs_from_table_cell(cell) for cell in row.findall("w:tc", WORD_NS)]
                if any(cell != "" for cell in cells):
                    lines.append("\t".join(cells))
    return "\n".join(lines)


def extract_xlsx_text(content_bytes: bytes) -> str:
    with zipfile.ZipFile(io.BytesIO(content_bytes)) as archive:
        workbook_root = ElementTree.fromstring(archive.read("xl/workbook.xml"))
        workbook_rels_root = ElementTree.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        shared_strings = load_shared_strings(archive)
        relationship_targets = {
            rel.attrib["Id"]: rel.attrib["Target"]
            for rel in workbook_rels_root.findall(".//rel:Relationship", SHEET_NS)
            if rel.attrib.get("Id") and rel.attrib.get("Target")
        }

        lines: list[str] = []
        for sheet in workbook_root.findall(".//s:sheet", SHEET_NS):
            sheet_name = (sheet.attrib.get("name") or "").strip()
            section_lines: list[str] = []
            if sheet_name:
                section_lines.append(sheet_name)
            relationship_id = sheet.attrib.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
            target = relationship_targets.get(relationship_id or "")
            if not target:
                if section_lines:
                    lines.append("\n".join(section_lines))
                continue
            sheet_path = target if target.startswith("xl/") else f"xl/{target}"
            sheet_root = ElementTree.fromstring(archive.read(sheet_path))
            for row in sheet_root.findall(".//s:sheetData/s:row", SHEET_NS):
                cells = row.findall("s:c", SHEET_NS)
                if not cells:
                    continue
                max_column = max(column_index_from_ref(cell.attrib.get("r", "")) for cell in cells)
                values = [""] * max_column
                for cell in cells:
                    column_index = column_index_from_ref(cell.attrib.get("r", ""))
                    if column_index <= 0:
                        continue
                    value = extract_xlsx_cell_value(cell, shared_strings)
                    if value is not None and value != "":
                        values[column_index - 1] = value
                if any(value != "" for value in values):
                    section_lines.append("\t".join(values))
            if section_lines:
                lines.append("\n".join(section_lines))
    return "\n\n".join(lines)


def extract_xlsx_cell_value(cell: ElementTree.Element, shared_strings: list[str]) -> str | None:
    inline = cell.find("s:is/s:t", SHEET_NS)
    if inline is not None:
        return inline.text or ""
    if cell.attrib.get("t") == "s":
        value_node = cell.find("s:v", SHEET_NS)
        if value_node is None or value_node.text is None:
            return ""
        index = int(value_node.text)
        return shared_strings[index] if 0 <= index < len(shared_strings) else ""
    value_node = cell.find("s:v", SHEET_NS)
    if value_node is not None:
        return value_node.text or ""
    return None


def paragraph_text(paragraph: ElementTree.Element) -> str:
    texts = [node.text or "" for node in paragraph.findall(".//w:t", WORD_NS)]
    return "".join(texts).strip()


def paragraphs_from_table_cell(cell: ElementTree.Element) -> str:
    texts = [paragraph_text(paragraph) for paragraph in cell.findall("w:p", WORD_NS)]
    return " ".join(text for text in texts if text)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def load_shared_strings(archive: zipfile.ZipFile) -> list[str]:
    if "xl/sharedStrings.xml" not in archive.namelist():
        return []
    root = ElementTree.fromstring(archive.read("xl/sharedStrings.xml"))
    return ["".join(node.text or "" for node in item.findall(".//s:t", SHEET_NS)) for item in root.findall("s:si", SHEET_NS)]


def column_index_from_ref(cell_ref: str) -> int:
    letters = "".join(ch for ch in cell_ref if ch.isalpha()).upper()
    if not letters:
        return 0
    index = 0
    for letter in letters:
        index = index * 26 + (ord(letter) - 64)
    return index
