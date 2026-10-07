# Engineering Report - Story 0050

## Status

`IMPLEMENTED - LOCAL VALIDATION COMPLETE; RUNTIME VALIDATION PENDING`

## Outcome

Trading Core remains authoritative for PAPER local financial facts, while LIVE
facts remain broker-backed. Protection state is explicit across the risk
snapshot boundary. Automated plans remain protected-only; manual plans may
preserve an explicitly unprotected state for later stop/take-profit management.
Unavailable stop-loss risk is represented as absent, not as a synthetic zero.

## Validation

The affected module suites pass locally: Risk Domain `26`, Market Intelligence
`489`, and Trading Core `577` tests. Focused regressions also pass for the new
manual/automated protection boundary. `git diff --check` passes.

Runtime validation was rerun with the Docker Compose stack. The official HTTP
surface returned successful registration/login, eligible risk-profile lookup,
PAPER account creation, account listing, and account-scoped empty-position
retrieval after authentication. No credentials or tokens are recorded here.
The complete PAPER decision path was then exercised through a selected-market
scan, opportunity Trade Plan creation, human acceptance, and risk evaluation.
The evaluation failed closed with `PAPER_MARGIN_UNAVAILABLE`; no execution or
position was created.

## Known Limitations

* The completeness of every local fact mapping still requires human review
  against persisted PAPER account and trade data.
* Story 0051 remains the owner of broker capability and margin facts.
* Kraken provider-backed required-margin preview is still unavailable, so the
  complete PAPER risk authorization path remains blocked by design.

## Git State

```text
IMPLEMENTATION_COMMIT = pending
DOCUMENTATION_COMMIT = pending
PUSH = NO
MERGE = NO
```

The remediation branch preserves unrelated worktree modifications and stages
only this Story's documentation for its commit.

## Human Actions Required

1. Review the implementation against ADR-028 and ADR-042.
2. Run the affected Maven tests and record the output.
3. Confirm the runtime PAPER risk-context evidence.
4. Update Story status and acceptance checkboxes after review.
