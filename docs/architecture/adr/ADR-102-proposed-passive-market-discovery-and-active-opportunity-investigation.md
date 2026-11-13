# ADR-102 - Passive Market Discovery and Active Opportunity Investigation Architecture

## Status

ACCEPTED - human architecture approval recorded 2026-11-13

## Acceptance Record

The project owner approved ADR-102 as the V1 architecture baseline on
2026-11-13. This approval authorizes architectural planning and the Epic 0238
roadmap only. It does not authorize application implementation, schema
migrations, scheduler enablement, AI provider activation, runtime scans,
historical acquisition, broker operations or execution.

## Date

2026-11-13

## Decision Type

Market Intelligence operational scanning, market-awareness lifecycle and
passive-to-active coordination

## Related ADRs

- `ADR-020` - Market Intelligence Architecture
- `ADR-021` - Governance of Market Intelligence Analysis Executions
- `ADR-033` - Active and Passive Market Intelligence Orchestration
- `ADR-034` - Strategy, StrategyMatch and Trading Opportunity Boundaries
- `ADR-035` - Strategy Evaluation Live and Backtest Compatibility
- `ADR-036` - Split Strategy Governance into Validation and Operational Dimensions
- `ADR-037` - Persisted Strategy Definitions as Runtime Source of Truth
- `ADR-056` - Deterministic Evidence Contract
- `ADR-057` - Historical Market Evidence, Known-At Semantics, and Source Retention
- `ADR-076` - Market Thesis Authority and Evidence Composition
- `ADR-079` - Deterministic Liquidity Evidence Trust and Policy Boundary
- `ADR-087` - Fresh Snapshot Reuse and Refresh Coordination
- `ADR-100` - Capability-Aware Forex Quote-Based LIVE Tradeability
- `ADR-101` - Autonomous Trading Research Lifecycle and Governance

## Related Stories

- `0005`, `0006`, `0007`, `0021`, `0022`, `0235`, `0236`, `0237`

## 1. Context and Product Problem

Trading OS has a substantially implemented intention-driven Active Scanner and
the deterministic single-market analysis primitives required by a Passive
Scanner. The operational passive boundary is missing: there is no bounded
configured universe, recurring batch lifecycle, durable passive run, or
trader-facing passive awareness feed.

The product goal is:

```text
bounded market monitoring
    -> deterministic passive discovery
    -> explainable candidate awareness
    -> explicit active investigation
    -> human opportunity review
```

Passive monitoring is market awareness. It is not account-specific trade
authorization, risk evaluation, broker execution, or autonomous trading.

## 2. Existing Architecture

The existing Active Scanner remains the authority for intention-driven work:

```text
User -> Trading OS Web -> ActiveScan -> account eligibility
     -> AnalysisExecution(ACTIVE) -> Observation -> StrategyMatch
     -> TradingOpportunity -> active scan projection
```

The existing passive path is an execution mode and strategy, not an operational
scanner. `PassiveAnalysisStrategy`, `AnalysisExecution(PASSIVE)`, deterministic
capabilities, `Observation`, `MarketThesis`, and
`ProductionIntelligencePipeline` must be reused.

Historical research executions that happen to use passive mode remain governed
by ADR-101 and are not operational passive monitoring.

## 3. Decision Drivers

- Preserve the Active/Passive distinction in ADR-033.
- Monitor a market once even when multiple accounts reference it.
- Preserve canonical market, provider and product identity.
- Avoid introducing a new service or generic workflow framework.
- Make recurring work bounded, durable and restart-safe.
- Keep deterministic evidence and eligibility authoritative.
- Keep AI advisory and optional.
- Preserve explicit human authority before active trading decisions.
- Avoid requiring historical research completeness for lightweight monitoring.

## 4. Passive and Active Responsibilities

The Passive Scanner is batch-driven and account-independent. It maintains
market awareness for an explicitly configured universe.

The Active Scanner is user-intention-driven and account-contextual. It resolves
account, broker, market and system eligibility before deeper analysis.

Passive output may inform an Active Scan, but it never substitutes for active
eligibility, current evidence, risk evaluation or human authorization.

## 5. Market Universe Ownership

V1 uses a global, explicitly configured universe. Market Data remains the
authority for canonical market identity, provider identity and product type.
Market Intelligence owns only the passive monitoring selection policy.

The V1 source of truth is a validated application configuration containing
enabled canonical market entries, provider/product identity, cadence and
operational limits. Each passive run persists the resolved ordered market
snapshot and a universe configuration fingerprint. No user-facing universe CRUD
or new universe database aggregate is introduced in V1.

The scheduler consumes a resolved `PassiveMarketUniverse` contract rather than
reading environment-variable or framework property names directly. The
configuration adapter validates and resolves that contract. This preserves a
future application boundary for persisted or user-managed preferences without
changing `PassiveScanRun` identity or lifecycle.

This is a deliberate hybrid boundary: configuration selects the universe, while
the durable run snapshots the exact operational selection. A future persisted
universe administration model can be added without changing execution identity.

EUR/USD CFD through cTrader and BTC/USD through Kraken are demonstration
candidates only. They are not hardcoded universal defaults, and each must pass
its provider/product-specific evidence and capability checks.

