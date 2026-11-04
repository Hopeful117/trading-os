# Implementation Plan - Story 0197

## Overview

Add a deterministic, read-only experimental calculation boundary inside Broker
Service. The boundary will consume existing cTrader observation facts, calculate
the first candidate hypothesis, preserve its evidence and uncertainty, and
return an explicitly experimental result.

The result will not enter `BrokerAccountSnapshotV2`, `RiskFactsProvider`, T0,
T1, `EquityFact`, policy admission or execution. No cTrader authenticated
request path will be added or changed by this Story.

No FTMO policy, risk threshold, execution flag, ADR or provider authority rule
will change.

## Design

### 1. Add an isolated experimental package

Create a package under:

```text
broker-service/src/main/java/com/hope/trading/broker_service/ctrader/experimentation/
```

Create a small immutable result model and calculator, tentatively:

```text
CTraderDerivedEquityExperiment
DerivedEquityExperimentResult
DerivedEquityConsistencyStatus
DerivedEquityValidationStatus
DerivedEquityHypothesis
```

The final names must follow repository conventions and remain internal to
Broker Service. No controller or public API is added.

The result will contain:

- bound external account identity;
- valuation currency;
- candidate value, balance and total net unrealized PnL;
- included position identifiers;
- source timestamps and collection window;
- calculation version and hypothesis;
- consistency status;
- validation status;
- explicit diagnostics.

`validationStatus` will remain `UNVERIFIED` for every result produced by this
Story. Numeric calculation success must not imply financial validation.

### 2. Implement the first calculation hypothesis

Implement only:

```text
candidateEquity = balance + sum(netUnrealizedPnL)
```

The calculator will:

- use `BigDecimal` only;
- require a non-empty account identity and valuation currency;
- require a present balance;
- require complete position and PnL collection states;
- require exactly one PnL entry per observed position;
- reject duplicate position or PnL identities;
- reject unknown PnL positions;
- reject missing or non-interpretable net PnL;
- preserve each source PnL monetary precision without silently rounding;
- preserve the observation timestamps and calculation version;
- classify swap, commission and mirroring commission as `NOT_INCLUDED_UNVERIFIED`
  rather than adding them.

No alternative cost formula will be calculated until the official semantics
needed to avoid double counting are established. The result will explicitly
state that the hypothesis is not FTMO validated.

### 3. Preserve the minimum missing metadata

The existing observation already preserves:

- external account identity;
- valuation asset;
- balance;
- position identities and update timestamps;
- PnL position identities and monetary scale;
- observation start, provider-observed and completion timestamps;
- collection availability and completeness.

The cTrader `ProtoOATrader.balanceVersion` is needed for experimental
coherence checks but is not currently preserved. Add it only to the internal
provider observation account metadata, using an optional provider field and
compatibility constructors. Map it in `mapObservation(...)`.

Do not add `balanceVersion` to public Trading Core DTOs or the normalized risk
snapshot in this Story.

The PnL response has no provider mark timestamp. The experiment must preserve
that limitation and classify temporal validation as `UNVERIFIED` rather than
inventing one.

### 4. Add temporal consistency checks

Add a pure comparison operation for two observations of the same external
account. It will compare:

- account identity and valuation currency;
- balance version when present;
- balance value;
- position identity set;
- position volume, entry price and provider update timestamp;
- PnL identity set and values;
- collection availability and completeness;
- observation age and collection-window duration.

The operation will classify:

```text
CONSISTENT_WITH_OBSERVATIONS
INCOMPLETE
INCOHERENT
STALE
UNVERIFIED
```

`CONSISTENT_WITH_OBSERVATIONS` means only that the compared observations did
not expose a detected difference. It does not mean atomicity or financial
validation.

The calculator will reject or downgrade the result when:

- the observation is incomplete;
- the account identity changes;
- the position set changes;
- the PnL coverage changes;
- balance version changes;
- required timestamps are absent;
- the configured maximum age or collection window is exceeded.

No live double-read orchestration is added. The comparison remains a pure
offline/testable operation for future controlled collection work.

### 5. Keep production risk boundaries unchanged

Do not modify:

- `CTraderAuthoritativeSnapshotCapability` equity provenance mapping;
- `BrokerAccountSnapshotV2` or its Trading Core DTO;
- `RiskFactsProvider` or `EquityFact`;
- `RiskFactRequirements`;
- `InvariantEvidenceAdmission`;
- `RiskResultAggregator`;
- T0 or T1 services;
- cTrader execution capability;
- `CTRADER_SAFETY_EXECUTION_ENABLED`.

