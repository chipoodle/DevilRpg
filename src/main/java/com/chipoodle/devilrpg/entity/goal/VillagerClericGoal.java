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
    /** Botellas que carga de una vez (los tres huecos del soporte). */
    private static final int BOTELLAS = 3;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /** El soporte de pociones (su puesto de trabajo, en la iglesia): la celda a la que va a trabajar. */
    @Nullable
    private BlockPos soporte;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;

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
        // 4) ¿FALTAN BOTELLAS? Se cargan botellas de AGUA del almacén (el jugador las trae embotelladas).
        for (int hueco = 0; hueco < 3; hueco++) {
            if (!stand.getItem(hueco).isEmpty()) {
                continue;
            }
            ItemStack agua = VillageStorage.quitar(level, center, this::esBotellaDeAgua, 1);
            if (agua != null) {
                stand.setItem(hueco, agua);
                return;
            }
        }
        restTicks = IDLE_REST_TICKS; // no hay nada que hacer: a esperar (y el latido le reclamará el puesto)
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
