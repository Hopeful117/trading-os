# Story 0114 - Funding Evidence Runtime Acquisition

## Metadata

**ID:** `0114`

**Title:** Funding Evidence Runtime Acquisition

**Status:** Blocked

**Related Stories:** `0113`, `0096`, `0111`, `0112A`

**Related ADRs:** `ADR-057`, `ADR-063`

This Story defines the bounded provider investigation and, if the provider
semantics are sufficient, the implementation of passive Kraken Funding Evidence
acquisition. It does not authorize implementation until the Repository Analysis
and Implementation Plan have been approved by the human engineer.

---

## Goal

Start a third passive Market Data evidence clock that persists immutable,
provider-neutral Funding Evidence representing what Trading OS learned from
Kraken and when it learned it.

The desired evidence pipeline is:

```text
Kraken
    -> provider adapter
    -> provider-neutral normalization
    -> Funding Evidence
    -> fingerprint/idempotent persistence
    -> Market Data PostgreSQL
```

Funding Evidence is market/provider truth. It must remain distinct from Funding
Cost, which depends on a position, notional, direction, holding interval, and
provider settlement semantics.

---

## Context

Market Data currently has durable OHLC and Order Book evidence acquisition.
Funding Evidence has a provider-neutral model, migration, fingerprint, JDBC
repository, and tests, but the runtime table is empty and no acquisition path is
implemented.

Story `0113` established the evidence foundation and explicitly deferred
provider-specific acquisition until provider endpoints and historical
availability were validated.

Official Kraken documentation identifies a Futures historical funding endpoint:

```text
GET /derivatives/api/v3/historicalfundingrates?symbol=<symbol>
```

The Futures WebSocket ticker also exposes separate current and predicted funding
fields. The exact historical response semantics and the correct symbol mapping
for the bounded runtime market must be verified before implementation.

---

## Problem

The platform cannot currently preserve Funding Evidence during normal operation.
Consequently, future historical research cannot distinguish:

```text
when funding applied
```

from:

```text
when Trading OS knew the provider funding information
```

Using current provider state as historical evidence, treating predicted funding
as realized funding, or converting missing funding to zero would create invalid
research data.

---

## Scope

- Reconstruct and validate the existing Funding Evidence model, schema,
  fingerprint, idempotence, and strict cutoff query boundary.
- Investigate the official Kraken Futures capabilities for current, predicted,
  realized, and historical funding information.
- Verify the applicable product family, market identity, provider symbol, funding
  interval, effective timestamp, provider update behavior, and historical
  recoverability for the bounded perpetual research market.
- Determine whether the current model can safely distinguish realized/current
  funding from predicted/indicative funding.
- If the provider semantics and model are sufficient, add a provider adapter and
  bounded passive acquisition path inside Market Data.
- Keep provider DTOs and Kraken-specific semantics inside the Kraken adapter.
- Persist only validated, provider-neutral Funding Evidence with truthful
  `knownAt` and effective-time semantics.
- Make acquisition configurable and bounded to the initial research market.
- Preserve idempotent persistence for repeated semantic provider observations and
  safe failure behavior for unavailable or malformed provider responses.
- Re-measure OHLC, Order Book, and FeatureSnapshot runtime health after any
  activation.
- Perform a bounded investigation of the existing Order Book checksum behavior
  and the previous `trend-context-analysis` timeout without expanding either
  concern into this Story.

---

## Out of Scope

- Funding Cost calculation.
- Position funding accrual or settlement.
- Execution, fee, slippage, spread, market-impact, or transaction-cost models.
- Strategy research, backtesting, P&L, risk rules, or TradePlan behavior.
- Funding-derived features or changes to `feature-projection-v1`.
- New ML models, LLM reasoning, or Python-owned market facts.
- Whole-catalog or multi-provider funding acquisition.
- A new service, message broker, scheduler framework, or public API.
- Rewriting, truncating, or migrating existing OHLC or Order Book evidence.
- Order Book checksum redesign.
- Increasing the `trend-context-analysis` timeout merely to remove failures.
- Commit, push, merge, or deployment automation.

---

## Provider Investigation Gate

Before implementation, establish from official Kraken documentation and the
existing adapter:

- whether Kraken exposes current, predicted, realized, and historical funding;
- whether the historical endpoint supports the actual bounded perpetual symbol;
- the historical response schema, resolution, retention, pagination, and limits;
- funding-rate and settlement/effective-time semantics;
- whether historical values can be recovered later or must be captured now;
- whether provider corrections are possible and how immutable evidence records
  them;
- the appropriate polling or streaming mechanism and safe cadence.

The investigation must classify historical recoverability as exactly one of:

```text
FULLY_RECOVERABLE
PARTIALLY_RECOVERABLE
LIMITED_RETENTION
CURRENT_ONLY
UNKNOWN
```

If predicted and realized funding cannot be distinguished safely, or if the
provider symbol/response semantics remain unresolved, implementation must stop
with `PROVIDER_BLOCKED`, `ADR_REQUIRED`, or `UNKNOWN` as appropriate.

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

