"""Phase 1 chat regression under Phase 2 signed principal transport."""
import base64
import hashlib
import hmac
import json
import time
import uuid

import httpx
import pytest

from app.main import create_app


def principal(secret: str, user_id=3, campus_id=11, expires=None):
    payload = f"v1|{user_id}|{campus_id}|{expires or int(time.time()) + 100}|{uuid.uuid4()}"
    token = base64.urlsafe_b64encode(payload.encode()).decode().rstrip("=")
    signature = hmac.new(secret.encode(), payload.encode(), hashlib.sha256).hexdigest()
    return token + "." + signature


def signed(client, method, path, secret, actor, *, payload=None, params=None):
    req = client.build_request(method, path, json=payload, params=params)
    ts = str(int(time.time()))
    nonce = str(uuid.uuid4())
    target = req.url.raw_path.decode("ascii")
    if method == "POST":
        target += "#" + hashlib.sha256(req.content).hexdigest()
    canonical = "\n".join((method, target, actor, ts, nonce))
    sign = hmac.new(secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
    req.headers.update({
        "X-Agent-Principal": actor, "X-Agent-Timestamp": ts,
        "X-Agent-Nonce": nonce, "X-Agent-Signature": sign
    })
    return req


@pytest.mark.asyncio
async def test_actual_asgi_http_auth_multiturn_and_replay(settings):
    app = create_app(settings)
    secret = settings.internal_token.get_secret_value()
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            assert (await client.get("/healthz")).json() == {"status": "ok", "phase": 2}
            assert (await client.get("/readyz")).status_code == 200
            path = "/internal/v1/chat"
            payload = {"message": "明天打印30页"}
            assert (await client.post(path, json=payload)).status_code == 401
            old = {"X-Internal-Token": secret, "X-Authenticated-User-Id": "3"}
            assert (await client.post(path, json=payload, headers=old)).status_code == 401
            actor = principal(secret)
            blank = signed(client, "POST", path, secret, actor, payload={"message": "   "})
            assert (await client.send(blank)).status_code == 422
            first = signed(client, "POST", path, secret, actor, payload=payload)
            response = await client.send(first)
            assert response.status_code == 200, response.text
            data = response.json()
            assert data["turns"] == 1
            assert "不能发布任务" in data["answer"]
            uuid.UUID(data["session_id"])
            assert (await client.send(first)).status_code == 401  # same request nonce
            second = signed(client, "POST", path, secret, actor,
                            payload={"message": "送到教学楼", "session_id": data["session_id"]})
            assert (await client.send(second)).json()["turns"] == 2
            # A forged header or body userId does not affect the signed principal.
            forged = signed(client, "POST", path, secret, actor,
                            payload={"message": "another turn", "userId": 999, "session_id": data["session_id"]})
            forged.headers["X-Authenticated-User-Id"] = "999"
            assert (await client.send(forged)).json()["turns"] == 3
            other = signed(client, "POST", path, secret, principal(secret, 4, 11),
                           payload={"message": "other", "session_id": data["session_id"]})
            assert (await client.send(other)).json()["turns"] == 1
            campus = signed(client, "POST", path, secret, principal(secret, 3, 12),
                            payload={"message": "other campus", "session_id": data["session_id"]})
            assert (await client.send(campus)).json()["turns"] == 1
            tampered = signed(client, "POST", path, secret, actor, payload={"message": "valid"})
            tampered.headers["X-Agent-Signature"] = "0" * 64
            assert (await client.send(tampered)).status_code == 401
            expired = principal(secret, expires=int(time.time()) - 5)
            req = signed(client, "POST", path, secret, expired, payload=payload)
            assert (await client.send(req)).status_code == 401
            invalid_body = signed(client, "POST", path, secret, actor, payload=payload)
            invalid_body.content = b'{"message":"tampered"}'  # signature binds to the original JSON
            assert (await client.send(invalid_body)).status_code == 401
