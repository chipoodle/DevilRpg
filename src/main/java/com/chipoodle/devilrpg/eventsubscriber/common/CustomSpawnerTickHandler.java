package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.spawner.AggressiveZombieSpawnRule;
import com.chipoodle.devilrpg.spawner.CustomSpawner;
import com.chipoodle.devilrpg.spawner.VexSpawnRule;
import com.chipoodle.devilrpg.survival.HordeManager;
import com.chipoodle.devilrpg.survival.LairManager;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Hookea el {@link CustomSpawner} al tick del servidor. Mantiene un spawneador por cada dimension
 * del overworld y registra las reglas de spawn una sola vez.
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public class CustomSpawnerTickHandler {

    private static final Map<ServerLevel, CustomSpawner> SPAWNERS = new HashMap<>();

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            cleanupLevel(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            cleanupLevel(level);
        }
        SPAWNERS.clear();
    }

    private static void cleanupLevel(ServerLevel level) {
        HordeManager.onLevelUnload(level);
        VillageManager.onLevelUnload(level);
        SPAWNERS.remove(level);
    }

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
                s.register(new VexSpawnRule());
                return s;
            });
            spawner.tick();

            // Asedios a las aldeas (iteracion 3 en miniatura).
            VillageManager.tick(level);
            // Guaridas (iteracion 2): focos de enemigos que el jugador puede asaltar.
            LairManager.tick(level);
        });
    }
}
