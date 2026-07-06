CREATE TABLE IF NOT EXISTS rule_approval_template_versions (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES rule_approval_templates(id),
    version INTEGER NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    steps_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(template_id, version)
);

CREATE INDEX IF NOT EXISTS idx_rule_approval_template_versions_template
    ON rule_approval_template_versions(template_id, version);

INSERT INTO rule_approval_template_versions(
    template_id, version, name, description, status, steps_json, created_by_user_id, created_at, updated_at
)
SELECT id, version, name, description, status, steps_json, created_by_user_id, created_at, updated_at
FROM rule_approval_templates
ON CONFLICT (template_id, version) DO NOTHING;
