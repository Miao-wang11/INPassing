"""Private FastAPI service for Java-authenticated campus Agent workflows."""
import logging
import uuid
from contextlib import asynccontextmanager
from typing import Annotated

import httpx
from fastapi import Depends, FastAPI, HTTPException, Query, Request
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from langgraph.types import Command
from pydantic import BaseModel, Field, model_validator

from app.clients.java_backend import JavaReadOnlyClient, JavaToolError
from app.config import Settings
from app.agent.delegation import DemandExtractor, build_delegation_graph
from app.agent.autonomous_route import build_autonomous_graph
from app.security import Actor, NonceGuard, WireSecurity, require_actor
from app.tools.read_only import build_read_only_tools

logger = logging.getLogger(__name__)


class RouteRequest(BaseModel):
    message: str = Field(default="", max_length=2000)
    session_id: uuid.UUID | None = None
    task_id: int | None = Field(default=None, gt=0)
    acknowledge_unverified: bool = False

    @model_validator(mode="after")
    def validate_action(self):
        if bool(self.message.strip()) == (self.task_id is not None):
            raise ValueError("Provide exactly one nonblank message or task selection")
        if self.task_id is not None and self.session_id is None:
            raise ValueError("session_id required for selection")
        if any(word in self.message.lower() for word in ("支付密码", "paypassword", "pay_password")):
            raise ValueError("Do not enter payment secrets")
        return self


