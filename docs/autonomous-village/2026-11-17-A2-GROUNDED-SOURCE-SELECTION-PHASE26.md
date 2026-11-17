# A2 — ranked grounded DROP source selection (Phase 26, 2026-11-17)

## Status

**Implementation published; Harness validation pending.** A2 remains OPEN. ChatGPT has not executed Gradle tests, strict lint, diagnostics compilation, or Minecraft runtime.

- Previous run: `a2n-20261117-153059` (Phase 25).
- Previous tested SHA: `011373d0b3423b188611867d7f4751ab2ac88c66`.
- Previous result: static PASS; A2 3/8 perimeter and 3/8 strict; C4 PASS `[1,0,0]`.
- Phase 25 source recovery: 77 `DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY`, zero `DROP_TAKEOFF_SOURCE_SNAP_REPLAN`.
- IDX4: `03df487a-ff61-482c-9407-ed5cdd0d1b5d`, stuck at `567,122,534` with grounded=true, feetValid=false, support=false, realAabbFits=true.
- Production checkpoint: `a795c33d03678e2a12d066645e5dbc927d7f3efb`.

## Phase 25 diagnosis

Source inspection found that both grounded source recovery call sites used `NavSourceCellPolicy.uniqueAdjacentCandidate`. That method inspected only the four cardinal neighbors at the same Y and returned null if it found more than one physically valid overlap. It did not rank valid alternatives and did not inspect diagonal cells.

The runtime evidence proves that no candidate was selected, but it does not by itself distinguish whether the actual world had zero valid cardinal candidates or multiple valid cardinal candidates. Phase 26 fixes both algorithmic gaps without claiming which one occurred in that specific world.

## Production change

1. Added `NavSourceCellPolicy.bestAdjacentCandidate`.
2. Evaluates all eight horizontal neighbors at the same feet Y; a candidate must overlap the real AABB horizontally by a positive area.
3. Deterministic ranking:
   - Greatest real-AABB horizontal overlap area.
   - Nearest candidate center to the mob's current X/Z.
   - Lexicographic X/Z tie-break for exact equality.
4. Both executor recovery paths now use this selector.
5. The candidate predicate remains strict: `esPieValido` (including protected-cell rejection, feet/head clearance and solid support), explicit `hasSupport`, and `fitsAtCellCenter` using the translated real mob AABB.
6. After selection, the executor still performs horizontal MoveControl snap only. It requires actual candidate occupancy, centering, support and current real-AABB fit before replanning. No teleport, jump impulse, direct velocity, BREAK, BUILD or BRIDGE was introduced.
7. No budgets, search radii, safe-drop limits, collision, support, protection, headroom or landing criteria were changed.

The existing strict `uniqueAdjacentCandidate` remains available for its policy tests; production executor no longer calls it.

## Tests authored (not run by ChatGPT)

- `NAV_SOURCE_CELL_POLICY_TEST`: expected 14 assertions (10 existing + 4 new).
- `NAV_DROP_TAKEOFF_POLICY_TEST`: remains 51 assertions.
- New source-policy coverage: multiple valid alternatives select deterministically; larger overlap wins; a diagonal-only valid candidate is found; no physically valid candidate remains rejected.

## Harness instructions

1. Resolve the live `feature/autonomous-village` HEAD from GitHub and safely sync it. The current code checkpoint is `a795c33d03678e2a12d066645e5dbc927d7f3efb`; handoff docs will be a descendant, so do not assume this SHA is the final branch HEAD.
2. Preserve all local/untracked files and original worlds. No reset/clean/stash or destructive checkout.
3. Run static gates first: `.\gradlew.bat test`, `lint_aldea.py --strict`, `git diff --check`, `.\gradlew.bat compileJava -Pa2nDiagnostics=true`.
4. Confirm source-cell 14 assertions, DROP take-off 51 assertions, and all existing suites.
5. If static PASS, run A2 Run 1 only in the disposable world.
6. For each grounded-invalid source, capture candidate position and predicates: AABB overlap, chunk loaded, protected, feet valid, support, translated real-AABB fit. Confirm selected candidate, horizontal snap, physical centered occupancy, and only then route/cache invalidation and replan.
7. Specifically inspect IDX4 and all previously blocked UUIDs. If no candidate exists, report all eight neighbor predicate values rather than only `candidate=NONE`.
8. Report exact tested SHA, all eight UUID outcomes, perimeter/strict counts, C4, world hashes and artifact paths.

## Gates / preservation

- No Run 2 unless Run 1 reaches 8/8 strict.
- No Full 12K until five consecutive 8/8 strict runs.
- C4 last PASS `[1,0,0]`; save/reload remains OPEN.
- IDX7/IDX8 remain independent.
- A2 remains OPEN until runtime acceptance passes.
- Harness must not modify production source. Preserve original worlds and local-only artifacts.
