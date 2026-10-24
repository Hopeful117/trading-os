# Story 0155 - Implementation Plan

## Plan Status

`REVISED - AWAITING HUMAN APPROVAL`

The Repository Analysis was approved by the human engineer, and the Story was
extended to add durable execution provenance. The existing trade model is not
itself authoritative: `TradingServiceImpl.openTrade()` persists an OPEN trade
with `openedAt = Instant.now()` without an execution attempt or broker fill.

`TradingServiceImpl.openTrade()` persists an OPEN trade with
`openedAt = Instant.now()` without an execution attempt or broker fill. The
same model is used by `PaperSettlementService`, which persists the actual fill
timestamp. There is currently no durable provenance field or execution
association to distinguish the two records.

## 1. Durable Provenance

Do not add a repository query that treats every non-null `openedAt` as executed
trading evidence. That would count a known non-executed legacy path and violate
the Story's fail-closed requirement.

Add a provider-neutral durable provenance association, preferably a
`trade_execution_provenance` table, with:

- immutable `trade_id`;
- immutable `execution_attempt_id`;
- immutable `broker_order_id`;
- immutable `fill_id`;
- immutable `executed_at`;
- explicit execution mode/source identifying the PAPER settlement path.

The association must have database uniqueness preventing duplicate provenance
for the same execution fill/order and must be written transactionally with
`PaperSettlementService` after a valid fill has been selected and the resulting
Trade identity is known. Legacy HTTP-created trades and broker-synchronized
positions receive no provenance.

For aggregated PAPER positions, record one provenance row per settlement/fill
association. Evidence counts distinct Risk days from provenance rows, not from
the Trade table alone.

## 2. Preserve Existing Authority

Use the existing persisted `Trade` lifecycle as the source for local executed
trading evidence:

```text
executed PAPER fill
    -> PaperSettlementService
    -> Trade.openedAt
    -> ExecutedTradingEvidence
    -> ChallengeReevaluationService
    -> ChallengeEvaluator
```

Only trades with a durable provenance association created by the existing
settlement path may qualify. The implementation must not use
`TradeOutcome.ENTRY_ACKNOWLEDGED`, ExecutionIntent, TradePlan, balance changes,
or synchronized positions without an authoritative fill association.

## 3. Evidence Reader

Add a small provider-neutral application service/port in Trading Core that:

- loads qualifying provenance for the Challenge account;
- restricts evidence to provenance at or after the Challenge start and no later
  than the observation instant;
- resolves each `executedAt` through `RiskDay.containing(instant, zone, reset)`;
- returns distinct `RiskDay.date()` values;
- produces `COMPLETE` evidence with a deterministic source reference when the
  persisted source is readable;
- returns explicit `UNAVAILABLE` or `CONTRADICTORY` evidence on missing or
  invalid required configuration/data.

The provenance table is not Challenge evidence storage. It is a durable
execution lineage record; `ExecutedTradingEvidence` remains a read projection
for Challenge evaluation.

## 4. Challenge Integration

Inject the evidence reader into `ChallengeReevaluationService` and use it for
the existing PAPER reevaluation paths. Keep explicit evidence overloads for
tests and future provider-neutral callers where they are already part of the
contract.

The service must preserve:

- Risk evaluation and breach precedence;
- provider-confirmation requirements;
- active-Challenge ownership and account scoping;
- legacy single-phase behavior;
- fail-closed behavior when evidence is unavailable.

## 5. Risk-Day Semantics

Resolve the timezone and reset from the immutable Challenge instance when
present, otherwise use the existing account configuration path only where the
legacy Challenge contract requires it. Do not use the JVM, database, Docker,
browser or user timezone.

Use `RiskDay.containing` for DST-safe boundaries. A trade opened exactly at the
reset belongs to the new Risk day; a trade before the reset belongs to the
previous day.

## 6. Tests

Add focused tests for:

- one qualifying PAPER trade;
- provenance is created for an executed PAPER fill;
- legacy HTTP-created trades cannot qualify;
- broker-synchronized positions cannot qualify;
- repeated finalization/recovery does not duplicate provenance;
- multiple trades on one day counting once;
- trades on distinct Risk days;
- null `openedAt` being ignored or producing incomplete evidence according to
  the selected source semantics;
- Challenge start boundary and post-start filtering;
- Europe/Paris DST transition;
- non-midnight reset through the generic `RiskDay` contract;
- account and active-Challenge scoping;
- unavailable evidence preventing FTMO minimum-day progression;
- existing provider-confirmation and Risk-breach behavior remaining unchanged.

Update persistence/application tests only where the new repository query or
service wiring requires it. Do not add cTrader fixtures as runtime evidence.

## 7. Documentation Reconciliation

Update the Story implementation report after implementation with:

- the evidence authority and its PAPER-only scope;
- explicit cTrader runtime limitations;
- validation commands and results;
- residual cTrader execution/reconciliation gaps;
- `Documentation update` outcome and vault outcome.

No frontend documentation or provider-specific documentation is required. The
durable provenance schema must be reconciled with the existing execution
ownership boundary before implementation.

## 8. Validation

Run, at minimum:

```text
trading-core focused evidence and Challenge tests
trading-core full clean verify when practical
git diff --check
```

Run the existing Risk, FTMO definition, PAPER and Kraken regression suites when
the affected module validation permits. Do not claim cTrader runtime
validation.

## 9. Stop Conditions

Stop implementation and report a new architecture decision if:

- the provenance association cannot prove a Trade-to-fill relationship;
- Challenge-effective Risk-day configuration cannot be consumed without a new
  authority boundary;
- live/provider evidence is required to complete the PAPER implementation;
- a broader persistent evidence model becomes necessary.

## 10. Expected Files

Expected changes are limited to:

- `trading-core/src/main/java/.../challenge/application/` evidence reader and
  Challenge reevaluation wiring;
- `trading-core/src/main/java/.../repository/TradeRepository.java` if a scoped
  query is required;
- `trading-core/src/main/java/.../execution/application/service/PaperSettlementService.java`
  for transactional provenance creation;
- `trading-core/src/main/java/.../execution/infrastructure/persistence/` for
  the provenance persistence model and adapter;
- `trading-core/src/main/resources/db/migration/common/V39__...sql` for the
  provenance schema;
- focused Trading Core tests;
- Story 0155 implementation report and code review artifacts.

No Broker Service, cTrader adapter, Risk formula, credential or cTrader
execution capability changes are expected. A Trading Core persistence
migration is now explicitly in scope.
