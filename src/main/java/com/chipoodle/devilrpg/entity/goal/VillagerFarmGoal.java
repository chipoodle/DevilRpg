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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Goal del <b>granjero</b>: la cadena de suministro de verdad de la aldea.
 * <ol>
 *   <li><b>Cosecha</b> los cultivos maduros de las parcelas de la aldea (y los <b>replanta</b>).</li>
 *   <li><b>Planta</b> en la tierra de cultivo vacía (semillas de su inventario o de la despensa).</li>
 *   <li><b>Abona TODO el plantío</b> con <b>harina de huesos</b>: no una planta por salida, sino las que le quepan en
 *       la tanda (16 por viaje), repartiéndolas por la parcela y sin repetir en las que ya fue.</li>
 *   <li><b>Llena el compostero</b> con las semillas que le <b>sobran</b> (trigo y betabel): es de donde sale la harina
 *       de huesos del paso 3.</li>
 *   <li><b>Lleva el trigo a la despensa</b> y allí lo convierte en <b>pan</b> (3 de trigo por hogaza), vacía el
 *       compostero ya lleno y se trae los recambios (semillas y abono).</li>
 * </ol>
 * Con esto la comida de la aldea ya no es un contador abstracto: sale del trigo que este aldeano cultiva de verdad
 * (ver {@link VillagePantry}).
 */
public class VillagerFarmGoal extends Goal {

    /** Distancia a la que ya se considera que llega al cultivo. */
    private static final double REACH = 3.0D;
    /** Ticks de faena por acción (medio segundo): se le ve dar el golpe sin eternizarse. */
    private static final int WORK_TICKS = 10;
    /** Descanso entre acción y acción. */
    private static final int REST_TICKS = 15;
    /** Descanso cuando no hay nada que hacer (4 s). Buscar cultivos recorre las parcelas, no se hace cada tick. */
    private static final int IDLE_REST_TICKS = 80;
    /** Si no logra acercarse en este tiempo, abandona el objetivo. */
    private static final int STUCK_LIMIT = 120;
    /** Si se aleja más de esto del centro, deja de trabajar (derivado del radio de la aldea). */
    private static final double MAX_DISTANCE_FROM_CENTER = VillageGenerator.FENCE_RADIUS + 12.0D;
    /** Trigo que lleva encima antes de ir a la despensa: cada 4 cosechas baja a guardarlo y hornear. */
    private static final int LLEVAR_TRIGO = 4;
    /** Semillas que se guarda como mucho: si lleva más, las suelta (si no, se le llena el inventario y no le cabe el trigo). */
    private static final int SEMILLAS_MAX = 8;
    /**
     * Semillas de SOBRA que guarda para el <b>compostero</b> (además de las que necesita para sembrar). Antes las
     * tiraba al suelo en cuanto pasaba de {@link #SEMILLAS_MAX}: el compostero NUNCA se llenaba (nadie le echaba
     * nada), así que no había harina de huesos y el abono se quedaba sin hacer.
     */
    private static final int SEMILLAS_PARA_COMPOSTAR = 16;
    /** Semillas que echa al compostero por visita (no se queda plantado allí). */
    private static final int COMPOSTAR_MAX = 16;
    /** Si la despensa tiene MÁS semillas que esto, se lleva unas cuantas para el compostero. */
    private static final int SEMILLAS_SOBRANTES_EN_DESPENSA = 32;
    /** Hogazas como mucho por visita (para que se le vea trabajar). */
    private static final int HORNEAR_MAX = 2;
    /**
     * Harina de huesos que se lleva encima como mucho. Antes 4: con eso abonaba UNA planta por visita (lo pidió el
     * jugador: "que abone todo el plantío, no nada más una planta"), así que ahora carga una tanda de 16 y las gasta
     * seguidas por toda la parcela.
     */
    private static final int HARINA_MAX = 16;
    /** Plantas que abona como mucho en una misma salida (para no echar la tarde abonando sin llevar nada al cofre). */
    private static final int ABONAR_MAX = 32;
    /** Cuánto puede traerse del almacén a la despensa en una visita (comida, semillas y abono del recolector). */
    private static final int TRAER_DEL_ALMACEN = 64;

