CREATE TABLE historical_source_retirement_acknowledgement (
    operation_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_symbol VARCHAR(255) NOT NULL,
    market_id UUID NOT NULL,
    depth INTEGER NOT NULL,
    dependency_fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_historical_source_retirement_ack PRIMARY KEY (operation_id),
    CONSTRAINT ck_historical_source_retirement_ack_depth CHECK (depth > 0),
    CONSTRAINT ck_historical_source_retirement_ack_state CHECK
        (state IN ('ACKNOWLEDGED', 'CONFIRMED', 'RECONCILIATION_REQUIRED'))
);
