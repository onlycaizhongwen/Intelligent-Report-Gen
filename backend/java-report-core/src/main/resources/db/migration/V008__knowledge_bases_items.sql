CREATE TABLE IF NOT EXISTS knowledge_bases (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  owner_user_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'enabled',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_knowledge_bases_owner
ON knowledge_bases(owner_user_id, updated_at DESC)
WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS knowledge_items (
  id BIGSERIAL PRIMARY KEY,
  knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_bases(id),
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  source_type VARCHAR(80) NOT NULL DEFAULT 'manual',
  index_status VARCHAR(40) NOT NULL DEFAULT 'pending',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_knowledge_items_base_status
ON knowledge_items(knowledge_base_id, index_status)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_items_title
ON knowledge_items(title)
WHERE deleted_at IS NULL;
