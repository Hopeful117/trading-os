# Story 0149 - Implementation Report

## Result

`IMPLEMENTED LOCALLY - REAL CTRADER VALIDATION REQUIRED`

The existing provider-neutral v2 authoritative snapshot now feeds the existing
live Risk facts boundary. No new Risk architecture or provider-specific Risk
formula was introduced.

## Changed Files

- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProvider.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProviderTest.java`
- Story 0149 closure artifacts in this directory.

## Implemented Behavior

- Fresh and complete v2 account facts map to `RiskFactsProvider.Snapshot`.
- Identity mismatches fail closed before any legacy fallback.
- Only Feign retryable v2 failures use the legacy Kraken-compatible path.
- Stale, incomplete and missing required account facts remain unavailable.
- Open positions without provider margin/protection facts remain unavailable.
- Deals are limited to `[from, to)` and preserve `settlementAsset`.
- Malformed deal timestamps remain present for downstream fail-closed validation.
- Serialized authoritative payloads remain available as Risk provenance.

## Documentation Outcome

The Story, Repository Analysis, Implementation Plan, Implementation Report, Code
Review Report and Engineering Report were created because the implementation
existed without the canonical Story 0149 closure artifacts. No existing
architecture document required modification.

## Vault Outcome

The vault was not consulted. No curated-vault action is proposed; the repository
and accepted ADRs were sufficient and authoritative.

## Validation

| Check | Result |
|---|---|
| Trading Core compilation | PASS |
| `ModeAwareRiskFactsProviderTest` | PASS |
| Targeted Risk service tests | PASS |
| `BrokerSynchronizationServiceImplTest` | PASS |
| `git diff --check` | PASS |
| Real cTrader validation | NOT RUN - credentials and controlled provider procedure unavailable |

Mockito emitted a JDK dynamic-agent warning during tests; it did not fail the
test runs.
