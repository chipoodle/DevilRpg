package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.skillsystem.MinionDeathDamageSource;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public interface ISoulEntity {

    int DIVISOR_NIVEL_PARA_POTENCIA_EFECTO = 5;

    /**
     * Kills the entity if it has no owner.
     *
     * @param thisEntity
     */
    default void addToAiStep(ITamableEntity thisEntity) {
        if (thisEntity.getOwnerUUID() == null || thisEntity.getOwner() == null || !thisEntity.getOwner().isAlive() || !thisEntity.isTame())
            thisEntity.hurt(new MinionDeathDamageSource("DEATH BY NO OWNER"), Integer.MAX_VALUE);
    }

   default VertexConsumer getBuffer(MultiBufferSource bufferIn, ResourceLocation texture) {
       return bufferIn.getBuffer(RenderType.entityTranslucent(texture));
   }

    default int getPotenciaPocion(int niveles) {
        return (int) Math.ceil((double) niveles / (DIVISOR_NIVEL_PARA_POTENCIA_EFECTO));
    }
}
