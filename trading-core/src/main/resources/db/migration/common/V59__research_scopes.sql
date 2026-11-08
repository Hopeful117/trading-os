create table if not exists research_scope (
    id uuid primary key,
    owner_user_id uuid not null,
    status varchar(16) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    scope_version bigint not null default 0,
    constraint ck_research_scope_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

create table if not exists research_scope_market (
    scope_id uuid not null references research_scope(id) on delete cascade,
    market_id uuid not null,
    primary key (scope_id, market_id)
);

create index if not exists ix_research_scope_owner on research_scope(owner_user_id, created_at desc);
create index if not exists ix_research_scope_market on research_scope_market(market_id);
