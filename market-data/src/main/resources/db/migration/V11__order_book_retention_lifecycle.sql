CREATE TABLE historical_order_book_replay_artifact (
    artifact_id UUID NOT NULL,
    semantic_identity VARCHAR(255) NOT NULL,
    artifact_fingerprint VARCHAR(64) NOT NULL,
    market_id UUID NOT NULL,
    depth INTEGER NOT NULL,
    sequence_id UUID NOT NULL,
    evaluation_cutoff TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    latest_occurred_at TIMESTAMP(6) WITH TIME ZONE,
    latest_known_at TIMESTAMP(6) WITH TIME ZONE,
    source_start_ordinal BIGINT,
    source_end_ordinal BIGINT,
    provider VARCHAR(32),
    provider_symbol VARCHAR(255),
    normalization_version VARCHAR(64) NOT NULL,
    source_evidence_fingerprint VARCHAR(64) NOT NULL,
    bids_payload TEXT NOT NULL,
    asks_payload TEXT NOT NULL,
    promotion_reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_historical_order_book_replay_artifact PRIMARY KEY (artifact_id),
    CONSTRAINT uk_historical_order_book_artifact_identity UNIQUE (semantic_identity),
    CONSTRAINT uk_historical_order_book_artifact_fingerprint UNIQUE (artifact_fingerprint),
    CONSTRAINT fk_historical_order_book_artifact_sequence FOREIGN KEY (sequence_id)
        REFERENCES historical_order_book_sequence (sequence_id),
    CONSTRAINT ck_historical_order_book_artifact_depth CHECK (depth > 0)
);

CREATE INDEX ix_historical_order_book_artifact_sequence
    ON historical_order_book_replay_artifact (sequence_id, evaluation_cutoff);

CREATE TABLE historical_order_book_evidence_dependency (
    dependency_id UUID NOT NULL,
    dependency_key VARCHAR(255) NOT NULL,
    consumer_type VARCHAR(64) NOT NULL,
    consumer_identity VARCHAR(255) NOT NULL,
    artifact_id UUID,
    sequence_id UUID,
    state VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_historical_order_book_dependency PRIMARY KEY (dependency_id),
    CONSTRAINT uk_historical_order_book_dependency_key UNIQUE (dependency_key),
    CONSTRAINT fk_historical_order_book_dependency_artifact FOREIGN KEY (artifact_id)
        REFERENCES historical_order_book_replay_artifact (artifact_id),
    CONSTRAINT fk_historical_order_book_dependency_sequence FOREIGN KEY (sequence_id)
        REFERENCES historical_order_book_sequence (sequence_id),
    CONSTRAINT ck_historical_order_book_dependency_state
        CHECK (state IN ('PROTECTED', 'PROMOTED', 'RELEASED', 'HOLD')),
    CONSTRAINT ck_historical_order_book_dependency_target
        CHECK (artifact_id IS NOT NULL OR sequence_id IS NOT NULL)
);

CREATE INDEX ix_historical_order_book_dependency_sequence
    ON historical_order_book_evidence_dependency (sequence_id, state);

CREATE TABLE historical_order_book_retention_hold (
    hold_id UUID NOT NULL,
    hold_key VARCHAR(255) NOT NULL,
    artifact_id UUID,
    sequence_id UUID,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_historical_order_book_retention_hold PRIMARY KEY (hold_id),
    CONSTRAINT uk_historical_order_book_retention_hold_key UNIQUE (hold_key),
    CONSTRAINT fk_historical_order_book_hold_artifact FOREIGN KEY (artifact_id)
        REFERENCES historical_order_book_replay_artifact (artifact_id),
    CONSTRAINT fk_historical_order_book_hold_sequence FOREIGN KEY (sequence_id)
        REFERENCES historical_order_book_sequence (sequence_id),
    CONSTRAINT ck_historical_order_book_hold_target
        CHECK (artifact_id IS NOT NULL OR sequence_id IS NOT NULL)
);

CREATE INDEX ix_historical_order_book_hold_sequence
    ON historical_order_book_retention_hold (sequence_id, released_at);

CREATE TABLE historical_order_book_retirement_operation (
    operation_id UUID NOT NULL,
    operation_fingerprint VARCHAR(64) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    selected_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    details TEXT NOT NULL,
    CONSTRAINT pk_historical_order_book_retirement_operation PRIMARY KEY (operation_id),
    CONSTRAINT uk_historical_order_book_retirement_fingerprint UNIQUE (operation_fingerprint),
    CONSTRAINT ck_historical_order_book_retirement_mode CHECK (mode IN ('DRY_RUN', 'MUTATION')),
    CONSTRAINT ck_historical_order_book_retirement_status
        CHECK (status IN ('SELECTED', 'COMPLETED', 'FAILED'))
);
