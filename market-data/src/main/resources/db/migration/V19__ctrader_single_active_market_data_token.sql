ALTER TABLE ctrader_market_data_token
    ADD COLUMN active_marker VARCHAR(16);

UPDATE ctrader_market_data_token
SET active_marker = 'ACTIVE'
WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_ctrader_market_data_token_active
    ON ctrader_market_data_token (active_marker);
