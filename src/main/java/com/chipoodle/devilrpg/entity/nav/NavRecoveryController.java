package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Bounded recovery memory. Only physically occupied cells are recorded. After two
 * consecutive no-progress route failures, one backtrack to a still-valid old cell is allowed.
 */
public final class NavRecoveryController {
    public enum Decision { REPLAN, BACKTRACK }
    public static final int MAX_HISTORY = 16;

    private final Deque<BlockPos> reached = new ArrayDeque<>();
    private int consecutiveNoProgress;
    private boolean backtrackUsed;
    private BlockPos lastBacktrack;

    public void recordReached(BlockPos position) {
        if (position == null) return;
        BlockPos p = position.immutable();
        if (!reached.isEmpty() && reached.peekLast().equals(p)) return;
        reached.addLast(p);
        while (reached.size() > MAX_HISTORY) reached.removeFirst();
    }

    public Decision onNoProgress() {
        consecutiveNoProgress++;
        return consecutiveNoProgress >= 2 && !backtrackUsed ? Decision.BACKTRACK : Decision.REPLAN;
    }

    public BlockPos chooseBacktrack(BlockPos current, Predicate<BlockPos> stillValid) {
        return chooseBacktrack(current, 0.0D, stillValid);
    }

    /**
     * Select the newest physically reached cell that is still valid and farther than the
     * arrival tolerance. Nearby history entries are skipped rather than aborting the search,
     * so ordinary one-block movement history can still yield a useful bounded backtrack.
     */
    public BlockPos chooseBacktrack(BlockPos current, double minimumDistanceSqr,
                                     Predicate<BlockPos> stillValid) {
        if (current == null || backtrackUsed || stillValid == null
                || minimumDistanceSqr < 0.0D) return null;
        Iterator<BlockPos> it = reached.descendingIterator();
        while (it.hasNext()) {
            BlockPos candidate = it.next();
            if (candidate.equals(current) || candidate.equals(lastBacktrack)
                    || candidate.distSqr(current) <= minimumDistanceSqr) continue;
            if (stillValid.test(candidate)) return candidate.immutable();
        }
        return null;
    }

    public void markBacktrack(BlockPos target) {
        backtrackUsed = true;
        lastBacktrack = target == null ? null : target.immutable();
    }

    public void onMissionProgress() {
        onVerifiedProgress();
    }

    /** Any verified physical or terrain progress rearms the bounded recovery sequence. */
    public void onVerifiedProgress() {
        consecutiveNoProgress = 0;
        backtrackUsed = false;
        lastBacktrack = null;
    }

    public void reset() {
        reached.clear();
        consecutiveNoProgress = 0;
        backtrackUsed = false;
        lastBacktrack = null;
    }

    public int consecutiveNoProgress() { return consecutiveNoProgress; }
    public int historySize() { return reached.size(); }
    public boolean backtrackUsed() { return backtrackUsed; }
    public BlockPos lastBacktrack() { return lastBacktrack == null ? null : lastBacktrack.immutable(); }
}
