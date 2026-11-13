CREATE TABLE research_hypotheses (
    hypothesis_id UUID NOT NULL,
    version INTEGER NOT NULL,
    research_id UUID NOT NULL,
    market_id UUID NOT NULL,
    context_fingerprint VARCHAR(128) NOT NULL,
    proposal TEXT NOT NULL,
    validation_status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_research_hypothesis PRIMARY KEY (hypothesis_id, version),
    CONSTRAINT uk_research_hypothesis_version UNIQUE (research_id, hypothesis_id, version),
    CONSTRAINT fk_research_hypothesis_execution FOREIGN KEY (research_id)
        REFERENCES research_executions (research_id)
);

CREATE INDEX idx_research_hypotheses_execution
    ON research_hypotheses (research_id, created_at DESC);
