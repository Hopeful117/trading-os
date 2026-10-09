# Implementation Plan - Story 0052

## Plan Status

`APPROVED - HUMAN APPROVED 2026-10-08`

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

## Focused Runtime Blocker Remediation

The current local PAPER validation reached an approved risk decision and created
an `ExecutionIntent`, but execution stopped before submission with
`RISK_REVALIDATION_UNAVAILABLE`. This remediation narrows the next
implementation slice to execution-time T1 revalidation.

### Phase 6 - Reproduce and classify the T1 failure

1. Add a deterministic service-level test for a complete PAPER T1 approval
   path, including account facts, Market Data valuation, required margin,
   ready Trade Plan, and persisted T1 outcome.
2. Add assertions for each unavailable dependency so the resulting reason code
   identifies the failing boundary rather than only exposing the aggregate
   `RISK_REVALIDATION_UNAVAILABLE` state.
3. Preserve fail-closed behavior: an unavailable dependency must not submit an
   order.

### Phase 7 - Correct the minimal dependency boundary

1. Fix only the dependency proven unavailable by the regression test among
   PAPER risk facts, Market Data valuation, required margin, and execution-ready
   Trade Plan loading.
2. Preserve the existing `RequiredMarginPort` PAPER behavior, local PAPER
   settlement authority, risk version binding, and explicit human execution
   action.
3. Do not weaken risk rules, replace unavailable facts with fabricated values,
   or route PAPER execution through Broker Service.

### Phase 8 - Protect the browser retry path

1. Add a frontend regression test for a T1-unavailable execution response.
2. Ensure the UI does not call `/executions/validate` a second time for the
   same approved plan and idempotency boundary when the user is retrying T1.
3. Keep T1 retry on `/executions/{id}/retry-t1` and preserve idempotency errors
   as visible actionable failures.

### Phase 9 - Validate the controlled PAPER journey

1. Run the focused Trading Core tests and affected Angular tests.
2. Run the Trading Core, Broker Service, and frontend quality checks required
   by the repository.
3. Repeat the authenticated local PAPER journey through the Gateway with a
   low-exposure plan.
4. Verify `APPROVED` T1, PAPER execution completion, persisted position after
   reload, and the existing human-authorized close path where applicable.
5. Record the exact environment, execution identifiers, observed states, and
   any remaining blocker without using LIVE credentials or Kraken private APIs.
