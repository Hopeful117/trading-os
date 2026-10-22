ALTER TABLE trade_outcome
    ADD COLUMN trade_id UUID REFERENCES trades(trade_id);

CREATE INDEX idx_trade_outcome_trade_id
    ON trade_outcome(trade_id);
