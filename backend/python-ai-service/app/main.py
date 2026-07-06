import json

from fastapi import Depends, FastAPI, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.document_processing.application.parse_indexing_service import (
    DocumentParseIndexingService,
    configured_db_connection_factory,
    configured_search_index,
)
from app.document_processing.application.object_loader import configured_object_loader
from app.document_processing.application.vector_index import configured_vector_index
from app.rag_retrieval.application.indexed_rag_retriever import configured_indexed_retriever
from app.rag_retrieval.application.local_rag_runtime import LocalRagRuntime
from app.report_generation.application.generation_worker import ReportGenerationWorker
from app.shared_kernel.errors import BusinessException, PromptInjectionException, business_exception_handler
from app.shared_kernel.security import get_current_user, prompt_guard

app = FastAPI(title="Intelligent Report Python AI Service")
app.add_exception_handler(BusinessException, business_exception_handler)
rag_runtime = LocalRagRuntime(retriever=configured_indexed_retriever())


class ApiResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: dict | list | None = None


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5
    context: dict | None = None


class ChatRequest(BaseModel):
    question: str
    context: dict | None = None


class DocumentParseEventRequest(BaseModel):
    eventType: str
    eventKey: str
    payload: dict


class ReportGenerationEventRequest(BaseModel):
    eventType: str
    eventKey: str
    payload: dict


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


def create_sse_event(
    *,
    type: str,
    task_id: str,
    content: str = "",
    stage: str | None = None,
    references: list | None = None,
    progress: float | None = None,
    error_code: str | None = None,
    trace_id: str | None = None,
) -> str:
    payload = {
        "type": type,
        "taskId": task_id,
        "content": content,
        "stage": stage,
        "references": references or [],
        "progress": progress,
        "errorCode": error_code,
        "traceId": trace_id,
    }
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


@app.post("/api/v1/documents/upload")
async def upload_document(file: UploadFile, current_user: dict = Depends(get_current_user)) -> ApiResponse:
    """Internal parse executor compatibility endpoint. Java owns external upload orchestration."""
    return ApiResponse(data={"documentId": 1, "parseStatus": "pending", "filename": file.filename})


@app.post("/api/v1/internal/document-parse-events")
async def handle_document_parse_event(
    request: DocumentParseEventRequest,
    current_user: dict = Depends(get_current_user),
) -> ApiResponse:
    """Internal execution endpoint for local smoke tests and controlled worker adapters."""
    service = DocumentParseIndexingService(
        db_connection_factory=configured_db_connection_factory(),
        search_index=configured_search_index(),
        object_loader=configured_object_loader(),
        vector_index=configured_vector_index(),
    )
    return ApiResponse(data=await service.handle(request.model_dump()))


@app.post("/api/v1/internal/report-generation-events")
async def handle_report_generation_event(
    request: ReportGenerationEventRequest,
    current_user: dict = Depends(get_current_user),
) -> ApiResponse:
    """Internal execution endpoint for controlled report generation workers."""
    return ApiResponse(data=await ReportGenerationWorker(rag_runtime=rag_runtime).handle(request.model_dump()))


@app.post("/api/v1/documents/search")
async def search_documents(request: SearchRequest, current_user: dict = Depends(get_current_user)) -> ApiResponse:
    chunks = rag_runtime.retrieve(request.query, request.context, request.top_k)
    return ApiResponse(data=[
        {
            "chunkId": chunk.chunk_id,
            "score": chunk.score,
            "content": chunk.content,
            "sourceTitle": chunk.source_title,
        }
        for chunk in chunks
    ])


@app.post("/api/v1/embeddings")
async def create_embedding(request: SearchRequest, current_user: dict = Depends(get_current_user)) -> ApiResponse:
    return ApiResponse(data=rag_runtime.embedding(request.query))


@app.post("/api/v1/chat")
async def chat(request: ChatRequest, current_user: dict = Depends(get_current_user)):
    """OpenSpec: report-generation / REQ-AI-001 / controlled SSE stream for AI generation."""
    if prompt_guard.is_injection(request.question):
        raise PromptInjectionException()

    async def generate():
        task_id = str((request.context or {}).get("taskId", "chat-preview"))
        chunks = rag_runtime.retrieve(request.question, request.context, 5)
        references, trace_id = rag_runtime.references(task_id, chunks)
        yield create_sse_event(
            type="stage",
            task_id=task_id,
            content="Retrieving authorized knowledge",
            stage="retrieval",
            progress=0.3,
        )
        yield create_sse_event(
            type="delta",
            task_id=task_id,
            content=rag_runtime.answer_delta(request.question, chunks),
            stage="writing",
            progress=0.6,
        )
        yield create_sse_event(
            type="references",
            task_id=task_id,
            references=references,
            trace_id=trace_id,
        )
        yield create_sse_event(type="done", task_id=task_id, content="completed", stage="export", progress=1.0)

    return StreamingResponse(generate(), media_type="text/event-stream")
