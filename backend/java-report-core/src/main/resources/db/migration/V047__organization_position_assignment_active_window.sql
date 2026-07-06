ALTER TABLE organization_position_assignments
    ADD COLUMN IF NOT EXISTS active_from TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS active_to TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_organization_position_assignments_active_window
    ON organization_position_assignments(user_id, active_from, active_to)
    WHERE status = 'enabled';
