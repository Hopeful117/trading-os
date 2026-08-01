CREATE TABLE trade_plan_versions (
    trade_plan_id UUID NOT NULL,
    version BIGINT NOT NULL,
    previous_version BIGINT,
    status VARCHAR(32) NOT NULL,
    trading_context_id UUID NOT NULL,
    trading_context_version BIGINT NOT NULL,
    trading_context_snapshot_at TIMESTAMP WITH TIME ZONE NOT NULL,
    execution_payload TEXT NOT NULL,
    rationale_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_trade_plan_versions PRIMARY KEY (trade_plan_id, version),
    CONSTRAINT fk_trade_plan_previous_version FOREIGN KEY (trade_plan_id, previous_version)
        REFERENCES trade_plan_versions (trade_plan_id, version),
    CONSTRAINT ck_trade_plan_version_positive CHECK (version > 0),
    CONSTRAINT ck_trade_plan_previous_version CHECK (
        (version = 1 AND previous_version IS NULL)
        OR (version > 1 AND previous_version = version - 1)
    )
);

CREATE TABLE risk_validation_acknowledgments (
    acknowledgment_id UUID NOT NULL,
    trade_plan_id UUID NOT NULL,
    accepted_trade_plan_version BIGINT NOT NULL,
    risk_validated_trade_plan_version BIGINT NOT NULL,
    trading_context_id UUID NOT NULL,
    trading_context_version BIGINT NOT NULL,
    evaluation_id UUID NOT NULL,
    decision VARCHAR(32) NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_risk_validation_acknowledgments PRIMARY KEY (acknowledgment_id),
    CONSTRAINT uq_risk_ack_accepted_plan UNIQUE (trade_plan_id, accepted_trade_plan_version),
    CONSTRAINT uq_risk_ack_evaluation UNIQUE (evaluation_id),
    CONSTRAINT fk_risk_ack_accepted_plan FOREIGN KEY (trade_plan_id, accepted_trade_plan_version)
        REFERENCES trade_plan_versions (trade_plan_id, version),
    CONSTRAINT fk_risk_ack_validated_plan FOREIGN KEY (trade_plan_id, risk_validated_trade_plan_version)
        REFERENCES trade_plan_versions (trade_plan_id, version)
);
