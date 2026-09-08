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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Gestor del asedio a la primera aldea. Cuando el jugador llega a la aldea (objetivo), se genera y se
 * lanza una ola de monstruos. El jugador debe defender:
 * <ul>
 *   <li>Si limpia la ola a tiempo -> la aldea se salva: recibe recompensa (materiales + exp + una receta
 *       en libro) y el objetivo avanza a la siguiente aldea.</li>
 *   <li>Si no logra limpiarla a tiempo -> la aldea cae: avanza el objetivo sin recompensa.</li>
 * </ul>
 */
public final class VillageManager {

    private static final int DEFAULT_WAVE = 6;
    private static final long TIMEOUT_TICKS = 3 * 60 * 20; // 3 minutos para defender

    private static final Map<ServerLevel, List<VillageDefense>> DEFENSES = new HashMap<>();

    private VillageManager() {
    }

    /** Lanza el asedio a la aldea en {@code center} (se genera y spawnea la ola). */
    public static void start(ServerLevel level, ServerPlayer player, int objectiveIndex, BlockPos center) {
        // No re-lanzar si ya hay un asedio activo para este jugador/objetivo.
        for (VillageDefense d : DEFENSES.getOrDefault(level, List.of())) {
            if (d.playerUUID.equals(player.getUUID()) && d.objectiveIndex == objectiveIndex) {
                return;
            }
        }
        VillageGenerator.generate(level, center);

        Random random = new Random();
        List<UUID> wave = new ArrayList<>();
        for (int i = 0; i < DEFAULT_WAVE; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            BlockPos pos = center.offset((int) Math.round(Math.cos(angle) * 8), 0, (int) Math.round(Math.sin(angle) * 8));
            AggressiveZombieEntity zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
            if (zombie != null) {
                zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
                level.addFreshEntity(zombie);
                wave.add(zombie.getUUID());
            }
        }
        DEFENSES.computeIfAbsent(level, l -> new ArrayList<>())
                .add(new VillageDefense(objectiveIndex, player.getUUID(), center, wave, 0L));

        player.displayClientMessage(Component.literal("¡Defiende la aldea de los monstruos!"), false);
        DevilRpg.LOGGER.info("[Village] Asedio iniciado en {} para {}", center, player.getGameProfile().getName());
    }

    /** Se llama en el tick del servidor: resuelve los asedios activos. */
    public static void tick(ServerLevel level) {
        List<VillageDefense> list = DEFENSES.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            VillageDefense d = list.get(i);
            d.tickTicks++;
            boolean waveCleared = isWaveCleared(level, d.wave);
            if (waveCleared || d.tickTicks > TIMEOUT_TICKS) {
                boolean saved = waveCleared; // salvada si limpió la ola
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(d.playerUUID);
                if (player == null) {
                    list.remove(i);
                    continue;
                }
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
                list.remove(i);
            }
        }
    }

    private static boolean isWaveCleared(ServerLevel level, List<UUID> wave) {
        for (UUID uuid : wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive()) {
                return false; // al menos uno sigue vivo
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
        final List<UUID> wave;
        long tickTicks;

        VillageDefense(int objectiveIndex, UUID playerUUID, BlockPos center, List<UUID> wave, long tickTicks) {
            this.objectiveIndex = objectiveIndex;
            this.playerUUID = playerUUID;
            this.center = center;
            this.wave = wave;
            this.tickTicks = tickTicks;
        }
    }
}
