package com.chipoodle.devilrpg.world;

/** Pure pantry-credit transaction math; the container/level adapter calls this. */
public final class PantryCreditPolicy {
    private PantryCreditPolicy() {}

    public record Result(int creditUsed, int physicalNeeded, int granted, int remainingCredit) {}

    /** physical is the points actually obtained from containers for the post-credit need. */
    public static Result apply(int credit, int requested, int physical) {
        int c = Math.max(0, credit);
        int req = Math.max(0, requested);
        int use = Math.min(c, req);
        int need = req - use;
        int obtained = Math.max(0, physical);
        int granted = Math.min(req, use + obtained);
        int remaining = c - use + Math.max(0, obtained - need);
        return new Result(use, need, granted, remaining);
    }
}
