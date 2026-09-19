package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/**
 * <b>El CLÉRIGO de la aldea</b>: prepara <b>pociones de verdad</b> en su <b>soporte de pociones</b> con lo que el pueblo
 * junta. Es su oficio propio (prioridad 4, como el granjero o el herrero), el único rol que hasta ahora no tenía goal
 * del mod: su faena era la actividad de trabajar de vanilla y, como no reclamaba su puesto, caía a IDLE (el fallo de
 * "da vueltas sobre su eje").
 * <p>
 * <b>La cadena es la del pueblo, no magia</b>: los guardias y el jugador matan bichos, el <b>recolector</b> barre el
 * botín del suelo y lo deja en el <b>almacén</b> (pepitas de oro, ojos de araña, pólvora, carne podrida), el
 * <b>granjero</b> cría zanahorias, y el clérigo <b>toma de allí</b> lo que necesita. Lo que el pueblo no puede
 * conseguir (la <b>verruga del Nether</b>, el <b>polvo de blaze</b> y las <b>botellas de agua</b>) lo trae el jugador
 * al almacén: sin ello el clérigo no puede empezar la tanda y no la empieza (no se inventa nada).
 * <p>
 * <b>La poción la hace el JUEGO</b>: el clérigo no simula nada, <b>carga el soporte</b> ({@code BrewingStandBlockEntity}:
 * las botellas en sus tres huecos, el ingrediente encima y el polvo de blaze de combustible) y el soporte cuece solo
 * (agua + verruga = <b>poción extraña</b>; extraña + zanahoria dorada = <b>visión nocturna</b>; extraña + ojo de araña
 * = <b>veneno</b>; y con pólvora, la versión <b>arrojadiza</b>). Cuando la poción está lista, la saca y la deja en el
 * almacén, que es de donde la coge el jugador (y, cuando se pueda, se la dará a la guardia).
 */
public class VillagerClericGoal extends Goal {

    /** Prioridad con la que se engancha: es su oficio (la misma que el granjero y el herrero). */
    public static final int PRIORIDAD = 4;

    private static final float VELOCIDAD = 0.6F;
    /** Distancia a la que ya se considera que está en el soporte. */
    private static final double REACH = 3.0D;
    /** Ticks de faena por acción (medio segundo): se le ve trabajar sin eternizarse. */
    private static final int WORK_TICKS = 10;
    /** Descanso entre acción y acción. */
    private static final int REST_TICKS = 15;
    /** Descanso cuando no hay nada que hacer (4 s). */
    private static final int IDLE_REST_TICKS = 80;
    /** Si no logra acercarse en este tiempo, abandona (y se apunta el sitio: I33). */
    private static final int STUCK_LIMIT = 120;
    /**
     * Paciencia para el <b>viaje al agua</b> (30 s): el agua del pueblo está a 65-95 bloques y con la paciencia del
     * puesto (6 s) se rendía a mitad de camino (medido con el arnés).
     */
    private static final int STUCK_AGUA = 600;
    /** Botellas que carga de una vez (los tres huecos del soporte). */
    private static final int BOTELLAS = 3;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /** El soporte de pociones (su puesto de trabajo, en la iglesia): la celda a la que va a trabajar. */
    @Nullable
    private BlockPos soporte;
    /** La celda de AGUA a la que va a llenar las botellas de cristal (se busca cuando le hacen falta). */
    @Nullable
    private BlockPos agua;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    /** Contador aparte para el viaje al agua (su paciencia es mucho mayor: ver {@link #STUCK_AGUA}). */
    private int stuckAgua;
    /** Lo más cerca que ha estado del <b>puesto</b> (soporte) desde que empezó esa ida. */
    private double mejorDistancia = Double.MAX_VALUE;
    /**
     * Lo más cerca que ha estado de la <b>orilla</b> en el viaje al agua, <b>aparte</b> de {@link #mejorDistancia}.
     * <p>
     * Compartir el contador entre las dos piernas es un bug medido con el arnés: al llenar las botellas el clérigo
     * está a ~3 bloques de la orilla, así que {@code mejorDistancia} se queda en 3; a la vuelta, los ~90 bloques
     * hasta el soporte <b>nunca</b> mejoran ese 3, {@code stuckTicks} sube a 120 en 6 s y el clérigo
     * <b>aparca su propio soporte</b> a mitad de camino (`no consigue llegar a 1397,121,1371` en el log, a los 6 s
     * de llenar las botellas y estando aún a 57 bloques). Con la vuelta medida de cero, cada paso que da hacia el
     * soporte cuenta como acercarse.
     */
    private double mejorDistanciaAgua = Double.MAX_VALUE;

