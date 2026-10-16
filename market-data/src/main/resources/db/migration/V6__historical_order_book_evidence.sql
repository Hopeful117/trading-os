CREATE TABLE historical_order_book_sequence (
    sequence_id UUID NOT NULL,
    market_id UUID NOT NULL,
    depth INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    invalidated_at TIMESTAMP(6) WITH TIME ZONE,
    invalidation_reason VARCHAR(255),
    CONSTRAINT pk_historical_order_book_sequence PRIMARY KEY (sequence_id),
    CONSTRAINT fk_historical_order_book_sequence_market FOREIGN KEY (market_id) REFERENCES markets (market_id),
    CONSTRAINT ck_historical_order_book_sequence_depth CHECK (depth > 0),
    CONSTRAINT ck_historical_order_book_sequence_status CHECK (status IN ('ACTIVE', 'INVALIDATED'))
);

CREATE TABLE historical_order_book_evidence (
    evidence_id UUID NOT NULL,
    market_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    provider_symbol VARCHAR(255),
    depth INTEGER NOT NULL,
    evidence_type VARCHAR(16) NOT NULL,
    origin VARCHAR(16) NOT NULL,
    source_start_ordinal BIGINT,
    source_end_ordinal BIGINT,
    sequence_id UUID NOT NULL,
    sequence_ordinal BIGINT NOT NULL,
    provider_sequence BIGINT,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    known_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    bids_payload TEXT NOT NULL,
    asks_payload TEXT NOT NULL,
    checksum BIGINT,
    normalization_version VARCHAR(64) NOT NULL,
    content_fingerprint VARCHAR(64) NOT NULL,
    trusted BOOLEAN NOT NULL,
    CONSTRAINT pk_historical_order_book_evidence PRIMARY KEY (evidence_id),
    CONSTRAINT uk_historical_order_book_fingerprint UNIQUE (content_fingerprint),
    CONSTRAINT fk_historical_order_book_market FOREIGN KEY (market_id) REFERENCES markets (market_id),
    CONSTRAINT fk_historical_order_book_sequence FOREIGN KEY (sequence_id) REFERENCES historical_order_book_sequence (sequence_id),
    CONSTRAINT ck_historical_order_book_depth CHECK (depth > 0),
    CONSTRAINT ck_historical_order_book_sequence CHECK (sequence_ordinal >= 0),
    CONSTRAINT ck_historical_order_book_type CHECK (evidence_type IN ('SNAPSHOT', 'DELTA')),
    CONSTRAINT ck_historical_order_book_origin CHECK (origin IN ('PROVIDER', 'CHECKPOINT'))
);

CREATE INDEX ix_historical_order_book_replay
    ON historical_order_book_evidence (market_id, depth, sequence_id, known_at, sequence_ordinal);

CREATE INDEX ix_historical_order_book_captured
    ON historical_order_book_evidence (market_id, depth, occurred_at);

CREATE INDEX ix_historical_order_book_sequence_status
    ON historical_order_book_sequence (market_id, depth, status);
