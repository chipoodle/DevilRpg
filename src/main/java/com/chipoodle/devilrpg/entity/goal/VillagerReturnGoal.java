package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import java.util.EnumSet;

/**
 * Brings a worker that fled beyond profession range back into the village.
 *
 * <p>Profession goals such as {@code VillagerFisherGoal} refuse to start beyond
 * {@code FENCE_RADIUS + 10}; without this goal a displaced worker stays idle forever.
 * This goal owns MOVE only while the worker is outside that range; flee (priority 0)
 * still wins while a monster is near.</p>
 */
public class VillagerReturnGoal extends Goal {
    private static final float SPEED = 1.0F;

    private final Villager villager;
    private final BlockPos center;

    public VillagerReturnGoal(Villager villager, BlockPos center) {
        this.villager = villager;
        this.center = center.immutable();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !villager.isBaby() && VillagerReturnPolicy.shouldStart(distanceSqr());
    }

    @Override
    public boolean canContinueToUse() {
        return !villager.isBaby() && VillagerReturnPolicy.shouldContinue(distanceSqr());
    }

    @Override
    public void start() {
        VillageManager.ponerActividad(villager, "Volviendo al pueblo");
        move();
    }

    @Override
    public void tick() {
        move();
    }

    @Override
    public void stop() {
        villager.getNavigation().stop();
    }

    private void move() {
        VillageManager.caminarHacia(villager, center, SPEED);
    }

    private double distanceSqr() {
        return villager.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D);
    }
}
