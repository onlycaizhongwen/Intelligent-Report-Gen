ALTER TABLE rules
    ADD COLUMN IF NOT EXISTS schedule_locked_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_rules_schedule_lease
    ON rules(schedule_locked_until)
    WHERE deleted_at IS NULL AND schedule_locked_until IS NOT NULL;
