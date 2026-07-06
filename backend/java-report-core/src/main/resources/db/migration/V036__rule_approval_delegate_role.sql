ALTER TABLE rule_approval_records
    ADD COLUMN IF NOT EXISTS delegate_role VARCHAR(100);
