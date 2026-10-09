CREATE TABLE manual_trade_plan_idempotency (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    trade_plan_id UUID NOT NULL,
    trade_plan_version BIGINT NOT NULL,
    CONSTRAINT uk_manual_trade_plan_idempotency_actor_key UNIQUE (actor_id, idempotency_key)
);
