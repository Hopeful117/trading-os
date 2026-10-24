CREATE TABLE challenge_definition_phase (
    definition_id UUID NOT NULL,
    definition_version VARCHAR(64) NOT NULL,
    phase_key VARCHAR(80) NOT NULL,
    phase_order INTEGER NOT NULL CHECK (phase_order > 0),
    profit_target_ratio NUMERIC(30,12),
    progression_value_source VARCHAR(32),
    minimum_trading_days INTEGER CHECK (minimum_trading_days IS NULL OR minimum_trading_days > 0),
    risk_policy_id UUID,
    risk_policy_version VARCHAR(64),
    provider_confirmation_required BOOLEAN NOT NULL,
    PRIMARY KEY (definition_id, definition_version, phase_key),
    UNIQUE (definition_id, definition_version, phase_order),
    FOREIGN KEY (definition_id, definition_version)
        REFERENCES challenge_definition(id, definition_version),
    FOREIGN KEY (risk_policy_id, risk_policy_version)
        REFERENCES risk_profile(id, semantic_version),
    CHECK ((profit_target_ratio IS NULL AND progression_value_source IS NULL)
        OR (profit_target_ratio IS NOT NULL AND profit_target_ratio > 0
            AND progression_value_source IS NOT NULL)),
    CHECK ((risk_policy_id IS NULL AND risk_policy_version IS NULL)
        OR (risk_policy_id IS NOT NULL AND risk_policy_version IS NOT NULL))
);

ALTER TABLE challenge_instance ADD COLUMN active_risk_policy_id UUID;
ALTER TABLE challenge_instance ADD COLUMN active_risk_policy_version VARCHAR(64);
ALTER TABLE challenge_instance ADD COLUMN current_phase_key VARCHAR(80);
ALTER TABLE challenge_instance ADD COLUMN phase_progression_state VARCHAR(32);
ALTER TABLE challenge_instance ADD COLUMN phase_progression_evidence TEXT;
ALTER TABLE challenge_instance ADD COLUMN phase_progressed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE challenge_instance
    ADD CONSTRAINT fk_challenge_active_risk_policy
    FOREIGN KEY (active_risk_policy_id, active_risk_policy_version)
    REFERENCES risk_profile(id, semantic_version);
