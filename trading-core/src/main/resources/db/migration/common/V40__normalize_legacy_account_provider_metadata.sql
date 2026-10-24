-- V34 replaced the broker provider name with an opaque provider registration
-- reference. Repair accounts created before that migration so the canonical
-- broker-account relation and legacy metadata agree.
UPDATE accounts
SET broker = CAST((
    SELECT b.provider_registration_reference
    FROM broker_account b
    WHERE b.id = accounts.broker_account_id
) AS VARCHAR)
WHERE broker_account_id IS NOT NULL
  AND (
      broker IS NULL
      OR broker <> CAST((
          SELECT b.provider_registration_reference
          FROM broker_account b
          WHERE b.id = accounts.broker_account_id
      ) AS VARCHAR)
  );
