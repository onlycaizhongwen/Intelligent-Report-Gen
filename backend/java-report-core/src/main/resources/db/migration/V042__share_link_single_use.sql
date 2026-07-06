ALTER TABLE share_links
ADD COLUMN IF NOT EXISTS single_use BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_share_links_single_use
ON share_links(status, single_use);
