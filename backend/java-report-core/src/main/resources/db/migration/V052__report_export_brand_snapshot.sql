ALTER TABLE report_export_files
ADD COLUMN IF NOT EXISTS brand_snapshot JSONB;
