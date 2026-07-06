ALTER TABLE rule_approval_records
    ADD COLUMN IF NOT EXISTS delegate_active_from TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS delegate_active_to TIMESTAMP WITH TIME ZONE;
