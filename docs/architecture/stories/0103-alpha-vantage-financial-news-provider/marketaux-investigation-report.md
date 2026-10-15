# MARKETaux FINANCIAL NEWS PROVIDER INVESTIGATION

DATE: 2026-10-15
REPOSITORY HEAD: e9d7b7c6b60449d33ef302646dc41e85ae99bc3f
ORIGIN/MAIN: e9d7b7c6b60449d33ef302646dc41e85ae99bc3f
BRANCH: main
WORKTREE STATUS: pre-existing uncommitted Story 0103 changes and unrelated
reports are present; they were preserved. `git fetch origin` was attempted but
failed because the local SSH security-key agent refused signing.

==================================================
1. EXECUTIVE SUMMARY
==================================================

Marketaux is technically a strong candidate for the active development
Financial News provider. Its official Free plan currently advertises 100
requests/day, 3 articles per news request, instant access, global coverage,
5,000+ sources, 80+ markets, 30+ languages, crypto coverage, entity
identification, snippets, and a provider UUID that can be retrieved later.

The current Trading OS provider abstraction can host a Marketaux adapter without
domain changes. The existing Alpha Vantage adapter should remain retained and
disabled for future Premium evaluation.

The main blocker is not technical. Marketaux's official Terms of Use grant a
limited personal, non-commercial license, but also contain a broad prohibition
on access through automated or non-human means. The API documentation clearly
expects token-authenticated API requests, so the relationship between the API
service and that clause is ambiguous. The terms also do not clearly authorize
long-term storage, external LLM processing, or future ML dataset construction.

Recommendation: use Marketaux only with explicit limitations and obtain written
confirmation from Marketaux before treating it as a durable personal runtime
provider. Do not implement the adapter as part of this investigation.

==================================================
2. CURRENT STORY 0103 STATE
==================================================

FinancialNewsSourcePort:
  Exists as a capability-specific provider-neutral port with `financialNews`,
  `availability`, and an optional availability message.

Alpha Vantage adapter:
  Exists locally under `news-service/.../infrastructure/alphavantage`.
  It owns HTTP transport, query parameters, API-key handling, response parsing,
  normalization, quota classification, and scheduling.

Synchronization:
  `AlphaVantageFinancialNewsSynchronizationJob` uses a bounded lookback and
  fixed-delay polling. Persistence is additive through `NewsCatalogService`.

Persistence:
  `FinancialNewsItemEntity` stores normalized facts in PostgreSQL. The generated
  domain ID is deterministic from `(sourceName, sourceItemId)` through
  `NewsIdentity.newsItemId`.

Availability:
  The provider-neutral states are `AVAILABLE`, `STALE`, `UNAVAILABLE`,
  `INCOMPLETE`, and `UNSUPPORTED`. Alpha Vantage quota and transport failures
  map to `UNAVAILABLE`; partial normalization maps to `INCOMPLETE`.

Observability:
  The adapter reports attempted/completed synchronization, rejected count, and
  provider availability. It does not log raw payloads or credentials. The
  runtime report records that Alpha Vantage startup passed, but its controlled
  request returned a quota/rate-limit response and persisted zero items.

Tests:
  The local News Service suite passes with 55 tests after the independent review
  fixes. The repository also contains Market Intelligence propagation tests,
  Gateway tests, persistence tests, and Compose validation from Story 0103.

Provider abstraction ready for second provider:
PARTIAL

The port, domain item, persistence, public API, internal API, and Market
Intelligence contracts are provider-neutral. However, `NewsCatalogService`
currently resolves one `FinancialNewsSourcePort` through `getIfAvailable()`.
Multiple active financial-news beans are therefore ambiguous. Independent
synchronization jobs can write separately, but reads require one selected
provider or a future aggregation/selection decision.

==================================================
3. ALPHA VANTAGE STATUS
==================================================

NEWS_SENTIMENT Free:
  The current official Alpha Vantage documentation documents News & Sentiments
  under Alpha Intelligence, but the entitlement of the endpoint is not stated
  consistently in the fetched section. The previous controlled runtime request
  using the local Free key returned an unavailable quota/entitlement response
  before normalization. This is evidence that the current key cannot be used,
  not proof that every Free account has identical entitlement behavior.

NEWS-only Free endpoint:
  No separate financial-news-only endpoint was found in the current official
  documentation. `NEWS_SENTIMENT` remains the relevant Alpha Vantage news
  endpoint.

