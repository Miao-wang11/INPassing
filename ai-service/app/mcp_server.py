"""Per-request MCP stdio server: real protocol, authenticated read-only Java adapters.

Launch only through the trusted FastAPI agent. The MCP principal is never a model
tool argument, and a fresh subprocess is used per request to prevent actor mixing.
"""
import json
import os

import httpx
from mcp.server.fastmcp import FastMCP

from app.agent.route_matching import WalkNetwork, _normalize
from app.clients.java_backend import JavaReadOnlyClient
from app.config import Settings
from app.security import WireSecurity
from app.tools.read_only import TaskCard, safe_validate


def build_server(settings, actor, *, origin="", destination=""):
    server = FastMCP("shunshou-campus-readonly")

    async def read(path, params=None):
        async with httpx.AsyncClient(
            base_url=settings.java_base_url.rstrip("/") + "/",
            timeout=settings.request_timeout_seconds, follow_redirects=False,
        ) as http:
            return await JavaReadOnlyClient(http, WireSecurity(settings)).get(path, actor, params)

    async def network():
        return WalkNetwork(await read("/internal/agent/v1/matching/network"), actor.campus_id)

    @server.tool()
    async def search_tasks(keyword: str = "", type: str = "", pickup: str = "",
                           delivery: str = "", page: int = 1, size: int = 10) -> dict:
        """Search eligible PUBLIC tasks, in the signed-in campus. Use bounded SQL filters
        (keyword or pickup/delivery, optionally type) and pages; do not claim global optimality.
        Returned cards may omit weight and handoff waiting time: these are UNKNOWN.
        """
        if (not isinstance(page, int) or isinstance(page, bool) or not 1 <= page <= 5 or not isinstance(size, int) or isinstance(size, bool)
                or not 1 <= size <= 20 or len(keyword) > 80 or len(type) > 32
                or len(pickup) > 80 or len(delivery) > 80):
            raise ValueError("invalid search parameters")
        if keyword and (pickup or delivery):
            raise ValueError("choose keyword OR pickup/delivery SQL filters")
        data = await read("/internal/agent/v1/matching/candidates", {
            "keyword": keyword, "type": type, "pickup": pickup, "delivery": delivery,
            "page": page, "size": size,
        })
        if not isinstance(data, dict) or not isinstance(data.get("records"), list):
            raise ValueError("invalid task page")
        return {
            "page": page, "size": size,
            "examined": int(data["examined"]),
            "totalBeforeEligibility": int(data["totalBeforeEligibility"]),
            "morePages": data["morePages"] is True,
            "records": [safe_validate(TaskCard, x) for x in data["records"][:size]],
            "scope": "same campus, active, server prechecked; not all tasks or a reservation",
        }

    @server.tool()
    async def get_task_detail(task_id: int) -> dict:
        """Get an active campus PUBLIC task's details; private codes are never returned."""
        if type(task_id) is not int or task_id <= 0:
            raise ValueError("invalid task ID")
        return safe_validate(TaskCard, await read(f"/internal/agent/v1/tasks/{task_id}"))

    @server.tool()
    async def check_accept_eligibility(task_id: int) -> dict:
        """Read-only tentative accept eligibility. Java must check again on user confirmation."""
        if type(task_id) is not int or task_id <= 0:
            raise ValueError("invalid task ID")
        data = await read(f"/internal/agent/v1/tasks/{task_id}/eligibility")
        if not isinstance(data, dict) or type(data.get("canAttempt")) is not bool:
            raise ValueError("invalid eligibility response")
        return {"taskId": task_id, "canAttempt": data["canAttempt"],
                "reason": str(data.get("reason", ""))[:80], "finalCheckRequired": True}

    @server.tool()
    async def resolve_campus_place(name: str) -> dict:
        """Resolve a campus location from verified walk-network aliases. Ambiguous or
        absent names must be clarified, never replaced with made-up coordinates.
        """
        if not name or len(name) > 100:
            raise ValueError("invalid place name")
        raw = await read("/internal/agent/v1/matching/network")
        if raw.get("campusId") != actor.campus_id:
            raise ValueError("campus mismatch")
        query = _normalize(name)
        matches = []
        for place in raw.get("places", [])[:200]:
            if not place.get("verifiedAt"):
                continue
            aliases = place.get("aliasesJson") or "[]"
            try:
                aliases = json.loads(aliases) if isinstance(aliases, str) else aliases
            except (TypeError, ValueError):
                aliases = []
            candidates = [place.get("name", "")] + (aliases if isinstance(aliases, list) else [])
            if any(isinstance(v, str) and _normalize(v) == query for v in candidates):
                matches.append({"placeId": place["id"], "name": place["name"]})
        if len(matches) == 1:
            return {"status": "resolved", "place": matches[0], "source": "verified_campus_network"}
        return {"status": "ambiguous" if matches else "unavailable",
                "candidates": matches[:10], "source": "verified_campus_network"}

    @server.tool()
    async def estimate_task_detour(task_id: int) -> dict:
        """Compute ONLY this task's extra walking minutes along the authenticated
        user's stated origin -> pickup -> delivery -> destination. Network edges
        and deterministic math are tool-controlled; UNKNOWN if not routable.
        Includes assumed walking speed and handoff buffer, not real-time Tencent routing.
        """
        if type(task_id) is not int or task_id <= 0:
            raise ValueError("invalid task ID")
        if not origin or not destination:
            return {"taskId": task_id, "routeVerified": False, "reason": "MISSING_USER_ROUTE"}
        card = safe_validate(TaskCard, await read(f"/internal/agent/v1/tasks/{task_id}"))
        walk = await network()
        minutes = walk.detour(origin, destination, card["pickupArea"], card["deliveryArea"])
        if minutes is None:
            return {"taskId": task_id, "routeVerified": False,
                    "reason": "PLACE_OR_VERIFIED_NETWORK_UNAVAILABLE",
                    "source": "verified_walk_edges_only"}
        return {"taskId": task_id, "routeVerified": True,
                "estimatedDetourMinutes": round(minutes, 1),
                "source": "verified_walk_edges_only",
                "note": "planning estimate using 1.25m/s and 4-minute handoff; not ETA guarantee"}

    return server


def main():
    settings = Settings()
    principal = os.environ.get("SHUNSHOU_MCP_PRINCIPAL", "")
    actor = WireSecurity(settings).verify_principal(principal)
    build_server(settings, actor,
                 origin=os.environ.get("SHUNSHOU_MCP_ORIGIN", ""),
                 destination=os.environ.get("SHUNSHOU_MCP_DESTINATION", "")).run(transport="stdio")


if __name__ == "__main__":
    main()
