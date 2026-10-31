CREATE TABLE margin_policy (
    id UUID NOT NULL,
    version VARCHAR(64) NOT NULL,
    instrument VARCHAR(160) NOT NULL,
    margin_rate NUMERIC(38,18) NOT NULL,
    source_category VARCHAR(32) NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    configured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reporting_currency_scale INTEGER NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id, version),
    CONSTRAINT margin_policy_rate_positive CHECK (margin_rate > 0 AND margin_rate <= 1),
    CONSTRAINT margin_policy_scale_valid CHECK (reporting_currency_scale BETWEEN 0 AND 18)
);

CREATE TABLE margin_policy_assignment (
    id BIGSERIAL PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    policy_id UUID NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT margin_policy_assignment_fk FOREIGN KEY (policy_id, policy_version)
        REFERENCES margin_policy(id, version)
);

CREATE INDEX margin_policy_assignment_lookup ON margin_policy_assignment(account_id, effective_at);
CREATE UNIQUE INDEX margin_policy_assignment_effective_unique
    ON margin_policy_assignment(account_id, effective_at);