Adapter status:
  KEEP. Automated validation passes, startup passes, and the provider-neutral
  boundary is useful even though the current Free runtime did not return facts.

Recommended role:
  Retain as a disabled future Premium/V1 provider. Do not delete it and do not
  spend additional quota merely to repeat the prior result.

==================================================
4. MARKETAUX FREE TIER
==================================================

Requests/day:
  100.

Articles/request:
  3 on the Free plan.

Latency:
  The pricing page advertises instant news access. A separate published latency
  SLA was not found.

Historical access:
  The news endpoint documents `published_after`, `published_before`,
  `published_on`, and pagination for all plans. The Free page does not state a
  retention window, so long historical availability is UNCLEAR.

Markets:
  80+ global markets advertised; crypto, currencies, equities, ETFs, indices,
  commodities, and other entities are included in the public product material.

Languages:
  30+ languages advertised; the API accepts a comma-separated `language`
  filter.

Authentication:
  Required `api_token` query parameter. This creates the same URL logging risk
  as other query-token APIs and requires redaction in HTTP/access logs.

Personal live usage:
  UNCLEAR. The Terms grant personal, non-commercial use, but also prohibit
  automated/non-human access in broad language. Written provider confirmation is
  required before continuous personal polling is treated as permitted.

Sources:
  - https://www.marketaux.com/pricing
  - https://www.marketaux.com/documentation
  - https://www.marketaux.com/faq
  - https://www.marketaux.com/tos

==================================================
5. MARKETAUX API CONTRACT
==================================================

Endpoint:
  `GET https://api.marketaux.com/v1/news/all`.

Filters:
  `symbols`, `entity_types`, `industries`, `countries`, `sentiment_gte`,
  `sentiment_lte`, `min_match_score`, `filter_entities`,
  `must_have_entities`, `group_similar`, `search`, `domains`,
  `exclude_domains`, `source_ids`, `exclude_source_ids`, `language`,
  `published_before`, `published_after`, `published_on`, `sort`,
  `sort_order`, `limit`, and `page`.

Pagination:
  `page` is 1-based. The maximum result set is 20,000. Every paginated request
  consumes another request allowance. Free `limit` is capped at 3.

Response envelope:
  Success returns `meta.found`, `meta.returned`, `meta.limit`, `meta.page`, and
  `data` article objects. No-results responses return an empty `data` object.

Identity:
  Each article has a `uuid`; the documentation calls it the unique identifier
  and explicitly says it can be stored and used with `GET /v1/news/uuid/uuid`.

Timestamps:
  `published_at` is supplied in UTC. No provider ingestion timestamp or article
  revision/update timestamp is documented.

Publisher:
  `source` is the source domain, e.g. `investing.com`, not the Marketaux
  provider identity. This preserves provider/publisher separation at domain
  level, but does not provide author-level provenance.

Content depth:
  The API exposes `title`, `description`, `keywords`, and `snippet`. The FAQ
  states that only a short snippet and link are provided, not the full article.
  The documentation describes the snippet as the first 60 characters of the
  article body.

Entities:
  Entities expose `symbol`, `name`, `exchange`, `exchange_long`, `country`,
  `type`, `industry`, `match_score`, `sentiment_score`, and highlights.

Crypto metadata:
  The official product page explicitly includes crypto. Official examples show
  cryptocurrency entities such as BTCUSD and ETHUSD with exchange `CC` and
  country `global`. The exact symbols supported should be resolved through the
  entity search endpoint rather than guessed.

Sentiment:
  Sentiment is entity-level and highlight-level, scored from -1 to +1. It is
  not a Trading OS impact field.

Error model:
  Official documentation describes JSON `{error:{code,message}}` responses.
  Common codes include `malformed_parameters` (400), `invalid_api_token` (401),
  `usage_limit_reached` (402), `endpoint_access_restricted` (403), 404 for
  missing resources/routes, 429 for rate limits within a 60-second window, and
  500/503 for server/maintenance failures.

==================================================
6. FINANCIALNEWSITEM MAPPING
==================================================

