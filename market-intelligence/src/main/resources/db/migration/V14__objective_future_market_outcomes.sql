CREATE TABLE observed_market_outcomes (
    outcome_id UUID PRIMARY KEY,
    market_id UUID NOT NULL,
    evaluation_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    horizon_seconds BIGINT NOT NULL,
    maturity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    reference_price NUMERIC(38, 18),
    reference_observation_fingerprint VARCHAR(64),
    final_price NUMERIC(38, 18),
    future_high NUMERIC(38, 18),
    future_low NUMERIC(38, 18),
    forward_return NUMERIC(38, 18),
    future_high_return NUMERIC(38, 18),
    future_low_return NUMERIC(38, 18),
    future_range NUMERIC(38, 18),
    expected_observation_count INTEGER NOT NULL,
    observed_observation_count INTEGER NOT NULL,
    reference_normalization_version VARCHAR(80),
    future_normalization_versions VARCHAR(500),
    future_evidence_fingerprint VARCHAR(64),
    outcome_definition_version VARCHAR(80) NOT NULL,
    outcome_fingerprint VARCHAR(64) NOT NULL UNIQUE,
    computed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    analysis_execution_id UUID
);

CREATE INDEX idx_observed_outcome_market_cutoff_horizon
    ON observed_market_outcomes(market_id, evaluation_cutoff, horizon_seconds);

CREATE INDEX idx_observed_outcome_analysis_execution
    ON observed_market_outcomes(analysis_execution_id);
