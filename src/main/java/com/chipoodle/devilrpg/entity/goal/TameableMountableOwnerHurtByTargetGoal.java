package com.chipoodle.devilrpg.entity.goal;

import java.util.EnumSet;

import com.chipoodle.devilrpg.entity.ITameableEntity;

import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TargetGoal;


public class TameableMountableOwnerHurtByTargetGoal extends TargetGoal {
   private final ITameableEntity tameAnimal;
   private LivingEntity ownerLastHurtBy;
   private int timestamp;

   public TameableMountableOwnerHurtByTargetGoal(ITameableEntity p_i1667_1_) {
      super((MobEntity) p_i1667_1_, false);
      this.tameAnimal = p_i1667_1_;
      this.setFlags(EnumSet.of(Goal.Flag.TARGET));
   }

   public boolean canUse() {
      if (this.tameAnimal.isTame() && !this.tameAnimal.isOrderedToSit()) {
         LivingEntity livingentity = this.tameAnimal.getOwner();
         if (livingentity == null) {
            return false;
         } else {
            this.ownerLastHurtBy = livingentity.getLastHurtByMob();
            int i = livingentity.getLastHurtByMobTimestamp();
            return i != this.timestamp && this.canAttack(this.ownerLastHurtBy, EntityPredicate.DEFAULT) && this.tameAnimal.wantsToAttack(this.ownerLastHurtBy, livingentity);
         }
      } else {
         return false;
      }
   }

   public void start() {
      this.mob.setTarget(this.ownerLastHurtBy);
      LivingEntity livingentity = this.tameAnimal.getOwner();
      if (livingentity != null) {
         this.timestamp = livingentity.getLastHurtByMobTimestamp();
      }

      super.start();
   }
}
