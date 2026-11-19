# A2 Phase 52 — stair placement + IDX5 regression forensics (2026-11-18)

## 1. Result

**COMPLETE for the requested forensics; both defects explained.**

- **IDX3 does not place stairs because no candidate is geometrically valid**: 2,340 candidate events,
  all rejected with `celdaAire=false` and/or occupied head cells. No primitive placement is attempted.
  This is not an anchor/cooldown loop.
- **IDX5 regression is causally linked to CADENCE_WAIT deferral**: at t2 Phase50 returned
  `NO_PROGRESS` and issued a DROP move at t3; Phase51 returns `CADENCE_WAIT`, emits
  `A2_LOCAL_STAIR_WAIT`, and **never issues any MOVE_COMMAND**. The intended counter fix prevents
  planning/fallback, so the mob remains at spawn and the global front is exhausted.

- RUN_ID: `a2n-20261118-225649`
- RUN_HEAD: `4a331b15f355ca428115de5084b10c97fb18be6f`
- PRODUCTION_SHA: `ea21466a0cc7cc2671f5439cd5ff7bbaeecd6d9e`
- A2: 2/8 strict (idx1 t512, idx6 t1094), C4 PASS `[1,0,0]`
- No production/test change.

## 2. Trace configuration

This diagnostic Run1 used:

```text
JAVA_TOOL_OPTIONS=-Ddevilrpg.a2n.traceStair=true -Ddevilrpg.a2n.traceStairCandidates=true
```

Evidence that candidates were active:

```text
STAIR_BUILDER_CANDIDATE=12060 total
STAIR_BUILDER_ANCHOR_REFRESH=361
```

Phase51's `CANDIDATE=0` was a missing `traceStairCandidates` property, not a code path absence.

## 3. IDX3 builder reconstruction

UUID `5d440cee-c70b-4840-882d-f692ced332f8`.

```text
STAIR_BUILDER_ENTER=397
STAIR_BUILDER_GATE_FALSE=0
STAIR_BUILDER_COOLDOWN=380
STAIR_BUILDER_CANDIDATE=2340
STAIR_BUILDER_RETURN=397
A2_LOCAL_STAIR_WAIT=360
STAIR_BUILDER_ANCHOR_REFRESH=39
CONSTRUCCION-ESCALERA=0
```

Candidate rejection distribution (IDX3):

```text
2241 celdaAire=false hueco1=false hueco2=false soporte=true permitido=true
  55 celdaAire=false hueco1=false hueco2=true  soporte=true permitido=true
  19 celdaAire=true  hueco1=false hueco2=true  soporte=true permitido=true
  19 celdaAire=false hueco1=true  hueco2=false soporte=true permitido=true
   6 celdaAire=false hueco1=true  hueco2=true  soporte=true permitido=true
```

All 2,340 rejections are occupancy/headroom geometry. There is no `ponerEscalon=false`, no
`NO verificado`, no placement attempt.

First effective attempt:

```text
t1 base=594,95 anchor=594,95 mob=594,96
candidate=594,94 dy=1 -> celdaAire=false
candidate=594,93 dy=2 -> celdaAire=false
candidate=594,92 dy=3 -> celdaAire=false
...
```

The terrain around the descent is already solid; building cannot place a block. The builder then sets
cooldown and repeats on the same occupied geometry. The anchor refreshes down through natural movement
(e.g. t23 `anchor 594,95 -> 594,93`, `hasTraversedStair=false`) but the candidate field remains solid.

```text
IDX3_CLASSIFICATION: OTHER — no valid candidate geometry (terrain occupied)
IDX3_ANCHOR_REFRESH: exercised only with hasTraversedStair=false; monotonic-anchor traversal case NOT exercised.
```

Final `594,70,600`, FAILED; `escalones=0/64`, `colocados=0/24`; global front exhausted.

## 4. IDX5 Phase50 vs Phase51

Spawn identical: `594,87,466`; mission `661,71,533`.

First divergence:

```text
t1 both: ACCESS_CLEARED, anchor594,86, retraso10.

Phase50 t2:
  STAIR_BUILDER_RETURN outcome=NO_PROGRESS (not CADENCE_WAIT)
  A2_STAIR_FALLBACK_DECISION objetivo617,83,465
  (no A2_LOCAL_STAIR_WAIT)
Phase50 t3:
  MOVE_COMMAND reason=DROP node=596,86,466
  position begins moving east.

Phase51 t2:
  STAIR_BUILDER_RETURN outcome=CADENCE_WAIT retraso=9
  A2_LOCAL_STAIR_WAIT local=0/64 counters_unchanged=true
  no fallback/movement
Phase51:
  MOVE_COMMAND total = 0
  remains 594,87→594,77→594,75 RECOVERING
  global front exhausted rutaFrente=0
```

Timeline:

```text
tick   Phase50                     Phase51
1      planning/access cleared     planning/access cleared
2      NO_PROGRESS → fallback      CADENCE_WAIT → WAIT, null
3      MOVE_COMMAND DROP           no MOVE_COMMAND
100    603.48,85,466 TRAVERSING    594.50,85,466 PLANNING
500    628.49,71,469 TRAVERSING    594.50,77,466 RECOVERING
1200   648.25,71,518 TRAVERSING    594.50,77,466 RECOVERING
5999   (entered strict t730)       594.50,75,466 RECOVERING
```

First observable divergence: **t2 STAIR_BUILDER_RETURN outcome** and the immediate deferral. Phase50
continued to fallback/local planning even on the cooldown return; Phase51 returns CADENCE_WAIT and
defers, so no route/movement is ever selected. The mob cannot wait for a stair because the builder never
places one, and no alternative fallback occurs.

```text
IDX5_CAUSAL_LINK_TO_CADENCE_WAIT: CONFIRMED
CADENCE_WAIT deferral currently blocks all planning when the builder cannot place,
removing the movement opportunity that Phase50 had.
```

## 5. A2 Run1

```text
A2: 2/8 strict, 2/8 perimeter
idx1 ec5c0e4c-714f-4879-9959-6d77121d142b strict t512
idx2 b2532f2c-da85-46d8-8a53-d960e3c9e3fe no entry
idx3 5d440cee-c70b-4840-882d-f692ced332f8 no entry
idx4 0f166a9c-f56d-46be-9a39-e298a89ad5e2 no entry
idx5 426df5d6-056d-4942-a145-c7cd3a961169 no entry
idx6 6f138811-0e80-4318-9c90-b4b6e658e151 strict t1094
idx7 52a5b141-16a9-4a4f-9eb7-8c9f7953beba no entry
idx8 1c2861fb-5bad-4776-9880-081311622a4e no entry
C4: PASS [1,0,0]
```

No physical stair was placed/traversed in the run (`CONSTRUCCION=0`, `TRAVESADO=0`), so monotonic anchor
remains runtime-unvalidated.

## 6. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 7. Recommendation

1. CADENCE_WAIT must not nullify the only movement opportunity: defer only the stair attempt while
   preserving a valid non-stair local/fallback route (as Phase50 effectively did).
2. IDX3 needs an approach/route diagnostic for an all-solid candidate field; do not treat this as an
   anchor bug.
3. Monotonic anchor still needs a natural or approved fixture with a real placement + traversal.
4. Do not change budgets/predicates. A2 remains OPEN at 2/8.
