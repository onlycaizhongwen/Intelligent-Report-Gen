import asyncio
import json
import os
from typing import Any

from app.document_processing.application.parse_indexing_service import DocumentParseIndexingService


class RocketMqDocumentParseSource:
    """RocketMQ pull-consumer adapter kept isolated from parse business logic."""

    def __init__(self, consumer: Any, topic: str, tag: str = "document.parse.requested"):
        self.consumer = consumer
        self.topic = topic
        self.tag = tag

    @classmethod
    def from_env(cls) -> "RocketMqDocumentParseSource":
        from rocketmq.client import PushConsumer

        namesrv = os.getenv("ROCKETMQ_ENDPOINT", "rocketmq-namesrv:9876")
        group = os.getenv("ROCKETMQ_DOCUMENT_CONSUMER_GROUP", "python-ai-document-parse-consumer")
        topic = os.getenv("ROCKETMQ_DOCUMENT_TOPIC", "document_parse_requested")
        consumer = PushConsumer(group)
        consumer.set_namesrv_addr(namesrv)
        return cls(consumer=consumer, topic=topic)

    async def run(self, service: DocumentParseIndexingService) -> None:
        from rocketmq.client import _CConsumeStatus

        self.start(service, _CConsumeStatus)
        try:
            while True:
                await asyncio.sleep(60)
        finally:
            self.consumer.shutdown()

    def start(self, service: DocumentParseIndexingService, consume_status: Any | None = None) -> None:
        if consume_status is None:
            from rocketmq.client import _CConsumeStatus

            consume_status = _CConsumeStatus

        def callback(message: Any) -> Any:
            try:
                event = json.loads(message.body.decode("utf-8"))
                asyncio.run(service.handle(event))
                return consume_status.CONSUME_SUCCESS
            except Exception:
                return consume_status.RECONSUME_LATER

        self.consumer.subscribe(self.topic, callback, self.tag)
        self.consumer.start()


class RocketMqKnowledgeIndexCleanupSource:
    """RocketMQ adapter for knowledge.item.deleted cleanup events."""

    def __init__(self, consumer: Any, topic: str, tag: str = "knowledge.item.deleted"):
        self.consumer = consumer
        self.topic = topic
        self.tag = tag

    @classmethod
    def from_env(cls) -> "RocketMqKnowledgeIndexCleanupSource":
        from rocketmq.client import PushConsumer

        namesrv = os.getenv("ROCKETMQ_ENDPOINT", "rocketmq-namesrv:9876")
        group = os.getenv("ROCKETMQ_KNOWLEDGE_CLEANUP_CONSUMER_GROUP", "python-ai-knowledge-cleanup-consumer")
        topic = os.getenv("ROCKETMQ_KNOWLEDGE_ITEM_DELETED_TOPIC", "knowledge_item_deleted")
        consumer = PushConsumer(group)
        consumer.set_namesrv_addr(namesrv)
        return cls(consumer=consumer, topic=topic)

    async def run(self, service: Any) -> None:
        self.start(service)
        try:
            while True:
                await asyncio.sleep(60)
        finally:
            self.consumer.shutdown()

    def start(self, service: Any, consume_status: Any | None = None) -> None:
        if consume_status is None:
            from rocketmq.client import _CConsumeStatus

            consume_status = _CConsumeStatus

        def callback(message: Any) -> Any:
            try:
                event = json.loads(message.body.decode("utf-8"))
                asyncio.run(service.handle(event))
                return consume_status.CONSUME_SUCCESS
            except Exception:
                return consume_status.RECONSUME_LATER

        self.consumer.subscribe(self.topic, callback, self.tag)
        self.consumer.start()


class RocketMqKnowledgeItemIndexSource:
    """RocketMQ adapter for knowledge.item.index_requested events."""

    def __init__(self, consumer: Any, topic: str, tag: str = "knowledge.item.index_requested"):
        self.consumer = consumer
        self.topic = topic
        self.tag = tag

    @classmethod
    def from_env(cls) -> "RocketMqKnowledgeItemIndexSource":
        from rocketmq.client import PushConsumer

        namesrv = os.getenv("ROCKETMQ_ENDPOINT", "rocketmq-namesrv:9876")
        group = os.getenv("ROCKETMQ_KNOWLEDGE_INDEX_CONSUMER_GROUP", "python-ai-knowledge-index-consumer")
        topic = os.getenv("ROCKETMQ_KNOWLEDGE_ITEM_INDEX_REQUESTED_TOPIC", "knowledge_item_index_requested")
        consumer = PushConsumer(group)
        consumer.set_namesrv_addr(namesrv)
        return cls(consumer=consumer, topic=topic)

    async def run(self, service: Any) -> None:
        self.start(service)
        try:
            while True:
                await asyncio.sleep(60)
        finally:
            self.consumer.shutdown()

    def start(self, service: Any, consume_status: Any | None = None) -> None:
        if consume_status is None:
            from rocketmq.client import _CConsumeStatus

            consume_status = _CConsumeStatus

        def callback(message: Any) -> Any:
            try:
                event = json.loads(message.body.decode("utf-8"))
                asyncio.run(service.handle(event))
                return consume_status.CONSUME_SUCCESS
            except Exception:
                return consume_status.RECONSUME_LATER

        self.consumer.subscribe(self.topic, callback, self.tag)
        self.consumer.start()
