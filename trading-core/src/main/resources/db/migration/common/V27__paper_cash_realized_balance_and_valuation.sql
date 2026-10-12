ALTER TABLE accounts ADD COLUMN realized_balance NUMERIC(38,2);
ALTER TABLE accounts ADD COLUMN valuation_status VARCHAR(32);
ALTER TABLE accounts ADD COLUMN valued_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN market_data_timestamp TIMESTAMP WITH TIME ZONE;

UPDATE accounts
SET realized_balance = equity,
    valuation_status = 'UNAVAILABLE'
WHERE settlement_model = 'CASH'
  AND EXISTS (
      SELECT 1
      FROM broker_account broker
      WHERE broker.id = accounts.broker_account_id
        AND broker.execution_mode = 'PAPER'
  );
