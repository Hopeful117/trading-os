ALTER TABLE strategy_definitions ADD COLUMN scenario VARCHAR(200);

UPDATE strategy_definitions
SET scenario = name
WHERE scenario IS NULL;

ALTER TABLE strategy_definitions ALTER COLUMN scenario SET NOT NULL;
