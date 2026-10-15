# Financial News Capability Investigation Report

## Outcome

The repository contains a sound provider-neutral Financial News foundation, but
it does not yet contain a production Financial News capability. The next Story
should introduce one provider adapter and bounded synchronization, after an
explicit decision on provider licensing, retention, redistribution, and AI-use
rights. The adapter must implement the existing normalized contract and must not
expand News Service into sentiment, recommendation, risk, or execution logic.

No provider is selected by this investigation.

## Evidence Reviewed

- Story 0073 and its implementation context.
- Story 0074 and its XOOMAR economic-calendar implementation.
- Story 0102 and its user-facing economic-context implementation.
- ADR-008, ADR-020, ADR-022, and ADR-048.
- News Service Financial News domain, persistence, API, security, configuration,
  source port, synchronization, and tests.
- Market Intelligence News client, context contributor, and context records.
- Gateway routing and local runtime configuration.
- Official provider documentation and terms for Alpha Vantage, Finnhub, GDELT,
  CryptoPanic, and Massive/Polygon.

## Current Capability

### Implemented foundation

The following responsibilities already belong to News Service:

- Normalized `FinancialNewsItem` facts.
- Source and item identity through `(sourceName, sourceItemId)`.
- Title, summary, canonical URL, publication time, publisher, categories,
  currencies, market references, informational impact, source update time,
  fetch time, and normalization version.
- PostgreSQL persistence with uniqueness on `(source_name, source_item_id)`.
- Bounded time, market, currency, impact, and limit filters.
- Authenticated public reads at `/api/v1/news/items`.
- Internal context reads at `/internal/v1/news/context/{marketId}`.
- Optional Market Intelligence `NEWS` context propagation.
- Explicit `AVAILABLE`, `STALE`, `UNAVAILABLE`, `UNSUPPORTED`, and `INCOMPLETE`
  statuses.
- Deterministic fixture coverage without external credentials.

### Not implemented

- A production Financial News provider adapter.
- Provider credentials or quota management.
- Financial News synchronization scheduling.
- A provider-specific incremental cursor or replay strategy.
- First-seen or ingestion-history tracking.
- Provider licensing and retention enforcement.
- A frontend Financial News feed.
- News-derived sentiment, ranking, opportunity detection, or trade decisions.

The existing XOOMAR adapter is an economic-calendar adapter only. It must not be
treated as evidence that Financial News is available.

## Domain Assessment

### Content depth

The current contract is appropriate for a factual V1 feed. It preserves an
excerpt or summary and canonical link without storing full article bodies. That
keeps the service focused on normalized facts and reduces copyright, retention,
and redistribution exposure.

The next Story should not add full article content, generated summaries,
sentiment, embeddings, or source-specific fields. If later analysis requires
deeper content, it should be introduced as a separately approved capability
with explicit rights and storage rules.

### Identity and deduplication

`NewsIdentity.newsItemId(sourceName, sourceItemId)` and the database uniqueness
constraint provide deterministic identity for repeated items from one source.
This does not deduplicate syndicated copies or the same story represented by
different providers. Cross-provider clustering is therefore out of scope for
the first provider integration.

The next adapter must provide a stable source item identifier. A URL may be a
fallback only when the provider documents it as stable; title or publication
time alone is not a safe identity.

### Temporal semantics

The current read contract uses `publishedAt` for bounded retrieval and retains
`sourceUpdatedAt` and `fetchedAt` for provenance and freshness. The context
window is the previous 48 hours through `now`, and freshness is evaluated from
`fetchedAt` against the configured maximum age.

This is sufficient for an initial feed, but it does not preserve every fetch or
record correction history. The next Story should define whether updates to an
existing item are last-write-wins and should reject provider records with no
usable publication time rather than inventing one.

### Market and currency references

Market references are stored as comma-separated UUID values and filtered with
string matching followed by an in-memory validation filter. This is compatible
with the current bounded foundation but is not a scalable relational index.

The next provider adapter must only attach market IDs when a deterministic
repository mapping exists. It must not infer unsupported instruments from free
text or provider symbols. Currency codes may be retained when explicitly
provided by the source.

## Current Gaps and Risks

### Provider port shape

`NewsSourcePort` combines `economicEvents()` and `financialNews()`. That was
acceptable for the fixture foundation, but a Financial News-only provider
would be forced to implement an unrelated economic-calendar method. The next
Story should split or otherwise narrow the synchronization boundary before
adding a production Financial News adapter, while preserving public contracts.

### Synchronization behavior

`NewsCatalogService.synchronize(NewsSourcePort)` saves complete provider result
lists in one transaction. It has no explicit batch size, cursor, deletion policy,
per-item error policy, or provider rate-limit handling. A production adapter
must expose bounded batches and a scheduler or job policy that avoids unsafe
automatic retries and does not treat partial provider responses as complete
catalog truth.

### Persistence and queryability

The schema has a publication-time index and a source identity uniqueness
constraint. Categories, currencies, and market IDs are stored as delimited
text, so their filters are not backed by relational indexes. This is acceptable
for the initial bounded catalog but should be measured before higher-volume
ingestion. A first provider Story should not redesign the schema without
runtime volume evidence.

### API attribution

