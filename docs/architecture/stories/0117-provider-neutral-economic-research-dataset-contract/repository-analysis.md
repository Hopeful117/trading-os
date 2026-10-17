# Repository Analysis - Story 0117

## Repository Baseline

```text
HEAD = d407e9ab8f6bef828867a6950264112bfb17dc3b
branch = main
origin/main = d407e9ab8f6bef828867a6950264112bfb17dc3b
fetch = succeeded
worktree = clean except for the pre-existing untracked investigation report
```

Relevant merged implementations:

- `a790a2a2` / merge `66caaf95`: deterministic MARKET execution replay.
- `fd48bf93` / merge `6ec40935`: Economic Research Result Integration.
- `77c6b0e0` / merge `d407e9ab`: research runtime readiness follow-up.

The investigation artifact
`docs/architecture/reports/economic-research-dataset-readiness-investigation.md`
is preserved and is not part of this analysis change.

DevLog was queried for relevant prior context but is unavailable because the
provider has exhausted its credits. Repository evidence is authoritative.

## Architecture Revalidation

The previous Recommendation C remains valid.

Current evidence:

- `ResearchSample` is an immutable objective FeatureSnapshot/outcome pair.
- `ResearchDatasetBuilder` creates one sample per FeatureSnapshot and horizon.
- `DatasetManifest` contains objective population, feature schema, outcome
  schema, exclusion counts, and sample fingerprints only.
- `FeatureSnapshot` identity excludes outcome identity and is reusable across
  horizons and downstream strategy hypotheses.
- `ObservedMarketOutcome` contains objective market movement only.
- `StrategyIntent` is a concrete hypothetical action, not a TradePlan and not an
  objective market fact.
- `EconomicResult` is strategy/lifecycle-specific and already contains intent,
  entry/exit executions, costs, completeness, closed quantity, and residual
  exposure.
- `HistoricalEconomicEvaluationService` accepts a request and evaluates entry
  and exit replay, but no economic result persistence or dataset export exists.

No repository change invalidates the independent joinable dataset architecture.

## ADR Review and Governance

| ADR | Status | Current implication |
|---|---|---|
| ADR-059 | Accepted | Objective outcomes remain separate from strategy economics. |
| ADR-060 | Accepted | FeatureSnapshot is immutable, versioned, and T0-bounded. |
| ADR-061 | Proposed | Exact immutable outcome selection; current implementation already uses it. |
| ADR-062 | Proposed | Objective `research-dataset-v1` boundary; current code implements it. |
| ADR-063 | Accepted | Strict known-at Order Book evidence and replay. |
| ADR-064 | Accepted | Separate economic dataset contract, Trading Core authority, and Java/Python boundary. |

ADR-064 explicitly states that `research-dataset-v1` remains unchanged and that
future economic research uses a separate economic dataset contract. It also
authorizes the next primary Story to define the provider-neutral economic
contract, policy/evidence fingerprints, and strict input boundaries.

The remaining governance inconsistency is that ADR-061 and ADR-062 retain
`Proposed` status. This Story does not silently change those statuses. A new
ADR-065 may be created as `Proposed` if implementation requires a more precise
economic export contract, but marking it `Accepted` requires human architecture
approval. The implementation can proceed under ADR-064 only for decisions
already authorized there; any new authority boundary must remain explicitly
proposed until approved.

## Existing Objective Boundary

The following must remain untouched:

```text
research-dataset-v1
  = FeatureSnapshot
  + explicitly selected ObservedMarketOutcome
  + objective population and exclusion accounting
```

`ResearchSample` already fingerprints definition, market, cutoff, horizon,
projection, feature, outcome, status, and exclusion reason. Adding strategy,
execution, cost, or PnL fields would violate ADR-062 and break the Python
validator schema.

`FeatureProjectionV1` currently contains 43 typed features from OHLC, market
structure, and trend artifacts. Order Book liquidity is deliberately outside
the projection and must remain so.

## Existing Economic Boundary

`StrategyIntent` currently contains:

```text
instrument, side, orderType, quantity, decisionAt
```

It has no hypothesis metadata, market UUID, exit policy, or dedicated semantic
fingerprint. The request around it supplies market ID, entry/exit cutoffs,
research cutoff, transaction costs, and execution/cost model versions.

`SimulatedExecution` already preserves requested quantity, filled quantity,
average price, status, execution model version, source fingerprint, fills,
reason, and execution fingerprint.

`EconomicResult` already preserves lifecycle executions, cost completeness,
economic values, model versions, calculation fingerprint, closed quantity, and
residual exposure. The lifecycle evaluator requires a fully closed and fully
costed lifecycle for authoritative `netPnl`. It does not currently calculate a
non-null `netReturn`; no new return denominator policy is authorized by this
Story.

Current cost statuses are:

```text
AVAILABLE
UNAVAILABLE
NOT_APPLICABLE
INSUFFICIENT_EVIDENCE
UNSUPPORTED
```

Current cost completeness is:

```text
FULLY_COSTED
PARTIALLY_COSTED
GROSS_ONLY
INSUFFICIENT_EVIDENCE
```

