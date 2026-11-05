from __future__ import annotations

import sys

import pytest
from mcp import Client, StdioServerParameters

from app import server


class FakeClient:
    def __init__(self):
        self.calls: list[tuple[str, dict | None]] = []

    def get(self, path: str, params: dict | None = None) -> str:
        self.calls.append((path, params))
        return '{"source":"gateway"}'


@pytest.fixture
def fake_client(monkeypatch):
    client = FakeClient()
    monkeypatch.setattr(server, "client_from_environment", lambda: client)
    return client


@pytest.mark.asyncio
async def test_server_exposes_exactly_the_read_only_tools(fake_client):
    async with Client(server.mcp, raise_exceptions=True) as client:
        tools = await client.list_tools()

    assert {tool.name for tool in tools.tools} == {
        "list_markets",
        "get_market_thesis",
        "get_opportunities",
    }


@pytest.mark.asyncio
async def test_tools_call_only_approved_gateway_reads(fake_client):
    async with Client(server.mcp, raise_exceptions=True) as client:
        await client.call_tool("list_markets", {})
        await client.call_tool(
            "get_market_thesis",
            {
                "market_id": "00000000-0000-0000-0000-000000000001",
                "instrument": "BTC/USD",
                "direction": "LONG",
            },
        )
        await client.call_tool(
            "get_opportunities",
            {"instrument": "BTC/USD", "active_only": True, "limit": 10},
        )

    assert [path for path, _ in fake_client.calls] == [
        "/api/v1/markets",
        "/api/v1/intelligence/market-theses/latest",
        "/api/v1/opportunities",
    ]
    assert fake_client.calls[2][1]["size"] == 10


@pytest.mark.asyncio
async def test_tool_schema_rejects_out_of_range_opportunity_limit(fake_client):
    async with Client(server.mcp, raise_exceptions=True) as client:
        result = await client.call_tool("get_opportunities", {"limit": 51})

    assert result.is_error is True
    assert fake_client.calls == []


@pytest.mark.asyncio
async def test_tool_schema_rejects_invalid_direction(fake_client):
    async with Client(server.mcp, raise_exceptions=True) as client:
        result = await client.call_tool(
            "get_market_thesis",
            {
                "market_id": "00000000-0000-0000-0000-000000000001",
                "instrument": "BTC/USD",
                "direction": "BUY",
            },
        )

    assert result.is_error is True
    assert fake_client.calls == []


@pytest.mark.asyncio
async def test_server_starts_over_stdio():
    parameters = StdioServerParameters(
        command=sys.executable,
        args=["-m", "app.server"],
    )

    async with Client(parameters) as client:
        tools = await client.list_tools()

    assert {tool.name for tool in tools.tools} == {
        "list_markets",
        "get_market_thesis",
        "get_opportunities",
    }
