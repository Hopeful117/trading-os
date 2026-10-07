# Implementation Report - Story 0012

## Status

Closed after reconciliation with the current repository implementation.

## Reconciliation

The StrategyMatch-to-TradingOpportunity authority transition is implemented in
the repository. The current code is treated as the source of truth, as
approved by the human engineer.

The production path is now:

```text
Observation -> StrategyEvaluation -> StrategyMatch -> TradingOpportunity
```

`ProductionIntelligencePipeline` performs this work in one atomic transaction.
Only MATCH evaluations create or reuse a StrategyMatch and then create or reuse
the corresponding opportunity lineage. The legacy OHLC rule remains evidence
construction only and is not the authority for opportunity existence.

## Verified Behavior

- Opportunity direction is projected from `StrategyMatch.direction`.
- Strategy identity and version remain attributable through the opportunity.
- The opportunity lineage is deterministically derived from `matchId` and is
  distinct from it.
- Repeated processing of the same logical match is idempotent.
- Opportunity versions retain `strategy_match_id` attribution.
- The factory does not re-evaluate OHLC data or infer strategy identity.
- The transaction keeps StrategyMatch and its opportunity consistent on
  success or rollback.
- The existing bootstrap mapping remains truthful: the strategy is
  `UNVALIDATED`, and no validated edge or expectancy is implied.

The repository also contains later compatible extensions from Stories 0013 and
0029, including generic strategy-definition metadata and deterministic setup
snapshots. These extensions do not change the Story 0012 authority boundary.

## Validation

Executed successfully:

```bash
cd market-intelligence && mvn -q -Dtest=StrategyMatchOpportunityFactoryTest,ProductionIntelligencePipelineTest,StrategyMatchPersistenceTest,ActiveScanProjectionPersistenceTest,ActiveScanReconciliationServiceTest test
git diff --check
```

No production code was changed. The changes are limited to Story 0012
documentation and its implementation report.

## Documentation

Documentation update: Story 0012 was reconciled with the current causal
authority, transaction, provenance and idempotence behavior. No API or runtime
documentation update was required.

## Vault Outcome

- Vault consulted: no.
- Outcome: no vault action.
- Rationale: reconciliation was determined from the canonical Story and the
  current repository implementation.
