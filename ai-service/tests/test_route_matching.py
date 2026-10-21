"""Stage 4: measured-only walking graph, eligible shortlist, HITL and signed HTTP."""
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import httpx
import pytest
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from app.agent.route_matching import (
    RouteExtractor, WalkNetwork, _parse_departure, _rule_extract,
    rank_cards, build_route_graph,
)
from app.clients.java_backend import JavaToolError
from app.main import create_app
from app.security import WireSecurity
from tests.test_http import principal, signed

ZONE = ZoneInfo("Asia/Shanghai")


def graph_fixture(campus=7):
    # TEST fixture only; distances are synthetic and must never seed production.
    return {
        "campusId": campus, "routeMode": "VERIFIED_WALK_EDGES_ONLY",
        "places": [
            {"id": 1, "name": "图书馆", "aliasesJson": '["图书馆正门"]',
             "verifiedAt": "2026-10-01T12:00:00"},
            {"id": 2, "name": "三号宿舍", "aliasesJson": '["3号宿舍"]',
             "verifiedAt": "2026-10-01T12:00:00"},
            {"id": 3, "name": "快递站", "aliasesJson": '["菜鸟驿站"]',
             "verifiedAt": "2026-10-01T12:00:00"},
            {"id": 4, "name": "教学楼", "aliasesJson": '[]',
             "verifiedAt": "2026-10-01T12:00:00"},
        ],
        "edges": [
            {"from": 1, "to": 2, "distanceMeters": 600, "verifiedAt": "2026-10-01T12:00:00"},
            {"from": 1, "to": 3, "distanceMeters": 300, "verifiedAt": "2026-10-01T12:00:00"},
            {"from": 3, "to": 4, "distanceMeters": 300, "verifiedAt": "2026-10-01T12:00:00"},
            {"from": 4, "to": 2, "distanceMeters": 300, "verifiedAt": "2026-10-01T12:00:00"},
        ],
    }


def task(task_id=44, pickup="快递站", delivery="教学楼",
         expire=None, tip=5, delivery_fee=1, product_cost=30):
    return {
        "taskId": task_id, "type": "代取快递", "subType": "小件快递",
        "publicDesc": "代取快递", "pickupArea": pickup, "deliveryArea": delivery,
        "reward": tip + delivery_fee + product_cost, "tip": tip,
        "deliveryFee": delivery_fee, "productCost": product_cost,
        "expireTime": expire or (datetime.now(ZONE)+timedelta(days=2)).isoformat(),
    }


def slots():
    return {
        "origin": "图书馆", "destination": "三号宿舍",
        "max_detour_minutes": 10,
        "departure_at": (datetime.now(ZONE)+timedelta(days=1)).isoformat(),
    }


class FakeJava:
    def __init__(self, candidates, network=None, failure=None):
        self.candidates = candidates
        self.network = network if network is not None else graph_fixture()
        self.failure = failure
        self.paths = []

    async def get(self, path, actor, params=None):
        self.paths.append(path)
        if self.failure == "candidate" or (self.failure == "network" and path.endswith("/network")):
            raise JavaToolError("TIMEOUT")
        if path.endswith("/candidates"):
            return {"examined": len(self.candidates), "totalBeforeEligibility": len(self.candidates),
                    "morePages": False, "records": self.candidates}
        if path.endswith("/network"):
            return self.network
        raise AssertionError("No write methods must be called")


def test_user_language_parsing_without_invented_place_or_time():
    now = datetime.now(ZONE)
    example = "我明天下午五点从图书馆回三号宿舍，顺路想接点单，最多绕路十分钟"
    result = _rule_extract(example, now)
    assert result["origin"] == "图书馆"
    assert result["destination"] == "三号宿舍"
    assert result["max_detour_minutes"] == 10
    assert datetime.fromisoformat(result["departure_at"]).hour == 17
    assert "task_id" not in result and "campus_id" not in result
    assert _parse_departure("昨天八点", now) is None or "yesterday" not in result
    assert _rule_extract("从图书馆回三号宿舍，最多绕路十分钟", now).get("departure_at") is None


def test_verified_pedestrian_graph_and_explicit_no_coordinate_fallback():
    walk = WalkNetwork(graph_fixture(), 7)
    assert walk.place("3号宿舍") == 2
    assert walk.detour("图书馆", "三号宿舍", "快递站", "教学楼") == pytest.approx(8.0)
    # Unlisted/private-location string is never inferred from coordinates.
    assert walk.detour("图书馆", "三号宿舍", "陌生宿舍301室", "教学楼") is None
    assert WalkNetwork(graph_fixture(), 9).detour("图书馆", "三号宿舍", "快递站", "教学楼") is None
    broken = graph_fixture()
    broken["edges"] = broken["edges"][:1]
    assert WalkNetwork(broken, 7).detour("图书馆", "三号宿舍", "快递站", "教学楼") is None
    ambiguous = graph_fixture()
    ambiguous["places"][3]["aliasesJson"] = '["菜鸟驿站"]'
    assert WalkNetwork(ambiguous, 7).place("菜鸟驿站") is None


