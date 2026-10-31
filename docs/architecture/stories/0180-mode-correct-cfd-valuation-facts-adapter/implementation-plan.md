# Implementation Plan

## Overview

Implement a read-only CFD valuation path that reconstructs the persisted
provider-neutral contract from `Trade`, obtains timestamped Market Data facts
without persisting a valuation snapshot, and delegates all monetary arithmetic to
`CfdFinancialCalculator`.

The plan keeps the existing generic valuation-snapshot endpoint unchanged for
its current callers. It extracts or reuses the fact-evaluation logic behind a
separate internal read-only boundary, then adds a Trading Core CFD orchestration
service and immutable result contract. Existing SPOT and
`LINEAR_PERPETUAL` consumers remain on their current paths.

No database migration, public API, Risk Domain change, settlement change or
Broker Service operation is planned.

## Design Decisions

### Read-only Market Data boundary

Create a reusable Market Data fact-evaluation component that reads catalogue and
observation repositories, applies the existing freshness and provenance rules,
and returns provider-neutral facts without calling
`ValuationSnapshotRepository`.

Keep `ValuationSnapshotService.create` as the persistence-backed adapter for
existing callers. It should use the reusable evaluator and persist only when its
existing snapshot API explicitly requests persistence.

Expose the read-only evaluator through a new internal endpoint rather than
changing the meaning of the existing snapshot endpoint. The endpoint is an
internal service boundary, not a public API or Gateway route.

### Explicit fact evidence

The read-only response must carry typed mark and conversion evidence sufficient
for Trading Core to construct the existing `CurrencyConversionFact` without
parsing provider payloads or relying on a local capture timestamp. At minimum it
must preserve:

- fact status;
- requested price-use policy;
- raw mark price in quote currency;
- source effective timestamp and captured timestamp;
- source identity/version or equivalent provider-neutral provenance;
- conversion source currency, target currency, rate and leg provenance;
- valuation policy and freshness metadata.

The response may use new provider-neutral DTOs or an additive extension of the
existing transport contract, but must not expose cTrader payloads or provider
types.

### Trading Core orchestration

Add a CFD-specific application service or port in Trading Core that:

1. accepts a persisted `Trade` and its account/mode context;
2. rejects non-CFD or incomplete persisted contract projections;
3. reconstructs `InstrumentFinancialContract` from the persisted snapshot;
4. selects `CONSERVATIVE_SELL` for positive quantity and
   `CONSERVATIVE_BUY` for negative quantity;
5. requests one provider-neutral mark and any required directed FX fact;
6. rejects incomplete, stale, invalid or directionally incompatible facts;
7. constructs `CurrencyConversionFact` when quote and reporting currencies differ;
8. calls `CfdFinancialCalculator.calculateGrossPnlQuote` and, when needed,
   `calculateGrossPnlReporting`;
9. returns an immutable result carrying financial values, policy, timestamps and
   provenance without mutating persistence.

For `PAPER`, the service uses the persisted Trading Core exposure and Market
Data marks only. For `LIVE`, it returns the established unavailable outcome
until current provider position authority is available through the later
reconciliation capability. It must not call Broker Service as a shortcut.

## Affected Modules

### `market-data`

Owns the read-only fact evaluator, internal response contract and endpoint. It
continues to own observation freshness, conversion-leg selection and provider
normalization.

### `trading-core`

Owns persisted contract reconstruction, execution-mode authority, CFD valuation
orchestration and the immutable valuation result. It reuses the shared pure
financial calculator.

### `risk-domain`

No production changes. Existing tests must remain green as a regression check.

## Planned File Changes

The exact names may be refined during implementation, but the expected changes
are:

### Market Data production

- `market-data/src/main/java/com/hope/trading/market_data/service/ValuationSnapshotService.java`
  - extract fact evaluation from persistence-specific orchestration;
  - preserve existing snapshot persistence behavior for the current endpoint.
- New provider-neutral read-only fact evaluator under
  `market-data/.../service/`.
- New read-only valuation-facts request/response DTOs under
  `market-data/.../dto/` if the existing snapshot DTO cannot represent the
  no-persistence contract cleanly.
- `market-data/src/main/java/com/hope/trading/market_data/controller/InternalValuationController.java`
  - add the internal read-only facts route.

### Trading Core production