| FinancialNewsItem | Marketaux | Mapping | Confidence |
|---|---|---|---|
| sourceName | provider constant | `marketaux` | HIGH |
| sourceItemId | `uuid` | direct provider ID | HIGH |
| title | `title` | direct | HIGH |
| summary | `description` preferred, otherwise `snippet` | bounded excerpt | HIGH |
| canonicalUrl | `url` | direct | HIGH |
| publishedAt | `published_at` | parse UTC | HIGH |
| publisher | `source` domain | direct domain provenance | HIGH |
| categories | `entities[].industry`, entity type, bounded keywords | only if bounded and semantically accepted | MEDIUM |
| currencies | deterministic crypto/FX entity symbol mapping | code normalization only | MEDIUM |
| marketIds | entity symbol to canonical market registry | only after repository mapping | LOW |
| impact | no equivalent | `UNKNOWN` | HIGH |
| sourceUpdatedAt | not documented | `null` | HIGH |
| fetchedAt | local acquisition time | `Instant.now()` at fetch | HIGH |
| normalizationVersion | adapter constant | e.g. `marketaux-v1` | HIGH |

Required domain changes:
NONE for the first adapter. The existing model already supports the required
facts. No provider sentiment should be mapped to `ImpactLevel`.

==================================================
7. CRYPTO COVERAGE
==================================================

Marketaux is materially better aligned than a generic headline feed: its public
product page explicitly includes crypto and its entity model has cryptocurrency
entities, symbols, exchanges, and sentiment. BTC and ETH-style entities are
shown in official examples. The news endpoint can filter by identified symbols,
entity type, search terms, and publication window.

Useful first-pass filters would likely be one request using the provider's
verified crypto symbols for Bitcoin and Ethereum, plus a publication overlap.
The exact Marketaux entity symbols must be obtained from the entity search
endpoint during Story 0103B; `BTC`, `BTCUSD`, and exchange-specific symbols must
not be assumed interchangeable with Trading OS canonical markets.

Coverage remains a quantity risk: the Free plan returns only 3 articles per
request. Narrow crypto filters improve relevance but may reduce breadth.

==================================================
8. MACRO COVERAGE
==================================================

The endpoint searches article titles and full text and supports global financial
news, industries, countries, and languages. It should be capable of returning
qualitative context around Federal Reserve policy, rates, CPI, employment, USD,
banking, regulation, and risk-on/risk-off events when those terms occur in
covered sources.

The official API contract does not expose a dedicated macro event taxonomy
equivalent to the existing Economic Calendar. Marketaux should therefore remain
qualitative news context only. The dedicated Economic Calendar remains the
authoritative source for scheduled macro facts and numeric releases.

==================================================
9. TEMPORAL / NO-LOOKAHEAD READINESS
==================================================

publishedAt:
  Available as provider `published_at` in UTC.

sourceUpdatedAt:
  Not documented. It must remain null rather than being fabricated.

fetchedAt:
  Can be assigned locally at acquisition time.

firstSeenAt:
  Not provided. It cannot be reconstructed reliably from polling alone and must
  remain out of scope.

Known limitations:
  A polling window can observe an article late, and repeated overlapping windows
  do not prove first-seen time. Backtesting must use `published_at` and retain
  explicit acquisition evidence separately if a future story needs it.

==================================================
10. STORAGE / LICENSING
==================================================

Personal use:
  LIKELY, not fully confirmed. The Terms grant a limited license for personal,
  non-commercial use.

Private UI display:
  LIKELY for personal/non-commercial use, but the broad automated-access clause
  makes this require written confirmation for an API-backed private application.

Normalized persistence:
  UNCLEAR. The Terms allow downloading/accessing Content for personal,
  non-commercial use but do not state an API cache or retention period.

Title storage:
  UNCLEAR because title is third-party content and no API retention rule is
  stated.

Description/snippet storage:
  UNCLEAR. The provider supplies only a short snippet, but no persistence right
  or deletion window is documented.

URL storage:
  LIKELY and operationally necessary for provenance, but not explicitly granted
  as a durable API-cache right.

Metadata storage:
  UNCLEAR. Entity and sentiment metadata are provider-derived content; no clear
  retention or derived-data license was found.

Retention:
  UNCLEAR. No Marketaux API retention period, cache duration, or deletion
  obligation was found in the public documentation or Terms.

Redistribution:
  PROHIBITED/NOT AUTHORIZED for public or commercial redistribution by the
  current personal/non-commercial license. The Terms reserve personal use and
  prohibit unauthorized use; no public API, SaaS, or redistribution grant was
  found.

Commercialization:
  PROHIBITED under the Free personal/non-commercial license unless a different
  written commercial agreement is obtained.

