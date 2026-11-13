CREATE TABLE passive_scan_runs (
    run_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    trigger_context VARCHAR(80) NOT NULL,
    planned_window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    planned_window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    configuration_version VARCHAR(80) NOT NULL,
    fingerprint_algorithm VARCHAR(80) NOT NULL,
    universe_fingerprint VARCHAR(64) NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(40) NOT NULL,
    diagnostics TEXT NOT NULL,
    terminal_summary VARCHAR(500) NOT NULL,
    dispatch_requested_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_passive_scan_run_status_updated
    ON passive_scan_runs(status, updated_at);

CREATE TABLE passive_scan_markets (
    scan_market_id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    market_id UUID NOT NULL,
    provider VARCHAR(80) NOT NULL,
    product_type VARCHAR(80) NOT NULL,
    cadence_seconds BIGINT NOT NULL,
    minimum_evidence_profile VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    analysis_execution_id UUID NOT NULL,
    attempt_count INTEGER NOT NULL,
    failure_code VARCHAR(80),
    failure_reason VARCHAR(500),
    next_retry_at TIMESTAMP WITH TIME ZONE,
    lease_id UUID,
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    dispatch_requested_at TIMESTAMP WITH TIME ZONE,
    accepted_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_passive_scan_market_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_passive_scan_market_cadence CHECK (cadence_seconds > 0),
    CONSTRAINT ck_passive_scan_market_attempt_count CHECK (attempt_count > 0),
    CONSTRAINT uq_passive_scan_market UNIQUE (run_id, market_id),
    CONSTRAINT fk_passive_scan_market_run FOREIGN KEY (run_id)
        REFERENCES passive_scan_runs(run_id),
    CONSTRAINT fk_passive_scan_market_execution FOREIGN KEY (analysis_execution_id)
        REFERENCES analysis_executions(execution_id),
    CONSTRAINT uq_passive_scan_market_execution UNIQUE (analysis_execution_id)
);

CREATE INDEX idx_passive_scan_market_run_ordinal
    ON passive_scan_markets(run_id, ordinal);

CREATE INDEX idx_passive_scan_market_claim
    ON passive_scan_markets(status, lease_expires_at, next_retry_at);

CREATE TABLE passive_scan_market_attempts (
    attempt_id UUID PRIMARY KEY,
    scan_market_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    analysis_execution_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_passive_scan_attempt_number UNIQUE (scan_market_id, attempt_number),
    CONSTRAINT uq_passive_scan_attempt_execution UNIQUE (analysis_execution_id),
    CONSTRAINT uq_passive_scan_attempt_key UNIQUE (scan_market_id, idempotency_key),
    CONSTRAINT fk_passive_scan_attempt_market FOREIGN KEY (scan_market_id)
        REFERENCES passive_scan_markets(scan_market_id),
    CONSTRAINT fk_passive_scan_attempt_execution FOREIGN KEY (analysis_execution_id)
        REFERENCES analysis_executions(execution_id)
);

CREATE INDEX idx_passive_scan_attempt_market_number
    ON passive_scan_market_attempts(scan_market_id, attempt_number);
