package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * <b>La despensa de la aldea</b>: un barril de verdad (contenedor) en la plaza, junto a la campana.
 * <p>
 * Antes la comida de la aldea era un <b>contador abstracto</b>: cada latido "la granja producía 8" aunque no
 * hubiera ni un granjero ni un solo cultivo. Ahora la comida es <b>lo que hay guardado aquí dentro</b>: el granjero
 * cosecha el trigo de las parcelas, lo trae, lo convierte en pan y la aldea come de esa despensa. Si la despensa
 * está vacía, la aldea pasa hambre de verdad.
 * <p>
 * Se usa un barril (y no un cofre) porque se abre con la mano, se ve el pan dentro y encaja con la granja.
 */
public final class VillagePantry {

    /** Dónde se pone la despensa, relativo al centro de la aldea (en la plaza, en diagonal a la campana). */
    private static final BlockPos OFFSET = new BlockPos(5, 0, 5);
    /** Cuánta comida aporta cada cosa guardada. El pan es la ración buena; el trigo, el doble de crudo. */
    public static final int FOOD_PER_BREAD = 4;
    public static final int FOOD_PER_COOKED_MEAT = 4;
    public static final int FOOD_PER_RAW_MEAT = 2;
    public static final int FOOD_PER_WHEAT = 1;
    /** Trigo que hace falta para una hogaza (la receta de vanilla son 3). */
    public static final int WHEAT_PER_BREAD = 3;

    private VillagePantry() {
    }

    /** Posición (sin Y fija) de la despensa de esa aldea. */
    public static BlockPos pos(BlockPos center) {
        return center.offset(OFFSET.getX(), 0, OFFSET.getZ());
    }

