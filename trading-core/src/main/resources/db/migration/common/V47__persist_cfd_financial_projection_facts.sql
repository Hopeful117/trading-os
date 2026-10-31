ALTER TABLE trades ADD COLUMN product_type VARCHAR(32);
ALTER TABLE trades ADD COLUMN base_currency VARCHAR(16);
ALTER TABLE trades ADD COLUMN quote_currency VARCHAR(16);
ALTER TABLE trades ADD COLUMN quantity_unit VARCHAR(32);
ALTER TABLE trades ADD COLUMN contract_multiplier NUMERIC(38,12);
ALTER TABLE trades ADD COLUMN price_precision INTEGER;
ALTER TABLE trades ADD COLUMN quantity_precision INTEGER;
ALTER TABLE trades ADD COLUMN minimum_quantity NUMERIC(38,12);
ALTER TABLE trades ADD COLUMN quantity_increment NUMERIC(38,12);
ALTER TABLE trades ADD COLUMN contract_source_id VARCHAR(255);
ALTER TABLE trades ADD COLUMN contract_source_version BIGINT;
ALTER TABLE trades ADD COLUMN contract_observed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE trades ADD CONSTRAINT trades_financial_contract_complete
    CHECK (
        (product_type IS NULL AND base_currency IS NULL AND quote_currency IS NULL
            AND quantity_unit IS NULL AND contract_multiplier IS NULL
            AND price_precision IS NULL AND quantity_precision IS NULL
            AND minimum_quantity IS NULL AND quantity_increment IS NULL
            AND contract_source_id IS NULL AND contract_source_version IS NULL
            AND contract_observed_at IS NULL)
        OR (product_type = 'CFD' AND base_currency IS NOT NULL AND quote_currency IS NOT NULL
            AND quantity_unit IS NOT NULL AND contract_multiplier IS NOT NULL
            AND contract_multiplier > 0 AND price_precision IS NOT NULL AND price_precision >= 0
            AND quantity_precision IS NOT NULL AND quantity_precision >= 0
            AND minimum_quantity IS NOT NULL AND minimum_quantity > 0
            AND quantity_increment IS NOT NULL AND quantity_increment > 0
            AND contract_source_id IS NOT NULL AND contract_source_version IS NOT NULL
            AND contract_source_version > 0 AND contract_observed_at IS NOT NULL)
    );
