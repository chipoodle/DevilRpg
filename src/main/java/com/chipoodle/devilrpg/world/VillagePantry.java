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
 * <b>La despensa de la aldea</b>: un cofre doble de verdad (contenedor) en el <b>almacén de comida de la taberna</b>
 * (la cocina), junto al ahumador del cocinero.
 * <p>
 * Antes la comida de la aldea era un <b>contador abstracto</b>: cada latido "la granja producía 8" aunque no
 * hubiera ni un granjero ni un solo cultivo. Ahora la comida es <b>lo que hay guardado aquí dentro</b>: el granjero
 * cosecha el trigo de las parcelas, lo trae, lo convierte en pan y la aldea come de esa despensa. Si la despensa
 * está vacía, la aldea pasa hambre de verdad.
 * <p>
 * <b>Estuvo en el kiosco de la plaza</b> hasta la migración 46. El jugador pidió moverlo: <i>"el cofre de la comida
 * ya no tiene sentido que esté en el kiosco central... sería mejor moverlo a la taberna, tomar un cuarto y
 * convertirlo en almacén de comida"</i>. Ahora vive en la cocina de la taberna: donde el cocinero cocina y donde el
 * pueblo viene a comer. Se usa un cofre (y no un barril) porque el barril es el puesto de trabajo del
 * <b>pescador</b>: un aldeano sin oficio lo reclamaría y la aldea acabaría con un pescador.
 */
public final class VillagePantry {

    /** Las dos mitades del cofre doble, relativas a la <b>esquina de la taberna</b> (ver TABERNA_DESPENSA). */
    private static final BlockPos OFFSET = new BlockPos(
            VillageGenerator.TABERNA_DESPENSA[0], 0, VillageGenerator.TABERNA_DESPENSA[1]);
    /** Casilla de apoyo dentro de la cocina, relativa a la esquina de la taberna (el suelo libre delante del cofre). */
    private static final BlockPos APOYO = new BlockPos(OFFSET.getX() + 1, 0, OFFSET.getZ() + 1);
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

    /** Posición (sin Y fija) de la <b>primera mitad</b> del cofre de la despensa (en la cocina de la taberna). */
    public static BlockPos pos(BlockPos center) {
        BlockPos base = VillageGenerator.baseDeLaTaberna(center);
        return base.offset(OFFSET.getX(), 0, OFFSET.getZ());
    }

