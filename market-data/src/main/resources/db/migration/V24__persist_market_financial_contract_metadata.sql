ALTER TABLE markets ADD COLUMN quantity_unit VARCHAR(32);
ALTER TABLE markets ADD COLUMN contract_multiplier NUMERIC(38,12);
ALTER TABLE markets ADD COLUMN contract_source_id VARCHAR(255);
ALTER TABLE markets ADD COLUMN contract_source_version BIGINT;
ALTER TABLE markets ADD COLUMN contract_observed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE markets ADD CONSTRAINT markets_financial_contract_complete
    CHECK (
        (product_type <> 'CFD' AND quantity_unit IS NULL AND contract_multiplier IS NULL
            AND contract_source_id IS NULL AND contract_source_version IS NULL
            AND contract_observed_at IS NULL)
        OR (product_type = 'CFD' AND quantity_unit IS NOT NULL AND contract_multiplier IS NOT NULL
            AND contract_multiplier > 0 AND contract_source_id IS NOT NULL
            AND contract_source_version IS NOT NULL AND contract_source_version > 0
            AND contract_observed_at IS NOT NULL)
    );
