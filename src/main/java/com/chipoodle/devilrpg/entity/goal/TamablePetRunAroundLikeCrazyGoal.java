package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.entity.AbstractMountablePet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TamablePetRunAroundLikeCrazyGoal  extends Goal{
    private final AbstractMountablePet pet;
    private final double speedModifier;
    private double posX;
    private double posY;
    private double posZ;

    public TamablePetRunAroundLikeCrazyGoal(AbstractMountablePet p_25890_, double p_25891_) {
        this.pet = p_25890_;
        this.speedModifier = p_25891_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (!this.pet.isTame() && this.pet.isVehicle()) {
            Vec3 vec3 = DefaultRandomPos.getPos(this.pet, 5, 4);
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
        this.pet.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speedModifier);
    }

    public boolean canContinueToUse() {
        return !this.pet.isTame() && !this.pet.getNavigation().isDone() && this.pet.isVehicle();
    }

    public void tick() {
        if (!this.pet.isTame() && this.pet.getRandom().nextInt(this.adjustedTickDelay(50)) == 0) {
            Entity entity = this.pet.getFirstPassenger();
            if (entity == null) {
                return;
            }

            if (entity instanceof Player player) {
                int i = this.pet.getTemper();
                int j = this.pet.getMaxTemper();
                if (j > 0 && this.pet.getRandom().nextInt(j) < i && !net.minecraftforge.event.ForgeEventFactory.onAnimalTame(pet, (Player)entity)) {
                    this.pet.tame(player);
                    return;
                }

                this.pet.modifyTemper(5);
            }

            this.pet.ejectPassengers();
            this.pet.makeMad();
            this.pet.level.broadcastEntityEvent(this.pet, (byte)6);
        }

    }
}
