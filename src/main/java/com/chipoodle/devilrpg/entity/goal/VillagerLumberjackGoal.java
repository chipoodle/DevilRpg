package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * <b>Leñador / reforestador</b> de la aldea (etapa B): <b>tala árboles de verdad</b> alrededor del pueblo y <b>los
 * replanta</b>, y lleva la madera al <b>almacén</b> (que es de donde salen los tablones, los palos, los arcos y las
 * flechas de los herreros).
 * <p>
 * Es el <b>recolector</b> (el aldeano sin oficio) el que hace también esto: así el pueblo no gasta un puesto más (los
 * puestos fijos ya son granjero, los dos herreros, clérigo y recolector, y de los sobrantes sale la milicia). Va a
 * <b>prioridad 6</b>, por debajo de su goal de recoger (5): primero recoge lo que hay por el suelo y, cuando no hay
 * nada que recoger, se va al monte.
 * <p>
 * <b>Solo tala árboles DE VERDAD y fuera de la valla</b>, que es lo importante: el muro de la aldea y las casas son de
 * troncos, así que dentro del recinto no se toca nada. Un tronco cuenta como árbol si su base está sobre tierra y
 * tiene <b>hojas cerca</b> y otro tronco encima (un poste suelto no).
 */
public class VillagerLumberjackGoal extends Goal {

    /** Distancia a la que ya alcanza el tronco para dar el hachazo. */
    private static final double REACH = 3.5D;
    /** Ticks de hachazo antes de que el árbol caiga. */
    private static final int WORK_TICKS = 25;
    private static final int REST_TICKS = 10;
    /** Sin árboles a la vista: a esperar (buscar árboles recorre muchas columnas, no se hace por tick). */
    private static final int IDLE_REST_TICKS = 120;
    /** Si no logra acercarse en este tiempo, abandona ese árbol. */
    private static final int STUCK_LIMIT = 160;
    /** Troncos que lleva encima antes de ir al almacén a descargar. */
    private static final int LLEVAR_TRONCOS = 12;
    /** Semillas de árbol que se lleva del almacén para replantar. */
    private static final int SEMILLAS_POR_VIAJE = 16;
    /** Radio de búsqueda de árboles ALREDEDOR DEL LEÑADOR, y radio mínimo (fuera de la valla, que es de troncos). */
    private static final int RADIO_BUSQUEDA = 32;
    private static final double RADIO_MINIMO = VillageGenerator.FENCE_RADIUS + 3.0D;
    /** Hasta dónde se le deja alejar del pueblo (si no, se pierde por el mundo talando). */
    private static final double RADIO_MAXIMO = VillageGenerator.FENCE_RADIUS + 40.0D;
    /** Tronco más alto que tala de una vez (una selva puede tener árboles altísimos). */
    private static final int ALTURA_MAX = 16;
    /** Velocidad al ir al árbol (y al almacén). */
    private static final float VELOCIDAD = 0.6F;

    private enum Fase { TALAR, ENTREGAR }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Fase fase = Fase.TALAR;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;

