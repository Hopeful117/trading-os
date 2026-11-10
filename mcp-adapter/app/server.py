from __future__ import annotations

import json
import json
import os
from typing import Annotated, Literal
from uuid import UUID, uuid4
from urllib.parse import urlparse

from mcp.server import MCPServer
from pydantic import Field

from app.client import client_from_environment


mcp = MCPServer("trading-os-research")


@mcp.tool()
def list_markets(
    instrument: Annotated[str | None, Field(description="Optional canonical or provider instrument filter.")] = None,
) -> str:
    """List canonical markets through the public Trading OS Gateway."""
    return client_from_environment().get(
        "/api/v1/markets", {"instrument": instrument} if instrument is not None else None
    )


@mcp.tool()
def get_active_scan(scan_id: Annotated[UUID, Field(description="Trading OS active scan UUID.")]) -> str:
    """Read an authorized active-scan projection without starting or changing a scan."""
    return client_from_environment().get(f"/api/v1/intelligence/scans/{scan_id}")


@mcp.tool()
def start_market_scan(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID used for scan context.")],
    objective: Annotated[str, Field(min_length=1, max_length=500, description="Bounded scan objective.")],
    market_ids: Annotated[list[UUID], Field(min_length=1, max_length=50, description="Explicit canonical market UUIDs to scan.")],
    idempotency_key: Annotated[str, Field(min_length=1, max_length=200, description="Stable key reused only when retrying the same scan request.")],
) -> str:
    """Start an authorized Market Intelligence scan; this never creates a Trade Plan or executes an order."""
    if len(set(market_ids)) != len(market_ids):
        raise ValueError("market_ids must contain distinct UUIDs")
    client = client_from_environment()
    account = json.loads(client.get(f"/api/v1/agent/broker-accounts/{broker_account_id}/trading-account"))
    trading_account_id = account.get("accountId")
    if not isinstance(trading_account_id, str):
        raise ValueError("Authorized broker account has no Trading OS account")
    return client.post(
        "/api/v1/intelligence/scans",
        {
            "accountId": trading_account_id,
            "brokerAccountId": str(broker_account_id),
            "objective": objective,
            "requestedMarketIds": [str(value) for value in market_ids],
            "scopeMode": "SELECTED",
        },
        idempotency_key,
    )


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
def get_opportunity(
    opportunity_id: Annotated[UUID, Field(description="Trading OS opportunity UUID.")],
) -> str:
    """Read one opportunity with its supporting thesis and evidence."""
    return client_from_environment().get(f"/api/v1/opportunities/{opportunity_id}")


