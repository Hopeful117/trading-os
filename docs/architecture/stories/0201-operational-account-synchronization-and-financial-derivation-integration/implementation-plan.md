# Implementation Plan - Story 0201

## Operating Mode

This Story remains READ-ONLY with respect to cTrader. Only the existing FTMO
account `49411967` may be used for controlled synchronization. No order,
OAuth restart, credential rotation, account creation or provider mutation is
authorized. `CTRADER_SAFETY_EXECUTION_ENABLED` remains `false`.

Preserve all uncommitted changes from Stories 0198-0200. No commit, push, merge,
reset, clean, rebase or branch cleanup is part of implementation.

## Design Decisions

1. Keep the Story 0200 synchronization result and V52 snapshot metadata as the
   operational source of account synchronization state.
2. Add only provider-neutral projection fields needed by the normal Account API
   and Angular account workflow. Existing legacy V1 synchronization remains
   compatible and is not silently relabeled as cTrader V2 synchronization.
3. Reuse `CTraderDerivedEquityExperiment` at the Broker Service observation
   boundary, but transport its result as a separate diagnostic financial fact.
   It must not populate the authoritative `Account.equity` or
   `BrokerAccountFactsV2Dto.equityFact` fields.
4. Preserve the candidate's `BROKER_DERIVED` provenance, calculation version,
   consistency/validation status, source timestamps and diagnostics. The
   existing FTMO policy continues to reject it for equity-dependent risk.
5. Do not implement a used-margin formula. Do not infer zero used margin from an
   empty position collection. Free margin remains unavailable unless its
   dependencies are independently admissible.
6. Use the existing risk and execution boundaries for Trade Intent, T0, T1 and
   required proposed-order margin. No bypass or policy change is allowed.

If the existing neutral snapshot contract cannot carry the diagnostic fact
without exposing cTrader-specific types, stop before widening it and report the
contract conflict rather than leaking provider payloads.

## 1. Operational Snapshot Projection

### Broker Service

- Invoke `CTraderDerivedEquityExperiment.evaluate(...)` from the existing
  cTrader snapshot mapping path using the observation already collected for the
  authoritative snapshot.
- Map only a provider-neutral optional diagnostic record into the snapshot,
  with value, currency, provenance, calculation version, hypothesis,
  consistency status, validation status, source timestamps and bounded reasons.
- Keep the authoritative account equity fact unchanged and unavailable for the
  current cTrader observation.
- Do not add a new provider request, endpoint, cTrader transport type or
  execution path.
- Add mapper/contract tests proving that an experimental candidate is present
  only as diagnostic data and cannot become authoritative equity.

### Trading Core

- Mirror the optional diagnostic fact in the existing provider-neutral snapshot
  DTO and synchronization result only where required by the normal account
  projection.
- Extend the `Account` projection with nullable provider-neutral diagnostic
  fields only if the existing V52 metadata cannot expose the candidate through
  the normal account API. Use an additive Flyway migration with no raw provider
  payloads or credentials.
- Extend `AccountDto` and `AccountMapper` with:
  - synchronization status/completeness/freshness;
  - per-collection availability;
  - bounded unavailability reasons;
  - diagnostic derived-fact value and provenance metadata;
  - nullable financial values rather than numeric fallbacks.
- Keep the V2 structured result `diagnosticOnly=true` and make the account
  projection explicitly distinguish `ACCOUNT_SYNCHRONIZED`,
  `FINANCIAL_DATA_PARTIAL`, `RISK_EVIDENCE_INCOMPLETE` and
  `TRADING_NOT_AUTHORIZED` using existing status representations where
  possible. Do not create redundant enums if strings/records already provide
  the same contract.
- Preserve owner, provider, external identity, freshness and transaction checks.
- Preserve idempotence and unavailable-versus-empty semantics.

## 2. Normal Account API and Angular Integration

### Backend API

- Keep `ResponseEntity` on all affected controllers.
- Add or adapt the authenticated account-scoped synchronization call so the
  existing FTMO account can use the V2 structured result from the normal
  account workflow.
