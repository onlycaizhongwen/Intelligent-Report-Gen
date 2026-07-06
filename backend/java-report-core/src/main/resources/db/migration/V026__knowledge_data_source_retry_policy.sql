ALTER TABLE knowledge_data_sources
ADD COLUMN IF NOT EXISTS max_retry_count INTEGER NOT NULL DEFAULT 3;

UPDATE knowledge_data_sources
SET max_retry_count = 3
WHERE max_retry_count IS NULL;
