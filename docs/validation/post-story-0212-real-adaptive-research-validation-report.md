# Post-Story 0212 - Real Adaptive Research Validation Report

## Execution Metadata

| Field | Value |
|---|---|
| Date | 2026-11-06 |
| Branch | `main` |
| Commit under test | `983497204fe0863c5bf59c334371210a1965967e` plus current uncommitted worktree |
| Scope | Post-merge real-provider validation of Story 0212 |
| Provider target | `openai_chatgpt` through the existing ChatGPT OAuth adapter |
| Production changes | None |
| Trades, broker actions or strategy changes | None |

## Baseline

The merged baseline contains the Story 0212 final-synthesis workflow and its
provider-neutral contracts. The repository instructions, `TOOLS.md`, ADR-088,
ADR-089 and the implementation reports for Stories 0210, 0211 and 0212 were
read before validation.

The architecture remains Java-controlled: Market Intelligence owns research
scope, deterministic evidence, authorization, budgets, persistence and public
contracts. The AI Engine receives only authorized context and remains advisory.
MCP remains a Gateway-backed adapter and does not call the AI Engine directly.

## Independent Review and Stabilization Corrections

An independent Engineering Review Agent inspected the complete uncommitted
tracked and untracked diff, the runtime configuration, relevant Market Data and
Market Intelligence code, ADR-076, ADR-079, ADR-088 and ADR-089, and the tests.

The review identified one integration blocker and two lifecycle risks:

- The documented environment bootstrap did not generate the required AI Engine
  service JWT secret, so Compose could fail before startup even when AI was
  disabled.
- `LIVE_CURRENT` used a null cutoff for deterministic analysis but request-time
  cutoffs for initial AI interpretation, follow-up analysis and final synthesis.
- A restart during `RUNNING` could lose completed market progress because the
  report was persisted only at terminal completion.

The corrections are now implemented:

- `scripts/bootstrap-local-env.sh` generates and validates
  `MARKET_INTELLIGENCE_AI_ENGINE_SERVICE_JWT_SECRET` alongside the other local
  service secrets.
- All `LIVE_CURRENT` deterministic and AI research requests use live acquisition
  semantics without a request-time evaluation cutoff. Regression assertions cover
  deterministic, initial-AI and final-synthesis requests.
- Research persists a checkpoint after each market result. Stale `RUNNING`
  executions retain their last report, transition back to `REQUESTED`, and are
  redispatched after restart instead of being immediately discarded as expired.
  The checkpoint/requeue lifecycle has regression coverage.

No security boundary, Tradeability policy, Market Thesis invariant, broker
boundary or execution authority was changed.

## Environment Preflight

### Runtime

The local Docker stack was already running for the declared services:

- Gateway
- Market Data
- Market Intelligence
- Trading Core
- Broker Service
- News Service
- Eureka
- PostgreSQL dependencies

The initial Docker Compose service list did **not** include `ai-engine`.

The default Market Intelligence AI URL remains `http://ai-engine:8086`, but no
reachable AI Engine was present at `127.0.0.1:8086`.

After this preflight, the working tree was updated with a minimal operational
restoration: `ai-engine` is now declared in `docker-compose.yml`, its `/data`
directory is backed by `ai_engine_data`, and the Market Intelligence service-JWT
relationship is explicitly wired. Market Intelligence is also explicitly
enabled for the local validation runtime.

### Provider configuration

The host process did not initially provide the required AI Engine variables,
including:

- `AI_ENGINE_ENABLED`
- `AI_ENGINE_PROVIDER`
- `AI_ENGINE_MODEL`
- `AI_ENGINE_CHATGPT_OAUTH_ENABLED`
- `AI_ENGINE_CHATGPT_CREDENTIAL_HANDLE`
- `AI_ENGINE_CREDENTIAL_ENCRYPTION_KEY`

The local `.env` contains JWT configuration but no ChatGPT provider
configuration. No configured OAuth credential database was present at the
default `/data` paths. No credential values were read or printed.

The existing provider implementation was inspected and confirmed to fail closed
without explicit ChatGPT OAuth enablement, credential handle, encrypted
credential storage and the required authorization scopes.

