create table opportunity_decisions (
    id uuid primary key,
    actor_id uuid not null,
    account_id uuid not null,
    opportunity_id uuid not null,
    opportunity_version bigint not null,
    thesis_id uuid not null,
    thesis_version bigint not null,
    decision varchar(32) not null,
    rationale varchar(2000),
    idempotency_key varchar(200) not null,
    created_at timestamp with time zone not null,
    constraint uq_opportunity_decision_idempotency unique (actor_id, idempotency_key),
    constraint uq_opportunity_decision_identity unique
        (account_id, opportunity_id, opportunity_version, decision)
);

create index ix_opportunity_decision_account
    on opportunity_decisions (account_id, created_at);
