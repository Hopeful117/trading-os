# Engineering Report

## Result

Story 0091's Trading Core API workflow is implemented without adding a second
Challenge lifecycle authority. The catalog is provider-neutral, the start
operation remains delegated to the existing application service, and the API
uses authenticated ownership rather than client-supplied owner identity.

## Acceptance Review

- Catalog discovery: implemented and tested.
- Historical version exclusion: implemented and unit tested.
- Authenticated start: implemented and controller tested.
- Existing admission validation: preserved and exercised by existing service and
  Kraken benchmark tests.
- Deterministic public error semantics: implemented through Challenge-scoped
  exception mapping.
- Derived progress read: preserved.
- Kraken benchmark discovery/start: verified by integration tests.
- No HTTP lifecycle transition mutation: preserved.

## Residual Risks

- The catalog currently treats the latest `effectiveFrom` as current and uses
  lexicographic `definitionVersion` only when timestamps tie. If product needs
  explicit publication or retirement, that requires a separate domain decision
  and model change.
- Exact 00:30 UTC Risk Day reconstruction remains outside Story 0091, as
  documented by Stories 0087 and 0090.
- Real broker/sandbox and frontend end-to-end validation remain future work.

## Human Review

No Git commit, push, merge, or branch integration was performed. The working
tree remains available for human diff review.
