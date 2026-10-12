ALTER TABLE markets ADD COLUMN product_type VARCHAR(32);

UPDATE markets SET product_type = 'SPOT' WHERE product_type IS NULL;

ALTER TABLE markets ALTER COLUMN product_type SET NOT NULL;

ALTER TABLE markets DROP CONSTRAINT IF EXISTS uk_markets_provider_symbol;

ALTER TABLE markets ADD CONSTRAINT uk_markets_provider_symbol_product
    UNIQUE (provider, symbol, product_type);
