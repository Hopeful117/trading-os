ALTER TABLE analysis_executions
    ADD COLUMN evaluation_cutoff TIMESTAMP WITH TIME ZONE;

ALTER TABLE analysis_executions
    ADD COLUMN evidence_policy VARCHAR(40);

CREATE INDEX idx_analysis_execution_research_scope
    ON analysis_executions (market_id, evaluation_cutoff, evidence_policy);
