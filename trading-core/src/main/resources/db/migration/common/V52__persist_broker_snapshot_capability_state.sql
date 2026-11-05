ALTER TABLE accounts ADD COLUMN broker_snapshot_completeness VARCHAR(32);
ALTER TABLE accounts ADD COLUMN broker_snapshot_freshness VARCHAR(32);
ALTER TABLE accounts ADD COLUMN broker_snapshot_position_availability VARCHAR(32);
ALTER TABLE accounts ADD COLUMN broker_snapshot_order_availability VARCHAR(32);
ALTER TABLE accounts ADD COLUMN broker_snapshot_pnl_availability VARCHAR(32);
ALTER TABLE accounts ADD COLUMN broker_snapshot_deal_availability VARCHAR(32);
ALTER TABLE accounts ADD COLUMN broker_snapshot_observed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN broker_snapshot_completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN broker_snapshot_unavailability_reasons VARCHAR(4000);
