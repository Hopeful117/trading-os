CREATE TABLE markets (
    market_id UUID NOT NULL,
    provider VARCHAR(255) NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    base_asset VARCHAR(255) NOT NULL,
    quote_asset VARCHAR(255) NOT NULL,
    minimum_order_size NUMERIC(38, 2),
    minimum_cost NUMERIC(38, 2),
    tick_size NUMERIC(38, 2),
    quantity_precision INTEGER,
    price_precision INTEGER,
    trading_status VARCHAR(255),
    tradable BOOLEAN NOT NULL,
    closure_reason VARCHAR(255),
    last_updated TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_markets PRIMARY KEY (market_id),
    CONSTRAINT uk_markets_provider_symbol UNIQUE (provider, symbol)
);
