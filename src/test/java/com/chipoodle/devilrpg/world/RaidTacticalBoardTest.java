package com.chipoodle.devilrpg.world;

import java.util.UUID;

public final class RaidTacticalBoardTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    public static void main(String[] args) {
        RaidTacticalBoard board = new RaidTacticalBoard();
        long t = 100L;
        long target = 12345L;

        // 1 first claim
        check(true, board.tryClaim(A, 0, target, t));
        // 2 another owner cannot claim same active target
        check(false, board.tryClaim(B, 0, target, t + 1));
        // 3 different target concurrently
        check(true, board.tryClaim(B, 0, target + 1, t + 1));
        // 4 expired claim can be reassigned
        check(true, board.tryClaim(B, 0, target, t + RaidTacticalBoard.CLAIM_TICKS));
        // 5 valid owner can retain/renew own claim
        check(true, board.tryClaim(B, 0, target, t + RaidTacticalBoard.CLAIM_TICKS + 1));
        // 6 failed attempt cannot rapidly swap owners
        check(false, board.tryClaim(A, 0, target, t + RaidTacticalBoard.CLAIM_TICKS + 2));
        // 7 successful removal releases
        board.release(target);
        check(true, board.tryClaim(A, 0, target, t + RaidTacticalBoard.CLAIM_TICKS + 3));
        // 8 air/invalid target release invalidates claim
        board.release(target);
        check(false, board.isClaimedByOther(A, target, t + RaidTacticalBoard.CLAIM_TICKS + 4));
        // 9 owner invalidation permits reassignment
        board.tryClaim(A, 0, target, t + 200L);
        board.releaseOwner(A);
        check(true, board.tryClaim(B, 0, target, t + 201L));
        // 10 cleanup clears all claims
        board.clear();
        check(0, board.size());
        // 11 bounded board
        for (int i = 0; i < RaidTacticalBoard.MAX_CLAIMS + 10; i++) {
            board.tryClaim(A, 0, 9000L + i, t + 300L);
        }
        check(RaidTacticalBoard.MAX_CLAIMS, board.size());
        // 12 claim denial does not mutate world/board state
        int before = board.size();
        check(false, board.tryClaim(B, 0, 9000L, t + 301L));
        check(before, board.size());
        // 13 board has no mission/objective API
        check(false, java.util.Arrays.stream(RaidTacticalBoard.class.getMethods())
                .anyMatch(m -> m.getName().toLowerCase().contains("mision")));

        System.out.println("RAID_TACTICAL_BOARD_TEST: OK (13 aserciones)");
    }

    private static void check(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("esperado=" + expected + " actual=" + actual);
        }
    }

    private static void check(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("esperado=" + expected + " actual=" + actual);
        }
    }
}
