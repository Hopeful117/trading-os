# Implementation Plan: Python Research Dataset Consumer Validation

## Scope

Implement the approved Story 0109A scope:

- export a deterministic research dataset bundle from Market Intelligence;
- expose a stable manifest and JSONL sample contract;
- provide an offline Python validator and diagnostics command;
- validate Java-produced fixtures from Python without reimplementing Java domain serialization;
- preserve strict evidence, outcome, fingerprint, and fail-closed semantics.

Out of scope:

- ML training or model serving;
- public REST endpoints;
- historical backfill or repair jobs;
- broker, execution, risk, or trading-authority changes;
- changes to the canonical feature projection or outcome definitions.

## Design Decisions

### Cross-language fingerprint contract

The Java manifest will include the exact ordered fingerprint material used by
`ResearchDatasetBuilder`, alongside the existing dataset fingerprint. Python
will not reconstruct domain serialization or Java `toString()` behavior. It will
canonicalize the supplied material using the documented contract and verify the
SHA-256 dataset fingerprint.

The contract will be:

1. Java emits one UTF-8 string per fingerprint-material entry.
2. Entries are sorted using Java lexicographic ordering before hashing.
3. Entries are joined with the ASCII unit separator (`U+001F`) without a
   trailing separator.
4. The UTF-8 bytes are hashed with SHA-256 and rendered as lowercase
   hexadecimal.

This makes the existing Java fingerprint authoritative while giving Python an
exact, inspectable verification input.

### Bundle format

Each export directory contains:

- `dataset-manifest.json`: deterministic JSON with the dataset manifest, fingerprint
  material, export schema version, and SHA-256 of `samples.jsonl`;
- `samples.jsonl`: one deterministic sample per line, ordered by the existing
  builder ordering;
- `README.md`: generated usage and provenance notes, including the source
  command and fail-closed validation expectations.

The manifest contains no secrets and does not expose broker/provider payloads.
The sample file contains the existing domain sample projection only.

### Invocation

The export and validation paths will be implemented as explicit application
services and a controlled command entry point. The export command will require
an output directory and population parameters, use the application clock for
metadata, and fail if the requested population is empty or semantically
inconsistent. Python validation will run only against an existing bundle and
will never access the Java database or network.

## Java Changes

### Domain and application contracts

- Extend `DatasetManifest` with the fingerprint-material entries required by
  the cross-language contract and the export schema version.
- Add an application-level export request/result contract carrying population
  selection, output location, and export metadata.
- Add the missing feature-snapshot population query to the repository port and
  JPA adapter, constrained by cutoff range, projection version, and evidence
  policy.
- Reuse exact outcome-fingerprint lookup for the selected outcome population;
  do not introduce a second outcome-selection algorithm.

### Export orchestration

- Add a `ResearchDatasetExportService` that:
  - loads the requested snapshot population;
  - loads selected outcomes by their fingerprints;
  - delegates sample construction to `ResearchDatasetBuilder`;
  - writes JSONL using stable ordering and explicit UTF-8/newline rules;
  - computes the sample-file checksum;
  - writes the manifest and README atomically through a temporary directory or
    temporary files followed by rename;
  - rejects empty populations, missing selected outcomes, and mismatched
    population parameters.
- Keep `ResearchDatasetBuilder` as the single source of dataset semantics.
- Add a deterministic manifest serializer rather than relying on incidental
  reflection/property ordering.
- Add a controlled Spring command runner or equivalent existing application
  entry point, guarded by an explicit export command/profile so normal service
  startup is unchanged.

### Tests

- Unit-test exact fingerprint-material generation and the documented hash
  contract.
- Unit-test stable sample ordering and JSONL serialization.
- Unit-test fail-closed behavior for empty snapshots, missing outcomes,
  `STRICT_OBSERVED` violations, and inconsistent population metadata.
- Add an integration test that writes a complete golden bundle using fixed
  clock/repository fixtures.
- Add a Java fixture test asserting the manifest, JSONL, checksum, and README
  are reproducible for the same semantic inputs.

## Python Changes

Create a small offline package under `research/ml` with a standard
`pyproject.toml` and no network/runtime dependency on Java services.

### Validator

- Parse and validate manifest schema/version.
- Verify required files and `samples.jsonl` SHA-256.
- Verify the dataset fingerprint from manifest fingerprint material.
- Validate sample JSONL syntax and required fields.
- Enforce row-level invariants:
  - unique sample identity;
  - declared market, horizon, projection, and evidence policy;
  - `known_at <= close_time`;
  - strict-observed provenance/missingness rules;
  - outcome presence and outcome fingerprint equality;
  - no unexpected fields or malformed values.
- Return structured diagnostics and a non-zero exit code on any violation.

### CLI

Provide a command such as:

```text
python -m research_dataset validate <bundle-directory>
python -m research_dataset diagnose <bundle-directory>
```

`validate` is fail-closed and concise by default. `diagnose` emits stable,
machine-readable JSON diagnostics suitable for CI and local investigation.

### Tests

- Add a golden Java-produced fixture bundle.
- Test valid validation and checksum/fingerprint success.
- Test tampered manifest, tampered JSONL, duplicate rows, unknown fields,
  missing outcomes, future-known timestamps, strict-observed violations, and
  fingerprint mismatches.
- Test CLI exit codes and deterministic diagnostics.

## Validation Sequence

1. Run targeted Market Intelligence tests for builder, repositories, and export.
2. Run the Java golden-bundle integration test.
3. Run Python unit and CLI tests against the committed golden fixture.
4. Run the validator against the Java-generated fixture as an explicit
   cross-language check.
5. Run `git diff --check` and inspect the complete diff.
6. Run broader module validation if the targeted tests pass and the module's
   build is available.

## Risks and Mitigations

- **Fingerprint drift:** expose exact material from Java and test the hash with
  a golden fixture; Python never guesses domain serialization.
- **Partial exports:** write to a temporary location and publish only after all
  files and checksums succeed.
- **Accidental service behavior change:** guard the command entry point and keep
  normal application startup unchanged.
- **Contract leakage:** serialize only the existing public research sample and
  manifest fields; never export credentials or provider payloads.
- **Fixture staleness:** make the Java golden fixture a tested output of the
  current exporter, and keep the Python test fixture byte-for-byte aligned.

## Approval Gate

This plan requires human approval before implementation begins.
