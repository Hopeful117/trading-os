ALTER TABLE agent_grant DROP CONSTRAINT ck_agent_grant_permission;

ALTER TABLE agent_grant ADD CONSTRAINT ck_agent_grant_permission CHECK (permission IN (
    'accounts:read', 'accounts:sync', 'positions:read', 'markets:read',
    'risk:read', 'research:run', 'research:inspect', 'research:export',
    'plans:create', 'trade-plan-risk:evaluate',
    'trade-plan-approval:request', 'trade-plan-approval:status:read',
    'scans:run', 'executions:request', 'executions:continue-approved',
    'executions:status:read'
));
