package com.chipoodle.devilrpg.entity.goal;

import java.util.EnumSet;

import com.chipoodle.devilrpg.entity.ITameableEntity;

import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TargetGoal;

public class TameableMountableOwnerHurtTargetGoal extends TargetGoal {
   private final ITameableEntity tameAnimal;
   private LivingEntity ownerLastHurt;
   private int timestamp;

   public TameableMountableOwnerHurtTargetGoal(ITameableEntity p_i1668_1_) {
      super((MobEntity) p_i1668_1_, false);
      this.tameAnimal = p_i1668_1_;
      this.setFlags(EnumSet.of(Goal.Flag.TARGET));
   }

   public boolean canUse() {
      if (this.tameAnimal.isTame() && !this.tameAnimal.isOrderedToSit()) {
         LivingEntity livingentity = this.tameAnimal.getOwner();
         if (livingentity == null) {
            return false;
         } else {
            this.ownerLastHurt = livingentity.getLastHurtMob();
            int i = livingentity.getLastHurtMobTimestamp();
            return i != this.timestamp && this.canAttack(this.ownerLastHurt, EntityPredicate.DEFAULT) && this.tameAnimal.wantsToAttack(this.ownerLastHurt, livingentity);
         }
      } else {
         return false;
      }
   }

   public void start() {
      this.mob.setTarget(this.ownerLastHurt);
      LivingEntity livingentity = this.tameAnimal.getOwner();
      if (livingentity != null) {
         this.timestamp = livingentity.getLastHurtMobTimestamp();
      }

      super.start();
   }
}
