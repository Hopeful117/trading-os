# cTrader Research Lifecycle And Kraken Storage Audit

**Date:** 2026-11-08

**Mode:** read-only repository, runtime and PostgreSQL audit

**Repository HEAD:** `c7954b40` (`Merge pull request #277 from Hopeful117/story/0222-agent-native-historical-research`)

**DevLog:** unavailable in the current tool environment. The current
repository, accepted ADRs, running containers, sanitized logs and bounded SQL
measurements were used instead.

## Executive Summary

The current research population is provider-mixed in historical reports, but
the runtime catalogue is separable and the FTMO/cTrader universe can be
measured independently.

Current cTrader findings:

- Four cTrader canonical markets are present: EUR/USD, GBP/USD, XAU/USD and
  US30/USD. All are CFDs in the current Market Data catalogue.
- All four have persisted OHLC evidence across 15-minute, 1-hour and 4-hour
  intervals.
- cTrader OHLC evidence totals 7,188 rows in the current database.
- Only GBP/USD has a persisted FeatureSnapshot and outcome: one of each.
- No historical reconstruction lifecycle or dataset export job is persisted.
- The cTrader research bottleneck is therefore not absence of raw OHLC. It is
  missing feature/outcome population and validation for the cTrader universe.

Current lifecycle findings:

- `RESEARCH_LIFECYCLE_MARKET_IDS` currently contains exactly one market ID,
  `e23ba62a-995f-4755-8deb-11a71ab3788b`.
- That market resolves to Kraken `BTC/USD`, product type
  `LINEAR_PERPETUAL`, provider symbol `PF_XBTUSD`.
- The property is consumed by the scheduled passive lifecycle, readiness and
  integrity projections, and replay-artifact population. It is not used by
  the direct user analysis endpoint, controlled historical reconstruction
  command, or population-defined dataset export selection.
- On-demand market selection is not globally restricted by this passive list.
  It remains subject to market existence, request limits and the applicable
  authorization decision.

Current storage findings:

- `historical_order_book_evidence` contains exactly 124,293,051 rows, all
  measured as Kraken `PF_XBTUSD` depth 25 evidence.
- The table plus its indexes occupies 90,962,591,744 bytes, approximately
  85 GiB. The full `market_data` database is 91,276,098,583 bytes, so this
  table accounts for approximately 99.7% of the database.
- The table is not currently being populated by the configured capture path:
  `ORDER_BOOK_EVIDENCE_CAPTURE_ENABLED=false`. Live Kraken order-book
  subscription and in-memory order-book publication remain enabled.
- Retention scheduling and destructive retirement are not enabled by the
  current environment defaults. The database contains 51 protected replay
  artifacts and 51 protected dependencies, with no evidence segments yet
  backfilled.

**Final verdicts:**

- cTrader research coverage: `PARTIAL_COVERAGE`
- Research market-selection architecture: `MIXED_CONFIGURATION_CONCERNS`
- Kraken storage: `SAFE_CLEANUP_CANDIDATE`

The storage verdict is conditional: cleanup is a candidate for human-approved,
provider-scoped retention work, not authorization to delete data. The current
ADR requires dependency-aware logical retirement and does not authorize
unreviewed bulk deletion or physical table rewrites.

## Safety And Baseline

No source code, configuration, database row, schema, service process, provider
state, credential, grant or broker state was changed. No service was restarted
or rebuilt. No data was deleted, exported, reconstructed, synchronized or
backfilled.

The worktree was already dirty before this audit. Existing modifications and
untracked reports were not changed or reverted. This audit adds only the report
file named in the request.

Running containers at inspection time included Market Data, Market
Intelligence, Trading Core, Broker Service, Gateway, Eureka and their local
PostgreSQL databases.

The host filesystem reported:

```text
filesystem: /
capacity: 449G
used: 415G
available: 12G
usage: 98%
```

This is an operational storage risk. It is not by itself permission to bypass
the order-book retention governance.

## References And Authority

The audit read the requested historical reports and the following authoritative
architecture and implementation sources:

- `ADR-057`: strict historical evidence and known-at semantics.
- `ADR-060`: deterministic feature projection boundary.
- `ADR-062`: versioned research dataset boundary.
- `ADR-074`: cTrader authoritative account synchronization boundary.
- `ADR-077`: Market Data ownership of cTrader market-data transport and source
  binding.
- `ADR-080`: bounded order-book evidence storage and dependency-aware retention.
- `ADR-081`: cTrader instrument-specific product classification.
- Story 0111 lifecycle operational runbook.
- Market Data Market catalogue, OHLC evidence, order-book repositories and
  migrations.
- Market Intelligence lifecycle, analysis, readiness, reconstruction and export
  services.
- Trading Core broker-account records.
- Running service configuration, logs and PostgreSQL catalog measurements.

Evidence labels used below:

- `OBSERVED`: directly observed in a running service, container or log.
- `CODE_CONFIRMED`: established from current repository implementation.
- `MEASURED`: established from bounded read-only SQL or catalog measurement.
- `INFERRED`: supported interpretation, not a direct persisted fact.
- `UNVERIFIED`: the implementation or runtime state could not establish it.
- `NOT_MEASURED`: deliberately not measured because the operation was unsafe,
  unavailable or outside the bounded audit.

