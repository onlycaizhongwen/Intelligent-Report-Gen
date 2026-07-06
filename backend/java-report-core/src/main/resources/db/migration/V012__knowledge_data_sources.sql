CREATE TABLE IF NOT EXISTS knowledge_data_sources (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    endpoint TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_knowledge_data_sources_owner ON knowledge_data_sources(owner_user_id, updated_at DESC);
