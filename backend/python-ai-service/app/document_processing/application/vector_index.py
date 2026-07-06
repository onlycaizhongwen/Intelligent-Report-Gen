import os
import json
from typing import Any


class MilvusVectorIndex:
    def __init__(self, client: Any, collection_name: str = "knowledge_chunks", dimension: int = 64):
        self.client = client
        self.collection_name = collection_name
        self.dimension = dimension

    def upsert_chunk(self, chunk: dict) -> str:
        self._ensure_collection()
        primary_key = str(chunk["chunkId"])
        self.client.insert(
            collection_name=self.collection_name,
            data=[
                {
                    "chunk_id": primary_key,
                    "document_id": int(chunk["documentId"]),
                    "chunk_index": int(chunk["chunkIndex"]),
                    "content": str(chunk["content"]),
                    "embedding_model": str(chunk["embeddingModel"]),
                    "embedding": [float(value) for value in chunk["embeddingVector"]],
                }
            ],
        )
        if hasattr(self.client, "flush"):
            self.client.flush(self.collection_name)
        if hasattr(self.client, "load_collection"):
            self.client.load_collection(self.collection_name)
        return primary_key

    def delete_chunks(self, chunk_ids: list[str]) -> None:
        if not chunk_ids:
            return
        encoded_ids = ",".join(json.dumps(str(chunk_id), ensure_ascii=False) for chunk_id in chunk_ids)
        self.client.delete(
            collection_name=self.collection_name,
            filter=f"chunk_id in [{encoded_ids}]",
        )

    def _ensure_collection(self) -> None:
        if self.client.has_collection(self.collection_name):
            return
        data_type = getattr(self.client, "DataType", None)
        if data_type is None:
            from pymilvus import DataType

            data_type = DataType

        schema = self.client.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field(field_name="chunk_id", datatype=data_type.VARCHAR, is_primary=True, max_length=128)
        schema.add_field(field_name="document_id", datatype=data_type.INT64)
        schema.add_field(field_name="chunk_index", datatype=data_type.INT64)
        schema.add_field(field_name="content", datatype=data_type.VARCHAR, max_length=4096)
        schema.add_field(field_name="embedding_model", datatype=data_type.VARCHAR, max_length=128)
        schema.add_field(field_name="embedding", datatype=data_type.FLOAT_VECTOR, dim=self.dimension)

        index_params = self.client.prepare_index_params()
        index_params.add_index(
            field_name="embedding",
            index_type="AUTOINDEX",
            metric_type="COSINE",
        )
        self.client.create_collection(
            collection_name=self.collection_name,
            schema=schema,
            index_params=index_params,
        )


def configured_vector_index() -> MilvusVectorIndex | None:
    host = os.getenv("MILVUS_HOST", "").strip()
    port = os.getenv("MILVUS_PORT", "19530").strip()
    if not host:
        return None

    from pymilvus import MilvusClient

    uri = f"http://{host}:{port}"
    return MilvusVectorIndex(client=MilvusClient(uri=uri))
