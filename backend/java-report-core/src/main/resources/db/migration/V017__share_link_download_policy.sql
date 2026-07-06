ALTER TABLE share_links
ADD COLUMN IF NOT EXISTS allow_download BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_share_links_download
ON share_links(report_id, allow_download, status);
