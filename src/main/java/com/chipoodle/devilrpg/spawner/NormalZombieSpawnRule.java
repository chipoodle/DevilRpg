package com.chipoodle.devilrpg.spawner;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.spawnprofile.NormalZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Regla de spawn del {@code NormalZombieEntity}: presión nocturna de base que ataca al jugador desde el
 * inicio, incluso en la zona segura (su perfil tiene {@code minDistance = 0}, sin zona protegida).
 * <p>
 * Solo spawnea de noche (de día el zombie normal se quemaría), y su probabilidad crece con la distancia
 * del jugador a su punto de inicio según el {@code NormalZombieSpawnProfile}.
 */
public class NormalZombieSpawnRule implements CustomSpawnRule {

    private static final SpawnScaleProfile PROFILE = NormalZombieSpawnProfile.INSTANCE;

    private static final int MIN_INTERVAL_SECONDS = 20;     // intervalo minimo entre intentos (20 s)
    private static final int MAX_INTERVAL_SECONDS = 2 * 60; // intervalo maximo entre intentos (2 min)
    private static final int MAX_ALIVE_IN_WORLD = 15;       // limite de zombies normales vivos
    private static final int MIN_SPAWN_DISTANCE = 4;        // minimo cerca del jugador
    private static final int MAX_SPAWN_DISTANCE = 24;       // maximo cerca del jugador
    private static final int SURFACE_SEARCH_DOWN = 8;       // bloques hacia abajo para hallar suelo

    @Override
    public EntityType<? extends Mob> getEntityType() {
        return ModEntities.NORMAL_ZOMBIE.get();
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
    public float getSpawnChance(ServerLevel level, ServerPlayer player) {
        // Solo de noche: el zombie normal se quema al sol, así que de día no tiene sentido spawnearlo.
        if (!level.isNight()) {
            return 0.0F;
        }
        Vec3 spawn = getSpawnPoint(player);
        if (spawn == null) {
            return 0.0F;
        }
        double distance = Math.sqrt(player.distanceToSqr(spawn));
        // minDistance = 0 -> sin zona protegida, probabilidad > 0 desde el inicio.
        return (float) PROFILE.probability(distance, ThreatLevel.current(level));
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

    @Override
    public int getMaxAliveInLevel() {
        return MAX_ALIVE_IN_WORLD;
    }

    @Override
    public int getMinSpawnCount() {
        return 1;
    }

    @Override
    public int getMaxSpawnCount() {
        return 2;
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
