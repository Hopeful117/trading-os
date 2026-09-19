# Implementation Plan - Story 0050

## Plan Status

`PROPOSED - HUMAN APPROVAL REQUIRED`

## Phase 1 - Isolate local PAPER facts

Introduce a focused PAPER facts implementation or equivalent mode-specific
boundary without changing the Risk Domain API.

For PAPER, it must:

1. validate account and broker-account mapping;
2. read local balances;
3. map open trades to positions;
4. map closed trades to closed-trade facts;
5. expose explicit unprotected-position facts;
6. create deterministic local provenance;
7. return complete facts only when all local requirements are valid.

For LIVE, preserve Broker Service delegation and fail-closed behavior.

## Phase 2 - Assemble the immutable risk context

Keep repository access and external calls in Trading Core application/infrastructure
layers. Provide the Risk Domain only with immutable snapshots, including the
broker-neutral margin/capability input defined by Story 0051.

Do not add provider-specific fields or calls to the Risk Domain.

## Phase 3 - Tests

Add or update tests for:

```text
paperSnapshotIsCompleteWhenLocalFactsAreValid
paperSnapshotMapsLongTrade
paperSnapshotMapsShortTrade
paperSnapshotMapsClosedTrade
paperSnapshotRepresentsUnprotectedTrade
paperSnapshotDoesNotCallBrokerFacts
liveStillUsesBrokerFacts
unsupportedModeFailsClosed
```

Add an integration test proving the complete context reaches the existing risk
pipeline without changing deterministic rule behavior.

## Expected Files

```text
trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProvider.java
trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactsProvider.java
trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProviderTest.java
```

New classes are optional and must be justified by existing project patterns.

## Non-Goals

* no broker capability implementation;
* no margin formula implementation;
* no PAPER execution migration;
* no Market Data ownership change;
* no changes to Risk Domain rules;
* no commit or integration action.

## Validation Commands

```text
cd trading-core && mvn test
cd risk-domain && mvn test
git diff --check
```

## Exit Criteria

* local PAPER facts are complete and reloadable;
* LIVE facts remain Broker Service-backed;
* missing facts remain fail-closed;
* tests pass;
* the diff contains no unrelated changes;
* human approval is obtained before implementation.
