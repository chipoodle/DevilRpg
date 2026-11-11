package com.chipoodle.devilrpg.agent;

import java.util.UUID;

/** Focused pure test for the cross-worker TALAR tree claim. */
public final class ForestryTreeClaimsTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    public static void main(String[] args) {
        ForestryTreeClaims.clearAll();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        long tree = 0x0000006300620000L;      // arbitrary packed position
        long otherTree = 0x0000005000620014L;

        check(ForestryTreeClaims.acquire(OVERWORLD, tree, a, taskA, 0L), "A acquires");
        check(!ForestryTreeClaims.acquire(OVERWORLD, tree, b, taskB, 0L), "B denied same tree");
        check(ForestryTreeClaims.acquire(OVERWORLD, tree, a, taskA, 10L), "A renews own claim");
        check(!ForestryTreeClaims.renew(OVERWORLD, tree, b, taskB, 10L), "B cannot renew A claim");
        check(!ForestryTreeClaims.release(OVERWORLD, tree, b, taskB), "B stale release rejected");
        check(!ForestryTreeClaims.release(OVERWORLD, tree, a, taskB), "A wrong task release rejected");
        check(ForestryTreeClaims.ownerOf(OVERWORLD, tree, 10L).startsWith(a.toString()), "A still owner");

        check(ForestryTreeClaims.acquire(OVERWORLD, otherTree, b, taskB, 20L), "B acquires different tree");
        check(ForestryTreeClaims.acquire(NETHER, tree, b, taskB, 20L), "same position in another dimension");
        check(!ForestryTreeClaims.acquire(OVERWORLD, tree, a, taskB, 20L), "same worker other task denied");
        check(ForestryTreeClaims.release(OVERWORLD, tree, a, taskA), "A releases own claim");
        check(ForestryTreeClaims.acquire(OVERWORLD, tree, b, taskB, 30L), "B acquires released tree");
        check(!ForestryTreeClaims.release(OVERWORLD, tree, a, taskA), "stale A release cannot erase B");
        check(ForestryTreeClaims.ownerOf(OVERWORLD, tree, 30L).startsWith(b.toString()), "B survived stale release");

        long expiring = 0x0000000100020003L;
        check(ForestryTreeClaims.acquire(OVERWORLD, expiring, a, taskA, 0L), "A expiry claim");
        check(!ForestryTreeClaims.acquire(OVERWORLD, expiring, b, taskB,
                ForestryTreeClaims.CLAIM_TTL_TICKS - 1), "B denied before TTL");
        check(ForestryTreeClaims.acquire(OVERWORLD, expiring, b, taskB,
                ForestryTreeClaims.CLAIM_TTL_TICKS), "B acquires after TTL");

        check(ForestryTreeClaims.releaseAll(b) >= 1, "releaseAll removes B claims");
        check(ForestryTreeClaims.size() == 0, "registry empty after cleanup");
        ForestryTreeClaims.clearAll();
        System.out.println("FORESTRY_TREE_CLAIMS_TEST: OK");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("FAILED: " + what);
        }
    }
}
