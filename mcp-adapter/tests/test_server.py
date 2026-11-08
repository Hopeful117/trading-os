from __future__ import annotations

import json
import sys

import httpx
import pytest
from mcp import Client, StdioServerParameters

from app import server
from app.client import GatewayClientError


class FakeClient:
    def __init__(self):
        self.calls: list[tuple[str, dict | None]] = []
        self.post_calls: list[tuple[str, dict, str]] = []
        self.response = '{"source":"gateway"}'
        self.error: GatewayClientError | None = None

    def get(self, path: str, params: dict | None = None) -> str:
        self.calls.append((path, params))
        if self.error is not None:
            raise self.error
        return self.response

    def post(self, path: str, payload: dict, idempotency_key: str) -> str:
        self.post_calls.append((path, payload, idempotency_key))
        if self.error is not None:
            raise self.error
        return self.response


@pytest.fixture
def fake_client(monkeypatch):
    client = FakeClient()
    monkeypatch.setattr(server, "client_from_environment", lambda: client)
    return client


@pytest.mark.asyncio
async def test_server_exposes_bounded_tools(fake_client):
    async with Client(server.mcp, raise_exceptions=True) as client:
        tools = await client.list_tools()

    assert {tool.name for tool in tools.tools} == {
        "list_markets",
        "get_active_scan",
        "get_market_thesis",
        "get_opportunities",
        "list_authorized_accounts",
        "get_authorized_account",
        "get_authorized_account_status",
        "get_authorized_positions",
        "start_market_research",
        "get_research_status",
        "get_research_report",
        "request_account_synchronization",
        "get_agent_operation_status",
        "execute_approved_agent_operation",
    }


@pytest.mark.asyncio
async def test_tools_call_only_approved_gateway_reads(fake_client):
    async with Client(server.mcp, raise_exceptions=True) as client:
        await client.call_tool("list_markets", {})
        await client.call_tool(
            "get_active_scan",
            {"scan_id": "00000000-0000-0000-0000-000000000002"},
        )
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
        await client.call_tool("list_authorized_accounts", {})
        await client.call_tool(
            "get_authorized_account",
            {"broker_account_id": "00000000-0000-0000-0000-000000000004"},
        )
        await client.call_tool(
            "get_authorized_account_status",
            {"broker_account_id": "00000000-0000-0000-0000-000000000004"},
        )
        await client.call_tool(
            "get_authorized_positions",
            {"broker_account_id": "00000000-0000-0000-0000-000000000004"},
        )
        await client.call_tool(
            "request_account_synchronization",
            {"broker_account_id": "00000000-0000-0000-0000-000000000004"},
        )
        await client.call_tool(
            "get_agent_operation_status",
            {"request_id": "00000000-0000-0000-0000-000000000005"},
        )
        await client.call_tool(
            "execute_approved_agent_operation",
            {"request_id": "00000000-0000-0000-0000-000000000005"},
        )
        await client.call_tool(
            "start_market_research",
            {
                "scope_id": "00000000-0000-0000-0000-000000000006",
                "market_ids": [
                    "00000000-0000-0000-0000-000000000001",
                    "00000000-0000-0000-0000-000000000002",
                ],
                "objective": "Compare BTC and ETH",
                "depth": "STANDARD",
                "idempotency_key": "research-btc-eth-1",
            },
        )
        await client.call_tool(
            "get_research_status",
            {"research_id": "00000000-0000-0000-0000-000000000003"},
        )
        await client.call_tool(
            "get_research_report",
            {"research_id": "00000000-0000-0000-0000-000000000003"},
        )

    assert [path for path, _ in fake_client.calls] == [
        "/api/v1/markets",
        "/api/v1/intelligence/scans/00000000-0000-0000-0000-000000000002",
        "/api/v1/intelligence/market-theses/latest",
        "/api/v1/opportunities",
        "/api/v1/agent/broker-accounts",
        "/api/v1/agent/broker-accounts/00000000-0000-0000-0000-000000000004",
        "/api/v1/agent/broker-accounts/00000000-0000-0000-0000-000000000004/read-only-status",
        "/api/v1/agent/broker-accounts/00000000-0000-0000-0000-000000000004/positions",
        "/api/v1/agent/operation-requests/00000000-0000-0000-0000-000000000005",
        "/api/v1/intelligence/research/00000000-0000-0000-0000-000000000003",
        "/api/v1/intelligence/research/00000000-0000-0000-0000-000000000003/report",
    ]
    assert fake_client.calls[3][1]["size"] == 10
    assert fake_client.post_calls[0][0] == "/api/v1/agent/operation-requests"
    assert fake_client.post_calls[1][0].endswith("/execute")
    assert fake_client.post_calls[2][0] == "/api/v1/intelligence/research"
    assert fake_client.post_calls[2][1]["scopeId"] == "00000000-0000-0000-0000-000000000006"
    assert fake_client.post_calls[2][2] == "research-btc-eth-1"


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
async def test_tool_schema_rejects_invalid_scan_id(fake_client):
    async with Client(server.mcp, raise_exceptions=True) as client:
        result = await client.call_tool("get_active_scan", {"scan_id": "BTC/USD"})

    assert result.is_error is True
    assert fake_client.calls == []


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "status",
    ["RUNNING", "COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"],
)
async def test_active_scan_preserves_status_and_response_payload(fake_client, status):
    payload = {
        "scanId": "00000000-0000-0000-0000-000000000002",
        "status": status,
        "progress": {"completed": 1, "failed": 0},
        "markets": [
            {
                "marketId": "00000000-0000-0000-0000-000000000003",
                "provider": "KRAKEN",
                "productType": "SPOT",
                "analysisStatus": "COMPLETED",
                "resultQuality": "PARTIAL",
                "diagnostic": {"code": "MISSING_VOLUME", "message": "not available"},
                "freshness": "STALE",
                "provenance": {"source": "market-intelligence", "ruleVersion": "v1"},
                "contradictions": [{"code": "TIMEFRAME_DISAGREEMENT"}],
                "missingEvidence": [{"code": "VOLUME", "status": "NOT_IMPLEMENTED"}],
                "opportunities": [],
            }
        ],
    }
    fake_client.response = json.dumps(payload, separators=(",", ":"))

    async with Client(server.mcp, raise_exceptions=True) as client:
        result = await client.call_tool(
            "get_active_scan",
            {"scan_id": "00000000-0000-0000-0000-000000000002"},
        )

    assert result.is_error is False
    assert result.content[0].text == fake_client.response


