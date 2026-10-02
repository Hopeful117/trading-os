# Engineering Report - Story 0051

## Status

`IMPLEMENTED - RUNTIME VALIDATION PENDING`

## Outcome

The broker capability and required-margin boundary was implemented in commit
`40e5258`. Broker Service exposes neutral technical facts while provider details
remain in the Kraken adapter. Trading Core consumes those facts without moving
authorization, risk rules, or PAPER position authority out of their owning
domains.

## Validation

Fresh Maven validation completed after the documentation remediation:

* Broker Service: `201` tests passed.
* Trading Core: `543` tests passed.
* Risk Domain: `21` tests passed.
* `git diff --check`: passed.

## Known Limitations

* Runtime evidence for authenticated internal calls is not attached here.
* Staleness and unavailable-fact behavior require integrated verification.
* No provider other than Kraken is covered by the current implementation.
* Docker Compose is unavailable in the current execution environment, so the
  Kraken sandbox and deployed E2E proof were not run.

## Git State

```text
IMPLEMENTATION_COMMIT = 40e5258
DOCUMENTATION_BRANCH = docs/story-artifact-remediation
PUSH = NO
MERGE = NO
```

## Human Actions Required

1. Review the boundary against ADR-006, ADR-028, ADR-030, ADR-042, and ADR-044.
2. Verify service authentication and fail-closed behavior in the runtime.
3. Execute one controlled Kraken sandbox or equivalent deployed E2E proof.
4. Update the Story status after evidence review.
