# Code Review - Story 0183

## Scope Reviewed

Reviewed the complete local diff against `main`, including cTrader observation
mapping, V2 snapshot completeness, Trading Core synchronization, mode-aware risk
fact completeness, the new LIVE CFD reconciler, tests, and Story artifacts.

## Findings

The initial independent review identified four findings. They were corrected in
the current working tree. Follow-up reviews also identified and corrected
fail-closed validation gaps in V2 synchronization and added validation-order
coverage.

- The reconciler is now a Spring service and is invoked through the authenticated
  read-only V2 synchronization boundary.
- Snapshot freshness now uses `providerObservedAt`.
- Duplicate provider position references are rejected before synchronization
  mutation.
- Full canonical contract precision, quantity constraints, and provenance are
  required and compared before `MATCHED`.
- V2 broker synchronization rejects PAPER accounts before calling Broker
  Service or mutating local state.
- Required snapshot collections are validated before reconciliation iteration.
- Regression tests cover PAPER no-call/no-mutation behavior and malformed
  snapshot fail-closed behavior.
- V2 synchronization validates complete provider account facts before mutation,
  and identity/provider errors remain prioritized over malformed collections.

The review specifically verified:

- provider equity is mapped directly and is not reconstructed locally;
- current used margin is not wired to `RequiredMarginPort`;
- signed quantity and provider position identity remain provider-neutral;
- cTrader volume conversion stays inside Broker Service;
- canonical CFD classification and contract availability fail closed;
- matching uses provider position reference and rejects duplicate topology;
- incomplete or stale observations produce `UNAVAILABLE` before absence can be
  interpreted as closure;
- LIVE synchronization uses provider equity rather than local recalculation;
- PAPER valuation/settlement paths remain unchanged and do not call Broker
  Service for financial truth;
- no settlement, compatibility, Risk Domain, migration, or broker mutation was
  introduced.
- the new runtime boundary and all four review findings have regression tests.

## Residual Risk

The existing synchronization service still maintains local provider-linked
position linkage for historical reconciliation, as established by Story 0173.
That linkage is not treated as authoritative LIVE financial truth. A future
Phase 7 must consume the reconciler's provider facts without routing current
used margin into proposed-order required margin.

## Validation Evidence

- Trading Core full suite: PASS.
- Broker Service full suite: PASS.
- Focused LIVE reconciliation and cTrader mapping suites: PASS.
- `git diff --check`: PASS.

Review recommendation: ready for human review and commit; no workflow or Git
integration action was performed by the agent. The follow-up review found no
remaining major or functional defects after the final validation-order test.
