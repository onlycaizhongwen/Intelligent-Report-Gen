import asyncio
import logging
import os

from app.report_generation.application.generation_worker import ReportGenerationWorker

logger = logging.getLogger(__name__)


async def main() -> None:
    source = configured_message_source()
    worker = ReportGenerationWorker()
    logger.info("report generation worker starting")
    await source.run(worker)


def configured_message_source():
    provider = os.getenv("REPORT_GENERATION_WORKER_PROVIDER", "disabled").strip().lower()
    if provider != "rocketmq":
        return DisabledWorkerSource(provider)
    from app.report_generation.application.rocketmq_worker import RocketMqReportGenerationSource

    return RocketMqReportGenerationSource.from_env()


class DisabledWorkerSource:
    def __init__(self, provider: str):
        self.provider = provider

    async def run(self, worker: ReportGenerationWorker) -> None:
        logger.warning("report generation worker disabled: REPORT_GENERATION_WORKER_PROVIDER=%s", self.provider)
        while True:
            await asyncio.sleep(60)


if __name__ == "__main__":
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
    asyncio.run(main())
