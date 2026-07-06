ALTER TABLE knowledge_documents
ADD COLUMN IF NOT EXISTS parse_failure_reason TEXT;
