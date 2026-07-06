from collections.abc import AsyncGenerator


class RagService:
    """OpenSpec: report-generation / REQ-AI-001 / RAG 检索增强流程。"""

    async def answer(self, question: str, user_id: int) -> AsyncGenerator[str, None]:
        chunks = await self.retrieve(question, user_id)
        if not chunks:
            yield "未找到相关信息，请补充知识库或调整报告范围。"
            return
        yield "基于已授权知识库片段生成报告内容。"

    async def retrieve(self, question: str, user_id: int) -> list[dict]:
        if not question.strip():
            return []
        return [{"chunkId": 1, "score": 0.91, "content": "演示检索片段"}]
