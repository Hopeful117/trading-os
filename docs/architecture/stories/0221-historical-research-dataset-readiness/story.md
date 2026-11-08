# Story 0221 - Historical Research Dataset Readiness

## Metadata

**ID:** `0221`
**Title:** Historical Research Dataset Readiness
**Status:** Engineering Report completed - review findings addressed
**Priority:** P1 - Research Trustworthiness
**Predecessors:** Story `0220`
**Roadmap:** R3 - Populate Versioned Historical Research Bundles

## Goal

Establish a reliable, reproducible and temporally valid historical dataset
foundation for future quantitative research and unsupervised ML experiments.
Reuse the existing historical reconstruction, controlled export, validation and
offline research components. Do not rebuild them or fabricate unavailable data.

## Scope

- Inventory historical OHLCV, quote/spread, order-book, funding, structure,
  feature, outcome, economic-event and news evidence contracts.
- Distinguish implemented contracts, fixtures, local runtime data, deployed
  runtime data and provider data that is not ingested.
- Verify actual stored data when authorized, otherwise report
  `RUNTIME_DATA_NOT_VERIFIED`.
- Preserve `knownAt <= decisionKnownAt`, immutable evidence, explicit
  missingness, outcome identity selection and deterministic export semantics.
- Extend existing quality output only where the inventory proves a gap.
- Produce a historical data readiness report and assess unsupervised-ML
  feasibility without training or deploying models.

## Out Of Scope

- Provider backfills, bulk downloads or production database mutation.
- Live broker synchronization, execution, risk changes or deployment.
- New services, databases, event brokers, dataset stores or ML infrastructure.
- Model training, live inference, profitability claims or autonomous trading.
- Duplicate schemas, exporters, validators or feature pipelines.
- Replacing `knownAt` with candle close time or weakening strict evidence policy.

## Acceptance Criteria

- [ ] Existing historical research infrastructure is inventoried.
- [ ] Actual historical availability is measured where authorized.
- [ ] Fixtures are clearly distinguished from real data.
- [ ] At least one controlled export path is validated.
- [ ] Dataset fingerprints and provenance are verified.
- [ ] Temporal leakage protections remain enforced.
- [ ] Missingness and outcome completeness are measurable.
- [ ] A machine-readable and human-readable quality report is produced.
- [ ] ML readiness is assessed using actual evidence.
- [ ] Data insufficiency is reported rather than concealed.
- [ ] No unnecessary duplicate infrastructure is introduced.
- [ ] No model training or live inference is performed.
- [ ] Tests and documentation are complete.

## Required Deliverables

- `repository-analysis.md`
- `implementation-plan.md` when required after analysis approval
- `implementation-report.md`
- dedicated historical data readiness report

## Constraints

- Market Data owns canonical evidence acquisition and persistence.
- Market Intelligence owns reconstruction, feature/outcome pairing and export.
- Python consumes validated bundles and does not become canonical truth.
- Outcome selection must use the complete immutable outcome identity.
- Every historical eligibility decision must preserve explicit temporal metadata.
- Do not commit, push or merge automatically.

## Relevant ADRs

- `ADR-056` - deterministic evidence and analyzer boundary
- `ADR-057` - historical evidence and known-at semantics
- `ADR-058` - strict historical replay boundary
- `ADR-060` - versioned immutable feature snapshots
- `ADR-061` - explicit immutable outcome selection
- `ADR-062` - versioned research dataset boundary
- `ADR-063` - historical order-book evidence and replay
- `ADR-069` - replay acceleration invariants
- `ADR-079` - liquidity evidence trust boundary
- `ADR-080` - bounded order-book evidence retention

## Final Verdict Values

- `DATASET_READY_FOR_EXPERIMENTATION`
- `DATASET_FOUNDATION_READY_DATA_INSUFFICIENT`
- `DATASET_PIPELINE_INCOMPLETE`
- `RUNTIME_DATA_NOT_VERIFIED`
