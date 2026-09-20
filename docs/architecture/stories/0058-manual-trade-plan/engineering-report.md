# Engineering Report - Story 0058

## Status

`IMPLEMENTED - HUMAN REVIEW REQUIRED`

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

## Validation

* Market Intelligence full suite: `349` tests passed.
* Story-focused domain/application/API/persistence tests: `16` passed.
* Trading Core test compilation: passed with tests skipped.
* Flyway migration validation: passed through H2 integration suites.
* `git diff --check`: passed.

## Known Limitations

* The frontend does not yet expose the manual-trade form.
* Gateway and deployed runtime authentication have not been exercised for the
  new endpoint.
* The complete path from manual plan to risk approval, human decision,
  Execution Intent and PAPER fill belongs to subsequent Stories.
* Production database migration has not been executed in this environment.
* Existing unrelated worktree modifications were preserved and are not part of
  this Story's intended scope.

## Git State

```text
BRANCH = story/0058-manual-trade-plan
COMMIT = none
PUSH = NO
MERGE = NO
```

## Human Actions Required

1. Review the implementation against ADR-047 and Story 0058.
2. Review the manual request contract and explicit sizing/risk inputs.
3. Validate the authenticated Gateway route if runtime evidence is required.
4. Approve the next Story for risk evaluation and the human-controlled execution
   journey.
