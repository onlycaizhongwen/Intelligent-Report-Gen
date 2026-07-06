ALTER TABLE rule_debug_runs
    ADD COLUMN IF NOT EXISTS triggered_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT,
    ADD COLUMN IF NOT EXISTS error_message TEXT;

CREATE INDEX IF NOT EXISTS idx_rule_debug_runs_rule_type_status_id
    ON rule_debug_runs(rule_id, run_type, status, id DESC);
