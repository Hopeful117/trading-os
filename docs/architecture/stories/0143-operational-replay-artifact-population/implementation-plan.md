# Implementation Plan - Story 0143

## Design

Add an opt-in population policy to the existing research lifecycle. The policy
will be evaluated once per lifecycle run and will use the already configured
research market IDs and aligned observation cadence.

The population service will:

- select no more than the configured per-run limit;
- call the Market Data replay endpoint without a semantic identity to force
  canonical reconstruction;
- accept only an available, trusted response with complete provenance;
- derive the existing deterministic semantic identity;
- call the internal retention promotion endpoint with the replay response;
- record bounded success, skip, and failure outcomes without failing the research
  lifecycle itself.

## Files Expected

- `market-intelligence/.../config/ResearchLifecycleProperties.java`
  - add population configuration and validation;
- `market-intelligence/.../adapter/marketdata/OrderBookReplayClient.java`
  - add a provider-neutral promotion client contract or request model;
- `market-intelligence/.../application/research/ResearchArtifactPopulationService.java`
  - implement bounded canonical replay and promotion orchestration;
- `market-intelligence/.../application/research/ResearchLifecycleService.java`
  - invoke population at the approved lifecycle boundary;
- `market-intelligence/src/main/resources/application.properties`
  - add disabled-by-default environment-backed properties;
- focused Market Intelligence tests and any contract tests required for the client.

No schema change is expected. No public API change is expected. No change to
canonical replay or artifact validation ownership is expected.

## Policy Defaults

- Population disabled by default.
- Population depth uses the existing readiness replay depth unless explicitly
  configured.
- Maximum promotions per lifecycle run is small and positive when enabled.
- Promotion reason is required and identifies the operational research lifecycle.
- The semantic identity is the existing `OrderBookReplayIdentity` value.

## Validation Sequence

1. Run focused configuration, population, and lifecycle unit tests.
2. Run the relevant Market Intelligence Maven module tests.
3. Run the relevant Market Data security and retention tests.
4. Start the local runtime with population explicitly enabled and verify the
   authorized promotion request and artifact inventory.
5. Measure authenticated `/research` cold/warm and fallback behavior before and
   after population.
6. Run `git diff --check` and inspect the complete diff.
