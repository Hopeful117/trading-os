# Story 0195 - Implement Evidence-Based Risk Evaluation

## Metadata

**ID:** `0195`
**Title:** Implement Evidence-Based Risk Evaluation
**Status:** Draft
**Related ADRs:** `ADR-028`, `ADR-043`, `ADR-071`, `ADR-074`, `ADR-075`, `ADR-078`, `ADR-082`, `ADR-084`, `ADR-085`, `ADR-086`
**Related Stories:** `0193`, `0194`

---

## Goal

Evolve the deterministic Risk Domain so that each invariant is evaluated from
its own admissible evidence requirements while the global policy and execution
decision remain strictly fail-closed.

This Story improves provider-neutral risk diagnostics and contract precision. It
does not make cTrader compatible with FTMO or authorize any LIVE execution.

## Context

`RiskFactRequirements` currently expresses facts primarily at policy level and
the Risk Engine calculates a shared metric set before traversing rules. The
current contracts already carry equity provenance in parts of Trading Core, but
the `RiskSnapshot.AccountRiskFacts` path can lose that evidence metadata.

ADR-086 establishes invariant-specific evidence requirements, independent
diagnostics and fail-closed aggregation. ADR-084 and ADR-085 remain the
authority for financial fact quality and provenance.

## Scope

- Define evidence requirements for each supported invariant from its real
  evaluator dependencies.
- Evaluate independently provable invariants when other invariants are
  incomplete.
- Produce explicit diagnostics for `PASS`, `FAIL`, `INCOMPLETE` and
  `NOT_APPLICABLE`, reusing existing result types where safe.
- Preserve equity value, provenance, quality, timestamps, account identity,
  currency and completeness through Broker Service and Trading Core mappings.
- Keep aggregate authorization fail-closed for missing, stale, incoherent or
  inadmissible mandatory evidence.
- Apply the same requirements and aggregation during initial validation and T1.
- Add focused offline unit, contract and execution-boundary tests.

## Non-Goals

- No cTrader equity derivation or provider investigation.
- No change to FTMO thresholds, rule semantics or policy version `1.0.0`.
- No `equity = balance`, zero, stale or arbitrary numeric fallback.
- No cTrader Algo dependency or new microservice.
- No broker LIVE calls, provider mutation or order placement.
- No frontend, Market Intelligence or unrelated broker feature.
- No generic rule framework or replacement of deterministic rule evaluators.

## Acceptance Criteria

- [ ] Every supported invariant declares or resolves its own evidence
      requirements.
- [ ] `MAX_POSITION_RISK` and stop protection can produce diagnostics without
      equity when their required facts are complete.
- [ ] Equity-dependent invariants remain `INCOMPLETE` when equity is absent,
      stale, incoherent or inadmissible.
- [ ] A numeric `BROKER_DERIVED` equity remains inadmissible for the FTMO policy.
- [ ] Results distinguish `PASS`, `FAIL`, `INCOMPLETE` and explicit
      `NOT_APPLICABLE`, directly or through a safe equivalent.
- [ ] Missing data never becomes zero, false or `NOT_APPLICABLE`.
- [ ] Partial evaluation never exposes an executable approval.
- [ ] Any mandatory incomplete or unevaluated invariant blocks T0 and T1.
- [ ] Equity provenance and quality are preserved through the affected snapshot
      mappings, including `RiskSnapshot.AccountRiskFacts` where applicable.
- [ ] Account identity, currency, freshness and coherence mismatches block the
      affected invariant and global authorization.
- [ ] Initial evaluation and T1 use the same evidence requirement resolution.
- [ ] FTMO policy `FTMO_2STEP_50K_RISK` version `1.0.0` is unchanged.
- [ ] Kraken behavior remains provider-neutral and existing cTrader safety
      behavior remains fail-closed.
- [ ] No broker LIVE call or order operation is performed by tests.

## Required Tests

- Partial policy: position risk evaluates, drawdowns are incomplete, global
  decision is blocked and no order reaches Broker Service.
- Missing, stale, unknown-provenance and derived equity cases.
- Admissible reported equity with complete evidence.
- Missing or incomplete positions, stops, baseline, account identity or currency.
- Mandatory invariant not evaluated.
- Explicit policy `NOT_APPLICABLE` versus missing evidence.
- T0/T1 consistency and changed facts between validation and revalidation.
- Existing Risk Domain, Trading Core, Kraken, cTrader and idempotency/reconcile
  regressions relevant to affected modules.

## Migration Impact

The change should evolve existing contracts without introducing a new service or
provider-specific Risk Domain type. Legacy numeric equity without provenance is
unknown for policies requiring demonstrated provenance. Existing callers must
continue receiving a blocking aggregate result whenever a mandatory rule cannot
be evaluated.

## Safety Invariants

```text
PARTIAL_RESULT_IS_NOT_EXECUTION_AUTHORIZATION = true
INCOMPLETE_MANDATORY_RULE = BLOCKED
NOT_APPLICABLE_REQUIRES_POLICY_DECISION = true
EQUITY_BALANCE_FALLBACK = false
FTMO_POLICY_UNCHANGED = true
CTRADER_LIVE_CALLS = false
NEW_MICROSERVICE = false
```

## Validation

- Focused `risk-domain` tests.
- Focused `trading-core` risk and T1 tests.
- Affected Broker Service contract tests.
- Relevant module test suites when production contracts change.
- `git diff --check`.