## FTMO/cTrader Market Inventory

The canonical Market Data catalogue contains four cTrader markets. The
catalogue stores `product_type`, not a separate `asset_class` column; the
`CFD` product type is therefore used as the measured asset/product class.

| Canonical market | Symbol | Provider | Asset class/product type | OHLC rows | FeatureSnapshots | Outcomes |
|---|---|---|---|---:|---:|---:|
| `ba1576b7-7bd6-4260-a309-7e5a156cbce8` | EUR/USD | CTRADER | CFD | 1,912 | 0 | 0 |
| `8b336ec3-1557-4432-b052-ae569e041dac` | GBP/USD | CTRADER | CFD | 1,442 | 1 | 1 |
| `f3974ba8-c74a-4517-b9ba-001423ce028e` | XAU/USD | CTRADER | CFD | 1,918 | 0 | 0 |
| `f345a888-031d-48e6-a5fb-0e49d499a2eb` | US30/USD | CTRADER | CFD | 1,916 | 0 | 0 |
| **Total** |  | **CTRADER** | **CFD** | **7,188** | **1** | **1** |

All four cTrader markets have 15-minute, 1-hour and 4-hour evidence. The
catalogue measured statuses were:

| Symbol | Provider symbol ID | Trading status | Tradable |
|---|---:|---|---|
| EUR/USD | 1 | `UNKNOWN` | false |
| GBP/USD | 2 | `CLOSED` / `WEEKEND` | false |
| XAU/USD | 41 | `OPEN` | true |
| US30/USD | 283 | `CLOSED` / `OUTSIDE_TRADING_HOURS` | false |

Trading status is a current market-state observation, not a reason to discard
historical evidence. It must be rechecked before any live or paper workflow
uses a market.

### cTrader account and source binding

Trading Core contains a connected broker account named `FTMO Free cTrader` with
the cTrader provider registration and external account identity present. A
separate FTMO cTrader paper account and a revoked cTrader read-only account are
also present. This establishes a current cTrader account context in Trading
Core (`OBSERVED`).

The Market Data architecture deliberately keeps cTrader market-data source
binding separate from Trading Core broker-account ownership (`CODE_CONFIRMED`,
ADR-077). The Market Data catalogue does not contain a broker-account foreign
key for these four market rows, and the current `research_scope_market` table
does not provide a direct FTMO-account-to-cTrader-market association
(`MEASURED`). Therefore the following distinction is important:

- The four market rows are canonical cTrader Market Data instruments
  (`MEASURED`).
- The connected FTMO cTrader account is a Trading Core account binding
  (`MEASURED`).
- A durable account-owned universe mapping between the two was not established
  (`NOT_MEASURED` / not represented in the inspected Market Data schema).

This is consistent with ADR-077, which states that a cTrader account ID can be
an authorization/routing context for Market Data without becoming Trading OS
market-data ownership.

## cTrader Historical Coverage

### OHLC by market and interval

| Symbol | 15-minute | 1-hour | 4-hour | Earliest observed open | Latest observed open |
|---|---:|---:|---:|---|---|
| EUR/USD | 1,035 | 499 | 378 | 2026-09-14T13:00Z | 2026-11-08T22:15Z |
| GBP/USD | 861 | 345 | 236 | 2026-09-14T13:00Z | 2026-11-08T22:15Z |
| XAU/USD | 1,028 | 507 | 383 | 2026-09-14T13:00Z | 2026-11-06T21:45Z |
| US30/USD | 1,028 | 507 | 383 | 2026-09-14T13:00Z | 2026-11-06T21:45Z |

The current runtime logs show the Market Data OHLC capture scheduler actively
requesting all five configured market IDs, including the four cTrader IDs, at
the configured 15-minute interval. The latest log samples reported 199-200
accepted records per market and interval (`OBSERVED`).

The measured cTrader OHLC population is recent and bounded. It is not a
multi-year backfill. Strict temporal eligibility is supported by the evidence
model and the queried rows satisfy the inspected closed/non-synthetic/known-at
conditions for the current measurement window (`MEASURED` for the queried
population). A complete historical range beyond the persisted window was not
established (`UNVERIFIED`).

### Feature, outcome and execution coverage

The Market Intelligence database contains:

| cTrader research stage | Measured value |
|---|---:|
| Markets with persisted analysis executions | 2: EUR/USD and GBP/USD |
| cTrader analysis executions | 38 |
| Completed cTrader executions | 34 |
| FeatureSnapshots | 1, GBP/USD only |
| Outcomes | 1, GBP/USD only |
| Available outcomes | 1 |
| Exact snapshot/outcome cutoff pairs with an available outcome | 1 |
| Historical reconstruction lifecycles | 0 |
| Historical dataset export jobs | 0 |

The GBP/USD snapshot and outcome share cutoff
`2026-11-06T16:45:00Z`. No cTrader FeatureSnapshot exists for EUR/USD,
XAU/USD or US30/USD. The feature/outcome population is therefore not a
complete representation of the cTrader OHLC universe.

### Required cTrader-only summary

```text
cTrader canonical markets: 4
cTrader markets with historical OHLC: 4
cTrader historical OHLC rows: 7,188
cTrader markets with FeatureSnapshots: 1
cTrader FeatureSnapshots: 1
cTrader outcomes: 1
cTrader exact eligible feature/outcome pairs: 1
cTrader historical dataset exports: 0
```

