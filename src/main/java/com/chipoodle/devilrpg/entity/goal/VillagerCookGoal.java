package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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

import java.util.EnumSet;
import java.util.List;

/**
 * El <b>COCINERO</b> de la aldea (etapa E): el aldeano que <b>cocina</b> en el <b>ahumador de la cocina del kiosco</b>
 * lo que el pueblo tiene crudo —la carne del corral anexo y las patatas de la huerta— y lo devuelve a la
 * <b>despensa</b>.
 * <p>
 * Por qué importa tanto: en el contador de comida de la aldea la <b>carne cruda vale 2 puntos y la cocinada 4</b> (y la
 * patata 2, la asada 4). O sea que cocinar <b>duplica</b> la comida que ya había: es la palanca de hambre del pueblo,
 * y por eso el cocinero es un puesto fijo y no un adorno. Sin él, toda la carne que sube el ganadero del corral se
 * come cruda y vale la mitad.
 * <p>
 * Cómo cocina: igual que el granjero hornea el pan, <b>en su puesto y con los objetos de verdad</b> (saca la carne de
 * la despensa, la convierte y la vuelve a guardar), con su sonido y su humo. No hay tiempo de cocción ni carbón: la
 * aldea es de verdad en lo que se ve y en lo que cuesta (los ingredientes salen del barril), no en los temporizadores.
 */
public class VillagerCookGoal extends Goal {

    /** Cuántas piezas cocina por visita a la despensa (ni una más: el resto sigue crudo hasta la próxima vuelta). */
    private static final int COCINAR_MAX = 8;
    /** Ticks de faena antes de que la cocción ocurra (se le ve trabajar en el ahumador). */
    private static final int WORK_TICKS = 30;
    private static final int REST_TICKS = 20;
    /** Sin nada que cocinar: a esperar (mirar la despensa no se hace por tick). */
    private static final int IDLE_REST_TICKS = 200;
    /** Si no logra acercarse en este tiempo, abandona (invariante I3: atascado = no acercarse). */
    private static final int STUCK_LIMIT = 200;
    private static final float VELOCIDAD = 0.6F;
    /** Alcance al <b>punto del patio</b> desde el que se trabaja (el de la despensa, delante de la escalera sur). */
    private static final double REACH = 6.5D;
    /**
     * Alcance al <b>ahumador</b>, que está <b>dentro del kiosco</b> (sobre la plataforma, un bloque más arriba). Es
     * el que de verdad decide si trabaja: sin él, el cocinero "cocinaría" desde la otra punta de la plaza. Con 8
     * entra de sobra desde el patio (el punto de apoyo está a ~5,5 del ahumador), así que solo salta si el aldeano
     * se quedó corto por el camino.
     */
    private static final double ALCANCE_AHUMADOR = 8.0D;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    /** El ahumador del kiosco (el puesto de trabajo). Se mide en {@link #canUse}, no en cada tick. */
    @Nullable
    private BlockPos puesto;
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;

    public VillagerCookGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) {
            return false; // solo el cocinero (el gestor le da el goal solo a él)
        }
        // En plena refriega nadie cocina, y de noche el cocinero se va a la cama como todos.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > (VillageGenerator.FENCE_RADIUS + 10.0D) * (VillageGenerator.FENCE_RADIUS + 10.0D)) {
            return false; // se ha ido lejos del pueblo
        }
        // El puesto (el ahumador del kiosco), medido UNA vez por intento: `puestoDelCocinero` pregunta la cota de la
        // plaza (un barrido del terreno) y eso no se hace en cada tick. Sin ahumador no hay cocina a la que ir.
        puesto = VillageGenerator.puestoDelCocinero(level, center);
        if (!level.getBlockState(puesto).is(Blocks.SMOKER)) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        // Solo va si de verdad hay algo que cocinar (si no, no se queda plantado en el ahumador).
        if (contarCrudoEnLaDespensa(level) <= 0) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        target = VillagePantry.puntoDeApoyo(level, center);
        return target != null;
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
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || puesto == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        // Camina al punto del patio (nunca HACIA el ahumador: está dentro del kiosco, sobre la plataforma, y la
        // navegación no puede "llegar" a un bloque sólido), pero mira y mide contra el AHUMADOR: es su puesto.
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        double alAhumador = Math.sqrt(villager.distanceToSqr(puesto.getX() + 0.5D, puesto.getY() + 0.5D,
                puesto.getZ() + 0.5D));
        villager.getLookControl().setLookAt(puesto.getX() + 0.5D, puesto.getY() + 0.5D, puesto.getZ() + 0.5D);
        if (distancia > REACH || alAhumador > ALCANCE_AHUMADOR) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            VillageManager.ponerActividad(villager, "Yendo a la cocina");
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, "Cocinando");
            return;
        }
        workTicks = 0;
        cocinar(level);
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        puesto = null;
        restTicks = REST_TICKS;
        villager.getNavigation().stop();
    }

    // --- la faena -----------------------------------------------------------------------------------

    /**
     * Cocina: saca de la despensa lo que se puede cocinar ({@link VillagePantry#sePuedeCocinar}) y devuelve el
     * equivalente cocinado ({@link VillagePantry#cocinar}). Una pieza por una: no se inventa comida, solo se
     * <b>transforma</b> la que ya había (y por eso el contador de la aldea sube al doble con la carne).
     */
    private void cocinar(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null || puesto == null || !level.getBlockState(puesto).is(Blocks.SMOKER)) {
            return;
        }
        int cocinadas = 0;
        // Se busca una pieza cruda, se saca del barril y se guarda su versión cocinada.
        for (ItemStack cruda : CRUDAS) {
            while (cocinadas < COCINAR_MAX
                    && VillagePantry.sacar(despensa, s -> s.is(cruda.getItem()), 1) == 1) {
                ItemStack hecha = VillagePantry.cocinar(new ItemStack(cruda.getItem(), 1));
                // Si no cabe lo cocinado (despensa llena de crudo, que no se apila con lo cocido) se DEVUELVE
                // crudo lo que se sacó: ni se pierde la pieza ni se cocina para tirarlo.
                if (hecha.isEmpty() || !VillagePantry.guardar(despensa, hecha).isEmpty()) {
                    VillagePantry.guardar(despensa, new ItemStack(cruda.getItem(), 1));
                    break;
                }
                cocinadas++;
            }
        }
        if (cocinadas > 0) {
            level.playSound(null, puesto, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, puesto.getX() + 0.5D, puesto.getY() + 1.0D,
                    puesto.getZ() + 0.5D, 6, 0.15D, 0.15D, 0.15D, 0.01D);
            VillageManager.ponerSuceso(villager, "Cocino " + cocinadas + " piezas");
            DevilRpg.LOGGER.info("[Village] El cocinero: {} pieza(s) cocinadas (aldea {})", cocinadas, objectiveIndex);
        }
    }

    /** Lo que se puede cocinar, en el orden en que el cocinero lo va sacando del barril. */
    private static final List<ItemStack> CRUDAS = List.of(
            new ItemStack(Items.BEEF), new ItemStack(Items.PORKCHOP), new ItemStack(Items.CHICKEN),
            new ItemStack(Items.MUTTON), new ItemStack(Items.RABBIT), new ItemStack(Items.COD),
            new ItemStack(Items.SALMON), new ItemStack(Items.POTATO));

    private int contarCrudoEnLaDespensa(ServerLevel level) {
        return VillagePantry.contar(VillagePantry.despensa(level, center), VillagePantry::sePuedeCocinar);
    }

    private void irAlDestino() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
        }
    }
}
