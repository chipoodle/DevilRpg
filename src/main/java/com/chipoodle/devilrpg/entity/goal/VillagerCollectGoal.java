package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Goal del <b>constructor recolector</b>: además de reparar la aldea, recoge del suelo las cosas del pueblo
 * (semillas, trigo, abono, troncos, tablones, cuero, lana, metales...) y las guarda en el <b>cofre del almacén</b>.
 * <p>
 * Solo recoge una <b>lista blanca</b> de cosas del pueblo: lo que tú tires (armas, comida tuya, minerales raros) se
 * queda en el suelo para que lo puedas recuperar.
 */
public class VillagerCollectGoal extends Goal {

    /** Distancia a la que recoge el objeto del suelo. */
    private static final double REACH = 2.5D;
    /** Items que lleva encima antes de ir a descargar al almacén. */
    private static final int LLEVAR_MAX = 8;
    private static final int REST_TICKS = 20;
    private static final int IDLE_REST_TICKS = 100;
    private static final int STUCK_LIMIT = 120;
    /**
     * Radio alrededor del centro donde recoge (no se va por el mundo a por cosas). Derivado del radio de la valla:
     * con el recinto agrandado (36) un radio fijo de 40 dejaba los objetos del borde del pueblo sin recoger.
     * <p>
     * Y estaba <b>corto</b> (lo reportó el jugador: "nadie recoge los materiales del suelo"): medido en su guardado,
     * el botín de las refriegas —<b>pepitas de hierro</b>, carne podrida, pan— caía a <b>43-52 bloques</b> del centro,
     * justo por fuera de los 42 de antes, así que se quedaba ahí para siempre mientras el herrero esperaba hierro
     * para forjar. Ahora llega al <b>término del pueblo</b>, el mismo que usa el leñador para talar.
     */
    private static final double RADIO = VillageGenerator.FENCE_RADIUS + 28.0D;
    /**
     * Hasta dónde se le deja andar desde donde está a por un objeto (el término del pueblo es ancho: no cruza el
     * pueblo entero, pero con el muro al radio 62 tiene que llegar al menos a media aldea desde donde esté, o el
     * botín de las refriegas —que cae a 43-52 del centro— no lo vería nunca desde la plaza).
     */
    private static final double RADIO_DE_BUSQUEDA = VillageGenerator.FENCE_RADIUS + 8.0D;
    /** Solo se recogen objetos que lleven un rato en el suelo (5 s): así no le quita a nadie lo que acaba de soltar. */
    private static final int EDAD_MINIMA = 100;
    /**
     * Si <b>no hay nada que recoger</b> y el recolector está <b>lejos de la plaza</b> (más de estos bloques del
     * centro), se vuelve a ella en vez de quedarse donde esté.
     * <p>
     * Lo reportó el jugador: <i>"¿por qué el recolector está de charla en el 3er piso sin hacer nada?"</i> — se había
     * subido al <b>desván de la taberna</b> a por unas <b>camas tiradas</b> en el suelo (las camas están en su lista),
     * se rindió con ellas (I33: 6 s sin acercarse y las aparca) y, como no le quedaba nada a su alcance, se quedó
     * plantado <b>arriba</b> alternando "recoger" y "descansar" para siempre (medido con el arnés: su goal
     * {@code VillagerCollectGoal} arrancando y parando en el desván, con tres {@code Red Bed} tiradas a 12 bloques).
     */
    private static final double RADIO_VUELTA = 24.0D;
    /**
     * A qué altura, como mucho, se sube a por una cosa: el recolector barre el pueblo <b>a la altura de la calle</b> y
     * no entra en los <b>pisos</b> (ni en los sótanos) a por nada.
     * <p>
     * Medido con el arnés: subió al <b>desván de la taberna</b> a por tres <b>camas tiradas</b> en el suelo
     * ({@code 1441/1443/1444, 131, 1432-1434}; las camas están en su lista blanca) y desde ahí <b>no alcanzaba el
     * almacén</b> (ruta degenerada de 1 nodo) ni conseguía salir: se quedaba arriba en bucle —lo que el jugador vio
     * como *"de chala en el 3er piso sin hacer nada"*—. Los pisos son de quien vive ahí: lo que se tire arriba, suyo.
     */
    private static final int ALTURA_MAXIMA = 4;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private ItemEntity objetivo;
    @Nullable
    private BlockPos destino;
    private int restTicks;
    /** Ticks SIN ACERCARSE a lo que va a por ello: andar hacia el objetivo no cuenta como estar atascado. */
    private int stuckTicks;
    /** Distancia más corta lograda en este viaje. */
    private double mejorDistancia = Double.MAX_VALUE;
    /** ¿Va de vuelta a la plaza porque no había nada que recoger? (entonces al llegar NO descarga nada) */
    private boolean volviendoALaPlaza;

