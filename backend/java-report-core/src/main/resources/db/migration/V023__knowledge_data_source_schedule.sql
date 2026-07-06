ALTER TABLE knowledge_data_sources
ADD COLUMN IF NOT EXISTS schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS schedule_interval_seconds INTEGER,
ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS failure_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_knowledge_data_sources_due_schedule
ON knowledge_data_sources(next_run_at, id)
WHERE deleted_at IS NULL AND status = 'enabled' AND schedule_enabled = TRUE;
