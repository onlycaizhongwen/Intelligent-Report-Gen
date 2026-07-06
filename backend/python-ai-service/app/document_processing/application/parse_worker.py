import asyncio
import json
from dataclasses import dataclass
from typing import Protocol

from app.document_processing.application.parse_indexing_service import DocumentParseIndexingService


@dataclass(frozen=True)
class ReceivedMessage:
    message_id: str
    body: bytes


class MessageSource(Protocol):
    def receive(self, timeout_seconds: float = 3.0) -> ReceivedMessage | None:
        ...

    def ack(self, message: ReceivedMessage) -> None:
        ...

    def nack(self, message: ReceivedMessage, reason: str) -> None:
        ...


class DocumentParseWorker:
    def __init__(self, source: MessageSource, service: DocumentParseIndexingService):
        self.source = source
        self.service = service

    async def run_once(self) -> dict:
        message = self.source.receive()
        if message is None:
            return {"status": "idle"}

        try:
            event = json.loads(message.body.decode("utf-8"))
            result = await self.service.handle(event)
            self.source.ack(message)
            return {
                "status": "processed",
                "eventKey": result.get("eventKey"),
                "eventType": result.get("eventType"),
                "payload": result.get("payload"),
            }
        except Exception as exc:
            reason = f"invalid document parse message: {exc}"
            self.source.nack(message, reason)
            return {"status": "failed", "messageId": message.message_id, "failureReason": reason}

    async def run_forever(self, poll_interval_seconds: float = 1.0) -> None:
        while True:
            result = await self.run_once()
            if result["status"] == "idle":
                await asyncio.sleep(poll_interval_seconds)
