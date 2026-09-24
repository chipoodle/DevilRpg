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

import java.util.List;
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
    /**
     * <b>TRIGO QUE NO SE HORNEA NUNCA: es la comida de cría del ganadero.</b> Las <b>vacas</b> y las <b>ovejas</b> se
     * crían con <b>trigo</b> y el ganadero lo saca de la despensa ({@code VillagerAnimalFarmGoal.hayComidaParaCriar}
     * pide 2 y gasta 1 por animal). Mientras el granjero horneaba, se comía todo el trigo según llegaba, la despensa
     * nunca tenía 2 y el ganadero <b>no podía criar NUNCA</b>: medido en el guardado del jugador, el corral tenía
     * <b>3 vacas</b> (tope 6), la despensa <b>0 de trigo</b> (y 432 zanahorias, 155 patatas...) y el almacén <b>0 de
     * cuero</b> — sin cría no hay exceso, sin exceso no hay sacrificio y sin sacrificio no hay cuero (el jugador:
     * *"casi no se ha fabricado armaduras de cuero"*). Con la reserva, el ganadero siempre encuentra con qué criar.
     * <p>
     * Vive aquí, y no en el granjero, porque desde que el pan lo hace el <b>cocinero</b> ({@code
     * VillagerCookGoal.hornear}) la reserva tiene que estar donde está el horno: quien hornea es quien respeta el
     * tope. El granjero solo deja el trigo en la despensa.
     */
    public static final int RESERVA_DE_TRIGO_PARA_CRIAR = 4;
    /**
     * <b>RESERVAS DE LA DESPENSA: lo que la aldea NO se come de cada cosa.</b> Son <b>preferencias</b>, no candados
     * (si nadie tiene exceso, se come de la reserva: la aldea no se queda sin comer teniendo comida), y sirven para que
     * la comida se reparta <b>variada</b> en vez de comerse siempre lo mismo:
     * <ul>
     *   <li>{@link #RESERVA_PAN}: 4 hogazas (16 puntos). Son <b>3 las que pide vanilla para criar</b> (12 puntos de
     *       comida en el inventario), así que con 4 el pan de la cría está a salvo.</li>
     *   <li>{@link #RESERVA_COCIDA}: 4 piezas cocinadas (carne, patata asada, huevo estrellado).</li>
     *   <li>{@link #RESERVA_VEGETAL}: 16 vegetales (32 puntos), que es el granero del pueblo (la huerta da mucho).</li>
     *   <li>{@link #RESERVA_CRUDA}: 8 piezas crudas, para que el <b>cocinero</b> tenga qué cocinar (crudo = 2 puntos,
     *       cocinado = 4: comerlo crudo sería tirar la mitad).</li>
     * </ul>
     */
    public static final int RESERVA_PAN = 4;
    public static final int RESERVA_COCIDA = 4;
    public static final int RESERVA_VEGETAL = 16;
    public static final int RESERVA_CRUDA = 8;

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
        // TODOS LOS CONTENEDORES DE LA COCINA, UNIDOS. Lo pidió el jugador: *"es un cofre doble, lo debería reconocer
        // el cocinero; además dentro de la cocina hay otro cofre que no se ocupa y también debería poder ocuparlo, así
        // como todos los demás"*. Antes esto **devolvía el PRIMER cofre que encontraba y ya** (`return c`), así que el
        // resto de la cocina —incluido el cofre del jugador— quedaba invisible para el pueblo. Ahora se juntan todos
        // con `CompoundContainer`: el cocinero cocina con lo que haya en cualquiera de ellos y guarda donde quepa.
        java.util.List<Container> todos = new java.util.ArrayList<>();
        java.util.Set<BlockPos> puestos = new java.util.HashSet<>();
        java.util.function.Consumer<BlockPos> suma = q -> {
            if (puestos.contains(q)) {
                return;
            }
            BlockState estado = level.getBlockState(q);
            Container c = cofreEn(level, q);
            if (c == null) {
                return;
            }
            // Se apunta también su OTRA MITAD (un cofre doble devuelve el mismo contenedor unido por las dos
            // posiciones: sin esto se contaría dos veces).
            puestos.add(q);
            if (estado.getBlock() instanceof ChestBlock cofre) {
                puestos.add(q.relative(ChestBlock.getConnectedDirection(estado)));
            }
            todos.add(c);
        };
        // 1) EL COFRE DE LA COCINA, en su sitio exacto (las dos mitades del cofre doble).
        for (int k = 0; k <= 1; k++) {
            suma.accept(new BlockPos(base.getX() + OFFSET.getX() + k, nivel, base.getZ() + OFFSET.getZ()));
        }
        // 2) EL COFRE VIEJO DEL KIOSCO (aldea de antes de la migración 46), para no dejarla sin despensa.
        suma.accept(new BlockPos(center.getX(), nivel + 1, center.getZ() + 1));
        suma.accept(new BlockPos(center.getX() + 1, nivel + 1, center.getZ() + 1));
        // 3) Y TODO LO DEMÁS DE LA COCINA (su radio es pequeño: nada de la plaza del jugador).
        BlockPos p = new BlockPos(base.getX() + OFFSET.getX(), nivel, base.getZ() + OFFSET.getZ());
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -2, -2), p.offset(4, 3, 4))) {
            suma.accept(q.immutable());
        }
        if (todos.isEmpty()) {
            return null;
        }
        Container unido = todos.get(0);
        for (int i = 1; i < todos.size(); i++) {
            unido = new net.minecraft.world.CompoundContainer(unido, todos.get(i));
        }
        return unido;
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
                // Y los huevos estrellados, que son comida cocinada del pueblo (ración completa).
                + contar(c, VillagePantry::esHuevoEstrellado) * FOOD_PER_COOKED_MEAT
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
        // EL HUEVO, ESTRELLADO (lo pidió el jugador): sale de los huevos de las gallinas del corral y lo cocina el
        // cocinero en su ahumador igual que la carne.
        if (cruda.is(Items.EGG)) return new ItemStack(com.chipoodle.devilrpg.init.ModItems.HUEVO_ESTRELLADO.get());
        return ItemStack.EMPTY;
    }

    /** ¿Se puede cocinar esto? (carne cruda, patata o huevo) */
    public static boolean sePuedeCocinar(ItemStack s) {
        return esCarneCruda(s) || s.is(Items.POTATO) || s.is(Items.EGG);
    }

    /**
     * ¿Esto <b>va</b> en la despensa? La despensa es <b>la comida del pueblo</b> y los <b>recambios del granjero</b>
     * (semillas y harina de huesos, que él mismo coge de aquí para sembrar y abonar). Todo lo demás (plumas, cuero,
     * cuerda, lana, hierro, pepitas, troncos, tablones...) es material del <b>almacén</b> y se va de aquí.
     */
    public static boolean perteneceALaDespensa(ItemStack s) {
        return s.is(Items.BREAD) || s.is(Items.WHEAT) || s.is(Items.BAKED_POTATO) || esVegetal(s)
                || esCarneCruda(s) || esCarneCocida(s) || s.is(Items.APPLE)
                || esSemilla(s) || s.is(Items.BONE_MEAL)
                // Y EL HUEVO ESTRELLADO: es comida del pueblo (lo pidió el jugador: "y puedan consumir todos"), así que
                // entra en la despensa y en el reparto de raciones como una pieza cocinada más.
                || esHuevoEstrellado(s)
                // Y EL HUEVO **CRUDO**: no es comida (no se reparte como ración), pero es la MATERIA PRIMA del
                // cocinero, así que vive en la despensa como el trigo. El jugador lo vio claro: *"todavía no veo
                // cocinado ningún huevo estrellado y los huevos están en el almacén"* — el ganadero los deja en el
                // almacén, el granjero los trae a la despensa y el cocinero los fríe.
                || s.is(Items.EGG);
    }

    /** ¿Es un <b>huevo estrellado</b> (la comida que cocina el cocinero con los huevos del corral)? */
    public static boolean esHuevoEstrellado(ItemStack s) {
        return s.is(com.chipoodle.devilrpg.init.ModItems.HUEVO_ESTRELLADO.get());
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

    /** La receta de vanilla: un <b>hueso</b> da <b>tres</b> de polvo de hueso (ver {@code BoneMealItem}). */
    public static final int POLVO_DE_HUESO_POR_HUESO = 3;

    /**
     * <b>Muele huesos</b>: los saca de la despensa y, si hace falta, del almacén, y deja el <b>polvo de hueso</b> en la
     * despensa (la receta de vanilla, {@link #POLVO_DE_HUESO_POR_HUESO} por hueso). Lo llama el <b>granjero</b> en cada
     * visita al kiosco.
     * <p>
     * Los huesos los sueltan los esqueletos que mata la milicia y los recoge el <b>recolector</b> (ver
     * {@code VillagerCollectGoal.esDelPueblo}), y hasta ahora se quedaban guardados sin que nadie los usara: medido en
     * la partida del jugador (aldea 0), <b>0 de polvo de hueso en toda la aldea</b> —con los tres composteros a nivel
     * 1, 1 y 5 de 8, o sea sin haber producido ni uno— y la arboleda del pueblo con 10 plantones sin abonar. Es lo que
     * pidió: *"los granjeros tampoco nunca deben olvidar de hacer polvo de hueso además de cultivar, cosechar y
     * entregar vegetales"*.
     *
     * @return cuántos huesos se han molido
     */
    public static int molerHuesos(@Nullable Container despensa, @Nullable Container almacen, int max) {
        if (despensa == null || max <= 0) {
            return 0;
        }
        int huesos = sacar(despensa, s -> s.is(Items.BONE), max);
        if (huesos < max) {
            huesos += sacar(almacen, s -> s.is(Items.BONE), max - huesos);
        }
        if (huesos > 0) {
            guardar(despensa, new ItemStack(Items.BONE_MEAL, huesos * POLVO_DE_HUESO_POR_HUESO));
        }
        return huesos;
    }

    /**
     * <b>Saca comida de la despensa por valor</b> (para que coma la aldea): pan, carne cocinada, vegetales, carne cruda
     * y trigo — y, esto es lo que pidió el jugador, <b>de lo que MÁS SOBRA</b>, no siempre del pan.
     * <p>
     * <b>Por qué</b>: antes se comía en un orden fijo (pan → cocinado → vegetales → crudo → trigo), así que mientras
     * hubiera pan horneado <b>los vegetales no se tocaban nunca</b> y se apilaban en la despensa (medido con el arnés,
     * 23-sep-2026: en doce minutos las verduras subieron de 103 a 225 con la despensa entre 500 y 1140 puntos y el pan
     * siempre a cero, porque el cocinero lo reponía tan rápido como se comía). Y había <b>comida que no se comía
     * jamás</b>: los <b>huevos estrellados</b> se contaban como comida ({@link #comida}) pero no estaban en ninguna de
     * las listas de aquí. Lo pidió el jugador: *"revisa que todos los aldeanos coman toda la comida que se produce
     * (zanahorias, betabel, etc.)"*.
     * <p>
     * <b>La regla</b>: se come <b>un punto de la comida que más puntos tiene por encima de su reserva</b> (ver
     * {@link #RESERVA_PAN} y compañía) y, si no hay exceso en ninguna, del orden de siempre: la reserva es una
     * <b>preferencia</b>, no un candado. Así la despensa se mantiene <b>variada y sin montones</b> — lo que se produce
     * se come y lo que queda es la reserva del cocinero y del ganadero.
     */
    public static int sacarComida(@Nullable Container c, int puntos) {
        if (c == null || puntos <= 0) {
            return 0;
        }
        int sacados = 0;
        int faltan = puntos;
        while (faltan > 0) {
            Grupo grupo = elQueMasSobra(c);
            if (grupo == null) {
                break; // no hay nada que comer (ni exceso ni reserva)
            }
            if (sacar(c, grupo.filtro(), 1) != 1) {
                break; // no debería pasar (se acaba de contar), pero no se insiste
            }
            faltan -= grupo.puntos();
            sacados += grupo.puntos();
        }
        return Math.min(sacados, puntos);
    }

    /** Un grupo de comida de la despensa: qué es, cuántos puntos vale cada pieza y cuánto se le reserva. */
    private record Grupo(Predicate<ItemStack> filtro, int puntos, int reserva) {
    }

    /** Los grupos de comida <b>en el orden de siempre</b> (el que se usa cuando no hay exceso de nada). */
    private static List<Grupo> gruposDeComida() {
        return List.of(
                new Grupo(s -> s.is(Items.BREAD), FOOD_PER_BREAD, RESERVA_PAN),
                // Carne cocinada, patata asada y HUEVO ESTRELLADO: lo que cocina el cocinero (el huevo estrellado
                // faltaba aquí: se contaba como comida y no se comía nunca).
                new Grupo(s -> esCarneCocida(s) || s.is(Items.BAKED_POTATO) || esHuevoEstrellado(s),
                        FOOD_PER_COOKED_MEAT, RESERVA_COCIDA),
                new Grupo(VillagePantry::esVegetal, FOOD_PER_VEGETABLE, RESERVA_VEGETAL),
                new Grupo(VillagePantry::esCarneCruda, FOOD_PER_RAW_MEAT, RESERVA_CRUDA),
                new Grupo(s -> s.is(Items.WHEAT), FOOD_PER_WHEAT, RESERVA_DE_TRIGO_PARA_CRIAR));
    }

    /** El grupo con <b>más exceso</b> (puntos por encima de su reserva) y, si ninguno tiene exceso, el primero con algo. */
    @Nullable
    private static Grupo elQueMasSobra(@Nullable Container c) {
        Grupo mejor = null;
        int mejorSobra = 0;
        for (Grupo g : gruposDeComida()) {
            int sobra = contar(c, g.filtro()) * g.puntos() - g.reserva();
            if (sobra > mejorSobra) {
                mejorSobra = sobra;
                mejor = g;
            }
        }
        if (mejor != null) {
            return mejor;
        }
        for (Grupo g : gruposDeComida()) {
            if (contar(c, g.filtro()) > 0) {
                return g; // nadie tiene exceso: se come de la reserva (la aldea no se queda sin comer teniendo comida)
            }
        }
        return null;
    }
}
