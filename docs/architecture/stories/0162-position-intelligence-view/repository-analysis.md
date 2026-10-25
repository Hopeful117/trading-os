# Repository Analysis - Story 0162

## Story

0162 - Position Intelligence View

## Repository State

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `060cc654142aa33c01c9685ad3a913a41dc2b7d6` |
| Remote baseline | `origin/main` points to the same commit |
| Working tree | Clean before Story creation; Story 0162 artifacts are the only changes |
| DevLog | Unavailable for this analysis: `LLM_PROVIDER_ERROR` |

The repository remains authoritative for the current physical implementation.
The DevLog failure does not block this analysis.

## Analysis Sources

### Story and prior work

- `docs/architecture/stories/0162-position-intelligence-view/story.md`
- `docs/architecture/stories/0032-dedicated-open-positions-monitoring/repository-analysis.md`
- `docs/architecture/stories/0041-paper-local-position-query-valuation/story.md`
- `docs/architecture/stories/0155-authoritative-executed-trading-evidence/story.md`
- `docs/architecture/stories/0160-deterministic-trade-scenario-projection/story.md`
- `docs/architecture/stories/0161-trader-cockpit-tradeplan-decision-continuity/story.md`

### Governing ADRs

- `docs/architecture/adr/ADR-019.md`
- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-029.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-040.md`
- `docs/architecture/adr/ADR-042.md`
- `docs/architecture/adr/ADR-048.md`
- `docs/architecture/adr/ADR-050.md`

### Current implementation

- `trading-core/src/main/java/com/hope/trading/trading_core/controller/PositionController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/service/PositionQueryService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/dashboard/model/OpenPositionDashboardView.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/model/Trade.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/aggregate/TradeOutcome.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/infrastructure/persistence/TradeExecutionProvenanceEntity.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradeScenarioProjectionService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/AccountRiskMonitoringService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerPositionSnapshotV2Dto.java`
- `trading-os-web/src/app/core/services/position.service.ts`
- `trading-os-web/src/app/features/positions/pages/positions/positions.ts`
- `trading-os-web/src/app/features/positions/pages/positions/positions.html`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/tradeplan/TradingRationale.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/OpportunitySetupSnapshot.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/tradeplan/TradePlanOriginContextResolver.java`

## Current Architecture

### Position authority

ADR-042 establishes the required authority boundary:

```text
LIVE  -> Broker Service/provider facts
PAPER -> Trading Core persisted Account/Trade state
```

The current code follows this boundary in `PositionController`:

- PAPER loads the authenticated `Account` and delegates to
  `PositionQueryService.findPaperPositions(...)`.
- LIVE loads the broker account snapshot through `BrokerApiClient`, maps it to
  broker-neutral facts, and delegates to `PositionQueryService.findPositions(...)`.
- account ownership and BrokerAccount ownership are checked before reading either
  source.
- an unavailable LIVE broker currently produces an empty position list rather
  than an explicit degraded response.

The implementation does not introduce a universal persistent `Position`
aggregate, which is consistent with ADR-040 and ADR-042.

### Current position projection

`PositionQueryService` is already the shared composition boundary. It:

1. receives broker-neutral `PositionFact` values or converts open PAPER `Trade`
   values into the same fact shape;
2. resolves the internal market catalogue;
3. requests current Market Data price snapshots;
4. accepts only fresh price snapshots for current valuation;
5. calculates P/L, P/L percentage, exposure, risk amount, and risk percentage;
6. exposes protection, tradability, source, and timestamps through
   `OpenPositionDashboardView`.

`OpenPositionDashboardView` currently contains:

- position identity;
- account and market identity;
- symbol, side, quantity, and entry price;
- current price;
- Stop Loss and Take Profit;
- calculated and broker-reported unrealized P/L;
- risk and exposure;
- protection status;
- market tradability;
- opened time, price observation time, and calculation time;
- source (`BROKER` or `TRADING_CORE`);
- valuation status and quote asset.

It does not contain TradePlan, TradeOutcome, ExecutionIntent, Opportunity,
thesis, evidence, account monitoring result, or scenario consequences.

### PAPER representation

`Trade` remains the local PAPER position projection, as required by ADR-042.
Its persisted fields include:

- `tradeId`;
- symbol and side;
- entry/current/exit prices;
- quantity;
- P/L;
- opened/closed timestamps;
- Stop Loss and Take Profit;
- risk/reward fields;
- account and lifecycle status.

`PositionQueryService.toPositionFact(...)` rejects incomplete local PAPER state
and maps OPEN trades into the shared dashboard projection.

