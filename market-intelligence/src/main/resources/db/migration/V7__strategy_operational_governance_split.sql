-- ADR-036: split strategy governance into two explicit dimensions.
-- The former lifecycle_status mixed maturity and operational activation;
-- maturity truth already lives in validation_status, so the renamed
-- column now stores the pure operational status (ADR-036).
-- Value mapping of legacy single-lifecycle states:
--   DRAFT, CANDIDATE, VALIDATED -> DISABLED (never live-activated)
--   ENABLED                     -> ENABLED
--   RETIRED                     -> RETIRED

ALTER TABLE strategy_definitions RENAME COLUMN lifecycle_status TO operational_status;
ALTER TABLE strategy_definitions ALTER COLUMN operational_status TYPE VARCHAR(30);

UPDATE strategy_definitions SET operational_status = 'DISABLED'
WHERE operational_status IN ('DRAFT', 'CANDIDATE', 'VALIDATED');