Current execution statuses include `FILLED`, `PARTIALLY_FILLED`, `UNFILLED`,
`INSUFFICIENT_MARKET_EVIDENCE`, `INSUFFICIENT_DEPTH`,
`UNSUPPORTED_ORDER_TYPE`, `STALE_EVIDENCE`, and `INVALID_INPUT`.

## Proposed Minimal Contract

The implementation should add a small provider-neutral economic research
contract under the existing economic domain/application boundary, without
creating a production database aggregate.

### EconomicResearchDatasetDefinition

Immutable semantic definition containing:

- definition version;
- market universe/reference policy;
- T0 population interval and sampling policy;
- FeatureSnapshot projection/evidence policy;
- hypothesis identity/version;
- sizing policy/version;
- entry policy/version;
- exit policy/version and one authoritative horizon representation;
- execution model version;
- transaction-cost model version;
- research cutoff policy;
- required cost completeness;
- accepted execution statuses;
- optional exact objective outcome-join definition.

Its fingerprint must be deterministic and include canonical ordering of sets and
maps. It must not include UUIDs, creation times, or database identity.

### StrategyHypothesisIdentity

An immutable ID/version pair only. It must not accept source code, expressions,
DSL, notebook content, or executable strategy logic.

### Policies

V1 needs only:

- fixed base quantity policy with version;
- time-horizon exit policy with version and `Duration`.

The horizon is owned by the exit policy. `Texit = T0 + exitPolicy.horizon`.
No duplicate independent horizon field should be authoritative.

### EconomicResearchCandidate

Thin envelope referencing:

- economic dataset definition fingerprint;
- FeatureSnapshot semantic identity/reference;
- hypothesis identity;
- existing StrategyIntent;
- sizing policy;
- exit policy;
- execution policy/version.

It must not embed the FeatureSnapshot payload. Its candidate fingerprint must
include the definition, snapshot identity/content fingerprint, hypothesis,
intent semantic material, sizing, exit, and execution policy.

### EconomicResearchResult

Thin export envelope around the existing `EconomicResult`, not a replacement or
second accounting model. It should retain candidate identity, optional exact
outcome fingerprint, result identity, and disposition metadata while exposing
the existing EconomicResult fields unchanged.

The result identity should commit to candidate fingerprint, entry/exit execution
fingerprints, EconomicResult calculation fingerprint, research cutoff, and model
versions without redundantly reimplementing the calculation.

### EconomicResearchDatasetManifest

Separate from objective `DatasetManifest`. It should define the export schema,
definition fingerprint, population/candidate/result counts, execution status
counts, cost completeness counts, residual exposure accounting, disposition
counts, ordered candidate/result fingerprints, and source bundle fingerprint.

Runtime-generated checksums and repository code version belong to export metadata,
not semantic definition identity.

## Serialization Boundary

The canonical future bundle is:

```text
economic-intents.jsonl
economic-results.jsonl
economic-dataset-manifest.json
README.md
```

This Story may implement deterministic nested serializers and round-trip tests,
but it must not add a full import/evaluation pipeline or modify the existing
objective JSONL/Python contract.

Missingness must remain explicit. `UNAVAILABLE` and `INSUFFICIENT_EVIDENCE`
cannot serialize as numeric zero. `NOT_APPLICABLE` is distinct from unavailable
and may carry explicit zero only under the existing domain semantics.

## Java/Python Boundary

Java remains authoritative for FeatureSnapshot truth, objective outcome truth,
temporal eligibility, candidate validation, execution, fills, costs,
completeness, economic accounting, fingerprints, and factual export status.

Python may discover hypotheses, generate provider-neutral candidate intent
artifacts, and own statistical transformations, chronology, purging, embargo,
OOS, walk-forward, sensitivity, visualization, and ML later. Python must not
recalculate fills, VWAP, fees, funding, PnL, completeness, or temporal
eligibility.

## Explicit Non-Goals

No changes to objective research classes, FeatureProjectionV1, outcome classes,
Order Book replay, fee/funding acquisition, PAPER, Risk, Python, notebooks,
production persistence, migrations, backfill, or Cartesian precomputation.

No net-return policy will be invented. The contract must represent the existing
state where net PnL may be available only for a fully costed closed lifecycle,
while `netReturn` is unavailable until a denominator policy exists.

## Implementation Plan

1. Add immutable policy/identity value types and canonical fingerprint helpers
   under existing Trading Core economic conventions.
2. Add candidate, result envelope, and separate manifest contracts without
   persistence.
3. Add deterministic nested serialization and round-trip tests only where the
   current Jackson/domain conventions support them.
4. Add focused tests for quantity, side, horizon, hypothesis, model-version,
   missing-cost, optional-outcome, partial-fill, residual-exposure, and
   collection-order sensitivity.
5. Run objective dataset, Market Intelligence, Trading Core, and Python
   regression checks. Inspect the complete diff and confirm no production
   boundaries were expanded.

## Approval Gate

This Repository Analysis is complete but requires explicit human approval before
an Implementation Plan or production code changes are produced, per the
Engineering Story workflow.
