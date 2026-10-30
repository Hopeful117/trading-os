ALTER TABLE historical_order_book_replay_artifact
    ADD COLUMN source_origin VARCHAR(32) NOT NULL DEFAULT 'PROVIDER';

ALTER TABLE historical_order_book_replay_artifact
    ADD CONSTRAINT ck_historical_order_book_artifact_origin
    CHECK (source_origin IN ('PROVIDER', 'LOCAL_OBSERVATION', 'CHECKPOINT', 'MIXED'));
