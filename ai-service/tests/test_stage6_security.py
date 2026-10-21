"""Phase 6: same opaque session UUID cannot be used across authenticated actors."""
import httpx
import pytest

from app.main import create_app
from tests.test_http import principal, signed


@pytest.mark.asyncio
async def test_delegation_checkpoint_is_scoped_to_authenticated_user_and_campus(settings):
    secret = settings.internal_token.get_secret_value()
    def java(request):
        assert request.url.path.endswith("/offers")
        return httpx.Response(200, json={"code": 1, "data": {"total": 0, "records": []}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(java),
                                 base_url="http://java.test/api/") as backend:
        app = create_app(settings, java_http_override=backend)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                                         base_url="http://python.test") as client:
                a = principal(secret, 301, 7)
                b = principal(secret, 302, 7)
                wrong_campus = principal(secret, 301, 8)
                first = signed(client, "POST", "/internal/v1/delegation", secret, a,
                    payload={"message": "帮我打印30页，取件地点：图书馆，地址ID 10，小费5元"})
                reply = await client.send(first)
                assert reply.status_code == 200, reply.text
                assert reply.json()["status"] == "choose_mode"
                session = reply.json()["session_id"]
                for wrong in (b, wrong_campus):
                    attack = signed(client, "POST", "/internal/v1/delegation", secret, wrong,
                                    payload={"choice": "public", "session_id": session})
                    response = await client.send(attack)
                    assert response.status_code == 409
                resume = signed(client, "POST", "/internal/v1/delegation", secret, a,
                                payload={"choice": "public", "session_id": session})
                success = await client.send(resume)
                assert success.status_code == 200, success.text
                assert success.json()["status"] == "ready_for_java_preview"
                # Java-only quote/confirm endpoints are not exposed by Python.
                assert (await client.post("/api/agent/operations/fake/confirm")).status_code == 404


@pytest.mark.asyncio
async def test_route_resume_for_other_actor_is_refused(settings):
    secret = settings.internal_token.get_secret_value()
    def java(request):
        if request.url.path.endswith("/candidates"):
            return httpx.Response(200, json={"code": 1, "data": {"records": [{
                "taskId": 900, "type": "代取快递", "publicDesc": "测试候选",
                "pickupArea": "未知地点", "deliveryArea": "宿舍",
                "tip": 4, "deliveryFee": 0, "productCost": 0, "reward": 4,
                "expireTime": "2099-01-01T00:00:00",
            }], "morePages": False}})
        if request.url.path.endswith("/network"):
            return httpx.Response(200, json={"code": 1, "data": {
                "campusId": 7, "places": [], "edges": []}})
        raise AssertionError("Unexpected tool path")
    async with httpx.AsyncClient(transport=httpx.MockTransport(java),
                                 base_url="http://java.test/api/") as backend:
        app = create_app(settings, java_http_override=backend)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                                         base_url="http://python.test") as client:
                a, b = principal(secret, 301, 7), principal(secret, 302, 7)
                first = signed(client, "POST", "/internal/v1/route-match", secret, a,
                    payload={"message": "明天下午五点从图书馆回三号宿舍，最多绕路十分钟"})
                reply = await client.send(first)
                assert reply.status_code == 200, reply.text
                assert reply.json()["status"] == "choose_task"
                session = reply.json()["session_id"]
                wrong = signed(client, "POST", "/internal/v1/route-match", secret, b,
                    payload={"session_id": session, "task_id": 900,
                             "acknowledge_unverified": True})
                assert (await client.send(wrong)).status_code == 409
                good = signed(client, "POST", "/internal/v1/route-match", secret, a,
                    payload={"session_id": session, "task_id": 900,
                             "acknowledge_unverified": True})
                done = await client.send(good)
                assert done.status_code == 200, done.text
                assert done.json()["status"] == "ready_for_java_accept"
                assert done.json()["selected"]["estimatedDetourMinutes"] is None
