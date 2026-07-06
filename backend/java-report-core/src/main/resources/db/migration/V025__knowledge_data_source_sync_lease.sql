ALTER TABLE knowledge_data_sources
ADD COLUMN IF NOT EXISTS sync_locked_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_knowledge_data_sources_sync_lease
ON knowledge_data_sources(sync_locked_until)
WHERE deleted_at IS NULL;
