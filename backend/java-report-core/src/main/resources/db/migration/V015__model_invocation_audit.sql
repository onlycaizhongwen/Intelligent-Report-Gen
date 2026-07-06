CREATE TABLE IF NOT EXISTS model_invocations (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT,
  report_id BIGINT,
  actor_user_id BIGINT,
  provider VARCHAR(80),
  model_name VARCHAR(120),
  prompt_template_id VARCHAR(120),
  prompt_snapshot TEXT,
  context_snapshot TEXT,
  parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
  request_hash VARCHAR(160),
  status VARCHAR(40) NOT NULL,
  duration_ms BIGINT,
  input_tokens INTEGER,
  output_tokens INTEGER,
  total_tokens INTEGER,
  error_code VARCHAR(80),
  error_message TEXT,
  trace_id VARCHAR(160),
  audit_event_key VARCHAR(220) UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_model_invocations_task
ON model_invocations(task_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_model_invocations_report
ON model_invocations(report_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_model_invocations_trace
ON model_invocations(trace_id);

CREATE INDEX IF NOT EXISTS idx_model_invocations_model
ON model_invocations(provider, model_name, created_at DESC);

CREATE TABLE IF NOT EXISTS model_responses (
  id BIGSERIAL PRIMARY KEY,
  invocation_id BIGINT NOT NULL,
  response_order INTEGER NOT NULL DEFAULT 1,
  response_content TEXT,
  response_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  finish_reason VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_model_responses_invocation
ON model_responses(invocation_id, response_order);