Classification:
UNCLEAR for durable storage and private automated runtime; PROHIBITED for
public/commercial redistribution under the current terms.

==================================================
11. LLM / AI RIGHTS
==================================================

External LLM processing:
  UNCLEAR. The official Terms and public API documentation reviewed do not
  expressly allow or prohibit sending Marketaux snippets or metadata to an
  external LLM.

Derived analytics:
  UNCLEAR. Sentiment and entity annotations are supplied by Marketaux, but the
  terms do not clearly define rights to create or retain derived analytics.

AI-generated sentiment:
  UNCLEAR contractually and out of scope architecturally. It must not be added
  to the provider adapter or treated as a provider permission assumption.

Provider restrictions:
  Personal/non-commercial use is stated; no explicit AI, LLM, or model-training
  clause was found in the reviewed official Terms.

Verdict:
Do not send Marketaux content to an external LLM until Marketaux gives written
permission or the applicable provider terms are clarified. The current adapter
design can retain factual excerpts without implementing AI.

==================================================
12. FUTURE ML RIGHTS
==================================================

Historical dataset:
  UNCLEAR. No dataset retention or redistribution permission was found.

Feature engineering:
  UNCLEAR. Provider sentiment and entity metadata may be usable as temporary
  features, but the Terms do not grant a derived-data license.

Model training:
  UNCLEAR and not approved. No explicit model-training permission was found.

Backtesting:
  UNCLEAR. Stored publication facts may be technically useful, but legal rights
  for long-term retention and historical evaluation are not documented.

Derived data:
  UNCLEAR. Do not treat silence as permission.

Verdict:
Future ML rights remain unresolved and must not block a narrowly scoped factual
development experiment if personal runtime use is confirmed, but they must be
resolved before building a retained dataset or training pipeline.

==================================================
13. PROVIDER SENTIMENT
==================================================

Available:
  Yes. Entity-level `sentiment_score` and highlight sentiment are documented.

Semantics:
  A score from -1 to +1, attached to identified entities and highlighted text.
  It is not an article-wide authoritative market-impact classification.

Free Tier:
  The news endpoint is available on all plans; sentiment fields are shown in the
  official response contract. The exact per-plan availability of every sentiment
  detail is not separately enumerated, so this is treated as documented but not
  independently live-tested.

Recommended Trading OS role:
  Retain only as an external feature or comparison signal in a future story.
  Preserve raw/normalized factual news first and keep `ImpactLevel.UNKNOWN`.

AUTHORITATIVE:
NO

==================================================
14. FREE-TIER POLLING OPTIONS
==================================================

Assumptions for all options: one news request per poll, no pagination, Free
limit of 3, and a bounded overlapping publication window. API allowance is
100 requests/day.

CONSERVATIVE:
  Every 6 hours: 4 requests/day, up to 12 article slots/day.
  Lowest quota risk; poor freshness for active discretionary use.

BALANCED:
  Every 2 hours: 12 requests/day, up to 36 article slots/day.
  A practical development compromise with enough overlap for late publication.

AGGRESSIVE:
  Every 30 minutes: 48 requests/day, up to 144 article slots/day.
  Leaves less than half the daily allowance for manual exploration and gives
  little guaranteed breadth because the response cap remains 3.

Recommended:
  BALANCED, one crypto-focused request every 2 hours, with an overlap window and
  no automatic pagination. Reserve the remaining quota for controlled manual
  macro/search requests. Do not use multiple topic-specific requests until real
  article yield justifies the quota cost.

==================================================
15. ERROR / QUOTA MODEL
==================================================

Quota exhaustion:
  Official code `usage_limit_reached` with HTTP 402.

Rate limiting:
  HTTP 429 when request thresholds are exceeded within a 60-second window.
  Exact per-minute thresholds were not published in the documentation reviewed.

Invalid token:
  HTTP 401 with `invalid_api_token`.

Plan entitlement:
  HTTP 403 with `endpoint_access_restricted`.

Malformed response/request:
  HTTP 400 with `malformed_parameters` for documented parameter errors.

Timeout:
  Not a provider payload classification; the adapter must classify transport
  timeout as `UNAVAILABLE` with bounded retry behavior and no unsafe retries.

Recommended internal distinction:
  Preserve `quota_exhausted`, `rate_limited`, `invalid_token`,
  `plan_restricted`, `malformed_request`, `provider_unavailable`, and
  `timeout` internally, while mapping them to the existing provider-neutral
  availability model.

