"""Minimal durable LangGraph; no Java calls and no tools with side effects."""
import logging
from typing import Annotated

from langchain_core.messages import AIMessage, AnyMessage
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from typing_extensions import TypedDict

from app.models import Responder

logger = logging.getLogger(__name__)
FALLBACK_REPLY = "智能模型暂时不可用。当前不能通过智能助手办理交易，请使用原有业务页面。"


class AgentState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    turns: int
    status: str


def build_graph(checkpointer, responder: Responder):
    async def respond(state: AgentState) -> dict:
        try:
            answer = await responder.respond(state.get("messages", []))
            status = "scaffold"
        except Exception:  # provider failures must not be presented as transaction success
            logger.exception("model_response_failed")
            answer = FALLBACK_REPLY
            status = "model_unavailable"
        return {
            "messages": [AIMessage(content=answer)],
            "turns": state.get("turns", 0) + 1,
            "status": status,
        }

    builder = StateGraph(AgentState)
    builder.add_node("respond", respond)
    builder.add_edge(START, "respond")
    builder.add_edge("respond", END)
    return builder.compile(checkpointer=checkpointer)
