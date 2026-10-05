alter table trading_opportunity_versions
    add column if not exists market_id uuid;
