CREATE TABLE signal_replay_results (
    result_id UUID PRIMARY KEY,
    result_fingerprint VARCHAR(64) NOT NULL UNIQUE,
    market_id UUID NOT NULL,
    product VARCHAR(80) NOT NULL,
    instrument VARCHAR(120) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    timeframe VARCHAR(20) NOT NULL,
    evaluation_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    strategy_id UUID NOT NULL,
    strategy_version INTEGER NOT NULL,
    evidence_policy VARCHAR(40) NOT NULL,
    evidence_status VARCHAR(40) NOT NULL,
    evidence_fingerprint VARCHAR(64) NOT NULL,
    context_fingerprint VARCHAR(64) NOT NULL,
    cost_assumption_version VARCHAR(80) NOT NULL,
    evaluation_status VARCHAR(40) NOT NULL,
    direction VARCHAR(20),
    spread NUMERIC(30, 12) NOT NULL,
    transaction_cost NUMERIC(30, 12) NOT NULL,
    evidence_sample_count INTEGER NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_signal_replay_results_market_cutoff
    ON signal_replay_results (market_id, evaluation_cutoff);
