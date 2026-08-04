CREATE TABLE analysis_trade_plan_continuations (
    continuation_id UUID PRIMARY KEY,
    analysis_execution_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(user_id),
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    idempotency_key VARCHAR(200) NOT NULL,
    context_id UUID NOT NULL,
    context_version BIGINT NOT NULL,
    context_captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    profile_id UUID NOT NULL,
    profile_version BIGINT NOT NULL,
    state VARCHAR(30) NOT NULL,
    trade_plan_id UUID,
    trade_plan_version BIGINT,
    failure_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_analysis_trade_plan_continuation
        UNIQUE (analysis_execution_id, actor_id, account_id, idempotency_key),
    CONSTRAINT fk_continuation_profile FOREIGN KEY (profile_id, profile_version)
        REFERENCES trade_planning_profiles(profile_id, profile_version)
);
