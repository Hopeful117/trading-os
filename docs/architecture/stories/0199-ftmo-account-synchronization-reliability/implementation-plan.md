# Implementation Plan - Story 0199

## Operating Mode

This Story is reliability-first. No provider mutation, OAuth restart, account
creation, credential rotation or real order is authorized. The FTMO account
`49411967` is used only through the existing authenticated Trading OS path.

The uncommitted Story 0198 directory must remain intact. No Git integration
operation is part of this work.

## 1. Baseline and Runtime Trace

Record the current branch, HEAD, worktree state, effective cTrader environment,
`CTRADER_SAFETY_EXECUTION_ENABLED`, service health and the existing Story 0198
artifacts without reading or printing secrets.

Use the authenticated Angular application session, not a raw browser `fetch`, to
invoke the synchronization route. Capture only safe metadata:

- start and end timestamps;
- HTTP route and status;
- correlation ID;
- elapsed duration;
- last completed service stage;
- sanitized exception category/message;
- provider account identity match;
- snapshot completeness and freshness.

Limit real attempts to two sequential calls unless the first call proves a
provider safety cooldown or lease condition. Do not bypass persisted safety
controls.

## 2. Flow Instrumentation

If the baseline trace does not identify the stage, add structured duration logs
at these existing boundaries:

1. `AccountSynchronizationV2Controller` request start/end.
2. `BrokerSynchronizationServiceImpl` before and after the Feign snapshot call.
3. `GetAccountSnapshotV2Service` before and after provider capability execution.
4. `CTraderAuthoritativeSnapshotCapability` before and after the cTrader
   observation.
5. `JsonWebSocketCTraderOpenApiClient` per read-only request stage and aggregate
   observation completion/failure.

Logs must include a correlation ID, account-safe operation name and duration, but
never raw payloads, tokens, credentials or financial response dumps.

## 3. Timeout and Failure-Boundary Review

Verify the effective timeout behavior for:

- Gateway-to-Trading-Core HTTP handling;
- Trading Core Feign client;
- Broker Service provider snapshot operation;
- WebSocket connect, send, response wait and close;
- safety lease and cooldown behavior.

The existing cTrader request sequence must remain read-only. If the trace proves
that an operation can wait without a bound, add the smallest local timeout and
map the resulting failure to an explicit bounded application response. Do not
change financial completeness rules or turn incomplete data into success.

## 4. Minimal Correction

Implement only the defect demonstrated by the trace. Candidate corrections are
limited to:

- bounding a WebSocket connect/send/close operation;
- bounding or configuring the Trading Core Feign read timeout;
- propagating a provider timeout or partial response as an explicit HTTP error;
- adding narrowly scoped stage diagnostics.

Do not introduce a new asynchronous architecture, retry loop, fallback value,
equity derivation or synchronization policy change.

## 5. Tests

Add or adapt focused tests for the changed boundary:

- nominal snapshot response;
- provider response timeout;
- interrupted request;
- invalid or partial provider response;
- Feign/downstream failure propagation;
- persistence failure;
- repeated sequential synchronization without uncontrolled duplication;
- bounded completion assertions using generous deterministic margins.

Preserve tests proving identity matching, incomplete financial facts,
fail-closed risk behavior and disabled real execution.

## 6. Real Validation

After the correction and focused tests:

1. Confirm the existing FTMO account identity through the normal application.
2. Invoke the authenticated synchronization path once.
3. Record bounded result as `SYNC_SUCCESS`, `SYNC_FAILED_BOUNDED` or
   `SYNC_NOT_TESTED`.
4. Repeat once only if needed to establish reproducibility and safety.
5. Verify Trading Core and Angular state without substituting unavailable equity,
   margin or deals.
6. Confirm that no provider mutation or order request occurred.

If the snapshot remains incomplete, independent available facts may be reported,
but execution and risk admission must remain blocked under the existing contract.

## 7. Validation Commands

Run the applicable commands from each independent module:

```text
git diff --check
mvn test                         # broker-service, when affected
mvn test                         # trading-core, when affected
mvn test                         # gateway, when affected
npm run test:ci                  # trading-os-web, when affected
npm run build                    # trading-os-web, when affected
```

Use focused tests first for rapid feedback, then the broader affected-module
suite where runtime permits. Report timeouts separately from test failures.

## 8. Required Artifacts

Create only Story-local artifacts:

- `e2e-validation-matrix.md` with real trace, test evidence and result classes;
- `implementation-report.md` with root cause, execution trace, changes, tests,
  real FTMO result, safety regression, remaining blockers and one next action.

Documentation outcome: update only these Story-local artifacts. No architecture
or policy documentation should change unless the implementation changes a
documented contract, in which case stop and report the required decision.
