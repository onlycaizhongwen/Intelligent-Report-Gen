CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_user_id BIGINT,
  operation_type VARCHAR(80) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id BIGINT,
  result VARCHAR(40) NOT NULL,
  detail JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_operation_logs_type_created
ON operation_logs(operation_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_operation_logs_resource
ON operation_logs(resource_type, resource_id);
