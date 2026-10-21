import uuid

import httpx
import pytest

from app.main import create_app


@pytest.mark.asyncio
async def test_actual_asgi_http_auth_and_multiturn(settings):
    app = create_app(settings)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            assert (await client.get("/healthz")).json() == {"status": "ok", "phase": 1}
            assert (await client.get("/readyz")).status_code == 200
            endpoint = "/internal/v1/chat"
            msg = {"message": "明天打印30页"}
            assert (await client.post(endpoint, json=msg)).status_code == 401
            bad = {"X-Internal-Token": "wrong", "X-Authenticated-User-Id": "3"}
            assert (await client.post(endpoint, json=msg, headers=bad)).status_code == 401
            headers = {
                "X-Internal-Token": settings.internal_token.get_secret_value(),
                "X-Authenticated-User-Id": "3",
            }
            assert (await client.post(endpoint, json={"message": "   "}, headers=headers)).status_code == 422
            first = await client.post(endpoint, json=msg, headers=headers)
            assert first.status_code == 200, first.text
            data = first.json()
            assert data["turns"] == 1
            assert data["status"] == "scaffold"
            assert "不能发布任务" in data["answer"]
            uuid.UUID(data["session_id"])
            second = await client.post(
                endpoint, json={"message": "送到教学楼", "session_id": data["session_id"]}, headers=headers
            )
            assert second.status_code == 200, second.text
            assert second.json()["turns"] == 2
            other_headers = {**headers, "X-Authenticated-User-Id": "4"}
            other = await client.post(
                endpoint, json={"message": "其他人的对话", "session_id": data["session_id"]},
                headers=other_headers,
            )
            assert other.json()["turns"] == 1
