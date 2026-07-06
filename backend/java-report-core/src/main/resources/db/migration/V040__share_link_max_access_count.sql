ALTER TABLE share_links
ADD COLUMN IF NOT EXISTS max_access_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_share_links_max_access_count
ON share_links(status, max_access_count);
