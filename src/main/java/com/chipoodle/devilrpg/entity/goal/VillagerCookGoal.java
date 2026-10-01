package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * El <b>COCINERO</b> de la aldea (etapa E): el aldeano que <b>cocina</b> en el <b>ahumador de la cocina del kiosco</b>
 * lo que el pueblo tiene crudo —la carne del corral anexo y las patatas de la huerta— y lo devuelve a la
 * <b>despensa</b>.
 * <p>
 * Por qué importa tanto: en el contador de comida de la aldea la <b>carne cruda vale 2 puntos y la cocinada 4</b> (y la
 * patata 2, la asada 4). O sea que cocinar <b>duplica</b> la comida que ya había: es la palanca de hambre del pueblo,
 * y por eso el cocinero es un puesto fijo y no un adorno. Sin él, toda la carne que sube el ganadero del corral se
 * come cruda y vale la mitad.
 * <p>
 * Cómo cocina: <b>en su puesto y con los objetos de verdad</b> (saca la carne de la despensa, la convierte y la
 * vuelve a guardar), con su sonido y su humo.
 * <p>
 * <b>Y HORNEA EL PAN</b>, que es lo que pidió el jugador: *"el cocimiento de los panes no lo debería hacer el granjero
 * sino el COCINERO"*. Antes el granjero horneaba al llegar a la despensa (3 de trigo por hogaza); ahora el granjero
 * solo <b>deja el trigo</b> y es el cocinero el que lo mete en el ahumador ({@link #hornear}). Así cada oficio hace lo
 * suyo —el granjero cultiva y cosecha, el cocinero cocina— y la <b>reserva de trigo para criar</b>
 * ({@link VillagePantry#RESERVA_DE_TRIGO_PARA_CRIAR}) vive donde vive el horno: sin ella, quien hornea se come el
 * trigo del ganadero y el pueblo se queda sin cría, sin cuero y sin lana.
 * <p>
 * <b>Y EL FUEGO SE PAGA CON LEÑA DEL ALMACÉN</b> (lo pidió el jugador: *"el smoker, el furnance y todos los aparatos
 * donde se tenga que quemar necesitan ir por logs al almacén para que se use de combustible y funcionen"*). Antes el
 * humo salía de la nada. Ahora cada tanda de cocina <b>quema un tronco</b>: el cocinero <b>va al almacén</b> a por
 * leña ({@link #LENA_POR_VIAJE} troncos de una vez, que cunden para varias tandas), la lleva encima y la gasta al
 * cocinar. Y si el almacén no tiene leña <b>por encima de la {@link VillageStorage#RESERVA_LENA reserva}</b> (la
 * madera es también la materia prima del herrero), no cocina y lo dice en su etiqueta: un aparato sin combustible no
 * funciona, no se inventa el humo.
 */
public class VillagerCookGoal extends Goal {

    /** Cuántas piezas cocina por visita a la despensa (ni una más: el resto sigue crudo hasta la próxima vuelta). */
    private static final int COCINAR_MAX = 8;
    /**
     * Hogazas como mucho por visita (para que se le vea trabajar). La receta de vanilla son
     * {@link VillagePantry#WHEAT_PER_BREAD} de trigo por hogaza, y el pan vale
     * {@link VillagePantry#FOOD_PER_BREAD} puntos de comida: hornear 2 deja el viaje bien aprovechado. Se sigue
     * dejando la {@link VillagePantry#RESERVA_DE_TRIGO_PARA_CRIAR reserva de trigo para criar}, que no es para comer.
     * <p>
     * Era del <b>granjero</b> (lo horneaba al llegar a la despensa) hasta que el jugador lo mandó cambiar: *"el
     * cocimiento de los panes no lo debería hacer el granjero sino el COCINERO"*.
     */
    private static final int HORNEAR_MAX = 2;
    /** Ticks de faena antes de que la cocción ocurra (se le ve trabajar en el ahumador). */
    private static final int WORK_TICKS = 30;
    private static final int REST_TICKS = 20;
    /** Sin nada que cocinar: a esperar (mirar la despensa no se hace por tick). */
    private static final int IDLE_REST_TICKS = 200;
    /** Si no logra acercarse en este tiempo, abandona (invariante I3: atascado = no acercarse). */
    private static final int STUCK_LIMIT = 200;
    // (Aquí vivía TICKS_PARA_COMPROBAR_SI_HAY_RUTA, de la variante I161 —preguntar la ruta a los 40 ticks de atasco—,
    //  que se midió y se retiró: `Yendo a la cocina` pasó de 17 a 26 avisos en 4 corridas.)
    /** Velocidad de paseo del cocinero (igual que los demás goals del pueblo). */
    private static final float VELOCIDAD = 0.6F;
    /**
     * Alcance a la <b>casilla de la cocina</b> (la de delante del ahumador): el cocinero tiene que estar <b>en ella</b>
     * para cocinar. Antes eran 6,5 —«que llegue de sobra»— y por eso el cocinero cocinaba <b>desde fuera de la
     * taberna</b>: el jugador lo vio con captura, plantado en la plaza cocinando a través de la pared (y medido con el
     * arnés: {@code pos=1446,120,1425 … dCasilla=5,76 VEelAhumador=NO}). Con 2,0 está en la casilla o pegada a ella.
     */
    private static final double REACH = 2.0D;
    /**
     * Alcance al <b>ahumador</b>, que está <b>dentro del kiosco</b> (sobre la plataforma, un bloque más arriba). Es
     * el que de verdad decide si trabaja: sin él, el cocinero "cocinaría" desde la otra punta de la plaza. Con 8
     * entra de sobra desde el patio (el punto de apoyo está a ~5,5 del ahumador), así que solo salta si el aldeano
     * se quedó corto por el camino.
     */
    private static final double ALCANCE_AHUMADOR = 8.0D;
    /** Troncos que el cocinero se trae del almacén de una vez: cuatro tandas de cocina sin volver a cruzar el pueblo. */
    private static final int LENA_POR_VIAJE = 4;
    /**
     * Paciencia yendo al almacén por leña: es el mismo caso que el agua del clérigo —el almacén está al otro lado del
     * pueblo, a ~20-25 bloques de la taberna—, así que con los 200 ticks (10 s) del puesto se rendiría a mitad de
     * camino y aparcaría el almacén para siempre. Veinte segundos dan de sobra y siguen cortando el bucle.
     */
    private static final int STUCK_LENA = 20 * 20;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    /** El ahumador del kiosco (el puesto de trabajo). Se mide en {@link #canUse}, no en cada tick. */
    @Nullable
    private BlockPos puesto;
    /** Ahumador para el que se calculó {@link #vistaDeLaCocina} (I156: se elige UNA vez, no por tick). */
    @Nullable
    private BlockPos vistaDeLaCocinaDe;
    /** La casilla de pie desde la que se cocina ({@code null} si ninguna de al lado sirve). */
    @Nullable
    private BlockPos vistaDeLaCocina;
    /** Hasta qué tick vale el «no hay casilla de cocina» (el rechazo caduca; ver I156). */
    private long vistaDeLaCocinaRechazadaHasta;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    /** ¿La vuelta que está haciendo AHORA es la del almacén (a por leña) y no la de la cocina? */
    private boolean yendoPorLena;
    /** Contador y mejor distancia <b>de la pierna de la leña</b>: medidos aparte, como en el clérigo (ver {@link #STUCK_LENA}). */
    private int stuckLena;
    private double mejorDistanciaLena = Double.MAX_VALUE;
    /** Ya se avisó de que no hay leña: no se repite la línea del log en cada intento. */
    private boolean avisadoSinLena;

    public VillagerCookGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (restTicks > 0) {
            restTicks--;
            return false;
        }
        if (villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) {
            return false; // solo el cocinero (el gestor le da el goal solo a él)
        }
        // En plena refriega nadie cocina, y de noche el cocinero se va a la cama como todos.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > (VillageGenerator.FENCE_RADIUS + 10.0D) * (VillageGenerator.FENCE_RADIUS + 10.0D)) {
            return false; // se ha ido lejos del pueblo
        }
        // El puesto (el ahumador de la TABERNA desde la etapa F), medido UNA vez por intento: `puestoDelCocinero`
        // pregunta la cota de la plaza (un barrido del terreno) y eso no se hace en cada tick. Sin ahumador no hay
        // cocina a la que ir.
        puesto = VillageGenerator.puestoDelCocinero(level, center);
        if (!level.getBlockState(puesto).is(Blocks.SMOKER)) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        // Solo va si de verdad hay algo que hacer: cocinar carne/patatas/huevos O hornear el trigo que el granjero
        // acaba de dejar. Si no, no se queda plantado en el ahumador.
        if (contarCrudoEnLaDespensa(level) <= 0 && !hayTrigoQueHornear(level)) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        // SIN LEÑA ENCIMA, LO PRIMERO ES IR A POR ELLA al almacén: el ahumador no funciona sin combustible. Si ya
        // lleva, se va derecho a la cocina.
        if (!llevaLena()) {
            if (!irPorLena(level)) {
                restTicks = IDLE_REST_TICKS;
                return false;
            }
            return true;
        }
        // Con leña: camina a la casilla de DELANTE del ahumador (la cocina de la taberna), donde puede estar de pie.
        target = vistaDeLaCocina(level);
        if (target == null) {
            // NINGUNA casilla de al lado del ahumador sirve: no se cocina (mejor eso que empujar una pared).
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        if (VillageManager.esPuntoFallido(villager, target)) {
            // A esa casilla de la cocina no llegó hace poco (I33): no se queda plantado empujando, espera un rato.
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        yendoPorLena = false;
        return target != null;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        stuckLena = 0;
        mejorDistanciaLena = Double.MAX_VALUE;
        irAlDestino();
    }

    @Override
    public boolean canContinueToUse() {
        if (target != null && !yendoPorLena && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el punto de la cocina al que no llegó se apunta para no volver a
            // él en bucle, que es lo que dejaba al cocinero empujando el mismo obstáculo para siempre. (La pierna de
            // la leña tiene su propio contador y su propio aparcado: ver `tickDeLaLena`.)
            VillageManager.marcarPuntoFallido(villager, target);
            return false;
        }
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || puesto == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (yendoPorLena) {
            tickDeLaLena(level);
            return;
        }
        // Camina a la CASILLA DE LA COCINA (la de delante del ahumador) y trabaja SOLO desde ahí: nunca HACIA el
        // ahumador, que es un bloque sólido al que la navegación no puede llegar.
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        double alAhumador = Math.sqrt(villager.distanceToSqr(puesto.getX() + 0.5D, puesto.getY() + 0.5D,
                puesto.getZ() + 0.5D));
        villager.getLookControl().setLookAt(puesto.getX() + 0.5D, puesto.getY() + 0.5D, puesto.getZ() + 0.5D);
        // ¿ESTÁ DE VERDAD EN LA COCINA? No basta con estar cerca: hay que <b>ver el ahumador</b>, sin nada sólido en
        // medio. Sin esto cocinaba también desde la plaza (al otro lado de la pared de la taberna: lo reportó el
        // jugador) y desde el comedor a través del tabique de la cocina; medido con el arnés, a 5,19 bloques del
        // puesto con el tabique en medio y desde fuera a 6,44.
        boolean enLaCocina = distancia <= REACH && alAhumador <= ALCANCE_AHUMADOR
                && VillageManager.hayVistaLibre(level, villager.blockPosition(), puesto, villager);
        if (!enLaCocina) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            // I161 SE PROBÓ AQUÍ Y SE RETIRÓ (30-sep-2026): preguntar la ruta a los 40 ticks de atasco y aparcar la
            // casilla si no la hay. El cocinero quedaba bien en una corrida (8 y 6 avisos) pero **en las cuatro salió
            // peor que sin el cambio**: `Yendo a la cocina` **17 → 26** (4 corridas contra 4). Lo que NO se toca: la
            // pregunta en `canUse` (disparaba el bucle del portón de la granjera, I157) ni cada tick (coste). La clase
            // «sin ruta» del cocinero sigue **abierta** y su análisis está en `docs/PENDIENTE.md`.
            VillageManager.ponerActividad(villager, "Yendo a la cocina");
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, "Cocinando");
            return;
        }
        workTicks = 0;
        // LA TANDA SE PAGA CON UN TRONCO. Si se le acabó la leña por el camino (o se la quitó alguien), vuelve al
        // almacén a por más en vez de cocinar de la nada.
        if (!quemarLena()) {
            if (!irPorLena(level)) {
                restTicks = IDLE_REST_TICKS;
            }
            return;
        }
        // LA TANDA: cocina lo crudo Y hornea el pan (las dos cosas salen del mismo ahumador y del mismo tronco). El
        // anuncio es uno solo, para que la etiqueta del aldeano diga lo que de verdad ha hecho en la visita.
        int cocinadas = cocinar(level);
        int horneadas = hornear(level);
        if (cocinadas > 0 || horneadas > 0) {
            level.playSound(null, puesto, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, puesto.getX() + 0.5D, puesto.getY() + 1.0D,
                    puesto.getZ() + 0.5D, 6, 0.15D, 0.15D, 0.15D, 0.01D);
            VillageManager.ponerSuceso(villager, sucesoDeLaTanda(cocinadas, horneadas));
        }
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        puesto = null;
        yendoPorLena = false;
        restTicks = REST_TICKS;
        villager.getNavigation().stop();
    }

    // --- el fuego: la leña del almacén ------------------------------------------------------------------

    /** La casilla de DELANTE del ahumador (la cocina de la taberna): donde el cocinero puede estar de pie. */
    private BlockPos vistaDeLaCocina(ServerLevel level) {
        // I156 · NIVEL 3 (29-sep-2026): LA CASILLA DE LA COCINA SE **ELIGE**, NO SE SUPONE. Y SE ELIGE **UNA VEZ**.
        // Antes esto era `puesto + (0, 0, -1)`, A MANO, sin comprobar NADA. Medido: la celda fija estaba **DEBAJO DE
        // LA ESCALERA** (`destino=air encima=deepslate_tile_stairs`), así que no es casilla de pie y el planificador
        // no puede meterlo ahí: el cocinero se rendía con el ahumador al lado (12 rendiciones en 4 corridas).
        // Ahora: se recorren las cuatro casillas de al lado del ahumador **A LA COTA**, se exige **casilla de pie**, se
        // prefiere la de siempre (la de delante) si cumple, y si ninguna cumple se devuelve null -> no se cocina.
        // OJO CON EL COSTE, que ya me mordió (29-sep-2026): la primera versión preguntaba `hayVistaLibre` (un raycast)
        // por candidata **en cada `canUse`**, y eso **ralentizó el servidor**: `Buscando recambios` de los granjeros
        // pasó de **0-2** en ocho corridas a **39 y 17**, y apareció un `Can't keep up`. La vista se comprueba donde ya
        // se comprobaba —en el `tick`, con `enLaCocina`— y aquí **se cachea** el resultado, incluido el «no».
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        if (puesto.equals(vistaDeLaCocinaDe)) {
            if (vistaDeLaCocina == null && level.getGameTime() < vistaDeLaCocinaRechazadaHasta) {
                return null; // el «no» caduca (si no, un rechazo momentáneo dejaría al cocinero sin cocina para siempre)
            }
            if (vistaDeLaCocina != null && VillageManager.esCeldaDePie(level, vistaDeLaCocina)) {
                return vistaDeLaCocina;
            }
        }
        BlockPos preferida = new BlockPos(puesto.getX(), cota, puesto.getZ() - 1);
        BlockPos elegida = null;
        int[][] lados = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] lado : lados) {
            BlockPos p = new BlockPos(puesto.getX() + lado[0], cota, puesto.getZ() + lado[1]);
            if (!VillageManager.esCeldaDePie(level, p)) {
                continue; // los pies o la cabeza tapados: ahí no se puede estar
            }
            // (La versión con `hayRutaQueAlcanza` AQUÍ —una búsqueda de ruta por candidata— se probó y se RETIRÓ el
            // mismo día: el cocinero quedaba perfecto (`Yendo a la cocina` 1 y 0, y 23 piezas), pero el total de las
            // dos corridas se fue a **77 y 85** frente a 22-43 de la versión sin ella, por un BUCLE de la granjera en
            // el portón de la huerta (`Entrando a la huerta` 34 y 50, `pies=oak_fence_gate`, con `ruta=2 nodos
            // alcanza=SI`), que en las catorce corridas anteriores salía **0**. Ojo: el servidor NO iba lento (los
            // ticks alcanzados fueron idénticos, ~34.000), así que no era coste: era ese bucle. Ver I157.)
            if (p.equals(preferida)) {
                elegida = p;
                break; // la de siempre, si cumple el contrato
            }
            if (elegida == null) {
                elegida = p;
            }
        }
        vistaDeLaCocinaDe = puesto;
        vistaDeLaCocina = elegida;
        if (elegida == null) {
            vistaDeLaCocinaRechazadaHasta = level.getGameTime() + 100; // 5 s de «no» y se vuelve a mirar
        }
        return elegida;
    }

    /**
     * Manda al cocinero al <b>almacén</b> a por leña ({@code true} si hay a dónde ir). El punto es el de apoyo del
     * cobertizo (una casilla libre del suelo: al cofre no se navega, es sólido), el mismo que usa el herrero.
     * <p>
     * Lo <b>primero</b> que se mira es si queda leña <b>por encima de la reserva</b>
     * ({@link VillageStorage#hayLenaParaQuemar}): si no, el ahumador se queda apagado y el cocinero no cruza el
     * pueblo para nada (medido con el arnés: con el almacén en la reserva, el viaje era un paseo en balde).
     */
    private boolean irPorLena(ServerLevel level) {
        if (!VillageStorage.hayLenaParaQuemar(level, center)) {
            if (!avisadoSinLena) {
                avisadoSinLena = true;
                DevilRpg.LOGGER.info("[Village] El cocinero no cocina: el almacen no tiene lena por encima de la"
                                + " reserva de {} (aldea {}), asi que el ahumador se queda apagado",
                        VillageStorage.RESERVA_LENA, objectiveIndex);
            }
            VillageManager.ponerActividad(villager, "Sin lena para el ahumador");
            return false;
        }
        BlockPos almacen = VillageStorage.puntoDeApoyo(level, center);
        if (almacen == null || VillageManager.esPuntoFallido(villager, almacen)) {
            // Al almacén no llegó hace poco (I33: el cofre está tapado o rodeado): a esperar, no a empujar la pared.
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        target = almacen;
        yendoPorLena = true;
        stuckLena = 0;
        mejorDistanciaLena = Double.MAX_VALUE;
        VillageManager.ponerActividad(villager, "A por lena al almacen");
        return true;
    }

    /**
     * La pierna de la leña: camina al almacén y coge {@link #LENA_POR_VIAJE} troncos del excedente
     * ({@link VillageStorage#quitarLena}: nunca toca la reserva). Al cogerlos, la vuelta a la cocina se mide
     * <b>de cero</b> —el mismo bug que se midió en el clérigo: con el contador compartido, la caminata de vuelta
     * parecía "no acercarse" y el aldeano aparcaba su propio puesto a mitad de camino—.
     */
    private void tickDeLaLena(ServerLevel level) {
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (distancia > VillageStorage.ALCANCE_ALMACEN) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            VillageManager.ponerActividad(villager, "A por lena al almacen");
            if (distancia < mejorDistanciaLena - 0.5D) {
                mejorDistanciaLena = distancia;
                stuckLena = 0;
            } else if (++stuckLena >= STUCK_LENA) {
                DevilRpg.LOGGER.info("[Village] El cocinero se atasca yendo por lena al almacen ({}): lo deja por un"
                        + " rato", villager.blockPosition().toShortString());
                VillageManager.marcarPuntoFallido(villager, target);
                target = null;
                puesto = null;
                yendoPorLena = false;
                restTicks = IDLE_REST_TICKS;
            }
            return;
        }
        VillageManager.parar(villager);
        ItemStack lena = VillageStorage.quitarLena(level, center, LENA_POR_VIAJE);
        if (lena == null || lena.isEmpty()) {
            // Se la han llevado por delante (u otro aparato se comió el excedente): a esperar, sin cocinar de la nada.
            target = null;
            puesto = null;
            yendoPorLena = false;
            restTicks = IDLE_REST_TICKS;
            return;
        }
        ItemStack resto = guardarEnInventario(lena);
        if (!resto.isEmpty()) {
            VillageStorage.guardar(level, center, resto); // no le cupo (raro): de vuelta al almacén
        }
        DevilRpg.LOGGER.info("[Village] El cocinero: cogio {} tronco(s) del almacen para el ahumador (aldea {};"
                + " quedan {} en el almacen)", lena.getCount(), objectiveIndex,
                VillageStorage.cuentaLena(level, center));
        VillageManager.ponerSuceso(villager, "Cogi " + lena.getCount() + " tronco(s) para el ahumador");
        avisadoSinLena = false;
        yendoPorLena = false;
        target = vistaDeLaCocina(level); // I156: la casilla se ELIGE (a la cota, de pie y viendo el ahumador); null = no hay
        if (target == null) {
            restTicks = IDLE_REST_TICKS; // sin casilla de cocina, el goal se apaga y espera (no empuja paredes)
        }
        // La vuelta se mide de cero (el contador de la ida valía para el almacén, no para la cocina).
        mejorDistancia = Double.MAX_VALUE;
        stuckTicks = 0;
        mejorDistanciaLena = Double.MAX_VALUE;
        stuckLena = 0;
    }

    /** ¿Lleva leña encima (troncos en el zurrón)? */
    private boolean llevaLena() {
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize(); i++) {
            if (VillageStorage.esLena(mochila.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Quema <b>un tronco</b> de los que lleva encima (la tanda de cocina). {@code false} si no le queda ninguno. */
    private boolean quemarLena() {
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize(); i++) {
            ItemStack s = mochila.getItem(i);
            if (!VillageStorage.esLena(s)) {
                continue;
            }
            s.shrink(1);
            if (s.isEmpty()) {
                mochila.setItem(i, ItemStack.EMPTY);
            }
            return true;
        }
        return false;
    }

    /** Guarda en el zurrón del cocinero (lo que no quepa se devuelve). */
    private ItemStack guardarEnInventario(ItemStack stack) {
        ItemStack resto = stack.copy();
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize() && !resto.isEmpty(); i++) {
            ItemStack dentro = mochila.getItem(i);
            if (!dentro.isEmpty() && ItemStack.isSameItemSameComponents(dentro, resto)) {
                int espacio = dentro.getMaxStackSize() - dentro.getCount();
                int mete = Math.min(espacio, resto.getCount());
                dentro.grow(mete);
                resto.shrink(mete);
            }
        }
        for (int i = 0; i < mochila.getContainerSize() && !resto.isEmpty(); i++) {
            if (mochila.getItem(i).isEmpty()) {
                mochila.setItem(i, resto.copy());
                resto = ItemStack.EMPTY;
            }
        }
        return resto;
    }

    // --- la faena -----------------------------------------------------------------------------------

    /**
     * Cocina: saca de la despensa lo que se puede cocinar ({@link VillagePantry#sePuedeCocinar}) y devuelve el
     * equivalente cocinado ({@link VillagePantry#cocinar}). Una pieza por una: no se inventa comida, solo se
     * <b>transforma</b> la que ya había (y por eso el contador de la aldea sube al doble con la carne). El
     * <b>combustible</b> lo gasta antes {@link #quemarLena()} (un tronco por tanda).
     *
     * @return las piezas que ha cocinado (0 si no había nada crudo)
     */
    private int cocinar(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null || puesto == null || !level.getBlockState(puesto).is(Blocks.SMOKER)) {
            return 0;
        }
        int cocinadas = 0;
        // Se busca una pieza cruda, se saca del barril y se guarda su versión cocinada.
        for (ItemStack cruda : CRUDAS) {
            while (cocinadas < COCINAR_MAX
                    && VillagePantry.sacar(despensa, s -> s.is(cruda.getItem()), 1) == 1) {
                ItemStack hecha = VillagePantry.cocinar(new ItemStack(cruda.getItem(), 1));
                // Si no cabe lo cocinado (despensa llena de crudo, que no se apila con lo cocido) se DEVUELVE
                // crudo lo que se sacó: ni se pierde la pieza ni se cocina para tirarlo.
                if (hecha.isEmpty() || !VillagePantry.guardar(despensa, hecha).isEmpty()) {
                    VillagePantry.guardar(despensa, new ItemStack(cruda.getItem(), 1));
                    break;
                }
                cocinadas++;
            }
        }
        // Y SI EN LA DESPENSA NO HABÍA NADA, SE COCINAN LOS HUEVOS DEL ALMACÉN. El jugador: *"no veo que el cocinero
        // haga huevos estrellados"*, y la causa es el PUENTE granjero→despensa: el ganadero deja los huevos en el
        // almacén, el granjero los trae… pero cuando la despensa va llena de verdura y semillas (medido: 432
        // zanahorias, 155 patatas, 261 semillas) **no le caben** (`despensaNoTraga`) y el cocinero nunca los ve. Los
        // huevos son la materia prima del cocinero y el almacén es de donde salen, así que se fríen de allí y las
        // tortillas se dejan en el almacén, de donde el granjero las sube a la despensa como cualquier comida.
        if (cocinadas == 0) {
            Container almacen = VillageStorage.almacen(level, center);
            if (almacen != null) {
                while (cocinadas < COCINAR_MAX
                        && VillagePantry.sacar(almacen, s -> s.is(Items.EGG), 1) == 1) {
                    ItemStack hecha = VillagePantry.cocinar(new ItemStack(Items.EGG, 1));
                    // LAS TORTILLAS VAN A LA DESPENSA (lo aclaró el jugador: *"los huevos fritos se pueden quedar en la
                    // despensa, pues es su lugar para guardar"*): del almacén solo sale el huevo CRUDO.
                    if (hecha.isEmpty() || !VillagePantry.guardar(despensa, hecha).isEmpty()) {
                        VillageStorage.guardar(level, center, new ItemStack(Items.EGG, 1)); // no cabe: se devuelve crudo
                        break;
                    }
                    cocinadas++;
                }
            }
        }
        if (cocinadas > 0) {
            DevilRpg.LOGGER.info("[Village] El cocinero: {} pieza(s) cocinadas con un tronco del almacen (aldea {})",
                    cocinadas, objectiveIndex);
        }
        return cocinadas;
    }

    /**
     * <b>HORNEA EL PAN</b> con el trigo que el granjero ha dejado en la despensa: la receta de vanilla
     * ({@link VillagePantry#WHEAT_PER_BREAD} de trigo por hogaza), hasta {@link #HORNEAR_MAX} hogazas por visita, y
     * <b>sin tocar la {@link VillagePantry#RESERVA_DE_TRIGO_PARA_CRIAR reserva de trigo para criar}</b> (el ganadero
     * necesita 2 para criar vacas y ovejas; sin cría no hay cuero ni lana).
     * <p>
     * Antes lo hacía el granjero en la despensa; el jugador lo mandó cambiar: *"el cocimiento de los panes no lo
     * debería hacer el granjero sino el COCINERO"*. El pan es la mejor ración de la aldea
     * ({@link VillagePantry#FOOD_PER_BREAD} puntos) y sale del mismo ahumador, así que se hornea en la misma tanda que
     * la cocina (un solo tronco para las dos cosas).
     * <p>
     * Si el pan no cabe en la despensa (llena de verdura y semillas) <b>se devuelve el trigo</b>: igual que en
     * {@link #cocinar}, no se destruye materia prima por hornear de más.
     *
     * @return las hogazas que ha horneado (0 si no había trigo de sobra)
     */
    private int hornear(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null || puesto == null || !level.getBlockState(puesto).is(Blocks.SMOKER)) {
            return 0;
        }
        int horneadas = 0;
        while (horneadas < HORNEAR_MAX
                && VillagePantry.contar(despensa, s -> s.is(Items.WHEAT))
                    >= VillagePantry.WHEAT_PER_BREAD + VillagePantry.RESERVA_DE_TRIGO_PARA_CRIAR
                && VillagePantry.sacar(despensa, s -> s.is(Items.WHEAT), VillagePantry.WHEAT_PER_BREAD)
                    == VillagePantry.WHEAT_PER_BREAD) {
            if (!VillagePantry.guardar(despensa, new ItemStack(Items.BREAD)).isEmpty()) {
                VillagePantry.guardar(despensa, new ItemStack(Items.WHEAT, VillagePantry.WHEAT_PER_BREAD));
                break; // despensa llena: el trigo vuelve al barril y se hornea cuando haya hueco
            }
            horneadas++;
        }
        if (horneadas > 0) {
            DevilRpg.LOGGER.info("[Village] El cocinero: horneo {} pan(es) con el trigo de la despensa (aldea {};"
                    + " quedan {} de trigo, reserva para criar {})", horneadas, objectiveIndex,
                    VillagePantry.contar(despensa, s -> s.is(Items.WHEAT)),
                    VillagePantry.RESERVA_DE_TRIGO_PARA_CRIAR);
        }
        return horneadas;
    }

    /** El rótulo de la visita: lo que ha cocinado y lo que ha horneado, en una sola línea. */
    private static String sucesoDeLaTanda(int cocinadas, int horneadas) {
        if (cocinadas > 0 && horneadas > 0) {
            return "Cocino " + cocinadas + " pieza(s) y horneo " + horneadas + " pan(es)";
        }
        if (horneadas > 0) {
            return "Horneo " + horneadas + " pan(es)";
        }
        return "Cocino " + cocinadas + " piezas";
    }

    /** ¿Hay trigo en la despensa <b>por encima de la reserva de cría</b>, o sea al menos una hogaza que hornear? */
    private boolean hayTrigoQueHornear(ServerLevel level) {
        return VillagePantry.contar(VillagePantry.despensa(level, center), s -> s.is(Items.WHEAT))
                >= VillagePantry.WHEAT_PER_BREAD + VillagePantry.RESERVA_DE_TRIGO_PARA_CRIAR;
    }

    /** Lo que se puede cocinar, en el orden en que el cocinero lo va sacando del barril. */
    private static final List<ItemStack> CRUDAS = List.of(
            new ItemStack(Items.BEEF), new ItemStack(Items.PORKCHOP), new ItemStack(Items.CHICKEN),
            new ItemStack(Items.MUTTON), new ItemStack(Items.RABBIT), new ItemStack(Items.COD),
            new ItemStack(Items.SALMON), new ItemStack(Items.POTATO),
            // Y LOS HUEVOS de las gallinas del corral: el cocinero los hace estrellados (lo pidió el jugador) y, como
            // valen ración completa en la despensa, el pueblo entero come mejor con ellos.
            new ItemStack(Items.EGG));

    private int contarCrudoEnLaDespensa(ServerLevel level) {
        return VillagePantry.contar(VillagePantry.despensa(level, center), VillagePantry::sePuedeCocinar);
    }

    private void irAlDestino() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
        }
    }
}
