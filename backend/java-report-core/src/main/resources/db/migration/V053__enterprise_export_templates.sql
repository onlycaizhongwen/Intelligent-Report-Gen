CREATE TABLE IF NOT EXISTS enterprise_export_templates (
  id BIGSERIAL PRIMARY KEY,
  template_id VARCHAR(128) NOT NULL,
  name VARCHAR(255) NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  status VARCHAR(40) NOT NULL DEFAULT 'active',
  brand_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ,
  UNIQUE(template_id, version)
);

CREATE INDEX IF NOT EXISTS idx_enterprise_export_templates_latest
ON enterprise_export_templates(template_id, version DESC)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_enterprise_export_templates_status
ON enterprise_export_templates(status, updated_at DESC)
WHERE deleted_at IS NULL;
