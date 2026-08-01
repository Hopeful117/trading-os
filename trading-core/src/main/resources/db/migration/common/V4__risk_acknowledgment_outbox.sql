CREATE TABLE risk_acknowledgment_outbox (
    evaluation_id UUID PRIMARY KEY REFERENCES risk_evaluation(id),
    trade_plan_id UUID NOT NULL,
    trade_plan_version BIGINT NOT NULL,
    decision VARCHAR(32) NOT NULL CHECK (decision IN ('APPROVED','APPROVED_WITH_WARNINGS')),
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','PROCESSING','DELIVERED')),
    attempt_count INTEGER NOT NULL CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    claim_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_risk_ack_claim CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_token IS NULL AND lease_until IS NULL)
    )
);

CREATE INDEX idx_risk_acknowledgment_due
    ON risk_acknowledgment_outbox(status, next_attempt_at, lease_until);
CREATE INDEX idx_risk_acknowledgment_plan
    ON risk_acknowledgment_outbox(trade_plan_id, trade_plan_version, evaluation_id);
