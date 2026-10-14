ALTER TABLE markets ADD COLUMN provider_symbol VARCHAR(255);

UPDATE markets
SET provider_symbol = REPLACE(symbol, '/', '')
WHERE provider_symbol IS NULL;
