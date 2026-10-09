ALTER TABLE risk_evaluation ALTER COLUMN trade_plan_id DROP NOT NULL;
ALTER TABLE risk_evaluation ALTER COLUMN trade_plan_version DROP NOT NULL;
ALTER TABLE risk_evaluation ADD COLUMN evaluation_mode VARCHAR(32) NOT NULL DEFAULT 'PRE_TRADE';
