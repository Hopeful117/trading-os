CREATE TRIGGER immutable_risk_evaluation_t1
    BEFORE UPDATE OR DELETE ON risk_evaluation_t1
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
