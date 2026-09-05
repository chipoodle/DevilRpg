package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.spawner.AggressiveZombieSpawnRule;
import com.chipoodle.devilrpg.spawner.CustomSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Hookea el {@link CustomSpawner} al tick del servidor. Mantiene un spawneador por cada dimension
 * del overworld y registra las reglas de spawn una sola vez.
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class CustomSpawnerTickHandler {

    private static final Map<ServerLevel, CustomSpawner> SPAWNERS = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        event.getServer().getAllLevels().forEach(level -> {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                return;
            }
            CustomSpawner spawner = SPAWNERS.computeIfAbsent(level, l -> {
                CustomSpawner s = new CustomSpawner(l);
                // Aqui se registran las entidades custom. Anade mas reglas para mas mobs.
                s.register(new AggressiveZombieSpawnRule());
                return s;
            });
            spawner.tick();
        });
    }
}
