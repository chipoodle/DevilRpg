package com.chipoodle.devilrpg.spawner;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.spawnprofile.NormalZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Regla de spawn de los <b>vexes</b> (reemplaza a los zombies normales): amenaza aérea que ataca al
 * jugador y puede spawnear de día (a diferencia de los zombies, que se queman al sol). Mantiene las
 * mismas reglas de probabilidad por distancia del jugador a su punto de inicio según
 * {@code NormalZombieSpawnProfile}.
 */
public class NormalZombieSpawnRule implements CustomSpawnRule {

    private static final SpawnScaleProfile PROFILE = NormalZombieSpawnProfile.INSTANCE;

    private static final int MIN_INTERVAL_SECONDS = 20;     // intervalo minimo entre intentos (20 s)
    private static final int MAX_INTERVAL_SECONDS = 2 * 60; // intervalo maximo entre intentos (2 min)
    private static final int MAX_ALIVE_IN_WORLD = 15;       // limite de vexes vivos
    private static final int MIN_SPAWN_DISTANCE = 4;        // minimo cerca del jugador
    private static final int MAX_SPAWN_DISTANCE = 24;       // maximo cerca del jugador
    private static final int SURFACE_SEARCH_DOWN = 8;       // bloques hacia abajo para hallar suelo

    @Override
    public EntityType<? extends Mob> getEntityType() {
        return EntityType.VEX; // vex vanilla: vuela y puede spawnear de día
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
        // Los vexes no se queman al sol: pueden spawnear tanto de día como de noche.
        Vec3 spawn = getSpawnPoint(player);
        if (spawn == null) {
            return 0.0F;
        }
        double distance = Math.sqrt(player.distanceToSqr(spawn));
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

    @Override
    public void configureEntity(Mob entity, ServerLevel level, ServerPlayer player) {
        if (entity instanceof Vex vex) {
            // Vida limitada: el vex se desvanece a los 2 minutos para no acumularse.
            vex.setLimitedLife(2 * 60 * 20);
            // Atacar al jugador objetivo (el VexChargeAttackGoal persigue a getTarget()).
            vex.setTarget(player);
            vex.setPersistenceRequired();
        }
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
        if (cap == null) {
            return null;
        }
        Vec3 anchor = cap.getAnchorPoint();
        return anchor != null ? anchor : cap.getSpawnPoint();
    }
}
