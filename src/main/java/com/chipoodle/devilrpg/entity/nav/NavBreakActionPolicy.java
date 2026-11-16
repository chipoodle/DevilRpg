package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Executable BREAK-edge contract: a solid target is an action, never a movement waypoint. */
public final class NavBreakActionPolicy {
    public static final double ACTION_REACH_SQR = 6.25D;

    private NavBreakActionPolicy() { }

    public static boolean executable(CollisionGetter level, Mob mob, BlockPos stand, BlockPos target,
                                     NavObstruction.Policies policies, double actionReachSqr) {
        if (level == null || mob == null || stand == null || target == null || policies == null
                || !level.hasChunkAt(stand) || !level.hasChunkAt(target)) return false;
        if (policies.isProtected(target) || !policies.canBreak(target)
                || !NavObstruction.fits(level, mob, stand)
                || !NavObstruction.hasSupport(level, stand)) return false;

        double eyeX = stand.getX() + 0.5D;
        double eyeY = stand.getY() + mob.getEyeHeight();
        double eyeZ = stand.getZ() + 0.5D;
        Vec3 eye = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 targetCenter = target.getCenter();
        double dx = targetCenter.x - eyeX;
        double dy = targetCenter.y - eyeY;
        double dz = targetCenter.z - eyeZ;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        if (!NavBreakReachPolicy.reachable(stand.getY(), target.getY(), distanceSqr, actionReachSqr)) return false;

        BlockHitResult hit = level.clip(new ClipContext(eye, targetCenter,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    static boolean valid(boolean standClear, boolean supported, boolean targetProtected,
                         boolean breakAllowed, boolean inReach, boolean lineOfSight) {
        return standClear && supported && !targetProtected && breakAllowed && inReach && lineOfSight;
    }
}
