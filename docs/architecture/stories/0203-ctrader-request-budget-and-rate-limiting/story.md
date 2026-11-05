# Story 0203 - cTrader Request Budget and Rate Limiting

## Metadata

**ID:** `0203`
**Title:** cTrader Request Budget and Rate Limiting
**Status:** Draft
**Priority:** P0 - FTMO request-limit compliance
**Related Stories:** `0198`, `0199`, `0200`, `0201`, `0202`
**Reference Account:** cTrader FTMO Free account `49411967`

## Goal

Audit and protect every Trading OS path that can emit cTrader requests so the
integration is observable, bounded, deduplicated and able to refuse further
traffic before consumption can continue silently.

## Context

FTMO communicated a threshold of `2000` server requests per day. This is an
external limit whose precise accounting semantics are not yet confirmed. Trading
OS must therefore use a conservative configurable internal budget, initially
`1000` requests per day, without claiming that this value is the provider quota.

The existing cTrader integration includes authentication, account snapshots,
symbol reads, required-margin previews, subscriptions, reconnection and trading
paths. Runtime validation of Story 0203 must not contact cTrader during the
initial audit and must not run a load test.

## Problem

The repository does not yet provide one demonstrably central, persistent and
shared budget covering all cTrader protocol emissions, including reconnects,
retries and alternate adapters. Angular request counts and cTrader protocol
message counts also need to be distinguished.

## Scope

- Inventory all cTrader request emission paths and their triggers.
- Analyze existing logs/configuration for observed consumption without making
  authenticated provider calls.
- Introduce a centralized budget at the actual cTrader protocol emission
  boundary, covering retries, reconnects and alternate paths.
- Use persistent/shared reservation semantics compatible with the existing
  project infrastructure; do not add a new microservice.
- Bound retries, reconnects, cooldowns and concurrent duplicate requests.
- Reuse fresh snapshots and deduplicate concurrent reads where safe.
- Add explicit refusal, diagnostics and metrics for reservation/rejection,
  operation, trigger, retry, reconnect and cooldown behavior.
- Make real-provider tests opt-in and keep ordinary tests provider-free.
- Define a minimal runtime validation protocol for separate human approval.

## Out of Scope

- Any authenticated cTrader call during the initial audit.
- Load or stress testing against FTMO.
- Guessing the provider's exact quota accounting or reset timezone.
- Changing T0/T1, financial policy, execution safety or provider semantics.
- Bypassing cooldowns or allowing priority traffic to evade a strict budget.
- Introducing a new microservice or framework without explicit justification.
- Creating accounts, changing providers or submitting provider orders.
- Reopening the equity/margin investigation from Story 0202.
- Committing, pushing, merging, rebasing, cleaning or resetting automatically.

## Acceptance Criteria

- [ ] Every cTrader emission path is inventoried with trigger, frequency,
      retries, cache, owner and necessity classification.
- [ ] Observed consumption is separated from unverified hypotheses and from
      Angular-to-Trading-Core request counts.
- [ ] A configurable conservative internal daily budget defaults to `1000` and
      is distinct from the external `2000` FTMO threshold.
- [ ] Reservation occurs atomically before protocol emission and is shared and
      persistent across restarts/instances using existing infrastructure.
- [ ] Exhausted budgets produce controlled refusal and diagnostics, with no
      automatic retry loop.
- [ ] Reconnect, retry, cooldown and concurrent duplicate paths are bounded and
      tested.
- [ ] Fresh snapshot reuse and safe request coalescing reduce redundant calls.
- [ ] Metrics expose emitted, reserved, rejected, remaining, operation,
      trigger, retry, reconnect and cooldown counts without secrets.
- [ ] Ordinary tests cannot contact the real FTMO account; real-provider tests
      are explicit opt-in.
- [ ] No runtime provider validation is performed without separate human
      approval and a fixed call budget.
- [ ] Trading safety remains unchanged and `CTRADER_SAFETY_EXECUTION_ENABLED`
      remains `false`.

## Constraints

- Preserve all existing uncommitted changes from Stories 0198-0202.
- Use only account `49411967` for any future approved runtime validation.
- Do not bypass cTrader cooldowns or quota reservations.
- Do not invent provider quota semantics.
- Keep protocol safety traffic subject to an explicit, observable budget policy.
- Keep provider-specific code inside Broker Service adapters.
- Do not modify financial policy, T0/T1 or execution authorization.
- No Git integration actions without explicit human authorization.

## Relevant ADRs

- `docs/architecture/adr/ADR-073-ctrader-authentication-and-account-binding.md`
- `docs/architecture/adr/ADR-074-ctrader-authoritative-read-only-synchronization.md`
- `docs/architecture/adr/ADR-078-paper-live-execution-boundary.md`
- `docs/architecture/adr/ADR-084-live-financial-facts-and-provider-preflight-readiness.md`
- `docs/architecture/adr/ADR-087-fresh-snapshot-reuse-and-refresh-coordination.md`

## Relevant Modules

- `broker-service`
- `trading-core`
- `trading-os-web`

## Validation

- Static request inventory and configuration/log analysis.
- Targeted Broker Service budget, concurrency, persistence, retry, reconnect,
  cooldown and provider-isolation tests.
- Affected Trading Core and Angular tests where request triggering changes.
- Compilation and `git diff --check`.
- No authenticated runtime call during audit or automated test validation.
- A separately approved minimal runtime protocol only after offline validation.

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
