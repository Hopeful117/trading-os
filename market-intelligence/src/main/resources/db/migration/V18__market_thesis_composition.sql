CREATE TABLE market_theses (
    thesis_id UUID PRIMARY KEY,
    lineage_id UUID NOT NULL,
    version BIGINT NOT NULL,
    market_id UUID NOT NULL,
    instrument VARCHAR(120) NOT NULL,
    state VARCHAR(30) NOT NULL,
    scenario_direction VARCHAR(20) NOT NULL,
    composer_version VARCHAR(100) NOT NULL,
    input_fingerprint VARCHAR(64) NOT NULL,
    analysis_execution_id UUID,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE,
    supersedes UUID,
    payload TEXT NOT NULL,
    CONSTRAINT uq_market_thesis_lineage_version UNIQUE (lineage_id, version),
    CONSTRAINT uq_market_thesis_fingerprint UNIQUE (input_fingerprint)
);

CREATE INDEX idx_market_theses_market_evaluated
    ON market_theses (market_id, evaluated_at DESC);
