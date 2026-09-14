ALTER TABLE broker_account
    ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'LIVE';
