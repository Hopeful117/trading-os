# Story 0199 - FTMO Account Synchronization Reliability

## Metadata

**ID:** `0199`
**Title:** FTMO Account Synchronization Reliability
**Status:** Repository Analysis pending human approval
**Priority:** P0 - first FTMO trade from Trading OS
**Related Story:** `0198`

## Goal

Diagnose, correct and validate the real Trading Core synchronization path for
the already configured FTMO Free cTrader account, external account `49411967`.
The path must complete with a bounded and explicit HTTP result, preserve
financial and identity guarantees, and expose synchronized facts to Trading OS
without submitting a real order.

## Scope

- Reconstruct the real Angular/API Gateway/Trading Core/Broker Service/cTrader
  synchronization path.
- Reproduce the synchronization with the existing FTMO account using secure
  application mechanisms and bounded attempts.
- Identify the exact stage where progress stops.
- Add only targeted structured diagnostics, timing or timeout handling when
  supported by evidence.
- Correct the smallest confirmed reliability defect.
- Validate nominal, timeout, provider, partial-response, persistence and retry
  behavior without weakening safety or financial admissibility.
- Re-run the real synchronization and classify it as `SYNC_SUCCESS`,
  `SYNC_FAILED_BOUNDED` or `SYNC_NOT_TESTED`.

## Constraints

- Preserve the uncommitted Story 0198 artifacts and all unrelated work.
- Use only the existing FTMO Free cTrader account `49411967`.
- Do not create accounts, rotate credentials, restart OAuth unnecessarily or
  change providers.
- Do not submit, cancel, amend or close a real order.
- Keep `CTRADER_SAFETY_EXECUTION_ENABLED=false`.
- Do not derive or admit equity, margin or deals that the provider does not
  supply.
- Do not bypass T0/T1 or change FTMO, risk, execution or equity policy.
- Do not introduce a broad architectural refactor.
- Do not commit, push or merge without explicit human authorization.

## Acceptance Criteria

- The synchronization flow is mapped from the application route to cTrader.
- The reproduction records bounded timing, route, correlation and last reached
  stage without exposing secrets.
- The demonstrated root cause is distinguished from remaining hypotheses.
- The correction, if required, is minimal and covered by robust tests.
- Provider, network, partial-data, persistence and repeated-call failures are
  bounded and explicitly propagated.
- No incomplete financial fact becomes an invented value or an approval.
- The real FTMO synchronization result is explicitly classified.
- Trading Core and Angular expose the resulting state consistently.
- A Story-local implementation report records root cause, trace, changes, tests,
  real synchronization, safety regression status, blockers and one next action.
