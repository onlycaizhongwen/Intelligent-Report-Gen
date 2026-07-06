from app.rag_retrieval.application.indexed_rag_retriever import IndexedRagRetriever


def test_indexed_rag_retriever_combines_opensearch_and_milvus_hits():
    retriever = IndexedRagRetriever(
        search_index=RecordingSearchIndex(),
        vector_index=RecordingVectorSearch(),
    )

    chunks = retriever.search(
        question="receivables aging",
        query_vector=[0.1] * 64,
        top_k=3,
        context={"knowledgeBaseId": 7},
    )

    assert [chunk.chunk_id for chunk in chunks] == ["doc_99_chunk_1", "doc_99_chunk_0"]
    assert chunks[0].source_title == "Milvus indexed document"
    assert chunks[0].content == "Receivables aging requires follow-up."
    assert chunks[0].score == 0.92


class RecordingSearchIndex:
    def search(self, *, query, top_k, knowledge_base_id=None):
        assert query == "receivables aging"
        assert top_k == 3
        assert knowledge_base_id == 7
        return [
            {
                "chunkId": "doc_99_chunk_0",
                "sourceTitle": "OpenSearch indexed document",
                "content": "East revenue increased 12%.",
                "score": 0.71,
            }
        ]


class RecordingVectorSearch:
    def search(self, *, query_vector, top_k, knowledge_base_id=None):
        assert query_vector == [0.1] * 64
        assert top_k == 3
        assert knowledge_base_id == 7
        return [
            {
                "chunkId": "doc_99_chunk_1",
                "sourceTitle": "Milvus indexed document",
                "content": "Receivables aging requires follow-up.",
                "score": 0.92,
            }
        ]
