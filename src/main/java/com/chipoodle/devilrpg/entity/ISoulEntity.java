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

	int DIVISOR_NIVEL_PARA_POTENCIA_EFECTO = 5;

	/**
	 * Kills the entity if it has no owner.
	 * @param thisEntity
	 */
	default void addToAiStep(ITameableEntity thisEntity) {
		if (thisEntity.getOwnerUUID() == null || thisEntity.getOwner() == null || !thisEntity.getOwner().isAlive() || !thisEntity.isTame())
			thisEntity.hurt(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
	}

	default IVertexBuilder getBuffer(IRenderTypeBuffer bufferIn, ResourceLocation texture) {
		return bufferIn.getBuffer(RenderType.entityTranslucent(texture));
	}

	default int getPotenciaPocion(int niveles) {
		return (int) Math.ceil(niveles / (DIVISOR_NIVEL_PARA_POTENCIA_EFECTO));
	}
}
