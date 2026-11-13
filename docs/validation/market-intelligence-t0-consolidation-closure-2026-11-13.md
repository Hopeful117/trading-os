# Market Intelligence to T0 Consolidation Closure Review

## 1. Executive Summary

Final verdict: `CONSOLIDATION_PARTIALLY_VALIDATED`

The score semantics, Market Thesis eligibility boundary, `LIVE_CURRENT` fail-closed
behavior, V69 migration compatibility and deterministic Risk rules are coherent
and pass the focused validations. No LIVE broker operation occurred.

The full deterministic sequence is **not** proven by one end-to-end application
test. `OpportunityTradePlanPreparationIntegrationTest` starts the Trading Core
Spring application, but it replaces both the Market Intelligence client and
`TradePlanRiskEvaluationService` with Mockito beans. It proves the Trading Core
orchestration boundary and the `NO_TRADE` short-circuit, not the real T0
evaluation and persistence in that same workflow. The actual Risk service is
validated separately by 33 passing tests and a separate Paper CFD integration
test exists, but those facts do not satisfy the full closure criterion alone.

This is a validation limitation, not evidence of a runtime defect. The verdict
therefore remains partial rather than full.

## 2. Review Scope and Git State

Reviewed:

- `docs/validation/ftmo-live-research-tradeplan-t0-e2e-2026-11-13.md`
- `docs/validation/market-intelligence-tradeplan-t0-consolidation-2026-11-13.md`
- Story 0234 and ADR-076, ADR-079, ADR-086
- current consolidation diff and relevant untracked migration/story/report files
- Market Intelligence, Trading Core, MCP Adapter and Angular contracts

The working tree contains many unrelated changes in Gateway, Market Data, MCP,
Trading Core agent/execution authorization and frontend execution areas. They
were not discarded, reset, reformatted or included in this focused correction.

The relevant consolidation changes are limited to:

- `OpportunityScore` semantic constant and REST projection;
- Angular opportunity model and score presentation;
- TradePlan downstream rejection diagnostics;
- V69 migration portability and migration assertions;
- orchestration integration assertions;
- Story and validation documentation.

`git diff --check` passes.

## 3. Scoring Contract

The canonical domain calculation remains:

```text
average(observation confidence in 0..1) * 100
```

`OpportunityScore` constrains the result to `0..100` and documents it as
business priority, not probability. `OpportunityRankingEngine` uses it for
relative ordering only.

The REST `OpportunityResponse` now always emits:

```json
"scoreSemantics": "BUSINESS_PRIORITY_NOT_PROBABILITY"
```

The MCP Adapter does not duplicate or reinterpret this contract. Its opportunity
tools return the Gateway/REST JSON response through the existing safe client, so
the field is preserved transparently. No MCP-specific score calculation exists.

The Angular contract now requires the canonical literal rather than accepting an
arbitrary string or omitting the field. The opportunity table keeps the Thesis
decision separate from score and labels the score as signal strength with a
non-probability tooltip.

Therefore this combination remains valid and non-actionable:

```text
score = 100.00
quality = DEGRADED
thesis = NO_TRADE
```

`NO_TRADE` is authoritative for admission. Blocking contradictions and required
missing evidence are represented by Market Thesis; score, quality and thesis are
not collapsed into one number.

Relevant authority:

- ADR-076: Market Thesis is decision support, not Risk, TradePlan or execution.
- ADR-079: observed liquidity evidence is not automatically suitable liquidity.
- Story 0234: `SIGNAL_SCORE != PROBABILITY` and `NO_TRADE != TRADE_RECOMMENDATION`.

## 4. `LIVE_CURRENT` Blocker

The precise blocker is deliberate policy, not market-state mapping:

```java
TradeabilityAnalysisCapability.analyze(...)
```

returns an empty result when the request policy is `EvidenceAcquisitionPolicy.LIVE_CURRENT`:

```text
Tradeability requires an approved live order-book source; LIVE_CURRENT is unsupported
```

The capability requires an approved live order-book source because current
cTrader depth evidence does not establish the required complete, timestamped,
sequence-valid and replay-trustworthy Level II contract. ADR-079 explicitly
keeps this mode unsupported and fail-closed.

The downstream path is:

```text
Market snapshot / context
  -> TradeabilityAnalysisCapability
  -> TradeabilityCalculator
  -> MarketThesisComposer
  -> TradePlan admission
```

The calculator preserves snapshot identity, cutoff, freshness, bid/ask and
optional order-book/OHLC/news evidence. The composer maps unavailable, stale,
identity-incompatible and cutoff-incompatible tradeability into typed thesis
evidence. `MarketState.OPEN` and `tradable=true` establish market availability,
not liquidity suitability or complete candidate eligibility.

