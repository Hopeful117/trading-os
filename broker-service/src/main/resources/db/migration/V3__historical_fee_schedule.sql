CREATE TABLE historical_fee_schedule (
    schedule_id UUID NOT NULL,
    broker_account_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    instrument VARCHAR(255) NOT NULL,
    product_type VARCHAR(40) NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE,
    maker_rate NUMERIC(38, 18) NOT NULL,
    taker_rate NUMERIC(38, 18) NOT NULL,
    settlement_asset VARCHAR(32) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    known_at TIMESTAMP WITH TIME ZONE NOT NULL,
    origin VARCHAR(32) NOT NULL,
    normalization_version VARCHAR(64) NOT NULL,
    schedule_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT pk_historical_fee_schedule PRIMARY KEY (schedule_id),
    CONSTRAINT uk_historical_fee_schedule_fingerprint UNIQUE (schedule_fingerprint),
    CONSTRAINT ck_historical_fee_schedule_effective CHECK (
        effective_to IS NULL OR effective_from < effective_to
    ),
    CONSTRAINT ck_historical_fee_schedule_temporal CHECK (observed_at <= known_at),
    CONSTRAINT ck_historical_fee_schedule_origin CHECK (origin IN ('CAPTURED', 'HISTORICAL_BACKFILL'))
);

CREATE INDEX ix_historical_fee_schedule_lookup
    ON historical_fee_schedule (broker_account_id, provider, instrument, product_type, effective_from, known_at);