Those runtime prerequisites were subsequently restored locally. The OAuth
callback returned `AUTHORIZED` for provider `chatgpt`; no token value is
recorded here.

### Gateway and REST boundary

Unauthenticated requests to the public Gateway returned `401` for:

- `GET /api/v1/markets`
- `GET /api/v1/intelligence/research/integrity`

This confirms that authenticated validation must use the supported user JWT
path. No internal Market Intelligence endpoint was used to bypass Gateway
authorization.

## Offline Validation

| Check | Result |
|---|---|
| `cd market-intelligence && mvn test -q` | PASS |
| `cd ai-engine && .venv/bin/pytest -q` | PASS, 18 tests |
| `cd mcp-adapter && .venv/bin/pytest -q` | PASS, 30 tests |
| Docker Compose service configuration | PASS |
| `bash -n scripts/bootstrap-local-env.sh` | PASS |
| Unauthenticated Gateway boundary | PASS, requests rejected with `401` |
| Story 0211 deterministic evaluator and Story 0212 final-synthesis tests | Covered by Market Intelligence suite; PASS |

The Market Intelligence test run emitted existing test-environment warnings
about service discovery, JWT configuration and expected concurrency constraint
races. Maven completed successfully. These warnings are not treated as live

## Live Scenarios

The bounded campaign reached the real provider for Test A and reached the
authenticated research REST workflow for Test B. Test B did not produce a
complete adaptive cycle because deterministic runtime evidence was unavailable
or stale for the selected markets.

| Scenario | Result | Reason |
|---|---|---|
| Test A - initial interpretation | PASS | Real `openai_chatgpt` call completed against synthetic fixture evidence |
| Test B - complete adaptive cycle | PARTIAL | Initial request persisted a partial report; the post-fix retest completed deterministic branches but remained advisory because Tradeability was unavailable |
| Test C - no-follow-up behavior | NOT EXECUTED live | Covered by offline contracts/fixtures only |
| Test D - failure preservation | PASS offline only | Covered by existing deterministic tests; no live disruption attempted |
| REST create/status/report | PASS PARTIAL | Ephemeral user JWT; create, status and report retrieval succeeded |
| MCP retrieval | NOT EXECUTED | `TRADING_OS_JWT` was unavailable; no direct internal calls were used |

Test B produced persisted `ResearchExecution` records, including
`067983c9-0012-4bd6-a1b7-76a6c2618ae4` with status `PARTIALLY_COMPLETED`.
The report is retained as a partial-result validation artifact, not as proof of
a successful adaptive cycle. No live market conclusion was promoted from it.

After the partial attempt, the existing Market Data capture workflow was run
for the BTC/USD and ETH/USD Kraken market rows. The existing Research
Lifecycle workflow was then run with a temporary one-minute cadence for a
bounded runtime check. At the `2026-11-06T04:08:00Z` cutoff, both markets
completed deterministic analysis, produced active `PRICE_TREND_SHORT`
observations, and had current `NO_TRADE` Market Theses valid through
`2026-11-06T04:30:00Z`. This restores the deterministic prerequisites without
changing freshness policy, fabricating evidence or modifying trading logic.

Using the authenticated Web session, a new bounded Test B request was submitted
as `fcae6037-ef95-4b3d-8b5f-08d638cf00b0`. The request was accepted and the
report was persisted, but it remained `PARTIALLY_COMPLETED`: both markets had
current OHLC and Trend Context evidence, while both theses still contained the
required `TRADEABILITY_UNAVAILABLE` evidence. The BTC/USD AI interpretation
therefore also failed; ETH/USD remained deterministic-only. Authenticated
ticker and order-book subscriptions for both markets were accepted, and fresh
price observations were persisted. A direct public `ACTIVE` analysis request
was rejected with `403`, so no authorization boundary was bypassed.

Authenticated MCP retrieval remains unexecuted because the browser session
token is not available to the local MCP process without extracting it from the
browser session.

### Post-fix Test B retest

The live-research acquisition path was corrected before the retest. For
`LIVE_CURRENT` evidence, `ResearchApplicationService` no longer passes the
request execution instant as an evaluation cutoff. That instant could be later
than the snapshot captured by Market Data and incorrectly caused current
snapshots to be rejected as future evidence. Regression coverage was added for
the null cutoff and the `LIVE_CURRENT` policy.

