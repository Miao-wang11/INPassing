"""Signed Python→Java HTTP adapter + Tool schema and security tests."""
import json

import httpx
import pytest

from app.clients.java_backend import JavaReadOnlyClient, JavaToolError
from app.main import create_app
from app.security import WireSecurity
from app.tools.read_only import build_read_only_tools
from tests.test_http import principal, signed


@pytest.mark.asyncio
async def test_signed_java_http_and_private_fields_are_not_forwarded(settings):
    secret = settings.internal_token.get_secret_value()
    wire = WireSecurity(settings)
    actor = wire.verify_principal(principal(secret, 17, 7))
    seen = []

    def mock_java(req: httpx.Request):
        signed_headers = {k.lower(): v for k, v in req.headers.items()}
        verified = wire.verify_request("GET", req.url.raw_path.decode("ascii"), signed_headers)
        assert verified.user_id == 17
        assert verified.campus_id == 7
        seen.append(req.url.path)
        # Contract as returned by Java's deliberately privacy-minimized TaskCard.
        return httpx.Response(200, json={
            "code": 1, "data": {"total": 1, "records": [{
                "taskId": 42, "type": "校内代办", "subType": "资料打印",
                "publicDesc": "打印30页", "pickupArea": "图书馆", "deliveryArea": "教学楼",
                "reward": 10.0, "tip": 5.0, "deliveryFee": 5.0, "productCost": 0.0,
                "expireTime": "2026-10-22T16:00:00"
            }]}
        })

    async with httpx.AsyncClient(
        transport=httpx.MockTransport(mock_java), base_url="http://java.test/api/"
    ) as client:
        tools = {t.name: t for t in build_read_only_tools(JavaReadOnlyClient(client, wire), actor)}
        result = await tools["search_public_tasks"].ainvoke({"keyword": "打印"})
    assert result["records"][0]["taskId"] == 42
    assert set(result["records"][0]).isdisjoint({"contactPhone", "pickupCode", "privateNote", "publisherId"})
    assert seen == ["/api/internal/agent/v1/tasks/search"]


@pytest.mark.asyncio
async def test_java_timeout_and_business_failures_fail_closed(settings):
    wire = WireSecurity(settings)
    actor = wire.verify_principal(principal(settings.internal_token.get_secret_value()))

    def timeout(request):
        raise httpx.ReadTimeout("backend host and token must not leak")

    async with httpx.AsyncClient(transport=httpx.MockTransport(timeout),
                                 base_url="http://java.test/api/") as client:
        with pytest.raises(JavaToolError, match="TIMEOUT"):
            await JavaReadOnlyClient(client, wire).get("/internal/agent/v1/offers", actor)

    async with httpx.AsyncClient(
        transport=httpx.MockTransport(lambda r: httpx.Response(403, json={"msg": "private"})),
        base_url="http://java.test/api/",
    ) as client:
        with pytest.raises(JavaToolError, match="UNAUTHORIZED"):
            await JavaReadOnlyClient(client, wire).get("/internal/agent/v1/offers", actor)
        with pytest.raises(JavaToolError, match="TOOL_NOT_ALLOWED"):
            await JavaReadOnlyClient(client, wire).get("/order/accept/42", actor)


@pytest.mark.asyncio
async def test_python_tool_http_integration_and_reject_bad_java_schema(settings):
    secret = settings.internal_token.get_secret_value()
    wire = WireSecurity(settings)

    def java(req):
        if req.url.path.endswith("/offers"):
            return httpx.Response(200, json={"code": 1, "data": {"total": 1, "records": [
                {"offerId": 7, "category": "校内代办", "subType": "资料打印",
                 "title": "打印", "description": "自备纸张", "servicePrice": 3.0,
                 "availableFrom": "2026-10-21T09:00:00", "availableUntil": "2026-10-22T16:00:00"}
            ]}})
        return httpx.Response(200, json={"code": 1, "data": {"total": 1, "records": [{
            "taskId": 9, "type": "代拿餐食", "publicDesc": "拿餐", "pickupArea": "西门",
            "deliveryArea": "宿舍", "reward": 3.0, "privateNote": "DO-NOT-LEAK"
        }]}})

    async with httpx.AsyncClient(transport=httpx.MockTransport(java),
                                 base_url="http://java.test/api/") as java_http:
        app = create_app(settings, java_http_override=java_http)
        async with app.router.lifespan_context(app):
            async with httpx.AsyncClient(
                transport=httpx.ASGITransport(app=app), base_url="http://python.test"
            ) as caller:
                actor = principal(secret, 20, 7)
                req = signed(caller, "GET", "/internal/v1/tools/offers", secret, actor)
                res = await caller.send(req)
                assert res.status_code == 200, res.text
                assert res.json()["records"][0]["servicePrice"] == 3.0
                req = signed(caller, "GET", "/internal/v1/tools/tasks/search", secret, actor)
                bad = await caller.send(req)
                assert bad.status_code == 503 or bad.status_code == 422
                assert "DO-NOT-LEAK" not in bad.text
                # Tampering with the principal or requesting without signature never reaches Java.
                assert (await caller.get("/internal/v1/tools/offers")).status_code == 401
