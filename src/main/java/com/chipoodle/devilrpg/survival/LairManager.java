package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.entity.FrostVexEntity;
import com.chipoodle.devilrpg.entity.SculkCultivatorEntity;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.world.LairGenerator;
import com.chipoodle.devilrpg.world.VillageGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

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

    /**
     * Distancia mínima/máxima (bloques) a la que se genera la guarida respecto al objetivo.
     * <p>
     * La mínima NO es arbitraria: la guarida es una plataforma que (con el corral, su orla plana y su talud)
     * llega a ~31 bloques de su centro, y la aldea del objetivo nivela hasta 31 y escalona hasta 41. Con 75
     * siempre queda separada del borde de la aldea.
     */
    private static final int MIN_DISTANCE_FROM_OBJECTIVE = 75;
    private static final int MAX_DISTANCE_FROM_OBJECTIVE = 95;
    /** Radio en el que el jugador "activa" la guarida (hace que spawnee). */
    private static final int ACTIVATION_RADIUS = 64;
    /** Cada cuántos ticks intenta spawnear una tanda mientras el jugador está cerca. */
    private static final int SPAWN_INTERVAL_TICKS = 25 * 20;
    /** Cuántos enemigos spawnea por tanda. */
    private static final int WAVE_SIZE = 3;
    /** Radio alrededor de la guarida donde spawnean sus enemigos. */
    private static final int SPAWN_RADIUS = 14;
    /**
     * Distancia mínima a la que spawnean: el santuario tiene un foso de magma de radio 6, y un enemigo que
     * cayera ahí quedaría atrapado y ardiendo (se perdería la tanda), así que nunca se spawnea dentro.
     */
    private static final int SPAWN_MIN_DISTANCE = 8;
    /** Radio que patrullan los enemigos alrededor del núcleo de la guarida. */
    private static final int PATROL_RADIUS = 24;

    // --- Defensa del núcleo (Iteración 2: el santuario no se asalta impunemente) -------------------
    /** Radio del aura de Oscuridad que rodea al núcleo. */
    private static final double CORE_AURA_RADIUS = 8.0D;
    /** Cada cuántos ticks se refresca el aura. */
    private static final int CORE_AURA_TICKS = 40;
    /** Radio y cadencia de los colmillos con los que el núcleo (ya sin sello) ataca a quien se acerca. */
    private static final double CORE_FANG_RADIUS = 9.0D;
    private static final int CORE_FANG_TICKS = 4 * 20;
    /** Cuántos colmillos por descarga y cuántos ticks de aviso tienen (para poder esquivarlos). */
    private static final int CORE_FANGS = 4;
    private static final int FANG_WARMUP_TICKS = 8;
    /** Cada cuánto se le recuerda al jugador que el núcleo está sellado. */
    private static final int SEAL_HINT_TICKS = 100;
    private static final double SEAL_HINT_RADIUS = 24.0D;
    /**
     * Red de seguridad: si el sello sigue en pie tras este tiempo de guarida <b>activa</b> (con jugador
     * cerca), se abre igual. Evita que un cultivador atascado o inalcanzable deje el objetivo bloqueado. Como
     * un sello roto no puede convivir con un guardián vivo, al dispararse también <b>retira</b> al guardián.
     */
    private static final long SEAL_FORCE_OPEN_TICKS = 10L * 60L * 20L;

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
            // 1b) El sello: la caja que blinda el núcleo se abre cuando MUERE el guardián de la guarida (el
            //     cultivador). Una vez abierta ya no vuelve, aunque luego aparezca otro cultivador.
            int cultivators = countCultivators(level, lair);
            if (cultivators > 0 && !lair.guardianSeen) {
                lair.guardianSeen = true;
                // El reloj de la red de seguridad cuenta desde que HAY guardián, no desde la primera visita:
                // así no se dispara por tiempo acumulado de visitas anteriores sin guardián.
                lair.activeTicks = 0;
            }
            // 2) Spawnear enemigos solo si hay un jugador cerca de la guarida.
            Player near = level.getNearestPlayer(
                    lair.center.getX(), lair.center.getY(), lair.center.getZ(),
                    ACTIVATION_RADIUS, false);
            if (near == null) {
                continue;
            }
            lair.activeTicks++;
            // El sello cae SOLO si el guardián murió de verdad (nos avisa el propio cultivador en die()), o
            // por la red de seguridad por tiempo. OJO: antes se deducía de "no hay ningún cultivador cerca", y
            // eso también es cierto cuando el guardián simplemente no está (despawn, o aún no ha aparecido),
            // así que el sello se caía solo y el núcleo quedaba indefenso sin haber matado a nadie.
            if (!lair.sealBroken) {
                if (lair.guardianDead) {
                    openSeal(level, lair);
                } else if (lair.guardianSeen && lair.activeTicks > SEAL_FORCE_OPEN_TICKS) {
                    // Red de seguridad: hubo guardián pero no hay forma de matarlo (atascado, inalcanzable o
                    // desaparecido). Se retira si sigue vivo y se abre el sello.
                    if (cultivators > 0) {
                        dismissGuardian(level, lair);
                    }
                    openSeal(level, lair);
                }
            }
            // 2b) El núcleo sigue en pie: se defiende de quien se acerque.
            defendCore(level, lair);
            if (--lair.spawnTimer > 0) {
                continue;
            }
            lair.spawnTimer = SPAWN_INTERVAL_TICKS;
            spawnWave(level, lair, near);
        }
    }

    /**
     * Se invoca cuando el <b>guardián</b> de una guarida muere de verdad. Lo llama el propio
     * {@code SculkCultivatorEntity} desde {@code die()}, pasando el núcleo de su guarida (que guarda como
     * "hogar"). Es la única señal fiable de "hay que romper el sello": contar cultivadores no sirve, porque
     * su ausencia también significa "todavía no ha aparecido" o "se ha ido".
     */
    public static void onGuardianKilled(ServerLevel level, BlockPos lairCore) {
        List<Lair> list = LAIRS.get(level);
        if (list == null) {
            return;
        }
        for (Lair lair : list) {
            if (!lair.cleared && lair.corePos.equals(lairCore)) {
                lair.guardianDead = true;
                DevilRpg.LOGGER.info("[Lair] Guardián de la guarida {} ha muerto: el sello va a caer",
                        lair.objectiveIndex);
                return;
            }
        }
    }

    /**
     * Retira al guardián vivo de una guarida (red de seguridad del sello). Se usa solo cuando el sello se
     * abre por tiempo: así el estado queda coherente — <b>nunca hay un cultivador vivo con el sello roto</b>.
     * Es una salida de emergencia para que un guardián atascado o inalcanzable no bloquee el objetivo.
     */
    private static void dismissGuardian(ServerLevel level, Lair lair) {
        int left = 0;
        for (SculkCultivatorEntity cult : level.getEntitiesOfClass(SculkCultivatorEntity.class,
                new AABB(lair.center).inflate(ACTIVATION_RADIUS))) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, cult.getX(), cult.getY() + 1.0D, cult.getZ(),
                    25, 0.4D, 0.6D, 0.4D, 0.02D);
            cult.discard();
            left++;
        }
        if (left > 0) {
            DevilRpg.LOGGER.info("[Lair] Guardián de la guarida {} retirado por tiempo ({} cultivadores)",
                    lair.objectiveIndex, left);
            for (Player p : playersNear(level, lair.corePos, 64.0D)) {
                p.displayClientMessage(
                        Component.literal("El guardián abandona el santuario: el sello se deshace."), false);
            }
        }
    }

    /**
     * Abre el <b>sello</b> del núcleo: retira la caja de sellos y lo anuncia (partículas, sonido y aviso a
     * los jugadores que estén cerca). A partir de ahí el núcleo queda expuesto y se puede destruir, y la
     * guarida <b>ya no vuelve a criar guardianes</b>: el estado es siempre "sello roto = sin cultivador".
     */
    private static void openSeal(ServerLevel level, Lair lair) {
        lair.sealBroken = true;
        lair.guardianDead = true; // el sello roto implica que ya no hay guardián
        Block seal = ModBlocks.SCULK_SEAL_BLOCK.get();
        int removed = 0;
        int r = LairGenerator.SEAL_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = lair.corePos.offset(x, y, z);
                    if (level.getBlockState(p).is(seal)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                        removed++;
                    }
                }
            }
        }
        DevilRpg.LOGGER.info("[Lair] Sello de la guarida {} abierto ({} bloques retirados)", lair.objectiveIndex, removed);
        restoreSanctumFloor(level, lair.corePos);
        double cx = lair.corePos.getX() + 0.5D;
        double cy = lair.corePos.getY() + 1.0D;
        double cz = lair.corePos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 60, 1.2D, 1.2D, 1.2D, 0.02D);
        level.playSound(null, lair.corePos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 2.0F, 0.6F);
        for (Player p : playersNear(level, lair.corePos, 64.0D)) {
            p.displayClientMessage(
                    Component.literal("El sello del núcleo se ha roto: el santuario queda expuesto."), false);
        }
    }

    /**
     * El núcleo se defiende mientras siga en pie: quien se acerca al santuario sufre <b>Oscuridad</b> y, una
     * vez roto el sello, además le estallan <b>colmillos de invocador</b> alrededor. Así destruirlo es una
     * pelea bajo presión y no un trámite de picar un bloque.
     */
    private static void defendCore(ServerLevel level, Lair lair) {
        long time = level.getGameTime();
        if (time % CORE_AURA_TICKS == 0) {
            for (Player p : playersNear(level, lair.corePos, CORE_AURA_RADIUS)) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, CORE_AURA_TICKS + 30, 0, false, false));
            }
        }
        if (lair.sealBroken && time % CORE_FANG_TICKS == 0) {
            for (Player p : playersNear(level, lair.corePos, CORE_FANG_RADIUS)) {
                spawnFangsAround(level, p);
            }
        }
        if (!lair.sealBroken && time % SEAL_HINT_TICKS == 0) {
            for (Player p : playersNear(level, lair.corePos, SEAL_HINT_RADIUS)) {
                p.displayClientMessage(Component
                        .literal("El núcleo está sellado: mata al cultivador del sculk para romper el sello.")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
    }

    /**
     * Restaura el suelo del santuario bajo el núcleo (la capa de sculk que el sello había tapado). Sin esto,
     * al abrirse el sello quedaría un hueco de aire de 1 de fondo y el núcleo quedaría flotando.
     */
    private static void restoreSanctumFloor(ServerLevel level, BlockPos corePos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos floor = corePos.offset(x, -1, z);
                if (!level.getBlockState(floor).isSolid()) {
                    level.setBlock(floor, Blocks.SCULK.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Jugadores vivos (ni creativos ni espectadores) dentro de {@code radius} de {@code pos}. */
    private static List<Player> playersNear(ServerLevel level, BlockPos pos, double radius) {
        return level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(radius),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
    }

    /** Rodea al jugador de colmillos de invocador (telegrafiados: tienen aviso, así que se pueden esquivar). */
    private static void spawnFangsAround(ServerLevel level, Player player) {
        int placed = 0;
        for (int i = 0; i < CORE_FANGS; i++) {
            double angle = (i / (double) CORE_FANGS) * Math.PI * 2.0 + level.random.nextDouble() * 0.5D;
            int fx = (int) Math.floor(player.getX() + Math.cos(angle) * 1.4D);
            int fz = (int) Math.floor(player.getZ() + Math.sin(angle) * 1.4D);
            int fy = (int) Math.floor(player.getY());
            BlockPos ground = new BlockPos(fx, fy, fz);
            // Solo sobre suelo firme y con el hueco libre: si no, el colmillo saldría enterrado.
            if (!level.getBlockState(ground.below()).isSolid()) {
                continue;
            }
            if (!level.getBlockState(ground).isAir()) {
                continue;
            }
            level.addFreshEntity(new EvokerFangs(level, fx + 0.5D, fy, fz + 0.5D,
                    (float) Math.toDegrees(angle), FANG_WARMUP_TICKS, null));
            placed++;
        }
        if (placed > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /** Spawnea una tanda de enemigos alrededor de la guarida. */
    private static void spawnWave(ServerLevel level, Lair lair, Player player) {
        Random random = new Random();
        // La guarida "más lejana" del ancla genera más enemigos y incluye vexes helados.
        int count = WAVE_SIZE + Math.min(lair.objectiveIndex, 6);
        // Mantiene UN cultivador del sculk (el que cría la granja y expande la infección) MIENTRAS EL SELLO
        // SIGA EN PIE. Con el sello roto ya no se crían guardianes: su ritual está roto y el núcleo queda
        // expuesto, así que no puede aparecer un cultivador vivo junto a un sello abierto.
        if (!lair.sealBroken && countCultivators(level, lair) == 0) {
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
        // Siempre fuera del foso del santuario (ver SPAWN_MIN_DISTANCE).
        int dist = SPAWN_MIN_DISTANCE + random.nextInt(Math.max(1, SPAWN_RADIUS - 6));
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
            } else if (mob instanceof SculkCultivatorEntity cultivatorMob) {
                // El cultivador trabaja la infección dentro de ese mismo radio.
                cultivatorMob.setHome(lair.corePos, PATROL_RADIUS);
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
        /** ¿Ha llegado a vivir un cultivador (guardián) en esta guarida? */
        boolean guardianSeen;
        /** ¿Ha MUERTO el guardián? Solo esto (o la red de seguridad) rompe el sello. */
        boolean guardianDead;
        /** ¿Ya se abrió el sello del núcleo? Una vez abierto no se vuelve a cerrar. */
        boolean sealBroken;
        /** Ticks que la guarida ha estado activa (con jugador cerca), para la red de seguridad del sello. */
        long activeTicks;

        Lair(int objectiveIndex, BlockPos center, BlockPos corePos) {
            this.objectiveIndex = objectiveIndex;
            this.center = center;
            this.corePos = corePos;
        }

        /** Marca la guarida como limpiada y, si hay jugador, le da la recompensa. */
        void clear(ServerLevel level, Player player) {
            this.cleared = true;
            this.sealBroken = true;
            // Red de seguridad: si quedara algún sello en pie, se retira (el núcleo ya no está).
            removeLeftoverSeal(level);
            DevilRpg.LOGGER.info("[Lair] Guarida {} limpiada en {}", objectiveIndex, corePos);
            if (player != null) {
                player.displayClientMessage(Component.literal("¡Has destruido la guarida! El lugar queda en silencio."), false);
                player.giveExperiencePoints(40 + objectiveIndex * 15);
                player.addItem(new ItemStack(Items.BONE, 8));
                player.addItem(new ItemStack(Items.SOUL_SAND, 6));
                player.addItem(new ItemStack(Items.EMERALD, 3));
            }
        }

        /** Quita cualquier bloque de sello que siguiera rodeando el núcleo. */
        private void removeLeftoverSeal(ServerLevel level) {
            Block seal = ModBlocks.SCULK_SEAL_BLOCK.get();
            int r = LairGenerator.SEAL_RADIUS;
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos p = corePos.offset(x, y, z);
                        if (level.getBlockState(p).is(seal)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
            restoreSanctumFloor(level, corePos);
        }
    }
}