@mcp.tool()
def create_trade_plan_from_opportunity(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID.")],
    opportunity_id: Annotated[UUID, Field(description="Existing Trading OS opportunity UUID.")],
    opportunity_version: Annotated[int, Field(ge=1, description="Immutable opportunity version.")],
    idempotency_key: Annotated[str, Field(min_length=1, max_length=200,
                                          description="Stable key reused only when retrying the same request.")],
) -> str:
    """Create a Trade Plan from an existing opportunity; Trading Core owns planning and risk preflight."""
    return client_from_environment().post(
        f"/api/v1/agent/trade-plans/opportunities/{opportunity_id}",
        {"brokerAccountId": str(broker_account_id), "opportunityVersion": opportunity_version},
        idempotency_key,
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
def get_authorized_trade_plan(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID.")],
    trade_plan_id: Annotated[UUID, Field(description="Trade Plan UUID to inspect.")],
    trade_plan_version: Annotated[int, Field(ge=1, description="Immutable Trade Plan version.")],
) -> str:
    """Read one Trade Plan within an explicitly authorized broker-account scope."""
    return client_from_environment().get(
        f"/api/v1/agent/trade-plans/{trade_plan_id}/versions/{trade_plan_version}",
        {"accountId": str(broker_account_id)},
    )


@mcp.tool()
def evaluate_authorized_trade_plan_risk(
    trade_plan_id: Annotated[UUID, Field(description="Trade Plan UUID to evaluate.")],
    trade_plan_version: Annotated[int, Field(ge=1, description="Immutable Trade Plan version.")],
    idempotency_key: Annotated[str | None, Field(min_length=1, max_length=160,
                                                  description="Unique key for a genuinely new evaluation; reuse it only when retrying the same request.")] = None,
) -> str:
    """Request deterministic pre-trade risk evaluation using this agent's account-scoped grant."""
    key = idempotency_key or f"trade-plan-risk:{trade_plan_id}:{trade_plan_version}:{uuid4()}"
    return client_from_environment().post(
        f"/api/v1/agent/trade-plan-risk-evaluations/{trade_plan_id}/versions/{trade_plan_version}",
        {}, key,
    )


@mcp.tool()
def request_trade_plan_approval(
    trade_plan_id: Annotated[UUID, Field(description="Trade Plan UUID to submit for human approval.")],
    trade_plan_version: Annotated[int, Field(ge=1, description="Immutable Trade Plan version.")],
    risk_evaluation_id: Annotated[UUID, Field(description="Approved pre-trade risk evaluation UUID.")],
) -> str:
    """Create a short-lived, single-use human approval challenge; this never executes a trade."""
    response = client_from_environment().post(
        "/api/v1/agent/trade-plan-approvals",
        {"tradePlanId": str(trade_plan_id), "tradePlanVersion": trade_plan_version,
         "riskEvaluationId": str(risk_evaluation_id)},
        f"trade-plan-approval:{trade_plan_id}:{trade_plan_version}:{risk_evaluation_id}",
    )
    payload = json.loads(response)
    challenge_id = payload.get("challengeId")
    if not isinstance(challenge_id, str):
        raise ValueError("Gateway returned an invalid approval challenge")
    web_url = os.getenv("TRADING_OS_WEB_URL", "http://localhost:4200").rstrip("/")
    parsed = urlparse(web_url)
    if parsed.hostname not in {"127.0.0.1", "localhost", "::1"} and parsed.scheme != "https":
        raise ValueError("TRADING_OS_WEB_URL must use HTTPS outside localhost")
    payload["approvalUrl"] = f"{web_url}/trade-plan-approvals/{challenge_id}"
    return json.dumps(payload)


@mcp.tool()
def get_trade_plan_approval_status(
    challenge_id: Annotated[UUID, Field(description="Human approval challenge UUID.")],
) -> str:
    """Read the status of a human approval challenge; it does not approve or execute anything."""
    return client_from_environment().get(f"/api/v1/agent/trade-plan-approvals/{challenge_id}")


@mcp.tool()
def request_account_synchronization(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID.")],
) -> str:
    """Create a pending, human-approval-required account synchronization request."""
    return client_from_environment().post(
        "/api/v1/agent/operation-requests",
        {"operationType": "ACCOUNT_SYNCHRONIZATION", "resourceType": "BROKER_ACCOUNT", "resourceId": str(broker_account_id)},
        f"account-sync-request:{broker_account_id}",
    )


@mcp.tool()
def get_agent_operation_status(
    request_id: Annotated[UUID, Field(description="Agent operation request UUID.")],
) -> str:
    """Read the status of an operation requested by this agent."""
    return client_from_environment().get(f"/api/v1/agent/operation-requests/{request_id}")


@mcp.tool()
def execute_approved_agent_operation(
    request_id: Annotated[UUID, Field(description="Approved agent operation request UUID.")],
) -> str:
    """Execute one approved operation; POST failures are never retried."""
    return client_from_environment().post(
        f"/api/v1/agent/operation-requests/{request_id}/execute", {}, f"agent-operation-execute:{request_id}"
    )


@mcp.tool()
def start_market_research(
    scope_id: Annotated[UUID, Field(description="Explicit Trading OS research-scope UUID.")],
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
        {"scopeId": str(scope_id), "marketIds": [str(value) for value in market_ids], "objective": objective, "depth": depth},
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


@mcp.tool()
def inspect_historical_research_readiness(
    broker_account_id: Annotated[UUID, Field(description="Authorized broker-account UUID.")],
    market_ids: Annotated[list[UUID], Field(min_length=1, max_length=2, description="One or two canonical market UUIDs in the authorized account universe.")],
    cutoff_start: Annotated[str, Field(min_length=1, max_length=40, description="Inclusive UTC ISO-8601 evaluation cutoff.")],
    cutoff_end: Annotated[str, Field(min_length=1, max_length=40, description="Exclusive UTC ISO-8601 evaluation cutoff.")],
    dataset_profile: Annotated[str, Field(min_length=1, max_length=128, description="Bounded dataset profile/version.")],
    max_observations: Annotated[int, Field(ge=1, le=100_000, description="Maximum observations to inspect.")] = 10_000,
) -> str:
    """Inspect deterministic historical readiness through the public Gateway."""
    if len(market_ids) not in {1, 2} or len(set(market_ids)) != len(market_ids):
        raise ValueError("market_ids must contain one or two distinct UUIDs")
    return client_from_environment().post(
        "/api/v1/intelligence/research/readiness",
        {
            "brokerAccountId": str(broker_account_id),
            "marketIds": [str(value) for value in market_ids],
            "cutoffStart": cutoff_start,
            "cutoffEnd": cutoff_end,
            "datasetProfile": dataset_profile,
            "maxObservations": max_observations,
        },
        f"research-readiness:{broker_account_id}:{cutoff_start}:{cutoff_end}:{dataset_profile}",
    )


@mcp.tool()
def request_historical_dataset_export(
    scope_id: Annotated[UUID, Field(description="Explicit Trading OS historical research-scope UUID.")],
    population: Annotated[dict[str, object], Field(description="Validated ResearchPopulationSpec JSON object.")],
    max_rows: Annotated[int, Field(ge=1, le=100_000, description="Maximum exported rows.")] = 100_000,
    max_bytes: Annotated[int, Field(ge=1, le=100 * 1024 * 1024, description="Maximum exported bytes.")] = 100 * 1024 * 1024,
    max_duration_seconds: Annotated[int, Field(ge=1, le=3600, description="Maximum export duration in seconds.")] = 900,
    idempotency_key: Annotated[str, Field(min_length=1, max_length=200, description="Stable key reused for retries.")] = "",
) -> str:
    """Request a bounded offline dataset export; the dataset is not returned by MCP."""
    if not idempotency_key.strip():
        raise ValueError("idempotency_key is required and must be reused for retries")
    if not isinstance(population, dict) or len(population) > 32:
        raise ValueError("population must be a bounded object")
    return client_from_environment().post(
        f"/api/v1/intelligence/research/{scope_id}/exports",
        {"population": population, "maxRows": max_rows, "maxBytes": max_bytes,
         "maxDurationSeconds": max_duration_seconds},
        idempotency_key,
    )


@mcp.tool()
def get_historical_dataset_export_status(
    export_id: Annotated[UUID, Field(description="Historical dataset export job UUID.")],
) -> str:
    """Read authorized export lifecycle and artifact metadata without filesystem paths or dataset rows."""
    return client_from_environment().get(f"/api/v1/intelligence/research/exports/{export_id}")


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