def test_ranking_does_not_include_goods_budget_as_income_or_invent_eta():
    verified, unverified = rank_cards(slots(), [task(), task(45, pickup="未知站点"),
                                                   task(46, tip=1, product_cost=10000)],
                                       WalkNetwork(graph_fixture(),7))
    assert len(verified) == 2
    assert verified[0]["taskId"] == 44
    assert verified[0]["serviceFee"] == 6
    assert verified[0]["prepaidTotal"] == 36
    assert verified[0]["estimatedDetourMinutes"] == 8.0
    assert len(unverified) == 1
    assert unverified[0]["taskId"] == 45
    assert unverified[0]["estimatedDetourMinutes"] is None
    assert unverified[0]["score"] is None
    assert unverified[0]["routeVerified"] is False
    assert "重量" in unverified[0]["reason"]
    filtered, _ = rank_cards({**slots(), "max_detour_minutes": 7},
                              [task()], WalkNetwork(graph_fixture(),7))
    assert filtered == []
    stale, _ = rank_cards(slots(), [task(expire=(datetime.now(ZONE)-timedelta(minutes=1)).isoformat())],
                          WalkNetwork(graph_fixture(),7))
    assert stale == []


@pytest.mark.asyncio
async def test_clarification_interrupt_resumption_and_no_automatic_accept(settings):
    secret = settings.internal_token.get_secret_value()
    actor = WireSecurity(settings).verify_principal(principal(secret,9,7))
    java = FakeJava([task()])
    graph = build_route_graph(InMemorySaver(), RouteExtractor(settings), java, actor)
    cfg = {"configurable": {"thread_id": "route:9:7:same-thread"}}
    first = await graph.ainvoke({"messages": [HumanMessage(content="从图书馆回三号宿舍")]},cfg)
    assert first["status"] == "needs_clarification"
    second = await graph.ainvoke({"messages": [HumanMessage(
        content="明天下午五点，最多绕路十分钟")]},cfg)
    assert "__interrupt__" in second
    recommended = second["__interrupt__"][0].value["verified"]
    assert recommended[0]["taskId"] == 44
    # Choosing a task yields an endpoint, NOT a Java business write.
    result = await graph.ainvoke(Command(resume={"task_id":44}),cfg)
    assert result["status"] == "ready_for_java_accept"
    assert result["selected"]["acceptEndpoint"] == "/agent/matches/accept/44"
    assert set(java.paths) == {"/internal/agent/v1/matching/network",
                               "/internal/agent/v1/matching/candidates"}


@pytest.mark.asyncio
async def test_unverified_choice_must_acknowledge_uncertainty(settings):
    actor = WireSecurity(settings).verify_principal(
        principal(settings.internal_token.get_secret_value(),9,7))
    java = FakeJava([task(47, pickup="未建档地点")],network={})
    graph = build_route_graph(InMemorySaver(), RouteExtractor(settings),java,actor)
    cfg={"configurable":{"thread_id":"route:uncertain"}}
    state=await graph.ainvoke({"messages":[HumanMessage(
        content="明天下午五点从图书馆回三号宿舍，最多绕路十分钟")]},cfg)
    assert "__interrupt__" in state
    assert state["__interrupt__"][0].value["verified"] == []
    assert state["__interrupt__"][0].value["unverified"][0]["estimatedDetourMinutes"] is None
    bad=await graph.ainvoke(Command(resume={"task_id":47}),cfg)
    assert bad["status"] == "invalid_choice"


@pytest.mark.asyncio
async def test_candidate_failure_stops_matching_but_route_failure_falls_back(settings):
    actor=WireSecurity(settings).verify_principal(
        principal(settings.internal_token.get_secret_value(),9,7))
    prompt="明天下午五点从图书馆回三号宿舍，最多绕路十分钟"
    config={"configurable":{"thread_id":"candidate-outage"}}
    result=await build_route_graph(InMemorySaver(),RouteExtractor(settings),
                                  FakeJava([task()],failure="candidate"),actor).ainvoke(
                                      {"messages":[HumanMessage(content=prompt)]},config)
    assert result["status"] == "lookup_unavailable"
    assert "verified" not in result
    result=await build_route_graph(InMemorySaver(),RouteExtractor(settings),
                                  FakeJava([task()],failure="network"),actor).ainvoke(
                                      {"messages":[HumanMessage(content=prompt)]},
                                      {"configurable":{"thread_id":"network-outage"}})
    assert "__interrupt__" in result
    assert not result["__interrupt__"][0].value["verified"]


@pytest.mark.asyncio
async def test_signed_asgi_matching_real_http_with_fallback(settings):
    secret=settings.internal_token.get_secret_value()
    def handler(request):
        if request.url.path.endswith("/candidates"):
            return httpx.Response(200,json={"code":1,"data":{
                "examined":1,"totalBeforeEligibility":1,
                "morePages":False,"records":[task(55,pickup="未知取件点")]}})
        if request.url.path.endswith("/network"):
            return httpx.Response(200,json={"code":1,"data":{
                "campusId":7,"places":[],"edges":[]}})
        return httpx.Response(500)
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler),
                                 base_url="http://java.test/api/") as java_http:
        app=create_app(settings,java_http_override=java_http)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                                         base_url="http://python.test") as client:
                token=principal(secret,9,7)
                req=signed(client,"POST","/internal/v1/route-match",secret,token,
                    payload={"message":"明天下午五点从图书馆回三号宿舍，最多绕路十分钟"})
                reply=await client.send(req)
                assert reply.status_code==200,reply.text
                data=reply.json()
                assert data["status"]=="choose_task"
                assert not data["verified"]
                assert data["unverified"][0]["estimatedDetourMinutes"] is None
                sid=data["session_id"]
                no_ack=signed(client,"POST","/internal/v1/route-match",secret,token,
                    payload={"session_id":sid,"task_id":55})
                declined=await client.send(no_ack)
                assert declined.json()["status"]=="invalid_choice"
                assert (await client.send(req)).status_code==401
                assert (await client.post("/internal/v1/route-match",
                                          json={"message":"route"})).status_code==401
