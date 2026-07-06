-- Skill: S10 migration.gen
-- Migration: V002__init_vector_store.sql
-- Target: Milvus vector database
-- OpenSpec: knowledge-base-ingestion / report-citation-export-version
-- Requirement: REQ-KB-002, REQ-REPORT-003
-- Note: Milvus Collection creation is executed by Python AI service migration/bootstrap code or Milvus CLI, not by PostgreSQL.

CREATE COLLECTION IF NOT EXISTS knowledge_chunks (
  milvus_primary_key VARCHAR(160) PRIMARY KEY,
  chunk_id BIGINT,
  document_id BIGINT,
  knowledge_base_id BIGINT,
  knowledge_entry_id BIGINT,
  content_hash VARCHAR(128),
  source_type VARCHAR(64),
  embedding FLOAT_VECTOR(1024),
  created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_embedding
ON knowledge_chunks (embedding)
WITH (index_type = 'HNSW', metric_type = 'COSINE');

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_document
ON knowledge_chunks (document_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_knowledge_base
ON knowledge_chunks (knowledge_base_id);
