CREATE TABLE IF NOT EXISTS organization_position_assignments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_accounts(id),
    position_id BIGINT NOT NULL REFERENCES organization_positions(id),
    primary_position BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_organization_position_assignments_user_position UNIQUE (user_id, position_id)
);

CREATE INDEX IF NOT EXISTS idx_organization_position_assignments_user
    ON organization_position_assignments(user_id, primary_position DESC, id)
    WHERE status = 'enabled';

CREATE INDEX IF NOT EXISTS idx_organization_position_assignments_position
    ON organization_position_assignments(position_id, primary_position DESC, id)
    WHERE status = 'enabled';
