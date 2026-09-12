package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.init.ModDamageTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public interface ISoulEntity {

    int DIVISOR_NIVEL_PARA_POTENCIA_EFECTO = 5;

    /**
     * ¿Este minion debe morir cuando su dueño <b>no está disponible</b> (desconectado, o en otra dimensión)?
     * <p>
     * Por defecto {@code true}, que es lo correcto para los <b>temporales</b> (el shulker del hongo vive 1
     * minuto y no se guarda). Los minions <b>persistentes</b> (lobo, oso y wisp) lo sobrescriben a
     * {@code false}: solo mueren si el dueño <b>existe y está muerto</b>, así pueden esperar a que el jugador
     * vuelva a entrar (el estado se guarda en la capability de minions del jugador).
     */
    default boolean despawnsWithoutOwner() {
        return true;
    }

    /**
     * Kills the entity if it has no owner.
     *
     * @param thisEntity instance of ITamableEntity
     */
    default void addToAiStep(ITamableEntity thisEntity) {
        UUID ownerUUID = thisEntity.getOwnerUUID();
        LivingEntity owner = thisEntity.getOwner();
        // "Sin dueño asignado" y "dueño muerto" son definitivos: el minion se va.
        boolean neverTamed = ownerUUID == null || !thisEntity.isTame();
        boolean ownerDead = owner != null && !owner.isAlive();
        // "No lo encuentro" NO es lo mismo que "está muerto": puede estar desconectado o en otra dimensión.
        // Ahí solo mueren los temporales; los persistentes esperan al jugador.
        boolean ownerMissing = owner == null;
        boolean persistent = thisEntity instanceof ISoulEntity soul && !soul.despawnsWithoutOwner();
        if (neverTamed || ownerDead || (ownerMissing && !persistent)) {
            DamageSource damagesource = new DamageSource(
                    thisEntity.level()
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
