package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.entity.FrostVexEntity;
import com.chipoodle.devilrpg.entity.SculkCultivatorEntity;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.world.LairGenerator;
import com.chipoodle.devilrpg.world.VillageGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Gestor de las <b>guaridas</b> (Iteración 2): focos de enemigos que <b>cambian el terreno</b> a su
 * alrededor y que el jugador puede <b>asaltar</b>. Mientras el núcleo de una guarida siga en pie, la
 * guarida spawnea enemigos cuando el jugador está cerca; al destruir el núcleo, la guarida queda
 * <b>limpiada</b> (deja de spawnear y da recompensa).
 * <p>
 * La posición se genera de forma <b>determinista</b> cerca del objetivo de progresión (desplazada para que
 * el jugador la encuentre al explorar), así server y cliente coinciden sin sincronizar nada.
 */
public final class LairManager {

    /** Distancia mínima/máxima (bloques) a la que se genera la guarida respecto al objetivo. */
    private static final int MIN_DISTANCE_FROM_OBJECTIVE = 55;
    private static final int MAX_DISTANCE_FROM_OBJECTIVE = 95;
    /** Radio en el que el jugador "activa" la guarida (hace que spawnee). */
    private static final int ACTIVATION_RADIUS = 64;
    /** Cada cuántos ticks intenta spawnear una tanda mientras el jugador está cerca. */
    private static final int SPAWN_INTERVAL_TICKS = 25 * 20;
    /** Cuántos enemigos spawnea por tanda. */
    private static final int WAVE_SIZE = 3;
    /** Radio alrededor de la guarida donde spawnean sus enemigos. */
    private static final int SPAWN_RADIUS = 14;
    /** Radio que patrullan los enemigos alrededor del núcleo de la guarida. */
    private static final int PATROL_RADIUS = 24;

    private static final Map<ServerLevel, List<Lair>> LAIRS = new HashMap<>();
    private static final Set<String> GENERATED = new HashSet<>();

    private LairManager() {
    }

