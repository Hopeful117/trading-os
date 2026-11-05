# Implementation Plan - Story 0204

## Operating Mode

This Story is offline-first and provider-isolated. No cTrader request, private
Kraken request, broker request, order, credential use or external runtime call
is authorized during implementation. Public Kraken runtime validation, if still
needed after offline validation, will require a separate human approval with a
fixed call budget and will not be performed automatically.

Preserve Stories 0198-0203 and all unrelated work. Do not modify T0/T1, Risk
Domain, Execution, Broker Service, FTMO policies or cTrader protections. Do not
commit, push, merge, reset, clean or rebase.

## Design Decisions

1. Reuse the existing Market Data, Active Scan, ProductionIntelligencePipeline,
   deterministic capabilities and Market Thesis boundaries. Do not create a
   scanner, analyzer, thesis model or microservice in parallel.
2. Resolve the crypto validation scope from the persisted Market catalogue and
   filter by explicit provider/product identity. Do not hardcode or invent UUIDs
   for BTC/USD, ETH/USD or SOL/USD.
3. Treat H4/H1/M15 evidence as a readiness concern. The implementation must
   report insufficient persisted history rather than enabling broad capture or
   manufacturing continuity.
4. Preserve the existing explicit degraded states. A missing, stale, invalid or
   contradictory input must not become a valid opportunity or neutral score.
5. Keep Market Thesis as the deterministic composition authority. Do not replace
   supporting, contradicting or missing evidence with a scalar score.
6. Reuse existing Observation and Market Thesis persistence. Add a migration or
   new aggregate only if a targeted test proves that the required negative
   outcome cannot be retained through the current model.
7. Define the AI-ready projection as a provider-neutral, serializable read model
   or DTO at the Market Intelligence boundary. It must contain references,
   statuses, versions and timestamps, not secrets, service handles or raw
   unrestricted source payloads.
8. Keep the AI projection advisory. It cannot create an Opportunity, authorize
   Risk, create a Trade Plan or trigger Execution.
9. Prefer tests and small contract corrections over broad refactoring. Do not
   change strategy definitions or business thresholds.

## 1. Scope and Catalogue Readiness

- Inspect the existing market catalogue model and Kraken mappers for provider,
  product type, canonical symbol and provider symbol.
- Add or extend provider-free tests proving that a requested crypto scope is
  resolved only from existing catalogue records and preserves Spot/Futures
  identity.
- Reuse the existing `ActiveScanScopeResolutionService` and active scan request
  contract. Do not add passive scheduling.
- Verify that the resulting scan path uses Market Data and Market Intelligence
  only, with no Broker Service dependency.

Likely files:

- `market-intelligence/.../application/scope/`
- `market-intelligence/.../application/scan/`
- `market-data/.../model/Market.java`
- existing market catalogue and scope tests.

## 2. Market Data Readiness

- Extend targeted tests around `MarketFactsService`, historical OHLC and the
  persisted evidence repository where coverage is missing.
- Prove chronological ordering, duplicate/conflict handling, incomplete candle
  behavior, gaps, stale evidence, cutoff handling and normalization provenance.
- Verify readiness for the configured H4/H1/M15 lookback using deterministic
  fixtures or existing repository data. Do not enable mass capture.
- If the current contract already returns an explicit unavailable or insufficient
  state, retain it rather than adding fallback behavior.

Likely files:

- `market-data/.../service/MarketFactsService.java`
- `market-data/.../service/HistoricalOhlcHistoryService.java`
- `market-data/.../adapter/persistence/JpaHistoricalOhlcEvidenceRepository.java`
- `market-data/src/test/java/.../MarketFactsServiceTest.java`
- `market-data/src/test/java/.../HistoricalOhlcHistoryServiceTest.java`
- `market-data/src/test/java/.../HistoricalOhlcEvidencePersistenceTest.java`

## 3. Deterministic Capability and Pipeline Validation

- Run and extend existing tests for Trend Context, Market Structure, Momentum
  and Tradeability.
- Verify the configured Trend Context roles remain H4 bias, H1 setup and M15
  trigger, with explicit alignment, contradiction, invalidation and freshness.
- Verify capability outputs remain separate typed artifacts and retain versions,
  fingerprints and evidence references.
- Trace `ProductionIntelligencePipeline` from observation construction through
  thesis persistence, strategy evaluation, opportunity creation and no-signal
  completion.
- Add only focused pipeline tests for candidate, no-signal, degraded and
  insufficient-data outcomes.

Likely files:

- `market-intelligence/.../application/pipeline/ProductionIntelligencePipeline.java`
- `market-intelligence/.../application/capability/`
- `market-intelligence/.../domain/trendcontext/`
- `market-intelligence/.../domain/marketstructure/`
- `market-intelligence/.../domain/momentum/`
- `market-intelligence/.../domain/tradeability/`
- existing capability, pipeline and domain tests.

