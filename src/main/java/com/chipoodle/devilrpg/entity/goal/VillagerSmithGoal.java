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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * <b>Herrero</b> de la aldea: coge los materiales del <b>almacén</b>, trabaja en <b>su puesto</b> de la herrería y deja
 * lo fabricado de vuelta en el almacén (que es el almacén del pueblo: de ahí se equipa la futura guardia).
 * <p>
 * Reparto de trabajo entre los <b>dos</b> herreros (lo fijó el jugador):
 * <ul>
 *   <li><b>Herrero de ARMAS</b> (muelle de afilar): <b>espadas, escudos, arcos y flechas</b>.</li>
 *   <li><b>Herrero de HERRAMIENTAS</b> (mesa de herrería): <b>armaduras</b> (de hierro o de cuero) y la
 *       <b>transformación de materiales</b>: pepitas de metal → lingotes, chatarra (armas/armaduras viejas) →
 *       lingotes y carne de zombie podrida → cuero.</li>
 * </ul>
 * El ciclo es real, no un contador: va al almacén, <b>se lleva</b> los ingredientes, los trabaja en su puesto y
 * <b>trae</b> la pieza fabricada. Así se le ve cargado de hierro y volviendo con una espada, y queda en el log y en su
 * etiqueta ("Fabricó una espada de hierro").
 */
public class VillagerSmithGoal extends Goal {

    /** Distancia a la que ya "alcanza" su puesto para trabajar. */
    private static final double REACH = 3.5D;
    /** Ticks de trabajo por pieza. */
    private static final int WORK_TICKS = 40;
    private static final int REST_TICKS = 10;
    private static final int IDLE_REST_TICKS = 100;
    private static final int STUCK_LIMIT = 140;
    /** Si se aleja más de esto del centro de la aldea, deja de trabajar. */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 16.0D;

