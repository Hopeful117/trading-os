# Engineering Report - Story 0051

## Status

`CLOSED - HUMAN REVIEWED, COMMITTED AND PAPER RUNTIME VALIDATED`

## Outcome

The broker capability and required-margin boundary is implemented in the Story
commits. Broker Service exposes neutral technical facts while provider details
remain in the Kraken adapter. Trading Core consumes those facts without moving
authorization, risk rules, or PAPER position authority out of their owning
domains.

## Validation

Fresh Maven validation completed after the documentation remediation:

* Broker Service: `209` tests passed.
* Broker Service `mvn verify`: passed; JaCoCo checks met.
* Trading Core: `580` tests passed.
* Risk Domain: `26` tests passed.
* `git diff --check`: passed.

## Known Limitations

* Authenticated local runtime evidence confirms the internal capabilities and
  margin-preview endpoints; controller security integration tests cover positive,
  missing actor, account mismatch, and invalid credential cases.
* Staleness and unavailable-fact behavior are covered by the Trading Core client
  tests and provider-unavailable tests.
* The Kraken preview is provider-backed by `AssetPairs` facts and was confirmed
  locally against the configured Kraken endpoint.
* No provider other than Kraken is covered by the current implementation.
* The proof used the configured public Kraken endpoint through the local Docker
  deployment; a separate sandbox/deployed-environment proof is not required by
  the Story acceptance criteria and remains an optional follow-up.
* The fresh authenticated PAPER proof now reaches Risk with provider-backed
  required-margin facts and no `PAPER_MARGIN_UNAVAILABLE` result.

## Git State

```text
IMPLEMENTATION_COMMIT = 3dcbf283070be9df0f0d0e9966e8a20bfd0de95
DOCUMENTATION_COMMIT = f2eee8cdc729d74ccd2326bc07f71e376ab6af49
DOCUMENTATION_BRANCH = main (current workspace branch)
PUSH = NO
MERGE = NO
```

## Human Actions Required

1. Preserve the fresh runtime proof with the Story documentation commit.
2. Continue with the remaining debt commits on the dedicated debt-resolution branch.
