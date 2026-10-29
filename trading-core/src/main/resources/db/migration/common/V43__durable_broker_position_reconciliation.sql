ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS broker_position_reference VARCHAR(255);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS broker_close_deal_reference VARCHAR(255);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS broker_close_deal_references VARCHAR(4000);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS broker_closed_quantity NUMERIC(38,12);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS broker_original_quantity NUMERIC(38,12);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS broker_closed_notional NUMERIC(38,12);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS realized_pnl NUMERIC(38,12);

ALTER TABLE trades
    ADD CONSTRAINT uk_trades_account_broker_position
        UNIQUE (account_id, broker_position_reference);
