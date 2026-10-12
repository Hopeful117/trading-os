alter table challenge_definition
    add column if not exists required_instrument_product_type varchar(32);
