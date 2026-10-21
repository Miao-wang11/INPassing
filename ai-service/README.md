# Stage 3 — Natural-language Delegation (Java-owned transaction)

**Do not apply this branch to production until** the MySQL migration
`backend/migrations/20261021_agent_operation.sql` has been applied, end-to-end
integration with isolated Java/MySQL/Redis/Python has been performed and Stage 1/2
stacked PRs have been reviewed.

## State machine and trust boundary

Student's JWT → `POST /api/agent/chat` → Java validated campus and signed
actor → `POST /internal/v1/delegation` → Python LangGraph.

LangGraph extracts **only explicitly stated** fields, asks follow-up questions,
uses Java read-only service-offer search (no hallucinated offers), then runs a
LangGraph `interrupt` for user selection. `Command(resume=...)` resumes only
the mode choice. It returns a **prefill**, not a transaction or authoritative quote.

A caller holding the original student's JWT must separately call:

1. `POST /api/agent/drafts` with the prefill JSON. Java validates the task
   categories, address ownership, campus, prices, balances and original service
   quote. It creates **only an agent_operation PREPARED row**, not an order.
   Returns operationId, single-draft confirmation token, expiry and Java price.
2. Display the Java quote and the time-limit warning to the student. Only after
   an explicit affirmative click, send `POST /api/agent/operations/{id}/confirm`
   with `confirmToken` and `payPassword` in the Java request. Neither field
   is accepted or persisted by Python. The original `TaskService.publishTask`
   or `ServiceOfferService.book` executes within the Java database transaction.
3. `GET /api/agent/operations/{id}` safely retrieves status and real taskId/
   orderId if a caller times out. Never retry with a new operationId.
   The first committed result is returned to all duplicate confirmations.

Mode examples:
- `{"mode":"PUBLIC_TASK","task":{"type":"校内代办","subType":"资料打印",
  "publicDesc":"打印30页资料","pickupAddress":"图书馆",
  "deliveryAddressId":10,"tip":5,"deliveryFee":0,"productCost":0,
  "expireMinutes":60}}`
- `{"mode":"SERVICE_OFFER","offer":{"offerId":7,"pickupAddress":"图书馆",
  "deliveryAddressId":10,"shoppingBudget":0}}`

Money is never inferred from a total budget: `tip`, `deliveryFee` and
`productCost` must be confirmed separately for public tasks. Service quote
comes ONLY from Java, protected by a locked row compare during booking.
`expireMinutes` is the accept window, **not** a promised delivery deadline.
Only owned `deliveryAddressId` is accepted, no unrestricted contact fields.

The `agent_operation` row lock `SELECT ... FOR UPDATE` serializes same-ID
concurrent confirmations. The task/order, wallet operation, and SUCCEEDED
receipt share the existing MySQL transaction. The original service quote and
wallet constraints are checked a second time at execution. If execution
throws, the Java transaction rolls back and the draft remains PREPARED until
its expiry. Redis or LangGraph checkpoint is never relied on for money
idempotency.

Current deterministic Mock extraction is deliberately conservative and
demonstrates clarification; when an API key is configured, ChatOpenAI's
`with_structured_output(DemandPatch)` adds flexible language extraction.
Timeout/failure falls back to explicitly limited rules, never writes. Real
backend privacy and cross-campus checks remain Stage 2 security boundaries.

## Verification

```bash
cd ai-service && pip install -e '.[dev]' && pytest -q
mvn -B -f backend/pom.xml clean verify
# CI also runs AgentMysqlIdempotencyIT against real MySQL/InnoDB.
```

**Limitations:** No uni-app screens until Stage 5; confirm APIs can be exercised
via authenticated HTTP. This is not a fully integrated production deployment.
No routes, coordinates or estimated detour for Stage 4. Checkout quoting in
existing flows is preserved, but strict delivery-deadline enforcement does not
exist in the original task table. Transaction-local tests mock the actual
TaskService; full real-balance debit integration with live isolated user
fixtures remains a Stage 6 validation requirement. Python is still a
single-instance SQLite Checkpointer prototype; no claim of horizontally
scalable HITL before Postgres/Redis deployment.

---

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
