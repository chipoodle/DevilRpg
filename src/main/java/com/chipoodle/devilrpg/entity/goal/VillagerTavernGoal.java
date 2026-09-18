package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * <b>Ir a comer a la taberna.</b> El aldeano que tiene <b>hambre</b> (lleva una ración sin comer, el mismo criterio que
 * las raciones del pueblo) se va a la <b>taberna</b>, se sienta en una de sus mesas, come una ración de la despensa y
 * se queda un rato de charla; al salir se le quita el hambre y se le da un rato de <b>regeneración</b> (es lo que
 * pidió el jugador: que todos vayan a comer <i>"cuando lo necesiten"</i> y que los soldados <i>"pasen cuando no estén
 * de guardia a comer y reponer energía"</i>).
 * <p>
 * <b>No gasta comida de más</b>: al comer marca al aldeano como comido ({@link VillageManager#marcarComida}), y el
 * reparto abstracto del minuto ({@code VillageManager.repartirRaciones}) salta a los que ya comieron. Si el aldeano
 * <b>no puede</b> ir a la taberna (no hay taberna, la aldea está siendo atacada o no hay comida), el reparto del
 * minuto le da su ración como siempre: nadie se muere de hambre por no llegar a la mesa.
 * <p>
 * Va a prioridad {@link #PRIORIDAD}, <b>por debajo</b> de la faena de cada oficio: primero se trabaja y, cuando no hay
 * nada que hacer (o el guardia está entre rondas), se va a comer.
 */
public class VillagerTavernGoal extends Goal {

    /** Prioridad: por debajo de todos los oficios (4) y del guardia (3), para no quitarles la faena. */
    public static final int PRIORIDAD = 6;
    /** Puntos de comida que tiene que tener la despensa para que el pueblo vaya a la taberna a comer. */
    private static final int COMIDA_MINIMA = 4;
    /** Ticks que se queda comiendo en la mesa (y de charla) una vez llega. */
    private static final int TICKS_EN_LA_MESA = 20 * 8;
    /** Regeneración que se lleva al terminar (reponer energía), en ticks. */
    private static final int TICKS_DE_REGENERACION = 20 * 10;
    private static final double REACH = 1.8D;
    private static final float VELOCIDAD = 0.6F;
    private static final int STUCK_LIMIT = 160;
    private static final int REST_TICKS = 40;

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    /** La mesa que le toca a este aldeano (se calcula al entrar, cuando ya se tiene el nivel del servidor). */
    @Nullable
    private BlockPos mesa;
    private int espera;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    private int restTicks;

    public VillagerTavernGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** La mesa de este aldeano: una de las de la taberna, repartidas por su UUID (así no se apilan en la misma). */
    private BlockPos mesaDeEsteAldeano(ServerLevel level) {
        if (mesa == null) {
            BlockPos[] mesas = VillageGenerator.puntosDeLaTaberna(center, VillageGenerator.cotaDeLaPlaza(level, center));
            mesa = mesas[Math.floorMod(villager.getUUID().hashCode(), mesas.length)];
        }
        return mesa;
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
        // Solo con HAMBRE (el mismo reloj que las raciones del pueblo) y con la taberna en pie.
        if (!VillageManager.tieneHambre(level, villager) || !VillageGenerator.tabernaConstruida(level, center)) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex)
                || VillageManager.estaDescansando(villager)) {
            return false;
        }
        // Y tiene que haber comida en la despensa (si no, el reparto del minuto se encargará como siempre).
        if (VillagePantry.comida(level, center) < COMIDA_MINIMA) {
            return false;
        }
        // Y su mesa tiene que poder alcanzarse: si es la que se quedó aparcada al rendirse (I33), no se levanta a
        // comer para quedarse empujando la silla: ya comerá del reparto del minuto, que es de donde come el pueblo.
        return !VillageManager.esPuntoFallido(villager, mesaDeEsteAldeano(level));
    }

    @Override
    public void start() {
        espera = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        if (villager.level() instanceof ServerLevel level) {
            VillageManager.caminarHacia(villager, mesaDeEsteAldeano(level), VELOCIDAD);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        if (VillageManager.estaDescansando(villager)) {
            return false;
        }
        if (stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): la mesa a la que no llegó se apunta para no volver a ella en
            // bucle, que es lo que dejaba al aldeano empujando la misma silla (o la valla) para siempre.
            VillageManager.marcarPuntoFallido(villager, mesa);
            return false;
        }
        return VillageManager.tieneHambre(level, villager) || espera > 0;
    }

    @Override
    public void tick() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos destino = mesaDeEsteAldeano(level);
        villager.getLookControl().setLookAt(destino.getX() + 0.5D, destino.getY() + 0.5D, destino.getZ() + 0.5D);
        double distancia = Math.sqrt(villager.distanceToSqr(destino.getX() + 0.5D, destino.getY() + 0.5D,
                destino.getZ() + 0.5D));
        if (distancia > REACH && espera == 0) {
            VillageManager.caminarHacia(villager, destino, VELOCIDAD);
            VillageManager.ponerActividad(villager, "Yendo a la taberna");
            if (distancia < mejorDistancia - 0.5D) {
                mejorDistancia = distancia;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            return;
        }
        VillageManager.parar(villager);
        if (espera == 0) {
            comer(level);
        }
        espera++;
        villager.swing(InteractionHand.MAIN_HAND);
        VillageManager.ponerActividad(villager, "Comiendo en la taberna");
        if (espera >= TICKS_EN_LA_MESA) {
            villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TICKS_DE_REGENERACION, 0, false, true));
            espera = 0;
        }
    }

    @Override
    public void stop() {
        espera = 0;
        restTicks = REST_TICKS;
        VillageManager.parar(villager);
    }

    /** Come una ración de la despensa: la saca de verdad (un punto), marca al aldeano y lo apunta en el log. */
    private void comer(ServerLevel level) {
        int racion = VillagePantry.sacarComida(VillagePantry.despensa(level, center), 1);
        if (racion <= 0) {
            return; // se acabó la comida: se quedará con hambre y el reparto del minuto decidirá
        }
        VillageManager.marcarComida(level, villager);
        level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.7F, 1.0F);
        VillageManager.ponerSuceso(villager, "Comio en la taberna");
        DevilRpg.LOGGER.info("[Village] {} comio en la taberna (comida de la aldea {})",
                villager.getUUID(), VillagePantry.comida(level, center));
    }
}
