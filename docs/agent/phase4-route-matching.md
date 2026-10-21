# Stage 4 — Verified-campus walking-route task matching

Stage 4 is **not a navigation service** until a campus administrator supplies
independently measured route-network data. Nothing in mobile's campus-data.js
constitutes measured GPS or real walking-network data. Do not infer lat/lon
from campus names. The new migration creates EMPTY tables deliberately;
there are **zero fabricated coordinates/distances**.

## Flow and trust
Student JWT -> Java POST /api/agent/route-match -> signed Python
POST /internal/v1/route-match -> LangGraph extracts:
- origin/destination building names, future departure time and max detour minutes
- optional preference restricted to original five TaskTypeConstant categories

Java GET /api/internal/agent/v1/matching/candidates reuses original
TaskService.searchTasks() + read-only AgentReadOnlyService.eligibility()
checking trusted campus, candidate current state/expiry, own tasks,
student certification/sex and runner capacity, online/ban/credit. Existing
sensitive contacts, pickup codes, room numbers are not returned; previews
can race, so no eligibility decision is final.

Java GET /api/internal/agent/v1/matching/network returns current **verified
campus only** buildings and directed pedestrian path segments measured by
administrators. Campus ID always comes from signed, approved Java principal.
Non-verified, inactive, cross-campus and unknown nodes/edges are dropped.

Python Dijkstra walking distances:
baseline = route(start, destination)
via = route(start, pickup)+route(pickup, delivery)+route(delivery, destination)
extra = max(0, via-baseline) / **assumed** 1.25 m/s / 60 + **assumed**
4 min exchange time. Only if all 4 routes are connected and named places
uniquely resolve can the result be reported as an **estimate**. No guarantee
of real elapsed time, item weight, queueing, lift/elevation or operating hours.
If any route is unknown, return `unverified` with `estimatedDetourMinutes=null`
and `score=null` and require explicit risk acknowledgment. Never mark the
task as passing the 10-minute constraint.

Hard filters: same campus; unreserved, waiting and not expired; not own task;
certified student + runner profile eligibility; task accept expiry after
planned departure; optional chosen task type; estimated detour <= requested
cap **only where calculated**. Do NOT use `reward` (prepaid total including
shopping budget) as runner earnings. `serviceFee=tip+deliveryFee`.
Score (0–100) = 65*(1-extra/limit) + 25*min(serviceFee/10,1) + 10
for explicit type preference (5 neutral). Scores only for estimated-route
tasks. All assumptions and reason text are returned with each card.

## Explicit selection and final Java acceptance
LangGraph `interrupt()` presents cards; `Command(resume)` only returns
`selected` and `acceptEndpoint=/agent/matches/accept/{taskId}`.
Python NEVER calls a business write Tool. When user explicitly confirms,
frontend (Stage 5) must use existing JWT to POST the Java accept endpoint.
That endpoint directly reuses TaskOrderService.acceptOrder(), with the
original Redis distributed lock, task CAS, campus, identity and availability
checks. On conflicting task/expiry/eligibility failures, show the original
business rejection and suggest refreshing recommendations. No idempotent
success/claimed order is fabricated on a failed call.

## Deployment and data registration
Apply `backend/migrations/20261021_agent_walk_network.sql` on isolated DB.
Create evidence-backed `agent_walk_place` rows for actual approved campus
and `agent_walk_edge` rows with measured pedestrian route distances,
verified date and human-review source note. Each physical direction must be
independently represented; absence of an edge implies unknown, not
bidirectional walking. There is no agent or public endpoint to mutate
verified graph data. Import via controlled DBA process after field survey
or vetted pedestrian routing provider data is licensed and verified.

Route requests work without data but will show no verified ETA-based ranks.
Do not claim the mock graph test distances are real. SQLite Checkpointer and
Python replay nonce fence remain SINGLE-PROCESS prototypes from earlier
stages; Stage 5 deployment must address horizontal scaling and isolation.

## Tests
GitHub Actions: Python pytest includes graph/resume, route parsing, Dijkstra
directed edges, no-data fallbacks, expiry/budget/score, real signed FastAPI
HTTP, backend timeout. Java Maven includes request HMAC+replay and
cross-campus eligibility. A real isolated MySQL 8 test loads **synthetic
test-only** approved campus graph fixture and checks same-campus SQL
enforcement. No production university walking-network dataset is installed.

Stage 4 intentionally contains no uni-app cards, map visualization,
Docker orchestration, complex ML model, direct Python SQL, or new agent
Supervisor. It must be evaluated against a simple location/task-type
filtering baseline using labeled real examples in Stage 6.
