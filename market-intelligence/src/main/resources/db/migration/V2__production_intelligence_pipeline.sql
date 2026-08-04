CREATE TABLE intelligence_pipeline_runs (
    run_id UUID PRIMARY KEY,
    analysis_execution_id UUID NOT NULL,
    pipeline_version VARCHAR(80) NOT NULL,
    state VARCHAR(40) NOT NULL,
    observation_id UUID,
    observation_version BIGINT,
    opportunity_id UUID,
    opportunity_version BIGINT,
    failure_code VARCHAR(80),
    failure_message VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_pipeline_analysis_version UNIQUE (analysis_execution_id, pipeline_version)
);

CREATE INDEX idx_pipeline_opportunity ON intelligence_pipeline_runs(opportunity_id, opportunity_version);

CREATE TABLE analysis_executions (
    execution_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    market_id UUID NOT NULL,
    mode VARCHAR(20) NOT NULL,
    payload TEXT NOT NULL
);

CREATE TABLE capability_executions (
    execution_id UUID PRIMARY KEY,
    analysis_execution_id UUID NOT NULL,
    execution_group_id UUID NOT NULL,
    capability_id VARCHAR(120) NOT NULL,
    capability_version VARCHAR(40) NOT NULL,
    state VARCHAR(40) NOT NULL,
    attempt_number INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    payload TEXT NOT NULL
);
CREATE INDEX idx_capability_execution_analysis ON capability_executions(analysis_execution_id);

CREATE TABLE capability_artifacts (
    artifact_row_id UUID PRIMARY KEY,
    analysis_execution_id UUID NOT NULL,
    artifact_type VARCHAR(120) NOT NULL,
    artifact_version VARCHAR(40) NOT NULL,
    parameters_fingerprint VARCHAR(64) NOT NULL,
    input_fingerprint VARCHAR(64) NOT NULL,
    producing_execution_id UUID,
    produced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    CONSTRAINT uq_capability_artifact UNIQUE
        (analysis_execution_id, artifact_type, artifact_version, parameters_fingerprint, input_fingerprint)
);
CREATE INDEX idx_capability_artifact_analysis ON capability_artifacts(analysis_execution_id);

CREATE TABLE observations (
    observation_id UUID PRIMARY KEY,
    lineage_id UUID NOT NULL,
    version BIGINT NOT NULL,
    instrument VARCHAR(120) NOT NULL,
    observation_type VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    consolidation_rule_version VARCHAR(80) NOT NULL,
    idempotency_fingerprint VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    CONSTRAINT uq_observation_lineage_version UNIQUE (lineage_id, version)
);

CREATE TABLE observation_evidence (
    evidence_id UUID PRIMARY KEY,
    observation_id UUID NOT NULL REFERENCES observations(observation_id),
    capability_execution_id UUID NOT NULL,
    artifact_input_fingerprint VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL
);

CREATE TABLE trading_opportunity_versions (
    opportunity_id UUID NOT NULL,
    version BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    instrument VARCHAR(120) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    scenario VARCHAR(120) NOT NULL,
    timeframe VARCHAR(40) NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (opportunity_id, version)
);
CREATE INDEX idx_opportunity_equivalence ON trading_opportunity_versions
    (instrument, direction, scenario, timeframe, evaluated_at);

CREATE TABLE trade_planning_contexts (
    context_id UUID NOT NULL,
    version BIGINT NOT NULL,
    owner_id UUID NOT NULL,
    trading_account_id UUID NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (context_id, version)
);

CREATE TABLE analysis_trade_plan_generations (
    generation_id UUID PRIMARY KEY,
    analysis_execution_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    account_id UUID NOT NULL,
    context_id UUID NOT NULL,
    context_version BIGINT NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    state VARCHAR(40) NOT NULL,
    price_snapshot_id VARCHAR(200),
    price_snapshot_version BIGINT,
    price_captured_at TIMESTAMP WITH TIME ZONE,
    price_occurred_at TIMESTAMP WITH TIME ZONE,
    price_symbol VARCHAR(120),
    selected_price NUMERIC(38, 18),
    selected_side VARCHAR(10),
    bid NUMERIC(38, 18),
    ask NUMERIC(38, 18),
    last_price NUMERIC(38, 18),
    opportunity_id UUID,
    opportunity_version BIGINT,
    trade_plan_id UUID,
    trade_plan_version BIGINT,
    failure_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_generation_scope UNIQUE
        (analysis_execution_id, actor_id, account_id, idempotency_key)
);
