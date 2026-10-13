CREATE FUNCTION reject_trading_core_immutable_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER immutable_risk_profile
    BEFORE UPDATE OR DELETE ON risk_profile
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
CREATE TRIGGER immutable_risk_profile_rule
    BEFORE UPDATE OR DELETE ON risk_profile_rule
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
CREATE TRIGGER immutable_risk_day_baseline
    BEFORE UPDATE OR DELETE ON risk_day_baseline
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
CREATE TRIGGER immutable_risk_component_snapshot
    BEFORE UPDATE OR DELETE ON risk_component_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
CREATE TRIGGER immutable_risk_context_snapshot
    BEFORE UPDATE OR DELETE ON risk_context_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
CREATE TRIGGER immutable_risk_evaluation
    BEFORE UPDATE OR DELETE ON risk_evaluation
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