    private enum Tarea { COSECHAR, PLANTAR, FERTILIZAR, COMPOSTAR, DESPENSA }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Tarea tarea = Tarea.COSECHAR;
    /**
     * Plantas que YA ha abonado en esta salida: así la harina de huesos se reparte por TODA la parcela en vez de
     * gastarse entera en la primera planta que encuentra (que es lo que pasaba al no recordar por dónde iba).
     */
    private final Set<Long> abonadas = new HashSet<>();
    private int workTicks;
    private int restTicks;
    /** Ticks SIN ACERCARSE al objetivo (ver {@code tick}): andar hacia él no cuenta como estar atascado. */
    private int stuckTicks;
    /** Distancia más corta lograda en este viaje: mientras baje, el granjero está avanzando. */
    private double mejorDistancia = Double.MAX_VALUE;

    public VillagerFarmGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        if (villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.FARMER) {
            return false;
        }
        // En plena refriega nadie se pone a sembrar.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex)) {
            return false;
        }
        // Ni en su hora de descanso: el granjero también se va a la cama.
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
        // Distancia HORIZONTAL al centro: la Y del centro puede ser la del spawn del jugador y no debe contar (la
        // aldea es un recinto en el plano XZ).
        double dxCentro = villager.getX() - center.getX();
        double dzCentro = villager.getZ() - center.getZ();
        if (dxCentro * dxCentro + dzCentro * dzCentro > MAX_DISTANCE_FROM_CENTER * MAX_DISTANCE_FROM_CENTER) {
            return false;
        }
        Container despensa = VillagePantry.despensa(level, center);
        // 1) Con trigo o vegetales suficientes encima, A LA DESPENSA (aunque queden cultivos maduros): si el depósito
        // se deja para el final, en una parcela grande SIEMPRE hay algo maduro y el granjero se pasa la vida
        // cosechando sin llevar NADA al cofre. Se va cada 4 unidades entre trigo y vegetales.
        if (trigoEnMano() + vegetalesEnMano() >= LLEVAR_TRIGO && despensa != null) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        // 2) Cultivo maduro: a cosecharlo.
        target = buscarCultivo(level, true);
        if (target != null) {
            tarea = Tarea.COSECHAR;
            return true;
        }
        // 3) Tierra de cultivo vacía: a plantar. SOLO si lleva semillas EN LA MANO: `plantar()` las saca de su
        // inventario, así que mandarlo a sembrar "porque en la despensa hay semillas" no hacía nada y lo dejaba en
        // bucle igual que el paso 5 (si le faltan, el paso 5 lo manda a la despensa a por ellas).
        if (tieneSemillas()) {
            target = buscarTierraVacia(level);
            if (target != null) {
                tarea = Tarea.PLANTAR;
                return true;
            }
        }
        // 4) Cultivo creciendo: a fertilizar. SOLO si lleva harina de huesos encima, por el mismo motivo (si no la
        // tiene, se la trae de la despensa en el paso 6). Se saltan los que YA abonó en esta salida: así el abono
        // se reparte por toda la parcela (ver `abonadas`).
        if (harinaEnMano() > 0 && abonadas.size() < ABONAR_MAX) {
            target = buscarCultivoSinAbonar(level);
            if (target != null) {
                tarea = Tarea.FERTILIZAR;
                return true;
            }
        }
        // 5) COMPOSTERO: las semillas que le SOBRAN (más de las que necesita para sembrar) van al compostero, que es
        // lo que produce la harina de huesos con la que abona. Va DESPUÉS de sembrar y abonar (primero lo urgente) y
        // sin él la harina se acababa y el abono se quedaba sin hacer, porque nadie llenaba nunca el compostero.
        // OJO: se cuentan solo las COMPOSTABLES (trigo y betabel). Contando también la zanahoria y la patata, un
        // granjero cargado de vegetales se pasaría el día yendo al compostero a no echar nada (bucle).
        if (semillasCompostablesSobrantes() > 0) {
            target = buscarCompostero(level);
            if (target != null) {
                tarea = Tarea.COMPOSTAR;
                return true;
            }
        }
        // 6) Recambios: a la despensa, pero SOLO si allí está lo que le falta. Antes bastaba con que le faltara algo
        // en la mano, así que con la despensa sin harina de huesos (lo normal hasta que el compostero se llena) el
        // granjero iba al kiosco, no hacía nada, volvía a elegir la misma tarea y se quedaba PLANTADO allí en bucle,
        // con la etiqueta "Llevando la cosecha" y sin llevar nada encima (medido en el guardado del jugador:
        // inventario con 5 semillas de trigo, 3 panes y 14 de betabel, y NINGUNA cosecha).
        boolean haySemillas = VillagePantry.contar(despensa, VillagerFarmGoal::esSemilla) > 0;
        boolean hayAbono = VillagePantry.contar(despensa, s -> s.is(Items.BONE_MEAL)) > 0;
        if ((!tieneSemillas() && haySemillas) || (harinaEnMano() == 0 && hayAbono)) {
            tarea = Tarea.DESPENSA;
            target = VillagePantry.puntoDeApoyo(level, center);
            return true;
        }
        // Nada que hacer (ni cultivo maduro, ni tierra libre, ni abono, ni recambios): a esperar. El cerebro del
        // aldeano lo tiene paseando mientras, que es lo que hace un aldeano sin tarea.
        restTicks = IDLE_REST_TICKS;
        return false;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        abonadas.clear();
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
        // Para la despensa vale un alcance mayor (el cofre está dentro del kiosco y no se navega hacia él).
        double alcance = tarea == Tarea.DESPENSA ? VillagePantry.ALCANCE_DESPENSA : REACH;
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D));
        if (distancia > alcance) {
            // El rumbo se le da POR EL CEREBRO en cada tick (ver VillageManager.caminarHacia): navegando a mano, el
            // cerebro del aldeano lo manda a su puesto, a la plaza o a pasear y se va a otro lado a mitad de camino
            // ("primero da vueltas y se va a otro lado antes de recogerlos").
            VillageManager.caminarHacia(villager, target, 0.6F);
            // ATASCADO = NO ACERCARSE, no "estar andando": contar cada tick mandaba al granjero a empezar de cero cada
            // 6 s (120 ticks) aunque fuera avanzando, así que un viaje a la despensa no lo terminaba NUNCA y se quedaba
            // ciclado ("no sube al kiosco a poner la cosecha").
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
            return;
        }
        workTicks = 0;
        switch (tarea) {
            case COSECHAR -> {
                VillageManager.ponerActividad(villager, "Cosechando");
                cosechar(level);
            }
            case PLANTAR -> {
                VillageManager.ponerActividad(villager, "Sembrando");
                plantar(level);
            }
            case FERTILIZAR -> {
                VillageManager.ponerActividad(villager, "Abonando");
                fertilizar(level);
                // ABONAR TODO EL PLANTÍO, no una sola planta (lo pidió el jugador): si le queda harina y hay otro
                // cultivo creciendo al que no haya ido todavía, SIGUE con él en la misma salida (el goal no termina).
                // Antes el goal acababa tras una planta y volvía a elegir tarea, así que el abono se gastaba de uno
                // en uno y la parcela no se abonaba nunca.
                abonadas.add(target.asLong());
                if (harinaEnMano() > 0 && abonadas.size() < ABONAR_MAX) {
                    BlockPos siguiente = buscarCultivoSinAbonar(level);
                    if (siguiente != null) {
                        target = siguiente;
                        mejorDistancia = Double.MAX_VALUE;
                        stuckTicks = 0;
                        return; // el goal sigue vivo con el siguiente cultivo
                    }
                }
            }
            case COMPOSTAR -> {
                VillageManager.ponerActividad(villager, "Llenando el compostero");
                compostar(level);
            }
            case DESPENSA -> {
                // La etiqueta dice lo que de verdad va a hacer: si lleva cosecha encima, la lleva; si no, va a por
                // recambios. Antes decía siempre "Llevando la cosecha" aunque no llevara nada (el jugador lo veía
                // plantado en el kiosco con esa etiqueta y sin poner nada en el cofre).
                VillageManager.ponerActividad(villager,
                        trigoEnMano() + vegetalesEnMano() > 0 ? "Llevando la cosecha" : "Buscando recambios");
                enLaDespensa(level);
            }
        }
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        restTicks = REST_TICKS;
        abonadas.clear(); // la próxima salida vuelve a poder abonar desde el principio
        villager.getNavigation().stop();
    }

    // --- las faenas -------------------------------------------------------------------------------

    private void cosechar(ServerLevel level) {
        BlockState state = level.getBlockState(target);
        // OJO: la edad se lee con la propiedad del PROPIO cultivo (el betabel es 0-3 y el trigo 0-7).
        if (!(state.getBlock() instanceof CropBlock crop)
                || VillageGenerator.edadDelCultivo(state) != crop.getMaxAge()) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, target, null);
        level.destroyBlock(target, false);
        level.setBlock(target, crop.getStateForAge(0), Block.UPDATE_ALL); // replantado en el sitio
        for (ItemStack drop : drops) {
            // Las SEMILLAS solo hasta un tope: si se le llenan los 8 huecos con semillas, el trigo ya no le cabe, se le
            // cae al suelo y nunca acumula las 3 unidades que disparan el viaje a la despensa (por eso el cofre seguía
            // con las 12 semillas iniciales y la aldea pasaba hambre). El tope incluye las que guarda para el
            // COMPOSTERO (`SEMILLAS_PARA_COMPOSTAR`): antes las soltaba al suelo y el compostero seguía vacío.
            if (esSemilla(drop) && semillasEnMano() >= SEMILLAS_MAX + SEMILLAS_PARA_COMPOSTAR) {
                level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5D, target.getY() + 0.5D,
                        target.getZ() + 0.5D, drop));
                continue;
            }
            ItemStack resto = guardarEnInventario(drop);
            if (!resto.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, target.getX() + 0.5D, target.getY() + 0.5D,
                        target.getZ() + 0.5D, resto));
            }
        }
        level.playSound(null, target, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    private void plantar(ServerLevel level) {
        ItemStack semilla = sacarSemilla();
        if (semilla.isEmpty()) {
            return;
        }
        BlockState cultivo = cultivoDe(semilla);
        if (cultivo == null) {
            return;
        }
        level.setBlock(target, cultivo, Block.UPDATE_ALL);
        level.playSound(null, target, cultivo.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    private void fertilizar(ServerLevel level) {
        ItemStack harina = sacarHarina();
        if (harina.isEmpty()) {
            return;
        }
        if (net.minecraft.world.item.BoneMealItem.growCrop(harina, level, target)) {
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
            VillageManager.ponerSuceso(villager, "Abono la huerta");
        }
    }

    /** En la despensa: guarda el trigo, hornea pan, coge semillas y harina de huesos, y vacía el compostero. */
    private void enLaDespensa(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null) {
            DevilRpg.LOGGER.warn("[Village] El granjero llego al kiosco y NO encontro la despensa (aldea en {})", center);
            return;
        }
        int guardados = 0;
        // 1) Compostero lleno -> harina de huesos para la despensa (el abono de la aldea lo produce ella misma).
        for (BlockPos p : VillageGenerator.parcelasDe(level, center)) {
            BlockPos comp = p.offset(-1, 0, 0);
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos q = comp.offset(0, dy, 0);
                BlockState s = level.getBlockState(q);
                if (s.is(Blocks.COMPOSTER) && s.getValue(ComposterBlock.LEVEL) == 8) {
                    level.setBlock(q, s.setValue(ComposterBlock.LEVEL, 0), Block.UPDATE_ALL);
                    VillagePantry.guardar(despensa, new ItemStack(Items.BONE_MEAL));
                }
            }
        }
        // 2) TODO lo comestible que lleve encima, a la despensa: el trigo (para el pan) y los VEGETALES (zanahoria,
        // patata y betabel). Antes solo se guardaba el TRIGO, así que lo demás se quedaba en su inventario o se caía al
        // suelo: el contador de comida de la aldea mira LO QUE HAY EN LA DESPENSA, no lo plantado, así que la aldea
        // pasaba hambre con la huerta llena (y moría gente teniendo comida).
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT) || VillagePantry.esVegetal(s)) {
                int antes = s.getCount();
                ItemStack resto = VillagePantry.guardar(despensa, s.copy());
                guardados += antes - resto.getCount();
                villager.getInventory().setItem(i, resto);
            }
        }
        // 3) LO DEL ALMACÉN, A LA DESPENSA: el recolector (holgazán) recoge del suelo lo que se cae por el pueblo
        // —incluido lo que deja caer el propio juego cuando SU aldeano granjero cosecha, que tira el grano al
        // suelo— y lo guarda en el almacén. Como el contador de comida de la aldea mira LA DESPENSA, esa comida se
        // quedaba muerta de risa en el almacén y la aldea pasaba hambre con el almacén lleno. El granjero hace de
        // puente en cada visita: se trae la comida, las semillas y el abono que el recolector haya guardado.
        int traidos = VillagePantry.traspasar(VillageStorage.almacen(level, center), despensa,
                s -> s.is(Items.WHEAT) || s.is(Items.BREAD) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)
                        || s.is(Items.BONE_MEAL) || VillagePantry.esVegetal(s)
                        || VillagePantry.esCarneCruda(s) || VillagePantry.esCarneCocida(s),
                TRAER_DEL_ALMACEN);
        // 4) Hornear: 3 de trigo por hogaza (la receta de vanilla), como mucho HORNEAR_MAX por visita.
        int horneadas = 0;
        while (horneadas < HORNEAR_MAX
                && VillagePantry.sacar(despensa, s -> s.is(Items.WHEAT), VillagePantry.WHEAT_PER_BREAD)
                    == VillagePantry.WHEAT_PER_BREAD) {
            VillagePantry.guardar(despensa, new ItemStack(Items.BREAD));
            horneadas++;
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        // 5) Recambios: semillas y harina de huesos, si le faltan.
        if (!tieneSemillas()) {
            for (ItemStack plantable : List.of(new ItemStack(Items.WHEAT_SEEDS, 4), new ItemStack(Items.CARROT, 3),
                    new ItemStack(Items.POTATO, 3), new ItemStack(Items.BEETROOT_SEEDS, 3))) {
                if (VillagePantry.sacar(despensa, s -> ItemStack.isSameItem(s, plantable), plantable.getCount())
                        == plantable.getCount()) {
                    guardarEnInventario(plantable);
                    break;
                }
            }
        }
        if (harinaEnMano() == 0) {
            int coge = VillagePantry.sacar(despensa, s -> s.is(Items.BONE_MEAL), HARINA_MAX);
            if (coge > 0) {
                guardarEnInventario(new ItemStack(Items.BONE_MEAL, coge));
            }
        }
        // 6) SEMILLAS DE SOBRA PARA EL COMPOSTERO: si la despensa va llena de semillas (el recolector barre las que
        //    se caen por el pueblo) se lleva unas cuantas y las composta, que es de donde sale la harina de huesos con
        //    la que abona. Solo cuando él no tiene ya de sobra, para no llenarse los huecos de semillas.
        if (semillasSobrantes() == 0
                && VillagePantry.contar(despensa, VillagerFarmGoal::esSemilla) > SEMILLAS_SOBRANTES_EN_DESPENSA) {
            for (net.minecraft.world.item.Item semilla : List.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS)) {
                int cogidas = VillagePantry.sacar(despensa, s -> s.is(semilla), COMPOSTAR_MAX);
                if (cogidas > 0) {
                    guardarEnInventario(new ItemStack(semilla, cogidas));
                    break;
                }
            }
        }
        // LO QUE ACABA DE HACER, a la cabeza (y al log): es más informativo que el verbo de lo que está haciendo, y
        // es lo que el jugador necesita para saber si la cadena de comida funciona sin abrir el log.
        String suceso;
        if (horneadas > 0 && guardados > 0) {
            suceso = "Guardo " + guardados + " y horneo " + horneadas + " pan(es)";
        } else if (horneadas > 0) {
            suceso = "Horneo " + horneadas + " pan(es) en la despensa";
        } else if (guardados > 0) {
            suceso = "Guardo " + guardados + " en la despensa";
        } else if (traidos > 0) {
            suceso = "Trajo " + traidos + " del almacen a la despensa";
        } else {
            suceso = null; // no había nada que hacer: no se anuncia nada
        }
        if (suceso != null) {
            VillageManager.ponerSuceso(villager, suceso);
            DevilRpg.LOGGER.info("[Village] El granjero: {}", suceso);
        } else {
            DevilRpg.LOGGER.debug("[Village] El granjero visito la despensa (no habia nada que hacer)");
        }
    }

    // --- utilidades --------------------------------------------------------------------------------

    private void irAlObjetivo() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, 0.6F);
        }
    }

    /** Busca en las parcelas un cultivo maduro (o creciendo, si {@code maduro} es false). */
    @Nullable
    private BlockPos buscarCultivo(ServerLevel level, boolean maduro) {
        return buscarCultivo(level, maduro, Set.of());
    }

    /** Igual, pero saltando las posiciones de {@code saltar} (las plantas que ya abonó en esta salida). */
    @Nullable
    private BlockPos buscarCultivo(ServerLevel level, boolean maduro, Set<Long> saltar) {
        for (BlockPos parcela : VillageGenerator.parcelasDe(level, center)) {
            for (int dx = 0; dx < VillageGenerator.PLOT_WIDTH; dx++) {
                for (int dz = 0; dz < VillageGenerator.PLOT_DEPTH; dz++) {
                    BlockPos q = parcela.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos r = q.offset(0, dy, 0);
                        if (!saltar.isEmpty() && saltar.contains(r.asLong())) {
                            continue;
                        }
                        BlockState s = level.getBlockState(r);
                        if (s.getBlock() instanceof CropBlock crop) {
                            boolean esMaduro = VillageGenerator.edadDelCultivo(s) == crop.getMaxAge();
                            if (esMaduro == maduro) {
                                return r;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Un cultivo que esté <b>creciendo</b> y al que <b>todavía no haya ido</b> en esta salida: es lo que hace que la
     * harina de huesos se reparta por la parcela (abonar el plantío entero) en vez de gastarse en la primera planta
     * que encuentre, que era lo que pasaba al no acordarse de por dónde iba.
     */
    @Nullable
    private BlockPos buscarCultivoSinAbonar(ServerLevel level) {
        return buscarCultivo(level, false, abonadas);
    }

    /**
     * El <b>compostero</b> de la aldea que todavía <b>no está lleno</b>, o {@code null} si no hay. El compostero está
     * pegado a la esquina de cada parcela (ver {@code VillageGenerator.plot}).
     */
    @Nullable
    private BlockPos buscarCompostero(ServerLevel level) {
        for (BlockPos parcela : VillageGenerator.parcelasDe(level, center)) {
            BlockPos comp = parcela.offset(-1, 0, 0);
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos q = comp.offset(0, dy, 0);
                BlockState s = level.getBlockState(q);
                if (s.is(Blocks.COMPOSTER) && s.getValue(ComposterBlock.LEVEL) < ComposterBlock.MAX_LEVEL) {
                    return q;
                }
            }
        }
        return null;
    }

    /**
     * Echa al <b>compostero</b> las semillas que le <b>sobran</b> (más de {@link #SEMILLAS_MAX}, que es lo que
     * necesita para sembrar). De trigo y betabel: la <b>zanahoria y la patata NO</b>, que son semilla <b>y</b> comida.
     * <p>
     * El compostero lleno (nivel {@code READY}) lo vacía en la despensa al visitarla (ver {@link #enLaDespensa}), y esa
     * harina de huesos es la que después usa para abonar. Sin este paso el compostero se quedaba <b>vacío para
     * siempre</b> (nadie le echaba nada), la harina se acababa y el abono se quedaba sin hacer.
     */
    private void compostar(ServerLevel level) {
        if (target == null) {
            return;
        }
        BlockState state = level.getBlockState(target);
        if (!state.is(Blocks.COMPOSTER)) {
            return;
        }
        int echadas = 0;
        // `ComposterBlock.insertItem` gasta UNA unidad del stack que se le pasa (y no la gasta si el compostero ya
        // está lleno o si el objeto no es compostable), así que se le da un stack de 1 y se mira si se ha vaciado:
        // así el bucle no se puede quedar dando vueltas.
        while (echadas < COMPOSTAR_MAX && state.getValue(ComposterBlock.LEVEL) < ComposterBlock.MAX_LEVEL) {
            ItemStack una = sacarSemillaSobrante();
            if (una.isEmpty()) {
                break; // no le quedan semillas de sobra
            }
            state = ComposterBlock.insertItem(villager, state, level, una, target);
            if (!una.isEmpty()) {
                guardarEnInventario(una); // no era compostable (no debería pasar): se le devuelve
                break;
            }
            echadas++;
        }
        if (echadas > 0) {
            level.levelEvent(1500, target, 1); // el humo del compostero, como cuando lo llena el jugador
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.COMPOSTER_FILL_SUCCESS,
                    SoundSource.BLOCKS, 0.7F, 1.0F);
            VillageManager.ponerSuceso(villager, "Lleno el compostero (" + echadas + ")");
            DevilRpg.LOGGER.info("[Village] El granjero: Lleno el compostero con {} semilla(s)", echadas);
        }
    }

    /** Cuántas semillas lleva encima que le <b>sobran</b> (más de las que necesita para sembrar). */
    private int semillasSobrantes() {
        return Math.max(0, semillasEnMano() - SEMILLAS_MAX);
    }

    /**
     * Semillas <b>compostables</b> (trigo y betabel) que le sobran. La zanahoria y la patata <b>no</b> cuentan: son
     * semilla <b>y</b> comida, así que no se tiran al compostero — y contarlas mandaba al granjero al compostero a no
     * echar nada (bucle de viajes vacíos).
     */
    private int semillasCompostablesSobrantes() {
        return Math.max(0, semillasCompostablesEnMano() - SEMILLAS_MAX);
    }

    /** Semillas de trigo y betabel que lleva encima. */
    private int semillasCompostablesEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Saca del inventario UNA semilla compostable que le sobre (trigo o betabel), o vacío si no tiene de sobra. */
    private ItemStack sacarSemillaSobrante() {
        if (semillasCompostablesSobrantes() <= 0) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Busca tierra de cultivo con el hueco de arriba libre (para plantar). */
    @Nullable
    private BlockPos buscarTierraVacia(ServerLevel level) {
        for (BlockPos parcela : VillageGenerator.parcelasDe(level, center)) {
            for (int dx = 0; dx < VillageGenerator.PLOT_WIDTH; dx++) {
                for (int dz = 0; dz < VillageGenerator.PLOT_DEPTH; dz++) {
                    BlockPos q = parcela.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos tierra = q.offset(0, dy, 0);
                        BlockPos aire = tierra.above();
                        if (level.getBlockState(tierra).is(Blocks.FARMLAND) && level.getBlockState(aire).isAir()) {
                            return aire;
                        }
                    }
                }
            }
        }
        return null;
    }

    private int trigoEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.WHEAT)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Vegetales (zanahoria, patata, betabel) que lleva encima: también son comida de la aldea. */
    private int vegetalesEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (VillagePantry.esVegetal(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int harinaEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private boolean tieneSemillas() {
        return semillasEnMano() > 0;
    }

    /** Cuántas semillas lleva encima (sumando todos los tipos). */
    private int semillasEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemilla(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Saca una semilla del inventario (y devuelve la semilla que debe plantarse, o vacío). */
    private ItemStack sacarSemilla() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemilla(s)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack sacarHarina() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
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

    public static boolean esSemilla(ItemStack s) {
        return s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS);
    }

    /** El cultivo que crece de esa semilla. */
    @Nullable
    private static BlockState cultivoDe(ItemStack semilla) {
        if (semilla.is(Items.WHEAT_SEEDS)) return Blocks.WHEAT.defaultBlockState();
        if (semilla.is(Items.CARROT)) return Blocks.CARROTS.defaultBlockState();
        if (semilla.is(Items.POTATO)) return Blocks.POTATOES.defaultBlockState();
        if (semilla.is(Items.BEETROOT_SEEDS)) return Blocks.BEETROOTS.defaultBlockState();
        return null;
    }
}
