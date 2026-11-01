-- V49 used a fixed scale that could coerce excess precision before validation.
-- Keep the exact value, trim only storage padding from the old fixed-scale type,
-- and reject any existing or future value outside the ADR-083 scale contract.
ALTER TABLE margin_policy
    ALTER COLUMN margin_rate TYPE NUMERIC
    USING trim_scale(margin_rate);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM margin_policy WHERE scale(margin_rate) > 8) THEN
        RAISE EXCEPTION 'existing margin_policy rows violate the eight-decimal rate contract';
    END IF;
END;
$$;

ALTER TABLE margin_policy
    ADD CONSTRAINT margin_policy_rate_scale_valid CHECK (scale(margin_rate) BETWEEN 0 AND 8),
    ADD CONSTRAINT margin_policy_fields_non_blank CHECK (
        btrim(version) <> ''
        AND btrim(instrument) <> ''
        AND btrim(source_category) <> ''
        AND btrim(source_reference) <> ''
    ),
    ADD CONSTRAINT margin_policy_effective_order CHECK (
        expires_at IS NULL OR expires_at > effective_at
    );

CREATE TRIGGER immutable_margin_policy
    BEFORE UPDATE OR DELETE ON margin_policy
    FOR EACH ROW EXECUTE FUNCTION reject_trading_core_immutable_mutation();
