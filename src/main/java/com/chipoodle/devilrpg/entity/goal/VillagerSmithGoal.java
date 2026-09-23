package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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
    /** Radio en el que se mira si OTRO aldeano tiene el puesto (vanilla reclama los puestos a menos de 48). */
    private static final double RADIO_PUESTO = 48.0D;
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
    /**
     * Picos que quiere tener el pueblo en el almacén (etapa I): uno para el <b>minero</b> y otro de repuesto. El
     * pico <b>se gasta</b> picando (lo pidió el jugador), así que el de herramientas los va reponiendo.
     */
    private static final int OBJETIVO_PICOS = 2;
    /** El pico de hierro cuesta lo de vanilla: 3 lingotes y 2 palos. */
    private static final int LINGOTES_POR_PICO = 3;
    private static final int PALOS_POR_PICO = 2;
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
     * <p>
     * {@code quema} = la faena es una <b>fundición</b> y gasta <b>un tronco</b> del almacén (ver
     * {@link VillageStorage#quitarLena}). El fuego se paga: la <b>fragua no funciona sin leña</b> (lo pidió el
     * jugador). Las faenas que <b>no</b> queman son las de la mesa y el muelle: aserrar, hacer palos, forjar,
     * encorar, flechar, curtir cuero y fabricar armas.
     */
    private record Receta(String verbo, String suceso, List<ItemStack> ingredientes, ItemStack producto,
                          boolean quema) {
        Receta(String verbo, String suceso, List<ItemStack> ingredientes, ItemStack producto) {
            this(verbo, suceso, ingredientes, producto, false);
        }
    }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private Receta receta;
    private Fase fase = Fase.RECOGER;
    @Nullable
    private BlockPos destino;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    /** Última transición anotada en el log: así se ve el ciclo entero sin escribir una línea por tick. */
    private String ultimaAnotacion = "";
    /**
     * Turno del herrero entre <b>transformar materiales</b> y <b>fabricar lo que falta</b> (espada, escudo, arco y
     * armadura). Sin la rotación, la fabricación no se alcanzaba nunca: ver {@code elegirReceta} (I55).
     */
    private boolean turnoDeFabricar;

    public VillagerSmithGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * ¿Es el herrero de <b>ARMAS</b> (muelle de afilar) o el de <b>HERRAMIENTAS</b> (mesa de herrería)?
     * <p>
     * Se lee <b>EN VIVO</b> del oficio, no se cachea en el constructor, y es la misma trampa que ya costó un bug en
     * {@code VillagerPickupGoal}: el pueblo <b>reparte oficios</b> (repone el puesto que se queda vacío, una cría
     * crece y lo hereda) y este goal no se vuelve a construir para el mismo aldeano (ver
     * {@code VillageManager.asegurarGoalDeHerrero}), así que un aldeano al que le cambian el oficio se quedaba
     * yendo al puesto del oficio <b>viejo</b> —o al del compañero— para siempre.
     */
    private boolean armas() {
        return villager.getVillagerData().getProfession() == VillagerProfession.WEAPONSMITH;
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
        // Lo que QUEMA (las fundiciones) solo se elige si el almacén tiene leña por encima de la reserva: así el
        // herrero no se lleva pepitas que no puede fundir y, si no hay leña, se pone a lo que no gasta fuego.
        receta = elegirReceta(almacen, VillageStorage.hayLenaParaQuemar(level, center));
        if (receta == null) {
            restTicks = IDLE_REST_TICKS; // no hay materiales (o ya está todo hecho): a esperar
            return false;
        }
        BlockPos puesto = VillageGenerator.puestoDeHerreria(level, center, armas());
        if (puesto == null) {
            receta = null;
            restTicks = IDLE_REST_TICKS;
            return false; // todavía no hay taller en esta aldea
        }
        // El puesto es SUYO: se le pone en el cerebro y se toma su ticket del punto de interés (ver
        // `reclamarElPuesto`: sin `JOB_SITE` el juego no le registra la actividad de trabajar y el aldeano se queda
        // en IDLE, que es lo que el jugador veía como "da vueltas sobre su eje" con la etiqueta "Paseando").
        reclamarElPuesto(level, puesto);
        fase = Fase.RECOGER;
        destino = VillageStorage.puntoDeApoyo(level, center);
        if (destino != null && VillageManager.esPuntoFallido(villager, destino)) {
            // Al almacén no llegó hace poco (I33: el cofre está tapado, cerrado o rodeado): no se queda empujando
            // la misma pared, espera un rato y vuelve a intentarlo.
            destino = null;
            restTicks = IDLE_REST_TICKS;
            return false;
        }
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
        if (receta != null && destino != null && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el sitio al que no llegó (el almacén o su taller) se apunta para
            // no volver a por él en bucle, que es lo que dejaba al herrero empujando el mismo obstáculo para siempre.
            VillageManager.marcarPuntoFallido(villager, destino);
            return false;
        }
        return receta != null && destino != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (receta == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (destino == null) {
            // SIN DESTINO: el sitio al que iba ya no está (el jugador se llevó la muela o la mesa, se rompió el
            // almacén...). Antes este goal se quedaba VIVO sin hacer nada: ocupaba la bandera MOVE (así que el aldeano
            // no podía ni pasear ni hacer otra cosa), no escribía etiqueta (salía "Paseando") y no terminaba NUNCA,
            // porque `canContinueToUse` solo mira la receta. Ahora se suelta la faena y se vuelve a decidir en el
            // siguiente `canUse`, que es donde se comprueba que el taller sigue existiendo.
            anotar("sin destino: se deja la faena");
            receta = null;
            return;
        }
        villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
        double distancia = Math.sqrt(villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D,
                destino.getZ() + 0.5D));
        if (distancia > REACH) {
            VillageManager.caminarHacia(villager, destino, 0.6F);
            // LA ETIQUETA TAMBIÉN MIENTRAS SE CAMINA. Faltaba, y es lo que el jugador vio: el herrero se pasa la mayor
            // parte del ciclo ANDANDO (del almacén al taller hay más de 80 bloques en su aldea), así que durante ese
            // rato no decía nada y su etiqueta caía al texto genérico: "Paseando" (o "Trabajando") con el aldeano
            // yendo a su faena. Los demás goals del pueblo (el leñador, el granjero, el ganadero) sí lo hacían.
            VillageManager.ponerActividad(villager, verboDeCamino());
            anotar("yendo: " + verboDeCamino());
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
            VillageManager.ponerActividad(villager, verboActual());
            anotar("faena: " + verboActual());
            return;
        }
        workTicks = 0;
        switch (fase) {
            case RECOGER -> {
                if (recoger(level)) {
                    fase = Fase.TRABAJAR;
                    destino = VillageGenerator.puestoDeHerreria(level, center, armas());
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
        // I33: si el sitio de la fase nueva es el que se quedó APARCADO (no llegó a él hace poco —el aviso lo puso el
        // goal al rendirse), se deja la faena: se suelta la receta y se corta el goal (ver `canContinueToUse`), así
        // que al volver a arrancar elegirá otra cosa en vez de ir a empujar la misma pared (el taller o el almacén).
        if (destino != null && VillageManager.esPuntoFallido(villager, destino)) {
            receta = null;
            destino = null;
        }
    }

    /** Lo que hace AHORA (ya en el sitio): va en la etiqueta del aldeano y en el log. */
    private String verboActual() {
        return switch (fase) {
            case RECOGER -> "Buscando materiales";
            case TRABAJAR -> "Fabricando";
            case ENTREGAR -> "Llevando lo fabricado";
        };
    }

    /** Lo que está haciendo <b>mientras va</b> de un sitio a otro (antes no decía nada y salía "Paseando"). */
    private String verboDeCamino() {
        return switch (fase) {
            case RECOGER -> "Yendo al almacen";
            case TRABAJAR -> armas() ? "Yendo al muelle" : "Yendo a la mesa";
            case ENTREGAR -> "Volviendo al almacen";
        };
    }

    /**
     * Deja el ciclo del herrero en el log <b>una vez por transición</b> (no por tick: esto es un aldeano caminando
     * 80 bloques, o sea cientos de ticks por viaje). Es lo que hace falta para poder seguirle el rastro en el log
     * del jugador sin llenarlo.
     */
    private void anotar(String clave) {
        if (clave.equals(ultimaAnotacion)) {
            return;
        }
        ultimaAnotacion = clave;
        DevilRpg.LOGGER.info("[Village] {}: {} ({} -> {})", armas() ? "El herrero de armas"
                        : "El herrero de herramientas", clave, verboActual(),
                destino == null ? "sin destino" : destino.toShortString());
    }

    @Override
    public void stop() {
        receta = null;
        destino = null;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    // --- el puesto de trabajo (lo que el juego llama JOB_SITE) ---------------------------------------

    /**
     * Le pone al herrero <b>su puesto en el cerebro</b> ({@code JOB_SITE}) y le <b>toma el ticket</b> de punto de
     * interés: es lo que hace que el juego lo tenga por un aldeano <b>con puesto de trabajo</b>.
     * <p>
     * Hace falta de verdad, y está <b>medido</b> en el guardado del jugador (aldea 2, centro 1414,1414):
     * <ul>
     *   <li>el <b>muelle de afilar</b> (1419,120,1368) y la <b>mesa de herrería</b> (1418,120,1368) tenían
     *       {@code free_tickets=0} (el ticket cogido) y <b>ningún</b> aldeano con ese sitio en la memoria: el único
     *       herrero de armas lo tenía solo como {@code POTENTIAL_JOB_SITE} y el de herramientas, ni eso;</li>
     *   <li>y sin {@code JOB_SITE} el cerebro <b>no registra la actividad de trabajar</b> —vanilla la añade con
     *       {@code addActivityWithConditions(WORK, ..., JOB_SITE presente)}, así que
     *       {@code setActiveActivityIfPossible(WORK)} falla y se cae a {@code IDLE}—. Ese IDLE es lo que el jugador
     *       veía: el aldeano con la etiqueta <b>"Paseando"</b> a pleno día de trabajo (guardado con
     *       {@code DayTime=8137}, dentro de la franja WORK de 2000 a 9000) y "dando vueltas sobre su eje" (las
     *       conductas de IDLE: paseo aleatorio y caminar hacia donde mira), en vez de estar en su taller.</li>
     * </ul>
     * Un ticket <b>cogido sin dueño</b> es un ticket <b>perdido</b>: nadie lo puede volver a reclamar nunca (vanilla
     * solo lo suelta al morir el aldeano, {@code Villager.releaseAllPois}). Aquí se reconoce ese caso —el puesto está
     * cogido y ningún aldeano lo tiene en el cerebro—, se libera y se toma. Y no se le quita el puesto a nadie: si
     * otro aldeano lo tiene, no se toca.
     */
    private void reclamarElPuesto(ServerLevel level, BlockPos puesto) {
        Optional<GlobalPos> mio = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (mio.isPresent() && mio.get().pos().equals(puesto)) {
            return; // ya es suyo
        }
        for (Villager otro : level.getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(puesto).inflate(RADIO_PUESTO))) {
            if (otro == villager) {
                continue;
            }
            Optional<GlobalPos> suyo = otro.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (suyo.isPresent() && suyo.get().pos().equals(puesto)) {
                return; // el puesto es de otro: no se le quita a nadie
            }
        }
        PoiManager poi = level.getPoiManager();
        Predicate<Holder<PoiType>> vale = villager.getVillagerData().getProfession().heldJobSite();
        BiPredicate<Holder<PoiType>, BlockPos> cual = (tipo, pos) -> pos.equals(puesto);
        if (poi.take(vale, cual, puesto, 1).isEmpty()) {
            poi.release(puesto); // ticket PERDIDO: se suelta y se vuelve a intentar
            if (poi.take(vale, cual, puesto, 1).isEmpty()) {
                return; // no hay manera (el chunk no está cargado...): se reintenta en el siguiente canUse
            }
            DevilRpg.LOGGER.info("[Village] {}: el puesto de trabajo en {} tenia el ticket PERDIDO (cogido y sin"
                            + " dueno): liberado y reclamado", armas() ? "El herrero de armas"
                    : "El herrero de herramientas", puesto.toShortString());
        }
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), puesto));
        anotar("puesto reclamado: " + puesto.toShortString());
    }

    // --- las faenas ---------------------------------------------------------------------------------

    /** Se lleva del almacén los ingredientes de la receta (si siguen estando) y, si la faena quema, su tronco. */
    private boolean recoger(ServerLevel level) {
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen == null || receta == null || !hay(almacen, receta.ingredientes())) {
            return false;
        }
        // LA LEÑA PRIMERO: si la faena es una fundición y el almacén no tiene excedente por encima de la reserva, no
        // se lleva los ingredientes para nada — la fragua no funciona sin combustible (lo pidió el jugador).
        if (receta.quema()) {
            ItemStack lena = VillageStorage.quitarLena(level, center, 1);
            if (lena == null || lena.isEmpty()) {
                DevilRpg.LOGGER.info("[Village] {}: no funde, el almacen no tiene lena por encima de la reserva de {}",
                        armas() ? "El herrero de armas" : "El herrero de herramientas", VillageStorage.RESERVA_LENA);
                return false;
            }
            ItemStack resto = guardarEnInventario(lena);
            if (!resto.isEmpty()) {
                VillageStorage.guardar(level, center, resto); // sin sitio: de vuelta al almacén
            }
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
        // LA LEÑA DE LA FRAGUA: una fundición gasta el tronco que se trajo del almacén (ver `recoger`). Si no lo
        // lleva (se le perdió por el camino), NO fabrica: no sale un lingote de una fragua apagada.
        if (receta.quema()) {
            if (cuantosEnInventario(VillageStorage::esLena) < 1) {
                DevilRpg.LOGGER.debug("[Village] al herrero le falta la lena para {}: no funde", receta.producto());
                return;
            }
            gastarDelInventario(VillageStorage::esLena, 1);
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
        DevilRpg.LOGGER.info("[Village] {}: {}{}", armas() ? "El herrero de armas" : "El herrero de herramientas",
                receta.suceso(), receta.quema() ? " (quemo un tronco del almacen)" : "");
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
     * <p>
     * {@code hayLena}: las <b>fundiciones</b> ({@code quema}) solo valen si el almacén tiene leña que quemar por
     * encima de la reserva. Sin leña se saltan y el herrero sigue con lo que no gasta fuego (aserrar, palos,
     * forjar…): un pueblo sin madera no funde, pero no se queda quieto.
     */
    @Nullable
    private Receta elegirReceta(@Nullable Container almacen, boolean hayLena) {
        if (almacen == null) {
            return null;
        }
        Receta transformar = recetaDeTransformacion(almacen, hayLena);
        Receta fabricar = armas() ? recetaDeArmas(almacen) : recetaDeArmadura(almacen);
        // LAS DOS FAENAS ROTAN (I55). La fabricación —la espada, el escudo, el arco y, sobre todo, la ARMADURA— iba
        // SIEMPRE detrás de la transformación de materiales (fundir pepitas y chatarra, curtir cuero, aserrar troncos
        // y hacer palos) y esa transformación NO SE ACABA NUNCA: sus objetivos (32 tablones, 64 palos) se los come el
        // otro herrero —el de armas gasta palos en arcos y flechas y tablones en escudos—, así que el de HERRAMIENTAS
        // se pasaba la vida haciendo palos y tablones y NO HACÍA NI UNA PIEZA DE ARMADURA. Medido en el log del
        // jugador (aldea 2): Josefa, "Hizo 4 palos" / "Aserro un tronco en 4 tablones" toda la sesión y NINGUNA
        // armadura en el almacén (que tenía 19 de cuero y 8 lingotes de hierro: material de sobra), y la milicia cayó
        // al primer asalto de un zombi con una espada en el cofre. Es la misma lección de I53 —un paso detrás de otro
        // que nunca termina no se alcanza—: ahora el herrero ALTERNA una faena de cada.
        turnoDeFabricar = !turnoDeFabricar;
        if (turnoDeFabricar && fabricar != null) {
            return fabricar;
        }
        if (transformar != null) {
            return transformar;
        }
        return fabricar;
    }

    /**
     * La <b>transformación de materiales</b> (los pasos caros de contar): pepitas de metal y chatarra en lingotes,
     * cuero viejo y carne podrida en cuero, troncos en tablones y tablones en palos. {@code null} si no hay nada que
     * transformar.
     */
    @Nullable
    private Receta recetaDeTransformacion(Container almacen, boolean hayLena) {
        // 1) Pepitas de metal -> lingotes (la forja). Lo hacen los dos.
        if (hayLena && contar(almacen, Items.IRON_NUGGET) >= PEPITAS_POR_LINGOTE) {
            return new Receta("Fundiendo", "Fundio " + PEPITAS_POR_LINGOTE + " pepitas en un lingote",
                    List.of(new ItemStack(Items.IRON_NUGGET, PEPITAS_POR_LINGOTE)),
                    new ItemStack(Items.IRON_INGOT), true);
        }
        // 1b) MINERAL CRUDO -> LINGOTE (etapa I). Lo funde el propio MINERO en el horno de su caseta, pero si no
        //     puede (se le rompió el pico y no tiene con qué volver a la mina, o no tiene combustible) el crudo se
        //     queda en el almacén. Aquí lo funde el herrero, y con ese hierro forja el pico que devuelve al minero a
        //     la mina: es lo que cierra el círculo del pueblo sin depender de que el jugador le traiga hierro. Sin
        //     esto, un pueblo con el mineral ya sacado y <b>crudo</b> se quedaba esperándose para siempre.
        if (hayLena && contar(almacen, Items.RAW_IRON) > 0) {
            return new Receta("Fundiendo mineral", "Fundio mineral de hierro en un lingote",
                    List.of(new ItemStack(Items.RAW_IRON)), new ItemStack(Items.IRON_INGOT), true);
        }
        if (hayLena && contar(almacen, Items.RAW_COPPER) > 0) {
            return new Receta("Fundiendo mineral", "Fundio mineral de cobre en un lingote",
                    List.of(new ItemStack(Items.RAW_COPPER)), new ItemStack(Items.COPPER_INGOT), true);
        }
        if (hayLena && contar(almacen, Items.RAW_GOLD) > 0) {
            return new Receta("Fundiendo mineral", "Fundio mineral de oro en un lingote",
                    List.of(new ItemStack(Items.RAW_GOLD)), new ItemStack(Items.GOLD_INGOT), true);
        }
        // 2) Chatarra PURA (hierro que no se pone nadie) -> lingotes.
        for (ItemStack chatarra : CHATARRA_SIEMPRE) {
            if (hayLena && contarChatarra(almacen, chatarra.getItem()) > 0) {
                return new Receta("Fundiendo chatarra", "Fundio chatarra en un lingote",
                        List.of(new ItemStack(chatarra.getItem(), 1)), new ItemStack(Items.IRON_INGOT), true);
            }
        }
        // 2b) Equipo de hierro/malla que SÍ se pone la milicia (espada, escudo, armadura): se funde solo lo que
        //     SOBRA de la reserva. Si no, el herrero fundía la única espada del almacén y el espadachín no tenía con
        //     qué armarse nunca (ni armadura que ponerse, que es justo lo que el jugador quiere VER puesta).
        for (ItemStack chatarra : CHATARRA_CON_RESERVA) {
            if (hayLena && haySobranteChatarra(almacen, chatarra.getItem())) {
                return new Receta("Fundiendo chatarra", "Fundio chatarra en un lingote",
                        List.of(new ItemStack(chatarra.getItem(), 1)), new ItemStack(Items.IRON_INGOT), true);
            }
        }
        // 2c) Chatarra de ORO -> lingote de oro (el oro no lo quiere nadie para pelear: se funde entero y queda
        //     como tesoro del almacén), y armadura de CUERO vieja -> cuero, también con reserva.
        for (ItemStack chatarra : CHATARRA_DE_ORO) {
            if (hayLena && contarChatarra(almacen, chatarra.getItem()) > 0) {
                return new Receta("Fundiendo oro", "Fundio chatarra de oro en un lingote",
                        List.of(new ItemStack(chatarra.getItem(), 1)), new ItemStack(Items.GOLD_INGOT), true);
            }
        }
        for (ItemStack viejo : CUERO_VIEJO) {
            if (haySobranteChatarra(almacen, viejo.getItem())) {
                return new Receta("Reciclando cuero", "Reciclo una armadura de cuero",
                        List.of(new ItemStack(viejo.getItem(), 1)), new ItemStack(Items.LEATHER));
            }
        }
        // 3) Carne de zombie podrida -> cuero (lo hace el de herramientas, en su mesa).
        if (!armas() && contar(almacen, Items.ROTTEN_FLESH) >= CARNE_POR_CUERO) {
            return new Receta("Curtiendo cuero", "Curtio " + CARNE_POR_CUERO + " carne podrida en un cuero",
                    List.of(new ItemStack(Items.ROTTEN_FLESH, CARNE_POR_CUERO)), new ItemStack(Items.LEATHER));
        }
        // 4) MADERA: el leñador (etapa B) trae TRONCOS al almacén, y sin esto no había de dónde sacar tablones ni
        //    palos: el escudo pide 6 tablones y el arco y las flechas, palos. Los parte el de HERRAMIENTAS en su mesa.
        if (!armas()) {
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
        return null;
    }

    /** Herrero de ARMAS (muelle): espada, escudo, arco y flechas. */
    @Nullable
    private Receta recetaDeArmas(Container almacen) {
        // SE FABRICA LO QUE MÁS FALTA, no lo primero de una lista. Lo pidió el jugador: *"lo que quiero es que siempre
        // haya una distribución uniforme de armas y armaduras disponibles, es decir que los herreros evalúen viendo el
        // almacén qué es lo que falta más y lo construyan, y así siempre estén evaluando"*. Antes el orden era fijo
        // (espada → escudo → arco → flechas), así que con las espadas al tope el herrero se quedaba sin nada que hacer
        // aunque faltaran escudos: MEDIDO en su guardado, el almacén tenía **3 espadas y 0 escudos** y los guardias
        // esperando el escudo en la puerta (I75). Ahora se mira el hueco de CADA pieza y se forja la mayor.
        List<Candidato> candidatos = new ArrayList<>();
        int lingotes = contar(almacen, Items.IRON_INGOT);
        if (lingotes >= 2) {
            candidatos.add(new Candidato(OBJETIVO_ESPADAS - contar(almacen, Items.IRON_SWORD),
                    new Receta("Forjando", "Forjo una espada de hierro",
                            List.of(new ItemStack(Items.IRON_INGOT, 2)), new ItemStack(Items.IRON_SWORD))));
        }
        if (lingotes >= 1 && contar(almacen, Items.OAK_PLANKS) >= 6) {
            candidatos.add(new Candidato(OBJETIVO_ESCUDOS - contar(almacen, Items.SHIELD),
                    new Receta("Forjando", "Forjo un escudo",
                            List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.OAK_PLANKS, 6)),
                            new ItemStack(Items.SHIELD))));
        }
        if (contar(almacen, Items.STRING) >= 3 && contar(almacen, Items.STICK) >= 3) {
            candidatos.add(new Candidato(OBJETIVO_ARCOS - contar(almacen, Items.BOW),
                    new Receta("Encorando", "Armo un arco",
                            List.of(new ItemStack(Items.STRING, 3), new ItemStack(Items.STICK, 3)),
                            new ItemStack(Items.BOW))));
        }
        if (contar(almacen, Items.STICK) >= 1 && contar(almacen, Items.FEATHER) >= 1
                && contar(almacen, Items.IRON_NUGGET) >= 1) {
            candidatos.add(new Candidato(OBJETIVO_FLECHAS - contar(almacen, Items.ARROW),
                    new Receta("Flechando", "Hizo 4 flechas",
                            List.of(new ItemStack(Items.STICK), new ItemStack(Items.FEATHER),
                                    new ItemStack(Items.IRON_NUGGET)),
                            new ItemStack(Items.ARROW, 4))));
        }
        return elQueMasFalta(candidatos);
    }

    /**
     * Herrero de HERRAMIENTAS (mesa): armadura. De <b>hierro</b> si hay lingotes de sobra (12 o más: que no se quede
     * sin material para las armas) y, si no, de <b>cuero</b> (que sale de la carne podrida).
     */
    @Nullable
    private Receta recetaDeArmadura(Container almacen) {
        int lingotes = contar(almacen, Items.IRON_INGOT);
        boolean hierro = lingotes >= 12;
        // EL PICO DEL MINERO (etapa I) VA PRIMERO, y NO entra en el reparto de `elQueMasFalta`: la armadura tiene
        // objetivos de 7 piezas por tipo, así que con el reparto el pico no saldría NUNCA (su "falta" máxima es 2) y
        // el minero se quedaría sin herramienta con la mina a medias. Es una <b>herramienta de trabajo</b>, no una
        // pieza del equipo de la milicia: lo pidió el jugador (*"debe haber un herrero de herramientas que haga
        // herramientas para que el minero haga su trabajo, similar a los otros 2 herreros"*).
        if (contar(almacen, Items.IRON_PICKAXE) < OBJETIVO_PICOS && lingotes >= LINGOTES_POR_PICO
                && contar(almacen, Items.STICK) >= PALOS_POR_PICO) {
            return new Receta("Forjando", "Forjo un pico de hierro",
                    List.of(new ItemStack(Items.IRON_INGOT, LINGOTES_POR_PICO),
                            new ItemStack(Items.STICK, PALOS_POR_PICO)),
                    new ItemStack(Items.IRON_PICKAXE));
        }
        // Y AQUÍ IGUAL: la armadura que MÁS falta (por piezas, sin importar de hierro o de cuero), para que el juego
        // de armaduras esté repartido y no se acumulen cascos mientras faltan botas.
        List<Candidato> candidatos = new ArrayList<>();
        candidatos.add(new Candidato(OBJETIVO_ARMADURA - contar(almacen, Items.IRON_HELMET)
                - contar(almacen, Items.LEATHER_HELMET),
                pieza(almacen, hierro, "un casco", Items.IRON_HELMET, Items.LEATHER_HELMET, 5)));
        candidatos.add(new Candidato(OBJETIVO_ARMADURA - contar(almacen, Items.IRON_CHESTPLATE)
                - contar(almacen, Items.LEATHER_CHESTPLATE),
                pieza(almacen, hierro, "un peto", Items.IRON_CHESTPLATE, Items.LEATHER_CHESTPLATE, 8)));
        candidatos.add(new Candidato(OBJETIVO_ARMADURA - contar(almacen, Items.IRON_LEGGINGS)
                - contar(almacen, Items.LEATHER_LEGGINGS),
                pieza(almacen, hierro, "unas grebas", Items.IRON_LEGGINGS, Items.LEATHER_LEGGINGS, 7)));
        candidatos.add(new Candidato(OBJETIVO_ARMADURA - contar(almacen, Items.IRON_BOOTS)
                - contar(almacen, Items.LEATHER_BOOTS),
                pieza(almacen, hierro, "unas botas", Items.IRON_BOOTS, Items.LEATHER_BOOTS, 4)));
        return elQueMasFalta(candidatos);
    }

    /** Un candidato a fabricar: <b>cuánto falta</b> de esa pieza y la receta que la haría (o {@code null} sin material). */
    private record Candidato(int falta, @Nullable Receta receta) {
    }

    /**
     * <b>De los candidatos, la pieza que MÁS falta</b> (y que se pueda hacer: las recetas sin material llegan a
     * {@code null}). A igualdad de falta gana la primera, así que el reparto es <b>estable</b> entre latidos: es el
     * "evalúa el almacén y construye lo que falta" que pidió el jugador.
     */
    @Nullable
    private static Receta elQueMasFalta(List<Candidato> candidatos) {
        Candidato mejor = null;
        for (Candidato c : candidatos) {
            if (c.receta() == null || c.falta() <= 0) {
                continue; // ni se puede hacer, o ya está cubierta
            }
            if (mejor == null || c.falta() > mejor.falta()) {
                mejor = c;
            }
        }
        return mejor == null ? null : mejor.receta();
    }

    @Nullable
    private Receta pieza(Container almacen, boolean hierro, String nombre, net.minecraft.world.item.Item deHierro,
                         net.minecraft.world.item.Item deCuero, int cuantas) {
        net.minecraft.world.item.Item material = hierro ? Items.IRON_INGOT : Items.LEATHER;
        net.minecraft.world.item.Item producto = hierro ? deHierro : deCuero;
        if (contar(almacen, material) < cuantas) {
            return null;
        }
        return new Receta("Fabricando", "Hizo " + nombre + " de " + (hierro ? "hierro" : "cuero"),
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

    /**
     * Como {@link #contar}, pero <b>sin contar el equipo encantado</b>: lo encantado es de la <b>guardia</b> (tiene
     * prioridad para ellos, ver {@code VillagerGuardGoal.valorDeArma}), así que el herrero <b>no puede fundirlo</b> como
     * chatarra. Lo pidió el jugador: *"los equipos con encantamientos tienen prioridad"*. Sin esto, una espada
     * encantada que hubiera en el almacén podía acabar en lingotes antes de que un guardia llegara a verla.
     */
    private static int contarChatarra(Container almacen, net.minecraft.world.item.Item item) {
        return VillagePantry.contar(almacen, s -> s.is(item) && !s.isEnchanted());
    }

    /** ¿Hay chatarra de sobra (sin contar la encantada, que es de la guardia) por encima de la reserva? */
    private static boolean haySobranteChatarra(Container almacen, net.minecraft.world.item.Item item) {
        return contarChatarra(almacen, item) > RESERVA_DE_MILICIA;
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
        gastarDelInventario(s -> s.is(item), cuantas);
    }

    /** Gasta del inventario del aldeano esa cantidad de lo que cumpla el filtro (por ejemplo, <b>cualquier tronco</b>). */
    private void gastarDelInventario(Predicate<ItemStack> filtro, int cuantas) {
        int faltan = cuantas;
        for (int i = 0; i < villager.getInventory().getContainerSize() && faltan > 0; i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!filtro.test(s)) {
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
        return cuantosEnInventario(s -> s.is(item));
    }

    /** Cuántas unidades de lo que cumpla el filtro lleva encima el herrero. */
    private int cuantosEnInventario(Predicate<ItemStack> filtro) {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (filtro.test(s)) {
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