## 6. PassiveScanRun Lifecycle

`PassiveScanRun` is a dedicated operational orchestration aggregate. It is not
an `ActiveScan` and must not use synthetic actors, accounts or objectives.

The run stores:

- run identity and idempotency key;
- universe configuration version/fingerprint;
- planned window, trigger time and cadence decision;
- immutable ordered market snapshot;
- per-market child state and analysis linkage;
- dispatch and reconciliation timestamps;
- bounded diagnostics and terminal summary.

Run states are:

```text
REQUESTED -> DISPATCH_REQUESTED -> RUNNING
RUNNING -> COMPLETED | PARTIALLY_COMPLETED | FAILED
REQUESTED -> COMPLETED_NO_WORK
```

`COMPLETED_NO_WORK` means no market was eligible for this run. A market with
insufficient or stale evidence is not silently successful; it receives an
explicit non-positive child outcome and diagnostic.

Child states are:

```text
REGISTERED -> DISPATCH_REQUESTED -> ACCEPTED -> RUNNING
RUNNING -> COMPLETED | INSUFFICIENT_EVIDENCE | FAILED
REGISTERED -> SKIPPED
```

Dispatch claims use the established durable claim and lease pattern. A run and
all child linkages are persisted before dispatch. Retry may reattempt safe
handoff, but deterministic run/market identity prevents duplicate logical
executions. Provider writes are not part of this workflow.

V1 does not add cancellation fan-out. Retention is bounded operational history;
the exact duration is configuration and must not delete authoritative
Observation, MarketThesis or TradingOpportunity history.

## 7. Scheduling and Resource Budgets

V1 uses one predictable scheduler check approximately every five minutes. The
check creates work only for markets whose per-market cadence is due and which
have no compatible in-flight execution.

Provisional defaults are:

- configured demonstration universe: two entries;
- maximum markets per run: two;
- passive execution concurrency: one;
- AI invocation: disabled;
- notifications: disabled.

These are deployment configuration, not domain invariants. The scheduler must
consider market state, data freshness, last completed run, required timeframe,
provider budgets, in-flight work and deterministic evidence eligibility before
dispatch.

Provider request limits and retry behavior reuse existing provider budget and
rate-limit boundaries. Unavailable or closed markets are recorded as skipped or
insufficient evidence without blocking unrelated markets. Backpressure leaves
due work for a later run rather than expanding concurrency.

A market is dispatch-eligible only when all of the following are true:

```text
universe entry enabled
and canonical/provider/product identity resolves
and market is operationally eligible
and required evidence is available or safely acquirable
and per-market cadence is due
and no compatible execution is in flight
and run, concurrency, provider and retry budgets remain available
```

The wake-up interval is not an analysis interval. A wake-up that finds no due
market creates no execution. A completed attempt records its next eligible
time. Retryable failures use bounded attempts and persisted backoff; permanent
failures and unavailable-market outcomes remain visible without immediate
re-dispatch. Selection uses deterministic due-time ordering with a stable
market-id tie-breaker so lower-priority entries cannot be indefinitely starved.

The system must expose at least run count, selected/ skipped/ dispatched market
counts, active executions, retry count, provider-budget consumption, stale or
insufficient-evidence count, duplicate-suppression count and dispatch latency.

## 8. Passive Awareness Semantics

V1 does not introduce a separate `MarketCandidate` aggregate.

- `Observation` remains the authoritative deterministic fact/evidence record.
- `MarketThesis` remains the evidence-bearing market-level interpretation.
- `TradingOpportunity(PASSIVE_SCAN)` is created only when the existing strategy
  and thesis invariants produce a valid opportunity.
- The frontend candidate feed is a bounded read projection over these existing
  authoritative records, with explicit origin and evidence state.

The V1 passive candidate contract is the latest `TradingOpportunity` version
with `origin=PASSIVE_SCAN`, a required `strategyMatchId`, observation and
MarketThesis references, `MarketThesis.state=CANDIDATE`, and a validity window
that contains the projection time. Its `OpportunityStatus.ACTIVE` value is an
opportunity lifecycle state, not a statement that the candidate is account
actionable or trade-ready. The projection must label the origin explicitly and
must not place passive candidates in an active-only or account-actionable view
without that distinction.

An observation alone is not a candidate or trading opportunity. A passive
opportunity is not account-authorized. The current trade-plan generation
boundary rejects an accountless passive opportunity; this invariant must remain
explicitly tested. A missing, stale or contradictory thesis cannot be promoted
by the projection layer.

Freshness is evidence- and timeframe-specific. Projections must expose
observation time, evidence cutoff, analysis completion time, validity window,
quality and non-positive diagnostics. Repeated equivalent detections reuse the
existing opportunity fusion/deduplication policy where applicable; they do not
create an unbounded stream of duplicate opportunities.

Expiration or supersession must use the underlying validity window and evidence
policy. No universal signal TTL is introduced.

## 9. Passive-to-Active Coordination

The V1 flow is:

