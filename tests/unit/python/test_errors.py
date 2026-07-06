from app.shared_kernel.errors import (
    DocumentParseFailedException,
    LLMTimeoutException,
    PromptInjectionException,
    RetrievalEmptyException,
    TokenLimitException,
)


def test_business_errors_have_contract_codes():
    """REQ-AI-001/REQ-KB-002：AI 与解析错误应有稳定错误码。"""
    errors = [
        LLMTimeoutException(),
        TokenLimitException(),
        PromptInjectionException(),
        RetrievalEmptyException(),
        DocumentParseFailedException(),
    ]

    assert [error.code for error in errors] == [2001, 2002, 2003, 2004, 2005]
    assert all(error.message for error in errors)
