# Story 0103B - Repository Analysis

## Repository State

- The repository is currently on the dedicated branch
  `investigation/0103b-marketaux-provider`.
- The baseline `HEAD` and local `origin/main` are
  `e9d7b7c6b60449d33ef302646dc41e85ae99bc3f`.
- The worktree contains pre-existing Story 0103 implementation changes and
  unrelated untracked reports. They are outside this documentation change and
  must remain untouched.
- `git fetch origin` was attempted earlier but failed because the configured SSH
  security-key agent refused signing. No remote reference was changed.

## Existing Provider-Neutral Boundary

`news-service` already contains:

- `FinancialNewsSourcePort` for financial-news acquisition and availability;
- `FinancialNewsItem` for normalized facts;
- `NewsCatalogService` for additive synchronization and persistence;
- `FinancialNewsItemEntity` for PostgreSQL storage;
- provider-neutral public and internal news APIs;
- availability states including `AVAILABLE`, `INCOMPLETE` and `UNAVAILABLE`.

Story 0103 adds the Alpha Vantage adapter under the infrastructure boundary and
keeps it disabled by default. The adapter owns transport, credentials, response
parsing, provider error classification and normalization. This boundary is
reusable for Marketaux.

## Marketaux Fit

The investigation establishes the following mapping without domain changes:

| Trading OS fact | Marketaux fact | Decision |
|---|---|---|
| `sourceName` | provider constant | `marketaux` |
| `sourceItemId` | `uuid` | direct stable identity |
| `title` | `title` | direct |
| `summary` | `description`, otherwise `snippet` | bounded excerpt |
| `canonicalUrl` | `url` | direct |
| `publishedAt` | `published_at` | UTC instant |
| `publisher` | `source` domain | direct publisher provenance |
| `impact` | no equivalent | `UNKNOWN` |
| `sourceUpdatedAt` | not documented | absent |
| `fetchedAt` | local acquisition time | acquisition timestamp |
| `normalizationVersion` | adapter constant | `marketaux-v1` |

Entity-to-currency and entity-to-market mapping require explicit repository
rules. Symbols must not be guessed, and Marketaux sentiment must not be treated
as Trading OS impact.

## Provider Selection Risk

`NewsCatalogService` currently resolves one financial-news source through
`getIfAvailable()`. Registering Alpha Vantage and Marketaux as simultaneously
active beans would make provider resolution ambiguous or create unspecified
selection behavior.

Before a Marketaux adapter is enabled, one of the following must be approved:

1. explicit configuration selecting exactly one active financial-news provider;
2. a deterministic provider-selection policy; or
3. a separate aggregation design with defined identity, freshness and failure
   semantics.

The first option is the smallest coherent change. It is a decision gate, not an
implementation instruction for this documentation task.

## Legal And Operational Risk

Marketaux advertises a Free plan with 100 requests per day and up to three
articles per request. Its API uses an `api_token` query parameter, which creates
URL logging risk and requires redaction. The Terms of Use contain a broad
non-human-access restriction that is ambiguous relative to the API product.

Written provider clarification is therefore required before automated polling,
normalized durable storage or future AI/ML processing is treated as permitted.
Until then, the provider must remain unimplemented or disabled.

## Architectural Conclusion

The existing provider-neutral boundary is sufficient for a first Marketaux
adapter. No changes to `FinancialNewsItem`, public news APIs, persistence schema
or Market Intelligence contracts are justified by the investigation.

The next implementation, if approved, should be a bounded infrastructure-only
adapter plus explicit single-provider selection. Alpha Vantage should remain in
the repository as a disabled future provider and must not be silently replaced.

## Validation Gaps

- Provider authorization and retention rights are not yet documented by
  Marketaux.
- Free-tier historical retention is unclear.
- Exact Marketaux-to-Trading-OS market identity mapping is unresolved.
- No live Marketaux request should be executed before the authorization gate is
  satisfied.
- DevLog context retrieval was unavailable because the configured LLM provider
  quota was exhausted; repository sources and the investigation report are the
  current evidence base.

## Approval Boundary

This Repository Analysis records the implementation boundary and open decisions.
It does not approve adapter implementation, runtime activation, provider
selection, or data-use policy. Those decisions require explicit human approval.
