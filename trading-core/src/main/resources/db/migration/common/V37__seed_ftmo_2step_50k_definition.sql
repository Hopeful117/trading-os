-- Reproducible Trading OS definition for the FTMO 2-Step 50k evaluation.
-- External rules were verified against the official FTMO pages on 2026-10-24.
-- The definition and policy are immutable; a later rule change requires a new version.

INSERT INTO risk_profile (
    id, semantic_version, policy_id, policy_version, authority, created_at, provenance
) VALUES (
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090',
    '1.0.0',
    'FTMO_2STEP_50K_RISK',
    '1.0.0',
    'PROP_FIRM',
    TIMESTAMP WITH TIME ZONE '2026-10-24 12:00:00+02:00',
    'FTMO 2-Step 50k; official sources verified 2026-10-24; https://ftmo.com/en/trading-objectives/; https://ftmo.com/en/challenge/; daily loss 5% and static maximum loss 10% of initial simulated capital; both loss boundaries use STRICT semantics because the source describes a breach below the applicable limit; Risk-day timezone must be explicitly configured as Europe/Paris with a 00:00 reset; cTrader runtime validation remains BLOCKED_EXTERNAL and execution remains disabled'
);

INSERT INTO risk_profile_rule (
    profile_id, profile_semantic_version, rule_id, rule_version, category,
    severity, priority, maximum_ratio, provenance, breach_boundary
) VALUES
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090', '1.0.0',
    'MAX_POSITION_RISK', '1.0.0', 'POSITION', 'BLOCKING', 10, 0.01,
    'Generic deterministic Trading OS safety rule; not an FTMO provider rule', 'INCLUSIVE'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090', '1.0.0',
    'MAX_EXPOSURE', '1.0.0', 'PORTFOLIO', 'BLOCKING', 20, 0.03,
    'Generic deterministic Trading OS safety rule; not an FTMO provider rule', 'INCLUSIVE'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090', '1.0.0',
    'DAILY_DRAWDOWN', '1.0.0', 'ACCOUNT', 'BLOCKING', 30, 0.05,
    'FTMO 2-Step 50k; daily loss is 5% of initial simulated capital; official sources verified 2026-10-24; equality is safe and only a value below the applicable limit breaches', 'STRICT'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090', '1.0.0',
    'MAX_TOTAL_DRAWDOWN', '1.0.0', 'ACCOUNT', 'BLOCKING', 40, 0.10,
    'FTMO 2-Step 50k; maximum loss is a static 10% floor of initial simulated capital; official sources verified 2026-10-24; equality is safe and only a value below the applicable limit breaches', 'STRICT'
);

INSERT INTO challenge_definition (
    id, definition_version, provider, product, plan_code, capital_currency,
    starting_capital, profit_target_ratio, progression_value_source,
    required_settlement_model, required_instrument_product_type,
    risk_policy_id, risk_policy_version, source_url, retrieved_at,
    effective_from, provenance, created_at
) VALUES (
    '00890000-0000-4000-8000-000000000051',
    '1.0.0',
    'FTMO',
    '2_STEP',
    'STANDARD',
    'USD',
    50000,
    0.10,
    'BALANCE',
    NULL,
    NULL,
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090',
    '1.0.0',
    'https://ftmo.com/en/trading-objectives/',
    TIMESTAMP WITH TIME ZONE '2026-10-24 12:00:00+02:00',
    TIMESTAMP WITH TIME ZONE '2026-10-24 12:00:00+02:00',
    'FTMO 2-Step 50k definition; official sources verified 2026-10-24; https://ftmo.com/en/trading-objectives/; https://ftmo.com/en/challenge/; Challenge target 10%; Verification target 5%; minimum four trading days per phase; daily loss 5%; static maximum loss 10%; trading day reset at 00:00 CE(S)T; execution platform cTrader and integration target cTrader Open API are provenance only; runtime validation is BLOCKED_EXTERNAL; no provider confirmation is inferred locally',
    TIMESTAMP WITH TIME ZONE '2026-10-24 12:00:00+02:00'
);

INSERT INTO challenge_definition_phase (
    definition_id, definition_version, phase_key, phase_order,
    profit_target_ratio, progression_value_source, minimum_trading_days,
    risk_policy_id, risk_policy_version, provider_confirmation_required
) VALUES
(
    '00890000-0000-4000-8000-000000000051', '1.0.0', 'CHALLENGE', 1,
    0.10, 'BALANCE', 4,
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090', '1.0.0', TRUE
),
(
    '00890000-0000-4000-8000-000000000051', '1.0.0', 'VERIFICATION', 2,
    0.05, 'BALANCE', 4,
    '0a10c7e2-9d1e-4f5a-b6c8-123456789090', '1.0.0', FALSE
);
