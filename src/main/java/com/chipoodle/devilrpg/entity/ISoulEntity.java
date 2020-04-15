package com.chipoodle.devilrpg.entity;

import net.minecraft.entity.LivingEntity;

public interface ISoulEntity {

	public default void addToLivingTick(LivingEntity thisEntity) {
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
}
