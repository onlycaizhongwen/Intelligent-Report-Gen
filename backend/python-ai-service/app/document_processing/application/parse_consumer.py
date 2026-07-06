from typing import Any


class DocumentParseConsumer:
    """Handle document.parse.requested envelopes from RocketMQ."""

    def __init__(self, processor: Any):
        self.processor = processor

    async def handle(self, event: dict) -> dict:
        event_type = event.get("eventType")
        event_key = str(event.get("eventKey", ""))
        payload = event.get("payload") or {}
        if event_type != "document.parse.requested":
            return {
                "eventType": "document.parse.rejected",
                "eventKey": event_key,
                "payload": {
                    "documentId": payload.get("documentId"),
                    "status": "failed",
                    "failureReason": "unsupported event type",
                },
            }

        document_id = int(payload["documentId"])
        object_key = str(payload.get("objectKey", ""))
        result = await self.processor.process_document(document_id, object_key)
        return {
            "eventType": "document.parse.completed" if result.get("status") == "processed" else "document.parse.failed",
            "eventKey": event_key,
            "payload": result,
        }
