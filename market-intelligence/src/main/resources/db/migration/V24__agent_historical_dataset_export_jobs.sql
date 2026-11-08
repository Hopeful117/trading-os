create table if not exists historical_dataset_export_job (
    id uuid primary key,
    owner_user_id uuid not null,
    agent_id uuid not null,
    scope_id uuid not null,
    grant_id uuid not null,
    grant_version bigint not null,
    credential_id uuid not null,
    credential_version integer not null,
    idempotency_key varchar(200) not null,
    request_fingerprint varchar(128) not null,
    request_json text not null,
    status varchar(16) not null,
    artifact_id varchar(128),
    artifact_checksum varchar(128),
    artifact_bytes bigint,
    sample_count integer,
    failure_code varchar(64),
    correlation_id varchar(128),
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    expires_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint ck_historical_export_job_status check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    constraint uq_historical_export_agent_key unique (agent_id, idempotency_key)
);

create index if not exists ix_historical_export_agent on historical_dataset_export_job(agent_id, created_at desc);
