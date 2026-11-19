package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.entity.nav.NavObstruction;
import com.chipoodle.devilrpg.entity.nav.NavPlanner;
import com.chipoodle.devilrpg.entity.nav.NavRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A2 Stage 7K · <b>ARNÉS MÍNIMO RECONSTRUIDO DESDE CERO</b> (sólo el modo {@code edges}).
 * <p>
 * Aviso de honestidad: este fichero <b>NO es equivalente</b> al arnés perdido en Stage 7H. Se ha escrito de nuevo
 * contra las APIs de producción y los contratos documentados en {@code tools/a2n/LEEME.md} §4. Los únicos
 * contratos que implementa son los cinco de validación de aristas:
 * <ol>
 *   <li>WALK válido en llano;</li>
 *   <li>DROP válido de 3 bloques;</li>
 *   <li>DROP de 4 bloques RECHAZADO;</li>
 *   <li>subida válida de 2 bloques (STEP_UP/JUMP_UP);</li>
 *   <li>subida de 3 bloques RECHAZADA.</li>
 * </ol>
 * No duplica la validación de aristas: usa {@link NavPlanner#plan} y {@link NavObstruction} de producción, que es
 * justo lo que se quiere medir. Cada caso comprueba ANTES sus precondiciones de mundo; si falta una, se informa
 * {@code FALLO DE INSTRUMENTO} (no se cuenta como aprobado).
 */
@EventBusSubscriber(modid = DevilRpg.MODID)
public final class A2NavHarness {

    private static final String MODE = System.getProperty("a2n.mode", "none");
    private static final int ASENTAR_TICKS = 60;
    private static final int PRESUPUESTO = Integer.getInteger("devilrpg.nav.budget", 900);
    private static final int H = 48;
    private static final int V = 6;

    private static ServerLevel nivel;
    private static int ticks;
    private static boolean hecho;
    private static int casosOk;
    private static int casosTotal;
    private static int fallosDeInstrumento;
    private static final StringBuilder DETALLE = new StringBuilder();

    // Captura referencias exactas de aldeanos incorporados durante la generación del fixture.
    // Evita depender exclusivamente del índice espacial AABB que omitió entidades vivas en runs previos.
    private static boolean rCapturandoAldeanos;
    private static final java.util.Map<java.util.UUID, net.minecraft.world.entity.npc.Villager>
            rAldeanosCapturados = new java.util.LinkedHashMap<>();

    private A2NavHarness() {
    }

    @SubscribeEvent
    public static void alArrancar(final ServerStartedEvent evento) {
        nivel = evento.getServer().overworld();
        DevilRpg.LOGGER.info("[A2N] ARRANCADO modo={} (arnes RECONSTRUIDO Stage 7K)", MODE);
        DevilRpg.LOGGER.info("[A2N] JVM_TRACE traceNav={} traceGlobalSearch={} java={} vendor={}",
                Boolean.getBoolean("devilrpg.a2n.traceNav"),
                Boolean.getBoolean("devilrpg.a2n.traceGlobalSearch"),
                System.getProperty("java.version"), System.getProperty("java.vendor"));
    }

    @SubscribeEvent
    public static void capturarAldeanoDeFixture(final EntityJoinLevelEvent evento) {
        if (!rCapturandoAldeanos || nivel == null || evento.getLevel() != nivel
                || !(evento.getEntity() instanceof net.minecraft.world.entity.npc.Villager villager)) {
            return;
        }
        rAldeanosCapturados.put(villager.getUUID(), villager);
        log("C4 POPULATION JOIN uuid=" + villager.getUUID()
                + " profession=" + villager.getVillagerData().getProfession()
                + " pos=" + villager.blockPosition().toShortString()
                + " loadedFromDisk=" + evento.loadedFromDisk()
                + " canceledAtListener=" + evento.isCanceled());
    }

    private static java.util.List<net.minecraft.world.entity.npc.Villager> aldeanosCapturadosEn(
            net.minecraft.world.phys.AABB bounds) {
        java.util.List<net.minecraft.world.entity.npc.Villager> result = new java.util.ArrayList<>();
        for (var villager : rAldeanosCapturados.values()) {
            boolean tracked = villager.isAddedToLevel()
                    || (nivel != null && nivel.getEntity(villager.getUUID()) == villager);
            if (tracked && !villager.isRemoved() && villager.isAlive()
                    && bounds.intersects(villager.getBoundingBox())) {
                result.add(villager);
            }
        }
        return result;
    }

    @SubscribeEvent
    public static void alTick(final ServerTickEvent.Pre evento) {
        if (hecho || nivel == null) {
            return;
        }
        if ("raid".equals(MODE)) {
            // No iniciar si la telemetría solicitada no llegó al JVM real del servidor.
            if (!Boolean.getBoolean("devilrpg.a2n.traceNav")
                    || !Boolean.getBoolean("devilrpg.a2n.traceGlobalSearch")) {
                hecho = true;
                cerrar("INVALIDO", "JVM_TRACE_PROPERTIES missing: traceNav="
                        + Boolean.getBoolean("devilrpg.a2n.traceNav") + " traceGlobalSearch="
                        + Boolean.getBoolean("devilrpg.a2n.traceGlobalSearch"), evento.getServer());
                return;
            }
            // Edit 4: fixture -> spawn -> muestreo por tick + fases A/B. Sin totales ni veredictos.
            if (!rPreparado) {
                rPreparado = true;
                prepararRaidFixture(evento.getServer());
                if (rFixtureOk) {
                    prepararRaidAsaltantes(evento.getServer());
                } else {
                    hecho = true;
                    cerrar("INVALIDO", "RAID FIXTURE INVALIDO - Edit 4 | fallosDeInstrumento="
                            + fallosDeInstrumento, evento.getServer());
                    return;
                }
            }
            if (!rIniciado || hecho) {
                return;
            }
            if (rRegistroAsedioPendiente && rTicks >= rRegistroAsedioDesdeTick) {
                registrarAsedioPendiente(evento.getServer());
                if (rRegistroAsedioPendiente) {
                    return; // no se inicia el muestreo del asedio hasta registrar la ola completa
                }
            }
            rTicks++;
            muestrearRaid();
            muestrearDefensa();
            traceInit();
            traceTick(evento.getServer());
            if (rTicks == R_FASE_A) {
                informeRaid("A", evento.getServer());
            }
            if (rTicks == R_FASE_A + 1) {
                log("FASE B inicio t=" + rTicks + " " + resumenRaid());
            }
            if (rTicks >= R_FASE_B && !rPostRaidIniciado) {
                informeRaid("B", evento.getServer());
                rTrabajadoresQueReanudaron.clear();
                boolean diagSnapshot = Boolean.getBoolean("a2n.diagSnapshot");
                int retirados = 0;
                if (diagSnapshot) {
                    for (int i = 0; i < rAsaltantes.size(); i++) {
                        AggressiveZombieEntity atacante = rAsaltantes.get(i);
                        if (atacante == null || atacante.isRemoved()) {
                            continue;
                        }
                        String estado = String.valueOf(atacante.navExecutor().state());
                        BlockPos obj = atacante.navExecutor().objective();
                        var color = estado.equals("FAILED")
                                ? net.minecraft.ChatFormatting.RED
                                : estado.equals("TRAVERSING")
                                ? net.minecraft.ChatFormatting.GREEN
                                : net.minecraft.ChatFormatting.YELLOW;
                        atacante.setCustomName(net.minecraft.network.chat.Component.literal(
                                "A2 IDX" + i + " " + estado + " tgt="
                                        + (obj == null ? "-" : obj.toShortString()))
                                .withStyle(color));
                        atacante.setCustomNameVisible(true);
                    }
                    nivel.setDefaultSpawnPos(new BlockPos(661, 80, 590), 0.0F);
                    evento.getServer().saveEverything(false, true, true);
                    log("A2 DIAG SNAPSHOT saved diagSnapshot=true center=661,71,533 spawn=661,80,590");
                    rRetiradosPorArnes = 0;
                } else {
                    for (AggressiveZombieEntity atacante : rAsaltantes) {
                        if (atacante != null && !atacante.isRemoved()) {
                            atacante.discard();
                            retirados++;
                        }
                    }
                    rRetiradosPorArnes = retirados;
                }
                try {
                    com.chipoodle.devilrpg.world.VillageManager.terminarAsedioParaArnes(
                            evento.getServer().overworld(), 0);
                    com.chipoodle.devilrpg.world.VillageManager.ejecutarMantenimientoParaArnes(
                            evento.getServer().overworld(), 0, rCentro);
                    prepararRecursosC4Recovery(evento.getServer().overworld(), rCentro);
                    rPostRaidIniciado = true;
                    log("C4 POSTRAID maintenance=OK attackersRemovedForRecovery=" + retirados
                            + " recoveryTicks=" + R_RECUPERACION_TICKS);
                } catch (RuntimeException ex) {
                    fallosDeInstrumento++;
                    hecho = true;
                    cerrar("INVALIDO", "C4 POSTRAID maintenance failed: " + ex, evento.getServer());
                    return;
                }
                return;
            }
            if (rPostRaidIniciado && ++rPostRaidTicks >= R_RECUPERACION_TICKS) {
                int[] assignments = auditarAsignacionesJobSite();
                var villagers = aldeanosCapturadosEn(new net.minecraft.world.phys.AABB(rCentro).inflate(
                        com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 20.0D));
                int alive = 0;
                for (var villager : villagers) {
                    if (villager.isAlive() && !villager.isBaby()) alive++;
                }
                int expectedProfessionals = com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo();
                int professionals = 0;
                int professionalGoals = 0;
                int workerDeaths = 0;
                int workerUnknown = 0;
                int workersResumedAlive = 0;
                for (java.util.UUID workerId : rTrabajadoresEsperados) {
                    var entity = nivel.getEntity(workerId);
                    if (!(entity instanceof net.minecraft.world.entity.npc.Villager)) {
                        entity = rAldeanosCapturados.get(workerId);
                    }
                    if (entity instanceof net.minecraft.world.entity.npc.Villager worker
                            && worker.isAlive() && !worker.isRemoved()) {
                        professionals++;
                        if (tieneGoalProfesional(worker)) professionalGoals++;
                        if (rTrabajadoresQueReanudaron.contains(workerId)) {
                            workersResumedAlive++;
                        } else {
                            java.util.List<String> runningGoals = new java.util.ArrayList<>();
                            for (var wrapped : worker.goalSelector.getAvailableGoals()) {
                                if (wrapped.isRunning()) runningGoals.add(wrapped.getGoal().getClass().getSimpleName());
                            }
                            var jobSite = worker.getBrain().getMemory(
                                    net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                                    .map(memory -> memory.pos().toShortString()).orElse("none");
                            String eligibility = diagnosticoElegibilidadProfesional(worker);
                            log("C4 WORKER NOT RESUMED uuid=" + workerId
                                    + " profession=" + worker.getVillagerData().getProfession()
                                    + " expectedGoal=" + goalProfesionalEsperado(worker)
                                    + " runningGoals=" + runningGoals
                                    + " pos=" + worker.blockPosition().toShortString()
                                    + " jobSite=" + jobSite + " eligibility={" + eligibility + "} alive=true");
                        }
                    } else if (rTrabajadoresMuertos.contains(workerId)) {
                        workerDeaths++;
                        log("C4 WORKER CASUALTY uuid=" + workerId + " " + rCausaMuerteTrabajador.getOrDefault(
                                workerId, "deathSource=unknown"));
                    } else {
                        workerUnknown++;
                        log("C4 WORKER UNRESOLVED uuid=" + workerId
                                + " entity=" + (entity == null ? "missing" : entity.getType())
                                + " deathEvent=" + rTrabajadoresMuertos.contains(workerId));
                    }
                }
                boolean defenseObserved = rGuardiasQueCombatieron.size() == 4;
                boolean workersAccounted = professionals + workerDeaths == expectedProfessionals && workerUnknown == 0;
                boolean professionsRestored = workersAccounted
                        && workersResumedAlive == professionals
                        && professionalGoals == professionals;
                boolean assignmentsClean = assignments[0] > 0 && assignments[1] == 0 && assignments[2] == 0;
                boolean villageOutcomeObserved = alive > 0;
                boolean accepted = rFixtureOk && fallosDeInstrumento == 0 && defenseObserved
                        && professionsRestored && assignmentsClean && villageOutcomeObserved;
                log("C4 DEFENSE guardasQueCombatieron=" + rGuardiasQueCombatieron.size() + "/4"
                        + " trabajadoresEsperados=" + expectedProfessionals
                        + " trabajadoresVivos=" + professionals
                        + " trabajadoresReanudados=" + workersResumedAlive
                        + " bajasTrabajadores=" + workerDeaths
                        + " trabajadoresNoResueltos=" + workerUnknown
                        + " villageVillagersAlive=" + alive
                        + " professionGoals=" + professionalGoals + "/" + professionals);
                log("C4 JOB_SITE assignments=" + assignments[0] + " orphaned=" + assignments[1]
                        + " duplicateOwners=" + assignments[2]);
                log("C4 OUTCOME=" + (alive > 0 ? "SURVIVED" : "FALLEN")
                        + " attackersRemovedForRecovery=" + rRetiradosPorArnes
                        + " workerCasualties=" + workerDeaths);
                hecho = true;
                cerrar(accepted ? "C4_PASS" : "C4_FAIL",
                        "guardasCombatieron=" + defenseObserved + " profesionalesReanudados=" + professionsRestored
                                + " asignacionesJobSiteLimpias=" + assignmentsClean
                                + " supervivientes=" + alive + " trabajadoresEsperados=" + expectedProfessionals
                                + " trabajadoresVivos=" + professionals + " trabajadoresReanudados=" + workersResumedAlive
                                + " bajasTrabajadores=" + workerDeaths + " trabajadoresNoResueltos=" + workerUnknown
                                + " retiradosParaRecuperacion=" + rRetiradosPorArnes
                                + " jobSite=" + java.util.Arrays.toString(assignments)
                                + " fallosDeInstrumento=" + fallosDeInstrumento, evento.getServer());
            }
            return;
        }
        if ("longrange".equals(MODE)) {
            if (++ticks < ASENTAR_TICKS) {
                return;
            }
            tickLongrange(evento.getServer());
            return;
        }
        if ("construction".equals(MODE)) {
            if (++ticks < ASENTAR_TICKS) {
                return;
            }
            tickConstruction(evento.getServer());
            return;
        }
        if ("stairs".equals(MODE)) {
            if (++ticks < ASENTAR_TICKS) {
                return;
            }
            tickStageStairs(evento.getServer());
            return;
        }
        if ("stage6f".equals(MODE)) {
            if (++ticks < ASENTAR_TICKS) {
                return;
            }
            tickStage6f(evento.getServer());
            return;
        }
        if ("stage6e".equals(MODE)) {
            if (++ticks < ASENTAR_TICKS) {
                return;
            }
            tickStage6e(evento.getServer());
            return;
        }
        if (!"edges".equals(MODE)) {
            hecho = true;
            cerrar("SIN EJECUTAR", "modo '" + MODE + "' no reconstruido (solo existe 'edges')", evento.getServer());
            return;
        }
        if (++ticks < ASENTAR_TICKS) {
            return;
        }
        hecho = true;
        try {
            fixtureEdges();
        } catch (RuntimeException e) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO (excepcion en el fixture): " + e);
        }
        String veredicto = (fallosDeInstrumento > 0 || casosOk != casosTotal)
                ? "INVALIDO" : "COMPLETO";
        cerrar(veredicto, "casos=" + casosOk + "/" + casosTotal + " fallosDeInstrumento=" + fallosDeInstrumento,
                evento.getServer());
    }

    // ================================================================================================
    // Fixture `edges`
    // ================================================================================================
    private static void fixtureEdges() {
        // Zona lejos de la aldea, con chunks forzados (mundo aislado desechable del run).
        BlockPos base = nivel.getSharedSpawnPos().offset(600, 40, 600);
        int x0 = base.getX();
        int z0 = base.getZ();
        int y = base.getY();
        forzar(x0 - 8, z0 - 8, x0 + 24, z0 + 8, y);

        // Losa base: suelo solido en y-1 y aire hasta y+7 en toda la zona.
        for (int x = x0 - 6; x <= x0 + 22; x++) {
            for (int z = z0 - 6; z <= z0 + 6; z++) {
                for (int dy = 0; dy <= 7; dy++) {
                    aire(x, y + dy, z);
                }
                solido(x, y - 1, z);
            }
        }
        AggressiveZombieEntity sonda = sonda(x0, y, z0);
        if (sonda == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO: no se pudo crear la sonda (entidad nula)");
            return;
        }

        // ---------------- caso 1 · WALK llano ----------------
        BlockPos meta1 = new BlockPos(x0 + 4, y, z0);
        boolean pre1 = precondicion("caso1", meta1, true, true);
        NavPlanner.Result r1 = plan(sonda, new BlockPos(x0, y, z0), meta1);
        boolean ok1 = pre1 && !r1.route().isEmpty() && soloLlano(r1.route()) && !r1.hitBudget();
        caso("caso1 WALK llano valido", ok1, "pre=" + pre1 + " ruta=" + r1.route().length()
                + " todasLlano=" + soloLlano(r1.route()) + " presupuesto=" + r1.hitBudget(),
                "se esperaba ruta no vacia, toda WALK, sin agotar presupuesto");

        // ---------------- caso 2 · DROP de 3 (valido) ----------------
        // Plataforma baja: su posicion de pie queda 3 bloques por debajo de y.
        BlockPos meta2 = new BlockPos(x0 + 6, y - 3, z0);
        solido(meta2.getX(), y - 4, meta2.getZ());
        for (int dy = -3; dy <= 6; dy++) {
            aire(meta2.getX(), y + dy, meta2.getZ());
        }
        boolean pre2 = precondicion("caso2", meta2, true, true) && !nivel.getBlockState(meta2.below()).isAir();
        NavPlanner.Result r2 = plan(sonda, new BlockPos(x0, y, z0), meta2);
        int caida2 = caidaMaxima(r2.route());
        boolean ok2 = pre2 && !r2.route().isEmpty() && contiene(r2.route(), NavRoute.Transition.DROP)
                && caida2 <= 3 && !r2.hitBudget();
        caso("caso2 DROP de 3 bloques valido", ok2, "pre=" + pre2 + " ruta=" + r2.route().length()
                + " conDROP=" + contiene(r2.route(), NavRoute.Transition.DROP) + " caidaMax=" + caida2,
                "se esperaba ruta con DROP y caida <= 3");

        // ---------------- caso 3 · DROP de 4 (RECHAZADO) ----------------
        BlockPos meta3 = new BlockPos(x0 + 9, y - 4, z0);
        solido(meta3.getX(), y - 5, meta3.getZ());
        for (int dy = -4; dy <= 6; dy++) {
            aire(meta3.getX(), y + dy, meta3.getZ());
        }
        boolean pre3 = precondicion("caso3", meta3, true, true) && !nivel.getBlockState(meta3.below()).isAir();
        NavPlanner.Result r3 = plan(sonda, new BlockPos(x0, y, z0), meta3);
        boolean ok3 = pre3 && r3.route().isEmpty();
        caso("caso3 DROP de 4 bloques RECHAZADO", ok3, "pre=" + pre3 + " ruta=" + r3.route().length()
                + " motivo=" + r3.summary(), "se esperaba ruta VACIA (caida insegura)");

        // ---------------- caso 4 · subida de 2 (valida) ----------------
        BlockPos meta4 = new BlockPos(x0 + 12, y + 2, z0);
        for (int dy = 0; dy <= 7; dy++) {
            aire(meta4.getX(), y + dy, meta4.getZ());
        }
        solido(meta4.getX(), y + 1, meta4.getZ());
        boolean pre4 = precondicion("caso4", meta4, true, true);
        NavPlanner.Result r4 = plan(sonda, new BlockPos(x0, y, z0), meta4);
        boolean ok4 = pre4 && !r4.route().isEmpty() && subidaMaxima(r4.route()) == 2 && !r4.hitBudget();
        caso("caso4 subida de 2 bloques valida", ok4, "pre=" + pre4 + " ruta=" + r4.route().length()
                + " subidaMax=" + subidaMaxima(r4.route()), "se esperaba ruta con desnivel +2");

        // ---------------- caso 5 · subida de 3 (RECHAZADA) ----------------
        BlockPos meta5 = new BlockPos(x0 + 15, y + 3, z0);
        solido(meta5.getX(), y - 1, meta5.getZ());
        for (int dy = -1; dy <= 2; dy++) {
            solido(meta5.getX(), y + dy, meta5.getZ());
        }
        for (int dy = 3; dy <= 8; dy++) {
            aire(meta5.getX(), y + dy, meta5.getZ());
        }
        boolean pre5 = precondicion("caso5", meta5, true, true);
        NavPlanner.Result r5 = plan(sonda, new BlockPos(x0, y, z0), meta5);
        boolean ok5 = pre5 && r5.route().isEmpty();
        caso("caso5 subida de 3 bloques RECHAZADA", ok5, "pre=" + pre5 + " ruta=" + r5.route().length()
                + " motivo=" + r5.summary(), "se esperaba ruta VACIA (desnivel no ejecutable)");

        sonda.discard();
    }

    // ================================================================================================
    // ================================================================================================
    // Modo `stage6e` · BREAK AUTORIZADO DE UN NODO DE RUTA (verifica la exención BREAK/BRIDGE del Stage 7H)
    // Contrato y geometría: ver tools/a2n/LEEME.md §11. NO se declara equivalente al arnés perdido.
    // ================================================================================================
    private static AggressiveZombieEntity s6eMob;
    private static BlockPos s6eMuro;
    private static BlockPos s6eObjetivo;
    private static int s6eTicks;
    private static boolean s6ePreparado;
    private static boolean s6eRutaConBreak;
    private static boolean s6eAccionEnviada;
    private static boolean s6eGuardaProtege;
    private static boolean s6eGuardaNoRechaza;
    private static int s6eMovesAlRomper = -1;
    private static boolean s6eContinuo;
    private static String s6eAccionVista = "-";
    private static String s6eMotivo = "-";

    private static void prepararStage6e() {
        s6ePreparado = true;
        BlockPos base = nivel.getSharedSpawnPos().offset(700, 40, 700);
        int x0 = base.getX();
        int z0 = base.getZ();
        int y = base.getY();
        forzar(x0 - 8, z0 - 8, x0 + 16, z0 + 8, y);
        // Terreno: losa + aire.
        for (int x = x0 - 6; x <= x0 + 14; x++) {
            for (int z = z0 - 6; z <= z0 + 6; z++) {
                for (int dy = 0; dy <= 6; dy++) {
                    aire(x, y + dy, z);
                }
                solido(x, y - 1, z);
            }
        }
        // CÁMARA CERRADA 5x5 alrededor del ente: la ÚNICA salida posible es romper (BREAK) un muro.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean borde = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (!borde) {
                    continue;
                }
                for (int dy = 0; dy <= 3; dy++) {
                    solido(x0 + dx, y + dy, z0 + dz);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                solido(x0 + dx, y + 3, z0 + dz); // techo
            }
        }
        // Precondición: el muro a romper (cara +x) existe y es sólido ANTES de empezar.
        BlockPos muroEsperado = new BlockPos(x0 + 2, y, z0);
        if (nivel.getBlockState(muroEsperado).isAir()) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO stage6e: el muro esperado en " + muroEsperado.toShortString()
                    + " NO es sólido tras construir la cámara");
        }
        s6eObjetivo = new BlockPos(x0 + 7, y, z0);
        s6eMob = sonda(x0, y, z0);
        if (s6eMob == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO stage6e: sonda nula");
            return;
        }
        s6eMob.navExecutor().setObjective(s6eObjetivo);
        log("STAGE6E inicio: ente=" + s6eMob.blockPosition().toShortString()
                + " camara 5x5 cerrada, muro esperado=" + muroEsperado.toShortString()
                + " objetivo=" + s6eObjetivo.toShortString());
    }

    private static void tickStage6e(final MinecraftServer server) {
        s6eTicks++;
        if (!s6ePreparado) {
            prepararStage6e();
            if (s6eMob == null) {
                hecho = true;
                cerrar("INVALIDO", "stage6e: instrumentacion incompleta (mob nulo)", server);
                return;
            }
            return;
        }
        NavRoute ruta = s6eMob.navExecutor().route();
        if (ruta != null) {
            for (NavRoute.Node n : ruta.nodes()) {
                if (n.transition() == NavRoute.Transition.BREAK || n.transition() == NavRoute.Transition.BRIDGE) {
                    s6eRutaConBreak = true;
                    if (s6eMuro == null) {
                        s6eMuro = n.pos().immutable();
                    }
                }
            }
            NavRoute.Node actual = ruta.currentNode(s6eMob.navExecutor().indiceDeRuta());
            if (actual != null) {
                boolean enRuta = s6eMob.navExecutor().celdaDeRutaActiva(actual.pos());
                boolean ajena = s6eMob.navExecutor().celdaDeRutaActiva(actual.pos().offset(48, 32, 48));
                s6eGuardaProtege = s6eGuardaProtege || (enRuta && !ajena);
            }
        }
        String accion = s6eMob.navExecutor().rutaAccionDiag();
        if (accion.contains("BREAK") || accion.contains("BRIDGE")) {
            s6eAccionEnviada = true;
            s6eAccionVista = accion;
            s6eGuardaNoRechaza = s6eMob.navExecutor().replanteosPorGuarda() == 0;
        }
        boolean muroRoto = s6eMuro != null && nivel.getBlockState(s6eMuro).isAir();
        if (muroRoto && s6eMovesAlRomper < 0) {
            s6eMovesAlRomper = s6eMob.navExecutor().moves();
        }
        if (muroRoto && s6eMovesAlRomper >= 0 && s6eMob.navExecutor().moves() > s6eMovesAlRomper) {
            s6eContinuo = true;
        }
        if (s6eTicks % 100 == 0) {
            log("STAGE6E t=" + s6eTicks + " pos=" + s6eMob.blockPosition().toShortString()
                    + " muro=" + (s6eMuro == null ? "-" : s6eMuro.toShortString() + "/roto="
                    + nivel.getBlockState(s6eMuro).isAir())
                    + " ejec={" + s6eMob.navExecutor().describe() + "}"
                    + " accion={" + s6eAccionVista + "}");
        }
        if (s6eTicks >= 900) {
            hecho = true;
            veredictoStage6e(server);
        }
    }

    private static void veredictoStage6e(final MinecraftServer server) {
        boolean muroRoto = s6eMuro != null && nivel.getBlockState(s6eMuro).isAir();
        boolean llego = s6eMob.navExecutor().objective() != null
                && s6eMob.blockPosition().distSqr(s6eMob.navExecutor().objective()) <= 2.25D;
        if (s6eMuro == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO stage6e: la ruta NUNCA contuvo un nodo BREAK/BRIDGE "
                    + "(sin celda que verificar)");
        }
        caso("6e-1 la ruta de produccion contiene un nodo BREAK/BRIDGE", s6eRutaConBreak,
                "muro=" + (s6eMuro == null ? "-" : s6eMuro.toShortString()),
                "se esperaba un nodo BREAK/BRIDGE en la ruta aceptada");
        caso("6e-2 el ejecutor alcanzo el nodo y despacho su accion autorizada", s6eAccionEnviada,
                "accion={" + s6eAccionVista + "}", "se esperaba BREAK/BRIDGE en rutaAccionDiag");
        caso("6e-3 el bloque objetivo fue ELIMINADO del mundo", muroRoto,
                "muro=" + (s6eMuro == null ? "-" : s6eMuro.toShortString()) + " aire=" + muroRoto,
                "se esperaba la celda en aire");
        caso("6e-4 la guarda de ruta NO rechazo la accion autorizada", s6eGuardaNoRechaza,
                "replanteosPorGuarda=" + s6eMob.navExecutor().replanteosPorGuarda(),
                "se esperaba 0 replanteos por guarda");
        caso("6e-5 el ejecutor continuo tras la rotura (sin bucle sin fin)", s6eContinuo || llego,
                "continuo=" + s6eContinuo + " llego=" + llego + " moves=" + s6eMob.navExecutor().moves()
                        + " estado=" + s6eMob.navExecutor().state(),
                "se esperaba movimiento/replanteo posterior o llegada");
        caso("6e-6 la guarda protege la ruta activa y no celdas ajenas", s6eGuardaProtege,
                "guardaProtege=" + s6eGuardaProtege, "se esperaba true para el nodo activo y false para una ajena");
        s6eMotivo = "muro=" + (s6eMuro == null ? "-" : s6eMuro.toShortString()) + " roto=" + muroRoto
                + " replanteosGuarda=" + s6eMob.navExecutor().replanteosPorGuarda()
                + " estado=" + s6eMob.navExecutor().state();
        s6eMob.discard();
        String veredicto = (fallosDeInstrumento > 0 || casosOk != casosTotal) ? "INVALIDO" : "COMPLETO";
        cerrar(veredicto, "casos=" + casosOk + "/" + casosTotal
                + " fallosDeInstrumento=" + fallosDeInstrumento + " | " + s6eMotivo, server);
    }

    // ================================================================================================
    // Modo `stage6f` · ciclo de DESCENSO y sus invariantes de seguridad (contrato: LEEME.md §12)
    // Usa el validador de produccion NavExecutor.validarDescenso(...); NO duplica sus reglas.
    // ================================================================================================
    private static AggressiveZombieEntity s6fMob;
    private static boolean s6fPreparado;
    private static int s6fTicks;
    private static boolean s6fRutaDrop;
    private static boolean s6fAvanzoAntes;
    private static boolean s6fAterrizo;
    private static int s6fIdxPrevio = -1;
    private static BlockPos s6fNodoDrop;
    private static int s6fIdxBaseline = -1;
    private static BlockPos s6fAterrizaje;
    private static AggressiveZombieEntity s6fPlano;
    private static BlockPos s6fMisionPlano;
    private static String s6fMotivo = "-";
    private static String s6fDiagPlano = "-";
    private static boolean s6fCicloConstruccion;
    private static String s6fConstruccionDiag = "-";
    private static int s6fConstruccionCambios;
    private static String s6fEstadosVistos = "";

    private static void prepararStage6f() {
        s6fPreparado = true;
        BlockPos base = nivel.getSharedSpawnPos().offset(800, 40, 800);
        int x0 = base.getX();
        int z0 = base.getZ();
        int y = base.getY();
        forzar(x0 - 8, z0 - 8, x0 + 16, z0 + 8, y);
        for (int x = x0 - 6; x <= x0 + 14; x++) {
            for (int z = z0 - 6; z <= z0 + 6; z++) {
                for (int dy = 0; dy <= 6; dy++) {
                    aire(x, y + dy, z);
                }
                solido(x, y - 1, z);
            }
        }
        s6fMob = sonda(x0, y, z0);
        if (s6fMob == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO stage6f: sonda nula");
            return;
        }

        // ---- d1 · descenso VÁLIDO en columna contigua (+1,0): repisa 2 abajo, columna despejada ----
        s6fAterrizaje = new BlockPos(x0 + 1, y - 2, z0);
        aire(s6fAterrizaje.getX(), y - 2, s6fAterrizaje.getZ());
        aire(s6fAterrizaje.getX(), y - 1, s6fAterrizaje.getZ());
        aire(s6fAterrizaje.getX(), y, s6fAterrizaje.getZ());
        aire(s6fAterrizaje.getX(), y + 1, s6fAterrizaje.getZ());
        solido(s6fAterrizaje.getX(), y - 3, s6fAterrizaje.getZ());
        boolean pre1 = precondicion("d1", s6fAterrizaje, true, true);
        var d1 = s6fMob.navExecutor().validarDescenso(s6fAterrizaje);
        caso("6f-d1 descenso seguro ACEPTADO", pre1 && d1.valido(), "validador={" + d1.resumen() + "}",
                "se esperaba valido=true (soporte, holgura, caida 2 <= 3)");

        // ---- d2 · descenso de 4 bloques RECHAZADO, columna (0,-1) ----
        BlockPos hondo = new BlockPos(x0, y - 4, z0 - 1);
        for (int dy = -4; dy <= 1; dy++) {
            aire(hondo.getX(), y + dy, hondo.getZ());
        }
        solido(hondo.getX(), y - 5, hondo.getZ());
        boolean pre2 = precondicion("d2", hondo, true, true);
        var d2 = s6fMob.navExecutor().validarDescenso(hondo);
        caso("6f-d2 caida > limite segura RECHAZADA", pre2 && !d2.valido() && d2.rechazo().contains("caida"),
                "rechazo={" + d2.rechazo() + "}", "se esperaba rechazo por caida segura");

        // ---- d3 · aterrizaje BLOQUEADO rechazado, columna (-1,0) ----
        BlockPos ocupado = new BlockPos(x0 - 1, y - 1, z0);
        aire(ocupado.getX(), y - 1, ocupado.getZ());
        solido(ocupado.getX(), ocupado.getY(), ocupado.getZ());
        var d3 = s6fMob.navExecutor().validarDescenso(ocupado);
        boolean pre3 = !nivel.getBlockState(ocupado).isAir();
        caso("6f-d3 aterrizaje bloqueado RECHAZADO", pre3 && !d3.valido() && d3.rechazo().contains("holgura"),
                "pre=" + pre3 + " rechazo={" + d3.rechazo() + "}", "se esperaba rechazo por holgura");

        // ---- d4 · aterrizaje SIN soporte rechazado, columna (0,+1) ----
        BlockPos sinSoporte = new BlockPos(x0, y - 2, z0 + 1);
        for (int dy = -4; dy <= 1; dy++) {
            aire(sinSoporte.getX(), y + dy, sinSoporte.getZ());
        }
        boolean pre4 = precondicion("d4", sinSoporte, true, false);
        var d4 = s6fMob.navExecutor().validarDescenso(sinSoporte);
        caso("6f-d4 aterrizaje sin soporte RECHAZADO", pre4 && !d4.valido() && d4.rechazo().contains("soporte"),
                "pre=" + pre4 + " rechazo={" + d4.rechazo() + "}", "se esperaba rechazo por soporte");

        // ---- d5 · candidato NO descendente: CÁMARA CERRADA (fuerza CONSTRUCCIÓN sin agotar presupuesto) ----
        BlockPos sala = new BlockPos(x0 + 20, y, z0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    aire(sala.getX() + dx, y + dy, sala.getZ() + dz);
                }
                solido(sala.getX() + dx, y - 1, sala.getZ() + dz);
                solido(sala.getX() + dx, y - 2, sala.getZ() + dz);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean borde = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (borde) {
                    for (int dy = 0; dy <= 4; dy++) {
                        solido(sala.getX() + dx, y + dy, sala.getZ() + dz);
                    }
                }
                solido(sala.getX() + dx, y + 4, sala.getZ() + dz);
            }
        }
        // Stage 7W - REPISA del candidato: (x+1, y-1) AIRE, soporte en (x+1, y-2), holgura en y/y+1/y+2 y
        // sin techo sobre esa columna. Unica geometria en la que un candidato de nivel 1 llega a la puerta de
        // descenso con dy = 0 respecto a los pies del ente.
        aire(sala.getX() + 1, y - 1, sala.getZ());
        aire(sala.getX() + 1, y + 4, sala.getZ());
        boolean repisaOk = nivel.getBlockState(new BlockPos(sala.getX() + 1, y - 1, sala.getZ())).isAir()
                && nivel.getBlockState(new BlockPos(sala.getX() + 1, y, sala.getZ())).isAir()
                && nivel.getBlockState(new BlockPos(sala.getX() + 1, y + 1, sala.getZ())).isAir()
                && nivel.getBlockState(new BlockPos(sala.getX() + 1, y + 2, sala.getZ())).isAir()
                && com.chipoodle.devilrpg.entity.nav.NavObstruction.hasSolidFace(nivel,
                        new BlockPos(sala.getX() + 1, y - 2, sala.getZ()));
        if (!repisaOk) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO d5: repisa del candidato no cumple precondiciones en "
                    + (sala.getX() + 1) + "," + (y - 1) + "," + sala.getZ());
        }
        log("STAGE6F d5 candidato=" + (sala.getX() + 1) + "," + (y - 1) + "," + sala.getZ()
                + " dyPies=" + (y - (y - 1 + 1)) + " repisaOk=" + repisaOk);
        s6fMisionPlano = new BlockPos(sala.getX() + 6, y - 3, sala.getZ());
        solido(s6fMisionPlano.getX(), s6fMisionPlano.getY(), s6fMisionPlano.getZ());
        s6fPlano = sonda(sala.getX(), y, sala.getZ());
        if (s6fPlano == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO d5: no se pudo crear el ente de la sala");
        } else {
            s6fPlano.navExecutor().setObjective(s6fMisionPlano);
            log("STAGE6F d5: camara 5x5x3 cerrada en " + sala.toShortString() + " mision solida 3 abajo en "
                    + s6fMisionPlano.toShortString());
        }
        // ---- d6/d7 · ejecución real: el objetivo es el aterrizaje de d1 ----
        s6fMob.navExecutor().setObjective(s6fAterrizaje);
        log("STAGE6F inicio: ente=" + s6fMob.blockPosition().toShortString()
                + " aterrizaje=" + s6fAterrizaje.toShortString() + " objetivo fijado");
    }

    private static void tickStage6f(final MinecraftServer server) {
        s6fTicks++;
        if (!s6fPreparado) {
            prepararStage6f();
            if (s6fMob == null) {
                hecho = true;
                cerrar("INVALIDO", "stage6f: instrumentacion incompleta", server);
            }
            return;
        }
        NavRoute ruta = s6fMob.navExecutor().route();
        int idx = s6fMob.navExecutor().indiceDeRuta();
        if (ruta != null) {
            NavRoute.Node actual = ruta.currentNode(idx);
            if (actual != null && actual.transition() == NavRoute.Transition.DROP) {
                s6fRutaDrop = true;
                // A2 7P · baseline por NODO: la primera observacion de un nodo DROP fija la referencia y NO
                // cuenta como avance; solo se compara la MISMA celda en muestreos posteriores.
                if (!actual.pos().equals(s6fNodoDrop)) {
                    s6fNodoDrop = actual.pos().immutable();
                    s6fIdxBaseline = idx;
                } else if (idx > s6fIdxBaseline && s6fMob.getY() > actual.pos().getY() + 0.2D) {
                    s6fAvanzoAntes = true;
                }
            }
        }
        s6fIdxPrevio = idx;
        if (s6fAterrizaje != null && s6fMob.blockPosition().getY() <= s6fAterrizaje.getY()
                && s6fMob.blockPosition().distToCenterSqr(s6fAterrizaje.getX() + 0.5D,
                s6fAterrizaje.getY(), s6fAterrizaje.getZ() + 0.5D) <= 4.0D) {
            s6fAterrizo = true;
        }
        if (s6fPlano != null && s6fTicks % 20 == 0) {
            String dc = s6fPlano.navExecutor().planConstruccionDiag();
            if (!dc.equals(s6fConstruccionDiag)) {
                s6fConstruccionDiag = dc;
                s6fConstruccionCambios++;
            }
            String est = String.valueOf(s6fPlano.navExecutor().state());
            if (!s6fEstadosVistos.contains(est)) {
                s6fEstadosVistos = s6fEstadosVistos + " " + est;
            }
        }        if (s6fTicks % 100 == 0) {
            log("STAGE6F t=" + s6fTicks + " pos=" + s6fMob.blockPosition().toShortString()
                    + " ejec={" + s6fMob.navExecutor().describe() + "}");
        }
        if (s6fTicks >= 400) {
            hecho = true;
            s6fDiagPlano = s6fPlano == null ? "-" : s6fPlano.navExecutor().descensoDiag();
            s6fCicloConstruccion = s6fDiagPlano.contains("no DESCIENDE");
            boolean sinColocaciones = s6fPlano != null && s6fPlano.navExecutor().celdasConstruidas().isEmpty();
            if (!s6fCicloConstruccion) {
                fallosDeInstrumento++;
                log("FALLO DE INSTRUMENTO d5: el ciclo de construccion no dejo rechazo 'no DESCIENDE' (diag={"
                        + s6fDiagPlano + "})");
            }
            caso("6f-d5 candidato NO descendente RECHAZADO (ciclo de construccion)", s6fCicloConstruccion && sinColocaciones,
                    "rechazoObservado=" + s6fCicloConstruccion + " colocacionesVacias=" + sinColocaciones
                            + " diag={" + s6fDiagPlano + "} construccion={" + s6fConstruccionDiag + "} cambios="+ s6fConstruccionCambios + " estados={" + s6fEstadosVistos + "} colocadas="+ (s6fPlano == null ? -1 : s6fPlano.navExecutor().celdasConstruidas().size()),
                    "se esperaba 'no DESCIENDE' en descensoDiag y celdasConstruidas vacia");
            if (s6fPlano != null) {
                s6fPlano.discard();
            }            boolean pre = s6fRutaDrop;
            if (!pre) {
                fallosDeInstrumento++;
                log("FALLO DE INSTRUMENTO stage6f: la ruta nunca contuvo un nodo DROP");
            }
            caso("6f-d6/DROP no avanza antes del aterrizaje fisico", pre && !s6fAvanzoAntes,
                    "rutaDrop=" + s6fRutaDrop + " avanzoAntes=" + s6fAvanzoAntes, "se esperaba no avanzar antes");
            caso("6f-d7 aterrizaje fisico observado", s6fAterrizo,
                    "aterrizo=" + s6fAterrizo + " pos=" + s6fMob.blockPosition().toShortString(),
                    "se esperaba llegar al aterrizaje");
            s6fMotivo = "d5Ciclo=" + s6fCicloConstruccion + " rutaDrop=" + s6fRutaDrop + " avanzoAntes=" + s6fAvanzoAntes
                    + " aterrizo=" + s6fAterrizo;
            s6fMob.discard();
            String veredicto = (fallosDeInstrumento > 0 || casosOk != casosTotal) ? "INVALIDO" : "COMPLETO";
            cerrar(veredicto, "casos=" + casosOk + "/" + casosTotal
                    + " fallosDeInstrumento=" + fallosDeInstrumento + " | " + s6fMotivo, server);
        }
    }

    // ================================================================================================
    // Modo `stairs` · ACANTILADO (contrato recuperado de LEEME.md §4 y §19)
    // Geometria: llanura con suelo en Y=80 (se pisa en 81), meseta maciza hasta Y=86 (se pisa en 87),
    // cara en X=119 despejada Y81..86, anillo perimetral dz=+-7 / x=+-21 de Y79..Y89. Desnivel = 6.
    // ================================================================================================
    private static AggressiveZombieEntity sStMob;
    private static boolean sStPreparado;
    private static int sStTicks;
    private static BlockPos sStDestino;
    private static int sStColocacionesMax;
    private static int sStTravesadosMax;
    private static int sStParedes;
    private static int sStColocacionesSinBloque;
    private static boolean sStLlego;
    private static boolean sStGeometriaOk;
    private static String sStMotivo = "-";

    private static void prepararStageStairs() {
        sStPreparado = true;
        final int sueloLlanura = 80;
        final int techoMeseta = 86;
        final int bordeX = 120;
        final int caraX = 119;
        final int z0 = -8;
        forzar(bordeX - 24, z0 - 10, bordeX + 24, z0 + 10, sueloLlanura);
        // Llanura: solido en Y=80, aire por encima (se pisa en 81).
        for (int x = bordeX - 22; x < bordeX; x++) {
            for (int z = z0 - 8; z <= z0 + 8; z++) {
                for (int y = sueloLlanura + 1; y <= techoMeseta + 5; y++) {
                    aire(x, y, z);
                }
                solido(x, sueloLlanura, z);
            }
        }
        // Meseta: macizo de Y=80 a Y=86 (se pisa en 87).
        for (int x = bordeX; x <= bordeX + 22; x++) {
            for (int z = z0 - 8; z <= z0 + 8; z++) {
                for (int y = techoMeseta + 1; y <= techoMeseta + 5; y++) {
                    aire(x, y, z);
                }
                for (int y = sueloLlanura; y <= techoMeseta; y++) {
                    solido(x, y, z);
                }
            }
        }
        // Cara del acantilado y labio, despejados (X=119, Y81..86).
        for (int y = sueloLlanura + 1; y <= techoMeseta; y++) {
            for (int z = z0 - 2; z <= z0 + 2; z++) {
                aire(caraX, y, z);
            }
        }
        // Anillo perimetral dz=+-7 / x=+-21, Y79..Y89: la unica salida de la meseta es la cara.
        int paredes = 0;
        for (int y = 79; y <= 89; y++) {
            for (int x = bordeX - 21; x <= bordeX + 21; x++) {
                for (int z = z0 - 7; z <= z0 + 7; z++) {
                    if (z == z0 - 7 || z == z0 + 7 || x == bordeX - 21 || x == bordeX + 21) {
                        solido(x, y, z);
                        paredes++;
                    }
                }
            }
        }
        sStParedes = paredes;
        sStDestino = new BlockPos(110, sueloLlanura + 1, z0);
        // Validacion de la geometria ANTES de arrancar la IA.
        BlockPos spawn = new BlockPos(bordeX, techoMeseta + 1, z0);
        boolean g = nivel.getBlockState(spawn).isAir()
                && nivel.getBlockState(spawn.above()).isAir()
                && !nivel.getBlockState(spawn.below()).isAir()
                && nivel.getBlockState(sStDestino).isAir()
                && !nivel.getBlockState(sStDestino.below()).isAir()
                && (spawn.getY() - sStDestino.getY()) == 6;
        for (int y = sueloLlanura + 1; y <= techoMeseta; y++) {
            g = g && nivel.getBlockState(new BlockPos(caraX, y, z0)).isAir();
        }
        sStGeometriaOk = g;
        log("FIXTURE-ESCALERA GEOMETRIA: llanuraSuelo=" + sueloLlanura + " mesetaTecho=" + techoMeseta
                + " bordeX=" + bordeX + " caraX=" + caraX + " spawn=" + spawn.toShortString()
                + " destino=" + sStDestino.toShortString() + " desnivel=" + (spawn.getY() - sStDestino.getY())
                + " paredes=" + paredes + " geometriaOk=" + g);
        if (!g) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO stairs: la geometria no cumple el contrato documentado");
        }
        sStMob = sonda(spawn.getX(), spawn.getY(), spawn.getZ());
        if (sStMob == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO stairs: sonda nula");
            return;
        }
        sStMob.navExecutor().setObjective(sStDestino);
        log("FIXTURE-ACANTILADO: asaltante=" + sStMob.blockPosition().toShortString()
                + " (y real=" + String.format(java.util.Locale.ROOT, "%.2f", sStMob.getY()) + ")"
                + " destino=" + sStDestino.toShortString());
    }

    private static void tickStageStairs(final MinecraftServer server) {
        sStTicks++;
        if (!sStPreparado) {
            prepararStageStairs();
            if (sStMob == null) {
                hecho = true;
                cerrar("INVALIDO", "stairs: instrumentacion incompleta", server);
            }
            return;
        }
        int colocadas = sStMob.navExecutor().celdasConstruidas().size();
        int travesados = sStMob.navExecutor().escalonesTravesadosDelPlan();
        sStColocacionesMax = Math.max(sStColocacionesMax, colocadas);
        sStTravesadosMax = Math.max(sStTravesadosMax, travesados);
        // Cada celda contabilizada debe EXISTIR en el mundo (colocacion verificada, no intento).
        for (BlockPos c : sStMob.navExecutor().celdasConstruidas()) {
            if (nivel.getBlockState(c).isAir()) {
                sStColocacionesSinBloque++;
                break;
            }
        }
        if (sStMob.blockPosition().distSqr(sStDestino) <= 2.25D) {
            sStLlego = true;
        }
        if (sStTicks % 100 == 0) {
            log("FIXTURE-ESCALERA t=" + sStTicks + " pos=" + sStMob.blockPosition().toShortString()
                    + " colocadas=" + colocadas + " travesados=" + travesados
                    + " ejec={" + sStMob.navExecutor().describe() + "}"
                    + " construccion={" + sStMob.navExecutor().planConstruccionDiag() + "}");
        }
        if (sStTicks >= 1400) {
            hecho = true;
            veredictoStageStairs(server);
        }
    }

    private static void veredictoStageStairs(final MinecraftServer server) {
        int colocadas = sStMob.navExecutor().celdasConstruidas().size();
        int travesados = sStMob.navExecutor().escalonesTravesadosDelPlan();
        caso("stairs-A1 destinoAlcanzado", sStLlego,
                "pos=" + sStMob.blockPosition().toShortString() + " destino=" + sStDestino.toShortString()
                        + " dist=" + String.format(java.util.Locale.ROOT, "%.2f",
                        Math.sqrt(sStMob.blockPosition().distSqr(sStDestino))),
                "se esperaba llegar al destino (<=1.5 bloques)");
        caso("stairs-A2 bloqueColocadoVerificado", sStColocacionesMax >= 1 && sStColocacionesSinBloque == 0,
                "colocadasMax=" + sStColocacionesMax + " sinBloqueEnMundo=" + sStColocacionesSinBloque,
                "se esperaba >=1 colocacion y todas presentes en el mundo");
        caso("stairs-A3 escalonesTravesados>=1", sStTravesadosMax >= 1,
                "travesadosMax=" + sStTravesadosMax, "se esperaba al menos una travesia fisica");
        caso("stairs-A4 colocacionesComprobadasFallo=0", sStColocacionesSinBloque == 0,
                "sinBloqueEnMundo=" + sStColocacionesSinBloque,
                "se esperaba 0 colocaciones contabilizadas sin bloque");
        sStMotivo = "colocadas=" + colocadas + " travesados=" + travesados + " llego=" + sStLlego
                + " geometriaOk=" + sStGeometriaOk + " paredes=" + sStParedes
                + " estado=" + sStMob.navExecutor().state();
        if (sStMob != null) {
            sStMob.discard();
        }
        String veredicto = (fallosDeInstrumento > 0 || casosOk != casosTotal) ? "INVALIDO" : "COMPLETO";
        cerrar(veredicto, "casos=" + casosOk + "/" + casosTotal
                + " fallosDeInstrumento=" + fallosDeInstrumento + " | " + sStMotivo, server);
    }

    // ================================================================================================
    // A2 Stage 7ZB - FIXTURE del futuro modo `construction` (SOLO construccion + validacion de geometria;
    // esta stage NO anade despacho, ni bucle de tick, ni ejecucion de IA, ni marcador).
    //
    // Geometria derivada del fixture `stairs` (LEEME seccion 19) y de la EVIDENCIA de produccion del log
    // `a2n-20261016-184130`, donde el ejecutor coloco realmente escalones en 119,83,-8 y 118,81,-8.
    //   llanura: suelo solido en Y=80 (se pisa en 81)      meseta: macizo hasta Y=86 (se pisa en 87)
    //   cara del acantilado: X=120, despejada Y81..86      spawn 120,87,-8      destino 110,81,-8
    // Dos celdas de construccion CONECTADAS:
    //   paso 1 -> bloque en 119,83,-8, soporte LATERAL en la cara (120,83,-8), aterrizaje 119,84,-8
    //   paso 2 -> bloque en 118,81,-8, soporte DEBAJO en la llanura (118,80,-8), aterrizaje 118,82,-8
    // Secuencia de descenso resultante: 87 -> 84 (paso 1) -> 82 (paso 2) -> 81 (llanura) -> destino.
    // Ninguna de las dos celdas se puede pisar sin construirlas: sin ellas la caida es de 6 bloques.
    // ================================================================================================
    private static final int CON_LLANURA_SUELO = 80;
    private static final int CON_MESETA_TECHO = 86;
    private static final int CON_CARA_X = 120;
    private static final int CON_Z = -8;
    private static BlockPos conSpawn;
    private static BlockPos conDestino;
    private static BlockPos conPaso1;
    private static BlockPos conPaso2;
    private static String conResumen = "-";

    /** Construye SOLO el terreno del fixture multi-paso (no ejecuta IA ni fija objetivos). */
    private static void construirFixtureConstruccion() {
        final int borde = CON_CARA_X;
        final int z0 = CON_Z;
        forzar(borde - 24, z0 - 10, borde + 24, z0 + 10, CON_LLANURA_SUELO);
        // Llanura (x < borde): solido en Y=80 y aire por encima.
        for (int x = borde - 22; x < borde; x++) {
            for (int z = z0 - 8; z <= z0 + 8; z++) {
                for (int y = CON_LLANURA_SUELO + 1; y <= CON_MESETA_TECHO + 5; y++) {
                    aire(x, y, z);
                }
                solido(x, CON_LLANURA_SUELO, z);
            }
        }
        // Meseta (x >= borde): macizo de Y=80 a Y=86.
        for (int x = borde; x <= borde + 22; x++) {
            for (int z = z0 - 8; z <= z0 + 8; z++) {
                for (int y = CON_MESETA_TECHO + 1; y <= CON_MESETA_TECHO + 5; y++) {
                    aire(x, y, z);
                }
                for (int y = CON_LLANURA_SUELO; y <= CON_MESETA_TECHO; y++) {
                    solido(x, y, z);
                }
            }
        }
        // Cara despejada: X=119 (celda del paso 1) y X=120 quedan sin bloques en Y81..Y86.
        for (int y = CON_LLANURA_SUELO + 1; y <= CON_MESETA_TECHO; y++) {
            for (int z = z0 - 2; z <= z0 + 2; z++) {
                aire(borde - 1, y, z);
            }
        }
        // Anillo perimetral (unica salida = la cara), igual que en `stairs`.
        for (int y = 79; y <= 89; y++) {
            for (int x = borde - 21; x <= borde + 21; x++) {
                for (int z = z0 - 7; z <= z0 + 7; z++) {
                    if (z == z0 - 7 || z == z0 + 7 || x == borde - 21 || x == borde + 21) {
                        solido(x, y, z);
                    }
                }
            }
        }
        conSpawn = new BlockPos(borde, CON_MESETA_TECHO + 1, z0);
        conDestino = new BlockPos(110, CON_LLANURA_SUELO + 1, z0);
        conPaso1 = new BlockPos(borde - 1, CON_MESETA_TECHO - 3, z0);
        conPaso2 = new BlockPos(borde - 2, CON_LLANURA_SUELO + 1, z0);
    }

    /**
     * Valida la geometria del fixture multi-paso ANTES de cualquier ejecucion (no ejecuta IA).
     * Comprueba, con el estado REAL del mundo y las mismas condiciones que exige la produccion
     * (aire en celda/+1/+2, soporte con colision debajo o lateral, terreno de pie en los aterrizajes),
     * que las dos celdas candidatas existen, estan conectadas y que no hay atajo.
     */
    private static boolean validarFixtureConstruccion() {
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        // Precondiciones de la llanura y de la meseta.
        boolean llanuraOk = !nivel.getBlockState(conDestino.below()).isAir()
                && nivel.getBlockState(conDestino).isAir()
                && nivel.getBlockState(conDestino.above()).isAir();
        boolean mesetaOk = !nivel.getBlockState(conSpawn.below()).isAir()
                && nivel.getBlockState(conSpawn).isAir()
                && nivel.getBlockState(conSpawn.above()).isAir();
        ok = ok && llanuraOk && mesetaOk;
        sb.append("llanuraOk=").append(llanuraOk).append(" mesetaOk=").append(mesetaOk);
        // Paso 1: celda libre, holgura de pies/cuerpo/cabeza y SOPORTE LATERAL en la cara.
        boolean p1Aire = nivel.getBlockState(conPaso1).isAir()
                && nivel.getBlockState(conPaso1.above()).isAir()
                && nivel.getBlockState(conPaso1.above(2)).isAir();
        boolean p1Lateral = com.chipoodle.devilrpg.entity.nav.NavObstruction.hasSolidFace(nivel,
                conPaso1.offset(1, 0, 0));
        boolean p1Aterrizaje = nivel.getBlockState(new BlockPos(conPaso1.getX(), conPaso1.getY() + 1,
                conPaso1.getZ())).isAir();
        ok = ok && p1Aire && p1Lateral && p1Aterrizaje;
        sb.append(" | paso1=").append(conPaso1.toShortString()).append(" aire=").append(p1Aire)
                .append(" soporteLateralCara=").append(p1Lateral).append(" aterrizaje=").append(p1Aterrizaje);
        // Paso 2: celda libre, holgura y SOPORTE DEBAJO en la llanura.
        boolean p2Aire = nivel.getBlockState(conPaso2).isAir()
                && nivel.getBlockState(conPaso2.above()).isAir()
                && nivel.getBlockState(conPaso2.above(2)).isAir();
        boolean p2Debajo = com.chipoodle.devilrpg.entity.nav.NavObstruction.hasSolidFace(nivel,
                conPaso2.below());
        ok = ok && p2Aire && p2Debajo;
        sb.append(" | paso2=").append(conPaso2.toShortString()).append(" aire=").append(p2Aire)
                .append(" soporteDebajo=").append(p2Debajo);
        // Conexion: cada aterrizaje queda por encima del siguiente y la secuencia desciende hasta el destino.
        int y1 = conPaso1.getY() + 1;
        int y2 = conPaso2.getY() + 1;
        boolean conectado = conSpawn.getY() > y1 && y1 > y2 && y2 > conDestino.getY();
        ok = ok && conectado;
        sb.append(" | secuencia=").append(conSpawn.getY()).append('>').append(y1).append('>').append(y2)
                .append('>').append(conDestino.getY()).append(" conectado=").append(conectado);
        // Sin atajo: la caida directa meseta->llanura supera la caida segura y no hay repisas intermedias.
        int caidaDirecta = conSpawn.getY() - conDestino.getY();
        boolean sinAtajo = caidaDirecta > 3;
        ok = ok && sinAtajo;
        sb.append(" | caidaDirecta=").append(caidaDirecta).append(" sinAtajo=").append(sinAtajo);
        // Chunks cargados.
        boolean chunks = nivel.hasChunkAt(conSpawn) && nivel.hasChunkAt(conDestino)
                && nivel.hasChunkAt(conPaso1) && nivel.hasChunkAt(conPaso2);
        ok = ok && chunks;
        sb.append(" | chunks=").append(chunks);
        conResumen = sb.toString();
        log("FIXTURE-CONSTRUCCION GEOMETRIA: spawn=" + conSpawn.toShortString()
                + " destino=" + conDestino.toShortString() + " " + conResumen + " OK=" + ok);
        return ok;
    }

    // ================================================================================================
    // A2 Stage 7ZC - modo `construction`: bucle de ejecucion sobre el fixture de Stage 7ZB.
    // NO fuerza celdas: mide lo que el ejecutor de produccion haga realmente. C4 es SOLO evidencia por
    // muestreo (la maquina privada `faseEscalon` no expone evento publico de autorizacion).
    // ================================================================================================
    private static AggressiveZombieEntity conMob;
    private static boolean conPreparado;
    private static int conTicks;
    private static int conColocadasMax;
    private static int conTravesadosMax;
    private static int conVerificadasFallo;
    private static boolean conVioColocadaConCeroTravesias;
    private static boolean conDosColocadasConCeroTravesias;
    private static boolean conLlego;
    private static double conSaludMin;
    private static String conMotivo = "-";
    private static final StringBuilder conEventos = new StringBuilder();

    private static void prepararConstruction() {
        conPreparado = true;
        construirFixtureConstruccion();
        boolean geo = validarFixtureConstruccion();
        if (!geo) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO construction: validarFixtureConstruccion() = false");
            return;
        }
        conMob = sonda(conSpawn.getX(), conSpawn.getY(), conSpawn.getZ());
        if (conMob == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO construction: sonda nula");
            return;
        }
        conSaludMin = conMob.getHealth();
        conMob.navExecutor().setObjective(conDestino);
        log("FIXTURE-CONSTRUCCION INICIO: spawn=" + conSpawn.toShortString()
                + " destino=" + conDestino.toShortString()
                + " esperadas=" + conPaso1.toShortString() + " y " + conPaso2.toShortString()
                + " (expectativa historica, NO garantia)");
    }

    private static void tickConstruction(final MinecraftServer server) {
        conTicks++;
        if (!conPreparado) {
            prepararConstruction();
            if (conMob == null) {
                hecho = true;
                cerrar("INVALIDO", "construction: fixture o sonda invalidos", server);
            }
            return;
        }
        int colocadas = conMob.navExecutor().celdasConstruidas().size();
        int travesados = conMob.navExecutor().escalonesTravesadosDelPlan();
        conSaludMin = Math.min(conSaludMin, conMob.getHealth());
        if (colocadas > conColocadasMax) {
            conColocadasMax = colocadas;
            for (BlockPos c : conMob.navExecutor().celdasConstruidas()) {
                boolean enMundo = !nivel.getBlockState(c).isAir();
                if (!enMundo) {
                    conVerificadasFallo++;
                }
            }
            conEventos.append("COLOCADA t=").append(conTicks).append(" n=").append(colocadas)
                    .append(" travesados=").append(travesados)
                    .append(" celdas=").append(conMob.navExecutor().celdasConstruidas())
                    .append(" estado=").append(conMob.navExecutor().state()).append(" | ");
            log("FIXTURE-CONSTRUCCION COLOCACION t=" + conTicks + " colocadas=" + colocadas
                    + " travesados=" + travesados
                    + " celdas=" + conMob.navExecutor().celdasConstruidas()
                    + " estado=" + conMob.navExecutor().state()
                    + " construccion={" + conMob.navExecutor().planConstruccionDiag() + "}");
        }
        if (travesados > conTravesadosMax) {
            conTravesadosMax = travesados;
            conEventos.append("TRAVESIA t=").append(conTicks).append(" n=").append(travesados)
                    .append(" colocadas=").append(colocadas).append(" | ");
            log("FIXTURE-CONSTRUCCION TRAVESIA t=" + conTicks + " travesados=" + travesados
                    + " colocadas=" + colocadas + " pos=" + conMob.blockPosition().toShortString());
        }
        // C3/C4 (muestreo): colocacion existente con travesias a cero, y nunca 2 colocadas sin ninguna travesia.
        if (colocadas >= 1 && travesados == 0) {
            conVioColocadaConCeroTravesias = true;
        }
        if (colocadas >= 2 && travesados == 0) {
            conDosColocadasConCeroTravesias = true;
        }
        if (conMob.blockPosition().distSqr(conDestino) <= 2.25D) {
            conLlego = true;
        }
        if (conTicks % 100 == 0) {
            log("FIXTURE-CONSTRUCCION t=" + conTicks + " pos=" + conMob.blockPosition().toShortString()
                    + " colocadas=" + colocadas + " travesados=" + travesados
                    + " salud=" + conMob.getHealth() + " estado=" + conMob.navExecutor().state()
                    + " ejec={" + conMob.navExecutor().describe() + "}");
        }
        if (conTicks >= 1600) {
            hecho = true;
            veredictoConstruction(server);
        }
    }

    private static void veredictoConstruction(final MinecraftServer server) {
        var ej = conMob.navExecutor();
        int colocadas = ej.celdasConstruidas().size();
        int travesados = ej.escalonesTravesadosDelPlan();
        boolean llegadaEstado = ej.state() == com.chipoodle.devilrpg.entity.nav.NavState.ARRIVED;
        caso("C1 >=2 pasos de construccion conectados", conColocadasMax >= 2,
                "colocadasMax=" + conColocadasMax + " celdas=" + ej.celdasConstruidas(),
                "se esperaban >=2 celdas verificadas y conectadas");
        caso("C2 cada celda contabilizada EXISTE en el mundo", conColocadasMax >= 1 && conVerificadasFallo == 0,
                "colocadasMax=" + conColocadasMax + " sinBloque=" + conVerificadasFallo,
                "se esperaba >=1 colocacion y 0 celdas sin bloque");
        caso("C3 colocar NO cuenta como travesar (evidencia por muestreo)", conVioColocadaConCeroTravesias,
                "vioColocadaConCeroTravesias=" + conVioColocadaConCeroTravesias,
                "se esperaba observar colocadas>=1 con travesados==0");
        caso("C4 orden de dependencia (SOLO muestreo, sin evento publico)", conColocadasMax >= 2 && !conDosColocadasConCeroTravesias,
                "colocadasMax=" + conColocadasMax + " dosSinTravesia=" + conDosColocadasConCeroTravesias,
                "nunca 2 colocadas con 0 travesias");
        caso("C5 travesia fisica confirmada por produccion", conTravesadosMax >= 2,
                "travesadosMax=" + conTravesadosMax, "se esperaban >=2 travesias fisicas");
        caso("C6 sin dano ni caida insegura", conSaludMin >= conMob.getMaxHealth(),
                "saludMin=" + conSaludMin + " saludMax=" + conMob.getMaxHealth(),
                "se esperaba salud intacta");
        caso("C7 llegada acotada al destino", llegadaEstado && conLlego,
                "estado=" + ej.state() + " llego=" + conLlego + " pos=" + conMob.blockPosition().toShortString()
                        + " dist=" + String.format(java.util.Locale.ROOT, "%.2f",
                        Math.sqrt(conMob.blockPosition().distSqr(conDestino))),
                "se esperaba ARRIVED y posicion en el destino");
        conMotivo = "colocadas=" + colocadas + " travesados=" + travesados
                + " estado=" + ej.state() + " saludMin=" + conSaludMin
                + " pos=" + conMob.blockPosition().toShortString()
                + " construccion={" + ej.planConstruccionDiag() + "}"
                + " eventos={" + conEventos + "}";
        conMob.discard();
        String veredicto = (fallosDeInstrumento > 0 || casosOk != casosTotal) ? "INVALIDO" : "COMPLETO";
        cerrar(veredicto, "casos=" + casosOk + "/" + casosTotal
                + " fallosDeInstrumento=" + fallosDeInstrumento + " | " + conMotivo, server);
    }

    // ================================================================================================
    // A2 Stage 7ZF - modo `longrange`: navegacion integrada acantilado -> destino MAS ALLA.
    // Reutiliza el fixture validado de `construction` (misma meseta/cara/llanura) y mueve el destino a
    // 103,81,-8, es decir 17 bloques mas alla de la cara: no esta sobre el obstaculo.
    // LIMITE: `unreachable` NO se implementa en esta stage.
    // ================================================================================================
    private static AggressiveZombieEntity lrMob;
    private static boolean lrPreparado;
    private static int lrTicks;
    private static BlockPos lrDestino;
    private static int lrColocadasMax;
    private static int lrTravesadosMax;
    private static int lrVerificadasFallo;
    private static boolean lrLlego;
    private static boolean lrColocadaSinTravesia;
    private static double lrSaludMin;
    private static int lrPlanes;
    private static String lrMotivo = "-";
    private static final StringBuilder lrEventos = new StringBuilder();

    // ================================================================================================
    // A2 raid amurallado (RESTAURACION en curso - Edit 1). Solo estado reservado; el fixture,
    // el spawn, el muestreo por tick, las fases y los marcadores se anaden en edits posteriores.
    // ================================================================================================
    private static final int R_ASALTANTES = 8;
    private static final int R_RADIO_ASALTO = 95;
    private static final int R_RADIO_ALDEA = 71;
    private static final int R_RADIO_ANILLO = 62;
    private static final int R_ALTO_ANILLO = 3;
    // El muro de producción tiene dos capas continuas; las columnas de piedra añaden una tercera.
    // C4 valida el cierre continuo de dos capas, sin exigir que cada tramo de troncos sea una columna.
    private static final int R_ALTURA_MURO_VALIDADA = 2;
    /** Edit 10: contadores de la FUNDACION bajo el anillo (cierre del hueco vertical de Edit 9). */
    private static int rFundBloques;
    private static int rFundMin = Integer.MAX_VALUE;
    private static int rFundMax;
    private static int rFundOk;
    private static int rFundSinSoporte;
    private static int rTicks;
    private static final java.util.List<AggressiveZombieEntity> rAsaltantes = new java.util.ArrayList<>();
    private static final StringBuilder rInforme = new StringBuilder();

    // ================================================================================================
    // A2 raid amurallado - Edit 2: FIXTURE y preparacion de geometria (sin spawn, sin fases, sin marcadores
    // de resultado; eso llega en Edit 3+). Centro documentado del escenario: 661,71,533.
    // ================================================================================================
    private static boolean rPreparado;
    private static boolean rFixtureOk;
    private static int rCeldasAnillo;
    private static BlockPos rCentro;
    private static BlockPos rAncla;
    // ---- Edit 3: spawn de asaltantes + configuracion efectiva de navegacion (sin fases) ----
    private static int rSpawneados;
    private static int rFallosSpawn;
    private static boolean rIniciado;
    private static boolean rRegistroAsedioPendiente;
    private static int rRegistroAsedioDesdeTick;
    private static int rRegistroAsedioIntentos;
    private static String rNavConfig = "-";

    private static void prepararRaidAsaltantes(final MinecraftServer server) {
        var nivelR = server.overworld();
        // Configuracion EFECTIVA de navegacion custom: entradas documentadas por NavFeature.
        String prop = System.getProperty(com.chipoodle.devilrpg.entity.nav.NavFeature.PROPERTY);
        String env = System.getenv(com.chipoodle.devilrpg.entity.nav.NavFeature.ENV);
        boolean efectivo = com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation();
        rNavConfig = "propiedad=" + com.chipoodle.devilrpg.entity.nav.NavFeature.PROPERTY + "=" + prop
                + " env=" + com.chipoodle.devilrpg.entity.nav.NavFeature.ENV + "=" + env
                + " navCustomEfectivo=" + com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation();
        log("NAVCUSTOM " + rNavConfig);
        // Edit 32 · los puntos de asalto deben estar en SUPERFICIE real, no en una plataforma
        // artificial dentro de una montaña. La ventana vertical admite hasta 80 bloques sobre el centro.
        final int spawnYMin = rAncla.getY() - 8;
        final int spawnYMax = rAncla.getY() + 80;
        // Edit 30 · posiciones ya elegidas, para exigir columnas DISTINTAS entre direcciones.
        final java.util.Set<Long> usadas = new java.util.HashSet<>();
        for (int i = 1; i <= R_ASALTANTES; i++) {
            double ang = i * Math.PI * 2.0D / R_ASALTANTES;
            int nomX = rAncla.getX() + (int) Math.round(Math.cos(ang) * R_RADIO_ASALTO);
            int nomZ = rAncla.getZ() + (int) Math.round(Math.sin(ang) * R_RADIO_ASALTO);
            // Edit 29 · SPAWN ADAPTATIVO: si la columna nominal no es colocable, se prueba en la MISMA
            // direccion radial un radio vecino (offsets 0,+-4,+-8,+-12,+-16), determinista y acotado.
            // Se reutiliza el MISMO test (suelo con colision + 2 bloques de holgura): no se relaja nada.
            int sx = nomX;
            int sz = nomZ;
            int sy = -1;
            int conSuelo = 0;
            int offElegido = 0;
            final int[] offsSpawn = {0, 4, -4, 8, -8, 12, -12, 16, -16};
            for (int off : offsSpawn) {
                int radio = R_RADIO_ASALTO + off;
                int csx = rAncla.getX() + (int) Math.round(Math.cos(ang) * radio);
                int csz = rAncla.getZ() + (int) Math.round(Math.sin(ang) * radio);
                int csy = -1;
                int cConSuelo = 0;
                for (int prueba = spawnYMax; prueba >= spawnYMin; prueba--) {
                    if (nivelR.getBlockState(new BlockPos(csx, prueba - 1, csz)).isAir()) {
                        continue;
                    }
                    cConSuelo++;
                    if (nivelR.getBlockState(new BlockPos(csx, prueba, csz)).isAir()
                            && nivelR.getBlockState(new BlockPos(csx, prueba + 1, csz)).isAir()) {
                        csy = prueba;
                        break;
                    }
                }
                if (csy >= 0) {
                    sx = csx;
                    sz = csz;
                    sy = csy;
                    conSuelo = cConSuelo;
                    offElegido = off;
                    break;
                }
                if (off == 0) {
                    conSuelo = cConSuelo;
                }
            }
            // Edit 30 · SI EL RADIAL FALLA: busqueda LATERAL (perpendicular a la direccion nominal),
            // acotada a +-16 y en orden fijo. Mismo predicado de suelo+holgura y exigencia de columna
            // distinta (usadas). No se acepta nada dentro del muro: el radio se mantiene 95 +- 0.
            int lateralElegido = 0;
            if (sy < 0) {
                final int[] offsLat = {4, -4, 8, -8, 12, -12, 16, -16};
                for (int lat : offsLat) {
                    int csx = nomX + (int) Math.round(-Math.sin(ang) * lat);
                    int csz = nomZ + (int) Math.round(Math.cos(ang) * lat);
                    if (usadas.contains(((long) csx << 32) ^ (csz & 0xffffffffL))) {
                        continue;
                    }
                    int csy = -1;
                    for (int prueba = spawnYMax; prueba >= spawnYMin; prueba--) {
                        if (nivelR.getBlockState(new BlockPos(csx, prueba - 1, csz)).isAir()) {
                            continue;
                        }
                        if (nivelR.getBlockState(new BlockPos(csx, prueba, csz)).isAir()
                                && nivelR.getBlockState(new BlockPos(csx, prueba + 1, csz)).isAir()) {
                            csy = prueba;
                            break;
                        }
                    }
                    if (csy >= 0) {
                        sx = csx;
                        sz = csz;
                        sy = csy;
                        lateralElegido = lat;
                        break;
                    }
                }
            }
            if (lateralElegido != 0) {
                log("SPAWN idx=" + i + " nominal=" + nomX + "," + nomZ + " ajustadoLATERAL=" + sx + "," + sz
                        + " offsetLateral=" + lateralElegido + " (perpendicular a la direccion; holgura intacta)");
            }
            if (offElegido != 0) {
                log("SPAWN idx=" + i + " nominal=" + nomX + "," + nomZ + " ajustado=" + sx + "," + sz
                        + " offsetRadial=" + offElegido + " (misma direccion; holgura intacta)");
            }
            // No fabricar una plataforma de una sola columna si no se encontro superficie real.
            // Ese fallback anterior podia colocar al atacante dentro de roca maciza y falsear A2.
            if (sy < 0) {
                log("SPAWN idx=" + i + " sin columna de superficie transitable; no se fabrica plataforma"
                        + " nominal=" + nomX + "," + nomZ + " ventanaY=" + spawnYMin + ".." + spawnYMax);
            }
            if (sy >= 0 && usadas.contains(((long) sx << 32) ^ (sz & 0xffffffffL))) {
                // Edit 30: columna duplicada -> no se acepta (8 posiciones distintas).
                rFallosSpawn++;
                log("SPAWN idx=" + i + " pos=" + sx + "," + sy + "," + sz
                        + " ok=false motivo=POSICION_DUPLICADA");
                continue;
            }
            if (sy >= 0) {
                usadas.add(((long) sx << 32) ^ (sz & 0xffffffffL));
            }
            if (sy < 0) {
                rFallosSpawn++;
                String motivo = conSuelo == 0 ? "NO_SUITABLE_GROUND" : "INSUFFICIENT_CLEARANCE";
                log("SPAWN idx=" + i + " pos=" + sx + ",?," + sz + " ok=false motivo=" + motivo
                        + " columnasConSuelo=" + conSuelo
                        + " ventanaY=" + spawnYMin + ".." + spawnYMax);
                continue;
            }
            BlockPos pos = new BlockPos(sx, sy, sz);
            AggressiveZombieEntity asaltante = com.chipoodle.devilrpg.init.ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(nivelR, null, pos, net.minecraft.world.entity.MobSpawnType.COMMAND, false, false);
            boolean ok = false;
            if (asaltante != null) {
                asaltante.moveTo(sx + 0.5D, sy, sz + 0.5D, 0.0F, 0.0F);
                // Mismo contrato que VillageManager.spawnWave: el centro habilita MoveToVillageCenterGoal.
                asaltante.setVillageCenter(rAncla);
                asaltante.setGoToCenterActive(true);
                asaltante.setPersistenceRequired();
                ok = nivelR.addFreshEntity(asaltante);
                if (ok) {
                    // El índice se escribe tras insertar, igual que en la oleada de producción.
                    // Con índice 0 no se despawnea sin jugador y las bajas/brechas se atribuyen a esta aldea.
                    asaltante.setWorldSiegeIndex(0);
                    asaltante.navExecutor().setObjective(rAncla);
                    rAsaltantes.add(asaltante);
                    rSpawneados++;
                } else {
                    rFallosSpawn++;
                }
            } else {
                rFallosSpawn++;
            }
            log("SPAWN idx=" + i + " pos=" + pos.toShortString() + " ok=" + ok
                    + " entidad=" + (asaltante == null ? "-" : (asaltante.getUUID() + " vivo=" + asaltante.isAlive()))
                    + " objetivo=" + rAncla.toShortString() + " asaltantesConocidos=" + rAsaltantes.size());
        }
        rIniciado = true;
        log("SPAWN TOTAL: intentos=" + R_ASALTANTES + " ok=" + rSpawneados + " fallos=" + rFallosSpawn
                + " radio=" + R_RADIO_ASALTO + " ancla=" + rAncla.toShortString());
        if (rSpawneados != R_ASALTANTES) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO raid: spawneados=" + rSpawneados + "/" + R_ASALTANTES);
        } else {
            // addFreshEntity puede completar el alta en el nivel durante este mismo tick, pero el lookup por UUID
            // del ServerLevel no siempre queda visible hasta el siguiente tick. No registrar una ola parcial:
            // esperar y validar las ocho entidades antes de activar C4.
            rRegistroAsedioPendiente = true;
            rRegistroAsedioDesdeTick = rTicks + 1;
            rRegistroAsedioIntentos = 0;
            log("C4 TEST SIEGE registration deferred until next server tick; attackers=" + rAsaltantes.size());
        }
    }

    /**
     * Recursos de trabajo acotados para la fase C4 de reanudacion. El arnes no debe exigir que un Goal se ejecute
     * cuando su oficio no tiene materia prima: entrega una tarea real y pequena a cocinero, herrero de armas y
     * pescador. Solo modifica el mundo desechable de prueba y registra cantidades antes/despues.
     */
    private static void prepararRecursosC4Recovery(final ServerLevel level, final BlockPos center) {
        int carneAntes = com.chipoodle.devilrpg.world.VillagePantry.contar(
                com.chipoodle.devilrpg.world.VillagePantry.despensa(level, center),
                com.chipoodle.devilrpg.world.VillagePantry::sePuedeCocinar);
        net.minecraft.world.item.ItemStack carne = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.BEEF, 4);
        net.minecraft.world.item.ItemStack carneResto = com.chipoodle.devilrpg.world.VillagePantry.guardar(
                com.chipoodle.devilrpg.world.VillagePantry.despensa(level, center), carne);
        int carneAgregada = 4 - carneResto.getCount();

        int lingotesAntes = com.chipoodle.devilrpg.world.VillagePantry.contar(
                com.chipoodle.devilrpg.world.VillageStorage.almacen(level, center),
                stack -> stack.is(net.minecraft.world.item.Items.IRON_INGOT));
        int hierroAgregado = guardarRecursoC4(level, center, net.minecraft.world.item.Items.IRON_INGOT, 2);
        int tablonesAgregados = guardarRecursoC4(level, center, net.minecraft.world.item.Items.OAK_PLANKS, 6);
        int palosAgregados = guardarRecursoC4(level, center, net.minecraft.world.item.Items.STICK, 3);
        int cuerdasAgregadas = guardarRecursoC4(level, center, net.minecraft.world.item.Items.STRING, 3);
        int plumasAgregadas = guardarRecursoC4(level, center, net.minecraft.world.item.Items.FEATHER, 1);
        int pepitasAgregadas = guardarRecursoC4(level, center, net.minecraft.world.item.Items.IRON_NUGGET, 1);

        int pecesAntes = com.chipoodle.devilrpg.world.VillageGenerator.pecesEnElLago(level, center);
        int pecesAgregados = 0;
        for (int i = 0; i < 3; i++) {
            int agregados = com.chipoodle.devilrpg.world.VillageGenerator.reponerPecesDelLago(level, center);
            pecesAgregados += agregados;
            if (agregados <= 0) {
                break;
            }
        }
        int pecesDespues = com.chipoodle.devilrpg.world.VillageGenerator.pecesEnElLago(level, center);
        log("C4 TEST RESOURCES butcherRaw=" + carneAgregada + " pantryRawBefore=" + carneAntes
                + " smithIron=" + hierroAgregado + " ironBefore=" + lingotesAntes
                + " planks=" + tablonesAgregados + " sticks=" + palosAgregados
                + " string=" + cuerdasAgregadas + " feathers=" + plumasAgregadas + " nuggets=" + pepitasAgregadas
                + " fishBefore=" + pecesAntes + " fishAdded=" + pecesAgregados + " fishAfter=" + pecesDespues);
    }

    private static int guardarRecursoC4(final ServerLevel level, final BlockPos center,
            final net.minecraft.world.item.Item item, final int count) {
        net.minecraft.world.item.ItemStack rest = com.chipoodle.devilrpg.world.VillageStorage.guardar(
                level, center, new net.minecraft.world.item.ItemStack(item, count));
        return count - rest.getCount();
    }

    private static void registrarAsedioPendiente(final MinecraftServer server) {
        if (!rRegistroAsedioPendiente) {
            return;
        }
        java.util.List<java.util.UUID> attackerIds = new java.util.ArrayList<>();
        java.util.List<String> pendientes = new java.util.ArrayList<>();
        ServerLevel level = server.overworld();
        for (AggressiveZombieEntity instance : rAsaltantes) {
            if (instance == null) {
                pendientes.add("null-instance");
                continue;
            }
            java.util.UUID uuid = instance.getUUID();
            net.minecraft.world.entity.Entity resolved = level.getEntity(uuid);
            if (resolved instanceof AggressiveZombieEntity attacker) {
                int indexBefore = attacker.getWorldSiegeIndex();
                if (indexBefore != 0) {
                    attacker.setWorldSiegeIndex(0);
                }
                attackerIds.add(uuid);
                log("C4 TEST SIEGE resolve uuid=" + uuid + " instanceRemoved=" + instance.isRemoved()
                        + " instanceAlive=" + instance.isAlive() + " indexBefore=" + indexBefore
                        + " indexAfter=" + attacker.getWorldSiegeIndex() + " resolved=true");
            } else {
                pendientes.add(uuid + "(instanceRemoved=" + instance.isRemoved()
                        + ",alive=" + instance.isAlive() + ",instanceIndex=" + instance.getWorldSiegeIndex()
                        + ",resolved=" + (resolved == null ? "null" : resolved.getClass().getSimpleName()) + ")");
            }
        }
        if (attackerIds.size() == R_ASALTANTES) {
            try {
                com.chipoodle.devilrpg.world.VillageManager.iniciarAsedioParaArnes(
                        level, 0, rCentro, attackerIds);
                rRegistroAsedioPendiente = false;
                log("C4 TEST SIEGE registration complete attempts=" + (rRegistroAsedioIntentos + 1)
                        + " attackers=" + attackerIds.size());
            } catch (RuntimeException ex) {
                rRegistroAsedioPendiente = false;
                fallosDeInstrumento++;
                log("C4 TEST SIEGE ERROR after resolution: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            return;
        }
        rRegistroAsedioIntentos++;
        if (rRegistroAsedioIntentos >= 20) {
            rRegistroAsedioPendiente = false;
            fallosDeInstrumento++;
            log("C4 TEST SIEGE ERROR: UUID lookup unresolved after " + rRegistroAsedioIntentos
                    + " ticks; resolved=" + attackerIds.size() + "/" + R_ASALTANTES
                    + " pending=" + pendientes);
        } else {
            log("C4 TEST SIEGE pending attempt=" + rRegistroAsedioIntentos + " resolved="
                    + attackerIds.size() + "/" + R_ASALTANTES + " pending=" + pendientes);
        }
    }

    // ================================================================================================
    // A2 raid amurallado - Edit 4: muestreo acotado por tick y fases explicitas (A: 1..faseA,
    // B: faseA+1..faseB). SIN totales, SIN veredictos, SIN integridad de pared (eso es Edit 5).
    // ================================================================================================
    private static final int R_FASE_A = Integer.getInteger("a2n.faseA", 3000);
    private static final int R_FASE_B = Integer.getInteger("a2n.faseB", 6600);
    private static final int R_RECUPERACION_TICKS = Integer.getInteger("a2n.recuperacionTicks", 1200);
    private static boolean rPostRaidIniciado;
    private static int rPostRaidTicks;
    private static int rRetiradosPorArnes;
    private static final boolean[] rEntroPerimetro = new boolean[R_ASALTANTES];
    private static final boolean[] rDentroParedAhora = new boolean[R_ASALTANTES];
    private static final boolean[] rMuertoRegistrado = new boolean[R_ASALTANTES];
    /** Edit 8: ultima distancia horizontal observada por asaltante (-1 = NO MEDIDO aun). */
    private static final double[] rDistPrevia = {-1.0D, -1.0D, -1.0D, -1.0D, -1.0D, -1.0D, -1.0D, -1.0D};
    private static int rVivos;
    private static int rMuertos;
    private static int rDesaparecidos;
    private static int rDentro;

    private static int cuentaEntraronPerimetro() {
        int n = 0;
        for (boolean b : rEntroPerimetro) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    private static String dos(final double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ================================================================================================
    // A2 raid amurallado - Edit 5: informes de final de fase derivados de estado MEDIDO.
    // No se inventan umbrales, brechas, retiradas ni ceros: lo no medible se declara NO MEDIDO.
    // ================================================================================================
    private static int rCeldasAnilloAhora;
    private static int rAnilloEsperadas;
    private static int rAnilloSinSuelo;
    private static int rAnilloNoSolidas;
    private static int rAnilloDesplazadas;
    /** Base VALIDADA por columna al construir (clave = (dx,dz) empaquetado -> y de la base). */
    private static final java.util.Map<Long, Integer> rBaseValidada = new java.util.HashMap<>();

    private static long claveColumna(final int dx, final int dz) {
        return ((long) dx << 32) ^ (dz & 0xffffffffL);
    }

    /**
     * REGLA DE SUELO compartida (construccion y verificacion): bloque NO aire mas alto de la vertical
     * [cy-32..cy+32] que ademas (1) no es fluido y (2) tiene forma de colision. Devuelve Integer.MIN_VALUE
     * si no hay suelo aceptable. Usar SIEMPRE esta regla en ambos lados evita divergencias sutiles.
     */
    private static int sueloDeColumna(final net.minecraft.server.level.ServerLevel nivelR,
                                      final int x, final int z, final int cy) {
        for (int y = cy + 32; y >= cy - 32; y--) {
            BlockPos p = new BlockPos(x, y, z);
            var st = nivelR.getBlockState(p);
            if (st.isAir() || !st.getFluidState().isEmpty()
                    || st.getCollisionShape(nivelR, p).isEmpty()) {
                continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Recuenta el anillo APOYADO EN EL TERRENO en el instante de la fase (integridad real, misma regla
     * que la construccion). Distingue: celdas del muro ausentes, columnas sin suelo identificable y
     * columnas no verificables. NO cuenta una altura distinta como "muro intacto".
     */
    private static int integridadAnillo(final MinecraftServer server) {
        var nivelR = server.overworld();
        int presentes = 0;
        rAnilloEsperadas = 0;
        rAnilloSinSuelo = 0;
        rAnilloNoSolidas = 0;
        rAnilloDesplazadas = 0;
        if (rBaseValidada.isEmpty()) {
            // No hay baseline (no se construyo): no se puede afirmar nada sobre el muro.
            return 0;
        }
        for (var entrada : rBaseValidada.entrySet()) {
            int dx = (int) (entrada.getKey() >> 32);
            int dz = (int) (long) entrada.getKey();
            int baseOriginal = entrada.getValue();
            rAnilloEsperadas += R_ALTURA_MURO_VALIDADA;
            // NO se re-deriva el suelo: sueloDeColumna() veria la cima del muro como "terreno" y marcaria
            // todas las columnas como desplazadas. Se comprueba el soporte REAL bajo la base registrada.
            if (nivelR.getBlockState(new BlockPos(rAncla.getX() + dx, baseOriginal - 1,
                    rAncla.getZ() + dz)).isAir()) {
                // El terreno de apoyo desaparecio: columna no verificable (no se cuenta como intacta).
                rAnilloSinSuelo++;
                continue;
            }
            boolean columnaOk = true;
            for (int dy = 0; dy < R_ALTURA_MURO_VALIDADA; dy++) {
                if (nivelR.getBlockState(new BlockPos(rAncla.getX() + dx, baseOriginal + dy,
                        rAncla.getZ() + dz)).isAir()) {
                    columnaOk = false;
                }
            }
            if (columnaOk) {
                presentes += R_ALTURA_MURO_VALIDADA;
            } else {
                rAnilloNoSolidas++;
            }
        }
        return presentes;
    }

    // ================================================================================================
    // Edit 11(a) - TRACE DE NAVEGACION (solo arnes; apagado por defecto con -Ddevilrpg.a2n.traceNav=true).
    // Escribe un artefacto INDEPENDIENTE en build\a2n\<runId>\nav-trace.log. No toca produccion, no
    // escanea el mundo y no recalcula decisiones: solo observa lo que la API publica ya expone.
    // ================================================================================================
    private static final boolean TRACE_NAV = Boolean.getBoolean("devilrpg.a2n.traceNav");
    private static java.io.BufferedWriter trOut;
    private static long trRecords;
    private static long trDropped;
    private static long trFallos;
    private static final double[] trLastX = new double[R_ASALTANTES];
    private static final double[] trLastZ = new double[R_ASALTANTES];
    private static final String[] trLastEstado = new String[R_ASALTANTES];
    private static final int[] trCuenta = new int[R_ASALTANTES];
    private static final int[] trPrimerTick = new int[R_ASALTANTES];
    private static final int[] trUltimoTick = new int[R_ASALTANTES];

    private static void traceInit() {
        if (!TRACE_NAV || trOut != null) {
            return;
        }
        try {
            String run = System.getProperty("a2n.run", "sin-run");
            // Edit 32 · FIX de ruta: el proceso del juego corre con CWD en el directorio del run, asi que
            // "build/a2n/<run>" relativo apuntaba a <run>/game/build/... y el fichero nunca aparecia donde
            // se buscaba. Se resuelve contra a2n.raiz (raiz del repo), igual que cerrar() en L2347.
            java.nio.file.Path raizTrace = java.nio.file.Path.of(System.getProperty("a2n.raiz", "."));
            java.nio.file.Path dir = raizTrace.resolve("build").resolve("a2n").resolve(run);
            java.nio.file.Files.createDirectories(dir);
            trOut = java.nio.file.Files.newBufferedWriter(dir.resolve("nav-trace.log"));
            trOut.write("# schema=a2n-navtrace/1 run=" + run + " traceNav=" + TRACE_NAV
                    + " navCustom=" + com.chipoodle.devilrpg.entity.nav.NavFeature.customNavigation() + "\n");
            trOut.write("# cols=tickServidor,raidTick,idx,uuid,tipo,x,y,z,bbox,objetivo,dist2D,dist3D,movio,estado,cambioEstado,rutaAccion,descenso\n");
            log("TRACE NAV habilitado: " + dir.resolve("nav-trace.log"));
            // Edit 11(b): puente produccion -> este artefacto. NavTrace no hace nada si la bandera esta
            // apagada; aqui solo instalamos el consumidor cuando la traza esta activa.
            com.chipoodle.devilrpg.entity.nav.NavTrace.sink(linea -> {
                try {
                    trOut.write("EVT " + linea + "\n");
                    trRecords++;
                } catch (RuntimeException | java.io.IOException e) {
                    trFallos++;
                }
            });
        } catch (RuntimeException | java.io.IOException e) {
            trFallos++;
            log("FALLO DE INSTRUMENTO traceNav: no se pudo abrir el artefacto: " + e);
            trOut = null;
        }
    }

    private static void traceTick(final MinecraftServer server) {
        if (!TRACE_NAV || trOut == null) {
            return;
        }
        final int n = Math.min(rAsaltantes.size(), R_ASALTANTES);
        final long serverTick = server.overworld().getGameTime();
        for (int k = 0; k < n; k++) {
            AggressiveZombieEntity a = rAsaltantes.get(k);
            if (a == null || a.isRemoved()) {
                continue;
            }
            try {
                double x = a.getX();
                double z = a.getZ();
                double dx = x - rAncla.getX();
                double dz = z - rAncla.getZ();
                double d2 = Math.sqrt(dx * dx + dz * dz);
                double dyc = a.getY() - rAncla.getY();
                double d3 = Math.sqrt(dx * dx + dyc * dyc + dz * dz);
                boolean movio = trCuenta[k] > 0 && (Math.abs(x - trLastX[k]) > 0.01D || Math.abs(z - trLastZ[k]) > 0.01D);
                String estado = String.valueOf(a.navExecutor().state());
                boolean cambioEstado = trLastEstado[k] != null && !trLastEstado[k].equals(estado);
                if (trCuenta[k] == 0) {
                    trPrimerTick[k] = rTicks;
                }
                trUltimoTick[k] = rTicks;
                trLastX[k] = x;
                trLastZ[k] = z;
                trLastEstado[k] = estado;
                trCuenta[k]++;
                trOut.write(serverTick + "," + rTicks + "," + (k + 1) + "," + a.getUUID() + ","
                        + a.getType() + "," + dos(x) + "," + dos(a.getY()) + "," + dos(z) + ","
                        + dos(a.getBbWidth()) + "x" + dos(a.getBbHeight()) + ","
                        + rAncla.toShortString() + "," + dos(d2) + "," + dos(d3) + ","
                        + movio + "," + estado + "," + cambioEstado + ","
                        + "{" + a.navExecutor().rutaAccionDiag() + "},"
                        + "{" + a.navExecutor().descensoDiag() + "}\n");
                trRecords++;
            } catch (RuntimeException | java.io.IOException e) {
                trFallos++;
                if (trFallos <= 5) {
                    log("FALLO DE INSTRUMENTO traceNav idx=" + (k + 1) + ": " + e);
                }
            }
        }
    }

    private static void traceClose() {
        if (trOut == null) {
            if (TRACE_NAV) {
                log("TRACE NAV resumen: artefacto no disponible (fallos=" + trFallos + ")");
            }
            return;
        }
        try {
            trOut.write("# RESUMEN registros=" + trRecords + " descartados=" + trDropped
                    + " fallos=" + trFallos + "\n");
            for (int k = 0; k < R_ASALTANTES; k++) {
                if (trCuenta[k] > 0) {
                    trOut.write("# idx=" + (k + 1) + " registros=" + trCuenta[k] + " primerTick=" + trPrimerTick[k]
                            + " ultimoTick=" + trUltimoTick[k] + " huecos=" + (trUltimoTick[k] - trPrimerTick[k] + 1 - trCuenta[k])
                            + "\n");
                }
            }
            trOut.close();
            log("TRACE NAV cerrado: registros=" + trRecords + " descartados=" + trDropped + " fallos=" + trFallos);
        } catch (RuntimeException | java.io.IOException e) {
            trFallos++;
            log("FALLO DE INSTRUMENTO traceNav al cerrar: " + e);
        } finally {
            trOut = null;
        }
    }


    private static void informeRaid(final String fase, final MinecraftServer server) {
        rCeldasAnilloAhora = integridadAnillo(server);
        final int entraron = cuentaEntraronPerimetro();
        final String etiqueta = "FASE ".equals(fase) ? "A" : fase;
        log("RESUMEN FASE " + etiqueta + " t=" + rTicks + " spawneados=" + rSpawneados + "/" + R_ASALTANTES
                + " fallosSpawn=" + rFallosSpawn + " vivos=" + rVivos + " muertos=" + rMuertos
                + " desaparecidos=" + rDesaparecidos
                + " entraronPerimetroEver=" + entraron + " dentroParedAhora=" + rDentro);
        log("TOTALES " + etiqueta + ": colocados=" + rSpawneados + " vivosAlTerminar=" + rVivos
                + " entraronPerimetro=" + entraron + " dentroParedAhora=" + rDentro
                + " muertos=" + rMuertos + " desaparecidos=" + rDesaparecidos);
        log("PARED total: esperadas=" + rAnilloEsperadas + " presentes=" + rCeldasAnilloAhora
                + " faltantes=" + Math.max(0, rAnilloEsperadas - rCeldasAnilloAhora)
                + " columnasSinSuelo=" + rAnilloSinSuelo + " columnasNoSolidas=" + rAnilloNoSolidas
                + " columnasDesplazadas=" + rAnilloDesplazadas
                + " (base VALIDADA en construccion: un terreno cambiado NO se re-ancla ni se da por intacto)"
                + " celdasDentro(ahora)=" + rDentro + "/" + rAsaltantes.size()
                + " ticksDePica=NO MEDIDO (el arnes no usa pica)");
        log("CRITERIOS " + etiqueta + ": umbralesDeEntrada=NO DEFINIDOS en el repositorio"
                + " (no se inventan); medido: entraron=" + entraron + "/" + R_ASALTANTES
                + " dentroPared=" + rDentro + " vivos=" + rVivos
                + " radioAldea=" + R_RADIO_ALDEA + " radioAnillo=" + R_RADIO_ANILLO
                + " distanciaHorizontal=true");
        log("VEREDICTO " + etiqueta + ": SIN UMBRAL CONTRATADO (informativo; no se declara exito ni fracaso)"
                + " entraron=" + entraron + "/" + R_ASALTANTES + " vivos=" + rVivos
                + " muertos=" + rMuertos + " desaparecidos=" + rDesaparecidos);
        log("BAJAS DE COMBATE: muertesObservadas=" + rMuertos
                + " desparecidos=" + rDesaparecidos
                + " atribucionDeCombate=NO MEDIDA (sin instrumentacion de dano por fuente)");
        log("RETIRADA: NO MEDIDA (el arnes no instrumenta retirada)");
        for (int k = 0; k < Math.min(rAsaltantes.size(), R_ASALTANTES); k++) {
            AggressiveZombieEntity a = rAsaltantes.get(k);
            final int idx = k + 1;
            if (a == null || a.isRemoved()) {
                log("DESAPARECIO idx=" + idx + " t=" + rTicks + " (entidad eliminada o no disponible)");
                continue;
            }
            boolean vivo = a.isAlive() && a.getHealth() > 0.0F;
            double dx = a.getX() - rAncla.getX();
            double dz = a.getZ() - rAncla.getZ();
            log("RAID RESULTADO idx=" + idx + " vivo=" + vivo + " salud=" + dos(a.getHealth())
                    + " finalPos=" + a.blockPosition().toShortString()
                    + " distHorizontal=" + dos(Math.sqrt(dx * dx + dz * dz))
                    + " entroPerimetroEver=" + rEntroPerimetro[k]
                    + " dentroParedAhora=" + rDentroParedAhora[k]);
            if (!vivo) {
                log("MUERTE idx=" + idx + " t=" + rTicks + " salud=" + dos(a.getHealth())
                        + " pos=" + a.blockPosition().toShortString());
            }
        }
    }

    /** Una sola pasada por tick sobre los 8 asaltantes registrados; sin listas auxiliares ilimitadas. */
    private static void muestrearRaid() {
        rVivos = 0;
        rMuertos = 0;
        rDesaparecidos = 0;
        rDentro = 0;
        final int n = Math.min(rAsaltantes.size(), R_ASALTANTES);
        for (int k = 0; k < n; k++) {
            AggressiveZombieEntity a = rAsaltantes.get(k);
            final int idx = k + 1;
            if (a == null) {
                rDesaparecidos++;
                continue;
            }
            // Tras completar la animación de muerte, Entity.isRemoved() pasa a true y isAlive() deja
            // de servir para clasificarla. Se conserva el primer MUERTO observado (o health<=0) para
            // que una baja confirmada no cambie luego a DESAPARECIDO en snapshots posteriores.
            if (a.isRemoved()) {
                if (rMuertoRegistrado[k] || a.getHealth() <= 0.0F) {
                    rMuertos++;
                } else {
                    rDesaparecidos++;
                }
                continue;
            }
            boolean vivo = a.isAlive() && a.getHealth() > 0.0F;
            double dx = a.getX() - rAncla.getX();
            double dz = a.getZ() - rAncla.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (vivo) {
                rVivos++;
            } else {
                rMuertos++;
                if (!rMuertoRegistrado[k]) {
                    rMuertoRegistrado[k] = true;
                    log("RAID ESTADO idx=" + idx + " MUERTO t=" + rTicks + " salud=" + dos(a.getHealth())
                            + " pos=" + a.blockPosition().toShortString() + " dist=" + dos(dist));
                }
            }
            if (dist <= R_RADIO_ALDEA) {
                if (!rEntroPerimetro[k]) {
                    rEntroPerimetro[k] = true;
                    log("RAID ESTADO idx=" + idx + " ENTRO-PERIMETRO t=" + rTicks + " dist=" + dos(dist)
                            + " pos=" + a.blockPosition().toShortString());
                }
            }
            if (dist <= R_RADIO_ANILLO) {
                rDentro++;
                if (!rDentroParedAhora[k]) {
                    rDentroParedAhora[k] = true;
                    log("RAID ESTADO idx=" + idx + " DENTRO-PARED(ahora) t=" + rTicks + " dist=" + dos(dist)
                            + " pos=" + a.blockPosition().toShortString());
                }
            } else {
                rDentroParedAhora[k] = false;
            }
        }
        // Edit 8: bracket radial de ALTA resolucion (cada 20 ticks) SOLO para asaltantes a <=75 del ancla.
        // IMPORTANTE: esto es una observacion RADIAL (distPrevia -> dist). NO prueba cruce de una celda
        // concreta del muro, ni un hueco del perimetro digital, ni el muro: solo acota el intervalo.
        if (rTicks % 20 == 0) {
            for (int k = 0; k < n; k++) {
                AggressiveZombieEntity a = rAsaltantes.get(k);
                if (a == null || a.isRemoved()) {
                    continue;
                }
                double pdx = a.getX() - rAncla.getX();
                double pdz = a.getZ() - rAncla.getZ();
                double pdist = Math.sqrt(pdx * pdx + pdz * pdz);
                if (pdist > 75.0D) {
                    continue;
                }
                log("RAID PROX idx=" + (k + 1) + " t=" + rTicks
                        + " pos=" + a.blockPosition().toShortString()
                        + " distPrevia=" + (rDistPrevia[k] < 0.0D ? "NO MEDIDO" : dos(rDistPrevia[k]))
                        + " dist=" + dos(pdist) + " bracketRadial=(no-prueba-cruce-de-muro)"
                        + " estado=" + a.navExecutor().state()
                        + " ruta={" + a.navExecutor().rutaAccionDiag() + "}"
                        + " descenso={" + a.navExecutor().descensoDiag() + "}");
                rDistPrevia[k] = pdist;
            }
        }
        if (rTicks % 300 == 0) {
            log("RAID SNAPSHOT t=" + rTicks + " " + resumenRaid());
            // Task 3: observacion por-asaltante del ejecutor de navegacion. SOLO lectura: ninguna llamada
            // muta estado, fuerza rutas, cambia objetivos ni avanza ticks. Si un diagnostico no esta
            // disponible o lanza, se informa como NO DISPONIBLE sin abortar el snapshot.
            for (int k = 0; k < n; k++) {
                AggressiveZombieEntity a = rAsaltantes.get(k);
                if (a == null || a.isRemoved()) {
                    continue;
                }
                String estado;
                String ruta;
                String descenso;
                try {
                    estado = String.valueOf(a.navExecutor().state());
                } catch (RuntimeException e) {
                    estado = "NO DISPONIBLE(" + e.getClass().getSimpleName() + ")";
                }
                try {
                    ruta = a.navExecutor().rutaAccionDiag();
                } catch (RuntimeException e) {
                    ruta = "NO DISPONIBLE(" + e.getClass().getSimpleName() + ")";
                }
                try {
                    descenso = a.navExecutor().descensoDiag();
                } catch (RuntimeException e) {
                    descenso = "NO DISPONIBLE(" + e.getClass().getSimpleName() + ")";
                }
                String localDiag;
                String decisionEscalera;
                String escaleraDiag;
                try {
                    localDiag = a.navExecutor().objetivoLocalDiag();
                    decisionEscalera = a.decisionDescensoDiag();
                    escaleraDiag = a.escaleraDiag();
                } catch (RuntimeException e) {
                    localDiag = decisionEscalera = escaleraDiag =
                            "NO DISPONIBLE(" + e.getClass().getSimpleName() + ")";
                }
                double ddx = a.getX() - rAncla.getX();
                double ddz = a.getZ() - rAncla.getZ();
                log("RAID NAV idx=" + (k + 1) + " uuid=" + a.getUUID()
                        + " pos=" + a.blockPosition().toShortString()
                        + " dist=" + dos(Math.sqrt(ddx * ddx + ddz * ddz))
                        + " estado=" + estado + " ruta={" + ruta + "} descenso={" + descenso + "}"
                        + " local={" + localDiag + "} decisionEscalera={" + decisionEscalera
                        + "} escalera={" + escaleraDiag + "}");
            }
        }
    }


    private static final java.util.Set<java.util.UUID> rGuardiasQueCombatieron = new java.util.HashSet<>();
    /** UUIDs capturados en la fundación; no se pierde al trabajador cuando se aleja del recinto. */
    private static final java.util.Set<java.util.UUID> rTrabajadoresEsperados = new java.util.LinkedHashSet<>();
    private static final java.util.Set<java.util.UUID> rTrabajadoresQueReanudaron = new java.util.HashSet<>();
    private static final java.util.Set<java.util.UUID> rTrabajadoresMuertos = new java.util.HashSet<>();
    private static final java.util.Map<java.util.UUID, String> rCausaMuerteTrabajador = new java.util.HashMap<>();

    @SubscribeEvent
    public static void registrarMuerteTrabajador(
            net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.npc.Villager worker)) return;
        java.util.UUID workerId = worker.getUUID();
        if (!rTrabajadoresEsperados.contains(workerId)) return;
        rTrabajadoresMuertos.add(workerId);
        var source = event.getSource();
        var attacker = source.getEntity();
        var direct = source.getDirectEntity();
        String killer = attacker == null ? "none"
                : attacker.getType() + "#" + attacker.getId() + "/" + attacker.getUUID();
        String directEntity = direct == null ? "none"
                : direct.getType() + "#" + direct.getId() + "/" + direct.getUUID();
        String cause = "msgId=" + source.getMsgId() + " killer=" + killer + " direct=" + directEntity;
        rCausaMuerteTrabajador.put(workerId, cause);
        log("C4 WORKER DEATH uuid=" + workerId
                + " profession=" + worker.getVillagerData().getProfession()
                + " name=" + worker.getName().getString()
                + " pos=" + worker.blockPosition().toShortString()
                + " " + cause);
    }

    private static void muestrearDefensa() {
        if (nivel == null || rCentro == null) return;
        var zona = new net.minecraft.world.phys.AABB(rCentro)
                .inflate(com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 20.0D);
        for (var villager : aldeanosCapturadosEn(zona)) {
            if (!villager.isAlive() || villager.isBaby()) continue;
            if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(villager)) {
                for (var wrapped : villager.goalSelector.getAvailableGoals()) {
                    if (wrapped.isRunning()
                            && wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal goal) {
                        var enemy = goal.enemigoActual();
                        if (enemy != null && enemy.isAlive() && !enemy.isRemoved()
                                && rGuardiasQueCombatieron.add(villager.getUUID())) {
                            log("C4 GUARD ENGAGED guard=" + villager.getUUID() + " enemy=" + enemy.getUUID()
                                    + " t=" + rTicks + " pos=" + villager.blockPosition().toShortString());
                        }
                    }
                }
            }
        }
        // Muestrea los UUID capturados en el censo inicial, aunque el trabajador esté fuera del
        // AABB del recinto (p. ej. un leñador que ya salió a buscar un árbol). El conteo anterior
        // dependía de permanecer a 82 bloques del centro y podía producir un falso 11/12.
        if (rPostRaidIniciado) {
            for (java.util.UUID workerId : rTrabajadoresEsperados) {
                var entity = nivel.getEntity(workerId);
                if (!(entity instanceof net.minecraft.world.entity.npc.Villager)) {
                    entity = rAldeanosCapturados.get(workerId);
                }
                if (!(entity instanceof net.minecraft.world.entity.npc.Villager villager)
                        || !villager.isAlive() || villager.isRemoved() || villager.isBaby()) {
                    continue;
                }
                String expectedGoal = goalProfesionalEsperado(villager);
                if (expectedGoal.isEmpty()) continue;
                for (var wrapped : villager.goalSelector.getAvailableGoals()) {
                    if (!wrapped.isRunning()) {
                        continue;
                    }
                    String runningGoal = wrapped.getGoal().getClass().getSimpleName();
                    if (goalProfesionalActivo(villager, runningGoal)) {
                        boolean firstObservation = rTrabajadoresQueReanudaron.add(workerId);
                        if (firstObservation
                                && villager.getVillagerData().getProfession()
                                        == net.minecraft.world.entity.npc.VillagerProfession.NITWIT
                                && !runningGoal.equals(expectedGoal)) {
                            log("C4 WORKER ALTERNATE ACTIVITY uuid=" + workerId
                                    + " expectedGoal=" + expectedGoal + " activeGoal=" + runningGoal
                                    + " builderTag=" + villager.getPersistentData().getBoolean(
                                            com.chipoodle.devilrpg.world.VillageManager.BUILDER_TAG));
                        }
                        break;
                    }
                }
            }
        }
    }

    private static int[] auditarAsignacionesJobSite() {
        if (nivel == null || rCentro == null) return new int[] {0, 0, 0};
        var zona = new net.minecraft.world.phys.AABB(rCentro)
                .inflate(com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 20.0D);
        java.util.Set<Long> owners = new java.util.HashSet<>();
        int presentes = 0, huerfanas = 0, duplicadas = 0;
        for (var villager : aldeanosCapturadosEn(zona)) {
            var memory = villager.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            if (memory.isEmpty()) continue;
            presentes++;
            BlockPos pos = memory.get().pos();
            if (nivel.getPoiManager().getType(pos).isEmpty()) huerfanas++;
            if (!owners.add(pos.asLong())) duplicadas++;
        }
        return new int[] {presentes, huerfanas, duplicadas};
    }

    private static String resumenRaid() {
        return "vivos=" + rVivos + " muertos=" + rMuertos + " desaparecidos=" + rDesaparecidos
                + " entraronPerimetroEver=" + cuentaEntraronPerimetro() + " dentroParedAhora=" + rDentro
                + " seguidos=" + rAsaltantes.size() + " fase=" + (rTicks <= R_FASE_A ? "A" : "B");
    }


    /**
     * Evalua el recinto r=62 de un centro candidato SIN mutar el mundo. Mismos invariantes que la
     * construccion: suelo aceptable (sueloDeColumna), base apoyada, 3 bloques de holgura/altura y
     * continuidad entre columnas vecinas (salto <= 2). Devuelve {sinSuelo, fallosBase, fallosAltura,
     * fallosContinuidad, escaneos}. Solo lectura: no coloca ni rompe bloques.
     */
    private static int[] evaluarRecinto(final net.minecraft.server.level.ServerLevel nivelR,
                                        final int cx, final int cy, final int cz) {
        int sinSuelo = 0;
        int fallosBase = 0;
        int fallosAltura = 0;
        int fallosContinuidad = 0;
        int escaneos = 0;
        int anteriorBase = Integer.MIN_VALUE;
        int primeraBase = Integer.MIN_VALUE;
        int fallosFundacion = 0;
        final int r = R_RADIO_ANILLO;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt((double) (dx * dx + dz * dz));
                if (!(Math.abs(d - r) < 0.5D)) {
                    continue;
                }
                int suelo = sueloDeColumna(nivelR, cx + dx, cz + dz, cy);
                escaneos++;
                if (suelo == Integer.MIN_VALUE) {
                    sinSuelo++;
                    continue;
                }
                if (nivelR.getBlockState(new BlockPos(cx + dx, suelo, cz + dz)).isAir()) {
                    fallosBase++;
                }
                for (int dy = 1; dy <= R_ALTO_ANILLO; dy++) {
                    BlockPos p = new BlockPos(cx + dx, suelo + dy, cz + dz);
                    if (!nivelR.getBlockState(p).isAir() || !nivelR.getBlockState(p).getFluidState().isEmpty()) {
                        fallosAltura++;
                    }
                }
                if (primeraBase == Integer.MIN_VALUE) {
                    primeraBase = suelo + 1;
                }
                if (anteriorBase != Integer.MIN_VALUE && Math.abs((suelo + 1) - anteriorBase) > 2) {
                    fallosContinuidad++;
                }
                anteriorBase = suelo + 1;
                // Edit 19 · RESOLUBILIDAD DE FUNDACION (misma regla que la construccion, en SOLO LECTURA):
                // desde Y=69 hacia abajo se rellenaria solo AIRE; si el primer bloque no-aire no tiene
                // colision (agua/lava/planta) la columna NO se puede sellar -> candidato no elegible.
                for (int yf = 69; yf >= 69 - 64; yf--) {
                    BlockPos pf = new BlockPos(cx + dx, yf, cz + dz);
                    var stf = nivelR.getBlockState(pf);
                    if (stf.isAir()) {
                        continue;
                    }
                    if (stf.getCollisionShape(nivelR, pf).isEmpty()) {
                        fallosFundacion++;
                    }
                    break;
                }
            }
        }
        // SEAM: el anillo es cerrado, asi que la ultima columna tambien debe ser continua con la primera.
        if (primeraBase != Integer.MIN_VALUE && anteriorBase != Integer.MIN_VALUE
                && Math.abs(anteriorBase - primeraBase) > 2) {
            fallosContinuidad++;
        }
        return new int[] {sinSuelo, fallosBase, fallosAltura, fallosContinuidad, escaneos, fallosFundacion};
    }


    private static void prepararRaidFixture(final MinecraftServer server) {
        final int objectiveIndex = 0;
        final BlockPos requestedCenter = new BlockPos(661, 71, 533);
        final var level = server.overworld();
        final var saved = com.chipoodle.devilrpg.world.VillageSavedData.get(level);
        int forced = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                level.setChunkForced((requestedCenter.getX() >> 4) + dx,
                        (requestedCenter.getZ() >> 4) + dz, true);
                forced++;
            }
        }
        log("C4 CHUNKS forced=" + forced + " center=" + requestedCenter.toShortString()
                + " loaded=" + level.hasChunkAt(requestedCenter));
        if (!level.hasChunkAt(requestedCenter) || saved.isGenerated(objectiveIndex)
                || !level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                        new net.minecraft.world.phys.AABB(requestedCenter).inflate(100.0D)).isEmpty()) {
            fallosDeInstrumento++;
            rFixtureOk = false;
            log("C4 FIXTURE INVALIDO: mundo no virgen para objective=0 o centro no cargado; no se sobrescribe");
            return;
        }
        rAldeanosCapturados.clear();
        rCapturandoAldeanos = true;
        boolean preGenerated;
        try {
            preGenerated = com.chipoodle.devilrpg.world.VillageManager.preGenerate(
                    level, objectiveIndex, requestedCenter);
        } finally {
            rCapturandoAldeanos = false;
        }
        if (!preGenerated) {
            fallosDeInstrumento++;
            rFixtureOk = false;
            log("C4 FIXTURE INVALIDO: preGenerate no completó la aldea");
            return;
        }
        BlockPos center = saved.getCentre(objectiveIndex);
        if (center == null) {
            fallosDeInstrumento++;
            rFixtureOk = false;
            log("C4 FIXTURE INVALIDO: VillageSavedData no persistió el centro");
            return;
        }
        rCentro = center.immutable();
        rAncla = rCentro;
        int baseY = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, center);
        rBaseValidada.clear();
        rTrabajadoresEsperados.clear();
        rTrabajadoresQueReanudaron.clear();
        rTrabajadoresMuertos.clear();
        rCausaMuerteTrabajador.clear();
        java.util.Set<Long> wallColumns = com.chipoodle.devilrpg.world.VillageGenerator.columnasDelAnilloDelMuro(center);
        for (long packed : wallColumns) {
            BlockPos column = BlockPos.of(packed);
            rBaseValidada.put(claveColumna(column.getX() - center.getX(),
                    column.getZ() - center.getZ()), baseY);
        }
        rCeldasAnillo = wallColumns.size() * R_ALTURA_MURO_VALIDADA;
        rCeldasAnilloAhora = integridadAnillo(server);
        try {
            rCapturandoAldeanos = true;
            com.chipoodle.devilrpg.world.VillageManager.ejecutarMantenimientoParaArnes(level, objectiveIndex, center);
        } catch (RuntimeException ex) {
            fallosDeInstrumento++;
            rFixtureOk = false;
            log("C4 MAINTENANCE ERROR: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return;
        } finally {
            rCapturandoAldeanos = false;
        }
        var populationBounds = new net.minecraft.world.phys.AABB(center).inflate(
                com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 20.0D);
        var aabbVillagers = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, populationBounds);
        var villagers = aldeanosCapturadosEn(populationBounds);
        for (var captured : rAldeanosCapturados.values()) {
            log("C4 POPULATION CAPTURED uuid=" + captured.getUUID()
                    + " profession=" + captured.getVillagerData().getProfession()
                    + " alive=" + captured.isAlive() + " removed=" + captured.isRemoved()
                    + " addedToLevel=" + captured.isAddedToLevel()
                    + " pos=" + captured.blockPosition().toShortString());
        }
        java.util.Set<java.util.UUID> aabbUuids = new java.util.HashSet<>();
        for (var villager : aabbVillagers) aabbUuids.add(villager.getUUID());
        for (var villager : villagers) {
            if (!aabbUuids.contains(villager.getUUID())) {
                long chunkPos = new net.minecraft.world.level.ChunkPos(villager.blockPosition()).toLong();
                log("C4 POPULATION AABB_OMITTED uuid=" + villager.getUUID()
                        + " profession=" + villager.getVillagerData().getProfession()
                        + " pos=" + villager.blockPosition().toShortString()
                        + " entityLookupPresent=" + (level.getEntity(villager.getUUID()) == villager)
                        + " addedToLevel=" + villager.isAddedToLevel()
                        + " entitiesLoaded=" + level.areEntitiesLoaded(chunkPos)
                        + " chunkLoaded=" + level.hasChunkAt(villager.blockPosition()));
            }
        }
        log("C4 POPULATION SOURCES aabb=" + aabbVillagers.size()
                + " joinEvents=" + villagers.size() + " capturedOnly="
                + villagers.stream().filter(v -> !aabbUuids.contains(v.getUUID())).count());
        int guards = 0, founders = 0, guardGoals = 0, equipped = 0, professionals = 0, professionGoals = 0;
        java.util.Map<String, Integer> roster = new java.util.TreeMap<>();
        for (var villager : villagers) {
            if (!villager.isAlive() || villager.isBaby()) continue;
            if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(villager)) {
                guards++;
                boolean founder = false;
                for (int slot = 0; slot < 4; slot++) {
                    if (com.chipoodle.devilrpg.world.VillageManager.esGuardiaFundadora(villager, objectiveIndex, slot)) founder = true;
                }
                if (founder) founders++;
                boolean hasGoal = false;
                for (var wrapped : villager.goalSelector.getAvailableGoals()) {
                    if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal) hasGoal = true;
                }
                if (hasGoal) guardGoals++;
                if (!villager.getMainHandItem().isEmpty()) equipped++;
            } else if (villager.getVillagerData().getProfession()
                    != net.minecraft.world.entity.npc.VillagerProfession.NONE) {
                professionals++;
                rTrabajadoresEsperados.add(villager.getUUID());
                roster.merge(villager.getVillagerData().getProfession().toString(), 1, Integer::sum);
                if (tieneGoalProfesional(villager)) professionGoals++;
            }
        }
        int expectedProfessionals = com.chipoodle.devilrpg.world.VillageGenerator.puestosDelPueblo();
        boolean wallOk = !rBaseValidada.isEmpty() && rCeldasAnilloAhora == rCeldasAnillo
                && rAnilloSinSuelo == 0 && rAnilloNoSolidas == 0;
        boolean populationOk = founders == 4 && guardGoals == 4 && equipped == 4
                && professionals == expectedProfessionals && professionGoals == expectedProfessionals
                && rTrabajadoresEsperados.size() == expectedProfessionals;
        rFixtureOk = wallOk && populationOk;
        log("C4 PRODUCTION VILLAGE generated=true objective=" + objectiveIndex + " center=" + center.toShortString()
                + " baseY=" + baseY + " wallColumns=" + wallColumns.size()
                + " wallCells=" + rCeldasAnillo + " wallPresent=" + rCeldasAnilloAhora + " wallOk=" + wallOk
                + " supportMissing=" + rAnilloSinSuelo + " damagedColumns=" + rAnilloNoSolidas);
        log("C4 POPULATION villagers=" + villagers.size() + " guards=" + guards + " founders=" + founders + "/4"
                + " guardGoals=" + guardGoals + "/4 equipped=" + equipped + "/4 professionals=" + professionals
                + "/" + expectedProfessionals + " professionGoals=" + professionGoals + "/"
                + expectedProfessionals + " trackedWorkerUuids=" + rTrabajadoresEsperados.size()
                + " roster=" + roster);
        log("C4 FIXTURE ready=" + rFixtureOk + " (production VillageGenerator + VillageManager maintenance)");
    }

    /**
     * Diagnostico de elegibilidad al cierre C4. No altera goals ni crea recursos:
     * distingue un fallo de reanudacion de una profesion que esta correctamente ociosa/evadiendo peligro.
     */
    private static String diagnosticoElegibilidadProfesional(
            net.minecraft.world.entity.npc.Villager worker) {
        StringBuilder out = new StringBuilder();
        boolean underAttack = com.chipoodle.devilrpg.world.VillageManager
                .isVillageUnderAttack(nivel, 0);
        boolean resting = com.chipoodle.devilrpg.world.VillageManager.estaDescansando(worker);
        double dx = worker.getX() - rCentro.getX();
        double dz = worker.getZ() - rCentro.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        var threats = nivel.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                new net.minecraft.world.phys.AABB(worker.blockPosition()).inflate(22.0D));
        out.append("underAttack=").append(underAttack)
                .append(" resting=").append(resting)
                .append(" distanceCenter=").append(String.format(java.util.Locale.ROOT, "%.1f", distance))
                .append(" nearbyMonsters22=").append(threats.size());
        if (!threats.isEmpty()) {
            var nearest = threats.stream().min(java.util.Comparator.comparingDouble(worker::distanceToSqr))
                    .orElse(null);
            if (nearest != null) {
                out.append(" nearestMonster=").append(nearest.getType())
                        .append("#").append(nearest.getId())
                        .append("@").append(nearest.blockPosition().toShortString())
                        .append(" d=").append(String.format(java.util.Locale.ROOT, "%.1f",
                                Math.sqrt(worker.distanceToSqr(nearest))));
            }
        }
        var profession = worker.getVillagerData().getProfession();
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.NITWIT) {
            var items = nivel.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(rCentro).inflate(
                            com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 28.0D),
                    item -> item.isAlive() && item.getAge() >= 100);
            out.append(" agedItemEntitiesVillage=").append(items.size());
        } else if (profession == net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) {
            var pantry = com.chipoodle.devilrpg.world.VillagePantry.despensa(nivel, rCentro);
            var smoker = com.chipoodle.devilrpg.world.VillageGenerator.puestoDelCocinero(nivel, rCentro);
            out.append(" rawFood=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                    pantry, com.chipoodle.devilrpg.world.VillagePantry::sePuedeCocinar))
                    .append(" wheat=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            pantry, stack -> stack.is(net.minecraft.world.item.Items.WHEAT)))
                    .append(" smokerPresent=").append(nivel.getBlockState(smoker)
                            .is(net.minecraft.world.level.block.Blocks.SMOKER))
                    .append(" woodForFuel=").append(
                            com.chipoodle.devilrpg.world.VillageStorage.hayLenaParaQuemar(nivel, rCentro));
        } else if (profession == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH) {
            var storage = com.chipoodle.devilrpg.world.VillageStorage.almacen(nivel, rCentro);
            out.append(" iron=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                    storage, stack -> stack.is(net.minecraft.world.item.Items.IRON_INGOT)))
                    .append(" planks=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            storage, stack -> stack.is(net.minecraft.world.item.Items.OAK_PLANKS)))
                    .append(" sticks=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            storage, stack -> stack.is(net.minecraft.world.item.Items.STICK)))
                    .append(" string=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            storage, stack -> stack.is(net.minecraft.world.item.Items.STRING)))
                    .append(" feathers=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            storage, stack -> stack.is(net.minecraft.world.item.Items.FEATHER)))
                    .append(" nuggets=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            storage, stack -> stack.is(net.minecraft.world.item.Items.IRON_NUGGET)))
                    .append(" swords=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            storage, stack -> stack.is(net.minecraft.world.item.Items.IRON_SWORD)))
                    .append(" shields=").append(com.chipoodle.devilrpg.world.VillagePantry.contar(
                            storage, stack -> stack.is(net.minecraft.world.item.Items.SHIELD)));
        } else if (profession == net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN) {
            var barrel = com.chipoodle.devilrpg.world.VillageGenerator.puestoDelPescador(nivel, rCentro);
            out.append(" fisherBarrel=").append(barrel.toShortString())
                    .append(" barrelPresent=").append(nivel.getBlockState(barrel).is(net.minecraft.world.level.block.Blocks.BARREL))
                    .append(" fishInLake=").append(
                            com.chipoodle.devilrpg.world.VillageGenerator.pecesEnElLago(nivel, rCentro))
                    .append(" fisheryBuilt=").append(
                            com.chipoodle.devilrpg.world.VillageGenerator.pesqueraConstruida(nivel, rCentro));
        }
        return out.toString();
    }

    private static String goalProfesionalEsperado(net.minecraft.world.entity.npc.Villager villager) {
        var profession = villager.getVillagerData().getProfession();
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.FARMER) return "VillagerFarmGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH
                || profession == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH) return "VillagerSmithGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.MASON) return "VillagerMinerGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.NITWIT) return "VillagerCollectGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.FLETCHER) return "VillagerLumberjackGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD) return "VillagerAnimalFarmGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) return "VillagerCookGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN) return "VillagerFisherGoal";
        if (profession == net.minecraft.world.entity.npc.VillagerProfession.CLERIC) return "VillagerClericGoal";
        return "";
    }

    /**
     * El nitwit recolector tambien es el obrero constructor. Durante una recuperacion puede estar reparando
     * un daño real en lugar de recoger; ambas son actividades autonomas validas. No se acepta RepairGoal para
     * un nitwit sin BUILDER_TAG ni para otras profesiones.
     */
    private static boolean goalProfesionalActivo(
            net.minecraft.world.entity.npc.Villager villager, String runningGoal) {
        if (runningGoal.equals(goalProfesionalEsperado(villager))) {
            return true;
        }
        return villager.getVillagerData().getProfession()
                        == net.minecraft.world.entity.npc.VillagerProfession.NITWIT
                && "VillagerRepairGoal".equals(runningGoal)
                && villager.getPersistentData().getBoolean(
                        com.chipoodle.devilrpg.world.VillageManager.BUILDER_TAG);
    }

    private static boolean tieneGoalProfesional(net.minecraft.world.entity.npc.Villager villager) {
        String expected = goalProfesionalEsperado(villager);
        if (expected.isEmpty()) return false;
        for (var wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal().getClass().getSimpleName().equals(expected)) return true;
        }
        return false;
    }

    private static void prepararLongrange() {
        lrPreparado = true;
        construirFixtureConstruccion();
        if (!validarFixtureConstruccion()) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO longrange: geometria del fixture invalida");
            return;
        }
        lrDestino = new BlockPos(103, CON_LLANURA_SUELO + 1, CON_Z);
        // Control: el destino esta en la llanura, mas alla de la cara, y la caida directa supera la segura.
        boolean destinoOk = nivel.getBlockState(lrDestino).isAir()
                && !nivel.getBlockState(lrDestino.below()).isAir()
                && nivel.getBlockState(lrDestino.above()).isAir();
        int caidaDirecta = conSpawn.getY() - lrDestino.getY();
        log("FIXTURE-LONGRANGE GEOMETRIA: spawn=" + conSpawn.toShortString()
                + " destino=" + lrDestino.toShortString() + " destinoOk=" + destinoOk
                + " caidaDirecta=" + caidaDirecta + " maxSafeDrop=3 superaSegura=" + (caidaDirecta > 3)
                + " (control: sin escalones la ruta directa es insegura)");
        if (!destinoOk || caidaDirecta <= 3) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO longrange: el destino o el control de caida no se cumplen");
            return;
        }
        lrMob = sonda(conSpawn.getX(), conSpawn.getY(), conSpawn.getZ());
        if (lrMob == null) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO longrange: sonda nula");
            return;
        }
        lrSaludMin = lrMob.getHealth();
        lrMob.navExecutor().setObjective(lrDestino);
        log("FIXTURE-LONGRANGE INICIO: ente=" + lrMob.blockPosition().toShortString()
                + " destino=" + lrDestino.toShortString());
    }

    private static void tickLongrange(final MinecraftServer server) {
        lrTicks++;
        if (!lrPreparado) {
            prepararLongrange();
            if (lrMob == null) {
                hecho = true;
                cerrar("INVALIDO", "longrange: fixture o sonda invalidos", server);
            }
            return;
        }
        int colocadas = lrMob.navExecutor().celdasConstruidas().size();
        int travesados = lrMob.navExecutor().escalonesTravesadosDelPlan();
        lrSaludMin = Math.min(lrSaludMin, lrMob.getHealth());
        if (colocadas > lrColocadasMax) {
            lrColocadasMax = colocadas;
            for (BlockPos c : lrMob.navExecutor().celdasConstruidas()) {
                if (nivel.getBlockState(c).isAir()) {
                    lrVerificadasFallo++;
                }
            }
            lrEventos.append("COLOCADA t=").append(lrTicks).append(" n=").append(colocadas)
                    .append(" celdas=").append(lrMob.navExecutor().celdasConstruidas()).append(" | ");
            log("FIXTURE-LONGRANGE COLOCACION t=" + lrTicks + " colocadas=" + colocadas
                    + " travesados=" + travesados + " celdas=" + lrMob.navExecutor().celdasConstruidas()
                    + " estado=" + lrMob.navExecutor().state());
        }
        if (travesados > lrTravesadosMax) {
            lrTravesadosMax = travesados;
            lrEventos.append("TRAVESIA t=").append(lrTicks).append(" n=").append(travesados)
                    .append(" pos=").append(lrMob.blockPosition().toShortString()).append(" | ");
            log("FIXTURE-LONGRANGE TRAVESIA t=" + lrTicks + " travesados=" + travesados
                    + " pos=" + lrMob.blockPosition().toShortString()
                    + " colocadas=" + colocadas);
        }
        if (colocadas >= 1 && travesados == 0) {
            lrColocadaSinTravesia = true;
        }
        if (lrMob.blockPosition().distSqr(lrDestino) <= 2.25D) {
            lrLlego = true;
        }
        String desc = lrMob.navExecutor().describe();
        int planes = desc.contains("plans=") ? Integer.parseInt(desc.replaceAll(".*plans=(\\d+).*", "$1")) : 0;
        lrPlanes = Math.max(lrPlanes, planes);
        if (lrTicks % 100 == 0) {
            log("FIXTURE-LONGRANGE t=" + lrTicks + " pos=" + lrMob.blockPosition().toShortString()
                    + " colocadas=" + colocadas + " travesados=" + travesados
                    + " salud=" + lrMob.getHealth() + " estado=" + lrMob.navExecutor().state()
                    + " ejec={" + desc + "}");
        }
        if (lrTicks >= 1800) {
            hecho = true;
            veredictoLongrange(server);
        }
    }

    private static void veredictoLongrange(final MinecraftServer server) {
        var ej = lrMob.navExecutor();
        boolean llegadaEstado = ej.state() == com.chipoodle.devilrpg.entity.nav.NavState.ARRIVED;
        caso("LR-1 colocacion verificada en el mundo", lrColocadasMax >= 1 && lrVerificadasFallo == 0,
                "colocadasMax=" + lrColocadasMax + " sinBloque=" + lrVerificadasFallo,
                "se esperaba >=1 colocacion presente en el mundo");
        caso("LR-2 colocacion NO cuenta como travesia (muestreo)", lrColocadaSinTravesia,
                "colocadaConCeroTravesias=" + lrColocadaSinTravesia,
                "se esperaba observar colocadas>=1 con travesados==0");
        caso("LR-3 travesia fisica de pasos construidos", lrTravesadosMax >= 1,
                "travesadosMax=" + lrTravesadosMax, "se esperaba >=1 travesia");
        caso("LR-4 progresion de tramos de ruta (replanificacion)", lrPlanes >= 2,
                "planes=" + lrPlanes, "se esperaban >=2 planes (mas de un tramo)");
        caso("LR-5 llegada al destino MAS ALLA del acantilado", llegadaEstado && lrLlego,
                "estado=" + ej.state() + " llego=" + lrLlego + " pos=" + lrMob.blockPosition().toShortString()
                        + " dist=" + String.format(java.util.Locale.ROOT, "%.2f",
                        Math.sqrt(lrMob.blockPosition().distSqr(lrDestino))),
                "se esperaba ARRIVED en " + lrDestino.toShortString());
        caso("LR-6 sin dano ni caida insegura", lrSaludMin >= lrMob.getMaxHealth(),
                "saludMin=" + lrSaludMin + " saludMax=" + lrMob.getMaxHealth(),
                "se esperaba salud intacta");
        lrMotivo = "colocadas=" + ej.celdasConstruidas().size() + " travesados=" + ej.escalonesTravesadosDelPlan()
                + " planes=" + lrPlanes + " estado=" + ej.state() + " saludMin=" + lrSaludMin
                + " pos=" + lrMob.blockPosition().toShortString()
                + " construccion={" + ej.planConstruccionDiag() + "} eventos={" + lrEventos + "}";
        lrMob.discard();
        String veredicto = (fallosDeInstrumento > 0 || casosOk != casosTotal) ? "INVALIDO" : "COMPLETO";
        cerrar(veredicto, "casos=" + casosOk + "/" + casosTotal
                + " fallosDeInstrumento=" + fallosDeInstrumento + " | " + lrMotivo, server);
    }

    // Asserts y utilidades
    // ================================================================================================

    /** Precondiciones de mundo: la celda meta y su cabeza deben ser aire y tener soporte debajo. */
    private static boolean precondicion(final String etiqueta, final BlockPos meta, final boolean airePies,
                                        final boolean soporte) {
        boolean ok = true;
        if (!nivel.hasChunkAt(meta)) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO " + etiqueta + ": chunk no cargado en " + meta.toShortString());
            ok = false;
        }
        if (airePies && (!nivel.getBlockState(meta).isAir() || !nivel.getBlockState(meta.above()).isAir())) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO " + etiqueta + ": la celda meta o su cabeza NO son aire en "
                    + meta.toShortString());
            ok = false;
        }
        if (soporte && !NavObstruction.hasSolidFace(nivel, meta.below())) {
            fallosDeInstrumento++;
            log("FALLO DE INSTRUMENTO " + etiqueta + ": sin soporte con colision bajo " + meta.toShortString());
            ok = false;
        }
        return ok;
    }

    private static NavPlanner.Result plan(final AggressiveZombieEntity sonda, final BlockPos desde,
                                          final BlockPos hasta) {
        return NavPlanner.plan(nivel, sonda, desde, hasta, H, V, PRESUPUESTO, sonda.navPolicies());
    }

    /** ¿todas las transiciones son WALK y el desnivel es 0? */
    private static boolean soloLlano(final NavRoute ruta) {
        for (NavRoute.Node n : ruta.nodes()) {
            if (n.transition() != NavRoute.Transition.WALK) {
                return false;
            }
        }
        return true;
    }

    private static boolean contiene(final NavRoute ruta, final NavRoute.Transition t) {
        for (NavRoute.Node n : ruta.nodes()) {
            if (n.transition() == t) {
                return true;
            }
        }
        return false;
    }

    /** Mayor descenso (en bloques) entre nodos consecutivos de la ruta. */
    private static int caidaMaxima(final NavRoute ruta) {
        int max = 0;
        for (int i = 1; i < ruta.length(); i++) {
            int dy = ruta.nodes().get(i - 1).pos().getY() - ruta.nodes().get(i).pos().getY();
            max = Math.max(max, dy);
        }
        return max;
    }

    /** Mayor subida (en bloques) entre nodos consecutivos de la ruta. */
    private static int subidaMaxima(final NavRoute ruta) {
        int max = 0;
        for (int i = 1; i < ruta.length(); i++) {
            int dy = ruta.nodes().get(i).pos().getY() - ruta.nodes().get(i - 1).pos().getY();
            max = Math.max(max, dy);
        }
        return max;
    }

    private static void caso(final String nombre, final boolean ok, final String detalle, final String esperado) {
        casosTotal++;
        if (ok) {
            casosOk++;
        } else {
            fallosDeInstrumento++;
        }
        String linea = "REGRESION-ARISTAS " + (ok ? "OK   " : "FALLO") + " " + nombre
                + " :: " + detalle + " | esperado: " + esperado;
        DETALLE.append('|').append(linea);
        log(linea);
    }

    private static AggressiveZombieEntity sonda(final int x, final int y, final int z) {
        BlockPos pos = new BlockPos(x, y, z);
        AggressiveZombieEntity z0 = com.chipoodle.devilrpg.init.ModEntities.AGGRESSIVE_ZOMBIE.get()
                .create(nivel, null, pos, MobSpawnType.COMMAND, false, false);
        if (z0 == null) {
            return null;
        }
        z0.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
        z0.setPersistenceRequired();
        nivel.addFreshEntity(z0);
        return z0;
    }

    private static void forzar(final int x1, final int z1, final int x2, final int z2, final int y) {
        for (int cx = (x1 >> 4); cx <= (x2 >> 4); cx++) {
            for (int cz = (z1 >> 4); cz <= (z2 >> 4); cz++) {
                nivel.setChunkForced(cx, cz, true);
                nivel.getChunkAt(new BlockPos(cx << 4, y, cz << 4));
            }
        }
    }

    private static void aire(final int x, final int y, final int z) {
        nivel.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }

    private static void solido(final int x, final int y, final int z) {
        nivel.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
    }

    private static void cerrar(final String veredicto, final String detalle, final MinecraftServer server) {
        traceClose();   // Edit 11(a): cierra y resume el artefacto de traza si estaba activo
        int ok = casosOk;
        int total = casosTotal;
        log("RESUMEN ARNES: modo=" + MODE + " casos=" + ok + "/" + total);
        log("FIN (" + veredicto + " (" + MODE + ") - " + detalle + ")");
        try {
            Path raiz = Path.of(System.getProperty("a2n.raiz", "."));
            String run = System.getProperty("a2n.run", "sin-run");
            Path base = raiz.resolve("build").resolve("a2n").resolve(run);
            Files.createDirectories(base);
            Files.writeString(base.resolve("fin.txt"), veredicto + " (" + MODE + ") - " + detalle,
                    StandardCharsets.UTF_8);
            Files.writeString(base.resolve("estado.txt"), DETALLE.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[A2N] no pude escribir fin.txt: {}", e.toString());
        }
        if (server != null) {
            server.halt(false);
        }
    }

    private static void log(final String mensaje) {
        DevilRpg.LOGGER.info("[A2N] {}", mensaje);
    }
}
