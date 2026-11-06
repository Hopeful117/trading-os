CREATE TABLE historical_reconstruction_lifecycles (
    execution_id UUID PRIMARY KEY REFERENCES analysis_executions (execution_id),
    lease_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    failure_code VARCHAR(100),
    failure_reason VARCHAR(500),
    snapshot_fingerprint VARCHAR(128),
    outcome_fingerprint VARCHAR(128),
    output_directory VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