==================================================
16. ALPHA VANTAGE COMPARISON
==================================================

| Criterion | Marketaux Free | Alpha Vantage Free | Alpha Vantage Premium |
|---|---|---|---|
| Financial News | Available endpoint | `NEWS_SENTIMENT`, current key failed entitlement/quota validation | Premium status/entitlement should be rechecked before use |
| Crypto coverage | Explicit crypto entities and symbols | Ticker/topic filters documented; prior BTC/ETH call failed | Same endpoint with premium access expected, exact output not revalidated |
| Macro coverage | Search and global financial news | Topics and time filters | Same endpoint, plan-dependent |
| Calls/day | 100 | Official Premium page states standard limit 25/day | No daily limits advertised on Premium page |
| Articles/request | 3 | Adapter configured up to documented 1000, current entitlement unverified | Exact news result entitlement not independently verified |
| Stable article ID | UUID plus UUID lookup endpoint | No explicit stable article ID in Story 0103 contract | Same contract limitation unless provider changes it |
| Summary/excerpt | Description and short snippet; no full body | Summary field documented | Same endpoint contract |
| Historical access | Date filters documented; retention unclear | Time window documented; entitlement result unclear | Premium access not independently tested |
| Personal live use | Personal license but automation/storage ambiguous | Current local key did not produce facts | Premium page supports personal use for some APIs; news terms not fully resolved |
| Storage clarity | UNCLEAR | Deferred in Story 0103 | Deferred pending terms review |
| LLM-use clarity | UNCLEAR | Deferred in Story 0103 | Deferred |
| ML-use clarity | UNCLEAR | Deferred in Story 0103 | Deferred |
| Provider sentiment | Entity/highlight score -1 to +1 | Sentiment endpoint data documented | Same contract, entitlement dependent |
| Trading OS fit now | Strong technical fit with legal limitation | Poor current runtime fit | Future candidate |
| Trading OS V1 fit | Conditional on written rights clarification | Conditional on Premium entitlement and rights | Candidate after entitlement/rights validation |

==================================================
17. ALTERNATIVE PROVIDER SANITY CHECK
==================================================

NewsAPI.ai:
  Current official pricing and terms URLs were not retrievable during this
  investigation. No reliable current official evidence was available, so it
  cannot displace Marketaux on evidence quality.

NewsAPI.org:
  Official Developer plan: 100 requests/day, 24-hour article delay, search up
  to one month old, development/testing only, and not staging or production,
  including internally. Its terms prohibit reproducing/republishing copyrighted
  material and building a competing news database. It is useful for a pure
  development fixture path but not better for an active personal live Trading OS
  runtime.

CryptoPanic:
  Official developer/API pages were reachable but did not expose enough
  machine-readable current pricing, quota, storage, or licensing detail in the
  reviewed response. It is crypto-focused, but the evidence is insufficient to
  recommend it over Marketaux for this investigation.

Conclusion:
  No alternative is clearly superior based on current official evidence.

==================================================
18. DECISION MATRIX
==================================================

| Criterion | Weight | Score (0-5) | Weighted points | Reason |
|---|---:|---:|---:|---|
| Crypto relevance | 20% | 4 | 16 | Explicit crypto coverage and entity model |
| Free-tier usability | 15% | 4 | 12 | 100 requests/day but only 3 articles/request |
| Normalized-domain fit | 15% | 5 | 15 | UUID, title, excerpt, URL, timestamp, source map cleanly |
| Stable identity/provenance | 10% | 5 | 10 | UUID lookup and source-domain provenance |
| Temporal correctness | 10% | 3 | 6 | published_at exists; no update/ingestion timestamp |
| Storage/legal clarity | 10% | 2 | 4 | personal license but automation/storage are ambiguous |
| Future LLM suitability | 10% | 1 | 2 | no explicit external LLM permission |
| Future ML suitability | 5% | 1 | 1 | no explicit dataset/model-training permission |
| Operational simplicity | 5% | 4 | 4 | simple GET API and explicit error codes |

Weighted score:
70/100

Licensing veto:
NO confirmed veto, but a written clarification is a prerequisite for active
continuous use. Public/commercial redistribution is not permitted by the
current personal/non-commercial terms.

==================================================
19. RISKS
==================================================

1. The broad automated-access prohibition in the Terms may conflict with the
   intended scheduled API polling model.
2. Persistent storage and retention of third-party title/snippet/metadata are
   not expressly addressed.
