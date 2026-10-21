import uuid

import pytest
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.checkpoint.sqlite.aio import AsyncSqliteSaver

from app.graph import FALLBACK_REPLY, build_graph
from app.models import MockResponder, SCAFFOLD_REPLY


@pytest.mark.asyncio
async def test_minimal_graph_turns_and_user_isolation():
    graph = build_graph(InMemorySaver(), MockResponder())
    session_id = uuid.uuid4()
    config_a = {"configurable": {"thread_id": f"user:1:session:{session_id}"}}
    config_b = {"configurable": {"thread_id": f"user:2:session:{session_id}"}}
    first = await graph.ainvoke({"messages": [HumanMessage(content="打印资料")]}, config=config_a)
    second = await graph.ainvoke({"messages": [HumanMessage(content="送到宿舍")]}, config=config_a)
    other = await graph.ainvoke({"messages": [HumanMessage(content="其他用户")]}, config=config_b)
    assert first["turns"] == 1
    assert second["turns"] == 2
    assert other["turns"] == 1
    assert second["messages"][-1].content == SCAFFOLD_REPLY
    assert all("其他用户" not in str(m.content) for m in second["messages"])
    assert len(second["messages"]) == 4


@pytest.mark.asyncio
async def test_sqlite_checkpoint_survives_process_lifecycle(tmp_path):
    path = tmp_path / "graph.sqlite3"
    config = {"configurable": {"thread_id": "user:1:session:9d9934da-ffdc-41c7-a3d4-f6175be9ea18"}}
    async with AsyncSqliteSaver.from_conn_string(str(path)) as saver:
        graph = build_graph(saver, MockResponder())
        first = await graph.ainvoke({"messages": [HumanMessage(content="first")]}, config=config)
        assert first["turns"] == 1
    async with AsyncSqliteSaver.from_conn_string(str(path)) as saver:
        graph = build_graph(saver, MockResponder())
        second = await graph.ainvoke({"messages": [HumanMessage(content="second")]}, config=config)
        assert second["turns"] == 2


@pytest.mark.asyncio
async def test_model_failure_has_safe_fallback():
    class BrokenModel:
        async def respond(self, history):
            raise TimeoutError("private-provider-endpoint")

    graph = build_graph(InMemorySaver(), BrokenModel())
    state = await graph.ainvoke(
        {"messages": [HumanMessage(content="hello")]},
        config={"configurable": {"thread_id": "mock-failure"}},
    )
    assert state["status"] == "model_unavailable"
    assert state["messages"][-1].content == FALLBACK_REPLY
    assert "private-provider-endpoint" not in state["messages"][-1].content