### LIVE representation

`BrokerPositionSnapshotV2Dto` is provider-neutral and contains:

- instrument;
- signed quantity;
- entry price;
- observed time;
- opaque broker position reference;
- margin;
- Stop Loss.

The current LIVE read model does not expose an internal Trade or TradeOutcome
identity. The opaque broker position reference is a broker read identity, not a
TradePlan lineage identifier.

## Existing Lineage

### Execution and TradeOutcome

`TradeOutcome` retains:

- account ID;
- broker account ID;
- execution intent ID;
- optional `tradeId`;
- TradePlan ID and version;
- provenance entries;
- instrument, side, quantity, entry and protection parameters;
- lifecycle status and timestamps.

`TradeExecutionProvenanceEntity` provides a persisted relation from `tradeId`
to `executionAttemptId`, broker order, fill, execution mode, execution purpose,
and execution time.

This supports a useful PAPER lineage when the local Trade has an associated
TradeOutcome/provenance record. It does not establish a direct relation from a
LIVE broker position to a TradeOutcome.

The current position query does not load or expose this lineage.

### TradePlan and Opportunity

TradePlan persistence uses immutable ID/version identity. Opportunity-origin
plans retain `OpportunityPlanReference` values in `TradingRationale`; manual
plans may legitimately have no Opportunity.

`OpportunitySetupSnapshot` contains an entry-oriented reference price and time,
setup description, triggers, and analysis context references. It is not a
complete typed market snapshot for position monitoring.

ADR-050 requires account-scoped Opportunity provenance to remain immutable and
account-validated. The future view must not correlate an Opportunity to a
position using symbol, price, side, or timestamp alone.

### Lineage assessment

| Relationship | Current state | Analysis consequence |
|---|---|---|
| Position -> Trade | Partial | Stronger for PAPER local Trade; not direct for LIVE broker positions |
| Position -> TradePlan | Partial | Available through explicit TradeOutcome/ExecutionIntent lineage where retained |
| TradePlan -> Opportunity | Partial overall | Explicit for Opportunity-origin plans; absent by design for manual plans |
| Position -> original thesis | Partial | Requires TradePlan resolution; no current position endpoint support |

## Risk and Scenario Reuse

### Existing scenario projection

`TradeScenarioProjectionService` is an authenticated, read-only, exact
TradePlan/version projection service. It:

- validates TradePlan identity and version;
- validates actor ownership and account ownership;
- loads current account balance/equity;
- loads optional Challenge progress;
- obtains Market Data valuation facts;
- delegates deterministic scenario calculations to
  `TradeScenarioProjectionEngine`;
- exposes provenance and availability.

The service is designed for a TradePlan entry geometry. A position intelligence
view can reuse the Risk Domain engine and response semantics only through an
explicit adapter that supplies current position context. It must not duplicate
financial formulas in Trading Core or Angular.

### Current account monitoring gap

`AccountRiskMonitoringService` builds Risk Domain `PositionSnapshot` values from
Risk Facts. At present it supplies `Optional.empty()` for `lossAtStop` while
constructing those snapshots. Therefore account-level monitoring exists, but a
complete current per-position loss-at-Stop value cannot be assumed from the
current Risk monitoring path.

The Position Intelligence view must preserve unavailable/partial status instead
of interpreting missing protection risk as zero.

## Market Context and Thesis

The repository supports deterministic current market evidence and freshness
metadata through Market Data and Market Intelligence. However:

- the position projection has current price and price timestamp only;
- no complete entry market snapshot is attached directly to the position;
- `TradingRationale` stores thesis, confirmation conditions, invalidation
  conditions, and management rules as text/collections;
- those conditions do not currently define a universal machine-evaluable
  metric/operator/window contract;
- current Trend Context and other intelligence evidence are separate historical
  or current artefacts, not a position-bound comparison contract.

Consequently, the first capability may compare deterministic financial facts
such as price, P/L, distance to protection, and supported risk facts. It must
report Trend, Structure, Volatility, and thesis comparisons as unavailable or
unknown when comparable entry evidence is absent.

## Current Frontend Surface

The Angular application already has:

- `/positions` route;
- `PositionService.getPositions(accountId)`;
- reactive polling every 10 seconds;
- account selection;
- preserved stale UI data on refresh failure;
- explicit full-exposure close handling for PAPER and LIVE;
- position cards showing current financial/protection facts.

The current page does not provide:

