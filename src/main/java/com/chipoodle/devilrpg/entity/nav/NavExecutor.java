package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A2 · <b>Ejecutor de movimiento único</b> y máquina de estados de la navegación propia.
 * <p>
 * Es el ÚNICO sitio que llama a {@code getNavigation().moveTo(...)} y el único que decide la siguiente acción de
 * terreno. Los goals (aldea, persecución) solo <b>envían un objetivo</b> con {@link #setObjective}; no navegan ellos.
 * Eso es lo que arregla el fallo medido de los rezagados: `MOVE=-` con `plan=idle` (nadie pedía navegación) y
 * `local-escape` repitiendo el mismo waypoint rechazado.
 * <p>
 * Reglas de recuperación, todas acotadas:
 * <ul>
 *   <li>Toda ruta tiene un presupuesto de reintentos; al agotarlo el destino se marca {@link NavState#FAILED}.</li>
 *   <li>Una ruta con la <b>misma firma</b> que una ya fallada no se vuelve a intentar.</li>
 *   <li>Se detecta oscilación A→B→A por historial de celdas recientes.</li>
 *   <li>Cada acción de recuperación es un paso, con enfriamiento creciente y tope.</li>
 * </ul>
 */
public final class NavExecutor {

    // ---- Presupuestos (todos duros; ninguno crece sin límite) ----
    /**
     * Presupuesto de expansiones por plan. Por defecto 900 (el valor con el que se midio el fallo de inercia en
     * `a2n-20261015-170418`); se puede subir SOLO por propiedad de sistema para el experimento comparativo
     * `-Ddevilrpg.nav.budget=N`, sin tocar el codigo entre corridas.
     */
    private static final int MAX_EXPANSIONS_PER_PLAN = presupuestoDeBusqueda();

    private static int presupuestoDeBusqueda() {
        String p = System.getProperty("devilrpg.nav.budget");
        if (p == null) {
            return 900;
        }
        try {
            return Math.max(50, Math.min(20000, Integer.parseInt(p.trim())));
        } catch (NumberFormatException e) {
            return 900;
        }
    }
    private static final int MAX_REPLANS_PER_DESTINATION = 8;
    private static final int MAX_RECOVERY_ACTIONS = 24;
    private static final int REPLAN_COOLDOWN_TICKS = 20;
    private static final int RECOVERY_COOLDOWN_TICKS = 40;
    private static final int MAX_RECOVERY_COOLDOWN_TICKS = 400;
    /** Espera minima y maxima entre reintentos por presupuesto agotado (evita martillear busquedas caras). */
    private static final int MIN_BUDGET_BACKOFF_TICKS = 40;
    private static final int MAX_BUDGET_BACKOFF_TICKS = 400;
    /**
     * A2 FASE 3 · TOPE DE REINTENTOS POR PRESUPUESTO AGOTADO SIN PROGRESO.
     * Un solo `hitBudget` es INCONCLUYENTE; repetirlo indefinidamente no lo es. Este tope (configurable por
     * `-Ddevilrpg.nav.budgetRetries`, por defecto 10, acotado 1..20) convierte la repeticion sin progreso en un
     * fallo TERMINAL con motivo, que la recuperacion acotada de la Fase 2 puede intentar rearmar.
     */
    private static final int MAX_BUDGET_RETRIES = maxBudgetRetriesPorPropiedad();
    /**
     * A2 FASE 2 · REARMES TERMINALES. Un `FAILED` no puede dejar al ente inmovil para siempre, pero tampoco
     * puede reintentarse cada tick: tras cada rearme hay enfriamiento y solo se permiten
     * {@value #MAX_TERMINAL_REARMS} por mision. Al agotarlos el fallo es definitivo y con motivo.
     */
    private static final int MAX_TERMINAL_REARMS = 4;
    private static final int REARM_COOLDOWN_TICKS = 60;

    private static int maxBudgetRetriesPorPropiedad() {
        String p = System.getProperty("devilrpg.nav.budgetRetries");
        if (p == null) {
            return 10;
        }
        try {
            return Math.max(1, Math.min(20, Integer.parseInt(p.trim())));
        } catch (NumberFormatException e) {
            return 10;
        }
    }
    /** Dispersión del objetivo que dispara un recálculo (no se replanifica cada tick). */
    private static final double OBJECTIVE_MOVE_THRESHOLD_SQR = 9.0D;
    private static final int HORIZONTAL_RANGE = 48;
    private static final int VERTICAL_RANGE = 6;

    // ============================================================================================
    // A2 Stage 7B · ESCALONADO ACOTADO DEL OBJETIVO (navegacion de largo alcance).
    // Medido en `a2n-20261016-185324` (asedio amurallado): los cuatro asaltantes que salen a ~95 bloques NUNCA
    // encolan la meta —esta fuera de la ventana del planificador (`H=48 V=6`)—, agotan las 900 expansiones en cada
    // intento (`metaEncolada=false`, `evaluados=93600`) y no llegan a mover ni un bloque (`moves=0`). La MISION
    // FINAL no cambia nunca: lo que se le entrega al planificador es un OBJETIVO LOCAL dentro de la ventana.
    // ============================================================================================
    /** Margen con el que el objetivo local se mantiene DENTRO de la ventana (no se agranda la ventana). */
    private static final int MARGEN_DE_VENTANA = 4;
    /** Paso con el que se prueban objetivos locales: de mas lejos (borde de la ventana) a mas cerca. */
    private static final int PASO_DE_BUSQUEDA_DE_OBJETIVO = 4;
    /** Progreso horizontal MINIMO exigido a un objetivo local: si no acerca, no vale como escalon. */
    private static final double PROGRESO_MINIMO_DE_OBJETIVO = 8.0D;
    /** Tope DURO de objetivos locales por mision (acotado; nunca escalona sin fin). */
    private static final int MAX_ESCALONES_DE_OBJETIVO = 64;
    /** Fallos seguidos eligiendo objetivo local antes de rendirse con motivo explicito. */
    private static final int MAX_FALLOS_DE_ESCALON_DE_OBJETIVO = 10;

    // ============================================================================================
    // A2 Stage 7C · CAPA GLOBAL ACOTADA. El Stage 7B eligio el objetivo local por GEOMETRIA (pasos
    // radiales hacia la mision con validacion de celda) y se paro en el escape de 8 bloques medido:
    // ningun candidato radial quedaba dentro de ±VERTICAL_RANGE. Aqui el tramo se elige sobre una RUTA
    // GLOBAL calculada con una A* correcta y acotada sobre el MISMO grafo de movimientos validados:
    // el objetivo local es un waypoint DE ESA RUTA, es decir una celda a la que se ha demostrado que
    // se puede llegar (rodear el escape, subir por una rampa o construir escalones si el contrato de
    // BUILD lo permite). La ventana local NO se toca y el presupuesto local NO se toca.
    // ============================================================================================
    /** Radio horizontal de la busqueda global (bloques). Acotado: nunca "todo el mundo". */
    private static final int RADIO_GLOBAL_DE_BUSQUEDA = 112;
    /** Sonda vertical por arista de la busqueda global (la ruta puede subir/bajar mucho en total). */
    private static final int VENTANA_VERTICAL_GLOBAL = 3;
    /** Presupuesto TOTAL duro por búsqueda global; el trabajo se reanuda en slices de 4 expansiones por tick. */
    private static final int MAX_EXPANSIONES_GLOBALES = presupuestoGlobal();

    private static int presupuestoGlobal() {
        String raw = System.getProperty("devilrpg.nav.globalBudget");
        if (raw == null) raw = System.getenv("DEVILRPG_NAV_GLOBAL_BUDGET");
        if (raw == null) return 8000;
        try {
            return Math.max(8000, Math.min(32000, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException ignored) {
            return 8000;
        }
    }
    /** La ruta global se reutiliza mientras el ente no se aleje mas de 8 bloques del punto de calculo. */
    private static final double CACHE_GLOBAL_SQR = 64.0D;
    /**
     * A2 Stage 7C · re-estadios permitidos cuando la recuperacion generica se agota en un tramo largo (la mision
     * sigue fuera de la ventana local). Acotado: al agotarse, el fallo es terminal y con motivo.
     */
    private static final int MAX_REESTADIOS_GLOBALES = 8;
    /**
     * A2 Stage 7E · VENTANA DE GRACIA antes de declarar OBSTRUCTED. Medido en `a2n-20261017-125102`: en el primer
     * tick tras enviar el movimiento, `mob.getNavigation().isDone()==true` con `pathNull=true` (la navegacion de
     * vanilla NO habia producido ruta aun) y el predicado antiguo (`isDone && trans!=BREAK/BRIDGE`) declaraba la
     * arista obstruida con `aristaValida=true`, `pieDestino=true`, `ticksSinAvance=0` y `envios=1`. La obstruccion
     * pasa a medirse por AVANCE REAL hacia el nodo durante esta ventana, no por el estado instantaneo de vanilla.
     */
    private static final int TICKS_DE_GRACIA_DE_NODO = 20;
    /** A2 Stage 7E · mejora de distancia (bloques) que cuenta como avance real hacia el nodo. */
    private static final double AVANCE_MINIMO_DE_NODO = 0.25D;
    /**
     * A2 Stage 7G · REPLANTEO TRAS RECHAZO POR GUARDA DE RUTA. Cuando la guarda impide que la recuperacion
     * generica toque la ruta activa, ese rechazo NO es evidencia de que la ruta este bloqueada: se invalida la ruta
     * local y se replanifica. Acotado por si mismo (no se sube MAX_RECOVERY_ACTIONS ni se reintenta sin fin).
     */
    private static final int MAX_REPLANTEOS_POR_GUARDA = 12;
    private static final double ARRIVE_DISTANCE_SQR = 2.25D;
    /**
     * A2 Stage 6E · FIX B · distancia (al cuadrado) dentro de la cual el ejecutor DELEGA una transicion BREAK/BRIDGE
     * al nodo. Más lejos, primero CAMINA hacia el nodo. Es 2.5 bloques: la misma envolvente acotada de acción que
     * usa el ente para romper, con el margen justo para que la delegación no se quede corta.
     */
    private static final double ALCANCE_DE_ACCION_SQR = 6.25D;
    // ============================================================================================
    // A2 · TÚNEL SUBORDINADO. `TunnelActionPlanner` (puro) produce pasos de terreno validados; este
    // ejecutor es la ÚNICA autoridad que los ejecuta: un break por tick con lease, y el movimiento
    // final solo lo emite él. El planificador nunca mueve a la entidad ni modifica el mundo.
    // Medido (`a2n-20261121-123432`): IDX3/5 quedan en pozos 1x1 donde el grafo no puede representar
    // el estado posterior al BREAK (el destino sigue sólido) y no existe stand de ataque alcanzable.
    // ============================================================================================
    /** Tope de pasos por activación (cada paso abre 2 bloques y avanza 1 celda). */
    private static final int MAX_TUNEL_PASOS_POR_PLAN = TunnelPlanPolicy.MAX_STEPS_PER_PLAN;
    /**
     * A2 · CALLEJÓN SOSTENIDO. Ticks de PLANNING en la MISMA celda sin avance de la búsqueda global ni del
     * escalonado antes de pedir un plan de túnel. Acotado: no sustituye ningún presupuesto, solo evita que un
     * frente sólido deje AL EJECUTOR esperando para siempre (medido: IDX3 en CADENCE_WAIT durante miles de ticks).
     */
    private static final int TUNNEL_STALL_TICKS = 240;
    private static final int OSCILLATION_WINDOW = 24;

    private final Mob mob;
    private final NavObstruction.Policies policies;

    private NavState state = NavState.IDLE;
    private NavRoute route;
    private int routeIndex;
    /**
     * Exclusión de acciones de terreno: una única herramienta por entidad y tick.
     * El lease se consume al autorizar el intento, incluso si la primitiva no logra modificar el mundo.
     */
    private long traversalActionLeaseTick = Long.MIN_VALUE;
    private boolean traversalActionLeaseUsed;
    private BlockPos objective;
    /** El objetivo local procede de una transición BUILD y puede no tener soporte hasta ejecutar esa transición. */
    private boolean objetivoPermiteConstruccion;
    /** La transicion del waypoint local: BREAK/BRIDGE son nodos de accion, no celdas de pie. */
    private NavRoute.Transition objetivoTransicionLocal;
    /** Solid block targeted by a global BREAK waypoint; never used as a movement objective. */
    private BlockPos objetivoAccionLocal;
    /** Exact placement cell proving that an unsupported local objective came from a validated global BUILD edge. */
    private BlockPos objetivoAccionConstruccion;
    /**
     * A2 FASE 4 · el objetivo actual es TACTICO (orilla, escape) y NO es la mision. Mientras este activo,
     * llegar a el no puede declarar {@link NavState#ARRIVED}: se vuelve a la mision.
     */
    private boolean objetivoTactico;
    /** A2 FASE 2 · rearmes terminales consumidos por la mision actual y su enfriamiento. */
    private int rearmesTerminales;
    private long nextRearmAt;
    /** A2 FASE 2 · motivo del ultimo fallo terminal (para diagnostico y para decidir si el rearme sirve). */
    private String motivoFalloTerminal = "-";
    /** A2 REMEDIACIÓN 2 · el motivo terminal ya quedo fijado: no se vuelve a mutar ni a evaluar. */
    private boolean falloDefinitivoTerminal;
    /**
     * A2 FASE 2 · identidad de la MISION RAICES (la ultima etapa de una cadena, o el destino de setObjective).
     * Las etapas intermedias de la MISMA aldea no son misiones nuevas: no deben reiniciar el contador de rearmes.
     */
    private BlockPos misionRaiz;
    /** Mientras se aplica una cadena, setObjective no debe tratar la etapa como una mision nueva. */
    private boolean enCadenaDeEtapas;
    private boolean noReiniciarRearmes;
    private BlockPos plannedFrom;
    /** Original route start preserved while the temporary DROP take-off recovery route is active. */
    private BlockPos plannedFromDropSuspendido;
    private boolean plannedFromDropSuspendidoCapturado;

    /** Suspended main route while a physically validated path returns to a DROP take-off cell. */
    private NavRoute rutaDropSuspendida;
    private int indiceRutaDropSuspendida;
    private BlockPos objetivoRecuperacionOrigenDrop;
    private boolean recuperacionOrigenDropActiva;
    /** Adjacent supported source cell selected from the real AABB footprint while recovering a DROP take-off. */
    private BlockPos fuenteDropPendiente;
    private long inicioSnapFuenteDrop = -1L;
    private BlockPos fuenteDropSinCandidatoDesde;
    private long inicioEsperaFuenteDropSinCandidato = -1L;
    /** Backoff for a failed bounded exact take-off search; the suspended strategic route stays intact. */
    private long nextDropRecoveryPlanAt;
    /** A2 · rechazos consecutivos del mismo take-off de DROP: al tercero se abandona la arista, no se reintenta. */
    private int dropTakeoffRechazos;
    private BlockPos dropTakeoffRechazoClave;

    private int lastRouteSignature;
    /** A2 · rechazos consecutivos de la MISMA firma y reintentos forzados de esa ruta (anti-deadlock). */
    private int duplicateRechazos;
    private int reintentosForzados;
    private static final int MAX_DUPLICATE_REJECTIONS = 3;
    private static final int MAX_FORCED_ROUTE_RETRIES = 2;
    private int replans;
    private int recoveryActions;
    private long nextPlanAt;
    private StairBuildResult ultimoResultadoEscalon = StairBuildResult.NO_PROGRESS;
    private long nextRecoveryAt;
    private int recoveryCooldown = RECOVERY_COOLDOWN_TICKS;

    private final Deque<BlockPos> recentCells = new ArrayDeque<>();
    private boolean oscillating;
    private int recentCellOccurrences;
    private boolean postSnapFirstPlanPending;
    private boolean postSnapRouteStartPending;
    private long lastSnapResetTick = -1L;
    private boolean dropDescentCommanded;
    private boolean dropDescentStarted;
    private boolean dropDescentStalledReported;
    private double dropDescentStartY;
    private long dropDescentCommandTick;
    private boolean dropEdgeStepActive;
    private BlockPos dropEdgeStepTakeoff;
    private BlockPos dropEdgeStepLanding;
    private int dropEdgeStepTicks;
    private int dropEdgeStepNoProgress;
    private double dropEdgeStepBestDistance;
    /** Pending/active owner for the supported non-adjacent DROP approach waypoint. */
    private boolean dropApproachActive;
    private boolean dropApproachMoveOwned;
    private BlockPos dropApproachWaypoint;
    private BlockPos dropApproachLanding;
    private int dropApproachTicks;
    private int dropApproachNoProgress;
    private double dropApproachBestDistance;

    // ---- A2 · TÚNEL SUBORDINADO (el planificador propone, este ejecutor dispone) ----
    /** Plan validado en curso; inmutable una vez aceptado. */
    private java.util.List<TunnelActionPlanner.Step> tunnelPlan;
    /** Paso y fase en curso dentro del plan. */
    private TunnelExecutionPolicy.Cursor tunnelCursor;
    /** Fecha límite de la fase actual: acota la espera al actuador y al movimiento físico. */
    private long tunnelPhaseDeadline;
    /** Enfriamiento entre planes de túnel. */
    private long tunnelNextPlanAt;
    /** Planes pedidos en la misión actual (tope duro). */
    private int tunnelPlansThisMission;
    /** Pasos VERIFICADOS en la misión actual (tope duro). */
    private int tunnelStepsThisMission;
    /** Diagnóstico acotado: último motivo de descarte de un plan de túnel. */
    private String tunnelLastDiscard = "-";

    // ---- A2 · ESCALÓN DE ASCENSO (subir 1 bloque minando la cara y colocando el escalón) ----
    /** Celda de pies donde empezó el ascenso en curso, o null si no hay ninguno. */
    private BlockPos ascensoOrigen;
    /** Celda de la cara donde se colocará el escalón (y desde la que se sube). */
    private BlockPos ascensoBloque;
    /** El bloque del escalón ya está colocado: solo falta subir físicamente. */
    private boolean ascensoColocado;
    private long ascensoDeadline;
    private long ascensoNextAt;
    private int ascensosEnMision;
    private int ascensoFallos;
    private String ascensoUltimo = "-";
    /** Último objetivo de avance codicioso entregado (evita re-elegirlo cada tick). */
    private BlockPos avanceObjetivo;
    /** Enfriamiento del avance codicioso (barrido en cruz; no se repite cada tick). */
    private long avanceNextAt;
    /** Tick en que se entregó el último objetivo de avance (caduca si no se alcanza). */
    private long avanceObjetivoEn;
    private static final int MAX_ASCENSOS_MISION = 12;
    private static final int ASCENSO_FASE_MAX_TICKS = 300;
    private static final int ASCENSO_COOLDOWN_TICKS = 30;
    private static final int ASCENSO_MAX_FALLOS = 4;
    /** A2 · detección de callejón sostenido: celda y señales de progreso de la última observación. */
    private BlockPos celdaEnEsperaDePlan;
    private int ticksEnEsperaDePlan;
    private int globalExpansionesEnEspera = -1;
    private int escalonesEnEspera = -1;

    // ---- Instrumentación ----
    private int plansRun;
    private int expansionsLast;
    private int expansionsTotal;
    private int routesRejected;
    private int recoveriesRun;
    private int budgetHits;
    /**
     * A2 FASE 3 · reintentos por presupuesto agotado y su espera creciente. `budgetRetries` es la racha
     * actual; `presupuestosSinProgresoTotal` es el contador DURO que NO se reinicia al rearmar: solo lo
     * reinician el progreso medible, una mision nueva o un cambio de terreno. Asi el rearme de la Fase 2 no
     * puede convertir "presupuesto agotado sin progreso" en un bucle de reintentos.
     */
    private int budgetRetries;
    private int presupuestosSinProgresoTotal;
    private int budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
    private long nextBudgetRetryAt;
    /**
     * A2 FASE 3 · mejor distancia a la mision observada desde el ultimo reinicio del contador de presupuesto.
     * Si el ente se acerca de verdad, el contador vuelve a cero: el presupuesto agotado sigue siendo
     * inconcluyente mientras haya progreso medible.
     */
    private double mejorDistEnPresupuesto = Double.MAX_VALUE;
    /**
     * A2 FASE 2 · intentos consecutivos con un objetivo local NO utilizable, y mejor distancia observada.
     * Es la red que impide el `PLANNING` infinito cuando `escalonarObjetivoLocal` devuelve true sin cambiar
     * nada: sin ella, `tryPlan` salia cada tick sin planificar y el ente quedaba en PLANNING para siempre.
     */
    private int intentosObjetivoInvalido;
    private double mejorDistObjetivoInvalido = Double.MAX_VALUE;
    private static final int MAX_INTENTOS_OBJETIVO_INVALIDO = 10;
    /** A2 diagnostico: resumen acotado del ULTIMO plan (una linea). */
    private String lastPlanSummary = "-";
    /** A2 Stage 4K: motivo por el que la exhaucion de presupuesto se enruto a construccion. */
    private String reconstruccionEnrutada = "-";
    /** A2 diagnóstico: cuántas veces se ha enviado un objetivo y cuándo fue el último. */
    private int objectiveSubmissions;
    private long lastSubmissionTick = -1L;
    /** A2 diagnóstico: cuántas veces se ha llamado a tick() y cuándo la última. */
    private int tickCalls;
    private long lastTickAt = -1L;
    /** A2 diagnóstico: cuántas veces se ha enviado movimiento al juego y el último destino. */
    private int moveSubmissions;
    private String lastMoveTarget = "-";
    /** A2 Run22: ultimo comando de movimiento trazado, con cadencia acotada. */
    private String lastMoveTraceKey = "-";
    private long lastMoveTraceTick = -1L;
    /** A2 Phase 23: throttle physical DROP landing diagnostics to one event per 10 ticks. */
    private long lastDropLandingTraceTick = -1L;
    private double lastMoveTraceX = Double.NaN;
    private double lastMoveTraceY = Double.NaN;
    private double lastMoveTraceZ = Double.NaN;

    /** A2 diagnóstico: instantánea acotada para el arnés. */
    public String diag() {
        return "objSubs=" + objectiveSubmissions + " lastSubT=" + lastSubmissionTick
                + " ticks=" + tickCalls + " lastTickT=" + lastTickAt
                + " moves=" + moveSubmissions + " ultimoMov=" + lastMoveTarget
                + " plans=" + plansRun + " expUlt=" + expansionsLast + " expTot=" + expansionsTotal
                + " rechazadas=" + routesRejected + " rec=" + recoveriesRun + " recAcc=" + recoveryActions
                + " presupuesto=" + budgetHits + " reintPresup=" + budgetRetries + "/" + MAX_BUDGET_RETRIES
                + " rearmes=" + rearmesTerminales + "/" + MAX_TERMINAL_REARMS
                + " tactico=" + objetivoTactico + " motivoFallo={" + motivoFalloTerminal + "}"
                + " espera=" + budgetBackoff
                + " construccion=" + reconstruccionEnrutada + " ultimoPlan={" + lastPlanSummary + "}"
                + " etapa=" + indiceDeEtapa + "/" + cadenaEnviada + " trans=" + ultimaTransicionDeEtapa
                + " ruta=" + (route == null ? "-" : "n" + route.length() + "/sig" + route.signature())
                + " idx=" + routeIndex;
    }

    public NavExecutor(Mob mob, NavObstruction.Policies policies) {
        this.mob = mob;
        this.policies = policies;
    }

    // ------------------------------------------------------------------ API para los goals

    /**
     * Fija (o refresca) el objetivo. Solo replanifica si el objetivo se ha movido más de
     * {@link #OBJECTIVE_MOVE_THRESHOLD_SQR}: así un objetivo móvil no provoca un recálculo por tick.
     */
    /**
     * A2 Stage 4Q · GEOMETRIA CONSTRUIDA: celdas que el ejecutor ha colocado Y verificado. Se pasan al planificador
     * para que formen parte de la RUTA ACEPTADA (no son un efecto secundario ciego) y sobreviven al recálculo.
     */
    private final java.util.Set<BlockPos> construidas = new java.util.HashSet<>();

    /** A2 Stage 4Q · rastro de construccion/travesia para aceptacion (sin falsos positivos). */
    private int bloquesColocadosVerificados;
    private int bloquesTravesados;
    private String ultimoConstruido = "-";
    private long ultimoConstruidoTick = -1L;
    private String ultimaTravesia = "-";
    private long ultimaTravesiaTick = -1L;
    /** Ultima celda construida cuya travesia ya se confirmo (evita contar dos veces la misma). */
    private BlockPos travesadaConfirmada;

    /** A2 Stage 4Q · primitiva de colocacion inyectada (la del ente). Devuelve true si coloco y quedo verificado. */
    public interface Placement {
        boolean place(BlockPos cell);
    }

    private Placement placement;

    public void setPlacement(Placement placement) {
        this.placement = placement;
    }

    /** A2 Stage 4Q · aceptacion: bloques construidos verificados y cuantos ha travesado el propio ente. */
    public String construccionDiag() {
        return "construidas=" + construidas.size() + " colocadoVerif=" + bloquesColocadosVerificados
                + " travesados=" + bloquesTravesados
                + " ultimoConstruido=" + ultimoConstruido + "@" + ultimoConstruidoTick
                + " ultimaTravesia=" + ultimaTravesia + "@" + ultimaTravesiaTick;
    }

    public int bloquesTravesados() {
        return bloquesTravesados;
    }

    /**
     * A2 Stage 6E · copia ORDENADA de la geometría construida y verificada por este plan. La usa la regresión para
     * comprobar, bloque a bloque, que existe de verdad en el mundo, que no cayó dentro del cuerpo del ente y que
     * tenía soporte real en el momento de colocarse. Es una copia: no permite mutar el estado del ejecutor.
     */
    public java.util.List<BlockPos> celdasConstruidas() {
        java.util.List<BlockPos> copia = new java.util.ArrayList<>(construidas);
        copia.sort((a, b) -> {
            int cy = Integer.compare(a.getY(), b.getY());
            if (cy != 0) {
                return cy;
            }
            int cx = Integer.compare(a.getX(), b.getX());
            return cx != 0 ? cx : Integer.compare(a.getZ(), b.getZ());
        });
        return java.util.List.copyOf(copia);
    }

    /** A2 Stage 6E · cuántos escalones de este plan ha PISADO ya el ente (dependencia de travesía). */
    public int escalonesTravesadosDelPlan() {
        return escalonesTravesadosSet.size();
    }

    // ============================================================================================
    // A2 Stage 6A · CONSTRUCCION COMO PLAN CONECTADO (aprobado por el dueño).
    //
    // CONTRATO. Un `PlanDeConstruccion` representa una secuencia CONECTADA de acciones de construir y
    // moverse para salvar un desnivel que el movimiento normal no cubre. Estados por escalon:
    //
    //   NOT_STARTED -> PLACING -> PLACED_VERIFIED -> TRAVERSED
    //
    // Reglas del contrato (cada una atada a un fallo medido antes):
    //  1. La MISION (objective) no se toca: sigue siendo el destino original. El plan solo produce
    //     SUB-OBJETIVOS temporales y, al agotarse, se vuelve a planificar hacia la mision.
    //  2. Un escalon solo puede colocarse donde el mundo REAL cumple las precondiciones de la primitiva
    //     de colocacion (celda aire, dos huecos encima, bloque con colision debajo, sin proteccion).
    //  3. Un escalon NUNCA se apoya en un bloque que aun no existe: `construidas` solo contiene bloques
    //     COLOCADOS Y VERIFICADOS, jamás planificados.
    //  4. `PLACED_VERIFIED` NO autoriza el escalon siguiente: hace falta `TRAVERSED` (la entidad ocupa de
    //     verdad la celda superior del bloque, en un tick POSTERIOR y con el bloque presente).
    //  5. El ancla de partida NO cuenta como escalon construido.
    //  6. Presupuesto y cadencia acotados; si no se puede colocar, se retrocede (no se reintenta sin fin).
    // ============================================================================================
    public enum FaseEscalon { NOT_STARTED, PLACING, PLACED_VERIFIED, TRAVERSED }

    private static final int MAX_ESCALONES_POR_PLAN = 24;
    private static final int ESPERA_ENTRE_ESCALONES = 10;
    /**
     * A2 Stage 6D · FIX 4 · PRESUPUESTO PROPIO DE CONSTRUCCION.
     * Medido en `a2n-20261016-142948`: la busqueda de escalon se intento UNA SOLA VEZ en toda la corrida. La
     * causa fue doble: `esperaEscalon` solo bajaba cuando se llamaba a `pasoDeConstruccion` (una vez por
     * recuperacion, >=40 ticks), y el tope generico `MAX_RECOVERY_ACTIONS=6` ponia al ejecutor en FAILED antes de
     * que la espera terminara. La construccion pasa a tener su PROPIO presupuesto y una cadencia POR TICK.
     */
    private static final int MAX_INTENTOS_CONSTRUCCION = 40;
    /** Tope DURO absoluto de intentos de construccion en un plan: nunca se reinicia. */
    private static final int MAX_INTENTOS_CONSTRUCCION_TOTAL = 160;

    private BlockPos planMision;          // destino original (mision)
    private BlockPos escalonActual;       // celda donde se coloca el bloque del escalon en curso
    private FaseEscalon faseEscalon = FaseEscalon.NOT_STARTED;
    private int escalonesVerificados;
    private int escalonesTravesados;
    /** A2 Stage 6B · bloques construidos que la entidad ya PISO: unicos validos como soporte construido. */
    private final java.util.Set<BlockPos> escalonesTravesadosSet = new java.util.HashSet<>();
    /** A2 Stage 6D · FIX 4 · cadencia POR TICK y presupuesto de intentos del ciclo de construccion. */
    private long nextConstruccionAt;
    private int intentosConstruccion;
    private int intentosConstruccionTotal;
    private boolean construccionAgotada;
    /** A2 Stage 6E · FIX B · nodo de ruta sobre el que se esta actuando (BREAK/BRIDGE) y ultimo resultado real. */
    private BlockPos nodoDeRecuperacion;
    /** BREAK approach: primer bloque opaco al objetivo, delegado por el mismo RecoveryAction/gate de producción. */
    private BlockPos nodoDeRecuperacionOverride;
    private String ultimaAccionDeRuta = "-";
    /** A2 Stage 6F · estado del descenso explicito: intentos, fallos, ultimo descrito y ultimo rechazo de candidato. */
    private int intentosDeDescenso;
    private int fallosDeDescenso;
    private String ultimoDescenso = "-";
    private String ultimoRechazoDeDescenso = "-";
    /** A2 Stage 6F · fallos seguidos sobre el escalón pendiente y si su descenso ya se declaró imposible. */
    private int fallosMismoEscalon;
    private boolean descensoBloqueado;
    private long tickUltimaColocacion = -1L;
    private String motivoConstruccion = "-";
    private String ultimoFalloConstruccion = "-";

    /** Diagnostico del plan de construccion. */
    public String planConstruccionDiag() {
        return "fase=" + faseEscalon + " escalon=" + (escalonActual == null ? "-" : escalonActual.toShortString())
                + " verificados=" + escalonesVerificados + " travesados=" + escalonesTravesados
                + " intentos=" + intentosConstruccion + "/" + MAX_INTENTOS_CONSTRUCCION
                + " total=" + intentosConstruccionTotal + " agotada=" + construccionAgotada
                + " busquedas=" + busquedasConstruccion + " ultimaBusqueda={" + ultimaBusqueda + "}"
                + " motivo=" + motivoConstruccion + " fallo=" + ultimoFalloConstruccion;
    }

    public int escalonesTravesados() {
        return escalonesTravesados;
    }

    /**
     * A2 Stage 6D · CRITERIOS DE ACEPTACION del plan de construccion, en una linea, para el arnes.
     * Ninguno de estos booleanos sustituye a la medicion: la aceptacion exige que el MISMO ente coloque y
     * verifique un bloque, lo TRAVESE fisicamente en un tick posterior y llegue al destino original.
     */
    public String aceptacionDiag() {
        return "planCreado=" + (busquedasConstruccion > 0)
                + " bloqueColocadoVerificado=" + (bloquesColocadosVerificados > 0)
                + " escalonesVerificados=" + escalonesVerificados
                + " traversalFisica=" + (bloquesTravesados > 0)
                + " escalonesTravesados=" + escalonesTravesados
                + " construccionAgotada=" + construccionAgotada
                + " primerFalloConstruccion=" + ultimoFalloConstruccion;
    }

    public FaseEscalon faseEscalon() {
        return faseEscalon;
    }

    /**
     * A2 Stage 6D · Direcciones de descenso SIN vectores cero y SIN repetidos, conservando el orden de
     * preferencia (primero hacia la mision). Medido en `a2n-20261016-142948`: con `sz=0` la lista cruda
     * degeneraba en `{sx,0}` tres veces y `{0,0}` dos veces, asi que 15 evaluaciones cubrian solo 6 celdas
     * distintas. La deduplicacion no cambia QUE celdas se consideran, solo elimina trabajo repetido.
     */
    private static int[][] direccionesDeDescenso(final int sx, final int sz) {
        int[][] crudas = {{sx, sz}, {sx, 0}, {0, sz}, {-sx, 0}, {0, -sz}, {sx, -sz}, {-sx, sz}};
        boolean[][] visto = new boolean[3][3];
        java.util.List<int[]> salida = new java.util.ArrayList<>(crudas.length);
        for (int[] d : crudas) {
            if (d[0] == 0 && d[1] == 0) {
                continue; // vector cero: no es un paso horizontal
            }
            int ix = d[0] + 1;
            int iz = d[1] + 1;
            if (visto[ix][iz]) {
                continue; // repetido: ya se examino exactamente esa celda y nivel
            }
            visto[ix][iz] = true;
            salida.add(d);
        }
        return salida.toArray(new int[0][]);
    }

    // ============================================================================================
    // A2 Stage 6F · DESCENSO EXPLICITO. Una bajada se DESCRIBE antes de ejecutarla: de dónde se sale,
    // dónde se aterriza, con qué soporte, con qué holgura, con qué desplazamiento y si el aterrizaje es
    // terreno natural o un escalón verificado de este plan. Si algo de eso no se puede establecer, el
    // descenso se RECHAZA con su motivo: no se ordena movimiento y la ruta NO avanza.
    //
    // Motivo medido (`a2n-20261016-174835`): el plan colocó el escalón `119,82,-8` (aterrizaje `119,83,-8`)
    // DEBAJO del escalón que el ente estaba pisando (`119,85,-8`). La comprobación de entonces solo miraba
    // `bajada <= caidaSegura` (3), así que el descenso se aceptaba… pero el propio escalón pisado tapaba la
    // columna: el ente no podía caer hasta el aterrizaje y `moveTo` se repitió 73 veces sin moverse.
    // ============================================================================================
    private static final int MAX_INTENTOS_DESCENSO = 40;
    /**
     * A2 Stage 6F · fallos de descenso sobre el MISMO escalón antes de declarar el plan de bajada BLOQUEADO.
     * Medido en `a2n-20261016-183226`: tras colocar y pisar el escalón `119,83,-8`, el plan colocó el siguiente en
     * `118,81,-8` con aterrizaje `118,82,-8`, pero la recuperación genérica (el puente de la casa) había tendido un
     * tablón en `118,84,-8` que tapaba el camino de caída: 36 rechazos con el mismo motivo y el ejecutor terminando
     * en FAILED sin llegar al destino. Ahora, tras 3 fallos del MISMO escalón, el plan de descenso se declara
     * BLOQUEADO (con su motivo) y suelta al ejecutor para que la ruta siga (BREAK/DROP), en vez de insistir.
     */
    private static final int MAX_FALLOS_DE_DESCENSO_MISMO_ESCALON = 3;

    /**
     * @param salida     celda desde la que arranca la bajada (a la altura de los pies del ente, en la columna del aterrizaje)
     * @param aterrizaje celda de PIE resultante
     * @param rechazo    motivo EXACTO del rechazo, o {@code null} si el descenso es válido y ejecutable
     */
    public record Descenso(BlockPos salida, BlockPos aterrizaje, int dx, int dy, int dz,
                           boolean aterrizajeConstruido, String rechazo) {
        public boolean valido() {
            return rechazo == null;
        }

        public String resumen() {
            return "salida=" + salida.toShortString() + " aterrizaje=" + aterrizaje.toShortString()
                    + " dx=" + dx + " dy=" + dy + " dz=" + dz
                    + (aterrizajeConstruido ? " aterrizaje=CONSTRUIDO" : " aterrizaje=NATURAL")
                    + (valido() ? " OK" : " RECHAZADO(" + rechazo + ")");
        }
    }

    /**
     * A2 Stage 6F · ¿es EJECUTABLE físicamente este descenso? Se comprueba, en orden: que la columna sea contigua,
     * que se BAJE, la caída segura, la holgura de pies y cuerpo del aterrizaje, el CAMINO DE CAÍDA despejado entre la
     * salida y el aterrizaje, y —solo cuando se va a ejecutar de verdad— el soporte con colisión y que el aterrizaje
     * construido esté VERIFICADO por este plan.
     *
     * @param exigirSoporteReal {@code true} al ejecutar (el bloque ya existe); {@code false} al planificar un candidato
     *                          (el soporte lo creará la propia colocación)
     */
    private Descenso describirDescenso(final BlockPos aterrizaje, final boolean construido,
                                       final boolean exigirSoporteReal) {
        return describirDescenso(aterrizaje, construido, exigirSoporteReal, null);
    }

    private Descenso describirDescenso(final BlockPos aterrizaje, final boolean construido,
                                       final boolean exigirSoporteReal, final BlockPos origenPlan) {
        BlockPos yo = mob.blockPosition();
        BlockPos salida = new BlockPos(aterrizaje.getX(), yo.getY(), aterrizaje.getZ());
        int dx = aterrizaje.getX() - yo.getX();
        int dz = aterrizaje.getZ() - yo.getZ();
        int dy = aterrizaje.getY() - yo.getY();
        // A2 Edit 17 · CONTIGUIDAD con ORIGEN PLANIFICADO (opcional) y guarda de ejecutabilidad.
        // El camino vivo (dx/dz desde mob.blockPosition()) se conserva intacto. El camino planificado solo
        // puede AÑADIR una aceptacion, nunca saltarse las comprobaciones posteriores, y exige que el ente
        // siga estando a <=1 bloque (X/Z/Y) de la celda de despegue planificada, para no validar geometria
        // que el ente ya no puede ejecutar. En indice 0, o sin origen verificado, se usa solo el camino vivo.
        boolean contiguoVivo = NavDropTransitionPolicy.aterrizajeContiguo(yo.getX(), yo.getZ(),
                aterrizaje.getX(), aterrizaje.getZ());
        int dxPlan = 0;
        int dzPlan = 0;
        boolean contiguoPlan = false;
        boolean cercaOrigen = false;
        if (origenPlan != null) {
            dxPlan = aterrizaje.getX() - origenPlan.getX();
            dzPlan = aterrizaje.getZ() - origenPlan.getZ();
            contiguoPlan = NavDropTransitionPolicy.aterrizajeContiguo(origenPlan.getX(), origenPlan.getZ(),
                    aterrizaje.getX(), aterrizaje.getZ());
            cercaOrigen = NavDropTransitionPolicy.origenFisicamenteAlcanzado(
                    mob.onGround(), esPieValido(yo), NavObstruction.hasSupport(mob.level(), yo),
                    NavObstruction.fitsActualAabb(mob.level(), mob),
                    yo.getX(), yo.getY(), yo.getZ(),
                    origenPlan.getX(), origenPlan.getY(), origenPlan.getZ());
        }
        if (!(contiguoVivo || (contiguoPlan && cercaOrigen))) {
            String motivo = "el aterrizaje no esta en una columna contigua (vivo dx=" + dx + " dz=" + dz
                    + (origenPlan == null ? " plan=NONE"
                            : " plan dx=" + dxPlan + " dz=" + dzPlan + " cercaOrigen=" + cercaOrigen)
                    + ")";
            if (NavTrace.on()) {
                NavTrace.ev("DROP_CONTIG", "uuid=" + mob.getUUID() + " idxRuta=" + routeIndex
                        + " origen=" + (origenPlan == null ? "NONE" : origenPlan.toShortString())
                        + " aterrizaje=" + aterrizaje.toShortString() + " vivo=" + yo.toShortString()
                        + " dxVivo=" + dx + " dzVivo=" + dz
                        + " dxPlan=" + (origenPlan == null ? "NONE" : String.valueOf(dxPlan))
                        + " dzPlan=" + (origenPlan == null ? "NONE" : String.valueOf(dzPlan))
                        + " cercaOrigen=" + (origenPlan == null ? "NONE" : String.valueOf(cercaOrigen))
                        + " contiguidad=RECHAZA motivo={" + motivo + "}");
            }
            return new Descenso(salida, aterrizaje, dx, dy, dz, construido, motivo);
        }
        if (dy > 0) {
            return new Descenso(salida, aterrizaje, dx, dy, dz, construido, "el aterrizaje esta por ENCIMA (dy=" + dy + ")");
        }
        if (-dy > policies.maxSafeDrop()) {
            return new Descenso(salida, aterrizaje, dx, dy, dz, construido,
                    "la caida " + (-dy) + " supera la caida segura " + policies.maxSafeDrop());
        }
        // A2 REMEDIACIÓN DROP · HOLGURA POR COLISIÓN, NO POR `isAir()`.
        // Medido en a2n-20261019-200235: el aterrizaje 694,70,662 era `snow` (colisión vacía) y
        // 629,90,503 era `fern` (colisión vacía); ambos tienen soporte sólido debajo y el zombi cabe
        // de pie, pero `isAir()` los rechazaba y el DROP se repetía hasta FAILED. Se permite solo
        // vegetación/capa sin colisión NI fluido NI peligro; un bloque con colisión sigue rechazado.
        if (!sinFluidoNiPeligro(aterrizaje) || !sinFluidoNiPeligro(aterrizaje.above())
                || !NavObstruction.fits(mob.level(), mob, aterrizaje)) {
            return new Descenso(salida, aterrizaje, dx, dy, dz, construido,
                    "el aterrizaje no tiene holgura de pies y cuerpo");
        }
        if (exigirSoporteReal && !NavObstruction.hasSolidFace(mob.level(), aterrizaje.below())) {
            return new Descenso(salida, aterrizaje, dx, dy, dz, construido,
                    "el aterrizaje no tiene soporte con colision");
        }
        if (exigirSoporteReal && construido && !construidas.contains(aterrizaje.below())) {
            return new Descenso(salida, aterrizaje, dx, dy, dz, construido,
                    "el aterrizaje construido no esta verificado por este plan");
        }
        // CAMINO DE CAIDA: la misma regla se reutiliza en el constructor genérico para no colocar
        // escalones cuyo aterrizaje quedaría bloqueado por un puente/obra del asedio.
        String bloqueoColumna = rechazoColumnaCaida(aterrizaje, yo);
        if (bloqueoColumna != null) {
            return new Descenso(salida, aterrizaje, dx, dy, dz, construido, bloqueoColumna);
        }
        return new Descenso(salida, aterrizaje, dx, dy, dz, construido, null);
    }

    /**
     * Devuelve el bloque sólido exacto que obstruye la columna de caída, si existe. Solo se usa para proponer
     * una ruptura local; el ejecutor vuelve a validar alcance, protección, block entity y dureza antes de picar.
     */
    private BlockPos primerBloqueoEnCaminoDeCaida(final BlockPos aterrizaje) {
        BlockPos yo = mob.blockPosition();
        for (int y = aterrizaje.getY() + 1; y <= yo.getY(); y++) {
            BlockPos celda = new BlockPos(aterrizaje.getX(), y, aterrizaje.getZ());
            if (!mob.level().getBlockState(celda).getCollisionShape(mob.level(), celda).isEmpty()) {
                return celda;
            }
        }
        return null;
    }

    /** Celda sin fluido y sin bloques peligrosos aunque su colisión sea vacía (fuego, lava, zarzas...). */
    private boolean sinFluidoNiPeligro(final BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return !esPeligrosoParaAterrizaje(state);
    }

    /** Devuelve el rechazo exacto de la columna de caída para un escalón aún no colocado, o null si está libre. */
    public String rechazoColumnaCaidaParaEscalon(final BlockPos celdaBloque) {
        if (celdaBloque == null) {
            return "escalon nulo";
        }
        return rechazoColumnaCaida(celdaBloque.above(), mob.blockPosition());
    }

    private String rechazoColumnaCaida(final BlockPos aterrizaje, final BlockPos origen) {
        java.util.OptionalInt yBloqueada = NavDropColumnPolicy.primerBloqueoY(
                aterrizaje.getY(), origen.getY(), y -> {
                    BlockPos celda = new BlockPos(aterrizaje.getX(), y, aterrizaje.getZ());
                    return !sinFluidoNiPeligro(celda)
                            || !mob.level().getBlockState(celda).getCollisionShape(mob.level(), celda).isEmpty();
                });
        return yBloqueada.isPresent()
                ? "el camino de caida esta bloqueado en y=" + yBloqueada.getAsInt()
                : null;
    }

    /** Misma lista de peligros que usa el asedio para no considerar segura una celda sin colisión. */
    private static boolean esPeligrosoParaAterrizaje(final BlockState state) {
        return state.is(BlockTags.FIRE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE);
    }

    /**
     * A2 Stage 6F · un candidato de escalón solo vale si ADEMÁS su posición de pie es alcanzable por un descenso
     * ejecutable (camino de caída libre). Evita colocar escalones que el ente no puede pisar nunca.
     */
    private boolean descensoValido(final BlockPos celdaBloque) {
        Descenso d = describirDescenso(celdaBloque.above(), true, false);
        if (!d.valido()) {
            ultimoRechazoDeDescenso = celdaBloque.toShortString() + " -> " + d.rechazo();
            return false;
        }
        // A2 Stage 6F · un escalón de CONSTRUCCIÓN tiene que DESCENDER. Medido en `a2n-20261016-182723`: en la
        // llanura, ya a la altura de la misión, el plan seguía colocando escalones al MISMO nivel (dy=0) que
        // allanaban el terreno y consumían el presupuesto sin bajar nada. Un paso que no baja no es un descenso: si
        // el ente solo necesita caminar un nivel abajo, lo hace el movimiento ordinario de la ruta.
        if (d.dy() >= 0) {
            ultimoRechazoDeDescenso = celdaBloque.toShortString() + " -> el escalon no DESCIENDE (dy=" + d.dy() + ")";
            return false;
        }
        return true;
    }

    /**
     * A2 Stage 6D · Descripcion ACOTADA (una linea) de por que una celda no es colocable. Es SOLO descriptiva:
     * la decision autoritativa sigue siendo {@link #esCeldaColocable}, que es quien acepta o rechaza de verdad.
     * Se usa unicamente para la traza de la ultima busqueda (sin spam por tick).
     */
    private String describirRechazo(final BlockPos celda) {
        if (policies.isProtected(celda)) {
            return "isProtected=true";
        }
        if (!policies.canPlace(celda)) {
            return "canPlace=false";
        }
        if (!mob.level().getBlockState(celda).isAir()) {
            return "celda destino no es aire";
        }
        if (!mob.level().getBlockState(celda.above()).isAir()) {
            return "holgura 1 (arriba) no es aire";
        }
        if (!mob.level().getBlockState(celda.above(2)).isAir()) {
            return "holgura 2 (arriba+2) no es aire";
        }
        return "sin soporte con colision debajo";
    }

    /**
     * A2 Stage 6A · Busca UN escalon valido para descender desde la posicion actual hacia `mision`.
     * Solo acepta celdas que cumplan las precondiciones REALES de colocacion; no acepta una celda por estar
     * en la recta entre los extremos. Devuelve la celda o null si no hay ninguna en el alcance acotado.
     * <p>
     * A2 Stage 6D · FIX 1. El alcance vertical del PRIMER escalon ya no es un 3 fijo: se deriva del desnivel
     * REAL hasta la mision y se acota con el presupuesto explicito de escalones del plan. Medido en
     * `a2n-20261016-142948`: la unica celda colocable de la cara (`121,81,-8`) estaba 6 niveles por debajo y
     * quedaba fuera del limite fijo de 3, asi que la busqueda NUNCA la examinaba (15 candidatos, 0 aceptados).
     * <p>
     * La busqueda NO relaja ninguna regla: cada candidato se revalida contra el mundo vivo con
     * {@link #esCeldaColocable}, que ademas de la celda destino comprueba la POSICION DE PIE RESULTANTE
     * (`celda.above()` y `celda.above(2)` libres) y el soporte con colision. No se acepta nada por ser mas
     * bajo ni por estar mas cerca de la mision.
     */
    private BlockPos buscarEscalonDeDescenso() {
        if (placement == null || planMision == null) {
            return null;
        }
        BlockPos yo = mob.blockPosition();
        int desnivelMision = yo.getY() - planMision.getY();
        if (desnivelMision <= 0) {
            return null; // no hay descenso que construir
        }
        // Alcance vertical derivado del desnivel real, con tope explicito (presupuesto de escalones del plan).
        final int maxNivel = Math.max(1, Math.min(desnivelMision, MAX_ESCALONES_POR_PLAN));
        // Direccion horizontal preferente hacia la mision; se prueban primero los pasos que bajan y avanzan.
        int sx = Integer.signum(planMision.getX() - yo.getX());
        int sz = Integer.signum(planMision.getZ() - yo.getZ());
        // A2 Stage 6B · ESCALERA HACIA FUERA. Medido en `a2n-20261016-123326`: ninguna celda de una cara
        // VERTICAL tiene terreno justo debajo, asi que el primer escalon no existia. La estructura que SI es
        // construible con las reglas reales de Minecraft es una escalera que baja avanzando: cada bloque se apoya
        // en el ANTERIOR (ya colocado y PISADO) o en el terreno, y su celda superior queda libre para pisarla.
        // Se recorre por orden de "cuanto baja" y luego "cuanto avanza", exigiendo que la celda destino sea
        // alcanzable desde donde esta la entidad.
        int[][] direcciones = direccionesDeDescenso(sx, sz);
        busquedasConstruccion++;
        candidatosEvaluados = 0;
        primerCandidato = null;
        // A2 Stage 6E · FIX A · ORDEN DE DESCENSO. Stage 6D recorría los niveles de menos a más ("lo más somero
        // primero"), y con soporte lateral eso degenera en una repisa HORIZONTAL: el escalón a la altura de los pies
        // siempre es colocable. Medido: el ente avanzaba sin bajar. Aquí se prefiere el escalón que BAJA: primero el
        // de UN nivel (escalera limpia), después 2 y 3 (siempre dentro de la caída segura) y solo si no hay ninguno,
        // el paso al mismo nivel. El envolvente es el MISMO presupuesto acotado de escalones del plan.
        int[] ordenNiveles = ordenDeDescenso(maxNivel);
        ultimaBusqueda = "nivelesMax=" + maxNivel + " dirs=" + direcciones.length
                + " orden=" + java.util.Arrays.toString(ordenNiveles);
        // Primero: continuar la escalera existente desde el ultimo escalon traversado.
        // (Sin cambios de alcance: no hay evidencia medida de que este tramo necesite otra correccion.)
        for (BlockPos t : escalonesTravesadosSet) {
            for (int[] d : direcciones) {
                for (int nivel = 1; nivel <= 2; nivel++) {
                    BlockPos cand = new BlockPos(t.getX() + d[0], t.getY() - nivel, t.getZ() + d[1]);
                    candidatosEvaluados++;
                    if (primerCandidato == null) {
                        primerCandidato = cand.immutable();
                    }
                    if (esCeldaColocable(cand) && bajadaSegura(t.getY(), cand) && descensoValido(cand)) {
                        ultimaBusqueda = ultimaBusqueda + " evaluados=" + candidatosEvaluados
                                + " resultado=" + cand.toShortString() + " (escalera)";
                        return cand;
                    }
                }
            }
        }
        // Segundo: primer escalon desde la posicion actual, en orden de descenso y dentro de la caida segura.
        for (int nivel : ordenNiveles) {
            for (int[] d : direcciones) {
                BlockPos objetivo = new BlockPos(yo.getX() + d[0], yo.getY() - nivel, yo.getZ() + d[1]);
                candidatosEvaluados++;
                if (primerCandidato == null) {
                    primerCandidato = objetivo.immutable();
                }
                if (esCeldaColocable(objetivo) && bajadaSegura(yo.getY(), objetivo) && descensoValido(objetivo)) {
                    ultimaBusqueda = ultimaBusqueda + " evaluados=" + candidatosEvaluados
                            + " resultado=" + objetivo.toShortString();
                    return objetivo;
                }
            }
        }
        ultimaBusqueda = ultimaBusqueda + " evaluados=" + candidatosEvaluados
                + " resultado=null primerCandidato=" + (primerCandidato == null ? "-" : primerCandidato.toShortString())
                + " primerRechazo=" + (primerCandidato == null ? "-" : describirRechazo(primerCandidato));
        return null;
    }

    /**
     * A2 Stage 6F · orden de niveles del primer escalón: <b>primero el descenso MÁS HONDO que siga siendo seguro</b>
     * (nivel ≤ 1 + caída segura), después los más someros y por último el paso al mismo nivel.
     * <p>
     * Motivo medido (`a2n-20261016-174835`): con el orden anterior (un nivel primero) el ente quedaba de pie en
     * `119,86` sobre un escalón en `119,85`; ese escalón tapaba la columna, así que ya no había ningún descenso
     * alcanzable (el aterrizaje de `119,82` quedaba con el camino de caída bloqueado) y lo único colocable eran
     * escalones al MISMO nivel: el plan degeneraba en una repisa horizontal. Bajando primero lo más hondo que la
     * caída segura permite, el aterrizaje siguiente está en otra columna y la escalera SÍ progresa hacia abajo.
     */
    private int[] ordenDeDescenso(final int maxNivel) {
        java.util.LinkedHashSet<Integer> orden = new java.util.LinkedHashSet<>();
        int masHondo = Math.max(1, Math.min(maxNivel, 1 + policies.maxSafeDrop()));
        for (int nivel = masHondo; nivel >= 1; nivel--) {
            orden.add(nivel);
        }
        for (int nivel = 1; nivel <= maxNivel; nivel++) {
            orden.add(nivel);
        }
        int[] salida = new int[orden.size()];
        int i = 0;
        for (int nivel : orden) {
            salida[i++] = nivel;
        }
        return salida;
    }

    /**
     * A2 Stage 6E · FIX A · la posición de PIE resultante de colocar en {@code celda} es su celda superior. Se exige
     * que la bajada desde {@code piesDesde} no supere la caída segura: un escalón que el ente no puede pisar sin
     * caerse NO es un escalón (se rechaza, no se debilita el planificador).
     */
    private boolean bajadaSegura(final int piesDesde, final BlockPos celda) {
        int bajada = piesDesde - (celda.getY() + 1);
        return bajada <= policies.maxSafeDrop();
    }

    /** A2 Stage 6D · Instrumentacion acotada de la ultima busqueda de escalon (una linea, sin spam por tick). */
    private int busquedasConstruccion;
    private int candidatosEvaluados;
    private BlockPos primerCandidato;
    private String ultimaBusqueda = "-";

    /**
     * A2 Stage 6B · SOPORTE CON CONSTRUCCION. Un bloque construido NO es soporte valido solo por estar en
     * `construidas`: se exige (1) que exista AHORA en el mundo, (2) que su forma de colision cumpla el requisito
     * real de soporte, (3) que lo haya colocado y verificado este plan, y (5) que la entidad lo haya PISADO.
     * El ancla de partida nunca cuenta como soporte construido.
     */
    private boolean soporteConstruido(final BlockPos debajo) {
        if (!construidas.contains(debajo)) {
            return false; // (3) no lo puso este plan (o no esta verificado)
        }
        net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(debajo);
        if (st.isAir() || st.getCollisionShape(mob.level(), debajo).isEmpty()) {
            return false; // (1) y (2) no existe o no soporta
        }
        return escalonesTravesadosSet.contains(debajo); // (5) PISADO: colocacion != travesia
    }

    /**
     * A2 Stage 6E · FIX A · SOPORTE LATERAL. Un escalón puede apoyarse en una CARA SÓLIDA ya existente (regla real
     * de colocación de Minecraft), pero solo si:
     * <ul>
     *   <li>la cara la ofrece un bloque que EXISTE AHORA y tiene forma de colisión (nada flotante por vecindad);</li>
     *   <li>si esa cara es obra de ESTE plan, el bloque además consta como <b>PISADO</b> (misma dependencia de
     *       travesía que el soporte inferior: colocación ≠ travesía);</li>
     * </ul>
     * El soporte INFERIOR sigue valiendo por sí mismo; esto solo AÑADE una cara lateral válida.
     */
    private boolean soporteLateral(final BlockPos celda) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos vecino = celda.relative(d);
            if (!NavObstruction.hasSolidFace(mob.level(), vecino)) {
                continue; // esa cara no existe: no es soporte
            }
            if (!construidas.contains(vecino) || escalonesTravesadosSet.contains(vecino)) {
                return true; // terreno natural (ya estaba ahí) o bloque del plan YA PISADO
            }
        }
        return false;
    }

    /** A2 Stage 6E · FIX A · ¿el bloque colocado en esa celda caería DENTRO del cuerpo del ente? */
    private boolean invadeAlEnte(final BlockPos celda) {
        return new net.minecraft.world.phys.AABB(celda).intersects(mob.getBoundingBox().inflate(0.05D));
    }

    /** Precondiciones EXACTAS de la primitiva de colocacion, comprobadas contra el mundo real. */
    private boolean esCeldaColocable(final BlockPos celda) {
        if (policies.isProtected(celda) || !policies.canPlace(celda)) {
            return false;
        }
        if (!mob.level().getBlockState(celda).isAir()
                || !mob.level().getBlockState(celda.above()).isAir()
                || !mob.level().getBlockState(celda.above(2)).isAir()) {
            return false;
        }
        // A2 Stage 6E · FIX A · nunca se coloca un bloque dentro del cuerpo del ente.
        if (invadeAlEnte(celda)) {
            return false;
        }
        BlockPos debajo = celda.below();
        net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(debajo);
        boolean terreno = !st.isAir() && !st.getCollisionShape(mob.level(), debajo).isEmpty();
        return terreno || soporteConstruido(debajo) || soporteLateral(celda);
    }

    /**
     * A2 Stage 6A · Un paso del plan de construccion. Devuelve true si hizo algo.
     * Secuencia: buscar escalon -> colocarlo y verificarlo -> esperar a que la entidad lo PISE -> autorizar el
     * siguiente. El escalon siguiente no se autoriza hasta que el anterior consta como TRAVERSED.
     * <p>
     * A2 Stage 6D · FIX 4. Cadencia POR TICK y presupuesto PROPIO: un plan de construccion recibe intentos
     * repetidos y acotados sin que el tope generico de recuperacion lo mate, y cuando su presupuesto se agota el
     * fallo es terminal y explicito (no se oscila indefinidamente con el mismo fallo).
     */
    private boolean pasoDeConstruccion() {
        long ahora = mob.level().getGameTime();
        if (ahora < nextConstruccionAt) {
            return false; // cadencia por tick
        }
        if (intentosConstruccion >= MAX_INTENTOS_CONSTRUCCION
                || intentosConstruccionTotal >= MAX_INTENTOS_CONSTRUCCION_TOTAL) {
            construccionAgotada = true;
            ultimoFalloConstruccion = "presupuesto de construccion agotado (intentos=" + intentosConstruccion
                    + " total=" + intentosConstruccionTotal + ") ultimo=" + ultimoFalloConstruccion;
            return false;
        }
        // 1) Si hay un escalon pendiente de travesia, NO se construye el siguiente.
        if (escalonActual != null && faseEscalon == FaseEscalon.PLACED_VERIFIED) {
            BlockPos aterrizajePendiente = escalonActual.above();
            if (NavStairProgressPolicy.shouldAbandonPendingStep(mob.blockPosition().getY(),
                    aterrizajePendiente.getY(), planMision.getY(), policies.maxSafeDrop())) {
                BlockPos abandonado = escalonActual;
                faseEscalon = FaseEscalon.NOT_STARTED;
                escalonActual = null;
                // El ente ya descendio mas alla de este aterrizaje: no se cuenta como travesia, pero
                // tampoco se le obliga a regresar decenas de bloques para pisar un escalon obsoleto.
                intentosConstruccion = 0;
                intentosDeDescenso = 0;
                fallosMismoEscalon = 0;
                descensoBloqueado = false;
                construccionAgotada = false;
                nextConstruccionAt = ahora;
                ultimoFalloConstruccion = "escalon pendiente abandonado sin contarlo como travesia: "
                        + abandonado.toShortString() + " landing=" + aterrizajePendiente.toShortString()
                        + " mobY=" + mob.blockPosition().getY() + " misionY=" + planMision.getY();
                if (NavTrace.on()) {
                    NavTrace.ev("STAIR_ABANDONED", "uuid=" + mob.getUUID()
                            + " stair=" + abandonado.toShortString()
                            + " landing=" + aterrizajePendiente.toShortString()
                            + " pos=" + mob.blockPosition().toShortString()
                            + " mission=" + planMision.toShortString());
                }
                return true;
            }            boolean pisado = mob.blockPosition().equals(escalonActual.above())
                    && !mob.level().getBlockState(escalonActual).isAir()
                    && ahora > tickUltimaColocacion;
            if (pisado) {
                faseEscalon = FaseEscalon.TRAVERSED;
                escalonesTravesados++;
                escalonesTravesadosSet.add(escalonActual.immutable());
                ultimoConstruido = escalonActual.toShortString();
                // PROGRESO REAL (el ente ha pisado el escalon): se reinicia el presupuesto de intentos y de descenso.
                intentosConstruccion = 0;
                intentosDeDescenso = 0;
                fallosMismoEscalon = 0;
                nextConstruccionAt = ahora + ESPERA_ENTRE_ESCALONES;
            } else {
                // A2 Stage 6F · DESCENSO EXPLICITO: antes de ordenar movimiento se DESCRIBE y se VALIDA la bajada
                // (salida, aterrizaje, holgura, camino de caida, caida segura y soporte/verificacion del aterrizaje).
                BlockPos aterrizaje = escalonActual.above();
                Descenso descenso = describirDescenso(aterrizaje, true, true);
                ultimoDescenso = descenso.resumen();
                if (!descenso.valido()) {
                    ultimoFalloConstruccion = "escalon " + escalonActual.toShortString()
                            + " colocado y verificado pero su aterrizaje NO es alcanzable desde "
                            + mob.blockPosition().toShortString() + ": " + descenso.rechazo()
                            + " (caidaSegura=" + policies.maxSafeDrop() + ")";
                    fallosDeDescenso++;
                    fallosMismoEscalon++;
                    intentosConstruccion++;
                    intentosConstruccionTotal++;
                    nextConstruccionAt = ahora + ESPERA_ENTRE_ESCALONES;
                    if (fallosMismoEscalon >= MAX_FALLOS_DE_DESCENSO_MISMO_ESCALON) {
                        // El plan de bajada por aquí es IMPOSIBLE: se declara BLOQUEADO con su motivo y se suelta al
                        // ejecutor para que la ruta (BREAK/DROP) siga. Acotado: no se insiste más en este escalón.
                        descensoBloqueado = true;
                        ultimoFalloConstruccion = "PLAN DE DESCENSO BLOQUEADO tras " + fallosMismoEscalon
                                + " fallos: " + ultimoFalloConstruccion;
                        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} DESCESO BLOQUEADO: {}",
                                mob.getId(), ultimoFalloConstruccion);
                    }
                    return false;
                }
                fallosMismoEscalon = 0;
                if (descenso.dy() < 0) {
                    intentosDeDescenso++;
                    if (intentosDeDescenso > MAX_INTENTOS_DESCENSO) {
                        ultimoFalloConstruccion = "descenso sin aterrizar tras " + MAX_INTENTOS_DESCENSO
                                + " intentos hacia " + aterrizaje.toShortString() + " (salida="
                                + descenso.salida().toShortString() + ")";
                        fallosDeDescenso++;
                        construccionAgotada = true;
                        return false;
                    }
                }
                // Se le manda caminar hacia la celda de aterrizaje; cuando baje +-1 el propio juego lo deja caer, y la
                // travesia solo se da por buena cuando el ente esta FISICAMENTE en esa celda (comprobacion `pisado`).
                mob.getNavigation().moveTo(aterrizaje.getX() + 0.5D, aterrizaje.getY(),
                        aterrizaje.getZ() + 0.5D, descenso.dy() < 0 ? 0.9D : 1.0D);
                moveSubmissions++;
                lastMoveTarget = aterrizaje.toShortString() + ":DESCENSO(dy=" + descenso.dy() + ")";
                intentosConstruccion++;
                intentosConstruccionTotal++;
                nextConstruccionAt = ahora + ESPERA_ENTRE_ESCALONES;
                return false;
            }
        }
        if (escalonesVerificados >= MAX_ESCALONES_POR_PLAN) {
            ultimoFalloConstruccion = "presupuesto de escalones agotado (" + MAX_ESCALONES_POR_PLAN + ")";
            construccionAgotada = true;
            return false;
        }
        // 2) Buscar y colocar el escalon siguiente (solo tras TRAVERSED del anterior o al empezar).
        intentosConstruccion++;
        intentosConstruccionTotal++;
        BlockPos celda = buscarEscalonDeDescenso();
        if (celda == null) {
            ultimoFalloConstruccion = "sin celda colocable en el alcance (pos=" + mob.blockPosition().toShortString()
                    + " mision=" + planMision.toShortString() + ") " + ultimaBusqueda;
            nextConstruccionAt = ahora + ESPERA_ENTRE_ESCALONES;
            return false;
        }
        escalonActual = celda.immutable();
        faseEscalon = FaseEscalon.PLACING;
        if (!placement.place(escalonActual)) {
            ultimoFalloConstruccion = "la primitiva de colocacion rechazo " + escalonActual.toShortString();
            faseEscalon = FaseEscalon.NOT_STARTED;
            escalonActual = null;
            nextConstruccionAt = ahora + ESPERA_ENTRE_ESCALONES;
            return false;
        }
        // 3) VERIFICACION real releyendo el mundo.
        if (mob.level().getBlockState(escalonActual).isAir()) {
            ultimoFalloConstruccion = "el bloque NO quedo colocado en " + escalonActual.toShortString();
            faseEscalon = FaseEscalon.NOT_STARTED;
            escalonActual = null;
            nextConstruccionAt = ahora + ESPERA_ENTRE_ESCALONES;
            return false;
        }
        construidas.add(escalonActual);
        faseEscalon = FaseEscalon.PLACED_VERIFIED;
        escalonesVerificados++;
        bloquesColocadosVerificados++;
        tickUltimaColocacion = ahora;
        // A2 Stage 6E · el reloj de colocacion tambien alimenta el contador de TRAVESIA FISICA de Stage 4Q. Sin esto
        // `ultimoConstruidoTick` se quedaba en -1 para los escalones del plan de construccion y `bloquesTravesados`
        // no contaba una travesia que SI ocurrio (medido: `escalonesTravesados=1` con `traversalFisica=false`).
        ultimoConstruido = escalonActual.toShortString();
        ultimoConstruidoTick = ahora;
        // PROGRESO REAL (bloque colocado y verificado): se reinicia el presupuesto de intentos.
        intentosConstruccion = 0;
        nextConstruccionAt = ahora + ESPERA_ENTRE_ESCALONES;
        motivoConstruccion = "escalon " + escalonesVerificados + " colocado y verificado";
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] ESCALON #{} colocado y verificado en {} ({} de {})",
                mob.getId(), escalonActual.toShortString(), escalonesVerificados, MAX_ESCALONES_POR_PLAN);
        return true;
    }

    /**
     * A2 Stage 6D · FIX 4 · Un cambio de MISION abre un ciclo de construccion nuevo (presupuesto y contadores
     * frescos). NO se borra `construidas`: la geometria ya colocada y verificada sigue siendo valida y sobrevive
     * al recalculo (Stage 4Q).
     */
    private void reiniciarCicloDeConstruccion() {
        escalonActual = null;
        faseEscalon = FaseEscalon.NOT_STARTED;
        escalonesVerificados = 0;
        escalonesTravesados = 0;
        escalonesTravesadosSet.clear();
        intentosConstruccion = 0;
        intentosConstruccionTotal = 0;
        construccionAgotada = false;
        nextConstruccionAt = 0L;
        // A2 Stage 6F · el ciclo de descenso tambien se cierra con el de construccion.
        intentosDeDescenso = 0;
        fallosMismoEscalon = 0;
        descensoBloqueado = false;
        ultimoDescenso = "-";
        ultimoRechazoDeDescenso = "-";
        motivoConstruccion = "-";
        ultimoFalloConstruccion = "-";
    }

    public void setPlanMision(BlockPos mision) {
        BlockPos nueva = mision == null ? null : mision.immutable();
        if (nueva != null && !nueva.equals(this.planMision)) {
            reiniciarCicloDeConstruccion();
        }
        this.planMision = nueva;
    }

    /** A2 · etapas pendientes: al llegar a la actual se avanza a la siguiente (aproximacion vertical por etapas). */
    private final java.util.ArrayDeque<BlockPos> etapas = new java.util.ArrayDeque<>();
    /** A2 · identidad de la cadena activa: permite que el envio repetido sea idempotente. */
    private int cadenaActivaFirma;
    private long ultimoTickDiagRetencionCadena = Long.MIN_VALUE;
    private int cadenaEnviada;
    private int indiceDeEtapa;
    /** A2 · ultima transicion de etapa observada, con indices, coordenadas y motivo. */
    private String ultimaTransicionDeEtapa = "-";
    /**
     * A2 Stage 7B · MISION FINAL (la que fija el llamante o la ultima etapa de la cadena). NO cambia con el
     * escalonado: es la que mide el progreso y la que usa el plan de construccion.
     */
    private BlockPos misionFinal;
    /** A2 Stage 7B · objetivos locales usados y fallos seguidos eligiendolos (ambos ACOTADOS). */
    private int escalonesDeObjetivo;
    /** One bounded rearm of the local stair budget after a verified terrain break, per mission. */
    private boolean rearmadoPorCambioDeTerreno;
    /** Pending verified terrain change, consumed only if the local stair cap is later reached. */
    private boolean cambioTerrenoPendienteRearme;
    private int rearmesLocalesDeEscalera;
    private static final int MAX_REARMES_LOCALES_DE_ESCALERA = 2;
    private String ultimoStairFallbackDiag = "";
    private int fallosDeEscalonDeObjetivo;
    /** A2 Stage 7B · ultimo escalon local elegido (o el motivo exacto por el que no hubo ninguno). */
    private String ultimoEscalonDeObjetivo = "-";
    /** A2 Stage 7B · posicion de PIE de la mision (memorizada; se invalida al cambiar de mision). */
    private BlockPos misionEnPie;
    private boolean misionEnPieCalculada;
    /** A2 Stage 7C · cache de la ruta global (una por tramo, no por tick) y su diagnostico. */
    private NavPlanner.GlobalResult globalCache;
    /** True while the retained strategic A* frontier is being advanced across server ticks. */
    private boolean globalSearchPending;
    private final NavPlanner.GlobalSearchHandle globalSearchHandle = new NavPlanner.GlobalSearchHandle();
    private int globalSearchLastExpansions;
    private BlockPos globalSearchStart;
    private long lastGlobalPendingTraceTick = Long.MIN_VALUE;
    private BlockPos globalMision;
    private BlockPos globalDesde;
    private int globalExpansionesTotal;
    private String ultimoGlobalDiag = "-";
    /** A2 Stage 7C · re-estadios globales usados para la mision actual (acotado). */
    private int reestadiosGlobales;
    /** A2 Stage 7D · diagnostico de reentrada en `tryPlan` (cuantas veces no se planifico y por que). */
    private String ultimoMotivoDeReentrada = "-";
    private int reentradasSinPlan;
    /** A2 Stage 7E · diagnostico de la PRIMERA obstruccion declarada por `traverse` y su contador. */
    private String ultimaObstruccionDiag = "-";
    private int obstruccionesPorPredicado;
    /** A2 Stage 7E · ticks consecutivos sin acercarse al nodo activo (para distinguir atasco real de "sin ruta"). */
    private int ticksSinAvance;
    /** A2 Stage 7F · mejor distancia horizontal a la mision ya verificada (justifica reiniciar el tope de escalones). */
    private double mejorDistMision = Double.MAX_VALUE;
    private boolean saltoActivo;
    private BlockPos saltoOrigen;
    private BlockPos saltoObjetivo;
    private long saltoFirmaRuta;
    private BlockPos saltoMision;
    private int saltoTicks;
    private int saltoSinProgreso;
    private double saltoUltimaX;
    private long ultimaFirmaDiagConversion = Long.MIN_VALUE;
    private long ultimoTickDiagConversion = Long.MIN_VALUE;
    private final NavDropExclusionPolicy exclusionesDescenso = new NavDropExclusionPolicy();
    private final NavBreakExclusionPolicy exclusionesBreak = new NavBreakExclusionPolicy();
    private final NavRecoveryController recoveryController = new NavRecoveryController();
    /** Non-null only while the one-shot tactical backtrack is active; protects it from mission refreshes. */
    private BlockPos recoveryBacktrackObjective;
    /** One transient retry is allowed for the existing BREAK actuator cooldown. */
    private BlockPos ultimoBreakFallidoStand;
    private BlockPos ultimoBreakFallidoTarget;
    private int fallosBreakSinResolucion;
    private long nextBreakRetryAt;
    /** A2 Stage 7G · replanteos consumidos por rechazo de la guarda de ruta (acotado). */
    private int replanteosPorGuarda;
    private BlockPos breachMoveTarget;
    private int breachMoveTicks;
    private long breachMoveUntil;
    private int breachChainSteps;
    private BlockPos nodoSeguido;
    private double mejorDistAlNodo = Double.MAX_VALUE;

    /**
     * A2 · fija una CADENA de etapas. La primera pasa a ser el objetivo; el ejecutor ira avanzando a la siguiente
     * al llegar a cada una. Sirve para desniveles grandes sin tocar el rango vertical del planificador.
     * Si la cadena llega a su fin, el objetivo se queda en la ultima etapa (no se queda sin objetivo).
     */
    public void setObjectiveChain(java.util.List<BlockPos> cadena) {
        if (cadena == null || cadena.isEmpty()) {
            return;
        }
        int firma = firmaDeCadena(cadena);
        if (firma == cadenaActivaFirma) {
            return; // IDEMPOTENTE: mismo contenido -> no se toca el progreso ni se reordena nada
        }
        // El punto de aproximacion se deriva del rumbo ACTUAL del asaltante y puede desplazarse mientras camina.
        // No sustituir una etapa activa por ese nuevo punto: eso convierte el destino en una meta movil.
        BlockPos raiz = cadena.get(cadena.size() - 1).immutable();
        boolean mismaRaiz = raiz.equals(misionRaiz) && misionRaiz != null;
        boolean soloInterior = cadena.size() == 1;
        if (NavMissionChainPolicy.retainActiveApproachChain(mismaRaiz, objective != null,
                !etapas.isEmpty(), soloInterior)) {
            long ahora = mob.level().getGameTime();
            if (NavTrace.on() && (ultimoTickDiagRetencionCadena == Long.MIN_VALUE
                    || ahora - ultimoTickDiagRetencionCadena >= 100L)) {
                NavTrace.ev("MISSION_CHAIN_RETAINED", "uuid=" + mob.getUUID()
                        + " root=" + raiz.toShortString()
                        + " current=" + objective.toShortString()
                        + " incoming=" + cadena.get(0).toShortString()
                        + " remainingStages=" + etapas.size());
                ultimoTickDiagRetencionCadena = ahora;
            }
            return;
        }
        cadenaActivaFirma = firma;
        cadenaEnviada = cadena.size();
        etapas.clear();
        for (int i = 1; i < cadena.size(); i++) {
            etapas.addLast(cadena.get(i).immutable());
        }
        indiceDeEtapa = 0;
        // A2 FASE 2 · la RAIZ de la mision es la ULTIMA etapa (el interior). Mientras la raiz no cambie, las
        // etapas intermedias son el MISMO asedio: no reinician el contador de rearmes ni borran su motivo.
        misionRaiz = raiz;
        boolean enCadenaAnterior = enCadenaDeEtapas;
        boolean noResetAnterior = noReiniciarRearmes;
        enCadenaDeEtapas = true;
        noReiniciarRearmes = mismaRaiz;
        try {
            setObjective(cadena.get(0));
        } finally {
            enCadenaDeEtapas = enCadenaAnterior;
            noReiniciarRearmes = noResetAnterior;
        }
    }

    private static int firmaDeCadena(java.util.List<BlockPos> cadena) {
        int h = 1;
        for (BlockPos p : cadena) {
            h = 31 * h + p.hashCode();
        }
        return h;
    }

    public String ultimaTransicionDeEtapa() {
        return ultimaTransicionDeEtapa;
    }

    public int indiceDeEtapa() {
        return indiceDeEtapa;
    }

    public int cadenaEnviada() {
        return cadenaEnviada;
    }

    /** Avanza a la siguiente etapa SOLO cuando el ejecutor comprueba que la actual se alcanzó. Devuelve si avanzó. */
    private boolean avanzarEtapa() {
        if (etapas.isEmpty()) {
            return false;
        }
        BlockPos anterior = objective;
        BlockPos siguiente = etapas.pollFirst();
        objective = siguiente;
        objetivoPermiteConstruccion = false;
        indiceDeEtapa++;
        // A2 Stage 7B · la nueva ETAPA pasa a ser la MISION FINAL y el escalonado local se recalcula desde cero.
        misionFinal = siguiente.immutable();
        // A2 REMEDIACIÓN 2 · el plan de construccion usa `planMision` para decidir desniveles; si no se
        // sincroniza con la etapa nueva, sigue comparando contra la primera etapa.
        setPlanMision(siguiente);
        escalonesDeObjetivo = 0;
        rearmadoPorCambioDeTerreno = false;
        cambioTerrenoPendienteRearme = false;
        fallosDeEscalonDeObjetivo = 0;
        // A2 REMEDIACIÓN 2 · una etapa NUEVA y valida reinicia solo el contador de objetivo local invalido.
        // El presupuesto duro y los rearmes siguen scoped a la misma mision.
        intentosObjetivoInvalido = 0;
        mejorDistObjetivoInvalido = Double.MAX_VALUE;
        misionEnPieCalculada = false;
        misionEnPie = null;
        ultimaTransicionDeEtapa = "etapa " + (indiceDeEtapa - 1) + "->" + indiceDeEtapa
                + " de " + cadenaEnviada + ": " + (anterior == null ? "-" : anterior.toShortString())
                + " -> " + siguiente.toShortString() + " (alcanzada)";
        plannedFrom = null;
        state = NavState.PLANNING;
        nextPlanAt = 0L;
        nextBudgetRetryAt = 0L;
        budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
        budgetRetries = 0;
        mejorDistEnPresupuesto = Double.MAX_VALUE;
        return true;
    }

    /**
     * A2 Stage 7B · ¿este objetivo esta DENTRO de la ventana efectiva del planificador? Es la condicion que hace
     * que `NavPlanner` pueda siquiera encolarlo; fuera de ella la busqueda agota el presupuesto sin meta.
     */
    private boolean dentroDeLaVentana(final BlockPos desde, final BlockPos objetivo) {
        if (desde == null || objetivo == null) {
            return false;
        }
        return Math.abs(objetivo.getX() - desde.getX()) <= HORIZONTAL_RANGE - MARGEN_DE_VENTANA
                && Math.abs(objetivo.getZ() - desde.getZ()) <= HORIZONTAL_RANGE - MARGEN_DE_VENTANA
                && Math.abs(objetivo.getY() - desde.getY()) <= VERTICAL_RANGE;
    }

    /**
     * A2 · ¿cabe este waypoint en el tramo local? La ventana (48×6) es el límite de lo que el planificador PUEDE
     * encolar; el tramo es el límite de lo que el A* local puede RESOLVER con su presupuesto. Se conserva el
     * waypoint válido más lejano dentro del tramo y el primero cuando aún no hay objetivo.
     */
    private boolean cabeEnTramoLocal(final BlockPos yo, final BlockPos waypoint, final boolean yaHayObjetivo) {
        if (!yaHayObjetivo || yo == null || waypoint == null) {
            return true;
        }
        return NavLocalObjectiveLegPolicy.withinLeg(waypoint.getX() - yo.getX(),
                waypoint.getZ() - yo.getZ(), NavLocalObjectiveLegPolicy.MAX_LEG_CHEBYSHEV);
    }

    /**
     * A2 Stage 7B · POSICION DE PIE valida mas cercana a una altura de referencia, en una columna dada.
     * Valida = aire en pies y cuerpo, soporte con colision debajo, fuera de la proteccion de la aldea y a menos de
     * {@code VERTICAL_RANGE} de la altura de referencia (para que siga dentro de la ventana del planificador).
     */
    private BlockPos pieValidoCerca(final int x, final int z, final int yRef) {
        for (int dy = 0; dy <= VERTICAL_RANGE; dy++) {
            int[] signos = dy == 0 ? new int[]{0} : new int[]{-1, 1};
            for (int signo : signos) {
                int y = yRef + signo * dy;
                BlockPos c = new BlockPos(x, y, z);
                if (!mob.level().hasChunkAt(c)) {
                    continue;
                }
                if (esPieValido(c)) {
                    return c;
                }
            }
        }
        return null;
    }

    /** A2 Stage 7B · ¿esta celda es una posicion de PIE valida y utilizable? */
    private boolean esPieValido(final BlockPos c) {
        if (c == null || !mob.level().hasChunkAt(c)) {
            return false;
        }
        // A2 · LA CELDA DE PIE SE VALIDA POR COLISIÓN, NO POR isAir(): la capa de nieve de 1, la hierba alta o un
        // helecho no colisionan y no impiden estar de pie. Exigir Aire dejaba inválido un rellano nevado completo y
        // ninguna recuperación podía elegir fuente (medido en `a2n-20221121-213055`: IDX2 FAILED en 660,72,608).
        BlockState pies = mob.level().getBlockState(c);
        BlockPos arriba = c.above();
        BlockState cabeza = mob.level().getBlockState(arriba);
        return NavFeetCellPolicy.isValid(true,
                policies.isProtected(c),
                !pies.getCollisionShape(mob.level(), c).isEmpty(),
                !cabeza.getCollisionShape(mob.level(), arriba).isEmpty(),
                !pies.getFluidState().isEmpty() || esPeligrosoParaAterrizaje(pies),
                !cabeza.getFluidState().isEmpty() || esPeligrosoParaAterrizaje(cabeza),
                NavObstruction.hasSolidFace(mob.level(), c.below()));
    }

    /** Primer bloque con colisión entre los ojos del mob y el centro del objetivo BREAK. */
    private BlockPos primerBloqueoEnLineaDeVision(final BlockPos objetivo) {
        if (objetivo == null) {
            return null;
        }
        net.minecraft.world.phys.BlockHitResult hit = mob.level().clip(
                new net.minecraft.world.level.ClipContext(mob.getEyePosition(), objetivo.getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, mob));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos hitPos = hit.getBlockPos();
        // A ray aimed at an elevated node can graze the support block below it. Breaking that
        // floor is not a BREAK action: it removes the attacker's own footing and repeats.
        if (NavBreakTargetPolicy.isSupportBelowTarget(hitPos.getY(), objetivo.getY(),
                hitPos.getX() == objetivo.getX() && hitPos.getZ() == objetivo.getZ())) {
            return mob.level().getBlockState(objetivo).getCollisionShape(mob.level(), objetivo).isEmpty()
                    ? null
                    : objetivo.immutable();
        }
        return hitPos.immutable();
    }

    /**
     * A2 Stage 7B · MISION "DE PIE". La mision que llega de produccion puede NO ser una posicion de pie: el propio
     * arnes mide que el centro de la aldea es `minecraft:stone_bricks` (log `CELDA DEL CENTRO 661,71,533
     * esAire=false`). El planificador solo puede encolar celdas de pie, asi que con la celda solida como meta la
     * busqueda nunca encola (`metaEncolada=false`) y agota el presupuesto. Aqui se conserva la MISION ORIGINAL
     * ({@code misionFinal}, intacta) y se calcula una sola vez la posicion de pie mas cercana dentro de
     * {@code VERTICAL_RANGE} (misma columna y, si hace falta, las 8 contiguas).
     */
    private BlockPos misionDePie() {
        if (misionEnPieCalculada) {
            return misionEnPie;
        }
        misionEnPieCalculada = true;
        misionEnPie = null;
        if (misionFinal == null) {
            return null;
        }
        if (esPieValido(misionFinal)) {
            misionEnPie = misionFinal;
            return misionEnPie;
        }
        BlockPos p = pieValidoCerca(misionFinal.getX(), misionFinal.getZ(), misionFinal.getY());
        if (p == null) {
            int[][] vecinos = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            for (int[] v : vecinos) {
                p = pieValidoCerca(misionFinal.getX() + v[0], misionFinal.getZ() + v[1], misionFinal.getY());
                if (p != null) {
                    break;
                }
            }
        }
        if (p != null) {
            misionEnPie = p;
            ultimoEscalonDeObjetivo = "mision " + misionFinal.toShortString()
                    + " NO es posicion de pie -> se apunta a " + p.toShortString();
        } else {
            ultimoEscalonDeObjetivo = "mision " + misionFinal.toShortString()
                    + " NO es posicion de pie y no hay ninguna valida a menos de " + VERTICAL_RANGE + " bloques";
        }
        return misionEnPie;
    }

    /** A2 Stage 7B · ¿el objetivo alcanzado en este tick es la meta real (mision o su posicion de pie)? */
    private boolean objetivoEsLaMision() {
        if (objective == null) {
            return false;
        }
        // A2 FASE 4 · un objetivo TACTICO (orilla, escape) nunca es la mision, aunque coincida con ella.
        if (objetivoTactico) {
            return false;
        }
        if (misionFinal != null && objective.equals(misionFinal)) {
            return true;
        }
        BlockPos pie = misionDePie();
        return pie != null && objective.equals(pie);
    }

    /**
     * A2 Stage 7C · RUTA GLOBAL hacia la mision, memorizada por tramo. Se calcula con la A* correcta y acotada de
     * {@link NavPlanner#planGlobal} (heuristica admisible, reapertura, radio y presupuesto duros). Si el ente se
     * aleja mas de 8 bloques del punto donde se calculo, se recalcula: nunca se reutiliza una ruta que ya no
     * describe la situacion.
     */
    private NavPlanner.GlobalResult rutaGlobalHacia(final BlockPos mision) {
        BlockPos yo = mob.blockPosition();
        if (globalCache != null && !globalCache.pending() && mision.equals(globalMision) && globalDesde != null
                && globalDesde.distSqr(yo) <= CACHE_GLOBAL_SQR) {
            return globalCache;
        }
        boolean continuing = globalCache != null && globalCache.pending()
                && mision.equals(globalMision) && globalSearchStart != null
                && globalSearchStart.equals(yo);
        if (!continuing) {
            globalSearchLastExpansions = 0;
            globalSearchStart = yo.immutable();
        }
        NavPlanner.GlobalResult r = NavPlanner.planGlobalIncremental(mob.level(), mob, yo, mision,
                RADIO_GLOBAL_DE_BUSQUEDA, VENTANA_VERTICAL_GLOBAL, MAX_EXPANSIONES_GLOBALES, policies, construidas,
                exclusionesDescenso, exclusionesBreak, globalSearchHandle);
        globalExpansionesTotal += Math.max(0, r.expansions() - globalSearchLastExpansions);
        globalSearchLastExpansions = r.pending() ? r.expansions() : 0;
        if (!r.pending()) globalSearchStart = null;
        globalCache = r;
        globalMision = mision;
        globalDesde = yo.immutable();
        ultimoGlobalDiag = r.summary();
        if (!r.pending()) {
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} RUTA GLOBAL: {}", mob.getId(),
                    r.summary());
        }
        return r;
    }

    /** Distancia horizontal al cuadrado entre dos celdas (para comprobar que un tramo ACERCA de verdad). */
    private static double horizontalAlCuadrado(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** A2 Stage 7C · un waypoint de la ruta global es pisable si su nodo es una celda de PIE real. */
    private long huellaTerreno(BlockPos p) {
        return NavPlanner.huellaTerreno(mob.level(), p, policies.maxSafeDrop());
    }

    /**
     * A2 · <b>EJECUTAR LA RUTA GLOBAL VALIDADA.</b> Si la búsqueda global ya unió la celda ACTUAL con la misión,
     * esa ruta es la que hay que recorrer: sus aristas pasaron la misma validación que las locales (soporte,
     * caída segura, BUILD/BREAK, protección) y puede contener los escalones de obra que suben el desnivel. Volver
     * a planificar lo mismo con menos presupuesto era el bucle medido de IDX7/IDX8.
     * <p>
     * Devuelve {@code true} si tomó el control del tick: aceptó la ruta o dejó la búsqueda incremental en curso.
     */
    private boolean intentarRutaGlobalValidada(long now) {
        BlockPos mision = misionDePie();
        if (mision == null) {
            return false;
        }
        NavPlanner.GlobalResult global = rutaGlobalHacia(mision);
        if (global.pending()) {
            nextPlanAt = now + 1L;
            noPlanifica("ruta global validada: busqueda incremental pendiente");
            return true;
        }
        if (global.route().isEmpty()) {
            return false;
        }
        if (!global.alcanzoMision()) {
            // A2 · RUTA-FRENTE: cuando la búsqueda global no llega a la misión pero SÍ produjo un tramo validado
            // que ACERCA de verdad, ese tramo es ejecutable. Antes solo servía para elegir un waypoint y el ente
            // podía quedarse clavado sin plan (medido: IDX7/8 inmóviles en su spawn durante 6000 ticks).
            BlockPos destino = global.route().destination();
            if (destino.equals(mob.blockPosition())) {
                return false;
            }
            double antes = horizontalAlCuadrado(mob.blockPosition(), mision);
            double despues = horizontalAlCuadrado(destino, mision);
            if (despues >= antes - 4.0D) {
                return false;
            }
        }
        BlockPos yo = mob.blockPosition();
        if (globalDesde == null || !globalDesde.equals(yo)) {
            return false;
        }
        if (global.route().signature() == lastRouteSignature) {
            return false;
        }
        route = global.route();
        routeIndex = 0;
        plannedFrom = yo;
        mejorDistEnPresupuesto = Double.MAX_VALUE;
        budgetRetries = 0;
        presupuestosSinProgresoTotal = 0;
        intentosObjetivoInvalido = 0;
        mejorDistObjetivoInvalido = Double.MAX_VALUE;
        intentosDeDescenso = 0;
        state = NavState.TRAVERSING;
        if (NavTrace.on()) {
            NavTrace.ev("GLOBAL_ROUTE_EXECUTED", "uuid=" + mob.getUUID() + " tick=" + now
                    + " pos=" + yo.toShortString() + " mision=" + mision.toShortString()
                    + " nodos=" + route.length() + " exp=" + global.expansions()
                    + " resumen={" + global.summary() + "}");
        }
        return true;
    }

    static boolean esWaypointDePie(final NavRoute.Node n) {
        return NavClimbPolicy.esWaypointDePie(n.transition());
    }

    /**
     * A2 Stage 7B · OBJETIVO LOCAL. Si la mision final ya esta dentro de la ventana, se devuelve tal cual (no se
     * inventa nada). Si no, se avanza HACIA ella por pasos decrecientes y se devuelve la primera posicion de pie
     * valida que (1) este dentro de la ventana, (2) tenga los chunks cargados, (3) no este protegida y (4) ACERQUE
     * de verdad a la mision (progreso horizontal medible). Devuelve {@code null} si no existe ninguna: en ese caso
     * el llamante informa el motivo y termina, en vez de planificar a un objetivo imposible.
     * <p>
     * A2 Stage 7C · el tramo ya NO se elige por geometria: se elige sobre la RUTA GLOBAL acotada. El objetivo local
     * es el ULTIMO waypoint de pie o transicion BUILD seleccionable que quepa en la ventana local; la ventana no cambia.
     */
    private BlockPos elegirObjetivoLocal() {
        globalSearchPending = false;
        ultimoResultadoEscalon = StairBuildResult.NO_PROGRESS;
        objetivoPermiteConstruccion = false;
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        objetivoAccionConstruccion = null;
        BlockPos mision = misionDePie();
        if (mision == null) {
            ultimoEscalonDeObjetivo = "sin mision utilizable (mision="
                    + (misionFinal == null ? "-" : misionFinal.toShortString()) + ")";
            return null;
        }
        BlockPos yo = mob.blockPosition();
        if (mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az) {
            // Un escalon verificado pero aun no atravesado es el siguiente objetivo local obligatorio.
            // No se debe abandonar por un waypoint global distinto en ticks posteriores al de colocacion.
            az.confirmarTravesiaEscalonPendiente();
            BlockPos escalonPendiente = az.objetivoSobreUltimoEscalon();
            if (escalonPendiente != null) {
                boolean dentro = dentroDeLaVentana(yo, escalonPendiente);
                boolean cargado = mob.level().hasChunkAt(escalonPendiente);
                boolean protegido = policies.isProtected(escalonPendiente);
                boolean piesAire = cargado && mob.level().getBlockState(escalonPendiente).isAir();
                boolean cabezaAire = cargado && mob.level().getBlockState(escalonPendiente.above()).isAir();
                boolean soporte = cargado
                        && NavObstruction.hasSolidFace(mob.level(), escalonPendiente.below());
                boolean pieValido = cargado && !protegido && piesAire && cabezaAire && soporte;
                if (dentro && pieValido) {
                    objetivoPermiteConstruccion = false;
                    ultimoEscalonDeObjetivo = "mantener escalon pendiente hasta travesia real "
                            + escalonPendiente.toShortString();
                    if (NavTrace.on()) {
                        NavTrace.ev("STAIR_PENDING_WAYPOINT_SELECTED", "uuid=" + mob.getUUID()
                                + " target=" + escalonPendiente.toShortString()
                                + " step=" + escalonPendiente.below().toShortString()
                                + " pos=" + yo.toShortString());
                    }
                    return escalonPendiente;
                }
                String diagnostico = "escalon pendiente rechazado target=" + escalonPendiente.toShortString()
                        + " dentro=" + dentro + " cargado=" + cargado + " protegido=" + protegido
                        + " piesAire=" + piesAire + " cabezaAire=" + cabezaAire + " soporte=" + soporte
                        + " piesState=" + (cargado ? mob.level().getBlockState(escalonPendiente) : "chunk-unloaded")
                        + " cabezaState=" + (cargado ? mob.level().getBlockState(escalonPendiente.above()) : "chunk-unloaded")
                        + " soporteState=" + (cargado ? mob.level().getBlockState(escalonPendiente.below()) : "chunk-unloaded");
                if (!diagnostico.equals(ultimoEscalonDeObjetivo) && NavTrace.on()) {
                    NavTrace.ev("STAIR_PENDING_WAYPOINT_REJECTED", "uuid=" + mob.getUUID() + " " + diagnostico);
                }
                ultimoEscalonDeObjetivo = diagnostico;
            }
        }
        int desnivelDirecto = mision.getY() - yo.getY();
        boolean objetivoLocalFisicamenteAlcanzable =
                NavWaypointWindowPolicy.directoAlcanzable(desnivelDirecto, policies.maxSafeDrop());
        if (dentroDeLaVentana(yo, mision) && objetivoLocalFisicamenteAlcanzable) {
            if (globalCache != null && globalCache.pending()) {
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalCache = null;
                globalMision = null;
                globalDesde = null;
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
            }
            if (mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity) {
                BlockPos escalon = intentarConstruirEscalonSinWaypoint(mision, yo,
                        "mision dentro de ventana local y desnivel ejecutable");
                if (escalon != null) {
                    return escalon;
                }
                if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT && NavTrace.on()) {
                    NavTrace.ev("A2_STAIR_CADENCE_ROUTE_PRESERVED", "uuid=" + mob.getUUID()
                            + " tick=" + mob.level().getGameTime()
                            + " waypoint=" + mision.toShortString()
                            + " source=direct_mission");
                }
            }
            // CADENCE_WAIT postpones only the stair attempt. The directly reachable mission
            // remains a valid non-stair waypoint and must not be discarded.
            return mision;
        }
        if (dentroDeLaVentana(yo, mision) && !objetivoLocalFisicamenteAlcanzable) {
            ultimoEscalonDeObjetivo = "objetivo dentro de ventana pero desnivel no ejecutable local (dy="
                    + desnivelDirecto + ", caidaSegura=" + policies.maxSafeDrop()
                    + "); se exige ruta global";
            if (NavTrace.on()) {
                NavTrace.ev("LOCAL_OBJECTIVE_REQUIRES_GLOBAL_ROUTE", "uuid=" + mob.getUUID()
                        + " pos=" + yo.toShortString() + " mission=" + mision.toShortString()
                        + " dy=" + desnivelDirecto + " maxSafeDrop=" + policies.maxSafeDrop());
            }
        }
        NavPlanner.GlobalResult global = rutaGlobalHacia(mision);
        if (global.pending()) {
            globalSearchPending = true;
            ultimoEscalonDeObjetivo = global.summary();
            long traceTick = mob.level().getGameTime();
            if (NavTrace.on() && (lastGlobalPendingTraceTick == Long.MIN_VALUE
                    || traceTick - lastGlobalPendingTraceTick >= 20L)) {
                lastGlobalPendingTraceTick = traceTick;
                NavTrace.ev("NAV_GLOBAL_SEARCH_PENDING", "uuid=" + mob.getUUID()
                        + " tick=" + traceTick + " " + global.summary());
            }
            return null;
        }
        // El planificador GLOBAL puede agotar su presupuesto antes de producir un waypoint local, aunque el
        // descenso requiera una escalera. El fallback anterior solo se ejecutaba cuando agotaba el presupuesto
        // LOCAL; por eso un atacante en un acantilado quedaba en PLANNING sin llegar a construir.
        if (global.hitBudget()
                && mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az) {
            StairBuildResult stairOutcome = az.construirEscalonResultado(mision);
            if (stairOutcome == StairBuildResult.CADENCE_WAIT) {
                ultimoResultadoEscalon = StairBuildResult.CADENCE_WAIT;
                registrarEsperaCadenciaEscalon("presupuesto global agotado");
                return null;
            }
            boolean escalonColocado = stairOutcome == StairBuildResult.STAIR_PLACED;
            if (escalonColocado) {
                BlockPos escalonTransitable = az.objetivoSobreUltimoEscalon();
                if (escalonTransitable != null && dentroDeLaVentana(yo, escalonTransitable)
                        && esPieValido(escalonTransitable)) {
                    globalCache = null;
                    NavPlanner.cancelGlobalSearch(globalSearchHandle);
                    globalSearchStart = null;
                    globalSearchLastExpansions = 0;
                    globalMision = null;
                    globalDesde = null;
                    objetivoPermiteConstruccion = false;
                    ultimoEscalonDeObjetivo = "fallback por presupuesto GLOBAL: escalon lateral colocado y verificado "
                            + escalonTransitable.toShortString() + " hacia " + mision.toShortString();
                    com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] A2 GLOBAL-BUDGET-STAIR #{} objetivo={} escalon={}",
                            mob.getId(), mision.toShortString(), escalonTransitable.toShortString());
                    return escalonTransitable;
                }
                ultimoEscalonDeObjetivo = "fallback por presupuesto GLOBAL coloco escalon pero no hay celda de pies local valida";
            } else {
                String diagnostico = "fallback GLOBAL sin escalon: objetivo=" + mision.toShortString()
                        + " pos=" + yo.toShortString() + " decision={" + az.decisionDescensoDiag()
                        + "} escalera={" + az.escaleraDiag() + "}";
                if (!diagnostico.equals(ultimoEscalonDeObjetivo)) {
                    com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] A2 GLOBAL-BUDGET-STAIR-REJECT #{} {}",
                            mob.getId(), diagnostico);
                }
                ultimoEscalonDeObjetivo = diagnostico;
            }
        }
        if (!global.alcanzoMision() && global.route().length() <= 1
                && intentarRoturaDeRecuperacion()) {
            return null;
        }
        if (global.route().isEmpty()) {
            if (!global.hitBudget()) {
                BlockPos escalon = intentarConstruirEscalonSinWaypoint(mision, yo, "sin ruta global");
                if (escalon != null) {
                    return escalon;
                }
                if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT) {
                    return null;
                }
            }
            ultimoEscalonDeObjetivo = "sin ruta global hacia " + mision.toShortString() + ": " + global.reason()
                    + " frente=" + global.frente().toShortString();
            return null;
        }
        BlockPos objetivo = null;
        boolean objetivoConstruible = false;
        NavRoute.Transition objetivoTransicion = null;
        BlockPos objetivoAccion = null;
        BlockPos previoRuta = null;
        boolean descartados = false;
        for (NavRoute.Node n : global.route().nodes()) {
            if (n.transition() == NavRoute.Transition.DROP && previoRuta != null
                    && (exclusionesDescenso.excluida(NavDropExclusionPolicy.clave(
                            previoRuta.getX(), previoRuta.getY(), previoRuta.getZ(),
                            n.pos().getX(), n.pos().getY(), n.pos().getZ()),
                            huellaTerreno(n.pos()))
                        || exclusionesDescenso.excluida(NavDropExclusionPolicy.claveDestino(
                                n.pos().getX(), n.pos().getY(), n.pos().getZ()),
                                huellaTerreno(n.pos())))) {
                previoRuta = n.transition() == NavRoute.Transition.BREAK ? n.actionTarget() : n.pos();
                continue;
            }
            previoRuta = n.transition() == NavRoute.Transition.BREAK ? n.actionTarget() : n.pos();
            boolean requiereConstruccion = NavClimbPolicy.requiereConstruccion(n.transition());
            boolean accionBreak = n.transition() == NavRoute.Transition.BREAK;
            boolean accionBridge = n.transition() == NavRoute.Transition.BRIDGE;
            BlockPos actionTarget = accionBreak ? n.actionTarget() : null;
            if (!NavClimbPolicy.esWaypointSeleccionable(n.transition())) {
                continue;
            }
            // BREAK keeps the physical attack stand in n.pos() and the solid block in actionPos().
            // Select and validate the stand as a movement waypoint, but authorize the action against actionTarget.
            // BUILD/BRIDGE retain their own action-cell contracts and are not ordinary feet waypoints.
            BlockPos waypoint = n.standPos();
            if (requiereConstruccion && !NavBuildObjectivePolicy.isValid(waypoint, n.actionPos())) {
                descartados = true;
                if (NavTrace.on()) {
                    NavTrace.ev("BUILD_GLOBAL_WAYPOINT_REJECTED", "uuid=" + mob.getUUID()
                            + " tick=" + mob.level().getGameTime()
                            + " feet=" + waypoint.toShortString()
                            + " actionPos=" + (n.actionPos() == null ? "-" : n.actionPos().toShortString())
                            + " reason=missing_or_misaligned_action_pos");
                }
                continue;
            }
            if (!dentroDeLaVentana(yo, waypoint)) {
                if (n.transition() == NavRoute.Transition.DROP
                        && NavWaypointWindowPolicy.aceptable(waypoint.getX() - yo.getX(), waypoint.getZ() - yo.getZ(),
                                waypoint.getY() - yo.getY(), true, HORIZONTAL_RANGE - MARGEN_DE_VENTANA,
                                VERTICAL_RANGE, policies.maxSafeDrop())
                        && esPieValido(waypoint)) {
                    if (!cabeEnTramoLocal(yo, waypoint, objetivo != null)) {
                        break;
                    }
                    objetivo = waypoint;
                    objetivoConstruible = false;
                    objetivoTransicion = n.transition();
                    continue;
                }
                continue; // keep scanning: a later in-window node may still be the valid local waypoint
            }
            // ==========================================================================================
            // A2 Stage 7D · VALIDACION EN EL MOMENTO (causa raiz medida en `a2n-20261017-121902`).
            // La ruta global se calculo en `864,45,553`; despues la recuperacion generica ("pone un ESCALON ...
            // para subir al objetivo") dejo SOLIDA la celda del waypoint elegido, `908,49,535`
            // (`NO_ES_PIE(aire=false aireArriba=false soporteAbajo=true)`). El waypoint se entregaba igual al
            // planificador, el invariante lo declaraba no utilizable, el escalonado devolvia el MISMO waypoint
            // (idempotente) y `tryPlan` salia en CADA tick sin planificar: `noPlan` crecio de 202 a 2303 con
            // `plans` congelado y la ruta global (51 nodos) intacta. Se valida la celda contra el mundo ACTUAL y
            // se salta a la siguiente de la ruta; si ninguna vale, la ruta cacheada es obsoleta y se invalida.
            // ==========================================================================================
            if (accionBreak && (!(mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az)
                    || n.actionPos() == null
                    || !az.puedeRomperNodoDeNavegacion(actionTarget)
                    || !NavBreakActionPolicy.executable(mob.level(), mob, waypoint, actionTarget,
                            policies, NavBreakActionPolicy.ACTION_REACH_SQR))) {
                descartados = true;
                if (NavTrace.on()) {
                    NavTrace.ev("VERTICAL_TRANSITION_REJECTED", "uuid=" + mob.getUUID()
                            + " tick=" + mob.level().getGameTime() + " transition=BREAK target="
                            + waypoint.toShortString() + " feetY=" + yo.getY() + " reason=OUT_OF_REACH");
                }
                continue;
            }
            if (!requiereConstruccion && !accionBreak && !accionBridge && !esPieValido(waypoint)) {
                descartados = true;
                continue;
            }
            // A2 · TRAMO LOCAL ACOTADO: el waypoint válido más lejano que quepa en la pierna. Quedarse con el
            // último nodo de la ventana (medido: 58,8 bloques) entregaba al A* local una pierna que sus 900
            // expansiones no resolvían y el ejecutor terminaba FAILED sin moverse.
            if (!cabeEnTramoLocal(yo, waypoint, objetivo != null)) {
                break;
            }
            objetivo = waypoint;
            objetivoConstruible = requiereConstruccion;
            objetivoTransicion = n.transition();
            objetivoAccion = accionBreak ? actionTarget : (requiereConstruccion ? n.actionPos() : null);
        }
        if (objetivo == null) {
            if (!global.hitBudget()) {
                BlockPos escalon = intentarConstruirEscalonSinWaypoint(mision, yo,
                        "ruta global sin waypoint de pie valido");
                if (escalon != null) {
                    return escalon;
                }
                if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT) {
                    return null;
                }
            }
            objetivoPermiteConstruccion = false;
            globalCache = null;
            NavPlanner.cancelGlobalSearch(globalSearchHandle);
            globalSearchStart = null;
            globalSearchLastExpansions = 0;
            globalDesde = null;
            ultimoEscalonDeObjetivo = "ruta global sin waypoint de pie VALIDO dentro de la ventana desde "
                    + yo.toShortString() + (descartados ? " (habia waypoints pero el mundo cambio)" : "")
                    + " -> se invalida la ruta global para recalcularla";
            return null;
        }
        if (objetivo.equals(yo) && objetivoTransicion != NavRoute.Transition.BREAK) {
            if (!global.hitBudget()) {
                BlockPos escalon = intentarConstruirEscalonSinWaypoint(mision, yo,
                        "waypoint global coincide con posicion actual");
                if (escalon != null) {
                    return escalon;
                }
                if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT) {
                    return null;
                }
            }
            objetivoPermiteConstruccion = false;
            ultimoEscalonDeObjetivo = "el primer waypoint global es la celda actual (" + yo.toShortString()
                    + ") global={" + global.summary() + "}";
            return null;
        }
        if (!objetivoConstruible
                && objetivo.getY() < yo.getY() - policies.maxSafeDrop()
                && mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az) {
            if (NavTrace.on()) {
                String diag = "objetivo=" + objetivo.toShortString() + " mision=" + mision.toShortString()
                        + " buildable=" + objetivoConstruible + " local=" + escalonesDeObjetivo
                        + "/" + MAX_ESCALONES_DE_OBJETIVO + " fallos=" + fallosDeEscalonDeObjetivo
                        + "/" + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO
                        + " anchor=" + az.escaleraDiag();
                if (!diag.equals(ultimoStairFallbackDiag)) {
                    ultimoStairFallbackDiag = diag;
                    NavTrace.ev("A2_STAIR_FALLBACK_DECISION", "uuid=" + mob.getUUID()
                            + " tick=" + mob.level().getGameTime() + " pos=" + yo.toShortString() + " " + diag);
                }
            }
            BlockPos escalon = intentarConstruirEscalonSinWaypoint(mision, yo,
                    "waypoint global es la mision y exige escalera");
            if (NavTrace.on()) {
                NavTrace.ev("A2_STAIR_FALLBACK_RESULT", "uuid=" + mob.getUUID()
                        + " tick=" + mob.level().getGameTime()
                        + " result=" + (escalon == null ? "null" : escalon.toShortString())
                        + " stair={" + az.escaleraDiag() + "}");
            }
            if (escalon != null) {
                return escalon;
            }
            if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT && NavTrace.on()) {
                NavTrace.ev("A2_STAIR_CADENCE_ROUTE_PRESERVED", "uuid=" + mob.getUUID()
                        + " tick=" + mob.level().getGameTime()
                        + " waypoint=" + objetivo.toShortString()
                        + " transition=" + objetivoTransicion
                        + " source=validated_global_route");
            }
        }
        // A valid global waypoint has already passed the route and physical-foot checks above.
        // A stair cooldown delays construction, not movement along this independent route.
        objetivoPermiteConstruccion = objetivoConstruible
                && NavBuildObjectivePolicy.isValid(objetivo, objetivoAccion);
        objetivoTransicionLocal = objetivoTransicion;
        objetivoAccionLocal = objetivoTransicion == NavRoute.Transition.BREAK ? objetivoAccion : null;
        objetivoAccionConstruccion = objetivoTransicion == NavRoute.Transition.BUILD
                ? objetivoAccion : null;
        if (NavTrace.on()) {
            NavTrace.ev("LOCAL_OBJECTIVE_SELECTED", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime()
                    + " feet=" + objetivo.toShortString()
                    + " transition=" + objetivoTransicion
                    + " buildAuthorized=" + objetivoPermiteConstruccion
                    + " buildActionPos=" + (objetivoAccionConstruccion == null
                            ? "-" : objetivoAccionConstruccion.toShortString())
                    + " routeLength=" + global.route().length()
                    + " found=" + global.alcanzoMision());
        }
        ultimoEscalonDeObjetivo = "global " + (global.alcanzoMision() ? "ALCANZA la mision" : "solo frente")
                + " -> waypoint local " + objetivo.toShortString()
                + " (transicion=" + objetivoTransicion + " build=" + objetivoPermiteConstruccion
                + " rutaGlobal=" + global.route().length() + " exp=" + global.expansions() + ")";
        return objetivo;
    }

    /**
     * Ultimo recurso acotado cuando la ruta global no ofrece ningun waypoint local utilizable aunque no haya
     * agotado el presupuesto. Solo construye si AggressiveZombieEntity confirma que la geometria exige escalera.
     */
    private void registrarEsperaCadenciaEscalon(final String causa) {
        ultimoEscalonDeObjetivo = causa + ": CADENCE_WAIT; reintentar sin consumir objetivo local";
        if (NavStairTrace.on()) {
            NavStairTrace.ev("STAIR_PIPELINE_RETURN", "uuid=" + mob.getUUID()
                    + " outcome=CADENCE_WAIT causa={" + causa + "}");
        }
        if (NavTrace.on()) {
            NavTrace.ev("A2_LOCAL_STAIR_WAIT", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime()
                    + " local=" + escalonesDeObjetivo + "/" + MAX_ESCALONES_DE_OBJETIVO
                    + " reason=CADENCE_WAIT counters_unchanged=true");
        }
    }

    private BlockPos intentarConstruirEscalonSinWaypoint(final BlockPos mision, final BlockPos yo,
            final String causa) {
        if (NavStairTrace.on()) {
            NavStairTrace.ev("STAIR_PIPELINE_ENTER", "uuid=" + mob.getUUID() + " pos="
                    + yo.toShortString() + " mision=" + mision.toShortString() + " causa={" + causa
                    + "} local=" + escalonesDeObjetivo + "/" + MAX_ESCALONES_DE_OBJETIVO
                    + " fallos=" + fallosDeEscalonDeObjetivo + "/" + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO);
        }
        if (!(mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az)) {
            return null;
        }
        StairBuildResult stairOutcome = az.construirEscalonResultado(mision);
        ultimoResultadoEscalon = stairOutcome;
        if (stairOutcome == StairBuildResult.CADENCE_WAIT) {
            registrarEsperaCadenciaEscalon(causa);
            return null;
        }
        if (stairOutcome != StairBuildResult.STAIR_PLACED) {
            if (stairOutcome == StairBuildResult.ACCESS_CLEARED) {
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalMision = null;
                globalDesde = null;
                objetivoPermiteConstruccion = false;
                ultimoEscalonDeObjetivo = causa + ": ACCESS_CLEARED; reintento acotado sin consumir escalon";
            }
            if (NavStairTrace.on()) {
                NavStairTrace.ev("STAIR_PIPELINE_RETURN", "uuid=" + mob.getUUID()
                        + " outcome=" + stairOutcome + " causa={" + causa + "}");
            }
            return null;
        }
        BlockPos escalonTransitable = az.objetivoSobreUltimoEscalon();
        if (NavStairTrace.on()) {
            NavStairTrace.ev("STAIR_PIPELINE_PENDING", "uuid=" + mob.getUUID()
                    + " transitable=" + escalonTransitable + " stair={" + az.escaleraDiag() + "}");
        }
        if (escalonTransitable == null) {
            // construirEscalonHacia tambien puede haber despejado una obstruccion minable del corredor.
            // No hay escalon pendiente: invalidar la ruta para recalcular desde el mundo ya modificado.
            globalCache = null;
            NavPlanner.cancelGlobalSearch(globalSearchHandle);
            globalSearchStart = null;
            globalSearchLastExpansions = 0;
            globalMision = null;
            globalDesde = null;
            objetivoPermiteConstruccion = false;
            if (NavTerrainRearmPolicy.shouldResetLocalStairBudget(true, true,
                    rearmesLocalesDeEscalera, MAX_REARMES_LOCALES_DE_ESCALERA)) {
                escalonesDeObjetivo = 0;
                fallosDeEscalonDeObjetivo = 0;
                rearmesLocalesDeEscalera++;
                // The world changed at a verified access clear: allow the bounded terminal rearm
                // budget to be used again. This is still bounded by MAX_REARMES_LOCALES_DE_ESCALERA.
                rearmesTerminales = 0;
                nextRearmAt = 0L;
                falloDefinitivoTerminal = false;
                rearmadoPorCambioDeTerreno = false;
                cambioTerrenoPendienteRearme = true;
                ultimoEscalonDeObjetivo = "rearme local tras acceso de escalera despejado ("
                        + rearmesLocalesDeEscalera + "/" + MAX_REARMES_LOCALES_DE_ESCALERA + ")";
            } else {
                ultimoEscalonDeObjetivo = causa + ": obstruccion de acceso despejada; replanificar ruta global";
            }
            return null;
        }
        if (!dentroDeLaVentana(yo, escalonTransitable) || !esPieValido(escalonTransitable)) {
            globalCache = null;
            NavPlanner.cancelGlobalSearch(globalSearchHandle);
            globalSearchStart = null;
            globalSearchLastExpansions = 0;
            globalMision = null;
            globalDesde = null;
            ultimoEscalonDeObjetivo = causa + ": escalon verificado sin celda de pies local valida";
            return null;
        }
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalMision = null;
        globalDesde = null;
        objetivoPermiteConstruccion = false;
        ultimoEscalonDeObjetivo = causa + ": escalon verificado " + escalonTransitable.toShortString()
                + " hacia " + mision.toShortString();
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] A2 NO-WAYPOINT-STAIR #{} causa={} objetivo={} escalon={}",
                mob.getId(), causa, mision.toShortString(), escalonTransitable.toShortString());
        return escalonTransitable;
    }

    /**
     * A2 Stage 7B · aplica el escalonado: deja {@code objective} en el objetivo local acotado. Devuelve si hay un
     * objetivo local UTILIZABLE. Lleva sus propios contadores (escalones y fallos seguidos), ambos acotados.
     */
    private boolean esAccionLocalValida(BlockPos destino) {
        if (destino == null) {
            return false;
        }
        boolean bridge = objetivoTransicionLocal == NavRoute.Transition.BRIDGE;
        boolean romper = objetivoTransicionLocal == NavRoute.Transition.BREAK;
        BlockPos actionTarget = objetivoAccionLocal;
        boolean puedeRomper = romper
                && actionTarget != null
                && mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az
                && az.puedeRomperNodoDeNavegacion(actionTarget)
                && NavBreakActionPolicy.executable(mob.level(), mob, destino, actionTarget,
                        policies, NavBreakActionPolicy.ACTION_REACH_SQR);
        return NavObjectivePolicy.accionLocalValida(bridge, romper, puedeRomper);
    }

    private void tickBreachMove(long now) {
        breachMoveTicks++;
        if (now > breachMoveUntil || mob.blockPosition().equals(breachMoveTarget)) {
            if (mob.blockPosition().equals(breachMoveTarget) && breachChainSteps < 4
                    && misionFinal != null
                    && mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az) {
                BlockPos next = az.abrirPasajeHacia(misionFinal);
                if (next != null) {
                    breachChainSteps++;
                    breachMoveTarget = next;
                    breachMoveTicks = 0;
                    breachMoveUntil = now + 50L;
                    if (NavTrace.on()) {
                        NavTrace.ev("NAV_RECOVERY_BREACH_CHAIN", "uuid=" + mob.getUUID()
                                + " tick=" + now + " step=" + breachChainSteps + "/4"
                                + " target=" + next.toShortString());
                    }
                    return;
                }
            }
            if (NavTrace.on()) {
                NavTrace.ev("NAV_RECOVERY_BREACH_MOVE_END", "uuid=" + mob.getUUID()
                        + " tick=" + now + " pos=" + mob.blockPosition().toShortString()
                        + " target=" + breachMoveTarget.toShortString());
            }
            breachMoveTarget = null;
            breachMoveTicks = 0;
            breachChainSteps = 0;
            route = null;
            routeIndex = 0;
            globalCache = null;
            state = NavState.PLANNING;
            return;
        }
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(breachMoveTarget.getX() + 0.5D,
                breachMoveTarget.getY(), breachMoveTarget.getZ() + 0.5D, 0.9D);
        if (NavTrace.on() && breachMoveTicks % 10 == 0) {
            NavTrace.ev("NAV_RECOVERY_BREACH_MOVE", "uuid=" + mob.getUUID() + " tick=" + now
                    + " pos=" + mob.blockPosition().toShortString()
                    + " target=" + breachMoveTarget.toShortString()
                    + " ticks=" + breachMoveTicks);
        }
    }

    // ============================================================================================
    // A2 · TÚNEL SUBORDINADO. Flujo: pedir plan -> por cada paso ocupar `stand` exacto -> lease BREAK
    // para `feetBlock` y verificar Aire -> lease BREAK para `headBlock` y verificar Aire -> mover
    // físicamente a `post` y verificar soporte/AABB/ocupación -> SOLO entonces consumir el paso.
    // Cualquier fallo descarta el plan completo, invalida ruta/global y replanifica. El planificador
    // puro no emite movimiento ni modifica el mundo; este ejecutor sigue siendo la única autoridad.
    // ============================================================================================

    /**
     * A2 · DETECCIÓN ACOTADA DE CALLEJÓN SOSTENIDO. Cuenta ticks de PLANNING en la MISMA celda mientras no hay
     * avance de la búsqueda global ni del escalonado. Al superar el tope pide un plan de túnel; si no lo hay, el
     * contador sigue y el enfriamiento del planificador evita martillear. Devuelve {@code true} solo si arrancó
     * un plan (el llamante debe ceder el tick).
     */
    private boolean actualizarEsperaDePlanificacion(long now) {
        BlockPos celda = mob.blockPosition();
        // A2 · ENTRADA AL RECINTO: sobre el anillo, un paso de descenso acotado hacia la misión (ver método).
        if (intentarEntrarAlRecinto(now)) {
            ticksEnEsperaDePlan = 0;
            celdaEnEsperaDePlan = celda.immutable();
            return true;
        }
        // A2 · ENCADENADO DEL ASCENSO. Con desnivel real se intenta subir en CADA tick de PLANNING (4 direcciones,
        // barato y con enfriamiento propio). Así cada escalón de subida encadena el siguiente sin esperar al
        // callejón sostenido ni a que falle un plan: medido en `a2n-20261122-001314`, IDX8 subió el primer
        // bloque del acantilado y el encadenado se perdía porque este método salía antes por "celda nueva".
        if (desnivelReal() && intentarAscenso(now, "encadenado")) {
            ticksEnEsperaDePlan = 0;
            celdaEnEsperaDePlan = celda.immutable();
            return true;
        }
        if (desnivelReal() && intentarAvanceHaciaMision(now, "encadenado")) {
            ticksEnEsperaDePlan = 0;
            celdaEnEsperaDePlan = celda.immutable();
            return true;
        }
        // A2 · DESCESO PROGRESIVO EN DESNIVEL GRANDE (>=10): medido en `a2n-20261122-020402`, IDX4 bajó de
        // y=122 a y=109 en 6000 ticks y el descenso por DROP/escalón no llega. Un paso validado hacia abajo
        // cada 40 ticks lo baja de forma sostenida sin tocar presupuestos globales.
        if (desnivelDescenso() >= 10 && intentarDescensoHaciaMision(now, "desnivel_grande")) {
            ticksEnEsperaDePlan = 0;
            celdaEnEsperaDePlan = celda.immutable();
            return true;
        }
        // A2 · APROXIMACIÓN FINAL AL MURO: en la banda del anillo el avance micro-paso manda sobre el A*, para
        // que TODOS los atacantes se peguen al muro de la misma forma determinista (antes dependía de que el
        // plan local fallara, lo que introducía varianza entre corridas).
        if (misionNoDebajo() && cercaDelMuro() && intentarAvanceHaciaMision(now, "banda_muro")) {
            ticksEnEsperaDePlan = 0;
            celdaEnEsperaDePlan = celda.immutable();
            return true;
        }
        boolean mismaCelda = celdaEnEsperaDePlan != null && celdaEnEsperaDePlan.equals(celda);
        // A2 · Solo cuenta como avance una búsqueda global EN CURSO o una que ALCANZÓ la misión. Una búsqueda
        // que terminó SIN ruta no es progreso: si se contara, el contador de callejón no llegaría nunca a 0 y el
        // ente se quedaría reiniciando búsquedas de 8000 expansiones para siempre (medido: IDX7/8 clavados).
        boolean globalConRuta = globalCache == null || globalCache.pending() || globalCache.alcanzoMision();
        boolean globalAvanzo = globalExpansionesEnEspera >= 0
                && globalExpansionesTotal != globalExpansionesEnEspera && globalConRuta;
        boolean escalonAvanzo = escalonesEnEspera >= 0 && escalonesDeObjetivo != escalonesEnEspera;
        globalExpansionesEnEspera = globalExpansionesTotal;
        escalonesEnEspera = escalonesDeObjetivo;
        if (!mismaCelda || globalAvanzo || escalonAvanzo) {
            celdaEnEsperaDePlan = celda.immutable();
            ticksEnEsperaDePlan = 0;
            return false;
        }
        if (ticksEnEsperaDePlan < TUNNEL_STALL_TICKS) {
            ticksEnEsperaDePlan++;
            return false;
        }
        if (desnivelReal() && intentarAscenso(now, "callejon_sostenido")) {
            ticksEnEsperaDePlan = 0;
            celdaEnEsperaDePlan = celda.immutable();
            return true;
        }
        if (misionNoDebajo() && intentarAvanceHaciaMision(now, "callejon_sostenido")) {
            ticksEnEsperaDePlan = 0;
            celdaEnEsperaDePlan = celda.immutable();
            return true;
        }
        if (!intentarPlanDeTunel(now, "callejon_sostenido", true)) {
            return false;
        }
        ticksEnEsperaDePlan = 0;
        celdaEnEsperaDePlan = celda.immutable();
        return true;
    }

    /**
     * Pide un plan de túnel cuando la ruta local no existe o el frente es un callejón. Devuelve {@code true}
     * solo si hay un plan válido y este ejecutor pasa a ejecutarlo. La dirección es cardinal y debe acercar
     * de verdad a la misión; el plan se detiene en celdas protegidas o bloques no rompibles.
     */
    private boolean intentarPlanDeTunel(long now, String causa, boolean ultimoRecurso) {
        if (tunnelPlan != null) {
            return true;
        }
        if (!(mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az)) {
            return false;
        }
        if (breachMoveTarget != null || fuenteDropPendiente != null) {
            return false;
        }
        // A2 · EL TÚNEL NO COMPITE CON EL ESCALONADO. Si la misión exige DESCENSO y el plan de construcción
        // todavía es viable, esa estrategia manda: el escalonado ya está validado y el túnel solo debe entrar
        // cuando no queda camino de construcción (agotado o descenso bloqueado). Medido en
        // `a2n-20261121-212053`: sin esta prioridad el túnel se adelantó al escalón que resolvía IDX3.
        // Excepción medida (`a2n-20261121-221911`, IDX5): un CALLEJÓN SOSTENIDO (240 ticks sin ruta, sin avance
        // global ni escalonado) es evidencia más fuerte que "la construcción sigue siendo viable": ahí el túnel
        // sí puede entrar. Los otros dos disparadores conservan la prioridad intacta.
        if (!ultimoRecurso && TunnelPlanPolicy.constructionHasPriority(planMision != null
                        && mob.blockPosition().getY() > planMision.getY(),
                construccionAgotada, descensoBloqueado)) {
            return false;
        }
        if (!TunnelPlanPolicy.canRequest(now, tunnelNextPlanAt, tunnelPlansThisMission, tunnelStepsThisMission)) {
            return false;
        }
        BlockPos mision = misionFinal != null ? misionFinal : objective;
        if (mision == null) {
            return false;
        }
        BlockPos yo = mob.blockPosition();
        if (!NavObstruction.fits(mob.level(), mob, yo) || !NavObstruction.hasSupport(mob.level(), yo)) {
            return false;
        }
        int pasosDisponibles = TunnelPlanPolicy.stepsAvailable(tunnelStepsThisMission);
        if (pasosDisponibles <= 0) {
            return false;
        }
        int[][] direcciones = TunnelPlanPolicy.directionsToward(yo.getX(), yo.getZ(),
                mision.getX(), mision.getZ());
        java.util.List<TunnelActionPlanner.Step> mejor = java.util.List.of();
        double mejorAvance = TunnelPlanPolicy.MIN_HORIZONTAL_PROGRESS;
        for (int[] direccion : direcciones) {
            java.util.List<TunnelActionPlanner.Step> plan = TunnelActionPlanner.straight(yo,
                    direccion[0], direccion[1], Math.min(MAX_TUNEL_PASOS_POR_PLAN, pasosDisponibles),
                    p -> mob.level().hasChunkAt(p)
                            && az.puedeRomperNodoDeNavegacion(p)
                            && !mob.level().getBlockState(p).getCollisionShape(mob.level(), p).isEmpty(),
                    az::protegidaParaNavegacion,
                    p -> mob.level().hasChunkAt(p) && NavObstruction.hasSupport(mob.level(), p));
            if (plan.isEmpty()) {
                continue;
            }
            BlockPos post = plan.get(plan.size() - 1).post();
            double avance = TunnelPlanPolicy.horizontalProgress(yo.getX(), yo.getZ(), post.getX(), post.getZ(),
                    mision.getX(), mision.getZ());
            if (avance > mejorAvance) {
                mejorAvance = avance;
                mejor = plan;
            }
        }
        if (mejor.isEmpty()) {
            // Sin frente tunelable AHORA: no se martillea el mundo cada tick; se reintenta tras el enfriamiento.
            tunnelNextPlanAt = now + TunnelPlanPolicy.PLAN_COOLDOWN_TICKS;
            return false;
        }
        tunnelPlan = java.util.List.copyOf(mejor);
        tunnelCursor = TunnelExecutionPolicy.start(tunnelPlan.size());
        tunnelPhaseDeadline = now + TunnelPlanPolicy.PHASE_WAIT_TICKS;
        tunnelPlansThisMission++;
        tunnelNextPlanAt = now + TunnelPlanPolicy.PLAN_COOLDOWN_TICKS;
        ticksEnEsperaDePlan = 0;
        celdaEnEsperaDePlan = yo.immutable();
        // El túnel pasa a ser la única autoridad: ruta local y búsqueda global quedan invalidadas.
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalDesde = null;
        clearDropEdgeStep("tunnel_plan");
        clearDropApproachOwner("tunnel_plan");
        state = NavState.RECOVERING;
        if (NavTrace.on()) {
            TunnelActionPlanner.Step primero = tunnelPlan.get(0);
            NavTrace.ev("TUNNEL_PLAN_START", "uuid=" + mob.getUUID() + " tick=" + now
                    + " causa=" + causa
                    + " pos=" + yo.toShortString() + " mision=" + mision.toShortString()
                    + " direccion=" + (primero.post().getX() - yo.getX()) + ","
                    + (primero.post().getZ() - yo.getZ())
                    + " pasos=" + tunnelPlan.size()
                    + " planes=" + tunnelPlansThisMission + "/" + TunnelPlanPolicy.MAX_PLANS_PER_MISSION
                    + " pasosMision=" + tunnelStepsThisMission + "/" + TunnelPlanPolicy.MAX_STEPS_PER_MISSION
                    + " avance=" + String.format(java.util.Locale.ROOT, "%.2f", mejorAvance));
        }
        return true;
    }

    /** Un único dueño por tick: fase BREAK_* rompe un bloque; MOVE_POST emite el único movimiento. */
    private void tickTunnelPlan(long now) {
        if (tunnelPlan == null || tunnelCursor == null) {
            return;
        }
        if (tunnelCursor.complete()) {
            completarPlanDeTunel(now);
            return;
        }
        TunnelActionPlanner.Step step = tunnelPlan.get(tunnelCursor.stepIndex());
        // La ocupación EXACTA de `stand` solo se exige en las fases de rotura: en MOVE_POST la entidad
        // debe precisamente ABANDONAR el stand para ocupar `post`. Exigirla también allí descartaba el
        // plan justo cuando el movimiento se completaba (medido en `a2n-20261121-211306`).
        if (tunnelCursor.breakPhase()) {
            BlockPos stand = TunnelExecutionPolicy.standCell(tunnelCursor, tunnelPlan);
            if (stand == null || !mob.blockPosition().equals(stand)) {
                descartarPlanDeTunel(now, "stand_no_ocupado stand="
                        + (stand == null ? "-" : stand.toShortString())
                        + " pos=" + mob.blockPosition().toShortString());
                return;
            }
        }
        if (tunnelCursor.phase() == TunnelExecutionPolicy.Phase.MOVE_POST) {
            tickTunnelMove(now, step);
            return;
        }
        tickTunnelBreak(now, step, TunnelExecutionPolicy.breakTarget(tunnelCursor, tunnelPlan),
                TunnelExecutionPolicy.stepSibling(tunnelCursor, tunnelPlan));
    }

    private void tickTunnelBreak(long now, TunnelActionPlanner.Step step, BlockPos target, BlockPos sibling) {
        if (target == null) {
            descartarPlanDeTunel(now, "objetivo_de_tunel_nulo");
            return;
        }
        if (!(mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az)) {
            descartarPlanDeTunel(now, "ente_sin_actuador_de_tunel");
            return;
        }
        BlockState estado = mob.level().getBlockState(target);
        if (estado.isAir()) {
            // Ya abierto: se verifica sin gastar lease ni acción y se avanza de fase.
            avanzarFaseDeTunel(now, true, "ya_aire");
            return;
        }
        if (!az.puedeRomperNodoDeNavegacion(target)
                || estado.getCollisionShape(mob.level(), target).isEmpty()) {
            descartarPlanDeTunel(now, "bloque_no_rompible target=" + target.toShortString());
            return;
        }
        mob.getNavigation().stop();
        if (!tunelBreakEjecutable(step.stand(), target, sibling)) {
            descartarPlanDeTunel(now, "precondicion_de_tunel target=" + target.toShortString());
            return;
        }
        if (!az.tunelListo()) {
            // Cadencia/presupuesto del actuador: es una ESPERA acotada, no un fallo del paso.
            if (now > tunnelPhaseDeadline) {
                descartarPlanDeTunel(now, "actuador_sin_presupuesto_o_cadencia target="
                        + target.toShortString());
            }
            return;
        }
        if (!tryAcquireTunnelAction(target)) {
            if (now > tunnelPhaseDeadline) {
                descartarPlanDeTunel(now, "lease_rechazado target=" + target.toShortString());
            }
            return;
        }
        boolean invoked = recoveryAction != null
                && recoveryAction.run(objective == null ? target : objective, target, NavRoute.Transition.BREAK);
        boolean verificado = mob.level().getBlockState(target).isAir();
        if (!verificado) {
            descartarPlanDeTunel(now, "break_no_verificado invoked=" + invoked
                    + " target=" + target.toShortString());
            return;
        }
        avanzarFaseDeTunel(now, true, invoked ? "break_verificado" : "break_ya_resuelto");
    }

    private void tickTunnelMove(long now, TunnelActionPlanner.Step step) {
        BlockPos post = step.post();
        if (mob.blockPosition().equals(post)) {
            boolean soporte = NavObstruction.hasSupport(mob.level(), post);
            boolean cabe = NavObstruction.fits(mob.level(), mob, post);
            boolean aabbReal = NavObstruction.fitsActualAabb(mob.level(), mob);
            if (!soporte || !cabe || !aabbReal) {
                descartarPlanDeTunel(now, "post_invalido soporte=" + soporte + " cabe=" + cabe
                        + " aabb=" + aabbReal + " post=" + post.toShortString());
                return;
            }
            TunnelExecutionPolicy.Cursor antes = tunnelCursor;
            tunnelCursor = TunnelExecutionPolicy.afterMove(tunnelCursor, true);
            if (tunnelCursor.stepIndex() == antes.stepIndex()) {
                return;
            }
            tunnelStepsThisMission++;
            tunnelPhaseDeadline = now + TunnelPlanPolicy.PHASE_WAIT_TICKS;
            if (NavTrace.on()) {
                NavTrace.ev("TUNNEL_STEP_CONSUMED", "uuid=" + mob.getUUID() + " tick=" + now
                        + " paso=" + antes.stepIndex() + "/" + antes.planSize()
                        + " stand=" + step.stand().toShortString() + " post=" + post.toShortString()
                        + " pasosMision=" + tunnelStepsThisMission + "/" + TunnelPlanPolicy.MAX_STEPS_PER_MISSION
                        + " soporte=true aabb=true");
            }
            if (tunnelCursor.complete()) {
                completarPlanDeTunel(now);
            }
            return;
        }
        if (mob.blockPosition().distManhattan(post) > 3) {
            // Deriva fuera del paso (empuje/combate): el plan ya no describe el mundo, se replanifica.
            descartarPlanDeTunel(now, "movimiento_fuera_del_paso post=" + post.toShortString()
                    + " pos=" + mob.blockPosition().toShortString());
            return;
        }
        if (!NavObstruction.fits(mob.level(), mob, post) || !NavObstruction.hasSupport(mob.level(), post)) {
            descartarPlanDeTunel(now, "post_no_entrable post=" + post.toShortString());
            return;
        }
        if (now > tunnelPhaseDeadline) {
            descartarPlanDeTunel(now, "movimiento_sin_ocupacion post=" + post.toShortString()
                    + " pos=" + mob.blockPosition().toShortString());
            return;
        }
        double px = post.getX() + 0.5D;
        double py = post.getY();
        double pz = post.getZ() + 0.5D;
        mob.getNavigation().moveTo(px, py, pz, 1.0D);
        traceActuatorCommand("TUNNEL_MOVE_POST", post, now);
        if (mob.getNavigation().isDone()) {
            // La navegación de vanilla puede dar por completa una ruta de un nodo sin ocupar la celda.
            int manhattan = mob.blockPosition().distManhattan(post);
            boolean nudge = NavAdjacentApproachPolicy.shouldNudge(true, true, manhattan, 2,
                    NavObstruction.fits(mob.level(), mob, post), NavObstruction.hasSupport(mob.level(), post));
            if (nudge) {
                mob.getMoveControl().setWantedPosition(px, py, pz, 1.0D);
                if (NavTrace.on()) {
                    NavTrace.ev("TUNNEL_MOVE_NUDGE", "uuid=" + mob.getUUID() + " tick=" + now
                            + " post=" + post.toShortString() + " manhattan=" + manhattan
                            + " reason=navigation_done_without_physical_occupancy");
                }
            }
        }
    }

    private void avanzarFaseDeTunel(long now, boolean verificado, String motivo) {
        TunnelExecutionPolicy.Cursor antes = tunnelCursor;
        tunnelCursor = TunnelExecutionPolicy.afterBreak(tunnelCursor, verificado);
        if (tunnelCursor == antes) {
            return;
        }
        tunnelPhaseDeadline = now + (tunnelCursor.phase() == TunnelExecutionPolicy.Phase.MOVE_POST
                ? TunnelPlanPolicy.MOVE_WAIT_TICKS : TunnelPlanPolicy.PHASE_WAIT_TICKS);
        if (NavTrace.on()) {
            NavTrace.ev("TUNNEL_STEP_PHASE", "uuid=" + mob.getUUID() + " tick=" + now
                    + " paso=" + tunnelCursor.stepIndex() + "/" + tunnelCursor.planSize()
                    + " fase=" + tunnelCursor.phase() + " motivo=" + motivo
                    + " pos=" + mob.blockPosition().toShortString());
        }
    }

    /**
     * Mismas puertas que el BREAK de ruta (protección, permiso de rotura, stand de pie con soporte y alcance) más
     * una línea de visión que solo admite el bloque pedido o el otro bloque de la MISMA apertura de 2 de alto.
     */
    private boolean tunelBreakEjecutable(BlockPos stand, BlockPos target, BlockPos sibling) {
        if (stand == null || target == null) {
            return false;
        }
        if (!NavObstruction.fits(mob.level(), mob, stand) || !NavObstruction.hasSupport(mob.level(), stand)) {
            return false;
        }
        if (policies.isProtected(target) || !policies.canBreak(target)) {
            return false;
        }
        double distanciaSqr = mob.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D);
        if (!NavBreakReachPolicy.reachable(stand.getY(), target.getY(), distanciaSqr, ALCANCE_DE_ACCION_SQR)) {
            return false;
        }
        Vec3 ojo = mob.getEyePosition();
        BlockHitResult impacto = mob.level().clip(new ClipContext(ojo, target.getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        BlockPos primerBloque = impacto.getType() == HitResult.Type.BLOCK ? impacto.getBlockPos() : null;
        boolean aceptado = TunnelExecutionPolicy.lineOfSightAccepted(primerBloque, target, sibling);
        if (!aceptado && NavTrace.on()) {
            NavTrace.ev("TUNNEL_LOS_REJECT", "uuid=" + mob.getUUID() + " tick=" + mob.level().getGameTime()
                    + " stand=" + stand.toShortString() + " target=" + target.toShortString()
                    + " sibling=" + (sibling == null ? "-" : sibling.toShortString())
                    + " primerBloque=" + (primerBloque == null ? "-" : primerBloque.toShortString())
                    + " tipo=" + impacto.getType());
        }
        return aceptado;
    }

    /**
     * Lease del túnel: una única acción de terreno por entidad y tick, y solo para el bloque exacto que autoriza
     * el cursor (pies o cabeza del paso activo). Comparte el mismo par tick/usado que el lease de ruta, de modo
     * que no existen dos actuadores de terreno en el mismo tick.
     */
    public boolean tryAcquireTunnelAction(BlockPos requestedTarget) {
        if (tunnelPlan == null || tunnelCursor == null || tunnelCursor.complete()) {
            return false;
        }
        BlockPos autorizado = TunnelExecutionPolicy.breakTarget(tunnelCursor, tunnelPlan);
        long now = mob.level().getGameTime();
        boolean permitido = TraversalActionLeasePolicy.canAcquireTunnel(
                now, traversalActionLeaseTick, traversalActionLeaseUsed,
                tunnelCursor.breakPhase(), autorizado, NavRoute.Transition.BREAK, requestedTarget);
        if (!permitido) {
            return false;
        }
        traversalActionLeaseTick = now;
        traversalActionLeaseUsed = true;
        return true;
    }

    /** Descarta el plan COMPLETO, invalida ruta/global y deja el ejecutor replanificando. */
    private void descartarPlanDeTunel(long now, String motivo) {
        tunnelLastDiscard = motivo;
        tunnelPlan = null;
        tunnelCursor = null;
        tunnelPhaseDeadline = 0L;
        tunnelNextPlanAt = now + TunnelPlanPolicy.PLAN_COOLDOWN_TICKS;
        if (misionFinal != null) {
            objective = misionFinal.immutable();
        }
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        objetivoPermiteConstruccion = false;
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalDesde = null;
        state = NavState.PLANNING;
        nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
        if (NavTrace.on()) {
            NavTrace.ev("TUNNEL_PLAN_DISCARD", "uuid=" + mob.getUUID() + " tick=" + now
                    + " motivo={" + motivo + "} pos=" + mob.blockPosition().toShortString()
                    + " planes=" + tunnelPlansThisMission + "/" + TunnelPlanPolicy.MAX_PLANS_PER_MISSION
                    + " pasosMision=" + tunnelStepsThisMission + "/" + TunnelPlanPolicy.MAX_STEPS_PER_MISSION);
        }
    }

    /** Plan completado: el terreno cambió y la misión se replanifica desde la celda realmente ocupada. */
    private void completarPlanDeTunel(long now) {
        int pasos = tunnelPlan == null ? 0 : tunnelPlan.size();
        BlockPos fin = mob.blockPosition().immutable();
        tunnelPlan = null;
        tunnelCursor = null;
        tunnelPhaseDeadline = 0L;
        tunnelNextPlanAt = now + TunnelPlanPolicy.PLAN_COOLDOWN_TICKS;
        if (misionFinal != null) {
            objective = misionFinal.immutable();
        }
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        objetivoPermiteConstruccion = false;
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalDesde = null;
        state = NavState.PLANNING;
        nextPlanAt = now;
        if (NavTrace.on()) {
            NavTrace.ev("TUNNEL_PLAN_COMPLETE", "uuid=" + mob.getUUID() + " tick=" + now
                    + " pasos=" + pasos + " fin=" + fin.toShortString()
                    + " pasosMision=" + tunnelStepsThisMission + "/" + TunnelPlanPolicy.MAX_STEPS_PER_MISSION);
        }
    }

    /** Aborta sin replanificar ni tocar contadores de misión (cambio de misión u objetivo). */
    private void limpiarPlanDeTunel() {
        tunnelPlan = null;
        tunnelCursor = null;
        tunnelPhaseDeadline = 0L;
    }

    /** A2 diagnóstico acotado del túnel subordinado (una línea). */
    public String tunelDiag() {
        StringBuilder sb = new StringBuilder();
        sb.append("planes=").append(tunnelPlansThisMission).append('/')
                .append(TunnelPlanPolicy.MAX_PLANS_PER_MISSION)
                .append(" pasos=").append(tunnelStepsThisMission).append('/')
                .append(TunnelPlanPolicy.MAX_STEPS_PER_MISSION)
                .append(" disc=").append(tunnelLastDiscard);
        if (tunnelPlan == null || tunnelCursor == null) {
            return sb.append(" plan=-").toString();
        }
        sb.append(" plan=").append(tunnelCursor.stepIndex()).append('/').append(tunnelCursor.planSize())
                .append(':').append(tunnelCursor.phase());
        if (!tunnelCursor.complete()) {
            TunnelActionPlanner.Step step = tunnelPlan.get(tunnelCursor.stepIndex());
            sb.append(" stand=").append(step.stand().toShortString())
                    .append(" feet=").append(step.feetBlock().toShortString())
                    .append(" head=").append(step.headBlock().toShortString())
                    .append(" post=").append(step.post().toShortString());
        }
        return sb.toString();
    }

    // ============================================================================================
    // A2 · AVANCE CODICIOSO VALIDADO. Cuando el plan falla y la misión está POR ENCIMA, el ente puede estar en
    // terreno abierto a 8-10 bloques del acantilado: ni el escalón (no hay cara adyacente) ni el túnel (el frente
    // es aire) aplican. Se entrega un objetivo local acotado que ACERCA de verdad.
    // ============================================================================================
    private boolean intentarAvanceHaciaMision(long now, String causa) {
        if (now < avanceNextAt) {
            return false;
        }
        BlockPos mision = misionDeReferencia();
        if (mision == null) {
            return false;
        }
        BlockPos yo = mob.blockPosition();
        if (mision.getY() < yo.getY()) {
            return false;
        }
        if (avanceObjetivo != null && avanceObjetivo.equals(objective) && esPieValido(avanceObjetivo)
                && now - avanceObjetivoEn <= 200L) {
            return false; // ya hay un escalón de avance válido en curso (caduca a los 200 ticks)
        }
        double actual = horizontalAlCuadrado(yo, mision);
        double actualLineal = Math.sqrt(actual);
        // Con un desnivel real (>=3) o CERCA DEL MURO (<=66 del objetivo) hacen falta PASOS CORTOS para llegar
        // pegado al muro: exigir 3 bloques dejaba al ente a una celda del anillo sin poder avanzar (medido:
        // IDX8 en 720,71,509 a dist 63,7 con el plan agotado). La memoria de celdas recientes evita el vaivén.
        double umbral = (mision.getY() - yo.getY() >= 3 || actualLineal <= 66.0D) ? 0.5D : 3.0D;
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        // Barrido en CRUZ (4 direcciones cardinales × distancia 2..10): suficiente para acercarse al muro y
        // mucho más barato que el cuadrado completo, para poder intentarlo en cada tick de PLANNING.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            for (int dist = 2; dist <= 10; dist++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos candidato = yo.offset(dir[0] * dist, dy, dir[1] * dist);
                    if (!mob.level().hasChunkAt(candidato) || !esPieValido(candidato)
                            || recentCells.contains(candidato)) {
                        continue;
                    }
                    double d = horizontalAlCuadrado(candidato, mision);
                    if (Math.sqrt(d) >= actualLineal - umbral) {
                        continue;
                    }
                    if (d < mejorDist) {
                        mejorDist = d;
                        mejor = candidato;
                    }
                }
            }
        }
        if (mejor == null) {
            avanceNextAt = now + 40L;
            return false;
        }
        avanceNextAt = now + 20L;
        avanceObjetivo = mejor.immutable();
        avanceObjetivoEn = now;
        objective = mejor.immutable();
        objetivoPermiteConstruccion = false;
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        objetivoTactico = false;
        plannedFrom = null;
        route = null;
        routeIndex = 0;
        nextPlanAt = now;
        state = NavState.PLANNING;
        if (NavTrace.on()) {
            NavTrace.ev("PROGRESS_STEP_TARGET", "uuid=" + mob.getUUID() + " tick=" + now
                    + " causa=" + causa + " pos=" + yo.toShortString()
                    + " objetivo=" + mejor.toShortString() + " mision=" + mision.toShortString()
                    + " dAntes=" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(actual))
                    + " dDespues=" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(mejorDist)));
        }
        return true;
    }

    // ============================================================================================
    // A2 · DESCESO PROGRESIVO VALIDADO. Cuando la misión está POR DEBAJO y el plan falla (DROP rechazado,
    // escalón en CADENCE_WAIT), el ente puede quedar a pocos bloques de la cota sin bajar el último tramo:
    // medido en `a2n-20261122-013840`, IDX4 en 586,76,522 con `fallosDeDescenso=19`. Esta acción da UN paso
    // de descenso (1-3 bloques) hacia la misión, solo a una celda de pie válida con soporte que ACERCA.
    // ============================================================================================
    private long descensoNextAt;

    private boolean intentarDescensoHaciaMision(long now, String causa) {
        if (now < descensoNextAt) {
            return false;
        }
        BlockPos mision = misionDeReferencia();
        if (mision == null) {
            return false;
        }
        BlockPos yo = mob.blockPosition();
        if (mision.getY() >= yo.getY() || !mob.onGround()) {
            return false;
        }
        if (!esPieValido(yo) || !NavObstruction.hasSupport(mob.level(), yo)) {
            return false;
        }
        double actual = horizontalAlCuadrado(yo, mision);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        BlockPos mejor = null;
        double mejorDist = actual;
        for (int[] dir : dirs) {
            for (int dist = 1; dist <= 3; dist++) {
                for (int dy = -1; dy >= -3; dy--) {
                    BlockPos candidato = yo.offset(dir[0] * dist, dy, dir[1] * dist);
                    if (!mob.level().hasChunkAt(candidato) || !esPieValido(candidato)
                            || !NavObstruction.hasSupport(mob.level(), candidato)
                            || !NavObstruction.fits(mob.level(), mob, candidato)) {
                        continue;
                    }
                    double d = horizontalAlCuadrado(candidato, mision);
                    if (d >= actual - 0.25D) {
                        continue;
                    }
                    if (d < mejorDist) {
                        mejorDist = d;
                        mejor = candidato;
                    }
                }
            }
        }
        if (mejor == null) {
            descensoNextAt = now + 30L;
            return false;
        }
        descensoNextAt = now + 40L;
        mob.getNavigation().moveTo(mejor.getX() + 0.5D, mejor.getY(), mejor.getZ() + 0.5D, 1.0D);
        traceActuatorCommand("DESCEND_STEP", mejor, now);
        if (NavTrace.on()) {
            NavTrace.ev("DESCEND_STEP", "uuid=" + mob.getUUID() + " tick=" + now
                    + " causa=" + causa + " pos=" + yo.toShortString()
                    + " hacia=" + mejor.toShortString() + " mision=" + mision.toShortString());
        }
        return true;
    }

    // ============================================================================================
    // A2 · PASO DE ENTRADA AL RECINTO. Medido en `a2n-20261122-004219`: IDX3 e IDX7 quedan SOBRE el anillo
    // (y=72, distancia 62,46) sin poder bajar al interior; el A* local no encuentra arista para bajar del muro
    // y la misión queda a 0,46 del umbral estricto. Esta acción da UN paso de descenso (1-3 bloques) hacia la
    // misión, solo si la entidad está por ENCIMA del destino y el candidato es una celda de pie válida que
    // ACERCA. Acotada: enfriamiento y ventana de 66,5 bloques alrededor del centro.
    // ============================================================================================
    private long recintoNextAt;

    private boolean intentarEntrarAlRecinto(long now) {
        if (now < recintoNextAt) {
            return false;
        }
        BlockPos mision = misionDeReferencia();
        if (mision == null || !mob.onGround()) {
            return false;
        }
        BlockPos yo = mob.blockPosition();
        double dist = Math.sqrt(horizontalAlCuadrado(yo, mision));
        if (dist > 66.5D || dist <= 60.5D) {
            return false;
        }
        if (!esPieValido(yo) || !NavObstruction.hasSupport(mob.level(), yo)) {
            return false;
        }
        int sx = Integer.signum(mision.getX() - yo.getX());
        int sz = Integer.signum(mision.getZ() - yo.getZ());
        if (Math.abs(mision.getX() - yo.getX()) >= Math.abs(mision.getZ() - yo.getZ())) {
            sz = 0;
        } else {
            sx = 0;
        }
        int[][] pasos = {{sx, 0}, {0, sz}};
        for (int[] paso : pasos) {
            if (paso[0] == 0 && paso[1] == 0) {
                continue;
            }
            for (int dy = -1; dy >= -3; dy--) {
                BlockPos candidato = yo.offset(paso[0], dy, paso[1]);
                if (!mob.level().hasChunkAt(candidato) || !esPieValido(candidato)
                        || !NavObstruction.hasSupport(mob.level(), candidato)
                        || !NavObstruction.fits(mob.level(), mob, candidato)) {
                    continue;
                }
                if (horizontalAlCuadrado(candidato, mision) >= horizontalAlCuadrado(yo, mision)) {
                    continue;
                }
                recintoNextAt = now + 10L;
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(candidato.getX() + 0.5D, candidato.getY(),
                        candidato.getZ() + 0.5D, 1.0D);
                if (NavTrace.on()) {
                    NavTrace.ev("ENCLOSURE_STEP", "uuid=" + mob.getUUID() + " tick=" + now
                            + " pos=" + yo.toShortString() + " hacia=" + candidato.toShortString()
                            + " mision=" + mision.toShortString()
                            + " dist=" + String.format(java.util.Locale.ROOT, "%.2f", dist));
                }
                return true;
            }
        }
        recintoNextAt = now + 40L;
        return false;
    }

    // ============================================================================================
    // A2 · ESCALÓN DE ASCENSO. Cuando la misión está POR ENCIMA y el terreno no ofrece un escalón natural
    // (meseta con escalón vertical de 6-8 bloques), el ejecutor despeja la cara (pies, cabeza y techo del
    // escalón) y coloca un bloque en la celda de pies; después sube físicamente a él. Una acción de terreno
    // por tick con lease, verificación real y presupuesto compartido con el túnel.
    // ============================================================================================

    /**
     * A2 · REFERENCIA DE MISIÓN PARA LAS ACCIONES DE TERRENO. `misionDePie()` puede ser null cuando la misión
     * cae en una celda no pisable (edificio/huerto del punto de aproximación); para decidir "hay que subir" y
     * hacia dónde, la altura de la misión FINAL es suficiente y no depende de que exista una celda de pie.
     */
    private BlockPos misionDeReferencia() {
        BlockPos dePie = misionDePie();
        return dePie != null ? dePie : misionFinal;
    }

    /** ¿La misión utilizable está por encima de los pies? Solo entonces tiene sentido un ascenso. */
    private boolean misionPorEncima() {
        BlockPos mision = misionDeReferencia();
        return mision != null && mob.blockPosition().getY() < mision.getY();
    }

    /** ¿Hay un desnivel REAL (>= 3 bloques) que exige escalones de ascenso? */
    private boolean desnivelReal() {
        BlockPos mision = misionDeReferencia();
        return mision != null && mision.getY() - mob.blockPosition().getY() >= 3;
    }

    /** ¿La misión está al mismo nivel o por encima? (el avance horizontal también aplica a nivel). */
    private boolean misionNoDebajo() {
        BlockPos mision = misionDeReferencia();
        return mision != null && mision.getY() >= mob.blockPosition().getY();
    }

    /** ¿La misión está por debajo de los pies? (descenso) */
    private boolean misionDebajo() {
        BlockPos mision = misionDeReferencia();
        return mision != null && mision.getY() < mob.blockPosition().getY();
    }

    /** ¿La entidad está ya en la banda del muro (<=66 del objetivo)? */
    private boolean cercaDelMuro() {
        BlockPos mision = misionDeReferencia();
        return mision != null
                && horizontalAlCuadrado(mob.blockPosition(), mision) <= 66.0D * 66.0D;
    }

    /** Desnivel de descenso pendiente (positivo si la misión está por debajo). */
    private int desnivelDescenso() {
        BlockPos mision = misionDeReferencia();
        return mision == null ? 0 : mob.blockPosition().getY() - mision.getY();
    }

    /**
     * Pide un escalón de ascenso si la misión está por encima y hay una cara rompible hacia ella. Devuelve
     * {@code true} si tomó el control (plan iniciado). Todos los topes son duros y la celda se valida contra
     * el MISMO contrato que usa el ejecutor para romper y colocar.
     */
    private boolean intentarAscenso(long now, String causa) {
        if (ascensoBloque != null) {
            return true;
        }
        if (!(mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az)) {
            return false;
        }
        if (breachMoveTarget != null || fuenteDropPendiente != null || tunnelPlan != null) {
            return false;
        }
        if (now < ascensoNextAt || ascensosEnMision >= MAX_ASCENSOS_MISION
                || ascensoFallos >= ASCENSO_MAX_FALLOS) {
            return false;
        }
        BlockPos mision = misionDeReferencia();
        if (mision == null) {
            return false;
        }
        BlockPos yo = mob.blockPosition();
        if (mision.getY() <= yo.getY()) {
            return false;
        }
        if (!esPieValido(yo) || !NavObstruction.hasSupport(mob.level(), yo)) {
            return false;
        }
        int[][] direcciones = TunnelPlanPolicy.directionsToward(yo.getX(), yo.getZ(),
                mision.getX(), mision.getZ());
        String primerRechazo = "-";
        for (int[] direccion : direcciones) {
            BlockPos cara = yo.offset(direccion[0], 0, direccion[1]);
            if (!mob.level().hasChunkAt(cara) || az.protegidaParaNavegacion(cara)) {
                if (primerRechazo.equals("-")) primerRechazo = "carga_o_protegida " + cara.toShortString();
                continue;
            }
            if (mob.level().getBlockState(cara).getCollisionShape(mob.level(), cara).isEmpty()) {
                if (primerRechazo.equals("-")) primerRechazo = "sin_cara " + cara.toShortString();
                continue; // no es una cara: ahí no hay nada que escalar
            }
            // Solo muros de 2+ bloques. Un escalón de 1 se sube andando (STEP_UP): picarlo y reconstruirlo
            // sería un desperdicio de presupuesto y de terreno.
            if (NavObstruction.classify(mob.level(), mob, cara, policies) != NavObstruction.Kind.BREAKABLE) {
                if (primerRechazo.equals("-")) {
                    primerRechazo = "no_muro " + cara.toShortString() + " kind="
                            + NavObstruction.classify(mob.level(), mob, cara, policies);
                }
                continue;
            }
            if (!az.puedeRomperNodoDeNavegacion(cara)) {
                continue;
            }
            BlockPos cabeza = cara.above();
            BlockPos techo = cara.above(2);
            if (!NavClimbStepPolicy.clearable(true, true, false)) {
                continue;
            }
            if (!mob.level().getBlockState(cabeza).getCollisionShape(mob.level(), cabeza).isEmpty()
                    && !az.puedeRomperNodoDeNavegacion(cabeza)) {
                continue;
            }
            if (!mob.level().getBlockState(techo).getCollisionShape(mob.level(), techo).isEmpty()
                    && !az.puedeRomperNodoDeNavegacion(techo)) {
                continue;
            }
            if (!NavObstruction.hasSolidFace(mob.level(), cara.below())) {
                continue; // el escalón necesita apoyo debajo
            }
            ascensoOrigen = yo.immutable();
            ascensoBloque = cara.immutable();
            ascensoColocado = false;
            ascensoDeadline = now + ASCENSO_FASE_MAX_TICKS;
            state = NavState.RECOVERING;
            if (NavTrace.on()) {
                NavTrace.ev("CLIMB_STEP_START", "uuid=" + mob.getUUID() + " tick=" + now
                        + " causa=" + causa + " stand=" + yo.toShortString()
                        + " face=" + cara.toShortString() + " head=" + cabeza.toShortString()
                        + " top=" + techo.toShortString()
                        + " mision=" + mision.toShortString()
                        + " ascensos=" + ascensosEnMision + "/" + MAX_ASCENSOS_MISION);
            }
            return true;
        }
        ascensoNextAt = now + 40L;
        if (NavTrace.on()) {
            NavTrace.ev("CLIMB_STEP_PROBE", "uuid=" + mob.getUUID() + " tick=" + now
                    + " causa=" + causa + " pos=" + yo.toShortString()
                    + " mision=" + mision.toShortString() + " rechazo={" + primerRechazo + "}");
        }
        return false;
    }

    /** Una acción de terreno (o el movimiento final) por tick, con verificación real. */
    private void tickAscenso(long now) {
        if (ascensoBloque == null) {
            return;
        }
        if (!(mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az)) {
            abortarAscenso(now, "sin_actuador");
            return;
        }
        BlockPos cara = ascensoBloque;
        BlockPos arriba = cara.above();
        if (NavClimbStepPolicy.landingVerified(mob.blockPosition().equals(arriba), mob.onGround(),
                NavObstruction.hasSupport(mob.level(), arriba),
                NavObstruction.fitsActualAabb(mob.level(), mob))) {
            ascensosEnMision++;
            ascensoUltimo = "subido " + cara.toShortString() + " -> " + arriba.toShortString();
            ascensoFallos = 0;
            limpiarAscenso();
            // Sin enfriamiento tras un ascenso verificado: el escalón siguiente se intenta de inmediato.
            ascensoNextAt = now;
            if (NavTrace.on()) {
                NavTrace.ev("CLIMB_STEP_DONE", "uuid=" + mob.getUUID() + " tick=" + now
                        + " face=" + cara.toShortString() + " feet=" + arriba.toShortString()
                        + " ascensos=" + ascensosEnMision + "/" + MAX_ASCENSOS_MISION
                        + " pos=" + mob.blockPosition().toShortString());
            }
            terrainChanged();
            nextPlanAt = now;
            state = NavState.PLANNING;
            return;
        }
        // A2 · El salto de un bloque es parte del ascenso: NO se aborta por estar en el aire ni por un desvío
        // de una celda. Solo se abandona si la entidad YA está en el suelo, lejos del origen y fuera del destino.
        // Medido en `a2n-20261121-232929`: el mob saltó (blockPosition y=64) y el aborto lo mató a mitad de vuelo
        // justo después de colocar el escalón.
        if (ascensoOrigen != null && !mob.blockPosition().equals(ascensoOrigen)
                && !mob.blockPosition().equals(arriba)
                && mob.onGround() && mob.blockPosition().distManhattan(ascensoOrigen) > 1) {
            abortarAscenso(now, "movido pos=" + mob.blockPosition().toShortString());
            return;
        }
        if (now > ascensoDeadline) {
            abortarAscenso(now, "timeout face=" + cara.toShortString());
            return;
        }
        if (!ascensoColocado) {
            // 1) Despejar la cara: pies, cabeza y techo del escalón (una celda por tick). ADEMÁS se despeja el
            // TECHO DEL PROPIO STAND (mob feet + 2): al saltar al escalón la cabeza sube un bloque y, si esa
            // celda es sólida, el salto queda capado y el mob nunca corona el segundo escalón. Medido en
            // `a2n-20261122-003217`: IDX7 con el bloque 2 colocado y `pathCanReach=false`, cabeza topando.
            BlockPos techoDelStand = ascensoOrigen == null ? null : ascensoOrigen.above(2);
            for (BlockPos celda : new BlockPos[]{techoDelStand, cara, arriba, cara.above(2)}) {
                if (celda == null
                        || mob.level().getBlockState(celda).getCollisionShape(mob.level(), celda).isEmpty()) {
                    continue;
                }
                if (az.protegidaParaNavegacion(celda) || !az.puedeRomperNodoDeNavegacion(celda)) {
                    abortarAscenso(now, "bloque_no_rompible " + celda.toShortString());
                    return;
                }
                mob.getNavigation().stop();
                if (!az.tunelListo()) {
                    return; // cadencia del actuador: espera acotada por ascensoDeadline
                }
                if (!tryAcquireExplicit(NavRoute.Transition.BREAK, celda)) {
                    return;
                }
                boolean invoked = recoveryAction != null
                        && recoveryAction.run(objective == null ? celda : objective, celda,
                        NavRoute.Transition.BREAK);
                if (!mob.level().getBlockState(celda).getCollisionShape(mob.level(), celda).isEmpty()) {
                    abortarAscenso(now, "break_no_verificado invoked=" + invoked
                            + " " + celda.toShortString());
                    return;
                }
                ascensoDeadline = now + ASCENSO_FASE_MAX_TICKS;
                return; // una accion por tick
            }
            // 2) Colocar el escalón en la celda de la cara. PRIMERO se centra la entidad en SU celda: si está
            // pegada al borde, su AABB (inflado) invade la celda vecina y la primitiva rechaza la colocación.
            // Medido en `a2n-20261121-231122`: `escalon_no_colocable soporte=true invade=true` y el ascenso
            // se abortaba sin llegar a colocar nada.
            if (ascensoOrigen != null && !NavSourceCellPolicy.centeredInCell(
                    mob.getX(), mob.getZ(), ascensoOrigen, 0.20D)) {
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(ascensoOrigen.getX() + 0.5D, mob.getY(),
                        ascensoOrigen.getZ() + 0.5D, 0.35D);
                if (now > ascensoDeadline) {
                    abortarAscenso(now, "no_centrado " + ascensoOrigen.toShortString());
                }
                return;
            }
            boolean soporte = NavObstruction.hasSolidFace(mob.level(), cara.below())
                    || NavObstruction.hasSupportFace(mob.level(), cara);
            boolean invade = new net.minecraft.world.phys.AABB(cara)
                    .intersects(mob.getBoundingBox().inflate(0.05D));
            boolean colocable = NavClimbStepPolicy.placeable(true, true, true, soporte, invade,
                    az.protegidaParaNavegacion(cara));
            if (!colocable) {
                abortarAscenso(now, "escalon_no_colocable soporte=" + soporte + " invade=" + invade);
                return;
            }
            if (!tryAcquireExplicit(NavRoute.Transition.BUILD, cara)) {
                return;
            }
            boolean colocado = placement != null && placement.place(cara);
            if (!colocado || mob.level().getBlockState(cara).getCollisionShape(mob.level(), cara).isEmpty()) {
                abortarAscenso(now, "place_fallido " + cara.toShortString());
                return;
            }
            ascensoColocado = true;
            ascensoDeadline = now + ASCENSO_FASE_MAX_TICKS;
            if (NavTrace.on()) {
                NavTrace.ev("CLIMB_STEP_PLACED", "uuid=" + mob.getUUID() + " tick=" + now
                        + " face=" + cara.toShortString() + " pos=" + mob.blockPosition().toShortString());
            }
            return;
        }
        // 3) Subir físicamente al escalón colocado.
        if (!NavObstruction.fits(mob.level(), mob, arriba)
                || !NavObstruction.hasSupport(mob.level(), arriba)) {
            abortarAscenso(now, "destino_no_entrable " + arriba.toShortString());
            return;
        }
        double px = arriba.getX() + 0.5D;
        double py = arriba.getY();
        double pz = arriba.getZ() + 0.5D;
        mob.getNavigation().moveTo(px, py, pz, 1.0D);
        traceActuatorCommand("CLIMB_STEP_MOVE", arriba, now);
        if (mob.getNavigation().isDone()) {
            int manhattan = mob.blockPosition().distManhattan(arriba);
            boolean nudge = NavAdjacentApproachPolicy.shouldNudge(true, true, manhattan, 2,
                    NavObstruction.fits(mob.level(), mob, arriba),
                    NavObstruction.hasSupport(mob.level(), arriba));
            if (nudge) {
                mob.getMoveControl().setWantedPosition(px, py, pz, 1.0D);
                // A2 · SALTO ASISTIDO: subir un bloque exige saltar. Medido en `a2n-20261122-002353`: el
                // pathfinder declaró el segundo escalón inalcanzable (`pathCanReach=false`) y el mob se quedó
                // abajo, con el bloque colocado. Se pide el salto de forma acotada (uno cada 10 ticks).
                if (now % 10L == 0L) {
                    mob.getJumpControl().jump();
                    if (NavTrace.on()) {
                        NavTrace.ev("CLIMB_STEP_JUMP", "uuid=" + mob.getUUID() + " tick=" + now
                                + " target=" + arriba.toShortString()
                                + " pos=" + mob.blockPosition().toShortString());
                    }
                }
            }
        }
    }

    private void abortarAscenso(long now, String motivo) {
        ascensoUltimo = motivo;
        ascensoFallos++;
        limpiarAscenso();
        ascensoNextAt = now + ASCENSO_COOLDOWN_TICKS;
        state = NavState.PLANNING;
        nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
        if (NavTrace.on()) {
            NavTrace.ev("CLIMB_STEP_ABORTED", "uuid=" + mob.getUUID() + " tick=" + now
                    + " motivo={" + motivo + "} fallos=" + ascensoFallos + "/" + ASCENSO_MAX_FALLOS
                    + " pos=" + mob.blockPosition().toShortString());
        }
    }

    private void limpiarAscenso() {
        ascensoOrigen = null;
        ascensoBloque = null;
        ascensoColocado = false;
        ascensoDeadline = 0L;
    }

    private boolean tryAcquireExplicit(NavRoute.Transition transicion, BlockPos objetivo) {
        long now = mob.level().getGameTime();
        boolean permitido = TraversalActionLeasePolicy.canAcquireExplicit(
                now, traversalActionLeaseTick, traversalActionLeaseUsed,
                ascensoBloque != null, transicion, objetivo, transicion, objetivo);
        if (!permitido) {
            return false;
        }
        traversalActionLeaseTick = now;
        traversalActionLeaseUsed = true;
        return true;
    }

    /** A2 diagnóstico acotado del escalón de ascenso (una línea). */
    public String ascensoDiag() {
        return "ascensos=" + ascensosEnMision + "/" + MAX_ASCENSOS_MISION
                + " fallos=" + ascensoFallos + "/" + ASCENSO_MAX_FALLOS
                + " ultimo={" + ascensoUltimo + "}"
                + " activo=" + (ascensoBloque == null ? "-"
                : ascensoBloque.toShortString() + " colocado=" + ascensoColocado);
    }

    private boolean intentarRoturaDeRecuperacion() {
        long now = mob.level().getGameTime();
        if (breachMoveTarget != null || misionFinal == null
                || !(mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az)) {
            return false;
        }
        BlockPos abierto = az.abrirPasajeHacia(misionFinal);
        if (abierto == null) {
            return false;
        }
        breachMoveTarget = abierto;
        breachMoveTicks = 0;
        breachChainSteps = 1;
        breachMoveUntil = now + 50L;
        route = null;
        routeIndex = 0;
        lastRouteSignature = 0;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalDesde = null;
        state = NavState.PLANNING;
        if (NavTrace.on()) {
            NavTrace.ev("NAV_RECOVERY_BREACH", "uuid=" + mob.getUUID() + " tick=" + now
                    + " opened=" + abierto.toShortString()
                    + " pos=" + mob.blockPosition().toShortString());
        }
        return true;
    }

    private boolean escalonarObjetivoLocal() {
        if (NavTrace.on()) {
            NavTrace.ev("A2_LOCAL_STAIR_ENTER", "uuid=" + mob.getUUID() + " tick="
                    + mob.level().getGameTime() + " local=" + escalonesDeObjetivo
                    + "/" + MAX_ESCALONES_DE_OBJETIVO + " fallos=" + fallosDeEscalonDeObjetivo
                    + "/" + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO + " mision=" + misionFinal
                    + " objetivo=" + objective);
        }
        if (misionFinal == null) {
            return false;
        }
        if (escalonesDeObjetivo >= MAX_ESCALONES_DE_OBJETIVO) {
            fallosDeEscalonDeObjetivo = MAX_FALLOS_DE_ESCALON_DE_OBJETIVO;
            if (NavTrace.on()) {
                NavTrace.ev("A2_LOCAL_STAIR_WRITE", "uuid=" + mob.getUUID() + " tick="
                        + mob.level().getGameTime() + " op=SET_FALLOS_MAX old=?"
                        + " new=" + fallosDeEscalonDeObjetivo + " reason=LOCAL_LIMIT_AT_ENTRY");
            }
            ultimoEscalonDeObjetivo = "tope de escalones locales alcanzado (" + MAX_ESCALONES_DE_OBJETIVO
                    + ") hacia " + misionFinal.toShortString();
            return false;
        }
        BlockPos siguiente = elegirObjetivoLocal();
        if (globalSearchPending) {
            ultimoEscalonDeObjetivo = "búsqueda global incremental pendiente; no consumir intento local";
            return false;
        }
        if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT) {
            // A2 · TICK DE LA CADENCIA. Antes se retornaba sin llamar al constructor y el retraso (`escaleraRetraso`)
            // no avanzaba NUNCA: medido en `a2n-20261122-020402`, IDX4 con `A2_LOCAL_STAIR_WAIT CADENCE_WAIT
            // counters_unchanged=true` durante miles de ticks. Ahora se llama al constructor para que consuma la
            // cadencia; si sigue en espera, no se gasta paso local.
            intentarConstruirEscalonSinWaypoint(misionFinal, mob.blockPosition(), "tick_cadencia");
            ultimoEscalonDeObjetivo = "esperando cadencia de escalera; no consumir paso local";
            return false;
        }
        boolean siguientePermiteConstruccion = objetivoPermiteConstruccion
                && NavBuildObjectivePolicy.isValid(siguiente, objetivoAccionConstruccion);
        BlockPos siguienteAccionConstruccion = siguientePermiteConstruccion
                ? objetivoAccionConstruccion : null;
        if (siguiente == null) {
            fallosDeEscalonDeObjetivo++;
            if (NavTrace.on()) {
                NavTrace.ev("A2_LOCAL_STAIR_WRITE", "uuid=" + mob.getUUID() + " tick="
                        + mob.level().getGameTime() + " op=INC_FAIL new="
                        + fallosDeEscalonDeObjetivo + " reason=NO_LOCAL_OBJECTIVE");
            }
            ultimoEscalonDeObjetivo = ultimoEscalonDeObjetivo + " [intento " + fallosDeEscalonDeObjetivo + "/"
                    + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO + "]";
            return false;
        }
        // La idempotencia tiene dos salidas distintas: reutilizar un waypoint que sigue siendo ejecutable,
        // o rechazar una repeticion que sigue siendo inutilizable. Esta segunda salida NO es un nuevo escalon.
        boolean accionLocalValida = esAccionLocalValida(objective);
        boolean mismoObjetivo = siguiente.equals(objective);
        boolean objetivoActualUtilizable = esPieValido(objective)
                || siguientePermiteConstruccion || accionLocalValida;
        boolean objetivoDentroVentana = objective != null
                && dentroDeLaVentana(mob.blockPosition(), objective);
        NavLocalObjectiveStepPolicy.Decision decision = NavLocalObjectiveStepPolicy.decidir(
                mismoObjetivo, objetivoActualUtilizable, objetivoDentroVentana);
        if (decision == NavLocalObjectiveStepPolicy.Decision.REUTILIZAR) {
            return true; // el waypoint existente sigue siendo ejecutable; se conserva su ruta/transicion
        }
        if (decision == NavLocalObjectiveStepPolicy.Decision.RECHAZAR_REPETIDO_INUTILIZABLE) {
            fallosDeEscalonDeObjetivo++;
            ultimoEscalonDeObjetivo = "objetivo local repetido e inutilizable "
                    + siguiente.toShortString() + " [fallo " + fallosDeEscalonDeObjetivo + "/"
                    + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO + "]";
            if (NavTrace.on()) {
                NavTrace.ev("A2_LOCAL_STAIR_WRITE", "uuid=" + mob.getUUID() + " tick="
                        + mob.level().getGameTime() + " op=REJECT_REPEAT_UNUSABLE"
                        + " objective=" + siguiente.toShortString()
                        + " local=" + escalonesDeObjetivo
                        + " fallos=" + fallosDeEscalonDeObjetivo);
            }
            return false;
        }
        // Solo una identidad de objetivo nueva puede consumir un escalon. El contador de fallos
        // vuelve a cero al cambiar realmente de objetivo, no al volver a elegir el mismo candidato.
        fallosDeEscalonDeObjetivo = 0;
        escalonesDeObjetivo++;
        if (!NavLocalObjectiveProgressPolicy.aceptar(mob.blockPosition().getY(),
                misionFinal.getY(), siguiente.getY(),
                mob.getX(), mob.getZ(),
                misionFinal.getX() + 0.5D, misionFinal.getZ() + 0.5D,
                siguiente.getX() + 0.5D, siguiente.getZ() + 0.5D,
                objetivoTransicionLocal != null && objetivoTransicionLocal != NavRoute.Transition.WALK,
                mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az
                        && az.objetivoSobreUltimoEscalon() != null)) {
            escalonesDeObjetivo--;
            fallosDeEscalonDeObjetivo++;
            ultimoEscalonDeObjetivo = "candidato local sin progreso vertical: pos="
                    + mob.blockPosition().toShortString() + " mision=" + misionFinal.toShortString()
                    + " candidato=" + siguiente.toShortString() + " dy="
                    + (siguiente.getY() - mob.blockPosition().getY());
            return false;
        }
        if (NavTrace.on()) {
            NavTrace.ev("A2_LOCAL_STAIR_WRITE", "uuid=" + mob.getUUID() + " tick="
                    + mob.level().getGameTime() + " op=INC_STEP new=" + escalonesDeObjetivo
                    + " objective=" + siguiente
                    + " buildAuthorized=" + siguientePermiteConstruccion
                    + " buildActionPos=" + (siguienteAccionConstruccion == null
                            ? "-" : siguienteAccionConstruccion.toShortString()));
        }
        objective = siguiente;
        objetivoPermiteConstruccion = siguientePermiteConstruccion;
        objetivoAccionConstruccion = siguienteAccionConstruccion;
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        plannedFrom = null;
        route = null;
        routeIndex = 0;
        replans = 0;
        lastRouteSignature = 0;
        nextPlanAt = 0L;
        nextBudgetRetryAt = 0L;
        budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
        budgetRetries = 0;
        mejorDistEnPresupuesto = Double.MAX_VALUE;
        presupuestosSinProgresoTotal = 0;
        intentosObjetivoInvalido = 0;
        mejorDistObjetivoInvalido = Double.MAX_VALUE;
        state = NavState.PLANNING;
        return true;
    }

    /** A2 Stage 7B · diagnostico acotado del escalonado (una linea, sin spam por tick). */
    public String objetivoLocalDiag() {
        return "mision=" + (misionFinal == null ? "-" : misionFinal.toShortString())
                + " objetivo=" + (objective == null ? "-" : objective.toShortString())
                + " buildable=" + objetivoPermiteConstruccion
                + " escalones=" + escalonesDeObjetivo + "/" + MAX_ESCALONES_DE_OBJETIVO
                + " fallosEscalon=" + fallosDeEscalonDeObjetivo + "/" + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO
                + " expGlobal=" + globalExpansionesTotal
                + " ultimoEscalon={" + ultimoEscalonDeObjetivo + "}"
                + " ultimaGlobal={" + ultimoGlobalDiag + "}";
    }

    /** A2 Stage 7C · expansiones acumuladas de la capa GLOBAL (la regresion comprueba que esta acotada). */
    public int globalExpansionesTotal() {
        return globalExpansionesTotal;
    }

    /** A2 Stage 7C · ultimo resumen de la ruta global (o "-"). */
    public String ultimoGlobalDiag() {
        return ultimoGlobalDiag;
    }

    /**
     * A2 Stage 7C · ultima RUTA GLOBAL calculada (o {@code null}). La usa el arnes de aceptacion para comprobar el
     * contrato de movimiento sobre la ruta REAL: que el escape de 8 bloques no viaja como WALK/JUMP y que cada
     * arista respeta el desnivel ejecutable.
     */
    public NavRoute ultimaRutaGlobal() {
        return globalCache == null ? null : globalCache.route();
    }

    /** A2 Stage 7C · ¿la ultima ruta global alcanzo la mision? */
    public boolean ultimaGlobalAlcanzoMision() {
        return globalCache != null && globalCache.alcanzoMision();
    }

    /** A2 Stage 7C · re-estadios globales consumidos (la regresion distingue replanificacion de salto de etapa). */
    public int reestadiosGlobales() {
        return reestadiosGlobales;
    }

    /**
     * A2 Stage 7D · MOTIVO DEL ULTIMO TICK QUE NO PLANIFICO. Instrumento acotado (un contador y una cadena, sin
     * registro por tick): permite distinguir "esperando" de "atascado en el invariante" sin adivinar.
     */
    private void noPlanifica(final String motivo) {
        ultimoMotivoDeReentrada = motivo;
        reentradasSinPlan++;
    }

    /** A2 Stage 7D · diagnostico de reentrada: cuantas veces no se planifico y por que la ultima vez. */
    public String motivoDeReentrada() {
        return "noPlan=" + reentradasSinPlan + " motivo={" + ultimoMotivoDeReentrada + "}";
    }

    /** A2 Stage 7D · descripcion corta de por que el objetivo no es utilizable. */
    private String describirNoUtilizable() {
        if (objective == null) {
            return "objetivo=null";
        }
        StringBuilder sb = new StringBuilder("objetivo=").append(objective.toShortString());
        if (!dentroDeLaVentana(mob.blockPosition(), objective)) {
            sb.append(" FUERA_DE_VENTANA(dx=").append(Math.abs(objective.getX() - mob.blockPosition().getX()))
                    .append(" dz=").append(Math.abs(objective.getZ() - mob.blockPosition().getZ()))
                    .append(" dy=").append(Math.abs(objective.getY() - mob.blockPosition().getY())).append(')');
        }
        if (!esPieValido(objective)) {
            sb.append(" NO_ES_PIE(aire=").append(mob.level().getBlockState(objective).isAir())
                    .append(" aireArriba=").append(mob.level().getBlockState(objective.above()).isAir())
                    .append(" soporteAbajo=").append(NavObstruction.hasSolidFace(mob.level(), objective.below()))
                    .append(" protegido=").append(policies.isProtected(objective)).append(')');
        }
        return sb.toString();
    }

    /** A2 Stage 7D · reentradas totales sin planificar (diagnostico). */
    public int reentradasSinPlan() {
        return reentradasSinPlan;
    }

    /**
     * A2 FASE 4 · OBJETIVO TACTICO. Fija un destino inmediato (orilla, punto de escape) SIN tocar la MISION
     * ({@code misionFinal}) ni el plan de construccion. Llegar a el NO declara {@code ARRIVED}: el tick de
     * llegada vuelve a la mision. Un destino tactico que coincida con la posicion actual o con el objetivo
     * actual se maneja igual (no hay victoria falsa); si no cambia nada, el tick siguiente reencamina.
     *
     * @return {@code true} si el ejecutor acepta el destino tactico (siempre, salvo entrada nula)
     */
    private boolean meleeHoldDiagnostico;

    /** Stop directed movement while the attacker attacks in melee; the tactical mission is preserved. */
    public void holdForMelee() {
        meleeHoldDiagnostico = true;
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        ticksSinAvance = 0;
        if (mob != null) {
            mob.getNavigation().stop();
        }
    }

    /** Release melee hold and let the preserved tactical/strategic objective replan. */
    public void releaseMeleeHold() {
        meleeHoldDiagnostico = false;
        nextPlanAt = 0L;
    }

    public boolean meleeHoldDiag() {
        return meleeHoldDiagnostico;
    }

    private String ultimoBreakDecision = "-";
    private long ultimoBreakDecisionTick = Long.MIN_VALUE;
    private String ultimoDropDecision = "-";
    private long ultimoDropDecisionTick = Long.MIN_VALUE;
    private String ultimoRecoverDiag = "-";
    private long ultimoRecoverDiagTick = Long.MIN_VALUE;
    private String ultimoActuatorDiag = "-";
    private long ultimoActuatorDiagTick = Long.MIN_VALUE;

    private String ultimoPathTransaction = "-";
    private long ultimoPathTransactionTick = Long.MIN_VALUE;

    /** Diagnostic only: re-run path creation without installing it, for a one-node non-reaching path. */
    private void tracePathTransaction(BlockPos target, long now) {
        if (!NavTrace.on()) {
            return;
        }
        String key = target.toShortString();
        if (key.equals(ultimoPathTransaction) && now - ultimoPathTransactionTick < 200L) {
            return;
        }
        net.minecraft.world.level.pathfinder.Path current = mob.getNavigation().getPath();
        if (current == null || current.getNodeCount() > 1) {
            return;
        }
        BlockPos targetPos = mob.getNavigation().getTargetPos();
        boolean cachedByTarget = targetPos != null && targetPos.equals(target);
        if (cachedByTarget) {
            NavTrace.ev("PATH_TRANSACTION_CACHED_RETURN", "uuid=" + mob.getUUID() + " tick=" + now
                    + " requested=" + target.toShortString() + " targetPos=" + targetPos.toShortString()
                    + " currentNodes=" + current.getNodeCount()
                    + " currentCanReach=" + current.canReach()
                    + " note=createPath-would-return-active-path");
            return;
        }
        ultimoPathTransaction = key;
        ultimoPathTransactionTick = now;
        String nodes = "-";
        try {
            net.minecraft.world.level.pathfinder.Path retry = mob.getNavigation().createPath(target, 1);
            if (retry != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < retry.getNodeCount(); i++) {
                    var n = retry.getNode(i);
                    if (n != null) {
                        if (i > 0) sb.append('|');
                        sb.append(n.asBlockPos().toShortString()).append(':').append(n.type);
                    }
                }
                nodes = "retryNull=false retryCanReach=" + retry.canReach()
                        + " retryNodes=" + retry.getNodeCount() + " retryPath=" + sb;
            } else {
                nodes = "retryNull=true";
            }
        } catch (RuntimeException e) {
            nodes = "retryException=" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }
        NavTrace.ev("PATH_TRANSACTION", "uuid=" + mob.getUUID() + " tick=" + now
                + " requested=" + target.toShortString()
                + " start=" + mob.blockPosition().toShortString()
                + " bbox=" + mob.getBoundingBox()
                + " currentNodes=" + current.getNodeCount()
                + " currentCanReach=" + current.canReach()
                + " currentDone=" + mob.getNavigation().isDone()
                + " evaluator=" + mob.getNavigation().getNodeEvaluator().getClass().getSimpleName()
                + " " + nodes
                + " state=" + state + " objective=" + objective);
    }

    private boolean intentarBacktrackTrasFallo(long now, String reason) {
        if (recoveryController.onNoProgress() != NavRecoveryController.Decision.BACKTRACK) return false;
        BlockPos backtrack = recoveryController.chooseBacktrack(mob.blockPosition(), ARRIVE_DISTANCE_SQR, p ->
                esPieValido(p) && NavObstruction.fits(mob.level(), mob, p));
        if (backtrack == null) return false;

        recoveryController.markBacktrack(backtrack);
        mob.getNavigation().stop();
        objective = backtrack.immutable();
        recoveryBacktrackObjective = backtrack.immutable();
        objetivoTactico = true;
        objetivoPermiteConstruccion = false;
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        plannedFrom = null;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalMision = null;
        globalDesde = null;
        route = null;
        routeIndex = 0;
        nextPlanAt = now;
        state = NavState.PLANNING;
        if (NavTrace.on()) {
            NavTrace.ev("NAV_BACKTRACK_SELECTED", "uuid=" + mob.getUUID()
                    + " from=" + mob.blockPosition().toShortString()
                    + " target=" + backtrack.toShortString()
                    + " history=" + recoveryController.historySize()
                    + " consecutiveFailures=" + recoveryController.consecutiveNoProgress()
                    + " reason={" + reason + "}");
        }
        return true;
    }

    /** BREAK se ejecuta desde la celda de pie de la ruta, nunca caminando hacia el bloque sólido. */
    private void ejecutarBreakDesdePosicionDeAtaque(NavRoute.Node node, long now) {
        BlockPos target = node.actionTarget();
        BlockPos standRuta = node.standPos();
        if (mob.level().getBlockState(target).isAir()) {
            limpiarEstadoBreak();
            resolverBreakVerificado(target, mob.blockPosition());
            return;
        }
        // Proximity to an adjacent cell is not physical occupancy. The actuator must run
        // from the exact validated stand cell, otherwise a one-block distance can pass the
        // old <= 1.0 threshold and execute from the predecessor instead.
        if (!mob.blockPosition().equals(standRuta)) {
            mob.getNavigation().moveTo(standRuta.getX() + 0.5D, standRuta.getY(),
                    standRuta.getZ() + 0.5D, 1.0D);
            long approachTick = mob.level().getGameTime();
            traceActuatorCommand("BREAK_STAND_APPROACH", standRuta, approachTick);

            // Vanilla can return a one-node Path already marked done when the requested stand is
            // immediately adjacent (notably a horizontal step at the same feet Y). In that case
            // repeatedly issuing moveTo cannot move the mob: the path is considered complete even
            // though the mob is not physically in the validated stand cell. Use the same bounded,
            // support/AABB-guarded adjacent nudge already used for ordinary movement nodes.
            int standDistance = mob.blockPosition().distManhattan(standRuta);
            boolean mayNudge = NavAdjacentApproachPolicy.shouldNudge(
                    mob.getNavigation().isDone(), true, standDistance, 2,
                    NavObstruction.fits(mob.level(), mob, standRuta),
                    NavObstruction.hasSupport(mob.level(), standRuta));
            if (mayNudge) {
                mob.getMoveControl().setWantedPosition(standRuta.getX() + 0.5D, standRuta.getY(),
                        standRuta.getZ() + 0.5D, 1.0D);
                if (NavTrace.on()) {
                    NavTrace.ev("BREAK_STAND_NUDGE", "uuid=" + mob.getUUID()
                            + " tick=" + approachTick
                            + " from=" + mob.blockPosition().toShortString()
                            + " stand=" + standRuta.toShortString()
                            + " manhattan=" + standDistance
                            + " reason=navigation_done_without_physical_occupancy");
                }
            }
            ultimaAccionDeRuta = standRuta.toShortString() + (mayNudge
                    ? ":BREAK-ACERCARSE-STAND-DIRECTO" : ":BREAK-ACERCARSE-STAND");
            return;
        }
        BlockPos stand = mob.blockPosition();
        if (!NavBreakActionPolicy.executable(mob.level(), mob, stand, target, policies, ALCANCE_DE_ACCION_SQR)) {
            limpiarEstadoBreak();
            excluirAristaBreakYReplanificar(stand, target, "precondicion de ataque invalida", now);
            return;
        }
        if (ultimoBreakFallidoStand == null || !ultimoBreakFallidoStand.equals(stand)
                || !target.equals(ultimoBreakFallidoTarget)) {
            limpiarEstadoBreak();
        }
        if (now < nextBreakRetryAt) return;

        mob.getNavigation().stop();
        boolean invoked = invokeTerrainAction(
                NavRoute.Transition.BREAK, target, objective);
        boolean verified = mob.level().getBlockState(target).isAir();
        if (verified) {
            limpiarEstadoBreak();
            exclusionesBreak.resolve(stand, target);
            if (NavTrace.on()) {
                NavTrace.ev("BREAK_EXECUTED_FROM_STAND", "uuid=" + mob.getUUID()
                        + " stand=" + stand.toShortString() + " target=" + target.toShortString()
                        + " invoked=" + invoked + " verified=true");
            }
            resolverBreakVerificado(target, stand);
        } else if (!invoked && fallosBreakSinResolucion == 0) {
            // romperNodoDeRuta shares the tunnel actuator's existing 40-tick inter-block cooldown.
            // A false return can be transient, so wait once before blacklisting this stand->target edge.
            ultimoBreakFallidoStand = stand.immutable();
            ultimoBreakFallidoTarget = target.immutable();
            fallosBreakSinResolucion = 1;
            nextBreakRetryAt = now + RECOVERY_COOLDOWN_TICKS;
            if (NavTrace.on()) {
                NavTrace.ev("BREAK_ACTUATOR_RETRY_WAIT", "uuid=" + mob.getUUID()
                        + " stand=" + stand.toShortString() + " target=" + target.toShortString()
                        + " retryAt=" + nextBreakRetryAt + " reason=first_unverified_attempt");
            }
        } else {
            limpiarEstadoBreak();
            excluirAristaBreakYReplanificar(stand, target,
                    "segunda accion no verificada (invoked=" + invoked + ")", now);
        }
    }

    private void limpiarEstadoBreak() {
        ultimoBreakFallidoStand = null;
        ultimoBreakFallidoTarget = null;
        fallosBreakSinResolucion = 0;
        nextBreakRetryAt = 0L;
    }

    private void resolverBreakVerificado(BlockPos target, BlockPos stand) {
        ultimaAccionDeRuta = target.toShortString() + ":BREAK-VERIFICADO desde " + stand.toShortString();
        // Resume the strategic mission from the newly opened terrain; do not keep planning toward
        // the temporary attack stand as if it were still the mission. 
        if (misionFinal != null) objective = misionFinal.immutable();
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        lastRouteSignature = 0;
        objetivoPermiteConstruccion = false;
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalMision = null;
        globalDesde = null;
        state = NavState.PLANNING;
        nextPlanAt = mob.level().getGameTime();
    }

    private void excluirAristaBreakYReplanificar(BlockPos stand, BlockPos target, String reason, long now) {
        long fingerprint = NavPlanner.huellaBreak(mob.level(), stand, target);
        exclusionesBreak.exclude(stand, target, fingerprint);
        if (NavTrace.on()) {
            NavTrace.ev("BREAK_EDGE_EXCLUDED", "uuid=" + mob.getUUID()
                    + " stand=" + stand.toShortString() + " target=" + target.toShortString()
                    + " fingerprint=" + fingerprint + " exclusions=" + exclusionesBreak.size()
                    + " reason={" + reason + "}");
        }
        if (route != null) lastRouteSignature = route.signature();
        if (misionFinal != null) objective = misionFinal.immutable();
        objetivoTransicionLocal = null;
        objetivoAccionLocal = null;
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalMision = null;
        globalDesde = null;
        nodoDeRecuperacion = null;
        nextPlanAt = now;
        state = NavState.PLANNING;
    }

    private void traceRecoverDecision(String decision, String detail, long now) {
        if (!NavTrace.on()) {
            return;
        }
        if (!decision.equals(ultimoRecoverDiag) || now - ultimoRecoverDiagTick >= 100L) {
            ultimoRecoverDiag = decision;
            ultimoRecoverDiagTick = now;
            NavTrace.ev("RECOVER_DECISION", "uuid=" + mob.getUUID() + " tick=" + now
                    + " decision=" + decision + " " + detail);
        }
    }

    private void traceActuatorCommand(String kind, BlockPos target, long now) {
        if (!NavTrace.on()) {
            return;
        }
        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().getPath();
        String sig = kind + "|" + target + "|" + (path == null) + "|"
                + (path != null && path.canReach()) + "|" + (path == null ? -1 : path.getNodeCount())
                + "|" + (path == null ? -1 : path.getNextNodeIndex()) + "|" + state;
        if (sig.equals(ultimoActuatorDiag) && now - ultimoActuatorDiagTick < 100L) {
            return;
        }
        ultimoActuatorDiag = sig;
        ultimoActuatorDiagTick = now;
        NavTrace.ev("ACTUATOR_COMMAND", "uuid=" + mob.getUUID() + " tick=" + now
                + " kind=" + kind + " target=" + target.toShortString()
                + " pos=" + mob.blockPosition().toShortString()
                + " pathNull=" + (path == null)
                + " pathCanReach=" + (path != null && path.canReach())
                + " pathDone=" + mob.getNavigation().isDone()
                + " pathNodes=" + (path == null ? -1 : path.getNodeCount())
                + " pathIndex=" + (path == null ? -1 : path.getNextNodeIndex())
                + " distSqr=" + String.format(java.util.Locale.ROOT, "%.4f",
                mob.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D))
                + " state=" + state);
    }

    private void traceBreakDecision(String decision, String detail, long now) {
        if (!NavTrace.on()) {
            return;
        }
        if (!decision.equals(ultimoBreakDecision) || now - ultimoBreakDecisionTick >= 100L) {
            ultimoBreakDecision = decision;
            ultimoBreakDecisionTick = now;
            NavTrace.ev("BREAK_DECISION", "uuid=" + mob.getUUID() + " tick=" + now
                    + " decision=" + decision + " " + detail);
        }
    }

    private void traceDropDecision(String decision, String detail, long now) {
        if (!NavTrace.on()) {
            return;
        }
        if (!decision.equals(ultimoDropDecision) || now - ultimoDropDecisionTick >= 100L) {
            ultimoDropDecision = decision;
            ultimoDropDecisionTick = now;
            NavTrace.ev("DROP_DECISION", "uuid=" + mob.getUUID() + " tick=" + now
                    + " decision=" + decision + " " + detail);
        }
    }

    public boolean setTacticalObjective(BlockPos target) {
        if (target == null) {
            return false;
        }
        // A2 FASE 4 · `shore == posicion actual` no es un destino: no se toca el objetivo ni la mision.
        // Se devuelve true para que el goal NO caiga a la navegacion de vanilla (autoridad unica), pero el
        // ejecutor sigue con la mision. Asi no hay ARRIVED falso ni bucle tactico<->mision.
        if (mob.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D) <= ARRIVE_DISTANCE_SQR) {
            return true;
        }
        BlockPos immutable = target.immutable();
        // Si el destino tactico ES la mision actual, no se marca como tactico: llegar a la mision es una
        // llegada legitima, no un escape. Evita el bucle tactico->mision->tactico.
        if (objective != null && immutable.equals(objective) && objetivoEsLaMision()) {
            return true;
        }
        this.objetivoTactico = true;
        // Un destino tactico DISTINTO se aplica siempre (aunque este a menos de 3 bloques del objetivo
        // anterior): si no, el ejecutor seguiria apuntando a la mision con la bandera tactica puesta y el
        // tick de llegada la retomaria en bucle.
        if (objective == null || !objective.equals(immutable)) {
            objective = immutable;
            objetivoPermiteConstruccion = false;
            plannedFrom = null;
            route = null;
            routeIndex = 0;
            replans = 0;
            recoveryActions = 0;
            lastRouteSignature = 0;
            nextPlanAt = 0L;
            nextBudgetRetryAt = 0L;
            budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
            budgetRetries = 0;
            // A2 REMEDIACIÓN 2 · el nuevo destino tactico NO reinicia el contador DURO de presupuesto ni
            // finge progreso en el primer hitBudget: la referencia es la distancia ACTUAL al nuevo destino.
            mejorDistEnPresupuesto = mob.distanceToSqr(immutable.getX() + 0.5D, immutable.getY() + 0.5D,
                    immutable.getZ() + 0.5D);
            intentosObjetivoInvalido = 0;
            mejorDistObjetivoInvalido = Double.MAX_VALUE;
            state = NavState.PLANNING;
        }
        return true;
    }

    /** A2 FASE 4 · ¿el objetivo activo es tactico (no la mision)? */
    public boolean objectiveEsTactico() {
        return objetivoTactico;
    }

    /**
     * A2 REMEDIACIÓN 2 · abandona el objetivo tactico y RETOMA la mision guardada. Se usa al salir del agua
     * y al agotar el presupuesto de escapes: nunca declara ARRIVED ni destruye `misionFinal`.
     */
    public void clearTacticalObjective() {
        if (!objetivoTactico) {
            return;
        }
        objetivoTactico = false;
        recoveryBacktrackObjective = null;
        objective = misionFinal;
        objetivoPermiteConstruccion = false;
        plannedFrom = null;
        route = null;
        routeIndex = 0;
        lastRouteSignature = 0;
        nextPlanAt = 0L;
        nextBudgetRetryAt = 0L;
        // A2 REMEDIACIÓN 2 · limpiar los transitorios del destino tactico: si la orilla dejo replans=4 o
        // recoveryActions=6, la mision no debe fallar en el primer tick por contadores ajenos.
        replans = 0;
        recoveryActions = 0;
        recoveryCooldown = RECOVERY_COOLDOWN_TICKS;
        nextRecoveryAt = 0L;
        budgetRetries = 0;
        budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
        mejorDistEnPresupuesto = objective == null ? Double.MAX_VALUE
                : mob.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                objective.getZ() + 0.5D);
        intentosObjetivoInvalido = 0;
        mejorDistObjetivoInvalido = Double.MAX_VALUE;
        ticksSinAvance = 0;
        oscillating = false;
        recentCells.clear();
        nodoSeguido = null;
        mejorDistAlNodo = Double.MAX_VALUE;
        falloDefinitivoTerminal = false;
        state = objective == null ? NavState.IDLE : NavState.PLANNING;
    }

    /**
     * A2 REMEDIACIÓN 2 · CEDER LA PROPIEDAD DEL MOVIMIENTO AL COMBATE. No toca objetivo ni misión: solo
     * invalida la ruta/plan en curso para que, al terminar el combate, el ejecutor replanifique desde la
     * posición real y no avance nodos por un movimiento que ordenó otro goal.
     */
    /** Tactical retreat owns movement for this tick; do not clear the mission route. */
    public void suspendMovementForRetreat() {
        if (mob != null) {
            mob.getNavigation().stop();
        }
    }

    private boolean suspendidoDiagnostico;
    private long ultimoSuspendidoTick = -1L;

    public boolean suspendidoDiag() {
        return suspendidoDiagnostico;
    }

    public long ultimoSuspendidoTickDiag() {
        return ultimoSuspendidoTick;
    }

    public void suspendForCombat() {
        suspendidoDiagnostico = true;
        ultimoSuspendidoTick = mob.level().getGameTime();
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        lastRouteSignature = 0;
        nextPlanAt = 0L;
        ticksSinAvance = 0;
        oscillating = false;
        recentCells.clear();
        nodoSeguido = null;
        mejorDistAlNodo = Double.MAX_VALUE;
        // Solo se convierte un estado TRANSITORIO. ARRIVED/FAILED/IDLE conservan su semántica terminal:
        // un combate no debe reabrir una misión ya cumplida ni borrar el fallo terminal.
        if (state == NavState.TRAVERSING || state == NavState.RECOVERING
                || state == NavState.OBSTRUCTED || state == NavState.PLANNING) {
            state = NavState.PLANNING;
        }
    }

    /** A2 FASE 2 · rearmes terminales consumidos y motivo del ultimo fallo definitivo. */
    public String recuperacionTerminalDiag() {
        return "rearmes=" + rearmesTerminales + "/" + MAX_TERMINAL_REARMS
                + " proximoRearme=" + nextRearmAt
                + " motivoFallo={" + motivoFalloTerminal + "}"
                + " presupuestoReintentos=" + budgetRetries + "/" + MAX_BUDGET_RETRIES
                + " sinProgresoTotal=" + presupuestosSinProgresoTotal + "/" + MAX_BUDGET_RETRIES;
    }

    public int rearmesTerminales() {
        return rearmesTerminales;
    }

    public String motivoFalloTerminal() {
        return motivoFalloTerminal;
    }

    /**
     * Village occupation must be authoritative: discard any retained assault stage/chain before
     * assigning the conquered-village interior.
     */
    public void enterOccupationObjective(BlockPos target) {
        if (target == null) {
            return;
        }
        etapas.clear();
        enCadenaDeEtapas = false;
        cadenaActivaFirma = 0;
        objective = null;
        objetivoPermiteConstruccion = false;
        plannedFrom = null;
        route = null;
        routeIndex = 0;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalDesde = null;
        objetivoTactico = false;
        // Conquest is a genuine mission change: allow one bounded fresh occupation attempt.
        rearmesTerminales = 0;
        nextRearmAt = 0L;
        falloDefinitivoTerminal = false;
        escalonesDeObjetivo = 0;
        fallosDeEscalonDeObjetivo = 0;
        replans = 0;
        state = NavState.PLANNING;
        setObjective(target);
    }

    public void setObjective(BlockPos target) {        // A2 Stage 6A · la MISION del plan de construccion es SIEMPRE el objetivo fijado. Medido en
        // 2n-20261016-122549: planMision seguia null porque el arnes llama a setObjective directamente, asi
        // que el cableado desde la entidad nunca se ejecutaba y el plan de construccion jamas arrancaba.
        if (target == null) {
            return;
        }
        BlockPos immutable = target.immutable();
        // Solo una mision nueva cancela la prioridad tactica; reenviar la misma mision no debe cancelar la orilla temporal.
        if (!NavTacticalObjectivePolicy.preserveTacticalObjective(this.objetivoTactico, immutable.equals(this.misionFinal))) {
            this.objetivoTactico = false;
        }
        if (!enCadenaDeEtapas) {
            this.misionRaiz = immutable;
        }
        // A2 Stage 6D · FIX 4 · una mision NUEVA abre un ciclo de construccion nuevo; el reenvio idempotente
        // del mismo objetivo NO reinicia nada (Stage B · B: el envio repetido no debe tocar el progreso).
        if (!immutable.equals(this.planMision)) {
            reiniciarCicloDeConstruccion();
        }
        this.planMision = immutable;
        // A2 Stage 7B · la MISION FINAL es la que fija el llamante y NO cambia con el escalonado. Una mision nueva
        // reinicia los contadores del escalonado (el reenvio del MISMO objetivo no toca el progreso).
        if (!immutable.equals(this.misionFinal)) {
            NavPlanner.cancelGlobalSearch(globalSearchHandle);
            globalSearchStart = null;
            globalSearchLastExpansions = 0;
            limpiarRecuperacionOrigenDrop();
            recoveryBacktrackObjective = null;
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[JUMPUP] MISSIONCHANGE old={} new={} objective={}",
                    this.misionFinal, immutable, this.objective);
            if (this.objective != null) {
                // La mision nueva manda: aunque el movimiento sea corto, el planificador no puede
                // seguir apuntando al objetivo anterior.
                this.objective = immutable;
                this.objetivoPermiteConstruccion = false;
                this.objetivoTransicionLocal = null;
                this.objetivoAccionLocal = null;
                this.plannedFrom = null;
                this.route = null;
                this.routeIndex = 0;
                this.globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                this.globalDesde = null;
                this.state = NavState.PLANNING;
                this.nextPlanAt = 0L;
            }
            this.misionFinal = immutable;
            recoveryController.reset();
            exclusionesBreak.clear();
            limpiarEstadoBreak();
            // A2 FASE 2 · un CAMBIO DE MISION justifica rearmar: se reinicia el presupuesto terminal. Pero una
            // etapa intermedia de la MISMA aldea NO es una mision nueva: `noReiniciarRearmes` conserva el tope.
            if (!noReiniciarRearmes) {
                rearmesTerminales = 0;
                nextRearmAt = 0L;
                motivoFalloTerminal = "-";
                falloDefinitivoTerminal = false;
                presupuestosSinProgresoTotal = 0;
            }
            // A2 REMEDIACIÓN 2 · una etapa de la MISMA aldea no reinicia el presupuesto duro. La referencia
            // de progreso es la distancia ACTUAL al nuevo objetivo (el primer hitBudget no finge progreso).
            mejorDistEnPresupuesto = mob.distanceToSqr(immutable.getX() + 0.5D, immutable.getY() + 0.5D,
                    immutable.getZ() + 0.5D);
            intentosObjetivoInvalido = 0;
            mejorDistObjetivoInvalido = Double.MAX_VALUE;
            escalonesDeObjetivo = 0;
        rearmadoPorCambioDeTerreno = false;
        cambioTerrenoPendienteRearme = false;
            rearmesLocalesDeEscalera = 0;
            fallosDeEscalonDeObjetivo = 0;
            ultimoEscalonDeObjetivo = "-";
            misionEnPieCalculada = false;
            misionEnPie = null;
            // A2 Stage 7C · mision nueva: tambien se reinicia el presupuesto de re-estadios y la ruta global.
            reestadiosGlobales = 0;
            // A2 · una mision NUEVA abre un ciclo de tunel nuevo; el reenvio idempotente no lo reinicia.
            limpiarPlanDeTunel();
            tunnelPlansThisMission = 0;
            tunnelStepsThisMission = 0;
            tunnelNextPlanAt = 0L;
            tunnelLastDiscard = "-";
            duplicateRechazos = 0;
            reintentosForzados = 0;
            globalCache = null;
            NavPlanner.cancelGlobalSearch(globalSearchHandle);
            globalSearchStart = null;
            globalSearchLastExpansions = 0;
            globalDesde = null;
        }
        objectiveSubmissions++;
        lastSubmissionTick = mob.level().getGameTime();
        if (recoveryBacktrackObjective != null && !immutable.equals(misionFinal)) {
            recoveryBacktrackObjective = null;
        }
        if (recoveryBacktrackObjective != null && objetivoTactico && immutable.equals(misionFinal)) {
            return; // repeated goal refresh must not overwrite the one-shot backtrack target
        }
        if (objective == null) {
            objective = immutable;
            objetivoPermiteConstruccion = false;
            plannedFrom = null;
            replans = 0;
            recoveryActions = 0;
            lastRouteSignature = 0;
            if (!noReiniciarRearmes) {
                rearmesTerminales = 0;
                nextRearmAt = 0L;
                motivoFalloTerminal = "-";
                falloDefinitivoTerminal = false;
            }
            mejorDistEnPresupuesto = mob.distanceToSqr(immutable.getX() + 0.5D, immutable.getY() + 0.5D,
                    immutable.getZ() + 0.5D);
            presupuestosSinProgresoTotal = 0;
            intentosObjetivoInvalido = 0;
            mejorDistObjetivoInvalido = Double.MAX_VALUE;
            state = NavState.PLANNING;
            nextPlanAt = 0L;
            return;
        }
        if (objective.distToCenterSqr(immutable.getX() + 0.5D, immutable.getY() + 0.5D,
                immutable.getZ() + 0.5D) > OBJECTIVE_MOVE_THRESHOLD_SQR) {
            objective = immutable;
            objetivoPermiteConstruccion = false;
            plannedFrom = null;
            state = NavState.PLANNING;
            nextPlanAt = 0L;
            nextBudgetRetryAt = 0L;
            budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
            budgetRetries = 0;
        }
    }

    /** Abandona el objetivo y para el movimiento. */
    public void clearObjective() {
        objective = null;
        objetivoPermiteConstruccion = false;
        objetivoTactico = false;
        recoveryBacktrackObjective = null;
        rearmesTerminales = 0;
        nextRearmAt = 0L;
        motivoFalloTerminal = "-";
        falloDefinitivoTerminal = false;
        mejorDistEnPresupuesto = Double.MAX_VALUE;
        presupuestosSinProgresoTotal = 0;
        intentosObjetivoInvalido = 0;
        mejorDistObjetivoInvalido = Double.MAX_VALUE;
        misionRaiz = null;
        cadenaActivaFirma = 0; // una cadena nueva tras limpiar debe volver a enviarse
        // A2 Stage 7B · sin objetivo no hay mision final ni escalonado que conservar.
        misionFinal = null;
        recoveryController.reset();
        exclusionesBreak.clear();
        limpiarEstadoBreak();
        escalonesDeObjetivo = 0;
        rearmadoPorCambioDeTerreno = false;
        cambioTerrenoPendienteRearme = false;
        fallosDeEscalonDeObjetivo = 0;
        ultimoEscalonDeObjetivo = "-";
        route = null;
        routeIndex = 0;
        // A2 · sin objetivo no hay plan de tunel que ejecutar.
        limpiarPlanDeTunel();
        // A2 Stage 6D · FIX 4 · sin objetivo no hay plan de construccion: se cierra su ciclo.
        reiniciarCicloDeConstruccion();
        state = NavState.IDLE;
    }

    public NavState state() {
        return state;
    }

    /** A2 diagnóstico: objetivo actual, para poder fotografiar su vecindad. */
    public BlockPos objective() {
        return objective;
    }

    public NavRoute route() {
        return route;
    }

    /**
     * A2 Stage 6D · Volcado de la RUTA COMPLETA (todos los nodos en orden, con su transicion). Necesario para
     * identificar la PRIMERA transicion que falla: `describe()` solo muestra los ultimos cuatro nodos.
     */
    public String rutaDiag() {
        if (route == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < route.length(); i++) {
            NavRoute.Node n = route.currentNode(i);
            sb.append(i).append(':').append(n.transition()).append('@').append(n.pos().toShortString()).append(' ');
        }
        return sb.toString().trim();
    }

    public boolean isOscillating() {
        return oscillating;
    }

    public String describe() {
        return "state=" + state + " obj=" + (objective == null ? "-" : objective.toShortString())
                + " route=" + (route == null ? "-" : route.describe())
                + " idx=" + routeIndex + " replans=" + replans + " rec=" + recoveryActions
                + " osc=" + oscillating + " plans=" + plansRun + " exp=" + expansionsLast
                + " rej=" + routesRejected + " recRun=" + recoveriesRun + " budget=" + budgetHits;
    }

    /** Read-only recovery diagnostics for deterministic runtime fixtures and trace reports. */
    public String recoveryDiag() {
        BlockPos last = recoveryController.lastBacktrack();
        return "history=" + recoveryController.historySize()
                + " consecutiveFailures=" + recoveryController.consecutiveNoProgress()
                + " backtrackUsed=" + recoveryController.backtrackUsed()
                + " lastBacktrack=" + (last == null ? "-" : last.toShortString())
                + " tacticalObjective=" + (recoveryBacktrackObjective == null
                        ? "-" : recoveryBacktrackObjective.toShortString())
                + " strategicMission=" + (misionFinal == null ? "-" : misionFinal.toShortString());
    }

    public int recoveryHistorySize() { return recoveryController.historySize(); }
    public int recoveryConsecutiveNoProgress() { return recoveryController.consecutiveNoProgress(); }
    public boolean recoveryBacktrackUsed() { return recoveryController.backtrackUsed(); }
    public BlockPos recoveryBacktrackTarget() { return recoveryController.lastBacktrack(); }

    /** True only while the same stand->target edge fingerprint is still excluded in current terrain. */
    public boolean isBreakEdgeExcluded(BlockPos stand, BlockPos target) {
        return stand != null && target != null
                && exclusionesBreak.excluded(stand, target, NavPlanner.huellaBreak(mob.level(), stand, target));
    }

    public int breakExclusionCount() { return exclusionesBreak.size(); }

    public String metrics() {
        return "plans=" + plansRun + " expansionsUltima=" + expansionsLast + " expansionsTotal=" + expansionsTotal
                + " rutasRechazadas=" + routesRejected + " recuperaciones=" + recoveriesRun
                + " presupuestosAgotados=" + budgetHits;
    }

    // ------------------------------------------------------------------ ciclo por tick

    /** Un tick del ejecutor. Los goals NO deben llamar a moveTo por su cuenta. */
    /**
     * Reinicia el tope local SOLO con progreso real hacia la mision. Antes el contador solo se reiniciaba al
     * agotar una ruta, de modo que una ruta larga con avance verificable podia consumir los 24 escalones.
     */
    private void registrarProgresoHaciaMision() {
        if (misionFinal == null) {
            return;
        }
        double d = Math.hypot(mob.getX() - (misionFinal.getX() + 0.5D),
                mob.getZ() - (misionFinal.getZ() + 0.5D));
        if (mejorDistMision == Double.MAX_VALUE) {
            mejorDistMision = d;
            return;
        }
        if (d < mejorDistMision - PROGRESO_MINIMO_DE_OBJETIVO) {
            mejorDistMision = d;
            recoveryController.onMissionProgress();
            escalonesDeObjetivo = 0;
        rearmadoPorCambioDeTerreno = false;
        cambioTerrenoPendienteRearme = false;
            ultimoEscalonDeObjetivo = "progreso verificado en tick: dMision="
                    + String.format(java.util.Locale.ROOT, "%.1f", d) + " -> contador de escalones reiniciado";
        }
    }

    /** El salto recto de 2 solo se acepta si el corredor a la altura del apice y la cabeza esta libre. */
    

    

    private String estadoSalto = "-";
    private long saltosCompletados;
    private long saltosIniciados;
    private long saltosAbortados;
    private long saltosTimeout;
    private String ultimoMotivoSalto = "-";

    /** Diagnóstico: saltos que llegaron al camino de aterrizaje confirmado. */
    public long saltosCompletadosDiag() {
        return saltosCompletados;
    }

    public long saltosIniciadosDiag() {
        return saltosIniciados;
    }

    public long saltosAbortadosDiag() {
        return saltosAbortados;
    }

    public long saltosTimeoutDiag() {
        return saltosTimeout;
    }

    public String ultimoMotivoSaltoDiag() {
        return ultimoMotivoSalto;
    }

    

    
    public void tick() {
        if (meleeHoldDiagnostico) {
            mob.getNavigation().stop();
            return;
        }
        suspendidoDiagnostico = false;
        long now = mob.level().getGameTime();
        tickCalls++;
        lastTickAt = now;
        // A2 Stage 4Q · TRAVESIA VERIFICADA de lo construido: la entidad ocupa la celda SUPERIOR de un bloque que
        // ella misma coloco y verifico, en un tick POSTERIOR a la colocacion, y el bloque sigue existiendo. No se
        // infiere de proximidad, ni de una ruta, ni de un waypoint.
        for (BlockPos c : construidas) {
            if (mob.blockPosition().equals(c.above()) && !mob.level().getBlockState(c).isAir()) {
                if (ultimoConstruidoTick >= 0L && now > ultimoConstruidoTick
                        && !c.equals(travesadaConfirmada)) {
                    travesadaConfirmada = c;
                    bloquesTravesados++;
                    ultimaTravesia = c.toShortString();
                    ultimaTravesiaTick = now;
                }
            }
        }
        if (objective == null) {
            state = NavState.IDLE;
            return;
        }
        // A2 · El plan de túnel, si existe, es la ÚNICA autoridad de movimiento y acción de terreno de este tick.
        if (tunnelPlan != null) {
            tickTunnelPlan(now);
            return;
        }
        // A2 · El escalón de ascenso, si está en curso, también es dueño único del tick.
        if (ascensoBloque != null) {
            tickAscenso(now);
            return;
        }
        if (breachMoveTarget != null) {
            tickBreachMove(now);
            return;
        }
        registrarProgresoHaciaMision();
        recordCell(mob.blockPosition());
        BlockPos physicalCell = mob.blockPosition();
        if (esPieValido(physicalCell) && NavObstruction.fits(mob.level(), mob, physicalCell)) {
            recoveryController.recordReached(physicalCell);
        }

        double distSqr = mob.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                objective.getZ() + 0.5D);
        com.chipoodle.devilrpg.entity.AggressiveZombieEntity zombiePendiente =
                mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az ? az : null;
        BlockPos escalonPendiente = zombiePendiente == null ? null : zombiePendiente.objetivoSobreUltimoEscalon();
        boolean objetivoEsEscalonPendiente = escalonPendiente != null && objective.equals(escalonPendiente);
        boolean escalonPendienteOcupado = objetivoEsEscalonPendiente
                && zombiePendiente.escalonPendienteOcupado();
        boolean proximidadPuedeConsumirObjetivo = NavArrivalPolicy.proximityArrivalAllowed(
                objetivoEsEscalonPendiente, escalonPendienteOcupado);
        // A2 Phase19: a recovery backtrack is a one-shot physical return, not a proximity waypoint.
        // Keep its tactical objective alive until the mob occupies the exact historical block cell.
        boolean backtrackPendiente = recoveryBacktrackObjective != null
                && recoveryBacktrackObjective.equals(objective);
        boolean backtrackOcupadoFisicamente = backtrackPendiente
                && mob.blockPosition().equals(recoveryBacktrackObjective);
        boolean proximidadPuedeConsumirBacktrack = NavArrivalPolicy.backtrackArrivalAllowed(
                backtrackPendiente, backtrackOcupadoFisicamente);
        if (distSqr <= ARRIVE_DISTANCE_SQR && backtrackPendiente && !backtrackOcupadoFisicamente
                && NavTrace.on() && now % 20L == 0L) {
            NavTrace.ev("BACKTRACK_PROXIMITY_SUPPRESSED", "uuid=" + mob.getUUID()
                    + " target=" + recoveryBacktrackObjective.toShortString()
                    + " pos=" + mob.blockPosition().toShortString()
                    + " distSqr=" + String.format(java.util.Locale.ROOT, "%.3f", distSqr));
        }
        // A2 RUN20: no consumir un escalon pendiente por la tolerancia generica de proximidad.
        // Run19 demostro que la distancia <= 2.25 puede ser cierta al estar junto al bloque, sin pisarlo.
        if (distSqr <= ARRIVE_DISTANCE_SQR && objetivoEsEscalonPendiente && !escalonPendienteOcupado
                && NavTrace.on() && now % 100L == 0L) {
            NavTrace.ev("STAIR_WAYPOINT_PROXIMITY_SUPPRESSED", "uuid=" + mob.getUUID()
                    + " target=" + objective.toShortString()
                    + " step=" + escalonPendiente.toShortString()
                    + " pos=" + mob.blockPosition().toShortString()
                    + " distSqr=" + String.format(java.util.Locale.ROOT, "%.3f", distSqr));
        }
        if (distSqr <= ARRIVE_DISTANCE_SQR && proximidadPuedeConsumirObjetivo
                && proximidadPuedeConsumirBacktrack) {
            // A2 FASE 4 · LLEGADA TACTICA != LLEGADA DE MISION. La decision vive en una politica pura y
            // probada: solo MISION_LLEGADA (sin etapas) puede declarar ARRIVED.
            NavArrivalPolicy.Result llegada =
                    NavArrivalPolicy.decidir(objetivoTactico, objetivoEsLaMision(), !etapas.isEmpty());
            if (llegada == NavArrivalPolicy.Result.RETOMAR_MISION
                    || llegada == NavArrivalPolicy.Result.RECALCULAR) {
                boolean tactico = llegada == NavArrivalPolicy.Result.RETOMAR_MISION;
                ultimoEscalonDeObjetivo = (tactico ? "objetivo tactico alcanzado " : "escalon alcanzado ")
                        + objective.toShortString()
                        + " -> se recalcula hacia " + (misionFinal == null ? "-" : misionFinal.toShortString());
                objetivoTactico = false;
                recoveryBacktrackObjective = null;
                objective = NavArrivalPolicy.objetivoTrasLlegada(llegada, objective, misionFinal);
                objetivoPermiteConstruccion = false;
                plannedFrom = null;
                route = null;
                routeIndex = 0;
                replans = 0;
                lastRouteSignature = 0;
                nextPlanAt = 0L;
                nextBudgetRetryAt = 0L;
                budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
                budgetRetries = 0;
                mejorDistEnPresupuesto = Double.MAX_VALUE;
                state = NavState.PLANNING;
                if (NavTrace.on()) {
                    NavTrace.ev("ARRIVAL", "tipo=" + (tactico ? "tactico" : "escalon")
                            + " pos=" + mob.blockPosition().toShortString()
                            + " mision=" + (misionFinal == null ? "-" : misionFinal.toShortString())
                            + " desenlace=" + (tactico ? "RETOMA_MISION" : "RECALCULA_MISION"));
                }
                return;
            }
            // A2 · si quedan ETAPAS, se avanza a la siguiente en vez de terminar: asi se cubren desniveles grandes
            // con saltos pequenos y legales, sin tocar el rango vertical del planificador.
            if (llegada == NavArrivalPolicy.Result.AVANZAR_ETAPA && avanzarEtapa()) {
                return;
            }
            if (NavArrivalPolicy.declaraArrived(llegada)) {
                state = NavState.ARRIVED;
                route = null;
                routeIndex = 0;
                if (NavTrace.on()) {
                    NavTrace.ev("ARRIVAL", "tipo=mision pos=" + mob.blockPosition().toShortString()
                            + " mision=" + objective.toShortString() + " desenlace=ARRIVED");
                }
                return;
            }
            // Una etapa anunciada sin etapas pendientes (no deberia ocurrir): se replanifica, no se finge llegada.
            state = NavState.PLANNING;
            return;
        }

        if (state == NavState.PLANNING && actualizarEsperaDePlanificacion(now)) {
            return;
        }
        switch (state) {
            case ARRIVED -> {
                // Terminal: solo se sale si el objetivo se mueve (setObjective lo devuelve a PLANNING).
            }
            case FAILED -> {
                // A2 FASE 2 · RECUPERACION ACOTADA DE UN ESTADO TERMINAL.
                // Un FAILED no puede dejar al ente inmovil para siempre. El rearme es explicito y acotado:
                // conserva objetivo y mision, invalida solo el estado transitorio de plan y espera un
                // enfriamiento. Al agotar MAX_TERMINAL_REARMS el fallo es definitivo y con motivo.
                intentarRearmeTerminal(now);
            }
            case PLANNING -> tryPlan(now);
            case TRAVERSING -> traverse(now);
            case OBSTRUCTED -> {
                state = NavState.RECOVERING;
            }
            case RECOVERING -> recover(now);
            case IDLE -> state = NavState.PLANNING;
        }
    }

    /**
     * A2 FASE 2 · rearme acotado tras un {@link NavState#FAILED}. No cambia objetivo ni mision: solo invalida
     * el estado transitorio de planificacion y vuelve a {@link NavState#PLANNING}. Se niega si el destino es
     * permanente-mente invalido (ni la mision ni el objetivo son celdas de pie) o si se agoto el presupuesto.
     */
    private void intentarRearmeTerminal(long now) {
        if (NavTrace.on()) {
            NavTrace.ev("A2_REARM_ENTER", "uuid=" + mob.getUUID() + " tick=" + now
                    + " state=" + state + " rearmes=" + rearmesTerminales + "/" + MAX_TERMINAL_REARMS
                    + " definitive=" + falloDefinitivoTerminal + " next=" + nextRearmAt
                    + " motivo={" + motivoFalloTerminal + "} objetivo=" + objective
                    + " mision=" + misionFinal + " pos=" + mob.blockPosition().toShortString());
        }
        if (objective == null || misionFinal == null) {
            motivoFalloTerminal = "sin objetivo/mision que rearmar";
            return;
        }
        if (rearmesTerminales >= MAX_TERMINAL_REARMS) {
            if (NavTrace.on()) NavTrace.ev("A2_REARM_REJECTED", "uuid=" + mob.getUUID()
                    + " reason=MAX_REARMES");
            return;
        }
        if (falloDefinitivoTerminal) {
            if (NavTrace.on()) NavTrace.ev("A2_REARM_REJECTED", "uuid=" + mob.getUUID()
                    + " reason=FAILED_DEFINITIVE motivo={" + motivoFalloTerminal + "}");
            return;
        }
        if (now < nextRearmAt) {
            if (NavTrace.on()) NavTrace.ev("A2_REARM_REJECTED", "uuid=" + mob.getUUID()
                    + " reason=COOLDOWN now=" + now + " next=" + nextRearmAt);
            return;
        }
        if (motivoFalloTerminal.contains("presupuesto")
                && presupuestosSinProgresoTotal >= MAX_BUDGET_RETRIES) {
            if (NavTrace.on()) NavTrace.ev("A2_REARM_REJECTED", "uuid=" + mob.getUUID()
                    + " reason=BUDGET_NO_PROGRESS total=" + presupuestosSinProgresoTotal
                    + " motivo={" + motivoFalloTerminal + "}");
            motivoFalloTerminal = NavTerminalDiagnostics.appendOnce(motivoFalloTerminal,
                    " (no rearmable sin progreso)");
            falloDefinitivoTerminal = true;
            nextRearmAt = Long.MAX_VALUE;
            return;
        }
        boolean destinoPosible = misionDePie() != null || esPieValido(objective);
        NavRecoveryPolicy.Decision decision =
                NavRecoveryPolicy.trasFallo(rearmesTerminales, MAX_TERMINAL_REARMS, now, nextRearmAt, destinoPosible);
        if (decision.terminal()) {
            if (NavTrace.on()) NavTrace.ev("A2_REARM_REJECTED", "uuid=" + mob.getUUID()
                    + " reason=POLICY_TERMINAL destinoPosible=" + destinoPosible
                    + " motivo={" + decision.motivo() + "}");
            motivoFalloTerminal = decision.motivo() + " | ultimoFallo=" + motivoFalloTerminal;
            falloDefinitivoTerminal = true;
            nextRearmAt = Long.MAX_VALUE;
            return;
        }
        if (!decision.rearmar()) {
            if (NavTrace.on()) NavTrace.ev("A2_REARM_REJECTED", "uuid=" + mob.getUUID()
                    + " reason=POLICY_WAIT destinoPosible=" + destinoPosible
                    + " motivo={" + decision.motivo() + "}");
            return;
        }
        rearmesTerminales++;
        falloDefinitivoTerminal = false;
        misionEnPieCalculada = false;
        misionEnPie = null;
        route = null;
        routeIndex = 0;
        replans = 0;
        recoveryActions = 0;
        lastRouteSignature = 0;
        nextPlanAt = 0L;
        nextBudgetRetryAt = 0L;
        budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
        budgetRetries = 0;
        mejorDistEnPresupuesto = Double.MAX_VALUE;
        escalonesDeObjetivo = 0;
        fallosDeEscalonDeObjetivo = 0;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalDesde = null;
        ticksSinAvance = 0;
        oscillating = false;
        recentCells.clear();
        nextRearmAt = now + REARM_COOLDOWN_TICKS;
        state = NavState.PLANNING;
        if (NavTrace.on()) {
            NavTrace.ev("REARM", "uuid=" + mob.getUUID() + " intento=" + rearmesTerminales + "/"
                    + MAX_TERMINAL_REARMS + " pos=" + mob.blockPosition().toShortString()
                    + " mision=" + misionFinal.toShortString() + " motivoAnterior={" + motivoFalloTerminal + "}");
        }
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} REARME terminal {}/{} en {} (motivo: {})",
                mob.getId(), rearmesTerminales, MAX_TERMINAL_REARMS, mob.blockPosition().toShortString(),
                motivoFalloTerminal);
    }

    /** A2 FASE 2/3 · unica puerta a {@link NavState#FAILED}: deja SIEMPRE un motivo explicito. */
    private void fallar(final String motivo) {
        if (NavTrace.on()) {
            NavTrace.ev("A2_FALLAR_ENTER", "uuid=" + mob.getUUID() + " tick="
                    + mob.level().getGameTime() + " local=" + escalonesDeObjetivo
                    + "/" + MAX_ESCALONES_DE_OBJETIVO + " fallos=" + fallosDeEscalonDeObjetivo
                    + "/" + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO + " motivo={" + motivo + "}");
        }
        motivoFalloTerminal = motivo;
        falloDefinitivoTerminal = false; // este fallo es nuevo: la recuperacion vuelve a poder evaluarse
        boolean limiteLocalAgotadoEnFallo = escalonesDeObjetivo >= MAX_ESCALONES_DE_OBJETIVO
                && fallosDeEscalonDeObjetivo >= MAX_FALLOS_DE_ESCALON_DE_OBJETIVO;
        if (NavTerrainRearmPolicy.shouldRearm(cambioTerrenoPendienteRearme,
                limiteLocalAgotadoEnFallo, rearmadoPorCambioDeTerreno)) {
            escalonesDeObjetivo = 0;
            fallosDeEscalonDeObjetivo = 0;
            ultimoEscalonDeObjetivo = "rearme unico por BREAK previo y limite local agotado";
            rearmadoPorCambioDeTerreno = true;
            cambioTerrenoPendienteRearme = false;
            motivoFalloTerminal = "rearme unico por BREAK previo; replanificar";
            falloDefinitivoTerminal = false;
            state = NavState.PLANNING;
            nextPlanAt = mob.level().getGameTime();
            if (NavTrace.on()) {
                NavTrace.ev("NAV_TERRAIN_REARM", "uuid=" + mob.getUUID()
                        + " tick=" + mob.level().getGameTime()
                        + " mission=" + misionFinal.toShortString()
                        + " reason=" + motivo);
            }
            return;
        }        state = NavState.FAILED;
        if (NavTrace.on()) {
            NavTrace.ev("NAV_FAIL", "uuid=" + mob.getUUID() + " pos=" + mob.blockPosition().toShortString()
                    + " motivo={" + motivo + "} rearmes=" + rearmesTerminales + "/" + MAX_TERMINAL_REARMS);
        }
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} NAVEGACION FAILED en {}: {}",
                mob.getId(), mob.blockPosition().toShortString(), motivo);
    }

    private void tryPlan(long now) {
        if (NavTrace.on()) {
            NavTrace.ev("A2_TRYPLAN_ENTER", "uuid=" + mob.getUUID() + " tick=" + now
                    + " state=" + state + " pos=" + mob.blockPosition().toShortString()
                    + " objetivo=" + objective + " mision=" + misionFinal
                    + " local=" + escalonesDeObjetivo + "/" + MAX_ESCALONES_DE_OBJETIVO
                    + " fallos=" + fallosDeEscalonDeObjetivo + "/" + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO
                    + " replans=" + replans + "/" + MAX_REPLANS_PER_DESTINATION
                    + " budget=" + presupuestosSinProgresoTotal + "/" + MAX_BUDGET_RETRIES);
        }
        // A2 · DESNIVEL REAL CON MURO DELANTE: el ascenso manda sobre planificar. Si la misión está >= 3 bloques
        // por encima y hay un muro rompible adyacente, la ruta óptima es escalar ahí mismo; el A* prefiere rodear
        // la meseta (medido: IDX7/8 deambulando 500+ bloques alrededor del acantilado). Es acotado por
        // MAX_ASCENSOS_MISION y por el presupuesto del actuador.
        BlockPos misionAscenso = misionDeReferencia();
        if (misionAscenso != null && misionAscenso.getY() - mob.blockPosition().getY() >= 3
                && intentarAscenso(now, "desnivel_mayor")) {
            return;
        }
        if (Boolean.getBoolean("devilrpg.a2n.diagJump")) {
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[JUMPDBG] PLAN objective={} misionFinal={}",
                    objective, misionFinal);
        }
        if (now < nextPlanAt) {
            noPlanifica("esperaDePlan now=" + now + " nextPlanAt=" + nextPlanAt);
            return;
        }
        // A2 Stage 6E · FIX A · UN ESCALON COLOCADO Y VERIFICADO NO SE ABANDONA.
        // Medido en `a2n-20261016-173602`: tras colocar el primer escalon (`119,85,-8`), el planificador encontro una
        // ruta alternativa por la roca (BREAK + DROP) y el ejecutor paso a TRAVERSING; el escalon se quedo
        // `PLACED_VERIFIED` PARA SIEMPRE (`travesados=0`) y el ente bajo por otro camino. El contrato de Stage 6A dice
        // que hace falta `TRAVERSED` para autorizar el escalon siguiente: mientras ese paso este pendiente y la mision
        // siga exigiendo descenso, NO se cambia de plan. Es acotado: el presupuesto propio de construccion sigue
        // vigente, asi que o se travesia el escalon o el plan termina con un motivo explicito.
        if (faseEscalon == FaseEscalon.PLACED_VERIFIED && escalonActual != null && !construccionAgotada
                && !descensoBloqueado && planMision != null
                && mob.blockPosition().getY() > planMision.getY()) {
            state = NavState.RECOVERING;
            return;
        }
        // A2 · AGOTAR PRESUPUESTO NO ES "NO HAY RUTA". Medido en `a2n-20261015-170418`: la busqueda gastaba
        // 901/900 expansiones en CADA intento, se contaba como ruta rechazada, y a los 4 intentos el ejecutor
        // entraba en FAILED y el zombi se quedaba INERTE 4300 ticks (objetivo estatico => nunca se salia). Aqui el
        // agotamiento de presupuesto es un resultado INCONCLUYENTE: conserva el objetivo, reintenta con espera
        // creciente y NO consume el umbral terminal de rutas rechazadas.
        if (now < nextBudgetRetryAt) {
            noPlanifica("esperaDePresupuesto now=" + now + " nextBudgetRetryAt=" + nextBudgetRetryAt);
            return;
        }
        if (replans >= MAX_REPLANS_PER_DESTINATION) {
            noPlanifica("replans agotados=" + replans);
            fallar("replans agotados (" + replans + "/" + MAX_REPLANS_PER_DESTINATION + ")");
            return;
        }
        // ==========================================================================================
        // A2 Stage 7B · INVARIANTE DE VENTANA: al planificador NO se le entrega nunca un objetivo que no
        // pueda encolar. Medido en `a2n-20261016-185324`: un objetivo a ~95 bloques agotaba 900 expansiones
        // por plan con `metaEncolada=false` y el ente no se movia NUNCA (`moves=0`). Medido ademas en
        // `a2n-20261016-193348`: con la mision a 8 bloques pero siendo una celda SOLIDA (el centro de la aldea
        // es `stone_bricks`) el plan tampoco puede encolarla. Por eso el objetivo debe estar en la ventana Y ser
        // una posicion de PIE utilizable; si no, se deriva un OBJETIVO LOCAL acotado; si no existe ninguno
        // valido, se agota un presupuesto propio de intentos y se TERMINA con motivo explicito.
        // ==========================================================================================
        boolean accionLocalValida = esAccionLocalValida(objective);
        boolean objetivoUtilizable = objective != null && dentroDeLaVentana(mob.blockPosition(), objective)
                && (esPieValido(objective) || objetivoPermiteConstruccion || accionLocalValida);
        if (!objetivoUtilizable) {
            if (mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity) {
                BlockPos stair = intentarConstruirEscalonSinWaypoint(
                        objective != null ? objective : misionFinal,
                        mob.blockPosition(), "objetivo no utilizable");
                if (stair != null) {
                    return;
                }
                if (ultimoResultadoEscalon == StairBuildResult.ACCESS_CLEARED) {
                    // Terrain changed, but no stair exists. Retry from the updated world next
                    // tick without consuming the accepted-step budget.
                    nextPlanAt = now;
                    return;
                }
                if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT) {
                    nextPlanAt = now + 2L;
                    return;
                }
            }
            boolean objetivoEscalonado = escalonarObjetivoLocal();
            if (globalSearchPending) {
                nextPlanAt = now + 1L;
                noPlanifica("búsqueda A* global incremental pendiente");
                return;
            }
            if (ultimoResultadoEscalon == StairBuildResult.CADENCE_WAIT) {
                nextPlanAt = now + 2L;
                return;
            }
            if (!objetivoEscalonado) {
                if (fallosDeEscalonDeObjetivo >= MAX_FALLOS_DE_ESCALON_DE_OBJETIVO) {
                    // A2 · FRENTE EN CALLEJÓN SIN OBJETIVO LOCAL UTILIZABLE: último recurso acotado antes
                    // del fallo terminal es un plan de túnel subordinado hacia la misión.
                    if (desnivelReal() && intentarAscenso(now, "sin_objetivo_local")) {
                        return;
                    }
                    if (misionDebajo() && intentarDescensoHaciaMision(now, "sin_objetivo_local")) {
                        return;
                    }
                    if (misionNoDebajo() && intentarAvanceHaciaMision(now, "sin_objetivo_local")) {
                        return;
                    }
                    if (intentarPlanDeTunel(now, "sin_objetivo_local", false)) {
                        return;
                    }
                    noPlanifica("escalonado sin objetivo valido (" + fallosDeEscalonDeObjetivo + " fallos)");
                    fallar("sin objetivo local valido (" + fallosDeEscalonDeObjetivo + "/"
                            + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO + "): " + ultimoEscalonDeObjetivo);
                    return;
                }
                // Aun quedan intentos: se planifica al objetivo tal cual (comportamiento anterior) sin gastar
                // el presupuesto de plan en un objetivo que ya se sabe inencolable mas de lo debido.
                noPlanifica("escalonado fallido " + fallosDeEscalonDeObjetivo + "/"
                        + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO);
                nextBudgetRetryAt = now + MIN_BUDGET_BACKOFF_TICKS;
                return;
            }
            if (objective == null || !dentroDeLaVentana(mob.blockPosition(), objective)
                    || (!esPieValido(objective)
                        && !(objetivoPermiteConstruccion
                            && NavBuildObjectivePolicy.isValid(objective, objetivoAccionConstruccion))
                        && !accionLocalValida)) {
                // A2 FASE 2 · ESTE era el punto ciego: `escalonarObjetivoLocal()` puede devolver true SIN
                // cambiar nada (misma celda = idempotente) y, si esa celda NO es utilizable, se salia por aqui
                // en CADA tick sin planificar nunca: `state=PLANNING`, `route=-`, `plans` congelado.
                // Ahora el numero de intentos SIN progreso es acotado y termina en FAILED con motivo.
                double distInv = objective == null ? Double.MAX_VALUE
                        : mob.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                        objective.getZ() + 0.5D);
                boolean progresoInv = distInv < mejorDistObjetivoInvalido - 1.0D;
                if (progresoInv) {
                    mejorDistObjetivoInvalido = distInv;
                    intentosObjetivoInvalido = 0;
                }
                intentosObjetivoInvalido++;
                NavRecoveryPolicy.Decision decisionInvalido = NavRecoveryPolicy.trasObjetivoInvalido(
                        intentosObjetivoInvalido, MAX_INTENTOS_OBJETIVO_INVALIDO, progresoInv);
                if (decisionInvalido.terminal()) {
                    fallar(decisionInvalido.motivo() + " | " + describirNoUtilizable());
                    return;
                }
                noPlanifica("invariante: objetivo sigue NO utilizable (" + describirNoUtilizable()
                        + ") ventana=" + dentroDeLaVentana(mob.blockPosition(), objective)
                        + " pie=" + esPieValido(objective)
                        + " intento=" + intentosObjetivoInvalido + "/" + MAX_INTENTOS_OBJETIVO_INVALIDO);
                nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
                return;
            }
        }
        NavPlanner.Result result = NavPlanner.plan(mob.level(), mob, mob.blockPosition(), objective,
                HORIZONTAL_RANGE, VERTICAL_RANGE, MAX_EXPANSIONS_PER_PLAN, policies, construidas,
                exclusionesDescenso, exclusionesBreak);
        plansRun++;
        expansionsLast = result.expansions();
        expansionsTotal += result.expansions();
        lastPlanSummary = result.summary();
        if (result.hitBudget()) {
            // A2 · UNA RUTA GLOBAL VALIDADA SE EJECUTA, NO SE RE-PLANIFICA. Medido en `a2n-20261121-221911`:
            // IDX7 tenía una ruta global de 52 nodos desde SU MISMA celda hasta la misión (con aristas BUILD que
            // suben los 6-8 bloques de desnivel) y, aun así, gastaba el presupuesto local de 900 en un plan
            // equivalente y entraba en el bucle de reintentos. Si la ruta global une la celda actual con la
            // misión, es la ruta a ejecutar.
            // A2 · PRESUPUESTO LOCAL AGOTADO CON LA MISIÓN POR ENCIMA. Orden de decisión: primero la acción de
            // terreno de corto alcance (acercarse a pie, luego el escalón de ascenso), y solo si ninguna aplica
            // se lanza la búsqueda global. Así cada escalón de subida encadena el siguiente sin esperar una
            // búsqueda de 8000 expansiones que, junto a un acantilado, no puede resolver nada.
            if (desnivelReal() && intentarAscenso(now, "presupuesto_local")) {
                return;
            }
            if (misionNoDebajo() && intentarAvanceHaciaMision(now, "presupuesto_local")) {
                return;
            }
            if (intentarRutaGlobalValidada(now)) {
                return;
            }
            // A2 · MURO DELANTE CON PRESUPUESTO AGOTADO: el túnel es el último recurso (rompe 2 bloques de alto
            // y avanza). Medido en `a2n-20261122-011942`: IDX8 en el muro (720,71,509, dist 63,7) con el plan
            // local agotado 6/10 y SIN que el túnel se intentara nunca (solo se probaba con ruta VACÍA).
            if (intentarPlanDeTunel(now, "presupuesto_local", true)) {
                return;
            }
            if (misionDebajo() && intentarDescensoHaciaMision(now, "presupuesto_local")) {
                return;
            }
            // A2 Stage 4K: la exhaucion de presupuesto SOLO se enruta a CONSTRUCCION si el objetivo activo exige
            // construccion por geometria real (caida > segura Y sin aterrizaje intermedio soportado). Las rutas
            // complejas normales conservan su reintento de siempre.
            if (mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az) {
                StairBuildResult stairOutcome = az.construirEscalonResultado(objective);
                if (stairOutcome == StairBuildResult.CADENCE_WAIT) {
                    ultimoResultadoEscalon = StairBuildResult.CADENCE_WAIT;
                    registrarEsperaCadenciaEscalon("presupuesto local agotado");
                    nextPlanAt = now + 2L;
                    return;
                }
                if (stairOutcome == StairBuildResult.STAIR_PLACED) {
                    reconstruccionEnrutada = "presupuesto agotado + objetivo exige construccion -> escalon";
                    return; // sigue en PLANNING; el objetivo se conserva
                }
            }
            // A2 FASE 3 · El presupuesto agotado es INCONCLUYENTE mientras haya PROGRESO MEDIBLE. Si el ente
            // se acerca de verdad al objetivo, el contador vuelve a cero. Sin progreso, la repeticion tiene
            // un tope configurable y termina en FAILED con motivo (nunca se declara ARRIVED por esto).
            double distObjetivo = mob.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                    objective.getZ() + 0.5D);
            boolean huboProgreso = distObjetivo < mejorDistEnPresupuesto - 1.0D;
            if (huboProgreso) {
                mejorDistEnPresupuesto = distObjetivo;
                budgetRetries = 0;
                presupuestosSinProgresoTotal = 0;
            }
            budgetHits++;
            budgetRetries++;
            presupuestosSinProgresoTotal++;
            NavRecoveryPolicy.Decision decisionPresupuesto =
                    NavRecoveryPolicy.trasPresupuesto(presupuestosSinProgresoTotal, MAX_BUDGET_RETRIES, huboProgreso);
            if (decisionPresupuesto.terminal()) {
                if (NavTrace.on()) {
                    NavTrace.ev("BUDGET", "uuid=" + mob.getUUID() + " intento=" + presupuestosSinProgresoTotal
                            + "/" + MAX_BUDGET_RETRIES + " pos=" + mob.blockPosition().toShortString()
                            + " objetivo=" + objective.toShortString()
                            + " progreso=" + huboProgreso + " desenlace=TERMINAL");
                }
                fallar(decisionPresupuesto.motivo() + " hacia " + objective.toShortString());
                return;
            }
            budgetBackoff = Math.min(MAX_BUDGET_BACKOFF_TICKS, budgetBackoff * 2);
            nextBudgetRetryAt = now + budgetBackoff;
            if (NavTrace.on()) {
                NavTrace.ev("BUDGET", "uuid=" + mob.getUUID() + " intento=" + presupuestosSinProgresoTotal
                        + "/" + MAX_BUDGET_RETRIES + " pos=" + mob.blockPosition().toShortString()
                        + " objetivo=" + objective.toShortString() + " exp=" + result.expansions()
                        + "/" + MAX_EXPANSIONS_PER_PLAN + " progreso=" + huboProgreso
                        + " espera=" + budgetBackoff + " desenlace=REINTENTO");
            }
            return; // se sigue en PLANNING; el objetivo se conserva
        }
        budgetBackoff = MIN_BUDGET_BACKOFF_TICKS;
        budgetRetries = 0;
        if (result.route().isEmpty()) {
            // A2 · CALLEJÓN SIN RUTA. Antes de gastar recuperación genérica se pide un plan de túnel
            // subordinado (2 de alto, cardinal, hacia la misión). Si no hay plan válido el flujo no cambia.
            if (desnivelReal() && intentarAscenso(now, "sin_ruta_local")) {
                return;
            }
            if (misionDebajo() && intentarDescensoHaciaMision(now, "sin_ruta_local")) {
                return;
            }
            if (misionNoDebajo() && intentarAvanceHaciaMision(now, "sin_ruta_local")) {
                return;
            }
            if (intentarPlanDeTunel(now, "sin_ruta_local", false)) {
                return;
            }
            // A genuinely exhausted search is also a no-progress failure. Count it toward the
            // same two-failure recovery sequence used by stale routes.
            if (intentarBacktrackTrasFallo(now, "planificador sin ruta")) return;
            // Sin ruta REAL (se exploro sin agotar presupuesto): recuperacion acotada.
            routesRejected++;
            replans++;
            nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
            state = NavState.RECOVERING;
            return;
        }
        NavRoute acceptedRoute = result.route();
        if (objetivoTransicionLocal == NavRoute.Transition.BREAK) {
            BlockPos actionTarget = objetivoAccionLocal;
            if (actionTarget == null || !NavBreakActionPolicy.executable(mob.level(), mob, objective,
                    actionTarget, policies, NavBreakActionPolicy.ACTION_REACH_SQR)) {
                if (NavTrace.on()) {
                    NavTrace.ev("BREAK_LOCAL_ROUTE_REJECTED", "uuid=" + mob.getUUID()
                            + " stand=" + objective.toShortString()
                            + " action=" + (actionTarget == null ? "-" : actionTarget.toShortString())
                            + " reason=precondition_changed_before_route_acceptance");
                }
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalMision = null;
                globalDesde = null;
                objetivoTransicionLocal = null;
                objetivoAccionLocal = null;
                nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
                state = NavState.PLANNING;
                return;
            }
            acceptedRoute = NavBreakRoutePolicy.attachFinalBreak(acceptedRoute, objective, actionTarget);
            if (acceptedRoute == null) {
                if (NavTrace.on()) {
                    NavTrace.ev("BREAK_LOCAL_ROUTE_REJECTED", "uuid=" + mob.getUUID()
                            + " stand=" + objective.toShortString()
                            + " action=" + actionTarget.toShortString()
                            + " reason=local_route_does_not_end_at_attack_stand");
                }
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalMision = null;
                globalDesde = null;
                objetivoTransicionLocal = null;
                objetivoAccionLocal = null;
                nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
                state = NavState.PLANNING;
                return;
            }
        }
        // Una ruta con la misma firma que una ya fallada no se reintenta. Compare the executable
        // route, including its final BREAK action target, rather than the WALK-only planning path.
        // A2 · DEADLOCK POR RUTA DUPLICADA. Si la entidad NO se ha movido y el planificador produce la MISMA
        // ruta, rechazarla indefinidamente es un bloqueo: medido en `a2n-20261122-001314`, IDX3 en `617,72,577`
        // re-planificaba la misma ruta (firma 2102820130) cada ~200 ticks y gastaba los 8 replanteos sin mover
        // un pie. Tras 3 rechazos idénticos se REINTENTA la ruta (máximo 2 reintentos forzados por misión).
        boolean duplicada = acceptedRoute.signature() == lastRouteSignature;
        if (duplicada && duplicateRechazos >= MAX_DUPLICATE_REJECTIONS
                && reintentosForzados < MAX_FORCED_ROUTE_RETRIES) {
            reintentosForzados++;
            duplicateRechazos = 0;
            if (NavTrace.on()) {
                NavTrace.ev("REPLAN_SIGNATURE_FORCED_RETRY", "uuid=" + mob.getUUID() + " tick=" + now
                        + " candidateSignature=" + acceptedRoute.signature()
                        + " forced=" + reintentosForzados + "/" + MAX_FORCED_ROUTE_RETRIES);
            }
            duplicada = false;
        }
        if (duplicada) {
            duplicateRechazos++;
            if (NavTrace.on()) {
                NavTrace.ev("REPLAN_SIGNATURE_DECISION", "uuid=" + mob.getUUID() + " tick=" + now
                        + " candidateSignature=" + acceptedRoute.signature()
                        + " previousSignature=" + lastRouteSignature
                        + " decision=REJECT_DUPLICATE replansBefore=" + replans
                        + " replansAfter=" + (replans + 1)
                        + " duplicadas=" + duplicateRechazos + "/" + MAX_DUPLICATE_REJECTIONS);
            }
            if (postSnapFirstPlanPending) {
                if (NavTrace.on()) NavTrace.ev("POST_SNAP_FIRST_PLAN", "uuid=" + mob.getUUID()
                        + " tick=" + now + " start=" + mob.blockPosition().toShortString()
                        + " goal=" + (objective == null ? "NONE" : objective.toShortString())
                        + " signature=" + acceptedRoute.signature()
                        + " decision=REJECT_DUPLICATE state=" + state);
                postSnapFirstPlanPending = false;
            }
            routesRejected++;
            replans++;
            nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
            state = NavState.RECOVERING;
            return;
        }
        route = acceptedRoute;
        routeIndex = 0;
        duplicateRechazos = 0;
        if (postSnapFirstPlanPending) {
            if (NavTrace.on()) NavTrace.ev("POST_SNAP_FIRST_PLAN", "uuid=" + mob.getUUID()
                    + " tick=" + now + " start=" + mob.blockPosition().toShortString()
                    + " goal=" + (objective == null ? "NONE" : objective.toShortString())
                    + " plannedFrom=" + (plannedFrom == null ? "NONE" : plannedFrom.toShortString())
                    + " signature=" + acceptedRoute.signature()
                    + " decision=ACCEPT state=" + state);
            postSnapFirstPlanPending = false;
            postSnapRouteStartPending = true;
        }
        if (Boolean.getBoolean("devilrpg.a2n.diagDrop") && !route.isEmpty()) {
            NavRoute.Node n0 = route.currentNode(0);
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info(
                    "[DROPACCEPT] ROUTE_ACCEPTANCE tick={} pos={} feet={} objective={} mission={} cache={} first={} trans={} len={} fits={} support={} feetState={} headState={} belowState={}",
                    mob.level().getGameTime(), mob.blockPosition().toShortString(),
                    mob.getBoundingBox().minY, objective, misionFinal, globalCache != null,
                    n0.pos().toShortString(), n0.transition(), route.length(),
                    NavObstruction.fits(mob.level(), mob, n0.pos()),
                    NavObstruction.hasSupport(mob.level(), n0.pos()),
                    mob.level().getBlockState(n0.pos()), mob.level().getBlockState(n0.pos().above()),
                    mob.level().getBlockState(n0.pos().below()));
        }
        // A2 FASE 3 · una ruta aceptada es progreso real: el contador de presupuesto vuelve a cero.
        mejorDistEnPresupuesto = Double.MAX_VALUE;
        budgetRetries = 0;
        presupuestosSinProgresoTotal = 0;
        intentosObjetivoInvalido = 0;
        mejorDistObjetivoInvalido = Double.MAX_VALUE;
        // A2 Stage 6F · ruta nueva: los intentos de descenso del nodo anterior no cuentan para este.
        intentosDeDescenso = 0;
        plannedFrom = mob.blockPosition();
        state = NavState.TRAVERSING;
    }

    /**
     * A2 Stage 6F · ¿está ALCANZADO este nodo de ruta? Para WALK/STEP_UP/JUMP_UP basta la proximidad; para un DROP
     * NO: la ruta solo avanza cuando el ente está <b>físicamente en el aterrizaje</b> (a su altura o por debajo) y con
     * suelo firme debajo. Así no se puede "dar por bajado" un escalón por estar cerca de él en el aire.
     */
    private boolean nodoAlcanzado(final NavRoute.Node node) {
        // BUILD is complete only after its exact placement is verified and the mob physically
        // occupies the resulting feet cell while grounded, supported and fitting its real AABB.
        if (node.transition() == NavRoute.Transition.BUILD) {
            BlockPos placementTarget = node.actionPos();
            BlockPos liveFeet = mob.blockPosition();
            boolean placementVerified = placementTarget != null && construidas.contains(placementTarget)
                    && !mob.level().getBlockState(placementTarget).isAir();
            boolean arrived = NavBuildTransitionPolicy.physicallyArrived(
                    placementVerified, liveFeet.equals(node.pos()), mob.onGround(),
                    NavObstruction.hasSupport(mob.level(), liveFeet), NavObstruction.fitsActualAabb(mob.level(), mob));
            return arrived && esPieValido(liveFeet);
        }
        if (node.transition() == NavRoute.Transition.JUMP_UP) {
            return false;
        }
        if (recuperacionOrigenDropActiva) {
            BlockPos live = mob.blockPosition();
            return live.equals(node.pos()) && esPieValido(live)
                    && NavObstruction.hasSupport(mob.level(), live)
                    && NavObstruction.fitsActualAabb(mob.level(), mob);
        }
        // BREAK is an action, not a proximity waypoint. If proximity advanced this node,
        // the actuator would be skipped before it could approach the validated stand and act.
        if (node.transition() == NavRoute.Transition.BREAK) {
            return false;
        }
        com.chipoodle.devilrpg.entity.AggressiveZombieEntity zombie =
                mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az ? az : null;
        boolean escalonPendiente = zombie != null
                && node.pos().equals(zombie.objetivoSobreUltimoEscalon());
        boolean escalonOcupado = zombie != null && zombie.escalonPendienteOcupado();
        boolean dentroTolerancia = mob.distanceToSqr(node.pos().getX() + 0.5D, node.pos().getY() + 0.5D,
                node.pos().getZ() + 0.5D) <= ARRIVE_DISTANCE_SQR;
        boolean landingHeightReached = mob.blockPosition().getY() <= node.pos().getY();
        boolean liveCellSupported = NavObstruction.hasSolidFace(mob.level(), mob.blockPosition().below());
        boolean aterrizajeDropValido = landingHeightReached && liveCellSupported;
        if (node.transition() == NavRoute.Transition.DROP && NavTrace.on()) {
            long tick = mob.level().getGameTime();
            if (tick - lastDropLandingTraceTick >= 10L) {
                lastDropLandingTraceTick = tick;
                NavTrace.ev("DROP_LANDING_PROGRESS", "uuid=" + mob.getUUID()
                        + " tick=" + tick + " routeIndex=" + routeIndex
                        + " landing=" + node.pos().toShortString()
                        + " liveCell=" + mob.blockPosition().toShortString()
                        + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                mob.getX(), mob.getY(), mob.getZ())
                        + " onGround=" + mob.onGround()
                        + " landingHeightReached=" + landingHeightReached
                        + " liveCellSupported=" + liveCellSupported
                        + " validLanding=" + aterrizajeDropValido
                        + " withinTolerance=" + dentroTolerancia
                        + " distanceSqr=" + String.format(java.util.Locale.ROOT, "%.3f",
                                mob.distanceToSqr(node.pos().getX() + 0.5D,
                                        node.pos().getY() + 0.5D, node.pos().getZ() + 0.5D))
                        + " landingFits=" + NavObstruction.fits(mob.level(), mob, node.pos())
                        + " landingSupport=" + NavObstruction.hasSupport(mob.level(), node.pos())
                        + " delta=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                mob.getDeltaMovement().x, mob.getDeltaMovement().y, mob.getDeltaMovement().z));
            }
        }
        boolean alcanzadoPorRuta = NavArrivalPolicy.waypointReached(
                false,
                node.transition() == NavRoute.Transition.DROP,
                escalonPendiente,
                dentroTolerancia,
                escalonOcupado,
                aterrizajeDropValido);
        boolean backtrackPendiente = recoveryBacktrackObjective != null
                && recoveryBacktrackObjective.equals(objective)
                && recoveryBacktrackObjective.equals(node.pos());
        boolean backtrackOcupadoFisicamente = backtrackPendiente
                && mob.blockPosition().equals(recoveryBacktrackObjective);
        boolean alcanzado = NavArrivalPolicy.backtrackWaypointReached(
                backtrackPendiente, backtrackOcupadoFisicamente, alcanzadoPorRuta);
        if (backtrackPendiente && !backtrackOcupadoFisicamente && dentroTolerancia
                && NavTrace.on() && mob.level().getGameTime() % 20L == 0L) {
            NavTrace.ev("BACKTRACK_NODE_OCCUPANCY_REQUIRED", "uuid=" + mob.getUUID()
                    + " target=" + recoveryBacktrackObjective.toShortString()
                    + " pos=" + mob.blockPosition().toShortString()
                    + " routeNode=" + node.pos().toShortString()
                    + " distSqr=" + String.format(java.util.Locale.ROOT, "%.3f",
                            mob.distanceToSqr(node.pos().getX() + 0.5D, node.pos().getY() + 0.5D,
                                    node.pos().getZ() + 0.5D)));
        }
        if (escalonPendiente && alcanzado && NavTrace.on()) {
            NavTrace.ev("STAIR_NODE_PHYSICALLY_REACHED", "uuid=" + mob.getUUID()
                    + " node=" + node.pos().toShortString()
                    + " pos=" + mob.blockPosition().toShortString());
        }
        if (node.transition() == NavRoute.Transition.DROP && alcanzado && NavTrace.on()) {
            NavTrace.ev("DROP_DONE", "uuid=" + mob.getUUID() + " idxRuta=" + routeIndex
                    + " aterrizaje=" + node.pos().toShortString() + " pos=" + mob.blockPosition().toShortString());
        }
        return alcanzado;
    }

    /**
     * Run22: evidencia el comando que realmente recibe la navegacion vanilla, no solo el waypoint elegido.
     * Se emite al cambiar el destino/transicion y como maximo cada 10 ticks; incluye ruta vanilla y Goals activos.
     */
    private void trazarComandoMovimiento(final NavRoute.Node node, final String motivo,
            final double x, final double y, final double z, final double speed) {
        if (!NavTrace.on()) {
            return;
        }
        long tick = mob.level().getGameTime();
        String key = motivo + ":" + (node == null ? "-" : node.pos().toShortString() + ":" + node.transition());
        if (key.equals(lastMoveTraceKey) && lastMoveTraceTick >= 0L && tick - lastMoveTraceTick < 10L) {
            return;
        }
        net.minecraft.world.entity.ai.navigation.PathNavigation navigation = mob.getNavigation();
        net.minecraft.world.level.pathfinder.Path path = navigation.getPath();
        String pathInfo = "-";
        if (path != null) {
            StringBuilder nodes = new StringBuilder();
            int first = Math.max(0, path.getNextNodeIndex());
            int limit = Math.min(path.getNodeCount(), first + 4);
            for (int i = first; i < limit; i++) {
                net.minecraft.world.level.pathfinder.Node pathNode = path.getNode(i);
                BlockPos pathPos = new BlockPos(pathNode.x, pathNode.y, pathNode.z);
                if (nodes.length() > 0) {
                    nodes.append('|');
                }
                nodes.append(pathPos.toShortString())
                        .append("{feet=").append(mob.level().getBlockState(pathPos).getBlock())
                        .append(",head=").append(mob.level().getBlockState(pathPos.above()).getBlock())
                        .append(",support=").append(mob.level().getBlockState(pathPos.below()).getBlock())
                        .append('}');
            }
            BlockPos requestedCell = BlockPos.containing(x, y, z);
            pathInfo = path.getTarget() + "/n=" + path.getNodeCount()
                    + "/next=" + path.getNextNodeIndex() + "/canReach=" + path.canReach()
                    + "/nodes=" + nodes
                    + "/requestedCell=" + requestedCell.toShortString()
                    + "{feet=" + mob.level().getBlockState(requestedCell).getBlock()
                    + ",head=" + mob.level().getBlockState(requestedCell.above()).getBlock()
                    + ",support=" + mob.level().getBlockState(requestedCell.below()).getBlock() + "}";
        }
        String goals = mob.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.isRunning())
                .map(wrapped -> wrapped.getGoal().getClass().getSimpleName())
                .sorted().collect(java.util.stream.Collectors.joining(","));
        String delta = Double.isNaN(lastMoveTraceX) ? "-" : String.format(java.util.Locale.ROOT,
                "%.2f,%.2f,%.2f", mob.getX() - lastMoveTraceX, mob.getY() - lastMoveTraceY,
                mob.getZ() - lastMoveTraceZ);
        NavTrace.ev("MOVE_COMMAND", "uuid=" + mob.getUUID() + " tick=" + tick
                + " reason=" + motivo + " node=" + (node == null ? "-" : node.pos().toShortString())
                + " transition=" + (node == null ? "-" : node.transition())
                + " requested=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", x, y, z)
                + " speed=" + speed + " pos=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                        mob.getX(), mob.getY(), mob.getZ())
                + " deltaSinceTrace=" + delta + " navDone=" + navigation.isDone()
                + " navInProgress=" + navigation.isInProgress() + " path=" + pathInfo
                + " activeGoals=" + (goals.isEmpty() ? "-" : goals));
        lastMoveTraceKey = key;
        lastMoveTraceTick = tick;
        lastMoveTraceX = mob.getX();
        lastMoveTraceY = mob.getY();
        lastMoveTraceZ = mob.getZ();
    }

    private boolean dropApproachCellSafe(BlockPos cell) {
        return cell != null && esPieValido(cell)
                && NavObstruction.hasSupport(mob.level(), cell)
                && NavObstruction.fitsAtCellCenter(mob.level(), mob, cell);
    }

    private boolean dropApproachWaypointReached() {
        return dropApproachWaypoint != null
                && mob.blockPosition().equals(dropApproachWaypoint)
                && mob.onGround()
                && dropApproachCellSafe(dropApproachWaypoint);
    }

    private void beginDropApproachTracking(BlockPos waypoint, BlockPos landing) {
        dropApproachActive = true;
        dropApproachMoveOwned = false;
        dropApproachWaypoint = waypoint.immutable();
        dropApproachLanding = landing.immutable();
        dropApproachTicks = 0;
        dropApproachNoProgress = 0;
        dropApproachBestDistance = horizontalDistanceToDropApproach(waypoint);
    }

    private double horizontalDistanceToDropApproach(BlockPos waypoint) {
        double dx = waypoint.getX() + 0.5D - mob.getX();
        double dz = waypoint.getZ() + 0.5D - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Waits for the normal path while it makes progress. If it ends early or stalls, this
     * DROP-only owner takes MoveControl and advances to the already validated supported waypoint.
     * Returns true only after physical, grounded occupancy of that waypoint is verified.
     */
    private boolean tickDropApproachOwner(BlockPos landing, long now) {
        if (!dropApproachActive || dropApproachWaypoint == null
                || dropApproachLanding == null || !dropApproachLanding.equals(landing)) {
            return false;
        }
        if (dropApproachWaypointReached()) {
            if (NavTrace.on()) {
                NavTrace.ev("DROP_APPROACH_OWNER_REACHED", "uuid=" + mob.getUUID()
                        + " tick=" + now + " waypoint=" + dropApproachWaypoint.toShortString()
                        + " landing=" + landing.toShortString()
                        + " pos=" + mob.blockPosition().toShortString()
                        + " grounded=true support=true fit=true owner="
                        + (dropApproachMoveOwned ? "MOVE_CONTROL" : "NAVIGATION"));
            }
            clearDropApproachOwner("waypoint_reached");
            return true;
        }

        dropApproachTicks++;
        if (!dropApproachCellSafe(dropApproachWaypoint)) {
            abortDropApproachOwner("waypoint_no_longer_safe", now);
            return false;
        }
        if (NavDropApproachOwnershipPolicy.timedOut(dropApproachTicks)) {
            abortDropApproachOwner("timeout", now);
            return false;
        }

        // Do not steer horizontally while airborne. Vanilla gravity/collision remains authoritative.
        if (!mob.onGround()) {
            if (dropApproachMoveOwned) {
                mob.getNavigation().stop();
            }
            return false;
        }

        BlockPos current = mob.blockPosition();
        boolean currentSupported = esPieValido(current)
                && !NavDropEdgeStepPolicy.fullyOffSupport(mob.getBoundingBox(),
                        pos -> NavObstruction.hasSupport(mob.level(), pos))
                && NavObstruction.fitsActualAabb(mob.level(), mob);
        if (dropApproachMoveOwned && !currentSupported) {
            abortDropApproachOwner("current_source_not_physically_supported", now);
            return false;
        }

        double distance = horizontalDistanceToDropApproach(dropApproachWaypoint);
        if (NavDropApproachOwnershipPolicy.madeProgress(distance, dropApproachBestDistance)) {
            dropApproachBestDistance = distance;
            dropApproachNoProgress = 0;
        } else {
            dropApproachNoProgress++;
        }

        boolean navigationDone = mob.getNavigation().isDone();
        if (!dropApproachMoveOwned && NavDropApproachOwnershipPolicy.shouldTakeOwnership(
                true, navigationDone, false, dropApproachNoProgress)) {
            dropApproachMoveOwned = true;
            dropApproachNoProgress = 0;
            dropApproachBestDistance = distance;
            mob.getNavigation().stop();
            if (NavTrace.on()) {
                NavTrace.ev("DROP_APPROACH_OWNER_SELECTED", "uuid=" + mob.getUUID()
                        + " tick=" + now + " owner=MOVE_CONTROL"
                        + " reason=" + (navigationDone
                                ? "navigation_done_before_waypoint" : "navigation_no_progress")
                        + " waypoint=" + dropApproachWaypoint.toShortString()
                        + " landing=" + landing.toShortString()
                        + " pos=" + current.toShortString()
                        + " distance=" + String.format(java.util.Locale.ROOT, "%.3f", distance));
            }
        }

        if (!dropApproachMoveOwned) {
            return false;
        }
        if (NavDropApproachOwnershipPolicy.noProgress(dropApproachNoProgress)) {
            abortDropApproachOwner("no_progress", now);
            return false;
        }

        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(
                dropApproachWaypoint.getX() + 0.5D, mob.getY(),
                dropApproachWaypoint.getZ() + 0.5D, 0.9D);
        if (NavTrace.on() && dropApproachTicks % 5 == 0) {
            NavTrace.ev("DROP_APPROACH_OWNER_PROGRESS", "uuid=" + mob.getUUID()
                    + " tick=" + now + " owner=MOVE_CONTROL"
                    + " waypoint=" + dropApproachWaypoint.toShortString()
                    + " landing=" + landing.toShortString()
                    + " pos=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                            mob.getX(), mob.getY(), mob.getZ())
                    + " distance=" + String.format(java.util.Locale.ROOT, "%.3f", distance)
                    + " noProgress=" + dropApproachNoProgress);
        }
        return false;
    }

    private void abortDropApproachOwner(String reason, long now) {
        if (NavTrace.on()) {
            NavTrace.ev("DROP_APPROACH_OWNER_ABORTED", "uuid=" + mob.getUUID()
                    + " tick=" + now + " reason=" + reason
                    + " waypoint=" + (dropApproachWaypoint == null ? "-" : dropApproachWaypoint.toShortString())
                    + " landing=" + (dropApproachLanding == null ? "-" : dropApproachLanding.toShortString())
                    + " pos=" + mob.blockPosition().toShortString()
                    + " ticks=" + dropApproachTicks
                    + " noProgress=" + dropApproachNoProgress);
        }
        clearDropApproachOwner(reason);
        state = NavState.OBSTRUCTED;
    }

    private void clearDropApproachOwner(String reason) {
        boolean releaseNavigation = dropApproachActive;
        boolean releaseMoveControl = dropApproachActive && dropApproachMoveOwned;
        if (dropApproachActive && NavTrace.on()) {
            NavTrace.ev("DROP_APPROACH_OWNER_CLEARED", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime() + " reason=" + reason
                    + " waypoint=" + (dropApproachWaypoint == null ? "-" : dropApproachWaypoint.toShortString())
                    + " landing=" + (dropApproachLanding == null ? "-" : dropApproachLanding.toShortString())
                    + " owner=" + (dropApproachMoveOwned ? "MOVE_CONTROL" : "NAVIGATION"));
        }
        dropApproachActive = false;
        dropApproachMoveOwned = false;
        dropApproachWaypoint = null;
        dropApproachLanding = null;
        dropApproachTicks = 0;
        dropApproachNoProgress = 0;
        dropApproachBestDistance = Double.MAX_VALUE;
        if (releaseNavigation) {
            mob.getNavigation().stop();
        }
        if (releaseMoveControl) {
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D);
        }
    }

    /**
     * PathNavigation can return an empty, already-complete path when the mob's bounding box overlaps
     * an adjacent validated cell. Keep the physical-arrival invariant, but nudge the mob into that
     * cell through MoveControl instead of falsely advancing the route.
     */
    private void activateDropEdgeStep(BlockPos takeoff, BlockPos landing) {
        dropEdgeStepActive = true;
        dropEdgeStepTakeoff = takeoff.immutable();
        dropEdgeStepLanding = landing.immutable();
        dropEdgeStepTicks = 0;
        dropEdgeStepNoProgress = 0;
        dropEdgeStepBestDistance = Double.MAX_VALUE;
        mob.getNavigation().stop();
        if (NavTrace.on()) {
            NavTrace.ev("DROP_EDGE_STEP_SELECTED", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime()
                    + " takeoff=" + takeoff.toShortString() + " landing=" + landing.toShortString()
                    + " pos=" + mob.blockPosition().toShortString() + " grounded=" + mob.onGround());
            NavTrace.ev("DROP_EDGE_STEP_OWNER", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime() + " owner=MOVE_CONTROL transition=DROP");
        }
        commandDropEdgeStep(landing, mob.getY(), false);
    }

    private void tickDropEdgeStep(BlockPos landing, long now) {
        dropEdgeStepTicks++;
        net.minecraft.world.phys.AABB box = mob.getBoundingBox();
        boolean offSupport = NavDropEdgeStepPolicy.fullyOffSupport(box,
                pos -> NavObstruction.hasSupport(mob.level(), pos));
        if (offSupport || mob.getY() < dropEdgeStepTakeoff.getY() - 0.05D) {
            if (NavTrace.on()) {
                NavTrace.ev("DROP_EDGE_STEP_SUPPORT_CLEARED", "uuid=" + mob.getUUID()
                        + " tick=" + now + " pos=" + mob.blockPosition().toShortString()
                        + " aabb=" + box + " support=false");
                if (mob.getY() < dropEdgeStepTakeoff.getY() - 0.05D) {
                    NavTrace.ev("DROP_EDGE_STEP_DESCENT_STARTED", "uuid=" + mob.getUUID()
                            + " tick=" + now + " y=" + mob.getY());
                }
            }
            commandDropEdgeStep(landing, landing.getY(), true);
            dropEdgeStepActive = false;
            return;
        }
        double dx = (landing.getX() + 0.5D) - mob.getX();
        double dz = (landing.getZ() + 0.5D) - mob.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < dropEdgeStepBestDistance - 0.01D) {
            dropEdgeStepBestDistance = distance;
            dropEdgeStepNoProgress = 0;
        } else {
            dropEdgeStepNoProgress++;
        }
        if (NavDropEdgeStepPolicy.noProgress(dropEdgeStepNoProgress)) {
            // The lip did not release after the bounded window. This source->landing edge is not
            // physically executable; blacklist it and replan instead of reactivating the same DROP.
            BlockPos stalledTakeoff = dropEdgeStepTakeoff;
            BlockPos stalledLanding = dropEdgeStepLanding;
            if (stalledTakeoff != null && stalledLanding != null) {
                long clave = NavDropExclusionPolicy.clave(stalledTakeoff.getX(), stalledTakeoff.getY(),
                        stalledTakeoff.getZ(), stalledLanding.getX(), stalledLanding.getY(),
                        stalledLanding.getZ());
                exclusionesDescenso.excluir(clave, huellaTerreno(stalledLanding));
                exclusionesDescenso.excluir(NavDropExclusionPolicy.claveDestino(
                        stalledLanding.getX(), stalledLanding.getY(), stalledLanding.getZ()),
                        huellaTerreno(stalledLanding));
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_EDGE_STEP_EXCLUDED", "uuid=" + mob.getUUID()
                            + " tick=" + now + " takeoff=" + stalledTakeoff.toShortString()
                            + " landing=" + stalledLanding.toShortString()
                            + " reason=no_progress");
                }
            }
            clearDropEdgeStep("no_progress");
            route = null;
            routeIndex = 0;
            lastRouteSignature = 0;
            globalCache = null;
            NavPlanner.cancelGlobalSearch(globalSearchHandle);
            globalSearchStart = null;
            globalSearchLastExpansions = 0;
            globalDesde = null;
            state = NavState.PLANNING;
            return;
        }
        if (NavDropEdgeStepPolicy.timedOut(dropEdgeStepTicks)) {
            clearDropEdgeStep("timeout");
            state = NavState.OBSTRUCTED;
            return;
        }
        commandDropEdgeStep(landing, mob.getY(), false);
        if (NavTrace.on() && dropEdgeStepTicks % 5 == 0) {
            NavTrace.ev("DROP_EDGE_STEP_PROGRESS", "uuid=" + mob.getUUID() + " tick=" + now
                    + " pos=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                            mob.getX(), mob.getY(), mob.getZ())
                    + " horizontal=" + String.format(java.util.Locale.ROOT, "%.3f", distance)
                    + " grounded=" + mob.onGround() + " aabb=" + box);
        }
    }

    private void commandDropEdgeStep(BlockPos landing, double y, boolean descending) {
        double tx = NavDropEdgeStepPolicy.horizontalTargetX(landing);
        double tz = NavDropEdgeStepPolicy.horizontalTargetZ(landing);
        mob.getMoveControl().setWantedPosition(tx, y, tz, 0.9D);
        if (NavTrace.on()) {
            NavTrace.ev(descending ? "DROP_EDGE_STEP_DESCENT_COMMAND" : "DROP_EDGE_STEP_COMMAND",
                    "uuid=" + mob.getUUID() + " tick=" + mob.level().getGameTime()
                            + " takeoff=" + (dropEdgeStepTakeoff == null ? "-" : dropEdgeStepTakeoff.toShortString())
                            + " landing=" + landing.toShortString()
                            + " target=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", tx, y, tz)
                            + " pos=" + mob.blockPosition().toShortString());
        }
    }

    private void clearDropEdgeStep(String reason) {
        if (dropEdgeStepActive && NavTrace.on()) {
            NavTrace.ev("DROP_EDGE_STEP_ABORTED", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime() + " reason=" + reason
                    + " pos=" + mob.blockPosition().toShortString());
        }
        dropEdgeStepActive = false;
        dropEdgeStepTakeoff = null;
        dropEdgeStepLanding = null;
        dropEdgeStepTicks = 0;
        dropEdgeStepNoProgress = 0;
        dropEdgeStepBestDistance = Double.MAX_VALUE;
    }

    private void aplicarAcercamientoAdyacente(NavRoute.Node node, Vec3 aim, double speed) {
        if (node == null || !mob.getNavigation().isDone()) {
            return;
        }
        boolean movementNode = node.transition() != NavRoute.Transition.BREAK
                && node.transition() != NavRoute.Transition.BRIDGE
                && node.transition() != NavRoute.Transition.BUILD;
        int distance = mob.blockPosition().distManhattan(node.pos());
        int maxDistance = node.transition() == NavRoute.Transition.DROP ? 3 : 2;
        if (!NavAdjacentApproachPolicy.shouldNudge(true, movementNode, distance, maxDistance,
                NavObstruction.fits(mob.level(), mob, node.pos()),
                NavObstruction.hasSupport(mob.level(), node.pos()))) {
            return;
        }
        mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
        lastMoveTarget = node.pos().toShortString() + ":ACERCAMIENTO-DIRECTO-ADYACENTE";
        boolean backtrackNudge = recoveryBacktrackObjective != null
                && recoveryBacktrackObjective.equals(objective)
                && recoveryBacktrackObjective.equals(node.pos());
        if (backtrackNudge && NavTrace.on()) {
            NavTrace.ev("BACKTRACK_ADJACENT_NUDGE", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime()
                    + " from=" + mob.blockPosition().toShortString()
                    + " target=" + recoveryBacktrackObjective.toShortString()
                    + " manhattan=" + distance
                    + " speed=" + speed
                    + " reason=navigation_done_without_exact_occupancy");
        }
        if (NavTrace.on() && mob.level().getGameTime() % 10L == 0L) {
            NavTrace.ev("NAV_ADJACENT_NUDGE", "uuid=" + mob.getUUID()
                    + " tick=" + mob.level().getGameTime()
                    + " node=" + node.pos().toShortString()
                    + " transition=" + node.transition()
                    + " pos=" + mob.blockPosition().toShortString()
                    + " distance=" + distance
                    + " speed=" + speed);
        }
    }
    /**
     * Plans and executes a bounded, non-destructive route back to the take-off of the active DROP.
     * Vanilla PathNavigation may return a completed one-node path when the target is vertically
     * above the mob in the same column. In that case, use the same validated A* movement graph as
     * normal navigation, temporarily suspending (not discarding) the DROP route.
     */
    private boolean iniciarRecuperacionFisicaOrigenDrop(BlockPos takeoff, long now) {
        if (recuperacionOrigenDropActiva) {
            return takeoff.equals(objetivoRecuperacionOrigenDrop);
        }
        if (now < nextDropRecoveryPlanAt) {
            if (NavTrace.on() && now % 20L == 0L) {
                NavTrace.ev("DROP_TAKEOFF_RECOVERY_PLAN_RETRY_WAIT", "uuid=" + mob.getUUID()
                        + " tick=" + now + " retryAt=" + nextDropRecoveryPlanAt
                        + " originalRoutePreserved=" + (route != null)
                        + " originalCursor=" + routeIndex);
            }
            return false;
        }

        BlockPos start = mob.blockPosition();
        boolean grounded = mob.onGround();

        // A previously selected candidate remains authoritative until the mob physically reaches
        // its center. Do not admit A* merely because blockPosition crossed the cell boundary.
        if (fuenteDropPendiente != null) {
            BlockPos candidate = fuenteDropPendiente;
            boolean candidateStillValid = grounded
                    && esPieValido(candidate)
                    && NavObstruction.fitsAtCellCenter(mob.level(), mob, candidate);
            if (!grounded) {
                mob.getNavigation().stop();
                return false;
            }
            if (!candidateStillValid) {
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_TAKEOFF_SOURCE_CELL_REJECTED", "uuid=" + mob.getUUID()
                            + " tick=" + now + " candidate=" + candidate.toShortString()
                            + " reason=feet_support_or_real_aabb_fit_changed");
                }
                fuenteDropPendiente = null;
                inicioSnapFuenteDrop = -1L;
            } else {
                double dx = mob.getX() - (candidate.getX() + 0.5D);
                double dz = mob.getZ() - (candidate.getZ() + 0.5D);
                boolean centered = start.equals(candidate) && dx * dx + dz * dz <= 0.04D;
                boolean physicallyValid = NavSnapOwnershipPolicy.snapComplete(
                        centered, grounded, esPieValido(start),
                        NavObstruction.hasSupport(mob.level(), start),
                        NavObstruction.fitsActualAabb(mob.level(), mob));
                if (physicallyValid) {
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_TAKEOFF_SOURCE_SNAP_REACHED", "uuid=" + mob.getUUID()
                                + " tick=" + now + " source=" + candidate.toShortString()
                                + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                        mob.getX(), mob.getY(), mob.getZ())
                                + " grounded=" + mob.onGround()
                                + " feetValid=" + esPieValido(start)
                                + " support=" + NavObstruction.hasSupport(mob.level(), start)
                                + " realAabbFits=" + NavObstruction.fitsActualAabb(mob.level(), mob));
                        NavTrace.ev("SNAP_COMPLETE", "uuid=" + mob.getUUID() + " tick=" + now
                                + " candidate=" + candidate.toShortString()
                                + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                        mob.getX(), mob.getY(), mob.getZ())
                                + " distanceSqr=" + String.format(java.util.Locale.ROOT, "%.4f",
                                        dx * dx + dz * dz)
                                + " grounded=true feetValid=true support=true realAabbFits=true"
                                + " action=replan_from_verified_source");
                    }
                    fuenteDropPendiente = null;
                    inicioSnapFuenteDrop = -1L;
                    resetPostSnapNavigationState(candidate, now);
                    start = mob.blockPosition();
                } else {
                    if (NavTrace.on() && now % 10L == 0L) {
                        net.minecraft.world.phys.AABB before = mob.getBoundingBox();
                        net.minecraft.world.phys.AABB target = before.move(
                                candidate.getX() + 0.5D - mob.getX(), 0.0D,
                                candidate.getZ() + 0.5D - mob.getZ());
                        net.minecraft.world.phys.AABB swept = new net.minecraft.world.phys.AABB(
                                Math.min(before.minX, target.minX), Math.min(before.minY, target.minY),
                                Math.min(before.minZ, target.minZ), Math.max(before.maxX, target.maxX),
                                Math.max(before.maxY, target.maxY), Math.max(before.maxZ, target.maxZ));
                        boolean hit = mob.level().getBlockCollisions(mob, swept).iterator().hasNext();
                        NavTrace.ev("SNAP_SWEPT_AABB", "uuid=" + mob.getUUID() + " tick=" + now
                                + " before=" + before + " target=" + target + " swept=" + swept
                                + " collision=" + hit + " dx=" + String.format(java.util.Locale.ROOT, "%.3f", dx)
                                + " dz=" + String.format(java.util.Locale.ROOT, "%.3f", dz));
                    }
                    if (inicioSnapFuenteDrop >= 0L && now - inicioSnapFuenteDrop > 80L) {
                        if (NavTrace.on()) {
                            NavTrace.ev("DROP_TAKEOFF_SOURCE_SNAP_FAILED", "uuid=" + mob.getUUID()
                                    + " tick=" + now + " candidate=" + candidate.toShortString()
                                    + " live=" + start.toShortString()
                                    + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                            mob.getX(), mob.getY(), mob.getZ())
                                    + " reason=physical_center_not_reached_within_80_ticks");
                        }
                        fuenteDropPendiente = null;
                        inicioSnapFuenteDrop = -1L;
                        fallar("DROP source-cell recovery stalled before physical occupancy: "
                                + candidate.toShortString());
                        return false;
                    }
                    mob.getNavigation().stop();
                    mob.getMoveControl().setWantedPosition(candidate.getX() + 0.5D, mob.getY(),
                            candidate.getZ() + 0.5D, 0.35D);
                    if (NavTrace.on() && now % 10L == 0L) {
                        NavTrace.ev("DROP_TAKEOFF_SOURCE_SNAP_STARTED", "uuid=" + mob.getUUID()
                                + " tick=" + now + " from=" + start.toShortString()
                                + " candidate=" + candidate.toShortString()
                                + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                        mob.getX(), mob.getY(), mob.getZ())
                                + " target=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                                        candidate.getX() + 0.5D, mob.getY(), candidate.getZ() + 0.5D)
                                + " dy=0 speed=0.35");
                    }
                    return false;
                }
            }
        }

        boolean sourceFeetValid = esPieValido(start);
        boolean sourceSupported = NavObstruction.hasSupport(mob.level(), start);
        boolean sourceFits = NavObstruction.fitsActualAabb(mob.level(), mob);
        if (!NavStepUpRecoveryPolicy.canStartFrom(grounded, sourceFeetValid, sourceSupported, sourceFits)) {
            mob.getNavigation().stop();

            // Resolve a grounded but invalid integer source from the actual horizontal AABB footprint.
            // Rank all eight same-Y neighbors by real AABB overlap and center distance; only physically safe cells qualify.
            if (grounded && (!sourceFeetValid || !sourceSupported || !sourceFits)) {
                BlockPos candidate = NavSourceCellPolicy.bestAdjacentCandidate(
                        start, mob.getBoundingBox(), mob.getX(), mob.getZ(),
                        cell -> esPieValido(cell)
                                && NavObstruction.hasSupport(mob.level(), cell)
                                && NavObstruction.fitsAtCellCenter(mob.level(), mob, cell));
                if (NavTrace.on() && now % 10L == 0L) {
                    NavTrace.ev("DROP_TAKEOFF_SOURCE_CELL_INVALID", "uuid=" + mob.getUUID()
                            + " tick=" + now + " current=" + start.toShortString()
                            + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                    mob.getX(), mob.getY(), mob.getZ())
                            + " aabb=" + mob.getBoundingBox()
                            + " grounded=" + grounded + " feetValid=" + sourceFeetValid
                            + " support=" + sourceSupported + " realAabbFits=" + sourceFits
                            + " candidate=" + (candidate == null ? "NONE" : candidate.toShortString()));
                }
                if (candidate != null) {
                    fuenteDropPendiente = candidate.immutable();
                    inicioSnapFuenteDrop = now;
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_TAKEOFF_SOURCE_CELL_CANDIDATE", "uuid=" + mob.getUUID()
                                + " tick=" + now + " from=" + start.toShortString()
                                + " candidate=" + candidate.toShortString()
                                + " candidateFeetValid=" + esPieValido(candidate)
                                + " candidateSupport=" + NavObstruction.hasSupport(mob.level(), candidate)
                                + " candidateRealAabbFits="
                                + NavObstruction.fitsAtCellCenter(mob.level(), mob, candidate));
                        NavTrace.ev("DROP_TAKEOFF_SOURCE_SELECTED", "uuid=" + mob.getUUID()
                                + " tick=" + now + " from=" + start.toShortString()
                                + " selected=" + candidate.toShortString()
                                + " strategy=largest_overlap_then_nearest_center_then_lexicographic");
                    }
                    mob.getMoveControl().setWantedPosition(candidate.getX() + 0.5D, mob.getY(),
                            candidate.getZ() + 0.5D, 0.35D);
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_TAKEOFF_SOURCE_SNAP_STARTED", "uuid=" + mob.getUUID()
                                + " tick=" + now + " from=" + start.toShortString()
                                + " candidate=" + candidate.toShortString()
                                + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                        mob.getX(), mob.getY(), mob.getZ())
                                + " target=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                                        candidate.getX() + 0.5D, mob.getY(), candidate.getZ() + 0.5D)
                                + " dy=0 speed=0.35");
                    }
                    return false;
                }
            }

            boolean landingAnchor = NavStepUpRecoveryPolicy.shouldStabilizeLanding(
                    grounded, sourceFeetValid, sourceSupported, sourceFits);
            if (landingAnchor) {
                mob.getMoveControl().setWantedPosition(start.getX() + 0.5D, mob.getY(),
                        start.getZ() + 0.5D, 0.35D);
            }
            if (NavTrace.on() && now % 10L == 0L) {
                NavTrace.ev("DROP_TAKEOFF_RECOVERY_WAIT_GROUNDED", "uuid=" + mob.getUUID()
                        + " live=" + start.toShortString()
                        + " exact=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                                mob.getX(), mob.getY(), mob.getZ())
                        + " velocity=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                mob.getDeltaMovement().x, mob.getDeltaMovement().y, mob.getDeltaMovement().z)
                        + " onGround=" + grounded + " feetValid=" + sourceFeetValid
                        + " support=" + sourceSupported + " fits=" + sourceFits
                        + " landingAnchor=" + (landingAnchor ? start.toShortString() : "NONE")
                        + " takeoff=" + takeoff.toShortString());
            }
            return false;
        }

        // This is a short, exact tactical recovery, not a strategic route search. A 4-cell
        // horizontal / 3-cell vertical envelope contains at most 9x9x7 search states; the
        // existing 900-expansion hard cap is unchanged. The admissible global A* core reopens
        // improved states and terminates only after extracting the exact take-off state.
        NavPlanner.GlobalResult result = NavPlanner.planExact(mob.level(), mob, start, takeoff,
                4, 3, MAX_EXPANSIONS_PER_PLAN, policies, construidas,
                exclusionesDescenso, exclusionesBreak);
        boolean safe = result.alcanzoMision() && !result.hitBudget()
                && takeoff.equals(result.route().destination())
                && NavDropTransitionPolicy.rutaRecuperacionOrigenSegura(result.route(), start, takeoff);
        if (!safe) {
            if (NavTrace.on()) {
                NavTrace.ev("DROP_TAKEOFF_RECOVERY_PLAN_REJECTED", "uuid=" + mob.getUUID()
                        + " from=" + start.toShortString() + " takeoff=" + takeoff.toShortString()
                        + " exactGoal=true radius=4 verticalRange=3"
                        + " hitBudget=" + result.hitBudget() + " reached=" + result.alcanzoMision()
                        + " route=" + result.route().describe()
                        + " summary={" + result.summary() + "}");
            }
            // Keep the original route, cursor and plannedFrom untouched. A failed temporary
            // search must not destroy the route whose DROP take-off we are trying to recover.
            if (dropTakeoffRechazoClave != null && dropTakeoffRechazoClave.equals(takeoff)) {
                dropTakeoffRechazos++;
            } else {
                dropTakeoffRechazoClave = takeoff.immutable();
                dropTakeoffRechazos = 1;
            }
            // A2 · NO LIVELOCK: un take-off al que el grafo no puede volver (ascenso > +1 por arista) se
            // reintentaba para siempre conservando la ruta. Medido en `a2n-20261121-212053`: 160 rechazos
            // idénticos y el ente congelado en TRAVERSING. Al tercer rechazo, con una fuente física válida,
            // se abandona la arista y se replanifica desde la celda real.
            boolean fuenteValida = grounded && esPieValido(start)
                    && NavObstruction.hasSupport(mob.level(), start)
                    && NavObstruction.fitsActualAabb(mob.level(), mob);
            if (dropTakeoffRechazos >= 3 && fuenteValida) {
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_TAKEOFF_RECOVERY_ABANDONED", "uuid=" + mob.getUUID()
                            + " tick=" + now + " from=" + start.toShortString()
                            + " takeoff=" + takeoff.toShortString() + " rechazos=" + dropTakeoffRechazos
                            + " action=discard_drop_edge_and_replan_from_real_source");
                }
                dropTakeoffRechazos = 0;
                dropTakeoffRechazoClave = null;
                if (misionFinal != null) {
                    objective = misionFinal.immutable();
                }
                route = null;
                routeIndex = 0;
                plannedFrom = null;
                objetivoTransicionLocal = null;
                objetivoAccionLocal = null;
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalDesde = null;
                nextDropRecoveryPlanAt = 0L;
                state = NavState.PLANNING;
                nextPlanAt = now;
                return false;
            }
            nextDropRecoveryPlanAt = now + REPLAN_COOLDOWN_TICKS;
            return false;
        }

        rutaDropSuspendida = route;
        indiceRutaDropSuspendida = routeIndex;
        dropTakeoffRechazos = 0;
        dropTakeoffRechazoClave = null;
        objetivoRecuperacionOrigenDrop = takeoff.immutable();
        plannedFromDropSuspendido = plannedFrom;
        plannedFromDropSuspendidoCapturado = true;
        plannedFrom = start.immutable();
        nextDropRecoveryPlanAt = 0L;
        route = result.route();
        routeIndex = 0;
        recuperacionOrigenDropActiva = true;
        state = NavState.TRAVERSING;
        if (NavTrace.on()) {
            NavTrace.ev("DROP_TAKEOFF_RECOVERY_ROUTE_ACCEPTED", "uuid=" + mob.getUUID()
                    + " from=" + start.toShortString()
                    + " exact=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                            mob.getX(), mob.getY(), mob.getZ())
                    + " onGround=" + grounded + " feetValid=" + sourceFeetValid
                    + " sourceSupport=" + sourceSupported + " sourceFitsRealAabb=" + sourceFits
                    + " takeoff=" + takeoff.toShortString()
                    + " suspendedIndex=" + indiceRutaDropSuspendida
                    + " recoveryRoute=" + route.describe()
                    + " summary={" + result.summary() + "}");
        }
        return true;
    }

    /** Snap a grounded invalid feet cell to its unique AABB-overlapping support, then replan. */
    private void resolverFuenteActualYReplanificar(long now) {
        if (!mob.onGround()) {
            mob.getNavigation().stop();
            return;
        }
        BlockPos current = mob.blockPosition();
        if (fuenteDropPendiente != null) {
            BlockPos candidate = fuenteDropPendiente;
            boolean valid = esPieValido(candidate)
                    && NavObstruction.fitsAtCellCenter(mob.level(), mob, candidate);
            boolean centered = NavSourceCellPolicy.centeredInCell(
                    mob.getX(), mob.getZ(), candidate, 0.20D);
            if (valid && current.equals(candidate)
                    && NavSnapOwnershipPolicy.snapComplete(centered, mob.onGround(),
                            esPieValido(current), NavObstruction.hasSupport(mob.level(), current),
                            NavObstruction.fitsActualAabb(mob.level(), mob))) {
                if (NavTrace.on()) NavTrace.ev("DROP_TAKEOFF_SOURCE_SNAP_REPLAN", "uuid=" + mob.getUUID()
                        + " tick=" + now + " source=" + current.toShortString()
                        + " action=replan_from_verified_aabb_source");
                if (NavTrace.on()) NavTrace.ev("SNAP_COMPLETE", "uuid=" + mob.getUUID()
                        + " tick=" + now + " candidate=" + candidate.toShortString()
                        + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                mob.getX(), mob.getY(), mob.getZ())
                        + " centered=" + centered
                        + " action=replan_from_verified_source");
                fuenteDropPendiente = null;
                inicioSnapFuenteDrop = -1L;
                fuenteDropSinCandidatoDesde = null;
                inicioEsperaFuenteDropSinCandidato = -1L;
                resetPostSnapNavigationState(current, now);
                route = null;
                routeIndex = 0;
                plannedFrom = null;
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalMision = null;
                globalDesde = null;
                nextDropRecoveryPlanAt = 0L;
                state = NavState.PLANNING;
                nextPlanAt = now;
                return;
            }
            if (!valid) {
                fuenteDropPendiente = null;
                inicioSnapFuenteDrop = -1L;
            } else if (inicioSnapFuenteDrop >= 0L && now - inicioSnapFuenteDrop > 80L) {
                if (NavTrace.on()) NavTrace.ev("DROP_TAKEOFF_SOURCE_SNAP_FAILED", "uuid=" + mob.getUUID()
                        + " tick=" + now + " candidate=" + candidate.toShortString()
                        + " reason=physical_center_not_reached_within_80_ticks");
                fuenteDropPendiente = null;
                inicioSnapFuenteDrop = -1L;
                fallar("DROP source-cell recovery stalled before physical occupancy: " + candidate);
                return;
            } else {
                double dx = mob.getX() - (candidate.getX() + 0.5D);
                double dz = mob.getZ() - (candidate.getZ() + 0.5D);
                if (NavTrace.on() && now % 10L == 0L) {
                    net.minecraft.world.phys.AABB before = mob.getBoundingBox();
                    net.minecraft.world.phys.AABB target = before.move(
                            candidate.getX() + 0.5D - mob.getX(), 0.0D,
                            candidate.getZ() + 0.5D - mob.getZ());
                    net.minecraft.world.phys.AABB swept = new net.minecraft.world.phys.AABB(
                            Math.min(before.minX, target.minX), Math.min(before.minY, target.minY),
                            Math.min(before.minZ, target.minZ), Math.max(before.maxX, target.maxX),
                            Math.max(before.maxY, target.maxY), Math.max(before.maxZ, target.maxZ));
                    boolean hit = mob.level().getBlockCollisions(mob, swept).iterator().hasNext();
                    NavTrace.ev("SNAP_SWEPT_AABB", "uuid=" + mob.getUUID() + " tick=" + now
                            + " before=" + before + " target=" + target + " swept=" + swept
                            + " collision=" + hit + " dx=" + String.format(java.util.Locale.ROOT, "%.3f", dx)
                            + " dz=" + String.format(java.util.Locale.ROOT, "%.3f", dz));
                }
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(candidate.getX() + 0.5D, mob.getY(),
                        candidate.getZ() + 0.5D, 0.35D);
                if (NavTrace.on() && now % 10L == 0L) NavTrace.ev("DROP_TAKEOFF_SOURCE_SNAP_STARTED",
                        "uuid=" + mob.getUUID() + " tick=" + now + " from=" + current.toShortString()
                                + " candidate=" + candidate.toShortString() + " speed=0.35 action=source_snap_then_replan strategy=largest_overlap_then_nearest_center_then_lexicographic");
                return;
            }
        }
        BlockPos candidate = NavSourceCellPolicy.bestAdjacentCandidate(current, mob.getBoundingBox(), mob.getX(), mob.getZ(),
                cell -> esPieValido(cell) && NavObstruction.hasSupport(mob.level(), cell)
                        && NavObstruction.fitsAtCellCenter(mob.level(), mob, cell));
        if (candidate == null) {
            if (!current.equals(fuenteDropSinCandidatoDesde)) {
                fuenteDropSinCandidatoDesde = current.immutable();
                inicioEsperaFuenteDropSinCandidato = now;
            } else if (inicioEsperaFuenteDropSinCandidato >= 0L && now - inicioEsperaFuenteDropSinCandidato >= 80L) {
                if (NavTrace.on()) NavTrace.ev("DROP_TAKEOFF_SOURCE_CELL_UNRESOLVED", "uuid=" + mob.getUUID()
                        + " tick=" + now + " current=" + current.toShortString()
                        + " waited=80 reason=no_safe_supported_aabb_overlapping_neighbor");
                fuenteDropSinCandidatoDesde = null;
                inicioEsperaFuenteDropSinCandidato = -1L;
                fallar("DROP source-cell recovery found no safe supported AABB-overlapping neighbor at " + current);
                return;
            }
            mob.getNavigation().stop();
            if (NavTrace.on() && now % 10L == 0L) NavTrace.ev("DROP_TAKEOFF_SOURCE_CELL_INVALID",
                    "uuid=" + mob.getUUID() + " tick=" + now + " current=" + current.toShortString()
                            + " grounded=true feetValid=" + esPieValido(current)
                            + " support=" + NavObstruction.hasSupport(mob.level(), current)
                            + " realAabbFits=" + NavObstruction.fitsActualAabb(mob.level(), mob)
                            + " candidate=NONE");
            return;
        }
        fuenteDropSinCandidatoDesde = null;
        inicioEsperaFuenteDropSinCandidato = -1L;
        fuenteDropPendiente = candidate.immutable();
        inicioSnapFuenteDrop = now;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(candidate.getX() + 0.5D, mob.getY(), candidate.getZ() + 0.5D, 0.35D);
        if (NavTrace.on()) NavTrace.ev("DROP_TAKEOFF_SOURCE_CELL_CANDIDATE", "uuid=" + mob.getUUID()
                + " tick=" + now + " from=" + current.toShortString() + " candidate=" + candidate.toShortString()
                + " feetValid=" + esPieValido(candidate) + " support=" + NavObstruction.hasSupport(mob.level(), candidate)
                + " fits=" + NavObstruction.fitsAtCellCenter(mob.level(), mob, candidate)
                + " action=source_snap_then_replan");
        if (NavTrace.on()) NavTrace.ev("DROP_TAKEOFF_SOURCE_SELECTED", "uuid=" + mob.getUUID()
                + " tick=" + now + " from=" + current.toShortString()
                + " selected=" + candidate.toShortString()
                + " strategy=largest_overlap_then_nearest_center_then_lexicographic");
    }

    private void limpiarRecuperacionOrigenDrop() {
        fuenteDropSinCandidatoDesde = null;
        inicioEsperaFuenteDropSinCandidato = -1L;
        rutaDropSuspendida = null;
        indiceRutaDropSuspendida = 0;
        objetivoRecuperacionOrigenDrop = null;
        recuperacionOrigenDropActiva = false;
        if (plannedFromDropSuspendidoCapturado) {
            plannedFrom = plannedFromDropSuspendido;
        }
        plannedFromDropSuspendido = null;
        plannedFromDropSuspendidoCapturado = false;
        nextDropRecoveryPlanAt = 0L;
    }

    private void finalizarRecuperacionOrigenDrop(long now) {
        BlockPos takeoff = objetivoRecuperacionOrigenDrop;
        NavRoute suspended = rutaDropSuspendida;
        int suspendedIndex = indiceRutaDropSuspendida;
        boolean reached = takeoff != null && NavDropTransitionPolicy.origenFisicamenteAlcanzado(
                mob.blockPosition().getX(), mob.blockPosition().getY(), mob.blockPosition().getZ(),
                takeoff.getX(), takeoff.getY(), takeoff.getZ())
                && esPieValido(mob.blockPosition())
                && NavObstruction.hasSupport(mob.level(), mob.blockPosition())
                && NavObstruction.fitsActualAabb(mob.level(), mob);
        if (NavTrace.on()) {
            NavTrace.ev(reached ? "DROP_TAKEOFF_RECOVERY_REACHED" : "DROP_TAKEOFF_RECOVERY_INCOMPLETE",
                    "uuid=" + mob.getUUID() + " takeoff=" + takeoff
                            + " live=" + mob.blockPosition().toShortString()
                            + " routeIndex=" + routeIndex + " route=" + (route == null ? "-" : route.describe()));
        }
        if (reached && suspended != null) {
            BlockPos restoredPlannedFrom = plannedFromDropSuspendido;
            int restoredSignature = suspended.signature();
            route = suspended;
            routeIndex = suspendedIndex;
            limpiarRecuperacionOrigenDrop();
            state = NavState.TRAVERSING;
            if (NavTrace.on()) {
                NavTrace.ev("DROP_TAKEOFF_RECOVERY_ROUTE_RESTORED", "uuid=" + mob.getUUID()
                        + " routeSignature=" + restoredSignature
                        + " routeIndex=" + routeIndex + "/" + route.length()
                        + " plannedFrom=" + (plannedFrom == null ? "NONE" : plannedFrom.toShortString())
                        + " capturedPlannedFrom="
                        + (restoredPlannedFrom == null ? "NONE" : restoredPlannedFrom.toShortString()));
            }
            return;
        }

        int rejectedSignature = suspended == null ? 0 : suspended.signature();
        limpiarRecuperacionOrigenDrop();
        route = null;
        routeIndex = 0;
        plannedFrom = null;
        lastRouteSignature = rejectedSignature;
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalMision = null;
        globalDesde = null;
        state = NavState.PLANNING;
        nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
    }

    private void traverse(long now) {
        if (route == null) {
            clearDropApproachOwner("route_null");
            clearDropEdgeStep("route_null");
            state = NavState.PLANNING;
            return;
        }
        if (dropDescentCommanded) {
            if (!dropDescentStarted && mob.getY() < dropDescentStartY - 0.05D) {
                dropDescentStarted = true;
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_DESCENT_STARTED", "uuid=" + mob.getUUID() + " tick=" + now
                            + " startY=" + String.format(java.util.Locale.ROOT, "%.3f", dropDescentStartY)
                            + " y=" + String.format(java.util.Locale.ROOT, "%.3f", mob.getY())
                            + " vy=" + String.format(java.util.Locale.ROOT, "%.3f", mob.getDeltaMovement().y));
                }
            } else if (!dropDescentStarted && !dropDescentStalledReported
                    && now - dropDescentCommandTick > 20L) {
                dropDescentStalledReported = true;
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_DESCENT_STALLED", "uuid=" + mob.getUUID() + " tick=" + now
                            + " y=" + String.format(java.util.Locale.ROOT, "%.3f", mob.getY())
                            + " grounded=" + mob.onGround());
                }
            }
        }
        if (postSnapRouteStartPending) {
            if (NavTrace.on()) {
                NavTrace.ev("POST_SNAP_ROUTE_START", "uuid=" + mob.getUUID() + " tick=" + now
                        + " signature=" + route.signature() + " routeIndex=" + routeIndex
                        + " start=" + mob.blockPosition().toShortString()
                        + " state=TRAVERSING");
            }
            postSnapRouteStartPending = false;
        }
        // A pending source snap is the sole movement owner. Normal route commands (including DROP)
        // must not run until the snap completes, fails, or is explicitly invalidated.
        if (NavSnapOwnershipPolicy.snapOwnsMovement(fuenteDropPendiente)) {
            mob.getNavigation().stop();
            if (NavTrace.on() && now % 10L == 0L) {
                double dx = mob.getX() - (fuenteDropPendiente.getX() + 0.5D);
                double dz = mob.getZ() - (fuenteDropPendiente.getZ() + 0.5D);
                NavTrace.ev("SNAP_MOVE_OWNER", "uuid=" + mob.getUUID()
                        + " tick=" + now + " pending=" + fuenteDropPendiente.toShortString()
                        + " owner=SNAP routeBlocked=true action=resolve_snap_only");
                NavTrace.ev("SNAP_ADMISSION", "uuid=" + mob.getUUID() + " tick=" + now
                        + " live=" + mob.blockPosition().toShortString()
                        + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                mob.getX(), mob.getY(), mob.getZ())
                        + " candidate=" + fuenteDropPendiente.toShortString()
                        + " dx=" + String.format(java.util.Locale.ROOT, "%.3f", dx)
                        + " dz=" + String.format(java.util.Locale.ROOT, "%.3f", dz)
                        + " centeredInCell=" + NavSourceCellPolicy.centeredInCell(
                                mob.getX(), mob.getZ(), fuenteDropPendiente, 0.20D)
                        + " grounded=" + mob.onGround()
                        + " decision=REJECT_DROP_ROUTE_PENDING_SNAP");
            }
            resolverFuenteActualYReplanificar(now);
            return;
        }
        if (oscillating && NavSnapOwnershipPolicy.allowsOscillationAbort(fuenteDropPendiente)) {
            // A→B→A detectado: la ruta actual no sirve; se invalida y se pasa a recuperación.
            routesRejected++;
            if (recuperacionOrigenDropActiva) {
                lastRouteSignature = rutaDropSuspendida == null ? route.signature()
                        : rutaDropSuspendida.signature();
                limpiarRecuperacionOrigenDrop();
                plannedFrom = null;
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalMision = null;
                globalDesde = null;
                route = null;
                routeIndex = 0;
                state = NavState.PLANNING;
                nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
                return;
            }
            if (NavTrace.on()) {
                NavTrace.ev("POST_SNAP_GUARD", "uuid=" + mob.getUUID() + " tick=" + now
                        + " recentCells=" + recentCells.size() + " occurrences=" + recentCellOccurrences
                        + " oscillating=true pending=" + (fuenteDropPendiente == null ? "NONE" : fuenteDropPendiente.toShortString())
                        + " postSnapWindow=" + (lastSnapResetTick >= 0L && now >= lastSnapResetTick));
                NavTrace.ev("SNAP_GUARD", "uuid=" + mob.getUUID() + " tick=" + now
                        + " pending=NONE oscillating=true occurrences=" + recentCellOccurrences
                        + " recentCells=" + recentCells.size() + " action=abort_route");
            }
            lastRouteSignature = route.signature();
            route = null;
            state = NavState.RECOVERING;
            return;
        }
        NavRoute.Node node = route.currentNode(routeIndex);
        if (dropApproachActive && (node == null || node.transition() != NavRoute.Transition.DROP
                || dropApproachLanding == null || !dropApproachLanding.equals(node.pos()))) {
            clearDropApproachOwner("route_node_changed");
        }
        // A2 Stage 6F · la ruta avanza cuando el nodo esta ALCANZADO de verdad: para un DROP eso exige estar
        // fisicamente en el aterrizaje (a su altura o por debajo) y con suelo, no solo cerca.
        while (node != null && nodoAlcanzado(node)) {
            NavRoute.Node reachedNode = node;
            int previousRouteIndex = routeIndex;
            routeIndex++;
            if (reachedNode.transition() == NavRoute.Transition.DROP) {
                clearDropApproachOwner("landing_confirmed");
            }
            if (reachedNode.transition() == NavRoute.Transition.DROP && NavTrace.on()) {
                NavTrace.ev("DROP_LANDING_CONFIRMED", "uuid=" + mob.getUUID()
                        + " tick=" + now + " landing=" + reachedNode.pos().toShortString()
                        + " pos=" + mob.blockPosition().toShortString()
                        + " grounded=" + mob.onGround()
                        + " support=" + NavObstruction.hasSupport(mob.level(), mob.blockPosition()));
                NavTrace.ev("DROP_CURSOR_ADVANCE", "uuid=" + mob.getUUID()
                        + " landing=" + reachedNode.pos().toShortString()
                        + " previousIndex=" + previousRouteIndex
                        + " nextIndex=" + routeIndex
                        + " routeLength=" + route.length());
            }
            replanteosPorGuarda = 0;
            intentosDeDescenso = 0;
            node = route.currentNode(routeIndex);
        }
        if (dropApproachActive && (node == null || node.transition() != NavRoute.Transition.DROP
                || dropApproachLanding == null || !dropApproachLanding.equals(node.pos()))) {
            clearDropApproachOwner("route_node_changed");
        }
        if (node == null) {
            if (recuperacionOrigenDropActiva) {
                finalizarRecuperacionOrigenDrop(now);
                return;
            }
            // Ruta agotada sin llegar: si el destino no está cerca, hay que replanificar.
            // A2 Stage 7F · PROGRESO VERIFICADO AL CERRAR UN TRAMO. Antes el tope de escalones (24) se agotaba
            // aunque el ente subiera el escape entero (medido en `a2n-20261017-125658`: dos objetivos alcanzados y
            // FAILED a 29 bloques). Aqui se reinicia SOLO si al cerrar el tramo la distancia a la MISION ha bajado
            // de verdad al menos PROGRESO_MINIMO_DE_OBJETIVO: reiniciar en cada ruta vacia crearia un bucle de
            // re-estadiaje infinito, y eso NO se hace: sin progreso medido, el conteo sigue y el tope termina.
            BlockPos metaVerificada = misionDePie();
            if (metaVerificada != null) {
                double distMisionAhora = Math.hypot(mob.getX() - (metaVerificada.getX() + 0.5D),
                        mob.getZ() - (metaVerificada.getZ() + 0.5D));
                if (distMisionAhora < mejorDistMision - PROGRESO_MINIMO_DE_OBJETIVO) {
                    mejorDistMision = distMisionAhora;
                    escalonesDeObjetivo = 0;
        rearmadoPorCambioDeTerreno = false;
        cambioTerrenoPendienteRearme = false;
                    ultimoEscalonDeObjetivo = "progreso verificado al cerrar tramo: dMision="
                            + String.format(java.util.Locale.ROOT, "%.1f", distMisionAhora)
                            + " -> contador de escalones reiniciado";
                }
            }
            if (mob.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                    objective.getZ() + 0.5D) > ARRIVE_DISTANCE_SQR) {
                lastRouteSignature = route.signature();
                route = null;
                state = NavState.PLANNING;
            }
            return;
        }
        // La transición decide la acción, no solo el destino.
        Vec3 aim = new Vec3(node.pos().getX() + 0.5D, node.pos().getY(), node.pos().getZ() + 0.5D);
        // A2 Stage 7E · AVANCE REAL hacia el nodo activo: se mide cada tick y se lleva la mejor distancia vista.
        // Mientras el ente se acerque, NO hay obstruccion aunque vanilla no tenga ruta en ese instante.
        {
            BlockPos nodoActual = node.pos().immutable();
            if (!nodoActual.equals(nodoSeguido)) {
                nodoSeguido = nodoActual;
                ticksSinAvance = 0;
                mejorDistAlNodo = Double.MAX_VALUE;
            }
            double distAhora = Math.sqrt(mob.distanceToSqr(nodoActual.getX() + 0.5D, nodoActual.getY(),
                    nodoActual.getZ() + 0.5D));
            if (distAhora < mejorDistAlNodo - AVANCE_MINIMO_DE_NODO) {
                mejorDistAlNodo = distAhora;
                ticksSinAvance = 0;
            } else {
                ticksSinAvance++;
            }
        }
        // A2 diagnóstico: se registra CADA envío de movimiento al juego (destino y transición), para poder
        // distinguir "no produce ruta" de "produce ruta pero nunca mueve".
        moveSubmissions++;
        lastMoveTarget = node.pos().toShortString() + ":" + node.transition();
        switch (node.transition()) {
            case JUMP_UP -> {
                // Legacy route data is fail-closed; ordinary ascent is capped at one block.
                state = NavState.OBSTRUCTED;
                lastMoveTarget = node.pos().toShortString() + ":JUMP_UP-RECHAZADO-INVARIANTE-+1";
                return;
            }
            case STEP_UP -> {
                int stepDx = node.pos().getX() - mob.blockPosition().getX();
                int stepDy = node.pos().getY() - mob.blockPosition().getY();
                int stepDz = node.pos().getZ() - mob.blockPosition().getZ();
                if (recuperacionOrigenDropActiva && mob.onGround()
                        && !NavStepUpRecoveryPolicy.isAdjacentOneBlockStep(stepDx, stepDy, stepDz)) {
                    // The mob landed somewhere other than the planned predecessor. Do not try to
                    // execute a stale one-block edge from a different elevation; abandon this
                    // auxiliary route and replan the strategic mission from the live grounded cell.
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_TAKEOFF_RECOVERY_EDGE_STALE", "uuid=" + mob.getUUID()
                                + " pos=" + mob.blockPosition().toShortString()
                                + " target=" + node.pos().toShortString()
                                + " delta=" + stepDx + "," + stepDy + "," + stepDz
                                + " takeoff=" + objetivoRecuperacionOrigenDrop);
                    }
                    finalizarRecuperacionOrigenDrop(now);
                    return;
                }
                if (recuperacionOrigenDropActiva && !mob.onGround()) {
                    // Preserve horizontal travel during a natural jump/fall, but never ask JumpControl
                    // to start a second jump until the mob has landed on a verified source cell.
                    mob.getNavigation().stop();
                    mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1.0D);
                    if (NavTrace.on() && mob.level().getGameTime() % 10L == 0L) {
                        NavTrace.ev("DROP_TAKEOFF_STEP_UP_AIRBORNE", "uuid=" + mob.getUUID()
                                + " pos=" + mob.blockPosition().toShortString()
                                + " exactY=" + String.format(java.util.Locale.ROOT, "%.2f", mob.getY())
                                + " target=" + node.pos().toShortString()
                                + " delta=" + stepDx + "," + stepDy + "," + stepDz);
                    }
                    trazarComandoMovimiento(node, "STEP_UP-RECOVERY-AIRBORNE", aim.x, aim.y, aim.z, 1.0D);
                    return;
                }
                boolean sourceSupported = NavObstruction.hasSupport(mob.level(), mob.blockPosition());
                boolean recoveryStepAssist = recuperacionOrigenDropActiva
                        && NavStepUpRecoveryPolicy.canAssist(
                                stepDx, stepDy, stepDz, mob.onGround(), sourceSupported,
                                NavObstruction.blockedAtFeet(mob.level(), mob, node.pos().below()),
                                NavObstruction.headClearOneUp(mob.level(), mob, node.pos().below()),
                                NavObstruction.fits(mob.level(), mob, node.pos()),
                                NavObstruction.hasSupport(mob.level(), node.pos()));
                if (recoveryStepAssist) {
                    // Execute this already-validated one-block climb through vanilla's natural
                    // JumpControl + MoveControl. Exact physical occupancy remains the only advance condition.
                    mob.getNavigation().stop();
                    mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1.0D);
                    mob.getJumpControl().jump();
                    if (NavTrace.on() && mob.level().getGameTime() % 10L == 0L) {
                        NavTrace.ev("DROP_TAKEOFF_STEP_UP_ASSIST", "uuid=" + mob.getUUID()
                                + " from=" + mob.blockPosition().toShortString()
                                + " target=" + node.pos().toShortString()
                                + " onGround=" + mob.onGround()
                                + " sourceSupport=" + sourceSupported
                                + " support=" + NavObstruction.hasSupport(mob.level(), node.pos())
                                + " fits=" + NavObstruction.fits(mob.level(), mob, node.pos()));
                    }
                } else {
                    mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0D);
                    if (recuperacionOrigenDropActiva) {
                        aplicarAcercamientoAdyacente(node, aim, 1.0D);
                    }
                }
                trazarComandoMovimiento(node, "STEP_UP" + (recoveryStepAssist ? "-RECOVERY-ASSIST" : ""),
                        aim.x, aim.y, aim.z, 1.0D);
            }
            case DROP -> {
                if (dropApproachActive) {
                    if (dropApproachLanding == null || !dropApproachLanding.equals(node.pos())) {
                        clearDropApproachOwner("route_node_changed");
                        return;
                    }
                    if (!tickDropApproachOwner(node.pos(), mob.level().getGameTime())) {
                        return;
                    }
                }
                if (dropEdgeStepActive) {
                    if (dropEdgeStepLanding != null && dropEdgeStepLanding.equals(node.pos())) {
                        tickDropEdgeStep(node.pos(), mob.level().getGameTime());
                        return;
                    }
                    clearDropApproachOwner("route_node_changed");
                    clearDropEdgeStep("route_node_changed");
                }
                // A2 Stage 6F · DESCENSO EXPLICITO Y VERIFICADO. El nodo es el ATERRIZAJE: se describe y se valida
                // (contigüidad, holgura de pies y cuerpo, soporte con colisión —construido y VERIFICADO si es obra
                // del plan—, caída dentro del límite y CAMINO DE CAÍDA despejado) ANTES de ordenar movimiento. La
                // ruta no avanza hasta el aterrizaje físico (`nodoAlcanzado`).
                // Origen de esta transicion: el nodo anterior para DROP posteriores; en routeIndex=0,
                // plannedFrom es el origen fisico exacto desde el que se acepto esta ruta. Si el mob se
                // desplazo verticalmente, se ejecuta una ruta auxiliar validada hasta ese origen antes de caer.
                BlockPos origenAnterior = null;
                if (route != null && routeIndex > 0 && routeIndex < route.nodes().size()) {
                    NavRoute.Node actual = route.currentNode(routeIndex);
                    if (actual != null && actual.pos().equals(node.pos())) {
                        NavRoute.Node previo = route.currentNode(routeIndex - 1);
                        if (previo != null) {
                            origenAnterior = previo.pos();
                        }
                    }
                }
                BlockPos origenPlan = NavDropTransitionPolicy.origenPlanificado(
                        routeIndex, plannedFrom, origenAnterior);
                if (NavTrace.on() && routeIndex == 0) {
                    NavTrace.ev("DROP_TAKEOFF_ORIGIN_RESOLVED", "uuid=" + mob.getUUID()
                            + " routeIndex=0 routeStart=" + (plannedFrom == null ? "NONE" : plannedFrom.toShortString())
                            + " live=" + mob.blockPosition().toShortString()
                            + " landing=" + node.pos().toShortString()
                            + " resolved=" + (origenPlan == null ? "NONE" : origenPlan.toShortString()));
                }
                Descenso descenso = describirDescenso(node.pos(),
                        construidas.contains(node.pos().below()), true, origenPlan);
                boolean takeoffSupported = origenPlan != null
                        && esPieValido(origenPlan)
                        && NavObstruction.hasSupport(mob.level(), origenPlan)
                        && NavObstruction.fitsAtCellCenter(mob.level(), mob, origenPlan);
                long sourceCheckTick = mob.level().getGameTime();

                if (origenPlan == null) {
                    boolean liveGrounded = mob.onGround();
                    boolean liveFeetValid = esPieValido(mob.blockPosition());
                    boolean liveSupported = NavObstruction.hasSupport(mob.level(), mob.blockPosition());
                    boolean liveFits = NavObstruction.fitsActualAabb(mob.level(), mob);
                    if (liveGrounded && liveFeetValid && liveSupported && liveFits) {
                        origenPlan = mob.blockPosition().immutable();
                        takeoffSupported = true;
                        if (NavTrace.on()) NavTrace.ev("DROP_TAKEOFF_LIVE_ORIGIN_VERIFIED", "uuid=" + mob.getUUID()
                                + " tick=" + sourceCheckTick + " source=" + origenPlan.toShortString()
                                + " grounded=true feetValid=true support=true realAabbFits=true");
                    } else if (liveGrounded) {
                        mob.getNavigation().stop();
                        resolverFuenteActualYReplanificar(sourceCheckTick);
                        if (NavTrace.on() && sourceCheckTick % 10L == 0L) NavTrace.ev(
                                "DROP_TAKEOFF_ORIGIN_MISSING_SOURCE_RECOVERY", "uuid=" + mob.getUUID()
                                        + " tick=" + sourceCheckTick + " liveSource=" + mob.blockPosition().toShortString()
                                        + " feetValid=" + liveFeetValid + " support=" + liveSupported
                                        + " realAabbFits=" + liveFits);
                        return;
                    } else {
                        mob.getNavigation().stop();
                        if (NavTrace.on() && sourceCheckTick % 10L == 0L) NavTrace.ev(
                                "DROP_TAKEOFF_ORIGIN_MISSING_WAIT", "uuid=" + mob.getUUID()
                                        + " tick=" + sourceCheckTick + " liveSource=" + mob.blockPosition().toShortString()
                                        + " grounded=false action=wait_for_natural_landing");
                        return;
                    }
                }

                // A route may have been planned while the mob was falling or standing in an
                // unsupported blockPosition. Coordinate proximity is not a take-off: never
                // issue DROP from that stale origin. Let physics land the mob, then replan the
                // strategic route from its actual grounded, supported feet cell.
                if (origenPlan != null && !takeoffSupported) {
                    boolean liveGrounded = mob.onGround();
                    boolean liveFeetValid = esPieValido(mob.blockPosition());
                    boolean liveSupported = NavObstruction.hasSupport(mob.level(), mob.blockPosition());
                    boolean liveFits = NavObstruction.fitsActualAabb(mob.level(), mob);
                    if (NavDropTransitionPolicy.replanificarDesdeOrigenSoportado(
                            takeoffSupported, liveGrounded, liveFeetValid, liveSupported, liveFits)) {
                        if (NavTrace.on()) NavTrace.ev("DROP_TAKEOFF_UNSUPPORTED_REPLAN", "uuid=" + mob.getUUID()
                                + " tick=" + sourceCheckTick + " staleTakeoff=" + origenPlan.toShortString()
                                + " liveSource=" + mob.blockPosition().toShortString()
                                + " grounded=true feetValid=true support=true realAabbFits=true"
                                + " action=discard_stale_drop_and_replan");
                        route = null;
                        routeIndex = 0;
                        plannedFrom = null;
                        globalCache = null;
                        NavPlanner.cancelGlobalSearch(globalSearchHandle);
                        globalSearchStart = null;
                        globalSearchLastExpansions = 0;
                        globalMision = null;
                        globalDesde = null;
                        nextDropRecoveryPlanAt = 0L;
                        state = NavState.PLANNING;
                        nextPlanAt = sourceCheckTick;
                    } else if (NavDropTransitionPolicy.debeRecuperarFuenteActual(
                            takeoffSupported, liveGrounded, liveFeetValid, liveSupported, liveFits)) {
                        mob.getNavigation().stop();
                        resolverFuenteActualYReplanificar(sourceCheckTick);
                        if (NavTrace.on() && sourceCheckTick % 10L == 0L) NavTrace.ev(
                                "DROP_TAKEOFF_UNSUPPORTED_SOURCE_RECOVERY", "uuid=" + mob.getUUID()
                                        + " tick=" + sourceCheckTick + " staleTakeoff=" + origenPlan.toShortString()
                                        + " liveSource=" + mob.blockPosition().toShortString()
                                        + " grounded=true feetValid=" + liveFeetValid + " support=" + liveSupported
                                        + " realAabbFits=" + liveFits + " action=resolve_ranked_8_neighbor_aabb_source");
                    } else {
                        mob.getNavigation().stop();
                        if (NavTrace.on() && sourceCheckTick % 10L == 0L) NavTrace.ev(
                                "DROP_TAKEOFF_UNSUPPORTED_WAIT", "uuid=" + mob.getUUID()
                                        + " tick=" + sourceCheckTick + " staleTakeoff=" + origenPlan.toShortString()
                                        + " liveSource=" + mob.blockPosition().toShortString()
                                        + " grounded=false feetValid=" + liveFeetValid + " support=" + liveSupported
                                        + " realAabbFits=" + liveFits + " action=wait_for_natural_landing");
                    }
                    return;
                }

                // A2 · DROP QUE ATERRIZA MÁS ABAJO DE LO PREVISTO. Si el mob ya cayó a una celda de pie válida
                // (suelo, soporte, AABB) dentro de la caída segura y a ≤1 horizontal del aterrizaje del nodo,
                // el DROP está físicamente completado: se acepta el aterrizaje real y se replanifica en vez de
                // exigir volver al take-off (imposible cuando el grafo solo asciende +1 por arista).
                if (node.transition() == NavRoute.Transition.DROP
                        && NavDropOvershootPolicy.acceptRealLanding(
                                mob.onGround(), esPieValido(mob.blockPosition()),
                                NavObstruction.hasSupport(mob.level(), mob.blockPosition()),
                                NavObstruction.fitsActualAabb(mob.level(), mob),
                                mob.blockPosition().getX(), mob.blockPosition().getY(), mob.blockPosition().getZ(),
                                node.pos().getX(), node.pos().getY(), node.pos().getZ(),
                                policies.maxSafeDrop())) {
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_OVERSHOOT_LANDING_ACCEPTED", "uuid=" + mob.getUUID()
                                + " tick=" + sourceCheckTick + " planned=" + node.pos().toShortString()
                                + " real=" + mob.blockPosition().toShortString()
                                + " dy=" + (node.pos().getY() - mob.blockPosition().getY())
                                + " action=accept_real_landing_and_replan");
                    }
                    clearDropApproachOwner("drop_overshoot_landed");
                    clearDropEdgeStep("drop_overshoot_landed");
                    dropTakeoffRechazos = 0;
                    dropTakeoffRechazoClave = null;
                    if (misionFinal != null) {
                        objective = misionFinal.immutable();
                    }
                    route = null;
                    routeIndex = 0;
                    plannedFrom = null;
                    objetivoTransicionLocal = null;
                    objetivoAccionLocal = null;
                    globalCache = null;
                    NavPlanner.cancelGlobalSearch(globalSearchHandle);
                    globalSearchStart = null;
                    globalSearchLastExpansions = 0;
                    globalDesde = null;
                    state = NavState.PLANNING;
                    nextPlanAt = now;
                    return;
                }
                if (origenPlan != null
                        && !NavDropTransitionPolicy.origenFisicamenteAlcanzado(
                                mob.onGround(), esPieValido(mob.blockPosition()),
                                NavObstruction.hasSupport(mob.level(), mob.blockPosition()),
                                NavObstruction.fitsActualAabb(mob.level(), mob),
                                mob.blockPosition().getX(), mob.blockPosition().getY(), mob.blockPosition().getZ(),
                                origenPlan.getX(), origenPlan.getY(), origenPlan.getZ())) {
                    lastMoveTarget = origenPlan.toShortString() + ":ACERCARSE-ORIGEN-DROP";
                    iniciarRecuperacionFisicaOrigenDrop(origenPlan, sourceCheckTick);
                    return;
                }
                ultimoDescenso = descenso.resumen();
                long dropNow = mob.level().getGameTime();
                traceDropDecision(descenso.valido() ? "DROP_VALID" : "DROP_REJECTED",
                        "nodo=" + node.pos().toShortString()
                                + " takeoff=" + descenso.salida().toShortString()
                                + " dy=" + descenso.dy()
                                + " reason={" + descenso.rechazo() + "}"
                                + " constructed=" + descenso.aterrizajeConstruido()
                                + " idxRuta=" + routeIndex + "/" + (route == null ? "-" : route.length())
                                + " pathCanReach=" + (mob.getNavigation().getPath() != null
                                && mob.getNavigation().getPath().canReach())
                                + " pathDone=" + mob.getNavigation().isDone()
                                + " attempts=" + intentosDeDescenso + "/" + MAX_INTENTOS_DESCENSO
                                + " failures=" + fallosDeDescenso
                                + " nextRecoveryAt=" + nextRecoveryAt
                                + " pos=" + mob.blockPosition().toShortString()
                                + " state=" + state, dropNow);
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_CANDIDATE", "uuid=" + mob.getUUID() + " idxRuta=" + routeIndex
                            + " sig=" + (route != null ? route.signature() : "-")
                            + " nodo=" + node.pos().toShortString() + " trans=DROP");
                }
                if (!descenso.valido()) {
                    fallosDeDescenso++;
                    ultimaAccionDeRuta = node.pos().toShortString() + ":DROP-RECHAZADO (" + descenso.rechazo() + ")";
                    lastMoveTarget = node.pos().toShortString() + ":DROP-RECHAZADO";
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_REJECTED", "uuid=" + mob.getUUID() + " idxRuta=" + routeIndex
                                + " nodo=" + node.pos().toShortString() + " motivo={" + descenso.rechazo() + "}");
                    }
                    // DROP no puede transformarse en BREAK oportunista. La obstrucción se registra
                    // como exclusión del aterrizaje y el planificador debe encontrar otra ruta; si romper
                    // el bloque es necesario, deberá emitir un nodo BREAK explícito.
                    BlockPos obstaculoCaida = primerBloqueoEnCaminoDeCaida(node.pos());
                    BlockPos desde = origenPlan != null ? origenPlan : mob.blockPosition();
                    long huellaDrop = huellaTerreno(node.pos());
                    exclusionesDescenso.excluir(NavDropExclusionPolicy.clave(
                            desde.getX(), desde.getY(), desde.getZ(),
                            node.pos().getX(), node.pos().getY(), node.pos().getZ()),
                            huellaDrop);
                    // A blocked fall corridor is a property of the landing column, not of one take-off edge.
                    // Exclude this landing for every adjacent origin so A* must route around non-breakable foliage.
                    if (obstaculoCaida != null) {
                        long claveDestinoDrop = NavDropExclusionPolicy.claveDestino(
                                node.pos().getX(), node.pos().getY(), node.pos().getZ());
                        boolean nuevaExclusion = !exclusionesDescenso.contiene(claveDestinoDrop);
                        exclusionesDescenso.excluir(claveDestinoDrop, huellaDrop);
                        if (nuevaExclusion && NavTrace.on()) {
                            NavTrace.ev("DROP_DESTINATION_EXCLUDED", "uuid=" + mob.getUUID()
                                    + " landing=" + node.pos().toShortString()
                                    + " obstacle=" + obstaculoCaida.toShortString()
                                    + " obstacleState=" + mob.level().getBlockState(obstaculoCaida)
                                    + " fingerprint=" + huellaDrop
                                    + " entries=" + exclusionesDescenso.tamano());
                        }
                    } else if (NavTrace.on()) {
                        NavTrace.ev("DROP_REJECTION_NO_COLUMN_BLOCK", "uuid=" + mob.getUUID()
                                + " landing=" + node.pos().toShortString()
                                + " pos=" + mob.blockPosition().toShortString()
                                + " reason=" + descenso.rechazo());
                    }
                    // La exclusion evita reejecutar el mismo DROP; invalidar la ruta permite un replan acotado.
                    route = null;
                    routeIndex = 0;
                    lastRouteSignature = 0;
                    globalCache = null;
                    NavPlanner.cancelGlobalSearch(globalSearchHandle);
                    globalSearchStart = null;
                    globalSearchLastExpansions = 0;
                    globalDesde = null;
                    state = NavState.PLANNING;
                    return;
                }
                intentosDeDescenso++;
                if (intentosDeDescenso > MAX_INTENTOS_DESCENSO) {
                    fallosDeDescenso++;
                    ultimaAccionDeRuta = node.pos().toShortString() + ":DROP-SIN-ATERRIZAR tras "
                            + MAX_INTENTOS_DESCENSO + " intentos (salida=" + descenso.salida().toShortString() + ")";
                    lastMoveTarget = node.pos().toShortString() + ":DROP-SIN-ATERRIZAR";
                    state = NavState.OBSTRUCTED;
                    return;
                }
                ultimaAccionDeRuta = node.pos().toShortString() + ":DROP-EN-CURSO (salida="
                        + descenso.salida().toShortString() + " dy=" + descenso.dy()
                        + (descenso.aterrizajeConstruido() ? " construido)" : " natural)");
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_START", "uuid=" + mob.getUUID() + " idxRuta=" + routeIndex
                            + " aterrizaje=" + node.pos().toShortString() + " salida=" + descenso.salida().toShortString()
                            + " dy=" + descenso.dy() + " construido=" + descenso.aterrizajeConstruido()
                            + " originPlan=" + (origenPlan == null ? "NONE" : origenPlan.toShortString())
                            + " grounded=" + mob.onGround()
                            + " liveFeetValid=" + esPieValido(mob.blockPosition())
                            + " liveSupport=" + NavObstruction.hasSupport(mob.level(), mob.blockPosition())
                            + " liveAabbFits=" + NavObstruction.fitsActualAabb(mob.level(), mob));
                }
                traceDropDecision("DROP_COMMAND",
                        "takeoff=" + descenso.salida().toShortString()
                                + " attempts=" + intentosDeDescenso + "/" + MAX_INTENTOS_DESCENSO
                                + " distanceToLanding=" + String.format(java.util.Locale.ROOT, "%.3f",
                                Math.sqrt(mob.distanceToSqr(node.pos().getX() + 0.5D,
                                        node.pos().getY() + 0.5D, node.pos().getZ() + 0.5D))),
                        mob.level().getGameTime());
                // Descenso: avanzar por una celda de aproximación soportada antes de apuntar al
                // landing. Un moveTo directo al centro del landing puede cortar la esquina y cruzar
                // una columna sin soporte aunque la ruta aceptada contenga intermedios válidos.
                BlockPos approachCurrent = mob.blockPosition();
                BlockPos approachWaypoint = NavDropApproachPolicy.choose(approachCurrent, node.pos(),
                        cell -> esPieValido(cell)
                                && NavObstruction.hasSupport(mob.level(), cell)
                                && NavObstruction.fitsAtCellCenter(mob.level(), mob, cell));
                if (approachWaypoint == null) {
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_APPROACH_PATH_ABORT", "uuid=" + mob.getUUID()
                                + " tick=" + mob.level().getGameTime()
                                + " current=" + approachCurrent.toShortString()
                                + " landing=" + node.pos().toShortString()
                                + " reason=no_supported_approach_cell");
                    }
                    state = NavState.OBSTRUCTED;
                    lastMoveTarget = node.pos().toShortString() + ":DROP-SIN-APROXIMACION";
                    return;
                }
                boolean alreadyAdjacent = approachWaypoint.equals(approachCurrent);
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_APPROACH_WAYPOINT_SELECTED", "uuid=" + mob.getUUID()
                            + " tick=" + mob.level().getGameTime()
                            + " current=" + approachCurrent.toShortString()
                            + " waypoint=" + approachWaypoint.toShortString()
                            + " landing=" + node.pos().toShortString()
                            + " alreadyAdjacent=" + alreadyAdjacent);
                    NavTrace.ev("DROP_TAKEOFF_APPROACH_SELECTED", "uuid=" + mob.getUUID()
                            + " tick=" + mob.level().getGameTime()
                            + " takeoff=" + approachWaypoint.toShortString()
                            + " landing=" + node.pos().toShortString()
                            + " horizontal=" + (Math.abs(approachCurrent.getX() - node.pos().getX())
                                    + Math.abs(approachCurrent.getZ() - node.pos().getZ())));
                }
                if (alreadyAdjacent) {
                    int horizontalDistance = Math.abs(approachCurrent.getX() - node.pos().getX())
                            + Math.abs(approachCurrent.getZ() - node.pos().getZ());
                    boolean fitsLanding = NavObstruction.fits(mob.level(), mob, node.pos());
                    boolean supportLanding = NavObstruction.hasSupport(mob.level(), node.pos());
                    boolean takeoffOk = esPieValido(approachCurrent)
                            && NavObstruction.hasSupport(mob.level(), approachCurrent)
                            && NavObstruction.fitsAtCellCenter(mob.level(), mob, approachCurrent);
                    if (NavDropEdgeStepPolicy.shouldStart(node.transition() == NavRoute.Transition.DROP,
                            mob.getNavigation().isDone(), mob.onGround(),
                            approachCurrent.equals(node.pos()), horizontalDistance,
                            NavDropEdgeStepPolicy.MAX_HORIZONTAL, takeoffOk, takeoffOk,
                            supportLanding, fitsLanding)) {
                        activateDropEdgeStep(approachCurrent, node.pos());
                        return;
                    }
                    mob.getNavigation().stop();
                    boolean nudge = NavAdjacentApproachPolicy.shouldNudgeDrop(
                            mob.getNavigation().isDone(), horizontalDistance, 3,
                            fitsLanding, supportLanding);
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_TAKEOFF_APPROACH_REACHED", "uuid=" + mob.getUUID()
                                + " tick=" + mob.level().getGameTime()
                                + " waypoint=" + approachWaypoint.toShortString()
                                + " landing=" + node.pos().toShortString()
                                + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                        mob.getX(), mob.getY(), mob.getZ())
                                + " horizontal=" + horizontalDistance
                                + " fits=" + fitsLanding + " support=" + supportLanding);
                    }
                    if (nudge) {
                        mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 0.9D);
                        dropDescentCommanded = true;
                        dropDescentStarted = false;
                        dropDescentStalledReported = false;
                        dropDescentStartY = mob.getY();
                        dropDescentCommandTick = mob.level().getGameTime();
                        if (NavTrace.on()) {
                            NavTrace.ev("DROP_DESCENT_COMMAND", "uuid=" + mob.getUUID()
                                    + " tick=" + mob.level().getGameTime()
                                    + " takeoff=" + approachWaypoint.toShortString()
                                    + " landing=" + node.pos().toShortString()
                                    + " horizontal=" + horizontalDistance
                                    + " dy=" + (node.pos().getY() - approachCurrent.getY())
                                    + " grounded=" + mob.onGround());
                        }
                    } else if (NavTrace.on()) {
                        NavTrace.ev("DROP_TAKEOFF_APPROACH_REJECTED", "uuid=" + mob.getUUID()
                                + " tick=" + mob.level().getGameTime()
                                + " waypoint=" + approachWaypoint.toShortString()
                                + " landing=" + node.pos().toShortString()
                                + " horizontal=" + horizontalDistance
                                + " fits=" + fitsLanding + " support=" + supportLanding
                                + " reason=unsafe_takeoff_or_landing");
                    }
                } else {
                    boolean pathAccepted = mob.getNavigation().moveTo(approachWaypoint.getX() + 0.5D, mob.getY(),
                            approachWaypoint.getZ() + 0.5D, 0.9D);
                    beginDropApproachTracking(approachWaypoint, node.pos());
                    if (NavTrace.on()) {
                        NavTrace.ev("DROP_APPROACH_WAYPOINT_COMMAND", "uuid=" + mob.getUUID()
                                + " tick=" + mob.level().getGameTime()
                                + " waypoint=" + approachWaypoint.toShortString()
                                + " landing=" + node.pos().toShortString()
                                + " pathAccepted=" + pathAccepted
                                + " navDone=" + mob.getNavigation().isDone()
                                + " exact=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
                                        mob.getX(), mob.getY(), mob.getZ()));
                        NavTrace.ev("DROP_TAKEOFF_APPROACH_COMMAND", "uuid=" + mob.getUUID()
                                + " tick=" + mob.level().getGameTime()
                                + " takeoff=" + approachWaypoint.toShortString()
                                + " landing=" + node.pos().toShortString());
                    }
                    if (!pathAccepted) {
                        mob.getNavigation().stop();
                    }
                    if (!pathAccepted || mob.getNavigation().isDone()) {
                        tickDropApproachOwner(node.pos(), mob.level().getGameTime());
                        return;
                    }
                }
                traceActuatorCommand("DROP_APPROACH", BlockPos.containing(aim), mob.level().getGameTime());
                trazarComandoMovimiento(node, "DROP", aim.x, aim.y, aim.z, 0.9D);
            }
            case BUILD -> {
                // BUILD node.pos is the resulting feet cell; actionPos is the exact placement cell below it.
                BlockPos placementTarget = node.actionPos();
                if (placementTarget == null) {
                    state = NavState.OBSTRUCTED;
                    lastMoveTarget = node.pos().toShortString() + ":BUILD-SIN-ACTION-POS";
                    return;
                }
                boolean placementVerified = construidas.contains(placementTarget)
                        && !mob.level().getBlockState(placementTarget).isAir();
                if (NavBuildTransitionPolicy.shouldPlace(placementVerified)) {
                    // An unexpected solid cell is not accepted as our BUILD result.
                    if (!mob.level().getBlockState(placementTarget).isAir()) {
                        state = NavState.OBSTRUCTED;
                        lastMoveTarget = placementTarget.toShortString() + ":BUILD-CELDA-OCUPADA";
                        return;
                    }
                    if (!tryAcquireTraversalAction(NavRoute.Transition.BUILD, placementTarget)) {
                        state = NavState.OBSTRUCTED;
                        lastMoveTarget = placementTarget.toShortString() + ":BUILD-LEASE-RECHAZADO";
                        return;
                    }
                    if (placement == null || !placement.place(placementTarget)) {
                        lastMoveTarget = placementTarget.toShortString() + ":BUILD-FALLIDO";
                        state = NavState.OBSTRUCTED;
                        return;
                    }
                    if (mob.level().getBlockState(placementTarget).isAir()) {
                        lastMoveTarget = placementTarget.toShortString() + ":BUILD-NO-VERIFICADO";
                        state = NavState.OBSTRUCTED;
                        return;
                    }
                    construidas.add(placementTarget.immutable());
                    bloquesColocadosVerificados++;
                    ultimoConstruido = placementTarget.toShortString();
                    ultimoConstruidoTick = mob.level().getGameTime();
                    placementVerified = true;
                    lastMoveTarget = placementTarget.toShortString() + ":BUILD-VERIFICADO";
                }
                // Placement is not traversal: move to the resulting feet cell and keep this route index
                // until nodoAlcanzado confirms exact grounded occupancy and the real AABB.
                if (!placementVerified || !NavObstruction.fits(mob.level(), mob, node.pos())
                        || !NavObstruction.hasSupport(mob.level(), node.pos())) {
                    state = NavState.OBSTRUCTED;
                    lastMoveTarget = placementTarget.toShortString() + ":BUILD-POSTSTATE-INVALID";
                    return;
                }
                mob.getNavigation().moveTo(node.pos().getX() + 0.5D, node.pos().getY(),
                        node.pos().getZ() + 0.5D, 1.0D);
                traceActuatorCommand("BUILD_FEET_APPROACH", node.pos(), mob.level().getGameTime());
                lastMoveTarget = node.pos().toShortString() + ":BUILD-ACERCAR-PIES";
                return;
            }
            case BREAK -> {
                ejecutarBreakDesdePosicionDeAtaque(node, now);
                return;
            }
            case BRIDGE -> {
                // A2 Stage 6E · FIX B · LA TRANSICION ACTUA SOBRE SU NODO, NO SOBRE LA MISION LEJANA.
                // Medido en `a2n-20261016-170218`: la ruta empezaba en `0:BREAK@120,84,-9` y el ejecutor se limitaba
                // a pasar a RECOVERING; la recuperacion solo recibia el OBJETIVO, asi que ese nodo NUNCA se rompia y
                // el ente se quedaba en `idx=0` enviando `moveTo` sin ejecutar la transicion (10 envios, 0 avance).
                // Primero comprobar el estado REAL del bloque. Si BREAK ya quedó en aire, o BRIDGE
                // ya quedó sólido, avanzar sin exigir que el mob alcance una celda que ya no es objetivo.
                BlockPos actionPos = node.transition() == NavRoute.Transition.BREAK
                        ? node.actionTarget() : node.pos();
                boolean targetIsAir = mob.level().getBlockState(actionPos).isAir();
                if (NavObjectivePolicy.accionResuelta(node.transition() == NavRoute.Transition.BREAK, targetIsAir)) {
                    routeIndex++;
                    replanteosPorGuarda = 0;
                    // A verified BREAK changes the terrain. The old local objective may still
                    // point above the attacker after the broken block is gone; force a fresh
                    // local selection instead of spending the no-progress budget on it.
                    objetivoPermiteConstruccion = false;
                    objetivoTransicionLocal = null;
                    objetivoAccionLocal = null;
                    objective = misionFinal == null ? null : misionFinal.immutable();
                    plannedFrom = null;
                    globalCache = null;
                    NavPlanner.cancelGlobalSearch(globalSearchHandle);
                    globalSearchStart = null;
                    globalSearchLastExpansions = 0;
                    globalDesde = null;
                    state = NavState.PLANNING;
                    ultimaAccionDeRuta = actionPos.toShortString() + ":" + node.transition()
                            + "-YA-RESUELTO-REPLAN";
                    if (NavTrace.on()) {
                        NavTrace.ev("VERTICAL_TRANSITION_FAILED", "uuid=" + mob.getUUID()
                                + " tick=" + mob.level().getGameTime() + " transition=" + node.transition()
                                + " target=" + node.pos().toShortString() + " reason=BREAK_VERIFIED_REPLAN");
                    }
                    return;
                }
                // A2 · el nodo BREAK moderno es una POSICION DE PIE; actionPos es el bloque solido.
                if (node.actionPos() != null) {
                    double distStandSqr = mob.distanceToSqr(node.pos().getX() + 0.5D,
                            node.pos().getY() + 0.5D, node.pos().getZ() + 0.5D);
                    if (distStandSqr > 1.0D) {
                        mob.getNavigation().moveTo(node.pos().getX() + 0.5D, node.pos().getY(),
                                node.pos().getZ() + 0.5D, 1.0D);
                        traceActuatorCommand("BREAK_STAND_APPROACH", node.pos(), mob.level().getGameTime());
                        lastMoveTarget = node.pos().toShortString() + ":ACERCARSE-STAND";
                        ultimaAccionDeRuta = node.pos().toShortString() + ":BREAK-ACERCARSE-STAND (dist="
                                + String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distStandSqr)) + ")";
                        if (mob.getNavigation().isDone() && ticksSinAvance > TICKS_DE_GRACIA_DE_NODO) {
                            state = NavState.OBSTRUCTED;
                        }
                        return;
                    }
                    BlockPos blocker = primerBloqueoEnLineaDeVision(actionPos);
                    boolean reach = mob.distanceToSqr(actionPos.getX() + 0.5D, actionPos.getY() + 0.5D,
                            actionPos.getZ() + 0.5D) <= ALCANCE_DE_ACCION_SQR;
                    boolean authorized = mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az
                            && az.puedeRomperNodoDeNavegacion(actionPos);
                    if (!reach || !authorized || (blocker != null && !blocker.equals(actionPos))) {
                        state = NavState.OBSTRUCTED;
                        lastMoveTarget = node.pos().toShortString() + ":BREAK-PRECONDICION-FALLIDA";
                        ultimaAccionDeRuta = node.pos().toShortString() + ":BREAK-RECHAZADO action="
                                + actionPos.toShortString() + " reach=" + reach + " authorized=" + authorized
                                + " blocker=" + (blocker == null ? "-" : blocker.toShortString());
                        return;
                    }
                    nodoDeRecuperacionOverride = actionPos.immutable();
                    nodoDeRecuperacion = actionPos.immutable();
                    nextRecoveryAt = Math.min(nextRecoveryAt, mob.level().getGameTime());
                    state = NavState.RECOVERING;
                    ultimaAccionDeRuta = node.pos().toShortString() + ":BREAK-DELEGADO action="
                            + actionPos.toShortString();
                    return;
                }
                double distNodoSqr = mob.distanceToSqr(node.pos().getX() + 0.5D, node.pos().getY() + 0.5D,
                        node.pos().getZ() + 0.5D);
                if (distNodoSqr > ALCANCE_DE_ACCION_SQR) {
                    // Aun NO esta al alcance del nodo: se CAMINA HACIA EL NODO (no hacia la mision). Si la
                    // navegacion no puede acercarlo, se declara obstruido y decide la recuperacion acotada.
                    mob.getNavigation().moveTo(node.pos().getX() + 0.5D, node.pos().getY(),
                            node.pos().getZ() + 0.5D, 1.0D);
                    traceActuatorCommand("BREAK_APPROACH", node.pos(), mob.level().getGameTime());
                    tracePathTransaction(node.pos(), mob.level().getGameTime());
                    trazarComandoMovimiento(node, "ACERCARSE-ACCION", node.pos().getX() + 0.5D,
                            node.pos().getY(), node.pos().getZ() + 0.5D, 1.0D);
                    moveSubmissions++;
                    // PathNavigation no puede caminar hasta un bloque BREAK sólido. Si el Path no llega,
                    // delegamos el primer bloque del raycast (el objetivo mismo o una obstrucción anterior)
                    // al RecoveryAction existente; BlockInteractionPolicy sigue autorizando la rotura.
                    net.minecraft.world.level.pathfinder.Path approachPath = mob.getNavigation().getPath();
                    boolean pathCanReach = approachPath != null && approachPath.canReach();
                    BlockPos blocker = node.transition() == NavRoute.Transition.BREAK
                            ? primerBloqueoEnLineaDeVision(node.pos()) : null;
                    boolean blockerInReach = blocker != null && mob.distanceToSqr(
                            blocker.getX() + 0.5D, blocker.getY() + 0.5D, blocker.getZ() + 0.5D)
                            <= ALCANCE_DE_ACCION_SQR;
                    boolean debeDelegar = NavActionApproachPolicy.debeDelegarPrimerBloque(
                            pathCanReach, blockerInReach, node.transition() == NavRoute.Transition.BREAK);
                    long breakNow = mob.level().getGameTime();
                    String decision = distNodoSqr <= ALCANCE_DE_ACCION_SQR
                            ? "ACTION_RANGE_REACHED"
                            : pathCanReach ? "PATH_CAN_REACH"
                            : recoveryAction == null ? "RECOVERY_ACTION_UNAVAILABLE"
                            : blocker == null ? "BLOCKER_NULL"
                            : !blockerInReach ? "BLOCKER_OUT_OF_REACH"
                            : !debeDelegar ? "DELEGATION_POLICY_REJECTED"
                            : "DELEGATION_ACCEPTED";
                    traceBreakDecision(decision,
                            "target=" + node.pos().toShortString() + " transition=" + node.transition()
                                    + " distSqr=" + String.format(java.util.Locale.ROOT, "%.4f", distNodoSqr)
                                    + " actionRangeSqr=" + ALCANCE_DE_ACCION_SQR
                                    + " pathNull=" + (approachPath == null)
                                    + " pathCanReach=" + pathCanReach
                                    + " pathDone=" + mob.getNavigation().isDone()
                                    + " blocker=" + (blocker == null ? "-" : blocker.toShortString())
                                    + " blockerState=" + (blocker == null ? "-" : mob.level().getBlockState(blocker))
                                    + " blockerDistSqr=" + (blocker == null ? "-"
                                    : String.format(java.util.Locale.ROOT, "%.4f",
                                    mob.distanceToSqr(blocker.getX() + 0.5D, blocker.getY() + 0.5D,
                                            blocker.getZ() + 0.5D)))
                                    + " blockerInReach=" + blockerInReach
                                    + " policyDelegate=" + debeDelegar
                                    + " recoveryAction=" + (recoveryAction != null)
                                    + " idxRuta=" + routeIndex + "/"
                                    + (route == null ? "-" : route.length()) + " state=" + state, breakNow);
                    if (debeDelegar) {
                            nodoDeRecuperacionOverride = blocker.immutable();
                            nodoDeRecuperacion = blocker.immutable();
                            mob.getNavigation().stop();
                            nextRecoveryAt = Math.max(nextRecoveryAt, mob.level().getGameTime());
                            state = NavState.RECOVERING;
                            lastMoveTarget = node.pos().toShortString() + ":BREAK-FIRST-HIT-" + blocker.toShortString();
                            ultimaAccionDeRuta = node.pos().toShortString() + ":BREAK-PRIMER-BLOQUE-DELEGADO "
                                    + blocker.toShortString();
                            if (NavTrace.on()) {
                                NavTrace.ev("BREAK_APPROACH_OCCLUSION", "uuid=" + mob.getUUID()
                                        + " tick=" + mob.level().getGameTime()
                                        + " target=" + node.pos().toShortString()
                                        + " blocker=" + blocker.toShortString()
                                        + " targetHit=" + blocker.equals(node.pos())
                                        + " pathCanReach=" + pathCanReach
                                        + " blockerDistance=" + String.format(java.util.Locale.ROOT, "%.2f",
                                                Math.sqrt(mob.distanceToSqr(blocker.getX() + 0.5D,
                                                        blocker.getY() + 0.5D, blocker.getZ() + 0.5D))));
                            }
                            return;
                        }
                    lastMoveTarget = node.pos().toShortString() + ":ACERCARSE-AL-NODO";
                    ultimaAccionDeRuta = node.pos().toShortString() + ":" + node.transition()
                            + "-ACERCARSE (dist=" + String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distNodoSqr)) + ")";
                    if (mob.getNavigation().isDone() && ticksSinAvance > TICKS_DE_GRACIA_DE_NODO) {
                        state = NavState.OBSTRUCTED;
                    }
                    return;
                }
                nodoDeRecuperacion = node.pos().immutable();
                ultimaAccionDeRuta = node.pos().toShortString() + ":" + node.transition() + "-DELEGADO (bloque="
                        + mob.level().getBlockState(node.pos()).getBlock().getName().getString() + ")";
                state = NavState.RECOVERING;
            }
            default -> {
                mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0D);
                aplicarAcercamientoAdyacente(node, aim, 1.0D);
                trazarComandoMovimiento(node, "DEFAULT", aim.x, aim.y, aim.z, 1.0D);
            }
        }
        // A4.2 · RUTA STALE: si vanilla termino y no hay avance hacia un nodo no-BREAK/BRIDGE, no se
        // sigue enviando el mismo nodo ni se deja que la guarda de recuperacion consuma sus 6 intentos
        // contra el objetivo lejano. Se invalida SOLO la ruta local; el planificador existente decide
        // desde la posicion actual. BREAK/BRIDGE siguen su rama normal.
        if (NavRouteInvalidationPolicy.debeInvalidar(route != null, mob.getNavigation().isDone(),
                ticksSinAvance, TICKS_DE_GRACIA_DE_NODO, node.transition())) {
            if (recuperacionOrigenDropActiva) {
                int suspendedSignature = rutaDropSuspendida == null ? route.signature()
                        : rutaDropSuspendida.signature();
                if (NavTrace.on()) {
                    NavTrace.ev("DROP_TAKEOFF_RECOVERY_ROUTE_STALLED", "uuid=" + mob.getUUID()
                            + " takeoff=" + objetivoRecuperacionOrigenDrop
                            + " node=" + node.pos().toShortString()
                            + " transition=" + node.transition()
                            + " pos=" + mob.blockPosition().toShortString()
                            + " ticksSinAvance=" + ticksSinAvance);
                }
                limpiarRecuperacionOrigenDrop();
                route = null;
                routeIndex = 0;
                plannedFrom = null;
                lastRouteSignature = suspendedSignature;
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalMision = null;
                globalDesde = null;
                nodoSeguido = null;
                mejorDistAlNodo = Double.MAX_VALUE;
                ticksSinAvance = 0;
                nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
                state = NavState.PLANNING;
                return;
            }
            // A2 Stage 7E · DIAGNOSTICO DE LA PRIMERA OBSTRUCCION (instrumento acotado, una cadena):
            // se registra el nodo activo, la orden enviada, la posicion, la distancia, el estado real de la
            // navegacion de vanilla (isDone / path nulo / en curso) y si la arista es valida segun el modelo.
            ultimaObstruccionDiag = "PREDICADO=A4.2(isDone && sinAvance && trans!=BREAK/BRIDGE)"
                    + " nodo=" + node.pos().toShortString() + ":" + node.transition()
                    + " idx=" + routeIndex + "/" + route.length()
                    + " pos=" + mob.blockPosition().toShortString()
                    + " posExacta=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                            mob.getX(), mob.getY(), mob.getZ())
                    + " distNodo=" + String.format(java.util.Locale.ROOT, "%.2f",
                            Math.sqrt(mob.distanceToSqr(node.pos().getX() + 0.5D, node.pos().getY(),
                                    node.pos().getZ() + 0.5D)))
                    + " isDone=" + mob.getNavigation().isDone()
                    + " pathNull=" + (mob.getNavigation().getPath() == null)
                    + " enCurso=" + mob.getNavigation().isInProgress()
                    + " moveTarget=" + lastMoveTarget
                    + " envios=" + moveSubmissions
                    + " ticksSinAvance=" + ticksSinAvance
                    + " aristaValida=" + esAristaValidaHacia(node)
                    + " pieDestino=" + esPieValido(node.pos());
            // First failure replans from the real position; the second may backtrack once.
            if (intentarBacktrackTrasFallo(now, "ruta sin avance")) return;
            // La firma vieja queda registrada para que el planificador no acepte la misma ruta ya fallida.
            lastRouteSignature = route.signature();
            route = null;
            routeIndex = 0;
            nodoDeRecuperacion = null;
            nodoSeguido = null;
            mejorDistAlNodo = Double.MAX_VALUE;
            ticksSinAvance = 0;
            nextPlanAt = 0L;
            state = NavState.PLANNING;
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} RUTA INVALIDADA por falta de avance en {}:{}; se replanifica",
                    mob.getId(), node.pos().toShortString(), node.transition());
        }
    }

    /**
     * A2 Stage 7E · ¿la arista hacia este nodo sigue siendo ejecutable en el mundo AHORA? Sirve para separar
     * "arista invalida" de "deteccion de obstruccion equivocada": usa las mismas comprobaciones que el modelo
     * (posicion de pie, soporte, holgura) sin inventar nada nuevo.
     */
    private boolean esAristaValidaHacia(final NavRoute.Node node) {
        BlockPos c = node.pos();
        return switch (node.transition()) {
            case DROP -> {
                Descenso d = describirDescenso(c, construidas.contains(c.below()), true);
                yield d.valido();
            }
            case BUILD -> !mob.level().getBlockState(c).isAir()
                    || (mob.level().getBlockState(c).isAir() && mob.level().getBlockState(c.above()).isAir());
            case BREAK -> NavBreakActionPolicy.executable(mob.level(), mob, mob.blockPosition(), c, policies,
                    ALCANCE_DE_ACCION_SQR);
            case BRIDGE -> true;
            default -> esPieValido(c);
        };
    }

    /** A2 Stage 7E · diagnostico de la ultima obstruccion declarada por el predicado de `traverse`. */
    public String ultimaObstruccionDiag() {
        return ultimaObstruccionDiag;
    }

    /**
     * A2 Stage 7F · ¿esta celda pertenece a la RUTA ACTIVA (nodo pendiente, su celda de pie superior o su soporte
     * inmediato)? La usan las acciones GENERICAS de recuperacion para no invalidar la ruta que el plan esta
     * ejecutando. NO afecta a las acciones legitimas de la propia ruta (BUILD por `ponerEscalonConSoporteLateral`
     * ni BREAK por `romperNodoDeRuta`), que no consultan este predicado.
     */
    public boolean celdaDeRutaActiva(final BlockPos c) {
        if (route == null || c == null) {
            return false;
        }
        for (int i = Math.max(0, routeIndex); i < route.length(); i++) {
            NavRoute.Node n = route.currentNode(i);
            if (n == null) {
                break;
            }
            BlockPos p = n.pos();
            if (p.equals(c) || p.above().equals(c) || p.below().equals(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A2 Stage 7G · ¿la recuperacion generica esta siendo rechazada por la guarda de ruta? Se comprueba sobre la
     * celda que la recuperacion iba a tratar (nodo de recuperacion o, en su defecto, el objetivo).
     */
    private boolean rechazoPorGuardaDeRuta() {
        BlockPos celdaDeRecuperacion = nodoDeRecuperacion != null ? nodoDeRecuperacion : objective;
        if (celdaDeRecuperacion == null) {
            return false;
        }
        // A2 Stage 7H · la acción del propio nodo BREAK/BRIDGE conserva su exención histórica.
        NavRoute.Node activo = route == null ? null : route.currentNode(routeIndex);
        boolean exencionNodoActivo = activo != null && activo.pos().equals(celdaDeRecuperacion)
                && (activo.transition() == NavRoute.Transition.BREAK
                || activo.transition() == NavRoute.Transition.BRIDGE);
        // Run27: el primer bloque opaco puede ser vecino del nodo BREAK y, por ello, también
        // pertenecer a la huella de la ruta activa. Solo el override exacto delegado como BREAK
        // queda exento; la RecoveryAction/BlockInteractionPolicy sigue decidiendo si puede romperse.
        boolean exencionOverrideBreak = nodoDeRecuperacionOverride != null
                && nodoDeRecuperacionOverride.equals(celdaDeRecuperacion);
        boolean celdaActiva = celdaDeRutaActiva(celdaDeRecuperacion);
        return NavRecoveryGuardPolicy.debeRechazar(celdaActiva, exencionNodoActivo, exencionOverrideBreak);
    }

    /** A2 Stage 7G · replanteos consumidos por rechazo de guarda (diagnostico). */
    /** A2 Stage 7H · resumen del ULTIMO plan local (ruta/motivo) para el diagnostico del tramo final. */
    public String ultimoPlanResumen() {
        return lastPlanSummary;
    }

    public int replanteosPorGuarda() {
        return replanteosPorGuarda;
    }

    /**
     * A4.1 · Diagnostico de SOLO LECTURA de la decision de guarda de ruta. No cambia estado ni comportamiento:
     * expone el nodo activo, el nodo de recuperacion efectivo, la exencion BREAK/BRIDGE, el fallback al objetivo
     * y los contadores que deciden el fallo. Se usa desde el laboratorio opt-in.
     */
    public String guardaDiag() {
        NavRoute.Node activo = route == null ? null : route.currentNode(routeIndex);
        BlockPos recuperacion = nodoDeRecuperacion != null ? nodoDeRecuperacion : objective;
        boolean exencionNodoActivo = activo != null && recuperacion != null
                && activo.pos().equals(recuperacion)
                && (activo.transition() == NavRoute.Transition.BREAK
                || activo.transition() == NavRoute.Transition.BRIDGE);
        boolean exencionOverrideBreak = nodoDeRecuperacionOverride != null && recuperacion != null
                && nodoDeRecuperacionOverride.equals(recuperacion);
        boolean exencionBreak = exencionNodoActivo || exencionOverrideBreak;
        boolean celdaActiva = recuperacion != null && celdaDeRutaActiva(recuperacion);
        boolean rechaza = rechazoPorGuardaDeRuta();
        return "activo=" + (activo == null ? "-" : activo.pos().toShortString() + ":" + activo.transition())
                + " idx=" + routeIndex
                + " sig=" + (route == null ? "-" : String.valueOf(route.signature()))
                + " recovery=" + (recuperacion == null ? "-" : recuperacion.toShortString())
                + " objetivo=" + (objective == null ? "-" : objective.toShortString())
                + " lastMove=" + lastMoveTarget
                + " ticksSinAvance=" + ticksSinAvance
                + " retriesGuarda=" + replanteosPorGuarda + "/" + MAX_REPLANTEOS_POR_GUARDA
                + " exencionBreak=" + exencionBreak
                + " exencionOverrideBreak=" + exencionOverrideBreak
                + " celdaActiva=" + celdaActiva
                + " rechaza=" + rechaza;
    }

    /** A2 Stage 7E · cuantas veces el predicado de `traverse` declaro OBSTRUCTED. */
    public int obstruccionesPorPredicado() {
        return obstruccionesPorPredicado;
    }

    private void recover(long now) {
        traceRecoverDecision("ENTER",
                "pos=" + mob.blockPosition().toShortString()
                        + " state=" + state + " objective=" + objective
                        + " recoveryNode=" + nodoDeRecuperacion
                        + " override=" + nodoDeRecuperacionOverride
                        + " nextRecoveryAt=" + nextRecoveryAt + " now=" + now
                        + " recoveryActions=" + recoveryActions + "/" + MAX_RECOVERY_ACTIONS
                        + " routeIdx=" + routeIndex + " routeLen=" + (route == null ? -1 : route.length()), now);
        if (now < nextRecoveryAt) {
            traceRecoverDecision("WAIT", "remaining=" + (nextRecoveryAt - now)
                    + " nextRecoveryAt=" + nextRecoveryAt + " now=" + now, now);
            return;
        }
        // A2 Stage 6A · EL PLAN DE CONSTRUCCION VA ANTES DEL TOPE DE RECUPERACION. Medido en
        // 2n-20261016-121208: el tope MAX_RECOVERY_ACTIONS se alcanzaba (rec=6) y el ejecutor se rendia
        // (FAILED) sin haber intentado construir NUNCA. Construir no es "recuperar": es la accion que hace
        // transitable un desnivel, asi que no consume el presupuesto de recuperacion.
        // A2 Stage 6D · FIX 4 · CICLO DE VIDA PROPIO DE LA CONSTRUCCION.
        // Mientras la mision exija descenso y quede presupuesto de construccion, el sub-plan recibe oportunidades
        // repetidas SIN consumir el tope generico de recuperacion. Al agotar SU presupuesto el fallo es terminal y
        // explicito, de modo que no se oscila indefinidamente con el mismo fallo. El tope generico sigue vigente en
        // cuanto no hay construccion pendiente o su presupuesto se ha agotado.
        boolean tocaConstruir = planMision != null && mob.blockPosition().getY() > planMision.getY()
                && !construccionAgotada && !descensoBloqueado && nodoDeRecuperacionOverride == null;
        if (tocaConstruir) {
            if (pasoDeConstruccion()) {
                recoveryCooldown = RECOVERY_COOLDOWN_TICKS;
                nextRecoveryAt = now + recoveryCooldown;
                state = NavState.PLANNING;
                nextPlanAt = 0L;
                return;
            }
            if (construccionAgotada) {
                fallar("construccion agotada sin completar el descenso");
                return;
            }
            // A2 Stage 6F · MIENTRAS HAY UN ESCALÓN COLOCADO ESPERANDO TRAVESÍA, EL PLAN NO SE PISA. Medido en
            // `a2n-20261016-183226`: la recuperación genérica (el puente de la casa) tendió un tablón en `118,84,-8`
            // y tapó el camino de caída del escalón que el plan acababa de colocar (`118,81,-8`, aterrizaje
            // `118,82,-8`), dejando 36 rechazos y un FAILED sin destino. Es acotado: si el descenso de ese escalón
            // se declara BLOQUEADO (`descensoBloqueado`), esta rama deja de aplicar y el ejecutor sigue con la ruta.
            if (faseEscalon == FaseEscalon.PLACED_VERIFIED && escalonActual != null && !descensoBloqueado) {
                nextRecoveryAt = now + RECOVERY_COOLDOWN_TICKS;
                return;
            }
            // NO SE ESTRANGULA EL RESTO DE LA RECUPERACION: una acción genérica acotada, sin saltarse su tope.
            if (recoveryActions < MAX_RECOVERY_ACTIONS) {
                boolean acted = attemptOneRecovery();
                recoveryActions++;
                recoveriesRun++;
                if (acted) {
                    recoveryCooldown = RECOVERY_COOLDOWN_TICKS;
                } else {
                    recoveryCooldown = Math.min(MAX_RECOVERY_COOLDOWN_TICKS, recoveryCooldown * 2);
                }
            }
            nextRecoveryAt = now + recoveryCooldown;
            route = null;
            state = NavState.PLANNING;
            nextPlanAt = 0L;
            return;
        }
        // ==========================================================================================
        // A2 Stage 7G · RECHAZO POR GUARDA != RUTA BLOQUEADA. Si la recuperacion generica iba a tocar un nodo de la
        // ruta activa, la guarda devuelve `false`, la accion no hace nada y el codigo antiguo gastaba el presupuesto de
        // recuperacion hasta FAILED (medido en Stage 7F: `recuperacion agotada (6 acciones) en 886,49,544`). Ese
        // rechazo no prueba nada sobre la ruta: se invalida la ruta local y se replanifica, con tope propio.
        // ==========================================================================================
        if (NavTrace.on()) {
            NavTrace.ev("GUARD_STATE", "uuid=" + mob.getUUID() + " pos=" + mob.blockPosition().toShortString()
                    + " obj=" + (objective == null ? "-" : objective.toShortString())
                    + " mission=" + (misionFinal == null ? "-" : misionFinal.toShortString())
                    + " state=" + state + " " + guardaDiag());
        }
        if (rechazoPorGuardaDeRuta()) {
            traceRecoverDecision("GUARD_REJECT", "pos=" + mob.blockPosition().toShortString()
                    + " retries=" + replanteosPorGuarda + "/" + MAX_REPLANTEOS_POR_GUARDA
                    + " " + guardaDiag(), now);
            if (replanteosPorGuarda < MAX_REPLANTEOS_POR_GUARDA) {
                replanteosPorGuarda++;
                if (NavTrace.on()) {
                    NavTrace.ev("GUARD_REPLAN", "uuid=" + mob.getUUID() + " retry=" + replanteosPorGuarda
                            + "/" + MAX_REPLANTEOS_POR_GUARDA + " pos=" + mob.blockPosition().toShortString()
                            + " " + guardaDiag());
                }
                route = null;
                routeIndex = 0;
                recoveryActions = 0;
                ticksSinAvance = 0;
                nextPlanAt = 0L;
                nextRecoveryAt = 0L;
                state = NavState.PLANNING;
                ultimoMotivoDeReentrada = "rechazo por guarda de ruta -> ruta local invalidada y replanificada ("
                        + replanteosPorGuarda + "/" + MAX_REPLANTEOS_POR_GUARDA + ")";
                com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} GUARDA DE RUTA: replanteo {}/{} en {}",
                        mob.getId(), replanteosPorGuarda, MAX_REPLANTEOS_POR_GUARDA,
                        mob.blockPosition().toShortString());
                return;
            }
            if (NavTrace.on()) {
                NavTrace.ev("GUARD_FAIL", "uuid=" + mob.getUUID() + " retry=" + replanteosPorGuarda
                        + "/" + MAX_REPLANTEOS_POR_GUARDA + " pos=" + mob.blockPosition().toShortString()
                        + " " + guardaDiag());
            }
            noPlanifica("guarda de ruta: replanteos agotados " + replanteosPorGuarda);
            fallar("guarda de ruta: replanteos agotados (" + replanteosPorGuarda + "/"
                    + MAX_REPLANTEOS_POR_GUARDA + ")");
            return;
        }
        if (recoveryActions >= MAX_RECOVERY_ACTIONS) {
            // ==========================================================================================
            // A2 Stage 7C · RECUPERACION AGOTADA EN UN TRAMO LARGO: SE RE-ESTADIA, NO SE RINDE.
            // Medido en `a2n-20261016-233616`: subiendo la rampa, la recuperacion generica coloco y pico bloques
            // del propio camino, agoto sus 6 acciones y el ente quedo en FAILED en `862,43,553` con la ruta
            // global todavia valida (94 nodos, `alcanzoMision=true`). Mientras la mision siga FUERA de la ventana
            // local y queden re-estadios, se invalida la ruta global (el terreno cambio) y se replanifica. Es
            // ACOTADO (`MAX_REESTADIOS_GLOBALES`) y deja motivo explicito, tambien al agotarse.
            // ==========================================================================================
            if (misionFinal != null && !dentroDeLaVentana(mob.blockPosition(), misionFinal)
                    && reestadiosGlobales < MAX_REESTADIOS_GLOBALES) {
                reestadiosGlobales++;
                recoveryActions = 0;
                globalCache = null;
                NavPlanner.cancelGlobalSearch(globalSearchHandle);
                globalSearchStart = null;
                globalSearchLastExpansions = 0;
                globalDesde = null;
                route = null;
                routeIndex = 0;
                nextPlanAt = 0L;
                nextRecoveryAt = 0L;
                recoveryCooldown = RECOVERY_COOLDOWN_TICKS;
                state = NavState.PLANNING;
                ultimoEscalonDeObjetivo = "re-estadio global " + reestadiosGlobales + "/"
                        + MAX_REESTADIOS_GLOBALES + " en " + mob.blockPosition().toShortString()
                        + " (recuperacion generica agotada)";
                com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} RE-ESTADIO GLOBAL {}/{} en {}",
                        mob.getId(), reestadiosGlobales, MAX_REESTADIOS_GLOBALES,
                        mob.blockPosition().toShortString());
                return;
            }
            // Tope de recuperación: se rinde con este destino de forma explícita.
            ultimoEscalonDeObjetivo = "recuperacion agotada (" + recoveryActions + " acciones) en "
                    + mob.blockPosition().toShortString() + " reestadios=" + reestadiosGlobales;
            fallar("recuperacion agotada (" + recoveryActions + "/" + MAX_RECOVERY_ACTIONS
                    + " acciones), reestadios=" + reestadiosGlobales + "/" + MAX_REESTADIOS_GLOBALES);
            return;
        }
        // Acción acotada: un intento por llamada, con enfriamiento creciente.
        traceRecoverDecision("ATTEMPT", "pos=" + mob.blockPosition().toShortString()
                + " recoveryActions=" + recoveryActions + "/" + MAX_RECOVERY_ACTIONS
                + " recoveryNode=" + nodoDeRecuperacion + " objective=" + objective, now);
        boolean acted = attemptOneRecovery();
        recoveryActions++;
        recoveriesRun++;
        if (acted) {
            recoveryCooldown = RECOVERY_COOLDOWN_TICKS;
        } else {
            recoveryCooldown = Math.min(MAX_RECOVERY_COOLDOWN_TICKS, recoveryCooldown * 2);
        }
        nextRecoveryAt = now + recoveryCooldown;
        // A2 Stage 4Q · NO se descarta la ruta por haber recuperado: si el terreno cambio (una celda construida),
        // se replanifica CONSERVANDO la geometria construida; pero no se marca la firma como "ya fallada", porque
        // eso impedia aceptar una ruta que ahora SI es valida gracias a lo construido.
        route = null;
        state = NavState.PLANNING;
        nextPlanAt = 0L;
    }

    /**
     * Una acción de recuperación concreta. Devuelve {@code true} si hizo algo. La implementación concreta (romper,
     * puentear, apilar) la inyecta quien construye el ejecutor, para que las reglas de la aldea sigan en su sitio.
     * <p>
     * Solo se delegan acciones {@code BREAK}/{@code BRIDGE} asociadas al nodo activo. La recuperación
     * genérica basada en el objetivo estratégico no tiene permiso para modificar terreno.
     */
    private boolean attemptOneRecovery() {
        if (recoveryAction == null) {
            return false;
        }
        BlockPos towards = objective;
        if (towards == null) {
            return false;
        }
        NavRoute.Node actual = route == null ? null : route.currentNode(routeIndex);
        BlockPos nodo = null;
        NavRoute.Transition transicion = null;
        if (nodoDeRecuperacionOverride != null) {
            nodo = nodoDeRecuperacionOverride;
            transicion = NavRoute.Transition.BREAK;
            nodoDeRecuperacionOverride = null;
        } else if (actual != null && (actual.transition() == NavRoute.Transition.BREAK
                || actual.transition() == NavRoute.Transition.BRIDGE)) {
            // BREAK acts on the solid target, never on the traversable attack stand.
            nodo = actual.transition() == NavRoute.Transition.BREAK
                    ? actual.actionTarget().immutable() : actual.pos().immutable();
            transicion = actual.transition();
        }
        // La recuperación genérica basada en el objetivo global ya no puede modificar terreno.
        // Toda invocación pasa por la misma puerta que el BREAK normal.
        if (nodo == null || transicion == null) {
            return false;
        }
        boolean acted = invokeTerrainAction(transicion, nodo, towards);
        if (nodo != null) {
            // VERIFICACION REAL: la accion no se da por buena por haber sido invocada. Se relee el mundo.
            boolean resuelto = transicion == NavRoute.Transition.BREAK
                    ? mob.level().getBlockState(nodo).isAir()
                    : !mob.level().getBlockState(nodo).isAir();
            ultimaAccionDeRuta = nodo.toShortString() + ":" + transicion
                    + (resuelto ? "-VERIFICADO" : "-NO-VERIFICADO")
                    + " accion=" + (acted ? "actuo" : "no-actuo");
            return resuelto;
        }
        return acted;
    }

    /**
     * Única puerta de invocación del callback de terreno. Ningún caller puede ejecutar la herramienta
     * sin adquirir primero el lease para la transición activa y su celda exacta.
     */
    private boolean invokeTerrainAction(NavRoute.Transition transition, BlockPos target, BlockPos towards) {
        if (recoveryAction == null) {
            return false;
        }
        boolean leaseAcquired = tryAcquireTraversalAction(transition, target);
        return TraversalActionDispatchPolicy.dispatch(
                leaseAcquired, () -> recoveryAction.run(towards, target, transition));
    }

    /** Acción inyectable para una transición explícita (una llamada = como mucho un paso de trabajo). */
    public interface RecoveryAction {
        boolean run(BlockPos towards, BlockPos nodoDeRuta, NavRoute.Transition transicionDeRuta);
    }

    private RecoveryAction recoveryAction;

    /**
     * A2 Stage 6E · diagnóstico acotado de la ejecución de nodos de ruta: sobre qué celda se está actuando y con qué
     * resultado real (verificado contra el mundo). Una línea, sin spam por tick.
     */
    public String rutaAccionDiag() {
        return "nodoRecuperacion=" + (nodoDeRecuperacion == null ? "-" : nodoDeRecuperacion.toShortString())
                + " ultimaAccionDeRuta=" + ultimaAccionDeRuta + " tunel={" + tunelDiag() + "}"
                + " ascenso={" + ascensoDiag() + "}";
    }

    /**
     * A2 Stage 6F · diagnóstico acotado del DESCENSO EXPLÍCITO: intentos, fallos, el último descenso descrito (salida,
     * aterrizaje, desplazamiento, si el aterrizaje es construido o natural, y su veredicto) y el último candidato de
     * escalón rechazado por no tener descenso ejecutable. Una línea, sin spam por tick.
     */
    public String descensoDiag() {
        return "intentosDeDescenso=" + intentosDeDescenso + " fallosDeDescenso=" + fallosDeDescenso
                + " ultimoDescenso={" + ultimoDescenso + "} ultimoRechazoDeCandidato={" + ultimoRechazoDeDescenso + "}";
    }

    /**
     * A2 Stage 6F · valida (SIN ejecutar nada) el descenso hasta esa celda de pie, con EXACTAMENTE las mismas reglas
     * que usa el ejecutor al bajar. Lo usan las regresiones focalizadas para comprobar cada condición por separado
     * contra el validador de producción, en vez de replicarlo.
     */
    public Descenso validarDescenso(final BlockPos aterrizaje) {
        return describirDescenso(aterrizaje, construidas.contains(aterrizaje.below()), true);
    }

    /** A2 Stage 6F · índice del nodo de ruta en curso (la regresión comprueba que NO avanza sin aterrizar). */
    public int indiceDeRuta() {
        return routeIndex;
    }

    /** A2 Stage 7B · expansiones del último plan (la regresión comprueba que no se agota el presupuesto). */
    public int expansionsLast() {
        return expansionsLast;
    }

    /** A2 Stage 7B · expansiones acumuladas de todos los planes. */
    public int expansionsTotal() {
        return expansionsTotal;
    }

    /** A2 Stage 7B · órdenes de movimiento emitidas (prueba de que la ruta se EJECUTA, no solo se planifica). */
    public int moves() {
        return moveSubmissions;
    }

    /** A2 Stage 7B · objetivos locales usados para la misión actual. */
    public int escalonesDeObjetivo() {
        return escalonesDeObjetivo;
    }

    /** A2 Stage 6F · nodo de ruta en curso, o {@code null}. */
    public NavRoute.Node nodoDeRutaActual() {
        return route == null ? null : route.currentNode(routeIndex);
    }

    /**
     * Nodo cuya transición está siendo ejecutada ahora. No expone nodos de rutas en planificación,
     * terminales o invalidadas.
     */
    public NavRoute.Node activeNode() {
        if (state != NavState.TRAVERSING && state != NavState.RECOVERING) {
            return null;
        }
        return route == null ? null : route.currentNode(routeIndex);
    }

    public NavRoute.Transition activeTransition() {
        NavRoute.Node node = activeNode();
        return node == null ? null : node.transition();
    }

    /** Objetivo exacto de una acción de terreno; null para transiciones que solo mueven. */
    public BlockPos activeActionTarget() {
        NavRoute.Node node = activeNode();
        if (node == null) {
            return null;
        }
        return switch (node.transition()) {
            case BREAK -> node.actionTarget();
            case BUILD -> node.actionPos();
            case BRIDGE -> node.pos();
            default -> null;
        };
    }

    /**
     * Adquiere el actuador de terreno para la transición activa. El lease es por entidad (esta instancia)
     * y por tick de mundo; una segunda solicitud en el mismo tick se rechaza, aunque la primera haya fallado.
     */
    public boolean tryAcquireTraversalAction(NavRoute.Transition requestedTransition, BlockPos requestedTarget) {
        long now = mob.level().getGameTime();
        NavRoute.Node node = activeNode();
        BlockPos activeTarget = activeActionTarget();
        boolean allowed = TraversalActionLeasePolicy.canAcquire(
                now, traversalActionLeaseTick, traversalActionLeaseUsed,
                node != null, node == null ? null : node.transition(), activeTarget,
                requestedTransition, requestedTarget);
        if (allowed) {
            traversalActionLeaseTick = now;
            traversalActionLeaseUsed = true;
        }
        return allowed;
    }

    public String traversalLeaseDiag() {
        return "tick=" + traversalActionLeaseTick + " used=" + traversalActionLeaseUsed
                + " active=" + activeTransition()
                + " target=" + (activeActionTarget() == null ? "-" : activeActionTarget().toShortString());
    }

    public void setRecoveryAction(RecoveryAction action) {
        this.recoveryAction = action;
    }

    // ------------------------------------------------------------------ detección de oscilación

    private void recordCell(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (!recentCells.isEmpty() && recentCells.peekLast().equals(immutable)) {
            return;
        }
        recentCells.addLast(immutable);
        while (recentCells.size() > OSCILLATION_WINDOW) {
            recentCells.removeFirst();
        }
        // A→B→A: la misma celda reaparece tras haber estado en otra.
        int occurrences = 0;
        for (BlockPos p : recentCells) {
            if (p.equals(immutable)) {
                occurrences++;
            }
        }
        oscillating = occurrences >= 3 && recentCells.size() >= 8;
        recentCellOccurrences = occurrences;
    }

    /**
     * A verified source snap starts a new navigation phase. Clear the pre-snap oscillation
     * history and invalidate the aborted-route signature exactly once, seeding history with the
     * physically validated source cell.
     */
    private void resetPostSnapNavigationState(BlockPos validatedSource, long now) {
        clearDropApproachOwner("post_snap_reset");
        clearDropEdgeStep("post_snap_reset");
        if (!NavSnapPostResetPolicy.shouldResetAfterSnap(true)) {
            return;
        }
        int cellsBefore = recentCells.size();
        boolean oscillatingBefore = oscillating;
        int occurrencesBefore = recentCellOccurrences;
        int signatureBefore = lastRouteSignature;
        recentCells.clear();
        recentCells.addLast(validatedSource.immutable());
        oscillating = NavSnapPostResetPolicy.freshOscillating();
        recentCellOccurrences = NavSnapPostResetPolicy.freshOccurrences();
        lastRouteSignature = NavSnapPostResetPolicy.freshSignature();
        postSnapFirstPlanPending = true;
        postSnapRouteStartPending = false;
        lastSnapResetTick = now;
        if (NavTrace.on()) {
            NavTrace.ev("SNAP_POST_RESET", "uuid=" + mob.getUUID() + " tick=" + now
                    + " source=" + validatedSource.toShortString()
                    + " recentCellsBefore=" + cellsBefore + " recentCellsAfter=" + recentCells.size()
                    + " oscillatingBefore=" + oscillatingBefore + " oscillatingAfter=false"
                    + " occurrencesBefore=" + occurrencesBefore + " occurrencesAfter=1"
                    + " signatureBefore=" + signatureBefore + " signatureAfter=0"
                    + " reason=verified_source_snap_complete");
        }
    }

    /** Se llama cuando algo externo cambió el terreno: invalida la ruta en curso. */
    public void terrainChanged() {
        recoveryController.onVerifiedProgress();
        limpiarRecuperacionOrigenDrop();
        ticksEnEsperaDePlan = 0;
        celdaEnEsperaDePlan = null;
        route = null;
        plannedFrom = null;
        // A2 REMEDIACIÓN 2 · una ruta global cacheada tambien queda obsoleta si el terreno cambio.
        globalCache = null;
        NavPlanner.cancelGlobalSearch(globalSearchHandle);
        globalSearchStart = null;
        globalSearchLastExpansions = 0;
        globalDesde = null;
        // A2 FASE 2 · un cambio de terreno es una razon VALIDA para rearmar un FAILED sin esperar el
        // enfriamiento (el mundo ya no es el que hizo fallar). El presupuesto de rearmes sigue siendo el tope.
        // A2 REMEDIACIÓN 2 · pero un fallo por PRESUPUESTO AGOTADO SIN PROGRESO no se reabre por terreno:
        // el contador duro solo lo reinician el progreso o una mision nueva.
        boolean limiteLocalAgotado = escalonesDeObjetivo >= MAX_ESCALONES_DE_OBJETIVO
                && fallosDeEscalonDeObjetivo >= MAX_FALLOS_DE_ESCALON_DE_OBJETIVO;
        cambioTerrenoPendienteRearme = true;
        if (NavTerrainRearmPolicy.shouldResetLocalStairBudget(true, limiteLocalAgotado,
                rearmesLocalesDeEscalera, MAX_REARMES_LOCALES_DE_ESCALERA)) {
            escalonesDeObjetivo = 0;
            fallosDeEscalonDeObjetivo = 0;
            rearmesLocalesDeEscalera++;
            ultimoEscalonDeObjetivo = "rearme local de escalera por terreno verificado ("
                    + rearmesLocalesDeEscalera + "/" + MAX_REARMES_LOCALES_DE_ESCALERA + ")";
            if (NavTrace.on()) {
                NavTrace.ev("NAV_LOCAL_STAIR_REARM", "uuid=" + mob.getUUID()
                        + " tick=" + mob.level().getGameTime() + " resets=" + rearmesLocalesDeEscalera);
            }
        }
        if (NavTerrainRearmPolicy.shouldRearm(cambioTerrenoPendienteRearme, limiteLocalAgotado,
                rearmadoPorCambioDeTerreno)) {
            escalonesDeObjetivo = 0;
            fallosDeEscalonDeObjetivo = 0;
            ultimoEscalonDeObjetivo = "rearme unico por BREAK de terreno verificado";
            rearmadoPorCambioDeTerreno = true;
            cambioTerrenoPendienteRearme = false;
        }
        if (state == NavState.FAILED
                && !(motivoFalloTerminal.contains("presupuesto")
                && presupuestosSinProgresoTotal >= MAX_BUDGET_RETRIES)) {
            nextRearmAt = mob.level().getGameTime();
            falloDefinitivoTerminal = false;
        }
        // Un cambio de terreno justifica reintentar el objetivo local; NO reinicia el contador DURO.
        intentosObjetivoInvalido = 0;
        mejorDistObjetivoInvalido = Double.MAX_VALUE;
        budgetRetries = 0;
        state = objective == null ? NavState.IDLE : NavState.PLANNING;
        nextPlanAt = 0L;
    }

    /** Nivel de referencia (para telemetría). */
    public Level level() {
        return mob.level();
    }
}
