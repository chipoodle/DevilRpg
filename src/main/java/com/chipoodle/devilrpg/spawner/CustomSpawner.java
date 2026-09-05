package com.chipoodle.devilrpg.spawner;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

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
 * <p>
 * <b>Como registrar y spawneaer una nueva entidad personalizada:</b>
 * <ol>
 *   <li>Crea una clase que implemente {@link CustomSpawnRule} y define:
 *       <ul>
 *         <li>{@code getEntityType()} – el {@link net.minecraft.world.entity.EntityType} a spawneaer.</li>
 *         <li>{@code getMinIntervalTicks()} / {@code getMaxIntervalTicks()} – intervalo aleatorio en ticks (20 = 1s).</li>
 *         <li>{@code getSpawnChance(level, player)} – probabilidad 0..1 segun el contexto (ej. distancia del jugador).</li>
 *         <li>{@code findSpawnPosition(level, player)} – posicion valida lejos del jugador, o {@code null}.</li>
 *         <li>(Opcional) {@code configureEntity(mob, level, player)} – ajustar la entidad tras crearla.</li>
 *         <li>(Opcional) {@code getMaxAliveInLevel()} – limite de criaturas vivas simultaneas de este tipo.</li>
 *       </ul>
 *   </li>
 *   <li>Registra la regla en {@link com.chipoodle.devilrpg.eventsubscriber.common.CustomSpawnerTickHandler},
 *       dentro del {@code computeIfAbsent}: <code>s.register(new TuReglaSpawnRule());</code></li>
 * </ol>
 * Ejemplo de regla sencilla:
 * <pre>{@code
 * public class MiMobSpawnRule implements CustomSpawnRule {
 *     public EntityType<? extends Mob> getEntityType() { return ModEntities.MI_MOB.get(); }
 *     public int getMinIntervalTicks() { return 60 * 20; }   // 1 minuto
 *     public int getMaxIntervalTicks() { return 5 * 60 * 20; } // 5 minutos
 *     public float getSpawnChance(ServerLevel level, ServerPlayer player) { return 0.5F; }
 *     public @Nullable BlockPos findSpawnPosition(ServerLevel level, ServerPlayer player) {
 *         // buscar un bloque solido + aire lejos del jugador...
 *         return null;
 *     }
 *     public int getMaxAliveInLevel() { return 10; }
 * }
 * }</pre>
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
            DevilRpg.LOGGER.info("[CustomSpawner] {} en {} - temporizador vencido, intentando spawn de {}",
                    level.dimension().location(), rule.getEntityType().getDescriptionId(),
                    (remainingTicks.getOrDefault(rule, 0) == 0 ? "ahora" : "-"));
            trySpawn(rule, players);
            remainingTicks.put(rule, nextInterval(rule));
            DevilRpg.LOGGER.debug("[CustomSpawner] {} en {} - proximo intento en {} ticks",
                    level.dimension().location(), rule.getEntityType().getDescriptionId(), remainingTicks.get(rule));
        }
    }

    private void trySpawn(CustomSpawnRule rule, List<ServerPlayer> players) {
        // Respetar el limite de criaturas vivas de este tipo en el mundo.
        int alive = countAlive(rule);
        if (alive >= rule.getMaxAliveInLevel()) {
            DevilRpg.LOGGER.info("[CustomSpawner] {} en {} - {} vivos, tope {} alcanzado, NO se spawnea",
                    rule.getEntityType().getDescriptionId(), level.dimension().location(), alive,
                    rule.getMaxAliveInLevel());
            return;
        }
        for (ServerPlayer player : players) {
            float chance = rule.getSpawnChance(level, player);
            if (level.random.nextFloat() >= chance) {
                DevilRpg.LOGGER.debug("[CustomSpawner] {} en {} - probabilidad {} fallo para jugador {}",
                        rule.getEntityType().getDescriptionId(), level.dimension().location(), chance,
                        player.getGameProfile().getName());
                continue;
            }
            BlockPos pos = rule.findSpawnPosition(level, player);
            if (pos != null) {
                spawn(rule, player, pos);
                return; // solo un spawn por vencimiento de temporizador
            }
            DevilRpg.LOGGER.debug("[CustomSpawner] {} en {} - no hay posicion valida para jugador {}",
                    rule.getEntityType().getDescriptionId(), level.dimension().location(),
                    player.getGameProfile().getName());
        }
    }

    /** Cuenta las criaturas vivas del tipo de la regla en este nivel. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int countAlive(CustomSpawnRule rule) {
        AABB wholeWorld = new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        return level.getEntitiesOfClass((Class) rule.getEntityType().getBaseClass(), wholeWorld).size();
    }

    private void spawn(CustomSpawnRule rule, ServerPlayer player, BlockPos pos) {
        EntityType<? extends Mob> type = rule.getEntityType();
        Mob mob = type.create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
        if (mob == null) {
            DevilRpg.LOGGER.error("[CustomSpawner] {} en {} - No se pudo crear la entidad en {}",
                    rule.getEntityType().getDescriptionId(), level.dimension().location(), pos);
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
        DevilRpg.LOGGER.info("[CustomSpawner] {} en {} - SPAWNEADO en {} (jugador objetivo {})",
                rule.getEntityType().getDescriptionId(), level.dimension().location(), pos,
                player.getGameProfile().getName());
    }

    private int nextInterval(CustomSpawnRule rule) {
        int min = Math.max(0, rule.getMinIntervalTicks());
        int max = Math.max(min, rule.getMaxIntervalTicks());
        return min + level.random.nextInt(max - min + 1);
    }
}
