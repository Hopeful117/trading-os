# Repository Analysis - Story 0189

## Baseline

```text
REMOTE_MAIN: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
LOCAL_BASE: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
LOCAL_HEAD: 18f80e7b48c9bcd3919bc1d0d12bc16321d5867c
CURRENT_BRANCH: story/0189-adr-082-phase-8-cash-cfd-compatibility-retry
WORKTREE_STATE: pre-existing untracked Story 0185 and 0187 directories preserved
NEXT_AVAILABLE_STORY: 0189
STORY_0188_COMMITS: 9f86b779, b080d418
```

DevLog applicability was checked for `trading-os`. The request completed with
low-confidence, truncated context and no relevant ADR-082/ADR-083/0188 evidence.
The repository is authoritative for current physical behavior. Obsidian was not
consulted because the repository ADRs and tests are the relevant sources.

## Story 0188 Reconstruction

Story 0188 added `MarginPolicy`, `MarginPolicyPersistence`, V49 policy and
assignment tables, `MarginResolutionContext`, `PaperMarginExposureSnapshot`,
`PaperMarginCalculator`, and `PaperRequiredMarginClient`. It extended
`RequiredMarginPort` with financial-account/policy context and evidence, routed
PAPER through the local client, preserved LIVE through
`BrokerRequiredMarginClient`, and persisted the margin context in T0 evidence
for T1 lookup. It also updated T0/T1 request construction and focused tests.

The actual Story 0188 production diff contains no settlement compatibility change
and no dedicated application-level T0/T1 behavioral test. The tracked Story 0188
artifact set does not contain `code-review.md`.

## Phase 8 Readiness Matrix

| Capability | Implemented | Direct test evidence | Authority | Fail closed | Blocker |
|---|---|---|---|---|---|
| CFD product classification | YES | Existing CFD classification tests | Market Data contract | YES | NO |
| Canonical InstrumentFinancialContract | YES | Contract/calculator tests | Market Data and persisted Trade | YES | NO |
| Signed quantity semantics | YES | CFD and exposure tests | Trading Core financial model | YES | NO |
| Contract multiplier | YES | CFD projection tests | Financial contract | YES | NO |
| Base/quote/reporting currencies | YES | CFD/normalizer tests | Contract and account config | YES | NO |
| Persisted CFD contract projection | YES | Persistence/migration tests | Trade snapshot | YES | NO |
| PAPER CFD entry accounting | YES | Paper CFD settlement tests | PaperCfdSettlementService | YES | NO |
| PAPER CFD signed exposure | YES | Settlement and exposure tests | Local Trade state | YES | NO |
| PAPER CFD mark-to-market | YES | Valuation/settlement tests | Market Data plus local state | YES | NO |
| PAPER CFD equity | YES | Paper CFD lifecycle tests | Local cash plus MTM | YES | NO |
| PAPER CFD full exit | YES | Exit/persistence tests | PaperCfdSettlementService | YES | NO |
| PAPER CFD realized PnL | YES | Exit tests | Local settlement boundary | YES | NO |
| Exactly-once settlement | YES | Provenance persistence tests | Fill provenance store | YES | NO |
| Restart durability | PARTIAL | Existing CFD persistence only | PostgreSQL persistence | YES | YES |
| MarginPolicy persistence | PARTIAL | No direct policy persistence test | V49 plus JPA | PARTIAL | YES |
| MarginPolicy assignment | PARTIAL | No direct assignment integration test | Financial Account | PARTIAL | YES |
| Policy immutability/versioning | PARTIAL | Java duplicate/version checks only | JPA `@Immutable`; no DB trigger | NO | YES |
| Policy provenance | PARTIAL | Calculator evidence unit coverage | Policy source fields | PARTIAL | YES |
| PAPER required-margin calculation | YES | PaperMarginCalculatorTest | Local MarginPolicy | YES | NO |
| Existing-exposure reconstruction | PARTIAL | Snapshot unit tests only | Open Trade read model | YES | YES |
| Netting semantics | YES | Opening/reduction/reversal unit cases | Local calculator | YES | NO |
| Unsupported hedging fail-closed | YES | Exposure snapshot test | Local exposure boundary | YES | NO |
| Margin currency | PARTIAL | Normalizer/calculator tests | Quote then reporting currency | PARTIAL | YES |
| Margin FX conversion | YES | Directed/reverse/stale/rate tests | Market Data valuation | YES | NO |
| T0 required-margin evaluation | PARTIAL | Existing T0 tests mock margin | Local resolver intended | YES | YES |
| T1 independent required-margin re-evaluation | NO | No authoritative-input-change test | T1 must reacquire facts | NO | YES |
| LIVE provider required margin | YES | Broker margin client tests | Provider through Broker Service | YES | NO |
| LIVE account authority | YES | Phase 6 regression tests | Provider snapshot | YES | NO |
| LIVE position authority | YES | Phase 6 reconciliation tests | Provider snapshot | YES | NO |
| LIVE reconciliation | YES | LiveCfdReconciliationServiceTest | Broker Service facts | YES | NO |
| CFD proposed-order notional risk | YES | CFD projection tests | Trading Core contract | YES | NO |
| CFD stop-loss risk | YES | CFD projection tests | Trading Core contract | YES | NO |
| CFD equity risk | YES | Existing risk facts tests | Mode-aware facts | YES | NO |
| Human authorization | YES | Validate/create and execution tests | Execution boundary | YES | NO |
| Execution safety | YES | Safety/configuration regressions | Explicit execution gates | YES | NO |
| Product-aware settlement routing | YES | Execution finalization tests | PaperCfdSettlementService | YES | NO |
| Provider quantity normalization | YES | cTrader adapter tests | Broker adapter boundary | YES | NO |
| CASH + CFD compatibility | NO | Compatibility test remains rejected | SettlementCompatibilityPolicy | YES | YES by design |

