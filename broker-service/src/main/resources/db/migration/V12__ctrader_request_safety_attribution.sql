ALTER TABLE ctrader_request_safety_event
    ADD COLUMN operation VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN_OPERATION',
    ADD COLUMN trigger VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN_TRIGGER',
    ADD COLUMN priority VARCHAR(64) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN outcome VARCHAR(32) NOT NULL DEFAULT 'RESERVED';

CREATE INDEX idx_ctrader_request_safety_event_day_operation
    ON ctrader_request_safety_event (request_day, operation, trigger, priority, outcome);
