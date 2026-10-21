"""Internal-only FastAPI gateway for a minimal LangGraph scaffold.

The authenticated actor is provided by Java after service-token validation.
Stage 2 will add signed, expiring principal context and Java business adapters.
"""
import logging
import secrets
import uuid
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver
from pydantic import BaseModel, Field, field_validator

from app.config import Settings
from app.graph import build_graph
from app.models import build_responder

logger = logging.getLogger(__name__)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    session_id: uuid.UUID | None = None

    @field_validator("message")
    @classmethod
    def not_blank(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("message cannot be blank")
        return value


class ChatResponse(BaseModel):
    session_id: uuid.UUID
    answer: str
    turns: int
    status: str


class Actor(BaseModel):
    user_id: int = Field(gt=0)


def create_app(settings: Settings | None = None, *, graph_override=None) -> FastAPI:
    settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if graph_override is not None:
            app.state.graph = graph_override
            yield
            return
        settings.checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
        async with AsyncSqliteSaver.from_conn_string(str(settings.checkpoint_path)) as checkpointer:
            app.state.graph = build_graph(checkpointer, build_responder(settings))
            yield
        app.state.graph = None

    app = FastAPI(title="ShunShou AI (Phase 1)", lifespan=lifespan)
    app.state.settings = settings

    async def get_actor(
        x_internal_token: Annotated[str | None, Header()] = None,
        x_authenticated_user_id: Annotated[int | None, Header()] = None,
    ) -> Actor:
        expected = settings.internal_token.get_secret_value()
        if not x_internal_token or not secrets.compare_digest(x_internal_token, expected):
            raise HTTPException(status_code=401, detail="Invalid service credentials")
        if x_authenticated_user_id is None or x_authenticated_user_id <= 0:
            raise HTTPException(status_code=401, detail="Missing trusted actor")
        return Actor(user_id=x_authenticated_user_id)

    @app.get("/healthz")
    async def healthz():
        return {"status": "ok", "phase": 1}

    @app.get("/readyz")
    async def readyz(request: Request):
        if getattr(request.app.state, "graph", None) is None:
            raise HTTPException(status_code=503, detail="Graph not initialized")
        return {"status": "ready"}

    @app.post("/internal/v1/chat", response_model=ChatResponse)
    async def chat(payload: ChatRequest, request: Request, actor: Annotated[Actor, Depends(get_actor)]):
        graph = getattr(request.app.state, "graph", None)
        if graph is None:
            raise HTTPException(status_code=503, detail="Graph not ready")
        session_id = payload.session_id or uuid.uuid4()
        # Actor is NOT accepted from user text or JSON body. Session namespaces are user-scoped.
        thread_id = f"user:{actor.user_id}:session:{session_id}"
        config = {"configurable": {"thread_id": thread_id}, "recursion_limit": 6}
        try:
            state = await graph.ainvoke({"messages": [HumanMessage(content=payload.message)]}, config=config)
            return ChatResponse(
                session_id=session_id,
                answer=str(state["messages"][-1].content),
                turns=state["turns"],
                status=state["status"],
            )
        except Exception:
            logger.exception("agent_graph_failed", extra={"request_id": request.headers.get("X-Request-ID", "")[:64]})
            raise HTTPException(status_code=503, detail="AI service temporarily unavailable") from None

    return app


# Config errors intentionally fail startup (no unauthenticated fallback).
app = create_app()
