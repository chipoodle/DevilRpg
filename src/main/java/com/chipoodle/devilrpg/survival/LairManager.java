package com.chipoodle.devilrpg.survival;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
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
import net.minecraft.world.phys.Vec3;

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
    /**
     * Tope de enemigos vivos por guarida (sin contar al guardián). Las tandas siguen llegando hasta llenar
     * este cupo, y en cuanto matas a algunos vuelven a aparecer hasta rellenarlo.
     */
    private static final int MAX_LAIR_MOBS = 30;
    /**
     * Si matas al guardián y <b>no</b> rompes el núcleo, la guarida cría un <b>guardián de relevo</b> tras
     * este tiempo (de guarida activa, o sea con jugador cerca) y <b>vuelve a sellar el núcleo</b>.
     */
    private static final int GUARDIAN_RESPAWN_TICKS = 3 * 60 * 20;
    /**
     * Radio de seguridad para volver a sellar: si hay un jugador más cerca del núcleo que esto, la
     * consagración se pospone (avisando). La caja de sellos ocupa 3×3×3 alrededor del núcleo, así que 3
     * bloques garantiza que nadie quede dentro de ella.
     */
    private static final double RESEAL_CLEAR_RADIUS = 3.0D;
    /**
     * Radio para reconocer a qué guarida pertenece un guardián que murió <b>sin recordar su hogar</b> (p. ej.
     * uno guardado por una versión anterior, de antes de que el hogar se guardara en NBT, o creado con huevo
     * de spawn): se le asigna la guarida <b>más cercana</b> dentro de este radio. Las guaridas están a ≥200
     * bloques entre sí, así que 64 no es ambiguo.
     */
    private static final int GUARDIAN_MATCH_RADIUS = 64;
    /**
     * Radio en el que se busca a los guardianes <b>vivos</b> de una guarida para decidir si ya tiene uno y
     * para retirar duplicados. Es más generoso que {@link #GUARDIAN_MATCH_RADIUS} porque el guardián puede
     * perseguir a un jugador lejos del núcleo: si se contaba solo a 64 y se había alejado, la guarida creía
     * que no tenía guardián y <b>creaba otro</b> (dos guardianes a la vez). Como las guaridas están a ≥200
     * bloques entre sí, 96 sigue sin ser ambiguo.
     */
    private static final int GUARDIAN_COUNT_RADIUS = 96;
    /**
     * Radio (respecto al <b>objetivo</b>) en el que se asegura que la guarida de ese objetivo exista y se
     * gestione. Igual que el radio con el que se pre-genera la aldea: la guarida está a 75–95 del objetivo,
     * así que 140 cubre llegar a ella.
     */
    private static final int NEARBY_OBJECTIVE_RADIUS = 140;
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

    private static final Map<ServerLevel, List<Lair>> LAIRS = new HashMap<>();
    private static final Set<String> GENERATED = new HashSet<>();

    private LairManager() {
    }

    /**
     * Asegura que las guaridas de los objetivos <b>cercanos al jugador</b> estén creadas y se gestionen, no
     * solo la del objetivo actual.
     * <p>
     * Es imprescindible: {@code preGenerate} solo se llamaba con el índice del objetivo <b>actual</b>, así que
     * al volver a una guarida de un objetivo ya superado (a 540 m del actual, por ejemplo) esa guarida
     * <b>nunca se registraba</b> en esta sesión: no spawneaba nada, no aparecía su guardián y su estado
     * guardado ni se comprobaba. Daba igual esperar. Con esto, cualquier guarida a la que te acerques queda
     * viva y gestionada, sin importar en qué punto de la progresión estés.
     */
    public static void preGenerateNearby(ServerLevel level, Vec3 anchor, int currentIndex, BlockPos playerPos) {
        for (int i = 0; i <= currentIndex; i++) {
            BlockPos target = ObjectiveTargets.targetOf(anchor, i);
            if (ObjectiveTargets.horizontalDistSqr(playerPos, target)
                    <= (double) NEARBY_OBJECTIVE_RADIUS * NEARBY_OBJECTIVE_RADIUS) {
                preGenerate(level, i, target);
            }
        }
    }

    /** Pre-genera la guarida asociada al objetivo {@code objectiveIndex}, si aún no existe. */
    public static void preGenerate(ServerLevel level, int objectiveIndex, BlockPos target) {        String key = level.dimension().location() + ":" + objectiveIndex;
        if (GENERATED.contains(key)) {
            return;
        }
        LairSavedData saved = LairSavedData.get(level);
        // Posición determinista: ángulo/distancia derivados del índice del objetivo.
        Random rnd = new Random(0x1A18L + objectiveIndex);
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        int distance = MIN_DISTANCE_FROM_OBJECTIVE
                + rnd.nextInt(Math.max(1, MAX_DISTANCE_FROM_OBJECTIVE - MIN_DISTANCE_FROM_OBJECTIVE));
        int x = (int) Math.round(target.getX() + Math.cos(angle) * distance);
        int z = (int) Math.round(target.getZ() + Math.sin(angle) * distance);
        // Se genera EN LA POSICIÓN DETERMINISTA, sin moverla a la tierra más cercana (igual que la aldea, que
        // se genera en el objetivo). Antes se llamaba a findLand, que desplazaba el centro hasta 24 bloques
        // buscando tierra seca: en la costa el centro acababa en tierra con la huella casi toda en el mar, y
        // la guarida se construía nivelada al fondo marino, SUMERGIDA. Ahora, si la zona cae sobre agua, se
        // levanta una plataforma al nivel del agua (mismas reglas que la aldea).
        BlockPos spot = new BlockPos(x, target.getY(), z);
        // Si ya la limpiaste, no se genera NADA: el núcleo no vuelve a aparecer. Sin esto, al reiniciar la
        // partida la guarida renacía entera (terreno, núcleo y guardián) y la recompensa se podía repetir.
        // PERO la marca se COMPRUEBA contra el mundo: si el núcleo sigue ahí, la marca era falsa (p. ej. por
        // una posición que varió entre sesiones) y se deshace, porque si no la guarida queda muerta para
        // siempre: sin oleadas, sin guardián y con la caja de sellos en pie.
        if (saved.isCleared(objectiveIndex)) {
            BlockPos marked = saved.getClearedCore(objectiveIndex);
            if (marked == null) {
                // Marca guardada por una versión anterior (sin posición): se estima dónde debería estar el
                // núcleo —la superficie en el punto determinista de la guarida— para poder comprobarla.
                marked = new BlockPos(spot.getX(),
                        VillageGenerator.spawnY(level, spot.getX(), spot.getZ()), spot.getZ());
            }
            if (findCoreNear(level, marked) == null) {
                GENERATED.add(key);
                DevilRpg.LOGGER.info("[Lair] Guarida {} ya estaba limpiada: no se regenera", objectiveIndex);
                return;
            }
            saved.unmarkCleared(objectiveIndex);
            DevilRpg.LOGGER.warn("[Lair] La guarida {} estaba marcada como limpiada pero su núcleo sigue "
                    + "cerca de {}: se deshace la marca y se restaura", objectiveIndex, marked);
        }
        // Si el sello ya estaba roto, la guarida vuelve SIN caja de sellos (el núcleo queda expuesto).
        boolean sealBroken = saved.isSealBroken(objectiveIndex);
        BlockPos corePos = LairGenerator.generate(level, spot, !sealBroken);
        if (corePos == null) {
            return;
        }
        Lair lair = new Lair(objectiveIndex, spot, corePos);
        if (sealBroken) {
            lair.sealBroken = true;
            lair.guardianDead = true; // no hay guardián: el relevo saldrá a los GUARDIAN_RESPAWN_TICKS
        }
        LAIRS.computeIfAbsent(level, l -> new ArrayList<>()).add(lair);
        GENERATED.add(key);
        DevilRpg.LOGGER.info("[Lair] Guarida {} pre-generada en {} (nucleo en {}, sello {})",
                objectiveIndex, spot, corePos, sealBroken ? "ROTO" : "en pie");
    }

    /** Se llama en el tick del servidor: verifica los núcleos y spawnea enemigos de las guaridas activas. */
    public static void tick(ServerLevel level) {
        List<Lair> list = LAIRS.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Lair lair : list) {
            // 1) ¿Sigue en pie el núcleo? Se busca con TOLERANCIA (±3 en vertical, ±1 en horizontal) porque la
            //    altura calculada puede variar un bloque entre sesiones y, comparando solo la posición exacta,
            //    la guarida se marcaba como "limpiada" por error: se quedaba sin oleadas, sin guardián y con la
            //    caja de sellos en pie (el borrado de la caja apuntaba a la posición equivocada).
            if (!lair.cleared && syncCorePos(level, lair) == null) {
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
            // 2a) Cuántos guardianes vivos tiene la guarida AHORA mismo. Esta llamada además ADOPTA a los
            //     cultivadores sin hogar que anden por aquí (los dejó una sesión anterior, o salieron de un
            //     huevo de spawn) y RETIRA los duplicados: una guarida tiene UN guardián, no dos.
            int guardians = guardiansAlive(level, lair);
            // 2a-bis) Una "muerte" que no deja a la guarida sin guardián es fantasma: el que murió no era el
            //     guardián de esta guarida (o había otro vivo). No rompe el sello y no programa relevo.
            if (lair.guardianDead && guardians > 0) {
                lair.guardianDead = false;
                lair.guardianReborn = false;
                lair.respawnTicks = 0;
            }
            // 2a-ter) El sello cae SOLO porque el guardián murió de verdad (avisa el propio cultivador desde
            //     die()) y SOLO si de verdad no queda ningún guardián vivo: el guardián nunca se retira ni
            //     muere solo, aparece una vez y ahí se queda.
            if (!lair.sealBroken && lair.guardianDead && guardians == 0) {
                openSeal(level, lair);
            }
            // 2a-quater) Si mataste al guardián y dejaste el núcleo en pie, la guarida cría un guardián de
            //     relevo tras un tiempo (una vez por muerte). El sello NO vuelve: una vez roto, el núcleo sigue
            //     expuesto. Así el relevo sale estés donde estés.
            if (lair.guardianDead && !lair.guardianReborn) {
                if (++lair.respawnTicks >= GUARDIAN_RESPAWN_TICKS) {
                    respawnGuardian(level, lair);
                }
            }
            // 2b) El núcleo sigue en pie: se defiende de quien se acerque.
            defendCore(level, lair);
            // 2c) El guardián aparece EN CUANTO la guarida se activa, sin esperar a la primera tanda: así el
            //     sello (que existe desde que se generó la guarida) nunca está puesto sin nadie a quien matar.
            if (!lair.sealBroken && !lair.guardianDead && guardians == 0) {
                spawnOne(level, lair, near, new Random(), true);
            }
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
     *
     * @param lairCore núcleo de su guarida, o {@code null} si el guardián no lo tenía (p. ej. un guardián
     *                 guardado por una versión anterior, o creado con huevo de spawn). En ese caso se busca la
     *                 guarida <b>más cercana</b> a {@code deathPos}, que es la suya: si no, moriría sin avisar
     *                 y <b>el sello no caería nunca</b> (núcleo inaccesible con el guardián ya muerto).
     */
    public static void onGuardianKilled(ServerLevel level, BlockPos lairCore, BlockPos deathPos) {
        List<Lair> list = LAIRS.get(level);
        if (list == null) {
            return;
        }
        Lair found = null;
        double bestDist = Double.MAX_VALUE;
        if (lairCore != null) {
            for (Lair lair : list) {
                if (!lair.cleared && lair.corePos.equals(lairCore)) {
                    found = lair;
                    break;
                }
            }
        }
        if (found == null) {
            for (Lair lair : list) {
                if (lair.cleared) {
                    continue;
                }
                double dist = lair.center.distSqr(deathPos);
                if (dist < bestDist && dist <= (double) GUARDIAN_MATCH_RADIUS * GUARDIAN_MATCH_RADIUS) {
                    bestDist = dist;
                    found = lair;
                }
            }
        }
        if (found == null) {
            DevilRpg.LOGGER.warn("[Lair] Murió un guardián en {} pero no se encontró su guarida (radio {})",
                    deathPos, GUARDIAN_MATCH_RADIUS);
            return;
        }
        found.guardianDead = true;
        // Cada muerte programa su propio relevo (a los GUARDIAN_RESPAWN_TICKS).
        found.guardianReborn = false;
        found.respawnTicks = 0;
        DevilRpg.LOGGER.info("[Lair] Guardián de la guarida {} ha muerto: el sello va a caer",
                found.objectiveIndex);
    }

    /**
     * La guarida cría un <b>guardián de relevo</b> y <b>vuelve a sellar el núcleo</b>, si mataste al anterior
     * y dejaste el núcleo en pie. Ocurre a los {@link #GUARDIAN_RESPAWN_TICKS} de la muerte, y cada muerte
     * programa su propio relevo.
     * <p>
     * El relevo sale con partículas y sonido <b>alrededor del guardián</b> (no del núcleo) para que se note
     * quién ha vuelto. La caja de sellos no se puede levantar con alguien dentro del círculo —lo dejaría
     * encerrado y asfixiándose—, así que en ese caso se pospone y se le avisa por la barra de acción para que
     * se aparte: sin ese aviso parecería que el relevo no funciona.
     */
    private static void respawnGuardian(ServerLevel level, Lair lair) {
        if (!playersNear(level, lair.corePos, RESEAL_CLEAR_RADIUS).isEmpty()) {
            // Se pospone (y se reintenta cada tick) mientras haya alguien DENTRO del círculo: levantar la caja
            // con alguien ahí lo dejaría encerrado y asfixiándose. El aviso va al CHAT (no a la barra de
            // acción, que se pierde de vista) y se registra en el log, porque si no parece que el relevo no
            // funciona: el jugador se queda esperando pegado al núcleo y no entiende por qué no sale.
            if (lair.respawnTicks % 200 == 0) {
                DevilRpg.LOGGER.info("[Lair] Relevo de la guarida {} pospuesto: hay un jugador dentro del "
                        + "círculo del núcleo, y la caja de sellos no puede levantarse con él ahí",
                        lair.objectiveIndex);
                for (Player p : playersNear(level, lair.corePos, 64.0D)) {
                    p.displayClientMessage(Component.literal(
                            "El guardián no puede consagrarse mientras estés dentro del círculo del núcleo: "
                                    + "apártate unos bloques y saldrá."), false);
                }
            }
            return;
        }
        // Si la guarida YA tiene un guardián vivo, no se consagra otro: la "muerte" que programó este relevo
        // no era la del guardián de esta guarida (un cultivador de huevo de spawn, o uno viejo que se había
        // alejado), y sin esta comprobación acababan saliendo DOS guardianes a la vez.
        if (guardiansAlive(level, lair) > 0) {
            lair.guardianDead = false;
            lair.guardianReborn = false;
            lair.respawnTicks = 0;
            DevilRpg.LOGGER.info("[Lair] La guarida {} ya tiene un guardián vivo: no se consagra relevo",
                    lair.objectiveIndex);
            return;
        }
        Mob guardian = spawnOne(level, lair, null, new Random(), true);
        if (guardian == null) {
            return; // no se pudo crear: se reintenta en el siguiente tick
        }
        LairGenerator.buildSealCage(level, lair.corePos);
        lair.guardianReborn = true;
        lair.guardianDead = false;
        lair.sealBroken = false; // el sello vuelve a estar en pie
        lair.respawnTicks = 0;
        LairSavedData.get(level).setSealBroken(lair.objectiveIndex, false); // el sello vuelve: que se sepa
        DevilRpg.LOGGER.info("[Lair] La guarida {} ha consagrado un guardián de relevo: núcleo sellado de nuevo",
                lair.objectiveIndex);
        // Partículas bien visibles ALREDEDOR DEL GUARDIÁN (no del núcleo) para que se note que ha vuelto.
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                guardian.getX(), guardian.getY() + 1.0D, guardian.getZ(), 90, 0.7D, 1.1D, 0.7D, 0.03D);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                guardian.getX(), guardian.getY() + 1.0D, guardian.getZ(), 30, 0.6D, 0.8D, 0.6D, 0.0D);
        level.playSound(null, guardian.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.HOSTILE, 2.0F, 0.6F);
        for (Player p : playersNear(level, lair.corePos, 64.0D)) {
            p.displayClientMessage(Component.literal(
                    "¡Un nuevo guardián del sculk ha vuelto y ha sellado el núcleo!"), false);
        }
    }

    /**
     * Abre el <b>sello</b> del núcleo: retira la caja de sellos y lo anuncia (partículas, sonido y aviso a
     * los jugadores que estén cerca). A partir de ahí el núcleo queda expuesto y se puede destruir — si el
     * jugador no lo aprovecha, {@link #respawnGuardian} lo vuelve a sellar con un guardián nuevo.
     */
    private static void openSeal(ServerLevel level, Lair lair) {
        lair.sealBroken = true;
        lair.guardianDead = true; // el sello roto implica que ya no hay guardián
        LairSavedData.get(level).setSealBroken(lair.objectiveIndex, true); // que siga roto al reiniciar
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

    /**
     * Busca el bloque del núcleo alrededor de una posición, con tolerancia: ±3 bloques en vertical y ±1 en
     * horizontal. Devuelve la posición real (o {@code null} si no hay núcleo ahí).
     */
    private static BlockPos findCoreNear(ServerLevel level, BlockPos around) {
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = around.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.LAIR_CORE_BLOCK.get())) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Comprueba que el núcleo siga en pie y <b>corrige</b> la posición guardada si se movió uno o dos bloques
     * (la altura calculada puede variar entre sesiones). Devuelve la posición del núcleo, o {@code null} si de
     * verdad ya no está.
     */
    private static BlockPos syncCorePos(ServerLevel level, Lair lair) {
        if (level.getBlockState(lair.corePos).is(ModBlocks.LAIR_CORE_BLOCK.get())) {
            return lair.corePos;
        }
        BlockPos found = findCoreNear(level, lair.corePos);
        if (found != null) {
            DevilRpg.LOGGER.warn("[Lair] Núcleo de la guarida {} encontrado en {} (se esperaba {}): se corrige "
                    + "la posición guardada", lair.objectiveIndex, found, lair.corePos);
            lair.corePos = found;
        }
        return found;
    }

    /** Spawnea una tanda de enemigos alrededor de la guarida. */
    private static void spawnWave(ServerLevel level, Lair lair, Player player) {
        Random random = new Random();
        // El guardián lo cría tick() en cuanto la guarida se activa, no aquí: esta tanda es solo la horda.
        // El resto de la tanda llena el cupo de la guarida: si ya hay MAX_LAIR_MOBS vivos no llega nadie, y
        // en cuanto el jugador mata a algunos, las tandas siguientes los reponen hasta volver al cupo.
        int room = MAX_LAIR_MOBS - countLairMobs(level, lair);
        if (room <= 0) {
            DevilRpg.LOGGER.info("[Lair] Guarida {}: cupo lleno ({}/{}), no llega la tanda", lair.objectiveIndex,
                    MAX_LAIR_MOBS - room, MAX_LAIR_MOBS);
            return;
        }
        // La guarida "más lejana" del ancla genera más enemigos e incluye vexes helados.
        int count = Math.min(WAVE_SIZE + Math.min(lair.objectiveIndex, 6), room);
        DevilRpg.LOGGER.info("[Lair] Guarida {}: tanda de {} enemigos", lair.objectiveIndex, count);
        for (int i = 0; i < count; i++) {
            spawnOne(level, lair, player, random, false);
        }
    }

    /** Spawnea un enemigo de la guarida (cultivador, vex helado o zombie agresivo). Devuelve el mob creado. */
    private static Mob spawnOne(ServerLevel level, Lair lair, Player player, Random random, boolean cultivator) {
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
        return mob;
    }

    /**
     * Enemigos vivos de la guarida <b>sin contar al guardián</b>, para el cupo de {@link #MAX_LAIR_MOBS}.
     * <p>
     * Se cuentan solo los que están dentro del <b>radio de patrulla</b> (24), no en los 64 de activación: si
     * se contaban los rezagados escondidos a 30–60 bloques, llenaban el cupo y la guarida parecía muerta
     * (no spawneaba nada aunque no hubiera nadie combatiendo).
     */
    private static int countLairMobs(ServerLevel level, Lair lair) {
        AABB box = new AABB(lair.center).inflate(PATROL_RADIUS);
        return level.getEntitiesOfClass(AggressiveZombieEntity.class, box).size()
                + level.getEntitiesOfClass(FrostVexEntity.class, box).size();
    }

    /**
     * Guardianes <b>vivos</b> que pertenecen a esta guarida, y de paso deja la guarida con <b>uno solo</b>.
     * <p>
     * Hay tres formas de acabar con dos guardianes a la vez, y las tres se corrigen aquí:
     * <ul>
     *   <li>Un guardián de una <b>sesión anterior</b>: no se retira nunca y se guarda con el mundo, pero si su
     *       trozo aún no estaba cargado cuando la guarida se activó, el conteo no lo veía y nacía otro.</li>
     *   <li>Un guardián que <b>persiguió</b> a un jugador más allá del radio de conteo (por eso este radio es
     *       más amplio que {@link #GUARDIAN_MATCH_RADIUS}).</li>
     *   <li>Un cultivador <b>sin hogar</b> (huevo de spawn, o guardado por una versión anterior): se adopta
     *       —se le asigna el núcleo de esta guarida como hogar— en vez de dejar que la guarida crie otro.</li>
     * </ul>
     * De los que pertenecen a la guarida se queda el <b>más viejo vivo</b> (el id más bajo, que es el que se
     * creó antes) y los duplicados vivos se retiran con un aviso en el log y un golpe de partículas, para que
     * se note que fue el sculk y no un fallo. Los que están muriendo se dejan en paz: su {@code die()} ya avisa
     * a {@link #onGuardianKilled}.
     */
    private static int guardiansAlive(ServerLevel level, Lair lair) {
        List<SculkCultivatorEntity> mine = new ArrayList<>();
        for (SculkCultivatorEntity guardian : level.getEntitiesOfClass(SculkCultivatorEntity.class,
                new AABB(lair.corePos).inflate(GUARDIAN_COUNT_RADIUS))) {
            BlockPos home = guardian.getHomePos();
            if (home == null) {
                guardian.setHome(lair.corePos, PATROL_RADIUS);
                DevilRpg.LOGGER.info("[Lair] Guardián sin hogar adoptado por la guarida {} (estaba en {})",
                        lair.objectiveIndex, guardian.blockPosition());
                home = lair.corePos;
            }
            if (home.closerThan(lair.corePos, GUARDIAN_MATCH_RADIUS)) {
                mine.add(guardian);
            }
        }
        SculkCultivatorEntity keeper = null;
        for (SculkCultivatorEntity guardian : mine) {
            if (guardian.isDeadOrDying()) {
                continue;
            }
            if (keeper == null || guardian.getId() < keeper.getId()) {
                keeper = guardian;
            }
        }
        int alive = keeper == null ? 0 : 1;
        if (keeper != null) {
            for (SculkCultivatorEntity guardian : mine) {
                if (guardian == keeper || guardian.isDeadOrDying()) {
                    continue;
                }
                DevilRpg.LOGGER.warn("[Lair] La guarida {} tenía {} guardianes: se retira el duplicado y se "
                        + "queda el más antiguo", lair.objectiveIndex, mine.size());
                level.sendParticles(ParticleTypes.SCULK_SOUL, guardian.getX(), guardian.getY() + 1.0D,
                        guardian.getZ(), 25, 0.4D, 0.7D, 0.4D, 0.02D);
                guardian.discard();
            }
        }
        return alive;
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
    /**
     * Puntos de habilidad que paga destruir el núcleo de una guarida: {@code 4 + 1 por cada 3 objetivos ya
     * superados}, con tope 10. Son la moneda del árbol de skills (1 por nivel de experiencia) y llenar todos
     * los árboles pide nivel 300+, así que las misiones también empujan la progresión. Dan algo más que salvar
     * una aldea ({@code VillageManager}) porque el asalto a la guarida es más duro y más largo.
     */
    private static int lairSkillPoints(int objectiveIndex) {
        return Math.min(4 + Math.max(0, objectiveIndex) / 3, 10);
    }

    private static final class Lair {
        final int objectiveIndex;
        final BlockPos center;
        /** Núcleo de la guarida. <b>Mutable</b>: se corrige solo si entre sesiones la altura calculada varía. */
        BlockPos corePos;
        boolean cleared;
        int spawnTimer = SPAWN_INTERVAL_TICKS / 2; // primera tanda algo antes
        /** ¿Ha MUERTO el guardián? Solo eso rompe el sello. */
        boolean guardianDead;
        /** ¿Ya se crió el guardián de relevo de esta muerte? (cada muerte programa el suyo) */
        boolean guardianReborn;
        /** ¿Está roto el sello del núcleo (núcleo expuesto)? */
        boolean sealBroken;
        /** Ticks (de guarida activa) desde la muerte del guardián; al llegar al tope sale el relevo. */
        int respawnTicks;

        Lair(int objectiveIndex, BlockPos center, BlockPos corePos) {
            this.objectiveIndex = objectiveIndex;
            this.center = center;
            this.corePos = corePos;
        }

        /** Marca la guarida como limpiada y, si hay jugador, le da la recompensa. */
        void clear(ServerLevel level, Player player) {
            this.cleared = true;
            this.sealBroken = true;
            // Se guarda: una guarida limpiada NO vuelve a generarse en la próxima sesión. Se guarda también la
            // posición del núcleo para poder COMPROBAR la marca después (y deshacerla si era falsa).
            LairSavedData.get(level).markCleared(objectiveIndex, corePos);
            // Red de seguridad: si quedara algún sello en pie, se retira (el núcleo ya no está).
            removeLeftoverSeal(level);
            DevilRpg.LOGGER.info("[Lair] Guarida {} limpiada en {}", objectiveIndex, corePos);
            if (player != null) {
                player.displayClientMessage(Component.literal("¡Has destruido la guarida! El lugar queda en silencio."), false);
                player.giveExperiencePoints(40 + objectiveIndex * 15);
                player.addItem(new ItemStack(Items.BONE, 8));
                player.addItem(new ItemStack(Items.SOUL_SAND, 6));
                player.addItem(new ItemStack(Items.EMERALD, 3));
                // Ayuda de puntos de habilidad, como al salvar una aldea; aquí algo más porque asaltar una
                // guarida es más duro y más largo. Escala suave con el objetivo, con tope.
                int skillPoints = lairSkillPoints(objectiveIndex);
                PlayerExperienceCapabilityInterface expCap =
                        IGenericCapability.getUnwrappedPlayerCapability(player, PlayerExperienceCapability.INSTANCE);
                if (expCap != null) {
                    expCap.addUnspentPoints(skillPoints, player);
                    player.displayClientMessage(Component.literal(
                            "El sculk se apaga: +" + skillPoints + " puntos de habilidad."), false);
                    DevilRpg.LOGGER.info("[Lair] Guarida {} limpiada: +{} puntos de habilidad (quedan {})",
                            objectiveIndex, skillPoints, expCap.getUnspentPoints());
                }
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
