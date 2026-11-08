alter table research_scope add column if not exists historical_earliest_cutoff timestamp with time zone;
alter table research_scope add column if not exists historical_latest_cutoff timestamp with time zone;
alter table research_scope add column if not exists historical_max_observations bigint;
alter table research_scope add column if not exists historical_max_export_rows bigint;
alter table research_scope add column if not exists historical_max_export_bytes bigint;
alter table research_scope add column if not exists historical_max_duration_seconds bigint;

create table if not exists research_scope_dataset_profile (
    scope_id uuid not null references research_scope(id) on delete cascade,
    dataset_profile varchar(128) not null,
    primary key (scope_id, dataset_profile)
);
