package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;

/**
 * Keeps a descending stair chain monotonic after a physically traversed step.
 * A mob may stand on a higher natural support after traversal; that support must
 * not move the stair origin upward and recreate the same already-traversed step.
 */
public final class NavStairAnchorPolicy {
    private NavStairAnchorPolicy() {
    }

    public static BlockPos refresh(final BlockPos previousAnchor, final BlockPos currentSupport,
                                   final boolean hasTraversedStair) {
        if (currentSupport == null) {
            return previousAnchor;
        }
        if (previousAnchor == null || !hasTraversedStair || currentSupport.getY() <= previousAnchor.getY()) {
            return currentSupport;
        }
        return previousAnchor;
    }

    public static boolean isStrictlyBelow(final BlockPos candidate, final BlockPos traversedAnchor) {
        return candidate != null && traversedAnchor != null && candidate.getY() < traversedAnchor.getY();
    }
}