    /**
     * Punto de apoyo para que un aldeano vaya a la despensa: el <b>suelo libre de la cocina, delante del cofre</b>
     * (a la cota del pueblo y transitable).
     * <p>
     * OJO: nunca se navega HACIA el cofre, porque es un bloque sólido y la navegación no puede "llegar" a esa
     * casilla: el aldeano se quedaba dando vueltas sin descargar nada (el bug que vio el jugador). Se camina a este
     * punto y se comprueba la distancia AL COFRE. Desde la migración 46 la despensa está en la <b>cocina de la
     * taberna</b>, así que el punto es una casilla de esa cocina: el pueblo entra por su puerta (dx 3, dz 5) y se
     * pone delante del cofre.
     */
    public static BlockPos puntoDeApoyo(ServerLevel level, BlockPos center) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos base = VillageGenerator.baseDeLaTaberna(center);
        return new BlockPos(base.getX() + APOYO.getX(), nivel, base.getZ() + APOYO.getZ());
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
        BlockPos exacto = pos(center);
        BlockPos p = new BlockPos(exacto.getX(), nivel, exacto.getZ());
        if (level.getBlockState(p).is(Blocks.CHEST)) {
            return p;
        }
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -2, -2), p.offset(4, 3, 4))) {
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

    /**
     * El cofre (simple o <b>doble</b>) de la despensa, o {@code null} si esa aldea aún no tiene taberna.
     * <p>
     * Primero se mira <b>el sitio exacto</b> donde lo pone la cocina de la taberna (las dos mitades del cofre doble,
     * a la cota del pueblo) y solo si ahí no hay nada se busca cerca. Antes se escaneaba una caja y se devolvía
     * <b>el primer cofre que apareciera</b>: un cofre <b>del jugador</b> puesto en la plaza se lo quedaba la aldea
     * como despensa (y con la limpieza de la despensa, la aldea le habría movido las cosas al almacén).
     * <p>
     * El <b>cofre viejo del kiosco</b> (aldeas de antes de la migración 46) sigue valiendo mientras la migración no
     * pase: así una aldea vieja no se queda sin comida de un día para otro.
     */
    @Nullable
    public static Container despensa(ServerLevel level, BlockPos center) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos base = VillageGenerator.baseDeLaTaberna(center);
        // 1) EL COFRE DE LA COCINA, en su sitio exacto (las dos mitades del cofre doble).
        for (int k = 0; k <= 1; k++) {
            Container c = cofreEn(level, new BlockPos(base.getX() + OFFSET.getX() + k, nivel,
                    base.getZ() + OFFSET.getZ()));
            if (c != null) {
                return c;
            }
        }
        // 2) EL COFRE VIEJO DEL KIOSCO (aldea de antes de la migración 46), para no dejarla sin despensa.
        for (BlockPos viejo : new BlockPos[]{
                new BlockPos(center.getX(), nivel + 1, center.getZ() + 1),
                new BlockPos(center.getX() + 1, nivel + 1, center.getZ() + 1)}) {
            Container c = cofreEn(level, viejo);
            if (c != null) {
                return c;
            }
        }
        // 3) Y si no, SOLO dentro de la cocina de la taberna (su radio es pequeño: nada de la plaza del jugador).
        BlockPos p = new BlockPos(base.getX() + OFFSET.getX(), nivel, base.getZ() + OFFSET.getZ());
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -2, -2), p.offset(4, 3, 4))) {
            Container c = cofreEn(level, q.immutable());
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /**
     * <b>Solo</b> el cofre de la cocina de la taberna (o {@code null}). Lo usa la migración para no confundirlo con
     * el cofre viejo del kiosco mientras lo retira.
     */
    @Nullable
    public static Container despensaDeLaTaberna(ServerLevel level, BlockPos center) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos base = VillageGenerator.baseDeLaTaberna(center);
        for (int k = 0; k <= 1; k++) {
            Container c = cofreEn(level, new BlockPos(base.getX() + OFFSET.getX() + k, nivel,
                    base.getZ() + OFFSET.getZ()));
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** El contenedor de ese bloque si es un cofre (o {@code null} si no lo es). */
    @Nullable
    private static Container cofreEn(ServerLevel level, BlockPos q) {
        BlockState state = level.getBlockState(q);
        if (state.getBlock() instanceof ChestBlock cofre) {
            return ChestBlock.getContainer(cofre, state, level, q, true);
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

    /**
     * ¿Esto <b>va</b> en la despensa? La despensa es <b>la comida del pueblo</b> y los <b>recambios del granjero</b>
     * (semillas y harina de huesos, que él mismo coge de aquí para sembrar y abonar). Todo lo demás (plumas, cuero,
     * cuerda, lana, hierro, pepitas, troncos, tablones...) es material del <b>almacén</b> y se va de aquí.
     */
    public static boolean perteneceALaDespensa(ItemStack s) {
        return s.is(Items.BREAD) || s.is(Items.WHEAT) || s.is(Items.BAKED_POTATO) || esVegetal(s)
                || esCarneCruda(s) || esCarneCocida(s) || s.is(Items.APPLE)
                || esSemilla(s) || s.is(Items.BONE_MEAL);
    }

    /**
     * ¿Es una semilla de las que siembra el granjero? Es la <b>misma lista</b> que usa él
     * ({@code VillagerFarmGoal.esSemilla}, que ahora llama aquí): si las dos listas se separan, el granjero se queda
     * en bucle intentando sembrar algo que no sabe plantar.
     */
    public static boolean esSemilla(ItemStack s) {
        return s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS);
    }

    /**
     * <b>Limpia la despensa</b>: lo que no es comida ni recambio del granjero ({@link #perteneceALaDespensa}) se
     * mueve al <b>almacén</b>. Lo pidió el jugador: <i>"los materiales que no pertenezcan a la despensa, que los
     * muevan al almacén, como las plumas"</i>.
     * <p>
     * Si el almacén está lleno no se tira nada: el objeto se queda donde está (el jugador decide qué hacer con él).
     *
     * @return cuántas unidades se movieron de verdad (0 si no había nada que sacar)
     */
    public static int limpiarDespensa(ServerLevel level, BlockPos center) {
        Container despensa = despensa(level, center);
        if (despensa == null) {
            return 0;
        }
        int movidos = 0;
        for (int i = 0; i < despensa.getContainerSize(); i++) {
            ItemStack s = despensa.getItem(i);
            if (s.isEmpty() || perteneceALaDespensa(s)) {
                continue;
            }
            ItemStack resto = VillageStorage.guardar(level, center, s.copy());
            int puestos = s.getCount() - resto.getCount();
            if (puestos <= 0) {
                continue; // el almacén no admite más: se queda como estaba
            }
            despensa.setItem(i, resto);
            despensa.setChanged();
            movidos += puestos;
        }
        return movidos;
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