- Preserve `/api/v1/accounts/synchronize` for its existing legacy behavior.
- Ensure account listing/detail endpoints expose partial synchronization and
  missing financial capability metadata without raw provider payloads.

### Angular

- Change the `Account` model's financial fields that may be unavailable to
  nullable values and add typed synchronization/diagnostic metadata.
- Extend `AccountService` with a typed V2 synchronization method; keep the
  legacy method only for callers that still use it.
- Update the Accounts page/account card with focused additions only:
  - provider/account/currency/balance;
  - positions/orders availability;
  - partial synchronization status;
  - unavailable equity/margin/deals as `Unavailable`, never `0`;
  - derived equity candidate as explicitly unverified/non-authoritative;
  - diagnostic-only and not-trading-authorized messaging.
- Use Observables and `async` pipe for account state; do not add broad UI
  refactoring or manual polling.
- Add a synchronization action for the existing account-scoped V2 path, with
  explicit success/error feedback and no claim of execution readiness.
- Do not make the existing LIVE FTMO account appear in the current PAPER-only
  manual ticket filter unless the existing trade-flow contract explicitly
  supports it. The trade readiness result will be shown via existing risk
  surfaces or a focused status view instead.

## 3. Financial Derivation Integration

### Equity Candidate

- Reuse the existing `CTraderDerivedEquityExperiment`; do not create another
  calculator.
- Preserve the formula:

  ```text
  candidateEquity = balance + sum(netUnrealizedPnL)
  ```

- Require the experiment's existing complete identity, collection, temporal,
  precision and balance-version checks.
- Preserve `CTRADER_DERIVED_EQUITY_V1`,
  `BALANCE_PLUS_NET_UNREALIZED_PNL_V1` and `UNVERIFIED` validation.
- Do not insert the candidate into authoritative account equity, risk facts,
  T0, T1, FTMO policy admission or execution.
- For the current empty-position observation, the candidate may be represented
  only if the experiment itself reports a consistent complete empty position/PnL
  observation. Its presence must not alter the current unavailable authoritative
  equity state.

### Used and Free Margin

- Reuse existing `RequiredMarginPort` for proposed-order required margin.
- Do not derive current account used margin from positions, leverage, notional or
  an empty position set.
- Do not calculate free margin while authoritative/admissible equity and used
  margin are unavailable.
- Add tests proving non-calculability remains explicit and fail-closed.

### Deals

- Preserve the V52 deal availability state.
- Do not synthesize deals or reconstruct closed PnL from incomplete history.
- Keep unrelated account display/synchronization available when deals are
  unavailable.

## 4. Trade Intent, T0, T1 and Readiness Validation

- Reuse the existing Trading Core Trade Plan/Trade Intent path and the FTMO
  account mapping; do not create a provider order.
- Select a currently available provider-neutral CFD symbol and compatible plan
  parameters from existing market/contract APIs or test fixtures.
- Execute the existing validation sequence through the available API/services:
  account identity, symbol/contract, quantity, stop-loss, risk, T0, T1,
  approvals and execution readiness.
- Capture each deterministic outcome and its first blocking rule.
- Expected behavior is fail-closed if mandatory equity, deal or required-margin
  evidence is unavailable or inadmissible. Do not change the policy to force a
  pass.
- Verify that any partial invariant diagnostics remain non-authorizing and that
  no broker mutation endpoint is called.

## 5. Tests

### Backend Synchronization and Projection

- Full and partial V2 synchronization.
- Existing account and repeated synchronization idempotence.
- Available-empty versus unavailable collections.
- Persistence and DTO mapping of V52 metadata.
- Diagnostic derived fact mapping without authoritative equity mutation.
- Invalid provider response, identity mismatch, stale snapshot, timeout and
  transaction rollback.

### Derivations

- Reuse the existing Story 0197 experiment tests and add the integration
  boundary test.
