ALTER TABLE risk_profile_rule
    DROP CONSTRAINT IF EXISTS risk_profile_rule_rule_id_check;

ALTER TABLE risk_profile_rule
    ADD CONSTRAINT risk_profile_rule_rule_id_check
        CHECK (rule_id IN ('MAX_POSITION_RISK','MAX_EXPOSURE','DAILY_DRAWDOWN','MAX_TOTAL_DRAWDOWN'));
