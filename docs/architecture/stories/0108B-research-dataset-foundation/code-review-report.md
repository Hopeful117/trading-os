# Code Review Report - Story 0108B

Date: 2026-10-16 · Review type: independent, read-only

## Verdict

**NEEDS HUMAN REVIEW**

The implementation preserves the Market Intelligence boundary and the
deterministic feature/outcome separation. The initial independent review found
several correctness gaps; those findings were remediated and regression-tested.
Human acceptance remains outstanding.

## Initial Findings and Remediation

### Major: Export could diverge from the declared schema

The initial implementation did not validate schema completeness or types and
could append unknown fields. The builder now validates exact schema membership
and data types, while the exporter emits only schema-defined fields in declared
order.

### Major: Duplicate selected outcomes could be input-order dependent

The initial implementation used `findFirst()` for selected outcomes. Conflicting
records sharing a selected identity are now rejected deterministically. Exact
duplicate records remain equivalent.

### Major: Duplicate feature snapshots were silently discarded

Repeated snapshots are now represented as `DUPLICATE_FEATURE_SNAPSHOT`
exclusions. Conflicting snapshots sharing one semantic identity fail loudly.
Multiple configured horizons are still generated for the first snapshot rather
than being mistaken for duplicate snapshots.

### Minor: Missing outcomes were misclassified

An empty outcome population now produces `OUTCOME_MISSING`, while an available
but non-selected or incompatible identity remains a version/identity mismatch.

### Major: Projection version was not bound to the schema

The follow-up review identified that arbitrary population and snapshot versions
could otherwise be paired with the V1 schema. The builder now rejects any
population version other than the supported `feature-projection-v1` and includes
a regression test.

## Architecture Assessment

The implementation remains inside Market Intelligence. It does not introduce
public APIs, Gateway routes, frontend changes, provider coupling, persistence,
direct future-outcome dependencies, labels, strategy decisions, or ML behavior.
The explicit outcome selection and strict observed feature requirements preserve
the no-lookahead boundary defined by ADR-060 and ADR-061.

## Validation Evidence

- `mvn -q -Dtest=ResearchDatasetBuilderTest test` - passed after remediation.
- `mvn -q verify` - passed after remediation.
- `git diff --check` - passed.

The reviewer did not perform PostgreSQL runtime validation. DevLog context was
unavailable because the configured provider reported an insufficient-quota
error.

## Residual Risks and Gaps

- The V1 boundary has no deployed consumer or Python reader validation.
- PostgreSQL validation is not applicable to the current no-persistence scope,
  but remains a future integration concern if dataset materialization is added.
- Human review, commit, and integration remain outstanding.

## Approval Boundary

This report is an independent review record and recommendation. It does not
grant human approval or authorize commit, push, merge, or pull-request creation.
