package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.skillsystem.MinionDeathDamageSource;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;

public interface ISoulEntity {

	public default void addToLivingTick(TameableEntity thisEntity) {
		if (thisEntity.getOwnerId() == null || thisEntity.getOwner() == null || !thisEntity.getOwner().isAlive() || !thisEntity.isTamed())
			thisEntity.attackEntityFrom(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
		
		
		/*if (thisEntity.world.getGameTime() % 80L == 0L) {
			EffectInstance aux = new EffectInstance(Effects.GLOWING, 120, 0, true, false);
			EffectInstance active = thisEntity.getActivePotionEffect(Effects.GLOWING);
			if (thisEntity.getActivePotionEffect(Effects.GLOWING) == null
					|| aux.getAmplifier() > active.getAmplifier()) {
				thisEntity.addPotionEffect(aux);
			} else {
				active.combine(aux);
			}
		}*/
		
	}
	public default boolean addToshouldAttackEntity(LivingEntity target, LivingEntity owner){
		if (target instanceof TameableEntity) {
			TameableEntity entity = (TameableEntity) target;
			return !entity.isTamed() || entity.getOwner() != owner;
		} else 
			
			if (target instanceof PlayerEntity && owner instanceof PlayerEntity
				&& !((PlayerEntity) owner).canAttackPlayer((PlayerEntity) target)) {
			return false;
		} else if (target instanceof AbstractHorseEntity && ((AbstractHorseEntity) target).isTame()) {
			return false;
		} else {
			return true;
		}
	}
	
	public default boolean isEntitySameOwnerAsThis(Entity entityIn, TameableEntity entityThis) {
		boolean isSameOwner = false;
		if (entityIn instanceof TameableEntity && ((TameableEntity) entityIn).getOwner() != null)
			isSameOwner = ((TameableEntity) entityIn).getOwner().equals(entityThis.getOwner());
		return isSameOwner;
	}
}
