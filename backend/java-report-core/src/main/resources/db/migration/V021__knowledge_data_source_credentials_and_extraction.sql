ALTER TABLE knowledge_data_sources
ADD COLUMN IF NOT EXISTS username VARCHAR(200),
ADD COLUMN IF NOT EXISTS credential_secret TEXT,
ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT,
ADD COLUMN IF NOT EXISTS sync_query TEXT,
ADD COLUMN IF NOT EXISTS last_cursor VARCHAR(200);

ALTER TABLE knowledge_data_source_sync_runs
ADD COLUMN IF NOT EXISTS last_cursor VARCHAR(200);
