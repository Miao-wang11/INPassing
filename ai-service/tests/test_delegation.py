"""Runnable LangGraph delegation, clarification, service matching and HITL tests."""
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import httpx
import pytest
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from app.agent.delegation import (
    DemandExtractor, build_delegation_graph, validate_patch, preview_payload,
)
from app.clients.java_backend import JavaReadOnlyClient
from app.security import WireSecurity
from tests.test_http import principal, signed
from app.main import create_app


def settings_actor(settings):
    security = WireSecurity(settings)
    actor = security.verify_principal(principal(settings.internal_token.get_secret_value(), 9, 7))
    return security, actor


class FakeJava:
    def __init__(self, offers=None, fail=False):
        self.offers = offers or []
        self.calls = []
        self.fail = fail

    async def get(self, path, actor, params=None):
        self.calls.append((path, actor.user_id, actor.campus_id, params))
        if self.fail:
            from app.clients.java_backend import JavaToolError
            raise JavaToolError("TIMEOUT")
        assert path == "/internal/agent/v1/offers"
        return {"total": len(self.offers), "records": self.offers}


@pytest.mark.asyncio
async def test_multiturn_clarification_public_handoff_no_write(settings):
    _, actor = settings_actor(settings)
    java = FakeJava()
    graph = build_delegation_graph(InMemorySaver(), DemandExtractor(settings), java, actor)
    cfg = {"configurable": {"thread_id": "delegation:user:9:campus:7:session:123"}}
    first = await graph.ainvoke({"messages": [HumanMessage(content="帮我打印三十页资料送到教学楼，预算十块")]}, cfg)
    assert first["status"] == "needs_clarification"
    assert "tip" not in first["slots"]  # total budget never becomes service fee
    assert first["slots"]["category"] == "校内代办"
    assert first["slots"]["sub_type"] == "资料打印"
    assert first["slots"]["pages"] == 30
    assert first["slots"]["max_budget"] == 10.0
    second = await graph.ainvoke(
        {"messages": [HumanMessage(content="取件地点：图书馆，地址ID 10，小费5元")]}, cfg
    )
    assert "__interrupt__" in second
    assert second["__interrupt__"][0].value["kind"] == "choose_mode"
    result = await graph.ainvoke(Command(resume={"mode": "public"}), cfg)
    assert result["status"] == "ready_for_java_preview"
    assert result["preview_input"]["mode"] == "PUBLIC_TASK"
    assert result["preview_input"]["task"]["tip"] == 5
    assert result["preview_input"]["task"]["deliveryAddressId"] == 10
    assert result["preview_input"]["task"]["expireMinutes"] == 60
    assert result["preview_input"]["task"]["taskSpecs"] == '{"数量":30}'
    assert "payPassword" not in str(result)
    assert not any("confirm" in p or "publish" in p or "book" in p for p, *_ in java.calls)


@pytest.mark.asyncio
async def test_real_offer_from_java_and_invalid_choice_never_creates_order(settings):
    _, actor = settings_actor(settings)
    now = datetime.now(ZoneInfo("Asia/Shanghai")).replace(tzinfo=None)
    java = FakeJava([{
        "offerId": 7, "category": "代拿餐食", "subType": "奶茶咖啡代取",
        "title": "帮带咖啡", "servicePrice": 3.0,
        "availableFrom": (now-timedelta(hours=1)).isoformat(),
        "availableUntil": (now+timedelta(hours=1)).isoformat(),
    }])
    graph = build_delegation_graph(InMemorySaver(), DemandExtractor(settings), java, actor)
    config = {"configurable": {"thread_id": "delegation:user:9:campus:7:session:456"}}
    result = await graph.ainvoke({"messages": [HumanMessage(
        content="今晚谁能帮我带一杯咖啡？取件地点：咖啡店，地址ID 10"
    )]}, config)
    assert "__interrupt__" in result
    assert result["__interrupt__"][0].value["offers"][0]["offerId"] == 7
    invalid = await graph.ainvoke(Command(resume={"mode": "offer", "offer_id": 999}), config)
    assert invalid["status"] == "invalid_choice"
    assert invalid.get("preview_input") is None


@pytest.mark.asyncio
async def test_offer_preview_never_invents_quoted_price(settings):
    _, actor = settings_actor(settings)
    now = datetime.now(ZoneInfo("Asia/Shanghai")).replace(tzinfo=None)
    java = FakeJava([{
        "offerId": 7, "category": "代拿餐食", "subType": "奶茶咖啡代取",
        "title": "咖啡", "servicePrice": 4.5,
        "availableFrom": (now-timedelta(hours=1)).isoformat(),
        "availableUntil": (now+timedelta(hours=1)).isoformat(),
    }])
    graph = build_delegation_graph(InMemorySaver(), DemandExtractor(settings), java, actor)
    cfg = {"configurable": {"thread_id": "delegation:user:9:campus:7:session:457"}}
    x = await graph.ainvoke({"messages": [HumanMessage(
        content="需要咖啡，取件地点：西门咖啡店，地址ID 10"
    )]}, cfg)
    assert "__interrupt__" in x
    done = await graph.ainvoke(Command(resume={"mode": "offer", "offer_id": 7}), cfg)
    assert done["status"] == "ready_for_java_preview"
    assert done["preview_input"]["offer"]["offerId"] == 7
    assert "servicePrice" not in str(done["preview_input"])
    assert "payPassword" not in str(done["preview_input"])


