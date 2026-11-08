# MCP Research Integration Assessment

**Date:** 2026-11-08
**Mode:** read-only repository audit

## Verdict

The MCP adapter has the correct product shape for bounded research: it is a local stdio anti-corruption layer, calls only Gateway-backed public APIs, requires stable idempotency for research creation, and exposes status/report reads without execution authority. The integration is not end-to-end authorized for agents yet.

## Exposed Tools

`mcp-adapter/app/server.py` exposes:

- `start_market_research`: exactly two distinct canonical market UUIDs, bounded objective, `STANDARD`/`DEEP`, required idempotency key;
- `get_research_status`;
- `get_research_report`;
- separate operation-request tools for account synchronization, which are outside the research workflow.

The research tools call `/api/v1/intelligence/research` and its status/report subpaths. The adapter does not receive provider credentials, call internal services, execute tools inside Python, or place trades.

## Authorization Finding

Trading Core defines `AgentPermission.RESEARCH_RUN("research:run")` and maps it to `RESEARCH_SCOPE` in `AgentGrantCompatibility`. This establishes intended authorization vocabulary.

However, `MarketIntelligenceController` calls `actorId(authentication)`, which rejects any principal that is not `MiUserPrincipal`. It then passes that user ID to `ResearchApplicationService.start` and `findOwned`. Therefore the repository does not prove that a Gateway-authenticated `AgentPrincipal` can start or read a research execution. The permission exists, but the downstream Market Intelligence boundary has not been integrated with it.

## Security Boundary Assessment

| Boundary | Assessment |
|---|---|
| Broker credentials | Not exposed to MCP research tools |
| Trade execution | Not exposed by research tools |
| Risk authority | Remains outside research/MCP |
| Idempotency | Explicitly required and forwarded |
| Ownership | Human actor ownership is implemented |
| Agent grant enforcement | Present in Trading Core vocabulary, absent from research controller path |
| Report advisory status | Explicit in MCP docstring and report contract |
| Provider isolation | Python uses Gateway API, not provider APIs |

## Required Integration Work

The next implementation story should define, not infer:

- how an agent research request maps to owner scope and research scope;
- which exact `AgentGrant` resource is checked;
- whether research reads require `research:run` or a separate read permission;
- how the delegated agent identity and correlation ID are recorded;
- whether user-owned research may be read by the owning agent;
- how Gateway and Market Intelligence propagate the validated principal;
- how unauthorized and expired grants are represented without leaking research existence.

No change should bypass the existing default-deny agent model or derive authority from client-controlled delegated headers.
