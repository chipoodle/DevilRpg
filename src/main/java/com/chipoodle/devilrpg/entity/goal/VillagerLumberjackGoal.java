package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageSavedData;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * <b>Solo tala árboles DE VERDAD</b>, que es lo importante: el muro de la aldea y las casas son de troncos, así que
 * nunca se corta nada de lo construido. Un tronco cuenta como árbol si su base está sobre tierra y tiene <b>hojas
 * cerca</b> y otro tronco encima (un poste suelto no) y, <b>dentro del recinto</b>, además se pregunta al
 * <b>plano</b> de la aldea ({@link #esParteDeLaAldea}): lo que puso el pueblo no se toca. Con eso el leñador
 * <b>despeja los árboles del monte que quedaron dentro de la muralla</b> —lo pidió el jugador: medido en su guardado
 * había <b>137</b> árboles sueltos dentro de la valla, y el pueblo no los tocaba—, tala y replanta en la
 * <b>arboleda del pueblo</b> ({@link VillageGenerator#enLaArboleda}, el hueco de césped donde la aldea planta
 * <b>sus</b> árboles: es la madera de una aldea que nace <b>sin bosque</b>) y repuebla el monte <b>de fuera</b>. Lo
 * de dentro se tala pero <b>no</b> se replanta: la aldea se despeja.
 * <p>
 * Mientras la arboleda no tenga ni un árbol, el leñador <b>abona sus plantones</b> con la harina de huesos del
 * compostero del granjero, que es lo que hace que una islita tenga madera en minutos.
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
    private static final int RADIO_CLARO = 40;
    /**
     * Clavos a los que se les comprueba la separación: esa comprobación mira un cubo de bloques y se hace con los
     * más cercanos, no con las 800 columnas del barrido.
     */
    private static final int CANDIDATOS_A_COMPROBAR = 8;
    /** Ticks entre los barridos caros: buscar un claro donde plantar y mirar cómo va la arboleda del pueblo. */
    private static final int BARRIDO_COOLDOWN = 40;
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

    private enum Fase { TALAR, PLANTAR, ABONAR, ENTREGAR }

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
    private int barridoCooldown;
    /** Cota de la aldea (para los plantones de la arboleda): se pregunta UNA vez, no en cada tick. */
    private int nivelAldea = Integer.MIN_VALUE;
    /**
     * Las posiciones del <b>plano</b> de la aldea (lo que construyó el pueblo) en un {@code Set}, para saber si un
     * tronco es del muro/casa o un árbol del monte. {@code null} = todavía no se ha copiado (ver
     * {@link #esParteDeLaAldea}).
     */
    @Nullable
    private Set<Long> planoDeLaAldea;
    private long planoConsultadoEn;
    /** Cada cuánto se vuelve a copiar el plano (por si el obrero reconstruye algo). */
    private static final long PLANO_REFRESCO_TICKS = 200L;

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
        if (barridoCooldown > 0) {
            barridoCooldown--;
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
            if (hueco == null) {
                hueco = huecoDeLaArboleda(level); // la arboleda del pueblo, primero (es su madera)
            }
            if (hueco == null && barridoCooldown <= 0) {
                hueco = buscarClaro(level);
                barridoCooldown = BARRIDO_COOLDOWN;
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
        // ABONAR LA ARBOLEDA DEL PUEBLO: mientras no tenga NI UN árbol y el pueblo tenga harina de huesos (la del
        // compostero del granjero), el leñador la abona. Es lo que hace que una aldea sin bosque —una islita— tenga
        // madera en minutos en vez de esperar a que los plantones crezcan solos. En cuanto crece el primer árbol deja
        // de gastar harina: a partir de ahí la arboleda se sostiene sola (se tala y se replanta). El barrido es caro
        // (mira la arboleda entera), así que va con el mismo cooldown que la búsqueda de claro.
        if (barridoCooldown <= 0) {
            barridoCooldown = BARRIDO_COOLDOWN;
            if (necesitaAbonoLaArboleda(level)) {
                BlockPos planton = plantonDeLaArboledaMasCercano(level);
                if (planton != null && contenedorConHarina(level) != null) {
                    fase = Fase.ABONAR;
                    target = planton;
                    return true;
                }
            }
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
                case ABONAR -> "Yendo a la arboleda";
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
                case ABONAR -> "Abonando la arboleda";
                case ENTREGAR -> "Guardando la madera";
            });
            return;
        }
        workTicks = 0;
        switch (fase) {
            case TALAR -> talar(level);
            case PLANTAR -> plantar(level);
            case ABONAR -> abonar(level);
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

    /**
     * <b>Abona la arboleda del pueblo</b>: echa una <b>harina de huesos</b> del almacén (la que el granjero saca del
     * compostero) al plantón al que fue, para que el árbol crezca ya. Es lo que hace que una aldea <b>sin bosque</b>
     * —una islita— tenga madera en minutos en vez de esperar a que los plantones crezcan solos: no se inventa madera,
     * se cuida la que el pueblo plantó. El leñador deja de hacerlo en cuanto crece el primer árbol (ver
     * {@link #necesitaAbonoLaArboleda}).
     */
    private void abonar(ServerLevel level) {
        if (target == null || !level.getBlockState(target).is(BlockTags.SAPLINGS)) {
            return; // ya no hay plantón (creció, o alguien lo quitó)
        }
        Container almacen = contenedorConHarina(level);
        if (almacen == null) {
            return; // el pueblo no tiene harina (todavía no ha llenado el compostero)
        }
        ItemStack harina = new ItemStack(Items.BONE_MEAL, 1);
        if (VillagePantry.sacar(almacen, s -> s.is(Items.BONE_MEAL), 1) <= 0) {
            return;
        }
        if (net.minecraft.world.item.BoneMealItem.growCrop(harina, level, target)) {
            level.playSound(null, target, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
            VillageManager.ponerSuceso(villager, "Abono la arboleda");
            DevilRpg.LOGGER.info("[Village] El lenador: abona la arboleda del pueblo en {}", target);
        }
    }

    /**
     * ¿Toca abonar la arboleda? Sí mientras tenga <b>algún plantón</b> y <b>ningún árbol</b>: en cuanto crece el
     * primero, la arboleda se sostiene sola (el leñador la tala y la replanta) y no hay que gastar más harina.
     */
    private boolean necesitaAbonoLaArboleda(ServerLevel level) {
        boolean hayPlanton = false;
        for (BlockPos p : VillageGenerator.plantonesDeLaArboleda(center, nivelDeLaAldea(level))) {
            for (int dy = 0; dy <= ALTURA_MAX; dy++) {
                BlockState s = level.getBlockState(p.above(dy));
                if (s.is(BlockTags.LOGS)) {
                    return false; // ya hay un árbol: la arboleda está en marcha
                }
                if (s.is(BlockTags.SAPLINGS)) {
                    hayPlanton = true;
                }
            }
        }
        return hayPlanton;
    }

    /** El plantón de la arboleda más cercano (el que se abona), o {@code null} si no queda ninguno. */
    @Nullable
    private BlockPos plantonDeLaArboledaMasCercano(ServerLevel level) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (BlockPos p : VillageGenerator.plantonesDeLaArboleda(center, nivelDeLaAldea(level))) {
            if (!level.getBlockState(p).is(BlockTags.SAPLINGS)) {
                continue;
            }
            double d = villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = p;
            }
        }
        return mejor;
    }

    /**
     * Un <b>hueco libre de la arboleda del pueblo</b> donde plantar, o {@code null} si ya están todos ocupados.
     * <p>
     * Va <b>antes</b> del barrido general a propósito: la arboleda es la madera del pueblo y sus seis celdas están
     * <b>dentro del recinto</b>, donde el barrido general no planta (y una rejilla de 2 en 2 ni siquiera pasa por
     * todas ellas). Sin esto, el leñador talaba la arboleda y el hueco se quedaba vacío, así que la arboleda se
     * apagaba sola (lo reportó el jugador: "no está plantando").
     */
    @Nullable
    private BlockPos huecoDeLaArboleda(ServerLevel level) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (BlockPos p : VillageGenerator.plantonesDeLaArboleda(center, nivelDeLaAldea(level))) {
            if (!esHuecoDeTierra(level, p)) {
                continue; // ya hay un plantón o un árbol: lo cuida el pueblo
            }
            double d = villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = p;
            }
        }
        return mejor;
    }

    /**
     * Dónde está la <b>harina de huesos</b> del pueblo, o {@code null} si no hay. OJO: la hace el granjero en su
     * compostero y la guarda en la <b>despensa</b> (ahí vive, junto a las semillas y el abono, porque es un recambio
     * suyo); en el almacén solo aparece si la trajo el recolector de lo que cayó al suelo antes de que el granjero la
     * pasara. Se mira en los dos sitios: quedarse solo con el almacén dejaba la arboleda sin abonar nunca.
     */
    @Nullable
    private Container contenedorConHarina(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (VillagePantry.contar(despensa, s -> s.is(Items.BONE_MEAL)) > 0) {
            return despensa;
        }
        Container almacen = VillageStorage.almacen(level, center);
        if (VillagePantry.contar(almacen, s -> s.is(Items.BONE_MEAL)) > 0) {
            return almacen;
        }
        return null;
    }

    /** La cota de la aldea (para los plantones de la arboleda): se pregunta UNA vez, no en cada tick. */
    private int nivelDeLaAldea(ServerLevel level) {
        if (nivelAldea == Integer.MIN_VALUE) {
            nivelAldea = VillageGenerator.cotaDeLaPlaza(level, center);
        }
        return nivelAldea;
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
                if (dCentro > RADIO_MAXIMO) {
                    continue;
                }
                BlockPos base = baseDeArbol(level, x, z);
                if (base == null) {
                    continue;
                }
                // DENTRO DEL RECINTO: el muro y las casas son de TRONCOS, así que no se tala a lo loco. Se tala solo
                // si el árbol NO es parte de lo que construyó el pueblo (el plano dice qué bloques son suyos) — así
                // caen los árboles del monte que quedaron dentro de la muralla, que es lo que pidió el jugador
                // ("no está cortando los árboles de adentro de la villa": medido en su guardado había 137 árboles
                // sueltos dentro de la valla)— y, por supuesto, la arboleda del pueblo.
                if (dCentro < RADIO_MINIMO
                        && !VillageGenerator.enLaArboleda(center, new BlockPos(x, 0, z))
                        && esParteDeLaAldea(level, base)) {
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
     * ¿Ese tronco es <b>parte de la aldea construida</b> (el muro, el poste de una casa, el kiosco)? Entonces no se
     * tala: se pregunta al <b>plano</b> de la aldea, que apunta cada bloque que puso el pueblo ({@code colocar}), y
     * los árboles del monte no están en él. Es la forma exacta de distinguir un árbol de un poste sin adivinar por
     * los alrededores (una casa con un árbol pegado engañaría a cualquier heurística).
     * <p>
     * Basta con mirar la <b>base</b> del tronco: el plano es un censo de posiciones, así que si la base no está en
     * él, ese árbol no lo puso el pueblo (y si lo estuviera, ya no se toca). El plano se copia a un {@code Set} y se
     * refresca cada {@link #PLANO_REFRESCO_TICKS}: la búsqueda de árboles recorre cientos de columnas y preguntarlo
     * posición a posición sería un barrido lineal del plano por cada árbol.
     */
    private boolean esParteDeLaAldea(ServerLevel level, BlockPos base) {
        if (planoDeLaAldea == null || level.getGameTime() - planoConsultadoEn >= PLANO_REFRESCO_TICKS) {
            planoConsultadoEn = level.getGameTime();
            VillageSavedData.Blueprint plano = VillageSavedData.get(level).getBlueprint(objectiveIndex);
            if (plano == null) {
                planoDeLaAldea = Set.of();
            } else {
                Set<Long> copia = new HashSet<>();
                for (long pos : plano.positions()) {
                    copia.add(pos);
                }
                planoDeLaAldea = copia;
            }
        }
        return planoDeLaAldea.contains(base.asLong());
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
                if ((dCentro < RADIO_MINIMO && !VillageGenerator.enLaArboleda(center, new BlockPos(x, 0, z)))
                        || dCentro > RADIO_MAXIMO) {
                    continue; // dentro de la valla no se planta (como no se tala), salvo en su arboleda; y muy lejos tampoco
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
     * construyó encima, creció otra cosa, o están <b>dentro del pueblo</b>) se tiran: si no, el leñador volvería a
     * ellos para siempre.
     * <p>
     * Un hueco de <b>dentro de la muralla</b> no se replanta (salvo en la arboleda del pueblo): la aldea se
     * <b>despeja</b> de árboles, que es lo que pidió el jugador; repoblarla sería volver a llenarla de troncos.
     */
    @Nullable
    private BlockPos primerPendiente(ServerLevel level) {
        while (!pendientes.isEmpty()) {
            BlockPos p = pendientes.get(0).pos();
            if (esHuecoDeTierra(level, p) && seReplantaAqui(p)) {
                return p;
            }
            pendientes.remove(0);
        }
        return null;
    }

    /** ¿Ese punto está donde SÍ se replanta? Fuera del recinto, o en la arboleda del pueblo (nunca dentro). */
    private boolean seReplantaAqui(BlockPos p) {
        double dx = p.getX() - center.getX();
        double dz = p.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz) >= RADIO_MINIMO
                || VillageGenerator.enLaArboleda(center, p);
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
