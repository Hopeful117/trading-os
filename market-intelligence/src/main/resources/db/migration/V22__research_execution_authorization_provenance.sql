ALTER TABLE research_executions ADD COLUMN owner_user_id UUID;
ALTER TABLE research_executions ADD COLUMN initiating_agent_id UUID;
ALTER TABLE research_executions ADD COLUMN credential_id UUID;
ALTER TABLE research_executions ADD COLUMN grant_id UUID;
ALTER TABLE research_executions ADD COLUMN grant_version BIGINT;
ALTER TABLE research_executions ADD COLUMN credential_version INTEGER;

CREATE INDEX idx_research_owner ON research_executions (owner_user_id, updated_at DESC);
CREATE INDEX idx_research_agent ON research_executions (initiating_agent_id, updated_at DESC);
