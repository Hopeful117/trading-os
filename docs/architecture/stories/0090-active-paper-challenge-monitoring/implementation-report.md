# Implementation Report

## Scope

Implemented the provider-neutral active PAPER Challenge monitoring trigger
described by Story 0090. The implementation discovers ACTIVE Challenges in
stable bounded pages and delegates each eligible PAPER Challenge to the
existing transactional Risk and Challenge reevaluation pipeline.

## Changes

- Added keyset-style ACTIVE Challenge discovery ordered by `startedAt` and ID.
- Added pessimistically locked identifier-based Challenge reload and
  `REQUIRES_NEW` reevaluation.
- Added `ActiveChallengeMonitoringService` with PAPER filtering, per-Challenge
  failure isolation, cycle statistics, and structured logs.
- Added configurable scheduler properties for enabled state, interval, and
  batch size.
- Added a thin conditional scheduled trigger; direct orchestration remains
  available when scheduling is disabled.
- Revalidated the authoritative BrokerAccount execution mode inside the
  locked `REQUIRES_NEW` reevaluation boundary, preventing a PAPER trigger from
  evaluating a Challenge that became LIVE after discovery.
- Disabled scheduling in the test profile.
- Added focused tests for bounded paging, PAPER/LIVE filtering, failure
  isolation, cycle statistics, and identifier-based authoritative reload.
- Added Spring integration coverage for JPQL pagination and terminal exclusion,
  disabled scheduler context behavior, and concurrent reevaluation serialization.

## Validation

- `mvn -q -Dtest=ActiveChallengeMonitoringServiceTest,ChallengeReevaluationServiceTest test` in `trading-core`: passed.
- `mvn -q -Dtest=ActiveChallengeMonitoringServiceTest,ChallengeReevaluationServiceTest,ChallengePersistenceTest,ScheduledChallengeMonitoringTriggerTest,DisabledChallengeMonitoringContextTest,ChallengeMonitoringConcurrencyIntegrationTest test` in `trading-core`: passed.
- `mvn -q -DskipTests verify` in `trading-core`: passed.
- `mvn -q verify` in `risk-domain`: passed.
- `git diff --check`: passed.
- Existing PAPER valuation and Risk facts tests already cover local balances,
  open trades, and PAPER CASH valuation refresh behavior used by the delegated
  reevaluation path.

Mockito self-attachment, Byte Buddy, H2/Flyway, LoadBalancer, and SpringDoc
warnings remain non-blocking test/runtime warnings.

DevLog context retrieval was unavailable because the Story Agent API quota was
exhausted; current repository and canonical Story artifacts were used.

## Exclusions

- No provider-specific or Kraken-specific monitoring logic.
- No direct Market Data access, execution action, position closure, broker
  disablement, or event bus.
- No exact Risk Day reset-boundary reconstruction; the approved decision is
  **NO**, preserving existing first-authoritative-observation semantics.
- No Git commit, push, merge, or branch integration.
