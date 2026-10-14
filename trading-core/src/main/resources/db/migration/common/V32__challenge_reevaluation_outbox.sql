CREATE TABLE challenge_reevaluation_outbox (
    execution_intent_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','PROCESSING','DELIVERED','FAILED')),
    attempt_count INTEGER NOT NULL CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    claim_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_challenge_reevaluation_claim CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_token IS NULL AND lease_until IS NULL)
    )
);

CREATE INDEX idx_challenge_reevaluation_due
    ON challenge_reevaluation_outbox(status, next_attempt_at, lease_until);

CREATE INDEX idx_challenge_reevaluation_account
    ON challenge_reevaluation_outbox(account_id, execution_intent_id);
