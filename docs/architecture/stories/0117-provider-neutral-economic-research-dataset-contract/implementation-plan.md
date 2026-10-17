# Implementation Plan - Story 0117

## Plan Status

Ready for human approval. No production implementation has been performed from
this plan yet.

## Design Decision

Implement the provider-neutral contract in Trading Core's existing economic
domain package:

```text
trading-core/.../execution/domain/economic/research
```

This preserves the boundary established by ADR-064: Trading Core owns candidate
validation, execution, costs, fingerprints, and economic accounting. Market
Intelligence can produce candidate data through a neutral serialized contract or
future adapter without taking a compile-time dependency on Trading Core domain
classes. No cross-module dependency or persistence is introduced in this Story.

The existing `StrategyIntent` and `EconomicResult` remain the execution and
accounting types. New types are immutable records that reference them rather
than duplicating their behavior.

## Production Types

### `StrategyHypothesisIdentity`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/StrategyHypothesisIdentity.java`

Fields:

- stable hypothesis ID;
- hypothesis version.

Validation rejects blank values. No source code, expression, notebook, or
executable strategy is accepted.

### `FeatureSnapshotReference`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/FeatureSnapshotReference.java`

Fields:

- market ID;
- evaluation cutoff;
- feature projection version;
- context fingerprint;
- FeatureSnapshot content fingerprint.

The reference deliberately excludes operational `snapshotId` and
`analysisExecutionId`. It is the cross-service semantic join key and does not
embed feature values. The canonical FeatureSnapshot identity fingerprint is
preserved as supplied by Market Intelligence; Trading Core does not recompute
a second identity algorithm.

### `FixedBaseQuantityPolicy`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/FixedBaseQuantityPolicy.java`

Fields:

- policy version;
- positive base quantity.

The policy exposes deterministic identity material. Quantity changes must change
the identity.

### `TimeHorizonExitPolicy`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/TimeHorizonExitPolicy.java`

Fields:

- policy version;
- positive `Duration` horizon.

The policy owns `Texit = T0 + horizon`; no second authoritative horizon is
introduced in the candidate or definition.

### `EconomicResearchDatasetDefinition`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchDatasetDefinition.java`

Fields:

- definition version;
- population policy version;
- ordered market IDs;
- T0 start/end bounds;
- FeatureSnapshot projection version;
- evidence policy version;
- hypothesis identity;
- fixed-base sizing policy;
- entry policy version;
- time-horizon exit policy;
- execution model version;
- transaction-cost model version;
- research cutoff policy version;
- required cost completeness;
- accepted execution statuses;
- optional exact objective outcome join policy version.

The constructor normalizes immutable collections and rejects duplicate/blank
policy material. `fingerprint()` is derived from all semantic definition fields
using canonical sorted set material. Operational creation time, UUIDs, code
versions, and export checksums are excluded.

### `EconomicResearchCandidate`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchCandidate.java`

Fields:

- immutable dataset definition;
- FeatureSnapshot reference;
- hypothesis identity;
- existing `StrategyIntent`;
- sizing policy;
- exit policy;
- execution model version;
- transaction-cost model version.

The candidate validates policy/model and feature-projection consistency with its
definition at construction. V1 accepts only MARKET intents. Its fingerprint
includes the FeatureSnapshot canonical identity, intent semantic fields,
quantity, side, horizon, hypothesis, execution model, and cost model. It does
not include random IDs or operational decision time as an identity component
beyond the intent's existing semantic material where required by the current
model.

### `EconomicResearchResult`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchResult.java`

Fields:

- candidate fingerprint;
- optional exact objective outcome fingerprint;
- existing `EconomicResult`;
- disposition/status;
- optional exclusion or censoring reason;
- result fingerprint.

This is an export envelope only. All economic values and status semantics remain
inside `EconomicResult`. The result supports execution-adjusted/degraded results
and preserves explicit missingness through the nested result. Its fingerprint is
always derived from canonical semantic material; a supplied fingerprint is
accepted only when it matches the derived value.

### `EconomicResearchDatasetManifest`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchDatasetManifest.java`

Fields:

- export schema version;
- dataset definition fingerprint;
- candidate/result counts;
- execution status counts;
- cost completeness counts;
- result disposition counts;
- residual exposure total/count;
- ordered candidate fingerprints;
- ordered result fingerprints;
- candidate/result JSONL SHA-256 checksums;
- source bundle fingerprint.

The manifest validates count consistency, result disposition reconciliation,
bounded execution/cost counts, and unique fingerprints before canonicalizing all
maps/lists. It is separate from objective `DatasetManifest` and has no
objective sample fields.

