CREATE TABLE trade_plan_approval_challenge (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    trading_account_id UUID NOT NULL,
    trade_plan_id UUID NOT NULL,
    trade_plan_version BIGINT NOT NULL,
    risk_evaluation_id UUID NOT NULL,
    parameter_digest VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by_user_id UUID,
    reason VARCHAR(256),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trade_plan_approval_agent ON trade_plan_approval_challenge(agent_id);
CREATE INDEX idx_trade_plan_approval_owner ON trade_plan_approval_challenge(owner_user_id);
CREATE UNIQUE INDEX uq_trade_plan_approval_agent_key ON trade_plan_approval_challenge(agent_id, idempotency_key);
