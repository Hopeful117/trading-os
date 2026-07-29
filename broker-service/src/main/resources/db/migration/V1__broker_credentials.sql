create table broker_secret (
    id uuid primary key,
    broker_account_id uuid not null,
    provider varchar(40) not null,
    ciphertext text not null,
    initialization_vector varchar(64) not null,
    algorithm varchar(40) not null,
    key_version varchar(40) not null,
    secret_version bigint not null,
    status varchar(20) not null,
    api_key_hint varchar(32),
    created_at timestamp with time zone not null,
    activated_at timestamp with time zone,
    revoked_at timestamp with time zone,
    replaced_by_id uuid,
    row_version bigint not null default 0,
    constraint uq_broker_secret_version unique (broker_account_id, secret_version),
    constraint fk_broker_secret_replacement foreign key (replaced_by_id) references broker_secret(id)
);

create unique index uq_broker_secret_active
    on broker_secret (broker_account_id)
    where status = 'ACTIVE';
create index ix_broker_secret_account_status
    on broker_secret (broker_account_id, status);

create table broker_connection (
    id uuid primary key,
    broker_account_id uuid not null unique,
    owner_id uuid not null,
    provider varchar(40) not null,
    active_credential_reference uuid,
    technical_status varchar(40) not null,
    detected_permissions varchar(500) not null,
    external_account_id varchar(200),
    api_key_hint varchar(32),
    last_validated_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    row_version bigint not null default 0,
    constraint fk_connection_active_secret foreign key (active_credential_reference) references broker_secret(id)
);

create index ix_broker_connection_owner on broker_connection (owner_id);
create index ix_broker_connection_provider_status on broker_connection (provider, technical_status);
