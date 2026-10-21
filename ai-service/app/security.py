"""HMAC transport v1 shared with Java AgentWireSecurity.

Signed principal is issued by Java from the existing JWT context. Never use model
output, request JSON, or a user-id header as the owner of a LangGraph thread.
"""
import asyncio
import base64
import hashlib
import hmac
import re
import secrets
import time
import uuid
from dataclasses import dataclass
from urllib.parse import urlsplit

from fastapi import HTTPException, Request

from app.config import Settings

_HEX = re.compile(r"^[a-f0-9]{64}$")
_NONCE = re.compile(r"^[0-9a-f-]{36}$")


@dataclass(frozen=True)
class Actor:
    user_id: int
    campus_id: int
    principal: str


class WireSecurity:
    def __init__(self, settings: Settings):
        self.secret = settings.internal_token.get_secret_value().encode("utf-8")

    def _mac(self, data: str) -> str:
        return hmac.new(self.secret, data.encode("utf-8"), hashlib.sha256).hexdigest()

    def verify_principal(self, token: str) -> Actor:
        if not token or len(token) > 512 or "." not in token:
            raise ValueError("invalid principal")
        payload, signature = token.split(".", maxsplit=1)
        if not _HEX.fullmatch(signature):
            raise ValueError("invalid principal signature")
        try:
            raw = base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)).decode("utf-8")
            version, uid, campus, exp, nonce = raw.split("|")
            if (
                version != "v1" or not _NONCE.fullmatch(nonce)
                or not hmac.compare_digest(signature, self._mac(raw))
            ):
                raise ValueError("invalid principal")
            user_id, campus_id, expires_at = int(uid), int(campus), int(exp)
        except (ValueError, UnicodeError, OverflowError) as exc:
            raise ValueError("invalid principal") from exc
        now = int(time.time())
        if user_id <= 0 or campus_id <= 0 or expires_at <= now or expires_at > now + 180:
            raise ValueError("expired principal")
        return Actor(user_id=user_id, campus_id=campus_id, principal=token)

    def signed_headers(self, method: str, path_query: str, principal: str) -> dict[str, str]:
        ts = str(int(time.time()))
        nonce = str(uuid.uuid4())
        canonical = "\n".join((method, path_query, principal, ts, nonce))
        return {
            "X-Agent-Principal": principal,
            "X-Agent-Timestamp": ts,
            "X-Agent-Nonce": nonce,
            "X-Agent-Signature": self._mac(canonical),
        }

    def verify_request(self, method: str, path_query: str, headers: dict[str, str], *,
                       body: bytes = b"") -> Actor:
        # Signed request body digest is mandatory for POST to prevent payload replacement.
        actor = self.verify_principal(headers.get("x-agent-principal", ""))
        ts, nonce = headers.get("x-agent-timestamp", ""), headers.get("x-agent-nonce", "")
        signature = headers.get("x-agent-signature", "")
        if method not in ("GET", "POST") or not _NONCE.fullmatch(nonce) or not _HEX.fullmatch(signature):
            raise ValueError("invalid request envelope")
        try:
            if abs(int(time.time()) - int(ts)) > 90:
                raise ValueError("stale request")
        except (ValueError, OverflowError) as exc:
            raise ValueError("invalid request timestamp") from exc
        target = path_query + ("#" + hashlib.sha256(body).hexdigest() if method == "POST" else "")
        expected = self._mac("\n".join((method, target, actor.principal, ts, nonce)))
        if not hmac.compare_digest(expected, signature):
            raise ValueError("invalid request signature")
        return actor


class NonceGuard:
    """Single-process replay fence for the Phase 2 Python/SQLite single-instance deployment."""
    def __init__(self):
        self._seen: dict[str, float] = {}
        self._lock = asyncio.Lock()

    async def consume(self, nonce: str) -> bool:
        async with self._lock:
            now = time.monotonic()
            self._seen = {key: exp for key, exp in self._seen.items() if exp > now}
            if nonce in self._seen:
                return False
            self._seen[nonce] = now + 240
            return True


async def require_actor(request: Request) -> Actor:
    wire: WireSecurity = request.app.state.wire
    headers = {k.lower(): v for k, v in request.headers.items()}
    try:
        path_query = request.url.path
        if request.url.query:
            path_query += "?" + request.url.query
        actor = wire.verify_request(request.method, path_query, headers, body=await request.body())
        if not await request.app.state.nonces.consume(headers["x-agent-nonce"]):
            raise ValueError("replay")
        return actor
    except (ValueError, KeyError):
        raise HTTPException(status_code=401, detail="Invalid internal authentication") from None
