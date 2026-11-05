# ADR-088 - MCP-First Agent Interaction Boundary

## Status

Accepted - human architecture approval completed

## Date

2026-11-05

## Decision Type

Agent interaction boundary and read-only MCP exposure

## Related ADRs

- ADR-020 - Market Intelligence Architecture
- ADR-021 - Governance of Market Intelligence Analysis Executions
- ADR-028 - Deterministic Risk Engine Architecture
- ADR-030 - Broker Architecture
- ADR-034 - Strategy, StrategyMatch and Trading Opportunity Boundaries
- ADR-073 - cTrader Authentication and Explicit External Account Binding
- ADR-074 - cTrader Authoritative READ-ONLY Synchronization Boundary
- ADR-077 - cTrader Market Data Transport Boundary
- ADR-076 - Market Thesis Authority and Evidence Composition

## 1. Context

Trading OS already exposes useful, provider-neutral public contracts through the
Gateway for markets, Market Thesis, opportunities and analysis state. Agent
clients need a reusable interaction boundary that is not tied to Angular or to a
single model vendor. MCP is a suitable protocol boundary for this purpose, but
it must not become a second business architecture or a path around existing
authorization and domain ownership.

The current service surface is heterogeneous. Market Intelligence has
authenticated user-facing reads and service-authenticated `/internal/**`
endpoints. Market Data exposes public market reads and protected mutations.
Trading Core and Broker Service own account, risk and execution authority.

## 2. Proposed Decision

Trading OS SHOULD expose agent capabilities through a separate MCP anti-corruption
layer. The adapter SHALL call only stable public Gateway contracts and SHALL map
their responses to explicit MCP tools/resources. It SHALL NOT call repositories,
provider APIs, `/internal/**` endpoints, or service-to-service contracts.

The first transport SHOULD be local stdio, launched by the MCP client as a child
process. A remote Streamable HTTP MCP endpoint is deferred until a dedicated
OAuth/resource-server boundary and deployment policy are approved.

The initial read-only capability set is:

| MCP tool | Gateway contract | Authority |
|---|---|---|
| `list_markets` | `GET /api/v1/markets` | Market Data |
| `get_market_thesis` | `GET /api/v1/intelligence/market-theses/latest` | Market Intelligence |
| `get_opportunities` | `GET /api/v1/opportunities` | Market Intelligence |

The adapter SHALL preserve source identifiers, state, freshness, provenance,
missingness and contradictions. It SHALL not translate `CANDIDATE` into a
recommendation, score into probability, or a market read into an execution
instruction.

## 3. Security and Trust Model

- Local stdio is the initial trust boundary; the client process supplies a
  short-lived Trading OS user JWT through `TRADING_OS_JWT`.
- The JWT is sent only as an HTTPS `Authorization: Bearer` header when the
  configured Gateway URL is remote. It is never returned as tool output or
  placed in query parameters.
- The adapter is read-only by construction. No write, plan, risk, credential,
  broker or execution tool is registered.
- The Gateway remains the authentication and routing boundary. Existing service
  ownership and user ownership checks remain authoritative.
- A future remote MCP server MUST use MCP HTTP authorization semantics and a
  separately approved token audience/scope model. A user JWT pass-through is not
  an accepted remote design by default.

## 4. cTrader and External MCP Integrations

An external cTrader MCP server, if evaluated later, SHALL be treated as an
untrusted provider integration. It must not receive Trading OS credentials,
broker tokens, refresh tokens, or direct access to Trading OS services.

cTrader market facts remain owned by Market Data under ADR-077. cTrader account
and execution facts remain owned by Broker Service under ADR-073/074. Any future
MCP exposure must consume provider-neutral Trading OS contracts, not cTrader
DTOs or external MCP payloads as business truth. No external MCP server is
installed or connected by this ADR.

## 5. Alternatives

### Application-first

Rejected as the primary interaction boundary. It keeps the frontend central and
duplicates agent integration logic across clients.

### MCP-first through direct service access

Rejected. It bypasses Gateway authentication, duplicates domain authorization,
and risks exposing internal contracts or provider credentials.

### MCP endpoint inside Market Intelligence

Deferred. It couples a protocol adapter to one domain service and would require
remote authorization and lifecycle concerns in that service.

### Remote MCP first

Deferred. It offers broader deployment reach but requires an approved OAuth
resource-server model, tenant/session policy, rate limits and operational
hardening that are not currently established.

## 6. Consequences

Positive:

- OpenCode and other MCP clients can reuse the same capability boundary.
- Existing Gateway, ownership and domain authorities remain in control.
- Local prototyping does not require a new deployed microservice.
- Read-only agent interaction can be validated without broker or execution risk.

Costs and risks:

- The adapter must track versioned public contracts.
- Tool schemas need explicit limits and output-size controls.
- Local JWT handling requires operator discipline until a dedicated MCP auth
  boundary exists.
- MCP capability naming becomes an integration contract.

## 7. Scope Boundary

This proposal does not authorize:

- order placement, cancellation, position mutation or execution;
- TradePlan creation or approval;
- Risk evaluation or risk override;
- cTrader connection or external MCP installation;
- direct AI Engine access;
- public remote MCP deployment;
- changes to existing service ownership or Gateway security.

## 8. Approval Gate

```text
ADR-088_STATUS = ACCEPTED
HUMAN_ARCHITECTURE_APPROVAL = COMPLETED
REMOTE_MCP_DEPLOYMENT = NOT_AUTHORIZED
EXTERNAL_MCP_INSTALLATION = NOT_AUTHORIZED
READ_ONLY_LOCAL_STDIO_PROTOTYPE = IMPLEMENTED_FOR_REVIEW
```
