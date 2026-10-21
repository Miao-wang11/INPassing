"""Typed LangChain tools bound to authenticated per-request Actor, never LLM-supplied IDs."""
from langchain_core.tools import tool
from pydantic import BaseModel, ConfigDict, Field

from app.clients.java_backend import JavaReadOnlyClient
from app.security import Actor


class TaskCard(BaseModel):
    model_config = ConfigDict(extra="forbid")
    taskId: int
    type: str
    subType: str | None = None
    publicDesc: str
    pickupArea: str
    deliveryArea: str
    reward: str | float
    tip: str | float | None = None
    deliveryFee: str | float | None = None
    productCost: str | float | None = None
    expireTime: str | None = None


class OfferCard(BaseModel):
    model_config = ConfigDict(extra="forbid")
    offerId: int
    category: str
    subType: str | None = None
    title: str
    description: str
    servicePrice: str | float
    availableFrom: str
    availableUntil: str


class CandidatePage(BaseModel):
    total: int = Field(ge=0)
    records: list[dict]


def build_read_only_tools(java: JavaReadOnlyClient, actor: Actor):
    """Returns tools with an immutable, verified actor closure. Phase 3 will bind to LLM."""
    async def request_page(path: str, params: dict, model):
        data = await java.get(path, actor, params)
        page = CandidatePage.model_validate(data)
        return {
            "total": page.total,
            "records": [model.model_validate(item).model_dump(mode="json") for item in page.records],
        }

    @tool
    async def search_public_tasks(keyword: str = "", type: str = "", page: int = 1, size: int = 10) -> dict:
        """Search legal same-campus public tasks. No private contacts or acceptance action."""
        if page < 1 or size < 1 or size > 30 or len(keyword) > 80 or len(type) > 32:
            raise ValueError("invalid tool parameters")
        return await request_page("/internal/agent/v1/tasks/search",
                                  {"keyword": keyword or None, "type": type or None, "page": page, "size": size},
                                  TaskCard)

    @tool
    async def filter_public_tasks(type: str = "", pickup: str = "", delivery: str = "",
                                  page: int = 1, size: int = 10) -> dict:
        """Filter public tasks by existing Java task type and coarse pickup/delivery names."""
        if page < 1 or size < 1 or size > 30 or len(type) > 32 or len(pickup) > 80 or len(delivery) > 80:
            raise ValueError("invalid tool parameters")
        return await request_page("/internal/agent/v1/tasks/filter",
                                  {"type": type or None, "pickup": pickup or None,
                                   "delivery": delivery or None, "page": page, "size": size}, TaskCard)

    @tool
    async def list_service_offers(category: str = "", page: int = 1, size: int = 10) -> dict:
        """List available campus service offers using real Java business price and time window."""
        if page < 1 or size < 1 or size > 30 or len(category) > 32:
            raise ValueError("invalid tool parameters")
        return await request_page("/internal/agent/v1/offers",
                                  {"category": category or None, "page": page, "size": size}, OfferCard)

    @tool
    async def get_campus_catalog() -> list[dict]:
        """Read only the authenticated user's campus; this is NOT a building coordinate dictionary."""
        data = await java.get("/internal/agent/v1/campus", actor)
        if not isinstance(data, list):
            raise ValueError("invalid campus response")
        return [
            {"campusId": int(x["campusId"]), "campusName": str(x["campusName"]),
             "schoolName": str(x["schoolName"]), "coordinatesAvailable": False}
            for x in data
        ]

    @tool
    async def check_accept_eligibility(task_id: int) -> dict:
        """Read-only tentative accept eligibility; final decision remains with Java acceptOrder."""
        if task_id <= 0:
            raise ValueError("invalid task ID")
        data = await java.get(f"/internal/agent/v1/tasks/{task_id}/eligibility", actor)
        if not isinstance(data, dict) or not isinstance(data.get("canAttempt"), bool):
            raise ValueError("invalid eligibility response")
        return {"canAttempt": data["canAttempt"], "reason": str(data.get("reason", "")),
                "finalCheckRequired": True}

    return [search_public_tasks, filter_public_tasks, list_service_offers,
            get_campus_catalog, check_accept_eligibility]
