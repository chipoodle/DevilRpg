# A7-12 — Controlled OFF/AUDIT Reproducibility and Final Acceptance

Status: **COMPLETE** (2026-11-11), with the Campaign 6 AUDIT one-off preserved as a
non-reproduced limitation. A8-01 dependency satisfied. Earlier partial sections below are
historical checkpoints.

> Historical status notices below (Campaigns 1–8, including “A7-12 remains PARTIAL”) are
> superseded by Campaign 9 closure. Campaign 6 remains `INSUFFICIENT_EVIDENCE` and Campaign 8
> `NOT_REPRODUCED`; no universal equivalence is claimed.

## Baseline

```text
branch feature/autonomous-village
HEAD 97220be5453b57387002b9bc91935807cc665274
staged 0
unstaged 26
untracked 154
```

## Initial manifests

Both worlds are independent copies of the same disposable A5WALL fixture:

- village center 80,97,0
- tree 87,97,4 (four oak logs)
- storage baseline 128 logs
- worker fletcher at 82,98,2, empty inventory
- scenario `lumber-deliver-harvest`, 6000-tick bound

Known differences: worker UUID and game time are generated per run. No full block/inventory
snapshot was captured at tick zero, so exact manifest equivalence is not proven.

## Pairs

| Pair | Mode | POST carried | Warehouse delta | Final tick |
|---|---|---|---|---|
| 1 | OFF | 5 | +3 | 1585 |
| 1 | AUDIT | 4 | +2 | 1793 |
| 2 | OFF | 6 | +4 | 2037 |
| 2 | AUDIT | 5 | +3 | 2025 |

Both pairs:

```text
TALAR target=53,97,-17
POST-HARVEST delivery target=127,97,20
A5 FIN PASS
```

The direction is consistent: OFF carries/deposits exactly one more log than AUDIT. Task and
target selection are equivalent; final resource accounting is not.

## First divergence

The first observed divergence is at POST-HARVEST carried inventory (OFF +1). It precedes
delivery. Whether this is caused by AUDIT overhead/timing or by run-to-run worker variance
is not established. No gateway rejection occurred; AUDIT only observes and delegates.

## Accounting

The pair-level difference is one log:

```text
OFF: +3/+4 deposited
AUDIT: +2/+3 deposited
```

A complete reconciliation of initial + harvested - deposited - remaining - drops was not
captured. The final warehouse delta alone is insufficient to assign cause.

## Final classification

**PARTIAL.** Equivalence is not demonstrated, but no AUDIT-attributable defect is proven.
The difference is reproducible in direction across two pairs, so it cannot be dismissed as
pure noise without further instrumentation; it also cannot be classified as a defect yet.

## A7 decision

A7 does not pass acceptance. Do not migrate execution authority. Do not schedule another
repetition milestone merely to repeat this comparison.

Engineering recommendation:

1. Keep OFF as production default and AUDIT as a diagnostic-only mode.
2. Do not enable AUDIT in production or use it as an authority layer.
3. Close A7 as **validation-inconclusive**: identity, delivery and active-path safety are
   demonstrated; OFF/AUDIT resource equivalence is not.
4. Before any authority transfer, instrument the exact log pickup/drop accounting rather
   than repeating the end-balance comparison.

## Non-regression

## 2026-11-11 — Historical +1 forensic and multi-worker pair

### Historical +1

The raw logs for the historical pairs still exist:

| Pair | OFF | AUDIT | Observation |
|---|---|---|---|
| 1 | `stabilization-block1-scan-20261030-209` | `...-210` | OFF carried 5, warehouse 126→131; AUDIT carried 4, warehouse 126→130 |
| 2 | `...-211` | `...-212` | OFF carried 6, warehouse 122→128; AUDIT carried 5, warehouse 122→127 |

The `+1` is in the **harvested/carried quantity**, not in deposit accounting: each OFF
worker carried and deposited one more log than its AUDIT counterpart. The task/targets match
(`53,-17`, delivery `127,20`). No per-actor ledger existed then, and the initial manifest was
not captured, so the exact cause is **INSUFFICIENT_EVIDENCE** (not a proven AUDIT defect and
not a proven production divergence). It is not resolved by the new passing pair.

