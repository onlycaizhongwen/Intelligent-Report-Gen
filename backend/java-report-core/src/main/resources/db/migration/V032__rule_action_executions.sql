CREATE TABLE IF NOT EXISTS rule_action_executions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rules(id),
    run_id BIGINT NOT NULL REFERENCES rule_debug_runs(id),
    node_id VARCHAR(128) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INTEGER,
    max_retry_count INTEGER,
    endpoint TEXT,
    idempotency_key VARCHAR(256),
    next_retry_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rule_action_executions_rule_created
    ON rule_action_executions(rule_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_rule_action_executions_status_next_retry
    ON rule_action_executions(status, next_retry_at, id)
    WHERE status IN ('pending_retry', 'failed');
