package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    /** Dónde está el cofre de la despensa, relativo al centro (dentro del kiosco de la plaza). */
    private static final BlockPos OFFSET = new BlockPos(0, 1, 1);
    /** Cuánta comida aporta cada cosa guardada. El pan es la ración buena; el trigo, el doble de crudo. */
    public static final int FOOD_PER_BREAD = 4;
    public static final int FOOD_PER_COOKED_MEAT = 4;
    public static final int FOOD_PER_RAW_MEAT = 2;
    public static final int FOOD_PER_VEGETABLE = 2;
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
     * Punto de apoyo para que un aldeano vaya a la despensa: una casilla de suelo <b>delante del kiosco</b> (a la
     * cota del pueblo, transitable).
     * <p>
     * OJO: nunca se navega HACIA el cofre, porque es un bloque sólido y la navegación no puede "llegar" a esa
     * casilla: el aldeano se quedaba dando vueltas alrededor del kiosco sin descargar nada (el bug que vio el
     * jugador). Se camina a este punto y se comprueba la distancia AL COFRE.
     */
    public static BlockPos puntoDeApoyo(ServerLevel level, BlockPos center) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        return new BlockPos(center.getX(), nivel, center.getZ() + 4);
    }

    /** Distancia (en bloques) a la que un aldeano ya "alcanza" la despensa para dejar o coger cosas. */
    public static final double ALCANCE_DESPENSA = 5.0D;

    /**
     * Posición real del cofre de la despensa (o {@code null} si no se encuentra). La caja de búsqueda se mide desde
     * <b>la cota</b>, nunca desde la Y del centro (esa puede ser la del spawn del jugador y la búsqueda caería en el
     * aire).
     */
    public static BlockPos posReal(ServerLevel level, BlockPos center) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos p = new BlockPos(center.getX() + OFFSET.getX(), nivel + 1, center.getZ() + OFFSET.getZ());
        if (level.getBlockState(p).is(Blocks.CHEST)) {
            return p;
        }
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-6, -4, -6), p.offset(6, 4, 6))) {
            if (level.getBlockState(q).is(Blocks.CHEST)) {
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

    /** El cofre (simple o <b>doble</b>) de la despensa, o {@code null} si esa aldea aún no tiene kiosco. */
    @Nullable
    public static Container despensa(ServerLevel level, BlockPos center) {
        // OJO: la búsqueda se centra en LA COTA DEL PUEBLO, nunca en la Y del centro del objetivo: esa Y puede ser
        // cualquier cosa (en la partida del jugador era 101 con la aldea en la 63), así que la caja de búsqueda caía
        // en el aire, no encontraba el cofre y `asegurarKiosco` volvía a construir el kiosco EN CADA LATIDO: el log
        // se llenaba de "kiosco colocados" cada 10 s y cada reconstrucción BORRABA el cofre con lo que tuviera
        // dentro (por eso el granjero nunca dejaba comida: se la borraban).
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos p = new BlockPos(center.getX(), nivel + 1, center.getZ() + 1);
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-6, -3, -6), p.offset(6, 3, 6))) {
            BlockState state = level.getBlockState(q);
            if (state.getBlock() instanceof ChestBlock cofre) {
                Container c = ChestBlock.getContainer(cofre, state, level, q.immutable(), true);
                if (c != null) {
                    return c;
                }
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

    /**
     * Pasa de {@code origen} a la despensa hasta {@code max} unidades que cumplan el filtro, y devuelve cuántas movió.
     * <p>
     * Lo usa el granjero para <b>traer a la despensa lo que el recolector guardó en el almacén</b>: el aldeano sin
     * oficio recoge del suelo lo que se cae por el pueblo —incluido lo que deja caer el propio juego cuando su
     * aldeano granjero cosecha— y lo guarda en el almacén. Si esa comida se quedara allí, la aldea pasaría hambre con
     * el almacén lleno, porque el contador de comida mira <b>esta</b> despensa.
     */
    public static int traspasar(@Nullable Container origen, @Nullable Container destino,
                                Predicate<ItemStack> cual, int max) {
        if (origen == null || destino == null || max <= 0) {
            return 0;
        }
        int movidos = 0;
        for (int i = 0; i < origen.getContainerSize() && movidos < max; i++) {
            ItemStack s = origen.getItem(i);
            if (s.isEmpty() || !cual.test(s)) {
                continue;
            }
            int cuantos = Math.min(s.getCount(), max - movidos);
            ItemStack resto = guardar(destino, s.copyWithCount(cuantos));
            int puestos = cuantos - resto.getCount();
            if (puestos <= 0) {
                break; // la despensa no admite más
            }
            s.shrink(puestos);
            if (s.isEmpty()) {
                origen.setItem(i, ItemStack.EMPTY);
            }
            origen.setChanged();
            movidos += puestos;
        }
        return movidos;
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
                + contar(c, s -> s.is(Items.BAKED_POTATO)) * FOOD_PER_COOKED_MEAT
                + contar(c, VillagePantry::esVegetal) * FOOD_PER_VEGETABLE
                + contar(c, s -> s.is(Items.WHEAT)) * FOOD_PER_WHEAT;
    }

    /** Vegetales que come la aldea: zanahoria, patata y betabel (el betabel también se cultiva en la parcela). */
    public static boolean esVegetal(ItemStack s) {
        return s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT);
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
        if (cruda.is(Items.POTATO)) return new ItemStack(Items.BAKED_POTATO); // patata asada
        return ItemStack.EMPTY;
    }

    /** ¿Se puede cocinar esto? (carne cruda o patata) */
    public static boolean sePuedeCocinar(ItemStack s) {
        return esCarneCruda(s) || s.is(Items.POTATO);
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

    /** Saca comida de la despensa por valor (para que coma la aldea): pan, carne, vegetales y trigo crudo. */
    public static int sacarComida(@Nullable Container c, int puntos) {
        int faltan = puntos;
        faltan -= sacar(c, s -> s.is(Items.BREAD), (faltan + FOOD_PER_BREAD - 1) / FOOD_PER_BREAD) * FOOD_PER_BREAD;
        if (faltan <= 0) {
            return puntos;
        }
        // Carne cocinada y patata asada (lo que cocina el cocinero).
        faltan -= sacar(c, s -> esCarneCocida(s) || s.is(Items.BAKED_POTATO),
                (faltan + FOOD_PER_COOKED_MEAT - 1) / FOOD_PER_COOKED_MEAT) * FOOD_PER_COOKED_MEAT;
        if (faltan <= 0) {
            return puntos;
        }
        // Vegetales: zanahoria, patata y betabel.
        faltan -= sacar(c, VillagePantry::esVegetal,
                (faltan + FOOD_PER_VEGETABLE - 1) / FOOD_PER_VEGETABLE) * FOOD_PER_VEGETABLE;
        if (faltan <= 0) {
            return puntos;
        }
        // Carne cruda y, como último recurso, trigo.
        faltan -= sacar(c, VillagePantry::esCarneCruda, (faltan + FOOD_PER_RAW_MEAT - 1) / FOOD_PER_RAW_MEAT)
                * FOOD_PER_RAW_MEAT;
        if (faltan <= 0) {
            return puntos;
        }
        faltan -= sacar(c, s -> s.is(Items.WHEAT), faltan) * FOOD_PER_WHEAT;
        return puntos - Math.max(0, faltan);
    }
}
