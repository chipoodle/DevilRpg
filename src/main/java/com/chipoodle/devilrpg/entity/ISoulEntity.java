package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.skillsystem.MinionDeathDamageSource;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;

public interface ISoulEntity {

	static final int DIVISOR_NIVEL_PARA_POTENCIA_EFECTO = 5;

	public default void addToLivingTick(ITamableEntity thisEntity) {
		if (thisEntity.getOwnerUUID() == null || thisEntity.getOwner() == null || !thisEntity.getOwner().isAlive() || !thisEntity.isTame())
			thisEntity.hurt(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
	}
	
	public default boolean addToWantsToAttack(LivingEntity target, LivingEntity owner) {
		if (target instanceof TameableEntity) {
			TameableEntity entity = (TameableEntity) target;
			return !entity.isTame() || entity.getOwner() != owner;
		} else

		if (target instanceof PlayerEntity && owner instanceof PlayerEntity
				&& !((PlayerEntity) owner).canHarmPlayer((PlayerEntity) target)) {
			return false;
		} else if (target instanceof AbstractHorseEntity && ((AbstractHorseEntity) target).isTamed()) {
			return false;
		} else {
			return true;
		}
	}

	public default IVertexBuilder getBuffer(IRenderTypeBuffer bufferIn, ResourceLocation texture) {
		return bufferIn.getBuffer(RenderType.entityTranslucent(texture));
	}

	public default int getPotenciaPocion(int niveles) {
		return (int) Math.ceil(niveles / (DIVISOR_NIVEL_PARA_POTENCIA_EFECTO));
	}
}
