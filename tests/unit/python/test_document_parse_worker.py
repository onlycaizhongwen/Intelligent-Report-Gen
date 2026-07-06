import json

import pytest

from app.document_processing.application.parse_indexing_service import DocumentParseIndexingService
from app.document_processing.application.parse_worker import DocumentParseWorker, ReceivedMessage


@pytest.mark.asyncio
async def test_document_parse_worker_acks_after_persisting_message():
    source = RecordingMessageSource([
        {
            "eventType": "document.parse.requested",
            "eventKey": "42",
            "payload": {
                "documentId": 42,
                "objectKey": "knowledge/demo/report.txt",
                "text": "Revenue improved.\n\nRisk remains.",
            },
        }
    ])
    service = DocumentParseIndexingService()
    worker = DocumentParseWorker(source, service)

    result = await worker.run_once()

    assert result["status"] == "processed"
    assert result["eventKey"] == "42"
    assert source.acked == ["42"]
    assert source.nacked == []


@pytest.mark.asyncio
async def test_document_parse_worker_nacks_invalid_message_without_stopping_loop():
    source = RecordingMessageSource(["not-json"])
    worker = DocumentParseWorker(source, DocumentParseIndexingService())

    result = await worker.run_once()

    assert result["status"] == "failed"
    assert "invalid document parse message" in result["failureReason"]
    assert source.acked == []
    assert source.nacked == ["not-json"]


class RecordingMessageSource:
    def __init__(self, events):
        self.messages = [
            ReceivedMessage(
                message_id=str((event.get("eventKey") if isinstance(event, dict) else event)),
                body=json.dumps(event).encode("utf-8") if isinstance(event, dict) else str(event).encode("utf-8"),
            )
            for event in events
        ]
        self.acked = []
        self.nacked = []

    def receive(self, timeout_seconds: float = 3.0):
        return self.messages.pop(0) if self.messages else None

    def ack(self, message: ReceivedMessage):
        self.acked.append(message.message_id)

    def nack(self, message: ReceivedMessage, reason: str):
        self.nacked.append(message.message_id)
