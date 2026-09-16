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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    /** Cada cuánto cambia el relevo de puertas (2 min): así rotan en la misma noche. */
    private static final int RELEVO_TICKS = 2 * 60 * 20;
    /** Si se queda atascado (no se acerca) deja el punto y prueba con el siguiente. */
    private static final int STUCK_LIMIT = 200;
    private static final int REST_TICKS = 10;
    /** Si se aleja más de esto del centro de la aldea, deja de hacer la ronda. */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 16.0D;
    /** Velocidad de la ronda (y de la carrera al almacén si le falta el arma). */
    private static final float VELOCIDAD = 0.6F;
    /** Flechas que se lleva el arquero del almacén de una vez. */
    private static final int FLECHAS_POR_VIAJE = 16;

    // --- combate ------------------------------------------------------------------------------------

    /** Radio en el que el guardia ve a un monstruo y va a por él. */
    private static final double RADIO_COMBATE = 16.0D;
    /** Hasta dónde persigue: no se va del pueblo a matar zombis por el mundo (deriva del radio de la aldea). */
    private static final double RADIO_PERSEGUIR = VillageGenerator.FENCE_RADIUS + 8.0D;
    /** Distancia a la que el espadachín ya pega. */
    private static final double ALCANCE_ESPADA = 2.8D;
    /** Ticks entre golpes de espada y entre flechas. */
    private static final int CADENCIA_ESPADA = 20;
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
        if (dx * dx + dz * dz > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            return false;
        }
        // PRIMERO el equipo: un guardia sin arma no tiene nada que hacer en la ronda (y así se le ve ir al
        // almacén y volver armado, que es lo que pidió el jugador).
        equipando = !equipado(level);
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
        mejorDistancia = Double.MAX_VALUE;
        irAlDestino();
    }

    @Override
    public boolean canContinueToUse() {
        // Tampoco se corta por la hora de descanso: la guardia de noche es parte del servicio (ver `canUse`). Si el
        // aldeano acabara durmiendo (por ejemplo porque el cerebro lo tumbó en la cama), el goal se corta igual.
        // `destino == null` ocurre cuando el goal se acaba de quedar sin faena (por ejemplo, tras ir al almacén y no
        // encontrar equipo): se deja terminar para que el descanso haga efecto y no se quede dando vueltas.
        return destino != null && esGuardia(villager) && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !villager.isSleeping();
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
            BlockPos almacen = VillageStorage.puntoDeApoyo(level, center);
            equipando = true;
            if (almacen != null && !almacen.equals(destino)) {
                destino = almacen;
                mejorDistancia = Double.MAX_VALUE;
                stuckTicks = 0;
            }
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
            VillageManager.ponerActividad(villager, equipando ? "Yendo al almacén" : actividadDeGuardia(level));
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
            double dxAldea = monstruo.getX() - center.getX();
            double dzAldea = monstruo.getZ() - center.getZ();
            if (dxAldea * dxAldea + dzAldea * dzAldea > RADIO_PERSEGUIR * RADIO_PERSEGUIR) {
                continue; // fuera del término: no lo persigue
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
        villager.doHurtTarget(objetivo);
        level.playSound(null, villager.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.5F, 1.2F);
    }

    /** Arquero: flechas desde lejos (y si se queda sin flechas, el tick lo manda al almacén). */
    private void pelearConArco(ServerLevel level, Monster objetivo) {
        if (flechas() <= 0) {
            return; // sin flechas no puede disparar: el tick lo lleva al almacén a por más
        }
        // Aquí el que aguanta la posición es él (destino a su propio sitio): igual que el espadachín, necesita escribir
        // el rumbo cada tick para que el pánico del aldeano no le mande a correr.
        VillageManager.caminarHacia(villager, villager.blockPosition(), VELOCIDAD);
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
     * siguiente punto de la ronda. El relevo de puertas sale del reloj de juego y del número de guardia, así que
     * rota solo y sin que dos guardias se turnen el mismo puesto.
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
        // Ronda: un punto distinto por paso y por guardia (determinista, sin tiradas).
        double angulo = Math.toRadians((indice * 137.5D + paso * 47.0D) % 360.0D);
        int x = center.getX() + (int) Math.round(Math.cos(angulo) * RADIO_RONDA);
        int z = center.getZ() + (int) Math.round(Math.sin(angulo) * RADIO_RONDA);
        return new BlockPos(x, nivel, z);
    }

    /** Texto de lo que está haciendo (lo que se ve en su etiqueta). */
    private String actividadDeGuardia(ServerLevel level) {
        if (!level.isNight()) {
            return "Patrullando la aldea";
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
