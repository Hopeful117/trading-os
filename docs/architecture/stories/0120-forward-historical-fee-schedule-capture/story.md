# Story 0120 - Forward Historical Fee Schedule Capture

## Metadata

**ID:** `0120`

**Title:** Forward Historical Fee Schedule Capture

**Status:** Draft

**Related Stories:** `0113`, `0115`, `0116`

**Related ADRs:** `ADR-064`

This Story defines the bounded investigation and, if provider semantics are
sufficient, the implementation of passive forward-only Kraken fee-schedule
capture. It does not authorize implementation until the Repository Analysis
and any required Implementation Plan have been approved by the human engineer.

---

## Goal

Start preserving truthful provider/account fee-schedule evidence during normal
Broker Service operation so later economic research can select fee schedules by
provider, account, product, instrument, effective time, and research cutoff.

The desired evidence pipeline is:

```text
Kraken
    -> provider adapter
    -> provider-neutral normalization
    -> HistoricalFeeSchedule
    -> fingerprint/idempotent persistence
    -> Broker Service PostgreSQL
```

Only observations made after the capture clock starts may be persisted as
captured evidence. A newly observed current schedule must never be backdated to
an earlier effective time.

---

## Context

Story `0113` created the provider-neutral `HistoricalFeeSchedule` model,
strict temporal reader, persistence, fingerprints, and PostgreSQL constraints.
The runtime migration is now at V5 and the `historical_fee_schedule` table is
empty.

Kraken's authenticated `TradeVolume` endpoint can return current fee tiers for
asset pairs, including `PF_XBTUSD` when requested as a derivatives pair. The
endpoint documents current fee and volume-tier information, but does not expose
the historical effective timestamp needed to reconstruct past schedules.

Broker Service is the approved owner of provider/account fee truth and of
historical provider/account fee-schedule acquisition. Provider-specific Kraken
payloads must remain inside the Kraken infrastructure adapter.

---

## Problem

The platform has a durable fee-schedule evidence foundation but does not yet
preserve fee schedules during runtime operation. Consequently, future economic
research cannot distinguish:

```text
when a fee schedule was observed and became known to Trading OS
```

from:

```text
when the provider claims the schedule is effective
```

Using the current provider response as historical truth, backdating it, or
silently selecting the latest schedule for an earlier execution time would
create look-ahead and non-reproducible research results.

---

## Scope

- Document the existing `HistoricalFeeSchedule` domain, persistence,
  fingerprint, strict reader, and runtime migration state.
- Investigate the official Kraken fee endpoint, authentication permission,
  product/class and instrument mapping, maker/taker semantics, tier semantics,
  response stability, and any effective-time or historical-recovery behavior.
- Determine whether Kraken provides a defensible historical effective interval
  or only a current schedule observation.
- If provider semantics are sufficient, add a Kraken provider adapter and a
  bounded, passive, forward-only acquisition path inside Broker Service.
- Capture only provider-neutral schedules with truthful provider/account scope,
  product, instrument, maker/taker rates, `observedAt`, `knownAt`, source, and
  effective interval semantics.
- Make capture configurable and disabled by default unless an existing runtime
  convention requires another safe default.
- Preserve fingerprint idempotence, duplicate safety, and the existing
  no-overlap protection for captured schedules.
- Ensure provider failures, malformed payloads, missing rates, invalid decimal
  values, unknown instruments, and ambiguous effective times persist no
  synthetic evidence.
- Verify the existing Broker Service runtime and database without reset,
  truncation, or data rewriting.

---

## Out of Scope

- Reconstructing or backfilling historical fee schedules from current Kraken
  responses.
- Treating current fee tiers as evidence for any interval before observation.
- Historical trade-fee enrichment or broker fill reconciliation.
- Funding acquisition, funding cost, execution simulation, spread, slippage,
  P&L, Risk, or settlement behavior.
- Changes to `HistoricalFeeSchedule` unless provider investigation proves the
  existing contract cannot represent truthful forward observations.
- New services, message brokers, public APIs, or cross-service transport.
- Whole-catalog, multi-provider, or unbounded fee capture.
- Changes to Order Book, OHLC, FeatureSnapshot, or Market Intelligence
  acquisition behavior.
- Automatic retry of unsafe provider writes; this Story contains read-only
  provider acquisition only.
- Commit, push, merge, pull-request creation, or deployment automation.

---

## Provider Investigation Gate

Before implementation, establish from official Kraken documentation, a safe
provider response where available, and the existing adapter:

- whether `TradeVolume` returns current or historical fee information;
- whether `PF_XBTUSD` and the applicable product/class are supported;
- how maker and taker fees are represented;
- how active tiers and account volume inputs affect the response;
- which API permission is required;
- whether the response exposes a defensible effective timestamp or interval;
- whether provider corrections or tier changes can be detected;
- whether repeated identical observations are expected and safely idempotent;
- the safe polling cadence and bounded capture configuration.

