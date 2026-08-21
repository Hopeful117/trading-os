CREATE TABLE strategy_definitions (
    strategy_id UUID NOT NULL,
    version INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    lifecycle_status VARCHAR(20) NOT NULL,
    validation_status VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    asset_classes TEXT NOT NULL,
    timeframes TEXT NOT NULL,
    providers TEXT,
    required_inputs TEXT NOT NULL,
    parameters TEXT NOT NULL,
    research_ref VARCHAR(200),
    validation_evidence_ref VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_strategy_definitions PRIMARY KEY (strategy_id, version)
);

CREATE INDEX idx_strategy_definitions_lifecycle ON strategy_definitions(lifecycle_status);
CREATE INDEX idx_strategy_definitions_validation ON strategy_definitions(validation_status);
