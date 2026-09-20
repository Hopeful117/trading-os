# Engineering Report - Story 0051

## Status

`IMPLEMENTED - VALIDATION EVIDENCE PARTIAL`

## Outcome

The broker capability and required-margin boundary was implemented in commit
`40e5258`. Broker Service exposes neutral technical facts while provider details
remain in the Kraken adapter. Trading Core consumes those facts without moving
authorization, risk rules, or PAPER position authority out of their owning
domains.

## Validation

Focused capability and margin-client tests are present in the implementation
commit. The required full Maven validation was not rerun during this
documentation remediation, so this report does not claim a fresh passing
result.

## Known Limitations

* Runtime evidence for authenticated internal calls is not attached here.
* Staleness and unavailable-fact behavior require integrated verification.
* No provider other than Kraken is covered by the current implementation.

## Git State

```text
IMPLEMENTATION_COMMIT = 40e5258
DOCUMENTATION_BRANCH = docs/story-artifact-remediation
PUSH = NO
MERGE = NO
```

## Human Actions Required

1. Review the boundary against ADR-006, ADR-028, ADR-030, ADR-042, and ADR-044.
2. Run Broker Service, Trading Core, and Risk Domain tests.
3. Verify service authentication and fail-closed behavior in the runtime.
4. Update the Story status after evidence review.
