ALTER TABLE challenge_definition
    ADD COLUMN IF NOT EXISTS required_market_provider VARCHAR(32);

ALTER TABLE challenge_definition
    ADD COLUMN IF NOT EXISTS required_provider_registration_reference UUID;

ALTER TABLE challenge_instance
    ADD COLUMN IF NOT EXISTS required_market_provider VARCHAR(32);

ALTER TABLE challenge_instance
    ADD COLUMN IF NOT EXISTS required_provider_registration_reference UUID;

UPDATE challenge_definition
SET required_market_provider = 'CTRADER',
    required_provider_registration_reference = '3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012'
WHERE provider = 'FTMO' AND product = '2_STEP' AND plan_code = 'STANDARD';

UPDATE challenge_instance ci
SET required_market_provider = (
        SELECT cd.required_market_provider
        FROM challenge_definition cd
        WHERE cd.id = ci.definition_id
          AND cd.definition_version = ci.definition_version
    ),
    required_provider_registration_reference = (
        SELECT cd.required_provider_registration_reference
        FROM challenge_definition cd
        WHERE cd.id = ci.definition_id
          AND cd.definition_version = ci.definition_version
    )
WHERE EXISTS (
    SELECT 1
    FROM challenge_definition cd
    WHERE cd.id = ci.definition_id
      AND cd.definition_version = ci.definition_version
)
  AND (ci.required_market_provider IS NULL
       OR ci.required_provider_registration_reference IS NULL);
