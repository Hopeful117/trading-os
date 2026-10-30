CREATE TABLE historical_order_book_evidence_segment (
    segment_id UUID NOT NULL,
    market_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_symbol VARCHAR(255),
    depth INTEGER NOT NULL,
    sequence_id UUID NOT NULL,
    start_ordinal BIGINT NOT NULL,
    end_ordinal BIGINT,
    start_known_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    end_known_at TIMESTAMP(6) WITH TIME ZONE,
    successor_evidence_id UUID,
    successor_origin VARCHAR(32),
    status VARCHAR(16) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_historical_order_book_evidence_segment PRIMARY KEY (segment_id),
    CONSTRAINT uk_historical_order_book_evidence_segment_start
        UNIQUE (sequence_id, start_ordinal),
    CONSTRAINT fk_historical_order_book_segment_market
        FOREIGN KEY (market_id) REFERENCES markets (market_id),
    CONSTRAINT fk_historical_order_book_segment_sequence
        FOREIGN KEY (sequence_id) REFERENCES historical_order_book_sequence (sequence_id) ON DELETE CASCADE,
    CONSTRAINT ck_historical_order_book_segment_depth CHECK (depth > 0),
    CONSTRAINT ck_historical_order_book_segment_range
        CHECK (start_ordinal >= 0 AND (end_ordinal IS NULL OR end_ordinal >= start_ordinal)),
    CONSTRAINT ck_historical_order_book_segment_status
        CHECK (status IN ('OPEN', 'CLOSED', 'RETIRING', 'RETIRED')),
    CONSTRAINT ck_historical_order_book_segment_classification
        CHECK (classification IN ('CURRENT', 'LEGACY_UNKNOWN', 'LEGACY_CLASSIFIED')),
    CONSTRAINT ck_historical_order_book_segment_closure
        CHECK ((status = 'OPEN' AND end_ordinal IS NULL AND closed_at IS NULL)
            OR (status IN ('CLOSED', 'RETIRING', 'RETIRED') AND end_ordinal IS NOT NULL AND closed_at IS NOT NULL)),
    CONSTRAINT ck_historical_order_book_segment_successor
        CHECK ((successor_evidence_id IS NULL AND successor_origin IS NULL)
            OR (successor_evidence_id IS NOT NULL AND successor_origin IS NOT NULL))
);

CREATE INDEX ix_historical_order_book_segment_sequence
    ON historical_order_book_evidence_segment (sequence_id, start_ordinal, end_ordinal);

CREATE INDEX ix_historical_order_book_segment_status
    ON historical_order_book_evidence_segment (status, classification, end_ordinal);

ALTER TABLE historical_order_book_evidence_dependency
    ADD COLUMN segment_id UUID;

ALTER TABLE historical_order_book_evidence_dependency
    ADD CONSTRAINT fk_historical_order_book_dependency_segment
        FOREIGN KEY (segment_id) REFERENCES historical_order_book_evidence_segment (segment_id);

ALTER TABLE historical_order_book_evidence_dependency
    DROP CONSTRAINT ck_historical_order_book_dependency_target;

ALTER TABLE historical_order_book_evidence_dependency
    ADD CONSTRAINT ck_historical_order_book_dependency_target
        CHECK (artifact_id IS NOT NULL OR sequence_id IS NOT NULL OR segment_id IS NOT NULL);

CREATE INDEX ix_historical_order_book_dependency_segment
    ON historical_order_book_evidence_dependency (segment_id, state);

ALTER TABLE historical_order_book_retention_hold
    ADD COLUMN segment_id UUID;

ALTER TABLE historical_order_book_retention_hold
    ADD CONSTRAINT fk_historical_order_book_hold_segment
        FOREIGN KEY (segment_id) REFERENCES historical_order_book_evidence_segment (segment_id);

ALTER TABLE historical_order_book_retention_hold
    DROP CONSTRAINT ck_historical_order_book_hold_target;

ALTER TABLE historical_order_book_retention_hold
    ADD CONSTRAINT ck_historical_order_book_hold_target
        CHECK (artifact_id IS NOT NULL OR sequence_id IS NOT NULL OR segment_id IS NOT NULL);

    CREATE INDEX ix_historical_order_book_hold_segment
    ON historical_order_book_retention_hold (segment_id, released_at);

ALTER TABLE historical_order_book_sequence
    ADD COLUMN backfill_completed_at TIMESTAMP(6) WITH TIME ZONE;
