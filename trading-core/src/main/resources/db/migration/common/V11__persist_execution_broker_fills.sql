CREATE TABLE execution_broker_fill (
    fill_id VARCHAR(255) PRIMARY KEY,
    broker_order_id UUID NOT NULL REFERENCES execution_broker_order(id),
    quantity NUMERIC(30,12) NOT NULL,
    price NUMERIC(30,12) NOT NULL,
    fee NUMERIC(30,12) NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_execution_broker_fill_order
    ON execution_broker_fill(broker_order_id, executed_at);
