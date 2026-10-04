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

    /**
     * Prioridad: por debajo de todos los oficios (4), de la reparación del obrero (5) y de la recogida por oficio
     * (6), para no quitarles la faena. Era <b>6</b> y la recogida por oficio pasó a 6 (etapa H): se sube a 7 para
     * que no empaten (en un empate gana el que se engancha antes, y la recogida se le pone al aldeano antes que la
     * taberna).
     */
    public static final int PRIORIDAD = 7;
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

    /**
     * La mesa de este aldeano: una de las de la taberna, repartidas por su UUID (así no se apilan en la misma).
     * <p>
     * I189 · <b>RETIRADA</b> (3-oct-2026): se intentó devolver aquí la <b>casilla de pie</b> de al lado en vez del
     * centro de la mesa, porque la mesa es de ladrillo y los avisos traían `neto` 0,6-0,8 con `alcanza=SI`. **No hacía
     * falta** ✗: el `tick` de este goal <b>ya</b> camina a `VillageManager.casillaDePieCercaDe(level, destino)` desde
     * I158 (su comentario lo dice: *«la mesa es un mueble que NO SE PISA»*), así que el aldeano nunca intentó pisar la
     * mesa. La causa real de aquellos avisos es otra y vive en el despachador: el aldeano <b>no se mueve nada</b>
     * (`ANDADO` 0,0-0,9) con la ruta llegando, y el recado se abandonaba sin haberle dado un empujón (ver I190).
     */
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
        // I158 (30-sep-2026) · EL ATASCO SE MIDE CONTRA **EL PASO**, NO CONTRA LA MESA. La mesa es un mueble que NO SE
        // PISA (`destino=dark_oak_fence encima=oak_pressure_plate`), así que la recta hasta ella puede no bajar nunca
        // aunque el aldeano vaya andando su camino: medido, `Yendo a la taberna` se rendía **29 veces en 4 corridas**
        // con `ruta=3 nodos … alcanza=SI` — la ruta SÍ llegaba. Es la lección de I112/I140/I153 (el del obrero, la
        // misma tarde), aplicada aquí: se camina a la **casilla de pie** desde la que se come y el avance se mide
        // contra ESA casilla (y, si no baja, contra el avance de la ruta, que es lo que de verdad dice si va).
        BlockPos puesto = VillageManager.casillaDePieCercaDe(level, destino);
        double distancia = Math.sqrt(villager.distanceToSqr(puesto.getX() + 0.5D, puesto.getY() + 0.5D,
                puesto.getZ() + 0.5D));
        if (distancia > 0.8D && espera == 0) {
            // SE CAMINA A UNA CASILLA DE PIE, no a la mesa (regla de I114, la que arregló al ganadero en I131): la
            // celda de la mesa puede no ser pisable —o estar un nivel más arriba— y entonces el planificador devuelve
            // una ruta de UN nodo que no alcanza y el aldeano empuja hasta rendirse. MEDIDO (26-sep-2026): con la
            // recolectora ya libre de las parcelas (I130), "Filomena / Yendo a la taberna" se rindió **16 veces en una
            // corrida** —dos tercios del total de esa corrida— con `ruta=1 nodos … alcanza=NO` al destino `516,64,639`
            // desde `516,63,641` (dos bloques y un nivel de diferencia).
            VillageManager.caminarHacia(villager, puesto, VELOCIDAD);
            VillageManager.ponerActividad(villager, "Yendo a la taberna");
            if (distancia < mejorDistancia - 0.5D || VillageManager.avanzaPorLaRuta(villager)) {
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
