CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE historical_fee_schedule
    ADD CONSTRAINT ex_historical_fee_schedule_no_overlap
    EXCLUDE USING gist (
        broker_account_id WITH =,
        provider WITH =,
        instrument WITH =,
        product_type WITH =,
        tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&
    )
    WHERE (origin = 'CAPTURED');
