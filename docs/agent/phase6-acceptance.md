# Stage 6 — Evidence-driven verification and handoff

## Scope and limits

Phase 0-5 work lives in stacked PRs #14-#18, not main. Phase 6 is a
separate branch off Phase 5. Never merge out of order.

Verification categories MUST remain distinct:

1. **Automated:** Python pytest, Java Maven tests, *actual* MySQL concurrency
   and campus isolation integration tests, mobile Node contracts and
   Vue SFC compiler, Docker Compose service startup and unauthorized HTTP
   checks. CI run URL and commit SHA are the only source of pass claims.
2. **Fixture evaluation:** `python -m app.evaluation --input path/to/reviewed.json
   --output out/report.json` runs exactly the production `rank_cards`
   algorithm versus a fixed fee-sorting baseline. Inputs must be
   separately labeled. Synthetic fixtures are for algorithm safety only.
   Do **not** claim measured success rate, precision gain, matching efficiency
   or actual campus ETA accuracy from synthetic examples.
3. **Missing real validation:** an actual paid-model API integration,
   independently measured campus walking-network edges, mobile HBuilderX
   physical device UI, certified-user wallet balance and order/booking
   transaction on an isolated all-service deployment. Requires real,
   approved credentials, fixtures and campus route evidence. These are
   **NOT performed** simply because CI is green.

## Offline evaluation contract

Each JSON array item includes `caseId`, trusted `campusId`,
`network` in the same exact read-only Java Tool shape, `slots`
(origin/destination/departure_at/max_detour_minutes), `candidates`
that **already passed Java visibility and eligibility checks**, and
independently human-reviewed `relevantTaskIds`. No unverified coordinates
can be invented. Never copy real student identities, addresses or pickup
codes into evaluation fixtures.

Metrics: `precisionAtK` and `recallAtK` with K=3, route-evidence
coverage and unknown-route count. Compare against same eligible candidates
sorted by service fee (tip + deliveryFee). The baseline deliberately does
not apply a trusted route hard filter, therefore differences measure the
whole route-filtering approach, not just ranking. If no relevant tasks,
recall is **null**, not 1. If network empty, verified predictions are **0**.
Any improvements need paired real test cases and confidence intervals,
not just one synthetic example.

## Required release walkthrough (not yet complete)

- [ ] Verify the fresh Compose database has the Agent operation and walk tables after init. Historical `20261020` ALTER scripts must **not** run after the full `runningerrands.sql`; use a backup and schema-specific migration check for existing volumes.

- [ ] In an isolated sandbox, complete Java, Python, MySQL and Redis
      startup and valid signed Java↔Python chat exchange for a certified
      mock/test student account; verify old task hall and service storefront.
- [ ] Validate the ten-minute quote expiry round trip on both UTC containers and local Asia/Shanghai business time; MySQL DATETIME must be read as `LocalDateTime` with no implicit timezone conversion.
- [ ] With two certified campus-scoped accounts and non-production fake
      wallet balances, preview an Agent public task; verify no task/order/
      wallet write before explicit confirmation.
- [ ] After Java confirmation, verify real taskId and debit against database,
      then repeat exactly the same operation/token twice and concurrently;
      verify one debit, one task and stored receipt.
- [ ] A second account or different campus cannot see/confirm that draft.
- [ ] Create a *valid* service offer; after quoting, take it offline/change
      its price; verify the transaction rejects, no wallet debit.
- [ ] Put a task in a completed, claimed, expired, or withdrawn state;
      verify final Java accept refuses it with no duplicate order.
- [ ] Temporarily stop the model or map/network endpoint; demonstrate the
      user sees clarification/unverified route rather than false success.
- [ ] Confirm real LLM API structured extraction and fallbacks with an
      approved model key; collect 30+ ambiguous, missing-field and
      adversarial messages without identifying information.
- [ ] Collect reviewed routes from actual campus; quantify estimate error
      and compare with a fixed, human-labeled filtering baseline.
- [ ] Build and open the uni-app in HBuilderX and on actual WeChat
      developer tools/device. Test loading, conversation, quotes, stale
      task state, token refresh, phone safe area and back navigation.
- [ ] Confirm release secret management, TLS ingress ACL, PII redaction,
      DB/Agent checkpoint backup, single-instance limitations and recovery.
- [ ] Review PR #14→#15→#16→#17→#18→phase 6 in order after checks.

## Exit status

**Not production-accepted** until all release walkthrough items above
are executed with evidence. Mock model works only as a deterministic
language parser, not proof that a real LLM works. Never upload real
wallets, credentials or private customer data to GitHub.
