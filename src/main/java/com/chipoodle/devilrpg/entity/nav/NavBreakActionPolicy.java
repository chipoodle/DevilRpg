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
        if (level == null || mob == null || stand == null || target == null || policies == null) return false;
        if (policies.isProtected(target) || !policies.canBreak(target)
                || !NavObstruction.fits(level, mob, stand)
                || !NavObstruction.hasSupport(level, stand)) return false;

        // Keep the existing action-range metric (mob feet position -> target center).
        // During planning use the hypothetical cell center; during execution use the mob's exact position.
        boolean actualStand = mob.blockPosition().equals(stand);
        double feetX = actualStand ? mob.getX() : stand.getX() + 0.5D;
        double feetY = actualStand ? mob.getY() : stand.getY();
        double feetZ = actualStand ? mob.getZ() : stand.getZ() + 0.5D;
        double eyeX = actualStand ? mob.getEyePosition().x : stand.getX() + 0.5D;
        double eyeY = actualStand ? mob.getEyePosition().y : stand.getY() + mob.getEyeHeight();
        double eyeZ = actualStand ? mob.getEyePosition().z : stand.getZ() + 0.5D;
        Vec3 eye = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 targetCenter = target.getCenter();
        double dx = targetCenter.x - feetX;
        double dy = targetCenter.y - feetY;
        double dz = targetCenter.z - feetZ;
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
