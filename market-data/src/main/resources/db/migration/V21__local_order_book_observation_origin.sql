ALTER TABLE historical_order_book_evidence
    DROP CONSTRAINT ck_historical_order_book_origin;

ALTER TABLE historical_order_book_evidence
    ADD CONSTRAINT ck_historical_order_book_origin
        CHECK (origin IN ('PROVIDER', 'CHECKPOINT', 'LOCAL_OBSERVATION'));
