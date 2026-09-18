package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;

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
 * <b>Ojo con verlo</b>: el modelo del aldeano de vanilla <b>no</b> tiene capa de armadura ni de objeto en mano
 * ({@code VillagerRenderer} solo pone cabeza, profesión y brazos cruzados), así que lo que lleva puesto <b>no se
 * ve</b> todavía: es el paso siguiente (modelo propio tipo jugador con cabeza de aldeano).
 */
public class VillagerGuardGoal extends Goal {

    /** Espadachín: espada y escudo. */
    public static final int ESPADACHIN = 0;
    /** Arquero: arco y flechas. */
    public static final int ARQUERO = 1;

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
    private int restTicks;
    /** Contador de puntos de ronda (para que no repita el mismo). */
    private int paso;
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
        return villager.getPersistentData().getBoolean(VillageManager.GUARD_TAG);
    }

    /** Tipo de guardia de un aldeano alistado ({@link #ESPADACHIN} o {@link #ARQUERO}). */
    public static int tipoDe(Villager villager) {
        return villager.getPersistentData().getInt(VillageManager.GUARD_TYPE_TAG);
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
        equipando = sinEquipo && hayEquipoEnElAlmacen(level);
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
        irAlDestino();
        DevilRpg.LOGGER.info("[Village] Guardia {}: nuevo puesto {} (paso {}, aldea {}){}", villager.getUUID(), destino,
                paso, objectiveIndex, equipando ? " yendo antes al almacen a equiparse" : "");
    }

    @Override
    public boolean canContinueToUse() {
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
        // 1) COMBATE, lo primero: si hay un monstruo cerca, el guardia va a por él (si no, nunca defienden). El
        //    escaneo va cada ESCANEO_TICKS porque buscar entidades no se puede hacer en cada tick.
        if (--escanear <= 0) {
            escanear = ESCANEO_TICKS;
            enemigo = buscarEnemigo(level);
            // Y de paso se mira si el almacén tiene ya algo suyo: mirar un cofre (54 huecos) NO se hace por tick.
            hayEquipoEnAlmacen = hayEquipoEnElAlmacen(level);
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
            // Solo se desvía al almacén si allí hay algo suyo (dato del último escaneo): si no, sigue la ronda.
            equipando = hayEquipoEnAlmacen;
            BlockPos almacen = VillageStorage.puntoDeApoyo(level, center);
            if (equipando && almacen != null && !almacen.equals(destino)) {
                destino = almacen;
                mejorDistancia = Double.MAX_VALUE;
                stuckTicks = 0;
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
        villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
        double distancia = Math.sqrt(villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D,
                destino.getZ() + 0.5D));
        if (distancia > REACH) {
            VillageManager.caminarHacia(villager, destino, VELOCIDAD);
            // Atascado = NO ACERCARSE (invariante I3): un contador de ticks dejaría al guardia a medio camino.
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
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
        if (stuckTicks > 0 || espera > 0) {
            DevilRpg.LOGGER.info("[Village] Guardia {}: deja el puesto {} (paso {}, atascado {} ticks, plantado {}"
                            + " ticks, aldea {})", villager.getUUID(), destino, paso, stuckTicks, espera,
                    objectiveIndex);
        }
        destino = null;
        enemigo = null;
        espera = 0;
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
        return villager.getMainHandItem().is(Items.IRON_SWORD)
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
        // ARMADURA primero (lo pidió el jugador: que se les vea con armadura): pieza a pieza, la que haya.
        for (EquipmentSlot pieza : ARMADURAS) {
            cogerArmadura(almacen, pieza);
        }
        if (tipoDe(villager) == ARQUERO) {
            cogerYEquipar(almacen, EquipmentSlot.MAINHAND, s -> s.is(Items.BOW), "el arco");
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
            return equipado(level);
        }
        cogerYEquipar(almacen, EquipmentSlot.MAINHAND, s -> s.is(Items.IRON_SWORD), "la espada");
        cogerYEquipar(almacen, EquipmentSlot.OFFHAND, s -> s.is(Items.SHIELD), "el escudo");
        return equipado(level);
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
    private BlockPos puntoDeGuardia(ServerLevel level) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        if (level.isNight()) {
            int puerta = (int) ((level.getGameTime() / RELEVO_TICKS + indice) % 4L);
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
        // Ronda: un punto distinto por paso y por guardia (determinista, sin tiradas). Cada RONDA_CADA_ANEXO puntos
        // de la ronda, el guardia baja al CORRAL ANEXO (fuera de la valla): es lo que pidió el jugador ("la granja
        // anexa, dentro del patrullaje de la guardia").
        if (vaAlCorral(level)) {
            return puestoDelCorral(level, nivel);
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
     * Corre el puesto a una casilla donde el guardia <b>quepa de pie</b>, si la ideal está ocupada (una valla, un
     * poste, lo que haya puesto el jugador). Navegar hacia un bloque sólido es el fallo que el pueblo ya tiene
     * documentado (ver {@code VillageStorage.puntoDeApoyo}): el aldeano se queda empujándolo. Se mira primero a los
     * lados y luego hacia {@code haciaDonde} (el pueblo), <b>nunca</b> hacia el cercado.
     */
    private BlockPos puestoLibre(ServerLevel level, BlockPos puesto, int haciaDonde) {
        if (sePuedeEstar(level, puesto)) {
            return puesto;
        }
        for (int salto = 1; salto <= 2; salto++) {
            for (int dz : new int[]{salto, -salto}) {
                BlockPos vecino = puesto.offset(0, 0, dz);
                if (sePuedeEstar(level, vecino)) {
                    return vecino;
                }
            }
            if (haciaDonde != 0) {
                BlockPos vecino = puesto.offset(haciaDonde * salto, 0, 0);
                if (sePuedeEstar(level, vecino)) {
                    return vecino;
                }
            }
        }
        return puesto; // sin hueco mejor: se devuelve el puesto pedido (no hay nada que inventar)
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
