"""Signed, strictly read-only Java client with typed failure categories."""
import logging
import re
import time
import uuid
from typing import Any

import httpx

from app.security import Actor, WireSecurity

logger = logging.getLogger(__name__)
_READ_PATHS = frozenset({
    "/internal/agent/v1/tasks/search",
    "/internal/agent/v1/tasks/filter",
    "/internal/agent/v1/offers",
    "/internal/agent/v1/campus",
})


class JavaToolError(Exception):
    def __init__(self, kind: str):
        self.kind = kind
        super().__init__(kind)


class JavaReadOnlyClient:
    def __init__(self, client: httpx.AsyncClient, wire: WireSecurity):
        self.client = client
        self.wire = wire

    async def get(self, path: str, actor: Actor, params: dict[str, Any] | None = None):
        if path not in _READ_PATHS and not re.fullmatch(
            r"/internal/agent/v1/(?:tasks/[1-9][0-9]*(?:/eligibility)?|offers/[1-9][0-9]*)", path
        ):
            raise JavaToolError("TOOL_NOT_ALLOWED")
        clean_params = {k: v for k, v in (params or {}).items() if v is not None}
        request_id = str(uuid.uuid4())
        trace_id = actor.trace_id or request_id
        start = time.monotonic()
        logger.info("java_readonly_tool_start", extra={
            "request_id": request_id, "trace_id": trace_id, "tool": path})
        try:
            request = self.client.build_request("GET", path, params=clean_params)
            headers = self.wire.signed_headers("GET", request.url.raw_path.decode("ascii"), actor.principal)
            request.headers.update(headers)
            request.headers["X-Request-ID"] = request_id
            request.headers["X-Trace-ID"] = trace_id
            response = await self.client.send(request, follow_redirects=False)
        except httpx.TimeoutException as exc:
            logger.warning("java_readonly_tool_timeout", extra={"request_id": request_id, "trace_id": trace_id})
            raise JavaToolError("TIMEOUT") from exc
        except httpx.RequestError as exc:
            logger.warning("java_readonly_tool_unavailable", extra={"request_id": request_id, "trace_id": trace_id})
            raise JavaToolError("BACKEND_UNAVAILABLE") from exc

        logger.info("java_readonly_tool_done", extra={
            "request_id": request_id, "trace_id": trace_id,
            "http_status": response.status_code, "latency_ms": round((time.monotonic() - start) * 1000)})
        if response.status_code == 401 or response.status_code == 403:
            raise JavaToolError("UNAUTHORIZED")
        if response.status_code >= 500 or response.is_redirect:
            raise JavaToolError("BACKEND_UNAVAILABLE")
        if response.status_code >= 400:
            raise JavaToolError("BUSINESS_REJECTED")
        try:
            document = response.json()
            if not isinstance(document, dict) or document.get("code") != 1 or "data" not in document:
                raise JavaToolError("BUSINESS_REJECTED")
            return document["data"]
        except (ValueError, TypeError) as exc:
            raise JavaToolError("INVALID_RESPONSE") from exc
