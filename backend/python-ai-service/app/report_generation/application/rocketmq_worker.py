import asyncio
import json
import os
from typing import Any

from app.report_generation.application.generation_worker import ReportGenerationWorker


class RocketMqReportGenerationSource:
    """RocketMQ adapter for Java-confirmed report generation tasks."""

    def __init__(self, consumer: Any, topic: str, tag: str = "report.generation.outline_confirmed"):
        self.consumer = consumer
        self.topic = topic
        self.tag = tag

    @classmethod
    def from_env(cls) -> "RocketMqReportGenerationSource":
        from rocketmq.client import PushConsumer

        namesrv = os.getenv("ROCKETMQ_ENDPOINT", "rocketmq-namesrv:9876")
        group = os.getenv("ROCKETMQ_REPORT_GENERATION_CONSUMER_GROUP", "python-ai-report-generation-consumer")
        topic = os.getenv("ROCKETMQ_REPORT_GENERATION_TOPIC", "report_generation_outline_confirmed")
        consumer = PushConsumer(group)
        consumer.set_namesrv_addr(namesrv)
        return cls(consumer=consumer, topic=topic)

    def start(self, worker: ReportGenerationWorker) -> None:
        from rocketmq.client import _CConsumeStatus

        def callback(message: Any) -> Any:
            try:
                event = json.loads(message.body.decode("utf-8"))
                asyncio.run(worker.handle(event))
                return _CConsumeStatus.CONSUME_SUCCESS
            except Exception:
                return _CConsumeStatus.RECONSUME_LATER

        self.consumer.subscribe(self.topic, callback, self.tag)
        self.consumer.start()

    async def run(self, worker: ReportGenerationWorker) -> None:
        self.start(worker)
        try:
            while True:
                await asyncio.sleep(60)
        finally:
            self.consumer.shutdown()
