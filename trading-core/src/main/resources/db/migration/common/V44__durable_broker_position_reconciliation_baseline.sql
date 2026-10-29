ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS broker_baseline_close_deal_references VARCHAR(4000);
