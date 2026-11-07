CREATE TABLE agent_grant (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agent_identity(id),
    permission VARCHAR(32) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(128),
    granted_by_user_id UUID NOT NULL REFERENCES users(user_id),
    active_marker INTEGER,
    grant_version BIGINT NOT NULL,
    CONSTRAINT ck_agent_grant_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_agent_grant_permission CHECK (permission IN (
        'accounts:read', 'accounts:sync', 'positions:read', 'markets:read',
        'risk:read', 'research:run', 'plans:create')),
    CONSTRAINT ck_agent_grant_resource_type CHECK (
        resource_type IN ('BROKER_ACCOUNT', 'MARKET', 'RESEARCH_SCOPE')),
    CONSTRAINT ck_agent_grant_active_marker CHECK (active_marker IS NULL OR active_marker = 1),
    CONSTRAINT ck_agent_grant_active_state CHECK (
        (status = 'ACTIVE' AND active_marker IS NOT NULL AND active_marker = 1)
        OR (status IN ('REVOKED', 'EXPIRED') AND active_marker IS NULL)
    ),
    CONSTRAINT ck_agent_grant_revoked_state CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL)
        OR (status IN ('ACTIVE', 'EXPIRED') AND revoked_at IS NULL)
    )
);

CREATE INDEX idx_agent_grant_agent_created ON agent_grant(agent_id, created_at DESC);
CREATE INDEX idx_agent_grant_resource ON agent_grant(resource_type, resource_id);
CREATE UNIQUE INDEX uk_agent_grant_active_scope
    ON agent_grant(agent_id, permission, resource_type, resource_id, active_marker);

CREATE TABLE agent_authorization_audit_event (
    id UUID PRIMARY KEY,
    agent_id UUID,
    owner_user_id UUID REFERENCES users(user_id),
    grant_id UUID REFERENCES agent_grant(id),
    permission VARCHAR(32),
    resource_type VARCHAR(32),
    resource_id UUID,
    operation VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64),
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agent_authorization_audit_agent_time
    ON agent_authorization_audit_event(agent_id, occurred_at DESC);
CREATE INDEX idx_agent_authorization_audit_owner_time
    ON agent_authorization_audit_event(owner_user_id, occurred_at DESC);
