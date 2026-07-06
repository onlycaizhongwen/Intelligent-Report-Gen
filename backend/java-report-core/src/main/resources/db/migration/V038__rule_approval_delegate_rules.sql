CREATE TABLE IF NOT EXISTS rule_approval_delegate_rules (
    id BIGSERIAL PRIMARY KEY,
    assignee_role VARCHAR(100) NOT NULL,
    delegate_role VARCHAR(100) NOT NULL,
    active_from TIMESTAMP WITH TIME ZONE,
    active_to TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL DEFAULT 'enabled',
    reason TEXT,
    created_by_user_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rule_approval_delegate_rules_active
    ON rule_approval_delegate_rules(assignee_role, status, active_from, active_to, id DESC);
