# AI Research Quality Diagnosis - 2026-11-12

## Scope

This document diagnoses the existing read-only research execution
`baf5ecb8-99f7-4592-8010-6f72dacd7c92` for EUR/USD and GBP/USD. No research,
scan, export, broker operation, configuration change, or report rerun was
performed for this diagnosis.

The reviewed execution completed deterministic comparison but produced AI
interpretation for EUR/USD only. The conclusion below distinguishes observed
facts from code-based interpretation and unresolved runtime evidence.

## Executive Diagnosis

1. **EUR/USD AI interpretation completed.** The Market Intelligence service
   called the AI Engine once, received a successful result, and stored an AI
   execution identifier. The AI Engine health endpoint reported
   `enabled=true`, `shadowMode=true`, and provider `openai_chatgpt`.
2. **GBP/USD was not rejected by the AI Engine.** The report's
   `aiStatus=DISABLED` is produced because the research loop uses one global
   `initialAiUsed` flag. Once EUR/USD consumes that flag, GBP/USD is skipped.
   The skip is not represented by a market diagnostic or a persisted reason.
3. **This is a high-severity research completeness defect.** A two-market
   research request cannot currently provide per-market AI interpretation when
   both markets have a thesis. The behavior may be an intentional one-call AI
   budget, but the current contract makes that policy implicit and reports it
   as generic `DISABLED`.
4. **`STALE` is explained by persisted thesis freshness, not by the live
   quote path failing during the execution.** The report's thesis source
   cutoffs were approximately 14 days before execution. The deterministic
   analysis was requested with `LIVE_CURRENT` evidence acquisition and the
   Market Data service was producing current snapshots during the execution.
5. **The external provider invocation is not proven by the available evidence.**
   Java-to-Python AI Engine participation and successful polling are proven;
   the available logs do not prove that the configured external provider
   completed a model request. The result should therefore be classified as
   `PYTHON_AI_ENGINE_VERIFIED_MODEL_UNVERIFIED` unless persisted provider
   provenance is independently confirmed.

## Execution Evidence

| Item | Observed evidence | Classification |
|---|---|---|
| Execution | `baf5ecb8-99f7-4592-8010-6f72dacd7c92` completed for EUR/USD and GBP/USD | Fact |
| AI Engine call | One `POST /internal/v1/analyses`, followed by successful `GET` polling for execution `f35290d5-f8b6-4668-b25f-f6110e80a114` | Fact |
| EUR/USD AI status | `COMPLETED` in the persisted report | Fact |
| GBP/USD AI status | `DISABLED` in the persisted report | Fact |
| AI runtime | Health response: `UP`, `enabled=true`, `shadowMode=true`, `provider=openai_chatgpt` | Fact |
| Configured model | Runtime configuration identified `gpt-5.6-luna` | Fact from runtime configuration |
| External provider call | No safe, detailed provider log or persisted provider receipt was available | Unknown |
| Trading authority | Research remained advisory and did not authorize or execute a trade | Fact |

The existing validation report is the primary execution evidence:
`docs/validation/ai-research-first-e2e-validation-2026-11-12.md`.

## GBP/USD AI Status

### Code path

`ResearchApplicationService.buildReport` initializes the per-market status to
`DISABLED` at `ResearchApplicationService.java:322`. The first eligible market
enters the AI branch only when `!initialAiUsed` at lines 358-361, then sets
`initialAiUsed = true`. The loop does not reset this flag for the next market.

When the next market has a thesis, but `initialAiUsed` is already true, no
branch changes the status and no diagnostic is added. The result is therefore
persisted as generic `DISABLED` at lines 481-504.

The report-level `AI_INTERPRETATION_DISABLED` diagnostic is added only when
all market results are `DISABLED` or `NOT_REQUESTED` at lines 530-533. It does
not identify a partially skipped market.

### Root-cause classification

**Confirmed implementation limitation / defect:** AI eligibility is controlled
by one research-wide boolean, while the report is modeled per market.

**Not established:** Whether the intended product policy is one AI call per
research execution or one AI interpretation per eligible market. The current
implementation does not make that policy explicit in the report or expose a
budget reason.

### Minimum corrective direction

The implementation should make the policy explicit and observable:

- If the contract requires AI interpretation for every eligible market, use a
  per-market budget or a clearly bounded per-market invocation and add a
  regression test for a two-market request.
- If the contract intentionally permits one AI call per research, persist a
  distinct status such as `SKIPPED_BY_AI_BUDGET` with a diagnostic identifying
  the budget and the market that consumed it. Do not label the market
  `DISABLED` when the service is enabled and the market was eligible.

No implementation change was made because this diagnosis task is read-only and
the product policy requires human confirmation before changing the AI budget
contract.

## Freshness and `STALE`

`ResearchApplicationService.java:327-330` derives the report freshness solely
from `MarketThesis.validUntil`: it is `CURRENT` only when the execution clock
is before the thesis validity timestamp, otherwise `STALE`.

