package com.chipoodle.devilrpg.spawner;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Regla de spawn del {@link com.chipoodle.devilrpg.entity.AggressiveZombieEntity}.
 * <p>
 * - Intervalo aleatorio: 30 s a 10 min.
 * - Spawnea a 48-96 bloques del jugador (como si llevara tiempo patrullando).
 * - La probabilidad sube cuanto mas lejos este el jugador de su punto de inicio, y los atributos
 *   (vida/velocidad/daño) escalan automaticamente con la distancia (lo hace la propia entidad en
 *   {@code adjustAttributesBasedOnSpawnDistance}).
 */
public class AggressiveZombieSpawnRule implements CustomSpawnRule {

    // --- Configuracion de esta regla (usa constantes) ---
    private static final int MIN_INTERVAL_SECONDS = 30;      // intervalo minimo entre intentos (30 s)
    private static final int MAX_INTERVAL_SECONDS = 7 * 60;  // intervalo maximo entre intentos (7 min)
    private static final int MAX_ALIVE_IN_WORLD = 30;        // limite de zombies agresivos vivos simultaneos

    private static final int MIN_PLAYER_DISTANCE = 200;      // por debajo: probabilidad 0
    private static final int MAX_PLAYER_DISTANCE = 1500;     // por encima: probabilidad 1
    private static final int MIN_SPAWN_DISTANCE = 48;        // minimo lejos del jugador (bloques)
    private static final int MAX_SPAWN_DISTANCE = 96;        // maximo lejos del jugador (bloques)
    private static final int SURFACE_SEARCH_DOWN = 16;       // bloques hacia abajo para hallar suelo

    @Override
    public EntityType<? extends Mob> getEntityType() {
        return ModEntities.AGGRESSIVE_ZOMBIE.get();
    }

    @Override
    public int getMinIntervalTicks() {
        return MIN_INTERVAL_SECONDS * 20;
    }

    @Override
    public int getMaxIntervalTicks() {
        return MAX_INTERVAL_SECONDS * 20;
    }

    @Override
    public int getMaxAliveInLevel() {
        return MAX_ALIVE_IN_WORLD;
    }

    @Override
    public float getSpawnChance(ServerLevel level, ServerPlayer player) {
        Vec3 spawnPoint = getSpawnPoint(player);
        if (spawnPoint == null) {
            return 0.0F;
        }
        double distance = Math.sqrt(player.distanceToSqr(spawnPoint));
        if (distance < MIN_PLAYER_DISTANCE) {
            return 0.0F;
        }
        if (distance > MAX_PLAYER_DISTANCE) {
            return 1.0F;
        }
        return (float) ((distance - MIN_PLAYER_DISTANCE) / (MAX_PLAYER_DISTANCE - MIN_PLAYER_DISTANCE));
    }

    @Override
    @Nullable
    public BlockPos findSpawnPosition(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 25; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = MIN_SPAWN_DISTANCE + level.random.nextDouble() * (MAX_SPAWN_DISTANCE - MIN_SPAWN_DISTANCE);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos surface = findSurface(level, new BlockPos(x, player.getBlockY(), z));
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos findSurface(ServerLevel level, BlockPos start) {
        for (int y = start.getY(); y > start.getY() - SURFACE_SEARCH_DOWN; y--) {
            BlockPos pos = new BlockPos(start.getX(), y, start.getZ());
            if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir()) {
                return pos.above();
            }
        }
        return null;
    }

    private Vec3 getSpawnPoint(ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface cap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        return cap == null ? null : cap.getSpawnPoint();
    }
}