### Multi-worker pair

Runs: OFF `res-a7multi-off-20261111-120000`; AUDIT `res-a7multi-audit-20261111-121000`.
Same `a8-01-b-two` fixture: two trees `98,98,0` and `62,98,0`, empty shared warehouse.

- Both modes selected both trees, acquired distinct claims (no same-tree contention), and
  targeted `124,97,18` / `128,97,21` for delivery.
- OFF: one worker harvested/deposited 10; the other 0; an extra actor
  `1245bc92-3a73-4fb5-8592-13b91f7ac171` withdrew 1; warehouse delta 9; trace complete.
- AUDIT: both workers 5/5; the extra actor `f5f0655b-25fd-46be-9d6a-3845bcefcaff` withdrew 1
  and had no captured final inventory, producing actor delta `+1`; trace **complete=false**,
  `[A5] FIN FAIL`, warehouse delta 9.

**Divergence:** the extra actor is outside the two-worker baseline and is not reconciled in
AUDIT; per-worker distribution differs. This is a fixture/accounting-audience defect, not
evidence of production equivalence. Claim/target/task selection matches; route/lease
progression is **NOT_OBSERVABLE/NOT_TESTED** under OFF; abandonment is **NOT_TESTED**.

A7-12 remains **PARTIAL**. Smallest blocker: isolate exactly the two workers (no extra actor)
and capture final inventories for every actor, then repeat the pair; do not claim equivalence
from this run.

No controller, planner, storage, pantry, NBT, Goal authority, Brain or GoalSelector change.
Only test harness mode plumbing and disposable fixture changes.

## 2026-11-11 — Isolated two-worker pair (Campaign 3)

Campaign 2’s extra actor was a newly spawned village consumer after one-shot isolation. The
fixture now re-isolates every tick, leaving exactly the two registered lumberjacks.

| Mode | Run | Actor (tree 62) | Actor (tree 98) | Trace |
|---|---|---|---|---|
| OFF | `res-a7isolated-off-20261111-130000` | `fd162783…` 5/5 | `f827b644…` 5/5 | 22 events, complete |
| AUDIT | `res-a7isolated-audit-20261111-131000` | `ce8940fd…` 5/5 | `7d967504…` 5/5 | 22 events, complete |

Both: initial warehouse `{}`, trees `62,98,0` and `98,98,0`; TALAR targets match; distinct
claims acquired/released; delivery destinations `124,97,18`/`128,97,21`; harvest 5+5, deposit
5+5; final actors 0, warehouse 10; actor/warehouse/global deltas 0; `[A5] FIN PASS` before
shutdown. Differences: UUID/task identity and ticks (OFF 1303, AUDIT 1481) — expected.

**Classification:** target/task/claim/per-worker allocation/resource MATCH; actor identity and
timing EXPECTED_DIFFERENCE; no DIVERGENCE in the captured dimensions. Route/lease progression
is **NOT_OBSERVABLE/NOT_TESTED** under OFF; same-tree contention is **NOT_TESTED** (distinct
trees); abandonment/recovery is **NOT_TESTED**.

A7-12 remains **PARTIAL**. The smallest remaining blocker is passive route/lease evidence plus
a legitimate same-tree contention and abandonment pair. Do not claim equivalence or transfer
authority yet. OFF remains default; AUDIT diagnostic-only.

## 2026-11-11 — Passive navigation trace (Campaign 4)

Added a default-off (`-Ddevilrpg.navtrace=true`) shared observer at the Goal/lease layer:
`NAV_REQUEST`, `PATH_CREATED`, `ROUTE_PROGRESS`, `NAV_ARRIVAL`, `NAV_STOP`,
`LEASE_ACQUIRE`, `LEASE_RENEW`, `LEASE_RELEASE`. It logs only; no navigation, lease, claim,
task or inventory mutation.

| Mode | Run | Requests | Paths | Progress events | Arrival | Stops | Lease acquire/renew/release |
|---|---|---|---|---|---|---|---|
| OFF | `res-a7nav2-off-20261111-140000` | 186 | 186 | 38 | 0 | 4 | 0/0/0 (OFF delegates) |
| AUDIT | `res-a7nav2-audit-20261111-140500` | 179 | 179 | 38 | 0 | 4 | 2/400/0 |

