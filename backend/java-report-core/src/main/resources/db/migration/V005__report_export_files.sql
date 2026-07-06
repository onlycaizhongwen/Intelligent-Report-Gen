CREATE TABLE IF NOT EXISTS report_export_files (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  format VARCHAR(32) NOT NULL,
  template_id VARCHAR(128),
  download_policy VARCHAR(64) NOT NULL DEFAULT 'presigned_url',
  bucket VARCHAR(128) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  download_url TEXT,
  expires_at TIMESTAMPTZ,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_report_export_files_report
ON report_export_files(report_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_export_files_object
ON report_export_files(bucket, object_key);
