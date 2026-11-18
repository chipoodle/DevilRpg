package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/** Bounded horizontal edge-step used only after a DROP path ends at the platform lip. */
public final class NavDropEdgeStepPolicy {
    public static final int MAX_TICKS = 60;
    public static final int MAX_NO_PROGRESS_TICKS = 20;
    public static final int MAX_HORIZONTAL = 3;

    private NavDropEdgeStepPolicy() {
    }

    public static boolean shouldStart(boolean dropTransition, boolean navigationDone, boolean grounded,
                                      boolean atLanding, int horizontalDistance, int maxDistance,
                                      boolean takeoffSupported, boolean takeoffFits,
                                      boolean landingSupported, boolean landingFits) {
        return dropTransition && navigationDone && grounded && !atLanding
                && horizontalDistance > 0 && horizontalDistance <= maxDistance
                && maxDistance > 0 && maxDistance <= MAX_HORIZONTAL
                && takeoffSupported && takeoffFits && landingSupported && landingFits;
    }

    /** True only when every block column under the full AABB footprint lacks support. */
    public static boolean fullyOffSupport(AABB box, Predicate<BlockPos> hasSupport) {
        int minX = (int) Math.floor(box.minX + 1.0E-4D);
        int maxX = (int) Math.floor(box.maxX - 1.0E-4D);
        int minZ = (int) Math.floor(box.minZ + 1.0E-4D);
        int maxZ = (int) Math.floor(box.maxZ - 1.0E-4D);
        int y = (int) Math.floor(box.minY + 1.0E-4D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (hasSupport.test(new BlockPos(x, y, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean timedOut(int ticks) {
        return ticks > MAX_TICKS;
    }

    public static boolean noProgress(int ticksWithoutProgress) {
        return ticksWithoutProgress > MAX_NO_PROGRESS_TICKS;
    }

    public static double horizontalTargetX(BlockPos landing) {
        return landing.getX() + 0.5D;
    }

    public static double horizontalTargetZ(BlockPos landing) {
        return landing.getZ() + 0.5D;
    }
}
