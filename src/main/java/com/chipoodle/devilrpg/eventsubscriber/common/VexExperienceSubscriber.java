package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.spawner.VexSpawnRule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.monster.Vex;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * Aplica la XP escalada de los vexes al morir. Como el vex es una entidad vanilla (no podemos
 * sobreescribir su {@code getExperienceReward}), {@link VexSpawnRule} guarda la XP calculada (según la
 * distancia del spawn y la amenaza) en los datos persistentes del vex al crearlo; aquí se aplica cuando
 * el vex muere.
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class VexExperienceSubscriber {

    private VexExperienceSubscriber() {
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Vex vex) {
            CompoundTag data = vex.getPersistentData();
            if (data.contains(VexSpawnRule.VEX_XP_KEY)) {
                event.setDroppedExperience(data.getInt(VexSpawnRule.VEX_XP_KEY));
            }
        }
    }
}