The safety proof will include a compile-time/package boundary review and tests
that exercise only the experimental calculator and existing normalized snapshot
behavior.

### 6. Add deterministic tests

Create focused tests under the existing Broker Service cTrader test packages.

Required cases:

- empty account with zero total PnL;
- one positive PnL position;
- one negative PnL position;
- multiple positions with positive and negative PnL;
- monetary precision preservation;
- wrong valuation currency or missing currency;
- missing balance;
- missing position PnL;
- PnL for an unknown position;
- duplicate position identity;
- duplicate PnL identity;
- incomplete position, PnL or deal collection;
- balance version change between observations;
- changed position set;
- changed position facts;
- stale observation;
- excessive collection window;
- missing timestamps;
- explicit diagnostics for excluded commission, swap and mirroring commission;
- experimental result cannot construct or alter `RiskFactsProvider.EquityFact`;
- existing cTrader normalized snapshot keeps
  `EQUITY_PROVIDER_SOURCE_UNVALIDATED`.

Update the existing JSON mapper test only for balance-version preservation and
the existing snapshot test only where necessary to prove unchanged fail-closed
behavior.

No test uses credentials, a real token, a socket, a broker endpoint or an order
operation.

### 7. Add Story-local documentation

Create the following canonical Story-local documents:

- `financial-data-matrix.md`: source, unit, currency, timestamps and guarantees.
- `accounting-hypotheses.md`: included/excluded terms and double-counting risks.
- `independent-validation-protocol.md`: future comparison procedure without
  claiming any observed reference value.

The implementation report will record the final test commands, safety outcome,
remaining financial gaps and documentation reconciliation. No ADR is created
or amended by this Story.

## Files to Modify

- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderReadOnlyObservation.java`
  - Preserve optional provider `balanceVersion` in internal account facts.
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClient.java`
  - Map the documented `balanceVersion` field only; do not change account
    equity semantics or add a new provider request.
- `broker-service/src/test/java/com/hope/trading/broker_service/ctrader/authentication/JsonWebSocketCTraderOpenApiClientTest.java`
  - Cover balance-version mapping without promoting fixture equity.
- `broker-service/src/test/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderAuthoritativeSnapshotCapabilityTest.java`
  - Preserve explicit unavailable equity behavior if test coverage needs a
    focused assertion.

## Files to Create

- Experimental calculator/result classes under
  `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/experimentation/`.
- Focused calculator and temporal-consistency tests under
  `broker-service/src/test/java/com/hope/trading/broker_service/ctrader/experimentation/`.
- Story-local `financial-data-matrix.md`.
- Story-local `accounting-hypotheses.md`.
- Story-local `independent-validation-protocol.md`.
- Story-local `implementation-report.md` after implementation and validation.

No public controller, database migration, Trading Core contract or Risk Domain
file is planned.

## Validation Plan

Run from `broker-service`:

```text
mvn -q -Dtest=CTraderDerivedEquityExperimentTest,JsonWebSocketCTraderOpenApiClientTest,CTraderAuthoritativeSnapshotCapabilityTest test
mvn test
```

Also run from the repository root:

```text
git diff --check
```

If the full Broker Service suite is blocked by pre-existing worktree changes or
environment dependencies, report the exact failure and retain the focused test
result. No credentials or broker calls are permitted.

Quality validation must report affected modules, executed checks, blocked
checks and whether SonarQube was applicable/configured. No SonarQube finding
outside Story scope may be fixed by this Story.

## Documentation Reconciliation

The prototype adds a new experimental capability and therefore requires the
three Story-local accounting/validation documents and an implementation report.
No global architecture README, ADR, public API contract or operational
configuration should be changed.

## Risks and Stop Conditions

- If adding `balanceVersion` requires changing a public provider-neutral DTO,
  stop and request scope clarification instead of broadening the contract.
- If the existing observation constructors cannot preserve compatibility
  without changing unrelated tests, stop before modifying shared contracts.
- If the experiment would need a broker call, new endpoint or live collection
  orchestration, stop; this Story permits offline deterministic fixtures only.
- If any implementation path would feed the candidate into T0/T1 or mark
  `EquityFact` available, stop and revert that approach before validation.

## Expected Outcome

The Story will provide a reproducible numeric candidate and explicit evidence
that the candidate is incomplete or unverified where appropriate. It will not
change current FTMO readiness or authorize a first trade.
