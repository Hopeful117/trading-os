# Market Intelligence Quality and TradePlan/T0 Consolidation

## 1. Final Verdict

`CONSOLIDATION_VALIDATED_WITH_LIMITATIONS`

The requested consolidation is implemented and the relevant backend/database
validation passes. The workflow now makes the score meaning explicit, preserves
the distinction between market availability and complete tradeability evidence,
starts under H2, and has deterministic public-orchestration/Risk regression
coverage without any LIVE broker operation.

Limitations remain:

- The FTMO LIVE opportunity remains `NO_TRADE`; no real LIVE opportunity was
  fabricated to force T0.
- `LIVE_CURRENT` Tradeability remains intentionally unsupported and fail-closed.
- The public Spring integration test uses mocked external application ports for
  Market Intelligence and Risk; the actual deterministic Risk evaluation is
  covered by the Trading Core Risk application tests rather than by a single
  cross-service test with all persistence adapters real.
- The complete Angular suite has 15 existing failures across 8 files, mostly
  timeouts in unrelated screens. The production build succeeds.

No broker execution, account synchronization, position mutation, authorization
challenge or credential operation was performed.

## 2. Authoritative Context

The work is tracked by:

- `docs/architecture/stories/0234-market-intelligence-tradeplan-t0-consolidation/story.md`
- `docs/architecture/adr/ADR-076-market-thesis-authority-and-evidence-composition.md`
- `docs/architecture/adr/ADR-079-deterministic-liquidity-evidence-trust-and-policy-boundary.md`
- `docs/architecture/adr/ADR-086-evidence-based-risk-evaluation.md`
- `docs/validation/ftmo-live-research-tradeplan-t0-e2e-2026-11-13.md`

ADR-076 keeps Market Thesis as decision support, not probability, Risk or
execution authority. ADR-079 explicitly states that observed spread/order-book
evidence does not prove suitable liquidity and that `LIVE_CURRENT` Tradeability
is unsupported. ADR-086 requires per-invariant evidence requirements, partial
diagnostics and fail-closed aggregate authorization.

The DevLog applicability check completed with `LOW` confidence because its
projection was truncated and did not establish the complete Story/test history.
The repository and accepted ADRs were therefore used as the authoritative
implementation sources.

## 3. Scoring Semantics

The existing calculation is correct and was not changed:

```text
opportunity score = average(observation confidence scores) * 100
```

`OpportunityScore` already constrained the value to `0..100` and documented it
as business priority, never probability. It is used by
`OpportunityRankingEngine` for relative ordering. It is not:

- a calibrated probability of success;
- a Risk decision;
- a Market Thesis state;
- evidence quality;
- execution eligibility.

The previous `100.00 + NO_TRADE + DEGRADED` result is therefore semantically
valid: directional/observation priority can be maximal while the thesis is
blocked by missing or contradictory evidence.

### Correction

The public opportunity projection now includes:

```json
"score": 100.00,
"scoreSemantics": "BUSINESS_PRIORITY_NOT_PROBABILITY"
```

The Angular opportunity table already rendered the Thesis decision first and
kept `NO_TRADE` visibly distinct. Its score column now has an explicit tooltip
and remains labeled `Signal strength`; it does not present the value as a
probability or recommendation.

Analysis result quality remains a separate scan-level projection and remains
visible in the scan panel. No artificial score reduction was introduced.

## 4. Tradeability Evidence

The existing pipeline is:

```text
Market Data snapshot
  -> IntelligenceContext sections
  -> TradeabilityAnalysisCapability
  -> TradeabilityCalculator
  -> MarketThesisComposer
  -> TradePlan admission
```

### Evidence already available

`MarketSnapshotContext` already preserves:

- market identity;
- symbol;
- last price;
- bid and ask;
- `tradable` flag;
- occurrence timestamp.

The contributor also preserves source snapshot identity, source version,
occurred-at, fetched-at, evidence policy and evaluation cutoff through
`ContextProvenance`.