class DelegationRequest(BaseModel):
    """Only human choice is accepted; no task/order ID or password from the model."""
    message: str = Field(default="", max_length=2000)
    session_id: uuid.UUID | None = None
    choice: str | None = Field(default=None, pattern="^(public|offer)$")
    offer_id: int | None = Field(default=None, gt=0)

    @model_validator(mode="after")
    def validate_action(self):
        if bool(self.message.strip()) == bool(self.choice):
            raise ValueError("Supply exactly one nonblank message or a choice")
        if self.offer_id is not None and self.choice != "offer":
            raise ValueError("offer_id is only valid with offer choice")
        if self.choice and self.session_id is None:
            raise ValueError("session_id required for Graph resume")
        if any(word in self.message.lower() for word in ("支付密码", "paypassword", "pay_password")):
            raise ValueError("Never enter payment secrets in Agent chat")
        return self


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.wire = WireSecurity(settings)
        app.state.extractor = DemandExtractor(settings)
        app.state.nonces = NonceGuard()
        settings.checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
        async with httpx.AsyncClient(
            base_url=settings.java_base_url.rstrip("/") + "/",
            timeout=settings.request_timeout_seconds,
            follow_redirects=False,
        ) as java_http:
            app.state.java_tools = JavaReadOnlyClient(java_http, app.state.wire)
            async with AsyncSqliteSaver.from_conn_string(str(settings.checkpoint_path)) as saver:
                app.state.delegation_saver = saver
                yield
        app.state.delegation_saver = None

    app = FastAPI(title="ShunShou AI", lifespan=lifespan)

    @app.get("/healthz")
    async def healthz():
        return {"status": "ok"}

    @app.get("/readyz")
    async def readyz(request: Request):
        if getattr(request.app.state, "delegation_saver", None) is None:
            raise HTTPException(status_code=503, detail="Checkpoint not initialized")
        return {"status": "ready"}

    @app.post("/internal/v1/delegation")
    async def delegation(payload: DelegationRequest, request: Request,
                         actor: Annotated[Actor, Depends(require_actor)]):
        saver = getattr(request.app.state, "delegation_saver", None)
        if saver is None:
            raise HTTPException(status_code=503, detail="Delegation checkpoint unavailable")
        session_id = payload.session_id or uuid.uuid4()
        graph = build_delegation_graph(saver, request.app.state.extractor,
                                       request.app.state.java_tools, actor)
        config = {"configurable": {
            "thread_id": f"delegation:user:{actor.user_id}:campus:{actor.campus_id}:session:{session_id}"
        }, "recursion_limit": 8}
        try:
            if payload.choice:
                snapshot = await graph.aget_state(config)
                if not snapshot.tasks or not any(t.interrupts for t in snapshot.tasks):
                    raise HTTPException(status_code=409, detail="No pending choice to resume")
                state = await graph.ainvoke(Command(resume={
                    "mode": payload.choice, "offer_id": payload.offer_id
                }), config=config)
            else:
                state = await graph.ainvoke(
                    {"messages": [HumanMessage(content=payload.message.strip())]}, config=config)
            if "__interrupt__" in state:
                pauses = state["__interrupt__"]
                return {"session_id": session_id, "status": "choose_mode",
                        "answer": "请选择匹配的服务橱窗或生成公开任务草稿，尚未产生任何费用。",
                        "offers": pauses[0].value.get("offers", []),
                        "requires_confirmation": True, "preview_input": None}
            return {"session_id": session_id, "status": state.get("status", "unknown"),
                    "answer": state.get("question", ""),
                    "slots": state.get("slots", {}), "offers": state.get("offers", []),
                    "preview_input": state.get("preview_input"),
                    "requires_confirmation": state.get("status") == "ready_for_java_preview",
                    "model_degraded": state.get("model_degraded", False)}
        except HTTPException:
            raise
        except Exception:
            logger.exception("delegation_graph_failed")
            raise HTTPException(status_code=503, detail="Delegation temporarily unavailable") from None

    @app.post("/internal/v1/route-match")
    async def route_match(payload: RouteRequest, request: Request,
                          actor: Annotated[Actor, Depends(require_actor)]):
        saver = getattr(request.app.state, "delegation_saver", None)
        if saver is None:
            raise HTTPException(status_code=503, detail="Checkpoint unavailable")
        session_id = payload.session_id or uuid.uuid4()
        graph = build_autonomous_graph(saver, settings, actor)
        config = {"configurable": {"thread_id":
            f"route:user:{actor.user_id}:campus:{actor.campus_id}:session:{session_id}"},
            "recursion_limit": 8}
        try:
            if payload.task_id is not None:
                snapshot = await graph.aget_state(config)
                if not snapshot.tasks or not any(t.interrupts for t in snapshot.tasks):
                    raise HTTPException(status_code=409, detail="No pending task selection")
                state = await graph.ainvoke(Command(resume={
                    "task_id": payload.task_id,
                    "acknowledge_unverified": payload.acknowledge_unverified,
                }), config=config)
            else:
                state = await graph.ainvoke(
                    {"messages": [HumanMessage(content=payload.message.strip())]}, config=config)
            if "__interrupt__" in state:
                pause = state["__interrupt__"][0].value
                snapshot = await graph.aget_state(config)
                saved = snapshot.values or {}
                return {
                    "session_id": session_id, "status": "choose_task",
                    "answer": saved.get("answer", "请选择经工具核实的任务；此操作不会自动接单。"),
                    "verified": pause.get("verified", []),
                    "unverified": pause.get("unverified", []),
                    "events": saved.get("events", []),
                    "limitations": saved.get("limitations", []),
                    "slots": saved.get("slots", {}),
                    "comparison": saved.get("comparison", []),
                    "summary": saved.get("summary", ""),
                    "explanation_degraded": saved.get("explanation_degraded", False),
                    "requires_final_java_accept": True,
                }
            return {"session_id": session_id, "status": state.get("status"),
                    "answer": state.get("answer", ""),
                    "slots": state.get("slots", {}),
                    "verified": state.get("verified", []),
                    "unverified": state.get("unverified", []),
                    "selected": state.get("selected"),
                    "limitations": state.get("limitations", []),
                    "events": state.get("events", []),
                    "comparison": state.get("comparison", []),
                    "summary": state.get("summary", ""),
                    "explanation_degraded": state.get("explanation_degraded", False),
                    "goal_errors": state.get("goal_errors", []),
                    "model_degraded": state.get("model_degraded", False),
                    "requires_final_java_accept": state.get("status") == "ready_for_java_accept"}
        except HTTPException:
            raise
        except Exception:
            logger.exception("route_matching_failed")
            raise HTTPException(status_code=503, detail="Matching temporarily unavailable") from None

    async def call_tool(request: Request, actor: Actor, name: str, inputs: dict):
        tools = {tool.name: tool for tool in build_read_only_tools(request.app.state.java_tools, actor)}
        if name not in tools:
            raise HTTPException(status_code=404, detail="No such read-only tool")
        try:
            return await tools[name].ainvoke(inputs)
        except JavaToolError as ex:
            status = {"UNAUTHORIZED": 403, "BUSINESS_REJECTED": 400, "TIMEOUT": 504,
                      "BACKEND_UNAVAILABLE": 503, "INVALID_RESPONSE": 502}.get(ex.kind, 400)
            raise HTTPException(status_code=status, detail=ex.kind) from None
        except (ValueError, TypeError):
            raise HTTPException(status_code=422, detail="Invalid tool parameters/response") from None
        except Exception:
            logger.exception("read_only_tool_failed")
            raise HTTPException(status_code=503, detail="Read-only tool unavailable") from None

    @app.get("/internal/v1/tools/tasks/search")
    async def task_search(request: Request, actor: Annotated[Actor, Depends(require_actor)],
                          keyword: str = Query(default="", max_length=80),
                          type: str = Query(default="", max_length=32),
                          page: int = Query(default=1, ge=1),
                          size: int = Query(default=10, ge=1, le=30)):
        return await call_tool(request, actor, "search_public_tasks",
                               {"keyword": keyword, "type": type, "page": page, "size": size})

    @app.get("/internal/v1/tools/tasks/filter")
    async def task_filter(request: Request, actor: Annotated[Actor, Depends(require_actor)],
                          type: str = Query(default="", max_length=32),
                          pickup: str = Query(default="", max_length=80),
                          delivery: str = Query(default="", max_length=80),
                          page: int = Query(default=1, ge=1),
                          size: int = Query(default=10, ge=1, le=30)):
        return await call_tool(request, actor, "filter_public_tasks",
                               {"type": type, "pickup": pickup, "delivery": delivery,
                                "page": page, "size": size})

    @app.get("/internal/v1/tools/offers")
    async def offers(request: Request, actor: Annotated[Actor, Depends(require_actor)],
                     category: str = Query(default="", max_length=32),
                     page: int = Query(default=1, ge=1),
                     size: int = Query(default=10, ge=1, le=30)):
        return await call_tool(request, actor, "list_service_offers",
                               {"category": category, "page": page, "size": size})

    @app.get("/internal/v1/tools/tasks/{task_id}")
    async def task_detail(task_id: int, request: Request,
                          actor: Annotated[Actor, Depends(require_actor)]):
        return await call_tool(request, actor, "get_public_task_detail", {"task_id": task_id})

    @app.get("/internal/v1/tools/offers/{offer_id}")
    async def offer_detail(offer_id: int, request: Request,
                           actor: Annotated[Actor, Depends(require_actor)]):
        return await call_tool(request, actor, "get_service_offer_detail", {"offer_id": offer_id})

    @app.get("/internal/v1/tools/campus")
    async def campus(request: Request, actor: Annotated[Actor, Depends(require_actor)]):
        return await call_tool(request, actor, "get_campus_catalog", {})

    @app.get("/internal/v1/tools/tasks/{task_id}/eligibility")
    async def eligibility(task_id: int, request: Request,
                          actor: Annotated[Actor, Depends(require_actor)]):
        return await call_tool(request, actor, "check_accept_eligibility", {"task_id": task_id})

    return app


# Missing shared secret deliberately prevents startup; no unauthenticated fallback.
app = create_app()
