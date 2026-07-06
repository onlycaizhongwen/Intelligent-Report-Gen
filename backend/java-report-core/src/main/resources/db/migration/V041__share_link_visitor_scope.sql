ALTER TABLE share_links
ADD COLUMN IF NOT EXISTS allowed_visitors JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE share_links
ADD COLUMN IF NOT EXISTS allowed_visitor_domains JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_share_links_allowed_visitors
ON share_links USING GIN (allowed_visitors);

CREATE INDEX IF NOT EXISTS idx_share_links_allowed_visitor_domains
ON share_links USING GIN (allowed_visitor_domains);
