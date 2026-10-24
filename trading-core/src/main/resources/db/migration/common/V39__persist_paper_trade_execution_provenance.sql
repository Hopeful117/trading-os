ALTER TABLE execution_broker_fill
    ADD CONSTRAINT uk_execution_broker_fill_order_fill UNIQUE (broker_order_id, fill_id);

CREATE TABLE trade_execution_provenance (
    id UUID PRIMARY KEY,
    trade_id UUID NOT NULL REFERENCES trades(trade_id),
    execution_attempt_id UUID NOT NULL REFERENCES execution_attempt(id),
    broker_order_id UUID NOT NULL REFERENCES execution_broker_order(id),
    fill_id VARCHAR(255) NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    execution_mode VARCHAR(16) NOT NULL,
    execution_purpose VARCHAR(16) NOT NULL,
    CONSTRAINT fk_trade_execution_provenance_fill
        FOREIGN KEY (broker_order_id, fill_id)
        REFERENCES execution_broker_fill(broker_order_id, fill_id),
    CONSTRAINT uk_trade_execution_provenance_fill UNIQUE (broker_order_id, fill_id)
);

CREATE INDEX idx_trade_execution_provenance_trade_time
    ON trade_execution_provenance(trade_id, executed_at);
