# Repository Analysis - Story 0233

## Status

Repository Analysis pending human approval.

## Scope Understanding

The requested change is a domain evolution of the existing provider-neutral
account snapshot and evidence-scoped Risk path. It must make unsupported cTrader
margin fields non-global blockers without weakening mandatory evidence checks for
specific Risk invariants or LIVE execution.

The current FTMO observation is fresh and identity-valid. It contains USD
balance, leverage metadata, complete empty positions/orders and available deals.
The cTrader account-level `usedMargin` and `freeMargin` fields are absent. The
current adapter preserves that absence as `null`; no provider investigation or
endpoint search is required by this Story.

## Repository Evidence

### Current account snapshot

`BrokerAccountSnapshotV2Dto` carries account facts, collection availability,
completeness, freshness, observation timestamps and bounded unavailability
reasons. `BrokerAccountFactsV2Dto` already carries an `EquityFactDto` with
provenance, availability, timestamps, source, derivation method and reasons.

This is sufficient to preserve provider-versus-derived equity provenance. A new
generic provenance framework is not justified at this stage.

### cTrader mapping

`JsonWebSocketCTraderOpenApiClient.mapObservation(...)` reads optional
`trader.equity`, `trader.usedMargin` and `trader.freeMargin` fields and preserves
missing values as `null`. Runtime logs for account `49411967` confirmed that the
three fields were absent. `CTraderAuthoritativeSnapshotCapability` adds explicit
unavailability reasons and marks the snapshot incomplete when required account
facts are absent.

The existing empty-account equity rule is in
`CTraderAuthoritativeSnapshotCapability.emptyAccountDerivedEquityFact(...)`.
It requires balance, complete empty positions/PnL, inactive bonuses, a valid
valuation asset and a fresh observation timestamp. Its output is explicitly
`BROKER_DERIVED` with resolution method `BALANCE_WHEN_NO_OPEN_POSITIONS`.

No equivalent safe account-level used-margin or free-margin derivation is
currently present.

### Account status

`BrokerAccountReadOnlyStatusService` reads the latest snapshot without starting
provider synchronization. It reports `READ_ONLY_COMPLETE` only when the whole
snapshot is `COMPLETE` and `FRESH`; otherwise it reports
`CONNECTED_BUT_FACTS_INCOMPLETE`. The current response hardcodes both
`executionAllowed` and `riskEvaluationAllowed` to `false`, preserving Story
0232's safety requirement but not expressing capability-specific T0 readiness.

This is the primary readiness-contract candidate for targeted evolution. The
booleans must not simply be flipped; they need either operation-specific meaning
or a minimal additional diagnostic representation.

### Evidence-scoped Risk

`RiskFactRequirements.from(EffectiveRiskRuleSet)` derives requirements from
configured rule IDs. Current rules map as follows:

- `MAX_POSITION_RISK`: balance, positions and protective-stop evidence.
- `MAX_EXPOSURE`: equity and positions.
- `DAILY_DRAWDOWN`: balance and equity.
- `MAX_TOTAL_DRAWDOWN`: equity and starting balance.

The current configured rules do not set `usedMargin` or `freeMargin` as required
facts. `ModeAwareRiskFactsProvider` evaluates the selected requirements against
the authoritative snapshot rather than requiring every account field globally.
`InvariantEvidenceAdmission` already resolves missing evidence per invariant and
preserves `INCOMPLETE` semantics.

This supports the product goal, but tests must verify that incomplete account
metadata does not accidentally make independent T0 invariants unavailable and
that aggregate execution remains blocked when a mandatory invariant is missing.

### LIVE execution

`ExecutionTimeRiskRevalidationService` and the T1 execution pipeline require
fresh deterministic evidence and a persisted margin context where the selected
execution path needs it. `BrokerSubmissionStep` requires T1 approval before any
broker submission. Existing identity, freshness, provider and human-authorization
checks must remain unchanged.

ADR-084 explicitly separates current account used margin from provider-authoritative
required margin for a proposed LIVE order. This distinction must remain intact.

## Decision and Architecture Constraints

1. Reuse the existing snapshot and equity provenance structures.
2. Do not introduce a second readiness engine.
3. Keep account observation, T0 invariant readiness, LIVE preflight readiness and
   execution authorization distinct.
4. Do not derive used margin or free margin until explicit preconditions are
   demonstrated in repository contracts and tests.
5. Treat missing margin evidence as optional for operations that do not consume it,
   but blocking for any control that declares it mandatory.
6. Preserve `INCOMPLETE` for the global observation when the declared observation
   capability is not complete; capability-specific readiness must not be inferred
   from that global label alone.
7. Reconcile ADR-084/085/086 wording before implementation if the approved
   empty-account equity behavior changes their current decision semantics.

## Contradictions Requiring Approval

- ADR-085 states that cTrader balance-to-equity derivation is not approved, while
  the current implementation already contains a narrow validated empty-account
  derivation and the new product decision asks to preserve it.
- ADR-086 safety invariants include `EQUITY_BALANCE_FALLBACK = false`, which may
  remain correct if the existing derivation is explicitly narrower than a generic
  balance fallback, but this distinction must be made explicit.
- Story 0200 treats missing account-level equity, used margin and free margin as
  unavailable. The new Story should extend capability-specific readiness without
  silently changing Story 0200's synchronization safety contract.

These are material documentation and policy reconciliations, not implementation
details to resolve silently.

## Proposed Validation Matrix

### Fact derivation

- Complete empty account: derive equity only under explicit preconditions.
- Missing or incomplete orders: keep equity unavailable.
- Open positions or unresolved exposure: keep equity unavailable.
- Stale or conflicting timestamps: keep derived facts unavailable.
- Missing balance or valuation asset: keep derived facts unavailable.
- Used margin: verify no derivation unless a provider/account contract proves it.
- Free margin: verify no derivation unless coherent admissible inputs exist.

### T0 Risk

- Position-risk rule with balance, positions and stops: independently evaluable.
- Equity-dependent rule with derived equity: governed by explicit provenance
  admissibility and policy contract.
- Missing mandatory evidence: `INCOMPLETE`, never PASS.
- Multiple rules with different requirements: per-invariant results plus
  fail-closed aggregate.

### LIVE execution

- Missing mandatory margin/preflight evidence blocks execution.
- Failed T1, stale facts, identity mismatch or revoked authorization blocks.
- No code path bypasses human authorization or broker idempotency checks.

## Required Human Approval

Repository Analysis is complete for review. Implementation must not begin until
the human approves this analysis and the subsequent Implementation Plan.
