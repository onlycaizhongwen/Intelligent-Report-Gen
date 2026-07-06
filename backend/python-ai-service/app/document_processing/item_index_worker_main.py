import asyncio
import logging
import os

from app.document_processing.application.knowledge_item_indexing_service import KnowledgeItemIndexingService
from app.document_processing.application.parse_indexing_service import configured_db_connection_factory, configured_search_index
from app.document_processing.application.vector_index import configured_vector_index

logger = logging.getLogger(__name__)


async def main() -> None:
    source = configured_message_source()
    service = KnowledgeItemIndexingService(
        db_connection_factory=configured_db_connection_factory(),
        search_index=configured_search_index(),
        vector_index=configured_vector_index(),
    )
    logger.info("knowledge item index worker starting")
    await source.run(service)


def configured_message_source():
    provider = os.getenv("KNOWLEDGE_INDEX_WORKER_PROVIDER", "disabled").strip().lower()
    if provider != "rocketmq":
        return DisabledWorkerSource(provider)
    from app.document_processing.application.rocketmq_worker import RocketMqKnowledgeItemIndexSource

    return RocketMqKnowledgeItemIndexSource.from_env()


class DisabledWorkerSource:
    def __init__(self, provider: str):
        self.provider = provider

    async def run(self, service: KnowledgeItemIndexingService) -> None:
        logger.warning("knowledge index worker disabled: KNOWLEDGE_INDEX_WORKER_PROVIDER=%s", self.provider)
        while True:
            await asyncio.sleep(60)


if __name__ == "__main__":
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
    asyncio.run(main())
