ALTER TABLE user_accounts
    ADD COLUMN IF NOT EXISTS department VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS position VARCHAR(200) NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_user_accounts_department_position
    ON user_accounts(department, position)
    WHERE status = 'enabled';
