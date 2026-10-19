CREATE TABLE kraken_stream_termination (
    termination_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    product VARCHAR(16) NOT NULL,
    classification VARCHAR(40) NOT NULL,
    signal_type VARCHAR(32) NOT NULL,
    close_code INTEGER,
    close_reason VARCHAR(256),
    exception_class VARCHAR(255),
    exception_message VARCHAR(256),
    root_cause_class VARCHAR(255),
    root_cause_message VARCHAR(256),
    connected_at TIMESTAMP(6) WITH TIME ZONE,
    terminated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    intentional_reason VARCHAR(64),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_kraken_stream_termination PRIMARY KEY (termination_id),
    CONSTRAINT uk_kraken_stream_termination_connection UNIQUE (connection_id),
    CONSTRAINT ck_kraken_stream_termination_classification CHECK (
        classification IN (
            'REMOTE_CLOSE', 'NETWORK_FAILURE', 'LOCAL_DISPOSE',
            'APPLICATION_SHUTDOWN', 'EXPLICIT_UNSUBSCRIBE',
            'UNKNOWN_TRANSPORT_COMPLETION'
        )
    )
);

CREATE TABLE kraken_stream_termination_sequence (
    termination_id UUID NOT NULL,
    sequence_id UUID NOT NULL,
    market_id UUID NOT NULL,
    depth INTEGER NOT NULL,
    provider_sequence BIGINT,
    local_ordinal BIGINT NOT NULL,
    last_occurred_at TIMESTAMP(6) WITH TIME ZONE,
    last_known_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_kraken_stream_termination_sequence PRIMARY KEY (termination_id, sequence_id),
    CONSTRAINT fk_kraken_termination_sequence_termination FOREIGN KEY (termination_id)
        REFERENCES kraken_stream_termination (termination_id),
    CONSTRAINT fk_kraken_termination_sequence_sequence FOREIGN KEY (sequence_id)
        REFERENCES historical_order_book_sequence (sequence_id),
    CONSTRAINT fk_kraken_termination_sequence_market FOREIGN KEY (market_id)
        REFERENCES markets (market_id),
    CONSTRAINT ck_kraken_termination_sequence_depth CHECK (depth > 0),
    CONSTRAINT ck_kraken_termination_sequence_ordinal CHECK (local_ordinal >= 0)
);

CREATE INDEX ix_kraken_stream_termination_product_time
    ON kraken_stream_termination (product, terminated_at);

CREATE INDEX ix_kraken_stream_termination_sequence_sequence
    ON kraken_stream_termination_sequence (sequence_id);
