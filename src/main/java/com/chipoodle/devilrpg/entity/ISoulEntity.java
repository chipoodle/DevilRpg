package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.init.ModDamageTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public interface ISoulEntity {

    int DIVISOR_NIVEL_PARA_POTENCIA_EFECTO = 5;

    /**
     * Kills the entity if it has no owner.
     *
     * @param thisEntity
     */
    default void addToAiStep(ITamableEntity thisEntity) {
        if (thisEntity.getOwnerUUID() == null || thisEntity.getOwner() == null || !thisEntity.getOwner().isAlive() || !thisEntity.isTame()) {
            DamageSource damagesource = new DamageSource(
                    thisEntity.getLevel()
                    .registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(ModDamageTypes.MINION_DEATH));
            thisEntity.hurt(damagesource, Integer.MAX_VALUE);
        }
    }

   default VertexConsumer getBuffer(MultiBufferSource bufferIn, ResourceLocation texture) {
       return bufferIn.getBuffer(RenderType.entityTranslucent(texture));
   }

    default int getPotenciaPocion(int niveles) {
        return (int) Math.ceil((double) niveles / (DIVISOR_NIVEL_PARA_POTENCIA_EFECTO));
    }
}