No narrow mapping defect was found. No fallback price, synthetic quote, provider
or policy threshold was added.

## 5. Deterministic TradePlan to T0 Evidence

### Scenario A: Valid candidate

**Partially demonstrated.** The Spring integration test invokes the public
`OpportunityTradePlanOrchestrationService.createFromOpportunity` method and
verifies that a successful mocked Market Intelligence response creates the Risk
preflight command with:

- the returned TradePlan ID;
- version `1`;
- the expected account ID;
- no execution intent persistence.

However, the test declares:

```java
@MockitoBean private MarketIntelligenceTradePlanningClient marketIntelligence;
@MockitoBean private TradePlanRiskEvaluationService risk;
```

Thus it does not execute the real Market Intelligence admission, TradePlan
persistence or T0 Risk engine in one application workflow. The actual Risk
application service independently passes 33 tests, including completed
approval, rule evidence, identity, persistence and acknowledgment behavior.

### Scenario B: `NO_TRADE`

**Demonstrated at the orchestration boundary.** A simulated Market Intelligence
422 causes TradePlan creation to fail and verifies Risk is not invoked. The
downstream reason regression test verifies:

```text
code=MARKET_INTELLIGENCE_REJECTED
details.downstreamReason=MARKET_THESIS_PREPARATION_BLOCKED_NO_TRADE
```

The real Market Intelligence generation path rejects any thesis state other than
`CANDIDATE` before TradePlan generation. No TradePlan or T0 evaluation is created
for the reported LIVE `NO_TRADE` opportunity.

### Scenario C: Optional margin facts

**Demonstrated in Risk service tests.** Capability-scoped requirements allow
independent margin-independent invariants to evaluate when their requirements
are complete. Missing margin evidence remains unavailable and cannot silently
become zero or a positive result. Aggregate authorization remains governed by
the selected policy and remains fail-closed when a mandatory rule is incomplete.

### Scenario D: Mandatory facts

**Demonstrated in Risk service and invariant-admission tests.** Missing equity,
broker facts, required margin or position protection produces explicit
incomplete/context-unavailable diagnostics. A mandatory incomplete invariant
cannot pass aggregate execution authorization.

### Scenario E: Identity and authorization

**Demonstrated in focused tests, not as one end-to-end test.** Existing tests
cover account ownership/mismatch, broker-account mapping, planning context,
exact TradePlan version, stale version and Risk identity validation. No human
authorization or execution continuation was invoked.

### T0 conclusion

The deterministic Risk implementation and the public orchestration boundary are
coherent, but the requested full application-boundary T0 E2E remains unproven.
The current evidence supports `PARTIALLY_VALIDATED`, not `VALIDATED`.

## 6. V69 Migration

V69 is now portable for the supported H2 test configuration:

```sql
ALTER TABLE broker_account ADD COLUMN risk_profile_id UUID;
ALTER TABLE broker_account ADD COLUMN risk_profile_semantic_version VARCHAR(64);
```

Validation evidence:

- H2 Spring context starts for `OpportunityTradePlanPreparationIntegrationTest`.
- PostgreSQL Testcontainers runs `PostgresFreshInstallMigrationTest` with 7
  passing tests.
- Flyway validates and migrates 69 migrations on PostgreSQL.
- Fresh PostgreSQL schemas reach version 69.
- Historical PostgreSQL migration path 33 → 69 passes without rewriting prior
  migration history.

This validates fresh and repository-controlled historical migration paths. It
does **not** inspect a deployed production database's current
`flyway_schema_history` or compare its V69 checksum. No deployed database
credentials or endpoint were available or used, so deployed checksum status is
`UNKNOWN`, not assumed safe.

## 7. Angular Failure Classification

The previously reported 15 Angular failures were not reproducible in this
closure run:

```text
npm run test:ci
Test Files: 61 passed
Tests:      589 passed
```

No failing test name or exact error from the earlier run is present in the
repository evidence, so those earlier failures cannot be reliably classified as
pre-existing or introduced by this consolidation. The current result provides
no Angular failure to correct and no evidence of a frontend regression.

The production build also passes. Existing compiler warnings outside the changed
opportunity screen remain non-blocking warnings.

## 8. Cross-Service Compatibility

