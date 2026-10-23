ALTER TABLE broker_account
    ADD COLUMN provider_registration_reference UUID;

UPDATE broker_account
SET provider_registration_reference = '7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01'
WHERE provider = 'KRAKEN';

ALTER TABLE broker_account
    ALTER COLUMN provider_registration_reference SET NOT NULL;

ALTER TABLE broker_account
    DROP COLUMN provider;
