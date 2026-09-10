package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.entity.SoulWisp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.EnumSet;

public class SoulWispFollowOwnerGoal extends Goal {
    /**
     * Distancia a la que el wisp, si sigue sin poder llegar al dueño, se <b>teletransporta</b> a su lado.
     * Está por encima del `startDistance` (16) a propósito: entre 16 y 32 el wisp <b>vuela</b> de vuelta y
     * solo se teletransporta si de verdad no consigue alcanzarlo.
     */
    public static final int TELEPORT_WHEN_DISTANCE_IS = 32;
    private static final int MIN_HORIZONTAL_DISTANCE_FROM_PLAYER_WHEN_TELEPORTING = 2;
    private static final int MAX_HORIZONTAL_DISTANCE_FROM_PLAYER_WHEN_TELEPORTING = 3;
    private static final int MAX_VERTICAL_DISTANCE_FROM_PLAYER_WHEN_TELEPORTING = 1;
    private final SoulWisp soulWisp;
    private final LevelReader level;
    private final double speedModifier;
    private final PathNavigation navigation;
    private final float stopDistance;
    private final float startDistance;
    private final boolean canFly;
    private LivingEntity owner;
    private int timeToRecalcPath;
    private float oldWaterCost;

    public SoulWispFollowOwnerGoal(SoulWisp soulWisp, double speedModifier, float startDistance, float stopDistance, boolean canFly) {
        this.soulWisp = soulWisp;
        this.level = soulWisp.level();
        this.speedModifier = speedModifier;
        this.navigation = soulWisp.getNavigation();
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.canFly = canFly;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        if (!(soulWisp.getNavigation() instanceof GroundPathNavigation) && !(soulWisp.getNavigation() instanceof FlyingPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for FollowOwnerGoal");
        }
    }

    public boolean canUse() {
        LivingEntity owner = this.soulWisp.getOwner();
        if (owner == null) {
            return false;
        } else if (owner.isSpectator()) {
            return false;
        } else if (this.unableToMove()) {
            return false;
        } else if (this.soulWisp.distanceToSqr(owner) < (double) (this.startDistance * this.startDistance)) {
            return false;
        } else {
            // Antes había aquí un caso especial que devolvía false si el ranger tenía los goals de cortar o
            // recoger madera registrados — y como SIEMPRE tiene el de cortar, en la práctica el ranger nunca
            // seguía a su dueño. Se quitó a propósito: ahora, si el dueño se aleja más allá de startDistance,
            // suelta la tarea y vuelve (los goals de trabajo declaran el flag MOVE, así que esta prioridad
            // manda de verdad mientras está activo).
            this.owner = owner;
            return true;
        }
    }

    public boolean canContinueToUse() {
        if (this.navigation.isDone()) {
            return false;
        } else if (this.unableToMove()) {
            return false;
        } else {
            return (this.soulWisp.distanceToSqr(this.owner) > (double) (this.stopDistance * this.stopDistance));
        }
    }

    private boolean unableToMove() {
        return this.soulWisp.isOrderedToSit() || this.soulWisp.isPassenger() || this.soulWisp.isLeashed();
    }

    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.soulWisp.getPathfindingMalus(PathType.WATER);
        this.soulWisp.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    public void stop() {
        this.owner = null;
        this.navigation.stop();
        this.soulWisp.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
    }

    public void tick() {
        this.soulWisp.getLookControl().setLookAt(this.owner, 10.0F, (float) this.soulWisp.getMaxHeadXRot());
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            if (this.soulWisp.distanceToSqr(this.owner) >= (double) (TELEPORT_WHEN_DISTANCE_IS * TELEPORT_WHEN_DISTANCE_IS)) {
                this.teleportToOwner();
            } else {
                this.navigation.moveTo(this.owner, this.speedModifier);
            }

        }
    }

    private void teleportToOwner() {
        BlockPos blockpos = this.owner.blockPosition();

        for (int i = 0; i < 10; ++i) {
            int j = this.randomIntInclusive(-3, 3);
            int k = this.randomIntInclusive(-1, 1);
            int l = this.randomIntInclusive(-3, 3);
            boolean flag = this.maybeTeleportTo(blockpos.getX() + j, blockpos.getY() + k, blockpos.getZ() + l);
            if (flag) {
                return;
            }
        }

    }

    private boolean maybeTeleportTo(int p_25304_, int p_25305_, int p_25306_) {
        if (Math.abs((double) p_25304_ - this.owner.getX()) < 2.0D && Math.abs((double) p_25306_ - this.owner.getZ()) < 2.0D) {
            return false;
        } else if (!this.canTeleportTo(new BlockPos(p_25304_, p_25305_, p_25306_))) {
            return false;
        } else {
            this.soulWisp.moveTo((double) p_25304_ + 0.5D, (double) p_25305_, (double) p_25306_ + 0.5D, this.soulWisp.getYRot(), this.soulWisp.getXRot());
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos p_25308_) {
            BlockState blockstate = this.level.getBlockState(p_25308_.below());
            if (!this.canFly && blockstate.getBlock() instanceof LeavesBlock) {
                return false;
            } else {
                BlockPos blockpos = p_25308_.subtract(this.soulWisp.blockPosition());
                return this.level.noCollision(this.soulWisp, this.soulWisp.getBoundingBox().move(blockpos));
            }
    }

    private int randomIntInclusive(int p_25301_, int p_25302_) {
        return this.soulWisp.getRandom().nextInt(p_25302_ - p_25301_ + 1) + p_25301_;
    }
}