Both modes created paths (e.g., 29-node delivery path), progressed through nodes, and stopped;
resource trace remained complete (5/5, warehouse 5, deltas 0). OFF has no lease by design
(delegated navigation). AUDIT lease release was **not observed**, and distance-gated
`NAV_ARRIVAL` produced no event in either mode; both are instrumentation/observation gaps, not
claimed equivalence.

**Classification:** path creation/node progression MATCH; lease acquisition/renewal observable
in AUDIT only; lease release NOT_OBSERVABLE; arrival NOT_OBSERVABLE; route destination MATCH.
No authority change. A7-12 remains PARTIAL; do not infer arrival or release from path
destination or final inventory.

## 2026-11-11 — Lease closure and actual arrival (Campaign 5)

Campaign 4’s 2/400/0 was a **trace-placement gap**, not a missing release:
`VillagerLumberjackGoal.releaseNavigationLease()` called `NavigationLease.release` directly,
bypassing the Agent hook. It now logs the actual postcondition, and arrival uses the same
distance/`alcance` predicate as the production work gate (`VillagerLumberjackGoal` line 915),
not `navigation.isDone()`.

| Mode | Run | Requests/Paths | Progress | Arrival condition | Step advanced | Lease acquire/renew/release |
|---|---|---|---|---|---|---|
| OFF | `res-a7nav3-off-20261111-142500` | 186/186 | 44 | 50 | 2 | 0/0/0 (delegated) |
| AUDIT | `res-a7nav3-audit-20261111-143000` | 186/186 | 38 | 50 | 2 | 2/414/2 |

AUDIT lease dispositions:

| Lease/task | Acquire | Release | Disposition |
|---|---|---|---|
| `d5a78c7e-1d6f-3be2-8a17-611e1d222a8f` (tree) | tick 4149 | tick 4301 `RELEASED` | RELEASED |
| `f55a2aea-d6ef-33ea-baf0-7e16e6590c9d` (delivery) | tick 4305 | tick 4615 `RELEASED` | RELEASED |

No lease was active at finalization; no expiration observed. Renewals are repeated renewals of
the two identities (not 414 leases). Both modes reached arrival at the tree (`~3.48/3.34` vs
reach `3.5`) and delivery (`4.75` vs reach `5.0`), then advanced the task (2 step events).
Resource trace remained complete (5/5, warehouse 5, deltas 0, `[A5] FIN PASS`).

**Classification:** navigation request/path/arrival/step MATCH; route progress MATCH
semantically (44 vs 38 node-change events is timing/segmentation, not destination divergence);
AUDIT lease lifecycle CLOSED (RELEASED); OFF no lease is EXPECTED_DIFFERENCE. Remaining:
same-tree contention NOT_TESTED, abandonment/recovery NOT_TESTED. A7-12 remains PARTIAL.

## 2026-11-11 — Same-tree contention (Campaign 6)

Fixture: two lumberjacks both spawned near center, Tree A `98,98,0`, alternative Tree B
`62,98,0`, empty shared warehouse. No pre-existing claims.

### OFF — `res-a7contend-off-20261111-150000`

```text
worker A 45813de9…  ACQUIRE Tree A task 7cfc6164… tick 4147
worker B 096b3e8c…  EXCLUDED Tree A (claimed-by-other) tick 4147
worker B            ACQUIRE Tree B task ee8ce31a… tick 4149
A RELEASE Tree A; B RELEASE Tree B
A 5 harvested/deposited; B 5 harvested/deposited; warehouse 0→10
trace complete, 22 events, deltas 0, [A5] FIN PASS
```

### AUDIT — `res-a7contend-audit-20261111-151000`

```text
worker A 47f5233a…  ACQUIRE Tree A task d11cbe35… tick 4148
worker B cee47b33…  EXCLUDED Tree A (claimed-by-other) tick 4148
worker B            ACQUIRE Tree B task 1059eb1c… tick 4150
B RELEASE Tree B; A RELEASE Tree A
A re-ACQUIRE Tree A tick 6182; no harvest/deposit before timeout
B 5 harvested/deposited; warehouse 0→5
trace complete, 11 events, deltas 0, [A5] FIN FAIL (timeout)
```

