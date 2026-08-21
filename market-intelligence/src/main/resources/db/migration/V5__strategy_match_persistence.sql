CREATE TABLE strategy_matches (
    match_id UUID NOT NULL,
    strategy_id UUID NOT NULL,
    strategy_version INTEGER NOT NULL,
    market_id UUID NOT NULL,
    analysis_execution_id UUID NOT NULL,
    observation_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    context_digest VARCHAR(64) NOT NULL,
    condition_results TEXT NOT NULL,
    matched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_strategy_matches PRIMARY KEY (match_id),
    CONSTRAINT uq_strategy_match_identity UNIQUE
        (strategy_id, strategy_version, market_id, analysis_execution_id, context_digest)
);

CREATE INDEX idx_strategy_matches_analysis ON strategy_matches(analysis_execution_id);
CREATE INDEX idx_strategy_matches_strategy ON strategy_matches(strategy_id, strategy_version);
