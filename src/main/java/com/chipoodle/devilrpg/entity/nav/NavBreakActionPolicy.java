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

    private static final java.util.Map<String, Integer> diagnosticEvents = new java.util.HashMap<>();

    private static void trace(Mob mob, String reason, BlockPos stand, BlockPos target, Vec3 eye,
                              Vec3 rayEnd, BlockHitResult hit, double distanceSqr,
                              double tEnter, double legacyAimY) {
        if (!NavTrace.on()) return;
        String uuid = mob == null ? "-" : mob.getUUID().toString();
        int emitted = diagnosticEvents.getOrDefault(uuid, 0);
        if (emitted >= 128) return;
        diagnosticEvents.put(uuid, emitted + 1);
        NavTrace.ev("BREAK_LOS_DIAG", "uuid=" + uuid
                + " caller=NavBreakActionPolicy#executable reason=" + reason
                + " stand=" + (stand == null ? "-" : stand.toShortString())
                + " target=" + (target == null ? "-" : target.toShortString())
                + " eye=" + eye
                + " distanceSqr=" + distanceSqr
                + " legacyTEnter=" + tEnter
                + " legacyAimY=" + legacyAimY
                + " legacyRayEnd=" + (target == null ? "-" : "(" + (target.getX() + 0.5D)
                        + "," + legacyAimY + "," + (target.getZ() + 0.5D) + ")")
                + " rayEnd=" + rayEnd
                + " hitType=" + (hit == null ? "-" : hit.getType())
                + " hitBlock=" + (hit == null ? "-" : hit.getBlockPos().toShortString()));
    }

    public static boolean executable(CollisionGetter level, Mob mob, BlockPos stand, BlockPos target,
                                     NavObstruction.Policies policies, double actionReachSqr) {
        if (level == null || mob == null || stand == null || target == null || policies == null) {
            trace(mob, "NULL_INPUT", stand, target, null, null, null, -1.0D, -1.0D, Double.NaN);
            return false;
        }
        boolean protectedTarget = policies.isProtected(target);
        boolean breakAllowed = policies.canBreak(target);
        boolean standFits = NavObstruction.fits(level, mob, stand);
        boolean standSupported = NavObstruction.hasSupport(level, stand);
        if (protectedTarget || !breakAllowed || !standFits || !standSupported) {
            trace(mob, "PRECONDITION_REJECT protected=" + protectedTarget + " breakAllowed=" + breakAllowed
                    + " standFits=" + standFits + " standSupported=" + standSupported,
                    stand, target, null, null, null, -1.0D, -1.0D, Double.NaN);
            return false;
        }

        // Preserve the established action-range metric (mob feet -> target center).
        // Planning uses the hypothetical stand-cell center; execution uses the mob's exact position.
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
        if (!NavBreakReachPolicy.reachable(stand.getY(), target.getY(), distanceSqr, actionReachSqr)) {
            trace(mob, "REACH_REJECT", stand, target, eye, null, null, distanceSqr, -1.0D, Double.NaN);
            return false;
        }

        // Keep the former ray calculation in diagnostics so the geometric failure is measurable.
        double tEnter = 1.0D;
        if (Math.abs(dx) > 1.0E-7D) {
            double faceX = dx > 0.0D ? target.getX() : target.getX() + 1.0D;
            double tX = (faceX - eyeX) / dx;
            if (tX > 0.0D) tEnter = Math.min(tEnter, tX);
        }
        if (Math.abs(dz) > 1.0E-7D) {
            double faceZ = dz > 0.0D ? target.getZ() : target.getZ() + 1.0D;
            double tZ = (faceZ - eyeZ) / dz;
            if (tZ > 0.0D) tEnter = Math.min(tEnter, tZ);
        }
        double legacyAimY = tEnter > 0.0D && tEnter < 1.0D
                ? eyeY + (target.getY() + 0.5D - eyeY) / tEnter : Double.NaN;

        // Raycast to the target's interior center. The previous near-voxel-face projection
        // assumed a full-cube collision shape; for a thin pane it projected the endpoint below
        // the block, so the ray missed the pane's narrow central collision shape.
        Vec3 rayEnd = targetCenter;
        BlockHitResult hit = level.clip(new ClipContext(eye, rayEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        boolean accepted = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
        trace(mob, accepted ? "ACCEPT" : "RAYCAST_REJECT", stand, target, eye, rayEnd, hit,
                distanceSqr, tEnter, legacyAimY);
        return accepted;
    }

    static boolean valid(boolean standClear, boolean supported, boolean targetProtected,
                         boolean breakAllowed, boolean inReach, boolean lineOfSight) {
        return standClear && supported && !targetProtected && breakAllowed && inReach && lineOfSight;
    }
}
