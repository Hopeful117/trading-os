CREATE TABLE risk_evaluation_t1 (
    id                        UUID PRIMARY KEY,
    execution_intent_id       UUID NOT NULL,
    t0_evaluation_id          UUID NOT NULL,
    account_id                UUID NOT NULL,
    evaluated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    status                    VARCHAR(32) NOT NULL,
    decision                  VARCHAR(32),
    unavailable_reason_code   VARCHAR(64),
    result_schema_version     INTEGER,
    result_payload            TEXT,
    response_schema_version   INTEGER NOT NULL,
    response_payload          TEXT NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_risk_evaluation_t1_intent ON risk_evaluation_t1 (execution_intent_id);
CREATE INDEX idx_risk_evaluation_t1_account ON risk_evaluation_t1 (account_id, evaluated_at);

ALTER TABLE execution_attempt
    ADD COLUMN t1_evaluation_id UUID;