@pytest.mark.asyncio
async def test_backend_outage_does_not_silently_choose_public(settings):
    _, actor = settings_actor(settings)
    graph = build_delegation_graph(InMemorySaver(), DemandExtractor(settings),
                                   FakeJava(fail=True), actor)
    state = await graph.ainvoke({"messages": [HumanMessage(content="帮我打印资料")]},
                                {"configurable": {"thread_id": "safety-outage"}})
    assert state["status"] == "lookup_unavailable"
    assert "preview_input" not in state


@pytest.mark.asyncio
async def test_signed_actual_asgi_delegation_handoff(settings):
    secret = settings.internal_token.get_secret_value()
    async with httpx.AsyncClient(
        transport=httpx.MockTransport(lambda request: httpx.Response(200, json={
            "code": 1, "data": {"total": 0, "records": []}
        })), base_url="http://java.test/api/"
    ) as java_http:
        app = create_app(settings, java_http_override=java_http)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                                         base_url="http://python.test") as client:
                actor = principal(secret, 9, 7)
                first = signed(client, "POST", "/internal/v1/delegation", secret, actor,
                               payload={"message": "帮我打印资料，预算十块"})
                first_reply = await client.send(first)
                assert first_reply.status_code == 200, first_reply.text
                assert first_reply.json()["status"] == "needs_clarification"
                sid = first_reply.json()["session_id"]
                second = signed(client, "POST", "/internal/v1/delegation", secret, actor,
                    payload={"message": "取件地点：图书馆，地址ID 10，小费5元", "session_id": sid})
                next_reply = await client.send(second)
                assert next_reply.status_code == 200, next_reply.text
                assert next_reply.json()["status"] == "choose_mode"
                third = signed(client, "POST", "/internal/v1/delegation", secret, actor,
                    payload={"choice": "public", "session_id": sid})
                final = await client.send(third)
                assert final.status_code == 200, final.text
                assert final.json()["status"] == "ready_for_java_preview"
                assert "payPassword" not in final.text
                assert (await client.post("/internal/v1/delegation", json={
                    "message": "支付密码123456"
                })).status_code in (401, 422)


def test_no_llm_forged_money_identity_and_invalid_numbers():
    assert validate_patch({"category": "校内代办", "tip": -1, "userId": 999,
                           "campusId": 77, "tip2": 45, "max_budget": 10}) == {
                               "category": "校内代办", "max_budget": 10.0
                           }


@pytest.mark.asyncio
async def test_model_timeout_uses_conservative_rules(settings):
    _, actor = settings_actor(settings)
    extractor = DemandExtractor(settings)
    class TimeoutModel:
        async def ainvoke(self, messages):
            raise TimeoutError("provider may be down")
    extractor.model = TimeoutModel()
    graph = build_delegation_graph(InMemorySaver(), extractor, FakeJava(), actor)
    state = await graph.ainvoke(
        {"messages": [HumanMessage(content="帮我打印30页资料，预算10元")]},
        {"configurable": {"thread_id": "degraded-model"}}
    )
    assert state["model_degraded"] is True
    assert state["status"] == "needs_clarification"
    assert state["slots"]["pages"] == 30
    assert "tip" not in state["slots"]


@pytest.mark.asyncio
async def test_expired_or_wrong_category_offer_never_promoted(settings):
    _, actor = settings_actor(settings)
    now = datetime.now(ZoneInfo("Asia/Shanghai")).replace(tzinfo=None)
    java = FakeJava([{
        "offerId": 77, "category": "校内代办", "subType": "资料打印",
        "title": "打印", "servicePrice": 2,
        "availableFrom": (now-timedelta(days=2)).isoformat(),
        "availableUntil": (now-timedelta(days=1)).isoformat(),
    }])
    graph = build_delegation_graph(InMemorySaver(), DemandExtractor(settings), java, actor)
    state = await graph.ainvoke(
        {"messages": [HumanMessage(content="帮我打印20页资料，取件地点：图书馆，地址ID 10，小费4元")]},
        {"configurable": {"thread_id": "expired-offer"}}
    )
    assert "__interrupt__" in state
    assert state["__interrupt__"][0].value["offers"] == []


def test_deadline_is_recorded_but_never_misrepresented_as_enforced():
    from app.agent.delegation import DemandExtractor
    data = DemandExtractor._rule_extract("明天下午四点前帮我打印三十页资料")
    assert "明天下午四点前" in data["deadline_text"]
    assert data["pages"] == 30
    assert "delivery_address_id" not in data
