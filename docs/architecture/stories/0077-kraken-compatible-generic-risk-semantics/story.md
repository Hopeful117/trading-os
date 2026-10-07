# Story 0077 - Kraken-Compatible Generic Risk Semantics

## Metadata

**ID:** `0077`
**Title:** Kraken-Compatible Generic Risk Semantics
**Status:** CLOSED - HUMAN ACCEPTED

The provider-neutral Risk implementation and automated validation were reviewed
and accepted by the human engineer. Broker or Kraken sandbox validation remains
out of scope for this Story. The final human Git commit remains pending under
the repository workflow; this status does not authorize another implementation,
commit, push, or merge operation.
**Related ADRs:** `ADR-009`, `ADR-028`

---

## Goal

Extend the provider-neutral deterministic Risk Domain so it can represent an
authoritative resettable daily equity-loss baseline and an immutable account
starting-balance drawdown baseline without introducing Kraken-specific rules.

## Context

Kraken Prop evaluates current equity against a daily balance reference and a
static lifetime drawdown floor. The current Risk Domain has a daily drawdown
rule, but its account snapshot exposes only a loosely named daily start balance
and no immutable account starting balance. Its projection also exposes one
ambiguous drawdown value for both observed and projected risk.

The Risk Engine must consume immutable baselines supplied by the application
boundary. It must not calculate provider reset schedules, query repositories or
reconstruct broker state.

## Problem

The existing model cannot distinguish:

- an authoritative daily reference balance from an account starting balance;
- current observed drawdown from projected pre-trade drawdown;
- daily loss from static total drawdown.

The profile vocabulary and persistence schema also reject a generic total
drawdown rule.

## Scope

- Add an immutable provider-neutral daily risk baseline with reference balance,
  effective timestamp, source and provenance.
- Add an immutable account starting balance to the Risk account snapshot.
- Preserve separate daily and total projected drawdown metrics.
- Add the generic `MAX_TOTAL_DRAWDOWN` rule using a static starting-balance
  reference.
- Register the new rule and support it in policy conflict resolution.
- Evolve Trading Core snapshot construction to supply both baselines.
- Keep existing `DAILY_DRAWDOWN` identity stable.
- Allow profiles to contain the new rule without making it mandatory for every
  account.
- Extend persistence vocabulary and provenance payloads as required.
- Add focused tests and preserve existing profile behavior.

## Out of Scope

- Challenge lifecycle or Challenge domain objects.
- Profit targets or funded-account lifecycle.
- Kraken-specific rules or fee calculators.
- Trailing or high-water-mark drawdown.
- Minimum trading days, consistency rules or strategy restrictions.
- Automatic position closure or broker-side breach handling.
- Frontend work, new services, Kafka, Redis or longitudinal challenge replay.
- Kraken market position limits.

## Acceptance Criteria

- [ ] Risk evaluation consumes an explicit immutable daily baseline.
- [ ] Daily drawdown uses the supplied reference balance and remains independent
      from the account starting balance.
- [ ] Risk evaluation consumes an immutable account starting balance.
- [ ] `MAX_TOTAL_DRAWDOWN` evaluates a static starting-balance threshold and
      treats the exact threshold as a breach.
- [ ] Profits do not move the static total-drawdown threshold upward.
- [ ] The same implementation supports 3%, 5% and 6% configurations.
- [ ] Observed and projected daily/total drawdown semantics remain distinct.
- [ ] Existing `DAILY_DRAWDOWN` profiles remain valid.
- [ ] Profiles may optionally include `MAX_TOTAL_DRAWDOWN`.
- [ ] Rule, baseline and starting-balance provenance remains in immutable risk
      context snapshots and replay artifacts.
- [ ] Risk Engine code contains no Kraken-specific branch or identifier.
- [ ] Focused Risk Domain and affected Trading Core tests pass.

## Constraints

- Preserve ADR-009 provider independence and configurable profiles.
- Preserve ADR-028 determinism, immutability, explainability and replayability.
- Reset scheduling remains outside the Risk Engine.
- Do not reinterpret historical stored evaluations silently.
- Do not make total drawdown mandatory for personal trading profiles.
- Do not commit, push, merge or rewrite Git history automatically.

## Relevant Modules

- `risk-domain`
- `trading-core`

## Validation

- Run the complete `risk-domain` Maven test suite.
- Run focused and affected `trading-core` tests.
- Run relevant persistence/integration tests.
- Run `git diff --check`.
- Inspect the complete diff for provider-specific leakage and historical
  compatibility issues.

## Definition of Done

- [ ] Implementation completed.
- [ ] Relevant validation executed.
- [ ] Complete diff reviewed.
- [ ] Engineering report completed.
- [ ] Human commit created.

## Documentation Reconciliation

The implementation is present in the merged commit `33217c3` and includes the
Risk Domain, Trading Core, persistence, and focused test changes listed in the
commit. Automated validation passed and human closure was accepted on
2026-10-06. The final human Git commit remains pending under the repository
workflow.
