package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * El <b>MINERO</b> de la aldea (etapa I): el aldeano de oficio {@code MASON} cuya faena es <b>la mina del pueblo</b>
 * ({@link VillageGenerator#asegurarLaMinaDelPueblo}). Lo pidió el jugador: *"mejor haz otra profesión que sea de
 * minero, y que excave el suelo hacia abajo, haciendo túneles, andamiajes de soporte, escaleras en espiral, todo para
 * sacar minerales; luego en su lugar de trabajo fundirlos y el cobblestone que recoja, que lo filtre en agua para
 * sacar algunos pedernales"*.
 * <p>
 * <b>Lo que hace, en orden:</b>
 * <ol>
 *   <li><b>Baja el caracol</b> celda a celda: pica el hueco de paso (tres celdas, I26), pone la <b>pieza</b> de esa
 *       celda (losa o adoquín, ver {@link VillageGenerator#piezaDelCaracol}), rellena el suelo si está hueco, y cada
 *       {@link VillageGenerator#MINA_SOPORTE_CADA} escalones levanta un <b>marco de madera</b> (dos postes en las
 *       paredes y su viga) con los <b>tablones del almacén</b>.</li>
 *   <li>Cada {@link VillageGenerator#MINA_GALERIA_CADA} bloques de descenso <b>abre una galería</b> de
 *       {@link VillageGenerator#MINA_GALERIA_LARGO} celdas en cruz, con su marco cada
 *       {@link VillageGenerator#MINA_GALERIA_SOPORTE_CADA} celdas.</li>
 *   <li><b>Saca el mineral</b>: lo que pica lo recoge (adoquín, tierra, grava y <b>las vetas</b>, con el botín de su
 *       mineral) y además pica las <b>vetas que se le quedan a la vista en la pared</b> del túnel, que es lo que hace
 *       que la mina "saque minerales" de verdad y no solo piedra.</li>
 *   <li><b>Funde en su horno</b> ({@link VillageGenerator#hornoDelMinero}) el hierro, el cobre y el oro crudos: un
 *       lingote por mineral, con <b>carbón</b> de lo que él mismo ha picado (o leña del almacén, con la reserva de
 *       {@link VillageStorage#RESERVA_LENA} como los herreros).</li>
 *   <li><b>Filtra el adoquín en su balsa</b> ({@link VillageGenerator#balsaDelMinero}): {@value #ADOQUIN_POR_PEDERNAL}
 *       adoquines por <b>pedernal</b>. Es la respuesta del jugador al cuello de botella de I101 (la aldea no producía
 *       pedernal y las flechas dependían de lo que trajera él).</li>
 *   <li><b>Hace antorchas</b> (carbón + palo) y las va dejando por el túnel: una mina a oscuras es un criadero de
 *       bichos, y el pueblo no puede permitirse tener monstruos naciendo <b>dentro</b> de la muralla.</li>
 *   <li><b>Sube y lo baja todo al almacén</b> (lingotes, pedernal, carbón, gemas y el adoquín que sobre), de donde el
 *       herrero de herramientas saca los picos y el flechero las flechas.</li>
 * </ol>
 * <p>
 * <b>Su mina es SUYA</b> (I102): lo que cava no entra en el plano de la aldea (el obrero no lo repone) y el nivelado
 * y el tapagujeros del suelo lo excluyen. La <b>caseta</b>, en cambio, es del pueblo y la mantiene el obrero.
 * <p>
 * Va a la prioridad de los oficios ({@value #PRIORIDAD}), como el granjero o el herrero, y como todos los aldeanos
 * <b>come</b> ({@link VillageManager#tieneHambre}) y <b>duerme</b> ({@link VillageManager#estaDescansando}): en esas
 * dos franjas no empieza faena y el pueblo se encarga de él (la taberna y su cama).
 */
public class VillagerMinerGoal extends Goal {

    /** Prioridad: la de los oficios (la misma que el granjero, el pescador o los herreros). */
    public static final int PRIORIDAD = 4;

    /** Ticks que tarda en <b>picar y colocar una celda</b> del túnel (se le ve dar el pico). */
    private static final int TICKS_POR_CELDA = 25;
    /** Ticks por <b>faena del taller</b> (fundir, colar o hacer antorchas) y por carga/descarga del almacén. */
    private static final int TICKS_POR_FAENA = 40;
    private static final int REST_TICKS = 10;
    private static final int IDLE_REST_TICKS = 100;
    /** Si no logra acercarse en este tiempo, abandona (invariante I3: atascado = no acercarse). */
    private static final int STUCK_LIMIT = 240;
    private static final float VELOCIDAD = 0.6F;
    /** Alcance real del juego a la celda que pica (I97: el alcance no es "de vista", es de 3,5 para un aldeano). */
    private static final double REACH = 3.5D;
    /** Radio en el que se mira si OTRO aldeano tiene el puesto (vanilla reclama los puestos a menos de 48). */
    private static final double RADIO_PUESTO = 48.0D;
    /** Si se aleja más de esto del centro de la aldea, deja de trabajar. */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 16.0D;

    /** Celdas que cava antes de subir a su taller y al almacén (16 = media vuelta del caracol, 8 bloques). */
    private static final int CELDAS_POR_VIAJE = 32;
    /** Antorchas que deja puestas por el túnel: una cada X celdas. */
    private static final int CELDAS_POR_ANTORCHA = 8;
    /** Cuántas <b>vetas a la vista</b> pica como mucho por celda (lo que se ve en la pared del túnel). */
    private static final int VETAS_POR_CELDA = 2;
    /** El jugador: **{@value #ADOQUIN_POR_PEDERNAL} adoquines por un pedernal**, en la balsa de su caseta. */
    public static final int ADOQUIN_POR_PEDERNAL = 4;
    /** Adoquín que junta antes de subir a colarlo: {@value} (una pila: {@value} / 4 = 16 pedernales por viaje). */
    private static final int ADOQUIN_PARA_SUBIR = 64;
    /**
     * Mineral crudo que junta antes de subir a fundirlo. <b>No se sube por UNA veta</b>: el viaje de subida y bajada
     * por el caracol se paga solo (medido: con una veta por viaje, dos celdas de galería costaban minuto y medio y
     * casi todo era andar). Se funde igual todo lo que lleve cuando sube por cualquier otro motivo.
     */
    private static final int MINERAL_PARA_SUBIR = 3;
    /** Con estos huecos libres o menos en el zurrón, se sube a descargar (si no, lo sacado se queda por el suelo). */
    private static final int HUECOS_LIBRES_MINIMOS = 2;
    /**
     * Ticks que el minero espera su ración antes de volver al tajo. Con hambre y comida en el pueblo se para a comer
     * (lo pidió el jugador), pero <b>no sin límite</b>: si la taberna no le da nada, vuelve a la mina.
     */
    private static final int ESPERA_DE_COMIDA_MAXIMA = 400;
    /** Pedernal que quiere tener el pueblo en el almacén antes de ponerse a colar más. */
    private static final int OBJETIVO_PEDERNAL = 16;
    /** Lo que se lleva de una vez del almacén y lo que deja de reserva para el herrero de herramientas. */
    private static final int TABLONES_POR_VIAJE = 16;
    private static final int TABLONES_RESERVA = 8;
    /** Tablones que cuesta un <b>marco</b> (caracol o galería): dos postes de dos bloques (4) y su viga (1). */
    private static final int TABLONES_POR_MARCO = 5;
    private static final int ANTORCHAS_POR_VIAJE = 16;
    private static final int CARBON_POR_VIAJE = 8;
    private static final int PALOS_POR_VIAJE = 8;
    /** Una antorcha = un carbón + un palo (la receta de vanilla), y salen cuatro. */
    private static final int CARBON_POR_ANTORCHA = 1;
    private static final int ANTORCHAS_POR_CARBON = 4;
    /**
     * Cuántas celdas <b>seguidas</b> de agua o lava sella antes de dar la mina por terminada. Una bolsa de agua se
     * tapa con adoquín y el túnel sigue; un <b>mar</b> (un acuífero, el océano) no se tapa, y seguir cavando ahí
     * sería inundar la mina entera. Lo pidió el jugador: *"que selle las bolsas de agua o lava; si es un mar, que
     * pare"*.
     */
    private static final int SELLOS_MAXIMOS = 12;

    private enum Fase { RECOGER, CAVAR, TALLER, ENTREGAR }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    private Fase fase = Fase.RECOGER;
    /** Dónde tiene que <b>pararse</b> (una casilla que se pisa: el almacén, la caseta o la última celda hecha). */
    @Nullable
    private BlockPos destino;
    /** La celda que está <b>picando</b> ahora (el hueco de paso que va a abrir). */
    @Nullable
    private BlockPos celdaDeTrabajo;
    /** El paso del caracol que le toca (se lee del mundo al empezar la faena). */
    private int paso;
    /** La celda de la galería que le toca (0 = le toca la pieza del caracol). */
    private int indiceDeGaleria;
    /** Celdas que lleva cavadas desde la última subida al taller. */
    private int celdasCavadas;
    /** Ticks que lleva esperando su ración (con hambre y con comida en el pueblo): ver {@link #ESPERA_DE_COMIDA_MAXIMA}. */
    private int hambreEsperando;
    /** Celdas seguidas de agua/lava selladas (al pasar de {@link #SELLOS_MAXIMOS}, la mina se cierra). */
    private int sellosSeguidos;
    /** La celda que quedó <b>sellada</b> (agua/lava): en la vuelta siguiente se pica el adoquín y el túnel sigue. */
    @Nullable
    private BlockPos selloPendiente;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    /** Última transición anotada en el log: así se ve el ciclo entero sin escribir una línea por tick. */
    private String ultimaAnotacion = "";

    public VillagerMinerGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        // El oficio se lee EN VIVO (no se cachea): el pueblo reparte oficios y este goal no se vuelve a construir
        // para el mismo aldeano (ver `VillageManager.asegurarGoalDelMinero`), que es la trampa que ya costó un bug
        // en `VillagerSmithGoal`.
        if (villager.getVillagerData().getProfession() != VillagerProfession.MASON) {
            return false;
        }
        // En plena refriega nadie cava, y de noche el minero duerme como todos. Y SI TIENE HAMBRE, primero come: lo
        // pidió el jugador ("debe tener también su hora de comida y descansar como todo aldeano").
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        // ...PERO LA COMIDA NO PUEDE PARAR LA MINA PARA SIEMPRE. Si tiene hambre y el pueblo tiene comida, espera su
        // ración; si en {@link #ESPERA_DE_COMIDA_MAXIMA} ticks no ha comido (la taberna no le da nada: medido con el
        // arnés, se quedaba plantado en la puerta con la mina a medias), vuelve al tajo y come en la siguiente
        // ocasión. Sin este tope, una aldea con la comida justa dejaba la mina parada y al minero sin comer.
        if (VillageManager.tieneHambre(level, villager) && VillagePantry.comida(level, center) > 0
                && hambreEsperando < ESPERA_DE_COMIDA_MAXIMA) {
            hambreEsperando++;
            return false;
        }
        hambreEsperando = 0;
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            return false; // se ha ido lejos del pueblo
        }
        // La caseta: sin cortapiedras no hay puesto (el juego le borra el oficio, I23/I31) y sin horno ni balsa no
        // hay taller. Se pregunta UNA vez por intento: dentro busca bloques a la cota.
        BlockPos puesto = VillageGenerator.puestoDelMinero(level, center);
        if (puesto == null) {
            restTicks = IDLE_REST_TICKS;
            return false; // todavía no hay caseta en esta aldea
        }
        reclamarElPuesto(level, puesto);
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        // ¿HAY QUE SUBIR? Cuando lleva media vuelta cavada, cuando tiene mineral que fundir o colar, o cuando la
        // mina ya está cerrada (el tope). Así el viaje de subida se paga una vez cada media vuelta y no por celda.
        paso = VillageGenerator.progresoDeLaMina(level, center, nivel);
        boolean procesa = hayQueSubir();
        boolean terminada = VillageGenerator.laMinaLlegoAlTope(level, center, nivel, paso);
        boolean tocaSubir = terminada || celdasCavadas >= CELDAS_POR_VIAJE || procesa;
        // EL ALMACÉN, SI HACE FALTA: sin pico no hay mina (y el que gasta lo forja el herrero de herramientas, que
        // para eso está: lo pidió el jugador), y sin tablones no hay marcos ni sin carbón antorchas. Se pasa por el
        // almacén al empezar la vuelta (o en cuanto se le rompe el pico, aunque esté abajo).
        if (!tienePico() || (tocaSubir && leFaltaDelAlmacen())) {
            fase = Fase.RECOGER;
            destino = VillageStorage.puntoDeApoyo(level, center);
            celdaDeTrabajo = null; // el alcance se mide a la casilla donde trabaja, no a la mina
            return comprobarDestino();
        }
        if (tocaSubir) {
            fase = procesa ? Fase.TALLER : Fase.ENTREGAR;
            destino = procesa ? VillageGenerator.puntoDeApoyoDeLaCaseta(level, center)
                    : VillageStorage.puntoDeApoyo(level, center);
            celdaDeTrabajo = null;
            return comprobarDestino();
        }
        fase = Fase.CAVAR;
        return prepararElPicado(level, nivel);
    }

    /**
     * ¿Le toca <b>subir</b> a procesar lo que lleva, en vez de seguir cavando? Se sube con una <b>tanda hecha</b>, no
     * con la primera piedra: sin esto el minero subía al taller y al almacén <b>después de CADA celda</b> (medido con
     * el arnés: 16 celdas en 136 s, casi todo el tiempo andando el caracol de arriba abajo) en vez de cavar.
     * <p>
     * Los disparadores: <b>mineral crudo</b> que fundir, <b>adoquín</b> de sobra para sacar pedernal
     * ({@link #ADOQUIN_PARA_SUBIR}), que se ha quedado <b>sin antorchas</b> (una mina a oscuras cría bichos dentro de
     * la muralla) y el <b>zurrón lleno</b> (si no, lo que saque se queda por el suelo del túnel).
     */
    private boolean hayQueSubir() {
        if (cuantosEnInventario(VillagerMinerGoal::esMineralCrudo) >= MINERAL_PARA_SUBIR
                || cuantosEnInventario(Items.COBBLESTONE) >= ADOQUIN_PARA_SUBIR) {
            return true;
        }
        if (celdasCavadas > 0 && cuantosEnInventario(Items.TORCH) <= 0) {
            return true;
        }
        int libres = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            if (villager.getInventory().getItem(i).isEmpty()) {
                libres++;
            }
        }
        // Y EL ZURRÓN LLENO SOLO CUENTA SI PUEDE VACIARLO. Antes bastaba con tener dos huecos libres, y el minero lleva
        // SIEMPRE encima sus recados (pico, tablones, palos, carbón y leña: seis o siete huecos de los ocho), así que
        // subía a "entregar" una y otra vez SIN ENTREGAR NADA —los recados no los suelta— y la mina no avanzaba ni una
        // celda. Lo vio el jugador: *"el minero aparece como trabajando dentro de su choza pero realmente no hace
        // nada... se queda sólo entrando y saliendo de su choza"*. Con esto, "lleno" solo sube si lleva algo que el
        // almacén quiera (mineral, adoquín, pedernal, piedras...).
        return libres <= HUECOS_LIBRES_MINIMOS && hayParaEntregar();
    }

    /**
     * ¿Lleva algo que el almacén <b>quiera</b> (o sea, algo que no sea de los recados)? Es lo que decide si "tener el
     * zurrón lleno" es motivo para subir: lleno de tablones y palos —que se queda para trabajar— no lo es.
     */
    private boolean hayParaEntregar() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!s.isEmpty() && s.getCount() > cuantoSeQueda(s)) {
                return true; // lleva DE SOBRA de algo (aunque sea de los recados): eso se deja en el almacén
            }
        }
        return false;
    }

    /**
     * ¿Le falta algo que solo hay en el <b>almacén</b>? Los <b>tablones</b> de los marcos (que los asierra el
     * herrero de herramientas desde los troncos del leñador), el <b>carbón, los palos y la leña</b> de las antorchas
     * y del horno, y el <b>pico</b>. Se pregunta al empezar la vuelta, no por celda: una vuelta al almacén son más
     * de cien bloques de caracol.
     */
    private boolean leFaltaDelAlmacen() {
        if (cuantosEnInventario(Items.OAK_PLANKS) < TABLONES_POR_MARCO) {
            return true; // sin madera no hay marcos
        }
        // Vale el carbón Y el carbón vegetal (VillageStorage.esCarbon), y también la LEÑA: con un tronco se fabrica él
        // el carbón vegetal en el horno de la caseta (ver `trabajarEnElTaller`). Sin contar la leña, un pueblo sin
        // carbón dejaba al minero en BUCLE yendo y viniendo del almacén a por un carbón que no existe (medido con el
        // arnés: 59 viajes "Cargando material" y la mina parada en el paso 19).
        if (cuantosEnInventario(Items.TORCH) <= 0 && !((cuantosEnInventario(VillageStorage::esCarbon) > 0
                || cuantosEnInventario(VillageStorage::esLena) > 0) && cuantosEnInventario(Items.STICK) > 0)) {
            return true; // sin antorchas ni con qué hacerlas (carbón, un tronco para el carbón vegetal, o palos): la mina se queda a oscuras
        }
        return hayMineralCrudo() && cuantosEnInventario(VillageStorage::esCarbon) <= 0
                && cuantosEnInventario(VillageStorage::esLena) <= 0;
    }

    /**
     * Prepara la faena de picar: cuántas celdas del caracol están hechas ({@link VillageGenerator#progresoDeLaMina},
     * que lo <b>mira en el mundo</b>) y cuál es la que le toca. Si la mina ya está cerrada no hay faena: espera.
     * <p>
     * La <b>galería</b> se cava <b>antes</b> de poner la pieza de su celda del caracol, y su avance también se mira
     * en el mundo ({@link VillageGenerator#progresoDeLaGaleria}): así, si le pillan a media galería (la noche, un
     * asedio, el jugador apagando el servidor), al volver <b>sigue donde iba</b> en vez de dejar túneles a medias.
     */
    private boolean prepararElPicado(ServerLevel level, int nivel) {
        paso = VillageGenerator.progresoDeLaMina(level, center, nivel);
        if (VillageGenerator.laMinaLlegoAlTope(level, center, nivel, paso)) {
            restTicks = IDLE_REST_TICKS;
            destino = null;
            celdaDeTrabajo = null;
            return false; // la mina ya está hecha (fondo, un mar de agua/lava o un tope)
        }
        int galeriaHecha = VillageGenerator.abreGaleria(paso)
                ? VillageGenerator.progresoDeLaGaleria(level, center, nivel, paso) : 0;
        // ¿La PIEZA de esa celda ya está puesta? Es lo que distingue "le toca la celda del caracol" de "le toca la
        // galería de esa celda": la pieza se pone ANTES de cavar la galería, porque es la que deja el medio bloque por
        // el que se entra y se sale del túnel (el suelo de la galería va un bloque por debajo de la celda anterior del
        // caracol). Con el orden viejo —galería primero— el minero caía un bloque entero y no podía volver a subir.
        boolean piezaPuesta = level.getBlockState(VillageGenerator.celdaDelCaracol(center, nivel, paso))
                .is(VillageGenerator.piezaDelCaracol(paso).getBlock());
        if (VillageGenerator.abreGaleria(paso) && piezaPuesta
                && galeriaHecha < VillageGenerator.MINA_GALERIA_LARGO) {
            // LA GALERÍA: se cava desde su celda del caracol, que YA está hecha (por ahí se entra y se sale).
            indiceDeGaleria = galeriaHecha + 1;
            celdaDeTrabajo = VillageGenerator.celdaDeLaGaleria(center, nivel, paso, indiceDeGaleria);
            destino = indiceDeGaleria == 1 ? VillageGenerator.celdaDelCaracol(center, nivel, paso)
                    : VillageGenerator.celdaDeLaGaleria(center, nivel, paso, indiceDeGaleria - 1);
        } else {
            indiceDeGaleria = 0;
            celdaDeTrabajo = VillageGenerator.celdaDelCaracol(center, nivel, paso);
            destino = paso == 0 ? VillageGenerator.bocaDeLaMina(center, nivel)
                    : VillageGenerator.celdaDelCaracol(center, nivel, paso - 1);
        }
        // SI LA MINA ESTÁ TAPADA POR ALGO DEL PUEBLO (una casa, la pared de su caseta, lo que puso el jugador), el
        // minero NO lo toca: para y lo deja anotado. Es la misma lección de I24/I27 (lo construido no se cava) y sin
        // esta guarda el minero le abriría un boquete a su propia caseta, que está pegada al anillo del caracol.
        if (!VillageGenerator.elMineroPuedePicar(level.getBlockState(celdaDeTrabajo))) {
            anotar("la mina esta tapada en " + celdaDeTrabajo.toShortString());
            destino = null;
            celdaDeTrabajo = null;
            restTicks = IDLE_REST_TICKS * 20;
            return false;
        }
        return comprobarDestino();
    }

    /** Si al sitio donde va no llegó hace poco (I33), no se queda empujando la misma pared: lo deja por un rato. */
    private boolean comprobarDestino() {
        if (destino != null && VillageManager.esPuntoFallido(villager, destino)) {
            destino = null;
            celdaDeTrabajo = null;
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        return destino != null;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        irAlDestino();
    }

    @Override
    public boolean canContinueToUse() {
        if (destino != null && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el sitio al que no llegó se apunta para no volver a por él en
            // bucle (es lo que dejaba al aldeano empujando la misma pared para siempre).
            VillageManager.marcarPuntoFallido(villager, destino);
            return false;
        }
        return destino != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (destino == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
        // El alcance se mide a lo que va a PICAR (la celda del túnel), no a la casilla donde se para: en el caracol
        // el minero pica la celda de al lado, a un bloque de donde está.
        BlockPos referencia = celdaDeTrabajo != null ? celdaDeTrabajo : destino;
        double distancia = Math.sqrt(villager.distanceToSqr(referencia.getX() + 0.5D, referencia.getY() + 0.5D,
                referencia.getZ() + 0.5D));
        if (distancia > REACH) {
            // EL TIRÓN INTERMEDIO: desde la mina el almacén queda a más de lo que alcanza el planificador (medido: 56
            // bloques de radio alrededor del aldeano), y sin ruta el aldeano empujaba en línea recta — el jugador lo
            // vio atorado en el segundo piso de la taberna con la etiqueta "Yendo al almacen".
            irHaciaElDestino(level);
            VillageManager.ponerActividad(villager, verboDeCamino());
            anotar("yendo: " + verboDeCamino());
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < (fase == Fase.CAVAR ? TICKS_POR_CELDA : TICKS_POR_FAENA)) {
            VillageManager.ponerActividad(villager, verboActual());
            anotar("faena: " + verboActual());
            return;
        }
        workTicks = 0;
        switch (fase) {
            case RECOGER -> {
                if (!recoger(level)) {
                    destino = null; // no hay material (o se lo ha llevado otro): se vuelve a decidir
                    restTicks = IDLE_REST_TICKS;
                } else {
                    // CARGADO: ahora HAY que recalcular la faena. Sin esto el `destino` se quedaba en el almacén y el
                    // minero se pasaba la vida "Picando" de pie en el cofre (medido con el arnés: la etiqueta decía
                    // `Picando -> 517, 63, 666` y no se movía ni cavaba una celda).
                    fase = Fase.CAVAR;
                    if (!prepararElPicado(level, VillageGenerator.cotaDeLaPlaza(level, center))) {
                        destino = null;
                    }
                }
            }
            case CAVAR -> cavar(level);
            case TALLER -> {
                if (!trabajarEnElTaller(level)) {
                    fase = Fase.ENTREGAR;
                    destino = VillageStorage.puntoDeApoyo(level, center);
                }
            }
            case ENTREGAR -> {
                entregar(level);
                destino = null; // faena acabada: se vuelve a decidir en el siguiente `canUse`
            }
        }
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        restTicks = REST_TICKS;
        if (destino != null && VillageManager.esPuntoFallido(villager, destino)) {
            destino = null;
            celdaDeTrabajo = null;
        }
    }

    /** Lo que hace AHORA (ya en el sitio): va en la etiqueta del aldeano y en el log. */
    private String verboActual() {
        return switch (fase) {
            case RECOGER -> "Cargando material";
            case CAVAR -> "Picando";
            case TALLER -> "En el taller";
            case ENTREGAR -> "Bajando lo sacado";
        };
    }

    /** Lo que está haciendo <b>mientras va</b> de un sitio a otro (si no, la etiqueta cae al texto genérico). */
    private String verboDeCamino() {
        return switch (fase) {
            case RECOGER -> "Yendo al almacen";
            case CAVAR -> "Bajando a la mina";
            case TALLER -> "Subiendo al taller";
            case ENTREGAR -> "Subiendo al almacen";
        };
    }

    /**
     * Deja el ciclo del minero en el log <b>una vez por transición</b> (no por tick: esto es un aldeano caminando
     * 200 bloques por un caracol).
     */
    private void anotar(String clave) {
        if (clave.equals(ultimaAnotacion)) {
            return;
        }
        ultimaAnotacion = clave;
        DevilRpg.LOGGER.info("[Village] El minero: {} ({} -> {})", clave, verboActual(),
                destino == null ? "sin destino" : destino.toShortString());
    }

    @Override
    public void stop() {
        destino = null;
        celdaDeTrabajo = null;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    // --- el puesto de trabajo (lo que el juego llama JOB_SITE) ---------------------------------------

    /**
     * Le pone al minero <b>su puesto en el cerebro</b> ({@code JOB_SITE}) y le <b>toma el ticket</b> del punto de
     * interés, que es lo que hace que el juego lo tenga por un aldeano con puesto de trabajo (sin él el cerebro no
     * registra la actividad de trabajar y el aldeano se queda en IDLE "Paseando": ver {@code VillagerSmithGoal}
     * para el caso medido). Y, como allí, un ticket <b>cogido sin dueño</b> es un ticket perdido: se libera y se
     * vuelve a tomar; a nadie se le quita el suyo.
     */
    private void reclamarElPuesto(ServerLevel level, BlockPos puesto) {
        Optional<GlobalPos> mio = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (mio.isPresent() && mio.get().pos().equals(puesto)) {
            return; // ya es suyo
        }
        // Y SI YA TIENE UN CORTAPIEDRAS (el suyo o el que le haya dado el latido), TAMPOCO SE TOCA: si en la aldea
        // hay otro cortapiedras, el latido y este goal se lo quitaban y se lo daban uno a otro <b>en cada pasada</b>
        // (medido: `el puesto de trabajo en 459, 63, 656 tenia el ticket PERDIDO … liberado y reclamado` cada 10 s,
        // para siempre). El `JOB_SITE` solo sirve para que el juego le registre la actividad de trabajar; la faena la
        // manda este goal, que trabaja en SU caseta.
        if (mio.isPresent() && level.getBlockState(mio.get().pos()).is(Blocks.STONECUTTER)) {
            return;
        }
        for (Villager otro : level.getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(puesto).inflate(RADIO_PUESTO))) {
            if (otro == villager) {
                continue;
            }
            Optional<GlobalPos> suyo = otro.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (suyo.isPresent() && suyo.get().pos().equals(puesto)) {
                return; // el puesto es de otro: no se le quita a nadie
            }
        }
        PoiManager poi = level.getPoiManager();
        Predicate<Holder<PoiType>> vale = villager.getVillagerData().getProfession().heldJobSite();
        BiPredicate<Holder<PoiType>, BlockPos> cual = (tipo, pos) -> pos.equals(puesto);
        if (poi.take(vale, cual, puesto, 1).isEmpty()) {
            poi.release(puesto); // ticket PERDIDO: se suelta y se vuelve a intentar
            if (poi.take(vale, cual, puesto, 1).isEmpty()) {
                return; // no hay manera (el chunk no está cargado...): se reintenta en el siguiente canUse
            }
            DevilRpg.LOGGER.info("[Village] El minero: el puesto de trabajo en {} tenia el ticket PERDIDO (cogido y"
                    + " sin dueno): liberado y reclamado", puesto.toShortString());
        }
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), puesto));
        anotar("puesto reclamado: " + puesto.toShortString());
    }

    // --- las faenas ---------------------------------------------------------------------------------

    /**
     * Se lleva del almacén lo que necesita para la faena: <b>el pico</b> (sin él no hay mina), los <b>tablones</b>
     * de los marcos (dejando la {@link #TABLONES_RESERVA reserva} del herrero de herramientas, que es quien los
     * asierra), el <b>carbón y los palos</b> de las antorchas y la <b>leña</b> del horno. Lo pidió el jugador: *"el
     * material necesario para construir, como tablones, y las herramientas, que las saque del almacén"*.
     */
    private boolean recoger(ServerLevel level) {
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen == null) {
            return false;
        }
        boolean algo = false;
        // 1) EL PICO (al inventario, y a la mano el que va a usar).
        if (!tienePico()) {
            ItemStack pico = VillageStorage.quitar(level, center, s -> s.is(Items.IRON_PICKAXE)
                    || s.is(Items.DIAMOND_PICKAXE) || s.is(Items.NETHERITE_PICKAXE)
                    || s.is(Items.STONE_PICKAXE) || s.is(Items.WOODEN_PICKAXE), 1);
            if (pico == null || pico.isEmpty()) {
                DevilRpg.LOGGER.info("[Village] El minero: no hay pico en el almacen (lo forja el herrero de"
                        + " herramientas: 3 lingotes de hierro y 2 palos): espera");
                return false;
            }
            cogerEnLaMano(pico);
            algo = true;
            anotar("pico nuevo: " + pico.getItem());
        }
        // 2) LOS TABLONES de los marcos (nunca por debajo de la reserva del herrero).
        int tablones = cuantosEnInventario(Items.OAK_PLANKS);
        int libres = VillageStorage.cuenta(level, center, s -> s.is(Items.OAK_PLANKS)) - TABLONES_RESERVA;
        if (tablones < TABLONES_POR_VIAJE && libres > 0) {
            algo |= sacarDelAlmacen(level, almacen, s -> s.is(Items.OAK_PLANKS),
                    Math.min(TABLONES_POR_VIAJE - tablones, libres));
        }
        // 3) CARBÓN y PALOS para las antorchas (el carbón lo saca él mismo de la mina; esto es para arrancar). Vale el
        //    carbón y el CARBÓN VEGETAL (`VillageStorage.esCarbon`).
        if (cuantosEnInventario(Items.TORCH) < ANTORCHAS_POR_VIAJE) {
            algo |= sacarDelAlmacen(level, almacen, VillageStorage::esCarbon, CARBON_POR_VIAJE / 2);
            algo |= sacarDelAlmacen(level, almacen, s -> s.is(Items.STICK), PALOS_POR_VIAJE / 2);
        }
        // 4) LEÑA para el horno: para fundir y para hacer CARBÓN VEGETAL (1 tronco -> 1 carbón vegetal, la receta de
        //    vanilla, que es con lo que se hacen las antorchas cuando no hay carbón de veta). Sin comerse la reserva
        //    de leña, que es la misma regla que la fragua del herrero (`quitarLena` respeta `RESERVA_LENA`).
        boolean necesitaCarbon = cuantosEnInventario(VillageStorage::esCarbon) <= 0
                && cuantosEnInventario(Items.TORCH) < ANTORCHAS_POR_VIAJE;
        if (cuantosEnInventario(VillageStorage::esCarbon) <= 0 && cuantosEnInventario(VillageStorage::esLena) <= 0
                && (hayMineralCrudo() || necesitaCarbon)) {
            ItemStack lena = VillageStorage.quitarLena(level, center, 2);
            if (lena != null && !lena.isEmpty()) {
                ItemStack resto = guardarEnInventario(lena);
                if (!resto.isEmpty()) {
                    VillageStorage.guardar(level, center, resto);
                }
                algo = true;
            }
        }
        return algo || tienePico();
    }

    /** Saca hasta {@code cuantas} unidades de lo que cumpla el filtro y se las guarda en el inventario. */
    private boolean sacarDelAlmacen(ServerLevel level, Container almacen, Predicate<ItemStack> filtro, int cuantas) {
        if (cuantas <= 0) {
            return false;
        }
        ItemStack sacado = VillageStorage.quitar(level, center, filtro, cuantas);
        if (sacado == null || sacado.isEmpty()) {
            return false;
        }
        ItemStack resto = guardarEnInventario(sacado);
        if (!resto.isEmpty()) {
            VillageStorage.guardar(level, center, resto); // no le cupo: de vuelta al almacén
        }
        return true;
    }

    /**
     * <b>Pica una celda de la faena</b> (el hueco de paso del caracol o una celda de galería) y la deja hecha. Es
     * una celda por vuelta del goal, con sus bloques: el hueco de paso (tres celdas, I26), la pieza del suelo, el
     * relleno de debajo, el marco de madera cuando toca y la antorcha cuando toca.
     */
    private void cavar(ServerLevel level) {
        if (celdaDeTrabajo == null) {
            return;
        }
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        if (indiceDeGaleria > 0) {
            picarLaCeldaDeLaGaleria(level, nivel, indiceDeGaleria);
        } else {
            picarLaCeldaDelCaracol(level, nivel);
        }
        celdasCavadas++;
        gastarElPico();
        // La siguiente celda de la misma tanda (así no se vuelve a decidir la fase por celda).
        if (fase == Fase.CAVAR) {
            Fase antes = fase;
            if (!prepararElPicado(level, nivel)) {
                fase = antes;
            }
        }
    }

    /**
     * <b>El caracol</b>: abre el hueco de paso de esa celda (tres celdas por encima de la pieza, I26), se lleva lo
     * que salga, pone la pieza ({@link VillageGenerator#piezaDelCaracol}), rellena el suelo de debajo si está hueco
     * y, cuando toca, levanta el marco y deja la antorcha.
     */
    private void picarLaCeldaDelCaracol(ServerLevel level, int nivel) {
        BlockPos celda = VillageGenerator.celdaDelCaracol(center, nivel, paso);
        // 1) EL HUECO DE PASO (tres celdas encima de la pieza: es lo que pide el juego para subir un escalón, I26).
        for (int dy = 1; dy <= 3; dy++) {
            picarYRecoger(level, celda.above(dy), false);
        }
        // 2) LA PIEZA, picando antes lo que hubiera en esa celda (piedra, una veta...) y SELLANDO si es agua o lava.
        boolean eraSello = celda.equals(selloPendiente);
        if (!picarYRecoger(level, celda, true)) {
            selloPendiente = celda; // agua o lava: queda sellada de adoquín y se vuelve a intentar en la vuelta siguiente
            if (++sellosSeguidos > SELLOS_MAXIMOS) {
                cerrarLaMina(level, celda);
            }
            return;
        }
        if (!eraSello) {
            sellosSeguidos = 0;
        }
        selloPendiente = null;
        level.setBlock(celda, VillageGenerator.piezaDelCaracol(paso), Block.UPDATE_ALL);
        // 3) EL SUELO DE DEBAJO: si está hueco (aire, agua o lava), la pieza quedaría flotando y el que baje se cae.
        rellenarElSuelo(level, celda.below());
        // 4) LAS VETAS QUE SE VEN EN LA PARED (lo que hace que la mina saque minerales de verdad).
        minarLasVetasDeAlLado(level, celda);
        // 5) EL MARCO DE MADERA (cada MINA_SOPORTE_CADA escalones) y la antorcha (cada CELDAS_POR_ANTORCHA).
        if (VillageGenerator.llevaSoporte(paso)) {
            ponerElMarco(level, celda);
        }
        if (Math.floorMod(paso, CELDAS_POR_ANTORCHA) == 0) {
            ponerLaAntorcha(level, celda.above(2));
        }
        DevilRpg.LOGGER.info("[Village] El minero: caracol paso {} en {} (y={})", paso, celda.toShortString(),
                celda.getY());
        VillageManager.ponerSuceso(villager, "Cava el caracol");
    }

    /**
     * <b>Una celda de galería</b>: hueco de paso de dos celdas (el suelo es el terreno de debajo), relleno si está
     * hueco, veta de al lado, marco cada {@link VillageGenerator#MINA_GALERIA_SOPORTE_CADA} celdas y antorcha.
     */
    private void picarLaCeldaDeLaGaleria(ServerLevel level, int nivel, int indice) {
        BlockPos celda = VillageGenerator.celdaDeLaGaleria(center, nivel, paso, indice);
        for (int dy = 0; dy <= 1; dy++) {
            picarYRecoger(level, celda.above(dy), false);
        }
        boolean eraSello = celda.equals(selloPendiente);
        if (!picarYRecoger(level, celda, true)) {
            selloPendiente = celda;
            if (++sellosSeguidos > SELLOS_MAXIMOS) {
                cerrarLaMina(level, celda);
            }
            return;
        }
        if (!eraSello) {
            sellosSeguidos = 0;
        }
        selloPendiente = null;
        rellenarElSuelo(level, celda.below());
        minarLasVetasDeAlLado(level, celda);
        if (Math.floorMod(indice, VillageGenerator.MINA_GALERIA_SOPORTE_CADA) == 0) {
            ponerElMarcoDeLaGaleria(level, celda, indice);
        }
        if (Math.floorMod(indice, CELDAS_POR_ANTORCHA) == 0) {
            ponerLaAntorcha(level, celda.above());
        }
        DevilRpg.LOGGER.info("[Village] El minero: galeria {} (paso {}, celda {} de {})", celda.toShortString(), paso,
                indice, VillageGenerator.MINA_GALERIA_LARGO);
        VillageManager.ponerSuceso(villager, "Abre galeria");
    }

    /**
     * Pica esa celda <b>si se puede</b> (aire, terreno natural o una pieza de la mina: {@link
     * VillageGenerator#elMineroPuedePicar}) y se guarda lo que suelta. Nunca toca lo que ha puesto el pueblo: el
     * caracol pasa pegado a la pared de su propia caseta y sin esta guarda el minero le abriría un boquete.
     * <p>
     * Devuelve {@code false} si la celda era <b>agua o lava</b>: entonces <b>no se pica</b>, se <b>sella</b> con
     * adoquín (si es una bolsa, el túnel sigue; ver {@link #SELLOS_MAXIMOS} para el mar).
     */
    private boolean picarYRecoger(ServerLevel level, BlockPos pos, boolean cuentaElSello) {
        BlockState estado = level.getBlockState(pos);
        if (estado.isAir() || !VillageGenerator.elMineroPuedePicar(estado)) {
            return true; // aire (ya está hecho) o algo del pueblo (no se toca)
        }
        if (!estado.getFluidState().isEmpty()) {
            // AGUA O LAVA: se sella. Si es una bolsa, en la vuelta siguiente se pica el adoquín y el túnel sigue.
            level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            if (cuentaElSello) {
                DevilRpg.LOGGER.info("[Village] El minero: sella agua/lava en {} ({} seguidas)", pos.toShortString(),
                        sellosSeguidos + 1);
                return false;
            }
            return true;
        }
        if (!esLaMina(estado)) {
            // Partículas y sonido de lo que se rompe: se ve que está picando de verdad. (Las piezas de la mina, si
            // hay que repicarlas, se quitan en silencio: no es "mineral".)
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, estado), pos.getX() + 0.5D,
                    pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.0D);
            level.playSound(null, pos, estado.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack botin = botinDe(level, estado);
        if (botin != null && !botin.isEmpty()) {
            ItemStack resto = guardarEnInventario(botin);
            if (!resto.isEmpty()) {
                Block.popResource(level, pos, resto); // sin sitio en el zurrón: se queda en el suelo del túnel
            }
        }
        return true;
    }

    /**
     * <b>Cierra la mina</b>: el minero se ha topado con un mar de agua o de lava (más de {@link #SELLOS_MAXIMOS}
     * celdas seguidas) y deja la <b>piedra labrada</b> que marca el tope —el tapón de adoquín y la marca—, que es
     * lo que leen {@link VillageGenerator#laMinaLlegoAlTope} y el latido para saber que esa aldea ya tiene su mina.
     */
    private void cerrarLaMina(ServerLevel level, BlockPos celda) {
        for (int dy = 1; dy <= 3; dy++) {
            level.setBlock(celda.above(dy), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(celda, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        DevilRpg.LOGGER.info("[Village] El minero: la mina se PARA en {} ({} celdas de agua/lava seguidas): piedra"
                + " labrada de tope", celda.toShortString(), sellosSeguidos);
        VillageManager.ponerSuceso(villager, "La mina llego al tope");
    }

    /** ¿Esa celda ya es de la mina (adoquín, losa, tronco del marco, antorcha)? */
    private static boolean esLaMina(BlockState estado) {
        return estado.is(Blocks.COBBLESTONE) || estado.is(Blocks.COBBLESTONE_SLAB)
                || estado.is(Blocks.COBBLESTONE_STAIRS) || estado.is(Blocks.OAK_LOG)
                || estado.is(Blocks.OAK_PLANKS) || estado.is(Blocks.TORCH) || estado.is(Blocks.WALL_TORCH);
    }

    /** Si el suelo de una celda quedó hueco (aire, agua o lava), se rellena de adoquín: si no, la mina se cae. */
    private void rellenarElSuelo(ServerLevel level, BlockPos suelo) {
        BlockState estado = level.getBlockState(suelo);
        if (estado.isAir() || !estado.getFluidState().isEmpty()) {
            level.setBlock(suelo, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Pica las <b>vetas que se le quedan a la vista</b> en las paredes del túnel (las seis celdas de al lado de la
     * que acaba de abrir): es lo que hace que el minero <b>saque minerales</b> y no solo piedra, y es honesto —son
     * minerales que se ven desde el túnel, no una máquina de rayos X—. Como mucho {@value #VETAS_POR_CELDA} por
     * celda.
     */
    private void minarLasVetasDeAlLado(ServerLevel level, BlockPos celda) {
        int picadas = 0;
        for (Direction lado : Direction.values()) {
            if (picadas >= VETAS_POR_CELDA) {
                return;
            }
            BlockPos vecina = celda.relative(lado);
            BlockState estado = level.getBlockState(vecina);
            if (esVeta(estado) && VillageGenerator.elMineroPuedePicar(estado)) {
                picarYRecoger(level, vecina, false);
                picadas++;
            }
        }
    }

    private static boolean esVeta(BlockState estado) {
        return estado.is(BlockTags.COAL_ORES) || estado.is(BlockTags.COPPER_ORES)
                || estado.is(BlockTags.IRON_ORES) || estado.is(BlockTags.GOLD_ORES)
                || estado.is(BlockTags.REDSTONE_ORES) || estado.is(BlockTags.LAPIS_ORES)
                || estado.is(BlockTags.DIAMOND_ORES) || estado.is(BlockTags.EMERALD_ORES);
    }

    /**
     * Lo que suelta un bloque al picarlo <b>con pico de hierro y sin Fortuna</b> ({@code null} si no suelta nada):
     * el adoquín de la piedra, el deepslate picado, la tierra, la arena, y el mineral de cada veta. La <b>grava</b>
     * tiene además su 10% de <b>pedernal</b> de vanilla, que es la segunda fuente de pedernal del pueblo.
     */
    @Nullable
    private static ItemStack botinDe(ServerLevel level, BlockState estado) {
        Block b = estado.getBlock();
        if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE) {
            return new ItemStack(Items.COAL);
        }
        if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) {
            return new ItemStack(Items.RAW_IRON);
        }
        if (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE) {
            return new ItemStack(Items.RAW_COPPER);
        }
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE) {
            return new ItemStack(Items.RAW_GOLD);
        }
        if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) {
            return new ItemStack(Items.REDSTONE, 4);
        }
        if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE) {
            return new ItemStack(Items.LAPIS_LAZULI, 4);
        }
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return new ItemStack(Items.DIAMOND);
        }
        if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) {
            return new ItemStack(Items.EMERALD);
        }
        if (b == Blocks.STONE || b == Blocks.COBBLESTONE || b == Blocks.COBBLESTONE_SLAB
                || b == Blocks.COBBLESTONE_STAIRS) {
            return new ItemStack(Items.COBBLESTONE);
        }
        if (b == Blocks.DEEPSLATE || b == Blocks.COBBLED_DEEPSLATE) {
            return new ItemStack(Items.COBBLED_DEEPSLATE);
        }
        // LA TIERRA, LA ARENA Y LA GRAVA <b>NO</b> SE RECOGEN (la grava solo por su 10 % de pedernal): al minero no
        // le sirven de nada, y con el zurrón de OCHO huecos que tiene un aldeano, llenárselo de basura era lo que le
        // obligaba a subir a vaciarlo <b>cada tres celdas</b> (medido: dos celdas de galería en once minutos, con el
        // minero subiendo y bajando el caracol). Lo que no se recoge se queda en el túnel y el juego lo borra solo.
        if (b == Blocks.GRAVEL) {
            return level.getRandom().nextFloat() < 0.1F ? new ItemStack(Items.FLINT) : null;
        }
        if (estado.is(BlockTags.LOGS)) {
            return new ItemStack(b); // un tronco que se cruce en el túnel (la arboleda del pueblo, por ejemplo)
        }
        return null;
    }

    /**
     * El <b>marco de madera</b> del caracol: dos postes de tronco en las paredes (a la altura del paso) y la viga
     * de tablones encima, con los <b>tablones del almacén</b>. Los postes van en las celdas del radio (por dentro y
     * por fuera del anillo) y se <b>saltan</b> si ahí hay algo del pueblo: la pared este de la caseta está pegada al
     * caracol y el minero no puede abrirle un boquete (por eso el marco tampoco entra en el plano, I102).
     */
    private void ponerElMarco(ServerLevel level, BlockPos celda) {
        BlockPos[] lados = VillageGenerator.ladosDeLaCeldaDelCaracol(center,
                VillageGenerator.cotaDeLaPlaza(level, center), paso);
        int tablones = cuantosEnInventario(Items.OAK_PLANKS);
        if (tablones < TABLONES_POR_MARCO) {
            return; // sin madera no hay marco (y no se inventa: la trae del almacén)
        }
        for (BlockPos lado : lados) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos poste = lado.above(dy);
                if (!VillageGenerator.elMineroPuedePicar(level.getBlockState(poste))
                        || !level.getBlockState(poste).getFluidState().isEmpty()) {
                    return; // ahí hay algo del pueblo (o agua): sin marco
                }
            }
        }
        for (BlockPos lado : lados) {
            for (int dy = 1; dy <= 2; dy++) {
                level.setBlock(lado.above(dy), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        level.setBlock(celda.above(3), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        gastarDelInventario(Items.OAK_PLANKS, TABLONES_POR_MARCO);
    }

    /** El marco de una <b>galería</b>: dos postes (a la altura del paso) en las paredes y su viga encima. */
    private void ponerElMarcoDeLaGaleria(ServerLevel level, BlockPos celda, int indice) {
        Direction avance = VillageGenerator.direccionDeLaGaleria(paso);
        BlockPos[] lados = {celda.relative(avance.getClockWise()), celda.relative(avance.getCounterClockWise())};
        for (BlockPos lado : lados) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos poste = lado.above(dy);
                if (!VillageGenerator.elMineroPuedePicar(level.getBlockState(poste))
                        || !level.getBlockState(poste).getFluidState().isEmpty()) {
                    return;
                }
            }
        }
        if (cuantosEnInventario(Items.OAK_PLANKS) < TABLONES_POR_MARCO) {
            return;
        }
        for (BlockPos lado : lados) {
            for (int dy = 0; dy <= 1; dy++) {
                level.setBlock(lado.above(dy), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        level.setBlock(celda.above(2), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        gastarDelInventario(Items.OAK_PLANKS, TABLONES_POR_MARCO);
        DevilRpg.LOGGER.info("[Village] El minero: marco de la galeria {} (celda {})", celda.toShortString(), indice);
    }

    /**
     * Deja una <b>antorcha</b> en la pared del túnel (de las que lleva hechas). Una mina a oscuras es un criadero
     * de bichos, y estos nacen <b>dentro</b> de la muralla: la luz es parte del trabajo, no un adorno.
     */
    private void ponerLaAntorcha(ServerLevel level, BlockPos celda) {
        if (cuantosEnInventario(Items.TORCH) <= 0) {
            return;
        }
        for (Direction lado : Direction.values()) {
            if (lado.getAxis().isVertical()) {
                continue;
            }
            if (level.getBlockState(celda.relative(lado)).isFaceSturdy(level, celda.relative(lado), lado.getOpposite())) {
                level.setBlock(celda, Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, lado.getOpposite()), Block.UPDATE_ALL);
                gastarDelInventario(Items.TORCH, 1);
                return;
            }
        }
    }

    /**
     * <b>El taller</b> de la caseta, una faena por vuelta: fundir un mineral crudo en el horno, colar
     * {@value #ADOQUIN_POR_PEDERNAL} adoquines en la balsa por un pedernal, <b>hacer carbón vegetal</b> quemando un
     * tronco, o hacer antorchas con el carbón (o el carbón vegetal) y los palos. Devuelve {@code false} cuando ya no
     * hay nada que hacer y toca bajar lo sacado al almacén.
     */
    private boolean trabajarEnElTaller(ServerLevel level) {
        // 1) FUNDIR: hierro, cobre y oro crudos -> lingotes. Combustible: su carbón (que saca él) o la leña del
        //    almacén (que respeta la reserva de I-combustible, como la fragua del herrero).
        if (hayMineralCrudo() && (cuantosEnInventario(VillageStorage::esCarbon) > 0
                || cuantosEnInventario(VillageStorage::esLena) > 0)) {
            ItemStack crudo = quitarDelInventario(VillagerMinerGoal::esMineralCrudo, 1);
            if (crudo != null) {
                boolean conCarbon = cuantosEnInventario(VillageStorage::esCarbon) > 0;
                if (conCarbon) {
                    gastarDelInventario(VillageStorage::esCarbon, 1);
                } else {
                    gastarDelInventario(VillageStorage::esLena, 1);
                }
                ItemStack lingote = new ItemStack(lingoteDe(crudo.getItem()));
                ItemStack resto = guardarEnInventario(lingote);
                if (!resto.isEmpty()) {
                    VillageStorage.guardar(level, center, resto);
                }
                level.playSound(null, villager.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                        0.6F, 1.0F);
                VillageManager.ponerSuceso(villager, "Funde " + crudo.getItem().getDescription().getString());
                DevilRpg.LOGGER.info("[Village] El minero: funde {} en {} ({} un carbon)", crudo.getCount(),
                        lingote.getItem(), conCarbon ? "con" : "sin");
                return true;
            }
        }
        // 2) COLAR: 4 adoquines por un pedernal, en la balsa del agua (lo pidió el jugador).
        if (cuantosEnInventario(Items.COBBLESTONE) >= ADOQUIN_POR_PEDERNAL
                && VillageStorage.cuenta(level, center, s -> s.is(Items.FLINT)) < OBJETIVO_PEDERNAL) {
            gastarDelInventario(Items.COBBLESTONE, ADOQUIN_POR_PEDERNAL);
            ItemStack resto = guardarEnInventario(new ItemStack(Items.FLINT));
            if (!resto.isEmpty()) {
                VillageStorage.guardar(level, center, resto);
            }
            level.playSound(null, villager.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.0F);
            VillageManager.ponerSuceso(villager, "Saca pedernal");
            DevilRpg.LOGGER.info("[Village] El minero: cuela {} adoquines en la balsa y saca un pedernal", 
                    ADOQUIN_POR_PEDERNAL);
            return true;
        }
        // 3) CARBÓN VEGETAL: un tronco al horno (la receta de vanilla) cuando no le queda carbón y va justo de
        //    antorchas. Es la única fuente de carbón del pueblo cuando no hay veta a mano, y la leña la trae el
        //    leñador. Lo pidió el jugador: *"el carbón para hacer antorchas se puede hacer quemando logs en el
        //    furnace, ¿no?"*.
        if (cuantosEnInventario(VillageStorage::esCarbon) <= 0 && cuantosEnInventario(Items.TORCH) < ANTORCHAS_POR_VIAJE
                && cuantosEnInventario(VillageStorage::esLena) > 0) {
            gastarDelInventario(VillageStorage::esLena, 1);
            ItemStack resto = guardarEnInventario(new ItemStack(Items.CHARCOAL, CARBON_POR_ANTORCHA));
            if (!resto.isEmpty()) {
                VillageStorage.guardar(level, center, resto);
            }
            level.playSound(null, villager.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    0.6F, 0.8F);
            VillageManager.ponerSuceso(villager, "Quema un tronco en carbon");
            DevilRpg.LOGGER.info("[Village] El minero: quema un tronco en el horno y saca {} de carbon vegetal"
                    + " (para las antorchas)", CARBON_POR_ANTORCHA);
            return true;
        }
        // 4) ANTORCHAS: carbón (o carbón vegetal) + palo (la receta de vanilla), que son la luz de la mina.
        if (cuantosEnInventario(Items.TORCH) < ANTORCHAS_POR_VIAJE
                && cuantosEnInventario(VillageStorage::esCarbon) >= CARBON_POR_ANTORCHA
                && cuantosEnInventario(Items.STICK) >= 1) {
            gastarDelInventario(VillageStorage::esCarbon, CARBON_POR_ANTORCHA);
            gastarDelInventario(Items.STICK, 1);
            ItemStack resto = guardarEnInventario(new ItemStack(Items.TORCH, ANTORCHAS_POR_CARBON));
            if (!resto.isEmpty()) {
                VillageStorage.guardar(level, center, resto);
            }
            level.playSound(null, villager.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
            VillageManager.ponerSuceso(villager, "Hace antorchas");
            DevilRpg.LOGGER.info("[Village] El minero: hace {} antorchas con un carbon y un palo",
                    ANTORCHAS_POR_CARBON);
            return true;
        }
        return false;
    }

    /**
     * Deja en el almacén <b>todo lo sacado</b> y <b>lo que lleva DE SOBRA de sus recados</b>: se queda con
     * {@link #cuantoSeQueda} de cada cosa (el pico, unos tablones, unos palos, algo de carbón y algo de leña) y el
     * resto lo suelta. Antes era "todo o nada": un hueco con 64 palos no se soltaba nunca y el zurrón se le quedaba
     * sin sitio para el mineral (ver {@link #hayQueSubir}).
     */
    private void entregar(ServerLevel level) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            int seQueda = cuantoSeQueda(s);
            int sobra = s.getCount() - seQueda;
            if (sobra <= 0) {
                continue; // no lleva de sobra de eso
            }
            ItemStack resto = VillageStorage.guardar(level, center, s.copyWithCount(sobra));
            int devuelto = resto.getCount(); // lo que no le cupiera al almacén se queda en el zurrón (no se tira)
            if (seQueda + devuelto <= 0) {
                villager.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                s.setCount(seQueda + devuelto);
            }
        }
        celdasCavadas = 0; // subida hecha: la cuenta de la vuelta empieza de cero
        VillageManager.ponerSuceso(villager, "Deja lo sacado");
        DevilRpg.LOGGER.info("[Village] El minero: deja lo sacado en el almacen (pico {}, tablones {}, antorchas {})",
                tienePico() ? "si" : "no", cuantosEnInventario(Items.OAK_PLANKS),
                cuantosEnInventario(Items.TORCH));
    }

    /**
     * <b>Cuántas unidades de eso se queda</b> al llegar al almacén (el resto lo deja allí). Antes era "todo o nada" y
     * el minero se quedaba con los recados <b>enteros para siempre</b>: un hueco con 64 palos que no suelta nunca es un
     * hueco menos para el mineral, y con seis o siete huecos de recados el zurrón se le quedaba sin sitio (ver
     * {@link #hayQueSubir}).
     */
    private int cuantoSeQueda(ItemStack s) {
        if (esPico(s)) {
            return 2; // su herramienta y un repuesto
        }
        if (s.is(Items.OAK_PLANKS)) {
            return TABLONES_POR_VIAJE; // para los marcos de la galería
        }
        if (s.is(Items.STICK)) {
            return PALOS_POR_VIAJE; // para las antorchas (un palo = cuatro)
        }
        if (s.is(Items.TORCH)) {
            return ANTORCHAS_POR_VIAJE; // la luz de la mina
        }
        if (VillageStorage.esCarbon(s)) {
            return CARBON_POR_VIAJE; // para las antorchas y el horno
        }
        if (VillageStorage.esLena(s)) {
            return 2; // para el horno (fundir y hacer carbón vegetal)
        }
        return 0; // mineral, adoquín, pedernal, piedras...: al almacén
    }

    /** Lo que el minero <b>no</b> suelta al llegar al almacén (ver {@link #cuantoSeQueda}). */
    private boolean seLoQueda(ItemStack s) {
        return cuantoSeQueda(s) > 0;
    }

    // --- el pico (la herramienta, que gasta y le forja el herrero de herramientas) -------------------

    /** ¿Lleva un pico encima (en la mano o en el zurrón)? */
    private boolean tienePico() {
        return (!villager.getMainHandItem().isEmpty() && esPico(villager.getMainHandItem()))
                || cuantosEnInventario(VillagerMinerGoal::esPico) > 0;
    }

    private static boolean esPico(ItemStack s) {
        return s.is(Items.IRON_PICKAXE) || s.is(Items.DIAMOND_PICKAXE) || s.is(Items.NETHERITE_PICKAXE)
                || s.is(Items.STONE_PICKAXE) || s.is(Items.WOODEN_PICKAXE);
    }

    private static boolean esMineralCrudo(ItemStack s) {
        return s.is(Items.RAW_IRON) || s.is(Items.RAW_COPPER) || s.is(Items.RAW_GOLD);
    }

    /** ¿Lleva mineral crudo que fundir? */
    private boolean hayMineralCrudo() {
        return cuantosEnInventario(VillagerMinerGoal::esMineralCrudo) > 0;
    }

    private static net.minecraft.world.item.Item lingoteDe(net.minecraft.world.item.Item crudo) {
        if (crudo == Items.RAW_IRON) {
            return Items.IRON_INGOT;
        }
        if (crudo == Items.RAW_COPPER) {
            return Items.COPPER_INGOT;
        }
        return Items.GOLD_INGOT;
    }

    /** Pone el pico en la mano (se le ve trabajar con él) y guarda el que llevara. */
    private void cogerEnLaMano(ItemStack pico) {
        ItemStack viejo = villager.getMainHandItem();
        villager.setItemInHand(InteractionHand.MAIN_HAND, pico.copy());
        if (!viejo.isEmpty()) {
            ItemStack resto = guardarEnInventario(viejo);
            if (!resto.isEmpty()) {
                Block.popResource((ServerLevel) villager.level(), villager.blockPosition(), resto);
            }
        }
    }

    /**
     * <b>El pico se gasta</b> (lo pidió el jugador): una unidad de uso por celda picada, con la durabilidad de
     * verdad del pico (un pico de hierro son 250 celdas, uno de diamante muchas más). Cuando se rompe se va al
     * almacén a por otro, y si no hay, el <b>herrero de herramientas</b> forja más (3 lingotes de hierro y 2 palos,
     * con el hierro que funde el propio minero).
     */
    private void gastarElPico() {
        ItemStack pico = villager.getMainHandItem();
        if (!esPico(pico)) {
            return;
        }
        int uso = pico.getDamageValue() + 1;
        if (uso < pico.getMaxDamage()) {
            pico.setDamageValue(uso);
            return;
        }
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (villager.level() instanceof ServerLevel level) {
            level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
        VillageManager.ponerSuceso(villager, "Se le rompio el pico");
        DevilRpg.LOGGER.info("[Village] El minero: se le ha roto el pico ({} usos): va a por otro al almacen", uso);
    }

    // --- el inventario del aldeano (lo mismo que hace el herrero) ------------------------------------

    private void gastarDelInventario(net.minecraft.world.item.Item item, int cuantas) {
        gastarDelInventario(s -> s.is(item), cuantas);
    }

    private void gastarDelInventario(Predicate<ItemStack> filtro, int cuantas) {
        int faltan = cuantas;
        for (int i = 0; i < villager.getInventory().getContainerSize() && faltan > 0; i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!filtro.test(s)) {
                continue;
            }
            int quita = Math.min(faltan, s.getCount());
            s.shrink(quita);
            faltan -= quita;
            if (s.isEmpty()) {
                villager.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** Saca del zurrón hasta {@code cuantas} unidades de lo que cumpla el filtro (o {@code null} si no hay). */
    @Nullable
    private ItemStack quitarDelInventario(Predicate<ItemStack> filtro, int cuantas) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!filtro.test(s)) {
                continue;
            }
            int quita = Math.min(cuantas, s.getCount());
            ItemStack sacado = s.copyWithCount(quita);
            s.shrink(quita);
            if (s.isEmpty()) {
                villager.getInventory().setItem(i, ItemStack.EMPTY);
            }
            return sacado;
        }
        return null;
    }

    private int cuantosEnInventario(net.minecraft.world.item.Item item) {
        return cuantosEnInventario(s -> s.is(item));
    }

    private int cuantosEnInventario(Predicate<ItemStack> filtro) {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (filtro.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private ItemStack guardarEnInventario(ItemStack stack) {
        ItemStack resto = stack.copy();
        for (int i = 0; i < villager.getInventory().getContainerSize() && !resto.isEmpty(); i++) {
            ItemStack dentro = villager.getInventory().getItem(i);
            if (!dentro.isEmpty() && ItemStack.isSameItemSameComponents(dentro, resto)) {
                int espacio = dentro.getMaxStackSize() - dentro.getCount();
                int mete = Math.min(espacio, resto.getCount());
                dentro.grow(mete);
                resto.shrink(mete);
            }
        }
        for (int i = 0; i < villager.getInventory().getContainerSize() && !resto.isEmpty(); i++) {
            if (villager.getInventory().getItem(i).isEmpty()) {
                villager.getInventory().setItem(i, resto.copy());
                resto = ItemStack.EMPTY;
            }
        }
        return resto;
    }

    /**
     * A dónde se le manda <b>caminar</b> ahora mismo: el {@link #destino} o un <b>tirón intermedio</b> si el destino
     * queda fuera del alcance del planificador (ver {@link VillageManager#tironHacia}). Se recalcula solo cuando se
     * llega al tirón que tenía (y la plaza del pueblo se busca una vez por corrida, que es un barrido del terreno).
     */
    private void irHaciaElDestino(ServerLevel level) {
        if (destino == null) {
            return;
        }
        // EL MURO MANDA: si el destino está al otro lado de la muralla, primero se cruza por el portón (ver
        // `VillageManager.pasoParaCruzarElMuro` / I112). Va antes del atajo de "está cerca" porque cerca pero al otro
        // lado del muro no hay ruta directa.
        BlockPos porton = VillageManager.pasoParaCruzarElMuro(level, center, villager, destino);
        if (porton != null) {
            pasoDelViaje = null;
            VillageManager.caminarHacia(villager, porton, VELOCIDAD);
            return;
        }
        if (VillageManager.distanciaA(villager, destino) <= VillageManager.ALCANCE_DE_LA_RUTA) {
            pasoDelViaje = null;
            VillageManager.caminarHacia(villager, destino, VELOCIDAD);
            return;
        }
        if (pasoDelViaje == null || VillageManager.distanciaA(villager, pasoDelViaje) <= 2.0D) {
            if (plazaDelPueblo == null) {
                plazaDelPueblo = VillageManager.casillaDeLaCalle(level, center);
            }
            pasoDelViaje = VillageManager.tironHacia(level, center, villager, destino, plazaDelPueblo);
        }
        VillageManager.caminarHacia(villager, pasoDelViaje, VELOCIDAD);
    }

    /** El trozo del viaje al que se le manda ahora (ver {@link #irHaciaElDestino}); {@code null} = va al destino. */
    @Nullable
    private BlockPos pasoDelViaje;
    /** La plaza del pueblo, buscada una vez por corrida: es el último recurso del tirón. */
    @Nullable
    private BlockPos plazaDelPueblo;

    private void irAlDestino() {
        if (destino != null && villager.level() instanceof ServerLevel level) {
            irHaciaElDestino(level);
        }
    }
}
