ALTER TABLE rule_approval_delegate_rules
    ADD COLUMN IF NOT EXISTS active_weekdays JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_rule_approval_delegate_rules_weekdays
    ON rule_approval_delegate_rules USING GIN (active_weekdays);
