CREATE UNIQUE INDEX uk_agent_operation_active_request
    ON agent_operation_request(owner_user_id, operation_type, resource_type, resource_id)
    WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTING');

CREATE INDEX idx_agent_operation_executing_recovery ON agent_operation_request(status, executed_at)
    WHERE status = 'EXECUTING';
