package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.entity.AbstractMountablePet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TamablePetRunAroundLikeCrazyGoal  extends Goal{
    private final AbstractMountablePet horse;
    private final double speedModifier;
    private double posX;
    private double posY;
    private double posZ;

    public TamablePetRunAroundLikeCrazyGoal(AbstractMountablePet p_25890_, double p_25891_) {
        this.horse = p_25890_;
        this.speedModifier = p_25891_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (!this.horse.isTame() && this.horse.isVehicle()) {
            Vec3 vec3 = DefaultRandomPos.getPos(this.horse, 5, 4);
            if (vec3 == null) {
                return false;
            } else {
                this.posX = vec3.x;
                this.posY = vec3.y;
                this.posZ = vec3.z;
                return true;
            }
        } else {
            return false;
        }
    }

    public void start() {
        this.horse.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speedModifier);
    }

    public boolean canContinueToUse() {
        return !this.horse.isTame() && !this.horse.getNavigation().isDone() && this.horse.isVehicle();
    }

    public void tick() {
        if (!this.horse.isTame() && this.horse.getRandom().nextInt(this.adjustedTickDelay(50)) == 0) {
            Entity entity = this.horse.getFirstPassenger();
            if (entity == null) {
                return;
            }

            if (entity instanceof Player) {
                Player player = (Player)entity;
                int i = this.horse.getTemper();
                int j = this.horse.getMaxTemper();
                if (j > 0 && this.horse.getRandom().nextInt(j) < i && !net.minecraftforge.event.ForgeEventFactory.onAnimalTame(horse, (Player)entity)) {
                    this.horse.tame(player);
                    return;
                }

                this.horse.modifyTemper(5);
            }

            this.horse.ejectPassengers();
            this.horse.makeMad();
            this.horse.level.broadcastEntityEvent(this.horse, (byte)6);
        }

    }
}
