create table broker_revocation_notification (
    id uuid primary key,
    broker_account_id uuid not null unique,
    owner_id uuid not null,
    target_status varchar(40) not null,
    delivery_status varchar(20) not null,
    attempt_count integer not null default 0,
    retry_exhausted boolean not null default false,
    next_attempt_at timestamp with time zone not null,
    last_attempt_at timestamp with time zone,
    lease_until timestamp with time zone,
    last_error_code varchar(120),
    external_account_id varchar(200),
    revoked_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null default 0,
    constraint ck_broker_revocation_target check (target_status = 'REVOKED'),
    constraint ck_broker_revocation_delivery_status
        check (delivery_status in ('PENDING', 'DELIVERED', 'FAILED'))
);

create index ix_broker_revocation_due
    on broker_revocation_notification (delivery_status, next_attempt_at);
