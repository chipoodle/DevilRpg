package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/** Pure contract for exclusive DROP source-snap ownership and center completion. */
public final class NavSnapOwnershipPolicy {
    private NavSnapOwnershipPolicy() {
    }

    /** A pending source snap owns all movement until completion or explicit release. */
    public static boolean snapOwnsMovement(BlockPos pending) {
        return pending != null;
    }

    /** Normal route navigation may not issue a movement command while a snap is pending. */
    public static boolean allowsRouteMovement(BlockPos pending) {
        return pending == null;
    }

    /** The oscillation guard must not consume a normal route while a snap owns movement. */
    public static boolean allowsOscillationAbort(BlockPos pending) {
        return pending == null;
    }

    /** Same physical and radial contract used by the snap before releasing ownership. */
    public static boolean snapComplete(boolean centered, boolean grounded, boolean feetValid,
                                       boolean supported, boolean fits) {
        return centered && grounded && feetValid && supported && fits;
    }
}
