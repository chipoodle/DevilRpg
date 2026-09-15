package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Goal del <b>granjero</b>: la cadena de suministro de verdad de la aldea.
 * <ol>
 *   <li><b>Cosecha</b> los cultivos maduros de las parcelas de la aldea (y los <b>replanta</b>).</li>
 *   <li><b>Planta</b> en la tierra de cultivo vacía (semillas de su inventario o de la despensa).</li>
 *   <li><b>Fertiliza</b> con <b>harina de huesos</b> (la que da el compostero) para sacar pan antes.</li>
 *   <li><b>Lleva el trigo a la despensa</b> y allí lo convierte en <b>pan</b> (3 de trigo por hogaza).</li>
 * </ol>
 * Con esto la comida de la aldea ya no es un contador abstracto: sale del trigo que este aldeano cultiva de verdad
 * (ver {@link VillagePantry}).
 */
public class VillagerFarmGoal extends Goal {

    /** Distancia a la que ya se considera que llega al cultivo. */
    private static final double REACH = 3.0D;
    /** Ticks de faena por acción (medio segundo): se le ve dar el golpe sin eternizarse. */
    private static final int WORK_TICKS = 10;
    /** Descanso entre acción y acción. */
    private static final int REST_TICKS = 15;
    /** Descanso cuando no hay nada que hacer (4 s). Buscar cultivos recorre las parcelas, no se hace cada tick. */
    private static final int IDLE_REST_TICKS = 80;
    /** Si no logra acercarse en este tiempo, abandona el objetivo. */
    private static final int STUCK_LIMIT = 120;
    /** Si se aleja más de esto del centro, deja de trabajar (derivado del radio de la aldea). */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 12.0D;
    /** Trigo que lleva encima antes de ir a la despensa: cada 4 cosechas baja a guardarlo y hornear. */
    private static final int LLEVAR_TRIGO = 4;
    /** Semillas que se guarda como mucho: si lleva más, las suelta (si no, se le llena el inventario y no le cabe el trigo). */
    private static final int SEMILLAS_MAX = 8;
    /** Hogazas como mucho por visita (para que se le vea trabajar). */
    private static final int HORNEAR_MAX = 2;
    /** Harina de huesos que se lleva encima como mucho. */
    private static final int HARINA_MAX = 4;
    /** Cuánto puede traerse del almacén a la despensa en una visita (comida, semillas y abono del recolector). */
    private static final int TRAER_DEL_ALMACEN = 64;

    private enum Tarea { COSECHAR, PLANTAR, FERTILIZAR, DESPENSA }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Tarea tarea = Tarea.COSECHAR;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;

