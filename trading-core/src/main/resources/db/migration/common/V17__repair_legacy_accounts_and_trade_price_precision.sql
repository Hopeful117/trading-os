ALTER TABLE trades
    ALTER COLUMN entry_price SET DATA TYPE NUMERIC(38,12);
ALTER TABLE trades
    ALTER COLUMN current_price SET DATA TYPE NUMERIC(38,12);
ALTER TABLE trades
    ALTER COLUMN exit_price SET DATA TYPE NUMERIC(38,12);
ALTER TABLE trades
    ALTER COLUMN stop_loss SET DATA TYPE NUMERIC(38,12);
ALTER TABLE trades
    ALTER COLUMN take_profit SET DATA TYPE NUMERIC(38,12);

INSERT INTO broker_account (
    id,
    owner_id,
    provider,
    display_name,
    connection_status,
    created_at,
    updated_at,
    version,
    execution_mode
)
SELECT
    account_id,
    user_id,
    'KRAKEN',
    'Legacy placeholder - ' || name,
    'CREATED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0,
    'LIVE'
FROM accounts
WHERE broker_account_id IS NULL
  AND user_id IS NOT NULL
  AND broker IS NOT NULL;

UPDATE accounts
SET broker_account_id = account_id
WHERE broker_account_id IS NULL
  AND user_id IS NOT NULL
  AND broker IS NOT NULL;

UPDATE trades trade
SET entry_price = (
        SELECT MIN(fill.price)
        FROM accounts account
        JOIN broker_account broker ON broker.id = account.broker_account_id
        JOIN execution_intent intent ON intent.broker_account_id = broker.id
        JOIN execution_broker_order broker_order ON broker_order.intent_id = intent.id
        JOIN execution_broker_fill fill ON fill.broker_order_id = broker_order.id
        WHERE account.account_id = trade.account_id
          AND intent.purpose = 'ENTRY'
          AND intent.instrument = trade.symbol
          AND intent.quantity = trade.quantity
          AND fill.price > 0
    ),
    current_price = (
        SELECT MIN(fill.price)
        FROM accounts account
        JOIN broker_account broker ON broker.id = account.broker_account_id
        JOIN execution_intent intent ON intent.broker_account_id = broker.id
        JOIN execution_broker_order broker_order ON broker_order.intent_id = intent.id
        JOIN execution_broker_fill fill ON fill.broker_order_id = broker_order.id
        WHERE account.account_id = trade.account_id
          AND intent.purpose = 'ENTRY'
          AND intent.instrument = trade.symbol
          AND intent.quantity = trade.quantity
          AND fill.price > 0
    )
WHERE trade.entry_price <= 0
  AND EXISTS (
        SELECT 1
        FROM accounts account
        JOIN broker_account broker ON broker.id = account.broker_account_id
        JOIN execution_intent intent ON intent.broker_account_id = broker.id
        JOIN execution_broker_order broker_order ON broker_order.intent_id = intent.id
        JOIN execution_broker_fill fill ON fill.broker_order_id = broker_order.id
        WHERE account.account_id = trade.account_id
          AND intent.purpose = 'ENTRY'
          AND intent.instrument = trade.symbol
          AND intent.quantity = trade.quantity
          AND fill.price > 0
    );

UPDATE trades
SET trade_status = 'CLOSED',
    closed_at = COALESCE(closed_at, opened_at)
WHERE trade_status = 'OPEN'
  AND quantity <= 0;

ALTER TABLE trades
    ADD CONSTRAINT trades_positive_entry_price CHECK (entry_price > 0);
ALTER TABLE trades
    ADD CONSTRAINT trades_open_quantity_positive
        CHECK (trade_status <> 'OPEN' OR quantity > 0);
