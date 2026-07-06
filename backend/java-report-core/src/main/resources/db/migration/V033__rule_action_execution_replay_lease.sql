ALTER TABLE rule_action_executions
    ADD COLUMN IF NOT EXISTS replay_locked_until TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_rule_action_executions_replay_lease
    ON rule_action_executions(status, next_retry_at, replay_locked_until, id)
    WHERE action_type = 'webhook' AND status IN ('pending_retry', 'failed');