- Candidate equity positive/negative/empty PnL and precision preservation.
- Incomplete PnL, mismatched identity, stale or incoherent observation.
- Candidate provenance and FTMO inadmissibility.
- Used margin non-calculable without approved source.
- Free margin unavailable when dependencies are unavailable.

### Risk

- Partial synchronization remains diagnostic-only.
- Equity-dependent FTMO evidence remains incomplete for `BROKER_DERIVED`.
- T0 and T1 use the existing evidence requirements and fail closed.
- Missing approval and missing required margin block readiness.
- No test submits or simulates a provider mutation.

### Angular

- Account status renders available balance and unavailable financial facts
  correctly.
- Partial synchronization and diagnostics are visible.
- Null/unknown financial values never render as `0`.
- Candidate derived equity is visibly non-authoritative.
- Sync success does not render execution readiness.
- Trade readiness/blocker response is displayed without bypassing risk.

## 6. Controlled Runtime Validation

After implementation and focused tests:

1. Verify Eureka, Trading Core, Broker Service and Gateway health.
2. Confirm the existing authenticated account mapping without inspecting or
   printing credentials.
3. Confirm `CTRADER_SAFETY_EXECUTION_ENABLED=false`.
4. Invoke the account-scoped operational synchronization once for broker account
   `7913f82b-3b06-4ab3-bb0e-bf2bdc7d3456`.
5. Record HTTP status, bounded duration, synchronization status, persisted
   capabilities, financial provenance and unavailability reasons.
6. Verify PostgreSQL persistence and Account API projection.
7. Verify Angular display if the application is available.
8. Run the representative Trade Intent through T0/T1/readiness without submit.
9. Verify no cTrader order/mutation call occurred.

Classify runtime results as `SYNCED_COMPLETE`, `SYNCED_PARTIAL` or bounded
failure, and separately record risk/readiness status. Never equate sync success
with execution readiness.

## 7. Validation Commands

```text
git diff --check

# Trading Core focused synchronization, projection and risk tests
mvn -Dtest=BrokerSynchronizationServiceImplTest,AccountSynchronizationV2ControllerTest,BrokerSynchronizationPersistenceIntegrationTest test

# Broker Service derivation and snapshot contract tests
mvn -Dtest=CTraderDerivedEquityExperimentTest,CTraderAuthoritativeSnapshotCapabilityTest,JsonWebSocketCTraderOpenApiClientTest test

# Trading Core module suite
mvn test

# Angular affected tests and build
npm run test:ci
npm run build
```

Retry the Trading Core PostgreSQL integration suite in the available test
environment. Report connection timeouts separately from test failures. SonarQube
is applicable only if the repository's configured scan can run; no missing
configuration or external finding should be silently treated as a pass.

## 8. Documentation Reconciliation

Update only Story-local artifacts during implementation:

- `implementation-report.md`;
- `e2e-validation-matrix.md`;
- a focused financial derivation/operational status note only if required by the
  final contract.

No ADR update is planned. If the diagnostic derived-fact transport would require
changing ADR-084/085 semantics or admitting `BROKER_DERIVED` for FTMO, stop and
request a new architecture decision rather than expanding this Story.

## 9. Expected Files

Likely implementation surfaces, subject to verification:

- `broker-service/.../ctrader/synchronization/CTraderAuthoritativeSnapshotCapability.java`;
- provider-neutral broker snapshot model/DTO classes and focused tests;
- `trading-core/.../broker/dto/` snapshot/result DTOs;
- `trading-core/.../model/Account.java` and mapper/DTOs;
- additive Trading Core migration only if diagnostic projection persistence is
  required;
- account controller/service and synchronization tests;
- risk/readiness integration tests and existing Trade Intent fixtures;
- `trading-os-web` account model/service/page/card and focused tests;
- Story-local implementation and E2E reports.

No new microservice, provider mutation adapter, FTMO policy change or broad UI
refactor is planned.

## Approval Boundary

Implementation must not begin until this Implementation Plan is explicitly
approved by the human engineer.
