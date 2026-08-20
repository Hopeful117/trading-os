CREATE TABLE active_scans (
    scan_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    account_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(40) NOT NULL,
    objective TEXT NOT NULL,
    payload TEXT NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_active_scan_actor_key UNIQUE (actor_id, idempotency_key)
);

CREATE INDEX idx_active_scan_actor_key ON active_scans(actor_id, idempotency_key);
CREATE INDEX idx_active_scan_actor_created_at ON active_scans(actor_id, created_at);

CREATE TABLE active_scan_markets (
    scan_market_id UUID PRIMARY KEY,
    scan_id UUID NOT NULL,
    ordinal INTEGER NOT NULL,
    market_id UUID NOT NULL,
    eligible BOOLEAN NOT NULL,
    status VARCHAR(40) NOT NULL,
    analysis_execution_id UUID,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_active_scan_market UNIQUE (scan_id, market_id),
    CONSTRAINT fk_active_scan_market_scan FOREIGN KEY (scan_id)
        REFERENCES active_scans (scan_id),
    CONSTRAINT fk_active_scan_market_execution FOREIGN KEY (analysis_execution_id)
        REFERENCES analysis_executions (execution_id),
    CONSTRAINT uq_active_scan_market_execution UNIQUE (analysis_execution_id)
);

CREATE INDEX idx_active_scan_market_scan_ordinal
    ON active_scan_markets(scan_id, ordinal);
