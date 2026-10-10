alter table execution_intent_provenance
    add column if not exists account_id uuid;
alter table execution_intent_provenance
    add column if not exists source_scan_id uuid;
alter table execution_intent_provenance
    add column if not exists source_scan_market_id uuid;
alter table execution_intent_provenance
    add column if not exists analysis_execution_id uuid;
alter table execution_intent_provenance
    add column if not exists market_id uuid;

alter table trade_outcome_provenance
    add column if not exists account_id uuid;
alter table trade_outcome_provenance
    add column if not exists source_scan_id uuid;
alter table trade_outcome_provenance
    add column if not exists source_scan_market_id uuid;
alter table trade_outcome_provenance
    add column if not exists analysis_execution_id uuid;
alter table trade_outcome_provenance
    add column if not exists market_id uuid;
