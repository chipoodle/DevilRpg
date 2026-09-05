package com.chipoodle.devilrpg.spawner;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawneador generico dirigido por tick del servidor.
 * <p>
 * Mantiene una lista de {@link CustomSpawnRule}. Cada regla tiene su propio temporizador aleatorio
 * (entre min y max interval); cuando vence, se evalua la probabilidad por jugador y, si pasa, se
 * spawna la entidad en una posicion valida lejos del jugador. Luego el temporizador se reinicia a un
 * nuevo intervalo aleatorio.
 */
public class CustomSpawner {

    private final ServerLevel level;
    private final List<CustomSpawnRule> rules = new ArrayList<>();
    private final Map<CustomSpawnRule, Integer> remainingTicks = new HashMap<>();

    public CustomSpawner(ServerLevel level) {
        this.level = level;
    }

    /** Registra una regla de spawn. Puede llamarse varias veces para registrar varias entidades. */
    public void register(CustomSpawnRule rule) {
        rules.add(rule);
        remainingTicks.put(rule, nextInterval(rule));
    }

    /** Debe llamarse cada tick del servidor. */
    public void tick() {
        if (level.getServer() == null) {
            return;
        }
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.level() == level)
                .toList();
        if (players.isEmpty()) {
            return;
        }
        for (CustomSpawnRule rule : rules) {
            int remaining = remainingTicks.getOrDefault(rule, nextInterval(rule));
            if (remaining > 0) {
                remainingTicks.put(rule, remaining - 1);
                continue;
            }
            // Temporizador vencido: intentar spawnear
            trySpawn(rule, players);
            remainingTicks.put(rule, nextInterval(rule));
        }
    }

    private void trySpawn(CustomSpawnRule rule, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (level.random.nextFloat() >= rule.getSpawnChance(level, player)) {
                continue;
            }
            BlockPos pos = rule.findSpawnPosition(level, player);
            if (pos != null) {
                spawn(rule, player, pos);
                return; // solo un spawn por vencimiento de temporizador
            }
        }
    }

    private void spawn(CustomSpawnRule rule, ServerPlayer player, BlockPos pos) {
        EntityType<? extends Mob> type = rule.getEntityType();
        Mob mob = type.create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
        if (mob == null) {
            return;
        }
        // Colocar en el centro del bloque, mirando al jugador para que "patrulle" hacia el.
        double dx = player.getX() - pos.getX();
        double dz = player.getZ() - pos.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        mob.setDeltaMovement(0, 0, 0);
        rule.configureEntity(mob, level, player);
        level.addFreshEntity(mob);
    }

    private int nextInterval(CustomSpawnRule rule) {
        int min = Math.max(0, rule.getMinIntervalTicks());
        int max = Math.max(min, rule.getMaxIntervalTicks());
        return min + level.random.nextInt(max - min + 1);
    }
}
