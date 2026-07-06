CREATE TABLE IF NOT EXISTS organization_units (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES organization_units(id),
    unit_type VARCHAR(40) NOT NULL DEFAULT 'department',
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_organization_units_parent
    ON organization_units(parent_id, sort_order, id)
    WHERE status = 'enabled';

CREATE TABLE IF NOT EXISTS organization_positions (
    id BIGSERIAL PRIMARY KEY,
    organization_unit_id BIGINT NOT NULL REFERENCES organization_units(id),
    code VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    roles TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    manager_user_id BIGINT REFERENCES user_accounts(id),
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_organization_positions_unit_code UNIQUE (organization_unit_id, code)
);

CREATE INDEX IF NOT EXISTS idx_organization_positions_unit
    ON organization_positions(organization_unit_id, sort_order, id)
    WHERE status = 'enabled';

CREATE INDEX IF NOT EXISTS idx_organization_positions_roles
    ON organization_positions USING GIN (roles);
