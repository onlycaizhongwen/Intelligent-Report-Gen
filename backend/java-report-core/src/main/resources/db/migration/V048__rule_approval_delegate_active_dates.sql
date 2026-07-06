ALTER TABLE rule_approval_delegate_rules
    ADD COLUMN IF NOT EXISTS active_dates JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_rule_approval_delegate_rules_active_dates
    ON rule_approval_delegate_rules USING GIN (active_dates);
