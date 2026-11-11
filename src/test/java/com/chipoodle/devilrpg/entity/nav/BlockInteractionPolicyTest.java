package com.chipoodle.devilrpg.entity.nav;

import static com.chipoodle.devilrpg.entity.nav.BlockInteractionPolicy.Action.*;

public final class BlockInteractionPolicyTest {

    public static void main(String[] args) {
        // 1. ordinary block with budget -> BREAK
        eq(BREAK, decide(false, false, false, false, false, false, false, true, false, false, 0, 0, 0));
        // 2. fourth removal -> DETOUR
        eq(DETOUR, decide(false, false, false, false, false, false, false, true, false, false, 3, 0, 0));
        // 3. bedrock/unbreakable -> DETOUR
        eq(DETOUR, decide(false, false, false, false, true, false, false, true, false, false, 0, 0, 0));
        // 4. obsidian and crying obsidian -> DETOUR
        eq(DETOUR, decide(false, false, false, false, false, true, false, true, false, false, 0, 0, 0));
        // 5. protected target -> DETOUR
        eq(DETOUR, decide(true, false, false, false, false, false, false, true, false, false, 0, 0, 0));
        // 6. validated perimeter wall remains narrow BREAK exception
        eq(BREAK, decide(true, true, false, false, false, false, false, true, false, false, 0, 0, 0));
        // 7. own bridge -> DETOUR
        eq(DETOUR, decide(false, false, true, false, false, false, false, true, false, false, 0, 0, 0));
        // 8. block entity -> DETOUR
        eq(DETOUR, decide(false, false, false, true, false, false, false, true, false, false, 0, 0, 0));
        // 9. hazard/unsupported -> DETOUR
        eq(DETOUR, decide(false, false, false, false, false, false, true, true, false, false, 0, 0, 0));
        // 10. climb only when feasible
        eq(CLIMB, decide(false, false, false, false, false, false, false, true, true, false, 0, 0, 0));
        // 11. bridge only when feasible
        eq(BRIDGE, decide(false, false, false, false, false, false, false, true, false, true, 0, 0, 0));
        // 12. failed-target veto until expiry, then eligible
        eq(DETOUR, decide(false, false, false, false, false, false, false, true, false, false, 0, 100, 200));
        eq(BREAK, decide(false, false, false, false, false, false, false, true, false, false, 0, 200, 200));
        // 13. no safe action -> DETOUR
        eq(DETOUR, decide(false, false, false, false, false, false, false, false, false, false, 0, 0, 0));
        // 14. policy does not mutate/require an objective; decisions remain pure and repeatable
        BlockInteractionPolicy.Decision first = decide(false, false, false, false, false, false, false, true,
                false, false, 0, 0, 0);
        BlockInteractionPolicy.Decision second = decide(false, false, false, false, false, false, false, true,
                false, false, 0, 0, 0);
        if (first.action() != second.action()) {
            throw new AssertionError("decision no repetible");
        }
        System.out.println("BLOCK_INTERACTION_POLICY_TEST: OK (15 aserciones)");
    }

    private static BlockInteractionPolicy.Decision decide(boolean protectedTarget, boolean wall, boolean ownBridge,
            boolean blockEntity, boolean unbreakable, boolean obsidian, boolean hazard, boolean ordinary,
            boolean climbable, boolean bridge, int breaks, long now, long failedUntil) {
        return BlockInteractionPolicy.decide(new BlockInteractionPolicy.Candidate(protectedTarget, wall, ownBridge,
                blockEntity, unbreakable, obsidian, hazard, ordinary, climbable, bridge, breaks, now, failedUntil));
    }

    private static void eq(BlockInteractionPolicy.Action expected, BlockInteractionPolicy.Decision actual) {
        if (actual == null || expected != actual.action()) {
            throw new AssertionError("esperado=" + expected + " actual=" + actual);
        }
    }
}
