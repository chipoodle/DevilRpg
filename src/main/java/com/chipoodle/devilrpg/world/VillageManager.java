package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Gestor del asedio a la primera aldea. La aldea se pre-genera antes de que el jugador llegue; al llegar,
 * tras un pequeño margen para explorar, se lanza una ola de monstruos desde fuera de la valla. Si el
 * jugador la limpia a tiempo -> la aldea se salva (recompensa + avanza el objetivo); si no -> cae (avanza
 * sin recompensa).
 */
public final class VillageManager {

    /** Distancia a la que se pre-genera la aldea antes de llegar el jugador. */
    public static final int PRE_GENERATE_RADIUS = 140;
    /** Radio de llegada al objetivo (se considera "en la aldea"). */
    public static final int ARRIVE_RADIUS = 24;
    /** Ticks de margen para explorar la aldea antes del asedio (40 s). */
    private static final int GRACE_TICKS = 40 * 20;
    /** Ticks extra para limpiar la ola tras el asedio (2 min). */
    private static final int SIEGE_TIMEOUT_TICKS = 2 * 60 * 20;
    /** Número base de monstruos agresivos en la ola (escala con el índice del objetivo). */
    private static final int DEFAULT_WAVE = 8;
    /** Incremento máximo de la ola por alejarse (límite: no crece infinitamente). */
    private static final int MAX_WAVE_EXTRA = 20;
    /** Zona mínima/máxima (bloques) a la que spawnea la ola, FUERA de la valla (radio 29). */
    private static final int WAVE_SPAWN_MIN = 32;
    private static final int WAVE_SPAWN_MAX = 40;

    private static final Map<ServerLevel, List<VillageDefense>> DEFENSES = new HashMap<>();
    private static final Set<String> GENERATED = new HashSet<>();

    private VillageManager() {
    }

    /** Pre-genera la aldea (cabañas + aldeanos + valla) en una zona de tierra firme, si aún no existe. */
    public static void preGenerate(ServerLevel level, int objectiveIndex, BlockPos target) {
        String key = level.dimension().location() + ":" + objectiveIndex;
        if (GENERATED.contains(key)) {
            return;
        }
        BlockPos land = VillageGenerator.findLand(level, target);
        VillageGenerator.generate(level, land);
        GENERATED.add(key);
        DevilRpg.LOGGER.info("[Village] Aldea {} pre-generada en {}", objectiveIndex, land);
    }

    /** Inicia el asedio al llegar el jugador a la aldea (con un margen antes de la ola). */
    public static void start(ServerLevel level, ServerPlayer player, int objectiveIndex, BlockPos target) {
        // No re-lanzar si ya hay un asedio activo para este jugador/objetivo.
        for (VillageDefense d : DEFENSES.getOrDefault(level, List.of())) {
            if (d.playerUUID.equals(player.getUUID()) && d.objectiveIndex == objectiveIndex) {
                return;
            }
        }
        if (!GENERATED.contains(level.dimension().location() + ":" + objectiveIndex)) {
            preGenerate(level, objectiveIndex, target);
        }
        BlockPos land = VillageGenerator.findLand(level, target);
        DEFENSES.computeIfAbsent(level, l -> new ArrayList<>())
                .add(new VillageDefense(objectiveIndex, player.getUUID(), land));
        player.displayClientMessage(Component.literal("Llegaste a la aldea... los monstruos se acercan."), false);
    }

    /** Se llama en el tick del servidor: gestiona el margen, la ola y la resolución del asedio. */
    public static void tick(ServerLevel level) {
        List<VillageDefense> list = DEFENSES.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            VillageDefense d = list.get(i);
            d.tickTicks++;

            // Tras el margen de exploración, lanza la ola desde FUERA de la valla.
            if (!d.waveSpawned && d.tickTicks >= GRACE_TICKS) {
                spawnWave(level, d);
                d.waveSpawned = true;
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.playerUUID);
                if (p != null) {
                    p.displayClientMessage(Component.literal("¡Defiende la aldea de los monstruos!"), false);
                }
            }

            if (d.waveSpawned) {
                boolean waveCleared = isWaveCleared(level, d.wave);
                if (waveCleared || d.tickTicks > GRACE_TICKS + SIEGE_TIMEOUT_TICKS) {
                    boolean saved = waveCleared;
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(d.playerUUID);
                    if (player != null) {
                        if (saved) {
                            grantReward(player);
                            player.displayClientMessage(Component.literal("¡Has salvado la aldea! El objetivo avanza."), false);
                        } else {
                            player.displayClientMessage(Component.literal("La aldea cayó... El objetivo avanza."), false);
                        }
                        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
                        if (aux != null) {
                            aux.setObjectiveIndex(d.objectiveIndex + 1, player);
                        }
                    }
                    list.remove(i);
                }
            }
        }
    }

    private static void spawnWave(ServerLevel level, VillageDefense d) {
        Random random = new Random();
        // La ola crece al alejarse del ancla, pero con un LÍMITE: no se extiende infinitamente.
        int count = DEFAULT_WAVE + Math.min(d.objectiveIndex * 2, MAX_WAVE_EXTRA);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            // FUERA de la valla (radio 29): spawnea entre 32 y 40 bloques del centro.
            int dist = WAVE_SPAWN_MIN + random.nextInt(WAVE_SPAWN_MAX - WAVE_SPAWN_MIN);
            int x = (int) Math.round(d.center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(d.center.getZ() + Math.sin(angle) * dist);
            int y = VillageGenerator.findLand(level, new BlockPos(x, 0, z)).getY();
            AggressiveZombieEntity zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(level, null, new BlockPos(x, y, z), MobSpawnType.MOB_SUMMONED, true, true);
            if (zombie != null) {
                zombie.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
                level.addFreshEntity(zombie);
                d.wave.add(zombie.getUUID());
            }
        }
    }

    private static boolean isWaveCleared(ServerLevel level, List<UUID> wave) {
        for (UUID uuid : wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive()) {
                return false;
            }
        }
        return true;
    }

    private static void grantReward(ServerPlayer player) {
        player.addItem(new ItemStack(Items.IRON_INGOT, 8));
        player.addItem(new ItemStack(Items.LEATHER, 6));
        player.addItem(new ItemStack(Items.WRITTEN_BOOK)); // receta (por ahora un libro genérico)
        player.giveExperiencePoints(50);
    }

    /** Datos de un asedio en curso. */
    private static final class VillageDefense {
        final int objectiveIndex;
        final UUID playerUUID;
        final BlockPos center;
        final List<UUID> wave = new ArrayList<>();
        long tickTicks;
        boolean waveSpawned;

        VillageDefense(int objectiveIndex, UUID playerUUID, BlockPos center) {
            this.objectiveIndex = objectiveIndex;
            this.playerUUID = playerUUID;
            this.center = center;
        }
    }
}