`TradeabilityCalculator` already checks:

- snapshot availability and freshness;
- valid bid/ask and cutoff compatibility;
- observed spread;
- order-book replay validity and cutoff compatibility;
- historical OHLC range compatibility;
- time-bounded News evidence;
- market/instrument identity at thesis composition.

`MarketThesisComposer` maps supporting, weakening, blocking and missing
tradeability evidence into typed Thesis evidence. Identity, cutoff and stale
assessment mismatches receive precise diagnostics such as:

- `TRADEABILITY_UNAVAILABLE`;
- `TRADEABILITY_IDENTITY_INCOMPATIBLE`;
- `TRADEABILITY_CUTOFF_INCOMPATIBLE`;
- `TRADEABILITY_STALE`.

### Why the LIVE scan remained blocked

`TradeabilityAnalysisCapability.analyze` explicitly returns an empty result for
`EvidenceAcquisitionPolicy.LIVE_CURRENT` because approved live order-book
evidence is not available. This is the correct implementation of ADR-079, not a
mapping defect. `MarketState.OPEN` and `tradable=true` prove market availability
and provider-level tradability only; they do not prove complete strategy
tradeability or liquidity suitability.

The calculator also deliberately emits non-required `NOT_IMPLEMENTED` evidence
for spread policy, liquidity thresholds and range policy. These are not silently
promoted to support, and no universal threshold was invented.

No propagation correction was required in this area.

## 5. V69 Migration

The common migration was changed from one PostgreSQL-style multi-column
`ALTER TABLE` statement to two portable statements:

```sql
ALTER TABLE broker_account ADD COLUMN risk_profile_id UUID;
ALTER TABLE broker_account ADD COLUMN risk_profile_semantic_version VARCHAR(64);
```

This preserves the schema and PostgreSQL behavior while allowing H2 2.4 to
initialize the Spring integration context.

The migration is currently an uncommitted workspace migration and was validated
on fresh PostgreSQL schemas, so no committed production checksum was silently
rewritten. The migration-history assertions were updated from 67 to 69 because
the workspace now contains V67, V68 and V69.

## 6. TradePlan to T0 Validation

### Scenario A: valid candidate

The public Trading Core opportunity orchestration integration test now verifies
that a successful Market Intelligence response:

- preserves account identity and TradePlan version in the Risk command;
- invokes the supported Risk preflight application service;
- persists no execution intent during preparation.

The actual deterministic Risk service tests validate a completed approved
evaluation with exact plan/version identity and persisted traceability.

### Scenario B: `NO_TRADE`

The orchestration integration test verifies that a Market Intelligence 422
rejection does not invoke Risk. The unit regression test verifies that the
downstream reason is preserved as:

```text
code=MARKET_INTELLIGENCE_REJECTED
```

The Market Intelligence generation path rejects any thesis state other than
`CANDIDATE` before market-price acquisition or TradePlan persistence.

### Scenario C: optional margin facts unavailable

Existing deterministic Risk coverage confirms that missing `usedMargin` does
not block a policy that does not require it. The independent invariant can be
evaluated; aggregate behavior remains governed by the selected policy.

### Scenario D: mandatory facts missing

Existing Risk coverage confirms:

- missing equity produces `INCOMPLETE` for equity-dependent evaluation;
- incomplete broker facts produce `CONTEXT_UNAVAILABLE` with explicit reasons;
- missing required margin fails closed without leverage inference;
- missing position protection does not acknowledge Risk validation.

### Scenario E: identity and stale versions

Existing TradePlan and Risk tests cover account ownership, account mismatch,
planning-context ownership, exact/latest plan version requirements, and stale
TradePlan rejection. The Risk handoff requires the exact proposed/accepted
version and consistent account/context identity.

No readiness flag was forced and no Risk policy or threshold was changed.

## 7. Corrections Implemented

