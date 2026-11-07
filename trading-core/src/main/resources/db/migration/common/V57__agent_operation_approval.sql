CREATE TABLE agent_operation_request (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agent_identity(id),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    operation_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(128),
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by_user_id UUID REFERENCES users(user_id),
    executed_at TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(64),
    completed_at TIMESTAMP WITH TIME ZONE,
    grant_id UUID NOT NULL REFERENCES agent_grant(id),
    grant_version BIGINT NOT NULL,
    agent_version BIGINT NOT NULL,
    failure_classification VARCHAR(32),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_agent_operation_type CHECK (operation_type = 'ACCOUNT_SYNCHRONIZATION'),
    CONSTRAINT ck_agent_operation_resource CHECK (resource_type = 'BROKER_ACCOUNT'),
    CONSTRAINT ck_agent_operation_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_agent_operation_decision_fields CHECK (
        (status IN ('APPROVED', 'REJECTED', 'EXECUTING', 'SUCCEEDED', 'FAILED', 'OUTCOME_UNKNOWN')
            AND decided_at IS NOT NULL AND decided_by_user_id IS NOT NULL)
        OR status = 'EXPIRED'
        OR (status = 'PENDING_APPROVAL' AND decided_at IS NULL AND decided_by_user_id IS NULL)
    ),
    CONSTRAINT ck_agent_operation_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTING', 'SUCCEEDED', 'FAILED', 'OUTCOME_UNKNOWN', 'REJECTED', 'EXPIRED'))
);
CREATE INDEX idx_agent_operation_owner_created ON agent_operation_request(owner_user_id, created_at DESC);
CREATE INDEX idx_agent_operation_agent_created ON agent_operation_request(agent_id, created_at DESC);
CREATE UNIQUE INDEX uk_agent_operation_active_request
    ON agent_operation_request(owner_user_id, operation_type, resource_type, resource_id)
    WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTING');
ALTER TABLE agent_operation_request
    ADD CONSTRAINT ck_agent_operation_lifecycle_fields CHECK (
        (status = 'EXECUTING' AND executed_at IS NOT NULL)
        OR status <> 'EXECUTING'
    );
CREATE TABLE agent_operation_audit_event (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES agent_operation_request(id),
    agent_id UUID NOT NULL REFERENCES agent_identity(id),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    operation_type VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    event VARCHAR(64) NOT NULL,
    outcome VARCHAR(64),
    actor_type VARCHAR(32),
    actor_id UUID,
    broker_invocation_id UUID,
    failure_classification VARCHAR(32),
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_agent_operation_audit_request_time ON agent_operation_audit_event(request_id, occurred_at DESC);
CREATE INDEX idx_agent_operation_executing_recovery ON agent_operation_request(status, executed_at)
    WHERE status = 'EXECUTING';
