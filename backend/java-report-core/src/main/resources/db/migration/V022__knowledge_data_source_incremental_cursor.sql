ALTER TABLE knowledge_data_sources
ADD COLUMN IF NOT EXISTS cursor_column VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_knowledge_data_sources_cursor
ON knowledge_data_sources(owner_user_id, cursor_column)
WHERE deleted_at IS NULL;