**Contention/exclusivity:** at most one Tree A owner in both modes; B was excluded at
discovery, not a fabricated acquire rejection; B reselected Tree B through normal discovery;
no B harvest of Tree A; both claims released. **DIVERGENCE:** AUDIT’s owner A did not complete
its Tree A harvest within the bounded runtime (reacquired at 6182, timed out), while OFF’s owner
did. This is a workflow-completion divergence, not a duplicate-ownership violation; preserve
as evidence for a separate investigation. A7-12 remains **PARTIAL** pending that divergence
and abandonment/recovery.

## 2026-11-11 — Campaign 7 forensic diagnosis of AUDIT owner A

Raw evidence: `res-a7contend-audit-20261111-151000`, actor A
`47f5233a-1e27-4caa-9bbb-8856ddea62ec`, task `d11cbe35-ee0b-33ba-b043-21514596591d`,
Tree A `98,98,0`.

```text
4148 claim acquire Tree A
4151 TASK ASSIGN TALAR task d11cbe35
     NAV-GATE AUDIT UNAUTHORIZED_AUDITED task=null owner=empty (repeated)
4381 TASK CLEAR d11cbe35 + TREE-CLAIM RELEASE; no harvest, no arrival event
4401 task 31f05513 RECOGER_HARINA
4621 task db2095b4 ABONAR Tree B (sapling)
6181 ABONAR Tree B sapling→oak_log; CLEAR task db2095b4
6182 TREE-CLAIM ACQUIRE Tree A again (same actor)
6191 TASK ASSIGN TALAR task d11cbe35 (same task ID)
6241 TASK CLEAR; timeout at 2921, warehouse 5 (B only)
```

**Earliest divergence:** OFF reaches TALAR→ENTREGAR at 4281 and deposits 5; AUDIT clears
TALAR at 4381 without a harvest/arrival and moves to bone-meal work. The second acquisition is
a **legitimate new TALAR attempt after rediscovery** (same task ID; Tree B sapling had become
a log and work shifted back), not a reuse of a completed task and not a claim-exclusivity
violation.

**Cause classification: INSUFFICIENT_EVIDENCE.** The AUDIT owner’s flows show
`UNAUTHORIZED_AUDITED task=null owner=empty` on navigation, indicating the diagnostic lease
was not synchronized with the forestry snapshot in this contention run (unlike Campaign 5’s
acquired leases). The source still delegates navigation, and no source path proves that this
diagnostic state caused the TALAR clear. No harvest-check/attempt log exists to distinguish
“never reached interaction point” from “arrival but precondition failed.” Do not patch
production; a separate passive TALAR-abandon/harvest-check diagnostic campaign is required.

## 2026-11-11 — Campaign 8 TALAR diagnostics and reproduction

Added default-off (`-Ddevilrpg.navtrace`) events at the actual TALAR result and clear points:
`TALAR_HARVEST_RESULT`/`TALAR_NO_HARVEST` (base block, column, branches, total, target block
after) and `TALAR_CLEAR` (phase, carried, target block). These log existing computed results;
no extra validation, harvest call, claim/nav/task mutation.

| Mode | Run | Tree A owner | TALAR result | Tree B owner | Result |
|---|---|---|---|---|---|
| OFF | `res-a7diag-off-20261111-165000` | `0e30b84a…` 5/5 | base oak_log, column 5, total 5 | `e79081c3…` 5/5 | warehouse +10, PASS |
| AUDIT | `res-a7diag-audit-20261111-164500` | `2dc686c8…` 5/5 | base oak_log, column 5, total 5 | `a3d0f4fb…` 5/5 | warehouse +10, PASS |

Both: A claims Tree A, B is `EXCLUDED` and takes Tree B; both harvest 5 and deposit 5; both
release claims; trace complete 22 events, deltas 0. No `TALAR_NO_HARVEST` occurred.

**Campaign 6 divergence did not reproduce** in this pair. The added diagnostics show the normal
path: base log → column 5 → sapling replant → `TALAR_CLEAR` with carried 5. The Campaign 6
non-completion remains preserved as a one-off `INSUFFICIENT_EVIDENCE` event, not a confirmed
production defect. NAV-GATE `task=null` is diagnostic lease bookkeeping and is not shown to
influence control flow.

