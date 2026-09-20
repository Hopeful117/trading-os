# Code Review Checklist - Story 0050

## Reviewed Scope

* `ModeAwareRiskFactsProvider` PAPER and LIVE branches.
* Local PAPER fact mapping and fail-closed behavior.
* Focused provider regression test from commit `fdcb6ac`.
* Separation between Trading Core facts and Risk Domain decisions.

## Findings

No confirmed defect was established from the implementation commit and the
available Story scope. The implementation preserves the intended mode boundary
and does not move repository access into the Risk Domain.

## Review Notes

* PAPER account and trade state remains owned by Trading Core.
* LIVE facts continue to use the broker-backed path.
* Missing or inconsistent facts must not become an approval.
* The implementation does not implement broker capability or margin retrieval;
  those responsibilities belong to Story 0051.

## Human Review Required

* Verify the complete local fact mapping against the current persistence model.
* Run and inspect the affected Trading Core and Risk Domain tests.
* Confirm runtime evidence for reloadable PAPER facts before marking acceptance
  criteria complete.
