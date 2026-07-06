from app.rag_retrieval.application.local_rag_runtime import EvidenceChunk, LocalRagRuntime


def test_rag_runtime_uses_configured_retriever_before_context_fallback():
    retriever = RecordingRetriever([
        EvidenceChunk(
            chunk_id="doc_99_chunk_0",
            source_title="Indexed document",
            content="Receivables aging requires follow-up.",
            score=0.88,
        )
    ])
    runtime = LocalRagRuntime(retriever=retriever)

    chunks = runtime.retrieve(
        "receivables risk",
        {
            "knowledgeChunks": [
                {
                    "chunkId": "context-only",
                    "sourceTitle": "Fallback evidence",
                    "content": "Fallback content",
                    "score": 0.99,
                }
            ]
        },
        top_k=3,
    )

    assert chunks[0].chunk_id == "doc_99_chunk_0"
    assert retriever.calls[0]["question"] == "receivables risk"
    assert retriever.calls[0]["top_k"] == 3
    assert len(retriever.calls[0]["query_vector"]) == 64


def test_rag_runtime_falls_back_to_context_when_retriever_returns_no_hits():
    runtime = LocalRagRuntime(retriever=RecordingRetriever([]))

    chunks = runtime.retrieve(
        "east revenue",
        {
            "knowledgeChunks": [
                {
                    "chunkId": "context-1",
                    "sourceTitle": "Context document",
                    "content": "East revenue grew 12 percent.",
                    "score": 0.7,
                }
            ]
        },
        top_k=5,
    )

    assert chunks[0].chunk_id == "context-1"


class RecordingRetriever:
    def __init__(self, chunks):
        self.chunks = chunks
        self.calls = []

    def search(self, *, question, query_vector, top_k, context):
        self.calls.append(
            {
                "question": question,
                "query_vector": query_vector,
                "top_k": top_k,
                "context": context,
            }
        )
        return self.chunks
