ALTER TABLE trades
    DROP CONSTRAINT trades_open_quantity_positive;

ALTER TABLE trades
    ADD CONSTRAINT trades_open_quantity_valid
        CHECK (
            trade_status <> 'OPEN'
            OR (product_type = 'CFD' AND quantity <> 0)
            OR (COALESCE(product_type, '') <> 'CFD' AND quantity > 0)
        );
