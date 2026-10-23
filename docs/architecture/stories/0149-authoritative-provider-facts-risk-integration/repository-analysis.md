# Story 0149 - Repository Analysis

## Status

`RETROSPECTIVE CLOSURE ARTIFACT`

This artifact records the repository evidence for the already implemented
integration. The user explicitly authorized creation of the missing Story and
closure artifacts after implementation.

## Repository Baseline

| Item | Current value |
|---|---|
| Branch | `main` |
| HEAD | `4c0437557add01de299c491d3394bc5b259a57ab` |
| origin/main | `4c0437557add01de299c491d3394bc5b259a57ab` |
| Latest merged Story | Story 0148, PR #166 |
| Latest relevant ADR | ADR-074, cTrader authoritative READ-ONLY synchronization |
| Story created | `0149` |
| Working tree | Two tracked Trading Core modifications plus nine pre-existing untracked reports |
| DevLog | Applicability check attempted; unavailable because the configured provider was rate-limited |

## Current Architecture

Story 0148 exposes `BrokerAccountSnapshotV2` through the authenticated Broker
Service v2 endpoint. Its cTrader implementation provides account facts,
positions, orders, bounded deals, observation timestamps, completeness,
freshness and unavailability reasons.

Trading Core already owns `ModeAwareRiskFactsProvider`, which selects local
paper facts or live Broker Service facts before assembling the existing Risk
Domain `AccountSnapshot`, `PortfolioSnapshot` and `RiskEvaluationContext`.

The integration therefore extends that existing provider boundary rather than
creating another Risk architecture.

## Fact Availability Matrix

| Fact | Available | Authority / semantics |
|---|---|---|
| Account identity | Yes | Local broker account, opaque provider reference and bound external ID |
| Currency / valuation asset | Yes | Provider-normalized account fact |
| Balance | Yes | Provider-authoritative |
| Equity | Yes | Provider-authoritative; cTrader adapter composes it from balance and net unrealized P&L |
| Used margin | Yes | Nullable provider fact; required by Risk mapping |
| Free margin | Yes | Nullable provider fact; preserved in v2 but not currently consumed by Risk Domain |
| Positions | Yes | Provider references, instrument, signed quantity, entry price |
| Orders | Yes | Provider-neutral v2 snapshot, not currently used by Risk mapping |
| Deals / fills | Yes | Bounded, timestamped deals with fee, swap and realized P&L |
| Realized P&L | Yes | Deal facts, restricted to the requested Risk interval |
| Unrealized P&L | Indirect | Used by cTrader adapter to compose equity; no separate Risk fact field |
| Commission / swap | Yes | Deal fee and swap fields where supplied |
| Observation timestamps | Yes | Provider observation, start and completion timestamps |
| Completeness / freshness | Yes | `COMPLETE` and `FRESH` are required for current Risk input |
| Unavailability reasons | Yes | Preserved as provider-neutral reasons |

## Risk Boundary

`ModeAwareRiskFactsProvider` is the live/paper application boundary. The
existing deterministic Risk services and `risk-domain` remain unchanged.
Provider Service remains responsible for provider facts; Trading Core remains
responsible for deterministic interpretation and authorization.

The current v2 position contract does not carry provider margin or protective
stop evidence. Such snapshots are deliberately marked incomplete when positions
are present, preventing Risk approval rather than inferring missing values.

## Decision

Use the existing provider-neutral v2 snapshot as the input boundary. Do not add
FTMO rules, cTrader types, provider enums, or a second Risk snapshot model.