| Area | Correction |
|---|---|
| API semantics | Added `scoreSemantics` to `OpportunityResponse`. |
| Angular presentation | Added explicit score tooltip; Thesis decision remains authoritative and first. |
| TradePlan diagnostics | Existing correction now applies to `createFromOpportunity`, preserving downstream rejection reason. |
| H2 compatibility | Split V69 into two portable `ALTER TABLE` statements. |
| Migration assertions | Updated current-history assertions to version 69. |
| T0 integration boundary | Added public orchestration assertions for successful Risk invocation and `NO_TRADE` short-circuit. |

No architectural boundary changed and no new ADR was required.

## 8. Tests Executed

Passed:

- `mvn -q -Dtest=OpportunityEngineTest,OpportunityPoliciesTest,OpportunitySetupSnapshotPropagationTest,AnalysisTradePlanGenerationServiceTest,OpportunityTradePlanGenerationServiceTest test` in `market-intelligence`.
- `mvn -q -Dtest=OpportunityTradePlanGenerationServiceTest,MarketThesisComposerTest,TradeabilityAnalysisCapabilityTest test` in `market-intelligence`.
- `mvn -q -Dtest=OpportunityControllerTest test` in `market-intelligence`.
- `./mvnw -q -Dtest=OpportunityTradePlanPreparationIntegrationTest,OpportunityTradePlanOrchestrationServiceTest test` in `trading-core`.
- `./mvnw -q -Dtest=TradePlanRiskEvaluationServiceTest,InvariantEvidenceAdmissionTest,RiskAcknowledgmentDeliveryServiceTest test` in `trading-core`.
- `./mvnw -q -Dtest=OpportunityTradePlanPreparationIntegrationTest,OpportunityTradePlanOrchestrationServiceTest,TradePlanRiskEvaluationServiceTest,InvariantEvidenceAdmissionTest test` in `trading-core`.
- `./mvnw -q -Dtest=PostgresFreshInstallMigrationTest test` in `trading-core` after migration-history expectations were aligned.
- `npm run build` in `trading-os-web`.
- `git diff --check`.

Angular validation:

- `npm run test:ci` executed: 574 passed, 15 failed across 8 existing suites,
  primarily timeout failures outside the changed opportunity presentation.
- A direct `npm exec vitest run ...` attempt was not considered valid Angular
  validation because it bypassed the repository test configuration and failed
  with `describe is not defined`.

Build warnings remain for pre-existing Angular template diagnostics and bundle
style budgets; the production build exits successfully.

## 9. Files Modified

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/domain/opportunity/OpportunityScore.java`
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/OpportunityResponse.java`
- `trading-os-web/src/app/core/models/opportunity.model.ts`
- `trading-os-web/src/app/features/opportunities/opportunities.html`
- `trading-core/src/main/resources/db/migration/common/V69__persist_live_broker_account_risk_profile.sql`
- `trading-core/src/test/java/com/hope/trading/trading_core/migration/PostgresFreshInstallMigrationTest.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/tradeplanning/application/OpportunityTradePlanPreparationIntegrationTest.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/tradeplanning/application/OpportunityTradePlanOrchestrationService.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/tradeplanning/application/OpportunityTradePlanOrchestrationServiceTest.java`
- `docs/architecture/stories/0234-market-intelligence-tradeplan-t0-consolidation/story.md`

The earlier workspace changes listed by `git status` were not reverted or
modified outside the files directly required by this consolidation.

## 10. Remaining Limitations

- A true successful LIVE Research-to-T0 path remains impossible with the
  observed `NO_TRADE` thesis and incomplete cTrader financial facts, by design.
- `LIVE_CURRENT` Tradeability requires an approved live evidence source before
  it can produce a candidate-level tradeability result.
- The integration test uses mock service ports at the cross-service boundary;
  persistence-backed Risk behavior is validated separately in Trading Core.
- Angular full-suite timeout failures remain outside this focused change.

## 11. Safety Confirmation

- No cTrader write tool was used.
- No broker synchronization was started.
- No order, position or authorization challenge was created.
- No risk policy, account flag or credential was changed.
- No test contacts a real broker.
