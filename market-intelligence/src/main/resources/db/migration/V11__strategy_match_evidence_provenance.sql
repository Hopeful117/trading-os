ALTER TABLE strategy_matches
    ADD COLUMN observation_lineage_id UUID;
ALTER TABLE strategy_matches
    ADD COLUMN observation_version BIGINT;
ALTER TABLE strategy_matches
    ADD COLUMN evidence_cut_off_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE strategy_matches
    ADD COLUMN evidence_profile_version VARCHAR(100);
ALTER TABLE strategy_matches
    ADD COLUMN evidence_rule_version VARCHAR(100);
ALTER TABLE strategy_matches
    ADD COLUMN evidence_input_fingerprint VARCHAR(128);
ALTER TABLE strategy_matches
    ADD COLUMN evidence_assessment_fingerprint VARCHAR(128);