The reviewed report contained these source cutoffs:

| Market | Execution reference | Thesis source cutoff | Approximate age at execution |
|---|---|---|---:|
| EUR/USD | `2026-11-12T15:17:18.827853646Z` | `2026-10-29T04:37:46.812118Z` | 14 days, 10 hours, 39 minutes |
| GBP/USD | `2026-11-12T15:17:36.160049672Z` | `2026-10-28T20:33:24.676583Z` | 14 days, 18 hours, 44 minutes |

The exact thesis `validUntil` values were not included in the persisted report,
but the `STALE` classification is directly explained by the service's
`validUntil` comparison and the old thesis lineage. The source artifacts also
show that freshness windows are intentionally bounded:

- Trend context validity is based on the setup interval multiplied by the
  configured freshness multiplier (`TrendContextAnalysisCapability.java:98-101`).
- OHLC momentum validity is 900 seconds (`OhlcMomentumAnalysisCapability.java:67-70`).
- Tradeability rejects snapshots whose validity has expired at the evaluation
  cutoff (`TradeabilityAnalysisCapability.java:109-121`).

### Root-cause classification

**Confirmed data freshness condition:** the persisted Market Thesis lineage is
older than the research execution and is therefore stale by the service's
contract.

**Likely integration/data lifecycle gap:** deterministic analysis requests
`EvidenceAcquisitionPolicy.LIVE_CURRENT` at
`ResearchApplicationService.java:348-351`, while the report freshness label is
still based on the persisted thesis validity. This creates a mixed result:
current market evidence can be acquired while the thesis-level freshness field
remains stale.

**Not established:** whether the stale thesis is caused by a broken upstream
market-data synchronization job, an intentionally historical thesis, or a
missing thesis recomposition step. Available evidence proves the stale input,
not the upstream operational cause.

### Minimum corrective direction

- Define whether research freshness is thesis freshness, live-evidence
  freshness, or both.
- Persist the selected freshness basis and its timestamps in the report.
- If `LIVE_CURRENT` is the intended research mode, either recompute/update the
  thesis before classification or report separate `thesisFreshness` and
  `evidenceFreshness` fields.
- Add a freshness regression test covering a stale persisted thesis with a
  current live snapshot.

## AI Engine Validation Boundary

The Java adapter calls the AI Engine through the internal analysis and result
endpoints. The runtime health endpoint confirms the AI Engine is enabled and
configured for `openai_chatgpt`; Python provider selection also confirms that
the configured provider path is eligible when AI is enabled.

The available runtime evidence does not contain a provider request receipt,
provider response metadata, or safe model invocation trace. Therefore the
following claims are supported:

- **Verified:** Trading Core/Market Intelligence reached the Python AI Engine.
- **Verified:** The Python AI Engine accepted and completed the execution
  lifecycle used by EUR/USD.
- **Unverified:** The configured external provider actually served the model
  response rather than a fallback, mock, or otherwise internal result.

The Python AI Engine targeted tests passed: `18 passed`.

## Corrective Priority

1. **P0: Make per-market AI eligibility explicit.** Prevent a multi-market
   report from silently presenting a budget skip as `DISABLED`.
2. **P1: Define and expose freshness semantics.** Separate persisted thesis
   freshness from live evidence freshness, or make the report use one explicit
   basis.
3. **P1: Add safe provider provenance.** Persist a non-secret provider/model
   execution classification or receipt sufficient to distinguish configured
   provider execution from fallback, without storing prompts, credentials, or
   sensitive payloads.
4. **P2: Add regression coverage.** Cover two eligible markets, one stale thesis
   plus current live evidence, and provider/fallback provenance.

## Rerun Criteria

A future validation rerun should be considered useful only after the following
are decided and, where applicable, implemented:

- The expected AI budget policy for multi-market research is explicit.
- The report distinguishes `DISABLED`, `NOT_REQUESTED`, and budget-skipped
  markets.
- Freshness semantics identify thesis and live-evidence timestamps.
- Provider provenance can be verified without exposing secrets.
- The required research scope is still valid or a new human-approved scope is
  created.

## Validation Performed

- Read-only inspection of the existing validation report, ADR-089, ADR-092,
  relevant Market Intelligence source, AI Engine source, and runtime logs.
- AI Engine health check: `GET http://127.0.0.1:8086/health` returned
  `{"status":"UP","enabled":true,"shadowMode":true,"provider":"openai_chatgpt"}`.
- Targeted Python tests:
  `ai-engine/.venv/bin/python -m pytest ai-engine/tests/test_service.py ai-engine/tests/test_api.py ai-engine/tests/test_provider_runtime.py -q`
  -> `18 passed`.
- The attempted root Maven wrapper command was not applicable because the
  repository has no root `./mvnw`; no Java test result is claimed here.