    /**
     * La posición <b>real</b> del barril (con su Y), para que los aldeanos caminen al sitio exacto. Si no hay
     * barril devuelve la posición teórica.
     */
    public static BlockPos posReal(ServerLevel level, BlockPos center) {
        BlockPos p = pos(center);
        if (level.getBlockState(p).is(Blocks.BARREL)) {
            return p;
        }
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-8, -4, -8), p.offset(8, 4, 8))) {
            if (level.getBlockState(q).is(Blocks.BARREL)) {
                return q.immutable();
            }
        }
        return p;
    }

    /**
     * Llena la despensa con la <b>remesa inicial</b>: semillas para que el granjero pueda sembrar (sin esto la
     * aldea nueva no tendría de dónde sacar el primer trigo), un poco de abono y un par de panes para aguantar
     * hasta la primera cosecha. Solo se llama al crear la despensa.
     */
    public static void remesaInicial(@Nullable Container c) {
        if (c == null) {
            return;
        }
        if (contar(c, s -> true) > 0) {
            return; // ya tiene cosas dentro: no se le añade nada
        }
        guardar(c, new ItemStack(Items.WHEAT_SEEDS, 12));
        guardar(c, new ItemStack(Items.CARROT, 3));
        guardar(c, new ItemStack(Items.POTATO, 3));
        guardar(c, new ItemStack(Items.BONE_MEAL, 4));
        guardar(c, new ItemStack(Items.BREAD, 2));
    }

    /** El barril de la despensa, o {@code null} si esa aldea aún no tiene (aldeas viejas sin migrar). */
    @Nullable
    public static Container despensa(ServerLevel level, BlockPos center) {
        BlockPos p = pos(center);
        if (level.getBlockEntity(p) instanceof Container c) {
            return c;
        }
        // Por si el jugador la movió o la aldea es vieja: se busca un barril cerca del centro.
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-8, -4, -8), p.offset(8, 4, 8))) {
            if (level.getBlockState(q).is(Blocks.BARREL) && level.getBlockEntity(q) instanceof Container c) {
                return c;
            }
        }
        return null;
    }

    /** Cuenta cuántas unidades hay en la despensa que cumplan el filtro. */
    public static int contar(@Nullable Container c, Predicate<ItemStack> cual) {
        if (c == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && cual.test(s)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Cuánta <b>comida</b> hay en la despensa (lo que come la aldea). */
    public static int comida(ServerLevel level, BlockPos center) {
        Container c = despensa(level, center);
        if (c == null) {
            return 0;
        }
        return contar(c, s -> s.is(Items.BREAD)) * FOOD_PER_BREAD
                + contar(c, VillagePantry::esCarneCocida) * FOOD_PER_COOKED_MEAT
                + contar(c, VillagePantry::esCarneCruda) * FOOD_PER_RAW_MEAT
                + contar(c, s -> s.is(Items.WHEAT)) * FOOD_PER_WHEAT;
    }

    public static boolean esCarneCruda(ItemStack s) {
        return s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.CHICKEN) || s.is(Items.MUTTON)
                || s.is(Items.RABBIT) || s.is(Items.COD) || s.is(Items.SALMON);
    }

    public static boolean esCarneCocida(ItemStack s) {
        return s.is(Items.COOKED_BEEF) || s.is(Items.COOKED_PORKCHOP) || s.is(Items.COOKED_CHICKEN)
                || s.is(Items.COOKED_MUTTON) || s.is(Items.COOKED_RABBIT) || s.is(Items.COOKED_COD)
                || s.is(Items.COOKED_SALMON);
    }

    /** Lo cocinado equivalente a una carne cruda (lo que hace el cocinero). */
    public static ItemStack cocinar(ItemStack cruda) {
        if (cruda.is(Items.BEEF)) return new ItemStack(Items.COOKED_BEEF);
        if (cruda.is(Items.PORKCHOP)) return new ItemStack(Items.COOKED_PORKCHOP);
        if (cruda.is(Items.CHICKEN)) return new ItemStack(Items.COOKED_CHICKEN);
        if (cruda.is(Items.MUTTON)) return new ItemStack(Items.COOKED_MUTTON);
        if (cruda.is(Items.RABBIT)) return new ItemStack(Items.COOKED_RABBIT);
        if (cruda.is(Items.COD)) return new ItemStack(Items.COOKED_COD);
        if (cruda.is(Items.SALMON)) return new ItemStack(Items.COOKED_SALMON);
        return ItemStack.EMPTY;
    }

    /** Guarda un stack en la despensa y devuelve lo que <b>no</b> cupo (vacío si entró todo). */
    public static ItemStack guardar(@Nullable Container c, ItemStack stack) {
        if (c == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack resto = stack.copy();
        for (int i = 0; i < c.getContainerSize() && !resto.isEmpty(); i++) {
            ItemStack dentro = c.getItem(i);
            if (!dentro.isEmpty() && ItemStack.isSameItemSameComponents(dentro, resto)
                    && dentro.getCount() < Math.min(dentro.getMaxStackSize(), c.getMaxStackSize())) {
                int espacio = Math.min(dentro.getMaxStackSize(), c.getMaxStackSize()) - dentro.getCount();
                int mete = Math.min(espacio, resto.getCount());
                dentro.grow(mete);
                resto.shrink(mete);
                c.setChanged();
            }
        }
        for (int i = 0; i < c.getContainerSize() && !resto.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                c.setItem(i, resto.copy());
                resto = ItemStack.EMPTY;
                c.setChanged();
            }
        }
        return resto;
    }

    /**
     * Saca hasta {@code cuantas} unidades que cumplan el filtro (de una en una, respetando los stacks) y devuelve
     * cuántas sacó de verdad.
     */
    public static int sacar(@Nullable Container c, Predicate<ItemStack> cual, int cuantas) {
        if (c == null) {
            return 0;
        }
        int sacadas = 0;
        for (int i = 0; i < c.getContainerSize() && sacadas < cuantas; i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || !cual.test(s)) {
                continue;
            }
            int quita = Math.min(cuantas - sacadas, s.getCount());
            s.shrink(quita);
            sacadas += quita;
            if (s.isEmpty()) {
                c.setItem(i, ItemStack.EMPTY);
            }
            c.setChanged();
        }
        return sacadas;
    }

    /** Saca comida de la despensa por valor (para que coma la aldea): pan primero, luego cocinado, luego crudo. */
    public static int sacarComida(@Nullable Container c, int puntos) {
        int faltan = puntos;
        faltan -= sacar(c, s -> s.is(Items.BREAD), (faltan + FOOD_PER_BREAD - 1) / FOOD_PER_BREAD) * FOOD_PER_BREAD;
        if (faltan <= 0) {
            return puntos;
        }
        int crudo = (faltan + FOOD_PER_RAW_MEAT - 1) / FOOD_PER_RAW_MEAT;
        faltan -= sacar(c, VillagePantry::esCarneCocida, crudo) * FOOD_PER_COOKED_MEAT;
        if (faltan <= 0) {
            return puntos;
        }
        faltan -= sacar(c, VillagePantry::esCarneCruda, (faltan + FOOD_PER_RAW_MEAT - 1) / FOOD_PER_RAW_MEAT)
                * FOOD_PER_RAW_MEAT;
        if (faltan <= 0) {
            return puntos;
        }
        faltan -= sacar(c, s -> s.is(Items.WHEAT), faltan) * FOOD_PER_WHEAT;
        return puntos - Math.max(0, faltan);
    }
}
