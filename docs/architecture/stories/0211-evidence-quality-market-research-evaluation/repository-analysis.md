# Repository Analysis - Story 0211

## Status

Proposed analysis. Implementation is not authorized until this analysis and the
implementation plan are explicitly approved.

## Baseline

- Current branch: `main`
- HEAD: the merged Story 0210 merge commit
- Working tree: clean at analysis time
- Relevant boundaries: ADR-088 and ADR-089

## Existing Reusable Components

### Market Intelligence

- `ResearchApplicationService` already validates a two-market scope, assembles
  canonical market identity and latest `MarketThesis`, runs deterministic
  analysis, invokes the optional AI capability, enforces one adaptive follow-up,
  and persists a bounded `ResearchReport` JSON document.
- `ResearchReport.MarketResearchResult` already separates deterministic output,
  freshness, provenance, supporting/contradicting/missing evidence,
  invalidation conditions, AI status and diagnostics.
- `MarketThesisEvidence` already carries evidence ID, role, availability,
  source, reference, timeframe, explanation, observed/validity timestamps,
  provider, instrument, source fingerprint, rule version, source cutoff and
  requiredness.
- `MarketThesis` enforces evidence identity, source-cutoff consistency and
  blocking-contradiction rules.
- `ResearchDatasetBuilder` and related exporters already provide deterministic,
  versioned historical dataset construction with fingerprints and explicit
  exclusion reasons. They are not a report-quality evaluator and should not be
  duplicated.
- `ResearchIntegrityService` already detects acquisition lag, stale snapshots,
  missing outcomes and timestamp anomalies for the lifecycle dataset.

### AI Engine

- `InterpretationRequest` and `InterpretationDraft` are strict provider-neutral
  Pydantic contracts.
- `InterpretationService` rejects unknown evidence references in supporting and
  contradictory arguments before returning an interpretation.
- The provider registry, mock provider and ChatGPT adapter from Story 0210 are
  reusable. Live calls must remain opt-in and excluded from CI.
- Current validation does not independently evaluate whether an interpretation
  covers missingness, freshness, contradictions, invalidation or uncertainty in
  a scenario-specific way.

### Persistence and Retrieval

- `ResearchExecution` owns actor-scoped idempotency and lifecycle state.
- `JpaResearchExecutionEntity` persists the bounded report as JSON and avoids
  duplicating large raw evidence payloads.
- Existing REST and MCP retrieval expose the report without adding a second
  evaluation service or MCP business capability.

## Observable Quality Gaps

1. Report provenance identifies Market Intelligence, provider, market and thesis,
   but does not consistently expose deterministic capability ID/version,
   analysis execution ID, input artifact/fingerprint or an evidence-level
   lineage tuple.
2. Freshness is reduced to a market-level `CURRENT`, `STALE` or `UNKNOWN` value;
   the report does not explain which evidence is stale relative to the research
   timestamp or source cutoff.
3. AI evidence-reference validation covers argument references, but there is no
   provider-independent rubric for unsupported claims, missing evidence,
   contradiction acknowledgement, invalidation quality or uncertainty language.
4. Adaptive records preserve proposed/executed/rejected capability metadata, but
   do not record whether returned evidence changed or refined the interpretation.
5. Existing historical dataset exports evaluate data eligibility and outcomes,
   not the quality of deterministic versus LLM-assisted research reports.
6. The current automated tests cover contract and failure behavior, but do not
   run a six-scenario BTC/USD versus ETH/USD evaluation matrix with an explicit
   machine-checkable rubric.

## Supported Interpretation

The smallest useful Story 0211 should add a provider-independent evaluation
fixture/rubric layer around the existing research workflow, improve only the
report lineage fields required by measured gaps, and produce a reviewable
comparison of deterministic, LLM-assisted and one-follow-up results. It should
not create a new service, MCP tool, provider, score authority or market-analysis
algorithm.

## Required Constraints

- Java remains authoritative for evidence, capability authorization, lifecycle
  and persistence.
- Python remains advisory and provider-neutral.
- MCP remains a protocol mapping layer and gains no evaluation business logic.
- Synthetic fixtures must be explicitly labeled and separated from real-data
  evaluation; no historical market observation may be invented.
- Human usefulness review remains qualitative. No authoritative probability or
  single opaque quality score is introduced.

## DevLog Context

The Story Agent applicability check completed against revision `92aa213f`, but
its projection was truncated and returned low confidence. It confirmed the
ADR-089 responsibility boundary and did not establish additional measurable
criteria. The complete repository artifacts above were therefore used as the
authoritative current context.

## Files Likely Affected After Approval

- `market-intelligence/.../domain/research/ResearchReport.java`
- `market-intelligence/.../application/research/ResearchApplicationService.java`
- `market-intelligence/src/test/.../application/research/*`
- `market-intelligence/src/test/resources/...` for clearly labeled fixtures
- `ai-engine/app/domain/models.py` and tests only if the evaluation exposes a
  concrete contract gap
- Story 0211 canonical artifacts and an implementation/evaluation report

No Gateway or MCP code is expected unless compatibility tests demonstrate a
necessary serialized-field mapping change.
