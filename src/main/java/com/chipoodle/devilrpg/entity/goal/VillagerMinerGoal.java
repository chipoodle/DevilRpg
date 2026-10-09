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
 *   <li><b>Hace antorchas</b> (carbón + palo) y las va dejando por el túnel: una mina a oscuras es un criadero de
 *       monstruos, y el pueblo no puede permitirse tener monstruos naciendo <b>dentro</b> de la muralla.</li>
 *   <li><b>Sube y lo baja todo al almacén</b> (lingotes, carbón, gemas y el adoquín que saca), de donde el
 *       herrero de herramientas saca los picos y el flechero las flechas. <b>La balsa ya no es suya</b> (27-sep-2026):
 *       el adoquín → pedernal lo cuela el <b>herrero de herramientas</b> —era la faena que le comía el tiempo de la
 *       mina, medido— y aquí solo queda el <b>horno</b> del taller.</li>
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
    /**
     * Adoquín que junta antes de subir a <b>entregarlo</b>: {@value} (una pila). Ya no sube a <b>colarlo</b> —la balsa
     * es del herrero de herramientas desde el 27-sep-2026—, pero sí a dejarlo en el almacén: de ahí lo saca el herrero
     * para colar el pedernal y el obrero para la obra.
     */
    private static final int ADOQUIN_PARA_SUBIR = 64;
    /**
     * Mineral crudo que junta antes de subir a fundirlo. <b>No se sube por UNA veta</b>: el viaje de subida y bajada
     * por el caracol se paga solo (medido: con una veta por viaje, dos celdas de galería costaban minuto y medio y
     * casi todo era andar). Se funde igual todo lo que lleve cuando sube por cualquier otro motivo.
     */
    private static final int MINERAL_PARA_SUBIR = 3;
    /** Con estos huecos libres o menos en el inventario, se sube a descargar (si no, lo sacado se queda por el suelo). */
    private static final int HUECOS_LIBRES_MINIMOS = 2;
    /**
     * Ticks que el minero espera su ración antes de volver al tajo. Con hambre y comida en el pueblo se para a comer
     * (lo pidió el jugador), pero <b>no sin límite</b>: si la taberna no le da nada, vuelve a la mina.
     */
    private static final int ESPERA_DE_COMIDA_MAXIMA = 400;
    /**
     * Ticks que el minero espera <b>cuando el sitio al que tiene que ir le está aparcado</b> (I33) antes de volver a
     * intentarlo. El aparcado son <b>5 minutos</b>, y el minero <b>no tiene otra cosa que hacer</b>: su destino sale del
     * <b>plan de la mina</b> (una celda del caracol, que es una sola) o es el <b>almacén</b>, que es su única fuente de
     * pico y de recados. Con el aparcado largo no "sigue con lo demás": se queda plantado en la caseta con la etiqueta
     * "Trabajando" (que es lo que ve el jugador).
     * <p>
     * <b>Medido</b> (arnés, 24-sep-2026, su partida): (1) con el almacén aparcado desde el guardado
     * (`aparcadoHasta=129790`) el minero se pasó <b>140 s parado</b> con `goals=[]` y `pico=SIN PICO` antes de ir a por
     * un pico; con esta espera fue a por él en la mitad de tiempo y cogió un pico de madera. (2) Con una <b>celda de la
     * mina</b> aparcada (`499,53,620`) la mina se quedó <b>220 s en `pasos=18`</b> sin cavar una celda.
     * <p>
     * Y no vuelve al bucle de empujar la pared (que es lo que el aparcado evita) porque entre intento e intento hay esta
     * espera: se reintenta cada ~30 s, no en cada tick.
     */
    private static final int ESPERA_TRAS_APARCADO = 600;
    /**
     * Ticks de "no me acerco" antes de <b>volver a la caseta</b> y replanificar (ver el `tick`). Medido (24-sep-2026):
     * el minero quedaba <b>encajado fuera del túnel</b> en `501,55,621` y desde ahí el planificador no le daba
     * <b>ninguna</b> ruta a su celda (`ruta=1 nodos ... alcanza=NO`), así que se rendía tres veces en la misma corrida.
     * La caseta está arriba y de ella <b>sí</b> se baja andando el caracol: volviendo arriba y replanificando, la faena
     * sigue.
     */
    private static final int TICKS_PARA_VOLVER_A_LA_CASETA = 120;
    /** true mientras el minero vuelve a la caseta porque se ha quedado encajado sin ruta hasta su celda. */
    private boolean volviendoALaCaseta;
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
     * <p>
     * <b>Y ESTE TOPE SOLO NO BASTABA (medido el 26-sep-2026)</b>: con un acuífero de verdad el contador <b>nunca
     * llega a 13</b> porque se reinicia en cuanto el minero pica una celda de roca virgen, así que la mina no se
     * cerraba nunca y el minero se quedaba en un ir y venir de sellos ({@code 1, 2, 3, 1, 2, 3…} medido en el log,
     * 50 sellos y la galería siempre en {@code 0/24}). La señal que de verdad distingue la bolsa del mar es
     * <b>si el agua VUELVE a la misma celda</b> (ver {@link #selloDelMar}): un manantial aislado, al picarlo, se
     * queda seco; un acuífero conectado lo vuelve a llenar.
     */
    private static final int SELLOS_MAXIMOS = 12;

    /**
     * <b>La celda que ya pidió un sello y volvió a pedirlo</b>: la prueba de que el agua está <b>conectada</b> (un
     * mar) y no es una bolsa. Se guarda la ÚLTIMA: si una celda se sella, se repica y el agua vuelve, eso es un mar
     * y la mina se cierra ahí mismo con su piedra labrada. Un manantial aislado no vuelve.
     */
    @Nullable
    private BlockPos selloDelMar;
    /**
     * <b>La celda de la mina a la que le falta la antorcha</b> y a la que va ahora (fase {@code ENCENDER}), o
     * {@code null}. Ver {@link #buscarHuecoDeLuz}: la antorcha se pone al cavar, y como el minero <b>cava antes de
     * tener antorchas</b> (el carbón sale de la mina), esas celdas se quedaban a oscuras <b>para siempre</b>.
     */
    @Nullable
    private BlockPos huecoDeLuz;

    private enum Fase { RECOGER, CAVAR, TALLER, ENTREGAR, ENCENDER }

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
        // Y EL POZO QUE TOCA (28-sep-2026, el segundo pozo): si el que estaba en faena ya llegó a su tope, se pasa al
        // siguiente (el suroeste). Todo lo demás —caracol, galerías, progreso, el marco y las antorchas— sale del
        // pozo activo, así que no hay que tocar nada más.
        VillageGenerator.elegirElPozoActivo(level, center, nivel);
        // ¿HAY QUE SUBIR? Cuando lleva media vuelta cavada, cuando tiene mineral que fundir o colar, o cuando la
        // mina ya está cerrada (el tope). Así el viaje de subida se paga una vez cada media vuelta y no por celda.
        paso = VillageGenerator.progresoDeLaMina(level, center, nivel);
        boolean procesa = hayQueSubir(level);
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
        // LA LUZ QUE FALTA, ANTES DE SEGUIR CAVANDO (28-sep-2026, lo pidió el jugador). Si lleva antorchas y hay una
        // celda de la mina a la que le falta la suya (porque se cavó antes de tenerlas), va a ponerla: una mina a
        // oscuras es un criadero de monstruos DENTRO de la muralla. Va del frente hacia la boca, así que enciende la mina
        // entera en unas pocas vueltas y sin desviarse apenas (el caracol se anda al subir y al bajar).
        if (cuantosEnInventario(Items.TORCH) > 0) {
            BlockPos hueco = buscarHuecoDeLuz(level, nivel);
            if (hueco != null && !VillageManager.esPuntoFallido(villager, hueco)) {
                fase = Fase.ENCENDER;
                huecoDeLuz = hueco;
                destino = VillageManager.casillaDePieCercaDe(level, hueco);
                celdaDeTrabajo = hueco; // el alcance se mide a la antorcha, no a donde se para
                return comprobarDestino();
            }
        }
        fase = Fase.CAVAR;
        return prepararElPicado(level, nivel);
    }

    /**
     * ¿El pueblo <b>puede darle luz</b> a la mina ahora mismo? Vale la <b>antorcha hecha</b>, y también el
     * <b>carbón</b> (o el <b>carbón vegetal</b>) y la <b>leña</b> —con un tronco, el taller hace el carbón vegetal en
     * el horno de la caseta: la leña la trae el leñador—. Se piden también los <b>palos</b>: sin palo no hay antorcha
     * (carbón + palo), y sin esta comprobación el minero subiría al taller una y otra vez sin poder hacer nada.
     */
    private boolean elPuebloPuedeDarLuz(ServerLevel level) {
        if (VillageStorage.cuenta(level, center, s -> s.is(Items.TORCH)) > 0) {
            return true;
        }
        // LOS PALOS Y LA LEÑA VALEN DEL ALMACÉN **O DEL INVENTARIO**: el minero lleva los suyos encima (medido:
        // `0:16xminecraft:stick`, `2:2xminecraft:oak_log`) y con ellos hace el carbón vegetal y las antorchas en el
        // taller. Mirando solo el almacén, un pueblo sin palos en el cofre dejaba al minero bajar a oscuras aunque
        // pudiera hacerse la luz él mismo.
        boolean hayPalos = cuantosEnInventario(Items.STICK) >= 1
                || VillageStorage.cuenta(level, center, s -> s.is(Items.STICK)) >= 1;
        boolean hayCarbon = cuantosEnInventario(VillageStorage::esCarbon) >= CARBON_POR_ANTORCHA
                || VillageStorage.cuenta(level, center, VillageStorage::esCarbon) >= CARBON_POR_ANTORCHA;
        boolean hayLena = cuantosEnInventario(VillageStorage::esLena) > 0
                || VillageStorage.cuenta(level, center, VillageStorage::esLena) > 0;
        return hayPalos && (hayCarbon || hayLena);
    }

    /**
     * ¿Le toca <b>subir</b> a procesar lo que lleva, en vez de seguir cavando? Se sube con una <b>tanda hecha</b>, no
     * con la primera piedra: sin esto el minero subía al taller y al almacén <b>después de CADA celda</b> (medido con
     * el arnés: 16 celdas en 136 s, casi todo el tiempo andando el caracol de arriba abajo) en vez de cavar.
     * <p>
     * Los disparadores: <b>mineral crudo</b> que fundir, <b>adoquín</b> de sobra para sacar pedernal
     * ({@link #ADOQUIN_PARA_SUBIR}), que se ha quedado <b>sin antorchas</b> (una mina a oscuras cría monstruos dentro de
     * la muralla) y el <b>inventario lleno</b> (si no, lo que saque se queda por el suelo del túnel).
     */
    private boolean hayQueSubir(ServerLevel level) {
        if (cuantosEnInventario(VillagerMinerGoal::esMineralCrudo) >= MINERAL_PARA_SUBIR
                || cuantosEnInventario(Items.COBBLESTONE) >= ADOQUIN_PARA_SUBIR) {
            return true;
        }
        // SIN LUZ NO SE BAJA (28-sep-2026, lo pidió el jugador: *"el minero no está poniendo antorchas … se ve muy
        // oscuro"*). Antes esto era `celdasCavadas > 0 && TORCH <= 0`, así que la PRIMERA bajada se hacía a oscuras y
        // esas celdas se quedaban así hasta que el repaso (`buscarHuecoDeLuz`) las encendiera. **El carbón no tiene
        // por qué salir de la mina**: el taller hace CARBÓN VEGETAL quemando un tronco (la leña la trae el leñador al
        // almacén), así que se puede bajar con luz desde el primer viaje. Se pide solo si el pueblo PUEDE dársela
        // (antorchas hechas, o carbón/leña Y palos): si no puede, subir sería un bucle y cava a oscuras, que es lo
        // único que le queda (y el repaso lo encenderá cuando haya).
        if (cuantosEnInventario(Items.TORCH) <= 0 && elPuebloPuedeDarLuz(level)) {
            return true;
        }
        int libres = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            if (villager.getInventory().getItem(i).isEmpty()) {
                libres++;
            }
        }
        // Y EL INVENTARIO LLENO SOLO CUENTA SI PUEDE VACIARLO. Antes bastaba con tener dos huecos libres, y el minero lleva
        // SIEMPRE encima sus recados (pico, tablones, palos, carbón y leña: seis o siete huecos de los ocho), así que
        // subía a "entregar" una y otra vez SIN ENTREGAR NADA —los recados no los suelta— y la mina no avanzaba ni una
        // celda. Lo vio el jugador: *"el minero aparece como trabajando dentro de su choza pero realmente no hace
        // nada... se queda sólo entrando y saliendo de su choza"*. Con esto, "lleno" solo sube si lleva algo que el
        // almacén quiera (mineral, adoquín, pedernal, piedras...).
        return libres <= HUECOS_LIBRES_MINIMOS && hayParaEntregar();
    }

    /**
     * ¿Lleva algo que el almacén <b>quiera</b> (o sea, algo que no sea de los recados)? Es lo que decide si "tener el
     * inventario lleno" es motivo para subir: lleno de tablones y palos —que se queda para trabajar— no lo es.
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
            destino = indiceDeGaleria == 1 ? celdaDePieDelCaracol(center, nivel, paso)
                    : VillageGenerator.celdaDeLaGaleria(center, nivel, paso, indiceDeGaleria - 1);
        } else {
            indiceDeGaleria = 0;
            celdaDeTrabajo = VillageGenerator.celdaDelCaracol(center, nivel, paso);
            destino = paso == 0 ? celdaDePieDelCaracol(center, nivel, 0)
                    : celdaDePieDelCaracol(center, nivel, paso - 1);
        }
        // SI LA MINA ESTÁ TAPADA POR ALGO DEL PUEBLO (una casa, la pared de su caseta, lo que puso el jugador), el
        // minero NO lo toca: para y lo deja anotado. Es la misma lección de I24/I27 (lo construido no se cava) y sin
        // esta guarda el minero le abriría un boquete a su propia caseta, que está pegada al anillo del caracol.
        // EL AGUA SÍ SE TRABAJA (26-sep-2026): ahora el minero la AÍSLA con paredes y la SECA para seguir bajando
        // (lo pidió el jugador), así que una celda con fluido NO es "la mina está tapada": es faena suya. Se sigue
        // parando —y se anota— solo ante lo que ha puesto el pueblo (la pared de su caseta, una casa, algo del
        // jugador), que es la lección de I24/I27.
        if (!VillageGenerator.elMineroPuedePicar(level.getBlockState(celdaDeTrabajo))
                && level.getBlockState(celdaDeTrabajo).getFluidState().isEmpty()) {
            anotar("la mina esta tapada en " + celdaDeTrabajo.toShortString());
            destino = null;
            celdaDeTrabajo = null;
            restTicks = IDLE_REST_TICKS * 20;
            return false;
        }
        return comprobarDestino();
    }

    /**
     * <b>LA CASILLA DONDE SE ESTÁ DE PIE</b> encima de la pieza (losa o adoquín) del paso {@code paso} del caracol.
     * <p>
     * <b>Por qué no vale la celda de la pieza</b> (medido, 24-sep-2026, con el log de "no llegué" diciendo dónde y con
     * qué bloques): el minero se rendía <b>a 3 bloques de su propia celda</b> con `pies=cobblestone_slab` y
     * `destino=cobblestone_slab`: el destino que se le daba era la celda <b>del bloque de la pieza</b> —un bloque
     * macizo— y el planificador del juego <b>no puede meterlo ahí</b>; le devolvía una ruta de <b>1 nodo que no
     * alcanza</b> (`alcanza=NO`) y el aldeano se plantaba.
     * <p>
     * <b>PERO «ENCIMA» NO ES SIEMPRE {@code +1}, Y ESO COSTÓ OTRA CORRIDA</b> (26-sep-2026). La huella de la
     * <b>losa</b> (los pasos pares) está a {@code y + 0,5}: el aldeano que se para encima tiene los pies a 0,5 del
     * suelo de la celda, o sea <b>dentro de la propia celda de la losa</b> (su {@code blockPosition} es {@code y}),
     * mientras que la del <b>adoquín</b> (los impares) está a {@code y + 1} y sí se para en la de arriba. Preguntando
     * siempre {@code above()} se le mandaba a una celda con <b>el suelo a medio bloque</b> ({@code y + 1} sobre una
     * losa): el planificador del juego no la da por buena, y entonces el <b>cerebro del aldeano</b> hace lo que hace
     * vanilla cuando una ruta no llega —{@code MoveToTargetSink} <b>le borra el {@code WALK_TARGET}}</b>— mientras el
     * goal se lo vuelve a poner cada tick. El resultado medido con el arnés: {@code destino=SIN DESTINO} y
     * {@code nav=[sin ruta]} con el minero corriendo "Bajando a la mina", plantado en la superficie 8 bloques por
     * encima de su faena y <b>sin cavar una celda</b> (`pasos=16` congelado)… con el pozo ya abierto.
     */
    private static BlockPos celdaDePieDelCaracol(BlockPos center, int nivel, int paso) {
        return VillageGenerator.celdaDelCaracol(center, nivel, paso)
                .above(VillageGenerator.esLosaDelCaracol(paso) ? 0 : 1);
    }

    /**
     * Si al sitio donde va <b>no llegó hace poco</b> (I33), el minero <b>no se queda 5 minutos parado</b>: el aparcado
     * es para no empujar la misma pared en bucle, pero el minero <b>no tiene otra faena</b> a la que pasar —su destino
     * sale del plan de la mina o es el almacén de los recados—, así que con el aparcado largo se queda plantado en la
     * caseta sin cavar (medido: `goals=[]` y `aparcado=true` durante 140 s con el almacén, y `pasos` congelado 220 s con
     * una celda). Se olvida el aparcado y se reintenta tras {@link #ESPERA_TRAS_APARCADO}.
     */
    private boolean comprobarDestino() {
        if (destino != null && VillageManager.esPuntoFallido(villager, destino)) {
            VillageManager.olvidarPuntoFallido(villager);
            destino = null;
            celdaDeTrabajo = null;
            restTicks = ESPERA_TRAS_APARCADO;
            return false; // ahora no arranca: se reintenta dentro de ESPERA_TRAS_APARCADO
        }
        return destino != null;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        volviendoALaCaseta = false;
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
        // SIN PICO NO SE CAVA (medido el 26-sep-2026): el `canUse` ya pregunta por el pico al empezar la vuelta,
        // pero mientras el goal está corriendo no se vuelve a preguntar, así que con el pico roto el minero seguía
        // "picando" en el sitio —gastando tiempo y mano— sin poder sacar nada. Lo que tiene que hacer es SOLTAR la
        // faena para que `canUse` lo mande al almacén a por otro pico (o al taller). Medido: `pico=SIN PICO` con el
        // inventario lleno de adoquín y el minero clavado en la galería del paso 32.
        if (fase == Fase.CAVAR && !tienePico()) {
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
            if (volviendoALaCaseta) {
                // YA SE ESTÁ VOLVIENDO: se camina a la caseta (un sitio del que SIEMPRE hay ruta) y, al llegar, se
                // vuelve a decidir la faena desde arriba, que es desde donde el túnel se anda.
                BlockPos caseta = VillageGenerator.puntoDeApoyoDeLaCaseta(level, center);
                double hasta = Math.sqrt(villager.distanceToSqr(caseta.getX() + 0.5D, caseta.getY() + 0.5D,
                        caseta.getZ() + 0.5D));
                if (hasta <= REACH) {
                    volviendoALaCaseta = false;
                    destino = null; // que se vuelva a decidir con el minero ya en la caseta
                    restTicks = IDLE_REST_TICKS;
                    return;
                }
                VillageManager.caminarHacia(villager, caseta, VELOCIDAD);
                VillageManager.ponerActividad(villager, "Volviendo a la caseta");
                anotar("yendo: Volviendo a la caseta (encajado)");
                if (hasta < mejorDistancia - 0.5D) {
                    mejorDistancia = hasta;
                    stuckTicks = 0;
                } else {
                    stuckTicks++;
                }
                return;
            }
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
                // ENCAJADO FUERA DEL TÚNEL (I115): medido, el minero daba por perdida su celda 3 veces desde
                // `501,55,621` con `ruta=1 nodos ... alcanza=NO` — desde ahí NO hay ruta ninguna. Antes de rendirse se
                // vuelve a la CASETA (que está arriba y de la que sí se baja andando) y se replanifica desde allí.
                if (stuckTicks == TICKS_PARA_VOLVER_A_LA_CASETA) {
                    volviendoALaCaseta = true;
                    mejorDistancia = Double.MAX_VALUE;
                    stuckTicks = 0;
                }
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
                    //
                    // Y SI LE FALTA LUZ, AL TALLER EN VEZ DE A CAVAR (28-sep-2026; lo reportó el jugador: *"el minero
                    // no está poniendo antorchas … se ve muy oscuro"*). Aquí se forzaba `CAVAR` y eso **se saltaba el
                    // taller**, así que la primera bajada se hacía a oscuras y esas celdas dependían del repaso
                    // (`buscarHuecoDeLuz`). MEDIDO: en `build/medida-luz-2.log` el minero ya había cavado 8 celdas sin
                    // una sola antorcha. Se pide el taller **solo si el pueblo puede darle luz** (antorchas hechas, o
                    // carbón/leña del almacén O del inventario, siempre con palos): si no puede, cava a oscuras, que es lo
                    // único que le queda, y el repaso la encenderá cuando haya. Sin esa guarda, subir sería un bucle.
                    int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
                    boolean faltaLuz = cuantosEnInventario(Items.TORCH) <= 0 && elPuebloPuedeDarLuz(level);
                    if (faltaLuz || hayQueSubir(level)) {
                        fase = Fase.TALLER;
                        destino = VillageGenerator.puntoDeApoyoDeLaCaseta(level, center);
                        if (!comprobarDestino()) {
                            destino = null;
                        }
                    } else {
                        fase = Fase.CAVAR;
                        if (!prepararElPicado(level, nivel)) {
                            destino = null;
                        }
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
            case ENCENDER -> {
                // LA ANTORCHA QUE FALTABA: se pone (o se apunta el hueco para no quedarse en bucle con él).
                if (huecoDeLuz != null) {
                    if (ponerLaAntorcha(level, huecoDeLuz)) {
                        VillageManager.ponerSuceso(villager, "Enciende la mina");
                        anotar("encendio " + huecoDeLuz.toShortString());
                    } else {
                        VillageManager.marcarPuntoFallido(villager, huecoDeLuz);
                    }
                }
                huecoDeLuz = null;
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
            case ENCENDER -> "Encendiendo la mina";
        };
    }

    /** Lo que está haciendo <b>mientras va</b> de un sitio a otro (si no, la etiqueta cae al texto genérico). */
    private String verboDeCamino() {
        return switch (fase) {
            case RECOGER -> "Yendo al almacen";
            case CAVAR -> "Bajando a la mina";
            case ENCENDER -> "Con las antorchas";
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
            // EL MEJOR PICO DEL ALMACÉN, NO EL PRIMERO QUE APAREZCA. MEDIDO el 27-sep-2026: el filtro aceptaba los
            // cinco materiales y `VillageStorage.quitar` devuelve **el primero que cumpla**, así que el minero cogía el
            // de **MADERA** aunque el herrero hubiera forjado de **PIEDRA** (4 picos de madera y 2 de piedra forjados,
            // y **6 de madera** recibidos: `pico nuevo: minecraft:wooden_pickaxe`). El de madera pica piedra, así que
            // la mina avanza, pero **más despacio**: se le pide en orden de mejor a peor y se coge el primero que haya.
            ItemStack pico = null;
            for (net.minecraft.world.item.Item material : new net.minecraft.world.item.Item[]{
                    Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE,
                    Items.WOODEN_PICKAXE}) {
                pico = VillageStorage.quitar(level, center, s -> s.is(material), 1);
                if (pico != null && !pico.isEmpty()) {
                    break;
                }
            }
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
        //    SI AHÍ HAY AGUA O LAVA, el túnel se ahoga: se sella y, si el agua vuelve (un mar), la mina se cierra.
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos hueco = celda.above(dy);
            if (!picarYRecoger(level, hueco, true)) {
                selloPendiente = hueco;
                if (elAguaEsUnMar(hueco)) {
                    cerrarLaMina(level, celda);
                }
                return;
            }
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
        // ...Y EL SUELO SE VUELVE A MIRAR DESPUÉS: si la veta estaba DEBAJO de la celda, picarla deja aire bajo la
        // pieza y el escalón se queda flotando (y sin suelo por el que pisar). Misma regla que en la galería.
        rellenarElSuelo(level, celda.below());
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
     * <b>¿El agua de esta celda es una BOLSA o un MAR?</b> Se llama justo después de sellarla. La respuesta es
     * <b>MAR</b> si esa MISMA celda ya había pedido un sello (o sea: se selló, se repicó y el agua ha vuelto → el
     * cuerpo de agua está conectado y el túnel no puede pasar de ahí) o si ya van más de {@link #SELLOS_MAXIMOS}
     * sellos seguidos (el tope de siempre, que cubre el caso de un mar que va corriendo celda a celda).
     */
    private boolean elAguaEsUnMar(BlockPos hueco) {
        if (hueco.equals(selloDelMar)) {
            return true; // esta celda ya se había sellado y ha vuelto a pedirlo: el agua vuelve, es un mar
        }
        selloDelMar = hueco.immutable();
        return ++sellosSeguidos > SELLOS_MAXIMOS;
    }

    /**
     * <b>Una celda de galería</b>: hueco de paso de <b>TRES celdas</b> (el suelo es el terreno de debajo), relleno si
     * está hueco, veta de al lado, marco cada {@link VillageGenerator#MINA_GALERIA_SOPORTE_CADA} celdas y antorcha.
     * <p>
     * <b>POR QUÉ TRES Y NO DOS (medido el 27-sep-2026, con el código de vanilla delante)</b>: la galería va <b>a la
     * misma Y que la celda del caracol</b>, y esa celda lleva su <b>losa</b> (los pasos pares): el aldeano que baja
     * el caracol va <b>de pie ENCIMA de la losa</b>, o sea con los pies a {@code y + 0,5}. Su <b>nodo</b> de ruta es
     * entonces {@code y + 1} y su <b>caja</b> ocupa {@code y + 0,5 … y + 2,45}.
     * <ul>
     *   <li>Con la galería de <b>dos</b> celdas ({@code y} y {@code y + 1}), la celda {@code y + 2} es <b>roca</b>:
     *       la caja del aldeano <b>choca</b> con el techo (no puede ni entrar), y el planificador marca el vecino de
     *       {@code y + 1} como <b>BLOCKED</b> ({@code WalkNodeEvaluator.getPathTypeWithinMobBB} mete en el tipo del
     *       nodo <b>todas</b> las celdas de la caja, y una sola BLOCKED —malus −1— tumba el nodo entero). Como
     *       {@code findAcceptedNode} solo prueba el vecino <b>a la misma Y</b> (y {@code tryFindFirstGroundNodeBelow}
     *       —el que sabe bajar medio bloque— <b>solo se llama si el tipo es OPEN</b>), la galería queda
     *       <b>inalcanzable</b> desde el caracol: ni se entra ni se sale.</li>
     *   <li>Con <b>tres</b> celdas, el vecino de {@code y + 1} es OPEN (la caja cabe), y el planificador baja solo al
     *       nodo <b>de la galería</b> ({@code y}, el que tiene el suelo debajo): la entrada funciona. Y la salida
     *       también, porque {@code tryJumpOn} —el que sube medio bloque de vuelta a la losa— exige que la celda
     *       <b>encima</b> de la de la galería sea pisable, que es justo la tercera.</li>
     * </ul>
     * <b>Lo que se midió con esto</b> (corrida larga {@code MEDIR_MINERO}, 27-sep-2026): el minero abrió las celdas
     * <b>1, 2 y 3</b> de la galería del paso 32 <b>desde el propio anillo</b> (están a 1, 2 y 3 bloques de la celda
     * del caracol: dentro de su {@link #REACH} de 3,5) y ahí se quedó <b>3.600 ticks</b> con {@code galeria
     * hechas=3/24} congelado, {@code destino=507,46,610}, {@code nav=[sin ruta]}, el destino <b>borrado del cerebro</b>
     * ({@code destino=SIN DESTINO}) y {@code Volviendo a la caseta (encajado)} en bucle. La celda <b>4</b> está a
     * <b>4,0</b> del anillo: fuera de alcance, así que <b>tiene que entrar</b> a la galería… y no podía. El caracol
     * nunca tuvo este problema porque su hueco de paso ya son <b>tres</b> celdas.
     */
    private void picarLaCeldaDeLaGaleria(ServerLevel level, int nivel, int indice) {
        BlockPos celda = VillageGenerator.celdaDeLaGaleria(center, nivel, paso, indice);
        // EL HUECO DE PASO DE LA GALERÍA: `celda.above()` es la cabeza y `celda.above(2)` el tercer hueco (el que
        // deja entrar y salir al aldeano que viene de la losa del caracol; ver el porqué arriba). **LA CELDA DE LA
        // GALERÍA NO SE TOCA EN ESTE BUCLE**: se pica abajo, con su cuenta de sellos. Y ESE ERA EL FALLO MEDIDO
        // (26-sep-2026): el bucle empezaba en `dy = 0` —o sea que picaba la celda de la galería con
        // `cuentaElSello = false`—, así que si ahí había AGUA se sellaba en esa llamada (en silencio) y la de abajo,
        // que sí cuenta, se encontraba adoquín, lo picaba en el acto y devolvía el agua al túnel: el sello no duraba
        // ni un tick, `sellosSeguidos` no subía, el contador de la galería se quedaba en cero y el minero repetía la
        // misma celda PARA SIEMPRE (medido: 121 veces la celda 1 y 120 la 2 de la galería del paso 32, `pasos`
        // congelado 110.000 ticks, 0 líneas de `sella agua/lava` y 0 de `la mina se PARA`). El caracol ya lo hacía
        // bien (`dy` desde 1) y por eso se le pide lo mismo: tres celdas de hueco.
        picarYRecoger(level, celda.above(), false);
        picarYRecoger(level, celda.above(2), false);
        boolean eraSello = celda.equals(selloPendiente);
        if (!picarYRecoger(level, celda, true)) {
            selloPendiente = celda;
            if (elAguaEsUnMar(celda)) {
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
        // Y EL SUELO SE VUELVE A MIRAR **DESPUÉS** DE LAS VETAS: `minarLasVetasDeAlLado` pica las SEIS de al lado —el
        // SUELO incluido—, así que si debajo de la galería había una veta, se la lleva al inventario y deja **aire**: el
        // túnel se queda **sin suelo** y deja de ser un sitio por el que se anda (I114: se camina a una **casilla de
        // pie**). Se rellena otra vez para que la galería que el minero acaba de abrir se pueda recorrer.
        rellenarElSuelo(level, celda.below());
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
        if (estado.isAir()) {
            return true; // ya está hecho: no hay nada que picar
        }
        // EL AGUA Y LA LAVA VAN PRIMERO (medido el 26-sep-2026, y era el fallo que tenía la mina clavada): esta
        // comprobación estaba DESPUÉS de la de "lo del pueblo", y `elMineroPuedePicar` dice que NO al agua —no es
        // aire, ni terreno natural, ni una pieza de la mina—, así que una celda inundada salía por la guarda de "no
        // se toca" y devolvía `true` **sin picar y sin sellar**. Consecuencia medida: la galería del paso 32 se topó
        // con un acuífero y el contador de la galería se quedaba en cero (el agua no es aire), el minero volvía a
        // por la MISMA celda 121 veces, el agua no se sellaba nunca (0 líneas de `sella agua/lava`), el tope no
        // llegaba (0 líneas de `la mina se PARA`) y `pasos` se quedó 110.000 ticks en 32 — con el pico rompiéndose
        // de tanto "picar" agua (1 vez `se le ha roto el pico`).
        if (!estado.getFluidState().isEmpty()) {
            // AGUA O LAVA: **NO SE CIERRA LA MINA**. Lo pidio el jugador (26-sep-2026): "lo que debe hacer es seguir
            // minando para abajo y construir paredes que aislen la mina del agua, sacar lo que esta adentro y
            // construir escaleras para llegar al fondo". Asi que, en este orden:
            //   1) SE AISLA: se sellan con adoquin las vecinas que tengan fluido (las cuatro de lado y el techo; el
            //      suelo del tunel ya lo rellena el minero). La vecina de DELANTE tambien es una de las cuatro, asi
            //      que el tunel avanza por celdas YA SECAS (y el minero las pica como adoquin normal);
            //   2) SE SECA esta celda: queda de aire, o sea el tunel sigue siendo TRANSITABLE;
            //   3) y el tunel SIGUE: no hay tope ni cierre por agua.
            // Antes se sellaba la PROPIA celda del tunel: eso lo dejaba intransitable y la mina acababa cerrandose
            // con su piedra labrada (I127, con la instruccion vieja de "si es un mar, que pare", ya cambiada).
            aislarDelAgua(level, pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (cuentaElSello) {
                DevilRpg.LOGGER.info("[Village] El minero: aisla el agua de {} y el tunel sigue", pos.toShortString());
            }
            return true;
        }
        if (!VillageGenerator.elMineroPuedePicar(estado)) {
            return true; // algo que ha puesto el pueblo o el jugador: no se toca
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
                Block.popResource(level, pos, resto); // sin sitio en el inventario: se queda en el suelo del túnel
            }
        }
        // Y EL AGUJERO QUE ACABO DE ABRIR, CON PAREDES: si al lado hay agua (o lava), se sella AQUÍ. Sin esto el
        // túnel se inunda por la primera veta que se pique en la pared (ver `aislarDelAgua` para la medida).
        aislarDelAgua(level, pos);
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
        // Y EL TOPE SE MARCA TAMBIÉN EN LA CELDA DEL CARACOL DE ESTE PASO, que es donde lo lee el pueblo
        // (`VillageGenerator.laMinaLlegoAlTope` mira `celdaDelCaracol(paso)`). Si el que se topó con el mar fue una
        // GALERÍA, marcar solo su celda dejaba a la mina SIN TOPE: el minero volvía a por la misma celda y, como
        // ahora ya estaba sellada con piedra labrada (que `elMineroPuedePicar` no deja tocar), se quedaba en bucle
        // para siempre. Medido el 26-sep-2026: 110.000 ticks alternando las celdas 1 y 2 de la galería del paso 32.
        BlockPos caracol = VillageGenerator.celdaDelCaracol(center, VillageGenerator.cotaDeLaPlaza(level, center),
                paso);
        if (!caracol.equals(celda)) {
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(caracol.above(dy), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(caracol, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        }
        DevilRpg.LOGGER.info("[Village] El minero: la mina se PARA en {} ({} celdas de agua/lava seguidas): piedra"
                + " labrada de tope", celda.toShortString(), sellosSeguidos);
        VillageManager.ponerSuceso(villager, "La mina llego al tope");
    }

    /**
     * <b>Aisla del agua (o de la lava) la celda que el minero acaba de abrir</b>: sella con adoquín las
     * <b>vecinas que tengan fluido</b> (las 26 de alrededor) y deja el suelo como está (el minero ya lo rellena).
     * <p>
     * <b>SE LLAMA AL ABRIR CUALQUIER CELDA, NO SOLO AL SECAR UNA DE AGUA</b> (medido el 27-sep-2026, y es lo que
     * tenía la galería del paso 32 inundada): secar la celda de agua y sellar SUS vecinas no basta, porque el agua
     * vuelve por una vecina que estaba <b>seca</b> en ese momento — una <b>veta picada en la pared</b>
     * ({@link #minarLasVetasDeAlLado} deja el hueco de aire y no sella nada) o el <b>tercer hueco del techo</b> de
     * la galería (el que hace falta para entrar desde la losa del caracol). Medido: con la galería secada celda a
     * celda, `hechas` subió a 6/24 y **volvió a 0/24** en cuanto el minero se fue al taller, y se quedó en 0/24
     * <b>223 muestras (~8.900 ticks)</b> con la celda 1 y la 3 re-secándose en bucle. Es lo que pidió el jugador:
     * *"construir paredes que aislen la mina del agua"*.
     * <p>
     * <b>DOS COSAS NO SE TAPIAN NUNCA</b>:
     * <ul>
     *   <li>las celdas del <b>paso del CARACOL</b> ({@link VillageGenerator#esCeldaDePasoDelCaracol}): un adoquín en la
     *       celda de un paso <b>impar</b> se leería como su <b>pieza</b> y el paso se daría por hecho sin suelo (la
     *       lección de I127);</li>
     *   <li>las de la <b>capa del suelo</b> (por encima de {@code cotaDeLaPlaza - 1}): ahí el agua es del pueblo
     *       (la acequia, el estanque) y el minero no la tapia.</li>
     * </ul>
     * Y las celdas de la <b>galería</b> SÍ se sellan —su contador cuenta aire, así que el sello no engaña a nadie—,
     * incluida la de <b>DELANTE</b>, que es la que de verdad corta el agua: medido el 27-sep-2026, con la celda de
     * delante sin sellar (era acuífero todavía) el túnel se inundó de `hechas=7/24` a `0/24` en cuanto el minero subió
     * al taller, y el censo de agua (`AGUA` del arnés) enseñó que el agua estaba **en las celdas del propio túnel** y
     * en la de delante, con la pared oeste ya sellada.
     */
    private void aislarDelAgua(ServerLevel level, BlockPos celda) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        for (BlockPos vecina : BlockPos.betweenClosed(celda.offset(-1, -1, -1), celda.offset(1, 1, 1))) {
            if (vecina.equals(celda)) {
                continue; // la propia celda la seca quien llama
            }
            if (level.getBlockState(vecina).getFluidState().isEmpty()) {
                continue;
            }
            if (vecina.getY() >= nivel - 1) {
                continue; // en la superficie el agua es del pueblo: no se tapia
            }
            if (VillageGenerator.esCeldaDePasoDelCaracol(center, nivel, vecina)) {
                continue; // el paso del CARACOL no se tapiar (un adoquín ahí se leería como la pieza del paso)
            }
            level.setBlock(vecina, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
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
        // le sirven de nada, y con el inventario de OCHO huecos que tiene un aldeano, llenárselo de basura era lo que le
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
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos[] lados = VillageGenerator.ladosDeLaCeldaDelCaracol(center, nivel, paso);
        int tablones = cuantosEnInventario(Items.OAK_PLANKS);
        if (tablones < TABLONES_POR_MARCO) {
            return; // sin madera no hay marco (y no se inventa: la trae del almacén)
        }
        // SE DESCARTAN LOS LADOS QUE NO VALEN, en vez de renunciar al marco entero:
        //  · los que tienen algo del pueblo (o agua) — la pared de la caseta está pegada al caracol;
        //  · y LOS QUE CAEN EN UNA CELDA DE PASO DE LA MINA (medido el 26-sep-2026 con el arnés y con
        //    `tools/arnes/columna_mina.py`): los postes van en las paredes (los dos lados del radio), pero en las
        //    ESQUINAS del anillo la "pared" de dentro es OTRA CELDA DEL CARACOL, así que el poste caía en el paso del
        //    escalón de al lado y lo taponaba. Medido: el marco del paso 16 (esquina suroeste) dejaba sus dos troncos
        //    en `500,55,621` y `500,56,621`, que son los pies y la cabeza del paso 15 — la mina se quedaba SIN SALIDA
        //    por su propio soporte, el minero no tenía ruta a su faena y no bajaba ni una celda. Y no es un caso
        //    raro: de los 15 marcos del caracol, 14 caen en una esquina (los pasos 16, 32, 48… alternan la esquina
        //    suroeste y la noreste), así que renunciar al marco entero dejaba la mina sin ningún soporte. Con esto
        //    queda el poste que sí tiene pared (el de fuera) y su viga, que es como se ve un marco de mina.
        java.util.List<BlockPos> validos = new java.util.ArrayList<>();
        for (BlockPos lado : lados) {
            boolean vale = true;
            for (int dy = 1; dy <= 2 && vale; dy++) {
                BlockPos poste = lado.above(dy);
                if (!VillageGenerator.elMineroPuedePicar(level.getBlockState(poste))
                        || !level.getBlockState(poste).getFluidState().isEmpty()) {
                    vale = false; // ahí hay algo del pueblo (o agua): ese poste no se pone
                } else if (VillageGenerator.esCeldaDePasoDeLaMina(center, nivel, poste)) {
                    vale = false; // ése es el paso del escalón de al lado (la esquina del anillo)
                }
            }
            if (vale) {
                validos.add(lado);
            }
        }
        if (validos.isEmpty()) {
            anotar("sin marco en el paso " + paso + " (no hay pared libre: sus postes caen en el paso)");
            return;
        }
        for (BlockPos lado : validos) {
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
     * de monstruos, y estos nacen <b>dentro</b> de la muralla: la luz es parte del trabajo, no un adorno.
     * <p>
     * Devuelve {@code true} si la ha puesto (para que {@link #buscarHuecoDeLuz} sepa si el hueco está resuelto).
     */
    private boolean ponerLaAntorcha(ServerLevel level, BlockPos celda) {
        if (cuantosEnInventario(Items.TORCH) <= 0) {
            return false;
        }
        for (Direction lado : Direction.values()) {
            if (lado.getAxis().isVertical()) {
                continue;
            }
            if (level.getBlockState(celda.relative(lado)).isFaceSturdy(level, celda.relative(lado), lado.getOpposite())) {
                level.setBlock(celda, Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, lado.getOpposite()), Block.UPDATE_ALL);
                gastarDelInventario(Items.TORCH, 1);
                return true;
            }
        }
        return false;
    }

    /** ¿Esa celda ya tiene una antorcha (de pie o de pared)? */
    private static boolean esAntorcha(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).is(Blocks.TORCH) || level.getBlockState(p).is(Blocks.WALL_TORCH);
    }

    /**
     * <b>LA PRIMERA CELDA DE LA MINA A LA QUE LE FALTA SU ANTORCHA</b>, buscando <b>del frente hacia la boca</b> (lo
     * más cerca del minero primero), o {@code null} si no falta ninguna.
     * <p>
     * <b>Por qué existe</b> (medido el 28-sep-2026, y lo reportó el jugador: *"el minero no está poniendo antorchas
     * en las paredes de las escaleras de caracol ni en las galerías … se ve muy oscuro y es un punto peligroso"*): la
     * antorcha se pone <b>al cavar la celda</b> ({@code ponerLaAntorcha} sale sin poner nada si en ese momento no
     * lleva), y el minero <b>cava antes de tener antorchas</b> —el carbón sale de la mina, así que las primeras
     * vueltas son a oscuras— y <b>nunca volvía a pasar por esas celdas</b>. MEDIDO con el censo del arnés: el caracol
     * tenía sus pasos <b>0, 8, 16, 24 y 32</b> con la celda de la cabeza en {@code air} (ni una antorcha) y la galería
     * del paso 32 sus celdas 8 y 16 igual, mientras el minero llevaba <b>8 antorchas sin gastar</b> en el inventario (las
     * fabricó a las 03:34 y había cavado la celda 8 de la galería a las <b>03:31</b>).
     */
    @Nullable
    private BlockPos buscarHuecoDeLuz(ServerLevel level, int nivel) {
        // 1) EL CARACOL, del frente hacia la boca: los pasos que tocan antorcha (`CELDAS_POR_ANTORCHA`).
        int p = paso - Math.floorMod(paso, CELDAS_POR_ANTORCHA);
        for (; p >= 0; p -= CELDAS_POR_ANTORCHA) {
            BlockPos celda = VillageGenerator.celdaDelCaracol(center, nivel, p);
            if (!level.getBlockState(celda).is(VillageGenerator.piezaDelCaracol(p).getBlock())) {
                continue; // ese paso todavía no está cavado: su luz toca cuando se cave
            }
            BlockPos antorcha = celda.above(2);
            if (!esAntorcha(level, antorcha) && level.getBlockState(antorcha).isAir()) {
                return antorcha;
            }
        }
        // 2) LAS GALERÍAS ABIERTAS, de la más nueva hacia atrás (sus celdas 8, 16 y 24 son las que tocan).
        for (int g = paso; g > 0; g--) {
            if (!VillageGenerator.abreGaleria(g)) {
                continue;
            }
            for (int i = CELDAS_POR_ANTORCHA; i <= VillageGenerator.MINA_GALERIA_LARGO; i += CELDAS_POR_ANTORCHA) {
                BlockPos celda = VillageGenerator.celdaDeLaGaleria(center, nivel, g, i);
                if (!level.getBlockState(celda).isAir()) {
                    continue; // esa celda de la galería aún no está cavada
                }
                BlockPos antorcha = celda.above();
                if (!esAntorcha(level, antorcha) && level.getBlockState(antorcha).isAir()) {
                    return antorcha;
                }
            }
        }
        return null;
    }

    /**
     * <b>El taller</b> de la caseta, una faena por vuelta: fundir un mineral crudo en el horno, <b>hacer carbón
     * vegetal</b> quemando un tronco, o hacer antorchas con el carbón (o el carbón vegetal) y los palos. Devuelve
     * {@code false} cuando ya no hay nada que hacer y toca bajar lo sacado al almacén.
     * <p>
     * <b>La balsa ya no está aquí</b> (27-sep-2026): colar adoquín → pedernal lo hace el herrero de herramientas (ver
     * {@link VillagerSmithGoal}), que era lo que pedía el jugador para que el minero solo picara. Medido: con la balsa
     * aquí, el minero encadenaba <b>33 coladas</b> (46 en otra corrida) porque el pedernal se quedaba en su inventario y el
     * umbral miraba el almacén.
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
        // 2) LA BALSA YA NO ES SUYA (27-sep-2026): la cuela el HERRERO DE HERRAMIENTAS. Era la faena que le comía el
        //    tiempo de la mina, y estaba MEDIDA: el pedernal se lo quedaba él en el inventario (esta rama guarda con
        //    `guardarEnInventario`) mientras el umbral que miraba era el del ALMACÉN, así que no se alcanzaba nunca y
        //    encadenaba coladas: 33 en una corrida y 46 en otra, con las últimas celdas de la galería a 5-8 minutos
        //    cada una. Lo pidió el jugador: *"pasar la balsa (colar adoquín → pedernal) y el acarreo al herrero de
        //    herramientas para que el minero solo pique"*. La receta está ahora en
        //    `VillagerSmithGoal.recetaDeTransformacion` (faena "Colando", en la balsa) y su ciclo la deja en el
        //    almacén en cada faena, que es lo que hace que el objetivo se cumpla.
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
     * resto lo suelta. Antes era "todo o nada": un hueco con 64 palos no se soltaba nunca y el inventario se le quedaba
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
            int devuelto = resto.getCount(); // lo que no le cupiera al almacén se queda en el inventario (no se tira)
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
     * hueco menos para el mineral, y con seis o siete huecos de recados el inventario se le quedaba sin sitio (ver
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

    /** ¿Lleva un pico encima (en la mano o en el inventario)? */
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

    /** Saca del inventario hasta {@code cuantas} unidades de lo que cumpla el filtro (o {@code null} si no hay). */
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
