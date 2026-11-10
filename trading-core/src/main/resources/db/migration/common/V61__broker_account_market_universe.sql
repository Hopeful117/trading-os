CREATE TABLE broker_account_market (
    broker_account_id UUID NOT NULL,
    market_id UUID NOT NULL,
    CONSTRAINT pk_broker_account_market PRIMARY KEY (broker_account_id, market_id),
    CONSTRAINT fk_broker_account_market_account FOREIGN KEY (broker_account_id)
        REFERENCES broker_account (id)
);

ALTER TABLE research_scope ADD COLUMN broker_account_id UUID;

ALTER TABLE research_scope ADD CONSTRAINT fk_research_scope_broker_account
    FOREIGN KEY (broker_account_id) REFERENCES broker_account (id);

CREATE INDEX idx_research_scope_broker_account ON research_scope (broker_account_id);
