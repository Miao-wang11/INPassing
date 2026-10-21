# Phase 2 security and API decision record

Baseline: `4451dff9594486828a876998caa0f09d60a9b209` (main).
Phase 1 prerequisite: `9a423eefbc212098be936bf2ddc42442953666cb`.

Java is the sole source of identity: existing JWT interceptor -> BaseContext ->
`CampusCatalogService.requireUserCampus`. `/agent/chat` is JWT protected.
Python only receives a signed short-lived principal; never trusts user input
or unverified identity headers. Python→Java GET requests are authenticated by
an HMAC over method, raw URL path+query, principal, timestamp, and unique nonce.
Java verifies nonce atomically in Redis and revalidates current campus. Signing
does not substitute for network isolation/TLS, and a compromised Python process
with a shared HMAC secret remains inside the trust boundary.

Response projections:
* Tasks: taskId, type, subType, publicDesc (redacted), coarse pickup/delivery
  building strings, reward, tip, deliveryFee, productCost, expireTime.
* Offers: offerId, category, subType, title/description (redacted),
  servicePrice and availability. No provider contact or private note.
* Eligibility: canAttempt + reason. This is tentative and read-only.
* Campus: current approved campus/school label and coordinatesAvailable=false.

Deliberate exclusions:
* **No GET addresses**: stored addresses include private phone/room details.
* **No nearby navigation time**: campus coordinates/route service unverified.
* **No write tools**: posting, booking, accepting, wallet and direct SQL excluded.
* **No persisted consent tokens/idempotency**: phase 3 only.
* **No automatic tool execution by LLM**: phase 3 workflow.
* **No public exposure of the internal interfaces.**

Rollout gate: use a real MySQL+Redis integration environment; test signed
Python→Java HTTP under non-ASCII query values; verify no sensitive text crosses
the model boundary; restrict internal ports to service network; add distributed
Python replay guard and Checkpointer for multi-instance production.