These values exclude the Kraken BTC market. They are not interchangeable with
the larger provider-mixed counts in earlier reports.

### What is usable now

The current cTrader OHLC evidence can already support bounded strict analysis
for the four canonical markets, subject to current market identity, temporal
provenance and capability requirements. Only the one GBP/USD snapshot/outcome
pair has reached the persisted dataset boundary. The other three markets need
analysis execution, feature projection, outcome maturation and exact dataset
selection before they can contribute ML samples.

The cTrader population is therefore **partially covered**, not ML-ready as a
universe. The dominant gap is feature/outcome population rather than raw OHLC
ingestion.

## BTC Lifecycle Configuration

### Current values

The running `market-intelligence` container currently reports:

```properties
RESEARCH_LIFECYCLE_ENABLED=true
RESEARCH_LIFECYCLE_MARKET_IDS=e23ba62a-995f-4755-8deb-11a71ab3788b
RESEARCH_LIFECYCLE_OBSERVATION_CADENCE=PT1H
RESEARCH_LIFECYCLE_OUTCOME_HORIZONS=PT1H,PT4H,PT24H
RESEARCH_ARTIFACT_POPULATION_ENABLED=true
RESEARCH_ARTIFACT_POPULATION_MAX_PER_RUN=1
RESEARCH_ARTIFACT_POPULATION_DEPTH=25
```

The running environment also has Kraken fee coverage configured for the same
connected Kraken broker account, `KRAKEN`, `LINEAR_PERPETUAL` and `PF_XBTUSD`.
That fee configuration is separate from market selection but confirms the
historical reason for the current lifecycle setup (`OBSERVED`).

### Market identity

The configured ID resolves in the canonical Market Data catalogue to:

```text
market ID:       e23ba62a-995f-4755-8deb-11a71ab3788b
symbol:          BTC/USD
provider:        KRAKEN
provider symbol: PF_XBTUSD
product type:    LINEAR_PERPETUAL
trading status:  OPEN
tradable:        true
```

It is not a cTrader market and is not part of the FTMO/cTrader catalogue
measured above. The Story 0111 operational runbook explicitly identifies this
ID as the bounded BTC/USD lifecycle example. The current configuration is
therefore a documented passive Kraken development/accumulation configuration,
not an accidental cTrader identity (`CODE_CONFIRMED`, `OBSERVED`). Whether the
project owner wants to retain it as the active passive universe is a product
and operations decision; the code does not establish that intent.

### What the property controls

`ResearchLifecycleProperties` parses the property into a set of UUIDs and
requires at least one ID when the lifecycle is enabled. `ResearchLifecycleService`
uses that set to:

1. Submit aligned passive observations for each configured market.
2. Query completed strict executions for projection.
3. Find snapshots for matured outcome evaluation.
4. Evaluate the configured outcome horizons.
5. Release replay dependencies for configured markets after terminal outcomes.
6. Supply the market universe to the separate research readiness and integrity
   projections.
7. Supply the market universe to optional replay-artifact population.

The scheduler invokes this service every minute after a Market Data service
discovery check. The passive cadence is one hour; the one-minute fixed delay is
the orchestration poll interval.

The property does **not** control the canonical market catalogue, cTrader
symbol discovery, direct analysis creation, the historical reconstruction
command's market argument, or the market IDs encoded in a dataset population
specification (`CODE_CONFIRMED`).

### Configuration origin

The property is defined in Market Intelligence application properties and
overridden through Docker Compose environment variables. The single BTC ID is
also present in the Story 0111 operational runbook. The repository evidence
supports this classification:

```text
configuration class:      ResearchLifecycleProperties
runtime consumer:        ResearchLifecycleService and scheduled projections
documented origin:       Story 0111 bounded BTC/USD runbook
classification:          documented passive monitoring configuration
not established as:       global research allowlist or cTrader default
```

## Market-Selection Call Paths

### A. Scheduled passive research

```text
ResearchLifecycleScheduler
  -> DiscoveryClient checks market-data
  -> ResearchLifecycleService.runOnce()
  -> ResearchLifecycleProperties.getMarketIds()
  -> submitLatestObservation() for each configured ID
  -> AnalysisExecutionService with PASSIVE + STRICT_OBSERVED
  -> projectCompletedExecutions() for configured IDs
  -> FeatureSnapshotProjectionService
  -> evaluateMaturedOutcomes() for configured IDs
  -> MarketOutcomeEvaluationService
  -> releaseCompletedReplayDependencies() for configured IDs
```

This path is intentionally passive-universe scoped. It is the only path that
should be limited by `RESEARCH_LIFECYCLE_MARKET_IDS`.

### B. User-triggered direct analysis

```text
Authenticated user
  -> Gateway
  -> POST /api/v1/intelligence/analyses
  -> MarketIntelligenceController.analyze()
  -> AnalysisExecutionService.create()
  -> requested marketId in IntelligenceAnalysisRequest
```

The request contains its own market ID. The controller and execution service do
not read `ResearchLifecycleProperties` for this path (`CODE_CONFIRMED`). The
market must still exist and the normal request/security/capability rules apply.

### C. User/agent research workflow

