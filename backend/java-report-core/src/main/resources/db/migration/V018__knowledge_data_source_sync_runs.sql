CREATE TABLE IF NOT EXISTS knowledge_data_source_sync_runs (
    id BIGSERIAL PRIMARY KEY,
    data_source_id BIGINT NOT NULL REFERENCES knowledge_data_sources(id),
    mode VARCHAR(32) NOT NULL DEFAULT 'manual',
    status VARCHAR(32) NOT NULL,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    failure_reason TEXT,
    message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_knowledge_data_source_sync_runs_source
ON knowledge_data_source_sync_runs(data_source_id, started_at DESC);
