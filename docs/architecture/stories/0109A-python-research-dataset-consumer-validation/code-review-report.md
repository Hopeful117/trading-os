# Code Review Report - Story 0109A

Date: 2026-10-16 · Review type: independent, read-only

## Verdict

**NEEDS HUMAN REVIEW**

The independent review initially found four major correctness gaps and one
minor test portability issue. All identified findings were remediated in the
current worktree and covered by targeted regression tests. Human acceptance
remains outstanding.

## Findings and Remediation

### Major: Missing market scope could expand the export

The export command previously converted an absent `marketIds` field into an
empty set, where the domain interprets an empty set as all markets. The command
now requires `marketIds` to be present and to be an array.

### Major: Rows could escape the declared population

The Python validator now verifies market UUIDs, constrained market membership,
and the half-open cutoff interval declared by the manifest. It also requires
the row evidence policy to match the manifest policy.

### Major: Conflicting duplicate identities could pass validation

The validator now tracks the semantic identity composed of market, cutoff,
horizon, and feature identity fingerprint. Repeated identities are rejected
even when their sample IDs or sample fingerprints differ.

### Major: Sample IDs were not bound to fingerprints

The validator now reproduces Java's `UUID.nameUUIDFromBytes` derivation from
the sample fingerprint and rejects mismatches.

### Minor: Golden fixture depended on the working directory

The Java test now resolves the fixture from the Maven project base directory,
so invocation from the module or repository root uses a stable path.

## Architecture Assessment

The implementation preserves the intended boundary:

- Java remains authoritative for facts, eligibility, outcomes, and semantic
  fingerprints.
- Python is offline and validates exported facts only.
- No broker, execution, risk mutation, public API, frontend, or deployed
  service was introduced.
- Strict observed evidence and explicit outcome selection remain enforced.

## Validation Evidence

- Python validator tests: 15 passed.
- `ResearchDatasetBuilderTest`: 13 passed.
- `mvn verify`: 627 tests passed and JaCoCo coverage checks passed.
- `git diff --check`: passed.

The full Maven suite produced existing infrastructure warnings but no failures.
DevLog context was unavailable because the configured provider reported
exhausted quota.

## Residual Risks and Gaps

- No deployed or PostgreSQL-backed production export invocation was performed.
- The repository currently has no persisted FeatureSnapshot population, so
  runtime data readiness remains unverified beyond the empty-bundle path.
- Human review, commit, and integration remain outstanding.

## Approval Boundary

This report is an independent review record and recommendation. It does not
grant human approval or authorize commit, push, merge, or pull-request
creation.
