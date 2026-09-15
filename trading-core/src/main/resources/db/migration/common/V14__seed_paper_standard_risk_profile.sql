-- Human-approved Trading OS PAPER Standard Risk Policy v1.
-- This migration provisions reference data only; it deliberately creates no
-- account, broker account, or profile assignment.
INSERT INTO risk_profile (
    id, semantic_version, policy_id, policy_version, authority, created_at, provenance
) VALUES (
    '0a10c7e2-9d1e-4f5a-b6c8-123456789043',
    '1.0.0',
    'TRADING_OS_STANDARD_RISK',
    '1.0.0',
    'PLATFORM',
    TIMESTAMP WITH TIME ZONE '2026-09-15 00:00:00+00',
    'Trading OS PAPER Standard Risk Policy v1, human-approved product policy, 2026-09-15'
);

INSERT INTO risk_profile_rule (
    profile_id, profile_semantic_version, rule_id, rule_version, category,
    severity, priority, maximum_ratio, provenance
) VALUES
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789043', '1.0.0',
    'MAX_POSITION_RISK', '1.0.0', 'POSITION', 'BLOCKING', 10, 0.01,
    'Trading OS PAPER Standard Risk Policy v1, human-approved product policy, 2026-09-15'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789043', '1.0.0',
    'MAX_EXPOSURE', '1.0.0', 'PORTFOLIO', 'BLOCKING', 20, 0.03,
    'Trading OS PAPER Standard Risk Policy v1, human-approved product policy, 2026-09-15'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789043', '1.0.0',
    'DAILY_DRAWDOWN', '1.0.0', 'ACCOUNT', 'BLOCKING', 30, 0.03,
    'Trading OS PAPER Standard Risk Policy v1, human-approved product policy, 2026-09-15'
);
