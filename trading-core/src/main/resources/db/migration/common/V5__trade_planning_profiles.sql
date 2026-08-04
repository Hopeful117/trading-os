CREATE TABLE trade_planning_profiles (
    profile_id UUID NOT NULL,
    profile_version BIGINT NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(user_id),
    risk_budget_amount NUMERIC(30,12) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    stop_strategy VARCHAR(64) NOT NULL,
    stop_distance_percent NUMERIC(18,8) NOT NULL,
    target_strategy VARCHAR(64) NOT NULL,
    target_risk_multiple NUMERIC(18,8) NOT NULL,
    planning_horizon VARCHAR(32) NOT NULL,
    validity_seconds BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_trade_planning_profiles PRIMARY KEY (profile_id, profile_version),
    CONSTRAINT ck_trade_planning_profile_version CHECK (profile_version > 0),
    CONSTRAINT ck_trade_planning_budget CHECK (risk_budget_amount > 0),
    CONSTRAINT ck_trade_planning_stop_distance CHECK (stop_distance_percent > 0),
    CONSTRAINT ck_trade_planning_target_multiple CHECK (target_risk_multiple > 0),
    CONSTRAINT ck_trade_planning_validity CHECK (validity_seconds > 0)
);

CREATE TABLE account_trade_planning_profile_assignments (
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    assignment_version BIGINT NOT NULL,
    profile_id UUID NOT NULL,
    profile_version BIGINT NOT NULL,
    assigned_by UUID NOT NULL REFERENCES users(user_id),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_account_trade_planning_profile_assignments
        PRIMARY KEY (account_id, assignment_version),
    CONSTRAINT ck_trade_planning_assignment_version CHECK (assignment_version > 0),
    CONSTRAINT fk_account_trade_planning_profile
        FOREIGN KEY (profile_id, profile_version)
        REFERENCES trade_planning_profiles(profile_id, profile_version)
);

CREATE INDEX idx_trade_planning_profiles_owner ON trade_planning_profiles(owner_id);
CREATE INDEX idx_trade_planning_assignment_effective
    ON account_trade_planning_profile_assignments(account_id, assignment_version DESC);