### `EconomicResearchResultDisposition`

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchResultDisposition.java`

Enum values:

- `EVALUATED`;
- `EXECUTION_UNAVAILABLE`;
- `EXCLUDED`;
- `CENSORED`.

This keeps export disposition separate from `EconomicResultStatus` and avoids
overloading accounting status with dataset population semantics.

## Serialization

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchCanonicalSerializer.java`

Implement a small explicit serializer that builds `LinkedHashMap` documents in
the declared schema order and delegates scalar JSON encoding to the module's
Jackson `ObjectMapper`. The serializer will expose:

- candidate JSON document;
- result JSON document;
- manifest JSON document.

Nested documents will use explicit field insertion rather than relying on record
reflection order. Maps, collections, fills, and cost evidence will be serialized
through explicit structures. Maps and collections will be sorted before
insertion. Times will use `Instant.toString()`. BigDecimal values will be passed
as numeric values and not converted through `double` or string formatting. Nulls
for unavailable fields will remain JSON null; status/completeness fields remain
explicit.

The serializer will not write files or create a bundle directory. Existing
`ResearchDatasetBundleExporter` remains untouched. A later export application
service can compose the three documents into:

```text
economic-intents.jsonl
economic-results.jsonl
economic-dataset-manifest.json
README.md
```

## Fingerprint Utility

File:
`trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchFingerprint.java`

Provide one package-local/public utility for length-delimited SHA-256 material,
normalizing nulls explicitly and preserving collection order unless the caller
has already declared a semantic set. This prevents each new record from
reimplementing subtly different digest logic.

The utility will not reuse `market-intelligence`'s `ArtifactFingerprint` class
because the modules are independent and Trading Core already contains the
required SHA-256 conventions.

## Tests

Add:

`trading-core/src/test/java/com/hope/trading/trading_core/execution/economic/research/EconomicResearchContractTest.java`

Cover:

1. blank/invalid policy and identity values are rejected;
2. fixed quantity changes candidate identity;
3. side/order/quantity changes candidate identity;
4. horizon/version changes candidate identity;
5. hypothesis version changes candidate identity;
6. execution/cost model changes candidate identity;
7. collection insertion order does not change definition/manifest identity;
8. FeatureSnapshot payload is absent from candidate serialization;
9. optional exact outcome reference is preserved and no outcome is accepted;
10. degraded and unavailable cost states retain explicit status and null net PnL;
11. partial fills and residual exposure remain visible through nested result;
12. canonical serialization is byte-stable for equivalent input records;
13. BigDecimal values retain numeric precision;
14. manifest counts reject inconsistent inputs.

No existing objective or economic evaluator test is changed unless a compilation
impact requires an import-only adjustment.

## Validation Sequence

1. `git diff --check`.
2. Focused Trading Core test:
   `mvn -pl trading-core -Dtest=EconomicResearchContractTest test` from the
   repository root, or the module-equivalent command if the root has no Maven
   reactor.
3. Existing economic regression tests:
   `mvn -f trading-core/pom.xml -Dtest='*Economic*Test' test`.
4. Existing Market Intelligence research/objective tests, confirming no frozen
   behavior changed.
5. Trading Core `verify` if the focused tests pass and the module environment
   supports the Jacoco gate.
6. Inspect the complete diff and status; confirm no persistence, migration,
   Python, objective research, PAPER, Risk, or replay files changed.

Blocked/unavailable commands will be reported with the exact reason rather than
being treated as passing.

## Documentation Reconciliation

The Story and Repository Analysis already describe the new boundary. After
implementation, inspect ADR-064 and determine whether the contract details need
a new `ADR-065` proposal. Do not mark any ADR `Accepted` without explicit human
architecture approval. The Implementation Report will record the outcome.

## Risks and Mitigations

- **Cross-module identity drift:** use the exact semantic FeatureSnapshot fields
  already exposed by the domain and test deterministic joins.
- **Accidental objective contract coupling:** keep all new types in the Trading
  Core economic research package and do not edit objective research classes.
- **False net return claims:** expose nested `EconomicResult` unchanged and do
  not calculate a denominator for `netReturn`.
- **Missing cost collapsed to zero:** serialize status/completeness and null
  amounts explicitly.
- **Nondeterministic export:** sort declared set/map fields and construct nested
  documents explicitly.
- **Scope expansion into execution:** keep this Story to immutable contracts,
  fingerprints, serializers, and tests; do not add evaluation orchestration.

## Approval Gate

Implementation must not begin until this Implementation Plan is explicitly
approved by the human engineer.
