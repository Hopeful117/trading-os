CREATE TABLE challenge_definition (
    id UUID NOT NULL,
    definition_version VARCHAR(64) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    product VARCHAR(80) NOT NULL,
    plan_code VARCHAR(80) NOT NULL,
    capital_currency VARCHAR(16) NOT NULL,
    starting_capital NUMERIC(30,12) NOT NULL CHECK (starting_capital > 0),
    profit_target_ratio NUMERIC(30,12) NOT NULL CHECK (profit_target_ratio > 0),
    progression_value_source VARCHAR(32) NOT NULL CHECK (progression_value_source = 'BALANCE'),
    risk_policy_id UUID NOT NULL,
    risk_policy_version VARCHAR(64) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id, definition_version),
    FOREIGN KEY (risk_policy_id, risk_policy_version)
        REFERENCES risk_profile(id, semantic_version)
);

CREATE TABLE challenge_instance (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    definition_id UUID NOT NULL,
    definition_version VARCHAR(64) NOT NULL,
    risk_policy_id UUID NOT NULL,
    risk_policy_version VARCHAR(64) NOT NULL,
    starting_capital NUMERIC(30,12) NOT NULL CHECK (starting_capital > 0),
    capital_currency VARCHAR(16) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','PASSED','BREACHED')),
    active_marker VARCHAR(16),
    passed_at TIMESTAMP WITH TIME ZONE,
    breached_at TIMESTAMP WITH TIME ZONE,
    terminal_reason VARCHAR(255),
    terminal_risk_evaluation_id UUID,
    version BIGINT NOT NULL,
    CONSTRAINT fk_challenge_definition
        FOREIGN KEY (definition_id, definition_version)
        REFERENCES challenge_definition(id, definition_version),
    CONSTRAINT fk_challenge_risk_policy
        FOREIGN KEY (risk_policy_id, risk_policy_version)
        REFERENCES risk_profile(id, semantic_version),
    CONSTRAINT ck_challenge_active_marker
        CHECK ((status = 'ACTIVE' AND active_marker = 'ACTIVE'
                AND passed_at IS NULL AND breached_at IS NULL
                AND terminal_reason IS NULL AND terminal_risk_evaluation_id IS NULL)
            OR (status = 'PASSED' AND active_marker IS NULL
                AND passed_at IS NOT NULL AND breached_at IS NULL
                AND terminal_reason IS NOT NULL AND TRIM(terminal_reason) <> '')
            OR (status = 'BREACHED' AND active_marker IS NULL
                AND passed_at IS NULL AND breached_at IS NOT NULL
                AND terminal_reason IS NOT NULL AND TRIM(terminal_reason) <> '')),
    CONSTRAINT uk_challenge_account_active_marker UNIQUE (account_id, active_marker)
);