- `trading-core/src/main/java/com/hope/trading/trading_core/market_data/valuation/MarketValuationPort.java`
  - add or adapt the smallest provider-neutral read-side contract while
    preserving existing callers and compatibility constructors.
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/MarketValuationClient.java`
  - call the read-only internal route for the CFD path and map typed evidence.
- `trading-core/src/main/java/com/hope/trading/trading_core/model/Trade.java`
  - add a validated read-side reconstruction method or delegate that creates an
    `InstrumentFinancialContract` from persisted fields without mutation.
- New CFD valuation application/domain classes under
  `trading-core/.../valuation/` or the established financial package for:
  - immutable valuation context/result;
  - unavailable/fail-closed outcome;
  - mode-aware CFD orchestration.

No new database entity, column or migration is expected.

## Implementation Sequence

### Step 1: Preserve and isolate Market Data fact evaluation

- Identify the fact-building portion of `ValuationSnapshotService`.
- Move or delegate repository reads, freshness checks, mark selection and
  conversion-leg construction to a reusable evaluator with no persistence side
  effect.
- Keep the existing snapshot service responsible for creating and saving
  `ValuationSnapshot` only for the existing snapshot endpoint.
- Add tests proving the evaluator does not invoke the snapshot repository.

### Step 2: Add the internal read-only transport boundary

- Define request fields for reporting currency, valuation timestamp, instrument
  identity, requested price-use policy, freshness requirement and required
  conversion assets.
- Define response fields for status, policy, freshness and typed source/conversion
  evidence.
- Add the internal endpoint using `ResponseEntity`, consistent with repository
  API conventions.
- Keep the existing `/internal/v1/valuation-snapshots/batch` behavior and
  serialization unchanged for existing callers.

### Step 3: Map read-only facts into Trading Core

- Extend or add a Market Data client method for read-only facts.
- Map the response to provider-neutral types only.
- Reject absent effective timestamps, absent provenance, non-available fact
  statuses, stale acquisition outcomes and incomplete conversion legs.
- Preserve mark policy and source evidence in the Trading Core result.
- Do not derive freshness from `capturedAt` when `effectiveAt` is absent or
  stale.

### Step 4: Reconstruct and value a persisted CFD exposure

- Reconstruct the contract from `Trade`'s persisted contract fields and reject
  incomplete snapshots.
- Determine signed quantity from the persisted trade quantity; do not infer
  direction from symbol text or provider payloads.
- Select conservative executable-side pricing from the sign of the quantity.
- Obtain the account reporting currency from the account's base currency.
- Build an explicit directed `CurrencyConversionFact` only when quote and
  reporting currencies differ.
- Invoke the existing calculator and return an immutable result containing quote
  PnL, reporting PnL when available, mark policy, timestamps and provenance.
- Keep the service read-only and avoid `Trade`, `Account`, `currentPrice`, cash,
  settlement and valuation-storage writes.

### Step 5: Enforce mode authority

- Add a PAPER test proving no Broker Service facts are requested.
- Add a LIVE test proving a local persisted Trade cannot be treated as current
  provider authority and returns the unavailable outcome.
- Do not introduce a temporary Broker Service call or a fallback to local
  `currentPrice` for LIVE.

### Step 6: Regression validation

- Run focused Market Data and Trading Core tests.
- Run existing CFD contract, persistence and calculator tests.
- Run existing SPOT and `LINEAR_PERPETUAL` valuation/risk tests.
- Run module-level compilation/tests and `git diff --check`.

## API Impact

- No public REST API or Gateway route changes.
- One new internal Market Data read-only endpoint is expected unless the
  existing internal contract can be safely extended without changing snapshot
  persistence semantics.
- Existing internal snapshot endpoint behavior remains backward compatible.
- Provider-specific payloads and cTrader identifiers remain private to adapters.

## Database Impact

- No schema change.
- No migration.
- No new valuation row for the read-only CFD path.
- Existing snapshot persistence remains available only to existing callers that
  explicitly use that path.

## Test Plan

### Market Data tests

- read-only evaluator returns fresh conservative SELL and BUY marks;
- missing, stale, invalid and incomplete observations fail closed;
- source effective/captured timestamps and provenance are preserved;
- identity conversion is explicit and cross-currency conversion includes directed
  legs and provenance;
- read-only evaluation does not call `ValuationSnapshotRepository`;
- existing snapshot endpoint still persists and returns the same behavior.

### Trading Core tests

- persisted CFD contract reconstructs after repository reload;
- long EUR/USD `+1000`, `1.10000 -> 1.10100` yields `+1 USD`;
- short EUR/USD `-1000`, `1.10000 -> 1.09900` yields `+1 USD`;
- long and short losses retain the negative sign;
- conservative side selection follows signed quantity;
- same-currency conversion uses identity;
- cross-currency conversion requires and applies the correct directed fact;
- missing, stale, invalid and wrong-direction facts return unavailable;
- non-CFD projections are rejected;
- PAPER path does not call Broker Service or mutate persistence;
- LIVE path fails closed without provider-authoritative exposure;
- result retains mark and FX evidence and policy metadata.

### Regression tests

- existing `CfdFinancialCalculatorTest`;
- Story 0178 contract tests;
- Story 0179 persistence and migration tests;
- `MarketValuationClientTest`;
- `ValuationSnapshotServiceTest`;
- existing SPOT and `LINEAR_PERPETUAL` valuation/risk suites.

## Validation Commands

Use the repository's independent service builds, with focused tests first. The
implementation report must record the exact commands and outcomes, including
any bounded timeout as inconclusive rather than successful.

Expected checks:

```text
mvn -q -pl market-data -Dtest=<focused-market-data-tests> test
mvn -q -pl trading-core -Dtest=<focused-trading-core-tests> test
git diff --check
```

Run broader affected-module validation when the focused checks pass and local
resources permit it. Do not perform authenticated cTrader execution, broker
mutation, controlled trading or settlement mutation.

## Risks and Mitigations

- Snapshot persistence could accidentally remain on the new path. Mitigate by
  separating fact evaluation from persistence and asserting repository
  interaction counts in tests.
- The generic valuation contract could lose typed source timestamps or conversion
  direction. Mitigate with explicit provider-neutral evidence fields and negative
  tests for missing/wrong-direction facts.
- Existing valuation callers could regress. Mitigate by keeping the old snapshot
  endpoint behavior and running existing Market Data, Risk and calculator tests.
- LIVE local state could be mistaken for provider authority. Mitigate with an
  explicit mode gate before fact assembly and a LIVE unavailable test.
- An implementation could broaden scope into CFD settlement or Risk changes.
  Mitigate by limiting changes to read-side facts and calculator orchestration;
  stop if a migration or compatibility change appears necessary.

## Plan Readiness

The plan is implementation-ready within the approved Story boundary. It does
not authorize implementation. Human approval of this exact Implementation Plan
is required before any production code is modified.
