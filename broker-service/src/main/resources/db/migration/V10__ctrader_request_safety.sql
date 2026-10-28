CREATE TABLE ctrader_request_safety (
    scope VARCHAR(128) PRIMARY KEY,
    request_day DATE NOT NULL,
    request_count BIGINT NOT NULL,
    alert_logged BOOLEAN NOT NULL,
    lease_until TIMESTAMPTZ NULL,
    lease_token VARCHAR(64) NULL,
    last_snapshot_at TIMESTAMPTZ NULL
);

CREATE TABLE ctrader_request_safety_event (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    request_day DATE NOT NULL,
    external_account_id VARCHAR(64) NULL,
    payload_type INTEGER NOT NULL
);

CREATE INDEX idx_ctrader_request_safety_event_day
    ON ctrader_request_safety_event (request_day, external_account_id, payload_type);
