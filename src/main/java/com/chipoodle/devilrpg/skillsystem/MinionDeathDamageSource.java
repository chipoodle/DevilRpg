package com.chipoodle.devilrpg.skillsystem;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;

public class MinionDeathDamageSource extends DamageSource {

	public MinionDeathDamageSource(String damageTypeIn) {
		super(damageTypeIn);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Gets the death message that is displayed when the player dies
	 */
	public ITextComponent getDeathMessage(LivingEntity entityLivingBaseIn) {
		LivingEntity livingentity = entityLivingBaseIn.getAttackingEntity();
		return null;//new TranslationTextComponent("");

	}

}
