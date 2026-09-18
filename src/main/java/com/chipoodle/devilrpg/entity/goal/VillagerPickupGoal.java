package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * <b>Recogida POR OFICIO</b>: cada aldeano recoge del suelo <b>los materiales de su propio oficio</b> mientras va a
 * lo suyo, y los guarda donde le toca (el <b>almacén</b> o la <b>despensa</b> del kiosco).
 * <p>
 * Lo pidió el jugador: <i>"nadie recoge los materiales del suelo y el recolector no se da abasto; sería mejor que cada
 * oficio recoja del suelo los materiales propios de su oficio"</i>. Y tenía razón medida: en su guardado había
 * <b>15 pepitas de hierro</b>, 5 de carne podrida, una bota, pan y 4 plantones tirados por el suelo. El <b>recolector
 * es UN aldeano</b> (el holgazán) y encima es también el <b>leñador</b>, así que se pasaba el día talando y
 * replantando y no le daba la vida para barrer el pueblo: el <b>herrero de armas</b> tenía pepitas tiradas a 20
 * bloques de su fragua mientras esperaba hierro para forjar (en el log: "Fundio 9 pepitas en un lingote", con las
 * demás por el suelo).
 * <p>
 * Cada oficio tiene su <b>lista</b> ({@link #materialesDe}) y su <b>destino</b> ({@link #destinoDe}):
 * <ul>
 *   <li><b>Granjero</b>: trigo, semillas, vegetales, abono, pan → la <b>despensa</b> (es comida, y el contador de
 *       comida de la aldea mira la despensa). Así recoge también lo que el cerebro vanilla del aldeano siega y deja
 *       caer al suelo, que era comida perdida.</li>
 *   <li><b>Herreros</b> (armas y herramientas): lingotes, pepitas, carbón, palos, cuero, cuerda, plumas y el
 *       <b>equipo que sueltan los enemigos</b> → el <b>almacén</b>, que es de donde sacan sus materiales.</li>
 *   <li><b>Ganadero</b>: carne (cruda y cocinada), huevos y pollos → la <b>despensa</b>, para que la carne llegue a la
 *       mesa sin esperar a que el granjero haga el viaje.</li>
 *   <li><b>Cocinero</b>: carne cruda y patatas → la <b>despensa</b> (su ahumador cocina desde ahí).</li>
 *   <li><b>Clérigo</b>: carne podrida, pepitas de oro y pólvora → el <b>almacén</b>.</li>
 * </ul>
 * Va a prioridad {@value #PRIORIDAD} —<b>por encima</b> de la faena del oficio (4)— pero con un radio corto
 * ({@value #RADIO} bloques alrededor del aldeano): no es un barrendero, es "lo que me encuentro yendo a trabajar". En
 * cuanto el suelo está limpio, deja de haber objetivo y vuelve a su oficio: se limita solo.
 */
public class VillagerPickupGoal extends Goal {

    /** Prioridad con la que se engancha: por encima del goal de su oficio (4) y por debajo del portón (2, sin banderas). */
    public static final int PRIORIDAD = 3;
    /** Distancia a la que recoge el objeto del suelo. */
    private static final double REACH = 2.5D;
    /** Radio (alrededor del ALDEANO, no del pueblo) en el que mira si hay algo suyo por el suelo. */
    private static final double RADIO = 20.0D;
    /** Hasta dónde se le deja ir del pueblo a por algo (el término del pueblo, como el leñador). */
    private static final double RADIO_DEL_PUEBLO = VillageGenerator.FENCE_RADIUS + 28.0D;
    /** Cosas suyas que lleva encima antes de ir a guardarlas. */
    private static final int LLEVAR_MAX = 8;
    /** Solo se recoge lo que lleve un rato en el suelo (5 s): no se le quita a nadie lo que acaba de soltar. */
    private static final int EDAD_MINIMA = 100;
    private static final int REST_TICKS = 20;
    private static final int IDLE_REST_TICKS = 100;
    private static final int STUCK_LIMIT = 140;
    private static final float VELOCIDAD = 0.6F;

    /** Dónde guarda lo que recoge: los materiales al almacén y la comida a la despensa del kiosco. */
    public enum Destino { ALMACEN, DESPENSA }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /**
     * Los materiales de SU oficio (lo único que recoge del suelo).
     * <p>
     * <b>Se lee EN VIVO</b>, no se cachea: el pueblo reparte oficios (una cría que crece, un puesto que se queda
     * vacío) y un goal que guardara la lista del oficio viejo seguiría recogiendo <b>lo del oficio anterior</b>: el
     * herrero nuevo no cogería ni un lingote porque su lista era la del granjero (o ninguna).
     */
    private Predicate<ItemStack> interes() {
        return materialesDe(villager.getVillagerData().getProfession());
    }

    /** Dónde guarda (el almacén o la despensa), también EN VIVO por el mismo motivo que {@link #interes()}. */
    private Destino destinoTipo() {
        return destinoDe(villager.getVillagerData().getProfession());
    }

    /** Nombre corto del oficio actual, para el log. */
    private String oficio() {
        return nombreDe(villager.getVillagerData().getProfession());
    }
    @Nullable
    private ItemEntity objetivo;
    @Nullable
    private BlockPos destino;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;

    public VillagerPickupGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // --- qué recoge y dónde lo guarda cada oficio ----------------------------------------------------

    /** Los materiales <b>propios del oficio</b>: lo único que este aldeano recoge del suelo. */
    public static Predicate<ItemStack> materialesDe(VillagerProfession profesion) {
        if (profesion == VillagerProfession.FARMER) {
            // La huerta y la despensa: grano, semillas, vegetales, abono y pan. Aquí entra también lo que el cerebro
            // vanilla del granjero siega y deja caer al suelo (que se quedaba ahí, perdiéndose).
            return s -> s.is(Items.WHEAT) || s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)
                    || s.is(Items.BONE_MEAL) || s.is(Items.BREAD) || VillagePantry.esVegetal(s);
        }
        if (profesion == VillagerProfession.SHEPHERD) {
            // El corral: carne, huevos y pollos. (La lana, las plumas y el cuero los barre el recolector.)
            return s -> VillagePantry.esCarneCruda(s) || VillagePantry.esCarneCocida(s)
                    || s.is(Items.EGG) || s.is(Items.CHICKEN);
        }
        if (profesion == VillagerProfession.BUTCHER) {
            // El ahumador: carne cruda y patatas (lo que sabe cocinar).
            return s -> VillagePantry.esCarneCruda(s) || s.is(Items.POTATO);
        }
        if (profesion == VillagerProfession.WEAPONSMITH || profesion == VillagerProfession.TOOLSMITH) {
            // La fragua: metales, combustible, palos y el equipo que sueltan los enemigos (que el pueblo recicla).
            return s -> s.is(Items.IRON_INGOT) || s.is(Items.GOLD_INGOT) || s.is(Items.IRON_NUGGET)
                    || s.is(Items.GOLD_NUGGET) || s.is(Items.COAL) || s.is(Items.CHARCOAL)
                    || s.is(Items.STICK) || s.is(Items.FLINT) || s.is(Items.LEATHER) || s.is(Items.STRING)
                    || s.is(Items.FEATHER) || VillagerCollectGoal.esEquipoDeEnemigo(s);
        }
        if (profesion == VillagerProfession.CLERIC) {
            // El templo: lo que el clérigo puede aprovechar (carne podrida, oro y pólvora).
            return s -> s.is(Items.ROTTEN_FLESH) || s.is(Items.GOLD_NUGGET) || s.is(Items.GUNPOWDER);
        }
        if (profesion == VillagerProfession.FISHERMAN) {
            // La pesquera: el pescado crudo (el que se le cae al suelo y el que salta del lago a la orilla).
            return s -> s.is(Items.COD) || s.is(Items.SALMON);
        }
        if (profesion == VillagerProfession.FLETCHER) {
            // El taller del LEÑADOR (etapa H): lo que suelta el monte y lo que él mismo deja caer (plantones, troncos,
            // palos, plumas y pedernal —que es de lo que se hacen las flechas—), más las flechas sueltas.
            return s -> s.is(Items.STICK) || s.is(Items.FLINT) || s.is(Items.FEATHER) || s.is(Items.ARROW)
                    || esDeMadera(s);
        }
        return s -> false;
    }

    /** ¿Ese objeto es MADERA (lo que trabaja un leñador)? Plantones, troncos, tablones y leños. */
    private static boolean esDeMadera(ItemStack s) {
        return s.getItem() instanceof net.minecraft.world.item.BlockItem bloque
                && (bloque.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock
                || s.is(net.minecraft.tags.ItemTags.LOGS)
                || s.is(net.minecraft.tags.ItemTags.PLANKS));
    }

    /** Dónde guarda lo que recoge: la comida a la <b>despensa</b> (que es lo que come el pueblo) y lo demás al almacén. */
    public static Destino destinoDe(VillagerProfession profesion) {
        if (profesion == VillagerProfession.FARMER || profesion == VillagerProfession.SHEPHERD
                || profesion == VillagerProfession.BUTCHER || profesion == VillagerProfession.FISHERMAN) {
            return Destino.DESPENSA;
        }
        return Destino.ALMACEN;
    }

    private static String nombreDe(VillagerProfession profesion) {
        if (profesion == VillagerProfession.FARMER) {
            return "el granjero";
        }
        if (profesion == VillagerProfession.SHEPHERD) {
            return "el ganadero";
        }
        if (profesion == VillagerProfession.BUTCHER) {
            return "el cocinero";
        }
        if (profesion == VillagerProfession.CLERIC) {
            return "el clerigo";
        }
        if (profesion == VillagerProfession.FISHERMAN) {
            return "el pescador";
        }
        if (profesion == VillagerProfession.FLETCHER) {
            return "el lenador";
        }
        return "el herrero";
    }

    /** ¿Este oficio recoge algo del suelo? (si no, no se le engancha el goal) */
    public static boolean tieneMateriales(VillagerProfession profesion) {
        return profesion == VillagerProfession.FARMER || profesion == VillagerProfession.SHEPHERD
                || profesion == VillagerProfession.BUTCHER || profesion == VillagerProfession.CLERIC
                || profesion == VillagerProfession.WEAPONSMITH || profesion == VillagerProfession.TOOLSMITH
                || profesion == VillagerProfession.FISHERMAN || profesion == VillagerProfession.FLETCHER;
    }

    // --- el goal ------------------------------------------------------------------------------------

    @Override
    public boolean canUse() {
        if (restTicks > 0) {
            restTicks--;
            return false;
        }
        if (villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > RADIO_DEL_PUEBLO * RADIO_DEL_PUEBLO) {
            return false; // se ha ido demasiado lejos: primero vuelve al pueblo
        }
        // Con los brazos llenos, a guardarlo (y luego vuelve a lo suyo).
        if (cuantosLleva() >= LLEVAR_MAX) {
            objetivo = null;
            destino = puntoDeDestino(level);
            return destino != null;
        }
        objetivo = buscarObjeto(level);
        if (objetivo == null) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        destino = null;
        return true;
    }

    @Override
    public void start() {
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        ir();
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.isBaby() || stuckTicks >= STUCK_LIMIT || VillageManager.estaDescansando(villager)) {
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
            villager.getLookControl().setLookAt(objetivo);
            double distancia = Math.sqrt(villager.distanceToSqr(objetivo));
            if (distancia > REACH) {
                VillageManager.caminarHacia(villager, objetivo.blockPosition(), VELOCIDAD);
                VillageManager.ponerActividad(villager, "Recogiendo lo suyo");
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
            ItemStack stack = objetivo.getItem().copy();
            int antes = stack.getCount();
            ItemStack resto = guardarEnInventario(stack);
            int cogidos = antes - resto.getCount();
            if (cogidos <= 0) {
                objetivo = null; // no le cabe: se queda en el suelo
                return;
            }
            objetivo.getItem().shrink(cogidos);
            level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.0F);
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
            if (distancia > alcanceDeGuardado()) {
                VillageManager.caminarHacia(villager, destino, VELOCIDAD);
                VillageManager.ponerActividad(villager, "Guardando lo suyo");
                if (distancia < mejorDistancia - 0.5D) {
                    mejorDistancia = distancia;
                    stuckTicks = 0;
                } else {
                    stuckTicks++;
                }
                return;
            }
            VillageManager.parar(villager);
            guardar(level);
            destino = null;
            restTicks = REST_TICKS;
        }
    }

    @Override
    public void stop() {
        objetivo = null;
        destino = null;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    // --- búsqueda y guardado ------------------------------------------------------------------------

    /** El objeto <b>suyo</b> más cercano (de su oficio, con un rato en el suelo y dentro del término del pueblo). */
    @Nullable
    private ItemEntity buscarObjeto(ServerLevel level) {
        ItemEntity mejor = null;
        double mejorDist = RADIO * RADIO;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(villager.blockPosition()).inflate(RADIO))) {
            if (!item.isAlive() || item.getItem().isEmpty() || item.tickCount < EDAD_MINIMA) {
                continue;
            }
            if (!interes().test(item.getItem())) {
                continue;
            }
            double dx = item.getX() - center.getX();
            double dz = item.getZ() - center.getZ();
            if (dx * dx + dz * dz > RADIO_DEL_PUEBLO * RADIO_DEL_PUEBLO) {
                continue; // fuera del término del pueblo: no se va por el mundo a por ello
            }
            double d = item.distanceToSqr(villager);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = item;
            }
        }
        return mejor;
    }

    /** El punto de apoyo del cofre donde guarda (el almacén o la despensa del kiosco, según su oficio). */
    @Nullable
    private BlockPos puntoDeDestino(ServerLevel level) {
        return destinoTipo() == Destino.DESPENSA ? VillagePantry.puntoDeApoyo(level, center)
                : VillageStorage.puntoDeApoyo(level, center);
    }

    private double alcanceDeGuardado() {
        return destinoTipo() == Destino.DESPENSA ? VillagePantry.ALCANCE_DESPENSA : VillageStorage.ALCANCE_ALMACEN;
    }

    /** Deja donde le toca todo lo que lleve de SU oficio. */
    private void guardar(ServerLevel level) {
        Container caja = destinoTipo() == Destino.DESPENSA ? VillagePantry.despensa(level, center)
                : VillageStorage.almacen(level, center);
        if (caja == null) {
            return;
        }
        int guardados = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty() || !interes().test(s)) {
                continue;
            }
            int antes = s.getCount();
            ItemStack resto = destinoTipo() == Destino.DESPENSA ? VillagePantry.guardar(caja, s.copy())
                    : VillageStorage.guardar(level, center, s.copy());
            guardados += antes - resto.getCount();
            villager.getInventory().setItem(i, resto);
        }
        if (guardados > 0) {
            VillageManager.ponerSuceso(villager, "Guardo " + guardados + " de lo suyo");
            DevilRpg.LOGGER.info("[Village] {} guardo {} cosa(s) de su oficio en {}",
                    oficio(), guardados, destinoTipo() == Destino.DESPENSA ? "la despensa" : "el almacen");
        }
    }

    private void ir() {
        if (objetivo != null) {
            VillageManager.caminarHacia(villager, objetivo.blockPosition(), VELOCIDAD);
            return;
        }
        if (destino != null) {
            VillageManager.caminarHacia(villager, destino, VELOCIDAD);
        }
    }

    private int cuantosLleva() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!s.isEmpty() && interes().test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Mete el stack en el inventario del aldeano y devuelve lo que no ha cabido. */
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
                int mete = Math.min(villager.getInventory().getItem(i).getMaxStackSize(), resto.getCount());
                villager.getInventory().setItem(i, resto.copyWithCount(mete));
                resto.shrink(mete);
            }
        }
        return resto;
    }
}
