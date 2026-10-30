ALTER TABLE position_close_command
    ADD COLUMN close_quantity NUMERIC(30, 12);

ALTER TABLE position_close_command
    ADD COLUMN remaining_quantity NUMERIC(30, 12);