## 4. Market Thesis and Negative Outcomes

- Verify `MarketThesisComposer` preserves market identity, source cutoff,
  validity, evidence roles, availability, contradiction severity, conditions,
  invalidation and fingerprints.
- Add or extend deterministic tests proving equal inputs and versions produce
  equal thesis content and identity.
- Verify JPA persistence and read projection preserve negative states and
  reasons across reload.
- Inspect whether no-signal and degraded executions already retain enough
  references. Only implement the smallest missing persistence/projection gap;
  do not redesign Observation or Market Thesis storage.
- Ensure Angular receives explicit state and reasons without converting missing
  evidence to a positive or neutral signal.

Likely files:

- `market-intelligence/.../application/thesis/MarketThesisComposer.java`
- `market-intelligence/.../domain/thesis/`
- `market-intelligence/.../adapter/persistence/JpaMarketThesisEntity.java`
- `market-intelligence/.../adapter/persistence/JpaMarketThesisRepository.java`
- `market-intelligence/.../adapter/web/MarketThesisController.java`
- existing thesis domain, persistence and controller tests.

## 5. AI-Ready Deterministic Projection

- Inspect `AiAnalysisCommand`, `AiStructuredResult`, `AiAnalysisCapability` and
  the existing context disclosure policy.
- Implement a minimal structured projection only if the existing contracts do
  not already provide the required serializable context.
- Include market identity, instrument/product/provider, source cutoff,
  evaluated/validity timestamps, deterministic evidence references, evidence
  role/status/severity, timeframe roles, rule/capability versions, fingerprints,
  missingness and contradictions.
- Exclude credentials, JWTs, account secrets, unrestricted service access and
  unnecessary private account facts.
- Add serialization and contract tests. Keep the disabled AI adapter unchanged
  unless a focused contract correction is required.

Likely files:

- `market-intelligence/.../application/port/AiAnalysisCommand.java`
- `market-intelligence/.../application/context/`
- `market-intelligence/.../domain/thesis/`
- a narrow new DTO/mapper under the existing Market Intelligence package only
  if reuse is insufficient.

## 6. Angular Validation

- Reuse current opportunity and Market Thesis models and services.
- Add or extend tests for `NO_TRADE`, `WATCH`, `CANDIDATE`, missing evidence and
  contradictions.
- Correct only a confirmed projection defect. Do not redesign components or
  introduce manual subscription patterns.
- Verify that score/ranking is not labeled or rendered as a probability and that
  deterministic evidence is not labeled as AI output.

Likely files:

- `trading-os-web/src/app/core/models/market-thesis.model.ts`
- `trading-os-web/src/app/core/models/opportunity.model.ts`
- `trading-os-web/src/app/features/opportunities/`
- existing opportunity and scan-panel specs.

## 7. Validation Commands

Run only after implementation:

- Targeted Market Data tests for market facts and historical OHLC.
- Targeted Market Intelligence tests for capability, pipeline, thesis,
  persistence, scope and negative outcomes.
- Targeted Angular opportunity and scan tests if frontend code changes.
- `cd market-intelligence && ../trading-core/mvnw test`.
- `cd market-data && ./mvnw test`.
- `cd trading-os-web && npm run check` when frontend code changes.
- `git diff --check`.

Broader quality validation is required if implementation touches more than tests
or a single narrow contract. No command may use real credentials or external
provider access.

## 8. Documentation Reconciliation

- Update only canonical documentation if implementation changes a documented
  API, configuration, runtime capability or roadmap state.
- Record the implementation outcome in Story-local `implementation-report.md`.
- Do not modify ADRs. If the implementation contradicts ADR-020, ADR-021,
  ADR-034, ADR-056 or ADR-076, stop and request an architectural decision.
- No Obsidian vault changes are permitted.

## 9. Expected Deliverables

- Focused implementation changes only where validation demonstrates a gap.
- Provider-free tests covering scope, data quality, deterministic outcomes,
  thesis reproducibility/persistence and AI-ready serialization.
- Angular test updates only where required by the current projection.
- `implementation-report.md` with reused components, changes, pipeline/data
  quality results, negative outcomes, AI readiness, Angular, tests, blockers and
  Story 0205 recommendation.

## 10. Stop Conditions

Stop and request guidance if:

- canonical crypto market identifiers cannot be resolved without a provider call;
- H4/H1/M15 readiness requires enabling unbounded or massive capture;
- negative outcome persistence requires a new architectural model;
- AI context requires private account data not justified by the use case;
- implementation would modify Risk, T0/T1, Execution, Broker Service or cTrader;
- any validation path requires real credentials or an external mutation;
- existing ADRs materially conflict with the required behavior.
