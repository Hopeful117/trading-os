CREATE TABLE opportunity_outcomes (
    outcome_id UUID PRIMARY KEY,
    opportunity_id UUID NOT NULL,
    opportunity_version BIGINT NOT NULL,
    market_id UUID,
    source_opportunity_fingerprint VARCHAR(64) NOT NULL,
    evaluation_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    horizon_seconds BIGINT NOT NULL,
    maturity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(40) NOT NULL,
    reason VARCHAR(500),
    market_outcome_market_id UUID,
    market_outcome_cutoff TIMESTAMP WITH TIME ZONE,
    market_outcome_horizon_seconds BIGINT,
    market_outcome_definition_version VARCHAR(80),
    market_outcome_fingerprint VARCHAR(64),
    outcome_definition_version VARCHAR(80) NOT NULL,
    outcome_fingerprint VARCHAR(64) NOT NULL UNIQUE,
    computed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    analysis_execution_id UUID
);

CREATE INDEX idx_opportunity_outcome_opportunity
    ON opportunity_outcomes(opportunity_id, opportunity_version);

CREATE INDEX idx_opportunity_outcome_status_computed
    ON opportunity_outcomes(status, computed_at);

CREATE INDEX idx_opportunity_outcome_market_cutoff_horizon
    ON opportunity_outcomes(market_id, evaluation_cutoff, horizon_seconds);