    public VillagerClericGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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
        if (villager.getVillagerData().getProfession() != VillagerProfession.CLERIC) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        // Su puesto de trabajo es el SOPORTE DE POCIONES (lo reclama el latido: ver `reclamarEstacionesDelPueblo`).
        Optional<GlobalPos> puesto = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (puesto.isEmpty() || !puesto.get().dimension().equals(level.dimension())) {
            return false; // sin soporte (o en otra dimensión) no hay nada que hacer
        }
        soporte = puesto.get().pos();
        if (VillageManager.esPuntoFallido(villager, soporte)) {
            restTicks = IDLE_REST_TICKS; // no llegó hace poco: espera en vez de empujar la misma pared (I33)
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        stuckAgua = 0;
        mejorDistanciaAgua = Double.MAX_VALUE;
        irAlSoporte();
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.isBaby() || soporte == null || VillageManager.estaDescansando(villager)) {
            return false;
        }
        if (stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = APARCAR EL SITIO (I33): el soporte al que no llegó no se reintenta en bucle.
            VillageManager.marcarPuntoFallido(villager, soporte);
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        soporte = null;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    @Override
    public void tick() {
        if (soporte == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        // 0) SI LLEVA BOTELLAS DE CRISTAL, PRIMERO AL AGUA: el pueblo no fabrica vidrio, así que las botellas las trae
        //    el jugador al almacén, pero LLENARLAS es cosa del clérigo (el bebedero del corral, el lago de la pesquera
        //    o cualquier charca del término). Sin esto, las botellas de agua tendrían que venir ya embotelladas de fuera
        //    y la cadena no se cerraría sola.
        if (llevaCristal()) {
            if (agua == null || !hayAguaAlLado(level, agua)) {
                agua = buscarAgua(level);
                if (agua != null) {
                    // La ida al agua se mide de cero (y con SU contador: ver `mejorDistanciaAgua`).
                    mejorDistanciaAgua = Double.MAX_VALUE;
                    stuckAgua = 0;
                    DevilRpg.LOGGER.info("[Village] El clerigo va a llenar las botellas a la orilla de {}", agua);
                }
            }
            if (agua == null) {
                restTicks = IDLE_REST_TICKS;
                VillageManager.ponerActividad(villager, "No encuentro agua");
                return;
            }
            double hastaElAgua = Math.sqrt(villager.distanceToSqr(agua.getX() + 0.5D, agua.getY() + 0.5D,
                    agua.getZ() + 0.5D));
            if (hastaElAgua > REACH) {
                VillageManager.caminarHacia(villager, agua, VELOCIDAD);
                VillageManager.ponerActividad(villager, "A por agua");
                if (hastaElAgua < mejorDistanciaAgua - 0.5D) {
                    mejorDistanciaAgua = hastaElAgua;
                    stuckAgua = 0;
                } else if (++stuckAgua >= STUCK_AGUA) {
                    // RENDIRSE = APARCAR ESA ORILLA (I33), pero con MUCHA más paciencia que en el puesto: el agua del
                    // pueblo está a 65-95 bloques (el bebedero, el lago) y en 6 s (el STUCK_LIMIT normal) no se llega
                    // ni a la esquina. Medido con el arnés: se rendía a mitad de camino y volvía al soporte.
                    DevilRpg.LOGGER.info("[Village] El clerigo se atasca yendo al agua en {} (orilla {}): la deja por"
                            + " un rato", villager.blockPosition().toShortString(), agua.toShortString());
                    VillageManager.marcarPuntoFallido(villager, agua);
                    agua = null;
                    stuckAgua = 0;
                    mejorDistanciaAgua = Double.MAX_VALUE;
                }
                return;
            }
            VillageManager.parar(villager);
            llenarBotellas(level);
            agua = null;
            stuckAgua = 0;
            mejorDistanciaAgua = Double.MAX_VALUE;
            // Y LA VUELTA AL SOPORTE SE MIDE DE CERO: si no, `mejorDistancia` sigue valiendo lo que se acercó a la
            // ORILLA (~3 bloques) y la caminata de vuelta de ~90 bloques parece "no acercarse" al soporte, así que a
            // los 6 s (STUCK_LIMIT) el clérigo APARCABA SU PROPIO SOPORTE a mitad de camino (medido con el arnés:
            // `no consigue llegar a 1397,121,1371`, a los 6 s de llenar las botellas y estando aún a 57 bloques).
            mejorDistancia = Double.MAX_VALUE;
            stuckTicks = 0;
            return;
        }
        double distancia = Math.sqrt(villager.distanceToSqr(soporte.getX() + 0.5D, soporte.getY() + 0.5D,
                soporte.getZ() + 0.5D));
        if (distancia > REACH) {
            irAlSoporte();
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            return;
        }
        VillageManager.parar(villager);
        if (!(level.getBlockState(soporte).getBlock() instanceof BrewingStandBlock)
                || !(level.getBlockEntity(soporte) instanceof BrewingStandBlockEntity soporteDePociones)) {
            return; // ya no hay soporte ahí (el jugador se lo llevó): el latido volverá a decidir
        }
        if (workTicks < WORK_TICKS) {
            workTicks++;
            VillageManager.ponerActividad(villager, verboActual());
            return;
        }
        workTicks = 0;
        villager.swing(InteractionHand.MAIN_HAND);
        trabajar(level, soporteDePociones);
        restTicks = REST_TICKS;
    }

    // --- la faena ------------------------------------------------------------------------------------

    /**
     * Radio en el que el clérigo busca agua para llenar las botellas. Tiene que dar para llegar al <b>bebedero del
     * corral</b> (~65 bloques de la iglesia) y al <b>lago de la pesquera</b> (~95), que es donde está el agua del
     * pueblo: con 24 —lo primero que se probó— se quedaba en el soporte diciendo "No encuentro agua" (medido).
     */
    private static final int RADIO_AGUA = 104;
    /** Paso del barrido (bloques): 4 no se salta una charca y no recorre el mundo columna a columna. */
    private static final int PASO_AGUA = 4;

    /** ¿Lleva botellas de <b>cristal</b> (vacías) encima? Entonces va al agua antes que al soporte. */
    private boolean llevaCristal() {
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize(); i++) {
            if (mochila.getItem(i).is(Items.GLASS_BOTTLE)) {
                return true;
            }
        }
        return false;
    }

    /** ¿Esa celda es agua (y se puede estar de pie al lado)? */
    private boolean esAgua(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * La <b>orilla</b> más cercana (una casilla seca al lado del agua, con sitio para pararse) a la que va a llenar
     * las botellas: el bebedero del corral, el lago de la pesquera o cualquier charca del término.
     * <p>
     * OJO CON EL DESTINO: se navega a la <b>orilla</b>, no a la celda de agua. Mandarlo al agua (lo primero que se
     * probó) es mandarlo a un bloque al que la navegación <b>no puede llegar</b>: el goal se rendía, se aparcaba el
     * sitio y volvía al soporte — el baile alrededor de la iglesia que se midió con el arnés.
     * <p>
     * Se barre en la <b>altura del terreno</b> ({@code WORLD_SURFACE}) y con un paso de {@link #PASO_AGUA}: mirando un
     * cubo pequeño —radio 24 y paso 2— el clérigo se quedaba con la etiqueta "No encuentro agua", porque el agua del
     * pueblo está a 65 (el bebedero) y 95 (el lago) bloques de la iglesia y una charca de 3×1 se cuela entre las
     * columnas pares. Solo se hace cuando va a llenar (no cada tick).
     */
    @Nullable
    private BlockPos buscarAgua(ServerLevel level) {
        BlockPos base = villager.blockPosition();
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (int dx = -RADIO_AGUA; dx <= RADIO_AGUA; dx += PASO_AGUA) {
            for (int dz = -RADIO_AGUA; dz <= RADIO_AGUA; dz += PASO_AGUA) {
                BlockPos alto = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        new BlockPos(base.getX() + dx, 0, base.getZ() + dz));
                for (int dy = 0; dy >= -2; dy--) { // el agua puede estar un bloque por debajo (la orilla)
                    BlockPos agua = alto.offset(0, dy, 0);
                    if (!esAgua(level, agua)) {
                        continue;
                    }
                    BlockPos orilla = orillaDe(level, agua);
                    if (orilla == null || VillageManager.esPuntoFallido(villager, orilla)) {
                        continue; // sin sitio para pararse al lado (o ya se intentó y no se llegó): no vale
                    }
                    double d = orilla.distSqr(base);
                    if (d < mejorDist) {
                        mejorDist = d;
                        mejor = orilla;
                        break;
                    }
                }
            }
        }
        return mejor;
    }

    /** Una casilla <b>seca y con sitio para pararse</b> al lado de esa agua (o {@code null} si el agua está encajonada). */
    @Nullable
    private BlockPos orillaDe(ServerLevel level, BlockPos agua) {
        for (BlockPos p : new BlockPos[]{agua.north(), agua.south(), agua.east(), agua.west(), agua.above()}) {
            if (sePuedeEstar(level, p)) {
                return p.immutable();
            }
        }
        return null;
    }

    /** ¿Esa casilla tiene sitio para pararse? (nada sólido en la casilla ni encima, y suelo firme debajo) */
    private boolean sePuedeEstar(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    /** ¿Sigue habiendo agua al lado de esa orilla? (por si el jugador la tapó mientras iba) */
    private boolean hayAguaAlLado(ServerLevel level, BlockPos orilla) {
        for (BlockPos p : new BlockPos[]{orilla.north(), orilla.south(), orilla.east(), orilla.west(), orilla.below()}) {
            if (esAgua(level, p)) {
                return true;
            }
        }
        return false;
    }

    /** Llena de agua las botellas de cristal que lleva (receta de vanilla: botella + agua = poción de agua). */
    private void llenarBotellas(ServerLevel level) {
        var mochila = villager.getInventory();
        int llenas = 0;
        for (int i = 0; i < mochila.getContainerSize() && llenas < BOTELLAS; i++) {
            ItemStack s = mochila.getItem(i);
            if (!s.is(Items.GLASS_BOTTLE)) {
                continue;
            }
            ItemStack agua = PotionContents.createItemStack(Items.POTION, Potions.WATER).copyWithCount(s.getCount());
            mochila.setItem(i, agua);
            llenas += s.getCount();
        }
        if (llenas > 0) {
            level.playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.0F);
            VillageManager.ponerSuceso(villager, "Botellas llenas");
            villager.swing(InteractionHand.MAIN_HAND);
            DevilRpg.LOGGER.info("[Village] El clerigo lleno {} botella(s) de agua", llenas);
        }
    }

    /** Saca <b>una</b> unidad de la mochila del clérigo que cumpla el filtro (o {@code null}). */
    @Nullable
    private ItemStack sacarDeLaMochila(java.util.function.Predicate<ItemStack> filtro) {
        var mochila = villager.getInventory();
        for (int i = 0; i < mochila.getContainerSize(); i++) {
            ItemStack s = mochila.getItem(i);
            if (s.isEmpty() || !filtro.test(s)) {
                continue;
            }
            ItemStack sacado = s.copyWithCount(1);
            s.shrink(1);
            if (s.isEmpty()) {
                mochila.setItem(i, ItemStack.EMPTY);
            }
            return sacado;
        }
        return null;
    }

    /** Verbo de lo que está haciendo (lo que se ve en su etiqueta). */
    private String verboActual() {
        return "En la iglesia";
    }

    /**
     * Una acción de la faena, por orden: <b>sacar lo que ya está hecho</b>, <b>cargar el ingrediente que toca</b> (con
     * lo que hay en el almacén) y <b>poner el combustible</b>. El soporte cuece solo: aquí no se simula nada.
     */
    private void trabajar(ServerLevel level, BrewingStandBlockEntity stand) {
        // 1) ¿POCIONES LISTAS? Se sacan y se dejan en el almacén (de ahí las coge el jugador).
        for (int hueco = 0; hueco < 3; hueco++) {
            ItemStack botella = stand.getItem(hueco);
            if (botella.isEmpty() || estaEnProceso(botella)) {
                continue;
            }
            ItemStack guardada = VillageStorage.guardar(level, center, botella.copy());
            if (guardada.getCount() < botella.getCount()) {
                stand.setItem(hueco, guardada);
                VillageManager.ponerSuceso(villager, "Pocion terminada");
                DevilRpg.LOGGER.info("[Village] El clerigo guardo una pocion en el almacen: {}",
                        botella.getHoverName().getString());
                return;
            }
        }
        // 2) ¿INGREDIENTE? Se elige por lo que tienen las botellas (agua -> verruga; extraña -> el efecto que haya).
        ItemStack enElSoporte = stand.getItem(3);
        if (enElSoporte.isEmpty()) {
            ItemStack ingrediente = ingredienteQueToca(level, stand);
            if (ingrediente != null) {
                stand.setItem(3, ingrediente);
                VillageManager.ponerSuceso(villager, "Preparando la mezcla");
                return;
            }
        }
        // 3) ¿COMBUSTIBLE? El polvo de blaze es lo que hace hervir el soporte.
        if (stand.getItem(4).isEmpty()) {
            ItemStack combustible = sacarDelAlmacen(level, s -> s.is(Items.BLAZE_POWDER), 1);
            if (combustible != null) {
                stand.setItem(4, combustible);
                return;
            }
        }
        // 4) ¿FALTAN BOTELLAS? Primero las de AGUA que lleva en la mochila (las que acaba de llenar), después las del
        //    almacén y, si no hay ninguna, BOTELLAS DE CRISTAL (el vidrio lo trae el jugador; llenarlas es cosa suya).
        for (int hueco = 0; hueco < 3; hueco++) {
            if (!stand.getItem(hueco).isEmpty()) {
                continue;
            }
            ItemStack agua = sacarDeLaMochila(this::esBotellaDeAgua);
            if (agua == null) {
                agua = VillageStorage.quitar(level, center, this::esBotellaDeAgua, 1);
            }
            if (agua != null) {
                stand.setItem(hueco, agua);
                return;
            }
        }
        ItemStack cristal = VillageStorage.quitar(level, center, s -> s.is(Items.GLASS_BOTTLE), BOTELLAS);
        if (cristal != null) {
            ItemStack sobra = villager.getInventory().addItem(cristal); // lo que no quepa vuelve al almacén
            if (!sobra.isEmpty()) {
                VillageStorage.guardar(level, center, sobra);
            }
            return; // y el tick lo llevará al agua (ver `llevaCristal`)
        }
        // Nada que hacer: la etiqueta dice QUÉ le falta. El pueblo no puede fabricar lo del Nether (verruga del
        // Nether, polvo de blaze) ni el vidrio, así que el aviso es lo que le dice al jugador qué traer.
        restTicks = IDLE_REST_TICKS;
        VillageManager.ponerActividad(villager, queFalta(level, stand));
    }

    /** Lo que le falta a la cadena, dicho en la etiqueta del clérigo (para que el jugador sepa qué traer). */
    private String queFalta(ServerLevel level, BrewingStandBlockEntity stand) {
        if (VillageStorage.cuenta(level, center, s -> s.is(Items.NETHER_WART)) <= 0) {
            return "Falta verruga del Nether";
        }
        if (stand.getItem(4).isEmpty()
                && VillageStorage.cuenta(level, center, s -> s.is(Items.BLAZE_POWDER)) <= 0) {
            return "Falta polvo de blaze";
        }
        if (VillageStorage.cuenta(level, center, this::esBotellaDeAgua) <= 0) {
            return "Faltan botellas de agua";
        }
        return "Sin faena";
    }

    /** Las <b>pociones que lleva dentro</b> ese objeto (vacío si no es una poción). */
    private static PotionContents contenidoDe(ItemStack s) {
        PotionContents contenido = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return contenido == null ? PotionContents.EMPTY : contenido;
    }

    /** ¿Esa botella es una <b>poción de agua</b> sin cocer? */
    private boolean esBotellaDeAgua(ItemStack s) {
        return s.is(Items.POTION) && contenidoDe(s).is(Potions.WATER);
    }

    /** ¿Esa botella está <b>en proceso</b> (agua o poción extraña: todavía le falta la cocción)? */
    private boolean estaEnProceso(ItemStack s) {
        if (!s.is(Items.POTION)) {
            return true; // lo que no sea poción (una botella de cristal suelta) no se guarda
        }
        PotionContents pocion = contenidoDe(s);
        return pocion.is(Potions.WATER) || pocion.is(Potions.AWKWARD) || pocion.is(Potions.MUNDANE)
                || pocion.is(Potions.THICK);
    }

    /**
     * El ingrediente que toca, <b>sacado del almacén</b> (el recolector deja ahí el botín del pueblo): la
     * <b>verruga del Nether</b> convierte el agua en poción extraña; la <b>zanahoria dorada</b> (o el <b>ojo de
     * araña</b>) le da el efecto; y la <b>pólvora</b> la hace arrojadiza. Si no hay ninguno, {@code null}.
     */
    @Nullable
    private ItemStack ingredienteQueToca(ServerLevel level, BrewingStandBlockEntity stand) {
        boolean todasAgua = true;
        boolean algunaExtraña = false;
        for (int hueco = 0; hueco < 3; hueco++) {
            ItemStack botella = stand.getItem(hueco);
            if (botella.isEmpty()) {
                continue;
            }
            PotionContents pocion = contenidoDe(botella);
            todasAgua &= pocion.is(Potions.WATER);
            algunaExtraña |= pocion.is(Potions.AWKWARD);
        }
        if (todasAgua) {
            return sacarDelAlmacen(level, s -> s.is(Items.NETHER_WART), 1);
        }
        if (algunaExtraña) {
            // Los efectos que el pueblo puede pagar: zanahoria dorada (visión nocturna) y ojo de araña (veneno).
            ItemStack efecto = sacarDelAlmacen(level, s -> s.is(Items.GOLDEN_CARROT) || s.is(Items.SPIDER_EYE), 1);
            if (efecto != null) {
                return efecto;
            }
            // Y si no la hay, LA ZANAHORIA DORADA SE HACE AQUÍ: 8 pepitas de oro (del botín que barre el recolector) y
            // una zanahoria (de la huerta). El pueblo no fabrica pociones "porque sí": las paga con lo que junta.
            ItemStack dorada = hacerZanahoriaDorada(level);
            if (dorada != null) {
                return dorada;
            }
            return sacarDelAlmacen(level, s -> s.is(Items.GUNPOWDER), 1); // arrojadiza
        }
        return null;
    }

    /**
     * <b>Zanahoria dorada</b> con lo que hay en el almacén (8 pepitas de oro + 1 zanahoria), que es la receta de
     * vanilla. Devuelve {@code null} si al pueblo le falta algo (entonces el clérigo no inventa nada).
     */
    @Nullable
    private ItemStack hacerZanahoriaDorada(ServerLevel level) {
        if (VillageStorage.cuenta(level, center, s -> s.is(Items.GOLD_NUGGET)) < 8
                || VillageStorage.cuenta(level, center, s -> s.is(Items.CARROT)) < 1) {
            return null;
        }
        VillageStorage.quitar(level, center, s -> s.is(Items.GOLD_NUGGET), 8);
        VillageStorage.quitar(level, center, s -> s.is(Items.CARROT), 1);
        DevilRpg.LOGGER.info("[Village] El clerigo preparo una zanahoria dorada (8 pepitas de oro + 1 zanahoria)");
        return new ItemStack(Items.GOLDEN_CARROT);
    }

    /** Saca del almacén lo primero que cumpla el filtro (y devuelve <b>una</b> unidad). */
    @Nullable
    private ItemStack sacarDelAlmacen(ServerLevel level, java.util.function.Predicate<ItemStack> filtro, int cuantas) {
        return VillageStorage.quitar(level, center, filtro, cuantas);
    }

    private void irAlSoporte() {
        if (soporte != null) {
            VillageManager.caminarHacia(villager, soporte, VELOCIDAD);
            VillageManager.ponerActividad(villager, "Yendo a la iglesia");
        }
    }
}