    /** Cuántas unidades de cada cosa quiere tener la aldea en el almacén (indumentaria de la milicia: 4 espadachines
     *  con escudo y 3 arqueros). Cuando el almacén tiene de sobra, el herrero deja de fabricar y descansa. */
    private static final int OBJETIVO_ESPADAS = 4;
    private static final int OBJETIVO_ESCUDOS = 4;
    private static final int OBJETIVO_ARCOS = 3;
    private static final int OBJETIVO_FLECHAS = 64;
    /** Una pieza de armadura por militar (4 espadachines + 3 arqueros). */
    private static final int OBJETIVO_ARMADURA = 7;
    /** Pepitas que hacen falta para un lingote (la receta de vanilla) y carne podrida para un cuero. */
    private static final int PEPITAS_POR_LINGOTE = 9;
    private static final int CARNE_POR_CUERO = 9;
    /**
     * Madera que quiere tener el pueblo en el almacén: tablones (escudos y obras) y palos (arcos y flechas). El
     * <b>leñador</b> trae los troncos y el herrero de herramientas los parte en su mesa.
     */
    private static final int OBJETIVO_TABLONES = 32;
    private static final int OBJETIVO_PALOS = 64;
    /** Troncos que el herrero de herramientas sabe aserrar (los que trae el leñador y los del propio juego). */
    private static final List<ItemStack> TRONCOS = List.of(
            new ItemStack(Items.OAK_LOG), new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.BIRCH_LOG),
            new ItemStack(Items.JUNGLE_LOG), new ItemStack(Items.ACACIA_LOG), new ItemStack(Items.DARK_OAK_LOG),
            new ItemStack(Items.MANGROVE_LOG), new ItemStack(Items.CHERRY_LOG), new ItemStack(Items.CRIMSON_STEM),
            new ItemStack(Items.WARPED_STEM));

    private enum Fase { RECOGER, TRABAJAR, ENTREGAR }

    /**
     * Una faena del herrero: qué se lleva del almacén, qué se deja y cómo se llama (para el log y la etiqueta).
     */
    private record Receta(String verbo, String suceso, List<ItemStack> ingredientes, ItemStack producto) {
    }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    private final boolean armas;
    @Nullable
    private Receta receta;
    private Fase fase = Fase.RECOGER;
    @Nullable
    private BlockPos destino;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;

    public VillagerSmithGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.armas = villager.getVillagerData().getProfession() == VillagerProfession.WEAPONSMITH;
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
        if (villager.getVillagerData().getProfession() != VillagerProfession.WEAPONSMITH
                && villager.getVillagerData().getProfession() != VillagerProfession.TOOLSMITH) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            return false;
        }
        // PRIMERO la receta (son cuentas sobre el contenedor, barato) y solo si hay faena se busca el taller (esa
        // búsqueda recorre la herrería bloque a bloque, así que no se hace cuando no hay nada que hacer).
        Container almacen = VillageStorage.almacen(level, center);
        receta = elegirReceta(almacen);
        if (receta == null) {
            restTicks = IDLE_REST_TICKS; // no hay materiales (o ya está todo hecho): a esperar
            return false;
        }
        if (VillageGenerator.puestoDeHerreria(level, center, armas) == null) {
            receta = null;
            restTicks = IDLE_REST_TICKS;
            return false; // todavía no hay taller en esta aldea
        }
        fase = Fase.RECOGER;
        destino = VillageStorage.puntoDeApoyo(level, center);
        return destino != null;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        irAlDestino();
    }

    @Override
    public boolean canContinueToUse() {
        return receta != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (receta == null || destino == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
        double distancia = Math.sqrt(villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D,
                destino.getZ() + 0.5D));
        if (distancia > REACH) {
            VillageManager.caminarHacia(villager, destino, 0.6F);
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, switch (fase) {
                case RECOGER -> "Buscando materiales";
                case TRABAJAR -> "Fabricando";
                case ENTREGAR -> "Llevando lo fabricado";
            });
            return;
        }
        workTicks = 0;
        switch (fase) {
            case RECOGER -> {
                if (recoger(level)) {
                    fase = Fase.TRABAJAR;
                    destino = VillageGenerator.puestoDeHerreria(level, center, armas);
                } else {
                    receta = null; // se lo ha llevado otro: se elige otra cosa
                }
            }
            case TRABAJAR -> {
                fabricar(level);
                fase = Fase.ENTREGAR;
                destino = VillageStorage.puntoDeApoyo(level, center);
            }
            case ENTREGAR -> {
                entregar(level);
                receta = null;
            }
        }
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        receta = null;
        destino = null;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    // --- las faenas ---------------------------------------------------------------------------------

    /** Se lleva del almacén los ingredientes de la receta (si siguen estando). */
    private boolean recoger(ServerLevel level) {
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen == null || receta == null || !hay(almacen, receta.ingredientes())) {
            return false;
        }
        for (ItemStack necesario : receta.ingredientes()) {
            int sacadas = VillagePantry.sacar(almacen, s -> ItemStack.isSameItem(s, necesario), necesario.getCount());
            if (sacadas <= 0) {
                continue;
            }
            ItemStack enMano = new ItemStack(necesario.getItem(), sacadas);
            ItemStack resto = guardarEnInventario(enMano);
            if (!resto.isEmpty()) {
                VillageStorage.guardar(level, center, resto); // no le cupo: de vuelta al almacén
            }
        }
        return true;
    }

    /** Trabaja la pieza en su puesto: gasta los ingredientes que traía y se queda con el producto. */
    private void fabricar(ServerLevel level) {
        if (receta == null) {
            return;
        }
        // ANTES DE FABRICAR, comprobar que de verdad TRAE los ingredientes: si los hubiera perdido por el camino
        // (muerte, un golpe, que se los quitara alguien) y se fabricara igual, saldría una pieza de la nada.
        for (ItemStack necesario : receta.ingredientes()) {
            if (cuantosEnInventario(necesario.getItem()) < necesario.getCount()) {
                DevilRpg.LOGGER.debug("[Village] al herrero le faltan materiales para {}: no fabrica",
                        receta.producto());
                return;
            }
        }
        for (ItemStack necesario : receta.ingredientes()) {
            gastarDelInventario(necesario.getItem(), necesario.getCount());
        }
        ItemStack resto = guardarEnInventario(receta.producto().copy());
        if (!resto.isEmpty()) {
            VillageStorage.guardar(level, center, resto); // sin sitio: se queda en el almacén directamente
        }
        level.playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE,
                SoundSource.NEUTRAL, 0.8F, 1.0F);
        level.playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                SoundSource.BLOCKS, 0.5F, 1.0F);
        VillageManager.ponerSuceso(villager, receta.suceso());
        DevilRpg.LOGGER.info("[Village] {}: {}", armas ? "El herrero de armas" : "El herrero de herramientas",
                receta.suceso());
    }

    /** Deja en el almacén lo que ha fabricado. */
    private void entregar(ServerLevel level) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack resto = VillageStorage.guardar(level, center, s.copy());
            villager.getInventory().setItem(i, resto);
        }
    }

    // --- recetas ------------------------------------------------------------------------------------

    /**
     * ¿Qué toca hacer ahora? Primero la <b>transformación de materiales</b> (pepitas y chatarra en lingotes, y carne
     * podrida en cuero: eso lo hace el de HERRAMIENTAS en su mesa) y después <b>fabricar</b> lo que falte, cada uno lo
     * suyo. Devuelve {@code null} si no hay nada que hacer.
     */
    @Nullable
    private Receta elegirReceta(@Nullable Container almacen) {
        if (almacen == null) {
            return null;
        }
        // 1) Pepitas de metal -> lingotes (la forja). Lo hacen los dos.
        if (contar(almacen, Items.IRON_NUGGET) >= PEPITAS_POR_LINGOTE) {
            return new Receta("Fundiendo", "Fundio " + PEPITAS_POR_LINGOTE + " pepitas en un lingote",
                    List.of(new ItemStack(Items.IRON_NUGGET, PEPITAS_POR_LINGOTE)),
                    new ItemStack(Items.IRON_INGOT));
        }
        // 2) Chatarra PURA (hierro que no se pone nadie) -> lingotes.
        for (ItemStack chatarra : CHATARRA_SIEMPRE) {
            if (contar(almacen, chatarra.getItem()) > 0) {
                return new Receta("Fundiendo chatarra", "Fundio chatarra en un lingote",
                        List.of(new ItemStack(chatarra.getItem(), 1)), new ItemStack(Items.IRON_INGOT));
            }
        }
        // 2b) Equipo de hierro/malla que SÍ se pone la milicia (espada, escudo, armadura): se funde solo lo que
        //     SOBRA de la reserva. Si no, el herrero fundía la única espada del almacén y el espadachín no tenía con
        //     qué armarse nunca (ni armadura que ponerse, que es justo lo que el jugador quiere VER puesta).
        for (ItemStack chatarra : CHATARRA_CON_RESERVA) {
            if (haySobrante(almacen, chatarra.getItem())) {
                return new Receta("Fundiendo chatarra", "Fundio chatarra en un lingote",
                        List.of(new ItemStack(chatarra.getItem(), 1)), new ItemStack(Items.IRON_INGOT));
            }
        }
        // 2c) Chatarra de ORO -> lingote de oro (el oro no lo quiere nadie para pelear: se funde entero y queda
        //     como tesoro del almacén), y armadura de CUERO vieja -> cuero, también con reserva.
        for (ItemStack chatarra : CHATARRA_DE_ORO) {
            if (contar(almacen, chatarra.getItem()) > 0) {
                return new Receta("Fundiendo oro", "Fundio chatarra de oro en un lingote",
                        List.of(new ItemStack(chatarra.getItem(), 1)), new ItemStack(Items.GOLD_INGOT));
            }
        }
        for (ItemStack viejo : CUERO_VIEJO) {
            if (haySobrante(almacen, viejo.getItem())) {
                return new Receta("Reciclando cuero", "Reciclo una armadura de cuero",
                        List.of(new ItemStack(viejo.getItem(), 1)), new ItemStack(Items.LEATHER));
            }
        }
        // 3) Carne de zombie podrida -> cuero (lo hace el de herramientas, en su mesa).
        if (!armas && contar(almacen, Items.ROTTEN_FLESH) >= CARNE_POR_CUERO) {
            return new Receta("Curtiendo cuero", "Curtio " + CARNE_POR_CUERO + " carne podrida en un cuero",
                    List.of(new ItemStack(Items.ROTTEN_FLESH, CARNE_POR_CUERO)), new ItemStack(Items.LEATHER));
        }
        // 4) MADERA: el leñador (etapa B) trae TRONCOS al almacén, y sin esto no había de dónde sacar tablones ni
        //    palos: el escudo pide 6 tablones y el arco y las flechas, palos. Los parte el de HERRAMIENTAS en su mesa.
        if (!armas) {
            // 1 tronco -> 4 tablones (se guardan para los escudos y para el propio pueblo).
            if (contar(almacen, Items.OAK_PLANKS) < OBJETIVO_TABLONES) {
                for (ItemStack tronco : TRONCOS) {
                    if (contar(almacen, tronco.getItem()) > 0) {
                        return new Receta("Aserrando", "Aserro un tronco en 4 tablones",
                                List.of(new ItemStack(tronco.getItem(), 1)), new ItemStack(Items.OAK_PLANKS, 4));
                    }
                }
            }
            // 2 tablones -> 4 palos (arcos y flechas).
            if (contar(almacen, Items.STICK) < OBJETIVO_PALOS && contar(almacen, Items.OAK_PLANKS) >= 2) {
                return new Receta("Haciendo palos", "Hizo 4 palos",
                        List.of(new ItemStack(Items.OAK_PLANKS, 2)), new ItemStack(Items.STICK, 4));
            }
        }
        // 5) Fabricar lo que falte, según el puesto.
        return armas ? recetaDeArmas(almacen) : recetaDeArmadura(almacen);
    }

    /** Herrero de ARMAS (muelle): espada, escudo, arco y flechas. */
    @Nullable
    private Receta recetaDeArmas(Container almacen) {
        if (contar(almacen, Items.IRON_SWORD) < OBJETIVO_ESPADAS && contar(almacen, Items.IRON_INGOT) >= 2) {
            return new Receta("Forjando", "Forjo una espada de hierro",
                    List.of(new ItemStack(Items.IRON_INGOT, 2)), new ItemStack(Items.IRON_SWORD));
        }
        if (contar(almacen, Items.SHIELD) < OBJETIVO_ESCUDOS && contar(almacen, Items.IRON_INGOT) >= 1
                && contar(almacen, Items.OAK_PLANKS) >= 6) {
            return new Receta("Forjando", "Forjo un escudo",
                    List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.OAK_PLANKS, 6)),
                    new ItemStack(Items.SHIELD));
        }
        if (contar(almacen, Items.BOW) < OBJETIVO_ARCOS && contar(almacen, Items.STRING) >= 3
                && contar(almacen, Items.STICK) >= 3) {
            return new Receta("Encorando", "Armo un arco",
                    List.of(new ItemStack(Items.STRING, 3), new ItemStack(Items.STICK, 3)), new ItemStack(Items.BOW));
        }
        if (contar(almacen, Items.ARROW) < OBJETIVO_FLECHAS && contar(almacen, Items.STICK) >= 1
                && contar(almacen, Items.FEATHER) >= 1 && contar(almacen, Items.IRON_NUGGET) >= 1) {
            return new Receta("Flechando", "Hizo 4 flechas",
                    List.of(new ItemStack(Items.STICK), new ItemStack(Items.FEATHER), new ItemStack(Items.IRON_NUGGET)),
                    new ItemStack(Items.ARROW, 4));
        }
        return null;
    }

    /**
     * Herrero de HERRAMIENTAS (mesa): armadura. De <b>hierro</b> si hay lingotes de sobra (12 o más: que no se quede
     * sin material para las armas) y, si no, de <b>cuero</b> (que sale de la carne podrida).
     */
    @Nullable
    private Receta recetaDeArmadura(Container almacen) {
        int lingotes = contar(almacen, Items.IRON_INGOT);
        boolean hierro = lingotes >= 12;
        if (contar(almacen, Items.IRON_HELMET) + contar(almacen, Items.LEATHER_HELMET) < OBJETIVO_ARMADURA) {
            return pieza(almacen, hierro, "casco", Items.IRON_HELMET, Items.LEATHER_HELMET, 5);
        }
        if (contar(almacen, Items.IRON_CHESTPLATE) + contar(almacen, Items.LEATHER_CHESTPLATE) < OBJETIVO_ARMADURA) {
            return pieza(almacen, hierro, "peto", Items.IRON_CHESTPLATE, Items.LEATHER_CHESTPLATE, 8);
        }
        if (contar(almacen, Items.IRON_LEGGINGS) + contar(almacen, Items.LEATHER_LEGGINGS) < OBJETIVO_ARMADURA) {
            return pieza(almacen, hierro, "grebas", Items.IRON_LEGGINGS, Items.LEATHER_LEGGINGS, 7);
        }
        if (contar(almacen, Items.IRON_BOOTS) + contar(almacen, Items.LEATHER_BOOTS) < OBJETIVO_ARMADURA) {
            return pieza(almacen, hierro, "botas", Items.IRON_BOOTS, Items.LEATHER_BOOTS, 4);
        }
        return null;
    }

    @Nullable
    private Receta pieza(Container almacen, boolean hierro, String nombre, net.minecraft.world.item.Item deHierro,
                         net.minecraft.world.item.Item deCuero, int cuantas) {
        net.minecraft.world.item.Item material = hierro ? Items.IRON_INGOT : Items.LEATHER;
        net.minecraft.world.item.Item producto = hierro ? deHierro : deCuero;
        if (contar(almacen, material) < cuantas) {
            return null;
        }
        return new Receta("Fabricando", "Hizo unas " + nombre + " de " + (hierro ? "hierro" : "cuero"),
                List.of(new ItemStack(material, cuantas)), new ItemStack(producto));
    }

    /**
     * Chatarra que se funde en un <b>lingote de hierro</b> (una pieza = un lingote) y que <b>no se pone nadie</b>:
     * herramientas viejas. Se funden siempre, sin reserva.
     */
    private static final List<ItemStack> CHATARRA_SIEMPRE = List.of(
            new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE));

    /**
     * Chatarra de hierro y de <b>malla</b> (que también es hierro) que <b>sí es equipo de la milicia</b>: espada,
     * escudo y armadura. De estas piezas se guarda una {@link #RESERVA_DE_MILICIA reserva} en el almacén y solo se
     * funde lo que sobra.
     */
    private static final List<ItemStack> CHATARRA_CON_RESERVA = List.of(
            new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD),
            new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
            new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS),
            new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
            new ItemStack(Items.CHAINMAIL_LEGGINGS), new ItemStack(Items.CHAINMAIL_BOOTS));

    /**
     * Chatarra de ORO (lo que llevan puesto los zombis, que sueltan oro a menudo): se funde <b>entera</b> en lingotes
     * de oro, sin reserva — el oro no vale para pelear y así no acaba puesto en un guardia. Los lingotes quedan en el
     * almacén como <b>tesoro del pueblo</b> (el jugador los puede retirar cuando quiera).
     */
    private static final List<ItemStack> CHATARRA_DE_ORO = List.of(
            new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.GOLDEN_PICKAXE), new ItemStack(Items.GOLDEN_AXE),
            new ItemStack(Items.GOLDEN_SHOVEL), new ItemStack(Items.GOLDEN_HOE),
            new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.GOLDEN_CHESTPLATE),
            new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.GOLDEN_BOOTS));

    /** Armadura de CUERO vieja: se recicla en cuero (una pieza = un cuero). */
    private static final List<ItemStack> CUERO_VIEJO = List.of(
            new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE),
            new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS));

    /**
     * Cuántas piezas de equipo del pueblo <b>no se funden nunca</b>: son la reserva de la milicia. Con la aldea
     * equipándose del almacén (espada, escudo y armadura), si el herrero fundía la única espada que había, el
     * espadachín se quedaba sin arma para siempre: el herrero la convertía en lingote y volvía a fabricar otra
     * espada, en un ciclo que no dejaba nada puesto. Ahora se funde <b>solo lo que sobra</b> de esa reserva.
     */
    private static final int RESERVA_DE_MILICIA = 2;

    /** ¿Hay más piezas de las que la milicia necesita en reserva? (entonces sí se puede fundir una). */
    private static boolean haySobrante(Container almacen, net.minecraft.world.item.Item item) {
        return contar(almacen, item) > RESERVA_DE_MILICIA;
    }

    // --- utilidades ---------------------------------------------------------------------------------

    private static int contar(Container almacen, net.minecraft.world.item.Item item) {
        return VillagePantry.contar(almacen, s -> s.is(item));
    }

    /** ¿Están todos los ingredientes en el almacén? */
    private static boolean hay(Container almacen, List<ItemStack> ingredientes) {
        for (ItemStack necesario : ingredientes) {
            if (contar(almacen, necesario.getItem()) < necesario.getCount()) {
                return false;
            }
        }
        return true;
    }

    /** Gasta del inventario del aldeano esa cantidad de ese item (lo que trajo del almacén). */
    private void gastarDelInventario(net.minecraft.world.item.Item item, int cuantas) {
        int faltan = cuantas;
        for (int i = 0; i < villager.getInventory().getContainerSize() && faltan > 0; i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!s.is(item)) {
                continue;
            }
            int quita = Math.min(faltan, s.getCount());
            s.shrink(quita);
            faltan -= quita;
            if (s.isEmpty()) {
                villager.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** Cuántas unidades de ese item lleva encima el herrero. */
    private int cuantosEnInventario(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
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

    private void irAlDestino() {
        if (destino != null) {
            VillageManager.caminarHacia(villager, destino, 0.6F);
        }
    }

    /** Bloques que el herrero reconoce como su taller (para los avisos del log). */
    public static List<net.minecraft.world.level.block.Block> bloquesDelTaller() {
        List<net.minecraft.world.level.block.Block> bloques = new ArrayList<>();
        bloques.add(Blocks.GRINDSTONE);
        bloques.add(Blocks.SMITHING_TABLE);
        return bloques;
    }
}
