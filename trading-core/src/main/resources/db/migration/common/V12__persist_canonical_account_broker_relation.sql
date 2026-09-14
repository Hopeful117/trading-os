ALTER TABLE accounts
    ADD COLUMN broker_account_id UUID;

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_broker_account
        FOREIGN KEY (broker_account_id) REFERENCES broker_account(id);

UPDATE accounts
SET broker_account_id = (
    SELECT c.broker_account_id
    FROM account_risk_configuration c
    JOIN broker_account b ON b.id = c.broker_account_id
    WHERE c.account_id = accounts.account_id
       AND accounts.user_id = b.owner_id
       AND accounts.broker IS NOT NULL
       AND accounts.broker = b.provider
       AND NOT EXISTS (
           SELECT 1
           FROM account_risk_configuration other_c
           WHERE other_c.account_id <> accounts.account_id
             AND other_c.broker_account_id = c.broker_account_id
       )
       AND (
           SELECT COUNT(*)
           FROM account_risk_configuration unique_c
           JOIN broker_account unique_b ON unique_b.id = unique_c.broker_account_id
           WHERE unique_c.account_id = accounts.account_id
             AND accounts.user_id = unique_b.owner_id
             AND accounts.broker IS NOT NULL
             AND accounts.broker = unique_b.provider
             AND NOT EXISTS (
                 SELECT 1
                 FROM account_risk_configuration unique_other_c
                 WHERE unique_other_c.account_id <> accounts.account_id
                   AND unique_other_c.broker_account_id = unique_c.broker_account_id
             )
       ) = 1
)
WHERE EXISTS (
    SELECT 1
    FROM account_risk_configuration c
    JOIN broker_account b ON b.id = c.broker_account_id
    WHERE c.account_id = accounts.account_id
       AND accounts.user_id = b.owner_id
       AND accounts.broker IS NOT NULL
       AND accounts.broker = b.provider
       AND NOT EXISTS (
           SELECT 1
           FROM account_risk_configuration other_c
           WHERE other_c.account_id <> accounts.account_id
             AND other_c.broker_account_id = c.broker_account_id
       )
       AND (
           SELECT COUNT(*)
           FROM account_risk_configuration unique_c
           JOIN broker_account unique_b ON unique_b.id = unique_c.broker_account_id
           WHERE unique_c.account_id = accounts.account_id
             AND accounts.user_id = unique_b.owner_id
             AND accounts.broker IS NOT NULL
             AND accounts.broker = unique_b.provider
             AND NOT EXISTS (
                 SELECT 1
                 FROM account_risk_configuration unique_other_c
                 WHERE unique_other_c.account_id <> accounts.account_id
                   AND unique_other_c.broker_account_id = unique_c.broker_account_id
             )
       ) = 1
);

ALTER TABLE accounts
    ADD CONSTRAINT accounts_broker_account_key UNIQUE (broker_account_id);

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_user_broker_key;

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_broker_key;
