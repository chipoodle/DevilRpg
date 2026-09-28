package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModCapabilities;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * <b>Guardia de la aldea</b>: el aldeano adulto <b>sobrante</b> que se alista en la milicia (lo decide
 * {@code VillageManager.repartirGuardia}, que le pone {@code GUARD_TAG} y su tipo).
 * <p>
 * Lo que hace, en orden de prioridad:
 * <ol>
 *   <li><b>PELEA</b> (lo primero: si no, nunca defienden). Si tiene un monstruo cerca, el guardia va a por él:
 *       el <b>espadachín</b> levanta el <b>escudo</b> y pega con la espada; el <b>arquero</b> dispara flechas
 *       (que saca de su mochila). El <b>bloqueo del escudo lo hace el propio juego</b>: {@code LivingEntity.hurt}
 *       comprueba {@code isDamageSourceBlocked} en cualquier entidad que tenga el escudo levantado, así que basta
 *       con {@code startUsingItem(OFF_HAND)}. Un guardia no huye: {@code VillageManager.quitarPanico} le vacía la
 *       actividad PANIC del cerebro.</li>
 *   <li><b>EQUIPARSE DEL ALMACÉN</b> (lo pidió el jugador): espada y escudo (espadachín) o arco y flechas
 *       (arquero), y además la <b>armadura</b> que el pueblo tenga fabricada, pieza a pieza. Lo que coge sale del
 *       almacén de verdad (es la producción de los herreros).</li>
 *   <li><b>RONDA</b> de día: punto a punto por <b>alrededor</b> del pueblo, por dentro del muro.</li>
 *   <li><b>PUERTAS</b> de noche: se planta en una de las <b>cuatro puertas</b> del muro y <b>rota</b> sola (el
 *       puesto sale del reloj de juego y de su número de guardia).</li>
 * </ol>
 * Se mueve <b>por el cerebro</b> ({@link VillageManager#caminarHacia}), como el resto de goals de la aldea.
 * <p>
 * <b>Y SE LE VE EL EQUIPO</b>: el aldeano de vanilla no puede enseñar armadura ni lo que lleva en la mano
 * ({@code VillagerRenderer} solo pone cabeza, profesión y brazos cruzados), así que los guardias se dibujan con
 * {@code GuardVillagerModel} (cuerpo de jugador + cabeza de aldeano) y las capas de vanilla de armadura y de objeto
 * en mano. Para eso el <b>cliente</b> tiene que saber quién es guardia: la marca de verdad son los datos
 * persistentes (solo del servidor), así que se <b>espeja</b> en una attachment <b>sincronizada</b>
 * ({@link ModCapabilities#VILLAGER_GUARD}, ver {@link #sincronizarMarcaDeGuardia}).
 */
public class VillagerGuardGoal extends Goal {

    /** Espadachín: espada y escudo. */
    public static final int ESPADACHIN = 0;
    /** Arquero: arco y flechas. */
    public static final int ARQUERO = 1;
    /**
     * El arquero <b>tal como viaja al cliente</b> en la marca sincronizada (1 = espadachín, 2 = arquero). El 0 está
     * reservado a "no es guardia", que es lo que necesita el render para decidir.
     */
    private static final int ARQUERO_SINCRONIZADO = 2;

    /** Radio de la ronda: por DENTRO del muro (el muro está a FENCE_RADIUS). */
    private static final double RADIO_RONDA = VillageGenerator.FENCE_RADIUS - 7.0D;
    /** Radio de las puertas: un poco por dentro, para estorbar lo justo en el paso. */
    private static final double RADIO_PUERTA = VillageGenerator.FENCE_RADIUS - 2.0D;
    /** Distancia a la que ya se considera que está en el punto. */
    private static final double REACH = 3.0D;
    /** Ticks de plantón en cada punto de la ronda, mirando al campo. */
    private static final int ESPERA_TICKS = 120;
    /** Cada cuántos puntos de la ronda el guardia baja al <b>corral anexo</b> (etapa D). */
    private static final int RONDA_CADA_ANEXO = 3;
    /**
     * Puestos de la guardia <b>alrededor del corral anexo</b> (relativos a su base): <b>fuera</b> de la valla, dos
     * bloques al oeste, repartidos a los <b>lados del portón</b>. La fila del portón ({@code dz = 0}) no se pisa y el
     * cercado no se cruza: el portón es la <b>única puerta del rebaño</b>.
     */
    private static final int[] PUNTOS_DEL_CORRAL = {-6, -4, -2, 2, 4, 6};

    /** Cada cuántos puntos de la ronda el guardia pasa por la <b>arboleda del pueblo</b> (etapa E). Si coincide con
     *  el corral, manda el corral (está fuera de la valla y es el que más lo necesita). */
    private static final int RONDA_CADA_ARBOLEDA = 2;
    /**
     * Puestos de la arboleda, relativos a su centro: un guardia distinto por cada uno y todos a <b>un bloque</b> del
     * centro. Los cuatro plantones están a dos, así que ningún guardia se queda plantado donde va a crecer un tronco.
     */
    private static final int[][] PUNTOS_DE_LA_ARBOLEDA = {
            {0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1},
    };
    /** Cada cuánto cambia el relevo de puertas (2 min): así rotan en la misma noche. */
    private static final int RELEVO_TICKS = 2 * 60 * 20;
    /** Si se queda atascado (no se acerca) deja el punto y prueba con el siguiente. */
    private static final int STUCK_LIMIT = 200;
    /**
     * Ticks de "no me acerco" que se aguantan <b>antes de preguntar si hay ruta que alcanza</b> (ver el `tick`). La
     * pregunta es cara (una búsqueda de ruta), así que no se hace en cada tick: se hace cuando ya parece atasco.
     */
    private static final int TICKS_PARA_PREGUNTAR_SI_HAY_RUTA = 60;
    /**
     * Cuántas veces, como mucho, se le <b>reafirma el destino</b> al guardia cuando su <b>cerebro va a otra parte</b>
     * (su paseo le pisa el `WALK_TARGET`: ver {@link VillageManager#elCerebroVaA}). El tope es lo que separa "el
     * cerebro le está peleando el destino" de "este mundo de verdad no deja llegar": en el segundo caso se agota y el
     * guardia se rinde y vuelve a su ronda, como siempre.
     */
    private static final int REAFIRMACIONES_DE_RONDA = 3;
    /** Reafirmaciones gastadas en el destino actual (ver {@link #REAFIRMACIONES_DE_RONDA}). */
    private int reafirmaciones;
    /**
     * Ticks de "no me acerco" al puesto de entrenamiento antes de comprobar **si el guardia va de verdad hacia él**
     * (ruta viva que alcance + cerebro apuntando al puesto). Medido (I119): se rendía 12 s con el destino pisado por
     * otra faena o con la ruta viva quedándose corta. Es una comprobación cara (una consulta de ruta), de ahí que se
     * haga una vez por episodio de atasco.
     */
    private static final int TICKS_PARA_COMPROBAR_SI_VA = 40;
    private static final int REST_TICKS = 10;
    /** Si se aleja más de esto del centro de la aldea, deja de hacer la ronda. */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 26.0D;
    /** Velocidad de la ronda (y de la carrera al almacén si le falta el arma). */
    private static final float VELOCIDAD = 0.6F;
    /** Flechas que se lleva el arquero del almacén de una vez. */
    private static final int FLECHAS_POR_VIAJE = 16;

    // --- combate ------------------------------------------------------------------------------------

    /** Radio en el que el guardia ve a un monstruo y va a por él. */
    private static final double RADIO_COMBATE = 16.0D;
    /**
     * Hasta dónde persigue: no se va del pueblo a matar zombis por el mundo (deriva del radio de la aldea).
     * <p>
     * Llega hasta la <b>granja anexa</b> (etapa D): el corral está <b>fuera de la valla</b> (a 43-57 del centro), así
     * que con el radio viejo ({@code FENCE_RADIUS + 8} = 44) los guardias <b>no defendían a los animales</b> ni al
     * ganadero: un zombi que entrara al corral se paseaba a 5 bloques de la ronda sin que nadie fuera a por él.
     */
    private static final double RADIO_PERSEGUIR = VillageGenerator.FENCE_RADIUS + 22.0D;
    /** Distancia a la que el espadachín ya pega. */
    private static final double ALCANCE_ESPADA = 2.8D;
    /** Ticks entre golpes de espada y entre flechas. */
    private static final int CADENCIA_ESPADA = 20;
    /**
     * Daño base del <b>espadazo</b> del guardia. No se puede sacar del atributo {@code ATTACK_DAMAGE} (el aldeano no
     * lo tiene, ver {@link #golpearConLaEspada}): es la base de un espadachín (4) y a eso se le suma el <b>filo</b> del
     * arma que lleva forjada.
     */
    private static final float DANO_BASE_ESPADA = 4.0F;
    private static final int CADENCIA_ARCO = 30;
    /** Cada cuántos ticks vuelve a mirar si hay enemigo cerca (buscar entidades no se hace por tick). */
    private static final int ESCANEO_TICKS = 10;
    /** Velocidad y dispersión de la flecha del arquero, y su daño base. */
    private static final float VELOCIDAD_FLECHA = 1.6F;
    private static final float DISPERSION_FLECHA = 6.0F;
    private static final float DANO_FLECHA = 4.0F;

    /** Piezas de armadura, en el orden en que se las va poniendo. */
    private static final EquipmentSlot[] ARMADURAS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /** Número de guardia (0..n): fija su puerta en el relevo y su secuencia de ronda. */
    private final int indice;
    /** true mientras la faena es ir al almacén a equiparse; false cuando ya toca la ronda/puerta. */
    private boolean equipando;
    /** Le falta el equipo (arma/escudo o arco/flechas): sin él no marcha a la guarida, pero SÍ hace la ronda. */
    private boolean sinEquipo;
    /** ¿Hay en el almacén algo suyo que ponerse? Se refresca al ritmo del escaneo de enemigos (no por tick). */
    private boolean hayEquipoEnAlmacen;
    @Nullable
    private BlockPos destino;
    @Nullable
    private Monster enemigo;
    private int espera;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    /**
     * El nodo de la <b>ruta viva</b> que el guardia perseguía en el tick anterior: es lo que mide el <b>avance por la
     * ruta</b> (ver el {@code tick}), que en una ronda circular es la única señal buena de "voy bien" —la distancia en
     * línea recta <b>sube</b> en un rodeo aunque el guardia esté andando su camino—.
     */
    @Nullable
    private BlockPos nodoDeLaRuta;
    /** La ruta de la que venía ese nodo: el avance solo cuenta <b>dentro de la misma ruta</b> (ver el {@code tick}). */
    @Nullable
    private Path rutaDeLaQueVengo;
    private int restTicks;
    /** Contador de puntos de ronda (para que no repita el mismo). */
    private int paso;
    /** El último puesto que se cantó en el log, para no repetir el aviso con el mismo sitio (ver {@code start}). */
    @Nullable
    private BlockPos ultimoPuesto;
    /** Ticks que faltan para volver a buscar enemigo / para el siguiente golpe o flecha. */
    private int escanear;
    private int cadencia;
    /** true mientras la milicia está de asalto en la guarida (lo dice {@code VillageManager.marchaDe}). */
    private boolean marchando;

    public VillagerGuardGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.indice = villager.getPersistentData().getInt(VillageManager.GUARD_INDEX_TAG);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** ¿Este aldeano está alistado en la guardia? */
    public static boolean esGuardia(Villager villager) {
        // SERVIDOR: manda la marca de verdad (los datos persistentes). CLIENTE: esos datos no llegan, así que se lee
        // la copia SINCRONIZADA (si no, el render no veía a ningún guardia y no enseñaba armadura ni arma).
        return villager.getPersistentData().getBoolean(VillageManager.GUARD_TAG) || guardaSincronizada(villager) > 0;
    }

    /** Tipo de guardia de un aldeano alistado ({@link #ESPADACHIN} o {@link #ARQUERO}). */
    public static int tipoDe(Villager villager) {
        int sincronizada = guardaSincronizada(villager);
        if (sincronizada > 0) {
            return sincronizada == ARQUERO_SINCRONIZADO ? ARQUERO : ESPADACHIN;
        }
        return villager.getPersistentData().getInt(VillageManager.GUARD_TYPE_TAG);
    }

    /** Lo que dice la copia <b>sincronizada</b> (0 = no es guardia, 1 = espadachín, 2 = arquero). */
    private static int guardaSincronizada(Villager villager) {
        Integer dato = villager.getExistingDataOrNull(ModCapabilities.VILLAGER_GUARD);
        return dato == null ? 0 : dato;
    }

    /**
     * <b>Espeja en la copia SINCRONIZADA la marca de la milicia</b> (la llama quien alista o da de baja al aldeano, y
     * el latido la repite: solo escribe cuando el valor CAMBIA, para no mandar un paquete por aldeano cada 10 s).
     * <p>
     * Es lo que hace que el <b>cliente</b> sepa quién es guardia y el renderer le ponga el modelo con armadura y arma.
     */
    public static void sincronizarMarcaDeGuardia(Villager villager) {
        int nuevo = villager.getPersistentData().getBoolean(VillageManager.GUARD_TAG)
                ? (villager.getPersistentData().getInt(VillageManager.GUARD_TYPE_TAG) == ARQUERO
                        ? ARQUERO_SINCRONIZADO : 1)
                : 0;
        if (guardaSincronizada(villager) != nuevo) {
            villager.setData(ModCapabilities.VILLAGER_GUARD, nuevo); // las attachments sincronizadas viajan al cambiar
        }
    }

    @Override
    public boolean canUse() {
        if (restTicks > 0) {
            restTicks--;
            return false;
        }
        if (villager.isBaby() || !esGuardia(villager) || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        // EL TURNO DE DESCANSO, TAMBIÉN AQUÍ: sin esta comprobación el goal se reiniciaría al tick siguiente de
        // cortarse por el turno y el guardia no llegaría a descansar nunca (ver `canContinueToUse`).
        if (!aldeaEnAsalto() && leTocaElTurnoDeDescanso() && !leTocaEntrenar()) {
            return false;
        }
        // OJO: aquí NO se comprueba `estaDescansando` (cosa que sí hacen los demás goals del pueblo): el guardia está
        // de servicio también de NOCHE, que es cuando le toca la puerta. El cerebro vanilla lo manda a la cama en la
        // franja de descanso y, cediendo el goal, el guardia se acostaba: medido en el log del jugador, el guardia
        // murió "Durmiendo" sin haber hecho una sola guardia de noche.
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        double dist2 = dx * dx + dz * dz;
        // PRIMERO el equipo: si le falta, va a por él... pero SOLO si el almacén tiene algo suyo que ponerse. Sin esa
        // comprobación, un guardia sin espada (y con el almacén sin hierro) se quedaba PLANTADO en el almacén para
        // siempre: medido en el guardado del jugador, la única guardia de la aldea llevaba horas allí sin patrullar
        // ni una vez (y con la etiqueta de "Durmiendo", que era el refresco genérico, no la realidad).
        sinEquipo = !equipado(level);
        // REVISIÓN DIARIA DEL ALMACÉN (lo pidió el jugador: *"todos los días deben revisar una vez por lo menos el
        // almacén y verificar si hay equipo para ellos, y si hay uno mejor que lo cambien. Los equipos con encantamientos
        // tienen prioridad"*): aunque ya vaya equipado, una vez al día va a mirar si hay algo MEJOR y, si lo hay, se
        // cambia y DEJA EL VIEJO en el almacén (de ahí lo recicla el herrero). Antes solo iba si le FALTABA equipo: un
        // guardia con armadura de cuero no se cambiaba a la de hierro nunca, y el que ya llevaba espada no volvía a
        // mirar. Medido en su partida: el almacén tenía 1 espada de hierro, 1 escudo y 1 casco y 1 botas de cuero para
        // una milicia de 4-7, y los guardias patrullaban de noche sin equipo.
        boolean tocaRevision = revisionPendiente(level);
        // La pregunta es la MISMA para los dos casos ("¿hay algo mejor que lo que llevo?"): al que le falta el arma,
        // cualquier arma del almacén le vale (su hueco vale 0), así que no hacen falta dos filtros.
        equipando = (sinEquipo || tocaRevision) && hayMejoraEnElAlmacen(level);
        // MARCHA A LA GUARIDA: si la milicia ha marchado, su sitio es el núcleo. Aquí la "correa" de la aldea no
        // cuenta: el núcleo está a 75-95 bloques del pueblo, así que marchar es salirse del término a propósito.
        // Y a la guarida solo se va CON el equipo puesto: sin arma no se asalta nada (se queda de ronda).
        BlockPos marcha = VillageManager.marchaDe(level, objectiveIndex);
        marchando = marcha != null;
        if (marchando) {
            destino = sinEquipo ? (equipando ? VillageStorage.puntoDeApoyo(level, center) : puntoDeGuardia(level))
                    : marcha;
            return destino != null;
        }
        if (dist2 > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            // LEJOS del pueblo y sin marcha: está VOLVIENDO de un asalto (o se perdió). El destino es volver, no un
            // punto de ronda: antes esto cortaba el goal y el guardia se quedaba plantado donde lo pillara.
            destino = equipando ? VillageStorage.puntoDeApoyo(level, center)
                    : new BlockPos(center.getX(), VillageGenerator.cotaDeLaPlaza(level, center), center.getZ());
            return destino != null;
        }
        destino = equipando ? VillageStorage.puntoDeApoyo(level, center) : puntoDeGuardia(level);
        return destino != null;
    }

    @Override
    public void start() {
        stuckTicks = 0;
        espera = 0;
        cadencia = 0;
        escanear = 0;
        enemigo = null;
        equipando = false;
        sinEquipo = false;
        hayEquipoEnAlmacen = false;
        mejorDistancia = Double.MAX_VALUE;
        nodoDeLaRuta = null; // faena nueva: el avance por la ruta se mide desde cero
        rutaDeLaQueVengo = null;
        irAlDestino();
        // SOLO SE CANTA CUANDO EL PUESTO CAMBIA. Con el aviso en cada `start()` el log se llenaba de "nuevo puesto"
        // repitiendo el MISMO sitio: de noche el relevo no depende de `paso`, y si el goal se reinicia (el cerebro
        // empujando a dormir) salían diez líneas por segundo con el mismo BlockPos (medido con el arnés, modo noche).
        if (destino != null && !destino.equals(ultimoPuesto)) {
            ultimoPuesto = destino;
            DevilRpg.LOGGER.info("[Village] Guardia {}: nuevo puesto {} (paso {}, aldea {}){}", villager.getUUID(),
                    destino, paso, objectiveIndex, equipando ? " yendo antes al almacen a equiparse" : "");
        }
    }

    @Override
    public boolean canContinueToUse() {
        // EL TURNO DE DESCANSO (lo pidió el jugador): cada guardia tiene su turno para comer, descansar o entrenar en
        // la barraca, y se van turnando para no dejar la aldea sola. Cuando le toca, el goal se corta y mandan los
        // demás (el de la taberna, el de la cama): el guardia vuelve solo cuando le toca el servicio otra vez. Con un
        // enemigo a la vista o la aldea en asalto NO hay descanso: primero se pelea.
        if (enemigo == null && !aldeaEnAsalto() && leTocaElTurnoDeDescanso() && !leTocaEntrenar()) {
            return false;
        }
        // Tampoco se corta por la hora de descanso: la guardia de noche es parte del servicio (ver `canUse`). Si el
        // aldeano acabara durmiendo (por ejemplo porque el cerebro lo tumbó en la cama), el goal se corta igual.
        // `destino == null` ocurre cuando el goal se acaba de quedar sin faena (por ejemplo, tras ir al almacén y no
        // encontrar equipo): se deja terminar para que el descanso haga efecto y no se quede dando vueltas.
        //
        // Y RENDIRSE ES **SALTAR EL PUESTO** (invariante I3: se rinde si NO SE ACERCA). Antes se rendía y volvía a
        // empezar con el MISMO `paso`, o sea con el MISMO puesto: medido con el arnés en la partida del jugador
        // (aldea 2, de día, guardia espadachín del puesto 0), la guardia empujaba la valla del corral 10 s
        // (`stuck=200`), se rendía, volvía a empezar en el mismo sitio y así en bucle (`STOP paso=3` → `START
        // paso=3` → la misma valla), sin patrullar NUNCA: el jugador lo veía "dando vueltas sobre sí misma de
        // manera errática" y con la etiqueta "Patrullando el corral" clavada. Ahora el puesto que no se alcanza se
        // salta y la ronda sigue (el siguiente paso puede volver a intentarlo: la aldea cambia sola).
        if (destino != null && stuckTicks >= STUCK_LIMIT) {
            DevilRpg.LOGGER.info("[Village] Guardia {}: no llego a {} (aldea {}): me salto el puesto y sigo la ronda",
                    villager.getUUID(), destino, objectiveIndex);
            // Y SE APUNTA COMO FALLIDO (I33): sin esto, el puesto al que no llega se le vuelve a dar y el guardia
            // se queda en bucle. Medido en el log del jugador (aldea 2, de noche): pasos 12, 13, 14, 15 y 16 seguidos
            // con el MISMO `BlockPos{x=1354, y=120, z=1414}` y "atascado 200 ticks" por vuelta — de noche el puesto
            // sale del RELOJ (el relevo de puertas) y no de `paso`, así que saltárselo no cambiaba nada.
            VillageManager.marcarPuntoFallido(villager, destino);
            apuntarPuntoMaloDeLaRonda(destino);
            paso++;
            return false;
        }
        return destino != null && esGuardia(villager) && !villager.isBaby() && !villager.isSleeping();
    }

    @Override
    public void tick() {
        if (destino == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        // 0) EL TURNO DE ENTRENAMIENTO, LO PRIMERO (lo pidió el jugador: "o entrenando en la sala de entrenamiento de
        //    sus barracas"): en sus turnos de descanso impares, el guardia se va a la DIANA de la barraca, le pega
        //    (golpe cada 2 s, con su sonido y sus partículas) y suda la fuerza: cada 5 min de diana cuenta como una
        //    matanza (I62), así que entrena de verdad pero despacio.
        if (!aldeaEnAsalto() && enemigo == null && leTocaEntrenar()) {
            if (entrenar(level)) {
                return;
            }
        }
        // 1) COMBATE, lo primero: si hay un monstruo cerca, el guardia va a por él (si no, nunca defienden). El
        //    escaneo va cada ESCANEO_TICKS porque buscar entidades no se puede hacer en cada tick.
        if (--escanear <= 0) {
            escanear = ESCANEO_TICKS;
            enemigo = buscarEnemigo(level);
            // Y de paso se mira si el almacén tiene ya algo suyo: mirar un cofre (54 huecos) NO se hace por tick.
            hayEquipoEnAlmacen = hayMejoraEnElAlmacen(level);
        }
        if (enemigo != null) {
            if (enemigo.isAlive() && !enemigo.isRemoved()) {
                calmar();
                pelear(level);
                return;
            }
            enemigo = null;
        }
        // 2) Sin enemigo: el escudo se baja y, si ha perdido el arma (o se ha quedado sin flechas), vuelve al almacén
        //    aunque el goal ya esté en marcha (`canUse` solo se llama al arrancar, y este goal corre de seguido).
        bajarEscudo();
        if (!equipado(level)) {
            sinEquipo = true;
            // SOLO SE VA AL ALMACÉN SI SE PUEDE LLEGAR Y NO ACABA DE IR. Lo reportó el jugador: *"los guardias están
            // yendo al almacén, se equipan y se quedan ahí parados sin hacer nada"* y, con captura, *"Segismunda, se ve
            // ciclada tratando de ir al almacén"*. MEDIDO en su guardado: el almacén tenía **3 espadas de hierro y 0
            // ESCUDOS**, y un espadachín solo está equipado con **espada + escudo** → iba, no lo encontraba (el herrero
            // forja el escudo en su turno) y volvía a intentarlo… en bucle, con la etiqueta "Yendo al almacén" siempre
            // puesta. Y la casilla de apoyo también se aparca cuando no se alcanza (I33). Ahora, tras un viaje que no
            // completa el equipo, se queda de RONDA un rato ({@link #TICKS_ENTRE_VIAJES}) en vez de encadenar viajes:
            // la aldea no se queda sin guardia y el escudo llega cuando llega.
            BlockPos almacen = VillageStorage.puntoDeApoyo(level, center);
            boolean viajeReciente = level.getGameTime() - ultimoViajeAlAlmacen < TICKS_ENTRE_VIAJES;
            equipando = hayEquipoEnAlmacen && !viajeReciente && almacen != null
                    && !VillageManager.esPuntoFallido(villager, almacen);
            if (equipando) {
                ultimoViajeAlAlmacen = level.getGameTime();
                if (!almacen.equals(destino)) {
                    destino = almacen;
                    mejorDistancia = Double.MAX_VALUE;
                    stuckTicks = 0;
                }
            }
        } else {
            sinEquipo = false;
        }
        // 3) MARCHA: el destino se refresca cada tick (puede empezar o acabar mientras el goal corre). Si se acabó y
        //    el guardia está lejos del pueblo, el destino pasa a ser VOLVER.
        BlockPos marcha = VillageManager.marchaDe(level, objectiveIndex);
        marchando = marcha != null;
        if (marchando && !sinEquipo && !equipando && !marcha.equals(destino)) {
            destino = marcha;
            mejorDistancia = Double.MAX_VALUE;
            stuckTicks = 0;
        } else if (!marchando && !equipando && !destino.closerThan(center, MAX_DISTANCE_FROM_CENTER)) {
            destino = new BlockPos(center.getX(), VillageGenerator.cotaDeLaPlaza(level, center), center.getZ());
            mejorDistancia = Double.MAX_VALUE;
            stuckTicks = 0;
        }
        // 3.b) Y EL HIERRO QUE HA LOOTEADO, AL ALMACÉN (27-sep-2026, medido): el que mata el zombi de raid se queda
        //      sus pepitas en el zurrón (`AggressiveZombieEntity.dropCustomDeathLoot`) y el herrero las necesita para
        //      el pico de hierro. El guardia VA a dejarlas (no espera a pasar por delante: su ronda es un círculo
        //      alrededor del pueblo y no pasa por el almacén). Va DESPUÉS del combate y de la marcha (pelear manda) y
        //      antes de la ronda; si el sitio está aparcado (I33) se queda como estaba.
        if (!marchando && !equipando && llevaHierro()) {
            BlockPos almacen = VillageStorage.puntoDeApoyo(level, center);
            if (almacen != null && !VillageManager.esPuntoFallido(villager, almacen)) {
                if (VillageManager.distanciaA(villager, almacen) <= REACH) {
                    dejarElHierroEnElAlmacen(level); // ha llegado: lo deja
                } else if (!almacen.equals(destino)) {
                    destino = almacen;
                    mejorDistancia = Double.MAX_VALUE;
                    stuckTicks = 0;
                    VillageManager.ponerActividad(villager, "Llevando el hierro al almacen");
                }
            }
        }
        villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
        double distancia = Math.sqrt(villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D,
                destino.getZ() + 0.5D));
        if (distancia > REACH) {
            VillageManager.caminarHacia(villager, destino, VELOCIDAD);
            // ATASCADO = NO ACERCARSE **NI AVANZAR POR LA RUTA** (27-sep-2026). El contador medía solo la distancia en
            // línea recta, y la ronda es un CÍRCULO: en un rodeo la recta SUBE aunque el guardia vaya bien por su
            // camino, así que se rendía con el destino delante. MEDIDO en los dos logs del 27-sep: cinco guardias se
            // rindieron con `ruta=16-32 nodos … alcanza=SI` y `nav=[… alcanza]`, o sea **con camino y andando** (p. ej.
            // `Ubaldo / Patrullando el corral`: destino a 20 bloques, ruta de **30 nodos**). Ahora también cuenta como
            // progreso **consumir nodos** de la ruta viva: si el nodo que persigue cambia, va hacia allí.
            boolean avanzoPorLaRuta = false;
            var rutaViva = villager.getNavigation().getPath();
            if (rutaViva != null && !rutaViva.isDone()) {
                BlockPos nodo = rutaViva.getNextNodePos();
                // AVANCE = consumir un nodo **de la MISMA ruta**. Si el planificador la ha vuelto a calcular (objeto
                // `Path` nuevo), NO cuenta: si no, un guardia empujando una pared —que recalcula cada pocos ticks—
                // se resetearía el contador solo y no se rendiría nunca (el bucle de I3 que este contador evita).
                avanzoPorLaRuta = rutaViva == rutaDeLaQueVengo && nodoDeLaRuta != null
                        && !nodo.equals(nodoDeLaRuta);
                rutaDeLaQueVengo = rutaViva;
                nodoDeLaRuta = nodo;
            } else {
                rutaDeLaQueVengo = null;
                nodoDeLaRuta = null;
            }
            if (distancia < mejorDistancia - 0.5D || avanzoPorLaRuta) {
                mejorDistancia = Math.min(mejorDistancia, distancia);
                stuckTicks = 0;
                reafirmaciones = 0;
            } else {
                stuckTicks++;
                // PERO NO ES ATASCO SI HAY RUTA QUE LLEGA. La ronda es un círculo alrededor del pueblo y sus caminos
                // dan RODEOS: el guardia se rendía en mitad de un rodeo de 32 nodos y el log, en ese mismo momento,
                // decía `alcanza=SI` (había camino: lo que subía era la distancia en línea recta). Se pregunta UNA vez,
                // cuando ya parece atasco, y si hay ruta que alcanza se sigue andando (medido, I115).
                if (stuckTicks == TICKS_PARA_PREGUNTAR_SI_HAY_RUTA
                        && VillageManager.hayRutaQueAlcanza(villager, destino)) {
                    stuckTicks = 0;
                }
                // Y TAMPOCO ES ATASCO SI EL CEREBRO VA A OTRA PARTE (I125): eso no es "no puedo llegar", es que **le
                // están mandando a otro sitio** —el paseo del cerebro le pisa el `WALK_TARGET`, medido: un guardia
                // "Yendo a entrenar" con el cerebro apuntando al almacén, y el jugador lo describió como *"caminando
                // erráticamente, como balanceándose… dos tareas en su cerebro en conflicto"*. Se reafirma el destino y
                // se sigue, con un TOPE de reafirmaciones para que un mundo que de verdad no deja acabe rindiéndose.
                if (!VillageManager.elCerebroVaA(villager, destino) && reafirmaciones < REAFIRMACIONES_DE_RONDA) {
                    reafirmaciones++;
                    mejorDistancia = Double.MAX_VALUE;
                    stuckTicks = 0;
                    VillageManager.caminarHacia(villager, destino, VELOCIDAD);
                }
            }
            VillageManager.ponerActividad(villager, equipando ? "Yendo al almacén"
                    : (marchando ? "Marchando a la guarida" : actividadDeGuardia(level)));
            return;
        }
        VillageManager.parar(villager);
        if (equipando) {
            boolean listo = equipar(level);
            equipando = false;
            paso++;
            destino = puntoDeGuardia(level);
            mejorDistancia = Double.MAX_VALUE;
            stuckTicks = 0;
            // Si el pueblo todavía NO tiene su pieza (los herreros van despacio: el hierro sale de los zombies), no se
            // queda yendo y viniendo al almacén: patrulla igual y vuelve a mirar dentro de un rato.
            restTicks = listo ? REST_TICKS : 400;
            return;
        }
        if (marchando) {
            // En el núcleo: se planta y pelea (el combate de arriba va primero en cada tick) y NO rota la ronda.
            villager.swing(InteractionHand.MAIN_HAND);
            VillageManager.ponerActividad(villager, "Asaltando la guarida");
            return;
        }
        // En el punto: un plantón mirando al campo (y el guardia gira la cabeza solo, con el look control).
        villager.swing(InteractionHand.MAIN_HAND);
        VillageManager.ponerActividad(villager, actividadDeGuardia(level));
        if (++espera >= ESPERA_TICKS) {
            espera = 0;
            paso++;
            destino = puntoDeGuardia(level);
            mejorDistancia = Double.MAX_VALUE;
            stuckTicks = 0;
        }
    }

    @Override
    public void stop() {
        // Se corta el servicio (se rindió en un puesto, se durmió, dejó de ser guardia...): queda dicho en el log,
        // que es lo único que permite reconstruir después por dónde andaba (el goal no se guarda con la partida).
        // SOLO SE AVISA CUANDO SE RINDE DE VERDAD (I94). Antes se logueaba con CUALQUIER `stuckTicks`/`espera` > 0, y
        // como a este goal le cortan el servicio cada pocos ticks, el log del jugador se llenaba de
        // "deja el puesto ... atascado 1-4 ticks" VARIAS VECES POR SEGUNDO (medido en su log, 22:16:34-22:16:59) y no
        // se veía nada más. Un aviso por rendición real (`STUCK_LIMIT`), que es lo que sirve para diagnosticar.
        if (stuckTicks >= STUCK_LIMIT) {
            DevilRpg.LOGGER.info("[Village] Guardia {}: se rinde en el puesto {} (paso {}, atascado {} ticks, plantado {}"
                            + " ticks, aldea {})", villager.getUUID(), destino, paso, stuckTicks, espera,
                    objectiveIndex);
        }
        destino = null;
        enemigo = null;
        nodoDeLaRuta = null;
        rutaDeLaQueVengo = null;
        // OJO: **NO** se pone `espera` (el rato plantado en el puesto) a cero. Se ponía, y con el servicio cortándose
        // cada pocos ticks el contador NUNCA llegaba a `ESPERA_TICKS`: el guardia se quedaba en el **paso 1** para
        // siempre y no completaba una sola ronda (los `paso 1` del log del jugador). Al volver al mismo puesto el rato
        // cuenta igual; si el puesto cambia, `puntoDeGuardia` reajusta el destino y el contador sigue su curso.
        restTicks = REST_TICKS;
        bajarEscudo();
        VillageManager.parar(villager);
    }

    // --- el combate ---------------------------------------------------------------------------------

    /**
     * El monstruo más cercano al que merece la pena ir, o {@code null}. Se mira solo a los {@link Monster} (los
     * asediadores del mod lo son) y <b>dentro del término de la aldea</b>: un guardia no se va del pueblo a matar
     * zombis por el mundo (dejaría la puerta sola).
     */
    @Nullable
    private Monster buscarEnemigo(ServerLevel level) {
        Monster mejor = null;
        double mejorDist = RADIO_COMBATE * RADIO_COMBATE;
        for (Monster monstruo : level.getEntitiesOfClass(Monster.class,
                villager.getBoundingBox().inflate(RADIO_COMBATE))) {
            if (!monstruo.isAlive() || monstruo.isRemoved()) {
                continue;
            }
            if (!marchando) {
                // Fuera de una marcha, el guardia no se va del término de la aldea a matar zombis (dejaría la puerta
                // sola). En un ASALTO sí: la guarida está a 75-95 bloques del pueblo y está llena de enemigos.
                double dxAldea = monstruo.getX() - center.getX();
                double dzAldea = monstruo.getZ() - center.getZ();
                if (dxAldea * dxAldea + dzAldea * dzAldea > RADIO_PERSEGUIR * RADIO_PERSEGUIR) {
                    continue;
                }
            }
            double dist = villager.distanceToSqr(monstruo);
            if (dist < mejorDist) {
                mejorDist = dist;
                mejor = monstruo;
            }
        }
        if (mejor != null) {
            return mejor;
        }
        // NADIE CERCA, PERO HAY UN INTRUSO DENTRO DE LA ALDEA: va a por él aunque esté lejos. Sin esto, un agresivo
        // que entra al pueblo por la esquina contraria a la ronda no lo veía ningún guardia (el escaneo son 16
        // bloques alrededor de cada uno) y el único que "defendía" era el SELLO, que lo teletransportaba fuera en el
        // primer latido: el jugador lo vio y lo reportó como antinatural (ver `expulsarHostilesDeLaAldea`, I59). Ahora
        // la milicia cruza el pueblo a por él, que es lo que se espera de una guardia.
        //
        // El aldeano-zombi se queda FUERA de esta búsqueda larga a propósito: puede ser una curación en marcha del
        // jugador y no se le va a mandar la milicia encima desde el otro extremo del pueblo (de cerca, como siempre,
        // sí entra en la lista de arriba).
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        double mejorLejos = RADIO_PERSEGUIR * RADIO_PERSEGUIR;
        for (Monster monstruo : level.getEntitiesOfClass(Monster.class, new AABB(center)
                .inflate(VillageGenerator.FENCE_RADIUS + 8.0D, 24.0D, VillageGenerator.FENCE_RADIUS + 8.0D))) {
            if (!monstruo.isAlive() || monstruo.isRemoved()
                    || monstruo instanceof net.minecraft.world.entity.monster.ZombieVillager) {
                continue;
            }
            if (!VillageManager.dentroDelRecinto(cota, center, monstruo, VillageGenerator.FENCE_RADIUS)) {
                continue; // fuera del recinto no es asunto de la guardia (dejaría la puerta sola)
            }
            double dist = villager.distanceToSqr(monstruo);
            if (dist < mejorLejos) {
                mejorLejos = dist;
                mejor = monstruo;
            }
        }
        return mejor;
    }

    /** Reparte el trabajo según el tipo: espada o arco. */
    private void pelear(ServerLevel level) {
        Monster objetivo = enemigo;
        if (objetivo == null) {
            return;
        }
        villager.getLookControl().setLookAt(objetivo, 30.0F, 30.0F);
        if (tipoDe(villager) == ARQUERO) {
            pelearConArco(level, objetivo);
        } else {
            pelearConEspada(level, objetivo);
        }
    }

    /** Espadachín: escudo levantado y espadazos. */
    private void pelearConEspada(ServerLevel level, Monster objetivo) {
        subirEscudo();
        // El rumbo se le da SIEMPRE (también pegado al enemigo): el aldeano de vanilla, cuando le pegan, escribe en su
        // cerebro un destino "huir de aquí" (actividad PANIC); si al estar a tiro se le quitara el destino, el pánico
        // le ganaría la carrera y el guardia saldría corriendo entre golpe y golpe.
        VillageManager.caminarHacia(villager, objetivo.blockPosition(), VELOCIDAD);
        VillageManager.ponerActividad(villager, "Atacando");
        if (distanciaHorizontal(objetivo) > ALCANCE_ESPADA) {
            return; // todavía va a por él (el rumbo ya está puesto arriba)
        }
        if (cadencia > 0) {
            cadencia--;
            return;
        }
        cadencia = CADENCIA_ESPADA;
        villager.swing(InteractionHand.MAIN_HAND);
        golpearConLaEspada(level, objetivo);
        level.playSound(null, villager.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.5F, 1.2F);
    }

    /**
     * El <b>espadazo del guardia</b>. No se puede usar {@code villager.doHurtTarget(...)}: ese método pide el
     * atributo {@code ATTACK_DAMAGE} de quien golpea y el <b>aldeano no lo tiene</b> (vanilla solo le da vida y
     * velocidad), así que el juego se caía con
     * {@code IllegalArgumentException: Can't find attribute minecraft:generic.attack_damage} en cuanto un guardia
     * alcanzaba a un monstruo (crash medido en la partida del jugador: un espadachín recién alistado atacando a una
     * araña).
     * <p>
     * El daño se calcula a mano: la base del espadachín más el <b>filo</b> del arma que lleva (los herreros del pueblo
     * les forjan espadas de hierro, y a veces con encantamientos), y se aplica con el aldeano como <b>atacante</b>
     * ({@code mobAttack}), así que el monstruo reacciona (se gira, ataca de vuelta) igual que si le hubiera pegado
     * un jugador.
     */
    private void golpearConLaEspada(ServerLevel level, Monster objetivo) {
        ItemStack arma = villager.getMainHandItem();
        DamageSource fuente = level.damageSources().mobAttack(villager);
        // El filo (y demás encantamientos del arma) se aplican con la API del juego, que es la que usa `doHurtTarget`.
        float dano = EnchantmentHelper.modifyDamage(level, arma, objetivo, fuente, DANO_BASE_ESPADA);
        objetivo.hurt(fuente, dano);
        EnchantmentHelper.doPostAttackEffectsWithItemSource(level, objetivo, fuente, arma);
        // El empujón del espadazo (el de vanilla lo da `Mob.doHurtTarget`): knockback hacia donde mira el guardia.
        double dx = Math.sin(Math.toRadians(villager.getYRot()));
        double dz = -Math.cos(Math.toRadians(villager.getYRot()));
        objetivo.knockback(0.4D, -dx, -dz);
    }

    /** Arquero: flechas desde lejos (y si se queda sin flechas, el tick lo manda al almacén). */
    private void pelearConArco(ServerLevel level, Monster objetivo) {
        if (flechas() <= 0) {
            return; // sin flechas no puede disparar: el tick lo lleva al almacén a por más
        }
        // Aquí el que aguanta la posición es él. OJO: se le PARA la navegación, no se le manda caminar a su propia
        // celda: escribir un destino en el sitio donde ya está hacia el que el aldeano gira sin avanzar (y con el
        // pánico del aldeano reescribiendo el rumbo) es lo que le hacía dar vueltas sobre sí mismo de manera errática
        // (lo reportó el jugador: "Bibiana está dando vueltas sobre sí misma"). Parar también le gana al pánico,
        // porque este goal tiene las banderas de movimiento.
        VillageManager.parar(villager);
        if (distanciaHorizontal(objetivo) > RADIO_COMBATE - 1.0D) {
            VillageManager.caminarHacia(villager, objetivo.blockPosition(), VELOCIDAD);
            VillageManager.ponerActividad(villager, "Buscando distancia");
            return;
        }
        VillageManager.ponerActividad(villager, "Disparando");
        if (cadencia > 0) {
            cadencia--;
            return;
        }
        cadencia = CADENCIA_ARCO;
        dispararFlecha(level, objetivo);
    }

    /**
     * Apaga el <b>pánico</b> del aldeano: le borra los recuerdos de <b>"me han pegado"</b>, que son los que disparan
     * la actividad PANIC del cerebro (la de salir corriendo y esconderse). Un guardia que huye no defiende nada.
     * <p>
     * No se puede hacer quitando la actividad (ya se probó): {@code Brain.addActivity} solo <b>añade</b>
     * comportamientos (no reemplaza la lista) y {@code removeAllBehaviors} se lleva por delante el cerebro entero.
     */
    private void calmar() {
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY);
    }

    /**
     * Dispara una flecha al enemigo. La flecha es de verdad (una {@link Arrow} del juego, con su dueño) y se gasta
     * del inventario del arquero: si no le queda, no dispara.
     */
    private void dispararFlecha(ServerLevel level, Monster objetivo) {
        if (sacarFlecha() <= 0) {
            return;
        }
        Arrow flecha = new Arrow(level, villager, new ItemStack(Items.ARROW), null);
        double dx = objetivo.getX() - villager.getX();
        double dy = objetivo.getEyeY() - flecha.getY();
        double dz = objetivo.getZ() - villager.getZ();
        double plano = Math.sqrt(dx * dx + dz * dz);
        flecha.shoot(dx, dy + plano * 0.2D, dz, VELOCIDAD_FLECHA, DISPERSION_FLECHA);
        flecha.setBaseDamage(DANO_FLECHA);
        level.addFreshEntity(flecha);
        level.playSound(null, villager.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 0.8F, 1.0F);
        VillageManager.ponerSuceso(villager, "Disparo una flecha");
    }

    /**
     * Levanta el escudo (mano secundaria). A partir de ahí <b>el bloqueo lo hace el propio juego</b>:
     * {@code LivingEntity.hurt} comprueba {@code isDamageSourceBlocked} en cualquier entidad que esté bloqueando, así
     * que no hay que tocar el daño. Solo lo hace el espadachín, que es el que lleva escudo.
     */
    private void subirEscudo() {
        if (tipoDe(villager) == ARQUERO || !villager.getOffhandItem().is(Items.SHIELD)) {
            return;
        }
        if (!villager.isBlocking()) {
            villager.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    /** Baja el escudo: fuera de combate no se va con el escudo levantado. */
    private void bajarEscudo() {
        if (villager.isBlocking()) {
            villager.stopUsingItem();
        }
    }

    /** Distancia HORIZONTAL al enemigo (invariante I2: la aldea se mide en el plano XZ). */
    private double distanciaHorizontal(net.minecraft.world.entity.Entity otro) {
        double dx = otro.getX() - villager.getX();
        double dz = otro.getZ() - villager.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // --- el equipo ----------------------------------------------------------------------------------

    /**
     * ¿Ya lleva lo suyo? El <b>espadachín</b> necesita espada (mano principal) y escudo (secundaria); el
     * <b>arquero</b>, arco y flechas en la mochila. Se comprueba de verdad leyendo sus manos y su inventario, no con
     * una marca: si le rompen el escudo o le quitan la espada, vuelve al almacén.
     */
    private boolean equipado(ServerLevel level) {
        if (tipoDe(villager) == ARQUERO) {
            return villager.getMainHandItem().is(Items.BOW) && flechas() > 0;
        }
        // El ESPADACHÍN vale con cualquier arma (espada o hacha), no solo con la de hierro: los encantados y las de
        // mejor material cuentan (ver `valorDeArma`). Exigir `IRON_SWORD` era lo que dejaba al guardia de brazos
        // cruzados con una espada de diamante o encantada en el almacén.
        return valorDeArma(villager.getMainHandItem()) > 0
                && villager.getOffhandItem().is(Items.SHIELD);
    }

    /**
     * Va al almacén y se lleva lo suyo (si el pueblo lo tiene fabricado: es la producción de los herreros).
     *
     * @return {@code true} si al terminar ya lleva el equipo completo (si no, se lo dirá {@link #equipado})
     */
    private boolean equipar(ServerLevel level) {
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen == null) {
            return false;
        }
        // ARMADURA primero: pieza a pieza, la MEJOR que haya (los encantados primero; si no hay nada mejor que lo que
        // ya lleva, se queda con lo suyo y el del almacén sigue ahí para otro).
        for (EquipmentSlot pieza : ARMADURAS) {
            // OJO con el hueco: un casco NO vale de peto. Antes esto lo miraba `cogerArmadura` y al reescribirlo hay que
            // seguir mirándolo.
            mejorar(level, almacen, pieza,
                    s -> s.getItem() instanceof ArmorItem armadura && armadura.getEquipmentSlot() == pieza,
                    VillagerGuardGoal::valorDeArmadura, "la armadura");
        }
        if (tipoDe(villager) == ARQUERO) {
            mejorar(level, almacen, EquipmentSlot.MAINHAND, s -> s.is(Items.BOW),
                    VillagerGuardGoal::valorDeArco, "el arco");
            if (flechas() < FLECHAS_POR_VIAJE) {
                int flechas = VillagePantry.sacar(almacen, s -> s.is(Items.ARROW), FLECHAS_POR_VIAJE);
                if (flechas > 0) {
                    ItemStack resto = guardarEnInventario(new ItemStack(Items.ARROW, flechas));
                    if (!resto.isEmpty()) {
                        VillageStorage.guardar(level, center, resto);
                    }
                    VillageManager.ponerSuceso(villager, "Cogio " + flechas + " flechas del almacen");
                }
            }
            marcarRevisionHecha(level);
            return equipado(level);
        }
        mejorar(level, almacen, EquipmentSlot.MAINHAND, VillagerGuardGoal::esUnArma,
                VillagerGuardGoal::valorDeArma, "el arma");
        mejorar(level, almacen, EquipmentSlot.OFFHAND, s -> s.is(Items.SHIELD),
                VillagerGuardGoal::valorDeEscudo, "el escudo");
        marcarRevisionHecha(level);
        return equipado(level);
    }

    /**
     * <b>Se pone lo mejor</b> que haya en el almacén en ese hueco, si es mejor que lo que ya lleva, y <b>deja lo viejo
     * en el almacén</b> (de ahí lo recicla el herrero: 1 hierro viejo = 1 lingote). Los <b>encantados tienen
     * prioridad</b> (lo pidió el jugador): cualquier pieza encantada gana a una sin encantar, y entre encantadas gana
     * la del material mejor.
     * <p>
     * Se copia el objeto <b>ENTERO</b> (con sus encantamientos y su desgaste), no un modelo por tipo: antes se cogía
     * `new ItemStack(s.getItem(), 1)`, que se llevaba la espada <b>sin</b> sus encantamientos.
     */
    private void mejorar(ServerLevel level, Container almacen, EquipmentSlot hueco, Predicate<ItemStack> filtro,
            java.util.function.ToIntFunction<ItemStack> valor, String nombre) {
        ItemStack puesto = villager.getItemBySlot(hueco);
        int actual = valor.applyAsInt(puesto);
        ItemStack elegido = null;
        int mejorValor = Math.max(actual, 0);
        for (int i = 0; i < almacen.getContainerSize(); i++) {
            ItemStack s = almacen.getItem(i);
            if (s.isEmpty() || !filtro.test(s)) {
                continue;
            }
            int v = valor.applyAsInt(s);
            if (v > mejorValor) {
                mejorValor = v;
                elegido = s;
            }
        }
        if (elegido == null) {
            return;
        }
        ItemStack nuevo = elegido.copyWithCount(1);
        elegido.shrink(1);
        almacen.setChanged();
        if (!puesto.isEmpty()) {
            // LO VIEJO, DE VUELTA AL ALMACÉN (para el herrero). Si no cupiera, se lo queda en la mochila: nunca se tira.
            ItemStack resto = VillageStorage.guardar(level, center, puesto.copy());
            if (!resto.isEmpty()) {
                guardarEnInventario(resto);
            }
        }
        villager.setItemSlot(hueco, nuevo);
        VillageManager.ponerSuceso(villager, "Se equipo con " + nombre + " del almacen");
        DevilRpg.LOGGER.info("[Village] {} se equipo con {} ({}): {} (aldea {})", villager.getUUID(), nombre,
                nuevo.getHoverName().getString(), nuevo.isEnchanted() ? "ENCANTADO" : "normal", objectiveIndex);
    }

    // --- lo que vale cada pieza (los encantados, primero) ---------------------------------------------

    /** Lo que suma una pieza <b>encantada</b>: gana a cualquier pieza sin encantar (lo pidió el jugador). */
    private static final int PRIORIDAD_ENCANTADO = 100;

    private static boolean esUnArma(ItemStack s) {
        return s.getItem() instanceof net.minecraft.world.item.SwordItem
                || s.getItem() instanceof net.minecraft.world.item.AxeItem;
    }

    /** Cuánto vale un arma: por material y, sobre todo, si está <b>encantada</b>. {@code 0} = no es un arma. */
    private static int valorDeArma(ItemStack s) {
        if (s.isEmpty() || !esUnArma(s)) {
            return 0;
        }
        net.minecraft.world.item.Item i = s.getItem();
        int base = i == Items.NETHERITE_SWORD ? 6
                : i == Items.DIAMOND_SWORD ? 5
                : i == Items.IRON_SWORD ? 4
                : i == Items.STONE_SWORD ? 3
                : i == Items.GOLDEN_SWORD ? 2
                : i == Items.WOODEN_SWORD ? 1
                : 2; // los hachas: por debajo de la espada de su material, pero valen de arma
        return base + (s.isEnchanted() ? PRIORIDAD_ENCANTADO : 0);
    }

    /** Cuánto vale un arco: encantado primero. {@code 0} = no es un arco. */
    private static int valorDeArco(ItemStack s) {
        return s.is(Items.BOW) ? 1 + (s.isEnchanted() ? PRIORIDAD_ENCANTADO : 0) : 0;
    }

    /** Cuánto vale un escudo: encantado primero. */
    private static int valorDeEscudo(ItemStack s) {
        return s.is(Items.SHIELD) ? 1 + (s.isEnchanted() ? PRIORIDAD_ENCANTADO : 0) : 0;
    }

    /** Cuánto vale una pieza de armadura: por material (cuero, oro, malla, hierro, diamante, netherita) y encantada. */
    private static int valorDeArmadura(ItemStack s) {
        if (!(s.getItem() instanceof ArmorItem armadura)) {
            return 0;
        }
        net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> mat = armadura.getMaterial();
        int base = mat == net.minecraft.world.item.ArmorMaterials.NETHERITE ? 6
                : mat == net.minecraft.world.item.ArmorMaterials.DIAMOND ? 5
                : mat == net.minecraft.world.item.ArmorMaterials.IRON ? 4
                : mat == net.minecraft.world.item.ArmorMaterials.CHAIN ? 3
                : mat == net.minecraft.world.item.ArmorMaterials.GOLD ? 2
                : mat == net.minecraft.world.item.ArmorMaterials.LEATHER ? 1 : 0;
        return base <= 0 ? 0 : base + (s.isEnchanted() ? PRIORIDAD_ENCANTADO : 0);
    }

    // --- la revisión diaria del almacén ---------------------------------------------------------------

    /**
     * <b>DEJA EN EL ALMACÉN EL HIERRO QUE HA LOOTEADO</b> (27-sep-2026). Lo pidió el jugador: *"los guardias, al matar
     * zombis que vengan de un raid del mundo, conseguirán hierro"*, y ese hierro tiene que llegar al <b>herrero</b>
     * para los picos.
     * <p>
     * <b>MEDIDO, y era el eslabón que faltaba</b>: el zombi de raid suelta sus pepitas y <b>el que lo mata se las
     * queda en el zurrón</b> ({@code AggressiveZombieEntity.dropCustomDeathLoot}: *"el que mata, lootea"*), pero el
     * guardia <b>no tenía ningún paso que las dejara</b> en el almacén: en el arnés, el que mataba (un herrero de
     * armas) llevaba su pepita <b>de t=300 a t=2.700</b> —y con etiquetas `Yendo al almacen` / `Volviendo al
     * almacen` de por medio— mientras el almacén seguía a **0**; el que depositaba era el herrero por SU goal
     * (que sí tiene su "deja lo tuyo"). Este paso es el que le faltaba al guardia: cuando lleva hierro y está en el
     * almacén (o pasa a {@value #REACH} bloques de su casilla de apoyo, que es por donde ronda), lo deja.
     * <p>
     * Se deja <b>solo el hierro</b> (pepitas y lingotes): es la cadena que pidió el jugador y no toca nada de lo que
     * el guardia necesita para pelear (su arma, su escudo y sus flechas no entran aquí).
     */
    private void dejarElHierroEnElAlmacen(ServerLevel level) {
        if (!llevaHierro()) {
            return; // lo normal: no lleva nada que dejar (y así no se toca el cofre por tick)
        }
        BlockPos apoyo = VillageStorage.puntoDeApoyo(level, center);
        if (apoyo == null || VillageManager.distanciaA(villager, apoyo) > REACH) {
            return; // todavía no está en el almacén
        }
        int pepitas = 0;
        int lingotes = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty() || !(s.is(Items.IRON_NUGGET) || s.is(Items.IRON_INGOT))) {
                continue;
            }
            ItemStack sobra = VillageStorage.guardar(level, center, s.copy());
            int dejado = s.getCount() - sobra.getCount();
            if (dejado <= 0) {
                continue; // el almacén no tiene sitio: se lo queda (nunca se tira nada del pueblo)
            }
            if (s.is(Items.IRON_NUGGET)) {
                pepitas += dejado;
            } else {
                lingotes += dejado;
            }
            s.shrink(dejado);
            if (s.isEmpty()) {
                villager.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (pepitas + lingotes > 0) {
            VillageManager.ponerSuceso(villager, "Deja el hierro en el almacen");
            DevilRpg.LOGGER.info("[Village] {} deja en el almacen el hierro que ha loteado: {} pepita(s) y {}"
                    + " lingote(s)", villager.getName().getString(), pepitas, lingotes);
        }
    }

    /** ¿Lleva hierro el guardia (pepitas o lingotes) que tenga que dejar en el almacén? */
    private boolean llevaHierro() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.IRON_NUGGET) || s.is(Items.IRON_INGOT)) {
                return true;
            }
        }
        return false;
    }

    /** Día de juego de la última revisión del almacén (marca del aldeano). */
    private static final String MARCA_REVISION = "DevilRpgEquipoRevisado";

    /** ¿Toca revisar el almacén hoy? Cada guardia, como mucho una vez al día (lo pidió el jugador). */
    private boolean revisionPendiente(ServerLevel level) {
        long dia = level.getGameTime() / 24000L;
        return villager.getPersistentData().getLong(MARCA_REVISION) < dia;
    }

    private void marcarRevisionHecha(ServerLevel level) {
        villager.getPersistentData().putLong(MARCA_REVISION, level.getGameTime() / 24000L);
    }

    /** ¿Hay en el almacén algo <b>mejor</b> que lo que ya lleva puesto (o algo suyo que le falte)? */
    private boolean hayMejoraEnElAlmacen(ServerLevel level) {
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen == null) {
            return false;
        }
        for (EquipmentSlot pieza : ARMADURAS) {
            if (hayMejor(almacen, pieza,
                    s -> s.getItem() instanceof ArmorItem armadura && armadura.getEquipmentSlot() == pieza,
                    VillagerGuardGoal::valorDeArmadura)) {
                return true;
            }
        }
        if (tipoDe(villager) == ARQUERO) {
            return hayMejor(almacen, EquipmentSlot.MAINHAND, s -> s.is(Items.BOW), VillagerGuardGoal::valorDeArco);
        }
        return hayMejor(almacen, EquipmentSlot.MAINHAND, VillagerGuardGoal::esUnArma, VillagerGuardGoal::valorDeArma)
                || hayMejor(almacen, EquipmentSlot.OFFHAND, s -> s.is(Items.SHIELD),
                        VillagerGuardGoal::valorDeEscudo);
    }

    private boolean hayMejor(Container almacen, EquipmentSlot hueco, Predicate<ItemStack> filtro,
            java.util.function.ToIntFunction<ItemStack> valor) {
        int actual = Math.max(valor.applyAsInt(villager.getItemBySlot(hueco)), 0);
        for (int i = 0; i < almacen.getContainerSize(); i++) {
            ItemStack s = almacen.getItem(i);
            if (!s.isEmpty() && filtro.test(s) && valor.applyAsInt(s) > actual) {
                return true;
            }
        }
        return false;
    }

    /** Saca del almacén una unidad de lo que pida el filtro y se la pone en la mano indicada. */
    private void cogerYEquipar(Container almacen, EquipmentSlot mano, Predicate<ItemStack> filtro, String nombre) {
        if (!villager.getItemBySlot(mano).isEmpty()) {
            return; // ya lleva algo en esa mano
        }
        // Primero se mira QUÉ pieza es: `VillagePantry.sacar` devuelve cuántas unidades ha sacado, no el objeto.
        ItemStack modelo = ItemStack.EMPTY;
        for (int i = 0; i < almacen.getContainerSize(); i++) {
            ItemStack s = almacen.getItem(i);
            if (!s.isEmpty() && filtro.test(s)) {
                modelo = new ItemStack(s.getItem(), 1);
                break;
            }
        }
        if (modelo.isEmpty()) {
            return; // el pueblo todavía no tiene esa pieza fabricada (la hacen los herreros)
        }
        if (VillagePantry.sacar(almacen, filtro, 1) <= 0) {
            return; // se la ha llevado otro guardia entre la comprobación y el saque
        }
        villager.setItemSlot(mano, modelo);
        VillageManager.ponerSuceso(villager, "Se equipo con " + nombre + " del almacen");
        DevilRpg.LOGGER.info("[Village] {} se equipo con {} (aldea {})",
                villager.getUUID(), nombre, objectiveIndex);
    }

    /**
     * Le pone la pieza de armadura de ese hueco si el pueblo tiene alguna fabricada (de hierro o de cuero: la que
     * haya). La armadura también <b>cuenta</b> de verdad (el juego la usa para reducir el daño), aunque el modelo del
     * aldeano todavía no la dibuje.
     */
    private void cogerArmadura(Container almacen, EquipmentSlot pieza) {
        if (!villager.getItemBySlot(pieza).isEmpty()) {
            return; // ya lleva algo puesto en ese hueco
        }
        Predicate<ItemStack> esDeEseHueco = s -> s.getItem() instanceof ArmorItem armadura
                && armadura.getEquipmentSlot() == pieza;
        ItemStack modelo = ItemStack.EMPTY;
        for (int i = 0; i < almacen.getContainerSize(); i++) {
            ItemStack s = almacen.getItem(i);
            if (!s.isEmpty() && esDeEseHueco.test(s)) {
                modelo = new ItemStack(s.getItem(), 1);
                break;
            }
        }
        if (modelo.isEmpty() || VillagePantry.sacar(almacen, esDeEseHueco, 1) <= 0) {
            return;
        }
        villager.setItemSlot(pieza, modelo);
        VillageManager.ponerSuceso(villager, "Se puso la armadura del almacen");
        DevilRpg.LOGGER.info("[Village] {} se puso la armadura del hueco {} (aldea {})",
                villager.getUUID(), pieza, objectiveIndex);
    }

    /**
     * ¿Tiene el almacén algo <b>suyo</b> que ponerse? (su espada y su escudo, su arco y flechas, o cualquier pieza de
     * armadura). Es lo que decide si merece la pena ir a por el equipo: sin esta comprobación, un guardia sin espada
     * —y con el pueblo sin hierro— se quedaba <b>plantado en el almacén para siempre</b> esperando algo que no existía
     * (medido en el guardado del jugador: la única guardia de la aldea, horas allí, sin patrullar ni una vez).
     */
    private boolean hayEquipoEnElAlmacen(ServerLevel level) {
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen == null) {
            return false;
        }
        for (int i = 0; i < almacen.getContainerSize(); i++) {
            ItemStack s = almacen.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean loSuyo = tipoDe(villager) == ARQUERO
                    ? (s.is(Items.BOW) || s.is(Items.ARROW))
                    : (s.is(Items.IRON_SWORD) || s.is(Items.SHIELD));
            if (loSuyo || s.getItem() instanceof ArmorItem) {
                return true; // la armadura también se la pone (pieza a pieza)
            }
        }
        return false;
    }

    /**
     * Radio (bloques) alrededor del almacén en el que un guardia cuenta como "estar en el almacén".
     * <p>
     * <b>12 y no 6</b>, y está medido: el punto de apoyo del almacén (el sitio al que los aldeanos intentan ir) es
     * <b>INALCANZABLE</b> en la partida del jugador — su log lo enseña con <b>siete aldeanos distintos</b>
     * (*"no consigue llegar a BlockPos{x=517, y=64, z=666}: lo deja por 5 min"*: el herrero de armas, el de
     * herramientas, el cocinero, el ganadero...). Con 6 bloques, un guardia que se queda a 7 del cofre **no se arma**;
     * con 12, el guardia se arma al pasar cerca aunque no consiga entrar al cobertizo. Y no hace falta entrar: el
     * contenedor se lee del mundo (`VillageStorage.almacen`), así que la distancia solo decide si la revisión ocurre.
     */
    private static final double RADIO_DE_LA_REVISION = 12.0D;

    /**
     * <b>REVISIÓN POR CERCANÍA</b> (lo pidió el jugador: *"¡DEBEN DE ARMARSE! diario tienen que checarlo, cada que se
     * acerquen al almacén"*): si ese guardia está <b>junto al almacén</b>, se le revisa el equipo y se le pone lo mejor.
     * <p>
     * Va por <b>DISTANCIA</b>, no por haber llegado a un destino, para que no dependa de que su goal consiga navegar
     * hasta el punto de apoyo del almacén: medido con el arnés (I94), los 5 guardias de la aldea del jugador se quedaban
     * <b>sin nada</b> y con su marca de revisión en <b>0</b> durante toda la corrida — nunca llegaban a {@code equipar}.
     * Lo llama el latido del pueblo ({@code VillageManager}), así que pasa <b>en cada pasada</b>: un guardia al que le
     * falta el arma la coge en cuanto pase por delante del almacén, y el que ya va equipado revisa <b>una vez al día</b>
     * si hay algo mejor.
     * <p>
     * Se reutiliza el propio goal como objeto temporal: así el equipo se coge con las MISMAS reglas de siempre (lo
     * mejor, los encantados primero, y lo viejo de vuelta al almacén para que el herrero lo recicle).
     */
    public static boolean equiparSiEstaCercaDelAlmacen(ServerLevel level, Villager guardia, BlockPos center,
            int objectiveIndex) {
        BlockPos punto = VillageStorage.puntoDeApoyo(level, center);
        if (guardia.distanceToSqr(punto.getX() + 0.5D, punto.getY() + 0.5D, punto.getZ() + 0.5D)
                > RADIO_DE_LA_REVISION * RADIO_DE_LA_REVISION) {
            return false; // no está en el almacén: ya irá (o ya lo mandará el latido)
        }
        VillagerGuardGoal revision = new VillagerGuardGoal(guardia, center, objectiveIndex);
        if (revision.equipado(level) && !revision.revisionPendiente(level)) {
            return true; // ya va equipado y ya revisó hoy: no se toca
        }
        return revision.equipar(level);
    }

    /** Cuántas flechas lleva encima el arquero (en su mochila de aldeano). */
    private int flechas() {
        int total = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.ARROW)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Gasta una flecha de la mochila del arquero y devuelve cuántas ha sacado (0 o 1). */
    private int sacarFlecha() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.ARROW)) {
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return 1;
            }
        }
        return 0;
    }

    /**
     * Mete lo que se le da en la mochila de aldeano y devuelve lo que no cupo. Se hace a mano (como en el goal del
     * herrero) porque la mochila del aldeano no es un {@code Inventory} normal.
     */
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

    // --- la ronda y las puertas ---------------------------------------------------------------------

    /**
     * Dónde tiene que estar ahora: de <b>noche</b>, en una de las cuatro puertas del muro; de <b>día</b>, en el
     * siguiente punto de la ronda —y cada pocos puntos, en el <b>corral anexo</b> o en la <b>arboleda del pueblo</b>
     * (lo que hace que también los defienda: cualquier bicho que se acerque a los animales o a los árboles lo ve
     * antes de que haga daño)—. El relevo de puertas sale del reloj de juego y del número de guardia, así que rota
     * solo y sin que dos guardias se turnen el mismo puesto.
     */
    /**
     * El puesto de la <b>puerta</b> {@code 0..3} (norte, este, sur, oeste) del <b>relevo nocturno</b>: a
     * {@link #RADIO_PUERTA} del centro, dentro del muro. Lo usa {@link #puntoDeGuardia} para el relevo y para poder
     * <b>pasar a la siguiente</b> cuando la suya no se alcanza.
     */
    private BlockPos puestoDeLaPuerta(int nivel, int puerta) {
        int dx = switch (puerta) {
            case 0 -> 0;   // norte (el muro está en -Z)
            case 1 -> (int) RADIO_PUERTA;
            case 2 -> 0;
            default -> -(int) RADIO_PUERTA;
        };
        int dz = switch (puerta) {
            case 0 -> -(int) RADIO_PUERTA;
            case 1 -> 0;
            case 2 -> (int) RADIO_PUERTA;
            default -> 0;
        };
        return new BlockPos(center.getX() + dx, nivel, center.getZ() + dz);
    }

    /**
     * <b>¿Le toca su turno de descanso?</b> (lo pidió el jugador: *"deberían estar patrullando o turnándose para comer,
     * o descanso, porque también necesitan descansar, o entrenando en la sala de entrenamiento de sus barracas, pero
     * turnados para que no dejen desprotegida la aldea; si es uno nada más pues sí puede tomarse sus tiempos, ni modo"*).
     * <p>
     * El turno se reparte por el <b>reloj</b> y el <b>número de guardia</b>: de cada {@link #TICKS_DE_SERVICIO} de
     * servicio, cada guardia se toma <b>uno</b> ({@code (gameTime / TICKS_DE_SERVICIO) % guardias == su número}), así
     * que <b>nunca se ausentan dos a la vez</b>. Con un solo guardia el turno también le toca (el jugador lo acepta:
     * la aldea se queda un rato con menos vigilancia) y con varios se van relevando.
     */
    private boolean leTocaElTurnoDeDescanso() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        int guardias = 0;
        for (Villager otro : level.getEntitiesOfClass(Villager.class,
                villager.getBoundingBox().inflate(VillageGenerator.FENCE_RADIUS + 8.0D))) {
            if (esGuardia(otro)) {
                guardias++;
            }
        }
        if (guardias <= 0) {
            return false;
        }
        long turno = (level.getGameTime() / TICKS_DE_SERVICIO) % guardias;
        return turno == Math.floorMod(indice, guardias);
    }

    /** ¿La aldea está en asalto? (con asalto no hay turnos de descanso: primero se pelea). */
    private boolean aldeaEnAsalto() {
        return villager.level() instanceof ServerLevel level
                && VillageManager.isVillageUnderAttack(level, objectiveIndex);
    }

    /**
     * <b>¿Le toca ENTRENAR (y no solo descansar) en este turno?</b> Se alterna: en un turno entrena en la barraca y en
     * el siguiente descansa (el goal se corta y come en la taberna o duerme), que es lo que pidió el jugador: comer,
     * descansar <b>o</b> entrenar, todo con turnos para no dejar la aldea sola.
     */
    private boolean leTocaEntrenar() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        return (level.getGameTime() / TICKS_DE_SERVICIO) % 2L == 0L;
    }

    /**
     * <b>La sesión de entrenamiento</b>: va a la diana de la barraca, se pone delante y le pega (cada 2 s, con su
     * sonido y sus partículas). Devuelve {@code false} si no hay a dónde ir (barraca sin diana en el plano), para que el
     * guardia siga con la ronda en vez de quedarse parado.
     */
    private boolean entrenar(ServerLevel level) {
        BlockPos diana = VillageGenerator.puestoDeEntrenamiento(center, VillageGenerator.cotaDeLaPlaza(level, center));
        if (!diana.closerThan(center, VillageGenerator.FENCE_RADIUS)) {
            return false;
        }
        double distancia = Math.sqrt(villager.distanceToSqr(diana.getX() + 0.5D, diana.getY() + 0.5D,
                diana.getZ() + 0.5D));
        if (distancia > REACH) {
            // A LA DIANA NO SE CAMINA: ES UN BLOQUE (I114). Se camina a la casilla de pie que la tiene delante —el
            // puesto del PATIO, ver `VillageGenerator.puestoDeEntrenamiento`—, que es la única que el planificador
            // acepta; si no existe ninguna, no se entrena y el guardia vuelve a la ronda. La búsqueda se hace UNA vez
            // (la diana no se mueve) y se cachea: hacerla por tick costaba 100 celdas.
            BlockPos puesto = puestoDeEntrenamientoCacheado(level, diana);
            if (puesto == null) {
                entrenoTicks = 0;
                stuckEntreno = 0;
                return false;
            }
            // Y EL ATASCO SE MIDE CONTRA EL PASO, NO CONTRA LA DIANA (I112/I119): la ruta al puesto del patio da un
            // RODEO alrededor de la barraca (medido: 23 y 39 nodos, `alcanza=SI`), así que la distancia a la diana
            // empieza ALEJÁNDOSE y el guardia se rendía EN MITAD DEL RODEO — siete rendiciones `Yendo a entrenar` en
            // una corrida, con el camino bueno delante—.
            double hastaElPuesto = Math.sqrt(villager.distanceToSqr(puesto.getX() + 0.5D, puesto.getY() + 0.5D,
                    puesto.getZ() + 0.5D));
            // SI NO LLEGA, SE RINDE Y SIGUE CON LA RONDA (I3/I33). Lo reportó el jugador: *"se quedó ciclado un guardia
            // al ir a entrenar"*. Ahora, si no se acerca en STUCK_LIMIT, se aparca la diana (no se reintenta en bucle)
            // y el guardia vuelve a su ronda: entrenará cuando la diana sea alcanzable.
            if (entrenoTicks == 0) {
                mejorEntreno = Double.MAX_VALUE; // medida nueva en cada sesión
            }
            if (hastaElPuesto < mejorEntreno - 0.5D) {
                mejorEntreno = hastaElPuesto;
                stuckEntreno = 0;
            } else if (++stuckEntreno == TICKS_PARA_COMPROBAR_SI_VA) {
                // NO SE EMPUJA LA PARED (I119, medido con el aviso de "no llegué"): si la navegación **no tiene ruta
                // viva que alcance** el puesto, o si el **cerebro va a OTRA parte** (otra faena le ha pisado el
                // `WALK_TARGET`: medido, un guardia "Yendo a entrenar" con el cerebro puesto en el almacén), se vuelve
                // a la **ronda** y ya entrenará en otro turno. Antes seguía 12 s midiendo contra la diana y encima se
                // aparcaba la diana 5 min.
                var camino = villager.getNavigation().getPath();
                boolean rutaViva = camino != null && camino.canReach();
                boolean elCerebroVaAlPuesto = villager.getBrain()
                        .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                        .map(t -> t.getTarget().currentBlockPosition().equals(puesto)).orElse(false);
                if (!rutaViva || !elCerebroVaAlPuesto) {
                    entrenoTicks = 0;
                    stuckEntreno = 0;
                    return false;
                }
            } else if (stuckEntreno >= STUCK_LIMIT) {
                entrenoTicks = 0;
                stuckEntreno = 0;
                VillageManager.marcarPuntoFallido(villager, diana);
                DevilRpg.LOGGER.info("[Village] Guardia {}: no llego a la diana {} (aldea {}): me vuelvo a la ronda",
                        villager.getUUID(), diana.toShortString(), objectiveIndex);
                return false;
            }
            VillageManager.caminarHacia(villager, puesto, VELOCIDAD);
            VillageManager.ponerActividad(villager, "Yendo a entrenar");
            return true;
        }
        VillageManager.parar(villager);
        bajarEscudo();
        villager.getLookControl().setLookAt(diana.getX() + 0.5D, diana.getY() + 0.5D, diana.getZ() + 0.5D);
        entrenoTicks++;
        if (entrenoTicks % 40 == 0) {
            villager.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, diana, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.NEUTRAL, 0.5F, 1.2F);
        }
        VillageManager.ponerActividad(villager, "Entrenando en la barraca");
        // Y LO QUE HA ENTRENADO CUENTA (despacio): 5 min de diana = una matanza (ver `sumarEntrenamiento`).
        VillageManager.sumarEntrenamiento(villager, 1);
        return true;
    }

    /**
     * Ticks de <b>servicio</b> de cada guardia antes de que le toque su turno de descanso (90 s): con varios guardias
     * se van relevando (ver {@link #leTocaElTurnoDeDescanso}).
     */
    private static final int TICKS_DE_SERVICIO = 20 * 90;
    /** Ticks que lleva el guardia en la sesión de entrenamiento de ahora (para el golpe cada 2 s). */
    /** Ticks entre dos viajes al almacén a equiparse (2 min): sin esto el guardia encadenaba viajes en bucle. */
    private static final int TICKS_ENTRE_VIAJES = 20 * 120;
    /** Tick del último viaje al almacén (para {@link #TICKS_ENTRE_VIAJES}). */
    private long ultimoViajeAlAlmacen = Long.MIN_VALUE;
    /** Lo más cerca que ha estado de la diana en la sesión (y su paciencia), aparte de la del puesto. */
    private double mejorEntreno = Double.MAX_VALUE;
    private int stuckEntreno;
    private int entrenoTicks;

    private BlockPos puntoDeGuardia(ServerLevel level) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        if (level.isNight()) {
            int base = (int) ((level.getGameTime() / RELEVO_TICKS + indice) % 4L);
            // SI ESA PUERTA NO SE ALCANZA, SE PASA A LA SIGUIENTE. El puesto del relevo nocturno sale del RELOJ (y
            // de su número de guardia), no de `paso`: saltarse el puesto no lo cambiaba y el guardia se quedaba
            // repitiendo el mismo sitio para siempre (pasos 12..16 con el mismo BlockPos en el log del jugador).
            // Se recorren las cuatro puertas y se devuelve la primera que no esté apuntada como fallida.
            for (int k = 0; k < 4; k++) {
                int puerta = (base + k) % 4;
                BlockPos punto = puestoDeLaPuerta(nivel, puerta);
                if (k == 3 || !VillageManager.esPuntoFallido(villager, punto)) {
                    return punto; // la última se devuelve aunque esté fallida: mejor eso que ningún puesto
                }
            }
            return puestoDeLaPuerta(nivel, base);
        }
        // Ronda: un punto distinto por paso y por guardia (determinista, sin tiradas). Cada RONDA_CADA_ANEXO puntos
        // de la ronda, el guardia baja al CORRAL ANEXO (fuera de la valla): es lo que pidió el jugador ("la granja
        // anexa, dentro del patrullaje de la guardia").
        if (vaAlCorral(level)) {
            BlockPos corral = puestoDelCorral(level, nivel);
            // SI EL PUESTO DEL CORRAL ESTÁ APARCADO (porque no llegó), SE SIGUE CON LA RONDA. Era el ÚNICO punto que
            // no miraba `esPuntoFallido`, así que tras rendirse en él (`me salto el puesto`, I33) el guardia volvía a
            // intentarlo en cada vuelta: lo reportó el jugador con captura —*"el espadachín se quedó como ciclado
            // patrullando el corral"*—. Ahora, si está aparcado, se prueba el punto siguiente de la ronda; cuando el
            // aparcamiento caduque (5 min) volverá a probar el corral.
            if (!VillageManager.esPuntoFallido(villager, corral)) {
                return corral;
            }
            paso++;
        }
        // Y cada RONDA_CADA_ARBOLEDA puntos, a la ARBOLEDA DEL PUEBLO (dentro de la valla, en la diagonal noreste):
        // es la madera de la aldea, y un guardia allí ve (y para) a cualquier bicho que entre a por los árboles.
        if (vaALaArboleda()) {
            BlockPos arboleda = VillageGenerator.puntoDeApoyoDeLaArboleda(center, nivel);
            // Un puesto distinto por guardia, y todos a UN bloque del centro de la arboleda: los cuatro plantones
            // están a dos, así que así ninguno se queda plantado justo donde va a crecer un tronco.
            int[] puesto = PUNTOS_DE_LA_ARBOLEDA[indice % PUNTOS_DE_LA_ARBOLEDA.length];
            return puestoLibre(level, arboleda.offset(puesto[0], 0, puesto[1]), Integer.signum(puesto[0]));
        }
        double angulo = Math.toRadians((indice * 137.5D + paso * 47.0D) % 360.0D);
        int x = center.getX() + (int) Math.round(Math.cos(angulo) * RADIO_RONDA);
        int z = center.getZ() + (int) Math.round(Math.sin(angulo) * RADIO_RONDA);
        BlockPos punto = new BlockPos(x, nivel, z);
        // EL CORRAL no se pisa en la ronda: desde que la muralla creció al radio 62 la granja está DENTRO y su valla
        // ocupa de 43 a 57 al este, así que un punto de la ronda de ese lado caería dentro del corral y el guardia se
        // pasaría el día empujando la valla. Si cae dentro, se corre hacia el muro: queda en el pasillo entre el
        // corral y la valla, que es por donde de verdad se pasa (y desde ahí ve a los animales). OJO con el número:
        // `FENCE_RADIUS - 3` es JUSTO la valla ESTE del corral (base + ANEXO_RADIO = centro + 59), o sea un bloque
        // sólido, y el guardia se quedaba empujándolo; el pasillo son las dos casillas de dentro del muro (60 y 61).
        if (VillageGenerator.estaEnElAnexo(center, punto)) {
            punto = new BlockPos(center.getX() + VillageGenerator.FENCE_RADIUS - 2, nivel, z);
        }
        return puestoLibre(level, punto, -1);
    }

    /**
     * Puesto del guardia <b>alrededor del corral</b>: en el suelo, <b>fuera</b> de la valla y a los lados del portón.
     * <p>
     * Antes el puesto era <b>dentro</b> del cercado (el punto de apoyo del ganadero, a solo 3 bloques del portón) y
     * el guardia <b>no llegaba nunca</b>: medido con el arnés en la partida del jugador (aldea 2, de día, guardia del
     * puesto 0), se quedaba <b>10 s empujando la valla oeste</b> a 4,0 del puesto (`mejor` no bajaba de 4,08), el goal
     * se rendía (`stuck=200`), volvía a empezar con el mismo puesto y así en bucle, con la etiqueta "Patrullando el
     * corral" clavada — lo que el jugador describió como *"dando vueltas sobre sí misma de manera errática"*. El
     * portón solo se abre cuando el aldeano va a cruzarlo (y no con un animal en el hueco), así que el guardia se
     * quedaba fuera; y cuando entraba de casualidad, se plantaba <b>en el hueco del portón</b>: la red de seguridad
     * se lo cerraba encima. Un guardia no tiene por qué cruzar la única puerta del rebaño: desde fuera de la valla ve
     * (y defiende) el corral igual.
     */
    private BlockPos puestoDelCorral(ServerLevel level, int nivel) {
        BlockPos base = VillageGenerator.baseDeAnexo(center);
        int dz = PUNTOS_DEL_CORRAL[Math.floorMod(indice, PUNTOS_DEL_CORRAL.length)];
        BlockPos puesto = new BlockPos(base.getX() - VillageGenerator.ANEXO_RADIO - 2, nivel, base.getZ() + dz);
        return puestoLibre(level, puesto, -1); // si está ocupado se corre hacia el pueblo, nunca hacia el cercado
    }

    /**
     * Puntos de la ronda que <b>algún guardia de esa aldea</b> no consiguió alcanzar hace poco, y hasta cuándo no se
     * vuelven a elegir. Es <b>por aldea</b> (no por guardia) a propósito: medido (24-sep-2026), <b>seis guardias
     * distintos</b> se rindieron en el <b>mismo</b> punto (`423,63,671`, una casilla dentro de un recinto amurallado a
     * la que el planificador del juego unas veces le encuentra la puerta y otras no). El aparcado de I33 es por aldeano,
     * así que cada guardia pagaba el fallo por su cuenta: con esto, el primero que se rinde <b>avisa a los demás</b>.
     * No se guarda con la partida (la ronda se recalcula): tras cargar, cada punto se paga una vez.
     */
    private static final Map<Long, Long> PUNTOS_MALOS_DE_LA_RONDA = new java.util.concurrent.ConcurrentHashMap<>();
    /** Cuánto se deja un punto malo de la ronda (5 min, como el aparcado de I33). */
    private static final long PUNTO_MALO_TICKS = 5L * 60L * 20L;

    /** La clave del punto malo: la aldea y la casilla (la Y entra porque la ronda la lleva). */
    private static long claveDelPuntoMalo(int objectiveIndex, BlockPos p) {
        return ((long) objectiveIndex << 56) ^ p.asLong();
    }

    /** Apunta que ese punto de la ronda no se alcanza (lo llama el guardia que se rinde; ver {@link #esPuntoMalo}). */
    private void apuntarPuntoMaloDeLaRonda(BlockPos p) {
        PUNTOS_MALOS_DE_LA_RONDA.put(claveDelPuntoMalo(objectiveIndex, p),
                villager.level().getGameTime() + PUNTO_MALO_TICKS);
    }

    /** ¿Ese punto de la ronda lo dio por inalcanzable algún guardia de esta aldea hace poco? */
    private boolean esPuntoMalo(ServerLevel level, BlockPos p) {
        Long hasta = PUNTOS_MALOS_DE_LA_RONDA.get(claveDelPuntoMalo(objectiveIndex, p));
        return hasta != null && level.getGameTime() < hasta;
    }

    /**
     * Corre el puesto a una casilla donde el guardia <b>quepa de pie</b> y <b>se pueda llegar</b>, si la ideal no vale
     * (una valla, un poste, lo que haya puesto el jugador). Navegar hacia un bloque sólido es el fallo que el pueblo ya
     * tiene documentado (ver {@code VillageStorage.puntoDeApoyo}): el aldeano se queda empujándolo. Se mira primero a
     * los lados y luego hacia {@code haciaDonde} (el pueblo), <b>nunca</b> hacia el cercado.
     * <p>
     * <b>Y NO BASTA CON QUE QUEPA DE PIE</b> (medido, 24-sep-2026, con el log nuevo de "no llegué"): la ronda es un
     * círculo de {@link #RADIO_RONDA} alrededor del centro y <b>atraviesa los edificios</b>. Dos guardias se rendían en
     * `423,63,671` —una casilla con aire a los pies, aire encima y suelo firme, o sea "se puede estar"— pero <b>dentro
     * de un recinto amurallado</b>: el planificador les daba una ruta que <b>no alcanza</b> (`29 nodos hasta 423,63,673
     * alcanza=NO`, o sea que se quedaban en la pared de fuera). Y otro en `509,63,650`, que resultó ser
     * <b>`cave_air`</b> (un hueco de cueva a la altura del pueblo). Por eso ahora, además de caber, se exige
     * <b>llegar</b> (`createPath(...).canReach()`), que es la prueba del caminante del juego; y el abanico de casillas
     * que se prueban es más ancho (hasta 6 bloques) para que el puesto acabe en la calle.
     */
    private BlockPos puestoLibre(ServerLevel level, BlockPos puesto, int haciaDonde) {
        BlockPos primeroDePie = null;
        Set<Long> calle = null; // se calcula una vez por puesto (recorrido en anchura desde la plaza)
        for (int salto = 0; salto <= 6; salto++) {
            if (salto == 0) {
                if (sePuedeEstar(level, puesto) && !esPuntoMalo(level, puesto)
                        && !dentroDeUnaConstruccion(level, puesto)) {
                    primeroDePie = puesto;
                    if (calle == null) {
                        calle = calleDeLaPlaza(level);
                    }
                    if (calle.contains(puesto.asLong()) && seLlega(level, puesto)) {
                        return puesto;
                    }
                }
                continue;
            }
            for (int dz : new int[]{salto, -salto}) {
                BlockPos vecino = puesto.offset(0, 0, dz);
                if (!sePuedeEstar(level, vecino) || esPuntoMalo(level, vecino)
                        || dentroDeUnaConstruccion(level, vecino)) {
                    continue;
                }
                if (primeroDePie == null) {
                    primeroDePie = vecino;
                }
                if (calle == null) {
                    calle = calleDeLaPlaza(level);
                }
                if (calle.contains(vecino.asLong()) && seLlega(level, vecino)) {
                    return vecino;
                }
            }
            if (haciaDonde != 0) {
                BlockPos vecino = puesto.offset(haciaDonde * salto, 0, 0);
                if (sePuedeEstar(level, vecino) && !esPuntoMalo(level, vecino)
                        && !dentroDeUnaConstruccion(level, vecino)) {
                    if (primeroDePie == null) {
                        primeroDePie = vecino;
                    }
                    if (calle == null) {
                        calle = calleDeLaPlaza(level);
                    }
                    if (calle.contains(vecino.asLong()) && seLlega(level, vecino)) {
                        return vecino;
                    }
                }
            }
        }
        // Sin nada mejor: el puesto de la calle (plaza), que es lo único de lo que consta que se llega desde cualquier
        // parte del pueblo, y si no la primera en la que quepa de pie (mejor eso que un punto inalcanzable: el guardia
        // se quedaría empujando la pared del recinto hasta rendirse, que es lo que medía I114/I115).
        if (primeroDePie == null || !seLlega(level, primeroDePie)) {
            BlockPos calle2 = VillageManager.casillaDeLaCalle(level, center);
            if (sePuedeEstar(level, calle2) && seLlega(level, calle2)) {
                return calle2;
            }
        }
        if (primeroDePie != null && seLlega(level, primeroDePie) && !dentroDeUnaConstruccion(level, primeroDePie)) {
            return primeroDePie;
        }
        // NI ESO: el guardia se QUEDA DONDE ESTÁ —que es lo único de lo que consta que se llega— en vez de ir a
        // empujar la pared de dentro. Medido (25-sep): por este respaldo se colaban todavía 6 rendiciones de golpe.
        return villager.blockPosition();
    }

    /**
     * <b>¿El caminante del juego <b>llega</b> a esa casilla?</b> (ruta que la alcanza de verdad, no que se queda corta)
     */
    private boolean seLlega(ServerLevel level, BlockPos destino) {
        var camino = villager.getNavigation().createPath(destino, 1);
        return camino != null && camino.canReach();
    }

    /** La diana para la que se calculó {@link #puestoDeEntrenamiento} (para no recalcularlo en cada tick). */
    @Nullable
    private BlockPos puestoDeEntrenamientoDe;
    /** La casilla de pie delante de esa diana: a dónde se camina de verdad para entrenar. */
    @Nullable
    private BlockPos puestoDeEntrenamiento;

    /**
     * <b>La casilla de pie delante de la diana</b> (a {@link #REACH} o menos), o {@code null} si no hay ninguna. Se
     * calcula <b>una vez por diana</b> y se cachea.
     * <p>
     * <b>Por qué</b> (medido, 25-sep-2026): la diana es un <b>bloque</b>, y caminar hacia un bloque da una ruta de
     * <b>1 nodo</b> (I114): el guardia no da un paso y se rinde con la etiqueta `Yendo a entrenar` (seis guardias
     * distintos, medido). Con el puesto movido al patio ({@code VillageGenerator.puestoDeEntrenamiento}) hay casilla de
     * pie al lado y esto basta; y si algún día la diana queda encerrada devuelve {@code null} y el guardia se vuelve a
     * la ronda, en vez de empujar la pared.
     */
    @Nullable
    private BlockPos puestoDeEntrenamientoCacheado(ServerLevel level, BlockPos diana) {
        if (diana.equals(puestoDeEntrenamientoDe)) {
            return puestoDeEntrenamiento;
        }
        var candidatas = new java.util.ArrayList<BlockPos>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos p = diana.offset(dx, dy, dz);
                    if (!sePuedeEstar(level, p)) {
                        continue;
                    }
                    double d = Math.sqrt(p.distSqr(diana));
                    if (d > 0.5D && d <= REACH - 0.5D) {
                        candidatas.add(p.immutable());
                    }
                }
            }
        }
        if (candidatas.isEmpty()) {
            puestoDeEntrenamientoDe = diana;
            puestoDeEntrenamiento = null;
            return null;
        }
        // CADA GUARDIA, SU CASILLA (I122): el puesto era UNA celda para los seis, así que se estorbaban entre ellos
        // —y con los animales y los que pasan por el patio— con la ruta buena delante. Se ordenan por cercanía a la
        // diana y cada uno toma la suya por su número (`indice`), como el pueblo reparte ya los puestos de la arboleda
        // y los del corral (I4: geometría fija, no aleatoria).
        candidatas.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(diana)));
        BlockPos mia = candidatas.get(Math.floorMod(indice, candidatas.size()));
        puestoDeEntrenamientoDe = diana;
        puestoDeEntrenamiento = mia;
        return mia;
    }

    /**
     * <b>¿Esa casilla está DENTRO de una construcción del pueblo?</b> Lo dice el <b>PLANO</b> de la aldea
     * ({@code blueprintState}): si la casilla tiene <b>3 o más vecinas</b> (las 4 de al lado y la de encima) que el
     * plano quiere ocupadas, entonces es un <b>hueco de dentro de un edificio</b> y no una casilla de la calle.
     * <p>
     * <b>Es el criterio que faltaba</b> (medido, 25-sep-2026): la ronda metía a los guardias en `423,63,671`, una
     * casilla que <b>pasa</b> todas las pruebas locales —aire a los pies, aire encima, suelo firme, cielo abierto— y a
     * la que el planificador del juego unas veces le encuentra la puerta y otras no (mi recorrido en anchura incluso
     * entraba por la puerta). El plano no engaña: esa casilla tiene <b>cinco</b> vecinas suyas (un cofre, una diana y
     * tres adoquines) — es el hueco de dentro de un edificio del pueblo. Una casilla de calle pegada a una pared tiene
     * <b>una</b> vecina del plano: por eso el umbral es 3.
     */
    private boolean dentroDeUnaConstruccion(ServerLevel level, BlockPos p) {
        if (VillageManager.blueprintState(level, objectiveIndex, p) != null) {
            return true; // el plano quiere un bloque AQUÍ: no es una casilla de paso
        }
        int vecinas = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (VillageManager.blueprintState(level, objectiveIndex, p.offset(d[0], 0, d[1])) != null) {
                vecinas++;
            }
        }
        if (VillageManager.blueprintState(level, objectiveIndex, p.above()) != null) {
            vecinas++;
        }
        return vecinas >= 3;
    }

    /**
     * <b>¿Esa casilla es de LA CALLE del pueblo?</b> Para el recorrido en anchura de {@link #calleDeLaPlaza}.
     * <p>
     * Es más estricta que {@link #sePuedeEstar} a propósito: exige <b>AIRE</b> a los pies y a la cabeza, no solo "sin
     * colisión". Una <b>puerta abierta</b> tiene la forma de colisión vacía, así que con la prueba floja el recorrido
     * <b>entraba por la puerta</b> y decía que el interior de un recinto amurallado era calle — que es exactamente el
     * fallo medido (`423,63,671`, adonde los guardias iban a rendirse). <b>La ronda va por la calle, no abre puertas.</b>
     */
    private boolean esAireDeLaCalle(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    /**
     * <b>LA CALLE DEL PUEBLO</b>: las casillas de pie que están <b>conectadas andando con la plaza</b>, por un recorrido
     * en anchura desde ella (la plaza es lo único de lo que consta que se llega desde cualquier parte).
     * <p>
     * <b>Por qué hace falta</b> (medido, 24-sep-2026): la ronda es un círculo y metía a los guardias <b>dentro de un
     * recinto amurallado</b> (`423,63,671`). Esa casilla <b>pasa</b> las pruebas locales —aire a los pies, aire encima,
     * suelo firme, y hasta cielo abierto—, y el planificador unas veces le encuentra la puerta y otras no
     * (`ruta=30 nodos ... alcanza=SI` desde un sitio y `ruta=38 nodos hasta 430,63,666 alcanza=NO` desde otro), así que
     * los guardias se rendían allí una y otra vez. El recorrido en anchura contesta la pregunta de verdad: <b>¿se llega
     * andando desde la plaza?</b> Y de paso rechaza las casillas de una cueva y los rincones sin salida. No se pasa por
     * las puertas (una puerta no es una casilla de pie): <b>la ronda va por la calle, no entra en las casas</b>.
     */
    private Set<Long> calleDeLaPlaza(ServerLevel level) {
        Set<Long> calle = new HashSet<>();
        BlockPos plaza = VillageManager.casillaDeLaCalle(level, center);
        if (plaza == null) {
            return calle;
        }
        Deque<BlockPos> cola = new ArrayDeque<>();
        cola.add(plaza);
        calle.add(plaza.asLong());
        int maxCeldas = 4000;
        int radio = 80;
        while (!cola.isEmpty() && calle.size() < maxCeldas) {
            BlockPos p = cola.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) {
                        continue; // sin diagonales: el aldeano anda en cruz
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos q = new BlockPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                        if (Math.abs(q.getX() - plaza.getX()) > radio || Math.abs(q.getZ() - plaza.getZ()) > radio) {
                            continue;
                        }
                        if (calle.contains(q.asLong()) || !esAireDeLaCalle(level, q)) {
                            continue;
                        }
                        calle.add(q.asLong());
                        cola.add(q);
                    }
                }
            }
        }
        return calle;
    }

    /** ¿Esa celda tiene sitio para pararse? (nada sólido en la celda ni encima, y suelo firme debajo) */
    private boolean sePuedeEstar(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    /** ¿Este paso de la ronda le toca al <b>corral anexo</b>? (lo miran el destino y la etiqueta: uno solo) */
    private boolean vaAlCorral(ServerLevel level) {
        return paso % RONDA_CADA_ANEXO == 0 && VillageGenerator.anexoConstruido(level, center);
    }

    /** ¿Este paso de la ronda le toca a la <b>arboleda del pueblo</b>? (el corral manda si coinciden) */
    private boolean vaALaArboleda() {
        return paso % RONDA_CADA_ARBOLEDA == 0;
    }

    /** Texto de lo que está haciendo (lo que se ve en su etiqueta). */
    private String actividadDeGuardia(ServerLevel level) {
        if (!level.isNight()) {
            if (vaAlCorral(level)) {
                return "Patrullando el corral";
            }
            return vaALaArboleda() ? "Patrullando la arboleda" : "Patrullando la aldea";
        }
        int puerta = (int) ((level.getGameTime() / RELEVO_TICKS + indice) % 4L);
        String nombre = switch (puerta) {
            case 0 -> "norte";
            case 1 -> "este";
            case 2 -> "sur";
            default -> "oeste";
        };
        return "De guardia en la puerta " + nombre;
    }

    private void irAlDestino() {
        if (destino != null) {
            VillageManager.caminarHacia(villager, destino, VELOCIDAD);
        }
    }
}
