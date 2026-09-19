# Implementation Plan - Story 0052

## Plan Status

`PROPOSED - HUMAN APPROVAL REQUIRED`

## Phase 1 - Preserve the mode boundary

Document and test that:

```text
PAPER -> local Trading Core mutation and settlement
LIVE  -> Broker Service technical execution
```

Do not route PAPER through Broker Service mutation endpoints.

## Phase 2 - Bind execution to approved context

Ensure the execution intent stores or references:

```text
tradePlanId
tradePlanVersion
riskEvaluationId
accountId
capabilityVersion
idempotencyKey
```

Before submission, perform full risk revalidation and compare account,
portfolio, profile, and capability versions.

## Phase 3 - Preserve explicit lifecycle states

Use neutral states for shared concepts, while keeping mode-specific completion
semantics:

```text
VALIDATING
SUBMITTED
ACKNOWLEDGED
FILLED
PARTIALLY_FILLED
REJECTED
UNKNOWN
RECONCILIATION_REQUIRED
```

PAPER local transaction failure must use rollback or explicit recovery, not fake
provider reconciliation.

## Phase 4 - Full PAPER exit

Retain ADR-042 semantics:

* human-authorized `ClosePosition`;
* local target resolution;
* full close only;
* no exposure reversal;
* local opposite-side simulated fill;
* atomic local settlement;
* reloadable closed state.

## Phase 5 - Tests and frontend

Add backend tests for idempotency, version conflicts, local settlement atomicity,
and LIVE unknown reconciliation. Update frontend models only for states that
must be shown to the user.

Do not introduce WebSocket/SSE in this Story.

## Validation Commands

```text
cd trading-core && mvn test
cd broker-service && mvn test
cd trading-os-web && npm run test:ci
cd trading-os-web && npm run build
git diff --check
```

## Exit Criteria

* PAPER authority remains local;
* LIVE authority remains broker-backed;
* shared lifecycle concepts are explicit;
* unknown outcomes are safe;
* local PAPER settlement is atomic or recoverable;
* tests and frontend validation pass;
* human approval is obtained before implementation.