- [ ] Existing Funding Evidence model, migration, repository, fingerprint, and
      tests are documented with their actual field semantics.
- [ ] Official Kraken product, endpoint, symbol, funding, timestamp, and
      historical-recoverability semantics are documented.
- [ ] The bounded market identity is resolved dynamically or configurably and is
      not based on a stale hardcoded UUID.
- [ ] Current/predicted funding is never persisted as realized historical
      funding without explicit provider-semantic justification.
- [ ] `knownAt` represents the earliest defensible local acceptance time and is
      never fabricated from provider effective time or settlement boundaries.
- [ ] Strict historical reads exclude evidence where `knownAt` is after the
      research cutoff.
- [ ] Provider-specific DTOs and symbols do not leak into the canonical Funding
      Evidence contract.
- [ ] Canonical funding rates use `BigDecimal` and stable normalization.
- [ ] Repeated semantic provider observations are idempotent, including
      concurrent persistence where supported by the existing repository pattern.
- [ ] Provider timeout, error, malformed payload, missing rate, invalid decimal,
      and unknown market paths persist no synthetic evidence and never persist a
      zero rate as a fallback.
- [ ] Acquisition is bounded, configurable, and disabled by default when that
      matches existing passive-capture safety conventions.
- [ ] At least one real canonical Funding Evidence row is persisted before the
      Story can claim runtime success.
- [ ] OHLC and Order Book capture continue without reset or semantic regression.
- [ ] FeatureSnapshot remains unchanged and healthy.
- [ ] Focused provider mapping, fingerprint, persistence, temporal, failure, and
      regression tests pass.
- [ ] `git diff --check` passes and unrelated worktree changes are preserved.

---

## Constraints

- Market Data remains authoritative for normalized historical market evidence,
  temporal eligibility, and provider acquisition.
- Provider-specific Kraken payloads, symbols, and transport details terminate at
  the Kraken infrastructure adapter.
- Funding Evidence is not Funding Cost and must not acquire position semantics.
- Missing funding is explicit missingness, never zero funding.
- Historical research uses `knownAt <= evaluationCutoff` and the applicable
  funding effective-time semantics.
- Existing OHLC and Order Book collectors must not be disabled, reset, or
  unnecessarily disrupted.
- Existing evidence timestamps and rows are immutable.
- The initial acquisition universe remains bounded to the existing perpetual
  research market, expected to be the Kraken `PF_XBTUSD` product after runtime
  verification.
- No provider fallback may substitute another provider's funding rate.
- No implementation proceeds before Repository Analysis and Implementation Plan
  approval under the Engineering Story workflow.

---

## Relevant Artifacts

- `docs/architecture/stories/0113-realistic-execution-transaction-cost-research/story.md`
- `docs/architecture/stories/0113-realistic-execution-transaction-cost-research/implementation-report.md`
- `docs/architecture/stories/0096-kraken-linear-perpetual-market-data/story.md`
- `docs/architecture/adr/ADR-057.md`
- `docs/architecture/adr/ADR-063.md`
- `market-data/src/main/java/com/hope/trading/market_data/model/HistoricalFundingEvidence.java`
- `market-data/src/main/java/com/hope/trading/market_data/service/HistoricalFundingEvidenceRepository.java`
- `market-data/src/main/resources/db/migration/V7__historical_funding_evidence.sql`

---

## Relevant Provider Sources

- `https://docs.kraken.com/exchange/guides/general/historical-data`
- `https://docs.kraken.com/exchange/api-reference/futures-websocket/ticker`
- `https://docs.kraken.com/exchange/guides/futures/introduction`
- `https://futures.kraken.com/derivatives/api/v3/instruments`
- `https://futures.kraken.com/derivatives/api/v3/instruments/status`
- `https://futures.kraken.com/derivatives/api/v3/tickers`

---

## Validation Expectations

- Run focused Market Data provider, mapping, repository, temporal, and failure
  tests first.
- Run the complete affected Market Data test suite and verification when
  practical.
- Validate the first real persisted Funding Evidence row using the actual
  market, provider symbol, rate, effective time, `knownAt`, fingerprint, and
  normalization version.
- Prove duplicate polling behavior and absence of synthetic zero values.
- Re-measure OHLC, Order Book, Funding, and FeatureSnapshot runtime counts and
  growth without resetting databases.
- Inspect Order Book checksum support only as a bounded follow-up and classify
  it as A, B, C, D, E, or F without redesigning it.
- Inspect the source and intent of the `750 ms` trend capability timeout without
  changing it in this Story.
- Run `git diff --check`, inspect `git status`, and preserve unrelated changes.

---

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Provider investigation and architecture decision recorded.
- [ ] Implementation Plan approved when implementation is justified.
- [ ] Implementation completed, or provider blocker documented.
- [ ] Documentation reconciliation completed.
- [ ] Relevant validation executed and reported.
- [ ] Code Review completed.
- [ ] Engineering Report completed.
- [ ] Human commit and integration decisions completed.
