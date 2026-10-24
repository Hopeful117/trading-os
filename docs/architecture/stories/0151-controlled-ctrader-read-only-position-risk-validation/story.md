# Story 0151 - Controlled cTrader READ-ONLY Position Risk Validation

## Metadata

**ID:** `0151`
**Title:** Controlled cTrader READ-ONLY Position Risk Validation
**Status:** `Blocked - pending Spotware approval`
**Related Stories:** `0147`, `0148`, `0149`, `0150`
**Related ADRs:** `ADR-028`, `ADR-031`, `ADR-040`, `ADR-071`, `ADR-072`, `ADR-073`, `ADR-074`

---

## Goal

Validate the implemented cTrader provider-neutral position-risk fact path against
an authenticated, explicitly bound cTrader account using a controlled READ-ONLY
runtime procedure.

The validation must establish evidence for the existing deterministic Risk
boundary without enabling cTrader execution or changing provider state.

---

## Context

Story 0150 completed the local implementation that carries cTrader position
`usedMargin` and current position `stopLoss` through the provider-neutral
authoritative snapshot into Trading Core Risk facts.

Local deterministic validation is complete. Real provider validation cannot start
until the cTrader Open API application is activated by Spotware. The current
application status is `Submitted`, and authorization fails with
`OA client is not in active state`.

---

## Problem

The repository proves the mapping through deterministic tests, but it does not
yet prove that the real cTrader provider returns the expected position identity,
money precision, margin and stop-loss representation for the bound account.

Without that evidence, the implementation must not be treated as validated for
real provider operation or FTMO readiness.

---

## Scope

- Confirm that Spotware has activated the configured cTrader Open API application.
- Initiate a fresh OAuth authorization through the existing Gateway and Broker
  Service route after activation.
- Bind and verify the intended cTrader external account without rebinding or
  selecting an unexpected account.
- Execute the bounded READ-ONLY authoritative synchronization procedure.
- Capture provider-neutral evidence for account identity, position identity,
  instrument mapping, position margin, stop-loss state, precision, completeness
  and freshness.
- Verify that a provider-reported absent stop remains `UNPROTECTED`.
- Verify that missing or unusable required facts remain Risk-ineligible and fail
  closed.
- Record the runtime result, provider payload shape observations and any gap that
  requires a separate implementation Story.

---

## Out of Scope

- cTrader order placement, cancellation, amendment or close.
- Stop-loss or take-profit mutation.
- Any broker or account state mutation beyond authorization and binding.
- FTMO Challenge formulas, phase transitions or readiness approval.
- New Risk formulas or changes to deterministic Risk behavior.
- Commission, swap, take-profit or position P&L validation not required by the
  current position-risk contract.
- Provider-specific DTOs, credentials or raw secrets in repository artifacts.
- Automatic retries or workarounds for an inactive Spotware application.
- Production code changes unless a separate approved Story authorizes them.

---

## Acceptance Criteria

- [ ] The Spotware OA application is active/approved before runtime validation
      begins.
- [ ] A fresh OAuth flow succeeds through Gateway and Broker Service without
      exposing authorization codes or tokens.
- [ ] The selected external cTrader account matches the intended durable binding;
      identity mismatch fails closed.
- [ ] A bounded READ-ONLY synchronization obtains a coherent, complete and fresh
      provider observation for the selected account.
- [ ] Position identity and provider-neutral instrument mapping are evidenced.
- [ ] Position margin is evidenced from the provider-authoritative `usedMargin`
      fact with the expected money precision.
- [ ] Current position stop-loss evidence is mapped correctly, including explicit
      `UNPROTECTED` behavior when the provider reports no stop.
- [ ] Missing, stale, partial or inconsistent required facts remain unavailable
      or Risk-ineligible and do not produce an approval.
- [ ] No order, cancel, amend, close or protection mutation is attempted.
- [ ] Runtime evidence and residual gaps are recorded in the Story validation
      report without claiming FTMO or execution readiness.

---

## Constraints

- Preserve the Broker Service ownership of cTrader transport, authentication and
  provider-specific mapping.
- Preserve provider-neutral contracts at the Trading Core and Risk boundaries.
- Keep deterministic Risk behavior and fail-closed semantics unchanged.
- Treat LIVE provider facts as authoritative only when identity, completeness and
  freshness are established.
- Do not bypass Spotware application approval or weaken security controls.
- Use only the intended account and controlled READ-ONLY operations.
- Do not store credentials, tokens or sensitive provider payloads in the
  repository.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-028.md`
- `docs/architecture/adr/ADR-031.md`
- `docs/architecture/adr/ADR-040.md`
- `docs/architecture/adr/ADR-071.md`
- `docs/architecture/adr/ADR-072.md`
- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`

---

## Relevant Modules

- `gateway`
- `broker-service`
- `trading-core`

---

## Validation

- Verify the Spotware application state and record the evidence.
- Execute the existing Gateway-to-Broker Service OAuth route with the intended
  account only.
- Execute the bounded READ-ONLY cTrader synchronization procedure.
- Verify the provider-neutral snapshot and Trading Core Risk facts without any
  execution mutation.
- Run targeted regression tests for affected modules if implementation changes
  are required by a separately approved follow-up.
- Record blocked, unavailable or inconclusive runtime outcomes explicitly.

Until Spotware approval is received, the Story remains blocked and no runtime
acceptance criterion may be marked as passed.

---

## Definition of Done

- [ ] Spotware application activation is evidenced.
- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Fresh OAuth and exact-account binding validated.
- [ ] Controlled READ-ONLY position-risk validation completed.
- [ ] No cTrader mutation was attempted.
- [ ] Runtime validation report completed with evidence and residual gaps.
- [ ] Code Review completed if implementation changes were required.
- [ ] Engineering Report completed.
- [ ] Human validation of the resulting evidence completed.