```text
Passive Detection
    -> persisted Observation/Thesis/Opportunity provenance
    -> trader reviews candidate
    -> existing ActiveScan request
    -> account and broker eligibility revalidation
    -> AnalysisExecution(ACTIVE)
    -> human opportunity review
```

The existing Active Scan API remains the active investigation entry point. The
frontend passes the canonical market identity and requires account selection.
The active scan owns account-specific eligibility and does not trust passive
eligibility as a substitute.

The current ActiveScan request can be reused for ordinary active scans, but it
cannot by itself preserve passive-candidate traceability or request evidence
reuse. Story 0243 therefore adds optional source passive-candidate identity and
version fields to the existing request, command, request fingerprint and
immutable scan provenance. Omitting those fields preserves existing callers.

Passive evidence may be reused only when its evidence policy, cutoff,
canonical market/provider/product identity, required capabilities, context
fingerprint and freshness are compatible. Otherwise active analysis reacquires
the required evidence. Every reuse must retain references to the passive
observation or thesis and the active execution.

## 10. Deterministic Analysis Reuse

Shared and passive-compatible capabilities include market identity, current
market facts, trend context, OHLC range, OHLC momentum and basic quote/spread
evidence when the instrument profile permits it.

Market structure and tradeability remain shared capabilities but are selected
only when their evidence contract is satisfied. Level II, order-book replay,
deep liquidity suitability and account-specific constraints are not assumed to
be passive-compatible for every market.

Active analysis may use a deeper context plan than passive analysis. Research
reconstruction and historical admissibility workflows remain research-only.

## 11. AI and Research Boundaries

V1 works with AI disabled. AI cannot select an unbounded universe, override
eligibility or freshness, invent observations, promote a non-positive result,
authorize execution or create trading authority.

AI may later explain deterministic passive evidence or assist active relevance
interpretation through existing Market Intelligence ports and authorization
boundaries.

ADR-101 remains authoritative. Historical research hypotheses, research
reports and dataset outputs must not become operational strategies, Market
Theses or opportunities automatically.

## 12. Authorization and Safety

Passive monitoring uses public market-data capabilities and does not require
account synchronization merely to monitor a configured market. Configuration,
run execution, awareness viewing and active scan initiation are separate
permissions within the existing service boundaries.

Passive monitoring cannot place orders, modify positions, synchronize accounts,
change risk settings, impersonate users or bypass Trading Core. The connected
FTMO cTrader account remains Trading OS `LIVE` when used for account-specific
active validation; it is not required for global passive monitoring.

## 13. Historical Evidence Boundary

Operational passive monitoring requires only the minimum current or already
captured evidence needed by its selected capability plan. It does not trigger
historical backfills or require full research dataset readiness.

Operational freshness, historical research completeness and dataset
admissibility remain separate states. A provider without sufficient operational
evidence produces an explicit non-positive result rather than synthetic data.

## 14. Alternatives Considered

### Reuse ActiveScan

Rejected. Synthetic actors, accounts or objectives would violate the semantic
boundary in ADR-033 and make account-independent monitoring misleading.

### Scheduler Directly Creates AnalysisExecution Rows

Rejected. It would lack durable run identity, per-market lifecycle,
restart-safe claims, bounded reconciliation and operational observability.

### New MarketCandidate Aggregate

Deferred. Existing Observation, MarketThesis and TradingOpportunity invariants
already represent facts, interpretation and validated setups. A new aggregate
would duplicate lifecycle and provenance before a demonstrated requirement.

### New Scanner Microservice or Generic Workflow Engine

Rejected. The current Market Intelligence application already owns analysis
execution and bounded local dispatch. A new service would add operational and
consistency complexity without product value.

## 15. Consequences

Positive consequences:

- passive monitoring is account-independent and deduplicated;
- recurring work is durable, bounded and observable;
- active investigation retains existing safety and eligibility semantics;
- existing deterministic intelligence remains the source of truth;
- AI and research remain optional extensions.

Costs and limitations:

- V1 requires a new passive orchestration persistence slice;
- global configuration is not yet user-customizable;
- passive results may be non-positive when provider evidence is unavailable;
- restart recovery and retention require explicit tests and operational metrics;
- candidate presentation is a projection, not a new independent domain model.

## 16. Migration and Compatibility

Existing `ActiveScan`, `AnalysisExecution`, `Observation`, `MarketThesis`,
`StrategyMatch` and `TradingOpportunity` records remain unchanged in meaning.
Existing active APIs remain the only active-investigation path.

Passive runs use separate tables, endpoints and idempotency namespaces. No
existing active scan is converted into a passive run. Existing research
executions are not reclassified.

## 17. Explicit Non-Goals

- automatic order placement or position management;
- automatic active-scan escalation;
- AI-required monitoring;
- user watchlists or per-account passive universes;
- historical backfill orchestration;
- strategy discovery, backtesting or strategy promotion;
- global cross-market opportunity ranking;
- notifications or push delivery;
- generic workflow infrastructure;
- broker account synchronization.

## 18. Approval Boundary

Acceptance of this ADR establishes the architecture baseline for Epic 0238.
Implementation remains subject to the normal Engineering Story Repository
Analysis, Implementation Plan and human approval gates.
