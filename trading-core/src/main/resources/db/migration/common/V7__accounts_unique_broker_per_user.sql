-- A broker account must be unique per user, not globally.
-- The previous global UNIQUE(broker) prevented any second user from synchronizing.
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_broker_key;

ALTER TABLE accounts ADD CONSTRAINT accounts_user_broker_key UNIQUE (user_id, broker);
