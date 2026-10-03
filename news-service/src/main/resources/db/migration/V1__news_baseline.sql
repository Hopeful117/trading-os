CREATE TABLE economic_events (
    id UUID NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_event_id VARCHAR(200) NOT NULL,
    title VARCHAR(300) NOT NULL,
    category VARCHAR(100),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_at TIMESTAMP WITH TIME ZONE,
    currencies TEXT,
    market_ids TEXT,
    impact VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    previous_value VARCHAR(100),
    consensus_value VARCHAR(100),
    actual_value VARCHAR(100),
    source_updated_at TIMESTAMP WITH TIME ZONE,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    normalization_version VARCHAR(30) NOT NULL,
    CONSTRAINT pk_economic_events PRIMARY KEY (id),
    CONSTRAINT uq_economic_event_source UNIQUE (source_name, source_event_id)
);

CREATE INDEX ix_economic_events_scheduled_at ON economic_events (scheduled_at);

CREATE TABLE financial_news_items (
    id UUID NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_item_id VARCHAR(200) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    canonical_url VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    publisher VARCHAR(200),
    categories TEXT,
    currencies TEXT,
    market_ids TEXT,
    impact VARCHAR(20) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    normalization_version VARCHAR(30) NOT NULL,
    CONSTRAINT pk_financial_news_items PRIMARY KEY (id),
    CONSTRAINT uq_financial_news_source UNIQUE (source_name, source_item_id)
);

CREATE INDEX ix_financial_news_published_at ON financial_news_items (published_at);
