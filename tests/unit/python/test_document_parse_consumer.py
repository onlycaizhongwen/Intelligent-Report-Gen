import pytest

from app.document_processing.application.parse_consumer import DocumentParseConsumer


class RecordingProcessor:
    def __init__(self):
        self.calls = []

    async def process_document(self, document_id: int, object_key: str) -> dict:
        self.calls.append((document_id, object_key))
        return {
            "documentId": document_id,
            "status": "processed",
            "chunks": 2,
            "embeddingModel": "local-hash-embedding",
            "embeddingDimension": 64,
            "knowledgeChunks": [
                {
                    "chunkId": "doc_42_chunk_0",
                    "documentId": 42,
                    "chunkIndex": 0,
                    "sourceTitle": "report.txt",
                    "content": "Quarterly revenue increased by 12%.",
                    "parseConfidence": 0.98,
                    "embeddingStatus": "embedded",
                    "embeddingModel": "local-hash-embedding",
                    "embeddingDimension": 64,
                    "vectorRef": "emb_test",
                }
            ],
        }


@pytest.mark.asyncio
async def test_document_parse_consumer_processes_rocketmq_payload():
    processor = RecordingProcessor()
    consumer = DocumentParseConsumer(processor)

    result = await consumer.handle(
        {
            "eventType": "document.parse.requested",
            "eventKey": "42",
            "payload": {
                "documentId": 42,
                "objectKey": "knowledge/demo/report.txt",
            },
        }
    )

    assert processor.calls == [(42, "knowledge/demo/report.txt")]
    assert result == {
        "eventType": "document.parse.completed",
        "eventKey": "42",
        "payload": {
            "documentId": 42,
            "status": "processed",
            "chunks": 2,
            "embeddingModel": "local-hash-embedding",
            "embeddingDimension": 64,
            "knowledgeChunks": [
                {
                    "chunkId": "doc_42_chunk_0",
                    "documentId": 42,
                    "chunkIndex": 0,
                    "sourceTitle": "report.txt",
                    "content": "Quarterly revenue increased by 12%.",
                    "parseConfidence": 0.98,
                    "embeddingStatus": "embedded",
                    "embeddingModel": "local-hash-embedding",
                    "embeddingDimension": 64,
                    "vectorRef": "emb_test",
                }
            ],
        },
    }


@pytest.mark.asyncio
async def test_document_parse_consumer_rejects_wrong_event_type():
    consumer = DocumentParseConsumer(RecordingProcessor())

    result = await consumer.handle({"eventType": "report.export.requested", "eventKey": "1", "payload": {}})

    assert result["eventType"] == "document.parse.rejected"
    assert result["payload"]["failureReason"] == "unsupported event type"