@pytest.mark.asyncio
async def test_active_scan_preserves_empty_and_incomplete_projection(fake_client):
    fake_client.response = json.dumps(
        {
            "scanId": "00000000-0000-0000-0000-000000000002",
            "status": "COMPLETED",
            "resultQuality": "PARTIAL",
            "progress": {"totalCandidates": 2, "completed": 1, "failed": 1},
            "markets": [],
            "missingEvidence": [{"marketId": "unknown", "status": "UNAVAILABLE"}],
        },
        separators=(",", ":"),
    )

    async with Client(server.mcp, raise_exceptions=True) as client:
        result = await client.call_tool(
            "get_active_scan",
            {"scan_id": "00000000-0000-0000-0000-000000000002"},
        )

    assert result.is_error is False
    assert result.content[0].text == fake_client.response


@pytest.mark.asyncio
@pytest.mark.parametrize("status_code", [403, 404])
async def test_active_scan_ownership_denial_does_not_expose_payload(fake_client, status_code):
    fake_client.error = GatewayClientError(
        f"Gateway request failed with HTTP {status_code}"
    )

    async with Client(server.mcp, raise_exceptions=False) as client:
        result = await client.call_tool(
            "get_active_scan",
            {"scan_id": "00000000-0000-0000-0000-000000000002"},
        )

    assert result.is_error is True
    assert result.content[0].text == "Error executing tool get_active_scan"
    assert "accountId" not in result.content[0].text
    assert "secret" not in result.content[0].text


@pytest.mark.asyncio
@pytest.mark.parametrize("status_code", [403, 404])
async def test_active_scan_http_ownership_denial_is_sanitized(monkeypatch, status_code):
    class Stream:
        def __enter__(self):
            return httpx.Response(
                status_code,
                content=b'{"accountId":"private","secret":"must not escape"}',
                request=httpx.Request(
                    "GET", "http://127.0.0.1/api/v1/intelligence/scans/scan"
                ),
            )

        def __exit__(self, *args):
            return False

    monkeypatch.setenv("TRADING_OS_AGENT_CREDENTIAL", "test-credential")
    monkeypatch.setattr(httpx, "stream", lambda *args, **kwargs: Stream())

    async with Client(server.mcp, raise_exceptions=False) as client:
        result = await client.call_tool(
            "get_active_scan",
            {"scan_id": "00000000-0000-0000-0000-000000000002"},
        )

    assert result.is_error is True
    assert result.content[0].text == "Error executing tool get_active_scan"
    assert "private" not in result.content[0].text
    assert "must not escape" not in result.content[0].text


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
        "get_active_scan",
        "get_market_thesis",
        "get_opportunities",
        "list_authorized_accounts",
        "get_authorized_account",
        "get_authorized_account_status",
        "get_authorized_positions",
        "start_market_research",
        "get_research_status",
        "get_research_report",
        "request_account_synchronization",
        "get_agent_operation_status",
        "execute_approved_agent_operation",
    }
