ALTER TABLE historical_fee_schedule
    ADD COLUMN rate_unit VARCHAR(32) NOT NULL DEFAULT 'DECIMAL_FRACTION';

ALTER TABLE historical_fee_schedule
    ADD CONSTRAINT ck_historical_fee_schedule_rate_unit
        CHECK (rate_unit = 'DECIMAL_FRACTION');
