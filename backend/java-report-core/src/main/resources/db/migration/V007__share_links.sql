CREATE TABLE IF NOT EXISTS share_links (
  id BIGSERIAL PRIMARY KEY,
  report_id BIGINT NOT NULL REFERENCES reports(id),
  created_by BIGINT NOT NULL,
  share_token VARCHAR(120) NOT NULL UNIQUE,
  status VARCHAR(40) NOT NULL DEFAULT 'active',
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_share_links_report
ON share_links(report_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_share_links_token
ON share_links(share_token);
