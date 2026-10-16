CREATE TABLE feature_snapshots (
    snapshot_id UUID PRIMARY KEY,
    analysis_execution_id UUID NOT NULL,
    market_id UUID NOT NULL,
    evaluation_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    evidence_policy VARCHAR(40) NOT NULL,
    projection_version VARCHAR(80) NOT NULL,
    context_fingerprint VARCHAR(64) NOT NULL,
    snapshot_fingerprint VARCHAR(64) NOT NULL UNIQUE,
    identity_fingerprint VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    feature_payload TEXT NOT NULL,
    group_payload TEXT NOT NULL,
    provenance_payload TEXT NOT NULL
);

CREATE INDEX ix_feature_snapshots_market_cutoff
    ON feature_snapshots (market_id, evaluation_cutoff);

CREATE INDEX ix_feature_snapshots_projection_version
    ON feature_snapshots (projection_version);
