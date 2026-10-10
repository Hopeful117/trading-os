alter table trading_opportunity_versions
    add column if not exists account_id uuid;

alter table trading_opportunity_versions
    add column if not exists source_scan_id uuid;

alter table trading_opportunity_versions
    add column if not exists source_scan_market_id uuid;

alter table trading_opportunity_versions
    add column if not exists analysis_execution_id uuid;

create index if not exists ix_opportunity_versions_account
    on trading_opportunity_versions (account_id);