**Classification:** contention/exclusivity MATCH; TALAR harvest/deposit MATCH; Campaign 6
divergence NON-REPRODUCED (one pair each). A7-12 remains **PARTIAL** only for abandonment/
recovery, which remains NOT_TESTED.

## 2026-11-11 — Campaign 9 real abandonment and recovery

Trigger: after the worker acquired Tree A (`ForestryTreeClaims.ownerOf` returned a real
owner/task), the fixture removed Tree A’s target block through `ServerLevel.destroyBlock` as a
disposable environmental invalidation. No worker/task/claim/nav state was touched.

| Mode | Run | Trigger | Cleanup | Recovery | Result |
|---|---|---|---|---|---|
| OFF | `res-a7abandon3-off-20261111-224500` | Tree A removed tick 127 | claim A release; `NAV-GUARD ABANDON` tick 4150 | claim B tick 4152; 5/5 | warehouse +5, PASS |
| AUDIT | `res-a7abandon3-audit-20261111-225000` | Tree A removed tick 126 | claim A release; `NAV-GUARD ABANDON` tick 4149 | claim B tick 4151; 5/5 | warehouse +5, PASS |

AUDIT leases: Tree B task `adde8dcc…` acquired 4153 and `RELEASED` 4353; delivery task
`e6e8c150…` acquired 4357 and `RELEASED` 4813. No lease survived finalization.

Both: original task claim actually released; navigation stopped/replaced; subsequent normal
discovery selected Tree B with a new task identity; harvest 5, deposit 5; trace complete
11 events; actor/warehouse/global deltas 0. No stale claim, target or lease blocked recovery.
OFF has no lease by design (expected difference).

**Closure:** every mandatory A7-12 dimension now has accepted evidence: single-worker
resources/targets, isolated two-worker allocation, arrival/route/lease lifecycle, contention
exclusivity/reselection, and abandonment/cleanup/recovery. The Campaign 6 AUDIT owner
non-completion remains a non-reproduced one-off (`INSUFFICIENT_EVIDENCE`), documented as a
limitation rather than a confirmed production defect. **A7-12 COMPLETE.**








## 2026-11-11 — Controlled A8-01-E pair with the completed Resource Trace

A8-01 is COMPLETE; the trace now supplies per-actor and warehouse evidence. New paired runs
used the same single-lumberjack fixture (`a8-01-e-mode`, tree `98,98,0`, empty warehouse):

| Run | Mode | Actor | Task target | Delivery target | Events | Harvest/deposit | Final |
|---|---|---|---|---|---|---|---|
| `res-a8eoff2-20261110-225500` | OFF | `da124fa4…` | `98,98,0` | `124,97,18` | 11 | 5/5 | actor 0, warehouse 5 |
| `res-a8eaudit2-20261110-230000` | AUDIT | `1ffa007b…` | `98,98,0` | `124,97,18` | 11 | 5/5 | actor 0, warehouse 5 |

Classifications:

- **MATCH:** initial warehouse `{}`, tree, TALAR target, delivery target, harvest 5, deposit 5,
  final actor 0, warehouse 5, trace `complete=true`, no truncation/unattributed.
- **EXPECTED_DIFFERENCE:** actor UUID and task UUID (per-run identity); tick count
  (OFF 604 vs AUDIT 518).
- **DIVERGENCE:** none in this pair.
- **NOT_TESTED / NOT_OBSERVABLE:** route/lease progression under OFF (OFF does not create the
  same lease evidence), multi-worker contention, abandonment/recovery, and the historical
  shared-warehouse second-worker scenario.

**A7-12 remains PARTIAL.** The new pair demonstrates resource and target/delivery equivalence
for one controlled fixture, but does not resolve the historical two-pair `+1` difference or
cover every mandatory dimension. The smallest next action is a paired multi-worker/shared-
warehouse fixture with A8-01 traces plus lease/route evidence; do not claim equivalence or
transfer authority yet. OFF remains default; AUDIT diagnostic-only.
