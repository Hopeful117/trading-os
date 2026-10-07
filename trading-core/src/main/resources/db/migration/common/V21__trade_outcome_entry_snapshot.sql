CREATE TABLE execution_intent_provenance (
    execution_intent_id UUID NOT NULL REFERENCES execution_intent(id),
    opportunity_id UUID NOT NULL,
    opportunity_version BIGINT NOT NULL,
    strategy_match_id UUID,
    strategy_id UUID,
    strategy_version INTEGER,
    PRIMARY KEY (execution_intent_id, opportunity_id, opportunity_version)
);

ALTER TABLE execution_intent ADD COLUMN account_id UUID;
ALTER TABLE execution_intent ADD COLUMN stop_loss_price NUMERIC(30,12);
ALTER TABLE execution_intent ADD COLUMN take_profit_prices TEXT;
ALTER TABLE execution_intent ADD COLUMN expected_monetary_risk NUMERIC(30,12);
ALTER TABLE execution_intent ADD COLUMN risk_reward_ratio NUMERIC(30,12);

UPDATE execution_intent
SET account_id = (
    SELECT account_id
    FROM accounts
    WHERE accounts.broker_account_id = execution_intent.broker_account_id
)
WHERE account_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM accounts
    WHERE accounts.broker_account_id = execution_intent.broker_account_id
  );

CREATE TABLE trade_outcome (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    broker_account_id UUID NOT NULL,
    execution_intent_id UUID NOT NULL UNIQUE REFERENCES execution_intent(id),
    trade_plan_id UUID NOT NULL,
    trade_plan_version BIGINT NOT NULL,
    instrument VARCHAR(255) NOT NULL,
    side VARCHAR(32) NOT NULL,
    quantity NUMERIC(30,12) NOT NULL,
    entry_price NUMERIC(30,12),
    stop_loss_price NUMERIC(30,12),
    take_profit_prices TEXT,
    expected_monetary_risk NUMERIC(30,12),
    risk_reward_ratio NUMERIC(30,12),
    realized_pnl NUMERIC(30,12),
    actual_fee NUMERIC(30,12),
    closed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE trade_outcome_provenance (
    trade_outcome_id UUID NOT NULL REFERENCES trade_outcome(id),
    opportunity_id UUID NOT NULL,
    opportunity_version BIGINT NOT NULL,
    strategy_match_id UUID,
    strategy_id UUID,
    strategy_version INTEGER,
    PRIMARY KEY (trade_outcome_id, opportunity_id, opportunity_version)
);

CREATE INDEX idx_trade_outcome_account_created
    ON trade_outcome(account_id, created_at DESC);

CREATE INDEX idx_trade_outcome_trade_plan
    ON trade_outcome(trade_plan_id, trade_plan_version);

CREATE INDEX idx_trade_outcome_instrument
    ON trade_outcome(instrument, created_at DESC);

CREATE INDEX idx_trade_outcome_provenance_strategy
    ON trade_outcome_provenance(strategy_id, trade_outcome_id);