```text
User or agent
  -> Gateway
  -> POST /api/v1/intelligence/research
  -> MarketIntelligenceController.startResearch()
  -> request.marketIds()
  -> applicable authorization decision
  -> ResearchApplicationService.start()
  -> research execution worker
  -> MarketDataClient.findMarket() for each selected market
  -> deterministic research report
```

`ResearchApplicationService` requires exactly two distinct market IDs for this
research-report workflow. That is a request contract, not a passive lifecycle
fallback. The controller passes the request IDs into authorization and the
research aggregate. The passive lifecycle market set is not consulted
(`CODE_CONFIRMED`).

The current agent authorization path still carries the explicit scope/grant
context required by the existing endpoint, while historical readiness/export
uses the accepted broker-account authorization boundary. This audit does not
change or reopen that authorization architecture.

### D. Agent historical readiness and export

```text
Authorized agent
  -> MCP adapter / Gateway
  -> POST /api/v1/intelligence/research/readiness
  -> HistoricalReadinessApplicationService
  -> broker-account/market authorization decision
  -> HistoricalReadinessService using request.marketIds()
```

The readiness request accepts one or two market IDs and passes them directly to
the readiness repositories. It does not obtain them from the passive lifecycle
set (`CODE_CONFIRMED`). An authorized agent can therefore inspect GBP/USD
without adding it to `RESEARCH_LIFECYCLE_MARKET_IDS`, subject to the exact
broker-account authorization decision (`CODE_CONFIRMED`; runtime authorization
for a new GBP/USD request was not performed in this read-only audit).

Agent export follows the same population-defined market selection:

```text
Authorized agent
  -> POST /api/v1/intelligence/research/{scopeId}/exports
  -> HistoricalDatasetExportApplicationService
  -> population.marketIds
  -> broker-account/market authorization
  -> ResearchDatasetExportService
```

The export service limits the population to one or two markets, resolves the
selected feature and outcome fingerprints, validates the population and writes
the bounded export only when an authorized export is actually requested. The
passive lifecycle set is not consulted (`CODE_CONFIRMED`).

### E. Historical reconstruction

```text
Authorized local operator command
  -> HistoricalReconstructionCommand --market-id <id>
  -> HistoricalReconstructionService.run(marketId, cutoff, ...)
  -> HistoricalReconstructionPreflight
  -> strict Market Data evidence selection
  -> historical AnalysisExecutionService boundary
  -> FeatureSnapshotProjectionService
  -> MarketOutcomeEvaluationService
  -> one-sample ResearchDatasetExportService
```

The command accepts an explicit `--market-id` and the service uses that ID
throughout the preflight, execution, projection, outcome and one-sample export.
It does not read the passive lifecycle market set (`CODE_CONFIRMED`). It remains
protected by the separate human confirmation and approval-reference boundary.

### F. Dataset export

```text
Authorized export request
  -> population JSON
  -> population.marketIds
  -> selected snapshot fingerprints
  -> selected outcome fingerprints
  -> authorization
  -> exact dataset selection and validation
  -> export bundle
```

The population spec, not the lifecycle configuration, is the source of market
selection (`CODE_CONFIRMED`). There are currently zero persisted export jobs
and no export was initiated during this audit (`MEASURED`).

## Is BTC A Global Restriction?

| Question | Finding | Evidence |
|---|---|---|
| Does the variable limit only the scheduled lifecycle? | Yes | `CODE_CONFIRMED` from lifecycle consumers; readiness/integrity also use it for their passive projections |
| Can on-demand analysis target GBP/USD without changing it? | Yes | `CODE_CONFIRMED`: direct analysis carries its own `marketId`; research request carries `marketIds` |
| Can an authorized agent inspect GBP/USD without changing it? | Yes, if authorization allows it | `CODE_CONFIRMED`; not runtime-tested in this audit |
| Can historical reconstruction target GBP/USD without changing it? | Yes | `CODE_CONFIRMED`: explicit `--market-id` |
| Can dataset export include GBP/USD without changing it? | Yes, if authorized and exact selections exist | `CODE_CONFIRMED`: population `marketIds`; current export job count is zero |
| Is another hard-coded passive market allowlist present? | No second lifecycle allowlist found | `CODE_CONFIRMED` search; other request-specific limits exist |
| Does a workflow silently fall back to BTC? | No fallback found | `CODE_CONFIRMED`; missing/unknown IDs produce market failure or authorization failure |
| Are market constraints in the correct layer? | Mostly yes, with mixed concerns | Passive selection is lifecycle-scoped; request authorization and market existence are separate |

The architecture is therefore not `ON_DEMAND_RESTRICTION_CONFIRMED`. It is a
mixed configuration concern because the runtime passive population is Kraken
BTC while the product priority is FTMO/cTrader, and because readiness/integrity
views reflect the configured passive set. This affects what is continuously
accumulated and what those dashboards report, but it does not make the passive
set a global on-demand allowlist.

No implementation correction is required solely to decouple on-demand market
selection from this variable. A separate operational decision is required for
which markets should be in the passive universe.

## Research Configuration Flexibility

