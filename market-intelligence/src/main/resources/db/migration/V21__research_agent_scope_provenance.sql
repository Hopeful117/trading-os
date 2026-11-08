ALTER TABLE research_executions ADD COLUMN research_scope_id UUID;
CREATE INDEX idx_research_agent_scope ON research_executions (actor_id, research_scope_id, updated_at DESC);
