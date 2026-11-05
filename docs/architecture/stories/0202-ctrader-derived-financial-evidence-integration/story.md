# Story 0202 - cTrader Derived Financial Evidence Integration and Validation

## Metadata

**ID:** `0202`
**Title:** cTrader Derived Financial Evidence Integration and Validation
**Status:** Draft
**Priority:** P0 - unblock financial preconditions for the first FTMO trade
**Related Stories:** `0197`, `0198`, `0199`, `0200`, `0201`
**Reference Account:** cTrader FTMO Free account `49411967`

## Goal

Integrate deterministic derived financial facts as structured, traceable
financial evidence and establish objectively which facts are valid, coherent
and admissible for FTMO risk evaluation, without weakening T0/T1 controls.

## Context

Stories 0197-0201 established the cTrader READ-ONLY boundary, partial account
synchronization, financial provenance, fail-closed risk evaluation and the
experimental derived-equity calculation. The reference account has a balance
of `50000 USD`, while authoritative equity, used margin, free margin and deal
history remain unavailable or incomplete.

Existing provider limitations are established for this Story. The implementation
must reuse existing sources and calculators rather than repeat broad cTrader
provider investigation or rewrite Story 0197.

Relevant decisions are recorded in ADR-082, ADR-084, ADR-085 and ADR-086.

## Problem

The existing derived-equity experiment is diagnostic, but derived financial
facts are not yet integrated as a complete evidence flow from Broker Service to
Trading Core and risk admission. Used margin and free margin also require an
explicit determination of whether their available inputs support a safe,
deterministic derivation.

## Scope

- Reuse `CTraderDerivedEquityExperiment` and preserve its deterministic checks.
- Integrate structured financial evidence with metric, value, currency,
  provenance, derivation version, coverage, timestamps, freshness, consistency,
  validation status and bounded diagnostics.
- Evaluate deterministic used-margin derivation only when all required
  provider-neutral inputs and applicable account/instrument rules are present.
- Derive free-margin candidates only from admissible, coherent dependencies.
- Preserve the distinctions between `BROKER_REPORTED`, `BROKER_DERIVED` and
  `UNAVAILABLE`.
- Propagate evidence through Broker Service and Trading Core without leaking
  cTrader-specific types.
- Evaluate evidence admission for FTMO rules without automatically promoting
  `BROKER_DERIVED` evidence.
- Add deterministic tests for equity, margin, free margin, provenance,
  admission and T0/T1 fail-closed behavior.
- Revalidate the existing FTMO account in controlled READ-ONLY mode when the
  provider cooldown permits.

## Out of Scope

- Repeating general cTrader API research for equity or margin fields.
- Creating a new feasibility Story or rewriting Story 0197's calculator.
- Automatically making `BROKER_DERIVED` evidence admissible for FTMO.
- Changing FTMO thresholds, policy versions, T0/T1 requirements or human
  authorization rules.
- Using generic or unvalidated margin formulas.
- Treating an empty position collection as proof of zero used margin without an
  exhaustive and coherent model.
- Creating synthetic equity, margin, free margin or deal history.
- Adding a cTrader mutation, order, OAuth, account, provider or microservice.
- Bypassing or weakening the cTrader cooldown, T0, T1 or execution safety.
- Committing, pushing, merging, resetting, cleaning or rebasing automatically.

## Acceptance Criteria

- [ ] Existing equity derivation is reused, tested and exposed as explicit
      financial evidence without becoming authoritative by default.
- [ ] Used-margin derivation produces either a validated result under an
      explicit applicable model or an explicit non-admissible diagnostic.
- [ ] Free-margin derivation is unavailable or non-admissible whenever its
      dependencies are unavailable, stale, incoherent or non-admissible.
- [ ] Evidence provenance, derivation method/version, coverage, freshness,
      consistency, validation status and diagnostics survive the service boundary.
- [ ] Trading Core admission remains policy-owned and rejects insufficient or
      unauthorized derived evidence.
- [ ] T0 and T1 remain fail-closed and use aligned evidence requirements.
- [ ] The existing FTMO account can be revalidated without provider mutation
      when the cTrader cooldown permits; cooldown blocking is reported as such.
- [ ] No execution intent or provider order is created during validation.
- [ ] Targeted backend, derivation, admission and frontend tests pass, with
      infrastructure limitations reported precisely.
- [ ] Story-local implementation, evidence matrix, runtime and safety reports
      are produced.

## Constraints

- Preserve all existing uncommitted changes from Stories 0198-0201.
- Use only external account `49411967` for controlled runtime validation.
- Keep `CTRADER_SAFETY_EXECUTION_ENABLED=false`.
- Keep Broker Service responsible for observation and provenance, and Trading
  Core responsible for business admission.
- Preserve ADR-082, ADR-084, ADR-085 and ADR-086 invariants.
- Do not invent financial values or convert unavailable data to zero.
- Any policy change requires explicit human validation before implementation.
- Do not perform Git integration actions without explicit authorization.

## Relevant ADRs

- `docs/architecture/adr/ADR-082-cfd-financial-model-and-mark-to-market-accounting.md`
- `docs/architecture/adr/ADR-084-live-financial-facts-and-provider-preflight-readiness.md`
- `docs/architecture/adr/ADR-085-broker-equity-resolution-and-provenance-contract.md`
- `docs/architecture/adr/ADR-086-evidence-based-risk-evaluation.md`

## Relevant Modules

- `broker-service`
- `trading-core`
- `trading-os-web`

## Validation

- Targeted Broker Service derivation and snapshot tests.
- Targeted Trading Core evidence mapping, admission, risk and T0/T1 tests.
- Angular tests and production build when the account evidence surface changes.
- `git diff --check`.
- Controlled READ-ONLY synchronization and T0 revalidation for account `49411967`
  when the cooldown permits.
- PostgreSQL integration suite retry when the environment permits.

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
