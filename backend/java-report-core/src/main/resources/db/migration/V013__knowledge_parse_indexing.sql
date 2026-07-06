CREATE TABLE IF NOT EXISTS document_parse_results (
  id BIGSERIAL PRIMARY KEY,
  document_id BIGINT NOT NULL,
  parse_type VARCHAR(40) NOT NULL DEFAULT 'text',
  result_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  confidence DOUBLE PRECISION,
  status VARCHAR(40) NOT NULL,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_parse_results_doc
ON document_parse_results(document_id, parse_type, created_at DESC);

CREATE TABLE IF NOT EXISTS document_chunks (
  id BIGSERIAL PRIMARY KEY,
  document_id BIGINT NOT NULL,
  knowledge_item_id BIGINT,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  page_no INT,
  position_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  parse_confidence DOUBLE PRECISION,
  embedding_status VARCHAR(40) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_chunks_doc_index
ON document_chunks(document_id, chunk_index)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_chunks_item
ON document_chunks(knowledge_item_id)
WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS embeddings (
  id BIGSERIAL PRIMARY KEY,
  chunk_id BIGINT NOT NULL,
  embedding_model VARCHAR(120) NOT NULL,
  vector_dimension INT NOT NULL,
  milvus_collection VARCHAR(120) NOT NULL,
  milvus_primary_key VARCHAR(160) NOT NULL,
  content_hash VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_embeddings_chunk_model
ON embeddings(chunk_id, embedding_model);

CREATE INDEX IF NOT EXISTS idx_embeddings_milvus_key
ON embeddings(milvus_collection, milvus_primary_key);
