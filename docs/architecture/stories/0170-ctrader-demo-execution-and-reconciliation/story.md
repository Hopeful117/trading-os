# Story 0170 - Controlled cTrader Demo Execution and Reconciliation

## Metadata

**ID:** `0170`
**Title:** Controlled cTrader Demo Execution and Reconciliation
**Status:** Draft - Implementation Plan Awaiting Approval
**Related ADRs:** `ADR-071`
**Related Stories:** `0145`, `0146`, `0147`, `0148`, `0149`, `0150`, `0151`, `0154`, `0167`, `0169`

---

## Goal

Provide a provider-neutral execution path for the explicitly selected cTrader
demo account, while preserving deterministic Challenge/Risk controls, explicit
human authorization, idempotency, and reconciliation of uncertain provider
outcomes.

The Story must enable one bounded runtime demo execution proof without enabling
live-account execution or autonomous trading.

---

## Context

The cTrader OAuth binding and authoritative read-only synchronization path are
available. The bound account currently becomes `CONNECTED` only with read-only
permissions and the cTrader provider exposes no execution capability.

cTrader Open API requires the OAuth access token to have `trade` permission for
new orders. The cTrader `isLive` field is a provider transport fact, but it is
not universally equivalent to real-money execution: FTMO FREE accounts may be
simulated/demo accounts while cTrader reports `isLive=true`. The implementation
must therefore resolve an effective execution environment from verified
provider/business facts, must not infer demo safety from a local display label,
and must fail closed when that classification is unknown or contradictory.
The cTrader transport endpoint may still be `live` for this provider account;
transport environment and economic execution environment are separate facts.

FTMO remains the Challenge/business provider and cTrader remains the
execution-platform/broker provider. Trading Core owns execution authorization,
Risk, Challenge state, and reconciliation decisions. Broker Service owns the
cTrader adapter, transport, payload mapping, provider errors, and technical
capability composition.

---

## Problem

The current cTrader integration can authenticate, bind an account, and read
authoritative account facts, but cannot submit or reconcile an order. The
generic execution pipeline therefore cannot complete a controlled cTrader demo
proof, and no provider capability advertises cTrader execution support.

The current OAuth request uses only the `accounts` scope. Expanding the scope
without implementing provider permission detection, safety admission, and
unknown-outcome reconciliation would create an unsafe partial integration.

---

## Scope

- Extend cTrader authorization to request and validate the minimum required
  trading permission for the selected demo account.
- Detect and persist provider permissions without granting `TRADE` when the
  provider has not confirmed it.
- Implement the provider-neutral `ExecutionCapability` inside the cTrader
  adapter only.
- Map broker-neutral execution requests to cTrader order requests, including
  symbol identity, side, order type, volume precision, client idempotency key,
  and protective stop requirements.
- Preserve acknowledged, rejected, and unknown outcomes as distinct states.
- Reconcile an uncertain order result using authoritative cTrader order/deal
  observations before any retry is considered.
- Require a fresh and complete authoritative snapshot before execution
  admission.
- Require an active compatible FTMO Challenge context and deterministic Risk
  approval before a demo order can be submitted.
- Require explicit human execution authorization for the bounded runtime proof.
- Verify the external cTrader account's effective execution environment from
  provider/business facts. FTMO FREE accounts may be provider-reported `isLive`
  while remaining simulated; unknown or contradictory classifications must fail
  closed.
- Validate one minimal, bounded demo order with an attached protective stop,
  followed by deterministic confirmation and closure/reconciliation.
- Add focused adapter, contract, execution-pipeline, permission, idempotency,
  failure, and runtime validation tests.

---

## Out of Scope

- Live-account execution or any automatic live/demo account selection.
- Autonomous trading, strategy selection, opportunity ranking, or AI authority.
- Changes to FTMO Challenge rules, loss limits, phase semantics, or Risk
  formulas without a separate approved Story.
