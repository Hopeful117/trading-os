# Implementation Plan - Story 0118

## Plan Status

Ready for human approval. No production implementation has been performed from
this plan.

## Design Decision

Implement the pipeline as a bounded cross-service research workflow while
preserving the existing authority boundaries:

```text
Market Intelligence
  candidate population, exact FeatureSnapshot resolution,
  orchestration, export population and manifest
        |
        | internal service contract
        v
Trading Core
  candidate semantic validation, historical replay,
  cost semantics and EconomicResult authority
        |
        v
Market Data
  strict historical Order Book replay
```

No new service, database aggregate, broker call, Risk call, PAPER behavior, or
Python runtime is introduced.

The transport boundary is an internal, bounded service contract. The canonical
offline artifact remains one candidate JSON object per line in
`economic-intents.jsonl`; transport DTOs are adapters and are not new economic
domain models.

## Bundle Contract

The pipeline accepts a bounded bundle containing:

```text
economic-intents.jsonl
```

Each line contains the complete provider-neutral candidate material required to
reconstruct the existing `EconomicResearchCandidate`, including:

- explicit schema version;
- dataset definition material and fingerprint;
- exact `FeatureSnapshotReference`;
- hypothesis identity/version;
- existing `StrategyIntent` fields;
- sizing and exit policies;
- execution and transaction-cost policy versions;
- optional supplied candidate fingerprint.

The importer will reject blank lines, malformed JSON, duplicate candidate
fingerprints, unsupported versions, unknown fields where strict parsing is
required, and a population exceeding the configured bounded request limit.

The importer will reconstruct domain values through existing constructors and
will compare any supplied fingerprint with the recomputed candidate identity.

## Market Intelligence Orchestration

Add a research application boundary under the existing Market Intelligence
research package:

- parse the bounded candidate bundle;
- validate transport-level population invariants;
- resolve each exact `FeatureSnapshot` through
  `FeatureSnapshotRepository.findByFingerprint(...)` and verify market, cutoff,
  projection, context, and snapshot identity fields;
- reject missing or conflicting snapshots without nearest/latest fallback;
- invoke the internal Trading Core economic evaluation contract;
- preserve one result disposition for every input candidate, including rejected
  candidates;
- sort accepted and rejected records by the declared semantic candidate order;
- construct result JSONL and the economic manifest in memory;
- write no database rows and do not mutate objective research artifacts.

The orchestration service will not calculate fills, VWAP, costs, PnL,
completeness, or canonical economic fingerprints independently.

## Trading Core Evaluation Boundary

Add an internal Trading Core application endpoint/service adapter for one
validated candidate evaluation request. It will:

- reconstruct and validate `EconomicResearchCandidate`;
- enforce V1 `MARKET`-only semantics;
- require candidate decision time to equal the entry execution cutoff;
- derive `Texit = T0 + exitPolicy.horizon()`;
- construct the opposite-side exit intent using the actual entry filled
  quantity after replay;
- call the existing `HistoricalEconomicEvaluationService`;
- reuse existing `TransactionCosts` and `EconomicResult` semantics;
- return explicit unavailable/unsupported/partial outcomes rather than throwing
  away failed candidates.

The cost boundary will not acquire fees or funding. When no eligible evidence
exists, the request will carry explicit `UNAVAILABLE`, `INSUFFICIENT_EVIDENCE`,
or `NOT_APPLICABLE` values according to the declared policy and product type.
No unavailable amount will be represented as numeric zero.

## Exact Snapshot Verification

Market Intelligence is the owner of the persisted `FeatureSnapshot` lookup.
The service will require:

```text
candidate.marketId == snapshot.marketId
candidate.T0 == snapshot.evaluationCutoff
candidate projection == snapshot.featureProjectionVersion
candidate context fingerprint == snapshot.contextFingerprint
candidate snapshot fingerprint == snapshot.fingerprint
candidate identity fingerprint == snapshot.identityFingerprint
```

Any mismatch produces an explicit candidate disposition such as
`FEATURE_SNAPSHOT_MISMATCH` and is retained in the export accounting.

`FeatureSnapshot` values are never sent to or used by the execution evaluator
as execution evidence.

## Deterministic Export

Add an application export service and file writer for:

```text
economic-intents.jsonl
economic-results.jsonl
economic-dataset-manifest.json
README.md
```

The writer will:

- use the existing canonical economic serializer;
- serialize one record per line with a final newline when non-empty;
- order candidates/results by market, T0, hypothesis identity, and candidate
  fingerprint, with candidate fingerprint as the final deterministic tie-breaker;
- calculate SHA-256 checksums over exact UTF-8 file bytes;
- build the manifest only after all rows are accounted for;
- include candidate, result, disposition, execution status, cost completeness,
  residual exposure, missing commission/funding, and checksum accounting;
