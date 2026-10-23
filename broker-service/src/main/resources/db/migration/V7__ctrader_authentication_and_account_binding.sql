create table ctrader_authorization_attempt (
    id uuid primary key,
    owner_id uuid not null,
    broker_account_id uuid not null,
    provider_registration_reference uuid not null,
    state_hash varchar(128) not null unique,
    environment varchar(16) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    token_credential_reference uuid,
    discovered_account_ids text not null,
    selected_external_account_id varchar(200),
    permission_scope varchar(64) not null,
    status varchar(40) not null,
    failure_code varchar(64),
    row_version bigint not null default 0
);

create index ix_ctrader_auth_attempt_owner_account
    on ctrader_authorization_attempt (owner_id, broker_account_id, created_at);
create index ix_ctrader_auth_attempt_expiry
    on ctrader_authorization_attempt (expires_at, status);
