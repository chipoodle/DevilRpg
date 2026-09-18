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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
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
 * El <b>PESCADOR</b> de la aldea (etapa G): el aldeano que <b>pesca en el lago de la pesquera</b> y baja el pescado
 * <b>crudo a la despensa</b>, donde el cocinero lo ahúma (crudo = 2 puntos de comida, cocinado = 4, igual que la
 * carne del corral: el pescado es la <b>segunda fuente de proteína</b> del pueblo).
 * <p>
 * <b>No se inventa la comida</b>: el pescador saca del lago un <b>pez de verdad</b> (una entidad que se va), así que
 * lo que el pueblo come es lo que cría el lago, y el lago <b>se repuebla solo y despacio</b> hasta
 * {@link VillageGenerator#LAGO_PECES_MAX} peces (ver {@code VillageGenerator.reponerPecesDelLago}). El barril de la
 * pesquera es su <b>puesto de trabajo</b>: en vanilla el barril es del pescador, y hasta esta etapa el pueblo no usaba
 * barriles a propósito (las pipas de la taberna son de madera con corteza) para que nadie se volviera pescador sin
 * tener dónde pescar.
 * <p>
 * Va a la prioridad de los oficios ({@value #PRIORIDAD}), por debajo del guardia: primero se pesca y, cuando no hay
 * peces o es de noche, el reparto del minuto y la taberna se encargan de él como de los demás.
 */
public class VillagerFisherGoal extends Goal {

    /** Prioridad: la de los oficios (la misma que el granjero o el ganadero). */
    public static final int PRIORIDAD = 4;
    /** Ticks de faena con la caña en la mano antes de sacar un pez (se le ve pescar). */
    private static final int PESCAR_TICKS = 60;
    private static final int REST_TICKS = 20;
    /** Sin peces en el lago (o sin puesto): a esperar sin mirar el lago por tick. */
    private static final int IDLE_REST_TICKS = 200;
    /** Si no logra acercarse en este tiempo, abandona (invariante I3: atascado = no acercarse). */
    private static final int STUCK_LIMIT = 200;
    private static final float VELOCIDAD = 0.6F;
    /** Alcance al punto de la pasarela donde se pone a pescar. */
    private static final double REACH = 5.0D;
    /** Alcance al BARRIL (su puesto): es lo que decide si de verdad está trabajando. */
    private static final double ALCANCE_PUESTO = 8.0D;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /** La punta de la pasarela, sobre el agua: donde se pone con la caña. */
    @Nullable
    private BlockPos target;
    /** El barril de la pesquera (el puesto de trabajo del pescador). Se mide una vez por intento. */
    @Nullable
    private BlockPos puesto;
    private int pescaTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;

    public VillagerFisherGoal(Villager villager, BlockPos center, int objectiveIndex) {
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
        if (villager.getVillagerData().getProfession() != VillagerProfession.FISHERMAN) {
            return false; // solo el pescador (el gestor le da el goal solo a él)
        }
        // En plena refriega nadie pesca, y de noche el pescador se va a la cama como todos.
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > (VillageGenerator.FENCE_RADIUS + 10.0D) * (VillageGenerator.FENCE_RADIUS + 10.0D)) {
            return false; // se ha ido lejos del pueblo
        }
        // El puesto (el BARRIL de la pesquera) y el lago, medidos UNA vez por intento: preguntar la cota es un
        // barrido del terreno y eso no se hace por tick.
        puesto = VillageGenerator.puestoDelPescador(level, center);
        if (!level.getBlockState(puesto).is(Blocks.BARREL)) {
            restTicks = IDLE_REST_TICKS;
            return false; // sin pesquera no hay faena
        }
        // Solo va si de verdad hay peces que sacar (si no, no se queda plantado en la pasarela).
        if (VillageGenerator.pecesEnElLago(level, center) <= 0) {
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        target = VillageGenerator.trabajoDelPescador(level, center);
        if (target != null && VillageManager.esPuntoFallido(villager, target)) {
            // A esa punta de la pasarela no llegó hace poco (I33): no se queda plantado intentándolo, espera un rato.
            restTicks = IDLE_REST_TICKS;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        pescaTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        irAlDestino();
    }

    @Override
    public boolean canContinueToUse() {
        if (target != null && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): la punta de la pasarela a la que no llegó se apunta para no
            // volver a ella en bucle, que es lo que dejaba al pescador empujando la misma valla para siempre.
            VillageManager.marcarPuntoFallido(villager, target);
            return false;
        }
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || puesto == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        double alPuesto = Math.sqrt(villager.distanceToSqr(puesto.getX() + 0.5D, puesto.getY() + 0.5D,
                puesto.getZ() + 0.5D));
        // Mira al agua: es donde está el pez.
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() - 0.2D, target.getZ() + 0.5D);
        if (distancia > REACH || alPuesto > ALCANCE_PUESTO) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
            VillageManager.ponerActividad(villager, "Yendo a pescar");
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
        if (++pescaTicks < PESCAR_TICKS) {
            VillageManager.ponerActividad(villager, "Pescando");
            return;
        }
        pescaTicks = 0;
        pescar(level);
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

    private void irAlDestino() {
        if (target != null) {
            VillageManager.caminarHacia(villager, target, VELOCIDAD);
        }
    }

    /**
     * Saca <b>un pez de verdad</b> del lago (la entidad más cercana a la pasarela) y guarda lo que suelta —
     * pescado crudo— en la <b>despensa</b>, que es de donde come el pueblo (y de donde el cocinero lo coge para
     * ahumarlo). Con su salpicadura y su sonido, para que se vea que está pescando.
     */
    private void pescar(ServerLevel level) {
        AbstractFish pez = VillageGenerator.pezMasCercano(level, center);
        if (pez == null || target == null) {
            return;
        }
        ItemStack crudo = new ItemStack(pez.getType() == EntityType.SALMON ? Items.SALMON : Items.COD);
        // El pez se va del lago (no se duplica comida: lo que sale del lago es lo que come el pueblo) y su captura
        // va a la despensa; si no cupiera, se queda en el suelo de la pasarela, que el recolector la recoge.
        pez.discard();
        ItemStack resto = VillagePantry.guardar(VillagePantry.despensa(level, center), crudo);
        if (!resto.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, target, resto);
        }
        level.playSound(null, target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, target.getX() + 0.5D, target.getY() - 0.2D, target.getZ() + 0.5D,
                8, 0.3D, 0.1D, 0.3D, 0.02D);
        VillageManager.ponerSuceso(villager, "Pesco un pez");
        DevilRpg.LOGGER.info("[Village] El pescador: {} (comida de la aldea {})", crudo.getItem(),
                VillagePantry.comida(level, center));
    }
}
