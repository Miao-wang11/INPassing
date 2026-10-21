# ShunShou AI service: Stage 2 (read-only Tools)

Current version retains the **Phase 1 chat scaffold** (the LLM still cannot book,
publish, accept tasks or even autonomously call tools). Stage 2 adds signed,
explicitly allowlisted, **read-only** campus and task/service tools. No code in
`ai-service` can mutate Java orders, tasks or balances.

## Configuration

Same randomly generated `SHUNSHOU_AI_INTERNAL_TOKEN` (minimum 32 UTF-8 bytes)
must be set on **Java and Python servers**. Never commit it or send it to
uni-app. Java reads it through `runningerrands.agent.shared-secret`.
Java's Python URL uses `SHUNSHOU_AGENT_PYTHON_URL`
(default `http://127.0.0.1:8000`); Python uses
`SHUNSHOU_AI_JAVA_BASE_URL` (default `http://127.0.0.1:8080/api`).
Production requires TLS/private networking and strict network ACLs.

Run Python from `ai-service/` with Python 3.13:

```bash
python -m venv .venv
# Windows: .venv\\Scripts\\activate; Linux/macOS: source .venv/bin/activate
pip install -e '.[dev]'
cp .env.example .env
# Put the SAME fresh 32+ byte secret in the Java process environment.
# Set SHUNSHOU_AI_INTERNAL_TOKEN locally (never commit credentials).
uvicorn app.main:app --host 127.0.0.1 --port 8000
pytest -q
```

Run existing Java backend normally, with `SHUNSHOU_AI_INTERNAL_TOKEN` set in its
environment; existing MySQL and Redis dependencies still apply.
No frontend changes are made in Stage 2.

## Transport contract

`POST /api/agent/chat` is authenticated by **existing Java JWT** using the
`authentication` header. Java calls `CampusCatalogService.requireUserCampus`
and signs a short-lived `v1|userId|campusId|expires|nonce` principal using
HMAC-SHA256. Java calls `POST /internal/v1/chat` at Python with headers
`X-Agent-Principal`, `X-Agent-Timestamp`, `X-Agent-Nonce`,
`X-Agent-Signature`. POST signature also covers the SHA-256 of the raw body.

Python validates signer, expiration, timestamp and nonce before constructing the
`thread_id`. **Python no longer trusts `X-Authenticated-User-Id`**.

Python read-only tools call Java `GET /api/internal/agent/v1/**` with a *fresh*
signed nonce per call. Java validates HMAC, timestamp, replay via Redis SETNX,
then **re-reads approved campus** from the database before setting
`BaseContext` ThreadLocal. Unauthorized requests fail closed with HTTP 401.
Tools never accept userId/campusId as parameters. Graph state isn't shared
between different signed user or campus contexts.

Java internal GET endpoints:
- `/tasks/search` and `/tasks/filter` (public candidates only)
- `/tasks/{taskId}` (public, active, same-campus task detail)
- `/tasks/{taskId}/eligibility` (preview only, never calls ensureForAccept)
- `/offers` and `/offers/{offerId}`
- `/campus` (active campus name, **no fabricated building coordinates**)

Python exposes corresponding signed internal `/internal/v1/tools/...` queries
and 7 typed LangChain `@tool` wrappers in `app/tools/read_only.py`.
These are for **integration verification and later Agent wiring**, not public
student APIs. Business data returned to Python is allowlisted and avoids phone,
room number, pickup code, contact name and private notes. Freeform public text
is best-effort redacted, and remains untrusted (prompt-injection precautions
are still needed when the LLM is eventually connected).

## Verification and known limits

```bash
python -m compileall -q app tests
pytest -q
mvn -B -f ../backend/pom.xml clean verify
```

CI covers Pydantic/auth validation, signature tampering, replay and expiry,
different-user/campus sessions, HTTPX timeout, rejected business response,
schema minimization, Java MockMvc signed endpoint, Java cross-campus checks,
and Java read-only eligibility without runner profile mutation. A real uvicorn
TCP HTTP smoke test remains in the Python suite.

Important remaining work: integration with a real MySQL+Redis+two-process
deployment must be performed before going live. SQLite nonce replay guard on
Python is **single-process only**; do not deploy multiple Python instances
without a distributed replay store and Postgres checkpoint. Freeform public
description redaction is not a comprehensive privacy scrubber.

No draft, confirmation, payment, task publish, offer booking or task accept
API/tool is part of Phase 2.
