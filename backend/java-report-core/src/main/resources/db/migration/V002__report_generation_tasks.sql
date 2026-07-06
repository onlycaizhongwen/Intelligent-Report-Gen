CREATE TABLE IF NOT EXISTS reports (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  report_type VARCHAR(64) NOT NULL DEFAULT 'natural_language',
  owner_user_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'draft',
  current_version_id BIGINT,
  knowledge_scope JSONB NOT NULL DEFAULT '{}'::jsonb,
  summary TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_reports_owner_status
ON reports(owner_user_id, status)
WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS report_generation_tasks (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT,
  created_by BIGINT NOT NULL,
  generation_mode VARCHAR(40) NOT NULL,
  user_input JSONB NOT NULL,
  template_snapshot JSONB,
  status VARCHAR(40) NOT NULL DEFAULT 'pending',
  current_stage VARCHAR(64),
  progress INT NOT NULL DEFAULT 0,
  failure_reason TEXT,
  trace_id VARCHAR(96),
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_report_generation_tasks_creator_status
ON report_generation_tasks(created_by, status);

CREATE INDEX IF NOT EXISTS idx_report_generation_tasks_report
ON report_generation_tasks(report_id);

CREATE TABLE IF NOT EXISTS report_outlines (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  report_id BIGINT,
  outline_content JSONB NOT NULL,
  confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  confirmed_by BIGINT,
  confirmed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_outlines_task
ON report_outlines(task_id);