- keep wall-clock export metadata outside semantic fingerprints;
- emit a README describing the Java authority and Python read-only boundary.

The manifest population fingerprint will use the canonical ordered candidate and
result fingerprints plus the dataset definition fingerprint. Input transport
order will not affect it.

## Bounded Invocation

Use a repository-consistent internal REST endpoint under `/internal/v1` with the
existing service-JWT conventions. The endpoint will accept an explicitly bounded
candidate bundle and return or write only the requested bounded export. It will
not enumerate snapshots or evaluate an unbounded historical population.

The controller will return `ResponseEntity` and remain internal; no Gateway,
frontend, or public user route is added.

## Domain and Transport Boundaries

- Existing economic domain records remain the only economic authority.
- JSON parsing, file I/O, HTTP, and Jackson remain outside domain records.
- Transport DTOs are package-local boundary types and are not persisted.
- No duplicate `EconomicResult`, fill model, cost model, or strategy model is
  introduced.
- `research-dataset-v1` and its Python schema remain untouched.

## Expected Production Areas

The exact filenames may follow current package conventions, but the affected
areas are expected to be:

- `market-intelligence/.../application/research` for import, orchestration,
  exact snapshot resolution, export, manifest, and internal transport;
- `market-intelligence/.../adapter/tradingcore` for the internal Trading Core
  client;
- `trading-core/.../execution/application` for candidate validation/evaluation
  orchestration and internal transport;
- `trading-core/.../execution/api` or the current internal API convention for the
  bounded controller;
- focused tests in both services;
- the Story implementation report and an operator/runbook document if the
  endpoint needs invocation guidance.

No existing objective research, PAPER, Risk, persistence, migration, broker, or
Python production files should be modified.

## Deterministic Validation Tests

Add focused tests for:

1. valid JSONL import;
2. malformed JSON and unsupported schema rejection;
3. unsupported dataset, sizing, exit, execution, and cost versions;
4. candidate fingerprint mismatch;
5. duplicate candidate fingerprint;
6. invalid quantity, market, side, order type, and T0;
7. exact snapshot success, missing snapshot, identity mismatch, and market/T0
   mismatch;
8. no nearest/latest snapshot fallback;
9. `MARKET` evaluation and `LIMIT` rejection without conversion;
10. time-horizon exit derivation and BUY/SELL direction;
11. partial entry, partial exit, residual exposure, and zero-fill semantics;
12. unavailable commission, unavailable funding, and spot funding
    `NOT_APPLICABLE` semantics;
13. execution-adjusted economics without an invented net value;
14. result disposition preservation for all candidate outcomes;
15. canonical ordering independent of input line ordering;
16. checksum and manifest count reconciliation;
17. candidate/result/manifest identity determinism;
18. JSONL round-trip semantic preservation;
19. the complete deterministic fixture described by Story 0118;
20. objective `research-dataset-v1` regression tests.

## Runtime Proof

After focused tests pass, inspect current runtime evidence without mutation. If
one compatible FeatureSnapshot and both strict historical Order Book cutoffs are
available, run one small always-long `MARKET` candidate with a `PT1H` exit.

Record only non-sensitive facts:

- snapshot identity and T0;
- exit cutoff;
- entry/exit statuses and filled quantities;
- residual exposure;
- execution-adjusted PnL status/value;
- commission and funding statuses;
- cost completeness;
- candidate, result, and manifest fingerprints.

If evidence coverage is insufficient, report the proof as unavailable rather
than fabricating or weakening replay eligibility.

## Validation Sequence

1. `git diff --check`.
2. Focused Market Intelligence research pipeline tests.
3. Focused Trading Core economic pipeline tests.
4. Existing objective dataset and historical replay regressions.
5. `mvn -f market-intelligence/pom.xml test`.
6. `mvn -f trading-core/pom.xml test`.
7. `mvn -f market-intelligence/pom.xml verify` and
   `mvn -f trading-core/pom.xml verify` where module gates permit.
8. SonarQube analysis for affected modules when configured.
9. Safe bounded runtime proof if evidence coverage supports it.
10. Complete diff, status, and second-pass falsification review.

Failures, unavailable runtime evidence, and Quality Gate failures outside Story
scope will be reported explicitly and will not be hidden or fixed by unrelated
refactoring.

## Documentation Reconciliation

Update the Story implementation report with:

- actual transport and invocation contract;
- export filenames and schema version;
- deterministic ordering and checksum rules;
- cost/missingness behavior;
- runtime proof evidence or its absence;
- validation results and residual risks.

Add a small runbook only if the selected invocation path requires operator
instructions. Do not modify ADR status. If implementation introduces a new
architectural authority not covered by ADR-064, prepare an ADR proposal and stop
for human architecture approval.

## Approval Gate

Implementation must not begin until this Implementation Plan is explicitly
approved by the human engineer.