Economic-event responses resolve configured attribution, while Financial News
responses currently return no attribution field. The provider integration Story
must decide whether provider attribution is required for Financial News and
implement it from normalized source names and configuration, not hardcoded
provider branches in the controller.

### Market Intelligence propagation

Financial News is propagated as factual optional context and retains source and
fetch timestamps. The internal response and `NewsItemContext` currently omit
`marketIds`, so downstream consumers cannot see the normalized market-reference
metadata even though News Service stores it. This should be corrected only if
the approved consumer contract requires it; it is not a reason to introduce
provider-specific fields.

### Operational observability

The repository does not yet establish provider-specific metrics for fetch count,
latency, quota responses, rejected records, partial batches, item age, or
deduplication counts. The next Story should add structured operational signals
and health/status behavior without logging credentials or raw provider payloads.

## Provider Comparison

This comparison is directional, not a legal approval. Current terms must be
reviewed for the intended account, deployment, retention period, user exposure,
and AI processing before implementation.

| Provider | Coverage fit | Integration fit | Main constraints | Initial assessment |
| --- | --- | --- | --- | --- |
| Alpha Vantage | Financial news and sentiment-oriented endpoint | Straightforward REST adapter | Free tier is quota-limited; licensing, redistribution, and derived-use terms require review | Candidate for a narrow authenticated prototype, not approved for production |
| Finnhub | Broad market and company news | Straightforward REST adapter | Terms reviewed during investigation include deletion obligations after subscription ends; retention and redistribution need explicit validation | Technically strong, operational/legal retention risk |
| GDELT | Broad global news and event coverage | More normalization and filtering required | General-news relevance, attribution, stability, and derived-use semantics need careful validation | Useful coverage candidate, weaker domain specificity |
| CryptoPanic | Crypto-focused market news | Simple focused adapter | Crypto scope, plan limits, redistribution, and historical retention require review | Suitable only if crypto coverage is an explicit product priority |
| Massive/Polygon | Market/company news endpoint | Straightforward REST adapter | Separate product and business terms; plan, redistribution, historical access, and AI-use rights require review | Strong market-data ecosystem fit, commercial terms unresolved |

No provider satisfies the product and governance requirements by documentation
alone. Provider selection remains a human and product decision.

## Recommended Next Implementation Boundary

Create a focused Financial News provider integration Story with this scope:

- Select exactly one provider after terms and retention approval.
- Define a provider-specific configuration and credential mechanism.
- Add a provider adapter under News Service infrastructure.
- Normalize only title, excerpt/summary where permitted, canonical URL,
  publication time, publisher, categories, currencies, deterministic source
  ID, and provenance.
- Add a narrow Financial News synchronization port or split the existing
  combined port.
- Implement bounded polling with explicit provider response and partial-data
  handling.
- Preserve idempotent upsert behavior and avoid unsafe automatic write retries.
- Add provider status, fetch metrics, rejected-record metrics, and freshness
  evidence without logging secrets or raw sensitive payloads.
- Add focused adapter, persistence, API, and context-contract tests using
  recorded or deterministic fixtures that are permitted by the provider terms.
- Validate in the local PostgreSQL/Compose environment with the provider
  disabled by default.

Keep these items out of that Story:

- Full article storage or redistribution.
- AI summaries, sentiment, embeddings, or RAG.
- Cross-provider story clustering.
- Automated opportunity ranking or trade-plan generation.
- Risk, execution, or Trading Core changes.
- Frontend feed design unless separately approved.

## Acceptance Criteria for the Next Story

- Provider terms and intended data-use rights are explicitly recorded and
  approved before credentials are enabled.
- Provider-specific DTOs, errors, symbols, and transport details remain inside
  the adapter.
- Existing `FinancialNewsItem` public semantics remain provider-neutral.
- Stable source identity and deterministic upsert behavior are tested.
- Publication and fetch timestamps are preserved; missing required timestamps
  are handled explicitly.
- Provider quota, timeout, malformed-record, and partial-response behavior is
  tested.
- Synchronization is bounded and does not silently erase prior catalog data.
- Financial News status distinguishes unavailable, stale, unsupported, and
  incomplete results.
- Existing News-unavailable Market Intelligence behavior remains valid.
- No risk, opportunity, execution, or broker side effect is introduced.
- Provider-disabled startup and local PostgreSQL validation pass.
- `git diff --check` passes.

## Validation Performed

Executed successfully before this report:

- `mvn -B verify` in `news-service`.
- `mvn -B test` in `gateway`.
- `mvn -B test` in `market-intelligence`.
- Runtime startup validation for `news-service`, Gateway, and Market
  Intelligence in the local Compose environment.
- Flyway/PostgreSQL schema validation for the News Service database.
- Unauthenticated `GET /api/v1/news/items` returned `401`, confirming the
  public endpoint remains protected.
- Repository and provider documentation inspection.

No production Financial News provider request was executed. No external
provider credentials were used.

## Recommendation

Proceed with a separate provider-integration Story only after human approval of
the provider and its data-use terms. The current foundation should remain
provider-neutral. Do not claim live Financial News availability, historical
completeness, or AI-processing rights until those have been validated for the
selected provider.

## Repository State

No application source files, migrations, configuration, or tests were modified
by this investigation. Existing unrelated untracked reports were preserved. No
commit, push, merge, or history rewrite was performed.
