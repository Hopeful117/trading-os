CREATE TABLE historical_order_book_source_retirement_operation (
    operation_id UUID NOT NULL,
    operation_fingerprint VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_symbol VARCHAR(255) NOT NULL,
    market_id UUID NOT NULL,
    depth INTEGER NOT NULL,
    approval_reference VARCHAR(255) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    business_state VARCHAR(32) NOT NULL,
    operational_state VARCHAR(32) NOT NULL,
    preflight_fingerprint VARCHAR(64) NOT NULL,
    inventory_fingerprint VARCHAR(64) NOT NULL,
    dependency_fingerprint VARCHAR(64),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_order_book_source_retirement_operation PRIMARY KEY (operation_id),
    CONSTRAINT uk_order_book_source_retirement_fingerprint UNIQUE (operation_fingerprint),
    CONSTRAINT ck_order_book_source_retirement_depth CHECK (depth > 0),
    CONSTRAINT ck_order_book_source_retirement_business_state CHECK
        (business_state IN ('PROPOSED', 'PREPARED', 'SOURCE_RETIRED', 'REJECTED')),
    CONSTRAINT ck_order_book_source_retirement_operational_state CHECK
        (operational_state IN ('NOT_STARTED', 'PREFLIGHT_PASSED', 'BACKUP_VALIDATED',
                               'QUIESCENCE_CONFIRMED', 'PHYSICAL_IN_PROGRESS',
                               'PHYSICAL_RETIRED', 'FAILED', 'AMBIGUOUS',
                               'RECONCILIATION_REQUIRED'))
);

CREATE INDEX ix_order_book_source_retirement_scope
    ON historical_order_book_source_retirement_operation (provider, provider_symbol, market_id, depth);

CREATE TABLE historical_order_book_source_retirement_audit (
    transition_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    from_business_state VARCHAR(32),
    to_business_state VARCHAR(32),
    from_operational_state VARCHAR(32),
    to_operational_state VARCHAR(32) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    transition_fingerprint VARCHAR(64) NOT NULL,
    external_reference VARCHAR(255),
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_order_book_source_retirement_audit PRIMARY KEY (transition_id),
    CONSTRAINT uk_order_book_source_retirement_transition UNIQUE (transition_fingerprint),
    CONSTRAINT fk_order_book_source_retirement_audit_operation FOREIGN KEY (operation_id)
        REFERENCES historical_order_book_source_retirement_operation (operation_id)
);

CREATE INDEX ix_order_book_source_retirement_audit_operation
    ON historical_order_book_source_retirement_audit (operation_id, occurred_at);

ALTER TABLE historical_order_book_evidence_dependency
    DROP CONSTRAINT ck_historical_order_book_dependency_state;

ALTER TABLE historical_order_book_evidence_dependency
    ADD CONSTRAINT ck_historical_order_book_dependency_state
        CHECK (state IN ('PROTECTED', 'PROMOTED', 'RELEASED', 'HOLD', 'SOURCE_RETIRED'));

ALTER TABLE historical_order_book_evidence_dependency
    ADD COLUMN source_retirement_operation_id UUID;

ALTER TABLE historical_order_book_evidence_dependency
    ADD CONSTRAINT fk_order_book_dependency_source_retirement
        FOREIGN KEY (source_retirement_operation_id)
        REFERENCES historical_order_book_source_retirement_operation (operation_id);