    public VillagerCollectGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        // El RECOLECTOR es el aldeano SIN OFICIO (holgazán): el goal solo se le engancha a él. OJO: antes se exigía
        // la marca de OBRERO, que el recolector NUNCA tiene (los obreros se eligen entre los demás oficios), así que
        // esta comprobación dejaba al recolector sin hacer nada NUNCA: los objetos del pueblo se quedaban tirados por
        // el suelo (visto en el guardado: 155 objetos en una aldea).
        if (villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.NITWIT) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex)) {
            return false;
        }
        // De noche, a dormir: ni recoge ni se queda andando por el pueblo.
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
        // Distancia HORIZONTAL al centro (la Y del centro no cuenta: la aldea es un recinto en el plano XZ).
        if (distanciaHorizontalAlCentro() > RADIO * RADIO) {
            return false;
        }
        // Con las manos llenas, al almacén (a su punto de apoyo: el cofre es sólido y no se navega hacia él).
        if (cuantosLleva() >= LLEVAR_MAX) {
            BlockPos almacen = VillageStorage.puntoDeApoyo(level, center);
            if (VillageManager.esPuntoFallido(villager, almacen)) {
                // EL ALMACÉN ESTÁ APARCADO (no llegó hace poco: ver `tick`): no se vuelve a mandar allí, que era lo que
                // le hacía OSCILAR entre el almacén y la plaza en la escalera de la taberna sin bajar nunca. Se queda
                // por el pueblo (y si está metido en un piso, se vuelve a la plaza).
                if (fueraDeSuSitio(level)) {
                    destino = new BlockPos(center.getX(),
                            VillageGenerator.cotaDeLaPlaza(level, center), center.getZ());
                    volviendoALaPlaza = true;
                    return true;
                }
                restTicks = IDLE_REST_TICKS;
                return false;
            }
            destino = almacen;
            objetivo = null;
            volviendoALaPlaza = false;
            return true;
        }
        objetivo = buscarObjeto(level);
        if (objetivo == null) {
            restTicks = IDLE_REST_TICKS;
            // NADA QUE RECOGER: si está metido en una casa (el desván de la taberna) o lejos del pueblo, se VUELVE A
            // LA PLAZA en vez de quedarse plantado donde esté (ver {@link #RADIO_VUELTA}).
            if (fueraDeSuSitio(level)) {
                destino = new BlockPos(center.getX(), VillageGenerator.cotaDeLaPlaza(level, center), center.getZ());
                volviendoALaPlaza = true;
                return true;
            }
            return false;
        }
        destino = null;
        volviendoALaPlaza = false;
        return true;
    }

    /**
     * ¿Está <b>donde no toca</b>: metido en un piso que no es la calle (por encima del forjado de una casa) o lejos de
     * la plaza? Es lo que decide si, al no tener nada que recoger, se le manda de vuelta al pueblo.
     */
    private boolean fueraDeSuSitio(ServerLevel level) {
        if (Math.abs(villager.getY() - VillageGenerator.cotaDeLaPlaza(level, center)) > 4.0D) {
            return true; // el desván de la taberna (o el sótano de cualquier casa)
        }
        return distanciaHorizontalAlCentro() > RADIO_VUELTA * RADIO_VUELTA;
    }

    @Override
    public void start() {
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        ir();
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.isBaby() || VillageManager.estaDescansando(villager)) {
            return false;
        }
        if (stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el sitio al que no llegó (lo que iba a recoger, o el almacén) se
            // apunta para no volver a elegir EL MISMO en bucle, que es lo que dejaba al aldeano empujando el mismo
            // obstáculo para siempre.
            VillageManager.marcarPuntoFallido(villager, objetivo != null ? objetivo.blockPosition() : destino);
            return false;
        }
        if (objetivo != null) {
            return objetivo.isAlive();
        }
        return destino != null;
    }

    @Override
    public void tick() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (objetivo != null) {
            if (!objetivo.isAlive()) {
                objetivo = null;
                return;
            }
            BlockPos p = objetivo.blockPosition();
            villager.getLookControl().setLookAt(objetivo);
            double distancia = Math.sqrt(villager.distanceToSqr(objetivo));
            if (distancia > REACH) {
                // Se le manda POR EL CEREBRO, en cada tick: si se navega a mano, el cerebro del aldeano lo manda a
                // otra parte y se va sin recogerlo.
                VillageManager.caminarHacia(villager, p, 0.6F);
                // Solo cuenta como atasco NO ACERCARSE (contar cada tick lo mandaba a empezar de cero a los 6 s).
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
            VillageManager.ponerActividad(villager, "Recogiendo");
            ItemStack stack = objetivo.getItem().copy();
            int antes = stack.getCount();
            ItemStack resto = guardarEnInventario(stack);
            int cogidos = antes - resto.getCount();
            if (cogidos <= 0) {
                objetivo = null; // no le cabe: lo deja en el suelo
                return;
            }
            objetivo.getItem().shrink(cogidos);
            level.playSound(null, p, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.0F);
            if (objetivo.getItem().isEmpty()) {
                objetivo.discard();
            }
            objetivo = null;
            restTicks = REST_TICKS;
            return;
        }
        if (destino != null) {
            villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
            double distancia = Math.sqrt(villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D,
                    destino.getZ() + 0.5D));
            if (distancia > VillageStorage.ALCANCE_ALMACEN) {
                VillageManager.caminarHacia(villager, destino, 0.6F);
                VillageManager.ponerActividad(villager, volviendoALaPlaza ? "Volviendo a la plaza"
                        : "Yendo al almacen");
                if (distancia < mejorDistancia - 0.5D) {
                    mejorDistancia = distancia;
                    stuckTicks = 0;
                } else if (++stuckTicks >= STUCK_LIMIT) {
                    // NO LLEGA AL ALMACÉN: se apunta el sitio (I33) y se VUELVE A LA PLAZA. Medido con el arnés: desde
                    // el desván de la taberna el aldeano calcula ruta a la plaza (17 nodos) y al hueco del desván (14),
                    // pero al almacén le sale una ruta DEGENERADA de 1 nodo, o sea INALCANZABLE: con las manos llenas
                    // (lleva 8 cosas) se quedaba clavado arriba alternando "yendo al almacén" y descansar para siempre
                    // —el jugador lo vio "de charla en el 3er piso sin hacer nada"—. A la plaza sí llega, y desde la
                    // plaza el almacén sí se alcanza, así que al siguiente intento (pasados los 5 min del aparcado)
                    // entrega lo que lleva.
                    VillageManager.marcarPuntoFallido(villager, destino);
                    destino = new BlockPos(center.getX(),
                            VillageGenerator.cotaDeLaPlaza(level, center), center.getZ());
                    volviendoALaPlaza = true;
                    mejorDistancia = Double.MAX_VALUE;
                    stuckTicks = 0;
                }
                return;
            }
            VillageManager.parar(villager);
            destino = null;
            restTicks = REST_TICKS;
            if (volviendoALaPlaza) {
                volviendoALaPlaza = false; // a la plaza solo se va a eso: a estar donde tiene que estar
                return;                    // (no lleva nada que descargar)
            }
            VillageManager.ponerActividad(villager, "Guardando en el almacen");
            descargar(level);
        }
    }

    @Override
    public void stop() {
        objetivo = null;
        destino = null;
        volviendoALaPlaza = false;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    /**
     * Distancia <b>horizontal</b> (en el plano XZ) al centro de la aldea, al cuadrado. La Y no se mira a propósito:
     * el centro puede traer cualquier Y (la del spawn del jugador) y mirarla dejaba al aldeano fuera de su propio
     * pueblo.
     */
    private double distanciaHorizontalAlCentro() {
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        return dx * dx + dz * dz;
    }

    private void ir() {
        if (objetivo != null) {
            VillageManager.caminarHacia(villager, objetivo.blockPosition(), 0.6F);
            return;
        }
        if (destino != null) {
            VillageManager.caminarHacia(villager, destino, 0.6F);
        }
    }

    /** Deja en el almacén todo lo que lleve de la lista blanca. */
    private void descargar(ServerLevel level) {
        int guardados = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty() || !esDelPueblo(s)) {
                continue;
            }
            int antes = s.getCount();
            ItemStack resto = VillageStorage.guardar(level, center, s.copy());
            guardados += antes - resto.getCount();
            villager.getInventory().setItem(i, resto);
        }
        if (guardados > 0) {
            // Lo que acaba de hacer, en la cabeza y en el log: antes el recolector no decía nada y no había forma de
            // saber si estaba trabajando.
            VillageManager.ponerSuceso(villager, "Guardo " + guardados + " cosa(s) en el almacen");
            com.chipoodle.devilrpg.DevilRpg.LOGGER
                    .info("[Village] El recolector guardo {} cosa(s) en el almacen", guardados);
        }
    }

    @Nullable
    private ItemEntity buscarObjeto(ServerLevel level) {
        ItemEntity mejor = null;
        // Hasta dónde se le deja andar DESDE DONDE ESTÁ a por un objeto del término del pueblo (el AABB de arriba es
        // alrededor del centro; sin este tope, con el radio nuevo se iría de una punta a otra del término por una
        // pepita). Antes eran 24 fijos, que con el radio viejo de 42 dejaba fuera la mitad del pueblo.
        double mejorDist = RADIO_DE_BUSQUEDA * RADIO_DE_BUSQUEDA;
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(RADIO))) {
            if (!item.isAlive() || item.getItem().isEmpty()) {
                continue;
            }
            if (item.tickCount < EDAD_MINIMA) {
                continue; // recién soltado: se le deja un margen a quien lo soltó
            }
            if (!esDelPueblo(item.getItem())) {
                continue;
            }
            // NO SUBE A LOS PISOS (ni baja a los sótanos): el recolector barre el pueblo a la altura de la calle. Los
            // pisos son de quien vive ahí —el jugador se está haciendo su base en el desván de la taberna— y, además,
            // de ahí arriba no se puede entregar (ver `ALTURA_MAXIMA`).
            if (item.getY() > cota + ALTURA_MAXIMA || item.getY() < cota - 6) {
                continue;
            }
            if (VillageManager.esPuntoFallido(villager, item.blockPosition())) {
                continue; // a ese objeto no llegó hace poco: se prueba con el siguiente (I33)
            }
            double d = item.distanceToSqr(villager);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = item;
            }
        }
        return mejor;
    }

    /** Lista blanca: cosas del pueblo. Lo demás (tu equipo, tus minerales) no se toca. */
    public static boolean esDelPueblo(ItemStack s) {
        return s.is(Items.WHEAT) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS) || s.is(Items.BONE_MEAL)
                || s.is(Items.STICK) || s.is(Items.STRING) || s.is(Items.LEATHER) || s.is(Items.FEATHER)
                || s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.IRON_INGOT) || s.is(Items.COPPER_INGOT)
                || s.is(Items.GOLD_INGOT) || s.is(Items.CARROT) || s.is(Items.POTATO)
                || s.is(Blocks.OAK_LOG.asItem()) || s.is(Blocks.OAK_PLANKS.asItem())
                || s.is(Blocks.OAK_SAPLING.asItem()) || s.getDescriptionId().contains("sapling")
                // El PROPÁGULO del mangle se llama así (no "sapling"), pero es la semilla de un árbol y el leñador
                // la planta: si no entra aquí, se queda tirada en el suelo del manglar para siempre.
                || s.is(Items.MANGROVE_PROPAGULE)
                || s.getDescriptionId().contains("_log") || s.getDescriptionId().contains("_wool")
                || s.getDescriptionId().contains("_seeds")
                // LAS PIEZAS DEL PROPIO PUEBLO: cuando un asedio (o un aldeano con prisa) rompe una puerta, una
                // valla, una losa o una cama, la pieza cae al suelo y se quedaba ahí para siempre: no es comida ni
                // material de nadie, así que nadie la recogía. Son del pueblo y el pueblo las recupera (al almacén).
                || s.is(Blocks.OAK_DOOR.asItem()) || s.is(Blocks.DARK_OAK_DOOR.asItem())
                || s.is(Blocks.OAK_FENCE.asItem()) || s.is(Blocks.OAK_FENCE_GATE.asItem())
                || s.is(Blocks.OAK_SLAB.asItem()) || s.is(Blocks.OAK_STAIRS.asItem())
                || s.is(Blocks.RED_BED.asItem()) || s.is(Blocks.TORCH.asItem()) || s.is(Blocks.LANTERN.asItem())
                // MATERIALES DEL TALLER (lo que forjan los herreros): chips de metal (pepitas) y carne de zombie
                // podrida (de ahí sale el cuero).
                || s.is(Items.IRON_NUGGET) || s.is(Items.ROTTEN_FLESH)
                // LO DEL CORRAL ANEXO (etapa D): carne (cruda y cocinada), huevos, lana, plumas y cuero de los
                // animales del ganadero. El ganadero lo baja al almacén y el granjero pasa la carne a la despensa.
                || VillagePantry.esCarneCruda(s) || VillagePantry.esCarneCocida(s) || s.is(Items.EGG)
                // EQUIPO QUE SUELTAN LOS ENEMIGOS: ver `esEquipoDeEnemigo`.
                || esEquipoDeEnemigo(s);
    }

    /** Materiales de armadura que el pueblo <b>recicla</b>: los que aparecen puestos en los zombis. */
    private static final List<Holder<ArmorMaterial>> MATERIALES_DE_SAQUEO = List.of(
            ArmorMaterials.LEATHER, ArmorMaterials.CHAIN, ArmorMaterials.IRON, ArmorMaterials.GOLD);

    /** Armas y herramientas de enemigo que el pueblo recicla (el hierro y el oro que sueltan los zombis). */
    private static final List<Item> ARMAS_DE_SAQUEO = List.of(
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE,
            Items.SHIELD);

    /**
     * <b>Equipo que sueltan los enemigos</b> y que el pueblo recoge para el almacén: <b>cualquier pieza de armadura</b>
     * de cuero, malla, hierro u oro (son las que llevan puestos los zombis), las <b>armas y herramientas</b> de hierro
     * u oro, y los <b>arcos y flechas</b> de los esqueletos (que la milicia aprovecha tal cual).
     * <p>
     * Antes esto era una lista solo de HIERRO, así que la armadura de <b>cuero, malla y oro</b> que sueltan los zombis
     * se quedaba tirada en el suelo del pueblo para siempre (lo reportó el jugador con una captura).
     * <p>
     * Lo que <b>NO</b> se recoge a propósito: armadura ni armas de <b>diamante o netherite</b> (eso es del jugador, no
     * del pueblo) ni ninguna otra cosa que se deje por el suelo.
     */
    public static boolean esEquipoDeEnemigo(ItemStack s) {
        if (s.getItem() instanceof ArmorItem armadura) {
            return MATERIALES_DE_SAQUEO.contains(armadura.getMaterial());
        }
        return ARMAS_DE_SAQUEO.contains(s.getItem()) || s.is(Items.BOW) || s.is(Items.ARROW);
    }

    private int cuantosLleva() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!s.isEmpty() && esDelPueblo(s)) {
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
}