    public VillagerLumberjackGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        // El leñador es el RECOLECTOR (el aldeano sin oficio): es el que tiene menos faena fija. El gestor le da el
        // goal solo al holgazán que ya hace de recolector, así que basta con mirar el oficio.
        if (villager.getVillagerData().getProfession() != VillagerProfession.NITWIT) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > RADIO_MAXIMO * RADIO_MAXIMO) {
            return false; // se ha ido demasiado lejos del pueblo
        }
        // Con la mochila llena (o con madera y sin árboles a la vista) se va a descargar al almacén.
        BlockPos tronco = buscarArbol(level);
        if (troncosEnMano() >= LLEVAR_TRONCOS || (tronco == null && troncosEnMano() > 0)) {
            fase = Fase.ENTREGAR;
            target = VillageStorage.puntoDeApoyo(level, center);
            return target != null;
        }
        if (tronco == null) {
            // Sin árboles: si no le quedan semillas y el almacén tiene, va a por ellas (para replantar).
            if (semillasEnMano() == 0 && haySemillasEnElAlmacen(level)) {
                fase = Fase.ENTREGAR;
                target = VillageStorage.puntoDeApoyo(level, center);
                return target != null;
            }
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        fase = Fase.TALAR;
        target = tronco;
        return true;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        irAlObjetivo();
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        double alcance = fase == Fase.ENTREGAR ? VillageStorage.ALCANCE_ALMACEN : REACH;
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        if (distancia > alcance) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            // Atascado = NO ACERCARSE (invariante I3).
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            VillageManager.ponerActividad(villager, fase == Fase.ENTREGAR ? "Llevando la madera" : "Yendo al arbol");
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, fase == Fase.ENTREGAR ? "Guardando la madera" : "Talando");
            return;
        }
        workTicks = 0;
        if (fase == Fase.TALAR) {
            talar(level);
        } else {
            entregar(level);
        }
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        restTicks = REST_TICKS;
        villager.getNavigation().stop();
    }

    // --- las faenas ---------------------------------------------------------------------------------

    /**
     * Tala el árbol: recorre la columna de troncos hacia arriba (hasta {@link #ALTURA_MAX}) y se lleva la madera. Si la
     * base está sobre tierra, <b>replanta</b> ahí mismo una semilla de árbol (si tiene).
     */
    private void talar(ServerLevel level) {
        if (target == null) {
            return;
        }
        boolean eraBase = esTierra(level.getBlockState(target.below()));
        int talados = 0;
        BlockPos p = target;
        while (talados < ALTURA_MAX && level.getBlockState(p).is(BlockTags.LOGS)) {
            BlockState tronco = level.getBlockState(p);
            List<ItemStack> drops = Block.getDrops(tronco, level, p, null);
            level.destroyBlock(p, false);
            level.playSound(null, p, tronco.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
            for (ItemStack drop : drops) {
                ItemStack resto = guardarEnInventario(drop);
                if (!resto.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, resto));
                }
            }
            talados++;
            p = p.above();
        }
        // Las HOJAS que quedan colgando se quitan solas (mecánica de vanilla) y, si no, el recolector recoge lo que
        // caiga. Aquí solo se replanta: el pueblo no se queda sin árboles.
        if (eraBase && talados > 0) {
            replantar(level, target);
        }
        if (talados > 0) {
            VillageManager.ponerSuceso(villager, "Talo un arbol (" + talados + ")");
            DevilRpg.LOGGER.info("[Village] El lenador: talo {} tronco(s)", talados);
        }
    }

    /** Planta una semilla de árbol en la base del que acaba de talar (si tiene alguna). */
    private void replantar(ServerLevel level, BlockPos base) {
        if (!level.getBlockState(base).isAir() || !esTierra(level.getBlockState(base.below()))) {
            return;
        }
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemillaDeArbol(s) && s.getItem() instanceof BlockItem blockItem) {
                level.setBlock(base, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                VillageManager.ponerSuceso(villager, "Replanto el arbol");
                return;
            }
        }
    }

    /** Deja la madera en el almacén y coge semillas para seguir replantando. */
    private void entregar(ServerLevel level) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty() || !s.is(ItemTags.LOGS)) {
                continue;
            }
            ItemStack resto = VillageStorage.guardar(level, center, s.copy());
            villager.getInventory().setItem(i, resto);
        }
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen != null && semillasEnMano() == 0) {
            for (ItemStack semilla : List.of(new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.SPRUCE_SAPLING),
                    new ItemStack(Items.BIRCH_SAPLING), new ItemStack(Items.JUNGLE_SAPLING),
                    new ItemStack(Items.ACACIA_SAPLING), new ItemStack(Items.DARK_OAK_SAPLING))) {
                if (VillagePantry.sacar(almacen, s -> ItemStack.isSameItem(s, semilla), SEMILLAS_POR_VIAJE) > 0) {
                    guardarEnInventario(semilla.copyWithCount(SEMILLAS_POR_VIAJE));
                    break;
                }
            }
        }
    }

    // --- buscar árboles -----------------------------------------------------------------------------

    /**
     * El <b>árbol de verdad</b> más cercano al leñador, o {@code null}. Se recorre una rejilla de 2 en 2 alrededor
     * suyo (no por tick: el goal descansa cuando no encuentra nada) y solo se miran las columnas que caen
     * <b>fuera de la valla</b>, que es donde están los árboles del monte.
     */
    @Nullable
    private BlockPos buscarArbol(ServerLevel level) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (int dx = -RADIO_BUSQUEDA; dx <= RADIO_BUSQUEDA; dx += 2) {
            for (int dz = -RADIO_BUSQUEDA; dz <= RADIO_BUSQUEDA; dz += 2) {
                int x = villager.blockPosition().getX() + dx;
                int z = villager.blockPosition().getZ() + dz;
                double dCentroX = x - center.getX();
                double dCentroZ = z - center.getZ();
                double dCentro = Math.sqrt(dCentroX * dCentroX + dCentroZ * dCentroZ);
                if (dCentro < RADIO_MINIMO) {
                    continue; // dentro del pueblo no se tala (el muro y las casas son de troncos)
                }
                if (dCentro > RADIO_MAXIMO) {
                    continue;
                }
                BlockPos base = baseDeArbol(level, x, z);
                if (base == null) {
                    continue;
                }
                double dist = villager.distanceToSqr(base.getX() + 0.5D, base.getY() + 0.5D, base.getZ() + 0.5D);
                if (dist < mejorDist) {
                    mejorDist = dist;
                    mejor = base;
                }
            }
        }
        return mejor;
    }

    /**
     * Si en esa columna hay un árbol, devuelve la posición de su tronco <b>más bajo</b>. Un tronco cuenta como árbol
     * si su base está sobre tierra, tiene <b>otro tronco encima</b> (un poste de una pieza no es un árbol) y tiene
     * <b>hojas cerca</b>.
     */
    @Nullable
    private BlockPos baseDeArbol(ServerLevel level, int x, int z) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int y = cota + ALTURA_MAX + 4; y > cota - 4; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).is(BlockTags.LOGS)) {
                continue;
            }
            // Es tronco: se baja hasta el más bajo de esa columna.
            BlockPos base = p;
            while (level.getBlockState(base.below()).is(BlockTags.LOGS)) {
                base = base.below();
            }
            if (!esTierra(level.getBlockState(base.below()))) {
                return null; // colgando de otro tronco: forma parte de un árbol ya elegido por su base
            }
            if (!level.getBlockState(base.above()).is(BlockTags.LOGS)) {
                return null; // un tronco de una sola pieza (un poste): no es un árbol
            }
            return tieneHojasCerca(level, base) ? base : null;
        }
        return null;
    }

    /** ¿Hay hojas alrededor de esa columna? Es lo que distingue un árbol de un poste de madera. */
    private boolean tieneHojasCerca(ServerLevel level, BlockPos base) {
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos arriba = base.above(dy);
            for (BlockPos q : BlockPos.betweenClosed(arriba.offset(-2, 0, -2), arriba.offset(2, 0, 2))) {
                if (level.getBlockState(q).is(BlockTags.LEAVES)) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- utilidades ---------------------------------------------------------------------------------

    private static boolean esTierra(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }

    /** ¿Esa semilla es de árbol? (las de árbol se plantan al talar; las demás no). */
    private static boolean esSemillaDeArbol(ItemStack s) {
        return s.is(Items.OAK_SAPLING) || s.is(Items.SPRUCE_SAPLING) || s.is(Items.BIRCH_SAPLING)
                || s.is(Items.JUNGLE_SAPLING) || s.is(Items.ACACIA_SAPLING) || s.is(Items.DARK_OAK_SAPLING)
                || s.is(Items.CHERRY_SAPLING) || s.is(Items.MANGROVE_PROPAGULE);
    }

    private int troncosEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(ItemTags.LOGS)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int semillasEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemillaDeArbol(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private boolean haySemillasEnElAlmacen(ServerLevel level) {
        return VillagePantry.contar(VillageStorage.almacen(level, center),
                VillagerLumberjackGoal::esSemillaDeArbol) > 0;
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

    private void irAlObjetivo() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
        }
    }
}
