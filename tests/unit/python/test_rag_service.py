import pytest

from app.rag_retrieval.application.rag_service import RagService


@pytest.mark.asyncio
async def test_rag_answer_returns_no_context_message_for_empty_question():
    """REQ-AI-001：检索无结果时给出可见提示。"""
    service = RagService()

    chunks = [chunk async for chunk in service.answer("   ", user_id=1)]

    assert chunks
    assert "未找到" in chunks[0] or "鏈壘鍒" in chunks[0]


@pytest.mark.asyncio
async def test_rag_answer_returns_generated_content_for_question():
    """REQ-AI-001：有检索片段时返回增强生成内容。"""
    service = RagService()

    chunks = [chunk async for chunk in service.answer("本季度销售趋势", user_id=1)]

    assert chunks
    assert "生成" in chunks[0] or "鐢熸垚" in chunks[0]
