CREATE TABLE position_close_command (
    id                        UUID PRIMARY KEY,
    account_id                UUID NOT NULL,
    broker_account_id         UUID NOT NULL,
    broker_position_reference VARCHAR(255) NOT NULL,
    resolved_mutation_scope   VARCHAR(255) NOT NULL,
    idempotency_key           VARCHAR(160) NOT NULL,
    status                    VARCHAR(32) NOT NULL,
    reconciliation_result     VARCHAR(48),
    external_order_id         VARCHAR(255),
    failure_reason            VARCHAR(500),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE position_close_command
    ADD CONSTRAINT uk_position_close_idempotency UNIQUE (idempotency_key);

CREATE INDEX idx_position_close_account ON position_close_command (account_id);
CREATE INDEX idx_position_close_broker_account ON position_close_command (broker_account_id);