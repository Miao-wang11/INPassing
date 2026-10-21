# Stage 6 — evaluation, reproducible acceptance, and graduation-demo handoff

## Important distinction: testing scope

This phase uses three noninterchangeable kinds of evidence:

1. **Unit/HTTP regression**: Python LangGraph, HMAC session isolation, Java business adapters, Vue SFC syntax and Java/MySQL concurrency tests, executed in GitHub Actions.
2. **Disposable full-stack HTTP E2E**: Opt-in `SHUNSHOU_DEMO_SEED=true` runs the **original** Java login/TaskService/PaymentService, FastAPI and MySQL/Redis through real HTTP. The script `ai-service/tests/e2e_compose.py` is guarded by `SHUNSHOU_E2E_ISOLATED=yes`, uses an ephemeral CI-generated six-digit demo credential and never prints secrets. The test MUST be green before anyone claims the wallet flow works end to end.
3. **Synthetic offline route regression**: No real campus coordinates or independent travel times are available. `ai-service/evaluation/synthetic_routes.json` contains reviewer-authored artificial directed walkway segments and relevant-task labels; never import into a production campus. A route score is not an LLM matching accuracy metric.

### Baseline and evaluation methodology

Both algorithms receive exactly the same eligible candidate list. Baseline filters type and expiry, then orders by **tip + deliveryFee** (not prepaid total). Matching additionally resolves unique trusted place names, uses Dijkstra on directed verified edges, estimates walking detour with **assumed** 1.25 m/s pace and 4-minute handling allowance, enforces the user's max detour, and scores only connected routes. Unknown routes remain unverified with null ETA/score. An unverified-route example is excluded from relevance precision; it tests safe abstention.

Deterministic fixture version: `2026-10-21-v1`. CI example metrics (NOT REAL-WORLD):
- 3 cases, 2 have independent manual relevance labels; 1 deliberately lacks any route labels
- macro P@2: matching **0.75**, simple fixed baseline **0.25**
- false claims of a known unsafe route being within detour limit: **0**
- synthetic examples do not estimate recall on campus-wide tasks, user satisfaction, acceptance rates, map travel-time error, production throughput or LLM accuracy

Recreate from a clean Python environment:

```sh
cd ai-service
python -m pip install -e '.[dev]'
pytest -q
python -m app.evaluation --fixture evaluation/synthetic_routes.json --output evaluation/report.json
```

The evaluated output includes `source_type=synthetic_offline_fixture` and a disclaimer.
GitHub Actions also uploads `synthetic-route-evaluation` for precise replay.

### Stage 6 acceptance matrix

| Case | Expected authority / outcome | Test location |
| --- | --- | --- |
| Ordinary natural-language printing | extract explicit fields; stop at HITL until user selects public or offer | `test_delegation.py` |
| Ambiguous/missing fees | clarify, do not infer a total budget into service fee | `test_delegation.py` |
| Other user / wrong campus | signed actor & original Java owner/campus checks refuse | `test_http.py`, `AgentReadOnlyServiceTest`, disposable E2E |
| Replay and tampered body/nonce | reject before Graph mutation | `test_http.py`, `AgentMatchingHttpTest` |
| Preview before payment | PREPARED operation, no business order and no wallet debit | `AgentMysqlIdempotencyIT`, disposable E2E |
| Wrong payment password/confirmation token | refuse; no wallet debit | disposable E2E |
| Concurrent or repeated confirm | 1 execution per operation; return original receipt | `AgentMysqlIdempotencyIT`, disposable E2E |
| Expired draft | reject final confirmation | `AgentMysqlIdempotencyIT` |
| Service price changes | reject booked quote rather than silently change totals | `AgentMysqlIdempotencyIT` |
| Wrong offer / service unavailable | no invented recommendation or booking | `test_delegation.py` |
| Task expired, already claimed, or ineligible | Java original `acceptOrder` decides; UI shows stale on failure | `AgentMatchingServiceTest`, `test_route_matching.py` |
| No verified route or campus coordinates | NULL route ETA/score, explain uncertainty | `test_route_matching.py`, evaluation fixture |
| Java Tool unavailable / model timeout | explain degraded state; do not create order | `test_delegation.py`, `test_route_matching.py` |
| Existing five student tabs and other pages | no rewrite; SFC parsed and template compiled | `mobile/tests/validate-agent-ui.mjs` |
| Real installed mini-program/device rendering | **not yet tested** | must be recorded in manual UAT |

### Trace and troubleshooting

Java's `AgentChatController` creates UUID request/trace IDs and HMAC-signs the
principal/body. Python's `require_actor` validates and actor-scopes LangGraph
`thread_id`. `JavaReadOnlyClient` logs only Tool name, request/trace ID,
HTTP status and monotonic duration, not user prompts or private task notes.
Real payment is outside Python; the Java `agent_operation` MySQL row
holds actor/campus, payload hash, hashed confirmation token and final task/order
IDs. For an ambiguous client timeout, use GET `/api/agent/operations/{id}`
from the same JWT, **not** another draft or guessed success.

The existing SQLite checkpointer and local replay fence work only for a
single Python instance. Do not run multiple replicas in the current Compose.

## Student-facing demonstration (isolated accounts only)

**Story A — intelligent delegation.** Log in as an approved demo student,
enter the printing demand, answer missing place/fee/address questions,
view Java's calculated quote and actual expiry. Decline the confirmation to
show no task created. Confirm with the existing Java payment dialog. Requery
the same operation: same real task ID and no duplicate deduction. Show
normal task list and simulated transaction ledger as evidence, not model claims.

**Story B — choose an existing service.** Import an actual test service
into the same verified campus, with availability and quoted price. Show
matched offer and public-task alternative. Intentionally offline the service
before final Java booking; Java must reject it. No fake merchant role or
Python-generated order ID.

**Story C — commute recommendation.** Supply future start/end/time/max
detour. When real approved pedestrian data is absent, emphasize the
`UNVERIFIED` explanation and human-risk acknowledgment; never advertise a
specific ETA. With a separately reviewed (nonproduction) synthetic map,
show why tasks rank or are rejected. Student explicitly clicks accept; a
second competing student taking it first must cause a Java business failure
and refresh hint.

For a job interview, explain **why this is one Agent and multiple workflows**:
Java remains the monetary and permission source of truth, Python is a bounded
interpreter/explainer, MySQL row locks and original Service make transactions
idempotent, and missing real routing data forces abstention. The value is
business integration and controlled transactions, not an invented ML gain.

## Reproducibility and deployment

Apply the one-time migrations in `docs/agent/phase5-uniapp-deploy.md` only
after backing up a real database; never rerun `backend/runningerrands.sql`
on an existing volume. To start a local, nonproduction demo, use
`docker/.env.example` as a template, generate the shared HMAC secret and
explicit `SHUNSHOU_DEMO_PASSWORD` value **outside version control**,
and enable `SHUNSHOU_DEMO_SEED=true` only on a disposable isolated volume.
Use `docker compose --env-file docker/.env -f docker/docker-compose.yml
up --build -d`.

Production requires separate approvals for TLS/private-network rules,
multi-instance replay/checkpoint persistence, rate limiting, data retention,
real LLM provider failures, campus-certified geodata licenses, privacy
review, wallet reconciliation, backup/recovery and device UAT.
No performance, availability or real-world ranking improvement SLA is
claimed from three synthetic route fixtures or one GitHub Actions run.

**Do not merge PRs out of sequence.** Review PR #14 -> #15 -> #16 -> #17
-> #18 -> Stage 6 PR. No Stage 6 code may be merged directly into `main`
until the stacked PR dependencies and separate acceptance gates pass.
