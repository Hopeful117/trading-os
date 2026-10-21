CREATE INDEX ix_historical_order_book_trusted_snapshot
    ON historical_order_book_evidence (market_id, depth, evidence_type, trusted, known_at, sequence_id);
