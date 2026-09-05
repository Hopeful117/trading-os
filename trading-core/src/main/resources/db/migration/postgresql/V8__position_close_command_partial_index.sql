-- V8__position_close_command_partial_index.sql
CREATE UNIQUE INDEX uq_active_command_per_scope
    ON position_close_command (broker_account_id, resolved_mutation_scope)
    WHERE status IN ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'UNKNOWN');