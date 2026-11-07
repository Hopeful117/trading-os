from __future__ import annotations

from typing import Annotated, Literal
from uuid import UUID

from mcp.server import MCPServer
from pydantic import Field

from app.client import client_from_environment


mcp = MCPServer("trading-os-research")


@mcp.tool()
def list_markets() -> str:
    """List canonical markets through the public Trading OS Gateway."""
    return client_from_environment().get("/api/v1/markets")


@mcp.tool()
def get_active_scan(scan_id: Annotated[UUID, Field(description="Trading OS active scan UUID.")]) -> str:
    """Read an authorized active-scan projection without starting or changing a scan."""
    return client_from_environment().get(f"/api/v1/intelligence/scans/{scan_id}")


@mcp.tool()
def get_market_thesis(
    market_id: Annotated[UUID, Field(description="Trading OS market UUID.")],
    instrument: Annotated[str, Field(description="Canonical instrument identifier.")],
    direction: Annotated[
        Literal["LONG", "SHORT", "NONE"],
        Field(description="Market Thesis direction."),
    ],
) -> str:
    """Read the latest deterministic Market Thesis for a market."""
    return client_from_environment().get(
        "/api/v1/intelligence/market-theses/latest",
        {"marketId": str(market_id), "instrument": instrument, "direction": direction},
    )


@mcp.tool()
def get_opportunities(
    instrument: Annotated[str | None, Field(description="Optional instrument filter.")] = None,
    active_only: Annotated[bool, Field(description="Return only ACTIVE opportunities.")] = True,
    limit: Annotated[int, Field(ge=1, le=50, description="Maximum result page size.")] = 20,
) -> str:
    """Read trader-facing opportunities without creating plans or executing trades."""
    if limit < 1 or limit > 50:
        raise ValueError("limit must be between 1 and 50")
    params = {
        "instrument": instrument,
        "activeOnly": active_only,
        "page": 0,
        "size": limit,
        "sort": "createdAt",
        "direction": "desc",
    }
    return client_from_environment().get(
        "/api/v1/opportunities", {key: value for key, value in params.items() if value is not None}
    )


@mcp.tool()
def list_authorized_accounts() -> str:
    """List only broker accounts explicitly granted to this agent."""
    return client_from_environment().get("/api/v1/agent/broker-accounts")


@mcp.tool()
def get_authorized_account(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID.")],
) -> str:
    """Read one broker account explicitly granted to this agent."""
    return client_from_environment().get(f"/api/v1/agent/broker-accounts/{broker_account_id}")


@mcp.tool()
def get_authorized_account_status(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID.")],
) -> str:
    """Read synchronization/read-only status for an authorized broker account."""
    return client_from_environment().get(
        f"/api/v1/agent/broker-accounts/{broker_account_id}/read-only-status"
    )


@mcp.tool()
def get_authorized_positions(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID.")],
) -> str:
    """Read positions for a broker account explicitly granted to this agent."""
    return client_from_environment().get(
        f"/api/v1/agent/broker-accounts/{broker_account_id}/positions"
    )


@mcp.tool()
def start_market_research(
    market_ids: Annotated[list[UUID], Field(min_length=2, max_length=2, description="Exactly two canonical market UUIDs.")],
    objective: Annotated[str, Field(min_length=1, max_length=500, description="Bounded research objective.")],
    depth: Annotated[Literal["STANDARD", "DEEP"], Field(description="Research depth budget.")] = "STANDARD",
    idempotency_key: Annotated[str, Field(min_length=1, max_length=200, description="Stable key reused for retries.")] = "",
) -> str:
    """Start a bounded Trading OS-owned research workflow; this never authorizes a trade."""
    if len(market_ids) != 2 or market_ids[0] == market_ids[1]:
        raise ValueError("market_ids must contain exactly two distinct UUIDs")
    if not idempotency_key.strip():
        raise ValueError("idempotency_key is required and must be reused for retries")
    return client_from_environment().post(
        "/api/v1/intelligence/research",
        {"marketIds": [str(value) for value in market_ids], "objective": objective, "depth": depth},
        idempotency_key,
    )


@mcp.tool()
def get_research_status(
    research_id: Annotated[UUID, Field(description="Trading OS research UUID.")],
) -> str:
    """Read the status of an owned Trading OS research workflow."""
    return client_from_environment().get(f"/api/v1/intelligence/research/{research_id}")


@mcp.tool()
def get_research_report(
    research_id: Annotated[UUID, Field(description="Trading OS research UUID.")],
) -> str:
    """Read the structured advisory research report without any execution authority."""
    return client_from_environment().get(f"/api/v1/intelligence/research/{research_id}/report")


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
