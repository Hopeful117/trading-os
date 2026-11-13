CREATE TABLE execution_authorization (
    id UUID PRIMARY KEY,
    human_user_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    grant_id UUID,
    grant_version BIGINT,
    broker_account_id UUID NOT NULL,
    trading_account_id UUID NOT NULL,
    provider_identity VARCHAR(120) NOT NULL,
    market_identity VARCHAR(160) NOT NULL,
    canonical_market_id UUID NOT NULL,
    trade_plan_id UUID NOT NULL,
    trade_plan_version BIGINT NOT NULL,
    risk_evaluation_id UUID NOT NULL,
    execution_idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(40) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by_human_user_id UUID,
    consumed_at TIMESTAMP WITH TIME ZONE,
    consumed_by_agent_id UUID,
    execution_intent_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_execution_authorization_agent_key UNIQUE (agent_id, execution_idempotency_key),
    CONSTRAINT uk_execution_authorization_intent UNIQUE (execution_intent_id)
);

CREATE TABLE execution_authorization_audit (
    id UUID PRIMARY KEY,
    authorization_id UUID NOT NULL,
    actor_user_id UUID,
    actor_agent_id UUID,
    event VARCHAR(40) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_execution_authorization_agent ON execution_authorization(agent_id);
CREATE INDEX idx_execution_authorization_human ON execution_authorization(human_user_id);
CREATE INDEX idx_execution_authorization_status ON execution_authorization(status);
CREATE INDEX idx_execution_authorization_audit_auth ON execution_authorization_audit(authorization_id);
