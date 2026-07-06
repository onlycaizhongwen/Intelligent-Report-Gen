CREATE TABLE IF NOT EXISTS report_sections (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  version_id BIGINT,
  section_no INT NOT NULL,
  heading VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  citation_marks JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_report_sections_report_version
ON report_sections(report_id, version_id, section_no)
WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS report_versions (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  snapshot JSONB NOT NULL,
  created_by BIGINT NOT NULL,
  change_reason VARCHAR(255),
  is_current BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_versions_report_no
ON report_versions(report_id, version_no);

CREATE INDEX IF NOT EXISTS idx_report_versions_current
ON report_versions(report_id, is_current);
