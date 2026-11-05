CREATE TABLE research_executions (
    research_id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    market_ids TEXT NOT NULL,
    objective TEXT NOT NULL,
    depth VARCHAR(32) NOT NULL,
    status VARCHAR(40) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR(100),
    report TEXT,
    CONSTRAINT uk_research_actor_idempotency UNIQUE (actor_id, idempotency_key)
);

CREATE INDEX idx_research_actor_updated
    ON research_executions (actor_id, updated_at DESC);