The provider path must be classified as exactly one of:

```text
FULLY_RECOVERABLE
FORWARD_CAPTURE_ONLY
CURRENT_ONLY
PROVIDER_BLOCKED
UNKNOWN
```

If the provider does not expose a trustworthy historical effective interval,
implementation may proceed only with `FORWARD_CAPTURE_ONLY` semantics. A
current schedule must then become eligible from the local observation boundary,
not from an inferred or provider-absent earlier timestamp.

If provider/account/product/instrument/maker/taker semantics cannot be proven,
stop with `PROVIDER_BLOCKED`, `ADR_REQUIRED`, or `UNKNOWN` as appropriate.

---

## Architecture Decision Gate

After repository and provider investigation, select exactly one outcome:

```text
IMPLEMENT
ADAPT_MODEL_THEN_IMPLEMENT
ADR_REQUIRED
PROVIDER_BLOCKED
UNKNOWN
```

No runtime activation is allowed before this decision and the required human
approval gates are complete.

---

## Acceptance Criteria

- [ ] Existing `HistoricalFeeSchedule` fields, constraints, fingerprint, and
      strict temporal reader are documented with their actual semantics.
- [ ] Official Kraken endpoint, permission, product/class, instrument, tier,
      maker/taker, timestamp, and recoverability semantics are documented.
- [ ] `PF_XBTUSD` mapping is resolved dynamically or configurably and is not
      based on an unexplained stale identifier.
- [ ] Current Kraken fee responses are never backdated or used as historical
      fallback evidence.
- [ ] The first legitimate observation at time `T` is eligible only from `T`
      forward, unless the provider supplies a defensible effective interval.
- [ ] `observedAt` and `knownAt` preserve local observation and acceptance
      semantics and are not fabricated from provider tier boundaries.
- [ ] Strict historical reads continue to enforce effective-time and
      `knownAt` cutoff eligibility.
- [ ] Provider-specific DTOs, symbols, and response fields do not leak into the
      canonical fee-schedule contract.
- [ ] Maker/taker rates are normalized as `DECIMAL_FRACTION` with stable
      `BigDecimal` semantics.
- [ ] Repeated semantic observations are idempotent, including concurrent
      persistence where supported by the existing repository pattern.
- [ ] Provider timeout, authentication failure, rate limit, malformed payload,
      missing fee, invalid decimal, and unknown instrument paths persist no
      synthetic schedule.
- [ ] Capture is bounded, configurable, and disabled by default when consistent
      with existing passive-capture safety conventions.
- [ ] At least one real canonical captured schedule row is persisted before the
      Story can claim runtime success.
- [ ] Existing Broker Service behavior and unrelated evidence pipelines remain
      unchanged.
- [ ] Focused provider mapping, normalization, fingerprint, persistence,
      temporal, failure, configuration, and runtime regression tests pass.
- [ ] `git diff --check` passes and unrelated worktree changes are preserved.

---

## Constraints

- Broker Service remains authoritative for provider/account fee truth and
  acquisition.
- Provider-specific Kraken payloads, authentication, symbols, and transport
  details terminate at the Kraken infrastructure adapter.
- Fee schedules are evidence, not strategy decisions, Risk decisions, or
  economic-result calculations.
- The existing `HistoricalFeeSchedule` temporal and no-overlap invariants must
  remain enforced.
- Missing or ambiguous provider information must remain explicit; it must not
  be converted to zero, a guessed interval, or a latest-schedule fallback.
- Preserve encrypted credential handling and required Kraken API permissions.
- Do not reset databases, truncate tables, rewrite existing evidence, or weaken
  authorization.
- Do not commit, push, merge, or deploy automatically.

---

## Relevant ADRs

- `docs/architecture/adr/ADR-064.md`

---

## Relevant Modules

- `broker-service`

---

## Validation

- Focused provider client and response-mapping tests with representative
  current, tiered, malformed, missing, and error responses.
- Deterministic normalization and fingerprint tests.
- Persistence idempotence, overlap, rate-unit, and concurrent duplicate tests.
- Temporal tests proving forward-only eligibility and strict `knownAt` cutoff
  behavior.
- Configuration and disabled-by-default capture tests.
- `cd broker-service && ./mvnw verify`.
- Runtime Flyway/schema and row-count validation against the existing Broker
  Service PostgreSQL database without reset.
- Real provider or approved sandbox validation sufficient to persist at least
  one truthful canonical schedule row, if credentials and permissions allow.
- `git diff --check`.

---

## Definition of Done

- [ ] Repository Analysis approved
- [ ] Implementation Plan approved when required
- [ ] Implementation completed or provider gate reaches an explicit blocked
      outcome
- [ ] Relevant validation executed
- [ ] Diff reviewed in IntelliJ
- [ ] Code Review approved
- [ ] Engineering Report completed
- [ ] Human commit created
