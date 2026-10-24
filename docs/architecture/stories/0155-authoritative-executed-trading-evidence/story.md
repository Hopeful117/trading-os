# Story 0155 - Authoritative Executed-Trading Evidence for Challenge Progress

## Metadata

- **ID:** `0155`
- **Status:** Post-correction review passed - awaiting human validation
- **Related ADRs:** `ADR-075`, `ADR-028`, `ADR-031`, `ADR-041`
- **Related Stories:** `0152`, `0154`
- **Repository Analysis:** approved by human engineer
- **Implementation Plan:** `implementation-plan.md`
- **Approved Direction:** Add durable execution provenance for qualifying PAPER
  trades before generating Challenge evidence.
- **Blocker:** `Trade.openedAt` does not distinguish PAPER fill settlement from
  the legacy HTTP trade-opening path.

## Goal

Close the provider-neutral Challenge progress gap by deriving
`ExecutedTradingEvidence` from persisted Trading Core trades that were created
from an executed fill, and consume that evidence during Challenge reevaluation.

This Story enables deterministic minimum-trading-day progress for the existing
PAPER dry-run path without claiming cTrader execution or provider validation.

## Problem

`ExecutedTradingEvidence` is already part of the Challenge contract, but no
production service constructs it. Challenge reevaluation currently supplies
`UNAVAILABLE`, so FTMO minimum trading-day objectives cannot progress from real
local execution. `Trade.openedAt` is populated by PAPER settlement from the
executed fill timestamp, but the legacy `TradingServiceImpl.openTrade()` path
also populates it from `Instant.now()` without a broker fill. The current
`Trade` model has no durable provenance or execution-attempt association to
distinguish these cases. Provider-synchronized positions without an
authoritative opening timestamp must not be counted.

## Scope

- Add a provider-neutral evidence reader over persisted `Trade` records.
- Add durable execution provenance for each qualifying PAPER settlement,
  associated with the resulting Trade, execution attempt, broker order, fill
  identity and fill execution timestamp.
- Count distinct resolved trading days from non-null authoritative `openedAt`
  values whose durable provenance is a successful PAPER fill, for the active
  Challenge interval and configured Risk-day boundary.
- Return explicit `UNAVAILABLE`, `STALE` or `CONTRADICTORY` evidence when the
  source cannot produce a complete result.
- Pass the evidence into Challenge reevaluation for the existing PAPER path.
- Preserve Challenge's provider-confirmation and Risk authority semantics.
- Add deterministic unit, persistence/application and regression tests.

## Out of Scope

- cTrader OAuth, runtime validation, order mutation or reconciliation.
- Inferring execution from TradePlan, ExecutionIntent, broker acknowledgement,
  balance changes, synchronized positions without `openedAt`, or local wall
  clock time.
- Treating `TradeOutcome.ENTRY_ACKNOWLEDGED` as executed-trading evidence.
- New FTMO-specific classes, formulas or Challenge rules.
- New provider event contracts or a cTrader execution authority boundary.
- cTrader deal-history ingestion or provider-specific trading-day interpretation.

## Constraints

- `Trade.openedAt` is authoritative for Challenge evidence only when the new
  durable provenance association proves that the existing execution lifecycle
  created or updated the trade from a successful PAPER fill.
- The evidence must use the Challenge-effective Risk-day timezone and reset
  already stored on the Challenge instance/definition.
- Missing or incomplete evidence remains not satisfied and never becomes zero
  activity or Challenge-safe state.
- Existing PAPER, Kraken, legacy Challenge and FTMO definition behavior must
  remain compatible.
- cTrader runtime status remains `BLOCKED_EXTERNAL`; execution remains disabled.

## Acceptance Criteria

- [x] A PAPER trade created from an executed fill contributes exactly one
      resolved trading day, regardless of how many positions opened that day.
- [x] A qualifying trade has durable provenance linking the Trade to the
      execution attempt, broker order, fill identity and fill execution time.
- [x] Legacy HTTP-created trades cannot qualify without such provenance.
- [x] Trades with null `openedAt`, plans, intents, acknowledgements and balance
      changes do not contribute trading-day evidence.
- [x] Evidence uses the active Challenge's configured IANA timezone and reset,
      including DST behavior, without using the machine timezone.
- [x] Evidence is scoped to the active Challenge interval and account.
- [x] Challenge reevaluation consumes the evidence and can satisfy the FTMO
      minimum-day objective when profit, Risk and provider-confirmation rules
      independently allow progression.
- [x] Missing, stale, contradictory or unavailable source data remains fail
      closed and does not advance a Challenge.
- [x] Existing provider-confirmation requirements remain unchanged.
- [x] No cTrader capability, credential, provider payload or execution path is
      added.
- [x] The provenance write is transactional with PAPER settlement and is
      idempotent for repeated finalization/recovery.
- [x] Existing PAPER, Kraken and legacy Challenge regression tests remain green.
- [x] `git diff --check` passes.

## Required Validation

- Focused evidence and Challenge reevaluation tests.
- Trading Core persistence/application tests covering account and Challenge
  scope, distinct days and Risk-day/DST boundaries.
- Existing FTMO definition and Challenge regression tests.
- Full Trading Core verification when practical.
- Independent code review before delivery.

## Stop Conditions

Stop and request a separate Story if:

- the provenance association cannot prove a Trade-to-fill relationship;
- Challenge-effective Risk-day configuration cannot be consumed without a new
  authority boundary;
- live/provider evidence would require inferring cTrader behavior;
- implementation requires cTrader runtime validation or mutation;
- a broader persistent evidence model becomes necessary.

## Current Blocker

The approved implementation plan was extended by the human engineer to add
durable execution provenance. Implementation must still stop if the selected
provenance schema cannot prove the Trade-to-fill association, or if idempotent
recovery requires a broader execution architecture decision. Counting all
non-null `openedAt` values remains prohibited.

## Human Boundary

Repository Analysis approval is required before Implementation Planning.
Implementation requires explicit approval of the resulting Implementation Plan.
