ALTER TABLE execution_intent ALTER COLUMN trade_plan_id DROP NOT NULL;
ALTER TABLE execution_intent ALTER COLUMN trade_plan_version DROP NOT NULL;
ALTER TABLE execution_intent ALTER COLUMN risk_evaluation_id DROP NOT NULL;
ALTER TABLE execution_intent ALTER COLUMN risk_decision DROP NOT NULL;
ALTER TABLE execution_intent ALTER COLUMN risk_approved_at DROP NOT NULL;
ALTER TABLE execution_intent ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'ENTRY';
ALTER TABLE execution_intent ADD COLUMN target_trade_id UUID;

ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE trades ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_execution_intent_exit_target ON execution_intent (target_trade_id);
