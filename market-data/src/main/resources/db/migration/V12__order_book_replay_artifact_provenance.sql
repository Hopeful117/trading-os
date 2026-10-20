ALTER TABLE historical_order_book_replay_artifact
    ADD COLUMN artifact_schema_version VARCHAR(64) NOT NULL DEFAULT 'order-book-replay-artifact-v1';

ALTER TABLE historical_order_book_replay_artifact
    ADD COLUMN source_trust_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED';

ALTER TABLE historical_order_book_replay_artifact
    ADD COLUMN source_evidence_fingerprints_payload TEXT;

ALTER TABLE historical_order_book_replay_artifact
    ADD CONSTRAINT ck_historical_order_book_artifact_trust
    CHECK (source_trust_status IN ('TRUSTED', 'UNVERIFIED'));
