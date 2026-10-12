ALTER TABLE accounts ADD COLUMN settlement_model VARCHAR(32) DEFAULT 'INVENTORY';

UPDATE accounts
SET settlement_model = CASE
    WHEN EXISTS (
        SELECT 1
        FROM broker_account broker
        WHERE broker.id = accounts.broker_account_id
          AND broker.execution_mode = 'PAPER'
    ) THEN 'INVENTORY'
    ELSE 'UNKNOWN'
END;

ALTER TABLE accounts ALTER COLUMN settlement_model SET NOT NULL;