3. External LLM processing and future ML dataset construction are not expressly
   permitted.
4. The Free cap of 3 articles/request may produce insufficient crypto breadth.
5. `api_token` is a query parameter and can leak through HTTP/access logs unless
   redacted.
6. Two simultaneously enabled financial providers are ambiguous in the current
   `ObjectProvider` read path.
7. Provider UUID stability across article revisions is not explicitly stated,
   although UUID lookup and storage are documented.
8. Macro coverage is qualitative and cannot replace the existing Economic
   Calendar.
9. Source provenance is domain-level, not necessarily publisher/legal-entity or
   author-level provenance.
10. Free-plan facts and terms can change; the official Terms page was last
    updated January 10, 2021 while pricing/documentation are current.

==================================================
20. DECISION
==================================================

DECISION B
PROCEED WITH MARKETAUX WITH EXPLICIT LIMITATIONS

Marketaux is the best evidenced technical candidate for the current development
phase. Proceed only with a narrowly bounded personal/non-commercial experiment,
one active financial provider at a time, no public/commercial redistribution,
no external LLM/ML processing, no assumed long-term retention rights, and a
written clarification request to Marketaux covering automated polling, private
display, normalized storage, retention, derived data, and AI/ML use.

If Marketaux does not clarify these rights favorably, stop and reassess rather
than silently treating the API as permitted.

==================================================
21. STORY 0103 IMPACT
==================================================

Keep:
  `FinancialNewsSourcePort`, `FinancialNewsItem`, persistence, public/internal
  APIs, Market Intelligence contracts, Alpha Vantage adapter, Alpha Vantage
  tests, and Alpha Vantage disabled-by-default configuration.

Change later:
  Add explicit active-provider selection or enforce exactly one active financial
  provider. Add Marketaux-specific configuration, error classification,
  fixtures, and bounded synchronization only after rights clarification.

Remove:
  NONE.

==================================================
22. RECOMMENDED NEXT STORY
==================================================

Story 0103B - Marketaux Financial News Provider

Scope:
  Add a disabled-by-default Marketaux adapter behind
  `FinancialNewsSourcePort`, normalize Marketaux UUID/articles into the existing
  `FinancialNewsItem`, add explicit provider error classification, configure a
  single active development provider, add bounded polling and fixtures, and
  validate one controlled runtime request if rights are confirmed.

Out of scope:
  AI/LLM processing, sentiment interpretation, frontend changes, Risk,
  execution, public redistribution, provider aggregation, cross-provider
  deduplication, first-seen history, long-term ML datasets, and Alpha Vantage
  removal.

==================================================
23. SOURCES
==================================================

Access date for all sources below: 2026-10-15.

Official Marketaux:
  - https://www.marketaux.com/documentation
  - https://www.marketaux.com/pricing
  - https://www.marketaux.com/faq
  - https://www.marketaux.com/tos
  - https://www.marketaux.com/news/country/us

Official Alpha Vantage:
  - https://www.alphavantage.co/documentation/
  - https://www.alphavantage.co/premium/

Official NewsAPI.org:
  - https://newsapi.org/pricing
  - https://newsapi.org/terms

Official alternative pages reviewed:
  - https://newsapi.ai/pricing (not available as a current retrievable page)
  - https://newsapi.ai/terms-of-use (not available as a current retrievable page)
  - https://cryptopanic.com/developers/api/ (insufficient machine-readable
    current quota/licensing detail in the retrieved page)

==================================================
24. REPOSITORY MUTATIONS
==================================================

Application source modified:
NO. This investigation only inspected the existing worktree. The uncommitted
Story 0103 application changes predated this investigation and were preserved.

Git operations modifying history:
NO.

Unrelated files preserved:
YES.

The only new artifact from this investigation is this report. No Marketaux
adapter, configuration, test fixture, migration, or runtime environment change
was added.

==================================================
FINAL VERDICT
==================================================

Marketaux is technically suitable as the active development Financial News
candidate and fits the existing provider-neutral model better than the current
unusable Alpha Vantage Free runtime. It is not yet legally or operationally
unambiguous for continuous personal polling and durable storage because the
official Terms combine a personal/non-commercial license with a broad automated-
access prohibition and say nothing clear about retention, LLM, or ML rights.
Proceed only as a limited, single-provider development experiment after written
clarification; retain Alpha Vantage disabled for future Premium evaluation.