Market Intelligence was rebuilt and restarted without recreating its
dependencies. Authenticated Test B was submitted as
`9be1ea77-cb8d-40b0-ac15-69ded0d82896` and completed in approximately two
seconds. Both markets now reached deterministic analysis `COMPLETE` and
produced versioned Market Thesis records:

- BTC/USD: analysis `2aa80a73-a34f-4247-9d3e-05ef76ae6c10`, thesis version `222`.
- ETH/USD: analysis `ef2412d1-1425-4e2f-89ee-12aa054b68d0`, thesis version `2`.

The retest confirms that live market identity and snapshot evidence are
acquired successfully. It does not prove a complete adaptive AI cycle: BTC/USD
reported `aiStatus=FAILED`, ETH/USD reported `aiStatus=DISABLED`, and both
theses still contain required `TRADEABILITY_UNAVAILABLE` evidence. No follow-up
capability was requested or executed, and no trade authorization was produced.

### Post-review AI activation retest

After the independent-review corrections, Market Intelligence was rebuilt and
the Gateway was restarted to refresh its Eureka registration. The first request
attempt was rejected because the Gateway still held the old container hostname;
that infrastructure condition was corrected without changing authorization.

Authenticated Test B was then submitted as
`4ecae52d-f080-45a5-a354-70c4ad5ecfa3` and completed with status `COMPLETED`.
The provider was `openai_chatgpt` using the previously validated
`gpt-5.6-luna` configuration:

- BTC/USD deterministic analysis: `COMPLETE`.
- BTC/USD initial AI interpretation: `COMPLETED`.
- BTC/USD follow-up: requested by the provider but rejected by Java because
  `ai-market-reasoning` version `unknown` is not on the deterministic allowlist.
- BTC/USD final synthesis: not executed because the follow-up was rejected.
- ETH/USD deterministic analysis: `COMPLETE`.
- ETH/USD AI interpretation: `DISABLED` after the bounded one-initial-AI-request
  budget was consumed by BTC/USD.
- Initial AI evidence references were accepted against the persisted evidence
  set, and the AI output preserved `NO_TRADE` and returned `INSUFFICIENT_DATA`.

The exact earlier AI failure was an AI Engine HTTP `422` caused by the
Java-to-Python authorized-context wire shape. The explicit wire projection now
serializes the context fingerprint as a string and the live provider call
completes. The remaining follow-up rejection is an intentional deterministic
capability-allowlist boundary, not a provider failure or authorization bypass.

Test A produced the following sanitized live metadata:

- AI execution ID: `8b387288-d2c9-414d-b7b6-ed7b567869b1`
- Provider: `openai_chatgpt`
- Model: `gpt-5.6-luna`
- Terminal status: `COMPLETED`
- Provider call count: one
- Observed elapsed time: approximately 11 seconds
- Dataset: synthetic BTC/USD fixture, not current market evidence
- Evidence reference: `ebc950e4-f15f-4bdd-a464-354c7de328e4`
- Result: structured output accepted; neutral `WATCH` interpretation; no
  unsupported directional claim

## Reconciliation Assessment

The offline Story 0211/0212 fixtures verify the deterministic contract for:

- valid evidence references;
- follow-up authorization and execution records;
- final synthesis over follow-up evidence;
- evidence-backed material effects;
- unsupported/fabricated reference rejection;
- final-synthesis failure preservation;
- no-follow-up behavior;
- `CONFIRMED`, `STRENGTHENED`, `WEAKENED`, `INVALIDATED`, `UNCHANGED` and
  `INCONCLUSIVE` states where fixtures provide them.

These results prove contract behavior only. They do not prove that a real
ChatGPT response requests a follow-up, consumes returned follow-up evidence or
adds useful information in a deployed runtime.

## Findings

### Verified functionality

- Story 0212 is present in the merged `main` commit.
- Java final-synthesis orchestration, bounded budgets and evidence validation
  are covered by passing tests.
