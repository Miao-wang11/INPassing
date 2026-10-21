# ShunShou AI Service · Phase 1

This is a **non-transactional service scaffold**, not a live matching agent.
Existing Java services and uni-app code remain unchanged.
No Java business API, SQL, payment, order or booking tool is connected in Phase 1.

## Prerequisites

Python 3.13, then from `ai-service/`:

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -e '.[dev]'
cp .env.example .env
# Set SHUNSHOU_AI_INTERNAL_TOKEN to `python -c "import secrets; print(secrets.token_hex(32))"` output.
uvicorn app.main:app --host 127.0.0.1 --port 8000
pytest -q
```

The default mock responder **always explicitly tells users transactions are unavailable**.
Set `SHUNSHOU_AI_MODEL_PROVIDER=openai_compatible` and `MODEL_API_KEY` to test a
compatible chat-completions provider. No provider key is required for mock tests.

## Routes

- `GET /healthz` — process health
- `GET /readyz` — graph initialized
- `POST /internal/v1/chat` — authenticated scaffold chat

Example (never put this endpoint on public Internet):

```bash
curl -X POST http://127.0.0.1:8000/internal/v1/chat \
  -H "X-Internal-Token: $SHUNSHOU_AI_INTERNAL_TOKEN" \
  -H 'X-Authenticated-User-Id: 123' \
  -H 'Content-Type: application/json' \
  -d '{"message":"明天帮我打印30页"}'
```

`session_id` is a UUID returned on the first turn. Subsequent requests reuse it.
Checkpoints are SQLite for a **single-process MVP only**; do not use it as a
multi-replica coordination or financial/idempotency store. User namespaces in
`thread_id` are separated but the header is trusted only after service token
validation. **Phase 2 must replace this with signed, expiring Java principal
context and private-network transport.** Do not expose the internal API until then.

No payment passwords, raw JWTs or task/contact details should be sent to this
service. Keep model outputs away from execution endpoints. The future Java
adapter remains the only authority for permissions, prices, payments and IDs.

## Verification

- `pytest -q`: unit, persistent checkpoint and ASGI HTTP tests
- `pytest -q tests/test_socket_http.py`: actual TCP/uvicorn smoke test
- `python -m compileall -q app tests`: syntax check
- `mvn -B -f ../backend/pom.xml clean verify`: Java regression gate

The GitHub Actions workflow runs both Python and Java checks on the phase branch.
A real deployment needs a secret manager, rate limits, concurrent-thread control,
request tracing, operation idempotency and a server-side confirmation process in
later stages. No user-visible business claim is made by this skeleton.
