ALTER TABLE share_links
ADD COLUMN IF NOT EXISTS password_hash VARCHAR(160);

CREATE INDEX IF NOT EXISTS idx_share_links_status
ON share_links(status, expires_at);
