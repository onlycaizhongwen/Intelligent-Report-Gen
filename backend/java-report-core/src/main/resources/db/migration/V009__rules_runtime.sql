CREATE TABLE IF NOT EXISTS rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    definition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    current_version_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS rule_versions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rules(id),
    definition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    validation_status VARCHAR(32) NOT NULL DEFAULT 'valid',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rule_debug_runs (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rules(id),
    version_id BIGINT REFERENCES rule_versions(id),
    status VARCHAR(32) NOT NULL,
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rules_status_updated_at ON rules(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_rule_versions_rule_id ON rule_versions(rule_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_rule_debug_runs_rule_id ON rule_debug_runs(rule_id, id DESC);
