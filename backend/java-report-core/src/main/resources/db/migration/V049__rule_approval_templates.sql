CREATE TABLE IF NOT EXISTS rule_approval_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    steps_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rule_approval_templates_status
    ON rule_approval_templates(status);