| Capability | Finding | Evidence |
|---|---|---|
| Multiple lifecycle market IDs | Supported as a list/set | `CODE_CONFIRMED`; validation requires non-empty when enabled |
| Multiple providers in lifecycle | Model accepts UUIDs from the canonical catalogue | `CODE_CONFIRMED`; mixed-provider operational use not runtime-tested |
| Dynamic watchlist | Not implemented as a runtime watchlist source | `UNVERIFIED` beyond explicit environment configuration |
| User-specific passive universe | Not implemented by `ResearchLifecycleProperties` | `CODE_CONFIRMED` |
| Broker-account-based passive universe | Not implemented by the scheduler property | `CODE_CONFIRMED`; authorization is separate |
| On-demand market selection | Supported by request bodies/command/population | `CODE_CONFIRMED` |
| Add/remove lifecycle markets without restart | Not supported for environment-bound configuration | `CODE_CONFIRMED`; restart is required for container environment changes |
| Add/remove on-demand markets without restart | Supported if market catalogue and authorization permit | `CODE_CONFIRMED`; no lifecycle config change required |

The intended architecture should keep passive monitoring bounded and configurable
without forcing every canonical market into continuous processing. On-demand
research should continue to use explicit request market IDs plus market and
authorization validation.

## Kraken Order-Book Storage Inventory

### Actual storage

The market-data PostgreSQL catalog contains seven order-book-related tables.
The large raw store is not empty despite the current
`historical_order_book_evidence` count being reported as zero in an older
report; the older value was based on stale statistics, not an exact count.

| Table | Provider | Rows | Table size | Index size | Total size | Active writer | Active reader |
|---|---|---:|---:|---:|---:|---|---|
| `historical_order_book_evidence` | Kraken PF_XBTUSD | 124,293,051 exact | 32,848,773,120 B | 58,065,190,912 B | 90,962,591,744 B | Capture code exists; runtime capture disabled | Replay/artifact code and retention code |
| `historical_order_book_replay_artifact` | Kraken PF_XBTUSD | 51 | 72 kB | 64 kB | 85 MB catalog-reported | Market Intelligence promotion path | Market Intelligence context/research |
| `historical_order_book_sequence` | Kraken PF_XBTUSD | approx. 1,817 catalog estimate | 176 kB | 136 kB | 344 kB | Capture lifecycle | Replay/retention |
| `historical_order_book_evidence_dependency` | Kraken PF_XBTUSD | approx. 51 | 24 kB | 64 kB | 120 kB | Artifact promotion | Retention eligibility |
| `historical_order_book_evidence_segment` | Kraken PF_XBTUSD | 0 | 0 B | 32 kB | 32 kB | Segment backfill/capture metadata | Segment retention |
| `historical_order_book_retention_hold` | Provider-neutral | 0 | 0 B | 32 kB | 40 kB | Hold API | Retention eligibility |
| `historical_order_book_retirement_operation` | Provider-neutral | approx. 1 | 8 kB | 32 kB | 48 kB | Retention preview/mutation path | Audit state |

The table-provider attribution is measured from exact grouped evidence rows:

```text
provider:         KRAKEN
provider symbol:  PF_XBTUSD
depth:            25
evidence rows:    124,293,051
earliest known:   2026-10-17T12:26:58Z
latest known:     2026-10-29T04:33:29Z
```

The evidence breakdown is:

| Evidence type | Origin | Trusted | Rows |
|---|---|---|---:|
| DELTA | PROVIDER | true | 124,287,397 |
| SNAPSHOT | CHECKPOINT | true | 3,785 |
| SNAPSHOT | PROVIDER | true | 1,869 |

All exact evidence rows are Kraken PF_XBTUSD. No cTrader order-book evidence
was found in this table.

### Storage impact

The `market_data` database measured 91,276,098,583 bytes. The raw order-book
table represented approximately 99.7% of the database by total relation size.
The next largest market-data relation was `price_observations` at
approximately 151 MB. The order-book table's indexes alone occupied
approximately 54.1 GiB.

The host has only 12 GB free on `/`, while the raw table plus indexes is about
85 GiB. This makes the order-book relation operationally significant, not a
minor historical side table.

No daily growth rate was estimated. The evidence maximum is 2026-10-29 while
the current runtime date is 2026-11-08, and current capture is disabled. A
reliable growth-rate estimate would require a prior size snapshot or bounded
historical catalog samples that are not available in the current audit.

### Current write configuration and activity

The running Market Data container reports:

```properties
ORDER_BOOK_EVIDENCE_CAPTURE_ENABLED=false
ORDER_BOOK_EVIDENCE_CAPTURE_PROVIDER_SYMBOL=PF_XBTUSD
ORDER_BOOK_EVIDENCE_CAPTURE_DEPTH=25
ORDER_BOOK_ACQUISITION_STARTUP_ENABLED=true
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_PROVIDER=KRAKEN
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_SYMBOL=BTC/USD
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_PRODUCT_TYPE=LINEAR_PERPETUAL
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_PROVIDER_SYMBOL=PF_XBTUSD
ORDER_BOOK_ACQUISITION_DESIRED_SUBSCRIPTIONS_0_DEPTH=25
```

The live order-book subscription was restored and started at runtime. This is
an in-memory/live functionality path. Because evidence capture is disabled,
the subscription does not imply continued raw historical persistence
(`OBSERVED`).

No current evidence rows newer than 2026-10-29 were found. This is consistent
with capture having been disabled, but the exact disablement transition time is
not persisted (`INFERRED`).

### Retention state

Measured retention state:

