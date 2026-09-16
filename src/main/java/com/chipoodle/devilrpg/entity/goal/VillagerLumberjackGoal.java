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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * <b>Leñador / reforestador</b> de la aldea (etapa B): <b>tala árboles de verdad</b> alrededor del pueblo y <b>los
 * replanta</b>, y lleva la madera al <b>almacén</b> (que es de donde salen los tablones, los palos, los arcos y las
 * flechas de los herreros).
 * <p>
 * Es el <b>recolector</b> (el aldeano sin oficio) el que hace también esto: así el pueblo no gasta un puesto más (los
 * puestos fijos ya son <b>siete</b>: granjero, los dos herreros, clérigo, recolector, ganadero y cocinero, y de los
 * sobrantes sale la milicia). Va a <b>prioridad 6</b>, por debajo de su goal de recoger (5): primero recoge lo que hay
 * por el suelo y, cuando no hay nada que recoger, se va al monte.
 * <p>
 * <b>Repuebla el monte de verdad</b>: cada árbol que tala lo replanta <b>en su sitio</b> y con la <b>misma
 * especie</b>; y si se queda sin semilla en la mano, <b>se apunta el hueco</b> para volver con la primera que
 * consiga. Además, cuando lleva una <b>pila de semillas</b> encima (o ya no ve árboles), se va a
 * <b>plantarlas repartidas</b>: un árbol por sitio y con {@link #DISTANCIA_ENTRE_ARBOLES} de separación, empezando
 * por los huecos apuntados. Las semillas que encuentra (las que sueltan las hojas y recoge el recolector, las que
 * deja el jugador en el almacén) terminan en el monte, no apiladas en un cofre.
 * <p>
 * <b>Solo tala árboles DE VERDAD y fuera de la valla</b>, que es lo importante: el muro de la aldea y las casas son de
 * troncos, así que dentro del recinto no se toca nada. Un tronco cuenta como árbol si su base está sobre tierra y
 * tiene <b>hojas cerca</b> y otro tronco encima (un poste suelto no).
 */
public class VillagerLumberjackGoal extends Goal {

    /** Distancia a la que ya alcanza el tronco para dar el hachazo (y al hueco donde planta). */
    private static final double REACH = 3.5D;
    /** Ticks de hachazo antes de que el árbol caiga (y de faena antes de que la semilla quede plantada). */
    private static final int WORK_TICKS = 25;
    private static final int REST_TICKS = 10;
    /** Sin árboles ni semillas a la vista: a esperar (buscar árboles recorre muchas columnas, no se hace por tick). */
    private static final int IDLE_REST_TICKS = 120;
    /** Si no logra acercarse en este tiempo, abandona ese árbol. */
    private static final int STUCK_LIMIT = 160;
    /** Troncos que lleva encima antes de ir al almacén a descargar. */
    private static final int LLEVAR_TRONCOS = 12;
    /** Semillas de árbol que se lleva del almacén para replantar. */
    private static final int SEMILLAS_POR_VIAJE = 16;
    /**
     * Semillas que aguanta en la mano antes de ir a <b>plantarlas</b>: el leñador no es un vivero andante. En cuanto
     * llega a esta pila se va a repartirlas por el monte (y de paso no se le llena el inventario de semillas).
     */
    private static final int SEMILLAS_PARA_PLANTAR = 8;
    /** Huecos que recuerda (troncos que taló y no pudo replantar en el momento): a esos vuelve con la primera semilla. */
    private static final int MAX_PENDIENTES = 8;
    /**
     * Separación mínima entre árboles: es lo que hace que las semillas se <b>repartan</b> por el monte en vez de
     * apelotonarse en un rincón (y que el árbol nuevo tenga sitio para crecer).
     */
    private static final double DISTANCIA_ENTRE_ARBOLES = 5.0D;
    /** Radio de búsqueda de un claro donde repoblar (alrededor del leñador, como la tala). */
    private static final int RADIO_CLARO = 28;
    /**
     * Clavos a los que se les comprueba la separación: esa comprobación mira un cubo de bloques y se hace con los
     * más cercanos, no con las 800 columnas del barrido.
     */
    private static final int CANDIDATOS_A_COMPROBAR = 8;
    /** Ticks entre barridos de "¿hay algún claro donde plantar?" (también mira muchas columnas). */
    private static final int BUSCAR_CLARO_COOLDOWN = 40;
    /** Margen alrededor del corral anexo donde NO se planta: una rama no tiene que caerle al ganadero encima. */
    private static final int MARGEN_ANEXO = 3;
    /** Radio de búsqueda de árboles ALREDEDOR DEL LEÑADOR, y radio mínimo (fuera de la valla, que es de troncos). */
    private static final int RADIO_BUSQUEDA = 32;
    private static final double RADIO_MINIMO = VillageGenerator.FENCE_RADIUS + 3.0D;
    /** Hasta dónde se le deja alejar del pueblo (si no, se pierde por el mundo talando). */
    private static final double RADIO_MAXIMO = VillageGenerator.FENCE_RADIUS + 40.0D;
    /** Tronco más alto que tala de una vez (una selva puede tener árboles altísimos). */
    private static final int ALTURA_MAX = 16;
    /** Velocidad al ir al árbol (y al almacén). */
    private static final float VELOCIDAD = 0.6F;

    private enum Fase { TALAR, PLANTAR, ENTREGAR }

    /**
     * Un <b>hueco que se quedó sin replantar</b>: dónde estaba el árbol que se taló y de qué <b>especie</b> era (para
     * poner la misma). Es el "mismo sitio donde estaba el árbol" al que vuelve cuando consigue una semilla.
     */
    private record Hueco(BlockPos pos, @Nullable Item semilla) {
    }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Fase fase = Fase.TALAR;
    /** Troncos que taló y se quedaron sin replantar (sin semilla a mano): a esos vuelve luego. */
    private final List<Hueco> pendientes = new ArrayList<>();
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    private int claroCooldown;

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
        if (claroCooldown > 0) {
            claroCooldown--;
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
        BlockPos tronco = buscarArbol(level);
        int troncos = troncosEnMano();
        // Con la mochila llena (o con madera y sin árboles a la vista) se va a descargar al almacén.
        if (troncos >= LLEVAR_TRONCOS || (tronco == null && troncos > 0)) {
            fase = Fase.ENTREGAR;
            target = VillageStorage.puntoDeApoyo(level, center);
            return target != null;
        }
        // PLANTAR: repartir las semillas que lleva encima. Dos motivos para ir a plantar: que lleve una PILA (no es un
        // vivero andante) o que no haya árboles a la vista (entonces el monte se queda pelado y hay que reponerlo).
        // Primero los huecos que ya conoce —los troncos que taló y se quedaron sin replantar, que es el mismo sitio
        // donde estaba el árbol— porque mirarlos es barato; el barrido de un claro del monte se hace de vez en cuando.
        int semillas = semillasEnMano();
        if (semillas >= SEMILLAS_PARA_PLANTAR || (tronco == null && semillas > 0)) {
            BlockPos hueco = primerPendiente(level);
            if (hueco == null && claroCooldown <= 0) {
                hueco = buscarClaro(level);
                claroCooldown = BUSCAR_CLARO_COOLDOWN;
            }
            if (hueco != null) {
                fase = Fase.PLANTAR;
                target = hueco;
                return true;
            }
        }
        if (tronco != null) {
            fase = Fase.TALAR;
            target = tronco;
            return true;
        }
        // Sin árboles ni semillas en la mano: si el almacén tiene semillas, va a por ellas (para replantar).
        if (semillas == 0 && haySemillasEnElAlmacen(level)) {
            fase = Fase.ENTREGAR;
            target = VillageStorage.puntoDeApoyo(level, center);
            return target != null;
        }
        restTicks = IDLE_REST_TICKS;
        return false;
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
            VillageManager.ponerActividad(villager, switch (fase) {
                case TALAR -> "Yendo al arbol";
                case PLANTAR -> "Yendo a plantar";
                case ENTREGAR -> "Llevando la madera";
            });
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, switch (fase) {
                case TALAR -> "Talando";
                case PLANTAR -> "Plantando";
                case ENTREGAR -> "Guardando la madera";
            });
            return;
        }
        workTicks = 0;
        switch (fase) {
            case TALAR -> talar(level);
            case PLANTAR -> plantar(level);
            case ENTREGAR -> entregar(level);
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
     * base está sobre tierra, <b>replanta</b> ahí mismo una semilla <b>de la misma especie</b>; y si no tiene ninguna a
     * mano, <b>apunta el hueco</b> para volver con la primera que consiga (ver {@link #pendientes}).
     */
    private void talar(ServerLevel level) {
        if (target == null) {
            return;
        }
        BlockState troncoBase = level.getBlockState(target);
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
            BlockPos hueco = target;
            Item misma = semillaDeTronco(troncoBase);
            if (!replantar(level, hueco, misma)) {
                apuntarHueco(hueco, misma); // sin semilla a mano: el sitio queda pendiente, no se pierde
            }
        }
        if (talados > 0) {
            VillageManager.ponerSuceso(villager, "Talo un arbol (" + talados + ")");
            DevilRpg.LOGGER.info("[Village] El lenador: talo {} tronco(s)", talados);
        }
    }

    /**
     * Planta UNA semilla en el hueco al que fue: la del <b>mismo árbol</b> que hubo ahí si es un hueco apuntado, y si
     * no la primera que tenga. Es donde las semillas se <b>reparten</b>: un árbol por sitio (los claros se eligen con
     * {@link #estaDespejado}).
     */
    private void plantar(ServerLevel level) {
        if (target == null) {
            return;
        }
        BlockPos hueco = target;
        if (!esHuecoDeTierra(level, hueco)) {
            pendientes.removeIf(h -> h.pos().equals(hueco)); // el jugador construyó ahí o creció otra cosa
            return;
        }
        ItemStack semilla = sacarSemilla(semillaDelHueco(hueco));
        if (!(semilla.getItem() instanceof BlockItem blockItem)) {
            return; // no le quedan semillas de árbol (otro goal las habrá usado)
        }
        plantarSemilla(level, hueco, blockItem);
        pendientes.removeIf(h -> h.pos().equals(hueco));
        VillageManager.ponerSuceso(villager, "Planto un arbol");
        DevilRpg.LOGGER.info("[Village] El lenador: planto un arbol en {} (le quedan {} semillas)", hueco,
                semillasEnMano());
    }

    /**
     * Planta una semilla en la base del árbol que acaba de talar (la de la misma especie, si la tiene).
     *
     * @return {@code false} si no pudo (no tenía semilla a mano): el sitio queda <b>pendiente</b> y vuelve luego
     */
    private boolean replantar(ServerLevel level, BlockPos base, @Nullable Item preferida) {
        if (!esHuecoDeTierra(level, base)) {
            return true; // ya no hay hueco que replantar: no se apunta nada
        }
        ItemStack semilla = sacarSemilla(preferida);
        if (!(semilla.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        plantarSemilla(level, base, blockItem);
        VillageManager.ponerSuceso(villager, "Replanto el arbol");
        return true;
    }

    /** Pone el bloque de la semilla y su sonido. */
    private static void plantarSemilla(ServerLevel level, BlockPos pos, BlockItem semilla) {
        BlockState plantado = semilla.getBlock().defaultBlockState();
        level.setBlock(pos, plantado, Block.UPDATE_ALL);
        level.playSound(null, pos, plantado.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
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
                    new ItemStack(Items.ACACIA_SAPLING), new ItemStack(Items.DARK_OAK_SAPLING),
                    new ItemStack(Items.CHERRY_SAPLING), new ItemStack(Items.MANGROVE_PROPAGULE))) {
                if (VillagePantry.sacar(almacen, s -> ItemStack.isSameItem(s, semilla), SEMILLAS_POR_VIAJE) > 0) {
                    guardarEnInventario(semilla.copyWithCount(SEMILLAS_POR_VIAJE));
                    break;
                }
            }
        }
    }

    // --- buscar árboles y claros --------------------------------------------------------------------

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
                // lint:ok I1 porque aqui `base` es el tronco de un arbol que existe, no el centro ni la base de la
                // aldea: la distancia al arbol SI es en 3D (un tronco de la ladera esta mas abajo que el pueblo).
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
     * Un <b>claro del monte</b> donde poner una semilla nueva: suelo de tierra, <b>cielo abierto</b> (sin cielo el
     * sapling no crece) y <b>separado</b> de cualquier árbol o semilla ({@link #estaDespejado}). Se busca alrededor
     * del leñador, fuera de la valla (igual que la tala) y sin meterse en el corral de los animales.
     */
    @Nullable
    private BlockPos buscarClaro(ServerLevel level) {
        List<BlockPos> candidatos = new ArrayList<>();
        BlockPos base = villager.blockPosition();
        for (int dx = -RADIO_CLARO; dx <= RADIO_CLARO; dx += 2) {
            for (int dz = -RADIO_CLARO; dz <= RADIO_CLARO; dz += 2) {
                int x = base.getX() + dx;
                int z = base.getZ() + dz;
                double dCentroX = x - center.getX();
                double dCentroZ = z - center.getZ();
                double dCentro = Math.sqrt(dCentroX * dCentroX + dCentroZ * dCentroZ);
                if (dCentro < RADIO_MINIMO || dCentro > RADIO_MAXIMO) {
                    continue; // dentro de la valla no se planta (como no se tala) y muy lejos tampoco
                }
                if (enElAnexo(x, z)) {
                    continue; // el corral de los animales: no se le planta un árbol encima
                }
                BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (!esHuecoDeTierra(level, p) || !level.canSeeSky(p)) {
                    continue;
                }
                candidatos.add(p.immutable());
            }
        }
        // El más cercano primero (menos caminata) y la separación se comprueba solo a los primeros: esa comprobación
        // mira un cubo de bloques alrededor y no se le hace a 800 columnas.
        candidatos.sort(Comparator.comparingDouble(p -> villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D,
                p.getZ() + 0.5D)));
        for (int i = 0; i < Math.min(candidatos.size(), CANDIDATOS_A_COMPROBAR); i++) {
            if (estaDespejado(level, candidatos.get(i))) {
                return candidatos.get(i);
            }
        }
        return null;
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

    // --- los huecos pendientes (el mismo sitio donde estaba el árbol) --------------------------------

    /**
     * Apunta un hueco que se quedó sin replantar (el tronco se taló y no había semilla a mano). Se guardan los
     * últimos {@link #MAX_PENDIENTES}: no es un censo del monte, es "por dónde iba".
     */
    private void apuntarHueco(BlockPos pos, @Nullable Item semilla) {
        if (pendientes.size() >= MAX_PENDIENTES) {
            pendientes.remove(0); // el más viejo deja de ser prioritario
        }
        pendientes.add(new Hueco(pos.immutable(), semilla));
    }

    /**
     * El primer hueco apuntado que <b>todavía</b> se puede replantar, o {@code null}. Los que ya no valen (el jugador
     * construyó encima, creció otra cosa) se tiran: si no, el leñador volvería a ellos para siempre.
     */
    @Nullable
    private BlockPos primerPendiente(ServerLevel level) {
        while (!pendientes.isEmpty()) {
            BlockPos p = pendientes.get(0).pos();
            if (esHuecoDeTierra(level, p)) {
                return p;
            }
            pendientes.remove(0);
        }
        return null;
    }

    /** La semilla con la que hay que replantar ese hueco (la de la especie que había), o {@code null} si es un claro. */
    @Nullable
    private Item semillaDelHueco(BlockPos p) {
        for (Hueco h : pendientes) {
            if (h.pos().equals(p)) {
                return h.semilla();
            }
        }
        return null;
    }

    // --- utilidades ---------------------------------------------------------------------------------

    private static boolean esTierra(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }

    /**
     * ¿Ahí se puede <b>plantar</b>? Aire libre en el hueco y encima, y <b>tierra de verdad</b> debajo: el tag
     * {@code DIRT} del juego es exactamente lo que acepta un sapling, así que deja fuera la <b>arena</b> de la playa
     * (donde el juego no deja plantarlo y la semilla saltaría) y el <b>camino de tierra</b> de la aldea.
     */
    private static boolean esHuecoDeTierra(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                && level.getBlockState(p.below()).is(BlockTags.DIRT);
    }

    /**
     * ¿Ese hueco está <b>despejado</b>? Ni troncos, ni hojas, ni semillas a menos de {@link #DISTANCIA_ENTRE_ARBOLES}:
     * es lo que <b>reparte</b> las semillas por el monte en vez de amontonarlas (y lo que le deja sitio al árbol
     * nuevo para crecer).
     */
    private static boolean estaDespejado(ServerLevel level, BlockPos p) {
        int r = (int) DISTANCIA_ENTRE_ARBOLES;
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-r, 0, -r), p.offset(r, 7, r))) {
            BlockState s = level.getBlockState(q);
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS)) {
                return false;
            }
        }
        return true;
    }

    /** ¿Ese punto (X/Z) es el corral anexo (o su margen)? Ahí no se planta. */
    private boolean enElAnexo(int x, int z) {
        BlockPos anexo = VillageGenerator.baseDeAnexo(center);
        return Math.abs(x - anexo.getX()) <= VillageGenerator.ANEXO_RADIO + MARGEN_ANEXO
                && Math.abs(z - anexo.getZ()) <= VillageGenerator.ANEXO_RADIO + MARGEN_ANEXO;
    }

    /** La semilla que corresponde a ese tronco (para repoblar con la <b>misma especie</b> que había). */
    @Nullable
    private static Item semillaDeTronco(BlockState tronco) {
        if (tronco.is(Blocks.OAK_LOG)) return Items.OAK_SAPLING;
        if (tronco.is(Blocks.SPRUCE_LOG)) return Items.SPRUCE_SAPLING;
        if (tronco.is(Blocks.BIRCH_LOG)) return Items.BIRCH_SAPLING;
        if (tronco.is(Blocks.JUNGLE_LOG)) return Items.JUNGLE_SAPLING;
        if (tronco.is(Blocks.ACACIA_LOG)) return Items.ACACIA_SAPLING;
        if (tronco.is(Blocks.DARK_OAK_LOG)) return Items.DARK_OAK_SAPLING;
        if (tronco.is(Blocks.CHERRY_LOG)) return Items.CHERRY_SAPLING;
        if (tronco.is(Blocks.MANGROVE_LOG)) return Items.MANGROVE_PROPAGULE;
        return null; // un tronco que no conocemos: cualquier semilla vale
    }

    /** ¿Esa semilla es de árbol? (las de árbol se plantan al talar; las demás no). */
    private static boolean esSemillaDeArbol(ItemStack s) {
        return s.is(Items.OAK_SAPLING) || s.is(Items.SPRUCE_SAPLING) || s.is(Items.BIRCH_SAPLING)
                || s.is(Items.JUNGLE_SAPLING) || s.is(Items.ACACIA_SAPLING) || s.is(Items.DARK_OAK_SAPLING)
                || s.is(Items.CHERRY_SAPLING) || s.is(Items.MANGROVE_PROPAGULE);
    }

    /** Saca UNA semilla de árbol del inventario: la de la especie pedida si la tiene, y si no la primera que haya. */
    private ItemStack sacarSemilla(@Nullable Item preferida) {
        int alternativa = -1;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!esSemillaDeArbol(s)) {
                continue;
            }
            if (preferida != null && s.is(preferida)) {
                return sacarUnaDelSlot(i);
            }
            if (alternativa < 0) {
                alternativa = i;
            }
        }
        return alternativa < 0 ? ItemStack.EMPTY : sacarUnaDelSlot(alternativa);
    }

    /** Saca UNA unidad del hueco {@code slot} del inventario (y lo vacía si se queda sin nada). */
    private ItemStack sacarUnaDelSlot(int slot) {
        ItemStack s = villager.getInventory().getItem(slot);
        ItemStack una = s.copyWithCount(1);
        s.shrink(1);
        if (s.isEmpty()) {
            villager.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        return una;
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
