CREATE TABLE IF NOT EXISTS system_alerts (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    type VARCHAR(100) NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'info',
    status VARCHAR(32) NOT NULL DEFAULT 'unread',
    resource_type VARCHAR(100) NOT NULL,
    resource_id BIGINT,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_alerts_recipient_status_created
    ON system_alerts(recipient_user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_system_alerts_resource
    ON system_alerts(resource_type, resource_id);
