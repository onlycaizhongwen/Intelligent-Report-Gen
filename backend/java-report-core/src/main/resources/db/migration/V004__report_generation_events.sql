CREATE TABLE IF NOT EXISTS report_generation_events (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  stage VARCHAR(64),
  content TEXT,
  references_payload JSONB NOT NULL DEFAULT '[]'::jsonb,
  progress DOUBLE PRECISION,
  error_code VARCHAR(80),
  trace_id VARCHAR(96),
  sequence_no INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_generation_events_task_sequence
ON report_generation_events(task_id, sequence_no);

CREATE INDEX IF NOT EXISTS idx_report_generation_events_task_order
ON report_generation_events(task_id, sequence_no, id);