```text
sequences:              1,759 SEALED; 110 INVALIDATED
replay artifacts:       51
dependencies:           51 PROTECTED; 0 RELEASED
retention holds:        0
evidence segments:      0
retirement operations:  1 DRY_RUN / SELECTED
```

The current retention configuration uses safe defaults unless overridden:

- destructive retirement disabled;
- retention scheduler disabled;
- dry-run/preview functionality exists;
- storage-pressure guard uses a 10 GB minimum-free default;
- logical segment retirement is dependency and hold aware.

The retention scheduler code is capable of previewing and, only when explicitly
enabled, retiring eligible segments in bounded batches. It does not currently
have segment rows to assess because segment metadata has not been backfilled.

## Writers And Readers

### Writers

The Kraken WebSocket provider maps subscribed order-book snapshots and updates
into canonical deltas. When evidence capture is enabled, the capture service:

- starts a provider sequence on a trusted snapshot;
- appends deltas only when sequence continuity is maintained;
- writes five-minute checkpoints;
- invalidates sequences on gaps or persistence uncertainty;
- applies a storage-pressure guard before persistence.

The persistence path is `OrderBookEvidenceCaptureService` ->
`OrderBookEvidenceRepository` -> `historical_order_book_evidence` and related
sequence/retention tables (`CODE_CONFIRMED`).

The current runtime has this writer disabled, but the code and subscription
configuration remain available. No cTrader order-book evidence writer is
configured in the current environment; cTrader order-book support is a
capture-only/local-observation boundary and is not the source of the measured
85 GiB relation (`CODE_CONFIRMED`, `MEASURED`).

### Readers

The data is read by these implemented paths:

- `OrderBookReplayService` and `OrderBookEvidenceRepository` reconstruct a
  trusted book from a snapshot plus continuous deltas.
- Market Intelligence `OrderBookContextContributor` can use replay context for
  analysis.
- Market Intelligence `ResearchArtifactPopulationService` promotes canonical
  replay artifacts for the configured passive lifecycle market.
- `OrderBookRetentionService` reads evidence, dependencies, artifacts and holds
  for retention preview and governed logical retirement.
- Storage health and diagnostics inspect capture state and active sequences.

The live order-book state service and event publisher are separate from raw
historical persistence. Disabling historical evidence capture does not disable
live order-book subscription, state reconstruction in memory or publication to
live consumers (`CODE_CONFIRMED`).

### Current research relationship

The active Market Intelligence lifecycle is configured for Kraken BTC and
artifact population is enabled. Runtime logs repeatedly show replay-artifact
promotion failures with `Forbidden`, followed by feature snapshot identity
conflicts for BTC executions. Therefore the raw order-book store is connected
to the current Kraken lifecycle design, but the current lifecycle is not
successfully converting new replay evidence into a healthy research artifact
path (`OBSERVED`).

The cTrader historical research objective does not consume this Kraken
PF_XBTUSD raw order-book table. cTrader research currently uses cTrader OHLC
evidence and has no persisted cTrader order-book dataset (`MEASURED`).

## Safe Storage Reduction Options

No option below was executed.

| Option | Benefit | Dependencies/risk | Disk behavior | Approval |
|---|---|---|---|---|
| Disable future raw capture | Prevents future growth; already true in current runtime | Must preserve live subscription semantics and verify no consumer requires raw replay | No immediate reclaim | Human operations approval if configuration is to be made durable |
| Governed logical retirement of eligible segments | Reclaims logical rows in bounded batches while preserving retention audit state | Requires segment backfill/classification, dependency release, no holds, trusted successor boundaries and dry-run confirmation; current segment table is empty | `DELETE` generally does not return relation space to OS; space becomes reusable inside PostgreSQL | Explicit human approval per ADR-080 and existing destructive-retention control |
| Retain only a bounded Kraken replay horizon | Prevents recurrence and reduces future storage | Requires a documented retention horizon and verification that no active research/replay dependency crosses it | Same logical-versus-physical distinction | Human architecture and operations approval |
| Delete all Kraken evidence rows directly | Potentially largest logical reduction | Unsafe: bypasses dependency governance and may destroy replay/research evidence; not authorized | `DELETE` alone normally does not return OS space | Not recommended; separate explicit approval would still be required |
| `TRUNCATE` the raw table | Fast logical removal | Breaks foreign-key/sequence/replay semantics and destroys all provider evidence; not authorized | Relation files may be reset, but dependent objects and operational recovery must be handled | Not recommended |
| `VACUUM` after logical deletion | Reclaims dead tuple space for PostgreSQL reuse | Does not provide predictable immediate OS reclamation; can consume I/O | Usually no immediate filesystem return | DBA approval and post-operation monitoring |
| `VACUUM FULL` | Can rewrite and return unused space to OS | Requires strong lock, temporary disk headroom and operational downtime; current 12 GB free is inadequate for an 85 GiB rewrite | May reclaim filesystem space after rewrite | Not safe under current capacity; explicit DBA approval required |
| Partition detach/drop | Fast physical reclamation when data is partitioned by governed range | Current raw table is a regular relation with no inspected partitions; requires a future partitioned design and dependency mapping | Detach/drop can return space after drop | Future architecture Story and human approval |
| Table recreation/swap | Potential physical reclamation | Requires downtime, full rewrite capacity, FK/index recreation and rollback plan | Can reclaim after old relation removal | Not safe under current capacity; explicit DBA approval |

