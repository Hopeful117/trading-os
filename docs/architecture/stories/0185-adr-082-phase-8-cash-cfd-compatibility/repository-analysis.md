# Repository Analysis - Story 0185

## Repository State

```text
REMOTE_MAIN: 885fb3be7eb26e63404eea39889be4260dcd8d82
LOCAL_BASE: 885fb3be7eb26e63404eea39889be4260dcd8d82
LOCAL_HEAD: 885fb3be7eb26e63404eea39889be4260dcd8d82
BRANCH: story/0185-adr-082-phase-8-cash-cfd-compatibility
WORKTREE_STATE: clean before Story artifacts
```

Phase 7 is merged in PR #230. Story 0184 and its implementation are present on
`main`. The next available Story number is 0185.

## Relevant Boundaries

- `SettlementCompatibilityPolicy` allows `INVENTORY + SPOT` and
  `CASH + LINEAR_PERPETUAL`; it rejects `CASH + CFD`, `INVENTORY + CFD`, and
  `UNKNOWN + CFD`.
- `PaperCfdSettlementService` is a dedicated local accounting boundary and
  persists CFD contract facts, signed exposure, MTM, realized PnL, and
  execution provenance.
- `ExecutionFinalizationStep` selects the dedicated PAPER CFD settlement
  service before the generic PAPER settlement service when the market product
  is explicitly CFD.
- `ModeAwareRiskFactsProvider` preserves local PAPER accounting/valuation and
  Story 0183 provider-authoritative LIVE facts.
- `TradePlanRiskEvaluationService` and
  `ExecutionTimeRiskRevalidationService` use
  `CfdRiskProjectionService` for normalized CFD notional and stop risk.
- `ValidateAndCreateService` requires a completed approving pre-trade risk
  evaluation before creating an execution intent. T1 revalidation remains a
  separate execution gate.

## Required Margin Finding

`RequiredMarginPort` is an authoritative-looking boundary, but the current
implementation is not sufficient to authorize Phase 8:

1. `BrokerRequiredMarginClient` sends PAPER requests to
   `previewByProvider(...)` in Broker Service.
2. Kraken's margin preview uses `request.quantity() * request.price()` divided
   by a leverage value, defaulting to `CASH_LEVERAGE = 1`.
3. No Phase 8 contract proves that this preview is a local PAPER CFD
   proposed-order margin fact, nor that it incorporates the canonical CFD
   contract multiplier and mode-correct authority.
4. `UnavailableRequiredMarginClient` correctly fails closed, but it cannot make
   the capability ready.

The repository therefore proves fail-closed behavior, not readiness.

## Compatibility Decision

Do not alter the policy. Enabling it now would claim a complete accounting,
valuation, risk, and margin capability without proving the most important
proposed-order margin precondition.

```text
CASH + CFD = REJECTED
INVENTORY + CFD = REJECTED
UNKNOWN + CFD = REJECTED
```

## DevLog Limitation

DevLog context retrieval was attempted but authentication was unavailable. The
analysis uses the current repository, accepted ADR-082, and merged Stories
0178-0184 as authoritative sources for current state.
