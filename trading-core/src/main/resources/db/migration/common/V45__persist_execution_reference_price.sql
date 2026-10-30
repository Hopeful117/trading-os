ALTER TABLE execution_intent
    ADD COLUMN IF NOT EXISTS reference_price NUMERIC(30, 12);
