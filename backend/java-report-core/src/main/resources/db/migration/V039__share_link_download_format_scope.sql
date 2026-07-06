ALTER TABLE share_links
ADD COLUMN IF NOT EXISTS allowed_download_formats JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_share_links_allowed_download_formats
ON share_links USING GIN (allowed_download_formats);