    /** Pre-genera la guarida asociada al objetivo {@code objectiveIndex}, si aún no existe. */
    public static void preGenerate(ServerLevel level, int objectiveIndex, BlockPos target) {
        String key = level.dimension().location() + ":" + objectiveIndex;
        if (GENERATED.contains(key)) {
            return;
        }
        // Posición determinista: ángulo/distancia derivados del índice del objetivo.
        Random rnd = new Random(0x1A18L + objectiveIndex);
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        int distance = MIN_DISTANCE_FROM_OBJECTIVE
                + rnd.nextInt(Math.max(1, MAX_DISTANCE_FROM_OBJECTIVE - MIN_DISTANCE_FROM_OBJECTIVE));
        int x = (int) Math.round(target.getX() + Math.cos(angle) * distance);
        int z = (int) Math.round(target.getZ() + Math.sin(angle) * distance);
        BlockPos land = LairGenerator.findLand(level, new BlockPos(x, target.getY(), z));
        BlockPos corePos = LairGenerator.generate(level, land);
        if (corePos == null) {
            return;
        }
        LAIRS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Lair(objectiveIndex, land, corePos));
        GENERATED.add(key);
        DevilRpg.LOGGER.info("[Lair] Guarida {} pre-generada en {} (nucleo en {})", objectiveIndex, land, corePos);
    }

    /** Se llama en el tick del servidor: verifica los núcleos y spawnea enemigos de las guaridas activas. */
    public static void tick(ServerLevel level) {
        List<Lair> list = LAIRS.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Lair lair : list) {
            // 1) ¿Sigue en pie el núcleo? Si no, la guarida quedó limpiada.
            if (!lair.cleared && !level.getBlockState(lair.corePos).is(ModBlocks.LAIR_CORE_BLOCK.get())) {
                lair.clear(level, null);
            }
            if (lair.cleared) {
                continue;
            }
            // 2) Spawnear enemigos solo si hay un jugador cerca de la guarida.
            Player near = level.getNearestPlayer(
                    lair.center.getX(), lair.center.getY(), lair.center.getZ(),
                    ACTIVATION_RADIUS, false);
            if (near == null) {
                continue;
            }
            if (--lair.spawnTimer > 0) {
                continue;
            }
            lair.spawnTimer = SPAWN_INTERVAL_TICKS;
            spawnWave(level, lair, near);
        }
    }

    /** Spawnea una tanda de enemigos alrededor de la guarida. */
    private static void spawnWave(ServerLevel level, Lair lair, Player player) {
        Random random = new Random();
        // La guarida "más lejana" del ancla genera más enemigos y incluye vexes helados.
        int count = WAVE_SIZE + Math.min(lair.objectiveIndex, 6);
        // Además, mantiene UN cultivador del sculk (el que cría la granja y expande la infección).
        if (countCultivators(level, lair) == 0) {
            spawnOne(level, lair, player, random, true);
            count--;
        }
        for (int i = 0; i < count; i++) {
            spawnOne(level, lair, player, random, false);
        }
    }

    /** Spawnea un enemigo de la guarida (cultivador, vex helado o zombie agresivo). */
    private static void spawnOne(ServerLevel level, Lair lair, Player player, Random random, boolean cultivator) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        int dist = 4 + random.nextInt(SPAWN_RADIUS);
        int x = (int) Math.round(lair.center.getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(lair.center.getZ() + Math.sin(angle) * dist);
        int y = VillageGenerator.spawnY(level, x, z);
        boolean frost = !cultivator && lair.objectiveIndex >= 2 && random.nextInt(4) == 0;
        Mob mob;
        if (cultivator) {
            mob = ModEntities.SCULK_CULTIVATOR.get().create(level, null, new BlockPos(x, y, z), MobSpawnType.MOB_SUMMONED, true, true);
        } else if (frost) {
            mob = ModEntities.FROST_VEX.get().create(level, null, new BlockPos(x, y, z), MobSpawnType.MOB_SUMMONED, true, true);
        } else {
            mob = ModEntities.AGGRESSIVE_ZOMBIE.get().create(level, null, new BlockPos(x, y, z), MobSpawnType.MOB_SUMMONED, true, true);
        }
        if (mob != null) {
            mob.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            if (mob instanceof AggressiveZombieEntity zombie) {
                // Patrullan un radio alrededor del núcleo de la guarida (no se quedan pegados ni se pierden).
                zombie.setHome(lair.corePos, PATROL_RADIUS);
            } else if (mob instanceof FrostVexEntity vex) {
                vex.setTarget(player);
            }
            level.addFreshEntity(mob);
        }
    }

    /** Cuenta los cultivadores del sculk vivos cerca de la guarida. */
    private static int countCultivators(ServerLevel level, Lair lair) {
        int r = ACTIVATION_RADIUS;
        return level.getEntitiesOfClass(SculkCultivatorEntity.class,
                new net.minecraft.world.phys.AABB(lair.center).inflate(r)).size();
    }

    /** Se invoca cuando el núcleo de una guarida es destruido: la limpia y da recompensa al jugador. */
    public static void onCoreBroken(ServerLevel level, BlockPos corePos) {
        List<Lair> list = LAIRS.get(level);
        if (list == null) {
            return;
        }
        for (Lair lair : list) {
            if (!lair.cleared && lair.corePos.equals(corePos)) {
                Player player = level.getNearestPlayer(corePos.getX(), corePos.getY(), corePos.getZ(), 48.0D, false);
                lair.clear(level, player);
                return;
            }
        }
    }

    /** Datos de una guarida: centro, núcleo y estado. */
    private static final class Lair {
        final int objectiveIndex;
        final BlockPos center;
        final BlockPos corePos;
        boolean cleared;
        int spawnTimer = SPAWN_INTERVAL_TICKS / 2; // primera tanda algo antes

        Lair(int objectiveIndex, BlockPos center, BlockPos corePos) {
            this.objectiveIndex = objectiveIndex;
            this.center = center;
            this.corePos = corePos;
        }

        /** Marca la guarida como limpiada y, si hay jugador, le da la recompensa. */
        void clear(ServerLevel level, Player player) {
            this.cleared = true;
            DevilRpg.LOGGER.info("[Lair] Guarida {} limpiada en {}", objectiveIndex, corePos);
            if (player != null) {
                player.displayClientMessage(Component.literal("¡Has destruido la guarida! El lugar queda en silencio."), false);
                player.giveExperiencePoints(40 + objectiveIndex * 15);
                player.addItem(new ItemStack(Items.BONE, 8));
                player.addItem(new ItemStack(Items.SOUL_SAND, 6));
                player.addItem(new ItemStack(Items.EMERALD, 3));
            }
        }
    }
}
