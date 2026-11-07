CREATE TABLE agent_identity (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    display_name VARCHAR(100) NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_agent_identity_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);

CREATE INDEX idx_agent_identity_owner ON agent_identity(owner_user_id, created_at DESC);

CREATE TABLE agent_credential (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agent_identity(id),
    credential_digest VARCHAR(64) NOT NULL UNIQUE,
    credential_fingerprint VARCHAR(16) NOT NULL,
    credential_version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_marker INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    CONSTRAINT uk_agent_credential_version UNIQUE (agent_id, credential_version),
    CONSTRAINT ck_agent_credential_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_agent_credential_version CHECK (credential_version > 0),
    CONSTRAINT ck_agent_credential_active_marker CHECK (active_marker IS NULL OR active_marker = 1),
    CONSTRAINT ck_agent_credential_active_state CHECK (
        (status = 'ACTIVE' AND active_marker = 1)
        OR (status IN ('REVOKED', 'EXPIRED') AND active_marker IS NULL)
    )
);

CREATE INDEX idx_agent_credential_agent ON agent_credential(agent_id, created_at DESC);
CREATE UNIQUE INDEX uk_agent_credential_active ON agent_credential(agent_id, active_marker);

CREATE TABLE agent_audit_event (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agent_identity(id),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    credential_id UUID REFERENCES agent_credential(id),
    operation VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    reason_code VARCHAR(128),
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agent_audit_agent_time ON agent_audit_event(agent_id, occurred_at DESC);
CREATE INDEX idx_agent_audit_owner_time ON agent_audit_event(owner_user_id, occurred_at DESC);
