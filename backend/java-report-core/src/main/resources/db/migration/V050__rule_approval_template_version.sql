ALTER TABLE rule_approval_templates
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_rule_approval_templates_version
    ON rule_approval_templates(version);
