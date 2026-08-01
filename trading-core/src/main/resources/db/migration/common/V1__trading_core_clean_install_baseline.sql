CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(255) NOT NULL
);

CREATE TABLE rules (
    rules_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    max_risk_per_trade NUMERIC(38,2) NOT NULL,
    max_daily_loss NUMERIC(38,2) NOT NULL,
    max_total_drawdown NUMERIC(38,2) NOT NULL,
    max_trades_per_day INTEGER,
    cooldown_minutes_between_trades INTEGER,
    max_leverage NUMERIC(38,2),
    allowed_sessions VARCHAR(255)
);

CREATE TABLE accounts (
    account_id UUID PRIMARY KEY,
    broker VARCHAR(255) UNIQUE,
    name VARCHAR(255) NOT NULL,
    base_currency VARCHAR(255) NOT NULL,
    peak_equity NUMERIC(38,2) NOT NULL,
    equity NUMERIC(38,2) NOT NULL,
    rules_id UUID REFERENCES rules(rules_id),
    user_id UUID REFERENCES users(user_id)
);

CREATE TABLE "account-balance" (
    id UUID PRIMARY KEY,
    asset VARCHAR(255) NOT NULL,
    amount NUMERIC(38,2) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(account_id)
);

CREATE TABLE trades (
    trade_id UUID PRIMARY KEY,
    symbol VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    entry_price NUMERIC(38,2) NOT NULL,
    current_price NUMERIC(38,2),
    exit_price NUMERIC(38,2),
    quantity NUMERIC(38,2) NOT NULL,
    pnl NUMERIC(38,2),
    opened_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    stop_loss NUMERIC(38,2),
    take_profit NUMERIC(38,2),
    risk_amount NUMERIC(38,2),
    reward_amount NUMERIC(38,2),
    risk_reward_ratio NUMERIC(38,2),
    account_id UUID REFERENCES accounts(account_id),
    trade_status VARCHAR(255) NOT NULL
);

CREATE TABLE broker_account (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    provider VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    external_account_id VARCHAR(255),
    connection_status VARCHAR(255) NOT NULL,
    credential_reference UUID,
    last_validated_at TIMESTAMP WITH TIME ZONE,
    last_synchronized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE execution_intent (
    id UUID PRIMARY KEY, trade_plan_id UUID NOT NULL, trade_plan_version BIGINT NOT NULL,
    risk_evaluation_id UUID NOT NULL, risk_decision VARCHAR(255) NOT NULL,
    risk_approved_at TIMESTAMP WITH TIME ZONE NOT NULL, idempotency_key VARCHAR(160) NOT NULL,
    initiator_id UUID NOT NULL, broker_account_id UUID NOT NULL, instrument VARCHAR(255) NOT NULL,
    side VARCHAR(255) NOT NULL, order_type VARCHAR(255) NOT NULL, quantity NUMERIC(30,12) NOT NULL,
    limit_price NUMERIC(30,12), status VARCHAR(255) NOT NULL, active_attempt_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL,
    CONSTRAINT uk_execution_intent_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_execution_intent_trade_plan UNIQUE (trade_plan_id, trade_plan_version),
    CONSTRAINT uk_execution_intent_active_attempt UNIQUE (active_attempt_id)
);

CREATE TABLE execution_attempt (
    id UUID PRIMARY KEY, intent_id UUID NOT NULL, attempt_number INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL, broker_correlation_id VARCHAR(255), result_code VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL,
    CONSTRAINT uk_execution_attempt_number UNIQUE (intent_id, attempt_number)
);

CREATE TABLE execution_broker_order (
    id UUID PRIMARY KEY, intent_id UUID NOT NULL, attempt_id UUID NOT NULL,
    external_order_id VARCHAR(255) NOT NULL, status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_execution_broker_order_intent UNIQUE (intent_id),
    CONSTRAINT uk_execution_broker_external_order UNIQUE (external_order_id)
);

CREATE TABLE execution_event (
    id UUID PRIMARY KEY, intent_id UUID NOT NULL, event_type VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, payload VARCHAR(2000) NOT NULL
);
CREATE INDEX idx_execution_event_intent ON execution_event(intent_id, occurred_at);
