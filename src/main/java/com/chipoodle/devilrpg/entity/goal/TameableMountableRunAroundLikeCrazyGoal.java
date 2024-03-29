package com.chipoodle.devilrpg.entity.goal;

import java.util.EnumSet;

import com.chipoodle.devilrpg.entity.AbstractMountableTameableEntity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector3d;

public class TameableMountableRunAroundLikeCrazyGoal extends Goal {
   private final AbstractMountableTameableEntity horse;
   private final double speedModifier;
   private double posX;
   private double posY;
   private double posZ;

   public TameableMountableRunAroundLikeCrazyGoal(AbstractMountableTameableEntity p_i1653_1_, double p_i1653_2_) {
      this.horse = p_i1653_1_;
      this.speedModifier = p_i1653_2_;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE));
   }

   public boolean canUse() {
      if (!this.horse.isTame() && this.horse.isVehicle()) {
         Vector3d vector3d = RandomPositionGenerator.getPos(this.horse, 5, 4);
         if (vector3d == null) {
            return false;
         } else {
            this.posX = vector3d.x;
            this.posY = vector3d.y;
            this.posZ = vector3d.z;
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
      if (!this.horse.isTame() && this.horse.getRandom().nextInt(50) == 0) {
         Entity entity = this.horse.getPassengers().get(0);
         if (entity == null) {
            return;
         }

         if (entity instanceof PlayerEntity) {
            int i = this.horse.getTemper();
            int j = this.horse.getMaxTemper();
            if (j > 0 && this.horse.getRandom().nextInt(j) < i && !net.minecraftforge.event.ForgeEventFactory.onAnimalTame(horse, (PlayerEntity)entity)) {
               this.horse.tame((PlayerEntity)entity);
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

