ALTER TABLE knowledge_data_sources
ADD COLUMN IF NOT EXISTS field_mapping_json TEXT;

CREATE INDEX IF NOT EXISTS idx_knowledge_data_sources_source_type
ON knowledge_data_sources(owner_user_id, source_type)
WHERE deleted_at IS NULL;
