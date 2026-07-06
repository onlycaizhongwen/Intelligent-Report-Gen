from fastapi import Request
from fastapi.responses import JSONResponse


class BusinessException(Exception):
    def __init__(self, code: int, message: str, status_code: int = 400):
        self.code = code
        self.message = message
        self.status_code = status_code


class LLMTimeoutException(BusinessException):
    """OpenSpec: report-generation / REQ-AI-001 / 模型调用失败。"""

    def __init__(self):
        super().__init__(2001, "AI 服务繁忙，请稍后重试", 503)


class TokenLimitException(BusinessException):
    """OpenSpec: report-generation / REQ-AI-001 / Token 超限。"""

    def __init__(self):
        super().__init__(2002, "问题太长，请精简后重试", 400)


class PromptInjectionException(BusinessException):
    """OpenSpec: report-generation / REQ-AI-001 / Prompt 安全拦截。"""

    def __init__(self):
        super().__init__(2003, "输入包含不安全内容，请调整后重试", 400)


class RetrievalEmptyException(BusinessException):
    """OpenSpec: report-citation-export-version / REQ-REPORT-003 / 检索无结果。"""

    def __init__(self):
        super().__init__(2004, "未找到相关信息，请补充知识库或调整报告范围", 422)


class DocumentParseFailedException(BusinessException):
    """OpenSpec: knowledge-base-ingestion / REQ-KB-002 / 文件解析失败。"""

    def __init__(self):
        super().__init__(2005, "文档解析失败，请重新上传或修正文件", 422)


async def business_exception_handler(request: Request, exc: BusinessException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.code, "message": exc.message, "data": None},
    )
