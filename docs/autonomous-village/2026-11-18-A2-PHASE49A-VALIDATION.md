# A2 Phase 49A — IDX3 stair trace validation (2026-11-18)

## 1. Result

**COMPLETE for the IDX3 trace. The exact break is demonstrated:**

1. The builder places stair `607,93,601` (tick229).
2. The mob physically traverses it at t288; `ESCALERA-ANCLA` advances to `607,93,601`.
3. At t2299 `STAIR_BUILDER_ANCHOR_REFRESH` moves the anchor **up** from `607,93,601` to
   `607,94,601` (`blockPosition().below()`).
4. The next candidate with `dy=1` from base `607,94` is again `607,93,601`, accepted and placed.
5. The same stair is rebuilt/traversed repeatedly; net descent zero.

The cooldown also consumes a local stair step on every `CADENCE_WAIT` return, inflating the
`escalonesDeObjetivo` counter while no new step is built.

- RUN_HEAD: `6677e788e84aabae6e47cad72f342f5c3608cfeb`
- PRODUCTION_SHA: `9d6709f551f394e0ac7be69160c2aa46419d8058`
- RUN_ID: `a2n-20261118-172804`
- A2: 3/8 strict (idx1 t559, idx5 t730, idx6 t1055), C4 PASS `[1,0,0]`
- No production/test change.

## 2. Static

```text
gradlew test: PASS
lint_aldea.py --strict: PASS
git diff --check: PASS
compileJava -Pa2nDiagnostics=true: PASS
NAV_ADJACENT 15, NAV_DROP_APPROACH_OWNERSHIP 13, NAV_DROP_EDGE_STEP 16: PASS
```

## 3. IDX3 UUID

`e2cb58f6-df69-476d-9aa4-5fb2f60c583b`; final `607,95,601`, FAILED, local64/64, fallos10/10,
`escalera.colocados=18/24`.

## 4. Exact sequence

```text
t229 STAIR_BUILDER_ENTER pos=607,95,601 pending=null ancla=607,94 retraso=0 colocados=0
t229 ANCHOR_REFRESH supportBelow=607,94 anchorBefore=607,94 anchorAfter=607,94
t229 CANDIDATE base=607,94 anchor=607,94 mob=607,95 mobBelow=607,94
     candidate=607,93 dy=1 stepX=1 stepZ=-1 rejection=ACCEPTED_GEOMETRY
t229 STAIR_BUILDER_RETURN outcome=STAIR_PLACED pending=607,93 retraso=10 colocados=1

t288 ESCALON-TRAVESADO 607,93 desde607,94 (colocadoEnTick=229)
t288 ESCALERA-ANCLA avanza a607,93 (colocados=1/24)

t2299 STAIR_BUILDER_ENTER pos=607,95 pending=null ancla=607,93
t2299 ANCHOR_REFRESH supportBelow=607,94
     anchorBefore=607,93 -> anchorAfter=607,94   <-- break
...
t2451 STAIR_BUILDER_ENTER pending=null ancla=607,94 retraso=0 colocados=17
t2451 CANDIDATE base=607,94 candidate=607,93 dy=1 ACCEPTED_GEOMETRY
t2451 STAIR_BUILDER_RETURN outcome=STAIR_PLACED pending=607,93 retraso=10 colocados=18
```

`ANCHOR_REFRESH` at t2299 overwrites the advanced anchor with `blockPosition().below()` and moves it
one block upward. The subsequent `dy=1` candidate is the same stair coordinate.

## 5. Cooldown sequence

After placement `retraso=10`. Calls occur every 2 ticks:

```text
t2283 8->7, t2285 7->6, t2287 6->5, ...
t2545 8->7 ... t2559 1->0
```

At each CADENCE_WAIT:

```text
STAIR_BUILDER_COOLDOWN retrasoBefore=... retrasoAfter=... reason=CADENCE_WAIT
STAIR_BUILDER_RETURN outcome=NO_PROGRESS pending=null ... motivo=<stale success> rechazo=-
A2_LOCAL_STAIR_ENTER ...
A2_LOCAL_STAIR_WRITE op=INC_STEP new=...
```

Thus 10 cooldown returns consume 10 local objective steps before a new search. The cooldown itself is
bounded (10 calls, 20 ticks), but its returns are counted as local stair steps, which contributes
directly to 64/64.

## 6. Candidate behavior

At t2451:

```text
base=607,94 anchor=607,94 mob=607,95 mobBelow=607,94
candidate=607,93 dy=1 stepX=1 stepZ=-1 rejection=ACCEPTED_GEOMETRY
```

The candidate is not below the previously traversed stair `607,93`; it is that same coordinate. The
search is geometrically correct relative to the **wrong refreshed base** (`607,94`), so it accepts the
same block again.

## 7. Root cause

```text
ROOT_CAUSE: ANCHOR_REFRESH overwrites the traversed anchor (607,93) with
blockPosition().below() (607,94), moving the base upward. The next dy=1 candidate
is the same stair 607,93, producing a place/traverse/rebuild self-loop.

SECONDARY: CADENCE_WAIT returns increment the local objective counter, so cooldown
cycles consume the 64-step budget without building downward.
```

Demonstrated. Not the pending-promotion bug from Phase47.

## 8. Counts

```text
STAIR_BUILDER_COOLDOWN=406
STAIR_BUILDER_ANCHOR_REFRESH=42
STAIR_BUILDER_CANDIDATE=1370
STAIR_BUILDER_RETURN=465
ESCALON-TRAVESADO=18
CONSTRUCCION-ESCALERA same coordinate607,93,601 repeated
```

## 9. IDX4

Not touched. Horizontal-zero runtime remains `INSUFFICIENT_EVIDENCE`; no approved fixture.

## 10. Integrity

```text
PRODUCTION_MODIFIED: false
TESTS_MODIFIED: false
WORLDS_MODIFIED: false
A2 BA00A1C4C6A9E3D84871F0418EDB26D005204D5F82A9B5FF4F57C85C970C92C7
C4 11D064E21C7F63982ADD07F7EC862ECFB5CFD6918609D6459ACBC32BC4138EDF
```

## 11. Recommendation

ChatGPT should, with this evidence:

- preserve the traversed anchor `607,93` instead of refreshing it upward to `607,94`;
- ensure the next candidate is strictly below the last traversed stair;
- do not increment `escalonesDeObjetivo` for pure CADENCE_WAIT returns.

No budgets or physical predicates should be changed. A2 remains OPEN at 3/8 strict.
