# Implementation Report - Story 0118

## Outcome

Implemented the bounded economic research candidate path without changing the
existing economic domain authority, `research-dataset-v1`, Risk, PAPER,
persistence, migrations, broker behavior, or Python accounting.

## Implementation

Added a strict bounded `economic-research-candidate-v1` JSONL importer in
Trading Core. It rejects blank or malformed lines, unknown fields, unsupported
schema material, duplicate candidates, over-limit populations, invalid domain
values, LIMIT intents, and supplied fingerprint mismatches. Canonical candidate
serialization now includes the complete dataset definition required for safe
round-trip import.

Added the internal Trading Core evaluation path:

```text
POST /internal/v1/economic-research/evaluate
```

The adapter derives the exit cutoff from the canonical time-horizon policy and
delegates replay and accounting to `HistoricalEconomicEvaluationService`. Cost
inputs remain explicit: commission is unavailable, funding is unavailable for
applicable products and not applicable for spot, and no missing amount is
converted to zero. Unsupported or insufficient outcomes remain excluded rather
than inventing new result dispositions.

Added deterministic export for:

- `economic-intents.jsonl`;
- `economic-results.jsonl`;
- `economic-dataset-manifest.json`;
- `README.md`.

Exports are semantically ordered, newline-stable, UTF-8 SHA-256 checksummed, and
manifest-reconciled. Every candidate must have exactly one result disposition.

Market Intelligence now resolves only the exact referenced FeatureSnapshot by
fingerprint and verifies market, cutoff, projection, context, snapshot, and
identity fingerprints before invoking the internal Trading Core client. No
nearest/latest or population fallback exists.

The bounded orchestration path is now explicit: a service-authenticated Trading
Core orchestration endpoint delegates to Market Intelligence, which resolves
the exact snapshot and invokes the separate Trading Core evaluation endpoint.
The reverse Feign call uses the existing service-JWT audience and trusted-key
configuration in both services.

The candidate contract enforces `intent.decisionAt == featureSnapshot.evaluationCutoff`.
The request cost-model version must match every candidate. Manifest source-bundle
identity is derived from the dataset-definition fingerprint and sorted candidate
fingerprints, so transport-line reordering cannot change semantic identity.
Nested candidate objects are parsed strictly, and Market Intelligence rejects
oversized bundles and populations before snapshot resolution or remote calls.

## Changed Files

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/domain/economic/research/EconomicResearchCanonicalSerializer.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/research/EconomicResearchCandidateJsonlImporter.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/research/HistoricalEconomicResearchEvaluationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/research/EconomicResearchEvaluationController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/research/EconomicResearchMarketIntelligenceClient.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/research/EconomicResearchOrchestrationController.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/research/EconomicResearchExportService.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/application/research/EconomicResearchCandidateJsonlImporterTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/application/research/EconomicResearchOrchestrationControllerTest.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/EconomicResearchOrchestrationServiceTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/execution/economic/research/EconomicResearchContractTest.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/tradingcore/EconomicResearchClient.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/tradingcore/EconomicResearchFeignConfiguration.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/InternalEconomicResearchController.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/EconomicResearchOrchestrationService.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/ExactFeatureSnapshotResolver.java`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/ExactFeatureSnapshotResolverTest.java`
- `docs/architecture/stories/0118-economic-research-candidate-import-deterministic-export-pipeline/implementation-report.md`

## Validation

Passed:

```text
mvn -f trading-core/pom.xml -DskipTests compile
mvn -f market-intelligence/pom.xml -DskipTests compile
mvn -f trading-core/pom.xml -Dtest='*EconomicResearch*' test
mvn -f market-intelligence/pom.xml -Dtest='*EconomicResearch*' test
git diff --check
```

The Story-focused Trading Core suite passed with 18 tests, and the
Story-focused Market Intelligence suite passed with 2 tests. The complete
module suites were also started, but exceeded the local 120-second command
window before Maven emitted a final result; they are therefore not reported as
passed here. Both suites emitted existing
integration warnings, including transient H2/PostgreSQL connection warnings and
expected uniqueness-race logging, before timing out.

No bounded runtime proof was executed because no compatible immutable snapshot
and complete entry/exit historical evidence were established without mutating
runtime state.

## Runtime Validation

The Docker Compose runtime was already available and contained 21 immutable
feature snapshots and 56 observed market outcomes. Using the earliest stored
feature snapshot, the direct Trading Core endpoint
`POST /internal/v1/economic-research/evaluate` returned `200 OK` and produced a
single-candidate bundle with manifest fingerprint
`036a09f6a7dbd20d37166223a69f8cd44e05c44dced7974c56b90e0019ac1e63`.
The direct Market Intelligence endpoint
`POST /internal/v1/research/economic/evaluate` also returned `200 OK` with the
same deterministic result and fingerprint. The result disposition was
`EXECUTION_UNAVAILABLE` with `INSUFFICIENT_MARKET_EVIDENCE`, which is an
expected fail-closed outcome for the selected historical cutoff.

The cross-service Trading Core orchestration endpoint
`POST /internal/v1/economic-research/orchestrate` then returned `200 OK` after
the Eureka client cache refreshed the newly restarted Trading Core instance.
The response preserved the same manifest fingerprint and reported one
`EXECUTION_UNAVAILABLE` result with `INSUFFICIENT_MARKET_EVIDENCE`. The
initial failure was a runtime discovery race: Market Intelligence had not yet
seen Trading Core in its Eureka registry after the service restart.

## Frozen Boundaries

```text
research-dataset-v1 changed       = NO
Risk behavior changed             = NO
PAPER behavior changed            = NO
persistence or migrations added  = NO
broker behavior changed          = NO
Python accounting changed        = NO
public API or Gateway route added = NO
```

## Governance

DevLog applicability was checked but unavailable because its provider reported
exhausted quota. Current repository state and accepted ADR-064 were used as
authority. No ADR was modified or added; ADR-064 boundaries remain intact.