    public VillagerFarmGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        if (villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.FARMER) {
            return false;
        }
        // En plena refriega nadie se pone a sembrar.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex)) {
            return false;
        }
        // Ni en su hora de descanso: el granjero también se va a la cama.
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
        if (villager.blockPosition().distSqr(center) > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            return false;
        }
        Container despensa = VillagePantry.despensa(level, center);
        // 1) Con trigo o vegetales suficientes encima, A LA DESPENSA (aunque queden cultivos maduros): si el depósito
        // se deja para el final, en una parcela grande SIEMPRE hay algo maduro y el granjero se pasa la vida
        // cosechando sin llevar NADA al cofre. Se va cada 4 unidades entre trigo y vegetales.
        if (trigoEnMano() + vegetalesEnMano() >= LLEVAR_TRIGO && despensa != null) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        // 2) Cultivo maduro: a cosecharlo.
        target = buscarCultivo(level, true);
        if (target != null) {
            tarea = Tarea.COSECHAR;
            return true;
        }
        // 3) Tierra de cultivo vacía y semillas: a plantar.
        if (tieneSemillas() || VillagePantry.contar(despensa, VillagerFarmGoal::esSemilla) > 0) {
            target = buscarTierraVacia(level);
            if (target != null) {
                tarea = Tarea.PLANTAR;
                return true;
            }
        }
        // 4) Cultivo creciendo y harina de huesos: a fertilizar (así hay pan antes).
        if (harinaEnMano() > 0 || VillagePantry.contar(despensa, s -> s.is(Items.BONE_MEAL)) > 0) {
            target = buscarCultivo(level, false);
            if (target != null) {
                tarea = Tarea.FERTILIZAR;
                return true;
            }
        }
        // 5) Sin semillas ni abono: a la despensa a por recambios (si existe; si no, sigue con la tierra).
        if (despensa != null && (!tieneSemillas() || harinaEnMano() == 0)) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        restTicks = IDLE_REST_TICKS;
        return false;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
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
        // Para la despensa vale un alcance mayor (el cofre está dentro del kiosco y no se navega hacia él).
        double alcance = tarea == Tarea.DESPENSA ? VillagePantry.ALCANCE_DESPENSA : REACH;
        if (villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) > alcance * alcance) {
            // El rumbo se le da POR EL CEREBRO en cada tick (ver VillageManager.caminarHacia): navegando a mano, el
            // cerebro del aldeano lo manda a su puesto, a la plaza o a pasear y se va a otro lado a mitad de camino
            // ("primero da vueltas y se va a otro lado antes de recogerlos").
            VillageManager.caminarHacia(villager, target, 0.6F);
            stuckTicks++;
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            return;
        }
        workTicks = 0;
        switch (tarea) {
            case COSECHAR -> {
                VillageManager.ponerActividad(villager, "Cosechando");
                cosechar(level);
            }
            case PLANTAR -> {
                VillageManager.ponerActividad(villager, "Sembrando");
                plantar(level);
            }
            case FERTILIZAR -> {
                VillageManager.ponerActividad(villager, "Abonando");
                fertilizar(level);
            }
            case DESPENSA -> {
                VillageManager.ponerActividad(villager, "Llevando la cosecha");
                enLaDespensa(level);
            }
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

    // --- las faenas -------------------------------------------------------------------------------

    private void cosechar(ServerLevel level) {
        BlockState state = level.getBlockState(target);
        // OJO: la edad se lee con la propiedad del PROPIO cultivo (el betabel es 0-3 y el trigo 0-7).
        if (!(state.getBlock() instanceof CropBlock crop)
                || VillageGenerator.edadDelCultivo(state) != crop.getMaxAge()) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, target, null);
        level.destroyBlock(target, false);
        level.setBlock(target, crop.getStateForAge(0), Block.UPDATE_ALL); // replantado en el sitio
        for (ItemStack drop : drops) {
            // Las SEMILLAS solo hasta un tope: si se le llenan los 8 huecos con semillas, el trigo ya no le cabe, se le
            // cae al suelo y nunca acumula las 3 unidades que disparan el viaje a la despensa (por eso el cofre seguía
            // con las 12 semillas iniciales y la aldea pasaba hambre).
            if (esSemilla(drop) && semillasEnMano() >= SEMILLAS_MAX) {
                level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5D, target.getY() + 0.5D,
                        target.getZ() + 0.5D, drop));
                continue;
            }
            ItemStack resto = guardarEnInventario(drop);
            if (!resto.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5D, target.getY() + 0.5D,
                        target.getZ() + 0.5D, resto));
            }
        }
        level.playSound(null, target, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    private void plantar(ServerLevel level) {
        ItemStack semilla = sacarSemilla();
        if (semilla.isEmpty()) {
            return;
        }
        BlockState cultivo = cultivoDe(semilla);
        if (cultivo == null) {
            return;
        }
        level.setBlock(target, cultivo, Block.UPDATE_ALL);
        level.playSound(null, target, cultivo.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    private void fertilizar(ServerLevel level) {
        ItemStack harina = sacarHarina();
        if (harina.isEmpty()) {
            return;
        }
        if (net.minecraft.world.item.BoneMealItem.growCrop(harina, level, target)) {
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    /** En la despensa: guarda el trigo, hornea pan, coge semillas y harina de huesos, y vacía el compostero. */
    private void enLaDespensa(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null) {
            DevilRpg.LOGGER.warn("[Village] El granjero llego al kiosco y NO encontro la despensa (aldea en {})", center);
            return;
        }
        // 1) Compostero lleno -> harina de huesos para la despensa (el abono de la aldea lo produce ella misma).
        for (BlockPos p : VillageGenerator.parcelasDe(level, center)) {
            BlockPos comp = p.offset(-1, 0, 0);
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos q = comp.offset(0, dy, 0);
                BlockState s = level.getBlockState(q);
                if (s.is(Blocks.COMPOSTER) && s.getValue(ComposterBlock.LEVEL) == 8) {
                    level.setBlock(q, s.setValue(ComposterBlock.LEVEL, 0), Block.UPDATE_ALL);
                    VillagePantry.guardar(despensa, new ItemStack(Items.BONE_MEAL));
                }
            }
        }
        // 2) TODO lo comestible que lleve encima, a la despensa: el trigo (para el pan) y los VEGETALES (zanahoria,
        // patata y betabel). Antes solo se guardaba el TRIGO, así que lo demás se quedaba en su inventario o se caía al
        // suelo: el contador de comida de la aldea mira LO QUE HAY EN LA DESPENSA, no lo plantado, así que la aldea
        // pasaba hambre con la huerta llena (y moría gente teniendo comida).
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT) || VillagePantry.esVegetal(s)) {
                ItemStack resto = VillagePantry.guardar(despensa, s.copy());
                villager.getInventory().setItem(i, resto);
            }
        }
        // 3) LO DEL ALMACÉN, A LA DESPENSA: el recolector (holgazán) recoge del suelo lo que se cae por el pueblo
        // —incluido lo que deja caer el propio juego cuando SU aldeano granjero cosecha, que tira el grano al
        // suelo— y lo guarda en el almacén. Como el contador de comida de la aldea mira LA DESPENSA, esa comida se
        // quedaba muerta de risa en el almacén y la aldea pasaba hambre con el almacén lleno. El granjero hace de
        // puente en cada visita: se trae la comida, las semillas y el abono que el recolector haya guardado.
        int traidos = VillagePantry.traspasar(VillageStorage.almacen(level, center), despensa,
                s -> s.is(Items.WHEAT) || s.is(Items.BREAD) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)
                        || s.is(Items.BONE_MEAL) || VillagePantry.esVegetal(s)
                        || VillagePantry.esCarneCruda(s) || VillagePantry.esCarneCocida(s),
                TRAER_DEL_ALMACEN);
        // 4) Hornear: 3 de trigo por hogaza (la receta de vanilla), como mucho HORNEAR_MAX por visita.
        int horneadas = 0;
        while (horneadas < HORNEAR_MAX
                && VillagePantry.sacar(despensa, s -> s.is(Items.WHEAT), VillagePantry.WHEAT_PER_BREAD)
                    == VillagePantry.WHEAT_PER_BREAD) {
            VillagePantry.guardar(despensa, new ItemStack(Items.BREAD));
            horneadas++;
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        // 5) Recambios: semillas y harina de huesos, si le faltan.
        if (!tieneSemillas()) {
            for (ItemStack plantable : List.of(new ItemStack(Items.WHEAT_SEEDS, 4), new ItemStack(Items.CARROT, 3),
                    new ItemStack(Items.POTATO, 3), new ItemStack(Items.BEETROOT_SEEDS, 3))) {
                if (VillagePantry.sacar(despensa, s -> ItemStack.isSameItem(s, plantable), plantable.getCount())
                        == plantable.getCount()) {
                    guardarEnInventario(plantable);
                    break;
                }
            }
        }
        if (harinaEnMano() == 0) {
            int coge = VillagePantry.sacar(despensa, s -> s.is(Items.BONE_MEAL), HARINA_MAX);
            if (coge > 0) {
                guardarEnInventario(new ItemStack(Items.BONE_MEAL, coge));
            }
        }
        if (horneadas > 0) {
            DevilRpg.LOGGER.info("[Village] El granjero guardo su trigo y horneo {} pan(es) en la despensa", horneadas);
        } else if (traidos > 0) {
            DevilRpg.LOGGER.info("[Village] El granjero trajo {} unidad(es) de comida del almacen a la despensa", traidos);
        } else {
            DevilRpg.LOGGER.debug("[Village] El granjero visito la despensa (no habia trigo para hornear)");
        }
    }

    // --- utilidades --------------------------------------------------------------------------------

    private void irAlObjetivo() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, 0.6F);
        }
    }

    /** Busca en las parcelas un cultivo maduro (o creciendo, si {@code maduro} es false). */
    @Nullable
    private BlockPos buscarCultivo(ServerLevel level, boolean maduro) {
        for (BlockPos parcela : VillageGenerator.parcelasDe(level, center)) {
            for (int dx = 0; dx < VillageGenerator.PLOT_WIDTH; dx++) {
                for (int dz = 0; dz < VillageGenerator.PLOT_DEPTH; dz++) {
                    BlockPos q = parcela.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos r = q.offset(0, dy, 0);
                        BlockState s = level.getBlockState(r);
                        if (s.getBlock() instanceof CropBlock crop) {
                            boolean esMaduro = VillageGenerator.edadDelCultivo(s) == crop.getMaxAge();
                            if (esMaduro == maduro) {
                                return r;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Busca tierra de cultivo con el hueco de arriba libre (para plantar). */
    @Nullable
    private BlockPos buscarTierraVacia(ServerLevel level) {
        for (BlockPos parcela : VillageGenerator.parcelasDe(level, center)) {
            for (int dx = 0; dx < VillageGenerator.PLOT_WIDTH; dx++) {
                for (int dz = 0; dz < VillageGenerator.PLOT_DEPTH; dz++) {
                    BlockPos q = parcela.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos tierra = q.offset(0, dy, 0);
                        BlockPos aire = tierra.above();
                        if (level.getBlockState(tierra).is(Blocks.FARMLAND) && level.getBlockState(aire).isAir()) {
                            return aire;
                        }
                    }
                }
            }
        }
        return null;
    }

    private int trigoEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Vegetales (zanahoria, patata, betabel) que lleva encima: también son comida de la aldea. */
    private int vegetalesEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (VillagePantry.esVegetal(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int harinaEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private boolean tieneSemillas() {
        return semillasEnMano() > 0;
    }

    /** Cuántas semillas lleva encima (sumando todos los tipos). */
    private int semillasEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemilla(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Saca una semilla del inventario (y devuelve la semilla que debe plantarse, o vacío). */
    private ItemStack sacarSemilla() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemilla(s)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack sacarHarina() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
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

    public static boolean esSemilla(ItemStack s) {
        return s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS);
    }

    /** El cultivo que crece de esa semilla. */
    @Nullable
    private static BlockState cultivoDe(ItemStack semilla) {
        if (semilla.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT.defaultBlockState();
        if (semilla.is(Items.CARROT)) return Blocks.CARROTS.defaultBlockState();
        if (semilla.is(Items.POTATO)) return Blocks.POTATOES.defaultBlockState();
        if (semilla.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS.defaultBlockState();
        return null;
    }
}
