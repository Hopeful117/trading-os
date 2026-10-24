ALTER TABLE risk_profile_rule
    ADD COLUMN breach_boundary VARCHAR(16) NOT NULL DEFAULT 'INCLUSIVE';

ALTER TABLE risk_profile_rule
    ADD CONSTRAINT risk_profile_rule_breach_boundary_check
    CHECK (breach_boundary IN ('INCLUSIVE', 'STRICT'));
