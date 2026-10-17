CREATE TABLE historical_funding_evidence (
    evidence_id UUID NOT NULL,
    market_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    provider_symbol VARCHAR(255) NOT NULL,
    product_type VARCHAR(40) NOT NULL,
    funding_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    effective_from TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP(6) WITH TIME ZONE,
    funding_rate NUMERIC(38, 18) NOT NULL,
    settlement_asset VARCHAR(32) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    source_updated_at TIMESTAMP(6) WITH TIME ZONE,
    fetched_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    known_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    origin VARCHAR(32) NOT NULL,
    normalization_version VARCHAR(64) NOT NULL,
    evidence_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT pk_historical_funding_evidence PRIMARY KEY (evidence_id),
    CONSTRAINT uk_historical_funding_fingerprint UNIQUE (evidence_fingerprint),
    CONSTRAINT fk_historical_funding_market FOREIGN KEY (market_id) REFERENCES markets (market_id),
    CONSTRAINT ck_historical_funding_temporal CHECK (fetched_at <= captured_at AND captured_at <= known_at
        AND (effective_to IS NULL OR effective_from < effective_to)),
    CONSTRAINT ck_historical_funding_origin CHECK (origin IN ('CAPTURED', 'HISTORICAL_BACKFILL'))
);

CREATE INDEX ix_historical_funding_market_cutoff
    ON historical_funding_evidence (market_id, funding_time, known_at);
