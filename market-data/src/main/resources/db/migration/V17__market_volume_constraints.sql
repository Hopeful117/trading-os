ALTER TABLE markets
    ADD COLUMN maximum_order_size NUMERIC(38, 2);

ALTER TABLE markets
    ADD COLUMN order_step_size NUMERIC(38, 2);
