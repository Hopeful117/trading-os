ALTER TABLE challenge_definition
    ADD COLUMN required_settlement_model VARCHAR(32);

ALTER TABLE challenge_definition
    DROP CONSTRAINT IF EXISTS challenge_definition_progression_value_source_check;

ALTER TABLE challenge_definition
    DROP CONSTRAINT IF EXISTS CONSTRAINT_AE4D;

ALTER TABLE challenge_definition
    ADD CONSTRAINT ck_challenge_definition_progression_source
    CHECK (progression_value_source IN ('BALANCE', 'EQUITY'));