- Python provider contracts, terminal synthesis validation and evidence checks
  are covered by passing tests.
- MCP research lifecycle mapping is covered by 30 passing adapter tests.
- Gateway authorization rejects unauthenticated research-related access.
- No trading, risk, broker or execution boundary was exercised or modified.

### Environmental blockers

- Existing local `market-data` state contained two legacy CFD rows that caused
  Flyway `V24` to fail. The migration was corrected to preserve an all-null
  legacy CFD contract while still rejecting partial metadata; the focused test
  and full Market Data suite pass.
- Market Intelligence had a circular placeholder configuration for the AI Engine
  service-JWT and timeout properties. The circular properties were removed; the
  service now starts with the AI adapter enabled.
- The authenticated report endpoint initially exposed Jackson node metadata
  instead of the persisted report object. The response projection now converts
  the report tree to ordinary JSON-compatible values.
- Legacy consolidated analysis payloads with null collections are now restored
  as empty collections instead of breaking lifecycle deserialization.
- The initial selected BTC/USD branch failed deterministic analysis with
  stale/missing runtime evidence, and the initial ETH/USD branch had no market
  thesis. The existing capture and lifecycle workflows subsequently restored
  current OHLC, Trend Context and Market Thesis records for BTC/USD and
  ETH/USD. A later live Test B retest initially exposed a separate timing defect
  in the live evidence cutoff; after the minimal fix, both deterministic market
  branches completed. The required current tradeability artifact is still
  unavailable.
- Existing malformed historical `AnalysisExecution` payloads no longer caused
  deserialization errors after the null-collection compatibility fix; no
  destructive data cleanup was performed.
- MCP runtime retrieval was not executed because Test B did not produce a
  successful adaptive execution to retrieve.

### Unexecuted validations

- Real adaptive follow-up execution remains unexecuted: the provider requested
  `ai-market-reasoning:unknown`, which Java rejected because it is not allowlisted.
- Real final synthesis consuming deterministic follow-up evidence.
- Authenticated MCP retrieval of the same execution.
- Provider latency and usage accounting.
- Human qualitative assessment of live research usefulness.

### Review status

The independent review blocker and the two approved lifecycle corrections are
addressed in the current worktree. The corrected worktree still requires human
diff review and a future commit decision; no Git integration was performed.

## Conclusions

1. **Does ChatGPT perform the initial interpretation?** Yes. Test A completed
   through the real `openai_chatgpt` provider with accepted structured output and
   valid evidence references over synthetic fixture data. The post-review live
   retest also completed the initial interpretation for BTC/USD.
2. **Does the adaptive cycle execute end to end?** No. The post-review live
   request completed deterministic analysis and initial AI interpretation, but
   the provider's follow-up request was rejected by the Java allowlist and final
   synthesis was therefore not reached. Tradeability remained unavailable and
   no trade authorization was produced.
3. **Does final synthesis consume new deterministic evidence?** The offline
   contract test proves the intended path; real-provider behavior is unverified.
4. **Are hypothesis revisions correctly grounded?** Yes for deterministic
   fixtures; not verified with a live provider response.
5. **Are reports persisted and retrievable?** Yes. The partial Test B result was
   persisted and the REST report endpoint returned the full report object,
   including `markets`, `diagnostics` and adaptive capability fields.
6. **Is MCP retrieval validated?** No. The adapter tests pass, but no
   authenticated runtime MCP call was made.
7. **Does final synthesis provide demonstrable value over initial interpretation?**
   No live evidence is available to answer this.
8. **Is the Research Agent ready for the next functional development stage?**
   The implementation, live evidence acquisition and initial real-provider path
   are operational. The deployed adaptive workflow is not ready for sign-off
   until the requested follow-up capability has an approved deterministic
   implementation/allowlist entry and tradeability remains governed.

## Required Next Action

Before sign-off, expose the existing approved market execution-context/active
analysis workflow through the authorized user interface, or provide the
corresponding approved user capability to invoke it. The current Web session
can submit research and market subscriptions, but direct `ACTIVE` analysis is
forbidden. Once tradeability evidence is present, rerun one bounded adaptive
attempt and one authenticated MCP retrieval without exposing or committing
credentials.
