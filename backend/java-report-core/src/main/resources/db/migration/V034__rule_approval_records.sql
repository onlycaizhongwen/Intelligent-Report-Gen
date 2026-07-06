CREATE TABLE IF NOT EXISTS rule_approval_records (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rules(id),
    run_id BIGINT NOT NULL REFERENCES rule_debug_runs(id),
    node_id VARCHAR(100) NOT NULL,
    assignee_role VARCHAR(100) NOT NULL,
    approval_title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    approval_comment TEXT,
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rule_approval_records_rule_id ON rule_approval_records(rule_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_rule_approval_records_run_id ON rule_approval_records(run_id, id DESC);
