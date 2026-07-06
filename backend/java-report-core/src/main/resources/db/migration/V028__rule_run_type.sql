ALTER TABLE rule_debug_runs
    ADD COLUMN IF NOT EXISTS run_type VARCHAR(32) NOT NULL DEFAULT 'debug';

CREATE INDEX IF NOT EXISTS idx_rule_debug_runs_rule_type_id
    ON rule_debug_runs(rule_id, run_type, id DESC);
