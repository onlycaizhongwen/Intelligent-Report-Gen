from app.document_processing.application.vector_index import MilvusVectorIndex


def test_milvus_vector_index_creates_collection_and_upserts_embedding():
    client = RecordingMilvusClient()
    index = MilvusVectorIndex(client=client, collection_name="knowledge_chunks", dimension=64)

    vector_ref = index.upsert_chunk(
        {
            "chunkId": "doc_99_chunk_0",
            "documentId": 99,
            "chunkIndex": 0,
            "content": "Revenue risk evidence",
            "embeddingModel": "local-hash-embedding",
            "embeddingDimension": 64,
            "embeddingVector": [0.1] * 64,
        }
    )

    assert vector_ref == "doc_99_chunk_0"
    assert client.schema.fields["chunk_id"] == {
        "datatype": client.DataType.VARCHAR,
        "is_primary": True,
        "max_length": 128,
    }
    assert client.schema.fields["embedding"] == {
        "datatype": client.DataType.FLOAT_VECTOR,
        "dim": 64,
    }
    assert client.created_collection["collection_name"] == "knowledge_chunks"
    assert client.created_collection["schema"] is client.schema
    assert client.created_collection["index_params"] is client.index_params
    assert client.inserted[0]["collection_name"] == "knowledge_chunks"
    assert client.inserted[0]["data"][0]["chunk_id"] == "doc_99_chunk_0"
    assert client.inserted[0]["data"][0]["document_id"] == 99
    assert client.inserted[0]["data"][0]["content"] == "Revenue risk evidence"
    assert client.inserted[0]["data"][0]["embedding"] == [0.1] * 64
    assert client.flushed == "knowledge_chunks"
    assert client.loaded == "knowledge_chunks"


def test_milvus_vector_index_deletes_chunks_by_primary_key():
    client = RecordingMilvusClient()
    index = MilvusVectorIndex(client=client, collection_name="knowledge_chunks", dimension=64)

    index.delete_chunks(["doc_99_chunk_0", "doc_99_chunk_1"])

    assert client.deleted == {
        "collection_name": "knowledge_chunks",
        "filter": 'chunk_id in ["doc_99_chunk_0","doc_99_chunk_1"]',
    }


class RecordingMilvusClient:
    class DataType:
        INT64 = "INT64"
        VARCHAR = "VARCHAR"
        FLOAT_VECTOR = "FLOAT_VECTOR"

    def __init__(self):
        self.created_collection = None
        self.inserted = []
        self.deleted = None
        self.flushed = None
        self.loaded = None
        self.collections = set()
        self.schema = RecordingSchema()
        self.index_params = RecordingIndexParams()

    def has_collection(self, collection_name):
        return collection_name in self.collections

    def create_schema(self, **kwargs):
        self.schema.kwargs = kwargs
        return self.schema

    def prepare_index_params(self):
        return self.index_params

    def create_collection(self, **kwargs):
        self.created_collection = kwargs
        self.collections.add(kwargs["collection_name"])

    def insert(self, collection_name, data):
        self.inserted.append({"collection_name": collection_name, "data": data})

    def delete(self, collection_name, filter):
        self.deleted = {"collection_name": collection_name, "filter": filter}

    def flush(self, collection_name):
        self.flushed = collection_name

    def load_collection(self, collection_name):
        self.loaded = collection_name


class RecordingSchema:
    def __init__(self):
        self.kwargs = {}
        self.fields = {}

    def add_field(self, field_name, datatype, **kwargs):
        self.fields[field_name] = {"datatype": datatype, **kwargs}


class RecordingIndexParams:
    def __init__(self):
        self.indexes = []

    def add_index(self, **kwargs):
        self.indexes.append(kwargs)
