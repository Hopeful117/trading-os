alter table broker_risk_snapshot
    add column completed_at timestamp with time zone,
    add column snapshot_payload text;
