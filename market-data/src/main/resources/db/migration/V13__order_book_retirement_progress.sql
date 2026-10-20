ALTER TABLE historical_order_book_retirement_operation
    ADD COLUMN affected_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE historical_order_book_retirement_operation
    ADD COLUMN outcome VARCHAR(16);
