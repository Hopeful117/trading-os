CREATE TABLE agent_authentication_audit_event (
    id UUID PRIMARY KEY,
    agent_id UUID REFERENCES agent_identity(id),
    credential_id UUID REFERENCES agent_credential(id),
    owner_user_id UUID REFERENCES users(user_id),
    credential_version INTEGER,
    operation VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64),
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agent_auth_audit_agent_time
    ON agent_authentication_audit_event(agent_id, occurred_at DESC);
CREATE INDEX idx_agent_auth_audit_credential_time
    ON agent_authentication_audit_event(credential_id, occurred_at DESC);
