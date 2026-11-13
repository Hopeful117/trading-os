ALTER TABLE research_executions
    ADD COLUMN objective_type VARCHAR(64) NOT NULL DEFAULT 'COMPARATIVE_MARKET_RESEARCH';

ALTER TABLE research_executions
    ADD COLUMN timeframe VARCHAR(32);

ALTER TABLE research_executions
    ADD COLUMN horizon_seconds BIGINT;

ALTER TABLE research_executions
    ADD COLUMN cutoff_start TIMESTAMP WITH TIME ZONE;

ALTER TABLE research_executions
    ADD COLUMN cutoff_end TIMESTAMP WITH TIME ZONE;