### Cleanup approval checklist

Before any destructive cleanup is considered, a human reviewer must approve:

- exact provider and symbol predicate: Kraken `PF_XBTUSD`, depth 25;
- whether all 124,293,051 evidence rows are in scope or only eligible retired
  segments;
- verification that no current Market Intelligence or future approved workflow
  requires the retained range;
- replay artifact and dependency inventory, including the 51 protected
  dependencies;
- sequence and segment classification/backfill plan;
- dry-run fingerprint and candidate report;
- capture-disabled and live-order-book-preservation checks;
- PostgreSQL space and lock plan;
- backup/rollback and restore validation;
- post-cleanup relation-size and filesystem validation;
- explicit authorization for the exact destructive operation.

Under ADR-080, unknown legacy evidence fails closed. The absence of segment
metadata is not permission to delete by age or provider alone.

## Corrected Provider Coverage Matrix

| Provider | Asset class/product type | OHLC | FeatureSnapshots | Outcomes | Research priority |
|---|---|---:|---:|---:|---|
| CTRADER | CFD: EUR/USD, GBP/USD, XAU/USD, US30/USD | 7,188 | 1 | 1 | Current FTMO/cTrader priority |
| KRAKEN | LINEAR_PERPETUAL: BTC/USD | 5,245 | 523 | 3,562 | Deferred research; active passive lifecycle currently targets this market |
| KRAKEN | SPOT catalogue markets | Not included in current OHLC summary | Not included | Not included | Portfolio visibility/deferred research |
| Other | No additional provider rows measured in this audit | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | As applicable |

The Kraken BTC counts are intentionally kept separate from the cTrader counts.
The cTrader table is based on the four canonical `CTRADER` rows; the Kraken
table is based on the BTC canonical `KRAKEN` row. No provider statistics were
combined into the cTrader coverage conclusions.

## Corrected Diagnosis Of The Previous Report

### Is `FEATURE_PROJECTION_POPULATION_GAP` still correct for cTrader?

Yes, with provider-specific scope. cTrader has 7,188 OHLC rows across four
markets but only one FeatureSnapshot and one available outcome. The gap is
between raw cTrader inputs and the immutable feature/outcome dataset boundary.
The previous classification remains correct for cTrader, but provider-mixed
counts must not be used to describe the FTMO population.

### What causes the low GBP/USD and zero other cTrader snapshot counts?

The current passive lifecycle market list explains why the scheduled lifecycle
does not continuously populate cTrader: it contains only Kraken BTC. It does
not explain the one existing GBP/USD snapshot, which is persisted historical
state. The remaining missing cTrader snapshots are therefore caused by a
combination of passive-universe configuration and the absence of a completed
historical reconstruction/population run (`MEASURED` plus `INFERRED`).

The runtime also shows independent BTC projection identity conflicts and replay
promotion `Forbidden` failures. Those are confirmed current operational defects
for the Kraken lifecycle, but they are not evidence that cTrader OHLC ingestion
is absent.

### Does BTC lifecycle configuration block on-demand research?

No. Direct analysis, user/agent research requests, readiness, reconstruction and
dataset export obtain market IDs from their request or population contract.
They still require market existence, supported capability, temporal evidence and
the applicable authorization. The passive list affects scheduled accumulation
and passive readiness/integrity views, not global market selection.

### Is the historical pipeline biased toward Kraken?

Operationally, yes for the current passive lifecycle: its configured market,
fee coverage and replay-artifact path are Kraken BTC. This is a deployment
configuration bias, not a provider-agnostic architecture defect. The Market
Data service is actively capturing cTrader OHLC for four markets, so the raw
historical input path is not Kraken-only.

### Are order-book storage and ML coverage independent?

Yes. The large Kraken order-book table is a Market Data evidence-retention
problem. The cTrader ML readiness problem is a Market Intelligence
feature/outcome population problem. Kraken raw order-book cleanup can be
considered separately, provided live order-book functionality and approved
replay dependencies are preserved.

### Does the architecture support the desired flexible research behavior?

Yes, with current request limits. Passive monitoring is configurable and
selective. On-demand analysis and historical workflows accept explicit market
selection independent of the passive list. The implementation has separate
market-selection and authorization boundaries. No defect requiring a global
allowlist redesign was found.

## Ranked Next Actions

### 1. Establish a bounded cTrader passive/reconstruction population

**Problem:** Four cTrader markets have OHLC inputs but only one persisted
snapshot/outcome pair.

**Confirmed cause:** The active passive lifecycle is configured for Kraken BTC;
no cTrader reconstruction lifecycle or export job is persisted.

**Correction:** Human-select a bounded cTrader market set and cadence. Either
configure passive accumulation for that set or run the existing controlled
historical reconstruction path for a bounded sample. Do not add every catalogue
market by default.

**Benefit:** Produces cTrader-specific feature/outcome evidence and separates
FTMO research validation from Kraken accumulation.

**Risk:** Analysis workload, provider-data gaps and current snapshot/replay
operational failures may produce partial results.

**Approval:** Product/research scope and operational configuration approval.

