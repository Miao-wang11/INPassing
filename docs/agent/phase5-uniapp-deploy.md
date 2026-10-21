# Stage 5 — uni-app integration and Compose deployment (review before enabling writes)

## Real student-side integration

Entry: homepage has two additions; original home search, task hall,
service storefront, publishing, wallet and existing five tabBar entries
remain intact. Page in **separate mini-program subpackage**:
`pages-agent/assistant/assistant.vue`.

Mobile API `mobile/api/agent.js` uses the EXISTING `authentication` JWT
header from `mobile/utils/request.js` and calls Java only:

- POST `/api/agent/chat` — text/choice -> LangGraph delegated demand
- POST `/api/agent/route-match` — commute clarification/task selection
- POST `/api/agent/drafts` — **Java-owned** immutable operation preview
- POST `/api/agent/operations/{id}/confirm` — Java password+token confirmation
- GET `/api/agent/operations/{id}` — same operation status after uncertain timeout
- POST `/api/agent/matches/accept/{taskId}` — original Java acceptance Service

**No student browser hits Python**. Agent proxy wraps Python's successful
`{status,session_id,...}` into Java's existing `{code:1,data:{...}}`
envelope, for compatibility with shared `request.js`. Invalid Python
status/errors fail closed, not success.

Delegation UI keeps the original draftId/token in memory only; no password
or token is placed in localStorage, a chat message, Graph state or Python.
Address is chosen via existing authenticated address list; only owned
`deliveryAddressId` is mentioned to Agent, and Java verifies address owner
again. Users explicitly provide tip, delivery fee and goods budget; total
budget is never converted automatically. PREPARE creates only operation
draft, not task/order. Confirm uses the project's existing
`promptPayPassword` component and an explicit confirmation modal. A
failed/uncertain confirm retains the SAME operationId; frontend queries
Java receipt instead of creating a fresh draft or claiming success.
True success requires `status=SUCCEEDED` with real taskId.

Route UI separates verified walking graph estimates from unknown routes.
Unknown has **null** detour/score and needs another explicit warning
acknowledgment. Task choice never accepts the task; explicit final click
calls original Java Service. If another runner won, show stale and ask
the user to refresh. See Phase 4 limitations regarding missing real
campus pedestrian data, weight/queues/time assumptions.

## Docker Compose local setup

### Before starting

1. Copy `docker/.env.example` to `docker/.env`. Generate a random
   shared HMAC key using `python -c "import secrets; print(secrets.token_hex(32))"`
   and set `SHUNSHOU_AI_INTERNAL_TOKEN` (same value injected into backend
   and Python; NEVER commit .env).
2. Set unique strong MySQL/JWT/admin passwords.
3. **Fresh empty DB volume:** `backend/runningerrands.sql` is already a
   FULL schema containing the historical `20261020` campus, service-offer,
   dispute and operations-index changes. Docker runs that file **once**, then
   only `20261021_agent_operation.sql` and
   `20261021_agent_walk_network.sql`. Never add `20261020` ALTER scripts
   after the full base schema; a duplicate `campus_id` aborts MySQL init
   and leaves Agent tables missing.
   **Existing populated volume:** Docker init scripts do not rerun. Back up
   first, inspect actual table/column/index migration state, then selectively
   apply ONLY genuinely missing migrations in chronological order.
   DO NOT rerun `runningerrands.sql`, delete the MySQL volume, or attempt
   blanket non-idempotent ALTER scripts.
4. For mock local demonstration: `SHUNSHOU_AI_ENVIRONMENT=development`,
   `SHUNSHOU_AI_MODEL_PROVIDER=mock` (deterministic limited parser,
   **not full language model capability**). For production, set
   `SHUNSHOU_AI_ENVIRONMENT=production`,
   `SHUNSHOU_AI_MODEL_PROVIDER=openai_compatible`, provide actual model
   key/base URL. Never send the key to uni-app.
5. Use `docker compose --env-file docker/.env -f docker/docker-compose.yml config`
   to validate variables, then
   `docker compose --env-file docker/.env -f docker/docker-compose.yml up --build -d`.
   No Python port is published; only service network `rr-net` reaches it.
   Python deliberately uses **one worker**, SQLite checkpointer on named
   `agent_checkpoint` volume and process-local nonce guard. Keep it single
   instance. Java/MySQL/Redis remain the same services as before.
6. Verify using
   `docker compose --env-file docker/.env -f docker/docker-compose.yml exec -T ai-service python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/readyz').read().decode())"`
   and Java health `http://localhost:8080/api/actuator/health`
   if the existing actuator endpoint is enabled.
   Test actual chat/quote/accept only with an isolated certified test account,
   simulated balance and intentionally created test tasks.

### Deployment constraints

This Compose extension is a **single-node, development demonstration**,
not a hardened production release: no direct Python port, no Redis
replication/HA, SQLite graph snapshots are single-instance, same shared
HMAC secret is injected into both trusted processes, and current backend/
nginx port bindings may be accessible outside localhost. Production
requires TLS/reverse-proxy ACLs, secret manager, distributed replay fence/
checkpoint, backups, regulated fake-payment isolation and real campus route
data verification. Never claim fully operational multi-node architecture.

### Checks

```sh
cd mobile && node --test tests/agent-contract.test.mjs
# SFC parser/compiler validation in CI, using @vue/compiler-sfc installed by admin npm ci
mvn -B -f backend/pom.xml clean verify
cd ai-service && pytest -q
docker compose --env-file docker/.env -f docker/docker-compose.yml config --quiet
```

Mobile package.json has no official `uni-app` build script/dependencies;
compiling via HBuilderX/uni-app tooling remains a **manual device smoke
test**, NOT a fake npm build passing CI. Complete real device, MySQL and
Redis multi-process tests before declaring the product release-ready.
