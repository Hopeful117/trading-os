# Implementation Report - Story 0197

## Result

Implemented the approved experimental cTrader derived-equity boundary inside
Broker Service.

The experiment calculates only:

```text
balance + sum(netUnrealizedPnl)
```

Its result is versioned as `CTRADER_DERIVED_EQUITY_V1` and always marked
`UNVERIFIED`. It remains isolated from `BrokerAccountSnapshotV2`,
`RiskFactsProvider`, `EquityFact`, FTMO policy evaluation, T0, T1 and execution.

## Changes

- Added `CTraderDerivedEquityExperiment` with deterministic `BigDecimal`
  calculation, diagnostics, freshness and collection-window controls.
- Added offline comparison of two observations for account identity, balance
  version, balance, position facts and PnL facts.
- Preserved optional cTrader `balanceVersion` inside adapter-owned
  `CTraderReadOnlyObservation.AccountFacts`.
- Preserved the existing provider-authoritative equity mapping and fail-closed
  normalized snapshot behavior.
- Added financial data, accounting hypothesis and independent validation
  documents for this Story.
- Added deterministic tests for arithmetic, precision, empty accounts,
  incomplete/mismatched facts, duplicate PnL identities, staleness, changed
  balance versions, absent balance versions and changed position facts.
- Corrected review findings by adding explicit unavailable-input diagnostics,
  strict timestamp ordering, and strict comparison validation for collection
  availability, duplicate identities and missing PnL values.

## Validation

Passed:

```text
mvn -q -Dtest=CTraderDerivedEquityExperimentTest,JsonWebSocketCTraderOpenApiClientTest,CTraderAuthoritativeSnapshotCapabilityTest test
mvn test
git diff --check
```

Broker Service full suite result: 358 tests, 0 failures, 0 errors, 0 skipped.

The test process emitted the repository's existing Mockito dynamic-agent
warning. It did not fail the build.

SonarQube validation was attempted but is incomplete. The repository-wide
`./scripts/quality-scan.sh` reached `broker-service` and exceeded the local
120-second execution limit. A direct Broker Service scan then failed before
analysis because the scanner required the missing `sonar.organization`
property. No Quality Gate result is claimed.

No authenticated broker call, socket call, order operation or live account
validation was performed.

## Documentation Reconciliation

Updated Story-local documentation only:

- `financial-data-matrix.md`
- `accounting-hypotheses.md`
- `independent-validation-protocol.md`
- this implementation report

No ADR, public API, database schema, risk contract, execution flag or global
architecture contract required an update.

## Remaining Risks

- cTrader response atomicity remains unproven.
- PnL mark timestamps are not preserved by the current provider response model.
- Cost treatment and possible double counting remain unverified.
- The candidate remains inadmissible for FTMO risk evaluation and first-trade
  readiness.
- Independent validation against an authoritative reference equity has not
  occurred.

## Vault Outcome

The Obsidian vault was not consulted because this Story is governed by current
repository code, ADRs and cTrader adapter evidence; no transverse vault context
was needed to implement the approved local prototype.

Outcome: no vault action. No curated note or proposal artifact was created.
