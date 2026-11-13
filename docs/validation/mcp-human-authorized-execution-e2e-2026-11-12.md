# MCP Human-Authorized Execution E2E Validation

Date: 2026-11-12
Status: `BLOCKED`

## Scope

Validate the MCP human-authorized execution continuation path through the
Gateway and Trading Core without submitting a live broker order.

## Evidence Collected

| Area | Result |
| --- | --- |
| Trading Core compilation | Passed: `./mvnw -q -DskipTests compile` |
| Targeted authorization/security tests | Passed: feature flag, CSRF matcher, Origin filter, JWT boundary |
| Full Trading Core test suite | Not validated: command exceeded the 120 second execution window |
| Gateway without credentials | `401` |
| Trading Core without credentials | `403` |
| Broker Service health | `200` |
| Trading Web health | `200` |
| MCP server registration | 28 tools registered |
| MCP `list_markets` | Reached Gateway and failed closed with `401`; no agent credential available |
| cTrader read-only account state | Balance/equity read; no open positions or pending orders observed |
| cTrader asset catalogue | Asset `15` resolves to `USD` |
| Broker mutations | None performed |

## Isolated Runtime Progress

| Area | Result |
| --- | --- |
| Isolated Compose runtime | Started with dedicated project, ports, networks and volumes |
| Trading Core current image | Rebuilt and restarted from the working tree |
| Flyway migration `V68` | Passed; execution authorization permissions constraint applied |
| Synthetic human registration/login | Passed through the isolated Gateway |
| Synthetic agent credential exchange | Passed; short-lived agent token issued |
| `accounts:read` / `positions:read` grants | Passed and persisted |
| Execution authorization grants | Passed after `V68`: request, continue-approved, status-read |
| Agent broker-account read | Passed (`200`) |
| Agent authorization request boundary | Reached execution authorization service; failed at trade-plan loading because Market Intelligence is not running |
| Controlled broker account connection | Blocked; account remains `CREATED`, so broker observations require connection setup |

## Runtime Blockers

The original runtime blockers have been removed in the isolated environment.
The remaining blockers are:

- The production/default runtime was not modified; only the isolated runtime has
  migrations `V67` and `V68` and the execution-authorization feature flag.
- The isolated controlled broker account is not connected yet, so broker-backed
  read-only status and positions are unavailable.
- The isolated Compose topology does not currently include Market Intelligence,
  so an execution authorization request cannot load a valid Trade Plan from the
  configured orchestration path.
- No real human approval was requested or performed.
- No broker mutation or `ExecuteTradeService` continuation has been performed.

## Safety Result

No order was created, amended, cancelled, or closed. No broker-side mutation
or financial side effect was observed.

## Verdict

`BLOCKED`: the local source implementation and targeted tests are available,
but the active runtime has not been migrated or rebuilt with the feature, and
the authenticated MCP and human-approval prerequisites are unavailable.

## Required Follow-Up

1. Complete the controlled broker account connection through the supported
   application contract, or add the required isolated connection path.
2. Include a current Market Intelligence instance in the isolated topology, or
   create a valid manual Trade Plan through an approved application contract.
3. Evaluate risk and request real human approval before continuation.
4. Validate the controlled broker accept/reject/unknown outcomes and idempotency.

## Selected Isolated Approach

The active Docker Compose project and its named volumes will not be modified.
The E2E runtime will use a separate Compose project name, dedicated host ports,
dedicated database volumes, a current-source Trading Core image, and an
`e2e-controlled-broker` profile. The controlled broker will implement the
existing broker-neutral boundary behind the Broker Service and will fail closed
if any external cTrader endpoint is configured. Synthetic users, agents,
grants, broker accounts, plans and risk data will be created through local
application contracts where available. No existing FTMO account or production
credential will be used.