- a position detail/intelligence read model;
- TradePlan or Opportunity origin;
- thesis or management context;
- entry/current comparison beyond individual displayed values;
- current Stop/Target/Challenge consequences;
- per-source freshness and missingness explanation;
- a clear distinction between unknown, unavailable, stale, and unchanged
  analytical states.

The existing page already uses Observables and the async pipe. Any extension
should preserve this pattern and keep financial calculations backend-owned.

## Architectural Alignment

The proposed Story is aligned with the accepted ADRs:

- ADR-028: Risk remains deterministic, immutable-input based, read-only, and
  fail-closed.
- ADR-031: Trading Core assembles financial context; Market Intelligence owns
  TradePlan context; Risk owns financial authorization.
- ADR-040/042: LIVE and PAPER position authority remains mode-specific; no
  universal Position aggregate is introduced.
- ADR-048: source facts, deterministic evidence, interpretation, Risk, and human
  execution authority remain distinct.
- ADR-050: Opportunity provenance remains account-scoped and explicit.

No new ADR is required for the read-only composition itself. An ADR would be
required if implementation introduced a new persistent position authority,
continuous monitoring/event semantics, a new post-entry lifecycle, or automatic
management authority.

## Recommended Implementation Boundary

The implementation should remain limited to:

1. a Trading Core read use case for one authorized open position;
2. a mode-neutral response containing current facts, explicit lineage,
   freshness, availability, and deterministic comparisons;
3. an adapter from current position facts to the existing scenario projection
   semantics where the inputs are valid;
4. a reactive frontend detail surface;
5. negative/degraded-state coverage.

The implementation must not add:

- a universal Position aggregate;
- persisted monitoring snapshots;
- events or alerts;
- machine-evaluable thesis invalidation;
- automatic or recommended execution actions;
- provider-specific fields in public contracts.

## Expected Affected Areas

Likely affected areas, subject to the approved Implementation Plan:

- `trading-core` dashboard/position query and read API;
- `trading-core` execution provenance and TradePlan read ports, if existing
  contracts are sufficient to resolve lineage;
- `trading-core` risk application adapter for scenario consequences;
- `risk-domain` only if an existing reusable contract cannot express the needed
  read-only projection, and only with explicit scope approval;
- `trading-os-web` position service, models, route/detail presentation, and
  tests.

`broker-service`, `market-data`, and `market-intelligence` should not require
new domain authorities. Contract changes in those modules require evidence that
current read contracts cannot support the Story.

## Validation Baseline

The implementation will require at least:

- Trading Core ownership and account-isolation tests;
- PAPER and LIVE source-selection tests;
- explicit lineage-present and lineage-absent tests;
- stale, unavailable, missing-protection, unsupported-currency, and no-Challenge
  cases;
- no-mutation tests for all projection paths;
- Risk Domain/Story 0160 compatibility tests for current, Stop, Target, and
  Challenge consequences;
- Angular tests for loading, degraded states, and reactive rendering;
- affected Maven tests;
- Angular tests and production build;
- formatting and `git diff --check`;
- authenticated PAPER runtime validation.

## Risks and Blockers

### Risks

1. LIVE positions may have no deterministic local link to a TradePlan. The UI
   must show missing lineage rather than infer it.
2. Current account Risk monitoring does not populate `lossAtStop` for every
   position. The view must fail closed for unsupported per-position consequences.
3. Broker and Market Data observations may have different timestamps. The view
   must not imply a globally atomic snapshot.
4. Historical text conditions may be mistaken for executable invalidation rules.
5. Extending the existing close page could accidentally mix read-only insight
   with financial commands; the detail view must preserve the explicit close
   boundary.

### Blockers

No repository blocker prevents Repository Analysis or implementation planning.

Implementation must stop and request a decision if it requires:

- a new universal position identity;
- persistence of monitoring snapshots;
- alert/event semantics;
- a machine-evaluable thesis rule model;
- a new authority boundary;
- mutation of existing TradePlan or position lifecycle semantics.

## Repository Analysis Conclusion

The Story is implementable as a bounded read-only composition without a new
ADR, provided that the implementation preserves current authority boundaries
and treats missing lineage and missing entry context explicitly.

The smallest coherent next capability is the on-demand Position Intelligence
View. The largest foundational limitation is the absence of a complete typed,
auditable entry market-context snapshot linked to the executed position. That
limitation must remain visible and must not be hidden by inferred Trend,
Structure, Volatility, or thesis conclusions.

## Workflow Status

Repository Analysis is complete and awaits explicit human approval before an
Implementation Plan is produced.
