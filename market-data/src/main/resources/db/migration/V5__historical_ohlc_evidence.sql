CREATE TABLE historical_ohlc_evidence (
    observation_id UUID NOT NULL,
    market_id UUID NOT NULL,
    interval_name VARCHAR(32) NOT NULL,
    open_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    close_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    open NUMERIC(38, 18) NOT NULL,
    high NUMERIC(38, 18) NOT NULL,
    low NUMERIC(38, 18) NOT NULL,
    close NUMERIC(38, 18) NOT NULL,
    volume NUMERIC(38, 18),
    vwap NUMERIC(38, 18),
    trade_count INTEGER,
    closed BOOLEAN NOT NULL,
    synthetic BOOLEAN NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_symbol VARCHAR(255),
    source_id VARCHAR(255) NOT NULL,
    source_updated_at TIMESTAMP(6) WITH TIME ZONE,
    fetched_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    known_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    origin VARCHAR(32) NOT NULL,
    normalization_version VARCHAR(64) NOT NULL,
    observation_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT pk_historical_ohlc_evidence PRIMARY KEY (observation_id),
    CONSTRAINT uk_historical_ohlc_fingerprint UNIQUE (observation_fingerprint),
    CONSTRAINT fk_historical_ohlc_market FOREIGN KEY (market_id) REFERENCES markets (market_id),
    CONSTRAINT ck_historical_ohlc_time CHECK (open_time < close_time),
    CONSTRAINT ck_historical_ohlc_prices CHECK (
        open > 0 AND high > 0 AND low > 0 AND close > 0
        AND low <= open AND open <= high
        AND low <= close AND close <= high
    ),
    CONSTRAINT ck_historical_ohlc_volume CHECK (volume IS NULL OR volume >= 0),
    CONSTRAINT ck_historical_ohlc_trade_count CHECK (trade_count IS NULL OR trade_count >= 0)
);

CREATE INDEX ix_historical_ohlc_market_interval_open
    ON historical_ohlc_evidence (market_id, interval_name, open_time);

CREATE INDEX ix_historical_ohlc_market_interval_known_close
    ON historical_ohlc_evidence (market_id, interval_name, known_at, close_time);