**Acceptance criteria:** All selected markets are identified by canonical ID;
strict OHLC evidence is measured; feature snapshots and exact available
outcomes are produced; readiness and dataset validation report provider-specific
counts; no Kraken data is included accidentally.

### 2. Resolve cTrader population validation and lifecycle health

**Problem:** A raw OHLC count is not sufficient for ML readiness, and the
current runtime reports replay `Forbidden` and BTC snapshot identity conflicts.

**Confirmed cause:** Replay-artifact authorization/promotion is failing in the
current runtime; snapshot conflicts are being surfaced rather than overwritten.
The cTrader-specific cause of missing snapshots is not fully isolated without
running a reconstruction/population workflow, which is prohibited in this
audit.

**Correction:** Review the existing service authorization, artifact provenance,
snapshot identity inputs and exact temporal evidence checks in a separate
approved implementation/validation scope. Preserve fail-closed behavior.

**Benefit:** Makes readiness results trustworthy and prevents false ML claims.

**Risk:** Changes may affect retention dependencies and immutable identity
semantics.

**Approval:** Architecture and implementation approval; no authorization
redesign is implied.

**Acceptance criteria:** A bounded cTrader run completes without identity
conflicts; replay failures are either resolved or explicitly classified as
non-required for the selected profile; feature/outcome pairs pass exact
provenance and leakage checks; exports validate offline.

### 3. Govern Kraken order-book storage before filesystem pressure worsens

**Problem:** Kraken PF_XBTUSD order-book evidence occupies approximately 85 GiB
and the host has 12 GB free.

**Confirmed cause:** Historical evidence was previously captured at depth 25;
current capture is disabled, but existing rows and indexes remain.

**Correction:** Keep live subscription enabled if required, retain historical
capture disabled unless explicitly justified, then perform a dependency-aware
retention preview and approve only exact eligible logical segments. Define a
bounded future retention policy. Do not bulk-delete unknown legacy evidence.

**Benefit:** Prevents future unbounded growth and can reclaim logically unused
evidence while preserving current live Kraken functionality.

**Risk:** Incorrect cleanup can break replay reproducibility or protected
research artifacts. Physical reclamation may require capacity and downtime.

**Approval:** Explicit human operations/DBA approval for any destructive action.

**Acceptance criteria:** Provider/symbol/depth scope is verified; dependencies
are released or retained; dry-run fingerprint is reviewed; cleanup is bounded
and audited; live order-book state remains functional; PostgreSQL and filesystem
sizes are remeasured afterward.

## Required Human Approvals

No Engineering Story should be created from assumptions alone. The following
decisions require human review first:

- whether FTMO/cTrader should replace Kraken BTC as the active passive research
  universe;
- which cTrader markets, intervals, cadence and horizons are in scope;
- whether cTrader population should use passive accumulation or controlled
  reconstruction;
- whether current cTrader CFDs are valid for the intended research objective,
  consistent with ADR-081 classification and separate CFD settlement decisions;
- whether Kraken live order-book subscriptions must remain enabled;
- the exact Kraken evidence retention horizon and protected dependency policy;
- approval of any logical retirement or physical PostgreSQL operation;
- whether the current 12 GB filesystem headroom requires an emergency
  operational response before ordinary retention planning.

## Unresolved Questions

The following remain `UNVERIFIED` or `NOT_MEASURED`:

- Which specific cTrader markets are economically permitted by the current FTMO
  account beyond the canonical Market Data catalogue.
- Whether the cTrader Market Data source authorization and the Trading Core FTMO
  account are intentionally the same external account in the current deployment.
- Why the existing GBP/USD snapshot was created while the current lifecycle
  excludes GBP/USD.
- Exact transition time and deployment that disabled Kraken evidence capture.
- Whether any external consumer outside the inspected repository depends on the
  raw Kraken evidence relation.
- Whether all 51 protected replay artifacts remain required after a human
  review of their consumers and research history.
- Whether historical order-book segment backfill can be completed safely under
  current disk headroom.
- A reliable daily growth rate for future order-book storage.
- Whether the current cTrader product classifications and trading statuses are
  sufficient for the intended FTMO research sample; ADR-081 requires
  instrument-specific evidence and does not authorize settlement assumptions.

## Final Verdicts

### cTrader research coverage: `PARTIAL_COVERAGE`

Four canonical cTrader markets and 7,188 OHLC rows are measured, but only one
FeatureSnapshot, one outcome and one exact available pair exist. The raw input
stage is present; the research dataset stage is not populated across the
universe.

### Research market-selection architecture: `MIXED_CONFIGURATION_CONCERNS`

The passive lifecycle is intentionally limited to a configurable market list,
currently Kraken BTC. On-demand analysis, readiness, reconstruction and export
use explicit request/population market IDs and are not silently restricted to
that list. The concern is operational configuration bias and passive-report
scope, not a confirmed global on-demand restriction.

### Kraken storage: `SAFE_CLEANUP_CANDIDATE`

Kraken PF_XBTUSD order-book evidence is measured at 124,293,051 rows and
90,962,591,744 bytes, approximately 99.7% of the Market Data database. Capture
is currently disabled and cTrader research does not use this dataset. Cleanup
is a strong candidate, but only through ADR-080 dependency-aware review and
explicit human approval. Direct deletion, truncation, `VACUUM FULL`, partition
drop or table recreation were not performed and are not currently authorized.
