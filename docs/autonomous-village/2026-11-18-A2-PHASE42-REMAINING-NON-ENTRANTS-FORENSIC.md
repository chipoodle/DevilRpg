# A2 Phase 42 — remaining non-entrants forensic (2026-11-18)

## 1. Result

**COMPLETE.** All six non-entrants in `a2n-20261118-140906` have a causal chronology. There is no
single common navigation defect: the six split into independent source recovery, DROP-approach/DROP-
landing, BUILD-budget and step-budget classes. Phase 41 is not a regression; IDX4 and IDX8 both
completed a physical edge-step descent.

- Branch: `feature/autonomous-village`
- HEAD: `747826227a9a6bee9c29cf65cc34f99cb55f3003`
- Production: `4379dbc250856b292b6cc2e3703b6aaf3bc8022b`
- Run analyzed: `a2n-20261118-140906`
- A2: 2/8 strict, 2/8 perimeter; C4 PASS `[2,0,0]`
- No production/test/runtime change.

## 2. Entrants

```text
IDX1 39823371-64b5-4574-980a-38c1e673d1ca strict t2233
IDX6 7be81a3b-3084-4cc1-80bb-035cb09a21d3 strict t990
```

## 3. Comparative table

| IDX | UUID | Final pos | Dist | NavState | Last action | Classification |
|---|---|---|---:|---|---|---|
| 2 | `b6fadeb0…` | `662,72,608` | 75.10 | FAILED | DROP-EN-CURSO `661,71,607` | SOURCE_INVALID / SOURCE_RECOVERY |
| 3 | `7e20a00f…` | `604,96,600` | 88.19 | RECOVERING | DROP-EN-CURSO `606,95,600` | DROP_APPROACH / replans |
| 4 | `5ea20f89…` | `575,112,524` | 85.78 | TRAVERSING | later DROP `577,111,522` | DROP_LANDING (non-adjacent) |
| 5 | `fd459775…` | `594,87,466` | 94.05 | RECOVERING | DROP-EN-CURSO `596,86,466` | DROP_APPROACH / replans |
| 7 | `06bd0cb7…` | `707,63,459` | 86.74 | FAILED | BUILD `705,68,459` | BUILD / budget exhaustion |
| 8 | `b0cb68cd…` | `744,65,534` | 83.52 | FAILED | step toward `725,71,533` | STEP_UP/BUILD / local budget |

## 4. Individual chronologies

### IDX2 `b6fadeb0-3149-471a-815e-7698bb7d7d93`

- Spawn target/global mission near `661,73,533`; final `662,72,608`, distance75.10.
- Five `NAV_FAIL` events at `662,72,608`:
  `DROP source-cell recovery found no safe supported AABB-overlapping neighbor`.
- Rearms exhausted 4/4; final state FAILED.
- Root cause: source recovery has no safe supported AABB-overlapping neighbor. Independent of Phase41.
- Demonstrated cause: SOURCE_INVALID / SOURCE_RECOVERY.

### IDX3 `7e20a00f-1101-48af-9af7-b4cb4790d353`

- Final `604,96,600`, distance88.19, RECOVERING.
- `NAV_FAIL {replans agotados (8/8)}` at `603,96,600`.
- Final action `606,95,600:DROP-EN-CURSO` (dx2,dy-1); 22 descent attempts, 0 failures, but never
  entered.
- No Phase41 edge-step was selected (not adjacent; it remains on the approach waypoint loop).
- Cause: DROP_APPROACH did not reach a safe adjacent takeoff before the 8/8 replan limit. This is the
  remaining DROP-approach class, not the Phase41 edge-step.

### IDX4 `5ea20f89-7f94-47a5-9e55-46432e659ba8`

- Phase41 success: edge-step selected t3, support cleared t17, landed `567,119,534` t24, cursor
  advanced.
- It did **not** enter the village. It continued to `575,112,524` (distance85.78).
- Final route index4, later DROP landing `577,111,522`; `DROP_LANDING_PROGRESS validLanding=false` from
  t5887 to5997, `landingHeightReached=false`, live cell `575,112,524`, onGround=true.
- Final nav state TRAVERSING, no terminal NAV_FAIL.
- Cause: a later non-adjacent DROP landing is not reached; the mob remains 2–3 cells away and the
  landing is not physically valid. This is **not** an edge-step failure.
- Demonstrated: DROP_LANDING / approach to a later landing.

### IDX5 `fd459775-58ce-464d-809d-bb59f7870e97`

- Final `594,87,466`, distance94.05, RECOVERING.
- `NAV_FAIL {replans agotados (8/8)}` at `594,87,466`.
- Final action `596,86,466:DROP-EN-CURSO` (dx2,dy-1); 22 attempts.
- No edge-step; same DROP-approach loop as IDX3.
- Demonstrated: DROP_APPROACH / replans.

### IDX7 `06bd0cb7-0396-4e48-b85c-a956aba71d76`

- Final `707,63,459`, distance86.74, FAILED.
- `NAV_FAIL {presupuesto agotado sin progreso (10/10) hacia 705,68,459}`.
- `A2_REARM_REJECTED reason=FAILED_DEFINITIVE`.
- Local objective `705,68,459` is a BUILD target (buildable=true); no DROP involved.
- Demonstrated: BUILD/progress budget exhaustion. Independent.

### IDX8 `b0cb68cd-f8e9-457b-a2e6-bbaa62215ec6`

- Phase41 success: edge-step selected t182, support cleared t275, landed `746,63,534` t279.
- It then climbed toward `725,71,533`; final `744,65,534`, distance83.52, FAILED.
- `NAV_FAIL {sin objetivo local valido (10/10): tope de escalones locales alcanzado (64)}` and
  `{presupuesto agotado sin progreso (10/10) hacia 725,71,533}`.
- Global plan at final `exp=8000/8000`, no progress; rearm 4/4.
- Demonstrated: STEP_UP/BUILD/local-budget exhaustion after a successful DROP. Independent of Phase41.

## 5. Transversal findings

- **No common root cause.** Source recovery (IDX2), DROP-approach replans (IDX3/5), a later DROP landing
  (IDX4), BUILD budget (IDX7), and step/local budget (IDX8) are distinct.
- **Phase41 regression: NONE.** IDX4 and IDX8 both completed the edge-step and physical descent in this
  run. Their later blockers are downstream.
- The Phase41 edge-step fixed only the already-adjacent DROP at the platform lip.
- DROP-approach for a landing still 2+ cells away remains a blocker for IDX3/5 and later IDX4.
- IDX2's source recovery and IDX7's BUILD exhaustion are unchanged historical classes.

## 6. Missing evidence

- Exact block-state/collision arrays for IDX3/5 approach cells and IDX4's later landing `577,111,522`.
- Detailed per-tick path for the later IDX4 DROP (only 10-tick `DROP_LANDING_PROGRESS` samples).
- No new runtime was executed; no extrapolation beyond logged data.

## 7. Recommended next phase

Phase 43 should target the **non-adjacent DROP landing/approach** class (IDX3/5 and the later IDX4
node): why the executor stops 2–3 cells from a validated landing and never reaches an adjacent edge,
exhausting 8/8 replans. Keep Phase41's adjacent edge-step untouched. IDX2 and IDX7 remain independent
and should not be bundled.

## 8. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
ORIGINAL_WORLDS_MODIFIED: false
RUN2_EXECUTED: false
FULL12K_EXECUTED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

A2 remains OPEN.
