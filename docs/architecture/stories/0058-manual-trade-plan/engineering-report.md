# Engineering Report - Story 0058

## Status

`IMPLEMENTATION COMPLETE - INDEPENDENT REVIEW PASSED; HUMAN COMMIT PENDING`

## Outcome

ADR-047 is implemented for the Trade Plan domain and application boundary.
Manual and opportunity-driven entries now share the same Trade Plan model while
retaining truthful provenance. Manual plans require an authenticated author,
planning context and explicit execution/risk inputs, but do not require a
synthetic Opportunity.

Risk evaluation and execution remain downstream responsibilities. The manual
creation endpoint only persists a proposed Trade Plan.

## Architectural Compliance

* No second broker execution pipeline was introduced.
* No manual path creates an Execution Intent directly.
* No deterministic risk rule was weakened or duplicated.
* Existing opportunity-origin plans remain supported and are backfilled as
  `OPPORTUNITY` by migration.
* Provider-specific fields remain outside the Trade Plan model.
* Authenticated principal identity is used for manual author attribution; the
  request body does not provide an authoritative actor identifier.
* Manual creation now requires `Idempotency-Key`, persists a request fingerprint
  and plan reference, replays identical requests, and rejects conflicting reuse
  with `409`.
* The idempotency record is durable and uniquely scoped by authenticated actor
  and key; initial manual plan identity is deterministic for the same actor/key.

## Validation

* Market Intelligence full suite: 519 tests passed after the idempotency and
  validation changes.
* Story-focused idempotency/domain/application/API tests: passed.
* Trading Core test suite: 604 tests passed after the PAPER short-margin
  regression fixture correction recorded in the current worktree.
* Flyway migration validation: passed through H2 integration suites.
* `git diff --check`: passed.

## Known Limitations

* The frontend does not yet expose the manual-trade form.
* Deployed production authentication has not been exercised; the equivalent
  local authenticated Gateway E2E has passed.
* The complete path from manual plan to risk approval, human decision,
  Execution Intent and PAPER fill belongs to subsequent Stories.
* Production database migration has not been executed in this environment.
* Deployed runtime idempotency replay has not been exercised; local E2E replay
  and conflict validation passed.
* Existing unrelated worktree modifications were preserved and are not part of
  this Story's intended scope.

## Git State

```text
BRANCH = chore/debt-resolution
COMMIT = 0c1e366
PUSH = NO
MERGE = NO
```

## Human Actions Required

1. Review the implementation against ADR-047 and Story 0058.
2. Review the manual request contract and explicit sizing/risk inputs.
3. Preserve Story 0061 as the downstream runtime evidence for the complete
   manual PAPER journey.
4. Create the human-controlled commit for the reviewed documentation/status
   reconciliation.
