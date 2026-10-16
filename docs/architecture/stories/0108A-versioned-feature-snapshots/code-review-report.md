# Code Review Report - Story 0108A

Date: 2026-10-16 · Review type: independent, read-only

## Verdict

**APPROVE**

The broad Market Intelligence boundary is consistent with ADR-060, but the
current implementation does not yet provide sufficient guarantees for snapshot
identity, provenance, and source-artifact alignment.

## Findings

### Major: Snapshot identity conflicts are not enforced

Evidence:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/persistence/JpaFeatureSnapshotRepository.java`
- `market-intelligence/src/main/resources/db/migration/V15__immutable_feature_snapshots.sql`

Persistence uniqueness is based on `snapshot_fingerprint` only. A second row
can therefore be inserted for the same semantic snapshot identity when its
fingerprint differs. The repository does not perform an identity lookup or
reject a different fingerprint for that identity.

Required follow-up: define the complete snapshot identity, enforce it at the
database boundary, and explicitly reject a conflicting fingerprint.

### Major: Source artifact provenance is incomplete

Evidence:

- `FeatureProjectionV1.java`
- `AnalyzerProvenance.java`
- `FeatureSnapshot.java`

The persisted snapshot does not retain enough source artifact identity. OHLC
and Trend provenance omit result/evidence fingerprints, and structural
provenance is reconstructed from content while discarding originating artifact
identity and provenance fields.

Required follow-up: preserve artifact type/version, artifact identity,
input/result/evidence fingerprints, and relevant source provenance.

### Major: Source artifact temporal/context alignment is not validated

Evidence:

- `FeatureSnapshotProjectionService.java`
- `FeatureProjectionV1.java`

The projection requires a strict execution but does not verify that each
artifact matches the execution market, evaluation cutoff, context fingerprint,
or execution linkage. The supplied `analysisExecutionId` is not used for this
validation.

Required follow-up: validate market, cutoff, execution/context linkage, and
artifact provenance before projection; reject mismatches explicitly.

### Minor: Fingerprint omits schema semantics

`FeatureSnapshotFingerprint` includes feature name, datatype, and unit, but not
all schema semantics such as source capability, source semantic, and
missingness policy. Either include these semantics or guarantee that any such
change requires a new projection version.

### Insufficient Evidence: Concurrent conflict recovery

`saveIfAbsent` catches a duplicate insert and reads the existing row within the
same transaction. The behavior after a flush constraint failure may depend on
transaction rollback-only state. No concurrent persistence test was found.

Required follow-up: add a real concurrent persistence test and isolate the
insert/recovery transaction boundaries if runtime behavior requires it.

## Missing Test Coverage

- Different fingerprint with the same semantic identity.
- Concurrent insertion of the same snapshot.
- Full numeric, categorical, and boolean polymorphic round-trip.
- Projection-service execution and context validation.
- Temporal cutoff and artifact provenance mismatch.
- Source artifact identity preservation.
- PostgreSQL-specific persistence behavior.

## Architecture Assessment

The change remains inside Market Intelligence and does not introduce public
APIs, Gateway changes, provider coupling, outcome joins, or direct future-outcome
dependencies. The broad responsibility boundary is consistent with ADR-060.
The identity and provenance gaps prevent full compliance with the accepted
architecture.

## Artifact Assessment

The Story artifacts describe the broad implementation shape accurately, but the
Story and implementation report overstate completion for immutable identity
conflict handling and provenance preservation. Independent review remains
outstanding in the Definition of Done and is now recorded here as incomplete
because the verdict is `NEEDS CHANGES`.

## Validation Evidence

The reviewer did not execute tests or builds. The previously recorded
`mvn -q test`, `mvn -q verify`, and `git diff --check` results were treated as
prior evidence only.

DevLog context retrieval was unavailable because the configured provider
returned an insufficient-quota error. PostgreSQL runtime behavior was not
independently validated.

## Remediation Follow-up

After this review, the implementation was updated to address the findings:

- composite semantic identity is now persisted and unique;
- conflicting fingerprints for one identity are rejected;
- source artifact identity and cache-key fingerprints are retained in
  `AnalyzerProvenance`;
- feature-definition semantics participate in the snapshot fingerprint;
- source market, producing/reusing execution, and cutoff alignment are checked
  before projection;
- regression tests cover identity conflict and source-artifact alignment.

The follow-up review confirms that initial-artifact context validation and typed
round-trip assertions are present. The final remediation now preserves source
evidence fingerprints and ProducedArtifact contract versions for all projected
artifact families.

## Follow-up Review

The final review confirmed that the previous provenance findings are resolved.
Focused tests assert distinct evidence fingerprints for OHLC, Market Structure,
and Trend Context. The final verdict is `APPROVE`, subject to human acceptance
and the remaining PostgreSQL end-to-end limitation.

Validation rerun after the follow-up changes:

- `cd market-intelligence && mvn -q test` - passed.
- `cd market-intelligence && mvn -q verify` - passed.
- `cd market-intelligence && mvn -q -Dtest=FeatureProjectionV1Test test` - passed.
- `git diff --check` - passed.
