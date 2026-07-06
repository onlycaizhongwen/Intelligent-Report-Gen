ALTER TABLE rules
    ADD COLUMN IF NOT EXISTS schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS schedule_interval_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS failure_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_retry_count INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS schedule_input_json JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_rules_schedule_due
    ON rules(schedule_enabled, status, next_run_at, id)
    WHERE deleted_at IS NULL;