| Boundary | Evidence level | Result |
|---|---|---|
| Market Intelligence score domain → REST | Unit/controller | `scoreSemantics` is emitted canonically. |
| REST → Angular | Type/build | Required literal contract and presentation compile/build. |
| REST → MCP Adapter | Contract passthrough/tests | MCP does not duplicate score semantics; JSON is preserved by the safe client. |
| Market Thesis → TradePlan admission | Market Intelligence tests and LIVE report | `NO_TRADE` blocks admission; no execution eligibility inferred. |
| Trading Core orchestration → Risk preflight | Spring orchestration integration | Command identity/version propagation and `NO_TRADE` short-circuit pass; Risk service is mocked at this boundary. |
| Risk requirements → deterministic engine | Risk application/invariant tests | Rule-level partial diagnostics and fail-closed aggregate behavior pass. |
| Risk persistence / PostgreSQL | Targeted integration tests | PostgreSQL migration/history validation passes; full Risk persistence integration was not part of this closure run. |
| Full Market Intelligence service → Trading Core → real Risk | Cross-service E2E | Not tested in one local workflow. |

## 9. Corrective Changes

### Applied

- Tightened Angular `OpportunityResponse.scoreSemantics` from optional/arbitrary
  string to the canonical required literal.
- Removed the Angular fallback that could hide a missing or invalid semantics
  field; the API contract is now the single source of truth.
- Updated the seven affected Angular opportunity fixtures to carry the required
  canonical field.
- Normalized whitespace introduced in the opportunity table change.

Root cause: the first frontend contract exposed the new field but allowed its
absence and any string value, weakening the intended cross-service guarantee.

Compatibility: the backend already emits the canonical field. Older backend
responses that omit it will now fail the typed consumer contract rather than
silently presenting ambiguous score semantics.

### Not applied

No change was made to `LIVE_CURRENT`, Risk thresholds, identity gates, migration
history, broker integrations, MCP authority or execution boundaries.

The mocked integration test was not rewritten during this review because
constructing a real cross-service/persistence-backed Market Intelligence to
Trading Core to Risk fixture would be a separate test-design change, not a
minimal demonstrated production defect. Its scope is explicitly corrected in
this closure verdict.

## 10. Exact Tests Executed

Passed:

- `mvn -q -Dtest=OpportunityEngineTest,OpportunityPoliciesTest,OpportunitySetupSnapshotPropagationTest,AnalysisTradePlanGenerationServiceTest,OpportunityTradePlanGenerationServiceTest,MarketThesisComposerTest,TradeabilityAnalysisCapabilityTest,OpportunityControllerTest test` in `market-intelligence` — 41 tests.
- `./mvnw -q -Dtest=OpportunityTradePlanPreparationIntegrationTest,OpportunityTradePlanOrchestrationServiceTest,TradePlanRiskEvaluationServiceTest,InvariantEvidenceAdmissionTest test` in `trading-core`.
- `./mvnw -q -Dtest=PostgresFreshInstallMigrationTest test` in `trading-core` — 7 tests.
- `mcp-adapter/.venv/bin/python -m pytest mcp-adapter/tests` — 41 tests.
- `npm run test:ci` in `trading-os-web` — 589 tests in 61 files.
- `npm run build` in `trading-os-web`.
- `git diff --check`.

Expected warnings:

- Mockito dynamic-agent warning under the current JDK.
- Flyway warning that H2 2.4.240 is newer than its verified H2 version.
- Existing Angular extended diagnostics and canvas/jsdom warnings.

No test contacted cTrader or another real broker.

## 11. Remaining Blockers and Risks

- The real full application-boundary deterministic T0 E2E is not yet present.
- Deployed production Flyway checksum status for V69 is unknown; local fresh and
  historical validation passed.
- `LIVE_CURRENT` remains intentionally unsupported until an approved live
  order-book evidence contract exists.
- The previous Angular 15-failure claim is stale/not reproducible; a baseline
  comparison is unavailable, so historical root cause remains unknown.
- The repository remains uncommitted and contains unrelated user changes.

## 12. Safety Confirmation

- No broker order was submitted.
- No position was modified or closed.
- No account synchronization was started.
- No execution authorization or human approval challenge was created.
- No RiskRules, credentials, permissions or LIVE flags were changed.
- No synthetic LIVE opportunity or fallback market data was introduced.

## 13. Final Verdict

`CONSOLIDATION_PARTIALLY_VALIDATED`

The score/quality/thesis/eligibility boundaries are coherent, the deliberate
`LIVE_CURRENT` block is correctly identified and preserved, V69 is validated on
H2 and fresh/historical PostgreSQL paths, and all current focused test suites
pass. The closure cannot claim full validation because the existing public
TradePlan preparation integration test mocks the real T0 Risk service and the
Market Intelligence client. That missing end-to-end evidence is the only
material closure limitation identified in this review.
