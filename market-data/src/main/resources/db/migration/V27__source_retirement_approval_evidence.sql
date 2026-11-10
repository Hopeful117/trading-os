CREATE TABLE historical_order_book_source_retirement_approval (
    approval_reference VARCHAR(255) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_symbol VARCHAR(255) NOT NULL,
    market_id UUID NOT NULL,
    depth INTEGER NOT NULL,
    approved_actor VARCHAR(255) NOT NULL,
    valid_until TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_order_book_source_retirement_approval PRIMARY KEY (approval_reference),
    CONSTRAINT ck_order_book_source_retirement_approval_depth CHECK (depth > 0)
);

CREATE INDEX ix_order_book_source_retirement_approval_scope
    ON historical_order_book_source_retirement_approval
       (provider, provider_symbol, market_id, depth, valid_until);
