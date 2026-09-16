package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * <b>Guardia de la aldea</b>: el aldeano adulto <b>sobrante</b> que se alista en la milicia (lo decide
 * {@code VillageManager.repartirGuardia}, que le pone {@code GUARD_TAG} y su tipo).
 * <p>
 * Lo que hace, en este orden:
 * <ol>
 *   <li><b>EQUIPARSE DEL ALMACÉN</b> (lo pidió el jugador): si no lleva arma, va al almacén del pueblo y coge lo
 *       suyo — el <b>espadachín</b> una espada y un escudo (el escudo en la mano secundaria), el <b>arquero</b> un
 *       arco y flechas. Lo que coge sale del almacén de verdad (es la producción de los herreros).</li>
 *   <li><b>RONDA</b> de día: va punto a punto por <b>alrededor</b> del pueblo, por dentro del muro, y espera un poco
 *       en cada uno mirando al campo.</li>
 *   <li><b>PUERTAS</b> de noche: se planta en una de las <b>cuatro puertas</b> del muro (norte, sur, este y oeste) y
 *       <b>rota</b> sola: el puesto sale del reloj de juego y de su número de guardia, así que los tres o cuatro
 *       guardias no se apelotonan en la misma puerta y el relevo se hace solo.</li>
 * </ol>
 * Se mueve <b>por el cerebro</b> ({@link VillageManager#caminarHacia}), como el resto de goals de la aldea.
 * <p>
 * <b>Todavía no pelea</b>: atacar (y que el escudo bloquee de verdad) y la marcha a la guarida son los pasos
 * siguientes de la milicia.
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

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /** Número de guardia (0..n): fija su puerta en el relevo y su secuencia de ronda. */
    private final int indice;
    /** true mientras la faena es ir al almacén a equiparse; false cuando ya toca la ronda/puerta. */
    private boolean equipando;
    @Nullable
    private BlockPos destino;
    private int espera;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    private int restTicks;
    /** Contador de puntos de ronda (para que no repita el mismo). */
    private int paso;

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
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
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
        mejorDistancia = Double.MAX_VALUE;
        irAlDestino();
    }

    @Override
    public boolean canContinueToUse() {
        return esGuardia(villager) && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (destino == null || !(villager.level() instanceof ServerLevel level)) {
            return;
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
        espera = 0;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
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
