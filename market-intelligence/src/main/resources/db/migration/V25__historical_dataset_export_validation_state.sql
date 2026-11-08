alter table historical_dataset_export_job
    add column if not exists contract_validation_status varchar(16) not null default 'PENDING';

alter table historical_dataset_export_job
    add column if not exists experiment_eligibility_status varchar(16) not null default 'NOT_EVALUATED';
