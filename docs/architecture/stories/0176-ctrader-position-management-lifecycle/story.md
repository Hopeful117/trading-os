# Story 0176 - cTrader Position Management Lifecycle

## Metadata

**ID:** `0176`
**Title:** cTrader Position Management Lifecycle
**Status:** Draft
**Related ADRs:** `ADR-040`, `ADR-042`, `ADR-045`, `ADR-071`, `ADR-073`, `ADR-078`
**Related Stories:** `0170`, `0173`, `0174`, `0175`

---

## Goal

Complete the cTrader provider write-side position lifecycle required before a
separately authorized authenticated DEMO end-to-end validation. Trading Core's
existing broker-neutral close workflow must resolve and reconcile cTrader
positions without exposing provider protocol details outside Broker Service.

No broker mutation is authorized by this Story.

## Context

Trading Core already exposes the human-controlled position-close workflow and
Broker Service already defines `PositionManagementCapability`. Kraken provides
the current implementation, while `CTraderBrokerProvider` does not currently
register a cTrader position-management capability.

Story 0173 established durable cTrader position identity and authoritative
position/deal reconciliation. Stories 0174 and 0175 established the shared
cTrader instrument, quantity, precision and safety contracts for provider
operations.

## Problem

A close request for a cTrader-backed account currently fails when Broker
Service resolves `PositionManagementCapability`. The cTrader adapter therefore
cannot safely perform or reconcile a full or partial close through the existing
provider-neutral workflow.

## Scope

- Implement a cTrader `PositionManagementCapability` adapter.
- Resolve exactly one cTrader position using its durable provider identity.
- Validate broker-account ownership, provider identity and DEMO/LIVE safety
  boundaries before provider mutation.
- Implement full and partial close using the existing cTrader Open API contract.
- Reuse `CTraderExecutionInstrumentContract` for provider volume conversion and
  min/max/step validation.
- Preserve explicit idempotency, unknown outcomes and no-blind-retry behavior.
- Reconcile acknowledged and unknown outcomes from authoritative cTrader
  position/deal observations.
- Preserve partial-close quantity and full-close terminal facts through the
  existing Story 0173 reconciliation path.
- Prevent duplicate/concurrent unsafe mutation using existing command and
  provider-boundary mechanisms.
- Add focused capability, provider, reconciliation and regression tests.
- Preserve cTrader mutation disabled by default.

## Out of Scope

- Stop-loss or take-profit modification unless required by the existing
  broker-neutral capability contract.
- Any authenticated cTrader DEMO or LIVE mutation.
- Automatic position management, AI decisions, strategy activation or risk
  policy changes.
- Trading Core provider-specific logic or a new position persistence model.
- Market Data, Level II, order-book, storage, backfill or retention changes.
- Frontend redesign or cTrader-specific public routes.
- Multi-broker expansion or Kraken semantic changes beyond regression safety.

## Acceptance Criteria

- [ ] cTrader registers and resolves `PositionManagementCapability`.
- [ ] Full close targets one exact provider position by durable provider
      identity and validates account, provider and environment identity.
- [ ] Partial close is supported according to cTrader semantics and rejects
      zero, negative, oversized, below-minimum or non-representable quantities.
- [ ] Full and partial close reuse the authoritative cTrader instrument volume
      contract without independent scaling or dangerous rounding.
- [ ] LONG and SHORT close semantics are deterministic.
- [ ] Provider rejection is mapped to the existing broker-neutral error model.
- [ ] Ambiguous provider outcomes become `UNKNOWN` and require reconciliation;
      blind retry is not allowed.
- [ ] Acknowledged, unknown, partial and full outcomes reconcile from
      authoritative provider facts.
- [ ] Already-closed and concurrent equivalent commands produce no unsafe
      duplicate provider mutation.
- [ ] Existing Story 0173 no-resurrection and restart invariants remain valid.
- [ ] Trading Core remains broker-neutral and the frontend/Gateway workflow is
      reused without cTrader-specific public endpoints.
- [ ] `CTRADER_SAFETY_EXECUTION_ENABLED=false` remains the default and all
      automated validation performs zero broker mutations.
- [ ] Broker Service, Trading Core, Gateway and applicable frontend validation
      pass, including `git diff --check`.

## Constraints

- Preserve ADR-040's distinct position-management capability boundary.
- Preserve ADR-042's opaque provider reference and provider-specific command
  addressability rules.
- Preserve ADR-071 safety, idempotency and unknown-outcome behavior.
- Preserve ADR-078's PAPER/LIVE execution boundary.
- Keep cTrader protocol mapping and errors inside Broker Service.
- Reuse Story 0173 reconciliation and Stories 0174-0175 instrument semantics.
- Keep `CTRADER_SAFETY_EXECUTION_ENABLED=false` during implementation and
  validation.
- Do not inspect, print, commit or persist credentials outside existing secret
  handling.
- Do not move PostgreSQL storage or apply Market Data V21-V23 runtime changes.
- Preserve unrelated local and untracked runtime artifacts.

## Relevant ADRs

- `docs/architecture/adr/ADR-040.md`
- `docs/architecture/adr/ADR-042.md`
- `docs/architecture/adr/ADR-045.md`
- `docs/architecture/adr/ADR-071.md`
- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`

## Relevant Modules

- `broker-service`
- `trading-core`
- `gateway`
- `trading-os-web`

## Validation

- Focused cTrader capability, provider protocol, quantity and reconciliation
  tests with mocked provider responses.
- Complete `broker-service` and `trading-core` Maven test suites.
- Affected Gateway tests and existing Angular position-management tests.
- Angular full test suite and production build when the frontend contract is
  affected.
- `git diff --check` and complete diff review.
- Read-only runtime inspection only; no provider mutation or authenticated
  close is authorized.

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Documentation reconciliation completed
- [ ] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created
- [ ] Pull request externally validated by the human engineer
