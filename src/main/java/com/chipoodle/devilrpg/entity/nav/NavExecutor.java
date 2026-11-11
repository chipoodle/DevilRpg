package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final int MAX_REPLANS_PER_DESTINATION = 4;
    private static final int MAX_RECOVERY_ACTIONS = 6;
    private static final int REPLAN_COOLDOWN_TICKS = 20;
    private static final int RECOVERY_COOLDOWN_TICKS = 40;
    private static final int MAX_RECOVERY_COOLDOWN_TICKS = 400;
    /** Espera minima y maxima entre reintentos por presupuesto agotado (evita martillear busquedas caras). */
    private static final int MIN_BUDGET_BACKOFF_TICKS = 40;
    private static final int MAX_BUDGET_BACKOFF_TICKS = 400;
    /**
     * A2 FASE 3 · TOPE DE REINTENTOS POR PRESUPUESTO AGOTADO SIN PROGRESO.
     * Un solo `hitBudget` es INCONCLUYENTE; repetirlo indefinidamente no lo es. Este tope (configurable por
     * `-Ddevilrpg.nav.budgetRetries`, por defecto 6, acotado 1..20) convierte la repeticion sin progreso en un
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
            return 6;
        }
        try {
            return Math.max(1, Math.min(20, Integer.parseInt(p.trim())));
        } catch (NumberFormatException e) {
            return 6;
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
    private static final int MAX_ESCALONES_DE_OBJETIVO = 24;
    /** Fallos seguidos eligiendo objetivo local antes de rendirse con motivo explicito. */
    private static final int MAX_FALLOS_DE_ESCALON_DE_OBJETIVO = 6;

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
    /** Presupuesto DURO de la busqueda global. Se ejecuta una vez por tramo, nunca por tick. */
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
    private static final int MAX_REPLANTEOS_POR_GUARDA = 6;
    private static final double ARRIVE_DISTANCE_SQR = 2.25D;
    /**
     * A2 Stage 6E · FIX B · distancia (al cuadrado) dentro de la cual el ejecutor DELEGA una transicion BREAK/BRIDGE
     * al nodo. Más lejos, primero CAMINA hacia el nodo. Es 2.5 bloques: la misma envolvente acotada de acción que
     * usa el ente para romper, con el margen justo para que la delegación no se quede corta.
     */
    private static final double ALCANCE_DE_ACCION_SQR = 6.25D;
    private static final int OSCILLATION_WINDOW = 24;

    private final Mob mob;
    private final NavObstruction.Policies policies;

    private NavState state = NavState.IDLE;
    private NavRoute route;
    private int routeIndex;
    private BlockPos objective;
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
    private int lastRouteSignature;
    private int replans;
    private int recoveryActions;
    private long nextPlanAt;
    private long nextRecoveryAt;
    private int recoveryCooldown = RECOVERY_COOLDOWN_TICKS;

    private final Deque<BlockPos> recentCells = new ArrayDeque<>();
    private boolean oscillating;

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
    private static final int MAX_INTENTOS_OBJETIVO_INVALIDO = 6;
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

    private static final int MAX_ESCALONES_POR_PLAN = 12;
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
            cercaOrigen = NavDropTransitionPolicy.origenCompatible(yo.getX(), yo.getY(), yo.getZ(),
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
        // CAMINO DE CAIDA: la columna del aterrizaje, desde el nivel del ente hasta el aterrizaje, tiene que estar
        // libre. Si el propio escalon que el ente pisa la tapa, NO hay descenso posible por mucho que dy <= limite.
        for (int y = aterrizaje.getY() + 1; y <= yo.getY(); y++) {
            BlockPos celda = new BlockPos(aterrizaje.getX(), y, aterrizaje.getZ());
            if (!sinFluidoNiPeligro(celda)
                    || !mob.level().getBlockState(celda).getCollisionShape(mob.level(), celda).isEmpty()) {
                return new Descenso(salida, aterrizaje, dx, dy, dz, construido,
                        "el camino de caida esta bloqueado en y=" + y);
            }
        }
        return new Descenso(salida, aterrizaje, dx, dy, dz, construido, null);
    }

    /** Celda sin fluido y sin bloques peligrosos aunque su colisión sea vacía (fuego, lava, zarzas...). */
    private boolean sinFluidoNiPeligro(final BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return !esPeligrosoParaAterrizaje(state);
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
            boolean pisado = mob.blockPosition().equals(escalonActual.above())
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
    private int fallosDeEscalonDeObjetivo;
    /** A2 Stage 7B · ultimo escalon local elegido (o el motivo exacto por el que no hubo ninguno). */
    private String ultimoEscalonDeObjetivo = "-";
    /** A2 Stage 7B · posicion de PIE de la mision (memorizada; se invalida al cambiar de mision). */
    private BlockPos misionEnPie;
    private boolean misionEnPieCalculada;
    /** A2 Stage 7C · cache de la ruta global (una por tramo, no por tick) y su diagnostico. */
    private NavPlanner.GlobalResult globalCache;
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
    /** A2 Stage 7G · replanteos consumidos por rechazo de la guarda de ruta (acotado). */
    private int replanteosPorGuarda;
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
        cadenaActivaFirma = firma;
        cadenaEnviada = cadena.size();
        etapas.clear();
        for (int i = 1; i < cadena.size(); i++) {
            etapas.addLast(cadena.get(i).immutable());
        }
        indiceDeEtapa = 0;
        // A2 FASE 2 · la RAIZ de la mision es la ULTIMA etapa (el interior). Mientras la raiz no cambie, las
        // etapas intermedias son el MISMO asedio: no reinician el contador de rearmes ni borran su motivo.
        BlockPos raiz = cadena.get(cadena.size() - 1).immutable();
        boolean mismaRaiz = raiz.equals(misionRaiz) && misionRaiz != null;
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
        indiceDeEtapa++;
        // A2 Stage 7B · la nueva ETAPA pasa a ser la MISION FINAL y el escalonado local se recalcula desde cero.
        misionFinal = siguiente.immutable();
        // A2 REMEDIACIÓN 2 · el plan de construccion usa `planMision` para decidir desniveles; si no se
        // sincroniza con la etapa nueva, sigue comparando contra la primera etapa.
        setPlanMision(siguiente);
        escalonesDeObjetivo = 0;
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
        if (policies.isProtected(c)) {
            return false;
        }
        if (!mob.level().getBlockState(c).isAir() || !mob.level().getBlockState(c.above()).isAir()) {
            return false;
        }
        return NavObstruction.hasSolidFace(mob.level(), c.below());
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
        if (globalCache != null && mision.equals(globalMision) && globalDesde != null
                && globalDesde.distSqr(yo) <= CACHE_GLOBAL_SQR) {
            return globalCache;
        }
        NavPlanner.GlobalResult r = NavPlanner.planGlobal(mob.level(), mob, yo, mision,
                RADIO_GLOBAL_DE_BUSQUEDA, VENTANA_VERTICAL_GLOBAL, MAX_EXPANSIONES_GLOBALES, policies, construidas,
                exclusionesDescenso);
        globalExpansionesTotal += r.expansions();
        globalCache = r;
        globalMision = mision;
        globalDesde = yo.immutable();
        ultimoGlobalDiag = r.summary();
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} RUTA GLOBAL: {}", mob.getId(),
                r.summary());
        return r;
    }

    /** A2 Stage 7C · un waypoint de la ruta global es pisable si su nodo es una celda de PIE real. */
    private long huellaTerreno(BlockPos p) {
        long h = mob.level().getBlockState(p).hashCode();
        h = h * 31L + mob.level().getBlockState(p.above()).hashCode();
        h = h * 31L + mob.level().getBlockState(p.below()).hashCode();
        return h;
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
     * es el ULTIMO waypoint de pie de esa ruta que quepa en la ventana local; la ventana local no cambia.
     */
    private BlockPos elegirObjetivoLocal() {
        BlockPos mision = misionDePie();
        if (mision == null) {
            ultimoEscalonDeObjetivo = "sin mision utilizable (mision="
                    + (misionFinal == null ? "-" : misionFinal.toShortString()) + ")";
            return null;
        }
        BlockPos yo = mob.blockPosition();
        if (dentroDeLaVentana(yo, mision)) {
            return mision;
        }
        NavPlanner.GlobalResult global = rutaGlobalHacia(mision);
        if (global.route().isEmpty()) {
            ultimoEscalonDeObjetivo = "sin ruta global hacia " + mision.toShortString() + ": " + global.reason()
                    + " frente=" + global.frente().toShortString();
            return null;
        }
        BlockPos objetivo = null;
        BlockPos previoRuta = null;
        boolean descartados = false;
        for (NavRoute.Node n : global.route().nodes()) {
            if (n.transition() == NavRoute.Transition.DROP && previoRuta != null
                    && exclusionesDescenso.excluida(NavDropExclusionPolicy.clave(
                            previoRuta.getX(), previoRuta.getY(), previoRuta.getZ(),
                            n.pos().getX(), n.pos().getY(), n.pos().getZ()),
                            huellaTerreno(n.pos()))) {
                previoRuta = n.pos();
                continue;
            }
            previoRuta = n.pos();
            if (!esWaypointDePie(n)) {
                continue;
            }
            if (!dentroDeLaVentana(yo, n.pos())) {
                if (n.transition() == NavRoute.Transition.DROP
                        && NavWaypointWindowPolicy.aceptable(n.pos().getX() - yo.getX(), n.pos().getZ() - yo.getZ(),
                                n.pos().getY() - yo.getY(), true, HORIZONTAL_RANGE - MARGEN_DE_VENTANA,
                                VERTICAL_RANGE, policies.maxSafeDrop())
                        && esPieValido(n.pos())) {
                    objetivo = n.pos();
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
            if (!esPieValido(n.pos())) {
                descartados = true;
                continue;
            }
            objetivo = n.pos();
        }
        if (objetivo == null) {
            globalCache = null;
            globalDesde = null;
            ultimoEscalonDeObjetivo = "ruta global sin waypoint de pie VALIDO dentro de la ventana desde "
                    + yo.toShortString() + (descartados ? " (habia waypoints pero el mundo cambio)" : "")
                    + " -> se invalida la ruta global para recalcularla";
            return null;
        }
        if (objetivo.equals(yo)) {
            ultimoEscalonDeObjetivo = "el primer waypoint global es la celda actual (" + yo.toShortString()
                    + ") global={" + global.summary() + "}";
            return null;
        }
        ultimoEscalonDeObjetivo = "global " + (global.alcanzoMision() ? "ALCANZA la mision" : "solo frente")
                + " -> waypoint local " + objetivo.toShortString()
                + " (rutaGlobal=" + global.route().length() + " exp=" + global.expansions() + ")";
        return objetivo;
    }

    /**
     * A2 Stage 7B · aplica el escalonado: deja {@code objective} en el objetivo local acotado. Devuelve si hay un
     * objetivo local UTILIZABLE. Lleva sus propios contadores (escalones y fallos seguidos), ambos acotados.
     */
    private boolean escalonarObjetivoLocal() {
        if (misionFinal == null) {
            return false;
        }
        if (escalonesDeObjetivo >= MAX_ESCALONES_DE_OBJETIVO) {
            fallosDeEscalonDeObjetivo = MAX_FALLOS_DE_ESCALON_DE_OBJETIVO;
            ultimoEscalonDeObjetivo = "tope de escalones locales alcanzado (" + MAX_ESCALONES_DE_OBJETIVO
                    + ") hacia " + misionFinal.toShortString();
            return false;
        }
        BlockPos siguiente = elegirObjetivoLocal();
        if (siguiente == null) {
            fallosDeEscalonDeObjetivo++;
            ultimoEscalonDeObjetivo = ultimoEscalonDeObjetivo + " [intento " + fallosDeEscalonDeObjetivo + "/"
                    + MAX_FALLOS_DE_ESCALON_DE_OBJETIVO + "]";
            return false;
        }
        fallosDeEscalonDeObjetivo = 0;
        // A2 Stage 7D · la idempotencia solo vale si el objetivo SIGUE siendo utilizable. Antes bastaba con que la
        // celda coincidiera: si el mundo la habia dejado solida, se devolvia "true" sin cambiar nada y el llamante
        // salia sin planificar en cada tick (bucle medido en `a2n-20261017-121902`).
        if (siguiente.equals(objective) && esPieValido(objective)
                && dentroDeLaVentana(mob.blockPosition(), objective)) {
            return true; // el escalon ya estaba puesto Y sigue siendo valido: no se toca el progreso
        }
        escalonesDeObjetivo++;
        objective = siguiente;
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
        objective = misionFinal;
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
    public void suspendForCombat() {
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

    public void setObjective(BlockPos target) {
        // A2 Stage 6A · la MISION del plan de construccion es SIEMPRE el objetivo fijado. Medido en
        // 2n-20261016-122549: planMision seguia null porque el arnes llama a setObjective directamente, asi
        // que el cableado desde la entidad nunca se ejecutaba y el plan de construccion jamas arrancaba.
        if (target == null) {
            return;
        }
        BlockPos immutable = target.immutable();
        // A2 FASE 4 · un objetivo de MISION desactiva el modo tactico.
        this.objetivoTactico = false;
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
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[JUMPUP] MISSIONCHANGE old={} new={} objective={}",
                    this.misionFinal, immutable, this.objective);
            if (this.objective != null) {
                // La mision nueva manda: aunque el movimiento sea corto, el planificador no puede
                // seguir apuntando al objetivo anterior.
                this.objective = immutable;
                this.plannedFrom = null;
                this.route = null;
                this.routeIndex = 0;
                this.globalCache = null;
                this.globalDesde = null;
                this.state = NavState.PLANNING;
                this.nextPlanAt = 0L;
            }
            this.misionFinal = immutable;
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
            fallosDeEscalonDeObjetivo = 0;
            ultimoEscalonDeObjetivo = "-";
            misionEnPieCalculada = false;
            misionEnPie = null;
            // A2 Stage 7C · mision nueva: tambien se reinicia el presupuesto de re-estadios y la ruta global.
            reestadiosGlobales = 0;
            globalCache = null;
            globalDesde = null;
        }
        objectiveSubmissions++;
        lastSubmissionTick = mob.level().getGameTime();
        if (objective == null) {
            objective = immutable;
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
        objetivoTactico = false;
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
        escalonesDeObjetivo = 0;
        fallosDeEscalonDeObjetivo = 0;
        ultimoEscalonDeObjetivo = "-";
        route = null;
        routeIndex = 0;
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
            escalonesDeObjetivo = 0;
            ultimoEscalonDeObjetivo = "progreso verificado en tick: dMision="
                    + String.format(java.util.Locale.ROOT, "%.1f", d) + " -> contador de escalones reiniciado";
        }
    }

    /** El salto recto de 2 solo se acepta si el corredor a la altura del apice y la cabeza esta libre. */
    private boolean corredorRectoDeSalto(BlockPos origen, BlockPos destino) {
        int sx = Integer.signum(destino.getX() - origen.getX());
        int sz = Integer.signum(destino.getZ() - origen.getZ());
        int pasos = Math.max(Math.abs(destino.getX() - origen.getX()), Math.abs(destino.getZ() - origen.getZ()));
        for (int i = 1; i <= pasos; i++) {
            for (int dy = 2; dy <= 3; dy++) {
                BlockPos c = new BlockPos(origen.getX() + sx * i, origen.getY() + dy, origen.getZ() + sz * i);
                if (!mob.level().getBlockState(c).getCollisionShape(mob.level(), c).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean iniciarSalto(NavRoute.Node node) {
        if (mob == null || node == null || route == null) {
            return false;
        }
        BlockPos destino = node.pos();
        BlockPos pies = mob.blockPosition();
        int dy = destino.getY() - pies.getY();
        int dx = Math.abs(destino.getX() - pies.getX());
        int dz = Math.abs(destino.getZ() - pies.getZ());
        boolean recto2 = (dx == 2 && dz == 0) || (dx == 0 && dz == 2);
        if (dy != 2 || (dx > 1 || dz > 1) && !recto2) {
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info(
                    "[JUMPUP] REJECT pos={} target={} dx={} dy={} dz={} recto2={}", mob.blockPosition().toShortString(),
                    destino.toShortString(), dx, dy, dz, recto2);
            return false;
        }
        if (recto2 && !corredorRectoDeSalto(pies, destino)) {
            com.chipoodle.devilrpg.DevilRpg.LOGGER.info(
                    "[JUMPUP] REJECT_CORREDOR pos={} target={} dx={} dz={}", mob.blockPosition().toShortString(),
                    destino.toShortString(), dx, dz);
            return false;
        }
        if (!esPieValido(destino) || !NavObstruction.hasSupport(mob.level(), destino)
                || !NavObstruction.fits(mob.level(), mob, destino)) {
            return false;
        }
        saltoActivo = true;
        saltosIniciados++;
        saltoOrigen = pies.immutable();
        saltoObjetivo = destino.immutable();
        saltoFirmaRuta = route.signature();
        saltoMision = misionFinal != null ? misionFinal : objective;
        saltoTicks = 0;
        saltoSinProgreso = 0;
        saltoUltimaX = mob.getX();
        estadoSalto = "INICIANDO";
        mob.getNavigation().stop();
        mob.setDeltaMovement(0.28D * Integer.signum(destino.getX() - pies.getX()), 0.62D, 0.0D);
        if (destino.getX() == pies.getX()) {
            mob.setDeltaMovement(0.0D, 0.62D, 0.0D);
        }
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info(
                "[JUMPUP] START uuid={} origen={} objetivo={} firma={}", mob.getUUID(),
                pies.toShortString(), destino.toShortString(), saltoFirmaRuta);
        return true;
    }

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

    private void tickSalto() {
        saltoTicks++;
        BlockPos misionActual = misionFinal != null ? misionFinal : objective;
        if (mob.isRemoved() || route == null || route.signature() != saltoFirmaRuta
                || misionActual == null || !misionActual.equals(saltoMision)) {
            abortarSalto("ruta/mision cambio o entidad retirada");
            return;
        }
        if (!NavObstruction.hasSupport(mob.level(), saltoObjetivo) || !esPieValido(saltoObjetivo)) {
            abortarSalto("aterrizaje ya no valido");
            return;
        }
        mob.getNavigation().stop();
        if (saltoTicks == 1 && mob.onGround()) {
            mob.setDeltaMovement(0.28D * Integer.signum(saltoObjetivo.getX() - saltoOrigen.getX()), 0.62D, 0.0D);
            return;
        }
        if (mob.onGround()) {
            double fy = mob.getY();
            boolean piesOk = Math.abs(fy - saltoObjetivo.getY()) <= 0.1D;
            boolean xzOk = Math.abs(mob.getX() - (saltoObjetivo.getX() + 0.5D)) <= 1.1D
                    && Math.abs(mob.getZ() - (saltoObjetivo.getZ() + 0.5D)) <= 1.1D;
            if (piesOk && xzOk && NavObstruction.hasSupport(mob.level(), saltoObjetivo)) {
                saltoActivo = false;
                routeIndex++;
                saltosCompletados++;
                estadoSalto = "OK";
                state = NavState.TRAVERSING;
                com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[JUMPUP] LANDED uuid={} pos={} routeIndex={}",
                        mob.getUUID(), mob.blockPosition().toShortString(), routeIndex);
                return;
            }
            abortarSalto("aterrizaje fuera del objetivo");
            return;
        }
        double vx = Math.max(-0.28D, Math.min(0.28D, (saltoObjetivo.getX() + 0.5D) - mob.getX()));
        mob.setDeltaMovement(vx, mob.getDeltaMovement().y, 0.0D);
        if (Math.abs(mob.getX() - saltoUltimaX) < 0.005D) {
            saltoSinProgreso++;
        } else {
            saltoSinProgreso = 0;
            saltoUltimaX = mob.getX();
        }
        if (saltoSinProgreso > 5 || mob.horizontalCollision && saltoTicks > 10) {
            abortarSalto("sin progreso horizontal");
            return;
        }
        if (saltoTicks > 40) {
            abortarSalto("timeout de salto");
            return;
        }
        estadoSalto = "AIRE";
    }

    private void abortarSalto(String motivo) {
        if (!saltoActivo) {
            return;
        }
        saltoActivo = false;
        saltosAbortados++;
        ultimoMotivoSalto = motivo;
        if ("timeout de salto".equals(motivo)) {
            saltosTimeout++;
        }
        estadoSalto = "ABORT:" + motivo;
        route = null;
        routeIndex = 0;
        lastRouteSignature = (int) saltoFirmaRuta;
        globalCache = null;
        globalDesde = null;
        state = NavState.PLANNING;
        mob.getNavigation().stop();
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[JUMPUP] ABORT uuid={} motivo={} pos={} ticks={} mision={}",
                mob.getUUID(), motivo, mob.blockPosition().toShortString(), saltoTicks, misionFinal);
    }
    public void tick() {
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
        if (saltoActivo) {
            tickSalto();
            return;
        }
        registrarProgresoHaciaMision();
        recordCell(mob.blockPosition());

        double distSqr = mob.distanceToSqr(objective.getX() + 0.5D, objective.getY() + 0.5D,
                objective.getZ() + 0.5D);
        if (distSqr <= ARRIVE_DISTANCE_SQR) {
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
                objective = NavArrivalPolicy.objetivoTrasLlegada(llegada, objective, misionFinal);
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
        if (objective == null || misionFinal == null) {
            motivoFalloTerminal = "sin objetivo/mision que rearmar";
            return;
        }
        // A2 REMEDIACIÓN 2 · primero los atajos que NO mutan nada. Un fallo definitivo ya cerrado no puede
        // volver a concatenar texto ni evaluar el mundo en cada tick.
        if (rearmesTerminales >= MAX_TERMINAL_REARMS) {
            return;
        }
        if (falloDefinitivoTerminal) {
            return;
        }
        if (now < nextRearmAt) {
            return;
        }
        // A2 FASE 2/3 · EL PRESUPUESTO AGOTADO SIN PROGRESO NO ES REARMABLE. No es un fallo transitorio:
        // repetir la misma busqueda no puede dar otro resultado. Solo un cambio de terreno, progreso o una
        // mision nueva reinician el contador duro (y entonces el rearme vuelve a estar permitido).
        if (motivoFalloTerminal.contains("presupuesto")
                && presupuestosSinProgresoTotal >= MAX_BUDGET_RETRIES) {
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
            // Una sola escritura: a partir de aqui `falloDefinitivoTerminal` corta cualquier reevaluacion.
            motivoFalloTerminal = decision.motivo() + " | ultimoFallo=" + motivoFalloTerminal;
            falloDefinitivoTerminal = true;
            nextRearmAt = Long.MAX_VALUE;
            return;
        }
        if (!decision.rearmar()) {
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
        fallosDeEscalonDeObjetivo = 0;
        globalCache = null;
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
        motivoFalloTerminal = motivo;
        falloDefinitivoTerminal = false; // este fallo es nuevo: la recuperacion vuelve a poder evaluarse
        state = NavState.FAILED;
        if (NavTrace.on()) {
            NavTrace.ev("NAV_FAIL", "uuid=" + mob.getUUID() + " pos=" + mob.blockPosition().toShortString()
                    + " motivo={" + motivo + "} rearmes=" + rearmesTerminales + "/" + MAX_TERMINAL_REARMS);
        }
        com.chipoodle.devilrpg.DevilRpg.LOGGER.info("[Siege] asaltante #{} NAVEGACION FAILED en {}: {}",
                mob.getId(), mob.blockPosition().toShortString(), motivo);
    }

    private void tryPlan(long now) {
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
        boolean objetivoUtilizable = objective != null && dentroDeLaVentana(mob.blockPosition(), objective)
                && esPieValido(objective);
        if (!objetivoUtilizable) {
            if (!escalonarObjetivoLocal()) {
                if (fallosDeEscalonDeObjetivo >= MAX_FALLOS_DE_ESCALON_DE_OBJETIVO) {
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
                    || !esPieValido(objective)) {
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
                HORIZONTAL_RANGE, VERTICAL_RANGE, MAX_EXPANSIONS_PER_PLAN, policies, construidas);
        plansRun++;
        expansionsLast = result.expansions();
        expansionsTotal += result.expansions();
        lastPlanSummary = result.summary();
        if (result.hitBudget()) {
            // A2 Stage 4K: la exhaucion de presupuesto SOLO se enruta a CONSTRUCCION si el objetivo activo exige
            // construccion por geometria real (caida > segura Y sin aterrizaje intermedio soportado). Las rutas
            // complejas normales conservan su reintento de siempre.
            if (mob instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity az
                    && az.construirEscalonHacia(objective)) {
                reconstruccionEnrutada = "presupuesto agotado + objetivo exige construccion -> escalon";
                return; // sigue en PLANNING; el objetivo se conserva
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
            // Sin ruta REAL (se exploro sin agotar presupuesto): recuperacion acotada.
            routesRejected++;
            replans++;
            nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
            state = NavState.RECOVERING;
            return;
        }
        // Una ruta con la misma firma que una ya fallada no se reintenta.
        if (result.route().signature() == lastRouteSignature) {
            routesRejected++;
            replans++;
            nextPlanAt = now + REPLAN_COOLDOWN_TICKS;
            state = NavState.RECOVERING;
            return;
        }
        route = result.route();
        routeIndex = 0;
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
        if (node.transition() == NavRoute.Transition.JUMP_UP) {
            return false; // el salto solo avanza cuando el controlador confirma el aterrizaje
        }
        if (mob.distanceToSqr(node.pos().getX() + 0.5D, node.pos().getY() + 0.5D,
                node.pos().getZ() + 0.5D) > ARRIVE_DISTANCE_SQR) {
            return false;
        }
        if (node.transition() != NavRoute.Transition.DROP) {
            return true;
        }
        boolean alcanzado = mob.blockPosition().getY() <= node.pos().getY()
                && NavObstruction.hasSolidFace(mob.level(), mob.blockPosition().below());
        if (alcanzado && NavTrace.on()) {
            NavTrace.ev("DROP_DONE", "uuid=" + mob.getUUID() + " idxRuta=" + routeIndex
                    + " aterrizaje=" + node.pos().toShortString() + " pos=" + mob.blockPosition().toShortString());
        }
        return alcanzado;
    }

    private void traverse(long now) {
        if (route == null) {
            state = NavState.PLANNING;
            return;
        }
        if (oscillating) {
            // A→B→A detectado: la ruta actual no sirve; se invalida y se pasa a recuperación.
            routesRejected++;
            lastRouteSignature = route.signature();
            route = null;
            state = NavState.RECOVERING;
            return;
        }
        NavRoute.Node node = route.currentNode(routeIndex);
        // A2 Stage 6F · la ruta avanza cuando el nodo esta ALCANZADO de verdad: para un DROP eso exige estar
        // fisicamente en el aterrizaje (a su altura o por debajo) y con suelo, no solo cerca.
        while (node != null && nodoAlcanzado(node)) {
            routeIndex++;
            intentosDeDescenso = 0;
            node = route.currentNode(routeIndex);
        }
        if (node == null) {
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
                if (!iniciarSalto(node)) {
                    state = NavState.OBSTRUCTED;
                }
                return;
            }
            case STEP_UP -> mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0D);
            case DROP -> {
                // A2 Stage 6F · DESCENSO EXPLICITO Y VERIFICADO. El nodo es el ATERRIZAJE: se describe y se valida
                // (contigüidad, holgura de pies y cuerpo, soporte con colisión —construido y VERIFICADO si es obra
                // del plan—, caída dentro del límite y CAMINO DE CAÍDA despejado) ANTES de ordenar movimiento. La
                // ruta no avanza hasta el aterrizaje físico (`nodoAlcanzado`).
                // Edit 17 · ORIGEN PLANIFICADO de ESTA transicion: solo si el cursor apunta al nodo que se
                // ejecuta (identidad por posicion) y existe predecesor. NUNCA se usa plannedFrom (es el
                // origen de la RUTA, no el de cada DROP) ni indiceDeEtapa (es el indice de ETAPA).
                BlockPos origenPlan = null;
                if (route != null && routeIndex > 0 && routeIndex < route.nodes().size()) {
                    NavRoute.Node actual = route.currentNode(routeIndex);
                    if (actual != null && actual.pos().equals(node.pos())) {
                        NavRoute.Node previo = route.currentNode(routeIndex - 1);
                        if (previo != null) {
                            origenPlan = previo.pos();
                        }
                    }
                }
                Descenso descenso = describirDescenso(node.pos(),
                        construidas.contains(node.pos().below()), true, origenPlan);
                if (origenPlan != null
                        && !NavDropTransitionPolicy.origenCompatible(mob.blockPosition().getX(),
                                mob.blockPosition().getY(), mob.blockPosition().getZ(),
                                origenPlan.getX(), origenPlan.getY(), origenPlan.getZ())) {
                    // The DROP is evaluated only from its planned origin. Move there first; the
                    // existing contiguity/support checks remain authoritative once the origin is reached.
                    mob.getNavigation().moveTo(origenPlan.getX() + 0.5D, origenPlan.getY(),
                            origenPlan.getZ() + 0.5D, 1.0D);
                    lastMoveTarget = origenPlan.toShortString() + ":ACERCARSE-ORIGEN-DROP";
                    return;
                }
                ultimoDescenso = descenso.resumen();
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
                    BlockPos desde = origenPlan != null ? origenPlan : mob.blockPosition();
                    exclusionesDescenso.excluir(NavDropExclusionPolicy.clave(
                            desde.getX(), desde.getY(), desde.getZ(),
                            node.pos().getX(), node.pos().getY(), node.pos().getZ()),
                            huellaTerreno(node.pos()));
                    // La exclusion evita reejecutar el mismo DROP; invalidar la ruta permite un replan acotado.
                    route = null;
                    routeIndex = 0;
                    lastRouteSignature = 0;
                    globalCache = null;
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
                            + " dy=" + descenso.dy() + " construido=" + descenso.aterrizajeConstruido());
                }
                // Descenso: se deja caer al borde en vez de saltar.
                mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 0.9D);
            }
            case BUILD -> {
                // A2 Stage 4Q · CONSTRUIR DESDE LA RUTA. El nodo es la celda donde se coloca el bloque; la posicion
                // transitable resultante es su celda SUPERIOR. Se coloca con la primitiva de la casa, se VERIFICA
                // releyendo el estado, y la celda pasa a la geometria construida para que siga en la ruta aceptada.
                if (placement == null || !placement.place(node.pos())) {
                    // No se pudo construir: se registra el motivo y NO se avanza (la ruta se conserva).
                    lastMoveTarget = node.pos().toShortString() + ":BUILD-FALLIDO";
                    state = NavState.OBSTRUCTED;
                    return;
                }
                if (mob.level().getBlockState(node.pos()).isAir()) {
                    lastMoveTarget = node.pos().toShortString() + ":BUILD-NO-VERIFICADO";
                    state = NavState.OBSTRUCTED;
                    return;
                }
                construidas.add(node.pos().immutable());
                bloquesColocadosVerificados++;
                ultimoConstruido = node.pos().toShortString();
                ultimoConstruidoTick = mob.level().getGameTime();
                // La ruta NO se descarta: se sigue con el mismo indice y el mismo objetivo.
                lastMoveTarget = node.pos().toShortString() + ":BUILD-VERIFICADO";
                return;
            }
            case BREAK, BRIDGE -> {
                // A2 Stage 6E · FIX B · LA TRANSICION ACTUA SOBRE SU NODO, NO SOBRE LA MISION LEJANA.
                // Medido en `a2n-20261016-170218`: la ruta empezaba en `0:BREAK@120,84,-9` y el ejecutor se limitaba
                // a pasar a RECOVERING; la recuperacion solo recibia el OBJETIVO, asi que ese nodo NUNCA se rompia y
                // el ente se quedaba en `idx=0` enviando `moveTo` sin ejecutar la transicion (10 envios, 0 avance).
                double distNodoSqr = mob.distanceToSqr(node.pos().getX() + 0.5D, node.pos().getY() + 0.5D,
                        node.pos().getZ() + 0.5D);
                if (distNodoSqr > ALCANCE_DE_ACCION_SQR) {
                    // Aun NO esta al alcance del nodo: se CAMINA HACIA EL NODO (no hacia la mision). Si la
                    // navegacion no puede acercarlo, se declara obstruido y decide la recuperacion acotada.
                    mob.getNavigation().moveTo(node.pos().getX() + 0.5D, node.pos().getY(),
                            node.pos().getZ() + 0.5D, 1.0D);
                    moveSubmissions++;
                    lastMoveTarget = node.pos().toShortString() + ":ACERCARSE-AL-NODO";
                    ultimaAccionDeRuta = node.pos().toShortString() + ":" + node.transition()
                            + "-ACERCARSE (dist=" + String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distNodoSqr)) + ")";
                    if (mob.getNavigation().isDone() && ticksSinAvance > TICKS_DE_GRACIA_DE_NODO) {
                        state = NavState.OBSTRUCTED;
                    }
                    return;
                }
                boolean resuelto = node.transition() == NavRoute.Transition.BREAK
                        ? mob.level().getBlockState(node.pos()).isAir()
                        : !mob.level().getBlockState(node.pos()).isAir();
                if (resuelto) {
                    // La transicion YA esta satisfecha en el mundo (verificado, no supuesto): la ruta AVANZA.
                    routeIndex++;
                    ultimaAccionDeRuta = node.pos().toShortString() + ":" + node.transition()
                            + "-YA-RESUELTO-AVANZA";
                    return;
                }
                nodoDeRecuperacion = node.pos().immutable();
                ultimaAccionDeRuta = node.pos().toShortString() + ":" + node.transition() + "-DELEGADO (bloque="
                        + mob.level().getBlockState(node.pos()).getBlock().getName().getString() + ")";
                state = NavState.RECOVERING;
            }
            default -> mob.getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0D);
        }
        // A4.2 · RUTA STALE: si vanilla termino y no hay avance hacia un nodo no-BREAK/BRIDGE, no se
        // sigue enviando el mismo nodo ni se deja que la guarda de recuperacion consuma sus 6 intentos
        // contra el objetivo lejano. Se invalida SOLO la ruta local; el planificador existente decide
        // desde la posicion actual. BREAK/BRIDGE siguen su rama normal.
        if (NavRouteInvalidationPolicy.debeInvalidar(route != null, mob.getNavigation().isDone(),
                ticksSinAvance, TICKS_DE_GRACIA_DE_NODO, node.transition())) {
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
            case BREAK, BRIDGE -> true;
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
    private boolean rechazoPorGuardaDeRuta() {        BlockPos celdaDeRecuperacion = nodoDeRecuperacion != null ? nodoDeRecuperacion : objective;
        if (celdaDeRecuperacion == null) {
            return false;
        }
        // A2 Stage 7H · UNA ACCION AUTORIZADA DE LA RUTA NO ES "RECUPERACION GENERICA". Medido en
        // `a2n-20261017-135158` (stage6e): el nodo activo era `BREAK@522,110,388`, `traverse` lo delego a
        // recuperacion y esta transicion lo intercepto como rechazo de guarda: invalido la ruta y replanifico en vez
        // de romper el muro (`muroAire=false`, `BREAK-DELEGADO`). Si el nodo ACTIVO es BREAK/BRIDGE y coincide con la
        // celda de recuperacion, es ejecucion LEGITIMA de la ruta: el rechazo de guarda NO aplica.
        if (route != null) {
            NavRoute.Node activo = route.currentNode(routeIndex);
            if (activo != null && activo.pos().equals(celdaDeRecuperacion)
                    && (activo.transition() == NavRoute.Transition.BREAK
                    || activo.transition() == NavRoute.Transition.BRIDGE)) {
                return false;
            }
        }
        return celdaDeRutaActiva(celdaDeRecuperacion);
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
        boolean exencionBreak = activo != null && recuperacion != null
                && activo.pos().equals(recuperacion)
                && (activo.transition() == NavRoute.Transition.BREAK
                || activo.transition() == NavRoute.Transition.BRIDGE);
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
                + " celdaActiva=" + celdaActiva
                + " rechaza=" + rechaza;
    }

    /** A2 Stage 7E · cuantas veces el predicado de `traverse` declaro OBSTRUCTED. */
    public int obstruccionesPorPredicado() {
        return obstruccionesPorPredicado;
    }

    private void recover(long now) {
        if (now < nextRecoveryAt) {
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
                && !construccionAgotada && !descensoBloqueado;
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
     * A2 Stage 6E · FIX B · cuando la ruta actual pide {@code BREAK} o {@code BRIDGE}, se pasa además
     * <b>el nodo concreto</b> de la ruta y su transición, para que la acción actúe sobre ESA celda y no solo sobre
     * la misión lejana. En cualquier otro caso el nodo va a {@code null} y la recuperación sigue trabajando hacia el
     * objetivo (el comportamiento ya medido en Stage 6D).
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
        if (actual != null && (actual.transition() == NavRoute.Transition.BREAK
                || actual.transition() == NavRoute.Transition.BRIDGE)) {
            nodo = actual.pos().immutable();
            transicion = actual.transition();
        }
        boolean acted = recoveryAction.run(towards, nodo, transicion);
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

    /** Acción de recuperación inyectable (una llamada = como mucho un paso de trabajo). */
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
                + " ultimaAccionDeRuta=" + ultimaAccionDeRuta;
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
    }

    /** Se llama cuando algo externo cambió el terreno: invalida la ruta en curso. */
    public void terrainChanged() {
        route = null;
        plannedFrom = null;
        // A2 REMEDIACIÓN 2 · una ruta global cacheada tambien queda obsoleta si el terreno cambio.
        globalCache = null;
        globalDesde = null;
        // A2 FASE 2 · un cambio de terreno es una razon VALIDA para rearmar un FAILED sin esperar el
        // enfriamiento (el mundo ya no es el que hizo fallar). El presupuesto de rearmes sigue siendo el tope.
        // A2 REMEDIACIÓN 2 · pero un fallo por PRESUPUESTO AGOTADO SIN PROGRESO no se reabre por terreno:
        // el contador duro solo lo reinician el progreso o una mision nueva.
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
