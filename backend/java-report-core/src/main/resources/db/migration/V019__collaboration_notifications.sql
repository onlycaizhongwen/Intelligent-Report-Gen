CREATE TABLE IF NOT EXISTS collaboration_notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    type VARCHAR(80) NOT NULL,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    task_id BIGINT NOT NULL REFERENCES collaboration_tasks(id),
    status VARCHAR(32) NOT NULL DEFAULT 'unread',
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_collaboration_notifications_recipient
    ON collaboration_notifications(recipient_user_id, status, created_at DESC);
