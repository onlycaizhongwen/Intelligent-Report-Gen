CREATE TABLE IF NOT EXISTS file_objects (
  id BIGSERIAL PRIMARY KEY,
  bucket VARCHAR(128) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  checksum VARCHAR(128),
  storage_purpose VARCHAR(64) NOT NULL,
  owner_user_id BIGINT,
  sensitivity_level VARCHAR(32) NOT NULL DEFAULT 'internal',
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_objects_bucket_key
ON file_objects(bucket, object_key)
WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS knowledge_documents (
  id BIGSERIAL PRIMARY KEY,
  knowledge_base_id BIGINT NOT NULL,
  file_object_id BIGINT NOT NULL,
  document_title VARCHAR(255) NOT NULL,
  file_type VARCHAR(64) NOT NULL,
  parse_status VARCHAR(40) NOT NULL DEFAULT 'pending',
  ocr_required BOOLEAN NOT NULL DEFAULT FALSE,
  table_recognition_required BOOLEAN NOT NULL DEFAULT FALSE,
  uploaded_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_knowledge_documents_base_status
ON knowledge_documents(knowledge_base_id, parse_status)
WHERE deleted_at IS NULL;
