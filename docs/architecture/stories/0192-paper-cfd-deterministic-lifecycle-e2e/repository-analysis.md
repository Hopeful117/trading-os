# Repository Analysis - Story 0192

> **Superseded scope note:** The historical analysis below was written for a
> positive PAPER CFD lifecycle. ADR-051 and ADR-083 remain authoritative, so
> that lifecycle is not currently routable or authorized. Current evidence is
> limited to persisted T0/T1 decisions and fail-closed compatibility behavior.
> Positive settlement, MTM, close, fee, and replay evidence is deferred until a
> future Phase 8 authorization Story.

## Baseline

```text
REMOTE_MAIN: 3ba1c87f83790ae11113234c02344ea67c3b8db7
LOCAL_BASE: 3ba1c87f83790ae11113234c02344ea67c3b8db7
LOCAL_HEAD: story branch created from origin/main
CURRENT_BRANCH: story/0192-paper-cfd-deterministic-lifecycle-e2e
WORKTREE_STATE: pre-existing untracked Story 0185, 0187, and 0189 directories preserved
NEXT_AVAILABLE_STORY: 0192
LATEST_MERGED_STORY: 0191 via PR #234
```

DevLog applicability was checked for `trading-os`. The available context did not
provide stronger current implementation evidence than the repository artifacts,
so the current repository, ADRs, Stories, and tests are authoritative for this
analysis. Obsidian was not consulted because the relevant lifecycle and safety
contracts are present in the repository.

## Current Production Lifecycle

`ExecuteTradeService.execute` loads the authorized `ExecutionIntent`, rejects
terminal or expired intents, and invokes
`ExecutionTimeRiskRevalidationService.evaluateAndPersist` for ENTRY intents
before the execution validation, idempotency, attempt creation, broker
submission, response processing, and finalization pipeline. A rejected T1 is
persisted and returned before broker submission.

`SimulatedExecutionAdapter` is the local PAPER execution adapter. It verifies
the broker account is `PAPER`, reads the market and fresh tradable price, and
returns an acknowledged simulated fill. It does not call an external broker.

`ExecutionFinalizationStep` verifies the account execution mode and resolves a
unique market product identity before routing. CFD identity that is missing or
ambiguous is unavailable and cannot fall through to generic settlement. A CFD
with incompatible settlement semantics is rejected before mutation. The
positive settlement behavior described in the historical paragraphs remains
deferred.

## Existing Durable Evidence

The historical `PaperCfdSettlementPersistenceHardeningIntegrationTest` contains
deferred positive capability evidence and is disabled while compatibility is
blocked. Active PostgreSQL evidence currently covers:

- independent T0/T1 persistence and linkage;
- T1 rejection before settlement and account mutation;
- direct and routed `CASH + CFD` rejection.

The positive lifecycle remains a future gap. It requires ADR-083 completion and
an approved compatibility change before it may be reintroduced as active
acceptance evidence.

## Historical Test Seam (Deferred)

The integration test should use the existing Spring production beans and
PostgreSQL persistence. It may control deterministic external facts through the
existing test ports/Mockito beans for market valuation, market data, broker risk
facts, and the simulated execution price. It must not replace the production
T1 service, execution pipeline, finalization step, or CFD settlement service.

The following positive path is retained as historical intent only and must not
be re-enabled until the Phase 8 gate is approved:

1. Create a PAPER broker account and account with the existing valid CFD fixture.
2. Create a CFD trade plan and obtain/persist the official T0 approval through
   the existing risk boundary.
3. Invoke the existing human-authorized execution-intent path.
4. Configure a fresh T1 valuation that passes, then call `ExecuteTradeService`.
5. Assert a local simulated fill, one open signed CFD trade, fee-adjusted
   realized balance, and persisted execution provenance.
6. Reload the account, change the mark, call the existing MTM refresh, and assert
   equity changes while realized balance and open quantity remain stable.
7. Create the authorized CFD exit intent, execute it through the same PAPER
   pipeline, and assert full close, realized PnL, exit fee, zero open exposure,
   and persisted reload.
8. Replay the same fill/settlement evidence and assert no duplicate trade,
   balance change, or provenance row.

The negative path should use the same production execution service with a fresh
T1 valuation that deterministically rejects. Assertions must include absence of
broker submission/fill, execution attempt settlement, trade creation, account
balance/equity mutation, exposure, and provider mutation.

## Compatibility Boundary

`SettlementCompatibilityPolicy` still rejects `CASH + CFD`, as required by
ADR-082 and Stories 0185/0189. `PaperCfdSettlementService` currently requires
the existing PAPER account representation and is reached from the PAPER
finalization boundary when the market contract is CFD. The Story must test this
narrow existing lifecycle seam; it must not weaken or bypass the global
compatibility contract by changing domain policy, migration state, or public
account semantics.

## Scope and Regression Boundaries

- Story 0191 persisted `MarginPolicy` authority and Story 0190 fresh T1
  valuation semantics must remain unchanged.
- The Risk Domain remains deterministic and provider-neutral.
- Broker Service and cTrader remain out of scope; no external write may occur.
- LIVE execution remains provider-authoritative.
- Existing settlement persistence hardening remains the regression baseline.
- Pre-existing untracked Story directories must not be staged, rewritten, or
  deleted.

## Risks and Unknowns

- The exact fixture needed to drive official T0 and T1 through all persisted
  dependencies must be assembled from existing risk integration patterns rather
  than introducing a test-only shortcut around the production services.
- Human authorization is represented by the existing execution-intent contract;
  the test must assert the intent cannot execute without the persisted approved
  risk reference.
- The test must distinguish local simulated execution from provider execution
  explicitly, rather than treating an acknowledged simulation as a broker call.

## Repository Analysis Conclusion

The repository already contains the production components and durable settlement
evidence needed for Story 0192. The smallest safe implementation is a focused
integration test, with only minimal production changes if the current test seam
reveals a genuine lifecycle defect. The test must preserve global compatibility
rejection and must not expand into a new settlement or execution architecture.

**Human approval required:** approve this Repository Analysis before an
Implementation Plan is produced. No production code or tests have been changed
by this analysis.
