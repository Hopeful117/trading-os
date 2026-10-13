-- Reproducible Trading OS benchmark catalog entry for Kraken Prop Advanced 50k.
-- External rules were verified against the English official pages on 2026-10-13.
-- Immutable identity/version collisions must fail rather than mutate history.

-- V20 names the replacement constraint, but H2 retains the anonymous check
-- created by V2. Drop both possible names before applying the final vocabulary.
ALTER TABLE risk_profile_rule
    DROP CONSTRAINT IF EXISTS CONSTRAINT_C;

ALTER TABLE risk_profile_rule
    DROP CONSTRAINT IF EXISTS risk_profile_rule_rule_id_check;

ALTER TABLE risk_profile_rule
    ADD CONSTRAINT ck_risk_profile_rule_rule_id
    CHECK (rule_id IN ('MAX_POSITION_RISK','MAX_EXPOSURE','DAILY_DRAWDOWN','MAX_TOTAL_DRAWDOWN'));

INSERT INTO risk_profile (
    id, semantic_version, policy_id, policy_version, authority, created_at, provenance
) VALUES (
    '0a10c7e2-9d1e-4f5a-b6c8-123456789089',
    '1.0.0',
    'KRAKEN_PROP_ADVANCED_50K_RISK',
    '1.0.0',
    'PLATFORM',
    TIMESTAMP WITH TIME ZONE '2026-10-13 14:41:51+02:00',
    'Kraken Prop Evaluation Advanced 50k benchmark; English official sources verified 2026-10-13T14:41:51+02:00; https://www.kraken.com/pro/prop; https://support.kraken.com/articles/kraken-prop-faq; https://blog.kraken.com/product/prop/introducing-kraken-prop; benchmark policy uses generic DAILY_DRAWDOWN and MAX_TOTAL_DRAWDOWN; periodic margin funding and configurable commission are not modeled'
);

INSERT INTO risk_profile_rule (
    profile_id, profile_semantic_version, rule_id, rule_version, category,
    severity, priority, maximum_ratio, provenance
) VALUES
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789089', '1.0.0',
    'MAX_POSITION_RISK', '1.0.0', 'POSITION', 'BLOCKING', 10, 0.01,
    'Trading OS generic PAPER baseline required for account provisioning; not a Kraken-specific rule'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789089', '1.0.0',
    'MAX_EXPOSURE', '1.0.0', 'PORTFOLIO', 'BLOCKING', 20, 0.03,
    'Trading OS generic PAPER baseline required for account provisioning; not a Kraken-specific rule'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789089', '1.0.0',
    'DAILY_DRAWDOWN', '1.0.0', 'ACCOUNT', 'BLOCKING', 30, 0.03,
    'Kraken Prop Advanced 50k benchmark; English official sources verified 2026-10-13T14:41:51+02:00; daily balance baseline; equality is an inclusive breach'
),
(
    '0a10c7e2-9d1e-4f5a-b6c8-123456789089', '1.0.0',
    'MAX_TOTAL_DRAWDOWN', '1.0.0', 'ACCOUNT', 'BLOCKING', 40, 0.03,
    'Kraken Prop Advanced 50k benchmark; English official sources verified 2026-10-13T14:41:51+02:00; static starting-balance baseline; equality is an inclusive breach'
);

INSERT INTO challenge_definition (
    id, definition_version, provider, product, plan_code, capital_currency,
    starting_capital, profit_target_ratio, progression_value_source,
    required_settlement_model, required_instrument_product_type,
    risk_policy_id, risk_policy_version, source_url, retrieved_at,
    effective_from, provenance, created_at
) VALUES (
    '00890000-0000-4000-8000-000000000050',
    '1.0.0',
    'KRAKEN_PROP',
    'EVALUATION',
    'ADVANCED',
    'USD',
    50000,
    0.09,
    'EQUITY',
    'CASH',
    'LINEAR_PERPETUAL',
    '0a10c7e2-9d1e-4f5a-b6c8-123456789089',
    '1.0.0',
    'https://www.kraken.com/pro/prop',
    TIMESTAMP WITH TIME ZONE '2026-10-13 14:41:51+02:00',
    TIMESTAMP WITH TIME ZONE '2026-10-13 14:41:51+02:00',
    'Kraken Prop Evaluation Advanced 50k benchmark; English official sources verified 2026-10-13T14:41:51+02:00; supporting FAQ https://support.kraken.com/articles/kraken-prop-faq; supporting product announcement https://blog.kraken.com/product/prop/introducing-kraken-prop; target 9%; MDL 3%; MDD 3% static; evaluation fee USD 180 is provenance-only and does not reduce trading capital; commission 0.04% per side and periodic funding 0.033% per day charged every 4 hours are not modeled; no evaluation time limit; no automatic provider terminal actions modeled',
    TIMESTAMP WITH TIME ZONE '2026-10-13 14:41:51+02:00'
);
