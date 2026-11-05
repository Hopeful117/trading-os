from __future__ import annotations

from typing import Annotated, Literal
from uuid import UUID

from mcp.server import MCPServer
from pydantic import Field

from app.client import client_from_environment


mcp = MCPServer("trading-os-read-only")


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


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
