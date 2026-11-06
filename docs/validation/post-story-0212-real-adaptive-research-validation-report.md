# Post-Story 0212 - Real Adaptive Research Validation Report

## Execution Metadata

| Field | Value |
|---|---|
| Date | 2026-11-06 |
| Branch | `main` |
| Commit under test | `ec84cf7384b1b598db369c43c2fbd58ae07c982c` (`main`, merge of PR #257) |
| Scope | Post-merge real-provider validation of Story 0212 and current architecture assessment |
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

The independent review blocker and the two approved lifecycle corrections were
addressed, reviewed and merged through PR #257. The `main` baseline was clean
before this documentation update. This report remains a validation artifact, not
an authorization to trade.

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

## Architecture Assessment Update

### Baseline and pipeline

The merged implementation follows this actual sequence:

```text
Market Data contributors
    -> IntelligenceContextAssembler
    -> CapabilityAnalysisCoordinator / deterministic capabilities
    -> MarketThesisComposer and persisted Market Thesis
    -> ResearchApplicationService
    -> initial AI interpretation (optional)
    -> Java allowlisted deterministic follow-up (optional)
    -> final AI synthesis (optional, terminal)
    -> persisted ResearchExecution / ResearchReport
```

The Java application owns market scope, context assembly, deterministic execution,
AI authorization, budgets, evidence validation, persistence and REST contracts.
The Python service owns bounded provider execution and structured interpretation.
MCP maps only to Gateway contracts. Risk remains an independent deterministic
authorization engine and was not involved in the research execution.

### Current validation result

The current baseline was rechecked from `main` at the merge commit above.

| Check | Result |
|---|---|
| Market Intelligence full Maven suite | PASS |
| AI Engine project virtualenv suite | PASS, 18 tests |
| MCP Adapter project virtualenv suite | PASS, 30 tests |
| Market Data full Maven suite | PASS; expected malformed-input diagnostics were logged |
| Research evaluation fixture test | PASS |
| Docker Compose runtime | PASS; all declared services running |
| AI Engine health | PASS; enabled, shadow mode, `openai_chatgpt` |
| Unauthenticated Gateway market/research reads | PASS; `401` |
| Authenticated REST/MCP replay of historical research IDs | NOT EXECUTED; no authorized user JWT available |

The prior live campaign remains the strongest runtime evidence. It proved real
initial AI interpretation over authorized evidence, but did not prove a real
follow-up plus final synthesis cycle. The provider requested
`ai-market-reasoning:unknown`, which Java rejected by the allowlist. Tradeability
also remained unavailable. The historical IDs requested for inspection were not
replayed through REST because doing so without a user JWT would violate the
Gateway boundary.

### Exact blockers and classification

| Observation | Classification | Consequence |
|---|---|---|
| No user JWT available for the local process | Environmental limitation / authorization restriction | No authenticated REST or MCP replay |
| `LIVE_CURRENT` tradeability is unsupported by the deterministic capability | Legitimate domain constraint plus missing provider capability | Thesis preserves `TRADEABILITY_UNAVAILABLE`; no candidate execution suitability |
| Real provider follow-up ID/version was not allowlisted | Legitimate authorization restriction | Follow-up and final synthesis are correctly not executed |
| AI engine receives no canonical context when no thesis exists | Implementation coupling | AI is marked `NOT_REQUESTED` when the persisted thesis artifact is absent |
| AI test provider used synthetic evidence in Test A | Validation limitation | Proves contract/provider integration, not live-market usefulness |

### Does AI require a complete deterministic Market Thesis?

No, not in the strong sense. The implementation requires a persisted
`MarketThesis` artifact before AI (`ResearchApplicationService.java`), but it does
not require `MarketThesisState.CANDIDATE`. `WATCH` and `NO_TRADE` theses can still
be sent to the AI, with missingness and contradictions preserved.

That artifact requirement is justified because it supplies canonical market
identity, source cutoff, evidence IDs, freshness, provenance and deterministic
missingness. Requiring a complete tradeability result, Level II liquidity or a
`CANDIDATE` state before advisory interpretation would conflate analysis with
execution suitability and suppress useful explanations of uncertainty.

The correct separation is:

```text
Research context: validated identity, cutoff, provenance and a bounded thesis artifact
AI interpretation: advisory explanation of available, missing and contradictory evidence
Candidate eligibility: deterministic Market Thesis policy
Trade authorization: independent TradePlan and Risk authorities
Execution: human authorization, Trading Core and Broker Service
```

### Deterministic value assessment

The following must remain mandatory for any research context:

- canonical market and instrument identity;
- evidence market consistency;
- source cutoff and temporal validity;
- freshness and provenance validation;
- evidence-reference validation;
- bounded capability authorization and budgets;
- explicit missing, stale and contradictory evidence;
- deterministic Risk evaluation for any concrete TradePlan;
- human authorization before execution.

The following should be optional research inputs unless a specific policy makes
them required for a downstream candidate or execution decision:

- order-book and Level II evidence;
- advanced liquidity measurements;
- volume, which is currently not implemented;
- news context when no blocking-news policy applies;
- tradeability evidence for informational research;
- `CANDIDATE` thesis state;
- additional deterministic follow-up capabilities.

Optional means explicitly represented as missing or degraded, never silently
treated as support or suitability.

### Architecture comparison

| Scenario | Deterministic-first | Hybrid AI-first under current safeguards |
|---|---|---|
| Complete evidence | Reliable report and AI interpretation | Same safety, more flexible interpretation |
| Missing tradeability | Research is possible only if a thesis artifact exists; execution suitability remains unavailable | Advisory hypotheses can explain the gap; no execution conclusion |
| Missing order book | Thesis records missingness; candidate policy may block | AI can reason over other evidence and state the limitation |
| Stale evidence | Deterministic gates reject or degrade the affected path | Same cutoff/freshness gate; AI may explain stale evidence, not refresh it |
| Contradictory signals | Thesis preserves contradiction and may become `WATCH`/`NO_TRADE` | AI can compare hypotheses, but cannot erase contradiction or change canonical state |
| Insufficient history | Required context can fail the deterministic branch | AI can summarize the insufficiency only if a bounded thesis/context artifact exists |
| Volatile market | Deterministic freshness and tradeability policy remain authoritative | AI can describe uncertainty; it cannot infer executable suitability |
| Provider limitation | Explicit unavailable/partial result | Same result with potentially useful advisory interpretation |

The implementation is therefore already a constrained hybrid in the research
layer, even though deterministic analysis is performed first. A broad rewrite to
let Python independently acquire or normalize market data would reduce evidence
integrity and violate ADR-089.

### AI capability assessment

| Capability | Status | Evidence |
|---|---|---|
| Structured interpretation of supplied thesis evidence | Implemented and validated | Python contracts, provider tests, live initial call |
| Evidence-reference grounding | Implemented and validated | Java/Python validation and evaluation fixtures |
| Missing/stale/contradictory evidence handling | Implemented and validated in fixtures; live usefulness unvalidated | Story 0211/0212 evaluator |
| One authorized deterministic follow-up | Implemented and validated offline; live execution unvalidated | Java allowlist and fixture coverage |
| Final synthesis over returned evidence | Implemented and validated offline; live execution unvalidated | Story 0212 tests |
| Hypothesis reconciliation | Implemented and validated offline | Reconciliation effects and evidence checks |
| Multi-timeframe interpretation | Partially implemented | Deterministic Trend Context is supplied; no live qualitative assessment |
| Support/resistance hypotheses | Partially implemented | Structural-level deterministic capability exists; AI usefulness not measured live |
| Order-book/liquidity interpretation | Blocked or partial | ADR-079 policy and current evidence availability |
| News/macro interpretation | Blocked by available trusted context | No validated live research campaign |
| Calibrated probabilities or predictive performance | Missing by design | Not claimed by current contract or rubric |
| Conditional entry/stop/target zones | Missing from this research workflow | TradePlan/Risk boundaries remain separate |

### Independent review

An independent repository review was performed against the implementation and
ADRs 076, 079, 088 and 089. It found no reason to remove deterministic identity,
cutoff, provenance, evidence validation, Java authorization, budgets or Risk
separation. It confirmed that a persisted thesis artifact is a valid context
boundary, while a `CANDIDATE` thesis or complete tradeability is not a necessary
precondition for advisory AI interpretation.

The reviewer identified unresolved policy questions rather than an implementation
defect requiring an architectural rewrite:

- whether AI should run for a `NO_TRADE` thesis caused by a blocking contradiction;
- what defines a research-ready thesis versus a candidate-ready thesis;
- when Level II liquidity is required by candidate policy;
- what measurable threshold proves AI value over the deterministic baseline;
- whether provider fallback is acceptable when provider/model provenance changes.

### Recommendation

**Option 3: controlled experiment, within a constrained hybrid research model.**

Retain deterministic-first authority and do not implement a broad AI-first
migration yet. The current architecture is safe and reusable, but the available
runtime evidence is insufficient to claim that adaptive AI adds measurable value.
The next experiment should compare the same inputs through:

1. deterministic report only;
2. deterministic report plus initial AI interpretation;
3. deterministic report plus one authorized follow-up and final synthesis.

Measure evidence coverage, unsupported claims, contradiction preservation,
missingness, uncertainty reduction, human comprehension, latency, call count and
provider usage. Do not infer profitability or calibrated prediction from this
experiment.

### Incremental roadmap

1. Reconcile Story 0209 status metadata with the delivered implementation.
2. Make report semantics explicit for thesis state, research quality and execution
   suitability as separate fields or documented projections.
3. Run an authenticated bounded campaign with current market evidence and an
   approved follow-up capability/version.
4. Add controlled comparison telemetry for deterministic, initial-AI and adaptive
   outputs over identical fixtures and replayable real evidence.
5. Define policy profiles for research-ready, candidate-ready and
   liquidity-qualified contexts without weakening ADR-079.
6. Keep TradePlan, Risk, human validation and broker execution outside AI authority.

No architecture migration, ADR creation, Story creation or code change is
authorized by this assessment.

## Human Decision

Option 3, the controlled comparison within the constrained hybrid research model,
was approved by the human owner on 2026-11-06. This approval does not authorize
an architecture migration, an ADR, a new Story, a provider policy change or any
trade execution.

## Controlled Experiment Execution

The existing reproducible synthetic experiment was executed without application
code changes:

- 7 scenarios: trend confirmation, contradictory signals, missing evidence,
  stale data, comparative markets, adaptive investigation and no-follow-up;
- deterministic baseline and deterministic-versus-assisted comparison checks;
- adaptive follow-up governance and final-synthesis checks;
- 14 tests, 0 failures, 0 errors and 0 skipped;
- Maven result: `BUILD SUCCESS`.

This validates contract behavior and safety boundaries only. It does not measure
live trader usefulness or predictive performance. The live three-stage comparison
remains blocked by the missing authorized user JWT, unavailable current
tradeability evidence and the real provider follow-up request rejected by the
Java capability allowlist.

Result: **architecturally safe, analytically inconclusive**. No AI-first
migration is justified by this run.
