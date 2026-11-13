ALTER TABLE broker_account
    ADD COLUMN risk_profile_id UUID;

ALTER TABLE broker_account
    ADD COLUMN risk_profile_semantic_version VARCHAR(64);
