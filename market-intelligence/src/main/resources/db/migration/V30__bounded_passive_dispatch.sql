CREATE INDEX idx_passive_scan_market_due
    ON passive_scan_markets(market_id, updated_at DESC, status);

CREATE TABLE passive_dispatch_capacity_state (
    capacity_key VARCHAR(40) PRIMARY KEY,
    active_count INTEGER NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_passive_dispatch_capacity_count CHECK (active_count >= 0)
);

INSERT INTO passive_dispatch_capacity_state(capacity_key, active_count, version, updated_at)
VALUES ('PASSIVE', 0, 0, CURRENT_TIMESTAMP);

CREATE TABLE passive_dispatch_capacity_leases (
    lease_id UUID PRIMARY KEY,
    scan_market_id UUID NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_passive_dispatch_capacity_market FOREIGN KEY (scan_market_id)
        REFERENCES passive_scan_markets(scan_market_id)
);

CREATE INDEX idx_passive_dispatch_capacity_expiry
    ON passive_dispatch_capacity_leases(expires_at);
