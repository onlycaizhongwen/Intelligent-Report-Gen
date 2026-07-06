import csv
import io
from collections.abc import Callable
from pathlib import PurePosixPath
import re

from app.rag_retrieval.application.local_rag_runtime import LocalRagRuntime


class DocumentProcessor:
    """OpenSpec: knowledge-base-ingestion / REQ-KB-002 / document parse, chunk, embed."""

    _ALIGNED_WHITESPACE_DELIMITER = "__aligned_whitespace__"

    def __init__(
        self,
        object_loader: Callable[[str], str] | None = None,
        rag_runtime: LocalRagRuntime | None = None,
        chunk_size: int = 800,
    ):
        self.object_loader = object_loader or self._default_loader
        self.rag_runtime = rag_runtime or LocalRagRuntime()
        self.chunk_size = max(chunk_size, 120)

    async def process_document(self, document_id: int, object_key: str) -> dict:
        if not object_key:
            return {"documentId": document_id, "status": "failed", "failureReason": "document object key is required"}

        raw_text = self.object_loader(object_key)
        chunks = self._chunk_text(raw_text)
        if not chunks:
            return {
                "documentId": document_id,
                "status": "failed",
                "failureReason": "document text is empty after parsing",
            }

        knowledge_chunks = [
            self._to_chunk_payload(document_id, object_key, chunk_index, chunk)
            for chunk_index, chunk in enumerate(chunks)
        ]
        return {
            "documentId": document_id,
            "status": "processed",
            "chunks": len(knowledge_chunks),
            "embeddingModel": "local-hash-embedding",
            "embeddingDimension": 64,
            "knowledgeChunks": knowledge_chunks,
        }

    def _to_chunk_payload(self, document_id: int, object_key: str, chunk_index: int, content: str) -> dict:
        embedding = self.rag_runtime.embedding(content)
        return {
            "chunkId": f"doc_{document_id}_chunk_{chunk_index}",
            "documentId": document_id,
            "chunkIndex": chunk_index,
            "sourceTitle": PurePosixPath(object_key).name or object_key,
            "content": content,
            "parseConfidence": 0.98,
            "embeddingStatus": "embedded",
            "embeddingModel": embedding["model"],
            "embeddingDimension": embedding["dimension"],
            "vectorRef": embedding["vectorRef"],
            "embeddingVector": embedding["vector"],
        }

    def _chunk_text(self, raw_text: str) -> list[str]:
        paragraphs = [paragraph.strip() for paragraph in re.split(r"\n\s*\n+", raw_text or "") if paragraph.strip()]
        chunks: list[str] = []
        for paragraph in paragraphs:
            form_summary_chunks = self._form_summary_chunks(paragraph)
            if form_summary_chunks is not None:
                chunks.extend(form_summary_chunks)
                continue
            table_summary_chunks = self._table_summary_chunks(paragraph)
            if table_summary_chunks is not None:
                chunks.extend(table_summary_chunks)
                continue
            if len(paragraph) <= self.chunk_size:
                chunks.append(paragraph)
                continue
            chunks.extend(
                paragraph[index:index + self.chunk_size].strip()
                for index in range(0, len(paragraph), self.chunk_size)
                if paragraph[index:index + self.chunk_size].strip()
            )
        return chunks

    def _form_summary_chunks(self, paragraph: str) -> list[str] | None:
        lines = [line.strip() for line in paragraph.splitlines() if line.strip()]
        if len(lines) < 3:
            return None

        pairs: list[str] = []
        for line in lines[1:]:
            cells = self._split_structured_row(line, self._ALIGNED_WHITESPACE_DELIMITER)
            if len(cells) != 2:
                return None
            key, value = cells
            if not key or not value:
                return None
            pairs.append(f"{key}={value}")

        return [lines[0], " | ".join(pairs)] if len(pairs) >= 2 else None

    def _table_summary_chunks(self, paragraph: str) -> list[str] | None:
        lines = [line.strip() for line in paragraph.splitlines() if line.strip()]
        if len(lines) < 3:
            return None
        if self._looks_like_markdown_pipe_table(lines[1:]):
            return self._markdown_pipe_table_chunks(lines)
        delimiter = self._structured_table_delimiter(lines[1:])
        if delimiter is None:
            return None

        section_title = lines[0]
        header_index, header_row_count, header_cells = self._locate_header_row(lines[1:], delimiter)
        if header_cells is None:
            return None

        row_chunks: list[str] = [section_title]
        for row in lines[header_index + 1 + header_row_count:]:
            row_cells = self._split_structured_row(row, delimiter)
            if len(row_cells) != len(header_cells):
                return None
            pairs = [
                f"{header}={value.strip()}"
                for header, value in zip(header_cells, row_cells)
                if value.strip() != ""
            ]
            if not pairs:
                continue
            row_chunks.append(" | ".join(pairs))

        return row_chunks if len(row_chunks) > 1 else None

    def _markdown_pipe_table_chunks(self, lines: list[str]) -> list[str] | None:
        section_title = lines[0]
        table_lines = lines[1:]
        if len(table_lines) < 3:
            return None

        header_cells = self._normalize_header_cells(self._split_markdown_pipe_row(table_lines[0]))
        if len(header_cells) < 2:
            return None
        if not self._is_markdown_pipe_separator_row(table_lines[1], len(header_cells)):
            return None

        row_chunks: list[str] = [section_title]
        for row in table_lines[2:]:
            row_cells = self._split_markdown_pipe_row(row)
            if len(row_cells) != len(header_cells):
                return None
            pairs = [
                f"{header}={value.strip()}"
                for header, value in zip(header_cells, row_cells)
                if value.strip() != ""
            ]
            if not pairs:
                continue
            row_chunks.append(" | ".join(pairs))
        return row_chunks if len(row_chunks) > 1 else None

    def _locate_header_row(self, lines: list[str], delimiter: str) -> tuple[int, int, list[str] | None]:
        max_header_offset = min(len(lines) - 1, 2)
        for header_offset in range(0, max_header_offset + 1):
            header_cells = self._normalize_header_cells(self._split_structured_row(lines[header_offset], delimiter))
            if len(header_cells) < 2:
                continue
            if header_offset + 1 >= len(lines):
                continue
            next_row_cells = self._split_structured_row(lines[header_offset + 1], delimiter)
            if len(next_row_cells) != len(header_cells):
                continue
            merged_header_cells = self._merge_two_level_header_rows(header_cells, next_row_cells)
            if merged_header_cells is not None:
                if header_offset + 2 >= len(lines):
                    continue
                first_data_cells = self._split_structured_row(lines[header_offset + 2], delimiter)
                if len(first_data_cells) != len(merged_header_cells):
                    continue
                return header_offset, 2, self._deduplicate_header_cells(merged_header_cells)
            return header_offset, 1, self._deduplicate_header_cells(header_cells)
        return -1, 0, None

    @staticmethod
    def _structured_table_delimiter(lines: list[str]) -> str | None:
        candidates: list[tuple[str, int, int]] = []
        for delimiter in ("\t", ",", ";"):
            parsed_rows = [
                DocumentProcessor._split_structured_row(line, delimiter)
                for line in lines
                if delimiter in line or delimiter in {'\t'}  # keep tab candidate available for tabular rows
            ]
            if len(parsed_rows) < 2:
                continue
            column_count = len(parsed_rows[0])
            if column_count < 2:
                continue
            if any(len(row) != column_count for row in parsed_rows[1:]):
                continue
            candidates.append((delimiter, column_count, len(parsed_rows)))

        aligned_rows = [
            DocumentProcessor._split_structured_row(line, DocumentProcessor._ALIGNED_WHITESPACE_DELIMITER)
            for line in lines
            if re.search(r"\S\s{2,}\S", line)
        ]
        if len(aligned_rows) >= 2:
            column_count = len(aligned_rows[0])
            if (
                column_count >= 2
                and all(len(row) == column_count for row in aligned_rows[1:])
            ):
                candidates.append((DocumentProcessor._ALIGNED_WHITESPACE_DELIMITER, column_count, len(aligned_rows)))

        if not candidates:
            return None

        candidates.sort(key=lambda item: (item[1], item[2]), reverse=True)
        return candidates[0][0]

    @staticmethod
    def _split_structured_row(row: str, delimiter: str) -> list[str]:
        if delimiter == DocumentProcessor._ALIGNED_WHITESPACE_DELIMITER:
            return [cell.strip() for cell in re.split(r"\s{2,}", row.strip())]
        if delimiter in {",", ";"}:
            reader = csv.reader(io.StringIO(row), delimiter=delimiter, quotechar='"')
            try:
                parsed = next(reader)
            except StopIteration:
                return []
            return [cell.strip() for cell in parsed]
        return [cell.strip() for cell in row.split(delimiter)]

    @staticmethod
    def _split_markdown_pipe_row(row: str) -> list[str]:
        value = row.strip()
        if value.startswith("|"):
            value = value[1:]
        if value.endswith("|"):
            value = value[:-1]
        return [cell.strip() for cell in value.split("|")]

    def _looks_like_markdown_pipe_table(self, lines: list[str]) -> bool:
        if len(lines) < 3:
            return False
        if "|" not in lines[0] or "|" not in lines[1]:
            return False
        header_cells = self._split_markdown_pipe_row(lines[0])
        return self._is_markdown_pipe_separator_row(lines[1], len(header_cells))

    def _is_markdown_pipe_separator_row(self, row: str, expected_columns: int) -> bool:
        cells = self._split_markdown_pipe_row(row)
        if len(cells) != expected_columns:
            return False
        for cell in cells:
            raw = cell.strip()
            if raw == "":
                return False
            normalized = raw.replace(":", "").replace("-", "").strip()
            if normalized != "":
                return False
        return True

    @staticmethod
    def _normalize_header_cell(cell: str) -> str:
        return cell.lstrip("\ufeff").strip()

    def _normalize_header_cells(self, header_cells: list[str]) -> list[str]:
        normalized: list[str] = []
        for index, cell in enumerate(header_cells, start=1):
            clean = self._normalize_header_cell(cell)
            normalized.append(clean if clean else f"Column {index}")
        return normalized

    def _merge_two_level_header_rows(self, header_cells: list[str], subheader_cells: list[str]) -> list[str] | None:
        normalized_subheaders = [self._normalize_header_cell(cell) for cell in subheader_cells]
        non_empty_subheaders = [cell for cell in normalized_subheaders if cell]
        if not non_empty_subheaders or len(non_empty_subheaders) == len(normalized_subheaders):
            return None

        merged_headers: list[str] = []
        for header, subheader in zip(header_cells, normalized_subheaders):
            if header and subheader:
                merged_headers.append(f"{header} {subheader}")
            elif header:
                merged_headers.append(header)
            else:
                merged_headers.append(subheader)

        if any(cell == "" for cell in merged_headers):
            return None
        return merged_headers

    @staticmethod
    def _deduplicate_header_cells(header_cells: list[str]) -> list[str]:
        counts: dict[str, int] = {}
        for header in header_cells:
            counts[header] = counts.get(header, 0) + 1

        seen: dict[str, int] = {}
        normalized: list[str] = []
        for header in header_cells:
            if counts[header] == 1:
                normalized.append(header)
                continue
            seen[header] = seen.get(header, 0) + 1
            normalized.append(f"{header} #{seen[header]}")
        return normalized

    def _default_loader(self, object_key: str) -> str:
        title = PurePosixPath(object_key).name or "uploaded document"
        return f"Parsed content placeholder for {title}."
