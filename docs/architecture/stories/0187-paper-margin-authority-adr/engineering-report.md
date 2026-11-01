# Engineering Report - Story 0187

## Outcome

Story 0187 completed as an architecture-only Story. ADR-083 was independently
reviewed and explicitly accepted by the human engineer.

```text
STORY = 0187
ADR = ADR-083
ADR_STATUS = ACCEPTED
PRODUCTION_CODE_CHANGED = NO
IMPLEMENTATION_DEFERRED = YES
```

## Baseline

```text
REMOTE_MAIN = f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
LOCAL_BASE = f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
LOCAL_HEAD = f408172ebf4ba1ebb5ef6ae1eef0b6e465b1bece
BRANCH = story/0187-paper-margin-authority-adr
STORY_0186 = CONFIRMED
PAPER_MARGIN_PREVIEW_RUNTIME = AVAILABLE
PAPER_MARGIN_PREVIEW_AUTHORITATIVE = NO
ADR_082_PHASE_8 = BLOCKED
```

## Decision

```text
PAPER_MARGIN_AUTHORITY = LOCAL_VERSIONED_POLICY
PAPER_SIMULATION_MODEL = HYBRID_REFERENCE_DERIVED_LOCAL_POLICY
MARGIN_POLICY_CONCEPT = PROVIDER_NEUTRAL_MARGIN_POLICY
POLICY_OWNER = PAPER_FINANCIAL_ACCOUNT_OR_OWNED_SIMULATION_PROFILE
POLICY_SCOPE = ACCOUNT_WITH_EXACT_INSTRUMENT_OR_PRODUCT_RULES
POLICY_VERSIONED = YES
POLICY_PROVENANCE_REQUIRED = YES
```

Provider/reference configuration may seed a normalized policy, but runtime PAPER
authority is the immutable local policy. Broker Service is not required for PAPER
margin authorization.

## Margin Semantics

```text
REQUIRED_MARGIN_DEFINITION = INCREMENTAL_COLLATERAL_FOR_PROPOSED_ORDER
CANONICAL_MARGIN_REPRESENTATION = STATIC_POSITIVE_MARGIN_RATE
EXISTING_EXPOSURE_MODEL = SIGNED_NET_EXPOSURE_PER_ACCOUNT_AND_INSTRUMENT
NETTING = SUPPORTED_V1
HEDGING = UNSUPPORTED_V1
TIERED_MARGIN = UNSUPPORTED_V1
DYNAMIC_MARGIN = UNSUPPORTED_V1
```

V1 computes the difference between resulting and existing net exposure margin,
floored at zero. The current persisted PAPER trade list is not silently treated
as a complete net-position implementation; a later exposure snapshot boundary
must validate and aggregate it.

## Currency and Failure Behavior

```text
MARGIN_CALCULATION_CURRENCY = INSTRUMENT_QUOTE_CURRENCY
REPORTING_CONVERSION = EXPLICIT_DIRECTED_MARKET_DATA_FX
FX_AUTHORITY = MARKET_DATA_VALUATION_FACT
MISSING_FX = UNAVAILABLE
MISSING_POLICY = UNAVAILABLE
UNSUPPORTED_POLICY = UNAVAILABLE
MISSING_CONTRACT = UNAVAILABLE
MISSING_MARK = UNAVAILABLE
AMBIGUOUS_EXPOSURE = UNAVAILABLE
```

The ADR defines exact directed FX path semantics, deterministic freshness, rate
precision, exact intermediate arithmetic, and upward final monetary rounding.

## Mode Authority

```text
PAPER = LOCAL_TRADING_CORE_POLICY_AND_STATE
LIVE = PROVIDER_AUTHORITATIVE_FACT_THROUGH_BROKER_SERVICE
PAPER_RUNTIME_BROKER_MARGIN_DEPENDENCY = NO
CURRENT_PAPER_PROVIDER_PREVIEW_FUTURE = DIAGNOSTIC_OR_CONFIG_IMPORT_ONLY
```

The current PAPER provider preview remains non-authoritative until removed from
the authorization path by a future implementation Story.

## T0/T1

```text
T0_POLICY_SEMANTICS = CAPTURE_IMMUTABLE_POLICY_VERSION_WITH_CONTEXT
T1_POLICY_SEMANTICS = REUSE_EXACT_T0_POLICY_VERSION_WITH_FRESH_FACTS
POLICY_CHANGE_BETWEEN_T0_T1 = FAIL_CLOSED_ON_MISSING_REVOKED_OR_EXPIRED_VERSION
T1_FRESH_FACTS_REQUIRED = YES
```

Human authorization remains a separate gate from valid required margin.

## Domain Boundaries

```text
INSTRUMENT_FINANCIAL_CONTRACT_SEPARATE = YES
RISK_DOMAIN_PROVIDER_NEUTRAL = YES
RISK_DOMAIN_MARGIN_FORMULA = NO
FTMO_CHALLENGE_RULES_SEPARATE = YES
```

The Risk Domain receives the normalized monetary fact and does not calculate
margin or receive provider/policy mechanics.

## Compatibility and Safety

```text
CASH_PLUS_CFD = REJECTED
INVENTORY_PLUS_CFD = REJECTED
ADR_082_PHASE_8_RETRIED = NO
CTRADER_SAFETY_EXECUTION_ENABLED = false
BROKER_ORDER_SUBMISSIONS = 0
BROKER_POSITION_CLOSES = 0
PROVIDER_MUTATIONS = 0
```

## Validation and Review

- Latest-main baseline verified before work.
- ADR-082 and Stories 0183-0186 reconciled with current source.
- Independent architecture review completed with zero HIGH and MEDIUM findings.
- `git diff --check` and untracked Markdown whitespace checks passed.
- No production test suite was required because no production behavior changed.
- No database, Broker Service, Risk Domain, API, or compatibility changes were
  made.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is required or proposed;
the outcome remains proposal-only within the repository documentation workflow.

## Next Implementation Sequence

1. Implement the canonical financial-account and routing-account binding.
2. Introduce immutable versioned provider-neutral `MarginPolicy` assignment.
3. Add the validated PAPER exposure snapshot boundary.
4. Implement static netting-only incremental margin with explicit precision and
   FX semantics.
5. Route PAPER locally and preserve LIVE provider authority.
6. Remove the PAPER provider preview from authorization and retain only labelled
   diagnostic/import use.
7. Validate T0/T1, provenance, restart reproducibility, and fail-closed cases.
8. Create a separate Story to reconsider ADR-082 Phase 8 only after validation.

## Final Readiness

```text
ADR_NOT_READY = NO
READY_FOR_MARGIN_IMPLEMENTATION = YES
READY_TO_RETRY_ADR_082_PHASE_8 = NO
```

`READY_FOR_MARGIN_IMPLEMENTATION = YES` means the accepted architecture is now
precise enough for a separate implementation Story. It does not authorize
implementation in Story 0187 and does not authorize `CASH + CFD` compatibility.