## ADR-083 Implementation Fidelity

| Invariant | Evidence | Result |
|---|---|---|
| `PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY` | `PaperRequiredMarginClient` and local calculator | YES, subject to T1 gaps |
| `LIVE_MARGIN_AUTHORITY = PROVIDER` | `BrokerRequiredMarginClient` non-PAPER path | YES |
| `PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY = NO` | PAPER branch bypasses Feign preview | YES |
| `RISK_DOMAIN_PROVIDER_NEUTRAL = YES` | No Risk Domain production change | YES |
| Provider-neutral policy | `MarginPolicy` contains no provider fields | YES |
| Unconfigured policy unavailable | `MISSING_POLICY` resolution | YES |
| Unsupported semantics unavailable | Constructor rejects V1 unsupported flags | YES, persistence representation incomplete |
| Account identity separation | Account relation and owner checks | YES in resolver; integration evidence missing |
| Immutable/versioned policy | `(id, version)` key and Java/JPA model | NO, database mutation is not prevented |
| Provenance | Policy source and margin evidence fields | PARTIAL, assignment provenance is not carried in context |
| Incremental margin | `PaperMarginCalculator` formula | YES in unit tests |
| Fresh T1 facts | T1 calls local resolver again | NO, T1 freshness/reference binding is not proven |
| Risk receives monetary fact only | Existing `RequiredMarginPort.Fact` boundary | YES |

## Hard-Gate Findings

### T1 does not prove independent fresh margin truth

`ExecutionTimeRiskRevalidationService` carries the T0 policy context to T1, which
is correct for policy-version reuse. However, `PaperRequiredMarginClient` then
calls `market.value(..., FreshnessRequirement.t0())` and passes the request price
to `PaperMarginCalculator` rather than the fresh valuation `sourcePrice`. The
implementation therefore lacks proof that a changed T1 price or FX fact changes
the calculated required margin. Existing T1 tests mock `RequiredMarginPort` and
do not exercise this boundary.

### Durable policy invariants are incomplete

V49 has positive-rate and currency-scale checks, but not the ADR-083 eight-decimal
rate limit, nonblank/provenance checks, effective-time ordering, or database
immutability enforcement. JPA `@Immutable` does not protect direct SQL or future
database writes. No PostgreSQL integration test exercises policy assignment,
historical lookup, or immutability.

### Exposure and lifecycle evidence is incomplete

The resolver is a read-side unit component, but Story 0188 does not prove the
full local resolver through a persisted CFD T0/T1 evaluation and settlement
lifecycle. The resolver is passed all account trades and validates every row;
the application integration must prove that closed historical CFD rows are not
mistaken for current exposure while open facts remain complete.

## Compatibility Decision

Keep the existing matrix unchanged:

```text
INVENTORY + SPOT = ALLOWED
CASH + PERP = existing behavior unchanged
CASH + CFD = REJECTED
INVENTORY + CFD = REJECTED
UNKNOWN + CFD = REJECTED
```
