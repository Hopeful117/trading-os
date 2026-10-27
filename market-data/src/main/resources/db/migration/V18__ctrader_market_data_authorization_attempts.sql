CREATE TABLE ctrader_market_data_authorization_attempt (
    id UUID NOT NULL,
    state_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'INITIATED',
    environment VARCHAR(16),
    external_account_id BIGINT,
    failure_code VARCHAR(64),
    row_version BIGINT NOT NULL,
    CONSTRAINT pk_ctrader_market_data_authorization_attempt PRIMARY KEY (id),
    CONSTRAINT uq_ctrader_market_data_authorization_attempt_state UNIQUE (state_hash)
);