- Removal or weakening of human authorization, ownership, or fail-closed gates.
- Blind retries after timeouts or ambiguous provider responses.
- cTrader market-data ingestion or a new instrument catalogue.
- Provider-specific DTOs, OAuth details, or cTrader payloads in Trading Core,
  Risk Domain, Market Intelligence, or the frontend.
- Withdrawal, transfer, or account-management operations.
- General multi-broker execution redesign.

---

## Acceptance Criteria

- [ ] cTrader OAuth requests the minimum documented trading permission and the
      selected account's effective permissions are verified before execution.
- [ ] A cTrader account is marked execution-capable only when the provider
      confirms `TRADE` permission and the effective execution environment is
      explicitly classified as eligible.
- [ ] An unknown or contradictory effective environment, missing trade
      permission, stale snapshot, incomplete snapshot, or incompatible
      Challenge context blocks execution before provider submission.
- [ ] The FTMO FREE semantic exception is resolved inside Broker Service and is
      not represented as a cTrader-specific branch in Trading Core.
- [ ] Trading Core remains provider-neutral and receives only broker-neutral
      execution and reconciliation contracts.
- [ ] cTrader implements the broker-neutral execution capability without
      exposing cTrader request/response types outside Broker Service.
- [ ] Execution requests carry a stable idempotency/client reference and are
      not blindly retried after timeout or ambiguous transport failure.
- [ ] Provider acknowledgements, explicit rejections, and unknown outcomes map
      to distinct broker-neutral results.
- [ ] Unknown outcomes trigger authoritative order/deal reconciliation before
      any further execution decision.
- [ ] Volume, precision, symbol identity, margin, and protective-stop
      constraints are validated against current provider facts.
- [ ] The bounded runtime proof submits at most one minimal demo order, with
      explicit human authorization and a protective stop, then confirms and
      reconciles the resulting order/deal/position state.
- [ ] No live order is submitted during automated tests, builds, or startup.
- [ ] Existing Kraken execution behavior and non-Challenge workflows remain
      unchanged.
- [ ] Focused tests and affected module validation pass.
- [ ] Runtime evidence records account identity, provider environment, granted
      permission, authorization, request idempotency key, provider outcome,
      reconciliation result, and final position state without exposing secrets.

---

## Constraints

- Preserve `ADR-071` responsibility boundaries and safety invariants.
- Treat the current repository as authoritative for implementation state.
- Keep financial calculations, Risk decisions, authorization, and execution
  state transitions deterministic.
- Keep cTrader transport, OAuth, protocol payloads, and provider errors inside
  the Broker Service adapter.
- Treat a timeout or missing provider response as unknown, never as rejection.
- Do not retry an uncertain write until reconciliation proves no matching
  provider operation exists and the execution policy explicitly permits retry.
- Never infer execution safety from `isLive` or a local label alone; use the
  verified effective environment classification. FTMO FREE is a documented
  provider semantic exception, not a general live-account bypass.
- Do not create, log, print, or commit credentials or access tokens.
- Do not commit, push, merge, or execute the runtime proof automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-071.md`

---

## Relevant Modules

- `broker-service`
- `trading-core`
- `risk-domain`
- `gateway`

---

## Validation

- Focused cTrader OAuth, permission, environment, order mapping, and
  reconciliation tests.
- Broker-neutral execution contract and unknown-outcome regression tests.
- Trading Core Challenge/Risk/human-authorization guard tests.
- Existing Kraken execution and reconciliation regression tests.
- Affected Maven module tests and `git diff --check`.
- Runtime read-only synchronization and restart/reconnect evidence before any
  demo write operation.
- One manually authorized, minimal cTrader demo execution followed by
  provider-authoritative confirmation and reconciliation.
- Explicit verification that no live account or second order was touched.

---

## Definition of Done

- [x] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Documentation reconciliation completed
- [ ] Relevant validation executed
- [ ] Runtime demo proof reviewed by the human engineer
- [ ] Code Review completed
- [ ] Engineering Report completed
- [ ] Human commit created
