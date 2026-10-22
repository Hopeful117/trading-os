CREATE INDEX ix_historical_order_book_sequence_verification
    ON historical_order_book_evidence (sequence_id, sequence_ordinal, known_at);
