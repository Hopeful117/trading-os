# Implementation Plan - Story 0050

## Plan Status

`PROPOSED - HUMAN APPROVAL REQUIRED`

## Business Decision

The normal automated trading path remains strict: an automated Trade Plan must
contain a valid protective stop before risk authorization and execution.

Manual trading is a secondary workflow. A trader may open a manual position
without a stop-loss or take-profit and add those protections later. The system
must preserve this intent explicitly without converting an unprotected
position into a zero-risk position.

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

## Phase 2 - Model protection state without fabricating risk

Extend the broker-neutral risk snapshot so that a position can distinguish:

```text
PROTECTED
PARTIALLY_PROTECTED
UNPROTECTED
UNKNOWN
```

The absence of a stop must not be represented as `lossAtStop = 0`. The Risk
Domain must receive an explicit protection state and apply deterministic policy
to it.

Automated Trade Plans must remain protected. Manual Trade Plans may be
unprotected when the user explicitly confirms the manual operation.

## Phase 3 - Assemble the immutable risk context

Keep repository access and external calls in Trading Core application/infrastructure
layers. Provide the Risk Domain only with immutable snapshots, including the
broker-neutral margin/capability input defined by Story 0051.

Do not add provider-specific fields or calls to the Risk Domain.

## Phase 4 - Preserve automated/manual boundaries

Keep the source of the Trade Plan authoritative through the full path:

```text
MANUAL      -> manual protection policy
OPPORTUNITY -> mandatory protective stop
```

Manual execution without protection must still enforce ownership, quantity,
margin, exposure, funds, idempotency, and execution-time revalidation. It must
not bypass deterministic account or risk controls.

The manual Trade Plan contract must permit absent stop-loss and take-profit
values. Automated planning remains unchanged and continues to require a stop.

## Phase 5 - Tests

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
manualPlanMayOmitProtection
automatedPlanRequiresProtection
unprotectedManualPositionIsExplicit
unprotectedManualPositionCannotBeIncreasedAutomatically
```

Add integration tests proving that:

* an automated plan without protection cannot reach execution;
* a manually confirmed unprotected plan preserves its protection state;
* adding or changing protection later remains possible;
* unprotected state is not converted into zero loss;
* the complete context reaches the immutable risk pipeline.

## Expected Files

```text
trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProvider.java
trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactsProvider.java
trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProviderTest.java
trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/api/ManualTradePlanRequest.java
market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/ManualTradePlanningRequest.java
market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/tradeplan/ExecutionParameters.java
market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanBuilder.java
trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ValidateAndCreateService.java
trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ExecutionTimeRiskRevalidationService.java
risk-domain/src/main/java/com/hope/trading/risk/snapshot/PositionSnapshot.java
```

New classes are optional and must be justified by existing project patterns.

## Non-Goals

* no broker capability implementation;
* no margin formula implementation;
* no PAPER execution migration;
* no Market Data ownership change;
* no provider-specific fields or provider calls in the Risk Domain;
* no automated authorization without a protective stop;
* no synthetic loss or margin values;
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
* automated Trade Plans cannot execute without protection;
* explicitly confirmed manual Trade Plans may represent an unprotected
  position without fabricating zero risk;
* tests pass;
* the diff contains no unrelated changes;
* human approval is obtained before implementation.
