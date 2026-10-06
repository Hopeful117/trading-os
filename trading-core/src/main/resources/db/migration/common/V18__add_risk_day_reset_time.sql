ALTER TABLE account_risk_configuration
    ADD COLUMN risk_day_reset_time VARCHAR(5) NOT NULL DEFAULT '00:00';

ALTER TABLE account_risk_configuration
    ADD CONSTRAINT chk_account_risk_reset_time
    CHECK (risk_day_reset_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$');
