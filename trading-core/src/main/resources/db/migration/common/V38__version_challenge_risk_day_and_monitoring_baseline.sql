ALTER TABLE challenge_definition ADD COLUMN risk_time_zone VARCHAR(80);
ALTER TABLE challenge_definition ADD COLUMN risk_day_reset_time TIME;
ALTER TABLE challenge_definition ADD CONSTRAINT ck_challenge_definition_risk_day
    CHECK ((risk_time_zone IS NULL AND risk_day_reset_time IS NULL)
        OR (risk_time_zone IS NOT NULL AND risk_day_reset_time IS NOT NULL));

ALTER TABLE challenge_instance ADD COLUMN risk_time_zone VARCHAR(80);
ALTER TABLE challenge_instance ADD COLUMN risk_day_reset_time TIME;
ALTER TABLE challenge_instance ADD CONSTRAINT ck_challenge_instance_risk_day
    CHECK ((risk_time_zone IS NULL AND risk_day_reset_time IS NULL)
        OR (risk_time_zone IS NOT NULL AND risk_day_reset_time IS NOT NULL));

ALTER TABLE risk_evaluation ADD COLUMN daily_reference_balance NUMERIC(30,12);
ALTER TABLE risk_evaluation ADD COLUMN daily_loss_limit NUMERIC(30,12);
ALTER TABLE risk_evaluation ADD COLUMN daily_loss_used NUMERIC(30,12);
ALTER TABLE risk_evaluation ADD COLUMN daily_loss_remaining NUMERIC(30,12);
ALTER TABLE risk_evaluation ADD COLUMN total_drawdown_limit NUMERIC(30,12);
ALTER TABLE risk_evaluation ADD COLUMN total_drawdown_used NUMERIC(30,12);
ALTER TABLE risk_evaluation ADD COLUMN total_drawdown_remaining NUMERIC(30,12);

UPDATE challenge_definition
SET risk_time_zone = 'Europe/Paris', risk_day_reset_time = TIME '00:00:00'
WHERE id = '00890000-0000-4000-8000-000000000051'
  AND definition_version = '1.0.0';
